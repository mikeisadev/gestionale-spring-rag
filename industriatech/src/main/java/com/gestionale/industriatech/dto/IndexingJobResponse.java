package com.gestionale.industriatech.dto;

import com.gestionale.industriatech.model.IndexingStatus;

// "Fotografia" dello stato di un'indicizzazione, inviata al frontend.
// Le date sono in millisecondi (epoch): in JavaScript basta new Date(valore).
public record IndexingJobResponse(
    String id,
    String filename,
    IndexingStatus status,
    String phase,
    int pages,
    int chunks,
    int chunksIndexed,
    String message,
    long createdAt,
    Long startedAt,
    Long finishedAt
) {}
