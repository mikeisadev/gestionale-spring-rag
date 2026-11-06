package com.gestionale.industriatech.dto;

import java.util.List;

/** Lezione 12: la risposta di "Chiedi alla wiki", con le pagine lette dal modello e la durata. */
public record WikiAskResponse(String answer, List<WikiPageView.Link> pagesRead, long millis) {
}
