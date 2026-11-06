package com.gestionale.industriatech.wiki;

import java.util.List;

/**
 * Lezione 11: stato della wiki per la pagina di upload.
 *
 * La reindicizzazione gira IN BACKGROUND (come i PDF dalla lezione 3.1): il frontend
 * chiede questo stato ogni 2 secondi finché "running" è true.
 */
public record WikiStatusResponse(
        boolean enabled,          // false = WIKI_VAULT_PATH non impostata
        String vaultName,         // solo il nome della cartella, mai il percorso completo
        int indexedPages,
        int indexedChunks,
        boolean running,
        int totalPages,
        int processedPages,
        String currentPage,
        Long startedAt,
        Long finishedAt,
        WikiReindexReport lastReport,
        String error,
        List<String> failedPages
) {

    public static WikiStatusResponse disabled() {
        return new WikiStatusResponse(false, null, 0, 0, false, 0, 0, null, null, null, null, null, List.of());
    }
}
