package com.gestionale.industriatech.security;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * PRIMO STRATO dei guardrail (lezione 8): controlla il messaggio dell'utente
 * PRIMA di chiamare il modello. È codice Java normale: veloce, gratuito, facile da testare.
 *
 * ATTENZIONE: le liste di frasi vietate si aggirano facilmente (sinonimi, altre lingue,
 * errori di battitura voluti). Fermano i tentativi più banali: per questo esistono gli altri strati.
 */
@Component
public class InputGuard {

    private static final int MAX_LENGTH = 4_000;

    private static final List<Pattern> SUSPICIOUS_PATTERNS = List.of(
            Pattern.compile("ignora (tutte )?le (istruzioni|regole) precedenti", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore (all )?(previous|prior) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(mostrami|rivelami|stampa) (il )?(tuo )?(system prompt|prompt di sistema)",
                    Pattern.CASE_INSENSITIVE));

    // IBAN italiano e codice fiscale scritti senza spazi (versioni semplificate)
    private static final Pattern IBAN = Pattern.compile("\\bIT\\d{2}[A-Z]\\d{10}[0-9A-Z]{12}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAX_CODE = Pattern.compile("\\b[A-Z]{6}\\d{2}[A-Z]\\d{2}[A-Z]\\d{3}[A-Z]\\b",
            Pattern.CASE_INSENSITIVE);

    public GuardResult check(String message) {
        if (message == null || message.isBlank()) {
            return GuardResult.block("Scrivi una domanda per iniziare.", "messaggio vuoto");
        }

        if (message.length() > MAX_LENGTH) {
            return GuardResult.block("Il messaggio è troppo lungo: massimo " + MAX_LENGTH + " caratteri.",
                    "messaggio troppo lungo");
        }

        if (SUSPICIOUS_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(message).find())) {
            return GuardResult.block("Non posso eseguire questa richiesta.", "frase sospetta (prompt injection)");
        }

        // I dati personali non servono al modello: li oscuriamo prima di inviarli al provider.
        // È la "minimizzazione dei dati" del GDPR: non mandare a un fornitore esterno dati che non gli servono.
        String masked = IBAN.matcher(message).replaceAll("[IBAN OSCURATO]");
        masked = TAX_CODE.matcher(masked).replaceAll("[CODICE FISCALE OSCURATO]");

        return GuardResult.pass(masked, !masked.equals(message));
    }

    /**
     * @param blocked true = rispondiamo subito con "text" senza chiamare il modello
     * @param text    se bloccato: la risposta per l'utente; altrimenti: il messaggio ripulito da inviare al modello
     * @param reason  motivo del blocco (solo per i log)
     * @param masked  true se abbiamo oscurato dei dati personali
     */
    public record GuardResult(boolean blocked, String text, String reason, boolean masked) {

        static GuardResult pass(String sanitizedText, boolean masked) {
            return new GuardResult(false, sanitizedText, null, masked);
        }

        static GuardResult block(String replyToUser, String reason) {
            return new GuardResult(true, replyToUser, reason, false);
        }
    }
}
