package com.gestionale.industriatech.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.ConversationSummary;
import com.gestionale.industriatech.entity.Conversazione;
import com.gestionale.industriatech.entity.RuoloAccount;
import com.gestionale.industriatech.repository.ConversazioneRepository;
import com.gestionale.industriatech.service.ChatService;
import com.gestionale.industriatech.service.ConversationService;
import com.gestionale.industriatech.support.TestUsers;

/**
 * LIVELLO 2 (lezione 13): una chat è del suo proprietario. Serve MariaDB (integrazione).
 *   ./mvnw test -DexcludedTestGroups=llm -Dtest=ConversationOwnershipTest
 *
 * Nessuna chiamata al modello: i controlli scattano prima.
 */
@Tag("integration")
@SpringBootTest
class ConversationOwnershipTest {

    @Autowired ConversationService conversationService;
    @Autowired ConversazioneRepository conversazioneRepository;
    @Autowired ChatService chatService;

    private final UserContext anna = new UserContext("990101", "IT", false);
    private final UserContext bruno = new UserContext("990102", "IT", false);
    private final UserContext admin = new UserContext("990103", null, true);

    private final String chatDiAnna = UUID.randomUUID().toString();
    private final String chatSenzaProprietario = UUID.randomUUID().toString();

    @AfterEach
    void cleanUp() {
        conversationService.deleteConversation(chatDiAnna, anna);
        conversationService.deleteConversation(chatSenzaProprietario, admin);
        TestUsers.logout();
    }

    private static boolean contains(java.util.List<ConversationSummary> list, String id) {
        return list.stream().anyMatch(c -> c.id().equals(id));
    }

    @Test
    void unUtenteNonVedeNonLeggeENonEliminaLaChatDiUnAltro() {
        conversationService.saveUserMessage(chatDiAnna, "Quante ferie ho?", anna.userId());

        assertThat(contains(conversationService.listConversations(anna), chatDiAnna)).isTrue();
        assertThat(contains(conversationService.listConversations(bruno), chatDiAnna)).isFalse();
        assertThat(conversationService.getMessages(chatDiAnna, anna)).isPresent();
        assertThat(conversationService.getMessages(chatDiAnna, bruno)).isEmpty();      // -> 404
        assertThat(conversationService.deleteConversation(chatDiAnna, bruno)).isFalse(); // -> 404
        assertThat(conversationService.getMessages(chatDiAnna, anna)).isPresent();      // ancora lì
    }

    @Test
    void unUtenteNonPuoContinuareNePrendersiLaChatDiUnAltro() {
        conversationService.saveUserMessage(chatDiAnna, "Quante ferie ho?", anna.userId());

        // 1. Dal ChatService (il percorso di POST /api/chat): rifiutato PRIMA dello streaming
        TestUsers.loginAs(TestUsers.account(990102, RuoloAccount.USER, "IT")); // Bruno
        assertThatThrownBy(() -> chatService.streamRagMessage("E le mie?", chatDiAnna))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Chat non trovata");

        // 2. Anche chiamando direttamente il salvataggio, il proprietario non cambia
        assertThatThrownBy(() -> conversationService.saveUserMessage(chatDiAnna, "presa!", bruno.userId()))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(conversazioneRepository.findById(chatDiAnna).orElseThrow().getUtenteId()).isEqualTo(anna.userId());
    }

    @Test
    void leChatCreatePrimaDelLoginLeVedonoSoloGliAmministratori() {
        Conversazione vecchia = new Conversazione();
        vecchia.setId(chatSenzaProprietario);
        vecchia.setTitolo("Chat di prova senza proprietario");
        vecchia.setCreataIl(LocalDateTime.now());
        vecchia.setAggiornataIl(LocalDateTime.now());
        conversazioneRepository.save(vecchia);

        assertThat(contains(conversationService.listConversations(anna), chatSenzaProprietario)).isFalse();
        assertThat(conversationService.getMessages(chatSenzaProprietario, anna)).isEmpty();
        assertThat(contains(conversationService.listConversations(admin), chatSenzaProprietario)).isTrue();
        assertThat(conversationService.getMessages(chatSenzaProprietario, admin)).isPresent();
        // Nessuno può continuarla (non ha un proprietario a cui appartenere)
        assertThatThrownBy(() -> conversationService.ensureWritable(chatSenzaProprietario, admin))
                .isInstanceOf(ResponseStatusException.class);
    }
}
