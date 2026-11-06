package com.gestionale.industriatech.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Bozza di email preparata dall'agente (lezione 6.4, modello "prepara, poi conferma").
 *
 * L'agente può solo CREARE la bozza (stato BOZZA). L'invio avviene quando
 * l'utente preme "Conferma invio" nel gestionale: è sempre una persona a decidere.
 */
@Entity
@Table(name = "bozze_email")
@Getter @Setter
public class BozzaEmail {

    public static final String BOZZA = "BOZZA";
    public static final String INVIATA = "INVIATA";
    public static final String ANNULLATA = "ANNULLATA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Chi l'ha richiesta: solo lui potrà confermarla
    @Column(nullable = false, length = 64)
    private String utenteId;

    @Column(nullable = false)
    private String destinatario;

    @Column(nullable = false)
    private String oggetto;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String testo;

    @Column(nullable = false, length = 10)
    private String stato = BOZZA;

    @Column(nullable = false)
    private LocalDateTime creataIl;

    private LocalDateTime inviataIl;

    public BozzaEmail() {
    }
}
