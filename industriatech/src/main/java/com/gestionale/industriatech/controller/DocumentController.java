package com.gestionale.industriatech.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.IndexingJobResponse;
import com.gestionale.industriatech.service.DocumentIndexingService;
import com.gestionale.industriatech.service.PdfProcessingService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Riceve i PDF dal frontend React e li prepara per il sistema RAG.
 *
 * NOTA: la lezione usa "FilePart" di WebFlux. Questo progetto però gira
 * in modalità Spring MVC (nel pom ci sono sia webmvc che webflux, e in
 * questo caso Spring Boot sceglie MVC). In MVC il file arriva come
 * "MultipartFile", quindi usiamo quello.
 */
@RestController
@RequestMapping("/api/documents")
@CrossOrigin(origins = "http://localhost:5173")
public class DocumentController {

    private final DocumentIndexingService documentIndexingService;
    private final PdfProcessingService pdfProcessingService;

    // Cartella in cui salviamo i PDF (vedi app.documents.directory).
    // Il percorso è relativo alla cartella da cui avvii Spring Boot.
    private final Path uploadDirectory;

    public DocumentController(
            DocumentIndexingService documentIndexingService,
            PdfProcessingService pdfProcessingService,
            @Value("${app.documents.directory:documents/pdf}") String uploadDirectory
    ) {
        this.documentIndexingService = documentIndexingService;
        this.pdfProcessingService = pdfProcessingService;
        this.uploadDirectory = Path.of(uploadDirectory);
    }

    // Salva il PDF e avvia l'indicizzazione IN BACKGROUND.
    // Risponde subito con 202 Accepted ("richiesta accettata, la sto elaborando")
    // e con il job da interrogare per conoscere lo stato.
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IndexingJobResponse> uploadPdf(
            @RequestPart("file") MultipartFile file
    ) throws IOException {

        // 1. Controlli di base sul file ricevuto
        String originalFilename = file.getOriginalFilename();

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Il file caricato è vuoto");
        }

        if (originalFilename == null ||
                !originalFilename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Caricare un file PDF");
        }

        // 2. Non usiamo il nome originale come percorso fisico:
        // un nome come "../../config.pdf" potrebbe uscire dalla cartella.
        String storedFilename = UUID.randomUUID() + ".pdf";
        Path destination = uploadDirectory.resolve(storedFilename);

        // 3. Creiamo la cartella (se non esiste) e salviamo il PDF.
        Files.createDirectories(uploadDirectory);
        file.transferTo(destination.toAbsolutePath());

        // 4. Mettiamo in coda l'indicizzazione e rispondiamo subito
        IndexingJobResponse job = documentIndexingService.submit(
                destination,
                storedFilename,
                originalFilename
        );

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
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

    // TEMPORANEO (lezione 3): prova del modello di embedding.
    // GET http://localhost:8081/api/documents/test-embedding
    @GetMapping("/test-embedding")
    public Mono<Map<String, Object>> testEmbedding() {
        return Mono.fromCallable(pdfProcessingService::testEmbedding)
                .subscribeOn(Schedulers.boundedElastic());
    }

    // Trasforma gli errori di validazione in una risposta 400 leggibile dal frontend:
    // { "message": "Caricare un file PDF" }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidFile(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }
}
