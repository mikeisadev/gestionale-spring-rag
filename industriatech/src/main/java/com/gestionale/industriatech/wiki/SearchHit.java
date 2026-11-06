package com.gestionale.industriatech.wiki;

/** Lezione 12: un risultato di ricerca: la pagina trovata, un estratto del testo e il punteggio. */
public record SearchHit(String path, String title, String snippet, int score) {
}
