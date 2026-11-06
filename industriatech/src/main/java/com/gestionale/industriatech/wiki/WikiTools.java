package com.gestionale.industriatech.wiki;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import com.gestionale.industriatech.service.RagService;
import com.gestionale.industriatech.security.UserContext;
import com.gestionale.industriatech.tools.ToolAccessDeniedException;

/**
 * Lezione 11 (livello 2): strumento di SOLA LETTURA che permette all'LLM di aprire
 * una pagina intera della wiki e seguirne i collegamenti [[...]].
 *
 * Il RAG recupera un chunk (una sezione); con questo strumento il modello può leggere
 * il resto della pagina quando serve.
 */
@Component
@Conditional(WikiEnabledCondition.class)
public class WikiTools {

    private static final Logger log = LoggerFactory.getLogger(WikiTools.class);

    // Una pagina intera può essere lunga: oltre questo limite la tronchiamo (costo e contesto)
    static final int MAX_CHARACTERS = 12_000;

    private final WikiVault wikiVault;

    public WikiTools(WikiVault wikiVault) {
        this.wikiVault = wikiVault;
    }

    @Tool(description = """
            Legge una pagina completa della wiki aziendale.
            Accetta il percorso della pagina (ad esempio "concepts/Hash.md", la fonte di un documento
            con tipo="wiki") oppure il nome di un collegamento [[...]] citato in una pagina
            (ad esempio "Lezione 01 - Fondamenti della sicurezza").
            Usalo quando un documento recuperato proviene dalla wiki e ti serve la pagina completa,
            oppure per seguire un collegamento.""")
    public String readWikiPage(
            @ToolParam(description = "Percorso della pagina (concepts/Hash.md) oppure nome di un collegamento [[...]]") String page,
            ToolContext toolContext) {

        // L'utente arriva dal ToolContext, MAI da un parametro compilato dal modello (lezione 6)
        UserContext user = (UserContext) toolContext.getContext().get(UserContext.KEY);
        if (user == null) {
            throw new ToolAccessDeniedException("Utente non riconosciuto.");
        }

        // Il nome viene cercato tra le pagine ELENCATE dal vault: niente percorsi inventati
        String path = wikiVault.findPage(page).orElseThrow(() -> {
            // Nel log vediamo i tentativi strani (es. "../CLAUDE.md"): il modello non vede questo messaggio
            log.warn("[WIKI] Pagina richiesta dal modello e non trovata: \"{}\" (utente {})", page, user.userId());
            return new WikiAccessException("Pagina non trovata: " + page);
        });
        MarkdownNote note = MarkdownNote.parse(wikiVault.readPage(path));

        // Stesse regole della ricerca (RagService.isVisibleTo): i permessi si controllano ANCHE nello
        // strumento. Senza questo controllo, un utente potrebbe aggirare il filtro della ricerca
        // chiedendo al modello di leggere una pagina riservata a un altro reparto.
        String department = note.text("department").orElse(RagService.PUBLIC_DEPARTMENT);
        if (!RagService.PUBLIC_DEPARTMENT.equals(department) && !user.canAccessDepartment(department)) {
            log.warn("[WIKI] Accesso negato a {} per l'utente {} (reparto della pagina: {})", path, user.userId(), department);
            throw new ToolAccessDeniedException("Accesso negato: questa pagina è riservata a un altro reparto.");
        }

        String body = note.body().length() > MAX_CHARACTERS
                ? note.body().substring(0, MAX_CHARACTERS) + "\n[pagina troncata]"
                : note.body();

        log.info("[WIKI] Il modello ha letto la pagina {} ({} caratteri)", path, body.length());

        // Come per i documenti RAG (lezione 5): il contenuto è un DATO, chiuso in un tag che il testo non può chiudere
        return """
                <pagina_wiki percorso="%s">
                %s
                </pagina_wiki>""".formatted(path, neutralizeTags(body));
    }

    private static String neutralizeTags(String text) {
        return text.replace("<", "‹").replace(">", "›");
    }
}
