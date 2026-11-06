package com.gestionale.industriatech.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.gestionale.industriatech.entity.LogIndicizzazione;

public interface LogIndicizzazioneRepository extends JpaRepository<LogIndicizzazione, Long> {

    // Tutti i log di un documento, in ordine cronologico
    List<LogIndicizzazione> findByDocumentoIdOrderByIdAsc(String documentoId);

    // Lezione 10: quando eliminiamo un documento eliminiamo anche i suoi log.
    // NOTA: qui "documentoId" è l'id del CARICAMENTO (vedi LogIndicizzazione).
    @Transactional
    void deleteByDocumentoId(String documentoId);
}
