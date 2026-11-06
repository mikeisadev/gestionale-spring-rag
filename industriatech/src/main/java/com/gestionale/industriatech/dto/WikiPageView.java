package com.gestionale.industriatech.dto;

import java.util.List;

/** Lezione 12: una pagina della wiki pronta per il frontend (Markdown con i link già convertiti). */
public record WikiPageView(
        String path,
        String title,
        List<String> tags,
        String markdown,
        List<Link> backlinks,
        List<Link> outgoingLinks) {

    /** Un collegamento a un'altra pagina: percorso e titolo. */
    public record Link(String path, String title) {
    }
}
