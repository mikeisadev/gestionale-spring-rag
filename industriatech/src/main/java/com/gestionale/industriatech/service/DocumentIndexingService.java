package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.dto.DocumentUploadResponse;
import com.gestionale.industriatech.dto.IndexingJobResponse;
import com.gestionale.industriatech.model.IndexingJob;

import jakarta.annotation.PreDestroy;

/**
 * Esegue l'indicizzazione dei PDF IN BACKGROUND.
 *
 * Prima: la richiesta HTTP di upload aspettava la fine dell'indicizzazione
 *        -> con PDF grandi (o embedding lenti) scattava il timeout.
 * Ora:   l'upload crea un "job", risponde subito, e il frontend chiede
 *        periodicamente lo stato del job (polling).
 */
@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final DocumentService documentService;

    // Un solo thread: i PDF vengono indicizzati uno alla volta, in ordine di arrivo.
    // Il collo di bottiglia è il modello di embedding: indicizzare più PDF in
    // parallelo li farebbe solo competere per la stessa CPU.
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "indicizzazione-pdf")
    );

    // Job in memoria: id -> stato. ConcurrentHashMap perché vi accedono più thread.
    // NOTA: al riavvio del backend lo storico dei job si perde
    // (i chunk già salvati in pgvector invece restano).
    private final Map<String, IndexingJob> jobs = new ConcurrentHashMap<>();

    public DocumentIndexingService(DocumentService documentService) {
        this.documentService = documentService;
    }

    // Registra il job e lo mette in coda. Ritorna SUBITO, senza aspettare l'indicizzazione.
    public IndexingJobResponse submit(
            Path pdfPath,
            String storedFilename,
            String originalFilename
    ) {
        IndexingJob job = new IndexingJob(originalFilename);
        jobs.put(job.getId(), job);

        executor.submit(() -> runJob(job, pdfPath, storedFilename, originalFilename));

        log.info("Job " + job.getId() + " in coda per il PDF: " + originalFilename);
        return job.toResponse();
    }

    private void runJob(
            IndexingJob job,
            Path pdfPath,
            String storedFilename,
            String originalFilename
    ) {
        job.start();

        try {
            DocumentUploadResponse result =
                    documentService.indexPdf(pdfPath, storedFilename, originalFilename, job);

            job.complete(result.message());
            log.info("Job " + job.getId() + " completato: " + result.chunks() + " chunk indicizzati");

        } catch (IllegalArgumentException ex) {
            // Errori "previsti": PDF senza testo, nessun chunk...
            job.fail(ex.getMessage());
            log.warn("Job " + job.getId() + " fallito: " + ex.getMessage());

        } catch (Exception ex) {
            // Errori imprevisti: PDF corrotto, Ollama o database non raggiungibili...
            job.fail("Errore durante l'indicizzazione: " + ex.getMessage());
            log.error("Job " + job.getId() + " fallito", ex);
        }
    }

    public Optional<IndexingJobResponse> getJob(String id) {
        return Optional.ofNullable(jobs.get(id)).map(IndexingJob::toResponse);
    }

    // Tutti i job, dal più recente
    public List<IndexingJobResponse> listJobs() {
        return jobs.values().stream()
                .map(IndexingJob::toResponse)
                .sorted(Comparator.comparingLong(IndexingJobResponse::createdAt).reversed())
                .toList();
    }

    // Allo spegnimento (o al riavvio di devtools) fermiamo il thread di indicizzazione
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
