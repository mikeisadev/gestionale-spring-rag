package com.gestionale.industriatech.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.dto.auth.AccountResponse;
import com.gestionale.industriatech.dto.auth.LoginRequest;
import com.gestionale.industriatech.dto.auth.RegisterRequest;
import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.security.AccountUserDetails;
import com.gestionale.industriatech.security.LoginRateLimiter;
import com.gestionale.industriatech.service.AccountConflictException;
import com.gestionale.industriatech.service.AccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/**
 * Lezione 13: registrazione, login, logout e profilo. Contratto JSON:
 *
 *   GET  /api/auth/csrf      -> 200 { "token": "..." } e cookie XSRF-TOKEN       (pubblico)
 *   POST /api/auth/register  { email, password, nome } -> 201 account            (pubblico)
 *   POST /api/auth/login     { email, password }       -> 200 account + sessione (pubblico)
 *   POST /api/auth/logout    -> 204, sessione invalidata
 *   GET  /api/auth/me        -> 200 account, oppure 401 se non autenticato
 *
 * Errori: { "message": "..." } con 400 (dati non validi), 401, 409 (email già usata), 429 (troppi tentativi).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    // Stesso messaggio per email inesistente e password sbagliata: non riveliamo quali email esistono
    private static final String BAD_CREDENTIALS = "Email o password non corretti.";

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final LoginRateLimiter loginRateLimiter;

    // Stessi nomi di cookie usati da csrf().spa(): serve solo per CANCELLARE il token a login e logout
    private final CookieCsrfTokenRepository csrfCookies = CookieCsrfTokenRepository.withHttpOnlyFalse();

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository securityContextRepository,
                          AccountService accountService, AccountRepository accountRepository, LoginRateLimiter loginRateLimiter) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.accountService = accountService;
        this.accountRepository = accountRepository;
        this.loginRateLimiter = loginRateLimiter;
    }

    /**
     * Inizializza (o rinnova) il token anti-CSRF. Leggere il token fa scrivere a Spring
     * il cookie XSRF-TOKEN nella risposta; React lo rimanda nell'header X-XSRF-TOKEN.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        AccountResponse account = accountService.register(request);
        log.info("[AUTH] Nuovo account registrato: id {}", account.id()); // niente email né password nei log
        return ResponseEntity.status(HttpStatus.CREATED).body(account);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = AccountService.normalizeEmail(request.email());
        if (!loginRateLimiter.tryConsume(email)) {
            return error(HttpStatus.TOO_MANY_REQUESTS, "Troppi tentativi di accesso: riprova tra un minuto.");
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password()));
        } catch (AuthenticationException e) {
            // Password sbagliata, email inesistente, account disabilitato: per il client è lo stesso errore
            log.info("[AUTH] Login non riuscito ({})", e.getClass().getSimpleName());
            return error(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }

        // Session fixation: se l'attaccante ci ha fatto usare un ID di sessione che conosce,
        // dopo il login quell'ID non vale più. Cambiamo l'ID, i dati della sessione restano.
        if (httpRequest.getSession(false) != null) {
            httpRequest.changeSessionId();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse); // -> sessione HTTP

        // Nuovo utente, nuovo token CSRF: il frontend lo richiede con GET /api/auth/csrf
        csrfCookies.saveToken(null, httpRequest, httpResponse);

        AccountUserDetails principal = (AccountUserDetails) authentication.getPrincipal();
        log.info("[AUTH] Login riuscito: account {}", principal.getId());
        return ResponseEntity.ok(currentAccount(principal));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        // Invalida la sessione e svuota il SecurityContext
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        csrfCookies.saveToken(null, request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountUserDetails principal) {
        return currentAccount(principal);
    }

    // Dati aggiornati dal database (un admin potrebbe aver cambiato reparto o ruolo)
    private AccountResponse currentAccount(AccountUserDetails principal) {
        return accountRepository.findById(principal.getId())
                .map(AccountResponse::from)
                .orElseThrow(() -> new IllegalStateException("Account non più esistente"));
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message));
    }

    @ExceptionHandler(AccountConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(AccountConflictException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("Dati non validi");
        return error(HttpStatus.BAD_REQUEST, message);
    }
}
