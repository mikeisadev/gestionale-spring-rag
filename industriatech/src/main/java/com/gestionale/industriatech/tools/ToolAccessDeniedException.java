package com.gestionale.industriatech.tools;

/**
 * Lanciata da uno strumento quando l'utente non ha il permesso di usarlo.
 *
 * Spring AI rimanda al modello il MESSAGGIO dell'eccezione, così l'LLM può spiegare
 * all'utente cosa è successo. Per questo il messaggio deve essere chiaro e non deve
 * contenere dettagli tecnici (query SQL, stack trace, nomi di tabelle).
 */
public class ToolAccessDeniedException extends RuntimeException {

    public ToolAccessDeniedException(String message) {
        super(message);
    }
}
