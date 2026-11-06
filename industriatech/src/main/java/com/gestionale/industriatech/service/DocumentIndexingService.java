package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.dto.DocumentUploadResponse;
import com.gestionale.industriatech.dto.IndexingJobResponse;
import com.gestionale.industriatech.model.IndexingJob;

import jakarta.annotation.PreDestroy;

/**
 * Esegue l'indicizzazione dei documenti IN BACKGROUND.
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

    // true durante lo spegnimento: serve a dare un messaggio chiaro ai job interrotti
    private volatile boolean shuttingDown = false;

    // Lezione 7: storico permanente dei caricamenti e dei loro log
    private final IndexingHistoryService history;

    // Lezione 10: doppioni, versioni ed eliminazione
    private final DocumentCatalogService catalog;

    public DocumentIndexingService(
            DocumentService documentService,
            IndexingHistoryService history,
            DocumentCatalogService catalog
    ) {
        this.documentService = documentService;
        this.history = history;
        this.catalog = catalog;
    }

    // Registra il job e lo mette in coda. Ritorna SUBITO, senza aspettare l'indicizzazione.
    //
    // Lezione 10: "synchronized" perché il controllo dei doppioni e la creazione della riga
    // nello storico devono avvenire insieme. Senza, due upload identici arrivati nello
    // stesso istante passerebbero entrambi il controllo.
    public synchronized IndexingJobResponse submit(
            Path file,
            String storedFilename,
            String originalFilename,
            long sizeBytes,
            String hash
    ) {
        // Doppione? Nuova versione? Nuovo documento? (lancia DocumentConflictException se va rifiutato)
        DocumentCatalogService.UploadPlan plan = catalog.plan(hash, originalFilename);

        IndexingJob job = new IndexingJob(originalFilename, plan.version());
        jobs.put(job.getId(), job);

        history.create(job.getId(), originalFilename, storedFilename, sizeBytes,
                hash, plan.documentId(), plan.version());
        history.log(job.getId(), IndexingHistoryService.INFO, "UPLOAD", String.format(Locale.ITALIAN,
                "File \"%s\" ricevuto (%.2f MB), salvato come %s. In coda per l'indicizzazione.",
                originalFilename, sizeBytes / (1024.0 * 1024.0), storedFilename));
        history.log(job.getId(), IndexingHistoryService.INFO, "UPLOAD",
                "Impronta SHA-256 del contenuto: " + hash + " (nessun altro documento ha lo stesso contenuto).");
        history.log(job.getId(), IndexingHistoryService.INFO, "UPLOAD", plan.isNewVersion()
                ? String.format(Locale.ITALIAN,
                        "Esiste già un documento con questo nome: questo file è la VERSIONE %d. "
                                + "I chunk della versione %d verranno sostituiti solo se l'indicizzazione riesce.",
                        plan.version(), plan.previous().getVersione())
                : "Nuovo documento, versione 1. ID stabile (document_id): " + plan.documentId() + ".");

        executor.submit(() -> runJob(job, file, storedFilename, originalFilename, plan));

        log.info("Job " + job.getId() + " in coda per il file: " + originalFilename);
        return job.toResponse();
    }

    private void runJob(
            IndexingJob job,
            Path file,
            String storedFilename,
            String originalFilename,
            DocumentCatalogService.UploadPlan plan
    ) {
        job.start();
        history.sync(job.toResponse());
        history.log(job.getId(), IndexingHistoryService.INFO, "UPLOAD", "Indicizzazione avviata.");

        boolean completed = false;
        try {
            DocumentUploadResponse result = documentService.indexDocument(
                    file, storedFilename, originalFilename, plan.documentId(), plan.version(), job);

            // Lezione 10: la nuova versione è pronta, ora possiamo togliere quella vecchia
            if (plan.isNewVersion()) {
                job.setPhase("Sostituzione della versione precedente");
                replacePreviousVersion(job, plan, storedFilename);
            }

            job.complete(result.message());
            completed = true;
            log.info("Job " + job.getId() + " completato: " + result.chunks() + " chunk indicizzati");
            history.log(job.getId(), IndexingHistoryService.INFO, "FINE", String.format(Locale.ITALIAN,
                    "Documento indicizzato: %s%d chunk, durata totale %s.",
                    result.pages() > 0 ? result.pages() + " pagine, " : "",
                    result.chunks(), formatDuration(job.toResponse())));

        } catch (IllegalArgumentException ex) {
            // Errori "previsti": documento senza testo, nessun chunk...
            job.fail(ex.getMessage());
            log.warn("Job " + job.getId() + " fallito: " + ex.getMessage());
            history.log(job.getId(), IndexingHistoryService.ERROR, "FINE", ex.getMessage());

        } catch (Exception ex) {
            // Spegnimento (o riavvio di devtools) durante il lavoro: messaggio chiaro, non tecnico
            String message = shuttingDown
                    ? "Indicizzazione interrotta dallo spegnimento del backend: ricarica il file."
                    // Errori imprevisti: file corrotto, Ollama o database non raggiungibili...
                    : "Errore durante l'indicizzazione: " + ex.getMessage();
            job.fail(message);
            log.error("Job " + job.getId() + " fallito", ex);
            history.log(job.getId(), IndexingHistoryService.ERROR, "FINE", message);
        } finally {
            history.sync(job.toResponse());
        }

        // Lezione 10: un file non indicizzato non serve: lo togliamo dal disco.
        // Se era una nuova versione, la versione precedente resta quella attiva.
        if (!completed) {
            catalog.deleteStoredFile(storedFilename);
            history.log(job.getId(), IndexingHistoryService.WARN, "FINE", plan.isNewVersion()
                    ? "File eliminato dal disco. Resta attiva la versione " + plan.previous().getVersione() + "."
                    : "File eliminato dal disco: ricaricalo dopo aver risolto il problema.");
        }
    }

    // I nuovi chunk sono già salvati: se qui qualcosa va storto NON facciamo fallire il job
    // (perderemmo la versione nuova), ma lo scriviamo chiaramente nel log.
    private void replacePreviousVersion(IndexingJob job, DocumentCatalogService.UploadPlan plan, String storedFilename) {
        try {
            var retired = catalog.retirePreviousVersions(plan.documentId(), job.getId(), storedFilename);
            for (var old : retired) {
                history.log(job.getId(), IndexingHistoryService.INFO, "VERSIONI", String.format(Locale.ITALIAN,
                        "Versione %d sostituita: rimossi da pgvector i suoi %d chunk ed eliminato il suo file.",
                        old.getVersione(), old.getChunk()));
            }
        } catch (RuntimeException ex) {
            log.error("Sostituzione della versione precedente non riuscita", ex);
            history.log(job.getId(), IndexingHistoryService.ERROR, "VERSIONI",
                    "La nuova versione è attiva, ma non è stato possibile rimuovere la precedente: " + ex.getMessage());
        }
    }

    private static String formatDuration(IndexingJobResponse job) {
        if (job.startedAt() == null || job.finishedAt() == null) {
            return "n.d.";
        }
        long seconds = Math.round((job.finishedAt() - job.startedAt()) / 1000.0);
        return seconds >= 60 ? (seconds / 60) + " min " + (seconds % 60) + " s" : seconds + " s";
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

    // ContextClosedEvent arriva all'INIZIO dello spegnimento, prima che Spring chiuda
    // gli altri bean (es. il client HTTP verso Ollama): così l'errore che ne segue
    // viene riconosciuto come "interruzione" e non come un guasto.
    @EventListener(ContextClosedEvent.class)
    public void onShutdownStarted() {
        shuttingDown = true;
    }

    // Allo spegnimento (o al riavvio di devtools) fermiamo il thread di indicizzazione
    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        executor.shutdownNow();
    }
}
