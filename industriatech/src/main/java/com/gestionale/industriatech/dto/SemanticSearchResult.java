package com.gestionale.industriatech.dto;

import java.util.Map;

// Un chunk trovato dalla ricerca semantica: testo + metadati (file, pagina, ...)
public record SemanticSearchResult(
    String content,
    Map<String, Object> metadata
) {}
