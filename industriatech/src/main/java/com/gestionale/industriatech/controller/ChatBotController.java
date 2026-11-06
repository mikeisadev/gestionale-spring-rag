package com.gestionale.industriatech.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.ChatMessageResponse;
import com.gestionale.industriatech.dto.ChatRequest;
import com.gestionale.industriatech.dto.ConversationSummary;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.service.ChatService;
import com.gestionale.industriatech.service.ConversationService;

import jakarta.validation.Valid;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
// Lezione 13: niente @CrossOrigin qui, CORS è configurato una volta sola in SecurityConfig
public class ChatBotController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final CurrentUserProvider currentUserProvider;

    public ChatBotController(ChatService chatService, ConversationService conversationService,
                             CurrentUserProvider currentUserProvider) {
        this.chatService = chatService;
        this.conversationService = conversationService;
        this.currentUserProvider = currentUserProvider;
    }

    // Chat con RAG e memoria: la risposta arriva token per token come Server-Sent Events (SSE).
    // @Valid attiva le regole di ChatRequest (@NotBlank, @Size, @Pattern): se una non è
    // rispettata Spring risponde 400 senza nemmeno entrare nel metodo.
    @PostMapping(
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> streamRagMessage(
        @Valid @RequestBody ChatRequest request
    ) {
        return chatService.streamRagMessage(request.message(), request.conversationId())
                .map(token ->
                    ServerSentEvent.<String>builder()
                        .data(token)
                        .build()
                );
    }

    // Elenco delle chat salvate, dalla più recente
    // GET /api/chat/conversations
    @GetMapping("/conversations")
    public List<ConversationSummary> listConversations() {
        return conversationService.listConversations(currentUserProvider.currentUser());
    }

    // Tutti i messaggi di una chat
    // GET /api/chat/conversations/{id}/messages
    @GetMapping("/conversations/{id}/messages")
    public List<ChatMessageResponse> getMessages(@PathVariable String id) {
        return conversationService.getMessages(id, currentUserProvider.currentUser())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat non trovata"));
    }

    // Elimina una chat (archivio + memoria dell'LLM)
    // DELETE /api/chat/conversations/{id}
    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String id) {
        return conversationService.deleteConversation(id, currentUserProvider.currentUser())
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
