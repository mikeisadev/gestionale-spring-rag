package com.gestionale.industriatech.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestionale.industriatech.dto.auth.AccountResponse;
import com.gestionale.industriatech.dto.auth.AccountUpdateRequest;
import com.gestionale.industriatech.dto.auth.RegisterRequest;
import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.entity.RuoloAccount;
import com.gestionale.industriatech.repository.AccountRepository;

/**
 * Lezione 13: registrazione e gestione degli account.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** "  Mario.Rossi@Azienda.IT " -> "mario.rossi@azienda.it": una sola forma per ogni email. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Registrazione pubblica: il ruolo è SEMPRE USER e il reparto è vuoto.
     * Non c'è nessun parametro per cambiarli: il browser non può chiederli.
     */
    public AccountResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        // 1. Controllo "gentile", per un messaggio chiaro nel caso normale
        if (accountRepository.existsByEmail(email)) {
            throw new AccountConflictException("Esiste già un account con questa email.");
        }
        Account account = newAccount(email, request.password(), request.nome().strip(), RuoloAccount.USER);
        try {
            // 2. saveAndFlush: l'INSERT parte subito, così il vincolo UNIQUE scatta qui dentro.
            //    Due registrazioni contemporanee passano entrambe il controllo 1: la seconda si ferma qui.
            return AccountResponse.from(accountRepository.saveAndFlush(account));
        } catch (DataIntegrityViolationException e) {
            throw new AccountConflictException("Esiste già un account con questa email.");
        }
    }

    /** Usato dalla registrazione e dal bootstrap del primo amministratore. */
    public Account newAccount(String normalizedEmail, String rawPassword, String nome, RuoloAccount ruolo) {
        Account account = new Account();
        account.setEmail(normalizedEmail);
        account.setPasswordHash(passwordEncoder.encode(rawPassword)); // mai in chiaro
        account.setNome(nome);
        account.setRuolo(ruolo);
        account.setAbilitato(true);
        account.setCreatoIl(LocalDateTime.now());
        return account;
    }

    // ========== GESTIONE (solo amministratori, decisione B del piano) ==========

    public List<AccountResponse> list() {
        return accountRepository.findAllByOrderByCreatoIlAsc().stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request, Long currentAdminId) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account non trovato"));

        boolean self = account.getId().equals(currentAdminId);
        if (request.ruolo() != null) {
            RuoloAccount ruolo = RuoloAccount.valueOf(request.ruolo());
            // Un admin non può togliersi il ruolo da solo: il sistema non deve restare senza amministratori
            if (self && ruolo != RuoloAccount.ADMIN) {
                throw new AccountConflictException("Non puoi togliere a te stesso il ruolo di amministratore.");
            }
            account.setRuolo(ruolo);
        }
        if (request.abilitato() != null) {
            if (self && !request.abilitato()) {
                throw new AccountConflictException("Non puoi disabilitare il tuo account.");
            }
            account.setAbilitato(request.abilitato());
        }
        if (request.reparto() != null) {
            account.setReparto(request.reparto().isBlank() ? null : request.reparto().strip());
        }
        return AccountResponse.from(accountRepository.save(account));
    }
}
