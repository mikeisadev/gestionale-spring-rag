package com.gestionale.industriatech.service;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.dto.SemanticSearchResult;

/**
 * Ricerca semantica nel database vettoriale.
 *
 * Il database vettoriale NON genera risposte: recupera i chunk più vicini
 * per significato alla domanda. La risposta la scrive l'LLM (vedi ChatService).
 */
@Service
public class RagService {

    private final VectorStore vectorStore;

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<Document> search(
            String query,
            int topK
    ) {
        SearchRequest searchRequest =
                SearchRequest.builder()
                        .query(query)              // domanda da cercare
                        .topK(topK)                // massimo numero di chunk da recuperare
                        .similarityThreshold(0.5)  // soglia minima di similarità (da calibrare)
                        .build();

        // Qui Spring AI genera l'embedding della domanda con l'EmbeddingModel
        // e cerca in pgvector i vettori più vicini (distanza del coseno).
        return vectorStore.similaritySearch(searchRequest);
    }

    public List<SemanticSearchResult> searchDocuments(
            String query,
            int topK
    ) {
        return search(query, topK)
                .stream()
                .map(document ->
                        new SemanticSearchResult(
                                document.getText(),
                                document.getMetadata()
                        )
                )
                .toList();
    }

    // Costruisce il contesto testuale da inviare all'LLM insieme alla domanda
    public String buildContext(String question) {
        List<Document> documents = search(question, 5);

        if (documents.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();

        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();

            // Con withPagesPerDocument(1) il reader salva la pagina in "page_number"
            // (e non in "start_page_number" come indicato nella lezione originale).
            Object page = metadata.getOrDefault(
                    "page_number",
                    metadata.get("start_page_number")
            );

            context.append("\n\n--- DOCUMENTO ---\n");
            context.append("Fonte: ")
                    .append(metadata.get("original_filename"))
                    .append("\n");
            context.append("Pagina: ")
                    .append(page)
                    .append("\n");
            context.append("Contenuto:\n");
            context.append(document.getText());
        }

        return context.toString();
    }
}
