---
titolo: "Lezione 13 – Piano: Spring Security nel gestionale"
corso: Spring Boot, React e sistemi RAG
prompt: prompt/13-implementare-spring-security-con-login-registrazione-permessi-e-config-cors-e-di-sicurezza-globali.md
data: 2026-11-06
stato: in attesa di approvazione
tags:
  - spring-security
  - piano
---

# Piano: Spring Security nel gestionale

> [!info] Perché un piano prima del codice
> Il prompt 13 chiede all'agente di **fermarsi dopo il piano** e aspettare l'approvazione. Cambiare un piano costa una frase; cambiare un sistema di sicurezza già scritto costa molto di più. Questo file è quel piano.

## 1. Cosa ho verificato nella codebase

| Area | Stato reale |
|---|---|
| Versioni | Java 25, Spring Boot 4.1.2, **Spring Security 7.1.2** (dal BOM, non ancora tra le dipendenze), Spring AI 2.0.2 (aggiornata dalla 2.0.1 nella lezione 10) |
| Utente corrente | `CurrentUserProvider` restituisce un **utente demo fisso** da `application.properties` (`app.demo-user.*`); in Docker `APP_DEMO_USER_ADMIN`. È usato da chat, strumenti (via `ToolContext`), bozze email, ricerca RAG, wiki, eliminazione documenti |
| `UserContext` | `record(userId, department, admin)`: c'è già il punto unico in cui inserire l'utente autenticato |
| Conversazioni | `Conversazione` **non ha un proprietario**: oggi chiunque vede e continua tutte le chat (`findAllByOrderByAggiornataIlDesc`). La memoria (PostgreSQL) è indicizzata solo per `conversationId` |
| Bozze email | hanno già `utenteId` (oggi `utente-demo`) |
| Reparti | `Dipendenti.reparto` esiste (dato aziendale); l'utente demo ha `department=IT`. Le pagine della wiki con `department` sono già filtrate (lezioni 11–12) |
| CORS | `@CrossOrigin("http://localhost:5173")` duplicato su **8 controller**; nessuna configurazione globale |
| Frontend → API | **axios** (Dashboard, Dipendenti) e **fetch** in 12 punti (chat SSE, upload multipart, storico, log, wiki, bozze, `/api/me`). Nessun `credentials`, nessun token CSRF |
| Sviluppo | Vite su 5173 chiama direttamente `http://localhost:8081` (origini diverse). Nessun proxy in `vite.config.js` |
| Docker | nginx sulla 3000 inoltra `/api/` al backend: stessa origine. Header `Host` corretto nella lezione 10 |
| Actuator | esposti `health` e `metrics`, **pubblici** |
| Endpoint di prova | `/api/documents/test-embedding` (lezione 3), `/dati-generici/**` (`ControllerGenerico`, esempio delle prime lezioni con dati fittizi) |
| Test | `spring-boot-starter-webmvc-test` e `data-jpa-test`; manca il supporto test di Spring Security |

**Non presente** (e non lo invento): verifica dell'email, recupero password, gestione utenti, sessioni condivise tra più istanze.

## 2. Censimento degli endpoint e matrice dei permessi

Regola generale: **tutto ciò che non è elencato è negato** (`anyRequest().denyAll()`).

| Endpoint | Metodo | Anonimo | USER | ADMIN | Note |
|---|---|:-:|:-:|:-:|---|
| `/api/auth/register`, `/api/auth/login`, `/api/auth/csrf` | POST/GET | ✅ | ✅ | ✅ | |
| `/api/auth/me`, `/api/auth/logout` | GET/POST | ❌ 401 | ✅ | ✅ | `me` anonimo → 401 |
| `/api/dipendenti`, `/api/dipendenti/{email}` | GET | ❌ | ✅ | ✅ | |
| `/api/dipendenti`, `/api/dipendenti/{id}` | POST/PUT/DELETE | ❌ | ❌ 403 | ✅ | |
| `/api/chat` (SSE), `/api/chat/conversations/**` | POST/GET/DELETE | ❌ | ✅ **solo le proprie** | ✅ solo le proprie | proprietario sulla conversazione |
| `/api/email-drafts/**` | GET/POST/DELETE | ❌ | ✅ solo le proprie | ✅ | già filtrate per `utenteId` |
| `/api/rag/search` | POST | ❌ | ✅ | ✅ | filtro per reparto già presente |
| `/api/documents` (upload), `/api/documents/{id}` (delete) | POST/DELETE | ❌ | ❌ | ✅ | |
| `/api/documents/jobs/**`, `/history/**` | GET | ❌ | ❌ | ✅ | pagina "Solo Admin" |
| `/api/documents/test-embedding` | GET | ❌ | ❌ | ✅ | endpoint di prova |
| `/api/documents/wiki/reindex` | POST | ❌ | ❌ | ✅ | |
| `/api/documents/wiki/status` | GET | ❌ | ✅ | ✅ | serve al Wiki Explorer per sapere se la wiki c'è |
| `/api/wiki/search`, `/page`, `/assets`, `/ask` | GET/POST | ❌ | ✅ | ✅ | reparti già applicati |
| `/api/wiki/reload` | POST | ❌ | ❌ | ✅ | |
| `/api/me` (lezione 10) | GET | – | – | – | **rimosso**, sostituito da `/api/auth/me` |
| `/api/admin/accounts/**` *(nuovo, vedi decisione B)* | GET/PATCH | ❌ | ❌ | ✅ | |
| `/actuator/health` | GET | ✅ | ✅ | ✅ | solo `UP`/`DOWN`, senza dettagli |
| `/actuator/metrics/**` | GET | ❌ | ❌ | ✅ | contiene token e tempi |
| `/dati-generici/**` | GET | ❌ | ❌ | ❌ | negato (esempio didattico, non serve al gestionale) |
| `/error` | – | ✅ | ✅ | ✅ | necessario per i messaggi d'errore di Spring Boot |

Risposte: **401** JSON se manca l'autenticazione, **403** JSON se il ruolo non basta. Nessun redirect HTML verso una pagina di login.

## 3. Account

Nuova entity `Account` (tabella `account` su **MariaDB**, mai su pgvector), distinta da `Dipendenti`:

| Campo | Tipo | Note |
|---|---|---|
| `id` | Long | |
| `email` | varchar(254) **unique** | normalizzata: `trim` + minuscole (`Locale.ROOT`) |
| `passwordHash` | varchar(255) | `DelegatingPasswordEncoder` (BCrypt di default, prefisso `{bcrypt}`) |
| `nome` | varchar(100) | nome visualizzato |
| `ruolo` | `USER` / `ADMIN` | enum, la registrazione assegna sempre `USER` |
| `reparto` | varchar(50) nullable | **non** impostabile dal browser; lo assegna un admin |
| `abilitato`, `creatoIl` | | |

- DTO dedicati: `RegisterRequest(email, password, nome)` e `AccountResponse(id, email, nome, ruolo, reparto)`, **mai** l'entity né l'hash. Campi in più nel JSON (`ruolo`, `admin`...) vengono ignorati.
- Validazione server-side: email valida (max 254), password 12–128 caratteri, nome 1–100.
- Registrazioni concorrenti con la stessa email: oltre al controllo nel service, il **vincolo unique** del database; la `DataIntegrityViolationException` diventa **409**.
- Login errato (email inesistente o password sbagliata): stesso messaggio, *"Email o password non corretti"*. Nessuna password o session ID nei log.

## 4. Il primo amministratore (bootstrap)

Due variabili d'ambiente, **mai** nel repository:

```
APP_BOOTSTRAP_ADMIN_EMAIL=...
APP_BOOTSTRAP_ADMIN_PASSWORD=...   (minimo 12 caratteri)
```

All'avvio un `ApplicationRunner`:
1. se esiste già **almeno un admin** → non fa nulla (idempotente: i riavvii non cambiano niente, la password non viene mai sovrascritta);
2. se l'email esiste già come `USER` → **non** la promuove e scrive un avviso (qualcuno potrebbe essersi registrato con quell'email prima di te);
3. altrimenti crea l'admin. Due istanze avviate insieme (VS Code e Docker condividono MariaDB) → una vince, l'altra trova il vincolo unique e ignora l'errore.

Dopo il primo avvio le variabili si possono togliere. Nel log solo *"Amministratore iniziale creato: a***@azienda.it"*.

## 5. Sessione, CSRF, CORS

| Tema | Scelta |
|---|---|
| Autenticazione | sessione server-side (`HttpSessionSecurityContextRepository`), cookie `JSESSIONID` **HttpOnly**, `SameSite=Lax`, `Secure` configurabile (`SESSION_COOKIE_SECURE`, `false` in locale perché senza HTTPS) |
| Login | `POST /api/auth/login` JSON → `AuthenticationManager` → **nuovo ID di sessione** (difesa da *session fixation*) → contesto salvato nella sessione |
| Logout | invalida la sessione e cancella il cookie |
| CSRF | **attivo**. `csrf.spa()` di Spring Security 7: cookie `XSRF-TOKEN` leggibile da JavaScript, header `X-XSRF-TOKEN` nelle richieste POST/PUT/PATCH/DELETE (anche upload e chat SSE). `GET /api/auth/csrf` lo inizializza; dopo login e logout il frontend lo rinnova |
| CORS | **una** `CorsConfigurationSource` globale, origini da `app.cors.allowed-origins` (default `http://localhost:5173`), `allowCredentials=true` solo con origini esplicite, metodi `GET, POST, PUT, PATCH, DELETE`, header `Content-Type, X-XSRF-TOKEN`. Rimossi gli 8 `@CrossOrigin` |
| SSE | le richieste asincrone (`DispatcherType.ASYNC`) della chat in streaming vengono autorizzate già all'inizio: senza questa regola Spring Security 7 ricontrollerebbe la seconda "metà" della risposta e potrebbe troncare lo streaming |
| Tentativi di login | *(proposta)* massimo 5 tentativi al minuto per email con Bucket4j (già nel progetto) → 429 |

## 6. Utente autenticato → `UserContext`

- `CurrentUserProvider.currentUser()` legge l'`Account` dal `SecurityContext`: `userId` = id dell'account, `department` = `reparto` (può essere vuoto), `admin` = ruolo ADMIN. Mai da body, query string o header.
- Utente senza reparto: vede solo i contenuti **pubblici**; gli strumenti sui dipendenti rispondono "nessun reparto assegnato". Da gestire: oggi `canAccessDepartment` con reparto `null` lancerebbe un'eccezione.
- Rimossi `app.demo-user.*` e `APP_DEMO_USER_ADMIN`.
- **Conversazioni**: nuova colonna `utente_id`. Elenco, messaggi, eliminazione e continuazione (`POST /api/chat` con un `conversationId` esistente di un altro utente) solo per il proprietario, altrimenti **404** (non riveliamo che esiste). Le conversazioni già presenti, senza proprietario, restano visibili solo agli admin.

## 7. Frontend

- `src/api/http.js`: un unico `apiFetch` (`credentials: 'include'`, header CSRF sulle richieste non sicure, gestione 401/403) e un'istanza **axios** configurata allo stesso modo. Tutti i 12 `fetch` e le chiamate axios passano da lì; la chat SSE legge ancora lo stream (cambiano solo le opzioni della richiesta), l'upload resta `multipart`.
- `AuthContext` + `useAuth()`: `loading`, `user`, `login`, `logout`, `register`; carica `/api/auth/me` una sola volta all'avvio (niente "flash" della pagina di login al refresh).
- Pagine `/login` e `/registrazione` (validazione, messaggi in italiano, stato di caricamento, i dati inseriti non si perdono dopo un errore).
- Rotte: `RequireAuth` (anonimo → `/login?redirect=...`, poi torna alla pagina richiesta), `RequireRole("ADMIN")` → pagina **403** per gli utenti normali. "Upload file per sistema RAG" visibile e accessibile solo agli admin; i pulsanti "Aggiungi / Modifica / Elimina" dipendente solo agli admin.
- Sidebar: nome e ruolo dell'utente, pulsante "Esci".
- Un 401 a sessione scaduta riporta al login **una volta sola** (niente loop).

## 8. Decisioni su cui serve il tuo via libera

> [!question] A. Sviluppo: proxy di Vite (consigliato) o chiamate dirette alla 8081?
> - **Proxy di Vite** (`/api` → `http://localhost:8081`): in sviluppo, come in Docker, frontend e API hanno la **stessa origine**. Cookie e CSRF funzionano allo stesso modo nei due ambienti; `API_BASE_URL` diventa `""` anche in sviluppo, e `npm run dev:docker` punta il proxy alla 8090.
> - **Chiamate dirette** (come oggi): restano due origini. Funziona con CORS + credenziali, ma ci sono due configurazioni diverse da mantenere e da spiegare.
>
> In entrambi i casi la configurazione CORS globale resta (con l'origine 5173), e i test la verificano.

> [!question] B. Gestione degli account (consigliata, piccola)
> Senza una pagina di gestione, nessuno può assegnare un **reparto** o promuovere un altro admin: gli strumenti sui dipendenti non funzionerebbero per gli utenti normali. Proposta minima: `GET /api/admin/accounts` e `PATCH /api/admin/accounts/{id}` (ruolo, reparto, abilitato), con una pagina "Utenti" solo per admin. Un admin non può togliersi il ruolo da solo, così il sistema non resta senza amministratori.

> [!question] C. Limite ai tentativi di login (consigliato)
> 5 tentativi al minuto per email, risposta 429. Non era richiesto dal prompt, ma senza un limite una password si può provare all'infinito.

## 9. Test previsti

| Livello | Classe | Cosa verifica |
|---|---|---|
| 1 (senza database) | `AuthServiceTest` | normalizzazione email, hash (mai in chiaro), ruolo sempre USER, email duplicata → 409, bootstrap admin idempotente e che non promuove un USER esistente |
| 1 (MockMvc, servizi finti) | `SecurityMatrixTest` | **401/403** endpoint per endpoint (tabella del §2), `denyAll` su `/dati-generici`, Actuator |
| 1 (MockMvc) | `CsrfCorsTest` | POST senza token → 403, con token → ok; preflight da 5173 accettato con credenziali, da un'origine non configurata rifiutato |
| 1 (MockMvc) | `AuthControllerTest` | registrazione (validazione, campi `ruolo` ignorati, nessun hash nella risposta), login ok / password errata / email inesistente (stesso messaggio), nuovo ID di sessione, logout che invalida la sessione, `me` anonimo e autenticato |
| 2 (integrazione) | `ConversationOwnershipTest` | un utente non legge, non elimina e non continua la chat di un altro; vincolo unique su registrazioni concorrenti |
| Regressione | test esistenti | chat SSE e upload/job con un utente autenticato nel `SecurityContext` |

Frontend: `oxlint` e `vite build` senza nuovi errori. Verifica nel browser di anonimo, USER e ADMIN, sia su Vite sia su Docker.

## 10. Fasi (con una sosta alla fine di ognuna)

1. **Backend: account, login, sessione, CSRF, CORS, matrice dei permessi** + test di livello 1.
2. **Backend: utente reale nel chatbot** (`UserContext`, proprietario delle conversazioni, bozze, wiki) + regressione dei test esistenti.
3. **Frontend**: client HTTP unico, `AuthContext`, login/registrazione, rotte protette, 403.
4. **Verifica completa** (Vite e Docker, tre profili) + lezione, log, README.

## 11. Rischi di compatibilità

- **Chat SSE e richieste asincrone** (§5): da verificare con un test e nel browser.
- **Test esistenti** che chiamano `ChatService` direttamente: senza utente nel `SecurityContext` falliranno. Li aggiorno impostando un utente di test.
- **Backend in VS Code e in Docker**: condividono MariaDB (account comuni) ma le **sessioni sono separate** e in memoria: dopo un riavvio si rifà il login.
- **Utente demo**: sparisce. Per usare il gestionale servono le variabili del primo admin, oppure una registrazione (ruolo USER).
