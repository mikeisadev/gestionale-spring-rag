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

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatBotController {

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
        

        return ResponseEntity.ok(Map.of(
            "reply", "Hai scritto: " + request.message().trim()
        ));
    }

    public record ChatRequest(String message, List<ChatMessage> history) {}

    public record ChatMessage(String role, String content) {}
}
