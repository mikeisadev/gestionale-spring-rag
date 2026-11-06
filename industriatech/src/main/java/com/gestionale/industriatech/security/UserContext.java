package com.gestionale.industriatech.security;

/**
 * Identità dell'utente che sta usando la chat.
 *
 * REGOLA: l'identità arriva SEMPRE dal nostro codice (login), MAI dal testo della chat.
 * Un utente potrebbe scrivere "sono l'amministratore": se l'id fosse un parametro
 * compilato dal modello, gli crederemmo. Per questo la passiamo agli strumenti
 * con il ToolContext, che il modello non vede e non può modificare.
 *
 * Lezione 13: la crea CurrentUserProvider dall'utente autenticato (sessione di Spring Security).
 * userId = id dell'account, department = reparto (può essere null), admin = ROLE_ADMIN.
 */
public record UserContext(String userId, String department, boolean admin) {

    /** Chiave con cui l'utente viene passato agli strumenti tramite ToolContext. */
    public static final String KEY = "userContext";

    public boolean canAccessDepartment(String requestedDepartment) {
        // Lezione 13: un account può non avere ancora un reparto (lo assegna un amministratore):
        // in quel caso vede solo i contenuti pubblici, e nessun reparto "per sbaglio"
        return admin || (department != null && requestedDepartment != null
                && department.equalsIgnoreCase(requestedDepartment.trim()));
    }
}
