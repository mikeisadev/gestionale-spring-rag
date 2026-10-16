package com.gestionale.industriatech.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import com.gestionale.industriatech.entity.Dipendenti;

public interface DipendentiRepository extends JpaRepository<Dipendenti, Long> {

    // SELECT * FROM industriatech.dipendenti WHERE email = :email
    Optional<Dipendenti> findByEmail(String email);
}