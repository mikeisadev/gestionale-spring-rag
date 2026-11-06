package com.gestionale.industriatech.wiki;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.service.PdfProcessingService;
import com.gestionale.industriatech.service.RagService;

import jakarta.annotation.PreDestroy;

/**
 * Lezione 11: porta le pagine della LLM Wiki nel database vettoriale (pgvector),
 * accanto ai documenti caricati dalla pagina di upload.
 *
 * - reindicizza solo le pagine CAMBIATE (hash SHA-256 del contenuto);
 * - rimuove i chunk delle pagine cancellate dal vault;
 * - gira IN BACKGROUND, come l'indicizzazione dei PDF (lezione 3.1): con centinaia di pagine
 *   gli embedding richiedono minuti, troppi per una singola richiesta HTTP.
 */
@Service
@Conditional(WikiEnabledCondition.class)
public class WikiIngestionService {

    public static final String SOURCE_TYPE = "wiki";
    public static final String DOCUMENT_ID_PREFIX = "wiki:";

    // Metadati dei chunk della wiki
    public static final String METADATA_SOURCE_TYPE = "source_type";   // "wiki" (i PDF non ce l'hanno)
    public static final String METADATA_SECTION = "section";           // titolo della sezione
    public static final String METADATA_CONTENT_HASH = "content_hash"; // hash della versione della pagina
    public static final String METADATA_DEPARTMENT = "department";

    private static final Logger log = LoggerFactory.getLogger(WikiIngestionService.class);

    private final WikiVault wikiVault;
    private final VectorStore vectorStore;
    private final WikiIndexStateRepository stateRepository;
    private final String vaultName;

    // Stessi valori del chunking dei PDF (lezione 2)
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(PdfProcessingService.CHUNK_SIZE)
            .withMinChunkSizeChars(PdfProcessingService.MIN_CHUNK_SIZE_CHARS)
            .withMinChunkLengthToEmbed(PdfProcessingService.MIN_CHUNK_LENGTH_TO_EMBED)
            .withMaxNumChunks(PdfProcessingService.MAX_NUM_CHUNKS)
            .withKeepSeparator(true)
            .build();

    // Due reindicizzazioni contemporanee scriverebbero gli stessi chunk due volte
    private final ReentrantLock reindexLock = new ReentrantLock();

    // Un thread dedicato, come "indicizzazione-pdf" in DocumentIndexingService
    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            runnable -> new Thread(runnable, "indicizzazione-wiki"));

    // ===== Stato della reindicizzazione, letto dal frontend ogni 2 secondi =====
    private volatile boolean running;
    private volatile int totalPages;
    private volatile int processedPages;
    private volatile String currentPage;
    private volatile Long startedAt;
    private volatile Long finishedAt;
    private volatile WikiReindexReport lastReport;
    private volatile String error;
    private final List<String> failedPages = new CopyOnWriteArrayList<>();

    public WikiIngestionService(WikiVault wikiVault,
                                VectorStore vectorStore,
                                WikiIndexStateRepository stateRepository,
                                WikiProperties properties) {
        this.wikiVault = wikiVault;
        this.vectorStore = vectorStore;
        this.stateRepository = stateRepository;
        this.vaultName = properties.vaultName();
    }

    // ========== AVVIO IN BACKGROUND ==========

    /** Avvia la reindicizzazione e ritorna SUBITO. Lancia IllegalStateException se è già in corso. */
    public synchronized WikiStatusResponse startReindex() {
        if (running) {
            throw new IllegalStateException("Reindicizzazione della wiki già in corso");
        }
        running = true; // subito: un secondo clic deve trovare "già in corso"
        startedAt = System.currentTimeMillis();
        finishedAt = null;
        error = null;
        processedPages = 0;
        totalPages = 0;
        currentPage = null;
        failedPages.clear();

        executor.submit(() -> {
            try {
                lastReport = reindex();
            } catch (RuntimeException ex) {
                log.error("Reindicizzazione della wiki fallita", ex);
                error = "Reindicizzazione interrotta: " + ex.getMessage();
            } finally {
                currentPage = null;
                finishedAt = System.currentTimeMillis();
                running = false;
            }
        });
        return status();
    }

    public WikiStatusResponse status() {
        return new WikiStatusResponse(true, vaultName,
                stateRepository.countPages(), stateRepository.countChunks(),
                running, totalPages, processedPages, currentPage,
                startedAt, finishedAt, lastReport, error, List.copyOf(failedPages));
    }

    // ========== LA REINDICIZZAZIONE ==========

    /** Versione "sincrona": usata dal thread in background e dai test. */
    public WikiReindexReport reindex() {
        if (!reindexLock.tryLock()) {
            throw new IllegalStateException("Reindicizzazione della wiki già in corso");
        }
        try {
            return reindexChangedPages();
        } finally {
            reindexLock.unlock();
        }
    }

    private WikiReindexReport reindexChangedPages() {
        List<String> pages = wikiVault.listPages();
        Map<String, String> indexedHashes = stateRepository.findAllHashes();
        totalPages = pages.size();
        log.info("Wiki \"{}\": {} pagine da controllare", vaultName, pages.size());

        int updated = 0;
        int failed = 0;
        int chunks = 0;
        for (String path : pages) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("backend in spegnimento");
            }
            currentPage = path;
            try {
                String markdown = wikiVault.readPage(path);
                String hash = sha256(markdown);
                // Solo le pagine cambiate: se l'hash è uguale non spendiamo embedding
                if (!hash.equals(indexedHashes.get(path))) {
                    chunks += indexPage(path, markdown, hash);
                    stateRepository.save(path, hash); // solo DOPO che i chunk sono salvati
                    updated++;
                }
            } catch (WikiAccessException e) {
                // Una pagina illeggibile (ad esempio troppo grande) non deve bloccare tutte le altre
                log.warn("Pagina saltata: {}", e.getMessage());
                failedPages.add(e.getMessage());
                failed++;
            } catch (RuntimeException e) {
                // Ad esempio Ollama non raggiungibile: la pagina verrà ritentata alla prossima reindicizzazione
                log.warn("Indicizzazione della pagina {} non riuscita: {}", path, e.getMessage());
                failedPages.add(path + ": " + e.getMessage());
                failed++;
            }
            processedPages++;
        }

        // Le pagine cancellate dal vault devono sparire anche dal database vettoriale
        Set<String> removedPages = new HashSet<>(indexedHashes.keySet());
        pages.forEach(removedPages::remove);
        removedPages.forEach(path -> {
            vectorStore.delete(new FilterExpressionBuilder()
                    .eq(PdfProcessingService.METADATA_DOCUMENT_ID, DOCUMENT_ID_PREFIX + path).build());
            stateRepository.delete(path);
        });

        int unchanged = pages.size() - updated - failed;
        WikiReindexReport report = new WikiReindexReport(pages.size(), updated, unchanged, removedPages.size(), failed, chunks);
        log.info("Wiki \"{}\" reindicizzata: {}", vaultName, report);
        return report;
    }

    /**
     * Indicizza una pagina e ritorna il numero di chunk.
     *
     * Come per le nuove versioni dei documenti (lezione 10): PRIMA salviamo i chunk nuovi,
     * POI togliamo quelli della versione precedente (content_hash diverso). Se gli embedding
     * falliscono a metà, la pagina vecchia resta e il suo hash non viene aggiornato:
     * alla prossima reindicizzazione verrà ritentata.
     */
    int indexPage(String path, String markdown, String hash) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        String documentId = DOCUMENT_ID_PREFIX + path;

        // 0. Avanzi di un tentativo interrotto: chunk di QUESTA versione salvati, ma hash non registrato
        //    (es. backend spento tra vectorStore.add e stateRepository.save). Senza questa pulizia
        //    la pagina finirebbe due volte in pgvector. I chunk "buoni" della versione precedente
        //    hanno un altro content_hash e restano al loro posto.
        vectorStore.delete(b.and(
                b.eq(PdfProcessingService.METADATA_DOCUMENT_ID, documentId),
                b.eq(METADATA_CONTENT_HASH, hash)).build());

        // 1. Prima i chunk nuovi...
        List<Document> chunks = splitter.apply(toDocuments(path, markdown, hash));
        if (!chunks.isEmpty()) {
            vectorStore.add(chunks);
        }

        // 2. ...poi via quelli della versione precedente
        vectorStore.delete(b.and(
                b.eq(PdfProcessingService.METADATA_DOCUMENT_ID, documentId),
                b.ne(METADATA_CONTENT_HASH, hash)).build());
        return chunks.size();
    }

    List<Document> toDocuments(String path, String markdown, String hash) {
        // Il frontmatter va tolto PRIMA della lettura: MarkdownDocumentReader scambierebbe
        // le righe "---" per separatori e le proprietà finirebbero negli embedding
        MarkdownNote note = MarkdownNote.parse(markdown);
        String pageTitle = pageTitle(path);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put(METADATA_SOURCE_TYPE, SOURCE_TYPE);
        metadata.put(PdfProcessingService.METADATA_DOCUMENT_ID, DOCUMENT_ID_PREFIX + path);
        // Stessa chiave dei file caricati: così la citazione mostra il percorso della pagina
        metadata.put("original_filename", path);
        // Senza "department" nel frontmatter la pagina è pubblica (vedi RagService.isVisibleTo)
        metadata.put(METADATA_DEPARTMENT, note.text("department").orElse(RagService.PUBLIC_DEPARTMENT));
        metadata.put("tags", String.join(",", note.list("tags")));
        metadata.put("wiki_type", note.text("type").orElse(""));
        metadata.put(METADATA_CONTENT_HASH, hash);

        MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                .withHorizontalRuleCreateDocument(true)
                .withIncludeCodeBlock(true)
                .withIncludeBlockquote(true)
                .withAdditionalMetadata(metadata)
                .build();

        // [[concetti/chunking|il chunking]] -> "il chunking": il modello di embedding riceve testo naturale
        byte[] body = WikiLinks.toPlainText(note.body()).getBytes(StandardCharsets.UTF_8);

        List<Document> sections = new ArrayList<>();
        for (Document section : new MarkdownDocumentReader(new ByteArrayResource(body), config).get()) {
            String sectionTitle = String.valueOf(section.getMetadata().getOrDefault("title", pageTitle));
            // Aggiunta rispetto alla lezione: il titolo della pagina e della sezione entrano nel TESTO.
            // MarkdownDocumentReader toglie il titolo dal contenuto; senza, una sezione come
            // "Definizione: è una funzione che..." non direbbe più DI COSA parla.
            String text = "# " + pageTitle + (sectionTitle.equals(pageTitle) ? "" : " › " + sectionTitle)
                    + "\n\n" + section.getText();
            sections.add(Document.builder()
                    .text(text)
                    .metadata(section.getMetadata())
                    .metadata(METADATA_SECTION, sectionTitle)
                    .build());
        }
        return sections;
    }

    private static String pageTitle(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponibile", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
