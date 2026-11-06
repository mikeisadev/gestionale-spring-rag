package com.gestionale.industriatech.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.entity.DocumentoIndicizzato;
import com.gestionale.industriatech.repository.DocumentoIndicizzatoRepository;
import com.gestionale.industriatech.repository.LogIndicizzazioneRepository;

/**
 * Lezione 10: il "catalogo" dei documenti della base di conoscenza.
 *
 *   1. plan()                    -> a ogni caricamento: file doppione? nuovo documento? nuova versione?
 *   2. retirePreviousVersions()  -> dopo l'indicizzazione: la nuova versione prende il posto della vecchia
 *   3. delete()                  -> elimina un documento: righe dello storico, log, file e chunk in pgvector
 *
 * Il legame tra il database principale (MariaDB) e pgvector è il metadato "document_id"
 * che salviamo in ogni chunk.
 */
@Service
public class DocumentCatalogService {

    private static final Logger log = LoggerFactory.getLogger(DocumentCatalogService.class);

    static final List<String> ACTIVE = List.of("IN_CODA", "IN_ELABORAZIONE");
    static final List<String> ACTIVE_OR_COMPLETED = List.of("IN_CODA", "IN_ELABORAZIONE", "COMPLETATO");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN);

    private final DocumentoIndicizzatoRepository documentoRepository;
    private final LogIndicizzazioneRepository logRepository;
    private final VectorStore vectorStore;
    private final Path documentsDirectory;

    public DocumentCatalogService(
            DocumentoIndicizzatoRepository documentoRepository,
            LogIndicizzazioneRepository logRepository,
            VectorStore vectorStore,
            @Value("${app.documents.directory:documents/pdf}") String documentsDirectory
    ) {
        this.documentoRepository = documentoRepository;
        this.logRepository = logRepository;
        this.vectorStore = vectorStore;
        this.documentsDirectory = Path.of(documentsDirectory);
    }

    // ========== L'IMPRONTA DEL CONTENUTO (SHA-256) ==========

    /**
     * Legge il file a blocchi mentre calcola l'hash: anche con un file da 100 MB
     * non carichiamo tutto in memoria (a differenza di Files.readAllBytes).
     */
    public static String sha256(InputStream input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream in = new DigestInputStream(input, digest)) {
                in.transferTo(OutputStream.nullOutputStream()); // leggiamo e buttiamo: ci serve solo l'hash
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile calcolare l'hash del file", e);
        }
    }

    public static String sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return sha256(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile calcolare l'hash del file", e);
        }
    }

    // ========== 1. COSA FARE CON UN NUOVO CARICAMENTO ==========

    /**
     * Il risultato di plan().
     * previous = la versione attuale del documento, se il file ne è una nuova versione.
     */
    public record UploadPlan(String documentId, int version, DocumentoIndicizzato previous) {
        public boolean isNewVersion() {
            return previous != null;
        }
    }

    /**
     * Regole (in quest'ordine):
     *   a) stesso CONTENUTO di un documento già caricato -> doppione, rifiutato (409)
     *   b) stesso NOME di un documento in elaborazione    -> rifiutato: aspetta che finisca (409)
     *   c) stesso NOME di un documento completato         -> NUOVA VERSIONE di quel documento
     *   d) altrimenti                                     -> NUOVO documento, versione 1
     *
     * NOTA: "stesso nome = stesso documento" è una convenzione semplice, adatta al corso.
     * In un gestionale reale l'utente sceglierebbe esplicitamente "sostituisci il documento X".
     */
    public UploadPlan plan(String hash, String originalFilename) {
        // a) doppione: l'hash è identico, il contenuto anche (il nome può essere diverso)
        Optional<DocumentoIndicizzato> sameContent =
                documentoRepository.findByHashSha256AndStatoIn(hash, ACTIVE_OR_COMPLETED).stream().findFirst();
        if (sameContent.isPresent()) {
            DocumentoIndicizzato existing = sameContent.get();
            throw new DocumentConflictException(ACTIVE.contains(existing.getStato())
                    ? "Lo stesso file è già in elaborazione (\"" + existing.getNomeOriginale() + "\")."
                    : "Questo file è già nella base di conoscenza: \"" + existing.getNomeOriginale()
                            + "\", caricato il " + DATE.format(existing.getCaricatoIl())
                            + ". Il contenuto è identico, non serve indicizzarlo di nuovo.");
        }

        List<DocumentoIndicizzato> sameName =
                documentoRepository.findByNomeOriginaleIgnoreCaseAndStatoIn(originalFilename, ACTIVE_OR_COMPLETED);

        // b) una versione di questo documento è ancora in coda o in elaborazione
        if (sameName.stream().anyMatch(d -> ACTIVE.contains(d.getStato()))) {
            throw new DocumentConflictException("C'è già un caricamento in corso di \"" + originalFilename
                    + "\": attendi che finisca prima di caricare un'altra versione.");
        }

        // c) nuova versione dell'ultima versione completata
        return sameName.stream()
                .max(Comparator.comparingInt(DocumentoIndicizzato::getVersione))
                .map(current -> new UploadPlan(current.getDocumentoId(), current.getVersione() + 1, current))
                // d) nuovo documento: nuovo ID stabile
                .orElseGet(() -> new UploadPlan(UUID.randomUUID().toString(), 1, null));
    }

    // ========== 2. LA NUOVA VERSIONE PRENDE IL POSTO DELLA VECCHIA ==========

    /**
     * Chiamato DOPO che la nuova versione è stata indicizzata con successo.
     *
     * Perché non eliminiamo i vecchi chunk PRIMA di indicizzare quelli nuovi?
     *   - se l'indicizzazione fallisce (Ollama spento, file corrotto...) la versione vecchia resta;
     *   - durante l'indicizzazione (che può durare minuti) il chatbot continua a rispondere.
     * Per qualche secondo esistono entrambe le versioni: è il prezzo di questa scelta.
     *
     * @return le righe delle versioni sostituite (per i log)
     */
    public List<DocumentoIndicizzato> retirePreviousVersions(String documentId, String newUploadId, String newStoredFilename) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();

        // Tutti i chunk del documento TRANNE quelli del file appena indicizzato
        vectorStore.delete(b.and(
                b.eq(PdfProcessingService.METADATA_DOCUMENT_ID, documentId),
                b.ne("source", newStoredFilename)).build());

        List<DocumentoIndicizzato> retired = documentoRepository.findByDocumentoId(documentId).stream()
                .filter(d -> "COMPLETATO".equals(d.getStato()) && !d.getId().equals(newUploadId))
                .toList();

        int newVersion = documentoRepository.findById(newUploadId).map(DocumentoIndicizzato::getVersione).orElse(0);
        for (DocumentoIndicizzato old : retired) {
            old.setStato("SOSTITUITO");
            old.setMessaggio("Sostituito dalla versione " + newVersion);
            documentoRepository.save(old);
            deleteStoredFile(old.getNomeSalvato()); // il file vecchio non serve più
        }
        return retired;
    }

    // ========== 3. ELIMINAZIONE DI UN DOCUMENTO ==========

    public record DeletedDocument(String filename, int uploads) {}

    /**
     * Elimina il documento con tutte le sue versioni: chunk in pgvector, file su disco,
     * righe dello storico e relativi log.
     */
    public DeletedDocument delete(String documentId) {
        List<DocumentoIndicizzato> uploads = documentoRepository.findByDocumentoId(documentId);
        if (uploads.isEmpty()) {
            return null;
        }
        if (uploads.stream().anyMatch(d -> ACTIVE.contains(d.getStato()))) {
            throw new DocumentConflictException(
                    "Il documento è in elaborazione: potrai eliminarlo quando l'indicizzazione sarà finita.");
        }

        // 1. pgvector: i chunk con questo document_id.
        //    Per sicurezza anche quelli dei suoi file (metadato "source"): così togliamo
        //    anche eventuali chunk rimasti senza document_id.
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Object> storedFiles = uploads.stream()
                .map(DocumentoIndicizzato::getNomeSalvato)
                .filter(Objects::nonNull)
                .map(Object.class::cast)
                .toList();
        vectorStore.delete(storedFiles.isEmpty()
                ? b.eq(PdfProcessingService.METADATA_DOCUMENT_ID, documentId).build()
                : b.or(b.eq(PdfProcessingService.METADATA_DOCUMENT_ID, documentId), b.in("source", storedFiles)).build());

        // 2. disco, log e storico
        for (DocumentoIndicizzato upload : uploads) {
            deleteStoredFile(upload.getNomeSalvato());
            logRepository.deleteByDocumentoId(upload.getId());
            documentoRepository.delete(upload);
        }

        String filename = uploads.stream()
                .max(Comparator.comparingInt(DocumentoIndicizzato::getVersione))
                .map(DocumentoIndicizzato::getNomeOriginale)
                .orElse(documentId);

        log.info("Documento eliminato: {} ({} caricamenti, document_id {})", filename, uploads.size(), documentId);
        return new DeletedDocument(filename, uploads.size());
    }

    // Toglie un file da documents/pdf. Il nome arriva dal NOSTRO database (UUID + estensione),
    // non dall'utente: non c'è rischio che punti fuori dalla cartella.
    public void deleteStoredFile(String storedFilename) {
        if (storedFilename == null) {
            return;
        }
        try {
            Files.deleteIfExists(documentsDirectory.resolve(storedFilename));
        } catch (IOException ex) {
            log.warn("Impossibile eliminare il file {}: {}", storedFilename, ex.getMessage());
        }
    }
}
