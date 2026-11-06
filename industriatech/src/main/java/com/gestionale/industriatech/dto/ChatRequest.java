package com.gestionale.industriatech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// { "message": "Quali sono i suoi vantaggi?", "conversationId": "3f2b9c1e-...-..." }
public record ChatRequest(

    @NotBlank(message = "Il messaggio non può essere vuoto")
    @Size(max = 4000, message = "Il messaggio può contenere al massimo 4000 caratteri")
    String message,

    // UUID generato dal frontend per ogni nuova chat.
    // Massimo 36 caratteri: è la lunghezza della colonna conversation_id
    // della tabella SPRING_AI_CHAT_MEMORY (la lezione originale ammetteva fino a 64).
    @NotBlank(message = "conversationId obbligatorio")
    @Pattern(regexp = "[a-zA-Z0-9-]{8,36}", message = "conversationId non valido")
    String conversationId
) {}
