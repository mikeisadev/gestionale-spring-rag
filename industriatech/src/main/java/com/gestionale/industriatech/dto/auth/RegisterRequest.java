package com.gestionale.industriatech.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Lezione 13: i SOLI campi accettati alla registrazione.
 * Se il browser invia anche "ruolo": "ADMIN" o "id", Jackson li ignora: non esistono qui.
 */
public record RegisterRequest(
        @NotBlank(message = "L'email è obbligatoria")
        @Email(message = "Email non valida")
        @Size(max = 254, message = "Email troppo lunga")
        String email,

        @NotBlank(message = "La password è obbligatoria")
        @Size(min = 12, max = 128, message = "La password deve avere da 12 a 128 caratteri")
        String password,

        @NotBlank(message = "Il nome è obbligatorio")
        @Size(max = 100, message = "Il nome può avere al massimo 100 caratteri")
        String nome) {

    // Gli spazi copiati per sbaglio ("  mario@azienda.it ") vanno tolti PRIMA della validazione @Email
    public RegisterRequest {
        email = email == null ? null : email.strip();
    }

    // toString senza password: se il record finisse in un log, la password non comparirebbe
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", nome=" + nome + "]";
    }
}
