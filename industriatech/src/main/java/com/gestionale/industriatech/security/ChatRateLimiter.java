package com.gestionale.industriatech.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * QUINTO STRATO dei guardrail (lezione 8): limite di messaggi al minuto per ogni utente.
 *
 * Bucket4j usa l'algoritmo "token bucket": ogni utente ha un secchiello con N gettoni;
 * ogni messaggio ne consuma uno; i gettoni si ricaricano col tempo.
 * Secchiello vuoto = troppi messaggi -> 429 Too Many Requests.
 *
 * Protegge il budget da un utente malintenzionato o da un bug del frontend in loop.
 * NOTA: i secchielli sono in memoria; con più istanze del backend servirebbe un archivio
 * condiviso (es. Redis) o un API gateway.
 */
@Component
public class ChatRateLimiter {

    private final int messagesPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public ChatRateLimiter(@Value("${app.guardrails.rate-limit.messages-per-minute:10}") int messagesPerMinute) {
        this.messagesPerMinute = messagesPerMinute;
    }

    /** true = il messaggio può passare; false = limite raggiunto. */
    public boolean tryConsume(String userId) {
        return buckets.computeIfAbsent(userId, id -> newBucket()).tryConsume(1);
    }

    public int messagesPerMinute() {
        return messagesPerMinute;
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(messagesPerMinute)
                        .refillGreedy(messagesPerMinute, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
