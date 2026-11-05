package com.gestionale.industriatech.dto;

// JSON inviato per la ricerca semantica:
// { "query": "Come funziona la configurazione automatica?", "topK": 5 }
public record SemanticSearchRequest(
    String query,
    Integer topK
) {}
