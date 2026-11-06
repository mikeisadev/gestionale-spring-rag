package com.gestionale.industriatech.wiki;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Lezione 11: una pagina Markdown divisa in proprietà (il frontmatter YAML) e corpo del testo.
 *
 *   ---                      <- frontmatter: proprietà della pagina
 *   type: concept
 *   tags: [crittografia]
 *   ---
 *   # Hash                   <- corpo: il testo vero e proprio
 */
public record MarkdownNote(Map<String, Object> properties, String body) {

    private static final Logger log = LoggerFactory.getLogger(MarkdownNote.class);

    /** Il frontmatter è il blocco tra due righe "---" all'inizio del file. */
    private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\R(.*?)\\R---[ \\t]*(?:\\R|\\z)", Pattern.DOTALL);

    public MarkdownNote {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
    }

    public static MarkdownNote parse(String markdown) {
        // Alcuni editor mettono all'inizio del file il carattere invisibile BOM (﻿)
        String text = markdown.startsWith("﻿") ? markdown.substring(1) : markdown;
        Matcher matcher = FRONTMATTER.matcher(text);
        if (!matcher.find()) {
            return new MarkdownNote(Map.of(), text);
        }
        return new MarkdownNote(parseYaml(matcher.group(1)), text.substring(matcher.end()));
    }

    /** Valore testuale di una proprietà, ad esempio "department: HR". */
    public Optional<String> text(String key) {
        return Optional.ofNullable(properties.get(key))
                .map(value -> String.valueOf(value).trim())
                .filter(value -> !value.isEmpty());
    }

    /** Valori di una proprietà lista, ad esempio "tags: [rag, spring-ai]". Accetta anche un valore singolo. */
    public List<String> list(String key) {
        if (properties.get(key) instanceof Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(value -> String.valueOf(value).trim())
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        return text(key).map(List::of).orElse(List.of());
    }

    private static Map<String, Object> parseYaml(String yamlText) {
        // SafeConstructor crea solo mappe, liste e valori semplici: MAI oggetti Java arbitrari
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        try {
            if (!(yaml.load(yamlText) instanceof Map<?, ?> map)) {
                return Map.of();
            }
            Map<String, Object> properties = new LinkedHashMap<>();
            // Una proprietà senza valore (es. "updated:") per noi è come se non ci fosse
            map.forEach((key, value) -> {
                if (value != null) {
                    properties.put(String.valueOf(key), value);
                }
            });
            return properties;
        } catch (YAMLException e) {
            log.warn("Frontmatter non valido, la pagina viene letta senza proprietà: {}", e.getMessage());
            return Map.of();
        }
    }
}
