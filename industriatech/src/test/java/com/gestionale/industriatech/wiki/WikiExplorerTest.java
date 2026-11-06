package com.gestionale.industriatech.wiki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.controller.WikiController;
import com.gestionale.industriatech.security.CurrentUserProvider;
import com.gestionale.industriatech.security.UserContext;

/**
 * LIVELLO 1 (lezione 12): il Wiki Explorer.
 * Indice, backlink, ricerca, link per il web, asset e strumenti di "Chiedi alla wiki".
 */
class WikiExplorerTest {

    private static final UserContext IT = new UserContext("u", "IT", false);
    private static final UserContext HR = new UserContext("h", "HR", false);

    // ===== WikiIndex: collegamenti e backlink (i due test della lezione, più l'ambiguità) =====

    private final WikiIndex index = WikiIndex.of(List.of(
            WikiPage.from("index.md", "- [[Embedding]]\n- [[Pagina mancante]]"),
            WikiPage.from("concetti/embedding.md", "Vedi [[Chunking|il chunking]]."),
            WikiPage.from("concetti/chunking.md", "Torna a [[Embedding#Dimensioni|gli embedding]]."),
            WikiPage.from("a/Note.md", "uno"),
            WikiPage.from("b/Note.md", "due")));

    @Test
    void risolveICollegamentiPerNomeDelFile() {
        assertThat(index.resolve("Embedding")).contains("concetti/embedding.md");
        assertThat(index.resolve("concetti/chunking.md")).contains("concetti/chunking.md");
        assertThat(index.resolve("Pagina mancante")).isEmpty();
    }

    @Test
    void unNomeAmbiguoNonVieneRisolto() {
        // Meglio un link mancante che un link sbagliato
        assertThat(index.resolve("Note")).isEmpty();
        assertThat(index.resolve("a/Note")).contains("a/Note.md");
    }

    @Test
    void calcolaIBacklink() {
        assertThat(index.backlinksOf("concetti/embedding.md"))
                .containsExactly("concetti/chunking.md", "index.md");
        assertThat(index.outgoingLinksOf("index.md"))
                .containsExactly("concetti/embedding.md");
    }

    @Test
    void ilTitoloArrivaDalFrontmatterOppureDalNomeDelFile() {
        assertThat(WikiPage.from("x/Hash.md", "# Hash").title()).isEqualTo("Hash");
        assertThat(WikiPage.from("x/h.md", "---\ntitle: Funzioni di hash\naliases: [digest]\n---\ntesto").title())
                .isEqualTo("Funzioni di hash");
    }

    // ===== Vault temporaneo per ricerca, asset e strumenti =====

    @TempDir
    Path vault;

    WikiCatalog catalog;
    WikiSearchService search;
    WikiVault wikiVault;

    @BeforeEach
    void createVault() throws Exception {
        Path wiki = Files.createDirectories(vault.resolve("wiki/concepts"));
        Files.writeString(vault.resolve("wiki/index.md"), "# Index\n- [[Phishing]]\n- [[Perché il backup]]\n- [[Stipendi]]\n");
        Files.writeString(wiki.resolve("Phishing.md"), """
                ---
                aliases: [spear phishing]
                tags: [social-engineering]
                ---
                # Phishing
                Email ingannevoli. Vedi [[Perché il backup]]. ![[schema phishing.png]]
                """);
        Files.writeString(wiki.resolve("Perché il backup.md"), "# Backup\nLa regola 3-2-1 protegge dal ransomware.\n");
        Files.writeString(wiki.resolve("Ransomware.md"), "ransomware ransomware ransomware ransomware ransomware ransomware ransomware\n");
        Files.writeString(vault.resolve("wiki/Stipendi.md"), "---\ndepartment: HR\n---\n# Stipendi\nTabella riservata con ransomware.\n");
        Files.write(wiki.resolve("schema phishing.png"), new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        Files.writeString(vault.resolve("fuori.png"), "fuori");

        wikiVault = new WikiVault(new WikiProperties(vault.toString(), "wiki", 200_000, null));
        catalog = new WikiCatalog(wikiVault);
        search = new WikiSearchService(catalog);
    }

    @Test
    void laRicercaIgnoraMaiuscoleEAccenti() {
        // Anche index.md contiene "Perché" (nel collegamento), ma il titolo pesa di più
        assertThat(search.search("PERCHE", 10, IT).getFirst().path()).isEqualTo("concepts/Perché il backup.md");
    }

    @Test
    void ilTitoloPesaPiuDelTestoRipetuto() {
        // "Ransomware.md" ripete la parola 7 volte nel testo, ma il punteggio del testo ha un tetto (5)
        // mentre il titolo vale 15 + 10
        List<SearchHit> hits = search.search("ransomware", 10, IT);
        assertThat(hits.getFirst().path()).isEqualTo("concepts/Ransomware.md");
        assertThat(hits.getFirst().score()).isEqualTo(15 + 10 + 5);
    }

    @Test
    void laRicercaTrovaAncheGliAlias() {
        assertThat(search.search("spear", 10, IT)).extracting(SearchHit::path).containsExactly("concepts/Phishing.md");
    }

    @Test
    void laRicercaNonMostraLePagineDiAltriReparti() {
        // index.md (pubblica) contiene la parola "Stipendi" nel suo elenco: quella si può trovare
        assertThat(search.search("stipendi", 10, IT)).extracting(SearchHit::path).doesNotContain("Stipendi.md");
        assertThat(search.search("stipendi", 10, HR)).extracting(SearchHit::path).contains("Stipendi.md");
    }

    @Test
    void iWikilinkDiventanoLinkPerIlWeb() {
        WikiPage phishing = catalog.index().page("concepts/Phishing.md").orElseThrow();

        String markdown = WikiWebMarkdown.convert(phishing.body(), catalog.index(), IT);

        assertThat(markdown)
                .contains("[Perché il backup](/wiki/page?path=concepts%2FPerch%C3%A9%20il%20backup.md)")
                .contains("![schema phishing.png](/api/wiki/assets?name=schema%20phishing.png)");
    }

    @Test
    void unLinkAUnaPaginaRiservataResaTestoSemplice() {
        String markdown = WikiWebMarkdown.convert(catalog.index().page("index.md").orElseThrow().body(), catalog.index(), IT);

        assertThat(markdown).contains("- Stipendi").doesNotContain("Stipendi.md");
    }

    // ===== Endpoint: pagine e asset =====

    private WikiController controller(UserContext user) {
        // Lezione 13: l'utente arriva dalla sessione; qui lo simuliamo con un mock
        CurrentUserProvider provider = org.mockito.Mockito.mock(CurrentUserProvider.class);
        org.mockito.Mockito.when(provider.currentUser()).thenReturn(user);
        return new WikiController(catalog, search, wikiVault, provider);
    }

    @Test
    void unPercorsoInventatoRiceve404SenzaToccareIlDisco() {
        assertThatThrownBy(() -> controller(IT).page("../fuori.md"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void unaPaginaRiservataRiceve404() {
        assertThatThrownBy(() -> controller(IT).page("Stipendi.md")).isInstanceOf(ResponseStatusException.class);
        assertThat(controller(HR).page("Stipendi.md").title()).isEqualTo("Stipendi");
    }

    @Test
    void laPaginaHaBacklinkECollegamentiInUscita() {
        var view = controller(IT).page("concepts/Perché il backup.md");

        assertThat(view.backlinks()).extracting(l -> l.path()).containsExactly("concepts/Phishing.md", "index.md");
        assertThat(view.outgoingLinks()).isEmpty();
    }

    @Test
    void gliAssetAccettanoSoloNomiSempliciConEstensioniAmmesse() {
        var controller = controller(IT);
        assertThat(controller.asset("schema phishing.png").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.asset("schema phishing.png").getHeaders().getFirst("Content-Security-Policy")).contains("sandbox");

        for (String bad : List.of("../fuori.png", "concepts/schema phishing.png", "/etc/passwd.png", "index.md", "virus.exe")) {
            assertThatThrownBy(() -> controller.asset(bad)).as(bad).isInstanceOf(ResponseStatusException.class);
        }
        // Nome valido ma fuori dalla cartella della wiki: non è nell'indice
        assertThatThrownBy(() -> controller.asset("fuori.png")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void unAssetCollegatoSimbolicamenteFuoriDallaWikiNonVieneElencato() throws Exception {
        Files.createSymbolicLink(vault.resolve("wiki/trappola.png"), vault.resolve("fuori.png"));
        assertThat(wikiVault.listAssets()).doesNotContainKey("trappola.png");
    }

    // ===== Gli strumenti di "Chiedi alla wiki" =====

    private ToolContext context(UserContext user, List<String> pagesRead) {
        return new ToolContext(Map.of(UserContext.KEY, user, WikiAskTools.PAGES_READ, pagesRead));
    }

    @Test
    void loStrumentoLeggeLIndiceERegistraLePagineLette() {
        var tools = new WikiAskTools(catalog, search);
        List<String> pagesRead = new ArrayList<>();

        String index = tools.readWikiPage("index.md", context(IT, pagesRead));
        String phishing = tools.readWikiPage("Phishing", context(IT, pagesRead)); // anche dal nome del collegamento

        assertThat(index).startsWith("<pagina_wiki percorso=\"index.md\">");
        assertThat(phishing).contains("Email ingannevoli");
        assertThat(pagesRead).containsExactly("index.md", "concepts/Phishing.md");
    }

    @Test
    void unaPaginaInesistenteORiservataRestituisceUnSuggerimento() {
        var tools = new WikiAskTools(catalog, search);

        assertThat(tools.readWikiPage("../fuori.md", context(IT, new ArrayList<>()))).startsWith("Pagina non trovata");
        assertThat(tools.readWikiPage("Stipendi.md", context(IT, new ArrayList<>()))).startsWith("Pagina non trovata");
        assertThat(tools.searchWiki("stipendi", context(IT, new ArrayList<>()))).extracting(SearchHit::path).doesNotContain("Stipendi.md");
    }
}
