package com.gestionale.industriatech.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Una riga di log dell'indicizzazione di un documento (pulsante "Vedi log").
 *
 * Esempio:  12:03:05  INFO  CHUNKING  41 chunk generati (min 210, media 1.180, max 2.040 caratteri)
 */
@Entity
@Table(name = "log_indicizzazione", indexes = @Index(columnList = "documento_id"))
@Getter @Setter
public class LogIndicizzazione {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "documento_id", nullable = false, length = 36)
    private String documentoId;

    @Column(nullable = false)
    private LocalDateTime istante;

    // INFO, WARN, ERROR
    @Column(nullable = false, length = 10)
    private String livello;

    // UPLOAD, LETTURA, CHUNKING, EMBEDDING, FINE
    @Column(nullable = false, length = 20)
    private String fase;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String messaggio;

    public LogIndicizzazione() {
    }
}
