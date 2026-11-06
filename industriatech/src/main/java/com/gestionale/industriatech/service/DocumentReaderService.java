package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Lezione 10: sceglie il reader di Spring AI in base all'estensione del file.
 *
 *   .pdf                          -> PagePdfDocumentReader (un Document per pagina, con il numero di pagina)
 *   .md                           -> MarkdownDocumentReader (un Document per titolo/paragrafo)
 *   .txt                          -> TextReader (un unico Document)
 *   .docx .pptx .xlsx .odt ...    -> TikaDocumentReader (un unico Document con tutto il testo)
 *
 * Tutti i reader restituiscono una List<Document>: il resto della pipeline
 * (chunking, embedding, salvataggio) non cambia.
 */
@Service
public class DocumentReaderService {

    /** Formati accettati dall'upload. Il frontend mostra la stessa lista. */
    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "pdf",
            "docx", "doc", "odt", "rtf",   // documenti di testo
            "pptx", "odp",                 // presentazioni
            "xlsx", "ods",                 // fogli di calcolo
            "html", "htm",                 // pagine web salvate
            "md", "txt"                    // testo semplice
    );

    /** "Regolamento Ferie.DOCX" -> "docx"; senza estensione -> "" */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(String filename) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(filename));
    }

    /** Solo i PDF hanno le pagine: per gli altri formati il numero di pagina non esiste. */
    public static boolean hasPages(String filename) {
        return "pdf".equals(extensionOf(filename));
    }

    public List<Document> read(Path file) {
        return readerFor(file).get();
    }

    /** Il nome del reader usato, per i log di indicizzazione. */
    public String readerName(Path file) {
        return readerFor(file).getClass().getSimpleName();
    }

    private DocumentReader readerFor(Path file) {
        // FileSystemResource: i file sono in documents/pdf, non in src/main/resources
        Resource resource = new FileSystemResource(file);

        return switch (extensionOf(file.getFileName().toString())) {
            case "pdf" -> new PagePdfDocumentReader(resource,
                    PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build());

            case "md" -> new MarkdownDocumentReader(resource,
                    MarkdownDocumentReaderConfig.builder()
                            .withHorizontalRuleCreateDocument(true) // una riga "---" separa i documenti
                            .withIncludeCodeBlock(true)
                            .withIncludeBlockquote(true)
                            .build());

            case "txt" -> new TextReader(resource); // UTF-8 per default

            // Apache Tika riconosce da solo il formato (DOCX, PPTX, XLSX, ODT, RTF, HTML...)
            default -> new TikaDocumentReader(resource);
        };
    }
}
