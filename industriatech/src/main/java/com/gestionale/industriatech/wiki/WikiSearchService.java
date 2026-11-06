package com.gestionale.industriatech.wiki;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import com.gestionale.industriatech.security.UserContext;

/**
 * Lezione 12: ricerca per parole chiave con un punteggio semplice e SPIEGABILE, senza database vettoriale.
 *
 * Finché la wiki ha qualche centinaio di pagine, cercare in memoria è istantaneo:
 * l'indice delle 144 pagine della wiki di cybersecurity occupa pochi MB.
 */
@Service
@Conditional(WikiEnabledCondition.class)
public class WikiSearchService {

    // I pesi sono costanti con un nome: per cambiarli non cerchi numeri sparsi nel codice
    private static final int TITLE_PHRASE_SCORE = 15;
    private static final int TITLE_TERM_SCORE = 10;
    private static final int ALIAS_TERM_SCORE = 6;
    private static final int TAG_TERM_SCORE = 5;
    private static final int MAX_BODY_MATCHES = 5;
    private static final int SNIPPET_LENGTH = 180;

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");

    private final WikiCatalog catalog;

    public WikiSearchService(WikiCatalog catalog) {
        this.catalog = catalog;
    }

    /** Cerca solo tra le pagine che l'utente può vedere (reparti, lezione 11). */
    public List<SearchHit> search(String query, int limit, UserContext user) {
        String normalizedQuery = normalize(query).strip();
        List<String> terms = Arrays.stream(NON_WORD.split(normalizedQuery))
                .filter(term -> term.length() >= 2)
                .distinct()
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        return catalog.index().pages().stream()
                .filter(page -> page.isVisibleTo(user))
                .map(page -> new ScoredPage(page, score(page, normalizedQuery, terms)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPage::score).reversed()
                        .thenComparing(scored -> scored.page().title()))
                .limit(limit)
                .map(scored -> new SearchHit(
                        scored.page().path(),
                        scored.page().title(),
                        snippet(scored.page().body(), terms),
                        scored.score()))
                .toList();
    }

    /** Il titolo pesa più degli alias, gli alias più dei tag, i tag più del testo. */
    private static int score(WikiPage page, String normalizedQuery, List<String> terms) {
        String title = normalize(page.title());
        String aliases = normalize(String.join(" ", page.aliases()));
        List<String> tags = page.tags().stream().map(WikiSearchService::normalize).toList();
        String body = normalize(page.body());

        int score = title.contains(normalizedQuery) ? TITLE_PHRASE_SCORE : 0;
        for (String term : terms) {
            score += title.contains(term) ? TITLE_TERM_SCORE : 0;
            score += aliases.contains(term) ? ALIAS_TERM_SCORE : 0;
            score += tags.contains(term) ? TAG_TERM_SCORE : 0;
            // Tetto alle occorrenze: una pagina lunghissima non vince solo perché ripete una parola
            score += Math.min(countOccurrences(body, term), MAX_BODY_MATCHES);
        }
        return score;
    }

    /**
     * La prima riga del testo che contiene uno dei termini cercati, senza la sintassi dei wikilink.
     * Preferiamo le righe di testo ai titoli: "# Ransomware" sotto il titolo "Ransomware" non aggiunge nulla.
     */
    private static String snippet(String body, List<String> terms) {
        List<String> lines = body.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        return lines.stream()
                .filter(line -> !line.startsWith("#"))
                .filter(line -> terms.stream().anyMatch(normalize(line)::contains))
                .findFirst()
                .or(() -> lines.stream().filter(line -> terms.stream().anyMatch(normalize(line)::contains)).findFirst())
                .map(WikiLinks::toPlainText)
                .map(line -> line.length() > SNIPPET_LENGTH ? line.substring(0, SNIPPET_LENGTH) + "…" : line)
                .orElse("");
    }

    private static int countOccurrences(String text, String term) {
        int count = 0;
        for (int index = text.indexOf(term); index >= 0; index = text.indexOf(term, index + term.length())) {
            count++;
        }
        return count;
    }

    /** Minuscole e niente accenti: "Perché" e "perche" diventano uguali. */
    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return ACCENTS.matcher(decomposed).replaceAll("");
    }

    private record ScoredPage(WikiPage page, int score) {
    }
}
