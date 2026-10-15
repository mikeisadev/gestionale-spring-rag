package com.gestionale.industriatech.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestionale.industriatech.entity.Contratti;

public interface ContrattiRepository extends JpaRepository<Contratti, Long> {
    
}
