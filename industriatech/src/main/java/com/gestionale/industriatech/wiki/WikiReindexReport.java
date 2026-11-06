package com.gestionale.industriatech.wiki;

/** Lezione 11: esito di una reindicizzazione: pagine aggiornate, invariate, rimosse e non leggibili. */
public record WikiReindexReport(int pages, int updated, int unchanged, int removed, int failed, int chunks) {
}
