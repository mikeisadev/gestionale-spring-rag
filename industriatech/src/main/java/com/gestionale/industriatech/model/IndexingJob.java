package com.gestionale.industriatech.model;

import java.util.UUID;

import com.gestionale.industriatech.dto.IndexingJobResponse;

/**
 * Stato di un'indicizzazione in corso.
 *
 * Viene SCRITTO dal thread che indicizza il PDF e LETTO dalle richieste HTTP
 * del frontend, cioè da thread diversi. Per questo tutti i metodi sono
 * "synchronized": un solo thread alla volta può leggere o modificare l'oggetto,
 * così il frontend non vede mai uno stato a metà.
 */
public class IndexingJob {

    private final String id = UUID.randomUUID().toString();
    private final String filename;
    private final long createdAt = System.currentTimeMillis();

    private IndexingStatus status = IndexingStatus.IN_CODA;
    private String phase = "In coda";
    private int pages;
    private int chunks;
    private int chunksIndexed;
    private String message = "In attesa di essere elaborato";
    private Long startedAt;
    private Long finishedAt;

    public IndexingJob(String filename) {
        this.filename = filename;
    }

    public String getId() {
        return id;
    }

    public synchronized void start() {
        status = IndexingStatus.IN_ELABORAZIONE;
        phase = "Avvio";
        startedAt = System.currentTimeMillis();
        message = "Indicizzazione in corso";
    }

    public synchronized void setPhase(String phase) {
        this.phase = phase;
    }

    public synchronized void setPages(int pages) {
        this.pages = pages;
    }

    public synchronized void setChunks(int chunks) {
        this.chunks = chunks;
    }

    public synchronized void addIndexedChunks(int count) {
        this.chunksIndexed += count;
    }

    public synchronized void complete(String message) {
        status = IndexingStatus.COMPLETATO;
        phase = "Completato";
        this.message = message;
        finishedAt = System.currentTimeMillis();
    }

    public synchronized void fail(String message) {
        status = IndexingStatus.ERRORE;
        phase = "Errore";
        this.message = message;
        finishedAt = System.currentTimeMillis();
    }

    public synchronized IndexingJobResponse toResponse() {
        return new IndexingJobResponse(
                id, filename, status, phase, pages, chunks, chunksIndexed,
                message, createdAt, startedAt, finishedAt
        );
    }
}
