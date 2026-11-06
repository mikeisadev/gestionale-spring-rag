package com.gestionale.industriatech.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import com.gestionale.industriatech.dto.auth.AccountUpdateRequest;
import com.gestionale.industriatech.dto.auth.RegisterRequest;
import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.entity.RuoloAccount;
import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.service.AccountConflictException;
import com.gestionale.industriatech.service.AccountService;

/** LIVELLO 1 (lezione 13): AccountService e AdminBootstrap, senza Spring. */
class AccountSecurityTest {

    private final AccountRepository repository = mock(AccountRepository.class);
    private final AccountService service = new AccountService(repository, PasswordEncoderFactories.createDelegatingPasswordEncoder());

    @Test
    void lEmailVieneNormalizzata() {
        assertThat(AccountService.normalizeEmail("  Mario.Rossi@Azienda.IT ")).isEqualTo("mario.rossi@azienda.it");
    }

    @Test
    void dueRegistrazioniContemporaneeNonCreanoDueAccount() {
        // Il controllo "esiste già?" passa per entrambe, ma il vincolo UNIQUE del database ferma la seconda
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_account_email"));

        assertThatThrownBy(() -> service.register(new RegisterRequest("a@azienda.it", "una-password-lunga", "A")))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void unAdminNonPuoTogliersiIlRuoloNeDisabilitarsi() {
        Account admin = account(1L, RuoloAccount.ADMIN);
        when(repository.findById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.update(1L, new AccountUpdateRequest("USER", null, null), 1L))
                .isInstanceOf(AccountConflictException.class);
        assertThatThrownBy(() -> service.update(1L, new AccountUpdateRequest(null, null, false), 1L))
                .isInstanceOf(AccountConflictException.class);
    }

    @Test
    void unAdminAssegnaRepartoERuoloAUnAltroAccount() {
        Account user = account(2L, RuoloAccount.USER);
        when(repository.findById(2L)).thenReturn(Optional.of(user));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var updated = service.update(2L, new AccountUpdateRequest("ADMIN", " HR ", null), 1L);

        assertThat(updated.ruolo()).isEqualTo("ADMIN");
        assertThat(updated.reparto()).isEqualTo("HR");
    }

    // ===== Primo amministratore =====

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(repository, service, email, password);
    }

    @Test
    void creaIlPrimoAmministratoreConLaPasswordCifrata() {
        when(repository.existsByRuolo(RuoloAccount.ADMIN)).thenReturn(false);
        when(repository.existsByEmail("admin@azienda.it")).thenReturn(false);

        String log = bootstrap("Admin@Azienda.it", "una-password-segreta").createFirstAdmin();

        ArgumentCaptor<Account> saved = ArgumentCaptor.forClass(Account.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRuolo()).isEqualTo(RuoloAccount.ADMIN);
        assertThat(saved.getValue().getEmail()).isEqualTo("admin@azienda.it");
        assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}");
        assertThat(log).contains("a***@azienda.it").doesNotContain("una-password-segreta");
    }

    @Test
    void seEsisteGiaUnAmministratoreNonFaNulla() {
        when(repository.existsByRuolo(RuoloAccount.ADMIN)).thenReturn(true);

        bootstrap("admin@azienda.it", "una-password-segreta").createFirstAdmin();

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void nonPromuoveUnEmailGiaRegistrataDaUnUtente() {
        when(repository.existsByRuolo(RuoloAccount.ADMIN)).thenReturn(false);
        when(repository.existsByEmail("admin@azienda.it")).thenReturn(true);

        String log = bootstrap("admin@azienda.it", "una-password-segreta").createFirstAdmin();

        verify(repository, never()).saveAndFlush(any());
        assertThat(log).contains("NON viene promossa");
    }

    @Test
    void passwordCortaOMancanteNonCreaNulla() {
        when(repository.existsByRuolo(RuoloAccount.ADMIN)).thenReturn(false);

        assertThat(bootstrap("admin@azienda.it", "corta").createFirstAdmin()).contains("troppo corta");
        assertThat(bootstrap("", "").createFirstAdmin()).contains("APP_BOOTSTRAP_ADMIN_EMAIL");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void dueIstanzeInsiemeNonFallisconoAllAvvio() {
        when(repository.existsByRuolo(RuoloAccount.ADMIN)).thenReturn(false);
        when(repository.existsByEmail(any())).thenReturn(false);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_account_email"));

        assertThat(bootstrap("admin@azienda.it", "una-password-segreta").createFirstAdmin()).contains("un'altra istanza");
    }

    private static Account account(Long id, RuoloAccount ruolo) {
        Account account = new Account();
        account.setId(id);
        account.setEmail("x" + id + "@azienda.it");
        account.setPasswordHash("{noop}x");
        account.setNome("X");
        account.setRuolo(ruolo);
        account.setCreatoIl(LocalDateTime.now());
        return account;
    }
}
