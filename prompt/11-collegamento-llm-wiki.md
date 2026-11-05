Path LLM wiki = {MIO-USER-PATH}/Documents/Obsidian Vault/Cybersecurity LLM wiki

**Nota bene**: questo percorso vale soltanto sul mio pc. Uno studente che avvia questo prompt non avrà il vault Obsidian indicato con tutto il materiale che ho io.

Nel mio caso avevo creato un LLM Wiki sulla cyber security. Sottolinea che lo studente può creare un LLM Wiki su qualsiasi argomento. 

Ti ricordo che ciò che inizia in markdown dal punto 11 è un frammento della mia lezione sull'LLM Wiki. Quindi ricorda allo studente che dovrà studiarsi la [L19. LLM Wiki: come generare e implementare una delle memorie più efficienti per i Large Language Models](https://docs.google.com/document/d/1MJHvSyAeIvCacoOfQ6QaC56z5rNHmuYIIAWg1qvDTkM/edit?usp=sharing).

In questo modo potranno crearsi il loro LLM wiki in locale prima di tutto. Soltanto dopo potranno seguire con successo questa lezione e implementare l'LLM wiki nel loro progetto.

Suggerimento da dare ai studenti: LLM wiki può essere usata anche per studiare in autonomia tramite l'ausilio dell'AI, non solo come memoria di un LLM. Consiglia di usare le lezioni generate dall'esecuzione di ogni prompt in questa cartella come materiale /raw da far ingerire all'LLM per generare una wiki di studio.

## **11\) Collegare la LLM Wiki al sistema RAG**

A questo punto è naturale chiedersi: dopo tre lezioni dedicate al RAG, devo buttare via tutto e passare alla LLM Wiki? E se voglio usarle insieme, è davvero fattibile?

La risposta breve: **non devi smantellare nulla**. RAG e LLM Wiki risolvono problemi diversi e si combinano bene. In questo paragrafo, il primo un po' più impegnativo della lezione, aggiungiamo la wiki al chatbot della L18 scrivendo solo classi nuove.

### **11.1) RAG e LLM Wiki non sono rivali**

Pensa a un'azienda. Ha un motore di ricerca interno che trova qualsiasi documento (il RAG) e ha un manuale scritto con cura, che spiega come funzionano le cose (la wiki). Nessuno butterebbe il motore di ricerca perché esiste il manuale, né il contrario.

| Esigenza | Chi se ne occupa meglio |
| :---- | :---- |
| Migliaia di documenti, molti utenti, permessi per reparto | RAG (L17 e L18) |
| Sintesi già pronte, confronti, contraddizioni annotate | LLM Wiki |
| Dati vivi del gestionale | Strumenti (L18, paragrafo 6\) |
| Documenti ufficiali da citare alla lettera | RAG sulle fonti originali |

C'è anche un vantaggio inatteso: **le pagine della wiki sono documenti ideali per il RAG**. Sono già in Markdown pulito, divise in sezioni con titoli chiari, dense di informazioni e con le fonti citate. Indicizzare la wiki significa dare al RAG documenti scritti apposta per essere letti da un LLM.

### **11.2) Tre livelli di integrazione**

Possiamo collegare i due sistemi a tre livelli, dal più semplice al più ambizioso:

1. **la wiki entra in pgvector**: le pagine vengono indicizzate accanto ai PDF, con i loro metadati. Ricerca, advisor e citazioni della L18 restano identici;  
2. **uno strumento per leggere le pagine intere**: il RAG recupera un chunk, ma con uno strumento readWikiPage il modello può aprire la pagina completa e seguire i suoi collegamenti;  
3. **dal chatbot alla wiki**: le domande senza una buona risposta, o le risposte giudicate utili, diventano nuove fonti in raw/ per il prossimo ingest, sempre dopo una revisione umana.

Questo è il flusso complessivo:

| raw/ ──(ingest con l'agente)──► wiki/ ──(WikiIngestionService)──► pgvector                                   ▲                                   │                                   │                                   ▼                        readWikiPage (strumento) ◄──────────── chatbot della L18 |
| :---- |

In questo paragrafo implementiamo i livelli 1 e 2\. Il livello 3 è nell'esercitazione finale.

### **11.3) Configurazione, dipendenza e tabella di stato**

Prima di tutto aggiungiamo il lettore di documenti Markdown di Spring AI, che abbiamo già incontrato nella tabella dei reader della L18 (paragrafo 11.3):

| \<dependency\>     \<groupId\>org.springframework.ai\</groupId\>     \<artifactId\>spring-ai-markdown-document-reader\</artifactId\> \</dependency\> |
| :---- |

Poi diciamo al backend dove si trova il vault. Come per le chiavi API, il percorso arriva da una variabile d'ambiente:

| \# Cartella del vault di Obsidian (le pagine sono nella sottocartella "wiki") app.wiki.vault-path=\${WIKI\_VAULT\_PATH} app.wiki.wiki-folder=wiki app.wiki.max-page-bytes=200000 |
| :---- |

Infine creiamo, da pgAdmin, una piccola tabella che ricorda l'impronta (hash) di ogni pagina già indicizzata. Ci serve per non rigenerare gli embedding delle pagine che non sono cambiate, come avevamo suggerito per i PDF nella L18 (paragrafo 11.1):

| CREATE TABLE IF NOT EXISTS wiki\_index\_state (     path         VARCHAR(512) PRIMARY KEY,     content\_hash CHAR(64)     NOT NULL,     indexed\_at   TIMESTAMP    NOT NULL ); |
| :---- |

**Nota per la configurazione 11B della L17** (due database): il JdbcTemplate principale punta già a PostgreSQL, quindi crea la tabella su PostgreSQL, accanto a vector\_store.  
Ecco i file che aggiungiamo al backend della L18:

| src/main/java/com/example/chatbot/ ├── wiki/ │   ├── WikiConfig.java                  \[NUOVO\] │   ├── WikiProperties.java              \[NUOVO\] │   ├── WikiAccessException.java         \[NUOVO\] │   ├── WikiVault.java                   \[NUOVO\] │   ├── MarkdownNote.java                \[NUOVO\] │   ├── WikiLink.java                    \[NUOVO\] │   ├── WikiLinks.java                   \[NUOVO\] │   ├── WikiIndexStateRepository.java    \[NUOVO\] │   ├── WikiReindexReport.java           \[NUOVO\] │   ├── WikiIngestionService.java        \[NUOVO\] │   └── WikiTools.java                   \[NUOVO\] ├── config/ChatClientConfig.java         \[MODIFICATO: nuovo strumento\] └── controller/DocumentController.java   \[MODIFICATO: endpoint di reindicizzazione\] |
| :---- |

### **11.4) Leggere i file in sicurezza: WikiProperties e WikiVault**

La configurazione diventa un record. Con @DefaultValue i valori facoltativi hanno un default, mentre il percorso del vault è obbligatorio: se manca, l'applicazione non parte e il messaggio dice subito perché.  
*PERCORSO: wiki/WikiProperties.java*

| package com.example.chatbot.wiki; import java.nio.file.Path; import org.springframework.boot.context.properties.ConfigurationProperties; import org.springframework.boot.context.properties.bind.DefaultValue; /\*\*  \* Dove si trova la wiki sul disco.  \*  \* @param vaultPath    cartella del vault di Obsidian, ad esempio /Users/mario/llm-wiki  \* @param wikiFolder   sottocartella con le pagine scritte dall'LLM  \* @param maxPageBytes dimensione massima di una pagina: oltre questo limite non la leggiamo  \*/ @ConfigurationProperties("app.wiki") public record WikiProperties(         String vaultPath,         @DefaultValue("wiki") String wikiFolder,         @DefaultValue("200000") int maxPageBytes) {     public WikiProperties {         if (vaultPath \== null \|\| vaultPath.isBlank()) {             throw new IllegalArgumentException("La proprietà app.wiki.vault-path è obbligatoria");         }     }     public Path wikiRoot() {         return Path.of(vaultPath, wikiFolder).toAbsolutePath().normalize();     } } |
| :---- |

*PERCORSO: wiki/WikiConfig.java*

| package com.example.chatbot.wiki; import org.springframework.boot.context.properties.EnableConfigurationProperties; import org.springframework.context.annotation.Configuration; @Configuration @EnableConfigurationProperties(WikiProperties.class) public class WikiConfig { } |
| :---- |

*PERCORSO: wiki/WikiAccessException.java*

| package com.example.chatbot.wiki; /\*\* Errore di accesso alla wiki. Il messaggio non contiene mai percorsi assoluti del server. \*/ public class WikiAccessException extends RuntimeException {     public WikiAccessException(String message) {         super(message);     }     public WikiAccessException(String message, Throwable cause) {         super(message, cause);     } } |
| :---- |

Ora la classe più importante dal punto di vista della sicurezza. WikiVault è l'unico punto del backend che tocca i file della wiki, e i percorsi che riceve possono arrivare dall'esterno: da un'API o, peggio, da un LLM.  
*PERCORSO: wiki/WikiVault.java*

| package com.example.chatbot.wiki; import java.io.File; import java.io.IOException; import java.nio.charset.StandardCharsets; import java.nio.file.Files; import java.nio.file.LinkOption; import java.nio.file.Path; import java.util.Arrays; import java.util.List; import java.util.stream.Stream; import org.springframework.stereotype.Component; /\*\*  \* Accesso in sola lettura alle pagine della wiki.  \* Ogni percorso viene validato: nessuno può leggere file fuori dalla cartella della wiki.  \*/ @Component public class WikiVault {     private static final String MARKDOWN\_EXTENSION \= ".md";     private final Path wikiRoot;     private final int maxPageBytes;     public WikiVault(WikiProperties properties) {         this.wikiRoot \= toRealDirectory(properties.wikiRoot());         this.maxPageBytes \= properties.maxPageBytes();     }     /\*\* Percorsi relativi di tutte le pagine, ad esempio "concetti/embedding.md". \*/     public List\<String\> listPages() {         try (Stream\<Path\> files \= Files.walk(wikiRoot)) {             return files                     // NOFOLLOW\_LINKS: i collegamenti simbolici non vengono elencati                     .filter(file \-\> Files.isRegularFile(file, LinkOption.NOFOLLOW\_LINKS))                     .filter(file \-\> file.getFileName().toString().endsWith(MARKDOWN\_EXTENSION))                     .map(this::toRelativePath)                     .filter(path \-\> \!isHidden(path))                     .sorted()                     .toList();         } catch (IOException e) {             throw new WikiAccessException("Impossibile elencare le pagine della wiki", e);         }     }     /\*\* Legge una pagina. Il percorso può arrivare dall'esterno (API o LLM): prima lo validiamo. \*/     public String readPage(String relativePath) {         Path file \= resolvePage(relativePath);         try {             if (Files.size(file) \> maxPageBytes) {                 throw new WikiAccessException("Pagina troppo grande: " \+ relativePath);             }             return Files.readString(file, StandardCharsets.UTF\_8);         } catch (IOException e) {             throw new WikiAccessException("Impossibile leggere la pagina: " \+ relativePath, e);         }     }     private Path resolvePage(String relativePath) {         if (relativePath \== null \|\| relativePath.isBlank() \|\| \!relativePath.endsWith(MARKDOWN\_EXTENSION)) {             throw new WikiAccessException("Percorso di pagina non valido");         }         // normalize() risolve i "..": se il risultato esce dalla wiki, rifiutiamo la richiesta         Path candidate \= wikiRoot.resolve(relativePath).normalize();         if (\!candidate.startsWith(wikiRoot) \|\| \!Files.isRegularFile(candidate)) {             throw new WikiAccessException("Pagina non trovata: " \+ relativePath);         }         try {             // toRealPath() segue i collegamenti simbolici: anche la loro destinazione deve stare nella wiki             Path realFile \= candidate.toRealPath();             if (\!realFile.startsWith(wikiRoot)) {                 throw new WikiAccessException("Pagina non trovata: " \+ relativePath);             }             return realFile;         } catch (IOException e) {             throw new WikiAccessException("Pagina non trovata: " \+ relativePath, e);         }     }     private String toRelativePath(Path file) {         // Usiamo sempre "/" come separatore, anche su Windows: il percorso è l'identificativo della pagina         return wikiRoot.relativize(file).toString().replace(File.separatorChar, '/');     }     private static boolean isHidden(String relativePath) {         return Arrays.stream(relativePath.split("/")).anyMatch(segment \-\> segment.startsWith("."));     }     private static Path toRealDirectory(Path directory) {         try {             Path realDirectory \= directory.toRealPath();             if (\!Files.isDirectory(realDirectory)) {                 throw new IllegalStateException("Il percorso della wiki non è una cartella: " \+ directory);             }             return realDirectory;         } catch (IOException e) {             throw new IllegalStateException("La cartella della wiki non esiste: " \+ directory, e);         }     } } |
| :---- |

Il pericolo da cui ci difendiamo si chiama **path traversal**. Se un LLM manipolato chiedesse di leggere ../../.ssh/id\_rsa.md, un codice ingenuo uscirebbe dalla cartella della wiki. Le difese sono quattro:

1. accettiamo solo percorsi che terminano con .md;  
2. normalize() risolve i .. e startsWith(wikiRoot) verifica che il risultato resti dentro la wiki. Un percorso assoluto come /etc/passwd.md viene rifiutato allo stesso modo;  
3. toRealPath() segue i collegamenti simbolici: un link che punta fuori dalla wiki viene rifiutato, e listPages() non li elenca nemmeno;  
4. una pagina troppo grande non viene letta: il contesto del modello non è infinito e neppure la memoria del server.

Nota anche i messaggi di errore: dicono "Pagina non trovata" senza mai rivelare percorsi del server, perché, come abbiamo visto nella L18, il messaggio di un'eccezione lanciata da uno strumento torna al modello.

### **11.5) Separare proprietà e testo: MarkdownNote**

Ogni pagina della wiki inizia con il frontmatter. Questo record lo separa dal testo e lo legge con **SnakeYAML**, la libreria YAML già inclusa in Spring Boot.  
*PERCORSO: wiki/MarkdownNote.java*

| package com.example.chatbot.wiki; import java.util.Collection; import java.util.Collections; import java.util.LinkedHashMap; import java.util.List; import java.util.Map; import java.util.Objects; import java.util.Optional; import java.util.regex.Matcher; import java.util.regex.Pattern; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.yaml.snakeyaml.LoaderOptions; import org.yaml.snakeyaml.Yaml; import org.yaml.snakeyaml.constructor.SafeConstructor; import org.yaml.snakeyaml.error.YAMLException; /\*\* Una pagina Markdown divisa in proprietà (il frontmatter YAML) e corpo del testo. \*/ public record MarkdownNote(Map\<String, Object\> properties, String body) {     private static final Logger log \= LoggerFactory.getLogger(MarkdownNote.class);     /\*\* Il frontmatter è il blocco tra due righe "---" all'inizio del file. \*/     private static final Pattern FRONTMATTER \= Pattern.compile("\\\\A---\\\\R(.\*?)\\\\R---\[ \\\\t\]\*(?:\\\\R\|\\\\z)", Pattern.DOTALL);     public MarkdownNote {         properties \= Collections.unmodifiableMap(new LinkedHashMap\<\>(properties));     }     public static MarkdownNote parse(String markdown) {         String text \= markdown.startsWith("") ? markdown.substring(1) : markdown;         Matcher matcher \= FRONTMATTER.matcher(text);         if (\!matcher.find()) {             return new MarkdownNote(Map.of(), text);         }         return new MarkdownNote(parseYaml(matcher.group(1)), text.substring(matcher.end()));     }     /\*\* Valore testuale di una proprietà, ad esempio "department: HR". \*/     public Optional\<String\> text(String key) {         return Optional.ofNullable(properties.get(key))                 .map(value \-\> String.valueOf(value).trim())                 .filter(value \-\> \!value.isEmpty());     }     /\*\* Valori di una proprietà lista, ad esempio "tags: \[rag, spring-ai\]". Accetta anche un valore singolo. \*/     public List\<String\> list(String key) {         if (properties.get(key) instanceof Collection\<?\> values) {             return values.stream()                     .filter(Objects::nonNull)                     .map(value \-\> String.valueOf(value).trim())                     .filter(value \-\> \!value.isEmpty())                     .toList();         }         return text(key).map(List::of).orElse(List.of());     }     private static Map\<String, Object\> parseYaml(String yamlText) {         // SafeConstructor crea solo mappe, liste e valori semplici: mai oggetti Java arbitrari         Yaml yaml \= new Yaml(new SafeConstructor(new LoaderOptions()));         try {             if (\!(yaml.load(yamlText) instanceof Map\<?, ?\> map)) {                 return Map.of();             }             Map\<String, Object\> properties \= new LinkedHashMap\<\>();             map.forEach((key, value) \-\> properties.put(String.valueOf(key), value));             return properties;         } catch (YAMLException e) {             log.warn("Frontmatter non valido, la pagina viene letta senza proprietà: {}", e.getMessage());             return Map.of();         }     } } |
| :---- |

Due dettagli da senior:

* **SafeConstructor**: lo YAML può contenere tag che chiedono di creare oggetti Java arbitrari, un classico vettore d'attacco. Con SafeConstructor la libreria crea solo mappe, liste e valori semplici. Le pagine della wiki nascono da fonti esterne: trattiamo anche il loro YAML come input non fidato;  
* **errori gestiti**: un frontmatter scritto male non blocca nulla. La pagina viene letta senza proprietà e il problema finisce nei log, dove il lint lo può trovare.

### **11.6) I collegamenti: WikiLink e WikiLinks**

Ora insegniamo a Java la sintassi dei wikilink. Questa classe ci serve due volte: qui per pulire il testo prima degli embedding, nel paragrafo 12 per costruire il grafo e convertire i collegamenti per la web app.  
*PERCORSO: wiki/WikiLink.java*

| package com.example.chatbot.wiki; /\*\*  \* Un collegamento di Obsidian: \[\[destinazione\#sezione\|testo\]\], oppure \!\[\[file\]\] per un incorporamento.  \*  \* @param target  pagina o file di destinazione, ad esempio "Embedding" o "concetti/embedding.md"  \* @param heading sezione della pagina (facoltativa)  \* @param alias   testo da mostrare al posto del nome della pagina (facoltativo)  \* @param embed   true per \!\[\[...\]\]: il contenuto viene incorporato nella pagina  \*/ public record WikiLink(String target, String heading, String alias, boolean embed) {     /\*\* Il testo da mostrare: l'alias se c'è, altrimenti il nome della pagina senza cartelle ed estensione. \*/     public String displayText() {         if (alias \!= null) {             return alias;         }         String name \= target.substring(target.lastIndexOf('/') \+ 1);         return name.endsWith(".md") ? name.substring(0, name.length() \- ".md".length()) : name;     } } |
| :---- |

*PERCORSO: wiki/WikiLinks.java*

| package com.example.chatbot.wiki; import java.util.ArrayList; import java.util.List; import java.util.function.Function; import java.util.regex.MatchResult; import java.util.regex.Matcher; import java.util.regex.Pattern; import java.util.stream.Collectors; /\*\* Legge e trasforma i wikilink di Obsidian. Le righe dentro i blocchi di codice non vengono mai toccate. \*/ public final class WikiLinks {     // (\!?)          incorporamento facoltativo: \!\[\[immagine.png\]\]     // (\[^\\\[\\\]\|\#\]+)  destinazione: qualsiasi carattere tranne \[ \] \| \#     // (\#...)?       sezione facoltativa     // (\\\|...)?      testo alternativo facoltativo     private static final Pattern WIKI\_LINK \= Pattern.compile(             "(\!?)\\\\\[\\\\\[(\[^\\\\\[\\\\\]\|\#\\\\r\\\\n\]+)(?:\#(\[^\\\\\[\\\\\]\|\\\\r\\\\n\]\*))?(?:\\\\\|(\[^\\\\\[\\\\\]\\\\r\\\\n\]\*))?\]\]");     private static final Pattern CODE\_FENCE \= Pattern.compile("^\\\\s\*(\`\`\`\|\~\~\~)");     private WikiLinks() {     }     /\*\* Tutti i wikilink del testo, nell'ordine in cui compaiono. \*/     public static List\<WikiLink\> extract(String markdown) {         return lines(markdown).stream()                 .filter(line \-\> \!line.code())                 .flatMap(line \-\> WIKI\_LINK.matcher(line.text()).results())                 .map(WikiLinks::toLink)                 .toList();     }     /\*\* Testo pulito per gli embedding: \[\[Pagina\|testo\]\] diventa "testo", gli incorporamenti spariscono. \*/     public static String toPlainText(String markdown) {         return replaceLinks(markdown, link \-\> link.embed() ? "" : link.displayText());     }     /\*\* Sostituisce ogni wikilink con il testo restituito dalla funzione. \*/     public static String replaceLinks(String markdown, Function\<WikiLink, String\> replacement) {         return lines(markdown).stream()                 .map(line \-\> line.code()                         ? line.text()                         : WIKI\_LINK.matcher(line.text())                                 .replaceAll(match \-\> Matcher.quoteReplacement(replacement.apply(toLink(match)))))                 .collect(Collectors.joining());     }     private static WikiLink toLink(MatchResult match) {         return new WikiLink(                 match.group(2).strip(),                 blankToNull(match.group(3)),                 blankToNull(match.group(4)),                 \!match.group(1).isEmpty());     }     private static String blankToNull(String value) {         return value \== null \|\| value.isBlank() ? null : value.strip();     }     /\*\* Divide il testo in righe (ognuna con il suo "a capo") e segna quelle dentro i blocchi di codice. \*/     private static List\<Line\> lines(String markdown) {         List\<Line\> lines \= new ArrayList\<\>();         boolean insideCodeBlock \= false;         for (String text : markdown.split("(?\<=\\\\n)")) {             boolean fence \= CODE\_FENCE.matcher(text).find();             lines.add(new Line(text, insideCodeBlock \|\| fence));             if (fence) {                 insideCodeBlock \= \!insideCodeBlock;             }         }         return lines;     }     private record Line(String text, boolean code) {     } } |
| :---- |

Vediamo i punti principali:

* l'espressione regolare riconosce tutte le forme dei wikilink: \[\[Pagina\]\], \[\[Pagina\|testo\]\], \[\[Pagina\#Sezione\]\] e gli incorporamenti \!\[\[file.png\]\];  
* le righe dentro i blocchi di codice non vengono mai toccate: in una wiki tecnica un esempio di codice può contenere doppie parentesi quadre che non sono collegamenti;  
* toPlainText trasforma \[\[concetti/chunking\|il chunking\]\] in "il chunking": il modello di embedding riceve testo naturale invece di sintassi di Obsidian;  
* Matcher.quoteReplacement evita che caratteri come \$ nel testo vengano scambiati per riferimenti ai gruppi dell'espressione regolare. È un errore classico, facile da non vedere nei test.

### **11.7) L'indicizzazione: WikiIndexStateRepository e WikiIngestionService**

Il repository usa il JdbcTemplate già configurato nella L17 per leggere e scrivere la tabella di stato.  
*PERCORSO: wiki/WikiIndexStateRepository.java*

| package com.example.chatbot.wiki; import java.util.HashMap; import java.util.Map; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository; /\*\* Ricorda l'impronta (hash) di ogni pagina già indicizzata, per non rigenerare embedding inutili. \*/ @Repository public class WikiIndexStateRepository {     private final JdbcTemplate jdbcTemplate;     public WikiIndexStateRepository(JdbcTemplate jdbcTemplate) {         this.jdbcTemplate \= jdbcTemplate;     }     /\*\* Mappa percorso della pagina → hash del contenuto indicizzato. \*/     public Map\<String, String\> findAllHashes() {         return jdbcTemplate.query("SELECT path, content\_hash FROM wiki\_index\_state", resultSet \-\> {             Map\<String, String\> hashes \= new HashMap\<\>();             while (resultSet.next()) {                 hashes.put(resultSet.getString("path"), resultSet.getString("content\_hash"));             }             return hashes;         });     }     public void save(String path, String contentHash) {         jdbcTemplate.update("""                 INSERT INTO wiki\_index\_state (path, content\_hash, indexed\_at)                 VALUES (?, ?, now())                 ON CONFLICT (path) DO UPDATE                 SET content\_hash \= EXCLUDED.content\_hash, indexed\_at \= now()                 """, path, contentHash);     }     public void delete(String path) {         jdbcTemplate.update("DELETE FROM wiki\_index\_state WHERE path \= ?", path);     } } |
| :---- |

*PERCORSO: wiki/WikiReindexReport.java*

| package com.example.chatbot.wiki; /\*\* Esito di una reindicizzazione: pagine aggiornate, invariate, rimosse e non leggibili. \*/ public record WikiReindexReport(int pages, int updated, int unchanged, int removed, int failed) { } |
| :---- |

E ora il servizio che porta la wiki in pgvector:  
*PERCORSO: wiki/WikiIngestionService.java*

| package com.example.chatbot.wiki; import com.example.chatbot.service.RagService; import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.security.NoSuchAlgorithmException; import java.util.HashSet; import java.util.HexFormat; import java.util.List; import java.util.Map; import java.util.Set; import java.util.concurrent.locks.ReentrantLock; import org.slf4j.Logger; import org.slf4j.LoggerFactory; import org.springframework.ai.document.Document; import org.springframework.ai.reader.markdown.MarkdownDocumentReader; import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig; import org.springframework.ai.transformer.splitter.TokenTextSplitter; import org.springframework.ai.vectorstore.VectorStore; import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder; import org.springframework.core.io.ByteArrayResource; import org.springframework.stereotype.Service; /\*\*  \* Porta le pagine della LLM Wiki nel database vettoriale della L17.  \* Reindicizza solo le pagine cambiate e rimuove i chunk delle pagine cancellate.  \*/ @Service public class WikiIngestionService {     public static final String SOURCE\_TYPE \= "wiki";     private static final Logger log \= LoggerFactory.getLogger(WikiIngestionService.class);     private static final String DOCUMENT\_ID\_PREFIX \= "wiki:";     // Lo stesso nome di chiave usato da PagePdfDocumentReader: così le citazioni della L18 funzionano     // anche per la wiki. Al posto del numero di pagina mostriamo il titolo della sezione.     private static final String PAGE\_METADATA\_KEY \= "page\_number";     private final WikiVault wikiVault;     private final VectorStore vectorStore;     private final WikiIndexStateRepository stateRepository;     private final TokenTextSplitter splitter \= TokenTextSplitter.builder()             .withChunkSize(500)             .withMinChunkSizeChars(200)             .withMinChunkLengthToEmbed(20)             .withMaxNumChunks(10000)             .withKeepSeparator(true)             .build();     private final ReentrantLock reindexLock \= new ReentrantLock();     public WikiIngestionService(WikiVault wikiVault,                                 VectorStore vectorStore,                                 WikiIndexStateRepository stateRepository) {         this.wikiVault \= wikiVault;         this.vectorStore \= vectorStore;         this.stateRepository \= stateRepository;     }     public WikiReindexReport reindex() {         // Due reindicizzazioni contemporanee scriverebbero gli stessi chunk due volte         if (\!reindexLock.tryLock()) {             throw new IllegalStateException("Reindicizzazione della wiki già in corso");         }         try {             return reindexChangedPages();         } finally {             reindexLock.unlock();         }     }     private WikiReindexReport reindexChangedPages() {         List\<String\> pages \= wikiVault.listPages();         Map\<String, String\> indexedHashes \= stateRepository.findAllHashes();         int updated \= 0;         int failed \= 0;         for (String path : pages) {             try {                 String markdown \= wikiVault.readPage(path);                 String hash \= sha256(markdown);                 if (\!hash.equals(indexedHashes.get(path))) {                     replaceChunks(path, markdown);                     stateRepository.save(path, hash);                     updated++;                 }             } catch (WikiAccessException e) {                 // Una pagina illeggibile (ad esempio troppo grande) non deve bloccare tutte le altre                 log.warn("Pagina saltata: {}", e.getMessage());                 failed++;             }         }         // Le pagine cancellate dal vault devono sparire anche dal database vettoriale         Set\<String\> removedPages \= new HashSet\<\>(indexedHashes.keySet());         pages.forEach(removedPages::remove);         removedPages.forEach(path \-\> {             deleteChunks(path);             stateRepository.delete(path);         });         int unchanged \= pages.size() \- updated \- failed;         return new WikiReindexReport(pages.size(), updated, unchanged, removedPages.size(), failed);     }     private void replaceChunks(String path, String markdown) {         deleteChunks(path);         List\<Document\> chunks \= splitter.apply(toDocuments(path, markdown));         if (\!chunks.isEmpty()) {             vectorStore.add(chunks);         }     }     private void deleteChunks(String path) {         vectorStore.delete(new FilterExpressionBuilder()                 .eq("document\_id", DOCUMENT\_ID\_PREFIX \+ path)                 .build());     }     private List\<Document\> toDocuments(String path, String markdown) {         // Il frontmatter va tolto prima della lettura: MarkdownDocumentReader lo scambierebbe per un titolo         MarkdownNote note \= MarkdownNote.parse(markdown);         MarkdownDocumentReaderConfig config \= MarkdownDocumentReaderConfig.builder()                 .withHorizontalRuleCreateDocument(true)                 .withIncludeCodeBlock(true)                 .withAdditionalMetadata(Map.of(                         "source\_type", SOURCE\_TYPE,                         "document\_id", DOCUMENT\_ID\_PREFIX \+ path,                         "original\_filename", path,                         // Stesso filtro per reparto della L18: senza "department" la pagina sarebbe invisibile                         "department", note.text("department").orElse(RagService.PUBLIC\_DEPARTMENT),                         "tags", String.join(",", note.list("tags"))))                 .build();         byte\[\] body \= WikiLinks.toPlainText(note.body()).getBytes(StandardCharsets.UTF\_8);         return new MarkdownDocumentReader(new ByteArrayResource(body), config).get().stream()                 .map(section \-\> section.mutate()                         .metadata(PAGE\_METADATA\_KEY, section.getMetadata().getOrDefault("title", "introduzione"))                         .build())                 .toList();     }     private static String sha256(String text) {         try {             MessageDigest digest \= MessageDigest.getInstance("SHA-256");             return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF\_8)));         } catch (NoSuchAlgorithmException e) {             throw new IllegalStateException("SHA-256 non disponibile", e);         }     } } |
| :---- |

Vediamo le scelte una per una:

1. **solo le pagine cambiate**: l'hash SHA-256 del contenuto viene confrontato con quello salvato. Se è uguale, la pagina viene saltata e non spendiamo token di embedding;  
2. **prima elimina, poi aggiungi**: i vecchi chunk di una pagina vengono cancellati con un filtro su document\_id, come per i PDF nella L18 (paragrafo 11.1). Così una pagina riscritta non lascia chunk superati nel database;  
3. **le pagine cancellate spariscono**: se una pagina non esiste più nel vault, i suoi chunk e la sua riga di stato vengono rimossi;  
4. **il frontmatter va tolto prima della lettura**: MarkdownDocumentReader userebbe le righe \--- e il blocco YAML come se fossero una riga orizzontale e un titolo, e le proprietà finirebbero negli embedding;  
5. **un Document per sezione**: MarkdownDocumentReader crea un documento per ogni titolo della pagina e ne salva il testo nel metadato title. Le sezioni lunghe vengono poi divise da TokenTextSplitter, con gli stessi valori della L17;  
6. **metadati compatibili con la L18**: department permette al filtro per reparto della L18 di funzionare anche sulla wiki (senza questo metadato, gli utenti normali non vedrebbero le pagine), mentre original\_filename e page\_number permettono alle citazioni del DocumentContextAdvisor di mostrare il percorso della pagina e il titolo della sezione;  
7. **un errore non blocca tutto**: una pagina illeggibile, ad esempio troppo grande, viene contata in failed e le altre proseguono;  
8. **una reindicizzazione alla volta**: il ReentrantLock impedisce che due richieste contemporanee scrivano due volte gli stessi chunk.

### **11.8) L'endpoint di reindicizzazione e lo strumento readWikiPage**

Aggiungiamo al DocumentController della L17 un endpoint per avviare la reindicizzazione. Il percorso /api/documents/\*\* è già riservato agli amministratori dalla SecurityConfig della L18 (paragrafo 7.2): non serve toccare la sicurezza.  
*PERCORSO: controller/DocumentController.java (metodo da aggiungere)*

| // Nel costruttore aggiungi anche: WikiIngestionService wikiIngestionService @PostMapping("/wiki/reindex") public Mono\<WikiReindexReport\> reindexWiki() {     // Lettura dei file e chiamate al modello di embedding sono bloccanti: boundedElastic, come nella L17     return Mono.fromCallable(wikiIngestionService::reindex)             .subscribeOn(Schedulers.boundedElastic()); } |
| :---- |

Proviamolo con un token da amministratore:

| curl \-X POST http\://localhost:8080/api/documents/wiki/reindex \\   \-H "Authorization: Bearer \<TOKEN\_ADMIN\>" |
| :---- |

La prima volta tutte le pagine vengono indicizzate:

| { "pages": 42, "updated": 42, "unchanged": 0, "removed": 0, "failed": 0 } |
| :---- |

Se richiami subito l'endpoint, updated scende a 0 e unchanged sale a 42: nessuna chiamata al modello di embedding. In pgAdmin puoi controllare i chunk della wiki con:

| SELECT metadata-\>\>'original\_filename' AS pagina,        metadata-\>\>'page\_number'       AS sezione,        COUNT(\*)                       AS chunk FROM vector\_store WHERE metadata-\>\>'source\_type' \= 'wiki' GROUP BY pagina, sezione ORDER BY pagina; |
| :---- |

Ora il livello 2: lo strumento che permette al modello di aprire una pagina intera.  
*PERCORSO: wiki/WikiTools.java*

| package com.example.chatbot.wiki; import com.example.chatbot.security.UserContext; import com.example.chatbot.service.RagService; import com.example.chatbot.tools.ToolAccessDeniedException; import org.springframework.ai.chat.model.ToolContext; import org.springframework.ai.tool.annotation.Tool; import org.springframework.ai.tool.annotation.ToolParam; import org.springframework.stereotype.Component; /\*\* Strumento di sola lettura: permette all'LLM di aprire una pagina intera della wiki e seguirne i link. \*/ @Component public class WikiTools {     private static final int MAX\_CHARACTERS \= 12\_000;     private final WikiVault wikiVault;     public WikiTools(WikiVault wikiVault) {         this.wikiVault \= wikiVault;     }     @Tool(description \= """             Legge una pagina della wiki aziendale dato il suo percorso, ad esempio "concetti/embedding.md".             Usalo quando un documento recuperato proviene dalla wiki e ti serve la pagina completa,             oppure per seguire un collegamento \[\[...\]\] citato in una pagina.""")     public String readWikiPage(             @ToolParam(description \= "Percorso relativo della pagina, ad esempio concetti/embedding.md") String path,             ToolContext toolContext) {         UserContext user \= (UserContext) toolContext.getContext().get(UserContext.KEY);         MarkdownNote note \= MarkdownNote.parse(wikiVault.readPage(path));         // Stesse regole della ricerca vettoriale (L18, §7.3): i permessi si controllano anche nello strumento         String department \= note.text("department").orElse(RagService.PUBLIC\_DEPARTMENT);         boolean allowed \= user \!= null                 && (RagService.PUBLIC\_DEPARTMENT.equals(department) \|\| user.canAccessDepartment(department));         if (\!allowed) {             throw new ToolAccessDeniedException("Accesso negato: questa pagina è riservata a un altro reparto.");         }         String body \= note.body().length() \> MAX\_CHARACTERS                 ? note.body().substring(0, MAX\_CHARACTERS) \+ "\\n\[pagina troncata\]"                 : note.body();         // Come per i documenti RAG (L18, §5.3): il contenuto è un dato, delimitato da un tag che non può chiudere         return """                 \<pagina\_wiki percorso="%s"\>                 %s                 \</pagina\_wiki\>""".formatted(path, neutralizeTags(body));     }     private static String neutralizeTags(String text) {         return text.replace("\<", "‹").replace("\>", "›");     } } |
| :---- |

Ritroviamo le regole della L18:

* l'utente arriva dal **ToolContext**, mai da un parametro compilato dal modello (paragrafo 6.3);  
* lo strumento **controlla i permessi da solo**: senza questo controllo, un utente delle Vendite potrebbe aggirare il filtro della ricerca vettoriale chiedendo al modello di leggere una pagina dell'HR (terzo livello dei permessi, paragrafo 7.3);  
* il contenuto torna al modello **delimitato da un tag** che il testo della pagina non può chiudere (paragrafo 5.3).

Registriamo lo strumento nel ChatClientConfig della L18, accanto a quelli esistenti:

| .defaultTools(employeeTools, wikiTools) |
| :---- |

Poi aggiungiamo una regola in fondo al file prompts/system-prompt.st:

| REGOLE SULLA WIKI 9\. Se un documento recuperato proviene dalla wiki (la fonte è un file .md), puoi leggere    la pagina completa con lo strumento readWikiPage e seguire i collegamenti \[\[...\]\],    solo se servono a rispondere. |
| :---- |

### **11.9) È fattibile? Costi, limiti e accortezze**

Sì, ed è il motivo per cui conviene: abbiamo aggiunto classi senza modificare né la ricerca né gli advisor. Prima di portarlo in produzione, però, valuta questi punti:

* **costi**: l'ingest della wiki lo paga l'agente che usi sul tuo computer (abbonamento o API), mentre il backend paga solo gli embedding delle pagine cambiate;  
* **doppioni**: se indicizzi sia il PDF originale sia la pagina della wiki che lo riassume, la ricerca può recuperare due volte la stessa informazione. Puoi filtrare per source\_type, dare la precedenza ai chunk della wiki oppure indicizzare solo la wiki per gli argomenti già curati;  
* **aggiornamento**: avvia la reindicizzazione dopo ogni commit del vault, ad esempio con un hook di git che chiama l'endpoint, oppure con un metodo @Scheduled notturno;  
* **qualità**: un errore nella wiki diventa un errore del chatbot. Per questo ogni pagina cita le sue fonti, il lint gira regolarmente e le citazioni del chatbot riportano il percorso della pagina, così l'utente può verificare;  
* **sicurezza**: le pagine nascono da fonti esterne, quindi restano dati non fidati. Valgono tutte le difese della L17 e della L18: tag di delimitazione, regole nel system prompt, permessi negli strumenti, revisione umana delle modifiche.

**Checkpoint del paragrafo 11**:

* RAG e LLM Wiki si combinano: la wiki fornisce al RAG documenti già sintetizzati;  
* WikiVault è l'unico accesso ai file e blocca il path traversal;  
* WikiIngestionService indicizza solo le pagine cambiate, con metadati compatibili con la L18;  
* readWikiPage permette al modello di leggere pagine intere, rispettando i permessi per reparto;  
* nulla del vecchio sistema è stato smantellato.