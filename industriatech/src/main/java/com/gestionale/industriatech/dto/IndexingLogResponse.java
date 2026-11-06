package com.gestionale.industriatech.dto;

import java.time.LocalDateTime;

// Una riga di log mostrata dal pulsante "Vedi log"
public record IndexingLogResponse(
    LocalDateTime time,
    String level,
    String phase,
    String message
) {}
