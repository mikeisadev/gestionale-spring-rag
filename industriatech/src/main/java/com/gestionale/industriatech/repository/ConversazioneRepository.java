package com.gestionale.industriatech.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.gestionale.industriatech.entity.Conversazione;

public interface ConversazioneRepository extends JpaRepository<Conversazione, String> {

    // Lezione 13: solo le chat di un utente, dalla più recente
    List<Conversazione> findByUtenteIdOrderByAggiornataIlDesc(String utenteId);

    // Lezione 13 (amministratori): le proprie chat più quelle create prima del login (senza proprietario)
    List<Conversazione> findByUtenteIdOrUtenteIdIsNullOrderByAggiornataIlDesc(String utenteId);

    // UPDATE diretto, senza leggere prima la riga: evita l'errore di MariaDB
    // "Record has changed since last read" quando due richieste aggiornano la stessa chat.
    // Restituisce quante righe ha aggiornato (0 = la conversazione non esiste ancora).
    @Modifying
    @Query("UPDATE Conversazione c SET c.aggiornataIl = :quando WHERE c.id = :id")
    int touch(String id, LocalDateTime quando);

    // Lezione 13: come touch, ma solo se la chat è dell'utente (0 = non esiste OPPURE è di un altro)
    @Modifying
    @Query("UPDATE Conversazione c SET c.aggiornataIl = :quando WHERE c.id = :id AND c.utenteId = :utenteId")
    int touchOwned(String id, String utenteId, LocalDateTime quando);
}
