package com.gestionale.industriatech.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestionale.industriatech.dto.DocumentHistoryResponse;
import com.gestionale.industriatech.dto.IndexingJobResponse;
import com.gestionale.industriatech.dto.IndexingLogResponse;
import com.gestionale.industriatech.entity.DocumentoIndicizzato;
import com.gestionale.industriatech.entity.LogIndicizzazione;
import com.gestionale.industriatech.repository.DocumentoIndicizzatoRepository;
import com.gestionale.industriatech.repository.LogIndicizzazioneRepository;

/**
 * Storico dei caricamenti e relativi log, salvati su MariaDB (lezione 7).
 *
 * - IndexingJob (in memoria)  -> stato "vivo", letto ogni 2 secondi dal frontend
 * - questo service (database) -> storico permanente + log dettagliati ("Vedi log")
 */
@Service
public class IndexingHistoryService {

    private static final Logger log = LoggerFactory.getLogger(IndexingHistoryService.class);

    public static final String INFO = "INFO";
    public static final String WARN = "WARN";
    public static final String ERROR = "ERROR";

    private final DocumentoIndicizzatoRepository documentoRepository;
    private final LogIndicizzazioneRepository logRepository;
    private final VectorStore vectorStore;
    private final DocumentCatalogService catalog;

    public IndexingHistoryService(
            DocumentoIndicizzatoRepository documentoRepository,
            LogIndicizzazioneRepository logRepository,
            VectorStore vectorStore,
            DocumentCatalogService catalog
    ) {
        this.documentoRepository = documentoRepository;
        this.logRepository = logRepository;
        this.vectorStore = vectorStore;
        this.catalog = catalog;
    }

    // Chiamato all'upload: crea la riga dello storico (stato IN_CODA)
    @Transactional
    public void create(String id, String originalFilename, String storedFilename, long sizeBytes,
                       String hash, String documentId, int version) {
        DocumentoIndicizzato documento = new DocumentoIndicizzato();
        documento.setId(id);
        documento.setNomeOriginale(originalFilename);
        documento.setNomeSalvato(storedFilename);
        documento.setDimensioneByte(sizeBytes);
        documento.setHashSha256(hash);       // lezione 10
        documento.setDocumentoId(documentId); // lezione 10
        documento.setVersione(version);       // lezione 10
        documento.setStato("IN_CODA");
        documento.setMessaggio("In attesa di essere elaborato");
        documento.setCaricatoIl(LocalDateTime.now());
        documentoRepository.save(documento);
    }

    // Copia nel database lo stato attuale del job (fase, pagine, chunk, avanzamento...)
    @Transactional
    public void sync(IndexingJobResponse job) {
        documentoRepository.findById(job.id()).ifPresent(documento -> {
            documento.setStato(job.status().name());
            documento.setPagine(job.pages());
            documento.setChunk(job.chunks());
            documento.setChunkIndicizzati(job.chunksIndexed());
            documento.setMessaggio(truncate(job.message(), 1000));
            documento.setIniziatoIl(toLocal(job.startedAt()));
            documento.setTerminatoIl(toLocal(job.finishedAt()));
            documentoRepository.save(documento);
        });
    }

    // Aggiunge una riga di log (e la scrive anche nella console di Spring Boot)
    public void log(String documentId, String level, String phase, String message) {
        LogIndicizzazione riga = new LogIndicizzazione();
        riga.setDocumentoId(documentId);
        riga.setIstante(LocalDateTime.now());
        riga.setLivello(level);
        riga.setFase(phase);
        riga.setMessaggio(message);
        try {
            logRepository.save(riga);
        } catch (RuntimeException ex) {
            // Un problema nel salvare il LOG non deve far fallire l'INDICIZZAZIONE
            log.warn("Impossibile salvare la riga di log: {}", ex.getMessage());
        }

        String line = "[" + phase + "] " + message + " (documento " + documentId + ")";
        switch (level) {
            case ERROR -> log.error(line);
            case WARN -> log.warn(line);
            default -> log.info(line);
        }
    }

    public List<DocumentHistoryResponse> list() {
        return documentoRepository.findAllByOrderByCaricatoIlDesc().stream()
                .map(d -> new DocumentHistoryResponse(
                        d.getId(), d.getDocumentoId(), d.getVersione(),
                        d.getNomeOriginale(), d.getDimensioneByte(), d.getStato(),
                        d.getPagine(), d.getChunk(), d.getChunkIndicizzati(), d.getMessaggio(),
                        d.getCaricatoIl(), d.getIniziatoIl(), d.getTerminatoIl()))
                .toList();
    }

    public Optional<List<IndexingLogResponse>> logs(String documentId) {
        if (!documentoRepository.existsById(documentId)) {
            return Optional.empty();
        }
        return Optional.of(logRepository.findByDocumentoIdOrderByIdAsc(documentId).stream()
                .map(l -> new IndexingLogResponse(l.getIstante(), l.getLivello(), l.getFase(), l.getMessaggio()))
                .toList());
    }

    // All'avvio: un caricamento ancora "in corso" nel database è stato interrotto in modo brusco
    // (es. kill -9, crash, mancanza di corrente): non c'è stato il tempo di registrare l'errore.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void markInterruptedJobs() {
        List<DocumentoIndicizzato> interrupted =
                documentoRepository.findByStatoIn(List.of("IN_CODA", "IN_ELABORAZIONE"));

        for (DocumentoIndicizzato documento : interrupted) {
            // Togliamo gli eventuali chunk parziali: ogni chunk ha nei metadati "source" = file salvato
            removePartialChunks(documento);
            // Lezione 10: anche il file non serve più (l'utente dovrà ricaricarlo).
            // Se era una nuova versione, la versione precedente resta attiva.
            catalog.deleteStoredFile(documento.getNomeSalvato());

            documento.setStato("ERRORE");
            documento.setMessaggio("Indicizzazione interrotta dal riavvio del backend: ricarica il file.");
            documento.setTerminatoIl(LocalDateTime.now());
            documentoRepository.save(documento);
            log(documento.getId(), ERROR, "FINE",
                    "Indicizzazione interrotta dal riavvio del backend: ricarica il file.");
        }
    }

    private void removePartialChunks(DocumentoIndicizzato documento) {
        if (documento.getNomeSalvato() == null) {
            return;
        }
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("source", documento.getNomeSalvato()).build());
            log(documento.getId(), WARN, "EMBEDDING",
                    "Rimossi da pgvector gli eventuali chunk parziali dell'indicizzazione interrotta.");
        } catch (RuntimeException ex) {
            log(documento.getId(), ERROR, "EMBEDDING",
                    "Impossibile rimuovere i chunk parziali: " + ex.getMessage());
        }
    }

    private static LocalDateTime toLocal(Long epochMillis) {
        return epochMillis == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static String truncate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
