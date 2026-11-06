package com.gestionale.industriatech.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.dto.SemanticSearchResult;
import com.gestionale.industriatech.security.UserContext;

/**
 * Ricerca semantica nel database vettoriale.
 *
 * Il database vettoriale NON genera risposte: recupera i chunk più vicini
 * per significato alla domanda. La risposta la scrive l'LLM (vedi ChatService).
 */
@Service
public class RagService {

    /**
     * Lezione 11: reparto dei contenuti visibili a tutti.
     * Le pagine della wiki senza "department" nel frontmatter ricevono questo valore.
     */
    public static final String PUBLIC_DEPARTMENT = "PUBBLICO";

    private final VectorStore vectorStore;

    public RagService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Lezione 11: ricerca che rispetta i reparti.
     *
     * Le pagine della wiki possono dichiarare "department: HR" nel frontmatter. Un utente
     * di un altro reparto non deve ritrovarle nella ricerca (e lo strumento readWikiPage
     * fa lo stesso controllo).
     *
     * Versione semplice: chiediamo a pgvector qualche chunk in più e scartiamo in Java quelli
     * non visibili. Con il login (prompt 13) il filtro potrà passare direttamente a pgvector
     * (filterExpression), così topK resta preciso anche con molti contenuti riservati.
     */
    public List<Document> search(String query, int topK, UserContext user) {
        return search(query, topK * 2).stream()
                .filter(document -> isVisibleTo(document, user))
                .limit(topK)
                .toList();
    }

    /**
     * Un chunk è visibile se non ha un reparto (i file caricati dalla pagina di upload,
     * per ora), se è PUBBLICO, oppure se l'utente può accedere a quel reparto.
     */
    public static boolean isVisibleTo(Document document, UserContext user) {
        Object department = document.getMetadata().get("department");
        return department == null
                || PUBLIC_DEPARTMENT.equals(department)
                || (user != null && user.canAccessDepartment(String.valueOf(department)));
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
            int topK,
            UserContext user
    ) {
        // Lezione 11: anche la ricerca "diretta" rispetta i reparti (prima restituiva tutto)
        return search(query, topK, user)
                .stream()
                .map(document ->
                        new SemanticSearchResult(
                                document.getText(),
                                document.getMetadata()
                        )
                )
                .toList();
    }

    // Lezione 5: trasforma i documenti trovati in testo per il prompt.
    // Ogni documento è racchiuso in un tag <documento> con file e pagina:
    // il modello distingue chiaramente i DATI dalle istruzioni.
    public String formatAsContext(List<Document> documents) {
        return documents.stream()
                .map(document -> """
                        <documento fonte="%s"%s>
                        %s
                        </documento>""".formatted(
                        document.getMetadata().get("original_filename"),
                        pageAttribute(document) + wikiAttributes(document),
                        neutralizeTags(document.getText())))
                .collect(Collectors.joining("\n"));
    }

    // La costante vale "page_number": la chiave usata dal reader dei PDF.
    // Lezione 10: DOCX, Markdown, TXT... non hanno pagine -> niente attributo "pagina".
    private static String pageAttribute(Document document) {
        Object page = document.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
        return page == null ? "" : " pagina=\"" + page + "\"";
    }

    // Lezione 11: i chunk della wiki hanno tipo="wiki" e la sezione della pagina.
    // tipo="wiki" dice al modello che può aprire la pagina intera con readWikiPage.
    // (Anche un file .md caricato dalla pagina di upload ha la fonte in .md: per questo serve "tipo".)
    private static String wikiAttributes(Document document) {
        if (!"wiki".equals(document.getMetadata().get("source_type"))) {
            return "";
        }
        Object section = document.getMetadata().get("section");
        return " tipo=\"wiki\"" + (section == null ? "" : " sezione=\"" + neutralizeTags(String.valueOf(section)).replace("\"", "'") + "\"");
    }

    // Un documento malevolo potrebbe contenere "</documento>" per "uscire" dal tag:
    // sostituiamo i simboli < e > del testo con caratteri simili ma innocui.
    private static String neutralizeTags(String text) {
        return text == null ? "" : text.replace("<", "‹").replace(">", "›");
    }

    // Lezione 3: costruiva il contesto dentro il ChatService.
    // Dalla lezione 5 se ne occupa DocumentContextAdvisor (con formatAsContext).
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
