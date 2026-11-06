package com.gestionale.industriatech.support;

import java.time.LocalDateTime;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.entity.RuoloAccount;
import com.gestionale.industriatech.security.AccountUserDetails;

/**
 * Lezione 13: utenti finti per i test che chiamano i service direttamente (senza HTTP).
 *
 * Dalla lezione 13 l'utente arriva dal SecurityContext: senza un utente "loggato" ChatService
 * si rifiuta di partire. Questi metodi mettono un utente nel SecurityContext del thread del test.
 */
public final class TestUsers {

    private TestUsers() {
    }

    public static AccountUserDetails account(long id, RuoloAccount ruolo, String reparto) {
        Account account = new Account();
        account.setId(id);
        account.setEmail("test-" + id + "@example.com");
        account.setPasswordHash("{noop}non-usata");
        account.setNome("Utente di test " + id);
        account.setRuolo(ruolo);
        account.setReparto(reparto);
        account.setCreatoIl(LocalDateTime.now());
        return new AccountUserDetails(account);
    }

    /** "Effettua il login" con questo account nel thread corrente. */
    public static void loginAs(AccountUserDetails user) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities()));
    }

    public static void logout() {
        SecurityContextHolder.clearContext();
    }
}
