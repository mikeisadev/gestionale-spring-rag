package com.gestionale.industriatech.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.entity.RuoloAccount;

/**
 * Lezione 13: l'utente autenticato come lo vede Spring Security.
 *
 * Viene salvato nella sessione dopo il login: contiene solo ciò che serve
 * (id, email, ruolo, reparto). L'hash della password serve solo durante il login.
 */
public class AccountUserDetails implements UserDetails {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String nome;
    private final RuoloAccount ruolo;
    private final String reparto;
    private final boolean abilitato;

    public AccountUserDetails(Account account) {
        this.id = account.getId();
        this.email = account.getEmail();
        this.passwordHash = account.getPasswordHash();
        this.nome = account.getNome();
        this.ruolo = account.getRuolo();
        this.reparto = account.getReparto();
        this.abilitato = account.isAbilitato();
    }

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public RuoloAccount getRuolo() { return ruolo; }
    public String getReparto() { return reparto; }
    public boolean isAdmin() { return ruolo == RuoloAccount.ADMIN; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(ruolo.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return abilitato;
    }
}
