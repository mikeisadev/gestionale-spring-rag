package com.gestionale.industriatech.dto;

import java.time.LocalDateTime;

// Una bozza email da mostrare all'utente con i pulsanti "Conferma invio" / "Annulla"
public record EmailDraftResponse(
    Long id,
    String to,
    String subject,
    String body,
    String status,
    LocalDateTime createdAt
) {}
