package com.gestionale.industriatech.wiki;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import com.gestionale.industriatech.security.UserContext;

/**
 * Lezione 12: converte il Markdown di Obsidian in Markdown "standard" per il web.
 * Così il frontend non deve conoscere la sintassi dei wikilink.
 *
 *   [[Hash|l'hash]]      -> [l'hash](/wiki/page?path=concepts%2FHash.md)
 *   ![[schema.png]]      -> ![schema.png](/api/wiki/assets?name=schema.png)
 *   [[Pagina mancante]]  -> Pagina mancante   (testo semplice)
 */
public final class WikiWebMarkdown {

    /** Solo un NOME di file (niente "/" o "\"), con un'estensione di immagine o video ammessa. */
    public static final Pattern MEDIA_FILE = Pattern.compile("(?i)[^/\\\\]+\\.(png|jpe?g|gif|svg|webp|mp4|webm)");

    private WikiWebMarkdown() {
    }

    public static String convert(String body, WikiIndex index, UserContext user) {
        return WikiLinks.replaceLinks(body, link -> {
            if (link.embed() && MEDIA_FILE.matcher(link.target()).matches()) {
                return "![%s](/api/wiki/assets?name=%s)".formatted(link.displayText(), encode(link.target()));
            }
            // Un link a una pagina che non esiste (o che l'utente non può vedere) resta semplice testo
            return index.resolve(link.target())
                    .flatMap(index::page)
                    .filter(target -> target.isVisibleTo(user))
                    .map(target -> "[%s](/wiki/page?path=%s)".formatted(link.displayText(), encode(target.path())))
                    .orElse(link.displayText());
        });
    }

    private static String encode(String value) {
        // URLEncoder codifica gli spazi come "+": nei link Markdown usiamo "%20"
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
