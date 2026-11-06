package com.gestionale.industriatech.wiki;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.gestionale.industriatech.security.UserContext;

/**
 * Lezione 12: gli UNICI due strumenti di "Chiedi alla wiki": cercare e leggere.
 * Nessuno strumento può scrivere, e nessuno usa il database vettoriale.
 *
 * Lavorano sull'indice in memoria (WikiCatalog), non sul disco: un percorso inventato
 * dal modello non è nell'indice e non arriva mai al file system.
 */
@Component
@Conditional(WikiEnabledCondition.class)
public class WikiAskTools {

    /** Chiave del ToolContext con l'elenco delle pagine lette (lo mostriamo sotto la risposta). */
    public static final String PAGES_READ = "pagesRead";

    private static final Logger log = LoggerFactory.getLogger(WikiAskTools.class);

    private static final int MAX_SEARCH_RESULTS = 8;
    private static final int MAX_CHARACTERS = 12_000;

    private final WikiCatalog catalog;
    private final WikiSearchService searchService;

    public WikiAskTools(WikiCatalog catalog, WikiSearchService searchService) {
        this.catalog = catalog;
        this.searchService = searchService;
    }

    @Tool(description = "Cerca nella wiki per parole chiave. Restituisce percorso, titolo ed estratto delle pagine più pertinenti.")
    public List<SearchHit> searchWiki(
            @ToolParam(description = "Parole chiave da cercare, ad esempio: ransomware backup") String query,
            ToolContext toolContext) {
        List<SearchHit> hits = searchService.search(query, MAX_SEARCH_RESULTS, user(toolContext));
        log.info("[WIKI-ASK] searchWiki(\"{}\") -> {} risultati", query, hits.size());
        return hits;
    }

    @Tool(description = """
            Legge il testo completo di una pagina della wiki. Usa il percorso restituito da searchWiki,
            oppure index.md, oppure il nome di un collegamento [[...]].""")
    public String readWikiPage(
            @ToolParam(description = "Percorso della pagina, ad esempio concepts/Ransomware.md, oppure index.md") String path,
            ToolContext toolContext) {

        WikiIndex index = catalog.index();
        // Percorso esatto, oppure nome del collegamento risolto come fa Obsidian
        Optional<WikiPage> page = index.page(path).or(() -> index.resolve(path).flatMap(index::page))
                // Una pagina di un altro reparto è "non trovata": non riveliamo nemmeno che esiste
                .filter(found -> found.isVisibleTo(user(toolContext)));

        if (page.isEmpty()) {
            log.info("[WIKI-ASK] readWikiPage(\"{}\") -> non trovata", path);
            // Un suggerimento invece di un'eccezione: il modello può correggersi e riprovare
            return "Pagina non trovata: usa searchWiki per trovare il percorso corretto.";
        }

        WikiPage found = page.get();
        pagesRead(toolContext).add(found.path());
        log.info("[WIKI-ASK] readWikiPage(\"{}\")", found.path());
        return """
                <pagina_wiki percorso="%s">
                %s
                </pagina_wiki>""".formatted(found.path(), truncate(neutralizeTags(found.body())));
    }

    private static UserContext user(ToolContext toolContext) {
        return (UserContext) toolContext.getContext().get(UserContext.KEY);
    }

    @SuppressWarnings("unchecked")
    private static List<String> pagesRead(ToolContext toolContext) {
        Object list = toolContext.getContext().get(PAGES_READ);
        return list instanceof List<?> ? (List<String>) list : new java.util.ArrayList<>();
    }

    private static String truncate(String text) {
        return text.length() > MAX_CHARACTERS ? text.substring(0, MAX_CHARACTERS) + "\n[pagina troncata]" : text;
    }

    private static String neutralizeTags(String text) {
        return text.replace("<", "‹").replace(">", "›");
    }
}
