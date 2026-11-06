package com.gestionale.industriatech.security;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Il "canarino" (lezione 8.6): una parola in codice inventata, inserita nel system prompt.
 *
 * Non ha alcun significato e il modello non ha motivo di usarla. Se compare in una
 * risposta, qualcuno è riuscito a far ripetere al modello le sue istruzioni:
 * registriamo l'evento e interrompiamo la risposta.
 *
 * Lezione 9: nel test della lezione 8 la parola, che sta in fondo al system prompt,
 * è arrivata solo DOPO tutte le altre righe. Ora riconosciamo anche le frasi del
 * system prompt ("impronte"): la risposta si interrompe alla prima riga copiata.
 *
 * Il valore cambia a ogni avvio, così non può finire in documenti o esempi.
 */
@Component
public class PromptCanary {

    // Righe del system prompt abbastanza lunghe da non comparire per caso in una risposta normale
    private static final int MIN_FINGERPRINT_LENGTH = 40;

    private final String value = "CANARINO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    private final List<String> fingerprints;

    // Con due costruttori Spring non sa quale usare: @Autowired indica quello per l'applicazione
    @Autowired
    public PromptCanary(@Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {
        this(readLines(systemPrompt));
    }

    // Costruttore per i test: le "impronte" si passano direttamente
    PromptCanary(List<String> systemPromptLines) {
        this.fingerprints = systemPromptLines.stream()
                .map(String::strip)
                .filter(line -> line.length() >= MIN_FINGERPRINT_LENGTH)
                .filter(line -> !line.contains("{canary}"))
                .toList();
    }

    public String value() {
        return value;
    }

    /** true se il testo contiene la parola in codice o una frase del system prompt. */
    public boolean leakedIn(CharSequence text) {
        String answer = text.toString();
        return answer.contains(value) || fingerprints.stream().anyMatch(answer::contains);
    }

    /** Quante frasi del system prompt compaiono nel testo (usato dai test d'attacco). */
    public long leakedLineCount(CharSequence text) {
        String answer = text.toString();
        return fingerprints.stream().filter(answer::contains).count();
    }

    public String redact(String text) {
        return text.replace(value, "[parola di controllo rimossa]");
    }

    private static List<String> readLines(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossibile leggere il system prompt", e);
        }
    }
}
