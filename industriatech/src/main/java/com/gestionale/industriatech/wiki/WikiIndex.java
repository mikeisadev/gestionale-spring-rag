package com.gestionale.industriatech.wiki;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toUnmodifiableList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Lezione 12: fotografia IMMUTABILE della wiki: le pagine, come risolvere i link e chi cita chi (backlink).
 *
 * Immutabile = niente setter e collezioni di sola lettura: più richieste possono leggerla
 * contemporaneamente senza problemi di concorrenza. Per aggiornarla se ne crea una nuova (WikiCatalog).
 */
public final class WikiIndex {

    private final Map<String, WikiPage> pagesByPath;
    private final Map<String, List<String>> pathsByFileName;
    private final Map<String, List<String>> backlinks;
    private final Map<String, String> assetsByName; // "schema.png" -> "allegati/schema.png"

    private WikiIndex(Map<String, WikiPage> pagesByPath, Map<String, String> assetsByName) {
        this.pagesByPath = Map.copyOf(pagesByPath);
        this.pathsByFileName = pagesByPath.keySet().stream()
                .collect(groupingBy(WikiIndex::fileNameOf, toUnmodifiableList()));
        this.assetsByName = Map.copyOf(assetsByName);
        this.backlinks = computeBacklinks();
    }

    public static WikiIndex of(Collection<WikiPage> pages) {
        return of(pages, Map.of());
    }

    public static WikiIndex of(Collection<WikiPage> pages, Map<String, String> assetsByName) {
        return new WikiIndex(pages.stream().collect(toMap(WikiPage::path, Function.identity())), assetsByName);
    }

    public Collection<WikiPage> pages() {
        return pagesByPath.values();
    }

    public Optional<WikiPage> page(String path) {
        return Optional.ofNullable(pagesByPath.get(path));
    }

    /** Percorso di un'immagine o di un video incorporato, cercato per nome del file. */
    public Optional<String> asset(String name) {
        return Optional.ofNullable(assetsByName.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * Risolve un link come Obsidian: prima il percorso esatto, poi il nome del file, se è UNICO.
     * Se due pagine in cartelle diverse hanno lo stesso nome il link è ambiguo e non viene risolto:
     * meglio un link mancante che un link sbagliato.
     */
    public Optional<String> resolve(String target) {
        String fileTarget = target.endsWith(".md") ? target : target + ".md";
        if (pagesByPath.containsKey(fileTarget)) {
            return Optional.of(fileTarget);
        }
        List<String> candidates = pathsByFileName.getOrDefault(fileNameOf(fileTarget), List.of());
        return candidates.size() == 1 ? Optional.of(candidates.get(0)) : Optional.empty();
    }

    /** Pagine collegate da questa pagina (solo quelle che esistono). */
    public List<String> outgoingLinksOf(String path) {
        return page(path).stream()
                .flatMap(page -> page.links().stream())
                .filter(link -> !link.embed())
                .flatMap(link -> resolve(link.target()).stream())
                .distinct()
                .toList();
    }

    /** Pagine che citano questa pagina. Calcolate una volta sola, quando l'indice viene creato. */
    public List<String> backlinksOf(String path) {
        return backlinks.getOrDefault(path, List.of());
    }

    private Map<String, List<String>> computeBacklinks() {
        Map<String, List<String>> result = new HashMap<>();
        for (String source : pagesByPath.keySet()) {
            for (String target : outgoingLinksOf(source)) {
                if (!target.equals(source)) {
                    result.computeIfAbsent(target, key -> new ArrayList<>()).add(source);
                }
            }
        }
        result.replaceAll((target, sources) -> sources.stream().sorted().toList());
        return Map.copyOf(result);
    }

    private static String fileNameOf(String path) {
        return path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    }
}
