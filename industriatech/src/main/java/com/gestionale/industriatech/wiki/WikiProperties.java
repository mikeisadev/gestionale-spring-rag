package com.gestionale.industriatech.wiki;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Lezione 11: dove si trova la LLM Wiki sul disco.
 *
 * @param vaultPath    cartella del vault di Obsidian, ad esempio /Users/mario/llm-wiki
 * @param wikiFolder   sottocartella con le pagine scritte dall'LLM
 * @param maxPageBytes dimensione massima di una pagina: oltre questo limite non la leggiamo
 * @param displayPath  (facoltativo) percorso da cui ricavare il nome mostrato nella pagina.
 *                     Serve in Docker, dove il vault è montato in /app/wiki: senza, il nome sarebbe "wiki"
 */
@ConfigurationProperties("app.wiki")
public record WikiProperties(
        String vaultPath,
        @DefaultValue("wiki") String wikiFolder,
        @DefaultValue("200000") int maxPageBytes,
        String displayPath) {

    public WikiProperties {
        // Questo record viene creato solo se la wiki è configurata (vedi WikiEnabledCondition):
        // se arriviamo qui senza percorso è un errore di configurazione, e lo diciamo subito.
        if (vaultPath == null || vaultPath.isBlank()) {
            throw new IllegalArgumentException("La proprietà app.wiki.vault-path è obbligatoria");
        }
    }

    public Path wikiRoot() {
        return Path.of(vaultPath, wikiFolder).toAbsolutePath().normalize();
    }

    /** Il nome della cartella del vault (es. "Cybersecurity LLM wiki"): lo mostriamo nella pagina senza il percorso completo. */
    public String vaultName() {
        String path = displayPath == null || displayPath.isBlank() ? vaultPath : displayPath;
        return Path.of(path.replaceAll("[/\\\\]+$", "")).getFileName().toString();
    }
}
