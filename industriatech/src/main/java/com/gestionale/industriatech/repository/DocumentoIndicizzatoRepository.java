package com.gestionale.industriatech.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestionale.industriatech.entity.DocumentoIndicizzato;

public interface DocumentoIndicizzatoRepository extends JpaRepository<DocumentoIndicizzato, String> {

    // Storico dal caricamento più recente
    List<DocumentoIndicizzato> findAllByOrderByCaricatoIlDesc();

    // Caricamenti rimasti "a metà" (es. backend riavviato durante l'indicizzazione)
    List<DocumentoIndicizzato> findByStatoIn(List<String> stati);

    boolean existsByNomeSalvato(String nomeSalvato);

    // Lezione 10: esiste già un file con lo stesso contenuto (stesso hash)?
    List<DocumentoIndicizzato> findByHashSha256AndStatoIn(String hashSha256, List<String> stati);

    // Lezione 10: caricamenti con lo stesso nome (possibili versioni dello stesso documento)
    List<DocumentoIndicizzato> findByNomeOriginaleIgnoreCaseAndStatoIn(String nomeOriginale, List<String> stati);

    // Lezione 10: tutte le versioni (e i tentativi falliti) di un documento
    List<DocumentoIndicizzato> findByDocumentoId(String documentoId);

    // Lezione 10: righe create prima che esistessero documentoId e hash
    List<DocumentoIndicizzato> findByDocumentoIdIsNull();
}
