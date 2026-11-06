package com.gestionale.industriatech.wiki;

import java.util.List;

import com.gestionale.industriatech.security.UserContext;
import com.gestionale.industriatech.service.RagService;

/**
 * Lezione 12: una pagina della wiki già analizzata: titolo, proprietà, testo e collegamenti in uscita.
 *
 * Il titolo arriva dal frontmatter ("title: ...") oppure dal nome del file.
 */
public record WikiPage(
        String path,
        String title,
        List<String> aliases,
        List<String> tags,
        String department,
        String body,
        List<WikiLink> links) {

    public static WikiPage from(String path, String markdown) {
        MarkdownNote note = MarkdownNote.parse(markdown);
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        String defaultTitle = fileName.substring(0, fileName.length() - ".md".length());
        return new WikiPage(
                path,
                note.text("title").orElse(defaultTitle),
                note.list("aliases"),
                note.list("tags"),
                // Stessa regola della lezione 11: senza "department" la pagina è pubblica
                note.text("department").orElse(RagService.PUBLIC_DEPARTMENT),
                note.body(),
                WikiLinks.extract(note.body()));
    }

    /** Stessa regola di RagService e di readWikiPage (lezione 11): ogni strada verso i dati fa lo stesso controllo. */
    public boolean isVisibleTo(UserContext user) {
        return RagService.PUBLIC_DEPARTMENT.equals(department)
                || (user != null && user.canAccessDepartment(department));
    }
}
