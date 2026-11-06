package com.gestionale.industriatech.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Storico dei file caricati per il RAG (lezione 7).
 *
 * Prima lo stato di un caricamento viveva solo in memoria (IndexingJob) e spariva
 * al riavvio del backend. Ora ogni caricamento ha una riga in questa tabella,
 * con i suoi log in "log_indicizzazione".
 *
 * L'id è lo stesso del job di indicizzazione (UUID).
 *
 * Lezione 10: una riga = un CARICAMENTO. Più caricamenti possono essere versioni
 * dello stesso documento: le lega il "documentoId", l'ID stabile del documento.
 *
 *   id (caricamento)  documentoId   versione  stato
 *   a1b2...           d-777         1         SOSTITUITO
 *   c3d4...           d-777         2         COMPLETATO   <- quella usata dal chatbot
 */
@Entity
@Table(name = "documenti_indicizzati")
@Getter @Setter
public class DocumentoIndicizzato {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String nomeOriginale;

    // Nome del file salvato in documents/pdf (es. 550e8400-....pdf)
    @Column(length = 64)
    private String nomeSalvato;

    // Lezione 10: ID stabile del documento, uguale per tutte le sue versioni.
    // Lo salviamo anche nei metadati di ogni chunk, con la chiave "document_id".
    @Column(length = 36)
    private String documentoId;

    // Lezione 10: 1 al primo caricamento, poi 2, 3... a ogni nuova versione
    private int versione;

    // Lezione 10: impronta SHA-256 del contenuto (64 caratteri esadecimali).
    // Due file con lo stesso hash sono identici, anche se hanno nomi diversi.
    @Column(length = 64)
    private String hashSha256;

    private Long dimensioneByte;

    // IN_CODA, IN_ELABORAZIONE, COMPLETATO, ERRORE
    // Lezione 10: SOSTITUITO = versione vecchia, rimpiazzata da una più recente
    @Column(nullable = false, length = 20)
    private String stato;

    private int pagine;
    private int chunk;
    private int chunkIndicizzati;

    @Column(length = 1000)
    private String messaggio;

    @Column(nullable = false)
    private LocalDateTime caricatoIl;

    private LocalDateTime iniziatoIl;
    private LocalDateTime terminatoIl;

    public DocumentoIndicizzato() {
    }
}
