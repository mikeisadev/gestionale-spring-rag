package com.gestionale.industriatech.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Lezione 13: un account per entrare nel gestionale.
 *
 * NON è un Dipendenti: un dipendente è un dato aziendale (nome, reparto, ruolo lavorativo),
 * un account è un'identità con cui ci si autentica. Tenerli separati evita che inserire un
 * dipendente crei, senza volerlo, un modo per entrare nel sistema.
 *
 * Sta su MariaDB (database applicativo), mai su PostgreSQL/pgvector.
 */
@Entity
@Table(name = "account", uniqueConstraints = @UniqueConstraint(name = "uk_account_email", columnNames = "email"))
@Getter @Setter
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sempre normalizzata (spazi tolti, minuscole): "Mario@Azienda.it " -> "mario@azienda.it".
    // Il vincolo UNIQUE del database è l'ultima difesa contro due registrazioni contemporanee.
    @Column(nullable = false, length = 254)
    private String email;

    // Mai la password: solo l'hash, con il prefisso dell'algoritmo (es. "{bcrypt}$2a$10$...")
    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuoloAccount ruolo = RuoloAccount.USER;

    // Reparto per i permessi (strumenti, wiki). Lo assegna un amministratore, mai il browser.
    @Column(length = 50)
    private String reparto;

    @Column(nullable = false)
    private boolean abilitato = true;

    @Column(nullable = false)
    private LocalDateTime creatoIl;

    public Account() {
    }
}
