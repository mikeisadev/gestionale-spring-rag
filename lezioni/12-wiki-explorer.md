---
titolo: "Lezione 12 – Wiki Explorer: esplorare e interrogare la LLM Wiki"
corso: Spring Boot, React e sistemi RAG
prompt: prompt/12-wiki-explorer-implementare-un-esploratore-e-interrogatore-della-wiki.md
data: 2026-11-06
livello: junior / medior
tags:
  - llm-wiki
  - react
  - spring-ai
  - tool-calling
  - sicurezza
  - agenti-di-coding
---

# Lezione 12 – Wiki Explorer: esplorare e interrogare la LLM Wiki

> [!abstract] In breve
> Nell'analogia di Karpathy la wiki è il **codice sorgente** e Obsidian è l'**IDE**. In questa lezione costruiamo un secondo "IDE", questa volta sul web e **dentro il gestionale**: la scheda **Wiki Explorer**, con quattro funzioni:
> 1. **ricerca** per titolo, alias, tag e testo, con un estratto;
> 2. **lettura** delle pagine: Markdown, formule LaTeX, immagini e video, collegamenti cliccabili;
> 3. **navigazione**: chi cita la pagina (backlink) e chi viene citato;
> 4. **"Chiedi alla wiki"**: domande in linguaggio naturale **senza database vettoriale**. Il modello legge l'indice, cerca e apre le pagine con due strumenti in sola lettura.

> [!tip] Obsidian o una web app tua?
> Puoi continuare a usare **Obsidian** come IDE della tua wiki: va benissimo. Ma se vuoi un ambiente di studio diverso, fatto su misura per te (le tue schede, i tuoi colori, le tue scorciatoie, un pulsante "spiegamelo più semplice"...), puoi costruirtelo, **facendoti assistere da un agente di coding**. Questa lezione è un esempio di come si fa.

Collegamenti: [[11-collegare-llm-wiki-al-rag]] (le classi della wiki che riusiamo), [[08-guardrail-difese-a-strati]] (Markdown sicuro, limiti), [[06-strumenti-function-calling]] (ToolContext).

## 1. Due modi di interrogare la stessa wiki

| | Lezione 11: wiki **nel RAG** | Lezione 12: **Chiedi alla wiki** |
|---|---|---|
| Come trova le informazioni | ricerca vettoriale su 616 chunk in pgvector | legge `index.md`, cerca per parole chiave, apre le pagine intere |
| Serve indicizzare? | sì (circa 56 minuti di embedding la prima volta) | no: l'indice in memoria si costruisce in **257 ms** |
| Cosa vede il modello | 5 frammenti (sezioni) | pagine intere, e può seguire i collegamenti |
| Costo per domanda | 1 embedding + 1 chiamata al modello | più chiamate al modello (una per giro di strumenti) |
| Scala bene con | migliaia di documenti | qualche centinaio di pagine ben collegate |

Alla fine della lezione puoi fare la stessa domanda nella **Chat** (RAG) e in **Chiedi alla wiki**, e confrontare le risposte.

## 2. Il metodo: un prompt per un agente di coding

Il testo originale della lezione propone di far costruire questa parte a un **agente di coding**, scrivendo un buon prompt e controllando il risultato. È esattamente quello che è successo in questo progetto: il prompt `prompt/12-...md` è stato dato a un agente, che ha scritto il codice; tu lo controlli con la checklist del paragrafo 8.

Un buon prompt per un progetto intero somiglia a una **specifica**:

| Parte | Esempio |
|---|---|
| Ruolo | "sei un senior full stack developer (Java/Spring e React)" |
| Obiettivo | "crea Wiki Explorer, una web app per esplorare e interrogare una LLM Wiki" |
| Tecnologie | Spring Boot 4, Spring AI 2, React, react-markdown, remark-math... |
| Funzionalità | gli endpoint, uno per uno |
| **Vincoli di sicurezza** | sola lettura, percorsi validati, estensioni ammesse, limiti, niente `rehype-raw` |
| **Qualità** | classi piccole, record, indice immutabile, test JUnit sui punti critici |
| Modo di lavorare | "prima mostrami il piano, poi procedi per fasi e fermati a ogni fase" |

> [!warning] Se non scrivi i vincoli, l'agente sceglie la strada più breve
> I vincoli di sicurezza e qualità sono quelli che un senior controllerebbe in una code review. Un agente non li applica "per buon senso": se non li chiedi, la soluzione più corta (che di solito è la meno sicura) vince.

**Lavorare a fasi** (piano → backend senza AI → test → frontend → "Chiedi"), con un commit alla fine di ognuna: cambiare un piano costa una frase, cambiare il codice costa molto di più; e se una fase va storta torni indietro con un comando.

### 2.1 Le differenze rispetto al prompt: Wiki Explorer DENTRO il gestionale

Il prompt originale crea un progetto separato (`wiki-explorer/`). Qui lo abbiamo integrato nel gestionale, come scheda **Wiki Explorer** nel menu. Questo ha cambiato alcune scelte:

| Prompt originale | Nel gestionale | Perché |
|---|---|---|
| Progetto separato, Java 21 | Stesso backend (Java 25) e stesso frontend | Un'applicazione sola da avviare |
| Classi della wiki nuove | **Riusate** quelle della lezione 11 (`WikiVault`, `MarkdownNote`, `WikiLinks`...) | Niente codice duplicato |
| Proxy di Vite verso `localhost:8080` | `API_BASE_URL` (lezione 4) e `@CrossOrigin` come il resto del progetto | Coerenza con le altre pagine |
| Link `/page?path=...` | `/wiki/page?path=...` | Le rotte del gestionale |
| Nessun permesso | **Reparti** anche nell'explorer | Altrimenti sarebbe una porta laterale rispetto alla chat (lezione 11, §7.1) |
| Nessun guardrail su "Chiedi" | Limite di domande al minuto e InputGuard della chat | Ogni domanda costa token |

## 3. Il backend

```
wiki/
├── WikiPage.java           una pagina analizzata (record): titolo, alias, tag, reparto, testo, link
├── WikiIndex.java          fotografia IMMUTABILE: link risolti, backlink, asset
├── WikiCatalog.java        tiene l'indice in memoria e lo sostituisce in un colpo solo
├── SearchHit.java          un risultato di ricerca
├── WikiSearchService.java  ricerca per parole chiave con punteggio
├── WikiWebMarkdown.java    [nostro] [[wikilink]] -> link Markdown standard
├── WikiAskTools.java       searchWiki e readWikiPage (sola lettura)
└── WikiAskService.java     ChatClient con i due strumenti
controller/
├── WikiController.java     /api/wiki/search, /page, /assets, /reload
└── WikiAskController.java  /api/wiki/ask
```

Come nella lezione 11, tutte queste classi hanno `@Conditional(WikiEnabledCondition.class)`: senza `WIKI_VAULT_PATH` gli endpoint `/api/wiki/**` non esistono e la scheda mostra "Wiki non configurata".

### 3.1 WikiIndex: risolvere i link come Obsidian

```java
public Optional<String> resolve(String target) {
    String fileTarget = target.endsWith(".md") ? target : target + ".md";
    if (pagesByPath.containsKey(fileTarget)) {
        return Optional.of(fileTarget);                       // 1. percorso esatto
    }
    List<String> candidates = pathsByFileName.getOrDefault(fileNameOf(fileTarget), List.of());
    return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty(); // 2. nome, se UNICO
}
```

- se due pagine in cartelle diverse hanno lo stesso nome (`a/Note.md` e `b/Note.md`), `[[Note]]` è **ambiguo** e non viene risolto: meglio un link mancante che un link sbagliato;
- i **backlink** si calcolano una volta sola, quando l'indice viene creato;
- la classe è **immutabile**: niente setter, collezioni di sola lettura (`Map.copyOf`). Più richieste la leggono insieme senza problemi di concorrenza.

### 3.2 WikiCatalog: sostituire l'indice in un colpo solo

```java
private volatile WikiIndex index;

public synchronized WikiIndex reload() {
    index = load();   // l'indice nuovo prende il posto del vecchio con una sola assegnazione
    return index;
}
```

Chi sta leggendo il vecchio indice finisce il suo lavoro; le richieste successive usano il nuovo. `volatile` garantisce che tutti i thread vedano subito il nuovo riferimento. Nella pagina c'è il pulsante **Rileggi la wiki** (`POST /api/wiki/reload`), da usare dopo un ingest in Obsidian.

### 3.3 WikiSearchService: un punteggio spiegabile

| Dove compare il termine | Punti |
|---|---|
| tutta la frase cercata nel titolo | 15 |
| ogni termine nel titolo | 10 |
| ogni termine negli alias | 6 |
| ogni termine nei tag | 5 |
| ogni occorrenza nel testo | 1, **al massimo 5** |

- il tetto sulle occorrenze impedisce a una pagina lunghissima di vincere solo perché ripete una parola;
- `normalize()` toglie maiuscole e accenti: "perche" trova "Perché";
- i pesi sono **costanti con un nome**: per cambiarli non cerchi numeri sparsi nel codice.

> [!example] Visto dal vivo
> Cercando `ransomware` nella wiki di cybersecurity, al primo posto c'è *"2023-10-01 CISA - Backup Guidance and Stop**Ransomware** Guide"* (41 punti), sopra la pagina *"Ransomware"* (30). Il confronto è sulla **sottostringa**: "stopransomware" contiene "ransomware", e quella pagina ha la parola anche molte volte nel testo. Semplice e spiegabile, ma non perfetto: un esercizio è dare un bonus al titolo **identico** alla ricerca.

Un piccolo miglioramento rispetto al codice originale: l'estratto preferisce le righe di **testo** ai titoli. Prima, sotto il risultato "Ransomware", l'estratto era *"# Ransomware"*: non aggiungeva nulla.

### 3.4 WikiController: link per il web e sicurezza

`WikiWebMarkdown.convert` trasforma la sintassi di Obsidian in Markdown standard, così il frontend non deve conoscerla:

```
[[CIA Triad]]             -> [CIA Triad](/wiki/page?path=concepts%2FCIA%20Triad.md)
![[pipeline-rag.svg]]     -> ![pipeline-rag.svg](/api/wiki/assets?name=pipeline-rag.svg)
[[Pagina che non esiste]] -> Pagina che non esiste   (testo semplice)
```

Le scelte di sicurezza:

- **`/page` cerca nell'indice in memoria, non sul disco**: `../../segreto.md` non è nell'indice e riceve 404 **senza mai toccare il file system**;
- **`/assets` accetta solo un nome di file** (niente `/` o `\`) con estensione `png, jpg, jpeg, gif, svg, webp, mp4, webm`, e lo cerca tra gli asset elencati all'avvio (senza link simbolici);
- **gli SVG possono contenere JavaScript.** Dentro un `<img>` non viene eseguito, ma se qualcuno apre l'indirizzo dell'immagine direttamente, il browser lo eseguirebbe *nel nostro sito*. Per questo la risposta ha:
  ```
  Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; sandbox
  X-Content-Type-Options: nosniff
  ```
  Questo punto non era nel prompt: è un esempio di cosa aggiunge una revisione attenta;
- gli errori sono sempre `{ "message": ... }`, **senza percorsi né stack trace**.

### 3.5 "Chiedi alla wiki": due strumenti, nessun database vettoriale

```java
@Tool(description = "Cerca nella wiki per parole chiave. Restituisce percorso, titolo ed estratto...")
public List<SearchHit> searchWiki(String query, ToolContext toolContext) { ... }

@Tool(description = "Legge il testo completo di una pagina della wiki. Usa il percorso restituito da searchWiki, oppure index.md...")
public String readWikiPage(String path, ToolContext toolContext) { ... }
```

- lavorano sull'**indice in memoria**, non sul disco;
- una pagina inesistente (o riservata) restituisce **un suggerimento** invece di un'eccezione: *"Pagina non trovata: usa searchWiki per trovare il percorso corretto."*. Così il modello può correggersi e riprovare;
- il testo della pagina torna dentro `<pagina_wiki>`, con `<` e `>` neutralizzati (lezione 5);
- il system prompt (in `WikiAskService`) riproduce le operazioni di *query* dell'idea di Karpathy: prima l'indice, poi la ricerca, poi le pagine, sempre citando le fonti;
- `WikiAskService` ha un **suo** `ChatClient`, senza memoria né RAG: solo i due strumenti. `ChatClient.Builder` è un bean *prototype*, quindi ogni service riceve il suo;
- il limite alle chiamate degli strumenti è quello globale della lezione 6: al massimo 5 per strumento e 10 in tutto.

#### Le pagine lette, registrate dal codice

Una nostra aggiunta: nel `ToolContext` mettiamo una lista che lo strumento riempie a ogni pagina letta.

```java
List<String> pagesRead = new CopyOnWriteArrayList<>();
chatClient.prompt()
        .user(question)
        .toolContext(Map.of(UserContext.KEY, user, WikiAskTools.PAGES_READ, pagesRead))
        .call()
        .content();
```

Sotto la risposta la pagina mostra **"Pagine lette dal modello"** come link. Perché è importante lo spiega il paragrafo 7.

## 4. Il frontend

```
frontend/src/
├── api/wikiApi.js                  tutte le chiamate (con API_BASE_URL)
├── components/WikiMarkdown.jsx     react-markdown + remark-gfm + remark-math + rehype-katex
└── pages/wiki/
    ├── WikiExplorer.jsx            cornice: titolo, schede, "Rileggi la wiki" (<Outlet />)
    ├── WikiSearchPage.jsx          /wiki        ricerca, oppure index.md se non cerchi niente
    ├── WikiPageView.jsx            /wiki/page   pagina + "Citata da" + "Collegamenti"
    └── WikiAskPage.jsx             /wiki/ask    Chiedi alla wiki
```

```bash
npm install remark-gfm remark-math rehype-katex katex
```

Il cuore è `WikiMarkdown`:

```jsx
// NIENTE rehype-raw: l'HTML eventualmente presente nelle pagine NON viene interpretato
<ReactMarkdown remarkPlugins={[remarkGfm, remarkMath]} rehypePlugins={[rehypeKatex]} components={...}>
```

- i link interni (`/wiki/...`) diventano `<Link>` di React Router: **niente ricaricamento** della pagina;
- i link esterni si aprono in una nuova scheda con `rel="noopener noreferrer nofollow"`;
- `![[video.mp4]]` diventa un `<video>`;
- **nelle risposte del modello niente immagini**: un'immagine con un indirizzo esterno potrebbe "esfiltrare" dati nella query string (lezione 8).

Altri dettagli utili:
- **rotte annidate**: `/wiki` ha `index`, `page` e `ask` come figli e la cornice usa `<Outlet />`;
- **la ricerca è nell'indirizzo** (`/wiki?q=ransomware`): il tasto "indietro" del browser funziona e la ricerca si può condividere;
- **debounce di 250 ms**: scrivendo `ransomware` il backend riceve **una** richiesta, non dieci;
- l'ultima risposta di "Chiedi" resta in `sessionStorage`: se apri una pagina citata e torni indietro, non la perdi.

> [!note] Due avvisi del linter, risolti
> `oxlint` segnalava *"Calling setState synchronously within an effect"* in due pagine: impostavamo lo stato "in caricamento" dentro `useEffect`. La soluzione è **ricavarlo**: i risultati ricordano a quale ricerca appartengono, e "ricerca in corso" diventa semplicemente `found.query !== query`.

## 5. Permessi per reparto anche nell'explorer

Nella lezione 11 abbiamo stabilito la regola: **ogni strada verso i dati deve passare dallo stesso controllo**. Il Wiki Explorer è una strada nuova, quindi:

| Dove | Cosa succede a una pagina `department: HR` per un utente di IT |
|---|---|
| `GET /api/wiki/search` | non compare nei risultati |
| `GET /api/wiki/page` | 404 "Pagina non trovata" (non riveliamo che esiste) |
| backlink e collegamenti | non compare negli elenchi |
| link nel testo di un'altra pagina | diventa testo semplice |
| `searchWiki` / `readWikiPage` | non compare / "Pagina non trovata" |

La regola è in un solo posto, `WikiPage.isVisibleTo(user)`, la stessa usata da `RagService` e da `WikiTools`.

## 6. I test

`WikiExplorerTest` (livello 1, 17 test, circa 0,2 secondi), oltre ai 20 di `WikiTest` della lezione 11 che coprono già il path traversal di `WikiVault`:

| Area | Cosa verifica |
|---|---|
| `WikiIndex` | link risolti per nome e percorso; **nome ambiguo non risolto**; backlink e link in uscita; titolo dal frontmatter |
| Ricerca | maiuscole e accenti; il titolo batte il testo ripetuto (**30 punti** esatti); alias; reparti |
| `WikiWebMarkdown` | wikilink → link con `%20`; incorporamenti → asset; pagina riservata → testo semplice |
| Endpoint | percorso inventato → 404; pagina HR → 404 per IT, visibile per HR; backlink; **asset**: solo nomi semplici ed estensioni ammesse, header `sandbox`, link simbolico escluso |
| Strumenti | `index.md` e pagina dal nome; pagine lette registrate; pagina inesistente o riservata → suggerimento |

> [!bug] I test sbagliati esistono
> Due test sono falliti la prima volta: si aspettavano **un solo** risultato per "perche" e "stipendi", ma `index.md` contiene quelle parole nei suoi collegamenti, quindi è un risultato corretto. Il codice era giusto, le aspettative no. Per questo il prompt dice *"leggi i test, non solo il risultato"*.

Risultato: `./mvnw test -DexcludedTestGroups=llm` → **75 test su 75** verdi.

## 7. Verifica dal vivo

Wiki di cybersecurity (144 pagine) in Docker, porta 3000:

| Prova | Risultato |
|---|---|
| Avvio | *"Wiki Explorer: 144 pagine caricate in memoria in 257 ms"* |
| Home della scheda | `index.md` con i collegamenti cliccabili |
| Ricerca `ransomware` | 20 risultati, una sola richiesta al backend (debounce) |
| Pagina *Ransomware* | tag, testo, **18 backlink** e 20 collegamenti; clic su *CIA Triad* → nuova pagina **senza ricaricare** |
| `../CLAUDE.md` / asset `../../etc/passwd.png` | 404 / 400 |
| *"Quali sono le difese principali contro il ransomware per una piccola azienda?"* | risposta in **9,8 s** dopo `searchWiki` + 2 pagine lette (la guida CISA e la Lezione 04) |

> [!warning] Il modello non ha seguito due regole del system prompt
> - **Regola 1** ("leggi prima index.md"): non l'ha fatto. Con una domanda così precisa è andato dritto alla ricerca, e la risposta era comunque buona.
> - **Regola 4** ("cita i percorsi tra parentesi quadre"): ha citato i **titoli** tra virgolette. Il frontend trasforma `[percorso.md]` in link, ma senza quel formato le citazioni restano testo.
>
> La lezione: **un prompt è una richiesta, non una garanzia.** Per questo l'elenco "Pagine lette dal modello" è registrato **dal codice** (nello strumento), non chiesto al modello: è affidabile anche quando il modello non segue le istruzioni.

Con la **wiki di esempio** (`llm-wiki-esempio/`) abbiamo provato anche:
- la **formula** della similarità del coseno nella pagina *Embedding*, resa da KaTeX;
- il **diagramma SVG** nella pagina *RAG*, servito con gli header di sicurezza;
- la pagina *Politica stipendi* (`department: HR`): invisibile nella ricerca e 404 per l'utente demo di IT.

## 8. La checklist di revisione

Applicata al codice di questa lezione:

| Controllo | Esito | Dove |
|---|---|---|
| Nessun endpoint scrive, sposta o cancella file | ✅ | `WikiController`: solo GET, più `reload` che rilegge |
| `../`, percorsi assoluti e link simbolici rifiutati, con un test | ✅ | `WikiVault` (lezione 11), `WikiTest`, `WikiExplorerTest` |
| Asset: solo nomi semplici ed estensioni previste | ✅ | `WikiWebMarkdown.MEDIA_FILE`, test sugli asset |
| Errori senza percorsi né stack trace | ✅ | `@ExceptionHandler` → `{ "message" }` |
| Limiti su ricerca (200), domande (1000) e strumenti (5/10) | ✅ | controller, `WikiAskRequest`, `application.properties` |
| Niente `dangerouslySetInnerHTML` né `rehype-raw` | ✅ | `WikiMarkdown.jsx` (verificato con `grep`) |
| Chiave API da variabile d'ambiente | ✅ | `OPENAI_API_KEY` |
| Frontmatter con `SafeConstructor` | ✅ | `MarkdownNote` (lezione 11) |
| Le risposte citano le pagine usate | ⚠️ | il modello non sempre usa il formato richiesto: le pagine lette sono registrate dal codice |
| I test passano e verificano davvero qualcosa | ✅ | 37 test sulla wiki; due aspettative corrette dopo averle lette |
| *(aggiunta)* SVG con `Content-Security-Policy: sandbox` | ✅ | `WikiController.asset` |
| *(aggiunta)* Permessi per reparto su ogni strada | ✅ | `WikiPage.isVisibleTo` |

## Esercizi

1. Fai la stessa domanda nella **Chat** (RAG, lezione 11) e in **Chiedi alla wiki**. Confronta risposta, fonti, tempo e numero di pagine lette. Quale preferisci e perché?
2. Dai un bonus di 20 punti alla pagina il cui titolo è **identico** alla ricerca. Poi aggiorna il test `ilTitoloPesaPiuDelTestoRipetuto`.
3. Rendi obbligatoria la regola 1 nel **codice**: prima di chiamare il modello, metti il contenuto di `index.md` nel messaggio. Quanto cambiano i token consumati (Actuator, lezione 9)?
4. Aggiungi alla pagina una sezione **"Pagine senza collegamenti"** (orfane: nessun backlink). È uno dei controlli del *lint* di una LLM Wiki.
5. Riscrivi il prompt del paragrafo 2 per un tuo progetto: un'app di studio con le tue lezioni come wiki. Quali vincoli di sicurezza aggiungeresti?

---
Lezione precedente: [[11-collegare-llm-wiki-al-rag]]
