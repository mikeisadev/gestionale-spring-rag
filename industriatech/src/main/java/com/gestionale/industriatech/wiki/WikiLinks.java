package com.gestionale.industriatech.wiki;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lezione 11: legge e trasforma i wikilink di Obsidian.
 * Le righe dentro i blocchi di codice non vengono mai toccate.
 */
public final class WikiLinks {

    // (!?)              incorporamento facoltativo: ![[immagine.png]]
    // ([^\[\]|#\r\n]+)  destinazione: qualsiasi carattere tranne [ ] | # e gli a capo
    // (?:#(...))?       sezione facoltativa
    // (?:\|(...))?      testo alternativo facoltativo
    private static final Pattern WIKI_LINK = Pattern.compile(
            "(!?)\\[\\[([^\\[\\]|#\\r\\n]+)(?:#([^\\[\\]|\\r\\n]*))?(?:\\|([^\\[\\]\\r\\n]*))?]]");

    private static final Pattern CODE_FENCE = Pattern.compile("^\\s*(```|~~~)");

    private WikiLinks() {
    }

    /** Tutti i wikilink del testo, nell'ordine in cui compaiono. */
    public static List<WikiLink> extract(String markdown) {
        return lines(markdown).stream()
                .filter(line -> !line.code())
                .flatMap(line -> WIKI_LINK.matcher(line.text()).results())
                .map(WikiLinks::toLink)
                .toList();
    }

    /** Testo pulito per gli embedding: [[Pagina|testo]] diventa "testo", gli incorporamenti spariscono. */
    public static String toPlainText(String markdown) {
        return replaceLinks(markdown, link -> link.embed() ? "" : link.displayText());
    }

    /** Sostituisce ogni wikilink con il testo restituito dalla funzione. */
    public static String replaceLinks(String markdown, Function<WikiLink, String> replacement) {
        return lines(markdown).stream()
                .map(line -> line.code()
                        ? line.text()
                        // quoteReplacement: un "$" nel testo non viene scambiato per un gruppo della regex
                        : WIKI_LINK.matcher(line.text())
                                .replaceAll(match -> Matcher.quoteReplacement(replacement.apply(toLink(match)))))
                .collect(Collectors.joining());
    }

    private static WikiLink toLink(MatchResult match) {
        return new WikiLink(
                match.group(2).strip(),
                blankToNull(match.group(3)),
                blankToNull(match.group(4)),
                !match.group(1).isEmpty());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Divide il testo in righe (ognuna con il suo "a capo") e segna quelle dentro i blocchi di codice. */
    private static List<Line> lines(String markdown) {
        List<Line> lines = new ArrayList<>();
        boolean insideCodeBlock = false;
        for (String text : markdown.split("(?<=\\n)")) {
            boolean fence = CODE_FENCE.matcher(text).find();
            lines.add(new Line(text, insideCodeBlock || fence));
            if (fence) {
                insideCodeBlock = !insideCodeBlock;
            }
        }
        return lines;
    }

    private record Line(String text, boolean code) {
    }
}
