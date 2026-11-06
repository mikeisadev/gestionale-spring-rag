package com.gestionale.industriatech.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.dto.EmailDraftResponse;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.service.EmailDraftService;

/**
 * Endpoint usati dall'INTERFACCIA (non dall'agente) per gestire le bozze email.
 * È qui che avviene l'invio vero: dopo un clic dell'utente.
 */
@RestController
@RequestMapping("/api/email-drafts")
// Lezione 13: niente @CrossOrigin qui, CORS è configurato una volta sola in SecurityConfig
public class EmailDraftController {

    private final EmailDraftService emailDraftService;
    private final CurrentUserProvider currentUserProvider;

    public EmailDraftController(EmailDraftService emailDraftService, CurrentUserProvider currentUserProvider) {
        this.emailDraftService = emailDraftService;
        this.currentUserProvider = currentUserProvider;
    }

    // Bozze in attesa di conferma dell'utente corrente
    @GetMapping
    public List<EmailDraftResponse> pendingDrafts() {
        return emailDraftService.pendingDrafts(currentUserProvider.currentUser().userId());
    }

    // Pulsante "Conferma invio"
    @PostMapping("/{id}/send")
    public EmailDraftResponse send(@PathVariable long id) {
        return emailDraftService.send(id, currentUserProvider.currentUser().userId());
    }

    // Pulsante "Annulla"
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable long id) {
        emailDraftService.discard(id, currentUserProvider.currentUser().userId());
        return ResponseEntity.noContent().build();
    }
}
