package com.gestionale.industriatech.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.ChatRequest;
import com.gestionale.industriatech.service.ChatService;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatBotController {

    ChatService chatService;

    public ChatBotController(ChatService chatService) {
        this.chatService = chatService;
    }

    // Chat con RAG: la risposta arriva token per token come Server-Sent Events (SSE)
    @PostMapping(
        produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<String>> streamRagMessage(
        @RequestBody ChatRequest request
    ) {
        if (request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Il messaggio non può essere vuoto"
            );
        }

        // Prima: chatService.sendMessage(...) -> risposta completa in JSON, senza RAG.
        // Ora: ricerca semantica + LLM, e ogni token diventa un evento SSE.
        return chatService.streamRagMessage(request.message())
                .map(token ->
                    ServerSentEvent.<String>builder()
                        .data(token)
                        .build()
                );
    }
}
