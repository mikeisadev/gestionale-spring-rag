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
 * Un messaggio dell'archivio delle chat (domanda dell'utente o risposta dell'assistente).
 *
 * ATTENZIONE: questo archivio è diverso dalla memoria di Spring AI.
 * - Memoria (PostgreSQL, SPRING_AI_CHAT_MEMORY): solo gli ultimi 10 messaggi, per l'LLM.
 * - Archivio (MariaDB, messaggi_chat): TUTTI i messaggi, per mostrarli all'utente.
 */
@Entity
@Table(name = "messaggi_chat", indexes = @Index(columnList = "conversazione_id"))
@Getter @Setter
public class MessaggioChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversazione_id", nullable = false, length = 36)
    private String conversazioneId;

    // "user" oppure "assistant" (gli stessi valori usati dal frontend)
    @Column(nullable = false, length = 10)
    private String ruolo;

    // LONGTEXT: le risposte dell'LLM possono essere lunghe.
    // Senza columnDefinition Hibernate 7 su MariaDB creerebbe un "tinytext" (max 255 byte)!
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String contenuto;

    @Column(nullable = false)
    private LocalDateTime creatoIl;

    public MessaggioChat() {
    }
}
