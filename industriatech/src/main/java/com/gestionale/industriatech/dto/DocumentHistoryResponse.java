package com.gestionale.industriatech.dto;

import java.time.LocalDateTime;

// Una riga dello storico dei file caricati
public record DocumentHistoryResponse(
    String id,          // id del caricamento (per i log)
    String documentId,  // lezione 10: ID stabile del documento (per DELETE /api/documents/{documentId})
    int version,        // lezione 10: 1, 2, 3...
    String filename,
    Long sizeBytes,
    String status,
    int pages,
    int chunks,
    int chunksIndexed,
    String message,
    LocalDateTime uploadedAt,
    LocalDateTime startedAt,
    LocalDateTime finishedAt
) {}
