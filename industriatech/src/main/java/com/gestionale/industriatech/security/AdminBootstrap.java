package com.gestionale.industriatech.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.gestionale.industriatech.entity.RuoloAccount;
import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.service.AccountService;

/**
 * Lezione 13: crea il PRIMO amministratore all'avvio, dalle variabili d'ambiente
 *
 *   APP_BOOTSTRAP_ADMIN_EMAIL     APP_BOOTSTRAP_ADMIN_PASSWORD (minimo 12 caratteri)
 *
 * - se esiste già almeno un admin non fa nulla (i riavvii non cambiano niente, la password
 *   non viene mai sovrascritta): dopo il primo avvio le variabili si possono togliere;
 * - se l'email è già registrata come USER NON la promuove: qualcuno potrebbe essersi
 *   registrato con quell'email prima di te;
 * - due istanze avviate insieme (VS Code e Docker usano lo stesso MariaDB): una crea
 *   l'account, l'altra trova il vincolo UNIQUE e lascia stare.
 *
 * Nessuna credenziale nel repository, nei log o nei file Docker versionati.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 12;

    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final String email;
    private final String password;

    public AdminBootstrap(AccountRepository accountRepository, AccountService accountService,
                          @Value("${app.bootstrap-admin.email:}") String email,
                          @Value("${app.bootstrap-admin.password:}") String password) {
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.email = AccountService.normalizeEmail(email);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        String result = createFirstAdmin();
        if (result != null) {
            log.info("[AUTH] {}", result);
        }
    }

    /** Ritorna un messaggio per il log (mai la password), oppure null se non c'è nulla da dire. */
    String createFirstAdmin() {
        if (accountRepository.existsByRuolo(RuoloAccount.ADMIN)) {
            return null; // c'è già un amministratore: niente da fare
        }
        if (email.isBlank() || password == null || password.isBlank()) {
            return "Nessun amministratore: imposta APP_BOOTSTRAP_ADMIN_EMAIL e APP_BOOTSTRAP_ADMIN_PASSWORD e riavvia.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "APP_BOOTSTRAP_ADMIN_PASSWORD troppo corta (minimo " + MIN_PASSWORD_LENGTH + " caratteri): amministratore NON creato.";
        }
        if (accountRepository.existsByEmail(email)) {
            return "L'email " + mask(email) + " è già registrata come utente normale: per sicurezza NON viene promossa. "
                    + "Usa un'altra email per l'amministratore iniziale.";
        }
        try {
            accountRepository.saveAndFlush(accountService.newAccount(email, password, "Amministratore", RuoloAccount.ADMIN));
            return "Amministratore iniziale creato: " + mask(email);
        } catch (DataIntegrityViolationException e) {
            return "Amministratore iniziale già creato da un'altra istanza.";
        }
    }

    /** "mario.rossi@azienda.it" -> "m***@azienda.it" */
    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
