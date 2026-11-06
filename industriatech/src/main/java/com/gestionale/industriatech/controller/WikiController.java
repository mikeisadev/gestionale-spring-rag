package com.gestionale.industriatech.controller;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Conditional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.dto.WikiPageView;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.security.UserContext;
import com.gestionale.industriatech.wiki.SearchHit;
import com.gestionale.industriatech.wiki.WikiAccessException;
import com.gestionale.industriatech.wiki.WikiCatalog;
import com.gestionale.industriatech.wiki.WikiEnabledCondition;
import com.gestionale.industriatech.wiki.WikiIndex;
import com.gestionale.industriatech.wiki.WikiPage;
import com.gestionale.industriatech.wiki.WikiSearchService;
import com.gestionale.industriatech.wiki.WikiVault;
import com.gestionale.industriatech.wiki.WikiWebMarkdown;

/**
 * Lezione 12: il Wiki Explorer. Tutto in SOLA LETTURA: nessun endpoint scrive o cancella file.
 *
 *   GET  /api/wiki/search?q=...&limit=...  ricerca per parole chiave
 *   GET  /api/wiki/page?path=...           una pagina con backlink e collegamenti in uscita
 *   GET  /api/wiki/assets?name=...         immagini e video incorporati (![[...]])
 *   POST /api/wiki/reload                  rilegge la wiki dal disco
 *
 * Esiste solo se la wiki è configurata (WIKI_VAULT_PATH): altrimenti questi indirizzi rispondono 404.
 */
@RestController
@RequestMapping("/api/wiki")
// Lezione 13: niente @CrossOrigin qui, CORS è configurato una volta sola in SecurityConfig
@Conditional(WikiEnabledCondition.class)
public class WikiController {

    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_RESULTS = 50;

    private final WikiCatalog catalog;
    private final WikiSearchService searchService;
    private final WikiVault wikiVault;
    private final CurrentUserProvider currentUserProvider;

    public WikiController(WikiCatalog catalog, WikiSearchService searchService,
                          WikiVault wikiVault, CurrentUserProvider currentUserProvider) {
        this.catalog = catalog;
        this.searchService = searchService;
        this.wikiVault = wikiVault;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/search")
    public List<SearchHit> search(@RequestParam("q") String query,
                                  @RequestParam(value = "limit", defaultValue = "20") int limit) {
        if (query.isBlank() || query.length() > MAX_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La ricerca deve avere da 1 a 200 caratteri");
        }
        return searchService.search(query, Math.clamp(limit, 1, MAX_RESULTS), currentUserProvider.currentUser());
    }

    @GetMapping("/page")
    public WikiPageView page(@RequestParam("path") String path) {
        UserContext user = currentUserProvider.currentUser();
        // Cerchiamo SOLO nell'indice in memoria: un percorso inventato (../../segreto.md)
        // non è nell'indice e riceve 404 senza mai toccare il file system.
        WikiIndex index = catalog.index();
        WikiPage page = index.page(path)
                .filter(found -> found.isVisibleTo(user)) // riservata a un altro reparto: "non trovata"
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pagina non trovata"));

        return new WikiPageView(
                page.path(),
                page.title(),
                page.tags(),
                WikiWebMarkdown.convert(page.body(), index, user),
                toLinks(index.backlinksOf(path), index, user),
                toLinks(index.outgoingLinksOf(path), index, user));
    }

    @GetMapping("/assets")
    public ResponseEntity<Resource> asset(@RequestParam("name") String name) {
        if (!WikiWebMarkdown.MEDIA_FILE.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome di file non valido");
        }
        String relativePath = catalog.index().asset(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "File non trovato"));
        Path file = wikiVault.assetFile(relativePath); // stessi controlli delle pagine (link simbolici...)

        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(name).orElse(MediaType.APPLICATION_OCTET_STREAM))
                // Un SVG può contenere JavaScript: se qualcuno apre l'indirizzo direttamente, il browser
                // lo eseguirebbe nel NOSTRO sito. "sandbox" e "default-src 'none'" lo impediscono.
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; sandbox")
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(new FileSystemResource(file));
    }

    @PostMapping("/reload")
    public Map<String, Integer> reload() {
        return Map.of("pages", catalog.reload().pages().size());
    }

    private static List<WikiPageView.Link> toLinks(List<String> paths, WikiIndex index, UserContext user) {
        return paths.stream()
                .flatMap(path -> index.page(path).stream())
                .filter(page -> page.isVisibleTo(user))
                .map(page -> new WikiPageView.Link(page.path(), page.title()))
                .toList();
    }

    // Errori sempre nello stesso formato { "message": ... }, senza percorsi né stack trace
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() != null ? ex.getReason() : "Errore"));
    }

    @ExceptionHandler(WikiAccessException.class)
    public ResponseEntity<Map<String, String>> handleAccess(WikiAccessException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "File non trovato"));
    }
}
