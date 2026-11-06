package com.gestionale.industriatech.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.gestionale.industriatech.dto.IndexingJobResponse;
import com.gestionale.industriatech.entity.DocumentoIndicizzato;
import com.gestionale.industriatech.model.IndexingStatus;
import com.gestionale.industriatech.repository.DocumentoIndicizzatoRepository;
import com.gestionale.industriatech.service.DocumentCatalogService;
import com.gestionale.industriatech.service.DocumentConflictException;
import com.gestionale.industriatech.service.DocumentIndexingService;

/**
 * LIVELLO 2 (lezione 10): il ciclo di vita di un documento, dall'upload all'eliminazione.
 * Usa i database veri e Ollama (embedding): è un test di integrazione.
 *   ./mvnw test -DexcludedTestGroups=llm -Dtest=DocumentLifecycleTest
 *
 * Per non toccare i documenti veri usa un nome di file unico, e alla fine elimina tutto.
 */
@Tag("integration")
@SpringBootTest
class DocumentLifecycleTest {

    @Autowired
    DocumentIndexingService indexingService;

    @Autowired
    DocumentCatalogService catalog;

    @Autowired
    DocumentoIndicizzatoRepository repository;

    @Autowired
    VectorStore vectorStore;

    @Value("${app.documents.directory:documents/pdf}")
    Path documentsDirectory;

    private final String filename = "test-lezione10-" + UUID.randomUUID() + ".txt";
    private String documentId;

    // Simula l'upload: salva il file come farebbe il controller e mette in coda il job
    private IndexingJobResponse upload(String content) throws Exception {
        Files.createDirectories(documentsDirectory);
        String stored = UUID.randomUUID() + ".txt";
        Path file = Files.writeString(documentsDirectory.resolve(stored), content);
        try {
            return indexingService.submit(file, stored, filename, Files.size(file), DocumentCatalogService.sha256(file));
        } catch (DocumentConflictException ex) {
            Files.deleteIfExists(file);
            throw ex;
        }
    }

    private IndexingJobResponse waitForCompletion(IndexingJobResponse job) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        IndexingJobResponse current = job;
        while (current.status() == IndexingStatus.IN_CODA || current.status() == IndexingStatus.IN_ELABORAZIONE) {
            assertThat(Instant.now()).as("indicizzazione entro 2 minuti").isBefore(deadline);
            Thread.sleep(500);
            current = indexingService.getJob(job.id()).orElseThrow();
        }
        return current;
    }

    private List<Document> chunksOf(String documentId) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query("rimborso chilometrico")
                .topK(50)
                .similarityThresholdAll()
                .filterExpression("document_id == '" + documentId + "'")
                .build());
    }

    @AfterEach
    void cleanUp() {
        if (documentId != null) {
            catalog.delete(documentId); // non lasciamo documenti di test
        }
    }

    @Test
    void doppioniVersioniEdEliminazione() throws Exception {
        // 1. Primo caricamento -> nuovo documento, versione 1
        IndexingJobResponse v1 = waitForCompletion(upload("Il rimborso chilometrico è di 0,42 euro al km."));
        assertThat(v1.status()).isEqualTo(IndexingStatus.COMPLETATO);
        DocumentoIndicizzato rowV1 = repository.findById(v1.id()).orElseThrow();
        documentId = rowV1.getDocumentoId();

        assertThat(chunksOf(documentId)).isNotEmpty()
                .allSatisfy(chunk -> assertThat(chunk.getMetadata()).containsEntry("version", 1));

        // 2. Stesso contenuto -> doppione, rifiutato
        assertThatThrownBy(() -> upload("Il rimborso chilometrico è di 0,42 euro al km."))
                .isInstanceOf(DocumentConflictException.class);

        // 3. Stesso nome, contenuto nuovo -> versione 2, che SOSTITUISCE la 1
        IndexingJobResponse v2 = waitForCompletion(upload("Dal 2027 il rimborso chilometrico è di 0,45 euro al km."));
        assertThat(v2.status()).isEqualTo(IndexingStatus.COMPLETATO);
        assertThat(v2.version()).isEqualTo(2);

        assertThat(repository.findById(v1.id()).orElseThrow().getStato()).isEqualTo("SOSTITUITO");
        assertThat(documentsDirectory.resolve(rowV1.getNomeSalvato())).doesNotExist(); // file vecchio eliminato

        List<Document> chunks = chunksOf(documentId);
        assertThat(chunks).isNotEmpty()
                .allSatisfy(chunk -> {
                    assertThat(chunk.getMetadata()).containsEntry("version", 2);
                    assertThat(chunk.getText()).contains("0,45").doesNotContain("0,42");
                });

        // 4. Eliminazione -> spariscono righe, file e chunk
        String storedV2 = repository.findById(v2.id()).orElseThrow().getNomeSalvato();
        assertThat(catalog.delete(documentId).uploads()).isEqualTo(2);

        assertThat(repository.findByDocumentoId(documentId)).isEmpty();
        assertThat(documentsDirectory.resolve(storedV2)).doesNotExist();
        assertThat(chunksOf(documentId)).isEmpty();
        documentId = null; // già eliminato
    }
}
