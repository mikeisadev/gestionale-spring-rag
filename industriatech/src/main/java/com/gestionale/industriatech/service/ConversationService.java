package com.gestionale.industriatech.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.ChatMessageResponse;
import com.gestionale.industriatech.dto.ConversationSummary;
import com.gestionale.industriatech.entity.Conversazione;
import com.gestionale.industriatech.entity.MessaggioChat;
import com.gestionale.industriatech.repository.ConversazioneRepository;
import com.gestionale.industriatech.repository.MessaggioChatRepository;
import com.gestionale.industriatech.security.UserContext;

/**
 * Archivio delle chat (MariaDB): crea le conversazioni, salva TUTTI i messaggi
 * e permette di rivederle o eliminarle.
 *
 * Non va confuso con la ChatMemory di Spring AI, che tiene solo
 * gli ultimi 10 messaggi da rimandare all'LLM.
 */
@Service
public class ConversationService {

    private static final int MAX_TITLE_LENGTH = 60;

    private final ConversazioneRepository conversazioneRepository;
    private final MessaggioChatRepository messaggioChatRepository;
    private final ChatMemory chatMemory;

    public ConversationService(
            ConversazioneRepository conversazioneRepository,
            MessaggioChatRepository messaggioChatRepository,
            ChatMemory chatMemory
    ) {
        this.conversazioneRepository = conversazioneRepository;
        this.messaggioChatRepository = messaggioChatRepository;
        this.chatMemory = chatMemory;
    }

    /**
     * Lezione 13: l'utente può scrivere in questa chat? Sì se è nuova (non esiste) o se è sua.
     *
     * Va controllato PRIMA di salvare: save() di JPA con un ID già esistente AGGIORNA la riga.
     * Senza questo controllo, chi conosce l'ID della chat di un collega potrebbe "prendersela".
     * Rispondiamo "non trovata" anche quando esiste: non riveliamo gli ID degli altri.
     */
    public void ensureWritable(String conversationId, UserContext user) {
        conversazioneRepository.findById(conversationId)
                .filter(conversazione -> !user.userId().equals(conversazione.getUtenteId()))
                .ifPresent(conversazione -> {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat non trovata");
                });
    }

    // Salva la domanda dell'utente. Se la conversazione non esiste ancora la crea,
    // usando la domanda come titolo e l'utente come proprietario (lezione 13).
    @Transactional
    public void saveUserMessage(String conversationId, String text, String userId) {
        LocalDateTime now = LocalDateTime.now();

        // Prima proviamo ad aggiornare la data con un UPDATE diretto (nessuna lettura prima).
        // MariaDB 11.6+ (innodb_snapshot_isolation=ON) annulla una transazione che legge
        // una riga e poi la modifica dopo che un'altra transazione l'ha già cambiata.
        // Lezione 13: l'UPDATE vale solo per le chat dell'utente.
        int updated = conversazioneRepository.touchOwned(conversationId, userId, now);

        if (updated == 0) {
            // Seconda difesa (la prima è ensureWritable): se la chat esiste è di un altro
            if (conversazioneRepository.existsById(conversationId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat non trovata");
            }
            // Primo messaggio: la conversazione non esiste, la creiamo
            Conversazione nuova = new Conversazione();
            nuova.setId(conversationId);
            nuova.setUtenteId(userId);
            nuova.setTitolo(toTitle(text));
            nuova.setCreataIl(now);
            nuova.setAggiornataIl(now);
            conversazioneRepository.save(nuova);
        }

        saveMessage(conversationId, "user", text, now);
    }

    // Salva la risposta completa dell'assistente (a fine streaming)
    @Transactional
    public void saveAssistantMessage(String conversationId, String text) {
        LocalDateTime now = LocalDateTime.now();
        saveMessage(conversationId, "assistant", text, now);
        conversazioneRepository.touch(conversationId, now);
    }

    // Elenco delle chat dell'utente, dalla più recente.
    // Gli amministratori vedono anche quelle create prima del login (senza proprietario).
    public List<ConversationSummary> listConversations(UserContext user) {
        List<Conversazione> conversazioni = user.admin()
                ? conversazioneRepository.findByUtenteIdOrUtenteIdIsNullOrderByAggiornataIlDesc(user.userId())
                : conversazioneRepository.findByUtenteIdOrderByAggiornataIlDesc(user.userId());
        return conversazioni.stream()
                .map(c -> new ConversationSummary(c.getId(), c.getTitolo(), c.getCreataIl(), c.getAggiornataIl()))
                .toList();
    }

    // Tutti i messaggi di una chat (Optional vuoto se la chat non esiste O non è dell'utente)
    public Optional<List<ChatMessageResponse>> getMessages(String conversationId, UserContext user) {
        if (!canRead(conversationId, user)) {
            return Optional.empty();
        }

        return Optional.of(messaggioChatRepository.findByConversazioneIdOrderByIdAsc(conversationId).stream()
                .map(m -> new ChatMessageResponse(m.getRuolo(), m.getContenuto(), m.getCreatoIl()))
                .toList());
    }

    // Elimina la chat dall'archivio E dalla memoria dell'LLM
    @Transactional
    public boolean deleteConversation(String conversationId, UserContext user) {
        if (!canRead(conversationId, user)) {
            return false;
        }

        messaggioChatRepository.deleteByConversazioneId(conversationId);
        conversazioneRepository.deleteById(conversationId);
        chatMemory.clear(conversationId); // DELETE su SPRING_AI_CHAT_MEMORY (PostgreSQL)
        return true;
    }

    // Lezione 13: le proprie chat; gli admin anche quelle senza proprietario (create prima del login)
    private boolean canRead(String conversationId, UserContext user) {
        return conversazioneRepository.findById(conversationId)
                .map(c -> user.userId().equals(c.getUtenteId()) || (c.getUtenteId() == null && user.admin()))
                .orElse(false);
    }

    private void saveMessage(String conversationId, String role, String text, LocalDateTime when) {
        MessaggioChat messaggio = new MessaggioChat();
        messaggio.setConversazioneId(conversationId);
        messaggio.setRuolo(role);
        messaggio.setContenuto(text);
        messaggio.setCreatoIl(when);
        messaggioChatRepository.save(messaggio);
    }

    // "Parlami di Spring Boot e dei suoi starter..." -> "Parlami di Spring Boot e dei suoi starter…"
    private static String toTitle(String text) {
        String singleLine = text.strip().replaceAll("\\s+", " ");
        return singleLine.length() <= MAX_TITLE_LENGTH
                ? singleLine
                : singleLine.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }
}
