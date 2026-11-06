package com.gestionale.industriatech.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.dto.SemanticSearchRequest;
import com.gestionale.industriatech.dto.SemanticSearchResult;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.service.RagService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Controller indipendente per TESTARE la ricerca semantica (es. con Postman),
 * senza coinvolgere l'LLM.
 *
 * POST http://localhost:8081/api/rag/search
 * { "query": "Come funziona la configurazione automatica?", "topK": 5 }
 */
@RestController
@RequestMapping("/api/rag")
// Lezione 13: niente @CrossOrigin qui, CORS è configurato una volta sola in SecurityConfig
public class RagController {

    private final RagService ragService;
    private final CurrentUserProvider currentUserProvider;

    public RagController(RagService ragService, CurrentUserProvider currentUserProvider) {
        this.ragService = ragService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/search")
    public Mono<List<SemanticSearchResult>> search(
            @RequestBody SemanticSearchRequest request
    ) {
        if (request.query() == null ||
                request.query().isBlank()) {
            return Mono.error(
                new IllegalArgumentException("Query obbligatoria")
            );
        }

        // topK tra 1 e 20 (5 se non indicato)
        int topK = request.topK() == null
                ? 5
                : Math.max(1, Math.min(request.topK(), 20));

        // La ricerca usa JDBC e la chiamata di embedding: operazioni bloccanti
        return Mono.fromCallable(() ->
                    ragService.searchDocuments(
                            request.query(),
                            topK,
                            currentUserProvider.currentUser() // lezione 11: filtro per reparto
                    )
                )
                .subscribeOn(Schedulers.boundedElastic());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidQuery(IllegalArgumentException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }
}
