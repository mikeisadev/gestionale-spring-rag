package com.gestionale.industriatech.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lezione 12: una domanda per "Chiedi alla wiki" (massimo 1000 caratteri). */
public record WikiAskRequest(
        @NotBlank(message = "Scrivi una domanda")
        @Size(max = 1000, message = "La domanda può avere al massimo 1000 caratteri")
        String question) {
}
