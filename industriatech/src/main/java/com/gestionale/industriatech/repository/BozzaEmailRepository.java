package com.gestionale.industriatech.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestionale.industriatech.entity.BozzaEmail;

public interface BozzaEmailRepository extends JpaRepository<BozzaEmail, Long> {

    // Le bozze di un utente in un certo stato, dalla più recente
    List<BozzaEmail> findByUtenteIdAndStatoOrderByIdDesc(String utenteId, String stato);
}
