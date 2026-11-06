package com.gestionale.industriatech.service;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.rag.DocumentContextAdvisor;
import com.gestionale.industriatech.security.ChatRateLimiter;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.security.InputGuard;
import com.gestionale.industriatech.security.PromptCanary;
import com.gestionale.industriatech.security.UserContext;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // Numero di chunk recuperati: fisso nel codice, NON arriva dal frontend (limite di consumo)
    private static final int TOP_K = 5;

    private static final String CANARY_NOTICE =
            "\n\n⚠️ Risposta interrotta dai controlli di sicurezza.";

    // ChatClient del RAG (ChatClientConfig): system prompt, memoria e documenti tramite advisor
    private final ChatClient ragChatClient;

    // ChatClient "semplice", senza advisor: per i due metodi didattici senza RAG.
    // (L'advisor della memoria pretende sempre un conversation ID: qui non ce l'abbiamo.)
    private final ChatClient plainChatClient;

    private final RagService ragService;
    private final ConversationService conversationService;
    private final CurrentUserProvider currentUserProvider;

    // Lezione 8: guardrail
    private final InputGuard inputGuard;
    private final ChatRateLimiter rateLimiter;
    private final PromptCanary canary;

    public ChatService(
            ChatClient ragChatClient,
            ChatClient.Builder builder,
            RagService ragService,
            ConversationService conversationService,
            CurrentUserProvider currentUserProvider,
            InputGuard inputGuard,
            ChatRateLimiter rateLimiter,
            PromptCanary canary
    ) {
        this.ragChatClient = ragChatClient;
        this.plainChatClient = builder.build();
        this.ragService = ragService;
        this.conversationService = conversationService;
        this.currentUserProvider = currentUserProvider;
        this.inputGuard = inputGuard;
        this.rateLimiter = rateLimiter;
        this.canary = canary;
    }

    // CHAT NORMALE: aspetta la risposta completa dell'LLM (senza RAG)
    public String sendMessage(String message) {

        // Questa stringa ritorna la risposta dell'LLM che ho collegato
        return plainChatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    // CHAT CON STREAMING: restituisce la risposta pezzo per pezzo (senza RAG)
    public Flux<String> streamMessage(String message) {
        return plainChatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    // CHAT CON RAG, MEMORIA E STREAMING (lezione 5) + GUARDRAIL (lezione 8)
    // Molto più corto della versione della lezione 3: system prompt, storico e documenti
    // li gestiscono la configurazione (ChatClientConfig) e gli advisor.
    public Flux<String> streamRagMessage(String message, String conversationId) {
        StringBuilder answer = new StringBuilder();

        // Chi sta chiedendo? Lo decide il NOSTRO codice (la sessione del login), non il modello.
        // Va letto QUI, nel thread della richiesta: più avanti siamo su altri thread.
        UserContext user = currentUserProvider.currentUser();

        // Lezione 13: la chat è nuova o è dell'utente? Controllo SUBITO, prima che lo stream SSE
        // parta: così chi prova a continuare la chat di un altro riceve un normale 404.
        conversationService.ensureWritable(conversationId, user);

        // ===== GUARDRAIL: strato "consumo" - limite di messaggi al minuto =====
        if (!rateLimiter.tryConsume(user.userId())) {
            log.warn("[GUARDRAIL] Limite di {} messaggi/minuto superato da {}", rateLimiter.messagesPerMinute(), user.userId());
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Hai inviato troppi messaggi: riprova tra qualche secondo.");
        }

        // ===== GUARDRAIL: strato "input" - controllo PRIMA di chiamare il modello =====
        InputGuard.GuardResult guard = inputGuard.check(message);

        if (guard.blocked()) {
            log.warn("[GUARDRAIL] Messaggio bloccato ({}) nella chat {}", guard.reason(), conversationId);
            // Rispondiamo subito, senza chiamare il modello: niente costi e niente memoria "sporcata".
            // Nell'archivio salviamo domanda e risposta, così l'utente vede cosa è successo.
            return Mono.fromRunnable(() -> {
                        conversationService.saveUserMessage(conversationId, message, user.userId());
                        conversationService.saveAssistantMessage(conversationId, guard.text());
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .thenMany(Flux.just(guard.text()));
        }

        if (guard.masked()) {
            log.info("[GUARDRAIL] Dati personali oscurati prima dell'invio al modello (chat {})", conversationId);
        }

        // Da qui in poi usiamo SOLO il testo ripulito: è quello che va al modello, alla memoria e all'archivio
        String question = guard.text();

        // 1. Operazioni bloccanti (JPA su MariaDB, embedding + ricerca su pgvector)
        //    su un thread adatto, come nella lezione 3
        return Mono.fromCallable(() -> {
                    conversationService.saveUserMessage(conversationId, question, user.userId()); // archivio
                    return ragService.search(question, TOP_K, user);                // documenti (lezione 11: per reparto)
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 2. Chiamata all'LLM: passiamo agli advisor l'ID della conversazione e i documenti
                .flatMapMany(documents -> ragChatClient.prompt()
                        .user(question)
                        .advisors(advisor -> advisor
                                .param(ChatMemory.CONVERSATION_ID, conversationId)
                                .param(DocumentContextAdvisor.DOCUMENTS, documents))
                        // Lezione 6: l'utente arriva agli strumenti tramite ToolContext.
                        // Il ToolContext NON viene mai inviato al modello.
                        .toolContext(Map.of(UserContext.KEY, user))
                        .stream()
                        .content())
                // 3. Accumuliamo i token per salvare la risposta completa nell'archivio
                .doOnNext(answer::append)
                // ===== GUARDRAIL: strato "output" - il canarino =====
                // Se la parola in codice del system prompt compare nella risposta, qualcuno è riuscito
                // a far ripetere al modello le sue istruzioni: interrompiamo lo stream.
                .takeUntil(token -> canary.leakedIn(answer))
                .concatWith(Flux.defer(() -> {
                    if (!canary.leakedIn(answer)) {
                        return Flux.empty();
                    }
                    log.error("[GUARDRAIL] CANARINO nella risposta: possibile fuga del system prompt (chat {})", conversationId);
                    answer.append(CANARY_NOTICE);
                    return Flux.just(CANARY_NOTICE);
                }))
                // 4a. Fine normale: salviamo PRIMA di chiudere lo stream. Così la domanda
                //     successiva non può arrivare mentre il salvataggio è ancora in corso.
                .concatWith(Mono.<String>fromRunnable(() -> saveAnswer(conversationId, answer, false))
                        .subscribeOn(Schedulers.boundedElastic()))
                // 4b. L'utente ha chiuso la pagina o cambiato chat: salviamo la parte ricevuta
                .doOnCancel(() -> Schedulers.boundedElastic().schedule(() ->
                        saveAnswer(conversationId, answer, true)))
                // 4c. Errore dell'LLM a metà risposta: salviamo quello che è arrivato
                .doOnError(error -> Schedulers.boundedElastic().schedule(() ->
                        saveAnswer(conversationId, answer, false)));
    }

    private void saveAnswer(String conversationId, StringBuilder answer, boolean interrupted) {
        if (answer.isEmpty()) {
            return;
        }
        
        // Il canarino non deve finire nell'archivio
        String text = canary.redact(interrupted ? answer + "\n\n_(risposta interrotta)_" : answer.toString());
        conversationService.saveAssistantMessage(conversationId, text);
    }
}
