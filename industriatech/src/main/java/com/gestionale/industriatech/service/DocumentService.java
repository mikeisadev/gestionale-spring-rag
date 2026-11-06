package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.dto.DocumentUploadResponse;
import com.gestionale.industriatech.model.IndexingJob;

/**
 * Pipeline di indicizzazione dei documenti: il cuore della fase di "ingestion" del RAG.
 * Lezione 10: oltre ai PDF accetta DOCX, PPTX, XLSX, Markdown, TXT... (DocumentReaderService).
 *
 *   1. estrazione del testo                -> PdfProcessingService.extractText()
 *   2. chunking                            -> PdfProcessingService.splitDocuments()
 *   3. embedding e salvataggio su pgvector -> vectorStore.add(...) a blocchi
 *
 * Viene eseguita in background da DocumentIndexingService, che le passa
 * l'IndexingJob da aggiornare per mostrare l'avanzamento al frontend.
 *
 * Lezione 7: ogni fase scrive righe di log nello storico (pulsante "Vedi log").
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final PdfProcessingService pdfProcessingService;
    private final DocumentReaderService documentReaderService;
    private final VectorStore vectorStore;
    private final IndexingHistoryService history;

    // Quanti chunk inviare per ogni vectorStore.add(...)
    private final int batchSize;

    // Solo per i log: quale modello genera gli embedding e di quante dimensioni
    private final String embeddingModel;
    private final int embeddingDimensions;

    public DocumentService(
            PdfProcessingService pdfProcessingService,
            DocumentReaderService documentReaderService,
            VectorStore vectorStore,
            IndexingHistoryService history,
            @Value("${app.documents.indexing-batch-size:2}") int batchSize,
            @Value("${spring.ai.ollama.embedding.model:bge-m3}") String embeddingModel,
            @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}") int embeddingDimensions
    ) {
        this.pdfProcessingService = pdfProcessingService;
        this.documentReaderService = documentReaderService;
        this.vectorStore = vectorStore;
        this.history = history;
        this.batchSize = Math.max(1, batchSize);
        this.embeddingModel = embeddingModel;
        this.embeddingDimensions = embeddingDimensions;
    }

    public DocumentUploadResponse indexDocument(
            Path file,
            String storedFilename,
            String originalFilename,
            String documentId,
            int version,
            IndexingJob job
    ) {
        String id = job.getId();
        boolean hasPages = DocumentReaderService.hasPages(originalFilename);

        log.info(
            "\nDocumento ricevuto: " + originalFilename +
            "\nSalvato come: " + storedFilename +
            "\nPercorso: " + file.toAbsolutePath()
        );

        // ========== 1. LETTURA (nei PDF un Document per pagina) ==========
        job.setPhase("Lettura del documento");
        history.sync(job.toResponse());
        String reader = documentReaderService.readerName(file);
        history.log(id, IndexingHistoryService.INFO, "LETTURA", hasPages
                ? "Lettura del PDF con " + reader + " (un Document per ogni pagina)."
                : "Lettura del file con " + reader + " (formato ." + DocumentReaderService.extensionOf(originalFilename) + ").");

        long start = System.currentTimeMillis();
        List<Document> pages =
                pdfProcessingService.extractText(
                        file,
                        storedFilename,
                        originalFilename,
                        documentId,
                        version
                );

        if (pages.isEmpty()) {
            throw new IllegalArgumentException(hasPages
                    ? "Il PDF non contiene testo leggibile (è una scansione? Vedi la lezione 10, OCR)"
                    : "Il documento non contiene testo leggibile");
        }

        // Le "pagine" esistono solo nei PDF
        job.setPages(hasPages ? pages.size() : 0);
        int totalChars = pages.stream().mapToInt(p -> length(p.getText())).sum();
        history.log(id, IndexingHistoryService.INFO, "LETTURA", hasPages
                ? String.format(Locale.ITALIAN,
                        "%d pagine con testo lette in %s (%,d caratteri in totale). Le pagine vuote o solo immagini vengono scartate.",
                        pages.size(), millis(System.currentTimeMillis() - start), totalChars)
                : String.format(Locale.ITALIAN,
                        "%d sezioni di testo lette in %s (%,d caratteri in totale).",
                        pages.size(), millis(System.currentTimeMillis() - start), totalChars));

        // ========== 2. CHUNKING ==========
        job.setPhase("Suddivisione in chunk");
        history.sync(job.toResponse());
        history.log(id, IndexingHistoryService.INFO, "CHUNKING", String.format(Locale.ITALIAN,
                "TokenTextSplitter: chunkSize=%d token, minChunkSizeChars=%d, minChunkLengthToEmbed=%d, maxNumChunks=%d.",
                PdfProcessingService.CHUNK_SIZE, PdfProcessingService.MIN_CHUNK_SIZE_CHARS,
                PdfProcessingService.MIN_CHUNK_LENGTH_TO_EMBED, PdfProcessingService.MAX_NUM_CHUNKS));

        start = System.currentTimeMillis();
        List<Document> chunks =
                pdfProcessingService.splitDocuments(pages);

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessun chunk generato dal documento"
            );
        }

        job.setChunks(chunks.size());
        logChunkStatistics(id, chunks, System.currentTimeMillis() - start, hasPages);

        log.info(
            "\nPagine (o sezioni) con testo: " + pages.size() +
            "\nChunk generati: " + chunks.size()
        );

        // ========== 3. EMBEDDING E SALVATAGGIO SU POSTGRESQL + PGVECTOR ==========
        // Invece di un unico vectorStore.add(chunks) inviamo i chunk a piccoli blocchi:
        // così possiamo aggiornare l'avanzamento (e il log) dopo ogni blocco.
        job.setPhase("Generazione embedding e salvataggio");
        history.sync(job.toResponse());

        int totalBatches = (chunks.size() + batchSize - 1) / batchSize;
        history.log(id, IndexingHistoryService.INFO, "EMBEDDING", String.format(Locale.ITALIAN,
                "Modello di embedding %s (Ollama), vettori da %d dimensioni. %d chunk in %d blocchi da %d.",
                embeddingModel, embeddingDimensions, chunks.size(), totalBatches, batchSize));

        List<String> savedIds = new ArrayList<>();
        long embeddingStart = System.currentTimeMillis();
        int batchNumber = 0;

        try {
            for (int from = 0; from < chunks.size(); from += batchSize) {
                // Se il backend si sta spegnendo, interrompiamo il lavoro
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Indicizzazione interrotta");
                }

                int to = Math.min(from + batchSize, chunks.size());
                List<Document> batch = chunks.subList(from, to);
                batchNumber++;

                long batchStart = System.currentTimeMillis();

                // Il VectorStore genera gli embedding con l'EmbeddingModel e li salva
                vectorStore.add(batch);

                batch.forEach(chunk -> savedIds.add(chunk.getId()));
                job.addIndexedChunks(batch.size());
                history.sync(job.toResponse());
                history.log(id, IndexingHistoryService.INFO, "EMBEDDING", String.format(Locale.ITALIAN,
                        "Blocco %d/%d: chunk %d-%d salvati in %s.",
                        batchNumber, totalBatches, from + 1, to, millis(System.currentTimeMillis() - batchStart)));

                log.info("Chunk salvati: " + savedIds.size() + "/" + chunks.size());
            }
        } catch (RuntimeException ex) {
            // Se qualcosa va storto a metà, togliamo i chunk già salvati:
            // meglio nessun chunk che un documento indicizzato solo in parte.
            history.log(id, IndexingHistoryService.ERROR, "EMBEDDING", String.format(Locale.ITALIAN,
                    "Errore nel blocco %d/%d: %s", batchNumber, totalBatches, ex.getMessage()));
            if (!savedIds.isEmpty()) {
                log.warn("Errore durante il salvataggio: rimuovo " + savedIds.size() + " chunk parziali");
                vectorStore.delete(savedIds);
                history.log(id, IndexingHistoryService.WARN, "EMBEDDING", String.format(Locale.ITALIAN,
                        "Rimossi %d chunk parziali da pgvector: meglio nessun chunk che un documento a metà.",
                        savedIds.size()));
            }
            throw ex;
        }

        long embeddingMillis = System.currentTimeMillis() - embeddingStart;
        history.log(id, IndexingHistoryService.INFO, "EMBEDDING", String.format(Locale.ITALIAN,
                "%d chunk salvati in pgvector in %s (media %s per chunk).",
                chunks.size(), millis(embeddingMillis), millis(embeddingMillis / chunks.size())));

        // 4. Risposta
        return new DocumentUploadResponse(
                originalFilename,
                hasPages ? pages.size() : 0,
                chunks.size(),
                version > 1
                        ? "Versione " + version + " indicizzata: ha sostituito la precedente"
                        : "Documento indicizzato correttamente"
        );
    }

    // Statistiche utili per capire (e calibrare) il chunking
    private void logChunkStatistics(String id, List<Document> chunks, long elapsedMillis, boolean hasPages) {
        IntSummaryStatistics stats = chunks.stream().mapToInt(c -> length(c.getText())).summaryStatistics();

        history.log(id, IndexingHistoryService.INFO, "CHUNKING", String.format(Locale.ITALIAN,
                "%d chunk generati in %s. Lunghezza: minima %,d, media %,d, massima %,d caratteri.",
                chunks.size(), millis(elapsedMillis), stats.getMin(), Math.round(stats.getAverage()), stats.getMax()));

        // Le pagine divise in più chunk (le più "dense" di testo): solo per i PDF
        if (hasPages) {
            logDensestPages(id, chunks);
        }

        String preview = chunks.getFirst().getText().strip().replaceAll("\\s+", " ");
        history.log(id, IndexingHistoryService.INFO, "CHUNKING", "Inizio del primo chunk: \""
                + (preview.length() > 160 ? preview.substring(0, 160) + "…" : preview) + "\"");
    }

    private void logDensestPages(String id, List<Document> chunks) {
        Map<Object, Long> chunksPerPage = chunks.stream()
                .map(c -> c.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        String densest = chunksPerPage.entrySet().stream()
                .filter(e -> e.getValue() > 1)
                .sorted(Map.Entry.<Object, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> "pag. " + e.getKey() + " (" + e.getValue() + " chunk)")
                .collect(Collectors.joining(", "));

        history.log(id, IndexingHistoryService.INFO, "CHUNKING", densest.isEmpty()
                ? "Ogni pagina è diventata un solo chunk."
                : "Pagine divise in più chunk: " + densest + ".");
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }

    private static String millis(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        }
        double seconds = ms / 1000.0;
        return seconds < 60
                ? String.format(Locale.ITALIAN, "%.1f s", seconds)
                : String.format(Locale.ITALIAN, "%d min %d s", (long) seconds / 60, (long) seconds % 60);
    }
}
