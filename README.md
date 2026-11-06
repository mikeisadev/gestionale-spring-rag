# Spring Boot, React e sistemi RAG
Questa repository comprende un semplice progetto Java Spring Boot con React (ancora da implementare) per lo sviluppo di un gestionale a scopo didattico.

Più avanti nel corso, il gestionale includerà lo sviluppo di un sistema RAG collegato a uno o più LLM.

- **Docente**: Michele Mincone
- **Data**: 15/10/2026

## Tecnologie
- **Java 25**
- **Spring Boot v4.1.1 (*framework*)**
- **React (*ancora da implementare*)**

## Utilizzo AI
Inserisci nella cartella /prompt tutti i pezzi delle lezioni di questo corso (docenze Java e Agentic AI) che vuoi esplorare.

Se leggi i singoli file markdown, scoprirai che per ogni operazione otterai una lezione.

**NOTA**: potrai usare le lezioni generate in un LLM wiki con Obsidiain.

# Nota per avviare il frontend
Ricorda che se chiudi VS Code il dev server di Vite si interrompe e non potrai più vedere i cambiamenti che farai nel codice. 

Per riavviare esegui questi comandi:
1) `cd frontend` per entrare nella cartella frontend dove esiste vite
2) `npm run dev` per riavviare vite

## Ripasso protocollo HTTP, metodi e mapping Spring Boot
Stiamo costruendo il controller per sbloccare le operazioni CRUD

| **Azione**          | **Metodo HTTP** |   **Annotazione Spring Boot**       |
|---------------------|-----------------|-------------------------------------|
|   Create (CREA)     |    HTTP POST    |     @PostMapping      [OK]          |
|   Read  (LEGGI)     |    HTTP GET     |     @GetMapping       [OK]          |
|   Update (AGGIORNA) |    HTTP PUT     |     @PutMapping       [OK]          |
|   Delete (ELIMINA)  |    HTTP DELETE  |     @DeleteMapping    [OK]          |

# Sistema RAG – Lezioni generate
Le lezioni create a partire dai file della cartella `/prompt` si trovano nella cartella [`lezioni/`](lezioni/) e sono pronte da importare in Obsidian.

- [`lezioni/log-operazioni.json`](lezioni/log-operazioni.json): registro di ogni operazione eseguita
- [Lezione 1 – Caricare un PDF dal frontend](lezioni/01-upload-pdf-dal-frontend.md)
- [Lezione 2 – Leggere un PDF e dividerlo in chunk](lezioni/02-lettura-e-chunking-pdf.md)
- [Lezione 3 – Embedding, ricerca semantica e chat RAG](lezioni/03-embedding-e-ricerca-semantica.md)
- [Lezione 3.1 – Indicizzazione asincrona](lezioni/03b-indicizzazione-asincrona.md)
- [Lezione 4 – Tutto il progetto in Docker](lezioni/04-passaggio-docker.md)
- [Lezione 5 – Memoria della conversazione e chat salvate](lezioni/05-memoria-e-persistenza-conversazioni.md)
- [Lezione 6 – Gli strumenti: dal chatbot all'agente](lezioni/06-strumenti-function-calling.md)
- [Lezione 7 – Storico dei file caricati e log di indicizzazione](lezioni/07-storico-e-log-indicizzazione.md)
- [Lezione 8 – Guardrail: le difese a strati](lezioni/08-guardrail-difese-a-strati.md)
- [Lezione 9 – Tracce e test: sapere cosa succede](lezioni/09-osservabilita-e-test.md)

## Upload dei PDF (lezione 1)
| Cosa | Dove |
|---|---|
| Pagina React | `http://localhost:5173/upload-rag` (voce di menu "Upload file per sistema RAG") |
| Endpoint | `POST http://localhost:8081/api/documents` (multipart, campo `file`) |
| Cartella dei PDF | `documents/pdf/` (relativa alla cartella da cui avvii Spring Boot, esclusa da git) |
| Dimensione massima | 100 MB |

Prova rapida da terminale:

```bash
curl -F "file=@manuale.pdf" http://localhost:8081/api/documents
```

## Lettura e chunking dei PDF (lezione 2)
Dopo l'upload, `PdfProcessingService` legge il PDF pagina per pagina (`PagePdfDocumentReader`) e lo divide in chunk da circa 500 token (`TokenTextSplitter`). Nella console di Spring Boot vengono stampati, in via temporanea, pagine, chunk e metadati.

> **Nota**: con `withPagesPerDocument(1)` il numero di pagina si trova nel metadato `page_number` (non `start_page_number`).

## Embedding, ricerca semantica e chat RAG (lezione 3)
All'upload, ogni chunk viene trasformato in un vettore da **1024** valori (Ollama + `bge-m3`) e salvato in PostgreSQL/pgvector con `vectorStore.add(chunks)`. In chat, la domanda viene cercata per significato nei chunk e i 5 più pertinenti vengono passati all'LLM, che risponde in streaming (SSE) citando file e pagina.

| Endpoint | Scopo |
|---|---|
| `POST /api/chat` | Chat RAG in streaming (`text/event-stream`) |
| `POST /api/rag/search` | Test della sola ricerca semantica: `{"query": "...", "topK": 5}` |
| `GET /api/documents/test-embedding` | **Temporaneo**: mostra un embedding di prova |
| `GET /api/documents/jobs` | Stato di tutte le indicizzazioni (lezione 3.1) |
| `GET /api/documents/jobs/{id}` | Stato di una singola indicizzazione |

> **Attenzione**: se cambi modello di embedding, la colonna `vector_store.embedding` deve avere le stesse dimensioni del modello (es. `vector(1024)` per `bge-m3`). Spring AI non modifica una tabella già esistente.

## Indicizzazione asincrona (lezione 3.1)
L'upload (`POST /api/documents`) risponde subito con **202 Accepted** e un *job*; l'indicizzazione continua in background su un thread dedicato, un PDF alla volta. La pagina React interroga lo stato ogni 2 secondi e mostra fase, avanzamento e stima del tempo.

> **Nota sulle prestazioni**: con Ollama in Docker su macOS gli embedding usano solo la CPU (circa 20 secondi per chunk). Un PDF da 25 pagine (41 chunk) richiede circa 15 minuti. Lo stato dei job è in memoria: si perde al riavvio del backend.

# Avvio con Docker (lezione 4)
Tutta l'applicazione (database, backend, frontend, strumenti di amministrazione) gira in Docker.

1. Crea il file dei segreti e inserisci la tua chiave OpenAI:
   ```bash
   cp docker/.env.example docker/.env
   ```
2. Avvia tutto (la prima build richiede qualche minuto):
   ```bash
   cd docker && docker compose up -d --build
   ```
3. (Solo la prima volta, facoltativo) copia i dati dal MariaDB installato sul computer:
   ```bash
   ./docker/scripts/importa-dati-mariadb-locale.sh
   ```

| Servizio | Indirizzo | Credenziali |
|---|---|---|
| Gestionale (React) | http://localhost:3000 | – |
| Backend (API) | http://localhost:8090 | – |
| phpMyAdmin (MariaDB) | http://localhost:8082 | `industriatech` / `industriatech123` |
| pgAdmin (PostgreSQL) | http://localhost:5050 | `admin@example.com` / `admin123`, server *Chatbot PostgreSQL*: `chatbot123` |
| MariaDB | `localhost:3307` | `industriatech` / `industriatech123` |
| PostgreSQL + pgvector | `localhost:5433` | `chatbot` / `chatbot123` |
| Ollama | http://localhost:11434 | – |
| Mailpit (email di test) | http://localhost:8025 | – |
| Zipkin (tracce) | http://localhost:9411 | – |

**Sviluppo e Docker insieme**: in sviluppo frontend e backend girano su 5173 e 8081 (Vite e VS Code), in Docker su 3000 e 8090. Per modificare solo il frontend senza avviare il backend da VS Code: `cd frontend && npm run dev:docker` (Vite usa il backend Docker sulla 8090). Anche il backend avviato da VS Code usa il MariaDB in Docker (`localhost:3307`): avvia almeno `docker compose up -d mariadb postgres ollama`.

> **Attenzione**: non rinominare la cartella `docker/` e non aggiungere `name:` al compose: i volumi (dati di pgvector, modello Ollama) sono legati al nome del progetto.

## Memoria e chat salvate (lezione 5)
Il chatbot ricorda gli ultimi 10 messaggi di ogni conversazione (Spring AI `ChatMemory`, tabella `spring_ai_chat_memory` su PostgreSQL) e tutte le chat vengono archiviate su MariaDB (tabelle `conversazioni` e `messaggi_chat`). Nella pagina **Chat** puoi creare una nuova chat, riaprire quelle salvate (`/chat/<id>`) ed eliminarle.

| Endpoint | Scopo |
|---|---|
| `POST /api/chat` | `{"message": "...", "conversationId": "<uuid>"}`: risposta in streaming |
| `GET /api/chat/conversations` | Elenco delle chat |
| `GET /api/chat/conversations/{id}/messages` | Messaggi di una chat |
| `DELETE /api/chat/conversations/{id}` | Elimina una chat (archivio e memoria) |

## Strumenti dell'agente (lezione 6)
L'assistente può usare gli strumenti del gestionale:
- **dipendenti**: per reparto, per nome, elenco dei reparti. Chi non è admin vede solo il proprio reparto;
- **bozze email**: l'agente prepara la bozza, tu confermi l'invio dal pannello nella pagina Chat. Le email arrivano in Mailpit (http://localhost:8025);
- **ticket** tramite webhook di n8n (`N8N_API_KEY`).

| Proprietà | Significato |
|---|---|
| `app.demo-user.department` / `app.demo-user.admin` | Utente demo, finché non c'è il login (prompt 13) |
| `app.email.allowed-domain` | Unico dominio ammesso per le bozze email (`azienda.it`) |
| `app.n8n.base-url` / `N8N_API_KEY` | Il tuo n8n: workflow con nodo Webhook `POST /webhook/apri-ticket`, Header Auth `X-Api-Key` |
| `spring.ai.mcp.client.streamable-http.connections.n8n.*` | Client MCP verso n8n: **commentate**, attivale solo con n8n acceso |

## Storico dei file caricati (lezione 7)
Nella pagina di upload, sotto i caricamenti in corso, c'è lo **storico** di tutti i file indicizzati (tabelle `documenti_indicizzati` e `log_indicizzazione` su MariaDB). Il pulsante **"Vedi log"** mostra i dettagli di lettura, chunking ed embedding, anche in tempo reale durante l'indicizzazione.

| Endpoint | Scopo |
|---|---|
| `GET /api/documents/history` | Storico dei file caricati |
| `GET /api/documents/history/{id}/logs` | Log di un caricamento |

## Guardrail (lezione 8)
Il chatbot ha cinque strati di difesa: input (`InputGuard`: frasi d'attacco bloccate, IBAN e codice fiscale oscurati), contesto (tag `<documento>` e regole nel system prompt), strumenti (permessi e conferma umana), output (Markdown senza HTML né immagini, canarino nel system prompt) e consumo (max 800 token per risposta, 10 messaggi al minuto per utente).

| Proprietà | Significato |
|---|---|
| `spring.ai.openai.chat.max-tokens` | Lunghezza massima della risposta (800) |
| `app.guardrails.rate-limit.messages-per-minute` | Messaggi al minuto per utente (10), oltre: `429` |
| `app.guardrails.safeguard.enabled` | `SafeGuardAdvisor` di Spring AI, **spento**: controlla anche documenti e storico (vedi lezione 8) |

## Osservabilità e test (lezione 9)
- **Metriche**: `http://localhost:8081/actuator/metrics/gen_ai.client.token.usage` (in Docker: porta 8090)
- **Tracce**: Zipkin su http://localhost:9411 (container `industriatech-zipkin`)
- **Log dei prompt**: `SimpleLoggerAdvisor`, livello DEBUG solo in sviluppo

| Comando | Test eseguiti |
|---|---|
| `./mvnw test` | Solo unitari (veloci, gratuiti) |
| `./mvnw test -DexcludedTestGroups=llm` | + integrazione (servono database e Ollama) |
| `./mvnw test -DexcludedTestGroups=none` | Tutti, compresi qualità delle risposte e attacchi (consumano token OpenAI) |

## Ingestione dei documenti (lezione 10)
- **Formati**: PDF, DOCX, DOC, ODT, RTF, PPTX, ODP, XLSX, ODS, HTML, Markdown, TXT (reader di Spring AI: PDF, Markdown, Text e Apache Tika)
- **Doppioni**: un file con lo stesso contenuto (hash SHA-256) di uno già caricato viene rifiutato con 409
- **Versioni**: un file con lo stesso nome di un documento esistente ne diventa la nuova versione; i chunk della versione precedente vengono sostituiti solo se l'indicizzazione riesce
- **Eliminazione**: `DELETE /api/documents/{documentId}`, solo per amministratori (`app.demo-user.admin=true` in sviluppo, `APP_DEMO_USER_ADMIN=true` in `docker/.env`)

## LLM Wiki (lezione 11)
Le pagine Markdown di una **LLM Wiki** (vault di Obsidian) entrano nella base di conoscenza accanto ai documenti caricati, e il chatbot può aprire una pagina intera con lo strumento `readWikiPage`.

1. Prima crea la tua LLM Wiki (lezione L19), oppure usa quella di esempio: `llm-wiki-esempio/`
2. Imposta `WIKI_VAULT_PATH` con la cartella del vault:
   - VS Code: `cp .env.example .env` e inserisci il percorso
   - Docker: `cp docker/.env.example docker/.env` e inserisci **lo stesso** percorso (senza, Docker usa la wiki di esempio)
3. Dalla pagina "Upload file per sistema RAG" premi **Aggiorna indice della wiki** (oppure `POST /api/documents/wiki/reindex`). Si reindicizzano solo le pagine cambiate.

Senza `WIKI_VAULT_PATH` la wiki è disattivata e il gestionale funziona come prima.

## Wiki Explorer (lezione 12)
La scheda **Wiki Explorer** del menu (`/wiki`) è un "secondo IDE" della LLM Wiki, oltre a Obsidian:
- **Ricerca** per titolo, alias, tag e testo (senza database vettoriale); senza ricerca mostra `index.md`
- **Pagina**: Markdown con formule LaTeX, immagini e video, collegamenti cliccabili, backlink
- **Chiedi alla wiki**: il modello legge l'indice, cerca e apre le pagine con due strumenti in sola lettura (`searchWiki`, `readWikiPage`)

API: `GET /api/wiki/search?q=`, `GET /api/wiki/page?path=`, `GET /api/wiki/assets?name=`, `POST /api/wiki/reload`, `POST /api/wiki/ask`. Tutto in sola lettura; le pagine con `department` nel frontmatter sono visibili solo a quel reparto. Serve `WIKI_VAULT_PATH` (lezione 11).

