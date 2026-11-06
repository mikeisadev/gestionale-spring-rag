package com.gestionale.industriatech.security;

import java.io.IOException;
import java.util.Objects;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.repository.AccountRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lezione 13 (fase 2): l'utente nella sessione è una "fotografia" scattata al login.
 * Se intanto un amministratore gli cambia ruolo o reparto, o disabilita l'account,
 * la fotografia è vecchia. Questo filtro la aggiorna dal database a ogni richiesta:
 *
 *   - account eliminato o disabilitato -> sessione chiusa, la richiesta diventa anonima (401);
 *   - ruolo o reparto cambiati         -> nuova fotografia, salvata nella sessione.
 *
 * Costo: una query per richiesta autenticata. Per un gestionale interno va bene;
 * con molto traffico si userebbe una cache con una durata breve.
 *
 * NON è un @Component: lo registra SecurityConfig dentro la catena di Spring Security
 * (dopo aver letto la sessione, prima dei controlli sui ruoli).
 */
public class AccountRefreshFilter extends OncePerRequestFilter {

    private final AccountRepository accountRepository;
    private final SecurityContextRepository securityContextRepository;

    public AccountRefreshFilter(AccountRepository accountRepository, SecurityContextRepository securityContextRepository) {
        this.accountRepository = accountRepository;
        this.securityContextRepository = securityContextRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AccountUserDetails snapshot) {
            Account account = accountRepository.findById(snapshot.getId()).orElse(null);

            if (account == null || !account.isAbilitato()) {
                // Account eliminato o disabilitato: fuori subito, anche con una sessione ancora valida
                SecurityContextHolder.clearContext();
                if (request.getSession(false) != null) {
                    request.getSession(false).invalidate();
                }
            } else if (account.getRuolo() != snapshot.getRuolo()
                    || !Objects.equals(account.getReparto(), snapshot.getReparto())) {
                AccountUserDetails fresh = new AccountUserDetails(account);
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(fresh, null, fresh.getAuthorities()));
                SecurityContextHolder.setContext(context);
                securityContextRepository.saveContext(context, request, response);
            }
        }
        chain.doFilter(request, response);
    }
}
