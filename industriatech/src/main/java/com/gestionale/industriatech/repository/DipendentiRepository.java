package com.gestionale.industriatech.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.gestionale.industriatech.entity.Dipendenti;

public interface DipendentiRepository extends JpaRepository<Dipendenti, Long> {

    
}