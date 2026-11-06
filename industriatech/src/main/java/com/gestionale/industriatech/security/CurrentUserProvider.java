package com.gestionale.industriatech.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Restituisce l'utente corrente.
 *
 * Lezione 13: niente più "utente demo" da application.properties. L'utente arriva dalla
 * SESSIONE (login con Spring Security), aggiornata dal database da AccountRefreshFilter.
 * Mai da body, query string o header della richiesta: il client non può sceglierlo.
 *
 * ATTENZIONE: il SecurityContext vive nel thread della richiesta HTTP. Va letto lì
 * (ad esempio all'inizio di ChatService) e poi passato agli altri thread: agli strumenti
 * arriva con il ToolContext (lezione 6).
 */
@Component
public class CurrentUserProvider {

    public UserContext currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AccountUserDetails account)) {
            // "Fail closed": senza utente autenticato non si va avanti (SecurityConfig dovrebbe già averlo impedito)
            throw new IllegalStateException("Nessun utente autenticato");
        }
        return new UserContext(String.valueOf(account.getId()), account.getReparto(), account.isAdmin());
    }
}
