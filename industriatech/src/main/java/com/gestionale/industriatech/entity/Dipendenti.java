package com.gestionale.industriatech.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "dipendenti")
@Getter @Setter 
public class Dipendenti {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column 
    private String nome;

    @Column 
    private String cognome;

    // TODO: usare annotazione @Email (dipendenza Validator)
    @Column(unique = true)
    private String email;

    public Dipendenti() {
    }

    public Dipendenti(Long id, String nome, String cognome, String email) {
        this.id = id;
        this.nome = nome;
        this.cognome = cognome;
        this.email = email;
    }

}
