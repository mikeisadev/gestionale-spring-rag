package com.gestionale.industriatech.dto;

// Risposta inviata al frontend dopo l'upload di un PDF.
// Esempio:
// { "filename": "manuale.pdf", "pages": 15, "chunks": 28, "message": "..." }
public record DocumentUploadResponse(
    String filename,
    int pages,
    int chunks,
    String message
) {}
