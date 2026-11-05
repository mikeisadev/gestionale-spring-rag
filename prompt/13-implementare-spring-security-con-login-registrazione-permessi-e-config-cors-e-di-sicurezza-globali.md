# **13) Implementare Spring Security: login, registrazione, ruoli e CORS**

In questa sezione aggiungiamo l'autenticazione e l'autorizzazione al gestionale. Non basta mostrare una pagina di login: il backend deve verificare l'identità a ogni richiesta e applicare i permessi anche quando qualcuno chiama direttamente le API, senza passare dall'interfaccia.

Il prompt qui sotto è una specifica per un agente di coding. Prima di incollarlo, assicurati di aver completato le sezioni precedenti: in particolare, la memoria delle conversazioni, i tool del chatbot, il RAG e l'interfaccia React. Se alcune parti non sono ancora presenti, l'agente dovrà descriverlo nel piano e non inventare implementazioni che la codebase non contiene.

## **13.1) Obiettivo**

Implementa in questa codebase:

1. registrazione di un account e login;
2. ruoli applicativi e autorizzazioni sia nel backend sia nell'interfaccia;
3. configurazione centralizzata di Spring Security, CORS e protezione CSRF;
4. pagine React per login e registrazione e gestione dello stato dell'utente;
5. test automatici e documentazione didattica delle scelte.

L'obiettivo è integrare la sicurezza nel gestionale che esiste già, non creare un progetto dimostrativo separato. Leggi la repository e adatta ogni scelta alla versione effettivamente configurata.

## **13.2) Prompt per l'agente di coding**

**Ruolo:** sei un senior full-stack developer esperto di Java, Spring Boot, Spring Security e React. Devi integrare autenticazione e autorizzazione nella codebase esistente, mantenendo funzionanti il gestionale, il chatbot RAG in streaming e l'upload/indicizzazione dei documenti.

**Prima di modificare i file**, leggi README.md, prompt/00-introduzione.md, i prompt da 01 a 12 e le lezioni pertinenti. Poi esamina l'intera codebase: dipendenze Maven, configurazione Spring, entity e repository, tutti i controller e servizi, test, rotte e richieste del frontend, configurazione Vite, Nginx e Docker Compose. Non dare per scontato che gli esempi dei prompt precedenti siano già implementati: confrontali con il codice reale.

### Contesto attuale da verificare nel repository

- Backend Maven in `industriatech/`; al momento del prompt usa Java 25, Spring Boot 4.1.2 e Spring AI 2.0.1. Mantieni le versioni esistenti, salvo incompatibilità dimostrata.
- Il database applicativo principale è MariaDB e contiene i dati del gestionale; PostgreSQL con pgvector è usato per il vector store. Gli account e i relativi hash delle password appartengono al database applicativo, mai al database vettoriale.
- Sono presenti API per dipendenti, chat RAG in streaming SSE, upload e stato dei job documentali e ricerca semantica. Cerca e proteggi anche gli endpoint meno evidenti e quelli di prova.
- Frontend React/Vite in `frontend/`; in sviluppo gira su `http://localhost:5173`, mentre con Docker Nginx serve il frontend su `http://localhost:3000` e inoltra `/api/` al backend.
- La pagina di upload mostra già l'etichetta “Solo Admin” e un messaggio che anticipa la protezione con login. Rendila effettiva: una scritta o una route protetta nel browser non sostituisce mai i controlli server-side.

### Metodo di lavoro

1. **Analisi e piano:** prima di scrivere codice, restituisci un piano breve ma concreto con file/classi coinvolti, schema dell'account, endpoint, matrice dei permessi, configurazione sessione/CSRF/CORS, cambiamenti UI, test, rischi di compatibilità e documentazione. Indica cosa hai verificato e cosa invece non è presente. **Fermati e attendi la mia approvazione.**
2. Dopo l'approvazione, procedi per fasi verificabili, senza accorpare tutto in una modifica opaca. Alla fine di ogni fase mostra i file modificati, le decisioni rilevanti e i comandi/test eseguiti; fermati in attesa del mio comando per la fase successiva.
3. Non applicare le istruzioni operative di questo prompt prima che ti chieda esplicitamente di eseguirlo. Non fare commit autonomi.

### Scelte di autenticazione e account

- Usa **Spring Security** e una sessione server-side con cookie di sessione `HttpOnly`; non conservare password, session ID o token di autenticazione in `localStorage` o `sessionStorage`.
- Implementa endpoint JSON coerenti con il frontend, per esempio:
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `GET /api/auth/me` per conoscere l'utente autenticato
  - un endpoint o un meccanismo esplicito per inizializzare/rinnovare il token CSRF.
  Prima di fissare payload e codici HTTP, controlla convenzioni e gestione errori già presenti; documenta il contratto scelto.
- Crea un'entità di account distinta da `Dipendenti`: un dipendente è un dato aziendale e non deve diventare implicitamente un account autenticabile. Usa DTO dedicati e non restituire mai password, hash, campi interni o dettagli sensibili dell'entità.
- Memorizza esclusivamente password codificate con il `PasswordEncoder` raccomandato dalla versione di Spring Security in uso (per esempio `DelegatingPasswordEncoder` con BCrypt). Mai testo in chiaro, password predefinite o segreti hardcoded.
- Valida server-side email, password e input; normalizza l'email in modo coerente e applica un vincolo univoco nel database. Gestisci anche le registrazioni concorrenti, non solo il controllo preliminare nel service.
- La registrazione pubblica può assegnare soltanto `ROLE_USER`. Ignora o rifiuta ruoli, permessi, ID utente o stato privilegiato inviati dal browser. Non permettere mai a un utente di promuovere sé stesso ad amministratore.
- Prevedi un modo esplicito e documentato per creare il primo amministratore, configurabile tramite ambiente/secret e sicuro rispetto a riavvii e race condition. Non inserire credenziali dimostrative o password di default nel repository, nei log o nei dati Docker versionati.
- Restituisci errori JSON chiari e coerenti. Per credenziali errate non rivelare se l'email esiste; non includere password, session ID o dati sensibili nei log. Gestisci correttamente session fixation, logout e invalidazione della sessione.

### Ruoli e autorizzazioni

Usa almeno `ROLE_USER` e `ROLE_ADMIN`. Centralizza la policy in Spring Security e applicala agli endpoint reali dopo averli censiti. Come matrice di partenza:

| Funzione | Anonimo | `ROLE_USER` | `ROLE_ADMIN` |
|---|---:|---:|---:|
| Registrazione e login | Sì | Sì | Sì |
| Consultazione del proprio profilo e logout | No | Sì | Sì |
| Consultazione di dipendenti e uso della chat/RAG | No | Sì | Sì |
| Creazione, modifica ed eliminazione dei dipendenti | No | No | Sì |
| Upload documenti, gestione/consultazione dei job e operazioni di prova dell'indicizzazione | No | No | Sì |
| Endpoint di amministrazione, se presenti | No | No | Sì |

Verifica ogni rotta e metodo HTTP, inclusi `/api/rag/**`, `/api/documents/**`, endpoint diagnostici, controller generici e qualsiasi endpoint Actuator eventualmente configurato. La tabella è una base da confrontare con il comportamento e la UI esistenti: se una funzione richiede una policy diversa, motivala nel piano e attendi l'approvazione prima di allargare i permessi. Non usare regole permissive generiche per far passare i test; le rotte non previste devono essere negate per impostazione predefinita.

Se il chatbot o i tool implementati nei prompt precedenti usano un `UserContext`, ricava ID, ruolo e, quando realmente presente nel modello account, reparto dall'utente autenticato lato server. Non accettare questi valori da body, query string o header controllabili dal client. Non introdurre permessi per reparto senza una fonte dati e una regola verificabile; quando esistono, applicali anche alla ricerca e ai tool, non soltanto alla UI. Se esiste o viene aggiunta memoria delle conversazioni, verifica che un utente non possa leggere o continuare la conversazione di un altro alterando un ID.

### Spring Security, sessione, CSRF e CORS

- Configura bean `SecurityFilterChain` e i componenti strettamente necessari seguendo le API della versione installata; non copiare configurazioni obsolete basate su `WebSecurityConfigurerAdapter`.
- Poiché la SPA usa cookie di sessione, mantieni la protezione CSRF attiva per le richieste che modificano stato. Definisci un flusso CSRF esplicito per React (cookie leggibile solo se necessario, oppure endpoint dedicato) e invia il token nelle richieste non sicure, incluse upload e richieste JSON. Non disabilitare CSRF globalmente per risolvere un errore 403.
- Configura cookie/sessione con `HttpOnly`, `SameSite` appropriato e `Secure` quando si usa HTTPS; rendi configurabili le differenze tra sviluppo locale e produzione. Non esporre il cookie di sessione a JavaScript.
- Configura CORS una sola volta a livello globale, con origini esplicite e configurabili per ambiente. Includi almeno l'origine Vite di sviluppo; in Docker il frontend e le API passano da Nginx e hanno la stessa origine. Abilita le credenziali solo con origini esplicite: mai `*` insieme a `allowCredentials=true`. Limita metodi e header a quelli necessari e verifica preflight e richieste reali.
- Individua e rimuovi o armonizza i `@CrossOrigin` locali che duplicano la policy globale e consentono origini non previste.
- Per le API usa risposte `401 Unauthorized` quando manca l'autenticazione e `403 Forbidden` quando il ruolo non basta; non reindirizzare le chiamate fetch/SSE a una pagina HTML di login.
- Mantieni accessibili solo gli endpoint strettamente necessari all'autenticazione, al bootstrap CSRF e alle risorse statiche gestite dal frontend. Se Actuator è presente, esponi e proteggi solo ciò che è necessario, senza renderlo pubblico per comodità.

### Integrazione con React

- Aggiungi pagine accessibili di login e registrazione, con validazione e messaggi d'errore comprensibili in italiano; mostra stati di caricamento e non cancellare i dati inseriti senza motivo.
- Aggiungi un contesto/hook riutilizzabile per caricare `/api/auth/me`, rappresentare `loading`, utente autenticato e ruolo, e gestire login/logout. Evita richieste duplicate o flicker non necessario al refresh.
- Proteggi le route nel frontend e mostra/nascondi navigazione e azioni in base al ruolo, ma considera questi controlli solo UX: il backend resta l'autorità.
- Rendi accessibile l'area “Solo Admin” esclusivamente ad `ROLE_ADMIN`; per utenti non autorizzati mostra una pagina/stato 403 appropriato, mentre gli anonimi vengono indirizzati al login preservando la destinazione richiesta.
- Centralizza le chiamate HTTP o aggiorna i client esistenti affinché inviino le credenziali di sessione e il token CSRF. Adegua sia Axios sia `fetch` usato dalla chat SSE e dall'upload; non rompere lo streaming dei token né l'upload multipart.
- Gestisci globalmente o in modo consistente 401 e 403 senza creare loop di redirect. Mantieni `API_BASE_URL`, il proxy Vite e il proxy Nginx compatibili con le due modalità di esecuzione.

### Test, qualità e criteri di accettazione

Aggiungi dipendenze di test solo se necessarie e compatibili con il progetto. Scrivi test backend automatici che coprano almeno:

- registrazione valida, validazione input, email duplicata e assegnazione non manipolabile di `ROLE_USER`;
- password persistita come hash e mai inclusa nelle risposte;
- login riuscito, password errata, utente inesistente e logout/invalidation della sessione;
- endpoint del profilo anonimo e autenticato;
- matrice `401`/`403` per le API principali e permessi admin sulle operazioni di scrittura/upload;
- protezione CSRF: operazione mutativa respinta senza token e accettata con token valido;
- CORS per origini consentite e rifiuto di origini non configurate, con credenziali abilitate solo dove previsto;
- regressione delle API esistenti, in particolare SSE della chat e flusso upload/job.

Verifica inoltre che il frontend compili e che lint/build non introducano errori. Se un test richiede un servizio esterno o una chiave LLM, separalo dai test deterministici e descrivi come eseguirlo senza rendere la suite ordinaria dipendente da credenziali reali. Non dichiarare superata una verifica che non hai eseguito.

La sezione è completa quando un utente non autenticato non può chiamare direttamente API protette; un `ROLE_USER` non può caricare documenti o modificare dipendenti; un `ROLE_ADMIN` può usare le funzioni amministrative; CSRF e CORS funzionano sia nello sviluppo Vite sia con il reverse proxy Docker; e la chat SSE continua a funzionare con la sessione.

### Documentazione e consegna per lo studente

- Aggiorna solo le parti pertinenti della documentazione, senza stravolgere il README esistente. Spiega in italiano semplice, con esempi adatti a junior/medior: autenticazione contro autorizzazione, sessione/cookie, hash password, ruoli, CSRF, CORS e differenza tra route protetta e API protetta.
- Crea una lezione Markdown per questa grande operazione, compatibile con Obsidian, che descriva il flusso browser → Spring Security → controller e includa esempi di verifica senza credenziali reali.
- Registra l'operazione nel formato già usato da `lezioni/log-operazioni.json`, senza riscrivere o cancellare le voci precedenti.
- In chiusura riporta file modificati, API e policy implementate, variabili d'ambiente da configurare, comandi di test e limiti rimasti. Non riportare segreti nei riepiloghi.

## **13.3) Come controllare il lavoro dell'agente**

Prima approva il piano: verifica soprattutto quali endpoint saranno pubblici, come viene creato il primo amministratore e come React invierà il token CSRF. Dopo ogni fase controlla i test e prova almeno una richiesta anonima, una con `ROLE_USER` e una con `ROLE_ADMIN`. Un menu che nasconde il pulsante di upload non è una prova di sicurezza: la stessa operazione deve essere rifiutata dal backend quando il ruolo non è autorizzato.

Con sessioni a cookie, CSRF e CORS sono controlli complementari: CORS non autentica l'utente e non sostituisce CSRF; CSRF non decide quali ruoli possano usare un endpoint. Verifica ciascun confine separatamente e non disabilitare un controllo per far funzionare l'altro.