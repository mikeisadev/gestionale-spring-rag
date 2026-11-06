package com.gestionale.industriatech.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.gestionale.industriatech.entity.DocumentoIndicizzato;
import com.gestionale.industriatech.repository.DocumentoIndicizzatoRepository;

/**
 * All'avvio, aggiunge allo storico i documenti indicizzati PRIMA della lezione 7
 * (quando lo storico non esisteva): li ricava dai metadati dei chunk in pgvector.
 *
 * È idempotente: un documento già presente nello storico non viene aggiunto di nuovo.
 *
 * Lezione 10: aggiunge anche ID stabile, versione e hash ai documenti caricati prima.
 */
@Component
public class DocumentHistoryBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DocumentHistoryBackfill.class);

    // Un "documento" in pgvector = tutti i chunk con lo stesso file salvato (metadato source)
    private static final String DOCUMENTS_IN_VECTOR_STORE = """
            SELECT metadata->>'source'                       AS source,
                   MIN(metadata->>'original_filename')       AS original_filename,
                   COUNT(*)                                  AS chunks,
                   COUNT(DISTINCT metadata->>'page_number')  AS pages
            FROM vector_store
            WHERE metadata->>'source' IS NOT NULL
            GROUP BY metadata->>'source'
            """;

    private final JdbcTemplate jdbcTemplate; // il @Primary di VectorStoreConfig: PostgreSQL
    private final DocumentoIndicizzatoRepository documentoRepository;
    private final IndexingHistoryService history;
    private final Path documentsDirectory;

    public DocumentHistoryBackfill(
            JdbcTemplate jdbcTemplate,
            DocumentoIndicizzatoRepository documentoRepository,
            IndexingHistoryService history,
            @Value("${app.documents.directory:documents/pdf}") String documentsDirectory
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.documentoRepository = documentoRepository;
        this.history = history;
        this.documentsDirectory = Path.of(documentsDirectory);
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.query(DOCUMENTS_IN_VECTOR_STORE, rs -> {
                String source = rs.getString("source");
                if (documentoRepository.existsByNomeSalvato(source)) {
                    return;
                }
                addToHistory(source, rs.getString("original_filename"), rs.getInt("pages"), rs.getInt("chunks"));
            });
        } catch (RuntimeException ex) {
            // Lo storico è un "di più": se il recupero fallisce l'applicazione parte comunque
            log.warn("Recupero dello storico dei documenti non riuscito: {}", ex.getMessage());
        }

        try {
            addDocumentIds();
        } catch (RuntimeException ex) {
            log.warn("Aggiornamento dei documenti alla lezione 10 non riuscito: {}", ex.getMessage());
        }
    }

    // Lezione 10: aggiunge il metadato document_id ai chunk di un file (solo se manca)
    private static final String ADD_DOCUMENT_ID_TO_CHUNKS = """
            UPDATE vector_store
            SET metadata = (metadata::jsonb || jsonb_build_object('document_id', ?::text, 'version', 1))::json
            WHERE metadata->>'source' = ?
              AND metadata->>'document_id' IS NULL
            """;

    /**
     * Lezione 10: i documenti caricati PRIMA di questa lezione non hanno ID stabile,
     * versione e hash, e i loro chunk non hanno il metadato "document_id".
     * Li aggiorniamo una volta sola (le righe già aggiornate non vengono più toccate).
     */
    private void addDocumentIds() {
        for (DocumentoIndicizzato documento : documentoRepository.findByDocumentoIdIsNull()) {
            documento.setDocumentoId(documento.getId()); // l'ID stabile è l'id del primo caricamento
            documento.setVersione(1);

            Path file = documento.getNomeSalvato() == null ? null : documentsDirectory.resolve(documento.getNomeSalvato());
            if (file != null && Files.exists(file)) {
                documento.setHashSha256(DocumentCatalogService.sha256(file));
            }
            documentoRepository.save(documento);

            if ("COMPLETATO".equals(documento.getStato()) && documento.getNomeSalvato() != null) {
                int chunks = jdbcTemplate.update(ADD_DOCUMENT_ID_TO_CHUNKS, documento.getDocumentoId(), documento.getNomeSalvato());
                log.info("Lezione 10: document_id aggiunto a {} chunk di {}", chunks, documento.getNomeOriginale());
            }
        }
    }

    private void addToHistory(String source, String originalFilename, int pages, int chunks) {
        Path file = documentsDirectory.resolve(source);

        DocumentoIndicizzato documento = new DocumentoIndicizzato();
        documento.setId(UUID.randomUUID().toString());
        documento.setNomeOriginale(originalFilename != null ? originalFilename : source);
        documento.setNomeSalvato(source);
        documento.setStato("COMPLETATO");
        documento.setPagine(pages);
        documento.setChunk(chunks);
        documento.setChunkIndicizzati(chunks);
        documento.setMessaggio("Documento indicizzato prima dello storico (lezione 7)");

        // Se il PDF è ancora su disco usiamo la sua data e dimensione
        LocalDateTime uploadedAt = LocalDateTime.now();
        try {
            if (Files.exists(file)) {
                documento.setDimensioneByte(Files.size(file));
                uploadedAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), ZoneId.systemDefault());
            }
        } catch (IOException ignored) {
            // dimensione e data restano sconosciute
        }
        documento.setCaricatoIl(uploadedAt);
        documento.setTerminatoIl(uploadedAt);
        documentoRepository.save(documento);

        history.log(documento.getId(), IndexingHistoryService.WARN, "UPLOAD",
                "Documento indicizzato prima che esistesse lo storico: i log dettagliati di quel caricamento non sono disponibili. "
                        + "Dati ricavati da pgvector: " + pages + " pagine, " + chunks + " chunk.");

        log.info("Storico: aggiunto il documento già indicizzato {} ({} chunk)", originalFilename, chunks);
    }
}
