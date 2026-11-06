package com.gestionale.industriatech.wiki;

/**
 * Lezione 11: un collegamento di Obsidian: [[destinazione#sezione|testo]], oppure ![[file]] per un incorporamento.
 *
 * @param target  pagina o file di destinazione, ad esempio "Hash" o "concepts/hash.md"
 * @param heading sezione della pagina (facoltativa)
 * @param alias   testo da mostrare al posto del nome della pagina (facoltativo)
 * @param embed   true per ![[...]]: il contenuto viene incorporato nella pagina
 */
public record WikiLink(String target, String heading, String alias, boolean embed) {

    /** Il testo da mostrare: l'alias se c'è, altrimenti il nome della pagina senza cartelle ed estensione. */
    public String displayText() {
        if (alias != null) {
            return alias;
        }
        String name = target.substring(target.lastIndexOf('/') + 1);
        return name.endsWith(".md") ? name.substring(0, name.length() - ".md".length()) : name;
    }
}
