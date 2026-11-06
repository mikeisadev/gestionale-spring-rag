---
titolo: "Lezione 10 – Ingestione dei documenti: versioni, doppioni e nuovi formati"
corso: Spring Boot, React e sistemi RAG
prompt: prompt/10-migliorare-ingestione-documenti-e-accettare-formati-diversi.md
data: 2026-11-06
livello: junior / medior
tags:
  - rag
  - ingestione
  - sha-256
  - versioni
  - tika
  - markdown
  - spring-ai
---

# Lezione 10 – Ingestione dei documenti: versioni, doppioni e nuovi formati

> [!abstract] In breve
> La qualità di un RAG si decide quando i documenti **entrano**. Fino a ieri:
> - caricando due volte lo stesso PDF, finivano in pgvector due copie dei chunk;
> - caricando una nuova versione di un regolamento, il chatbot poteva rispondere con quella vecchia;
> - non si poteva eliminare un documento;
> - si potevano caricare solo PDF.
>
> Ora ogni documento ha un **ID stabile** e un'**impronta del contenuto** (hash SHA-256), le versioni nuove sostituiscono quelle vecchie, gli amministratori possono eliminare un documento e si caricano anche **Word, PowerPoint, Excel, Markdown, HTML e testo**.

Collegamenti: [[07-storico-e-log-indicizzazione]] (lo storico che qui diventa un catalogo), [[09-osservabilita-e-test]] (i livelli dei test).

## 0. Prima di iniziare: devo avviare il backend a mano?

Domanda dallo studente: *"È normale che devo avviare il backend manualmente? Non doveva essere tutto in Docker?"*

Sì, è tutto in Docker. Ci sono però **due modi** di usare il gestionale, e ognuno ha le sue porte:

| | Frontend | Backend | Quando si usa |
|---|---|---|---|
| **Docker** | http://localhost:3000 | http://localhost:8090 | Per *usare* l'app: si avvia tutto con `docker compose up -d` |
| **Sviluppo** | http://localhost:5173 (Vite) | http://localhost:8081 (VS Code) | Per *modificare* il codice: le modifiche si vedono subito |

Il frontend di Vite (5173) parla con il backend di VS Code (8081). Se apri la 5173 e il backend di VS Code è spento, non funziona niente, anche se Docker è acceso.

> [!tip] Sviluppare solo il frontend
> Se stai modificando solo React, non serve avviare il backend da VS Code: usa quello di Docker.
> ```bash
> cd frontend && npm run dev:docker
> ```
> Lo script imposta `VITE_API_URL=http://localhost:8090`. Ricorda però che il backend in Docker è un'**immagine**: le modifiche al codice Java arrivano solo dopo `docker compose up -d --build backend`.

### Un bug trovato strada facendo: 403 su ogni POST in Docker

Provando l'upload dal browser sulla porta 3000, il backend rispondeva **403**. Con `curl` invece funzionava. Perché?

- Il browser, nelle richieste POST e DELETE, aggiunge l'intestazione `Origin: http://localhost:3000`. `curl` non la manda.
- nginx inoltrava la richiesta al backend con `proxy_set_header Host $host`. **`$host` non contiene la porta**: il backend riceveva `Host: localhost`.
- Spring confronta `Origin` con `Host`: `localhost:3000` ≠ `localhost`. Considera la richiesta "da un altro sito", e quel sito non è nella lista di `@CrossOrigin`. Risposta: **403 Invalid CORS request**.

La correzione in `frontend/nginx.conf`:

```nginx
proxy_set_header Host $http_host;   # "localhost:3000", CON la porta
```

Il bug c'era dalla lezione 4 e bloccava anche la chat in Docker. Nella lezione 9 le prove in Docker erano state fatte con `curl`, che non manda `Origin`, per questo non l'avevamo visto.

> [!warning] Lezione da ricordare
> **Testa come l'utente.** Un test con `curl` non è un test del browser: CORS, cookie e intestazioni come `Origin` esistono solo lì.

## 1. Il problema: doppioni e versioni vecchie

Nella pipeline delle lezioni precedenti ogni caricamento genera un nuovo nome (UUID) e nuovi chunk. Pgvector non sa che due file sono "lo stesso documento":

```
regolamento-ferie.pdf (gennaio)  -> 12 chunk  "26 giorni di ferie"
regolamento-ferie.pdf (giugno)   -> 12 chunk  "28 giorni di ferie"
```

Alla domanda *"Quanti giorni di ferie ho?"* la ricerca trova chunk di **entrambe** le versioni, e il modello può rispondere con quella sbagliata.

## 2. ID stabile e impronta del contenuto

### 2.1 Tre nuove colonne

La tabella `documenti_indicizzati` (lezione 7) aveva **una riga per caricamento**. Ora più caricamenti possono essere **versioni dello stesso documento**:

| Colonna | Esempio | A cosa serve |
|---|---|---|
| `documento_id` | `c2d6974e-…` | **ID stabile**: uguale per tutte le versioni |
| `versione` | `1`, `2`, `3`… | Quale versione è |
| `hash_sha256` | `6c0df7af…` (64 caratteri) | **Impronta** del contenuto |

```
id (caricamento)  documento_id  versione  stato
a1b2…             c2d6974e…     1         SOSTITUITO
c3d4…             c2d6974e…     2         COMPLETATO   <- quella usata dal chatbot
```

L'ID stabile finisce anche nei **metadati di ogni chunk**, con la chiave `document_id`: è il "filo" che lega MariaDB a pgvector.

```java
metadata.put("source", storedFilename);
metadata.put("original_filename", originalFilename);
metadata.put("document_id", documentId);   // lezione 10
metadata.put("version", version);          // lezione 10
```

### 2.2 L'hash SHA-256

Un **hash** è un'impronta digitale del file: 64 caratteri calcolati dai byte del contenuto.
- Due file identici hanno **sempre** lo stesso hash, anche se hanno nomi diversi.
- Basta cambiare una virgola e l'hash cambia completamente.

La lezione originale usa `Files.readAllBytes(file)`. Con file fino a 100 MB è meglio leggere **a blocchi**, così non carichiamo tutto in memoria:

```java
public static String sha256(InputStream input) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (DigestInputStream in = new DigestInputStream(input, digest)) {
        in.transferTo(OutputStream.nullOutputStream()); // leggiamo e buttiamo: ci serve solo l'hash
    }
    return HexFormat.of().formatHex(digest.digest());
}
```

Il controller calcola l'hash **prima** di salvare il file:

```java
String hash = DocumentCatalogService.sha256(file.getInputStream());
```

> [!example] Un valore da verificare
> L'hash SHA-256 del testo `abc` è sempre `ba7816bf8f01cfea…20015ad`: è il primo test di `DocumentIngestionTest`.

### 2.3 Le regole del catalogo

`DocumentCatalogService.plan()` decide cosa fare con ogni nuovo file, in quest'ordine:

| Situazione | Esito |
|---|---|
| Stesso **contenuto** di un documento già caricato (anche con un altro nome) | **409 Conflict**: "Questo file è già nella base di conoscenza" |
| Stesso **nome** di un documento ancora in elaborazione | **409 Conflict**: "attendi che finisca" |
| Stesso **nome** di un documento completato, contenuto diverso | **Nuova versione** dello stesso documento |
| Altrimenti | **Nuovo documento**, versione 1 |

> [!note] Una convenzione semplice
> "Stesso nome = stesso documento" va bene per il corso. In un gestionale reale l'utente sceglierebbe esplicitamente "sostituisci il documento X", perché due file diversi possono chiamarsi entrambi `verbale.pdf`.

**Perché `synchronized`?** Controllare i doppioni e creare la riga nello storico devono avvenire *insieme*. Senza, due upload identici arrivati nello stesso istante passerebbero entrambi il controllo (*race condition*):

```java
public synchronized IndexingJobResponse submit(...) {
    UploadPlan plan = catalog.plan(hash, originalFilename); // controlla
    ...
    history.create(..., hash, plan.documentId(), plan.version()); // registra
```

## 3. Nuova versione: prima indicizzo, poi elimino

La lezione originale dice: *"elimina prima i suoi vecchi chunk e poi indicizza quelli nuovi"*. Noi facciamo il contrario:

1. indicizziamo la nuova versione (i suoi chunk hanno `source` = nuovo file);
2. **solo se è andata bene**, eliminiamo i chunk della vecchia:

```java
FilterExpressionBuilder b = new FilterExpressionBuilder();
vectorStore.delete(b.and(
        b.eq("document_id", documentId),      // i chunk di questo documento...
        b.ne("source", newStoredFilename))    // ...tranne quelli del file appena indicizzato
        .build());
```

| | Prima elimino, poi indicizzo | Prima indicizzo, poi elimino (la nostra scelta) |
|---|---|---|
| L'indicizzazione fallisce (Ollama spento, file corrotto) | Il documento **sparisce** | La versione vecchia **resta** |
| Durante l'indicizzazione (anche minuti) | Il chatbot non trova il documento | Il chatbot usa la versione vecchia |
| Svantaggio | – | Per qualche secondo esistono entrambe le versioni |

La riga della versione vecchia diventa `SOSTITUITO` e il suo file viene eliminato dal disco. Nel log del caricamento compare la fase **Versioni**:

```
INFO  Upload    Esiste già un documento con questo nome: questo file è la VERSIONE 2.
                I chunk della versione 1 verranno sostituiti solo se l'indicizzazione riesce.
INFO  Lettura   Lettura del file con TikaDocumentReader (formato .docx).
...
INFO  Versioni  Versione 1 sostituita: rimossi da pgvector i suoi 1 chunk ed eliminato il suo file.
```

Anche i file che **non** vengono indicizzati (errore, interruzione) ora vengono tolti dal disco: non servono a nessuno e occupano spazio.

## 4. Eliminare un documento (solo amministratori)

```
DELETE /api/documents/{documentId}
```

Elimina **tutte le versioni** del documento: i chunk in pgvector, i file su disco, le righe dello storico e i loro log.

```java
@DeleteMapping("/{documentId}")
public Map<String, String> deleteDocument(@PathVariable String documentId) {
    if (!currentUserProvider.currentUser().admin()) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo un amministratore può eliminare i documenti.");
    }
    ...
}
```

| Risposta | Quando |
|---|---|
| 200 | `{ "message": "\"regolamento.docx\" eliminato dalla base di conoscenza." }` |
| 403 | L'utente non è amministratore |
| 404 | Documento inesistente |
| 409 | Il documento è in elaborazione |

Il frontend chiede chi è l'utente con il nuovo `GET /api/me` e mostra il pulsante **Elimina** solo agli amministratori, con una conferma prima di procedere.

> [!danger] Nascondere un pulsante non è una protezione
> Chiunque può chiamare `DELETE /api/documents/…` con `curl`, senza passare dalla pagina. Il controllo vero è quello nel **backend** (il 403). Il pulsante nascosto serve solo a non confondere l'utente.

> [!tip] Provare l'eliminazione
> L'utente demo non è amministratore. In sviluppo metti `app.demo-user.admin=true` in `application.properties`; in Docker metti `APP_DEMO_USER_ADMIN=true` in `docker/.env` e riavvia il backend. Dal prompt 13 il ruolo arriverà dal login.

## 5. Più formati con i reader di Spring AI

Tutti i reader restituiscono una `List<Document>`: il resto della pipeline (chunking, embedding, salvataggio) **non cambia**. Basta scegliere il reader giusto, in `DocumentReaderService`:

```java
return switch (extensionOf(file.getFileName().toString())) {
    case "pdf" -> new PagePdfDocumentReader(resource, configUnaPaginaPerDocumento);
    case "md"  -> new MarkdownDocumentReader(resource, configMarkdown);
    case "txt" -> new TextReader(resource);
    default    -> new TikaDocumentReader(resource); // DOCX, PPTX, XLSX, ODT, RTF, HTML...
};
```

| Formato | Reader | Cosa produce |
|---|---|---|
| `.pdf` | `PagePdfDocumentReader` | Un `Document` per pagina, con `page_number` |
| `.md` | `MarkdownDocumentReader` | Un `Document` per titolo, con il metadato `title` |
| `.txt` | `TextReader` | Un unico `Document` |
| `.docx .doc .odt .rtf .pptx .odp .xlsx .ods .html .htm` | `TikaDocumentReader` | Un unico `Document` con tutto il testo |

Due nuove dipendenze nel `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-tika-document-reader</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-markdown-document-reader</artifactId>
</dependency>
```

> [!info] Apache Tika
> Tika è una libreria Apache che **riconosce da sola** il formato di un file e ne estrae il testo (usa Apache POI per Office, e altre librerie per il resto). È comoda ma pesante: aggiunge parecchi MB all'immagine Docker del backend.

### 5.1 I dettagli che cambiano

- **Lista bianca delle estensioni**: il controller accetta solo i formati della lista (`.exe` → 400). Conta l'**ultima** estensione: `manuale.pdf.exe` è rifiutato.
- **Il nome salvato** è `UUID + estensione` (es. `a51d4399-….docx`): il nome originale non diventa mai un percorso.
- **Niente pagine fuori dai PDF**: il tag del contesto diventa `<documento fonte="regolamento.docx">`, senza `pagina="null"`. Anche la regola 8 del system prompt ora dice *"indica il file e, se c'è, la pagina"*.
- **Tika e `TextReader` scrivono già `source`** nei metadati (il nome del file): lo sovrascriviamo con il nostro nome salvato, come per i PDF.
- **Via le stampe TEMPORANEE** della lezione 2: stampavano in console ogni pagina e ogni chunk. Con il PDF di 462 pagine caricato in questi giorni erano migliaia di righe; ora i dati utili sono nel log dello storico.

> [!warning] Sicurezza: i file sono input non fidato
> Un file caricato è un input dell'utente, come il testo della chat. Tika ha protezioni contro i file "bomba" (es. archivi che si espandono a GB), ma in produzione si aggiungono limiti di tempo e memoria al parsing, e un antivirus sugli upload.

## 6. L'overlap dei chunk (Spring AI 2.1)

Con il nostro chunking i chunk sono indipendenti: una frase importante può essere spezzata tra due blocchi. Con un **overlap** di, per esempio, 50 token, ogni chunk ripete la fine del precedente:

```
chunk 1: [............................ fine del chunk 1]
chunk 2:                     [fine del chunk 1 ........................]
                              ^^^^^^^^^^^^^^^^^ 50 token ripetuti
```

`TokenTextSplitter.withChunkOverlap(50)` arriva con **Spring AI 2.1**. Oggi (novembre 2026) su Maven Central la 2.1 è ancora una release candidate (`2.1.0-RC2`): non la usiamo in un progetto didattico che deve funzionare a lungo. Abbiamo invece aggiornato il BOM alla **2.0.2**, la versione della lezione, e lasciato il codice pronto in un commento:

```java
TokenTextSplitter.builder()
        .withChunkSize(500)
        ...
        // .withChunkOverlap(50)   // da Spring AI 2.1
        .build();
```

> [!question] Overlap: sempre?
> L'overlap aumenta il numero di chunk (e quindi gli embedding da calcolare e lo spazio in pgvector). Con 50 token su 500 sono circa il 10% in più. Ne vale la pena soprattutto per testi lunghi e continui (regolamenti, contratti), meno per FAQ già divise in paragrafi brevi.

## 7. PDF scansionati e documenti complessi

I reader estraggono solo il testo **vero**. Un PDF scansionato è una serie di immagini: per i reader è vuoto, e il caricamento finisce con *"Il PDF non contiene testo leggibile (è una scansione?)"*. Le strade:

- **OCR** (riconoscimento del testo nelle immagini), ad esempio Tesseract: Tika lo usa se è installato sul server;
- **conversione in Markdown prima dell'ingestione** con *markitdown* o *Docling* (librerie Python): conservano titoli e tabelle, poi si carica il `.md`, che ora accettiamo;
- **revisione umana** dei documenti più importanti: nessuno strumento estrae perfettamente tabelle complesse e moduli.

## 8. Arricchire i metadati

`KeywordMetadataEnricher` chiede al modello di chat alcune parole chiave per ogni chunk:

```java
KeywordMetadataEnricher enricher = new KeywordMetadataEnricher(chatModel, 5);
List<Document> enrichedChunks = enricher.apply(chunks);
```

**Non l'abbiamo attivato**, per una ragione di costi: fa **una chiamata al modello per ogni chunk**. Il PDF della guida OWASP caricato in questi giorni ha 925 chunk: sarebbero 925 chiamate a OpenAI per un solo file. Si usa dove serve davvero, ad esempio su pochi documenti molto consultati.

## 9. Migliorare la ricerca (teoria)

Quando la ricerca non trova i chunk giusti, la risposta non può essere buona. Le tecniche, dalla più semplice:

1. **calibrare `topK` e `similarityThreshold`** con le domande d'oro ([[09-osservabilita-e-test]], livello 2);
2. **riscrivere la domanda** prima della ricerca (`RewriteQueryTransformer`);
3. **generare più varianti** della domanda (`MultiQueryExpander`);
4. **ricerca ibrida**: vettoriale + per parole chiave, utile per codici e nomi propri (es. *"modulo F24"*);
5. **reranking**: un secondo modello riordina i chunk trovati.

> [!example] Un problema che il nostro chatbot ha davvero
> Dalla lezione 5 il chatbot ha la memoria, ma la **ricerca** usa solo l'ultima domanda. Dopo *"Quanto è il rimborso chilometrico?"*, la domanda *"E la diaria?"* viene cercata così com'è, senza contesto. Un `CompressionQueryTransformer` (o `RewriteQueryTransformer`) la trasformerebbe in *"Quanto è la diaria per le trasferte?"* prima della ricerca, al costo di una chiamata al modello in più.

## 10. I documenti caricati prima di questa lezione

I tre documenti già presenti non avevano ID stabile, versione e hash, e i loro chunk non avevano `document_id`. All'avvio, `DocumentHistoryBackfill` li aggiorna **una volta sola**:

```sql
UPDATE vector_store
SET metadata = (metadata::jsonb || jsonb_build_object('document_id', ?::text, 'version', 1))::json
WHERE metadata->>'source' = ?
  AND metadata->>'document_id' IS NULL
```

- la colonna `metadata` di pgvector è di tipo `json`: la convertiamo in `jsonb` per usare l'operatore `||` (unione di due oggetti JSON), poi torniamo a `json`;
- `AND metadata->>'document_id' IS NULL` rende l'aggiornamento **idempotente**: al secondo avvio non tocca niente.

Risultato: `manuale-spring.pdf` (4 chunk), `OWASP-Foundation-Strategic-Plan.pdf` (41) e `wstg-v4.2.pdf` (925) hanno tutti il loro `document_id`. L'hash manca solo per `manuale-spring.pdf`, il cui file non è più su disco.

## 11. I test

| Livello | Classe | Cosa verifica |
|---|---|---|
| 1 | `DocumentIngestionTest` (12 test, 1,6 s) | hash, estensioni, reader giusto per formato, Word letto da Tika, Markdown diviso per titoli, metadati `document_id`/`version`, tag senza pagina, regole del catalogo (con un repository *mock*) |
| 2 | `DocumentLifecycleTest` (integrazione, 7–14 s) | il ciclo completo su database veri e Ollama: upload → doppione rifiutato → versione 2 che sostituisce la 1 → eliminazione |

Il test di Word crea il file al volo con Apache POI (arriva insieme a Tika):

```java
try (XWPFDocument word = new XWPFDocument(); OutputStream out = Files.newOutputStream(docx)) {
    word.createParagraph().createRun().setText("Ogni dipendente ha diritto a 26 giorni di ferie.");
    word.write(out);
}
assertThat(readers.read(docx).getFirst().getText()).contains("26 giorni di ferie");
```

Risultato di `./mvnw test -DexcludedTestGroups=none`: **46 test su 46** verdi, compresi qualità delle risposte (PERTINENTE) e attacchi (7/7).

## 12. Verifica dal vivo (Docker, porta 3000)

1. Caricato `regolamento-trasferte.docx` (rimborso 0,42 €/km) → `TikaDocumentReader`, 1 chunk, versione 1.
2. Caricato `faq-ferie.md` dalla pagina → 2 chunk (uno per titolo).
3. Ricaricato lo stesso Markdown con il nome `FAQ-ferie (copia).md` → messaggio *"Questo file è già nella base di conoscenza: "faq-ferie.md"…"*.
4. Caricato un nuovo `regolamento-trasferte.docx` (0,45 €/km) → badge **v2**, la versione 1 grigia *"Sostituita dalla versione 2"*, in pgvector solo il chunk nuovo.
5. Domanda in chat: *"Quanto è il rimborso chilometrico per l'auto propria?"* → *"…0,45 euro al chilometro (Fonte: regolamento-trasferte.docx)."*: versione giusta, fonte senza pagina.
6. Eliminati entrambi i documenti dal pulsante **Elimina** → spariti righe, log, file e chunk. Con l'utente non amministratore: nessun pulsante, e `DELETE` risponde 403.
7. Caricato un `.exe` → 400 "Formato non supportato".

## 13. File toccati

| File | Modifica |
|---|---|
| `pom.xml` | Spring AI 2.0.2, reader Tika e Markdown |
| `service/DocumentReaderService.java` | **Nuovo**: reader per formato, lista delle estensioni |
| `service/DocumentCatalogService.java` | **Nuovo**: hash, regole doppioni/versioni, sostituzione, eliminazione |
| `service/DocumentConflictException.java` | **Nuovo**: diventa 409 |
| `controller/CurrentUserController.java` | **Nuovo**: `GET /api/me` |
| `controller/DocumentController.java` | Formati, hash, 409, `DELETE /{documentId}` |
| `service/DocumentIndexingService.java` | `submit` sincronizzato, sostituzione versione, pulizia file falliti |
| `service/DocumentService.java` | `indexDocument` per tutti i formati |
| `service/PdfProcessingService.java` | Usa i reader, metadati `document_id`/`version`, via le stampe temporanee |
| `service/DocumentHistoryBackfill.java` | Migrazione dei documenti esistenti |
| `service/RagService.java` | Attributo `pagina` solo se esiste |
| `entity/DocumentoIndicizzato.java` + repository | `documentoId`, `versione`, `hashSha256`, nuove query |
| `prompts/system-prompt.st` | Regola 8: la pagina "se c'è" |
| `frontend/src/pages/UploadRag.jsx` | Formati, icone, badge versione, "Sostituito", Elimina (admin), messaggi 409 |
| `frontend/nginx.conf` | `Host $http_host` (bug 403) |
| `frontend/package.json` | Script `dev:docker` |
| `docker/docker-compose.yml`, `docker/.env.example` | `APP_DEMO_USER_ADMIN` |

## Esercizi

1. Carica un file `.pptx` e uno `.xlsx`. Apri "Vedi log": quanti caratteri ha estratto Tika? Le tabelle di Excel sono leggibili nel primo chunk?
2. Rinomina un PDF già caricato e ricaricalo. Che messaggio ricevi? Perché il nome diverso non basta?
3. Cambia l'ordine in `retirePreviousVersions`: prima elimina, poi indicizza. Spegni Ollama durante il caricamento della versione 2: cosa resta in pgvector?
4. Con `app.demo-user.admin=false`, prova `curl -X DELETE http://localhost:8081/api/documents/<documentId>`. Poi spiega perché il pulsante nascosto non basta.
5. (Avanzato) Aggiungi una colonna `reparto` al documento e un filtro `reparto == 'IT'` nella ricerca: è il primo passo verso documenti visibili solo a certi reparti (prompt 13).

---
Lezione precedente: [[09-osservabilita-e-test]] · Prossima lezione: [[11-collegare-llm-wiki-al-rag]]
