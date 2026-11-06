package com.gestionale.industriatech.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Una chat dell'utente, mostrata nell'elenco a sinistra della pagina Chat.
 *
 * L'id è lo stesso "conversation ID" che usa la memoria di Spring AI:
 * un UUID generato dal frontend (36 caratteri).
 */
@Entity
@Table(name = "conversazioni")
@Getter @Setter
public class Conversazione {

    @Id
    @Column(length = 36)
    private String id;

    // Lezione 13: il proprietario (id dell'account). Solo lui vede e continua la chat.
    // Le chat create prima del login non hanno proprietario: le vedono solo gli amministratori.
    @Column(length = 36)
    private String utenteId;

    // Ricavato dal primo messaggio dell'utente
    @Column(nullable = false, length = 120)
    private String titolo;

    @Column(nullable = false)
    private LocalDateTime creataIl;

    // Aggiornato a ogni messaggio: serve per ordinare le chat dalla più recente
    @Column(nullable = false)
    private LocalDateTime aggiornataIl;

    public Conversazione() {
    }
}
