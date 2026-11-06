package com.gestionale.industriatech.service;

/**
 * Lezione 10: il caricamento o l'eliminazione non si può fare per lo stato attuale
 * dei documenti (file già caricato, indicizzazione in corso...).
 * Il controller la trasforma in 409 Conflict.
 */
public class DocumentConflictException extends RuntimeException {

    public DocumentConflictException(String message) {
        super(message);
    }
}
