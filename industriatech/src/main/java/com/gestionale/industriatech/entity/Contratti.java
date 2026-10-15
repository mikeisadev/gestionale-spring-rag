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

@Entity
@Table(name="contratti")
@Getter @Setter 
public class Contratti {
 
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column 
    private String tipo;

    @Column 
    private Float stipendio;

    @Column 
    private LocalDateTime dataStipulazione;

    @Column 
    private LocalDateTime dataTermine;

    // TODO: implementare la relazione con i dipendenti
    // RAGIONAMENTO: ogni contratto è associato a un dipendente
    // MySQL, MariaDB, Pg = DATABASE RELAZIONALI o RDBMS (relational database management system)

    public Contratti() {
    }

    public Contratti(Long id, String tipo, Float stipendio, LocalDateTime dataStipulazione, LocalDateTime dataTermine) {
        this.id = id;
        this.tipo = tipo;
        this.stipendio = stipendio;
        this.dataStipulazione = dataStipulazione;
        this.dataTermine = dataTermine;
    }

}
