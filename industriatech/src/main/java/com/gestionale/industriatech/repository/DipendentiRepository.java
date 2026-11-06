package com.gestionale.industriatech.repository;

import org.springframework.data.jpa.repository.Query;

import java.util.List;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.gestionale.industriatech.entity.Dipendenti;

public interface DipendentiRepository extends JpaRepository<Dipendenti, Long> {

    // SELECT * FROM industriatech.dipendenti WHERE email = :email
    Optional<Dipendenti> findByEmail(String email);

    // Lezione 6 - usati dagli strumenti dell'agente (tools/EmployeeTools.java)

    // SELECT * FROM dipendenti WHERE LOWER(reparto) = LOWER(:reparto) LIMIT 50
    List<Dipendenti> findTop50ByRepartoIgnoreCase(String reparto);

    // Cerca anche per nome COMPLETO: "Paolo Neri" (nome + cognome) oppure "Neri Paolo" (cognome + nome).
    // Con un semplice "nome LIKE %testo% OR cognome LIKE %testo%" la ricerca "Paolo Neri" non troverebbe nulla.
    @Query("""
            SELECT d FROM Dipendenti d
            WHERE LOWER(CONCAT(d.nome, ' ', d.cognome)) LIKE LOWER(CONCAT('%', :testo, '%'))
               OR LOWER(CONCAT(d.cognome, ' ', d.nome)) LIKE LOWER(CONCAT('%', :testo, '%'))
            ORDER BY d.cognome, d.nome""")
    List<Dipendenti> cercaPerNome(String testo);

    // Elenco dei reparti esistenti, senza doppioni
    @Query("SELECT DISTINCT d.reparto FROM Dipendenti d WHERE d.reparto IS NOT NULL AND d.reparto <> '' ORDER BY d.reparto")
    List<String> findDistinctReparti();
}