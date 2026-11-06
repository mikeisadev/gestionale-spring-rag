package com.gestionale.industriatech.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.DocumentHistoryResponse;
import com.gestionale.industriatech.dto.IndexingJobResponse;
import com.gestionale.industriatech.dto.IndexingLogResponse;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.service.DocumentCatalogService;
import com.gestionale.industriatech.service.DocumentConflictException;
import com.gestionale.industriatech.service.DocumentIndexingService;
import com.gestionale.industriatech.service.DocumentReaderService;
import com.gestionale.industriatech.service.IndexingHistoryService;
import com.gestionale.industriatech.service.PdfProcessingService;
import com.gestionale.industriatech.wiki.WikiIngestionService;
import com.gestionale.industriatech.wiki.WikiStatusResponse;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Riceve i documenti dal frontend React e li prepara per il sistema RAG.
 * (Lezione 10: non solo PDF, vedi DocumentReaderService.)
 *
 * NOTA: la lezione usa "FilePart" di WebFlux. Questo progetto però gira
 * in modalità Spring MVC (nel pom ci sono sia webmvc che webflux, e in
 * questo caso Spring Boot sceglie MVC). In MVC il file arriva come
 * "MultipartFile", quindi usiamo quello.
 */
@RestController
@RequestMapping("/api/documents")
// Lezione 13: niente @CrossOrigin qui, CORS è configurato una volta sola in SecurityConfig
public class DocumentController {

    private final DocumentIndexingService documentIndexingService;
    private final IndexingHistoryService indexingHistoryService;
    private final PdfProcessingService pdfProcessingService;
    private final DocumentCatalogService catalog;
    private final CurrentUserProvider currentUserProvider;

    // Lezione 11: presente solo se la wiki è configurata (WIKI_VAULT_PATH)
    private final ObjectProvider<WikiIngestionService> wikiIngestionService;

    // Cartella in cui salviamo i PDF (vedi app.documents.directory).
    // Il percorso è relativo alla cartella da cui avvii Spring Boot.
    private final Path uploadDirectory;

    public DocumentController(
            DocumentIndexingService documentIndexingService,
            IndexingHistoryService indexingHistoryService,
            PdfProcessingService pdfProcessingService,
            DocumentCatalogService catalog,
            CurrentUserProvider currentUserProvider,
            ObjectProvider<WikiIngestionService> wikiIngestionService,
            @Value("${app.documents.directory:documents/pdf}") String uploadDirectory
    ) {
        this.documentIndexingService = documentIndexingService;
        this.indexingHistoryService = indexingHistoryService;
        this.pdfProcessingService = pdfProcessingService;
        this.catalog = catalog;
        this.currentUserProvider = currentUserProvider;
        this.wikiIngestionService = wikiIngestionService;
        this.uploadDirectory = Path.of(uploadDirectory);
    }

    // Salva il file e avvia l'indicizzazione IN BACKGROUND.
    // Risponde subito con 202 Accepted ("richiesta accettata, la sto elaborando")
    // e con il job da interrogare per conoscere lo stato.
    // Lezione 10: accetta più formati e rifiuta i doppioni con 409 Conflict.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IndexingJobResponse> uploadDocument(
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        // 1. Controlli di base sul file ricevuto
        String originalFilename = file.getOriginalFilename();

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Il file caricato è vuoto");
        }

        // Lezione 10: lista "bianca" delle estensioni (vedi DocumentReaderService)
        if (!DocumentReaderService.isSupported(originalFilename)) {
            throw new IllegalArgumentException("Formato non supportato. Formati accettati: "
                    + DocumentReaderService.SUPPORTED_EXTENSIONS.stream().sorted().map(e -> "." + e)
                            .collect(Collectors.joining(", ")));
        }

        // 2. Lezione 10: l'impronta del contenuto, calcolata PRIMA di salvare il file
        String hash = DocumentCatalogService.sha256(file.getInputStream());

        // 3. Non usiamo il nome originale come percorso fisico:
        // un nome come "../../config.pdf" potrebbe uscire dalla cartella.
        // Teniamo solo l'estensione (già controllata sopra).
        String storedFilename = UUID.randomUUID() + "." + DocumentReaderService.extensionOf(originalFilename);
        Path destination = uploadDirectory.resolve(storedFilename);

        // 4. Creiamo la cartella (se non esiste) e salviamo il file.
        Files.createDirectories(uploadDirectory);
        file.transferTo(destination.toAbsolutePath());

        // 5. Mettiamo in coda l'indicizzazione e rispondiamo subito
        try {
            IndexingJobResponse job = documentIndexingService.submit(
                    destination,
                    storedFilename,
                    originalFilename,
                    file.getSize(),
                    hash
            );
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        } catch (DocumentConflictException ex) {
            // Doppione o caricamento già in corso: il file salvato non serve
            Files.deleteIfExists(destination);
            throw ex;
        }
    }

    // Lezione 10: elimina un documento (tutte le versioni) e i suoi chunk da pgvector.
    // Riservato agli amministratori. {documentId} è l'ID stabile (campo "documentId" dello storico).
    // DELETE http://localhost:8081/api/documents/{documentId}
    @DeleteMapping("/{documentId}")
    public Map<String, String> deleteDocument(@PathVariable String documentId) {
        // TEMPORANEO: finché non c'è il login (prompt 13) l'utente è quello "demo" di application.properties
        if (!currentUserProvider.currentUser().admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo un amministratore può eliminare i documenti.");
        }

        DocumentCatalogService.DeletedDocument deleted = catalog.delete(documentId);
        if (deleted == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento non trovato: " + documentId);
        }
        return Map.of("message", "\"" + deleted.filename() + "\" eliminato dalla base di conoscenza.");
    }

    // Stato di un singolo job (il frontend lo chiede ogni 2 secondi)
    // GET http://localhost:8081/api/documents/jobs/{id}
    @GetMapping("/jobs/{id}")
    public IndexingJobResponse getJob(@PathVariable String id) {
        return documentIndexingService.getJob(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Job non trovato: " + id));
    }

    // Tutti i job dall'avvio del backend, dal più recente
    // GET http://localhost:8081/api/documents/jobs
    @GetMapping("/jobs")
    public List<IndexingJobResponse> listJobs() {
        return documentIndexingService.listJobs();
    }

    // Lezione 7: storico permanente dei file caricati (anche dopo un riavvio)
    // GET http://localhost:8081/api/documents/history
    @GetMapping("/history")
    public List<DocumentHistoryResponse> history() {
        return indexingHistoryService.list();
    }

    // Lezione 7: i log di un caricamento (pulsante "Vedi log")
    // GET http://localhost:8081/api/documents/history/{id}/logs
    @GetMapping("/history/{id}/logs")
    public List<IndexingLogResponse> logs(@PathVariable String id) {
        return indexingHistoryService.logs(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento non trovato: " + id));
    }

    // ========== LEZIONE 11: LLM WIKI ==========

    // Stato della wiki: configurata? quante pagine indicizzate? reindicizzazione in corso?
    // GET http://localhost:8081/api/documents/wiki/status
    @GetMapping("/wiki/status")
    public WikiStatusResponse wikiStatus() {
        WikiIngestionService wiki = wikiIngestionService.getIfAvailable();
        return wiki == null ? WikiStatusResponse.disabled() : wiki.status();
    }

    // Avvia la reindicizzazione IN BACKGROUND e risponde subito con 202 (come l'upload).
    // La lezione originale aspetta la fine (Mono): con 144 pagine servono minuti, oltre il timeout di 120 s.
    // NOTA: come l'upload, per ora non richiede l'amministratore. Dal prompt 13 tutto /api/documents/** sarà protetto.
    // POST http://localhost:8081/api/documents/wiki/reindex
    @PostMapping("/wiki/reindex")
    public ResponseEntity<WikiStatusResponse> reindexWiki() {
        WikiIngestionService wiki = wikiIngestionService.getIfAvailable();
        if (wiki == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Wiki non configurata: imposta la variabile WIKI_VAULT_PATH e riavvia il backend.");
        }
        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(wiki.startReindex());
        } catch (IllegalStateException ex) {
            throw new DocumentConflictException(ex.getMessage()); // già in corso -> 409
        }
    }

    // TEMPORANEO (lezione 3): prova del modello di embedding.
    // GET http://localhost:8081/api/documents/test-embedding
    @GetMapping("/test-embedding")
    public Mono<Map<String, Object>> testEmbedding() {
        return Mono.fromCallable(pdfProcessingService::testEmbedding)
                .subscribeOn(Schedulers.boundedElastic());
    }

    // Trasforma gli errori di validazione in una risposta 400 leggibile dal frontend:
    // { "message": "Formato non supportato..." }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFile(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    // Lezione 10: doppione o documento in elaborazione -> 409 Conflict
    @ExceptionHandler(DocumentConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(DocumentConflictException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    // 403, 404...: stesso formato { "message": ... } anche per questi errori
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : ex.getMessage()));
    }
}
