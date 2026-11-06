---
titolo: "Lezione 7 – Storico dei file caricati e log di indicizzazione"
corso: Spring Boot, React e sistemi RAG
prompt: prompt/07-salvare-resoconto-chunking-e-embedding.md
data: 2026-11-06
livello: junior / medior
tags:
  - rag
  - osservabilità
  - logging
  - jpa
  - react
---

# Lezione 7 – Storico dei file caricati e log di indicizzazione

> [!abstract] In breve
> Nella pagina **Upload file per sistema RAG** ora ci sono due box:
> - **Caricamenti in corso**: come prima, con barra di avanzamento, pagine e chunk;
> - **Storico dei file caricati**: ogni documento con stato, pagine, chunk, dimensione, data, durata e il pulsante **"Vedi log"**, che mostra cosa è successo durante lettura, chunking ed embedding.
>
> Lo storico è salvato su **MariaDB**: sopravvive al riavvio del backend.

## 1. Perché serviva

Nella [[03b-indicizzazione-asincrona|lezione 3.1]] lo stato di un caricamento viveva solo **in memoria** (`IndexingJob` in una `ConcurrentHashMap`). Con questo approccio:
- riavviando il backend sparivano tutti i caricamenti;
- non c'era modo di sapere **cosa** era successo, per esempio quanti chunk per pagina, quanto è durato l'embedding o dove si è fermato un errore.

Per un sistema RAG questi dettagli contano: la qualità delle risposte dipende dalla qualità del chunking, e per migliorarlo bisogna poterlo **osservare**.

## 2. Due livelli di stato

| | `IndexingJob` (memoria) | `DocumentoIndicizzato` (database) |
|---|---|---|
| Dove | `ConcurrentHashMap` | MariaDB, tabella `documenti_indicizzati` |
| Serve a | Polling ogni 2 s del box "in corso" | Storico permanente |
| Dopo un riavvio | Sparisce | Resta |
| Log | No | Sì, tabella `log_indicizzazione` |

L'id è lo **stesso** per entrambi, il UUID del job. Quando un job finisce sparisce dal box "in corso" e la sua riga compare nello storico: il frontend aggiorna le due liste **insieme**, a ogni giro di polling.

```java
@Entity @Table(name = "documenti_indicizzati")
public class DocumentoIndicizzato {
    @Id private String id;                // = id del job
    private String nomeOriginale;         // manuale.pdf
    private String nomeSalvato;           // 550e8400-....pdf (= metadato "source" dei chunk)
    private Long dimensioneByte;
    private String stato;                 // IN_CODA, IN_ELABORAZIONE, COMPLETATO, ERRORE
    private int pagine, chunk, chunkIndicizzati;
    private LocalDateTime caricatoIl, iniziatoIl, terminatoIl;
}
```

## 3. Cosa c'è nei log

Ogni fase della pipeline scrive righe di log con **livello** (INFO, WARN, ERROR) e **fase** (UPLOAD, LETTURA, CHUNKING, EMBEDDING, FINE). Ecco un log reale:

```text
01:25:39 INFO  Upload     File "demo-storico.pdf" ricevuto (0,01 MB), salvato come 96ffea24-....pdf.
01:25:39 INFO  Upload     Indicizzazione avviata.
01:25:39 INFO  Lettura    Lettura del PDF con PagePdfDocumentReader (un Document per ogni pagina).
01:25:40 INFO  Lettura    3 pagine con testo lette in 280 ms (11.510 caratteri in totale).
01:25:40 INFO  Chunking   TokenTextSplitter: chunkSize=500 token, minChunkSizeChars=200, ...
01:25:40 INFO  Chunking   4 chunk generati in 108 ms. Lunghezza: minima 635, media 2.747, massima 3.589 caratteri.
01:25:40 INFO  Chunking   Pagine divise in più chunk: pag. 1 (2 chunk).
01:25:40 INFO  Chunking   Inizio del primo chunk: "Introduzione a Spring Boot Spring Boot e un framework..."
01:25:40 INFO  Embedding  Modello di embedding bge-m3 (Ollama), vettori da 1024 dimensioni. 4 chunk in 2 blocchi da 2.
01:25:45 INFO  Embedding  Blocco 1/2: chunk 1-2 salvati in 4,9 s.
01:25:54 INFO  Embedding  Blocco 2/2: chunk 3-4 salvati in 9,5 s.
01:25:54 INFO  Embedding  4 chunk salvati in pgvector in 14,5 s (media 3,6 s per chunk).
01:25:54 INFO  Fine       Documento indicizzato: 3 pagine, 4 chunk, durata totale 15 s.
```

> [!tip] Come leggere questi numeri
> - **Lunghezza dei chunk**: minima 635, massima 3.589 caratteri. La differenza è grande, ed è un indizio per la calibrazione del chunking (prompt 10: overlap e pulizia del testo).
> - **Pagine divise in più chunk**: sono le pagine più dense, quelle dove una frase può essere spezzata tra due chunk.
> - **Tempo per blocco**: mostra subito se il collo di bottiglia è l'embedding, come abbiamo scoperto nella lezione 3.1.

Per riportare nel log i **veri** parametri del chunking li abbiamo resi costanti pubbliche (`PdfProcessingService.CHUNK_SIZE` e simili), usate **sia** dallo splitter **sia** dal log: così non possono divergere.

> [!note] Un problema nel log non deve bloccare il lavoro
> `IndexingHistoryService.log()` cattura eventuali errori del database: se il salvataggio di una riga di log fallisce, l'**indicizzazione** continua. Il log è un aiuto, non una parte essenziale del lavoro.

## 4. I documenti caricati prima dello storico

All'avvio `DocumentHistoryBackfill` (un `ApplicationRunner`) guarda in **pgvector** quali documenti esistono, raggruppando i chunk per metadato `source`, e aggiunge allo storico quelli mancanti:

```sql
SELECT metadata->>'source', MIN(metadata->>'original_filename'),
       COUNT(*) AS chunks, COUNT(DISTINCT metadata->>'page_number') AS pages
FROM vector_store GROUP BY metadata->>'source';
```

Così nello storico sono comparsi anche `OWASP-Foundation-Strategic-Plan.pdf` (25 pagine, 41 chunk, 39,4 MB) e `manuale-spring.pdf`. Il loro log dice onestamente che i dettagli di quel caricamento **non sono disponibili**. L'operazione è **idempotente**: a ogni avvio aggiunge solo ciò che manca.

## 5. Interruzioni: tre casi diversi

Abbiamo provato a interrompere un'indicizzazione durante l'embedding in tutti i modi possibili:

| Caso | Cosa succede | Risultato nello storico |
|---|---|---|
| **Errore durante il lavoro** (Ollama giù, PDF corrotto) | Il `catch` registra l'errore e rimuove i chunk parziali | `ERRORE` con il motivo |
| **Spegnimento ordinato** (Ctrl+C, riavvio di devtools) | Spring chiude l'applicazione, il thread viene interrotto, il `catch` rimuove i chunk parziali | `ERRORE`: *"interrotta dallo spegnimento del backend"* |
| **Spegnimento brusco** (`kill -9`, crash, corrente) | Il processo muore all'istante, senza tempo per registrare nulla | Al riavvio successivo: `ERRORE`, *"interrotta dal riavvio del backend"*, chunk parziali rimossi |

> [!bug] Un messaggio che diceva la cosa sbagliata
> Al primo test lo spegnimento ordinato produceva il messaggio *"I/O error on POST request for http://localhost:11434/api/embed..."*, che sembra un guasto di Ollama. Spring infatti chiude **prima** il client HTTP e **poi** chiama il nostro `@PreDestroy`. La soluzione è ascoltare `ContextClosedEvent`, che arriva all'**inizio** della chiusura:
> ```java
> @EventListener(ContextClosedEvent.class)
> public void onShutdownStarted() { shuttingDown = true; }
> ```

> [!important] Il caso più subdolo: `kill -9`
> Con uno spegnimento brusco il database resta con `stato = IN_ELABORAZIONE` per sempre, e in pgvector potrebbero restare chunk di un documento **a metà**. All'avvio, `markInterruptedJobs()` (evento `ApplicationReadyEvent`) cerca questi casi, li segna come errore e cancella i chunk parziali con un filtro sui metadati:
> ```java
> vectorStore.delete(new FilterExpressionBuilder().eq("source", documento.getNomeSalvato()).build());
> ```
> È possibile perché ogni chunk ha nei metadati il nome del file salvato (`source`), che abbiamo aggiunto nella [[02-lettura-e-chunking-pdf|lezione 2]].

> [!note] Una piccola finestra
> Tomcat accetta richieste qualche centinaio di millisecondi **prima** di `ApplicationReadyEvent`: in quell'istante lo storico mostra ancora il vecchio stato. Nei test era di circa 240 ms.

## 6. Il frontend

```text
┌ Upload ─────────────────────────────────┐
│  Trascina qui il PDF...                  │
└──────────────────────────────────────────┘
┌ Caricamenti in corso ─────────────────── (solo se ce ne sono)
│  demo.pdf  Embedding · 3 pagine · 4 chunk  [In elaborazione] [Vedi log]
│  ████████░░░░░░░░  2/4 chunk (50%) · circa 9 s rimanenti
└──────────────────────────────────────────┘
┌ Storico dei file caricati ──────────── 3 documenti
│  demo.pdf     06 nov 01:25 · 5,2 KB · 3 pagine · 4 chunk · 15 s  [Completato] [Vedi log]
│  OWASP...pdf  05 nov 17:35 · 39,4 MB · 25 pagine · 41 chunk       [Completato] [Vedi log]
└──────────────────────────────────────────┘
```

- "Vedi log" apre una finestra (`IndexingLogModal`). Se il caricamento è ancora in corso, i log si aggiornano **in tempo reale**, ogni 2 secondi.
- La finestra si chiude con **Esc** o cliccando fuori, e scorre da sola fino all'ultima riga.
- Endpoint: `GET /api/documents/history` e `GET /api/documents/history/{id}/logs`.

## 7. Test eseguiti

| Prova | Risultato |
|---|---|
| Avvio con 2 documenti già indicizzati | Aggiunti allo storico con pagine e chunk ricavati da pgvector |
| Upload dal browser | Box "in corso" → log in tempo reale → riga nello storico con durata |
| PDF senza testo | Storico: `ERRORE – Il PDF non contiene testo leggibile` |
| Spegnimento ordinato durante l'embedding | `ERRORE – interrotta dallo spegnimento`, 0 chunk rimasti |
| `kill -9` durante l'embedding, poi riavvio | `ERRORE – interrotta dal riavvio`, chunk parziali rimossi |

## Verso la lezione 10

Lo storico diventerà la base per **aggiornare ed eliminare** i documenti e per **evitare i doppioni** con l'impronta (hash) del file. Per ora ogni caricamento crea una riga nuova, anche se il file è identico a uno già caricato.

## Domande di ripasso

1. Perché teniamo lo stato "vivo" in memoria e lo storico su database?
2. Quali informazioni del log aiutano a calibrare il chunking?
3. Cosa succede ai chunk già salvati se il backend viene spento durante l'embedding?
4. Perché un `kill -9` va gestito al riavvio successivo e non al momento dello spegnimento?
5. Perché abbiamo usato `ContextClosedEvent` invece di `@PreDestroy` per capire che è iniziato lo spegnimento?

---
Lezione precedente: [[06-strumenti-function-calling]] · Prossima lezione: [[08-guardrail-difese-a-strati]]
