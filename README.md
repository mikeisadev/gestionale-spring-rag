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

**Sviluppo e Docker insieme**: in sviluppo frontend e backend girano su 5173 e 8081 (Vite e VS Code), in Docker su 3000 e 8090. Anche il backend avviato da VS Code usa il MariaDB in Docker (`localhost:3307`): avvia almeno `docker compose up -d mariadb postgres ollama`.

> **Attenzione**: non rinominare la cartella `docker/` e non aggiungere `name:` al compose: i volumi (dati di pgvector, modello Ollama) sono legati al nome del progetto.
