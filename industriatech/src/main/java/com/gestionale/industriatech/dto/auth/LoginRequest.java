package com.gestionale.industriatech.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Lezione 13: credenziali per il login. */
public record LoginRequest(
        @NotBlank(message = "L'email è obbligatoria") @Size(max = 254) String email,
        @NotBlank(message = "La password è obbligatoria") @Size(max = 128) String password) {

    public LoginRequest {
        email = email == null ? null : email.strip();
    }

    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]"; // mai la password nei log
    }
}
