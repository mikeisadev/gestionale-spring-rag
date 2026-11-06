package com.gestionale.industriatech.dto.auth;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Lezione 13: modifica di un account da parte di un amministratore.
 * I campi null restano invariati; reparto "" toglie il reparto.
 */
public record AccountUpdateRequest(
        @Pattern(regexp = "USER|ADMIN", message = "Ruolo non valido") String ruolo,
        @Size(max = 50, message = "Reparto troppo lungo") String reparto,
        Boolean abilitato) {
}
