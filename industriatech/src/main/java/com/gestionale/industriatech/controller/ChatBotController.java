package com.gestionale.industriatech.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.dto.ChatRequest;
import com.gestionale.industriatech.dto.ChatResponse;
import com.gestionale.industriatech.service.ChatService;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatBotController {

    ChatService chatService;

    public ChatBotController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> rispondiChatBot(
        @RequestBody ChatRequest request
    ) {
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Il messaggio non può essere vuoto"));
        }

        // Qui dobbiamo richiamare tramite API un LLM e ritornare la risposta
        String llmResponse = chatService.sendMessage(request.message());

        return ResponseEntity.ok(
            Map.of(
                "request", "Il tuo prompt: " + request.message(),
                "response", llmResponse
            )
        );
    }
}
