package com.gestionale.industriatech.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestionale.industriatech.entity.MessaggioChat;

public interface MessaggioChatRepository extends JpaRepository<MessaggioChat, Long> {

    // SELECT * FROM messaggi_chat WHERE conversazione_id = ? ORDER BY id
    List<MessaggioChat> findByConversazioneIdOrderByIdAsc(String conversazioneId);

    // DELETE FROM messaggi_chat WHERE conversazione_id = ?
    void deleteByConversazioneId(String conversazioneId);
}
