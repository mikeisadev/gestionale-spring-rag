package com.gestionale.industriatech.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.WikiAskRequest;
import com.gestionale.industriatech.dto.WikiAskResponse;
import com.gestionale.industriatech.dto.WikiPageView;
import com.gestionale.industriatech.security.ChatRateLimiter;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.security.InputGuard;
import com.gestionale.industriatech.security.UserContext;
import com.gestionale.industriatech.wiki.WikiAskService;
import com.gestionale.industriatech.wiki.WikiCatalog;
import com.gestionale.industriatech.wiki.WikiEnabledCondition;

import jakarta.validation.Valid;

/**
 * Lezione 12: "Chiedi alla wiki".  POST /api/wiki/ask  { "question": "..." }
 *
 * Valgono gli stessi guardrail della chat (lezione 8): limite di lunghezza, limite di
 * domande al minuto (ogni domanda costa token) e InputGuard sugli attacchi più banali.
 */
@RestController
@RequestMapping("/api/wiki")
// Lezione 13: niente @CrossOrigin qui, CORS è configurato una volta sola in SecurityConfig
@Conditional(WikiEnabledCondition.class)
public class WikiAskController {

    private static final Logger log = LoggerFactory.getLogger(WikiAskController.class);

    private final WikiAskService askService;
    private final WikiCatalog catalog;
    private final CurrentUserProvider currentUserProvider;
    private final ChatRateLimiter rateLimiter;
    private final InputGuard inputGuard;

    public WikiAskController(WikiAskService askService, WikiCatalog catalog, CurrentUserProvider currentUserProvider,
                             ChatRateLimiter rateLimiter, InputGuard inputGuard) {
        this.askService = askService;
        this.catalog = catalog;
        this.currentUserProvider = currentUserProvider;
        this.rateLimiter = rateLimiter;
        this.inputGuard = inputGuard;
    }

    @PostMapping("/ask")
    public WikiAskResponse ask(@Valid @RequestBody WikiAskRequest request) {
        UserContext user = currentUserProvider.currentUser();

        if (!rateLimiter.tryConsume(user.userId())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Hai fatto troppe domande in poco tempo: riprova tra un minuto.");
        }
        InputGuard.GuardResult guard = inputGuard.check(request.question());
        if (guard.blocked()) {
            log.warn("[GUARDRAIL] Domanda alla wiki bloccata: {}", guard.reason());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Non posso eseguire questa richiesta.");
        }

        long start = System.currentTimeMillis();
        WikiAskService.Answer answer = askService.ask(guard.text(), user);

        var index = catalog.index();
        var pagesRead = answer.pagesRead().stream()
                .flatMap(path -> index.page(path).stream())
                .map(page -> new WikiPageView.Link(page.path(), page.title()))
                .toList();
        return new WikiAskResponse(answer.text(), pagesRead, System.currentTimeMillis() - start);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Errore"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleInvalid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("Domanda non valida");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }
}
