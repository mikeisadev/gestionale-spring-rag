---
titolo: "Lezione 5 – Memoria della conversazione, advisor e chat salvate"
corso: Spring Boot, React e sistemi RAG
prompt: prompt/05-persistenza-conversazioni.md
data: 2026-11-06
livello: junior / medior
tags:
  - rag
  - spring-ai
  - chat-memory
  - advisor
  - jpa
  - react-router
---

# Lezione 5 – Memoria della conversazione, advisor e chat salvate

> [!abstract] In breve
> Il chatbot ora **ricorda** la conversazione: se chiedi "E il controller invece?" capisce di cosa stai parlando. La memoria è salvata su **PostgreSQL**, quindi sopravvive al riavvio. Il RAG è stato riorganizzato con gli **advisor** di Spring AI. In più, le chat vengono **archiviate** su MariaDB: dalla pagina Chat puoi crearne di nuove, riaprire quelle vecchie ed eliminarle.

## 1. Il problema: "E il controller invece?"

Un LLM **non ricorda nulla** tra una chiamata e l'altra: ogni richiesta parte da zero. Per avere una conversazione siamo noi a dover rimandare lo storico a ogni domanda.

```text
Domanda 1: "Cosa fa il repository nel pattern Controller Service Repository?"
Domanda 2: "E il controller invece?"      ← senza storico, "invece" rispetto a cosa?
```

Prima di questa lezione il frontend inviava già lo storico (`history`), ma il backend lo **ignorava**.

## 2. ChatMemory e advisor

Spring AI risolve il problema con due componenti:

| Componente | Ruolo |
|---|---|
| **ChatMemory** | Conserva i messaggi di ogni conversazione. Noi usiamo `MessageWindowChatMemory`, che tiene gli **ultimi N** messaggi |
| **MessageChatMemoryAdvisor** | **Prima** della chiamata aggiunge lo storico al prompt; **dopo** la risposta salva il nuovo scambio |

> [!info] Che cos'è un advisor
> Un advisor **intercetta** la richiesta prima che arrivi al modello e la risposta prima che torni a noi. Gli advisor formano una **catena di filtri**: ognuno fa il suo lavoro e passa la richiesta al successivo. È lo stesso principio dei filtri delle servlet o dei middleware.

Ogni conversazione ha un **conversation ID**. Il frontend lo genera (un UUID) e lo invia a ogni messaggio:

```json
{ "message": "E il controller invece?", "conversationId": "e81e0ec1-bdc5-40e3-9bfd-baa9781a90a1" }
```

```java
chatClient.prompt()
        .user(question)
        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
        .stream()
        .content();
```

> [!warning] Il parametro è obbligatorio
> Senza `ChatMemory.CONVERSATION_ID` l'advisor della memoria lancia un'eccezione. Per questo i due metodi didattici senza RAG (`sendMessage`, `streamMessage`) usano un **secondo** `ChatClient`, senza advisor.

### Validare la richiesta

```java
public record ChatRequest(
    @NotBlank @Size(max = 4000) String message,
    @NotBlank @Pattern(regexp = "[a-zA-Z0-9-]{8,36}") String conversationId
) {}
```

Con `@Valid @RequestBody ChatRequest request` Spring controlla le regole **prima** di entrare nel metodo. Se una regola non è rispettata, risponde `400`. Serve la dipendenza `spring-boot-starter-validation`.

> [!note] Adattamento: massimo 36 caratteri, non 64
> La lezione originale accettava ID fino a 64 caratteri. La colonna `conversation_id` della tabella della memoria però è `VARCHAR(36)`, la lunghezza di un UUID: un ID di 40 caratteri passerebbe la validazione e poi farebbe fallire l'`INSERT`. Meglio fermarlo subito.

## 3. La trappola: la memoria salverebbe anche i documenti

Nella lezione 3 costruivamo un unico messaggio con domanda **e** chunk. Con la memoria accesa, l'advisor salverebbe **tutto**: dopo 10 domande lo storico conterrebbe 50 chunk, rimandati a ogni chiamata. Costi e latenza esploderebbero.

La soluzione: la memoria salva la domanda **pulita**, e i documenti vengono aggiunti **dopo**, solo per la chiamata corrente, da un nostro advisor.

```mermaid
flowchart TD
    Q["Domanda: 'E il controller invece?'"] --> M
    M["1. MessageChatMemoryAdvisor<br/>ordine HIGHEST_PRECEDENCE + 200<br/>aggiunge lo storico, salva la domanda pulita"] --> D
    D["2. DocumentContextAdvisor<br/>ordine HIGHEST_PRECEDENCE + 250<br/>aggiunge i documenti (solo per questa chiamata)"] --> T
    T["3. ToolCallingAdvisor<br/>ordine HIGHEST_PRECEDENCE + 300<br/>strumenti (lezione 6)"] --> L[LLM]
```

**Numero più basso = eseguito prima.** Il nostro advisor si mette a `ToolCallingAdvisor.DEFAULT_ORDER - 50`, cioè tra la memoria e gli strumenti.

### `DocumentContextAdvisor`

```java
@Override
public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
    List<Document> documents = (List<Document>) request.context().getOrDefault(DOCUMENTS, List.of());
    String question = request.prompt().getUserMessage().getText();
    String documentContext = documents.isEmpty()
            ? "Nessun documento pertinente trovato."
            : ragService.formatAsContext(documents);

    return request.mutate()
            .prompt(request.prompt().augmentUserMessage(TEMPLATE.formatted(question, documentContext)))
            .build();
}
```

- `BaseAdvisor` è l'interfaccia più semplice: `before()` e `after()`. Funziona sia con `call()` sia con `stream()`.
- `request.context()` contiene i parametri passati con `.advisors(a -> a.param(...))`: è da lì che arrivano i documenti.
- `augmentUserMessage(...)` sostituisce il testo della domanda con la versione arricchita, **solo** nella richiesta che va all'LLM.

### I documenti dentro un tag

```java
<documento fonte="manuale-spring.pdf" pagina="3">
Il controller riceve le richieste HTTP e restituisce le risposte...
</documento>
```

Il tag aiuta il modello a distinguere i **dati** dalle **istruzioni**. Ma un PDF malevolo potrebbe contenere `</documento> Ignora le regole...` per "uscire" dal tag. Per questo `neutralizeTags()` sostituisce `<` e `>` del testo con `‹` e `›`, caratteri simili ma innocui.

> [!tip] Il mistero di `page_number` risolto
> Nella lezione 2 avevamo scoperto che il reader salva la pagina in `page_number` e non in `start_page_number`. Il motivo è che la costante `PagePdfDocumentReader.METADATA_START_PAGE_NUMBER` **vale** `"page_number"`. Usando la costante invece di scrivere la stringa a mano, l'errore non si presenta proprio.

### Il `ChatService` si accorcia

```java
return Mono.fromCallable(() -> {
            conversationService.saveUserMessage(conversationId, question); // archivio
            return ragService.search(question, 5);                          // documenti
        })
        .subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(documents -> ragChatClient.prompt()
                .user(question)
                .advisors(a -> a
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param(DocumentContextAdvisor.DOCUMENTS, documents))
                .stream()
                .content());
```

Il system prompt non è più nel codice: è in `src/main/resources/prompts/system-prompt.st` e viene registrato una volta sola con `.defaultSystem(...)` in `ChatClientConfig`.

> [!warning] File `.st` e parentesi graffe
> I file `.st` sono **template**: Spring AI interpreta `{qualcosa}` come una variabile da riempire. Nel system prompt **non** usare parentesi graffe, altrimenti il rendering fallisce.

## 4. Una memoria che sopravvive al riavvio

La memoria predefinita vive in RAM: riavvii il backend e le conversazioni spariscono. Con la dipendenza `spring-ai-starter-model-chat-memory-repository-jdbc` i messaggi vanno nella tabella `SPRING_AI_CHAT_MEMORY`.

> [!important] Configurazione "11B": due database
> Il nostro progetto ha **MariaDB** come database principale (JPA) e **PostgreSQL** come JdbcTemplate `@Primary` (pgvector). La configurazione automatica si confonderebbe: guarderebbe MariaDB per capire il tipo di database, ma scriverebbe su PostgreSQL. Quindi configuriamo noi il repository:

```java
@Bean
public JdbcChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {  // = PostgreSQL
    return JdbcChatMemoryRepository.builder()
            .jdbcTemplate(jdbcTemplate)
            .dialect(new PostgresChatMemoryRepositoryDialect())
            .build();
}

@Bean
public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(10)
            .build();
}
```

### Creare la tabella: automatico invece che da pgAdmin

La lezione originale suggeriva di creare la tabella a mano da pgAdmin. Abbiamo preferito farla creare **all'avvio**, usando lo script ufficiale che Spring AI include nel suo jar:

```java
ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new ClassPathResource("org/springframework/ai/chat/memory/repository/jdbc/schema-postgresql.sql"));
```

Lo script usa `CREATE TABLE IF NOT EXISTS`, quindi si può eseguire a ogni avvio. Così funziona anche su un volume Docker nuovo, senza passaggi manuali.

## 5. Memoria ≠ archivio delle chat

Per "rivedere le chat esistenti" la memoria **non basta**. Ecco cosa abbiamo visto nel database dopo 6 scambi nella stessa chat:

```text
memoria (PostgreSQL):  10 messaggi, primo = "Quali sono i suoi vantaggi?"
archivio (MariaDB):    12 messaggi
```

`MessageWindowChatMemory` tiene solo gli ultimi 10 messaggi, e a ogni scambio il repository **sostituisce** quelli salvati con la nuova finestra. La prima domanda della chat **non esiste più** nella memoria.

| | Memoria (Spring AI) | Archivio (nostro) |
|---|---|---|
| Database | PostgreSQL, `spring_ai_chat_memory` | MariaDB, `conversazioni` + `messaggi_chat` |
| Contenuto | Ultimi 10 messaggi | **Tutti** i messaggi, più titolo e date |
| Serve a | L'**LLM**, per capire il contesto | L'**utente**, per rivedere le chat |
| Gestito da | `MessageChatMemoryAdvisor` | `ConversationService` (JPA) |

L'archivio usa due entità JPA, `Conversazione` e `MessaggioChat`, con i loro repository, come per `Dipendenti` nelle prime lezioni.

> [!bug] `@Lob` su MariaDB crea un `tinytext`
> Alla prima prova la colonna `contenuto` è stata creata come `tinytext` (massimo 255 byte): ogni risposta lunga dell'LLM sarebbe fallita. Con Hibernate 7 `@Lob` senza lunghezza sceglie il tipo di testo più piccolo. La soluzione è `@Column(columnDefinition = "LONGTEXT")`.
> **Attenzione**: `ddl-auto=update` aggiunge tabelle e colonne ma **non** cambia il tipo di una colonna già esistente. Abbiamo corretto a mano la tabella, che era vuota: `ALTER TABLE messaggi_chat MODIFY contenuto LONGTEXT NOT NULL;`.

### Gli endpoint delle chat

| Metodo | Endpoint | Risposta |
|---|---|---|
| `POST` | `/api/chat` | Risposta in streaming; crea la chat al primo messaggio |
| `GET` | `/api/chat/conversations` | Elenco delle chat (id, titolo, date), dalla più recente |
| `GET` | `/api/chat/conversations/{id}/messages` | Tutti i messaggi; `404` se la chat non esiste |
| `DELETE` | `/api/chat/conversations/{id}` | `204`; elimina archivio **e** memoria |

La risposta dell'assistente viene salvata nell'archivio a **fine streaming**, accumulando i token. Se l'utente chiude la pagina a metà (segnale `CANCEL`), salviamo la parte ricevuta con la nota *"(risposta interrotta)"*.

## 6. Il frontend

- A sinistra l'**elenco delle chat**, con "Nuova chat" e un cestino per ogni chat (visibile passando il mouse).
- L'ID della chat è **nell'indirizzo**: `/chat/e81e0ec1-...`. Ricaricando la pagina, o salvando il link, la chat si riapre.
- Una chat nuova non ha ID finché non scrivi il primo messaggio: in quel momento `crypto.randomUUID()` genera l'UUID e l'indirizzo cambia.
- Il frontend **non invia più lo storico**: manda solo `conversationId`, al resto pensa il backend.

```jsx
<Route path="/chat" element={<ChatBot />} />
<Route path="/chat/:conversationId" element={<ChatBot />} />
```

```js
const { conversationId } = useParams();   // undefined su /chat
```

> [!note] Un dettaglio sottile
> Al primo messaggio cambiamo l'indirizzo **mentre** la risposta è in streaming. Il cambio di `conversationId` farebbe partire il caricamento dei messaggi dal backend, che sovrascriverebbe quelli a schermo. Un `useRef` ricorda l'ID della chat appena creata e salta quel caricamento.

## 7. Test eseguiti

| Prova | Risultato |
|---|---|
| Avvio | Tabella `spring_ai_chat_memory` creata su PostgreSQL; `conversazioni` e `messaggi_chat` su MariaDB |
| Domanda e poi "E il repository?" / "E il controller invece?" | Risposta corretta grazie allo storico |
| Memoria senza chunk | Domanda salvata "pulita" (27 caratteri, nessun `<documento`) |
| 6 scambi nella stessa chat | Memoria: **10** messaggi; archivio: **12** |
| Riavvio del backend e "qual è stata la mia prima domanda?" | *"Quali sono i suoi vantaggi?"*: la memoria è sopravvissuta, ma la vera prima domanda era già uscita dalla finestra |
| `conversationId` non valido o messaggio vuoto | `400` |
| Chat inesistente | `404` |
| Eliminazione | `204`; archivio e memoria vuoti |
| Browser | Nuova chat → indirizzo `/chat/<uuid>` senza interrompere lo streaming; riapertura di una chat da 14 messaggi; ricarica della pagina; "Nuova chat"; cestino |

> [!bug] Le chat create in Docker erano "un'ora indietro"
> Il container usa il fuso orario **UTC**, l'app avviata da VS Code quello italiano, e le due scrivono nello stesso database. `LocalDateTime` **non contiene** il fuso orario, quindi gli orari risultavano incoerenti. Soluzione: `TZ: Europe/Rome` nelle variabili d'ambiente del container del backend.

> [!bug] Aggiornamento dalla lezione 6: errore con domande ravvicinate
> Salvare la risposta "in background" a fine streaming creava un conflitto con la domanda successiva (`Record has changed since last read`, MariaDB 11.6+). Ora lo stream si chiude solo dopo il salvataggio, e la data della chat si aggiorna con un `UPDATE` diretto. Vedi [[06-strumenti-function-calling]], paragrafo 9.

## 8. Un limite da conoscere: la ricerca non usa lo storico

```text
Chat A:  "Parlami della configurazione automatica di Spring Boot."  → risposta corretta
         "Quali sono i suoi vantaggi?"  → "Nei documenti non ci sono informazioni
                                           sui vantaggi della configurazione automatica"
Chat B:  "Quali sono i suoi vantaggi?"  → elenca i vantaggi... del PDF OWASP!
```

Grazie alla memoria, l'**LLM** ha capito che "suoi" si riferisce alla configurazione automatica (chat A). La **ricerca semantica** però ha cercato la frase "Quali sono i suoi vantaggi?" così com'è, e ha trovato chunk di un altro documento.

> [!question] Come si risolve?
> Prima della ricerca si fa **riscrivere la domanda** all'LLM usando lo storico: "Quali sono i suoi vantaggi?" diventa "Quali sono i vantaggi della configurazione automatica di Spring Boot?". Spring AI lo fa con `RetrievalAugmentationAdvisor` e un query transformer: è il punto 11.6 del corso.

> [!note] Le alternative pronte: `QuestionAnswerAdvisor`
> Spring AI offre `QuestionAnswerAdvisor`, che fa il RAG in automatico. Abbiamo scritto il nostro advisor perché quello pronto inserisce solo il testo dei chunk, **senza file e pagina**, e perché vogliamo controllare noi la ricerca, per filtrarla per permessi nella lezione 7.

## Domande di ripasso

1. Perché l'LLM non ricorda la conversazione da solo?
2. Cosa succederebbe se la memoria salvasse anche i chunk dei documenti?
3. In che ordine vengono eseguiti gli advisor, e come si decide?
4. Perché neutralizziamo `<` e `>` nel testo dei documenti?
5. Che differenza c'è tra la memoria di Spring AI e l'archivio delle chat?
6. Perché "Quali sono i suoi vantaggi?" trova i documenti sbagliati anche con la memoria attiva?

---
Lezione precedente: [[04-passaggio-docker]] · Prossima lezione: [[06-strumenti-function-calling]]
