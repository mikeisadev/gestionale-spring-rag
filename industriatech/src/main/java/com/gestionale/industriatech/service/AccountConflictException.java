package com.gestionale.industriatech.service;

/** Lezione 13: operazione sugli account in conflitto con lo stato attuale (email già usata...). Diventa 409. */
public class AccountConflictException extends RuntimeException {

    public AccountConflictException(String message) {
        super(message);
    }
}
