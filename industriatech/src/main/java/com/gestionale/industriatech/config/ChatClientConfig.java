package com.gestionale.industriatech.config;

import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.PostgresChatMemoryRepositoryDialect;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DataSourceInitializer;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import com.gestionale.industriatech.rag.DocumentContextAdvisor;
import com.gestionale.industriatech.security.PromptCanary;
import com.gestionale.industriatech.tools.EmailTools;
import com.gestionale.industriatech.tools.EmployeeTools;
import com.gestionale.industriatech.tools.SupportTicketTools;
import com.gestionale.industriatech.wiki.WikiTools;

/**
 * Configurazione del ChatClient con memoria e RAG tramite advisor.
 *
 * Il progetto usa DUE database (configurazione "11B"):
 *   - MariaDB  -> dati del gestionale (JPA)
 *   - PostgreSQL -> vettori (pgvector) e memoria delle conversazioni
 * Per questo la memoria va configurata a mano: la configurazione automatica
 * guarderebbe MariaDB per capire il tipo di database.
 */
@Configuration
public class ChatClientConfig {

    // 1. Crea la tabella SPRING_AI_CHAT_MEMORY su PostgreSQL all'avvio, se manca.
    //    Usiamo lo script ufficiale incluso nella libreria di Spring AI
    //    (CREATE TABLE IF NOT EXISTS: si può eseguire a ogni avvio).
    @Bean
    public DataSourceInitializer chatMemorySchemaInitializer(
            @Qualifier("pgVectorDataSource") DataSource pgVectorDataSource) {

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("org/springframework/ai/chat/memory/repository/jdbc/schema-postgresql.sql"));

        DataSourceInitializer initializer = new DataSourceInitializer();
        initializer.setDataSource(pgVectorDataSource);
        initializer.setDatabasePopulator(populator);
        return initializer;
    }

    // 2. Dove salvare i messaggi: PostgreSQL, tramite il JdbcTemplate @Primary di VectorStoreConfig.
    //    Definendo noi questo bean, Spring AI non crea il suo.
    @Bean
    public JdbcChatMemoryRepository chatMemoryRepository(JdbcTemplate jdbcTemplate) {
        return JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new PostgresChatMemoryRepositoryDialect())
                .build();
    }

    // 3. Quanti messaggi ricordare: gli ultimi 10 di ogni conversazione.
    //    Meno messaggi = meno token a ogni chiamata, ma memoria più corta.
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(10)
                .build();
    }

    // 4. Il ChatClient del RAG: system prompt + catena di advisor + strumenti (lezione 6)
    @Bean
    public ChatClient ragChatClient(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            DocumentContextAdvisor documentContextAdvisor,
            EmployeeTools employeeTools,
            EmailTools emailTools,
            SupportTicketTools supportTicketTools,
            ObjectProvider<SyncMcpToolCallbackProvider> mcpTools,
            ObjectProvider<WikiTools> wikiTools,
            PromptCanary canary,
            @Value("${app.guardrails.safeguard.enabled:false}") boolean safeGuardEnabled,
            @Value("${app.guardrails.safeguard.sensitive-words:password,credenziali di accesso}") List<String> sensitiveWords,
            @Value("classpath:/prompts/system-prompt.st") Resource systemPrompt) {

        List<Advisor> advisors = new ArrayList<>(List.of(
                MessageChatMemoryAdvisor.builder(chatMemory).build(), // storico (ordine +200)
                documentContextAdvisor));                            // documenti (ordine +250)

        // Lezione 8.3: guardrail già pronto di Spring AI, SPENTO per impostazione predefinita.
        // ATTENZIONE: controlla l'INTERO prompt (system prompt, storico, documenti), non solo
        // la domanda: se una parola vietata compare in un documento, blocca anche domande innocue.
        // E la domanda bloccata resta nella memoria: blocca anche le successive della stessa chat.
        if (safeGuardEnabled) {
            advisors.add(SafeGuardAdvisor.builder()
                    .sensitiveWords(sensitiveWords)
                    .failureResponse("Non posso rispondere a questa richiesta.")
                    .build());
        }

        // Lezione 9: in fondo alla catena (dopo il ToolCallingAdvisor) stampa ogni richiesta e
        // risposta del modello, compresi i giri del ciclo degli strumenti. Scrive a livello DEBUG:
        // si accende/spegne con logging.level.org.springframework.ai.chat.client.advisor
        advisors.add(new SimpleLoggerAdvisor());

        ChatClient.Builder configured = builder
                // Il system prompt è un template: {canary} viene sostituito con la parola in codice
                .defaultSystem(system -> system.text(systemPrompt).param("canary", canary.value()))
                .defaultAdvisors(advisors)
                // Strumenti: il modello può chiamare SOLO quelli registrati qui (minimo privilegio).
                // Il ciclo "chiedi strumento -> esegui -> rimanda il risultato" lo gestisce
                // il ToolCallingAdvisor, che Spring AI aggiunge da solo (ordine +300).
                .defaultTools(employeeTools, emailTools, supportTicketTools);

        // Strumenti esterni da server MCP (n8n), se la connessione è configurata
        mcpTools.ifAvailable(configured::defaultToolCallbacks);

        // Lezione 11: readWikiPage, solo se la wiki è configurata (WIKI_VAULT_PATH)
        wikiTools.ifAvailable(configured::defaultTools);

        return configured.build();
    }
}
