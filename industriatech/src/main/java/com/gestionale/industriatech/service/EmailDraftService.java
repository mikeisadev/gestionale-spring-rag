package com.gestionale.industriatech.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.EmailDraftResponse;
import com.gestionale.industriatech.entity.BozzaEmail;
import com.gestionale.industriatech.repository.BozzaEmailRepository;

/**
 * Bozze email: l'agente le PREPARA, l'utente le CONFERMA.
 *
 * Difese contro l'"Excessive Agency" (OWASP LLM03:2026):
 *   1. l'agente non può inviare: crea solo bozze;
 *   2. solo destinatari del dominio aziendale (app.email.allowed-domain);
 *   3. solo chi ha richiesto la bozza può confermarla o annullarla.
 */
@Service
public class EmailDraftService {

    private static final Logger log = LoggerFactory.getLogger(EmailDraftService.class);
    private static final Pattern SIMPLE_EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$");

    private final BozzaEmailRepository bozzaEmailRepository;
    private final JavaMailSender mailSender;
    private final String allowedDomain;
    private final String from;

    public EmailDraftService(
            BozzaEmailRepository bozzaEmailRepository,
            JavaMailSender mailSender,
            @Value("${app.email.allowed-domain:azienda.it}") String allowedDomain,
            @Value("${app.email.from:assistente@azienda.it}") String from
    ) {
        this.bozzaEmailRepository = bozzaEmailRepository;
        this.mailSender = mailSender;
        this.allowedDomain = allowedDomain.toLowerCase(Locale.ROOT);
        this.from = from;
    }

    // Chiamato dallo strumento dell'agente: crea SOLO la bozza
    @Transactional
    public long createDraft(String userId, String to, String subject, String body) {
        String recipient = to == null ? "" : to.trim().toLowerCase(Locale.ROOT);

        if (!SIMPLE_EMAIL.matcher(recipient).matches()) {
            throw new IllegalArgumentException("Indirizzo email del destinatario non valido.");
        }
        // Il controllo è sul dominio esatto: "x@azienda.it.sito-falso.com" non passa
        if (!recipient.endsWith("@" + allowedDomain)) {
            throw new IllegalArgumentException(
                    "Puoi preparare email solo verso indirizzi aziendali @" + allowedDomain + ".");
        }

        BozzaEmail bozza = new BozzaEmail();
        bozza.setUtenteId(userId);
        bozza.setDestinatario(recipient);
        bozza.setOggetto(subject == null || subject.isBlank() ? "(senza oggetto)" : subject.trim());
        bozza.setTesto(body == null ? "" : body.trim());
        bozza.setCreataIl(LocalDateTime.now());

        long id = bozzaEmailRepository.save(bozza).getId();
        log.info("Bozza email n. {} creata per {} (destinatario {})", id, userId, recipient);
        return id;
    }

    public List<EmailDraftResponse> pendingDrafts(String userId) {
        return bozzaEmailRepository.findByUtenteIdAndStatoOrderByIdDesc(userId, BozzaEmail.BOZZA).stream()
                .map(EmailDraftService::toResponse)
                .toList();
    }

    // Chiamato dal pulsante "Conferma invio" (endpoint REST), MAI dall'agente
    @Transactional
    public EmailDraftResponse send(long draftId, String userId) {
        BozzaEmail bozza = findOwnDraft(draftId, userId);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(bozza.getDestinatario());
        message.setSubject(bozza.getOggetto());
        message.setText(bozza.getTesto());

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            log.error("Invio della bozza {} fallito", draftId, ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Server email non raggiungibile");
        }

        bozza.setStato(BozzaEmail.INVIATA);
        bozza.setInviataIl(LocalDateTime.now());
        log.info("Bozza email n. {} inviata da {}", draftId, userId);
        return toResponse(bozzaEmailRepository.save(bozza));
    }

    @Transactional
    public void discard(long draftId, String userId) {
        BozzaEmail bozza = findOwnDraft(draftId, userId);
        bozza.setStato(BozzaEmail.ANNULLATA);
        bozzaEmailRepository.save(bozza);
    }

    // La bozza deve esistere, essere ancora una bozza e appartenere all'utente
    private BozzaEmail findOwnDraft(long draftId, String userId) {
        BozzaEmail bozza = bozzaEmailRepository.findById(draftId)
                .filter(b -> b.getUtenteId().equals(userId))   // 404 anche se è di un altro: non riveliamo che esiste
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bozza non trovata"));

        if (!BozzaEmail.BOZZA.equals(bozza.getStato())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La bozza è già stata inviata o annullata");
        }
        return bozza;
    }

    private static EmailDraftResponse toResponse(BozzaEmail b) {
        return new EmailDraftResponse(b.getId(), b.getDestinatario(), b.getOggetto(), b.getTesto(), b.getStato(), b.getCreataIl());
    }
}
