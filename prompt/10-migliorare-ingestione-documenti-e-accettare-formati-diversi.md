In questo frammento recuperato da una mia docenza espongo i vari metodi che posso usare per migliorare l'ingestione dei documenti.

Parlo anche della possibilità di aggiornare ed eliminare i documenti. Questa parte chiaramente è in relazione con il prompt "07-salvare-resoconto-chunking-e-embedding.md".

Parlo anche di overlap dei chunk, evitare di caricare file doppioni e, cosa molto importante, diamo la possibilità di caricare diversi formati di file con i reader di Spring AI.

## **11\) Migliorare l'ingestione dei documenti**

Nella L17 abbiamo detto che la qualità di un sistema RAG si decide nella fase di ingestione. Vediamo le evoluzioni più importanti per un progetto reale.

### **11.1) Aggiornare ed eliminare i documenti**

Nella pipeline della L17 ogni caricamento genera un nuovo UUID. Se carichi due volte lo stesso PDF, o una nuova versione del regolamento, nel database vettoriale finiscono due insiemi di chunk: il chatbot potrebbe rispondere usando la versione vecchia.

In un progetto reale ogni documento ha un **ID stabile** e un'**impronta del contenuto** (un hash):

1. salva l'elenco dei documenti caricati in una tabella del database principale (per esempio un'entity IndexedDocument con id, nome originale, hash, reparto e data di caricamento);  
2. a ogni caricamento calcola l'hash SHA-256 del file: se esiste già un documento con lo stesso hash, il file è identico e non serve reindicizzarlo;  
3. se carichi una nuova versione di un documento esistente, elimina prima i suoi vecchi chunk e poi indicizza quelli nuovi;  
4. salva l'ID del documento nei metadati di ogni chunk, con la chiave document\_id.

Calcolare l'hash di un file richiede poche righe:

| static String sha256(Path file) {     try {         MessageDigest digest \= MessageDigest.getInstance("SHA-256");         return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));     } catch (IOException \| NoSuchAlgorithmException e) {         throw new IllegalStateException("Impossibile calcolare l'hash del file", e);     } } |
| :---- |

Per eliminare tutti i chunk di un documento, VectorStore accetta un filtro sui metadati:

| public void deleteChunks(String documentId) {     vectorStore.delete(new FilterExpressionBuilder()             .eq("document\_id", documentId)             .build()); } |
| :---- |

Con queste due operazioni puoi aggiungere al gestionale anche un endpoint DELETE /api/documents/{id}, riservato agli amministratori, che elimina insieme il documento dalla tabella e i suoi chunk da pgvector.

### **11.2) L'overlap dei chunk**

Con il chunking della L17 i chunk sono indipendenti: può capitare che una frase importante venga spezzata a metà tra due blocchi. Con un **overlap** di, per esempio, 50 token, ogni chunk ripete l'ultima parte del precedente, così il significato non si perde al confine.

Come avevamo anticipato nella L17, TokenTextSplitter supporta l'overlap a partire da Spring AI 2.1 (al momento disponibile come release candidate). Con quella versione la configurazione diventa:

| TokenTextSplitter splitter \= TokenTextSplitter.builder()         .withChunkSize(500)         .withChunkOverlap(50)       // disponibile da Spring AI 2.1         .withMinChunkSizeChars(200)         .withMinChunkLengthToEmbed(20)         .withMaxNumChunks(10000)         .withKeepSeparator(true)         .build(); |
| :---- |

Con la versione 2.0.2 usata in questa lezione resta valido il codice della L17.

### **11.3) Più formati con i reader di Spring AI**

Spring AI offre un reader per ogni famiglia di formati:

| Formato | Reader | Dipendenza |
| :---- | :---- | :---- |
| PDF, un documento per pagina | PagePdfDocumentReader | spring-ai-pdf-document-reader |
| PDF con indice (segnalibri) | ParagraphPdfDocumentReader, un documento per ogni voce dell'indice | spring-ai-pdf-document-reader |
| DOCX, PPTX, XLSX, HTML e molti altri | TikaDocumentReader | spring-ai-tika-document-reader |
| Markdown | MarkdownDocumentReader | spring-ai-markdown-document-reader |
| Pagine HTML | JsoupDocumentReader | spring-ai-jsoup-document-reader |
| JSON e testo semplice | JsonReader e TextReader | Già inclusi in Spring AI |

Ad esempio, per un file Word basta cambiare reader nel nostro PdfProcessingService:

| TikaDocumentReader reader \= new TikaDocumentReader(new FileSystemResource(path)); List\<Document\> documents \= reader.read(); |
| :---- |

Tutti i reader restituiscono una List\<Document\>, quindi il resto della pipeline (chunking, embedding, salvataggio) non cambia.

### **11.4) PDF scansionati e documenti complessi**

Come abbiamo visto nella L17, i reader estraggono il testo solo dai PDF che contengono testo vero. Per i documenti scansionati e per quelli con impaginazioni complesse hai tre strade:

* **OCR**: un software che "legge" il testo dalle immagini, come Tesseract (open source). Apache Tika, la libreria usata da TikaDocumentReader, può usare Tesseract se è installato sul server;  
* **conversione in Markdown prima dell'ingestione**: strumenti come *markitdown* (già citato nella L17) o *Docling* trasformano PDF, Word e presentazioni in Markdown, conservando titoli e tabelle. Sono librerie Python: si usano in uno script separato e poi si indicizzano i file .md con MarkdownDocumentReader;  
* **revisione umana** dei documenti più importanti: nessuno strumento estrae perfettamente tabelle complesse, note a piè di pagina e moduli.

### **11.5) Arricchire i metadati**

Più metadati ha un chunk, più possibilità abbiamo di filtrarlo e ritrovarlo. Oltre a quelli che aggiungiamo noi (nome del file, pagina, reparto, ID del documento), Spring AI può generarne altri con un modello. Ad esempio KeywordMetadataEnricher chiede al modello alcune parole chiave per ogni chunk:

| KeywordMetadataEnricher enricher \= new KeywordMetadataEnricher(chatModel, 5); List\<Document\> enrichedChunks \= enricher.apply(chunks); |
| :---- |

**Attenzione al costo**: l'arricchimento fa una chiamata al modello di chat per ogni chunk. Su migliaia di chunk il costo si fa sentire, quindi usalo solo dove serve davvero.

### **11.6) Migliorare la ricerca**

Quando la ricerca non trova i chunk giusti, la risposta non può essere buona. Ecco le tecniche più usate, dalla più semplice alla più avanzata:

* **calibrare topK e similarityThreshold** con le domande d'oro del punto 10.6;  
* **riscrivere la domanda prima della ricerca**: una domanda colloquiale come *"e per i trasfertisti come funziona?"* viene trasformata da un modello in una domanda completa. In Spring AI se ne occupa RewriteQueryTransformer;  
* **generare più varianti della domanda** e cercare con tutte (MultiQueryExpander);  
* **ricerca ibrida**: combinare la ricerca vettoriale con la ricerca per parole chiave, utile per codici e nomi propri (per esempio *"modulo F24"*), che la ricerca semantica a volte non trova;  
* **reranking**: un secondo modello riordina i chunk trovati per pertinenza, prima di passarli all'LLM.

La riscrittura e le varianti della domanda (RewriteQueryTransformer e MultiQueryExpander) si integrano con RetrievalAugmentationAdvisor (punto 5.4): trovi gli esempi nella documentazione ufficiale di Spring AI, alla pagina dedicata al RAG.