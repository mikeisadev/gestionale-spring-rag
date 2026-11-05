package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;

/**
 * Prima vera classe del RAG: legge un PDF e lo prepara per il Vector Store.
 *
 * 1. extractText()    -> un Document per ogni pagina del PDF
 * 2. splitDocuments() -> divide le pagine in chunk più piccoli
 * 3. testEmbedding()  -> TEMPORANEO: mostra come una frase diventa un vettore
 */
@Service
public class PdfProcessingService {

    // Spring AI crea l'EmbeddingModel in base a spring.ai.model.embedding
    // (nel nostro progetto: Ollama con bge-m3, vettori da 1024 valori)
    private final EmbeddingModel embeddingModel;

    public PdfProcessingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    // ========== 1. ESTRAZIONE DEL TESTO ==========
    public List<Document> extractText(
            Path pdfPath,
            String storedFilename,
            String originalFilename
    ) {
        // Un Document per ogni pagina del PDF
        PdfDocumentReaderConfig config =
                PdfDocumentReaderConfig.builder()
                        .withPagesPerDocument(1)
                        .build();

        // FileSystemResource: il PDF è in documents/pdf, non in src/main/resources
        PagePdfDocumentReader reader =
                new PagePdfDocumentReader(
                        new FileSystemResource(pdfPath),
                        config
                );

        List<Document> pages = reader.read();

        List<Document> result = new ArrayList<>();

        for (Document page : pages) {
            // Saltiamo le pagine senza testo (pagine bianche o solo immagini):
            // non c'è niente da cercare e creerebbero chunk vuoti.
            if (page.getText() == null || page.getText().isBlank()) {
                continue;
            }

            // Copiamo i metadati del reader e aggiungiamo i nostri
            Map<String, Object> metadata =
                    new HashMap<>(page.getMetadata());

            metadata.put("source", storedFilename);
            metadata.put("original_filename", originalFilename);

            result.add(
                new Document(page.getText(), metadata)
            );
        }

        // ---------- TEMPORANEO: stampa delle pagine estratte ----------
        // Serve solo per vedere cosa produce il reader. Lo toglieremo più avanti.
        for (int i = 0; i < pages.size(); i++) {
            Document page = pages.get(i);

            System.out.println("\n==============================");
            System.out.println("PAGINA: " + (i + 1));
            System.out.println("==============================");
            System.out.println(page.getText());
            System.out.println("\nMETADATI:");
            System.out.println(page.getMetadata());
        }
        // ---------------------------------------------------------------

        return result;
    }

    // ========== 2. CHUNKING ==========
    public List<Document> splitDocuments(
            List<Document> documents
    ) {
        // Valori iniziali didattici: andranno calibrati sui documenti reali.
        TokenTextSplitter splitter =
                TokenTextSplitter.builder()
                        .withChunkSize(500)            // dimensione target in token
                        .withMinChunkSizeChars(200)    // minimo di caratteri per un chunk
                        .withMinChunkLengthToEmbed(20) // sotto i 20 caratteri il chunk viene scartato
                        .withMaxNumChunks(10000)       // numero massimo di chunk
                        .withKeepSeparator(true)       // mantiene gli a capo
                        .build();

        // apply() prende i documenti originali e restituisce i chunk.
        // Ogni chunk eredita i metadati della sua pagina.
        List<Document> chunks = splitter.apply(documents);

        // ---------- TEMPORANEO: stampa dei chunk generati ----------
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);

            System.out.println("\n==============================");
            System.out.println("CHUNK: " + (i + 1));
            System.out.println("==============================");
            System.out.println(chunk.getText());
            System.out.println("\nMETADATI:");
            System.out.println(chunk.getMetadata());
        }
        // ------------------------------------------------------------

        return chunks;
    }

    // ========== 3. TEMPORANEO: IL PRIMO EMBEDDING ==========
    // Serve solo a capire il meccanismo: nel flusso reale gli embedding
    // li genera il VectorStore da solo con vectorStore.add(chunks).
    public Map<String, Object> testEmbedding() {
        String text = "Spring Boot è un framework Java.";

        // Questa chiamata va al servizio di embedding (Ollama), non è un calcolo locale di Java
        float[] vector = embeddingModel.embed(text);

        float[] firstValues = Arrays.copyOf(vector, 10);

        System.out.println("EMBEDDING GENERATO:");
        System.out.println("Dimensioni: " + vector.length);
        System.out.println("Primi valori: " + Arrays.toString(firstValues));

        return Map.of(
            "testo", text,
            "dimensioni", vector.length,
            "primiValori", firstValues
        );
    }
}
