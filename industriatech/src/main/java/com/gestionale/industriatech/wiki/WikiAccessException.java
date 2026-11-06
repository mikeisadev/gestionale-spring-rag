package com.gestionale.industriatech.wiki;

/**
 * Errore di accesso alla wiki.
 * Il messaggio non contiene MAI percorsi assoluti del server: se l'errore nasce
 * dallo strumento readWikiPage, il messaggio torna al modello (lezione 6).
 */
public class WikiAccessException extends RuntimeException {

    public WikiAccessException(String message) {
        super(message);
    }

    public WikiAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
