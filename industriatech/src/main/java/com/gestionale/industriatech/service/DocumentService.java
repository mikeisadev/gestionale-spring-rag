package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.dto.DocumentUploadResponse;
import com.gestionale.industriatech.model.IndexingJob;

/**
 * Pipeline di indicizzazione dei PDF: il cuore della fase di "ingestion" del RAG.
 *
 *   1. estrazione del testo                -> PdfProcessingService.extractText()
 *   2. chunking                            -> PdfProcessingService.splitDocuments()
 *   3. embedding e salvataggio su pgvector -> vectorStore.add(...) a blocchi
 *
 * Viene eseguita in background da DocumentIndexingService, che le passa
 * l'IndexingJob da aggiornare per mostrare l'avanzamento al frontend.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final PdfProcessingService pdfProcessingService;
    private final VectorStore vectorStore;

    // Quanti chunk inviare per ogni vectorStore.add(...)
    private final int batchSize;

    public DocumentService(
            PdfProcessingService pdfProcessingService,
            VectorStore vectorStore,
            @Value("${app.documents.indexing-batch-size:2}") int batchSize
    ) {
        this.pdfProcessingService = pdfProcessingService;
        this.vectorStore = vectorStore;
        this.batchSize = Math.max(1, batchSize);
    }

    public DocumentUploadResponse indexPdf(
            Path pdfPath,
            String storedFilename,
            String originalFilename,
            IndexingJob job
    ) {
        log.info(
            "\nPDF ricevuto: " + originalFilename +
            "\nSalvato come: " + storedFilename +
            "\nPercorso: " + pdfPath.toAbsolutePath()
        );

        // 1. Estrazione del testo (un Document per pagina)
        job.setPhase("Lettura del PDF");

        List<Document> pages =
                pdfProcessingService.extractText(
                        pdfPath,
                        storedFilename,
                        originalFilename
                );

        if (pages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Il PDF non contiene testo leggibile"
            );
        }

        job.setPages(pages.size());

        // 2. Chunking
        job.setPhase("Suddivisione in chunk");

        List<Document> chunks =
                pdfProcessingService.splitDocuments(pages);

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessun chunk generato dal PDF"
            );
        }

        job.setChunks(chunks.size());

        log.info(
            "\nPagine con testo: " + pages.size() +
            "\nChunk generati: " + chunks.size()
        );

        // 3. Embedding e salvataggio su PostgreSQL + pgvector.
        // Invece di un unico vectorStore.add(chunks) inviamo i chunk a piccoli blocchi:
        // così possiamo aggiornare l'avanzamento dopo ogni blocco.
        job.setPhase("Generazione embedding e salvataggio");

        List<String> savedIds = new ArrayList<>();

        try {
            for (int start = 0; start < chunks.size(); start += batchSize) {
                // Se il backend si sta spegnendo, interrompiamo il lavoro
                if (Thread.currentThread().isInterrupted()) {
                    throw new IllegalStateException("Indicizzazione interrotta");
                }

                List<Document> batch =
                        chunks.subList(start, Math.min(start + batchSize, chunks.size()));

                // Il VectorStore genera gli embedding con l'EmbeddingModel e li salva
                vectorStore.add(batch);

                batch.forEach(chunk -> savedIds.add(chunk.getId()));
                job.addIndexedChunks(batch.size());

                log.info("Chunk salvati: " + savedIds.size() + "/" + chunks.size());
            }
        } catch (RuntimeException ex) {
            // Se qualcosa va storto a metà, togliamo i chunk già salvati:
            // meglio nessun chunk che un documento indicizzato solo in parte.
            if (!savedIds.isEmpty()) {
                log.warn("Errore durante il salvataggio: rimuovo " + savedIds.size() + " chunk parziali");
                vectorStore.delete(savedIds);
            }
            throw ex;
        }

        // 4. Risposta
        return new DocumentUploadResponse(
                originalFilename,
                pages.size(),
                chunks.size(),
                "Documento indicizzato correttamente"
        );
    }
}
