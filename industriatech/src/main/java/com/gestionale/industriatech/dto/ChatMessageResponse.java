package com.gestionale.industriatech.dto;

import java.time.LocalDateTime;

// Un messaggio di una chat salvata: role = "user" oppure "assistant"
public record ChatMessageResponse(
    String role,
    String content,
    LocalDateTime createdAt
) {}
