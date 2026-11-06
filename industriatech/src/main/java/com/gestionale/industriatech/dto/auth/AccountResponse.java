package com.gestionale.industriatech.dto.auth;

import java.time.LocalDateTime;

import com.gestionale.industriatech.entity.Account;

/**
 * Lezione 13: ciò che il frontend può sapere di un account.
 * Mai l'entity: niente hash della password né campi interni.
 */
public record AccountResponse(Long id, String email, String nome, String ruolo, String reparto,
                              boolean abilitato, LocalDateTime creatoIl) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getEmail(), account.getNome(),
                account.getRuolo().name(), account.getReparto(), account.isAbilitato(), account.getCreatoIl());
    }
}
