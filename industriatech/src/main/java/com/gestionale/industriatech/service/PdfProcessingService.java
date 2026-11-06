package com.gestionale.industriatech.service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

/**
 * Prima vera classe del RAG: legge un documento e lo prepara per il Vector Store.
 *
 * 1. extractText()    -> legge il file (un Document per pagina nei PDF)
 * 2. splitDocuments() -> divide il testo in chunk più piccoli
 * 3. testEmbedding()  -> TEMPORANEO: mostra come una frase diventa un vettore
 *
 * Lezione 10: il nome è rimasto "Pdf" per continuità con le lezioni precedenti,
 * ma ora legge anche DOCX, PPTX, XLSX, Markdown, TXT... (vedi DocumentReaderService).
 */
@Service
public class PdfProcessingService {

    // Parametri del chunking (pubblici: li riportiamo anche nei log di indicizzazione, lezione 7)
    public static final int CHUNK_SIZE = 500;              // dimensione target in token
    public static final int MIN_CHUNK_SIZE_CHARS = 200;    // minimo di caratteri per un chunk
    public static final int MIN_CHUNK_LENGTH_TO_EMBED = 20; // sotto i 20 caratteri il chunk viene scartato
    public static final int MAX_NUM_CHUNKS = 10000;        // numero massimo di chunk

    // Lezione 10: chiavi dei metadati che aggiungiamo a OGNI chunk
    public static final String METADATA_DOCUMENT_ID = "document_id"; // ID stabile del documento
    public static final String METADATA_VERSION = "version";         // 1, 2, 3... a ogni nuova versione

    // Spring AI crea l'EmbeddingModel in base a spring.ai.model.embedding
    // (nel nostro progetto: Ollama con bge-m3, vettori da 1024 valori)
    private final EmbeddingModel embeddingModel;
    private final DocumentReaderService documentReaderService;

    public PdfProcessingService(EmbeddingModel embeddingModel, DocumentReaderService documentReaderService) {
        this.embeddingModel = embeddingModel;
        this.documentReaderService = documentReaderService;
    }

    // ========== 1. ESTRAZIONE DEL TESTO ==========
    public List<Document> extractText(
            Path file,
            String storedFilename,
            String originalFilename,
            String documentId,
            int version
    ) {
        // Il reader giusto per il formato (PDF, DOCX, Markdown...)
        List<Document> sections = documentReaderService.read(file);

        List<Document> result = new ArrayList<>();

        for (Document section : sections) {
            // Saltiamo le parti senza testo (pagine bianche o solo immagini):
            // non c'è niente da cercare e creerebbero chunk vuoti.
            if (section.getText() == null || section.getText().isBlank()) {
                continue;
            }

            // Copiamo i metadati del reader e aggiungiamo i nostri.
            // NOTA: Tika e TextReader scrivono già "source" (il nome del file):
            // lo sovrascriviamo con il nome salvato, come per i PDF.
            Map<String, Object> metadata =
                    new HashMap<>(section.getMetadata());

            metadata.put("source", storedFilename);
            metadata.put("original_filename", originalFilename);
            metadata.put(METADATA_DOCUMENT_ID, documentId);
            metadata.put(METADATA_VERSION, version);

            result.add(
                new Document(section.getText(), metadata)
            );
        }

        // Lezione 10: abbiamo tolto la stampa TEMPORANEA delle pagine (lezione 2).
        // Su file grandi riempiva la console; ora i dati utili sono nei log dello storico.

        return result;
    }

    // ========== 2. CHUNKING ==========
    public List<Document> splitDocuments(
            List<Document> documents
    ) {
        // Valori iniziali didattici: andranno calibrati sui documenti reali.
        TokenTextSplitter splitter =
                TokenTextSplitter.builder()
                        .withChunkSize(CHUNK_SIZE)
                        .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
                        .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
                        .withMaxNumChunks(MAX_NUM_CHUNKS)
                        .withKeepSeparator(true)       // mantiene gli a capo
                        // Lezione 10: da Spring AI 2.1 si potrà aggiungere l'overlap,
                        // cioè ripetere la fine di un chunk all'inizio del successivo:
                        // .withChunkOverlap(50)
                        .build();

        // apply() prende i documenti originali e restituisce i chunk.
        // Ogni chunk eredita i metadati della sua pagina.
        return splitter.apply(documents);
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
