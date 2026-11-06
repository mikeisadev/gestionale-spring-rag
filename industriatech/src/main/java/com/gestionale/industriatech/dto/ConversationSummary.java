package com.gestionale.industriatech.dto;

import java.time.LocalDateTime;

// Una riga dell'elenco delle chat
public record ConversationSummary(
    String id,
    String title,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
