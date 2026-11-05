package com.gestionale.industriatech.model;

// Ciclo di vita dell'indicizzazione di un PDF:
// IN_CODA -> IN_ELABORAZIONE -> COMPLETATO (oppure ERRORE)
public enum IndexingStatus {
    IN_CODA,
    IN_ELABORAZIONE,
    COMPLETATO,
    ERRORE
}
