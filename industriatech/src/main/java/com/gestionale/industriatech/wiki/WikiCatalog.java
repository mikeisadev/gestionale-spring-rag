package com.gestionale.industriatech.wiki;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Lezione 12: tiene in memoria l'ultima fotografia della wiki (WikiIndex) e la rigenera su richiesta.
 *
 * reload() costruisce un indice NUOVO e lo sostituisce in un colpo solo: chi sta leggendo il vecchio
 * finisce il suo lavoro, le richieste successive usano il nuovo. "volatile" garantisce che tutti
 * i thread vedano subito il nuovo riferimento.
 */
@Component
@Conditional(WikiEnabledCondition.class)
public class WikiCatalog {

    private static final Logger log = LoggerFactory.getLogger(WikiCatalog.class);

    private final WikiVault wikiVault;
    private volatile WikiIndex index;

    public WikiCatalog(WikiVault wikiVault) {
        this.wikiVault = wikiVault;
        this.index = load();
    }

    public WikiIndex index() {
        return index;
    }

    /** Rilegge la wiki dal disco: la nuova fotografia sostituisce la vecchia in un colpo solo. */
    public synchronized WikiIndex reload() {
        index = load();
        return index;
    }

    private WikiIndex load() {
        long start = System.currentTimeMillis();
        List<WikiPage> pages = new ArrayList<>();
        for (String path : wikiVault.listPages()) {
            try {
                pages.add(WikiPage.from(path, wikiVault.readPage(path)));
            } catch (WikiAccessException e) {
                log.warn("Pagina saltata: {}", e.getMessage());
            }
        }
        WikiIndex loaded = WikiIndex.of(pages, wikiVault.listAssets());
        log.info("Wiki Explorer: {} pagine caricate in memoria in {} ms", pages.size(), System.currentTimeMillis() - start);
        return loaded;
    }
}
