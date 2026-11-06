package com.gestionale.industriatech.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Lezione 13: massimo N tentativi di login al minuto per la STESSA email (decisione C del piano).
 *
 * Senza un limite, una password si può provare all'infinito (attacco "a forza bruta").
 * Stesso meccanismo del limite della chat (lezione 8): un "secchio" di gettoni per email.
 *
 * NOTA: è in memoria. Con più istanze del backend ognuna ha il suo contatore.
 */
@Component
public class LoginRateLimiter {

    private final int attemptsPerMinute;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public LoginRateLimiter(@Value("${app.security.login-attempts-per-minute:5}") int attemptsPerMinute) {
        this.attemptsPerMinute = attemptsPerMinute;
    }

    public boolean tryConsume(String normalizedEmail) {
        return buckets.computeIfAbsent(normalizedEmail, key -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(attemptsPerMinute)
                        .refillGreedy(attemptsPerMinute, Duration.ofMinutes(1))
                        .build())
                .build())
                .tryConsume(1);
    }
}
