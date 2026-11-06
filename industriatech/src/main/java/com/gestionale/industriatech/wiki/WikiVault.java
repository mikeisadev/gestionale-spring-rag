package com.gestionale.industriatech.wiki;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Lezione 11: accesso in SOLA LETTURA alle pagine della wiki.
 *
 * È l'unico punto del backend che tocca i file della wiki. I percorsi che riceve
 * possono arrivare dall'esterno (un'API o, peggio, un LLM): ognuno viene validato,
 * nessuno può leggere file fuori dalla cartella della wiki (path traversal).
 */
@Component
@Conditional(WikiEnabledCondition.class)
public class WikiVault {

    private static final String MARKDOWN_EXTENSION = ".md";

    private final Path wikiRoot;
    private final int maxPageBytes;

    public WikiVault(WikiProperties properties) {
        this.wikiRoot = toRealDirectory(properties.wikiRoot());
        this.maxPageBytes = properties.maxPageBytes();
    }

    /** Percorsi relativi di tutte le pagine, ad esempio "concepts/hash.md". */
    public List<String> listPages() {
        try (Stream<Path> files = Files.walk(wikiRoot)) {
            return files
                    // NOFOLLOW_LINKS: i collegamenti simbolici non vengono elencati
                    .filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> file.getFileName().toString().endsWith(MARKDOWN_EXTENSION))
                    .map(this::toRelativePath)
                    .filter(path -> !isHidden(path))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new WikiAccessException("Impossibile elencare le pagine della wiki", e);
        }
    }

    /** Lezione 12: estensioni delle immagini e dei video che si possono incorporare (![[...]]). */
    public static final Set<String> ASSET_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "svg", "webp", "mp4", "webm");

    /**
     * Lezione 12: immagini e video della wiki, per nome del file in minuscolo.
     * Come per le pagine: niente link simbolici, niente cartelle nascoste.
     */
    public Map<String, String> listAssets() {
        try (Stream<Path> files = Files.walk(wikiRoot)) {
            Map<String, String> assets = new HashMap<>();
            files.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .filter(file -> ASSET_EXTENSIONS.contains(extensionOf(file.getFileName().toString())))
                    .map(this::toRelativePath)
                    .filter(path -> !isHidden(path))
                    .sorted()
                    // Due file con lo stesso nome in cartelle diverse: vince il primo (come per Obsidian, è ambiguo)
                    .forEach(path -> assets.putIfAbsent(baseFileName(path).toLowerCase(Locale.ROOT), path));
            return assets;
        } catch (IOException e) {
            throw new WikiAccessException("Impossibile elencare i file della wiki", e);
        }
    }

    /** Lezione 12: il file di un'immagine o di un video, con gli stessi controlli delle pagine. */
    public Path assetFile(String relativePath) {
        if (relativePath == null || !ASSET_EXTENSIONS.contains(extensionOf(relativePath))) {
            throw new WikiAccessException("File non valido");
        }
        return resolveInsideWiki(relativePath);
    }

    /** Legge una pagina. Il percorso può arrivare dall'esterno (API o LLM): prima lo validiamo. */
    public String readPage(String relativePath) {
        Path file = resolvePage(relativePath);
        try {
            if (Files.size(file) > maxPageBytes) {
                throw new WikiAccessException("Pagina troppo grande: " + relativePath);
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new WikiAccessException("Impossibile leggere la pagina: " + relativePath, e);
        }
    }

    /**
     * Lezione 11 (aggiunta rispetto al testo originale): trova una pagina dal NOME.
     *
     * In Obsidian i collegamenti di solito non contengono il percorso: [[Lezione 01 - Fondamenti]]
     * e non [[lessons/Lezione 01 - Fondamenti.md]]. Se il modello vuole seguire un collegamento,
     * conosce solo il nome. Cerchiamo il nome tra le pagine ELENCATE: il risultato è sempre un
     * percorso sicuro, e passa comunque dai controlli di readPage().
     */
    public Optional<String> findPage(String nameOrPath) {
        if (nameOrPath == null || nameOrPath.isBlank()) {
            return Optional.empty();
        }
        String wanted = stripHeading(nameOrPath.strip());
        List<String> pages = listPages();

        // 1. percorso esatto ("concepts/hash.md")
        if (pages.contains(wanted)) {
            return Optional.of(wanted);
        }
        // 2. nome della pagina, senza cartelle né estensione ("Hash", "hash.md")
        String name = baseName(wanted).toLowerCase(Locale.ROOT);
        return pages.stream()
                .filter(page -> baseName(page).toLowerCase(Locale.ROOT).equals(name))
                .findFirst();
    }

    private Path resolvePage(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || !relativePath.endsWith(MARKDOWN_EXTENSION)) {
            throw new WikiAccessException("Percorso di pagina non valido");
        }
        return resolveInsideWiki(relativePath);
    }

    private Path resolveInsideWiki(String relativePath) {
        // normalize() risolve i "..": se il risultato esce dalla wiki, rifiutiamo la richiesta.
        // Anche un percorso assoluto (/etc/passwd.md) finisce fuori dalla wiki ed è rifiutato.
        Path candidate = wikiRoot.resolve(relativePath).normalize();
        if (!candidate.startsWith(wikiRoot) || !Files.isRegularFile(candidate)) {
            throw new WikiAccessException("Pagina non trovata: " + relativePath);
        }
        try {
            // toRealPath() segue i collegamenti simbolici: anche la loro destinazione deve stare nella wiki
            Path realFile = candidate.toRealPath();
            if (!realFile.startsWith(wikiRoot)) {
                throw new WikiAccessException("Pagina non trovata: " + relativePath);
            }
            return realFile;
        } catch (IOException e) {
            throw new WikiAccessException("Pagina non trovata: " + relativePath, e);
        }
    }

    private String toRelativePath(Path file) {
        // Usiamo sempre "/" come separatore, anche su Windows: il percorso è l'identificativo della pagina
        return wikiRoot.relativize(file).toString().replace(File.separatorChar, '/');
    }

    private static boolean isHidden(String relativePath) {
        return Arrays.stream(relativePath.split("/")).anyMatch(segment -> segment.startsWith("."));
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseFileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static String stripHeading(String link) {
        int hash = link.indexOf('#');
        return hash < 0 ? link : link.substring(0, hash).strip();
    }

    private static String baseName(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        return name.endsWith(MARKDOWN_EXTENSION) ? name.substring(0, name.length() - MARKDOWN_EXTENSION.length()) : name;
    }

    private static Path toRealDirectory(Path directory) {
        try {
            Path realDirectory = directory.toRealPath();
            if (!Files.isDirectory(realDirectory)) {
                throw new IllegalStateException("Il percorso della wiki non è una cartella: " + directory);
            }
            return realDirectory;
        } catch (IOException e) {
            throw new IllegalStateException("La cartella della wiki non esiste: " + directory, e);
        }
    }
}
