package com.gestionale.industriatech.config;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.ai.mcp.ToolContextToMcpMetaConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.modelcontextprotocol.client.transport.customizer.McpSyncHttpClientRequestCustomizer;

/**
 * Il nostro backend come CLIENT MCP (lezione 6.7), collegato al server MCP di n8n
 * (nodo "MCP Server Trigger").
 *
 * La connessione si attiva togliendo il commento alle proprietà
 * spring.ai.mcp.client.streamable-http.connections.n8n.* in application.properties.
 * Finché sono commentate, questi bean non fanno nulla.
 */
@Configuration
public class McpClientConfig {

    private static final Logger log = LoggerFactory.getLogger(McpClientConfig.class);

    // 1. Autenticazione Bearer verso n8n.
    //    Aggiungiamo il token SOLO alle richieste dirette all'host di n8n:
    //    se colleghiamo altri server MCP, il token di n8n non deve arrivare a loro.
    @Bean
    public McpSyncHttpClientRequestCustomizer n8nMcpAuthorization(
            @Value("${app.n8n.mcp-token:}") String token,
            @Value("${spring.ai.mcp.client.streamable-http.connections.n8n.url:}") String n8nUrl) {

        String n8nHost = n8nUrl.isBlank() ? null : URI.create(n8nUrl).getHost();

        return (requestBuilder, method, endpoint, body, context) -> {
            if (n8nHost != null && !token.isBlank() && n8nHost.equalsIgnoreCase(endpoint.getHost())) {
                requestBuilder.header("Authorization", "Bearer " + token);
            }
        };
    }

    // 2. Filtro: degli strumenti esposti dal server MCP teniamo solo quelli che ci servono.
    //    Le descrizioni degli strumenti esterni entrano nel nostro contesto e potrebbero
    //    contenere istruzioni malevole: meno strumenti = meno rischi (e meno token).
    @Bean
    public McpToolFilter allowedMcpToolsFilter(
            @Value("${app.mcp.allowed-tools:crea_ticket,stato_ordine}") List<String> allowedTools) {

        return (connectionInfo, tool) -> {
            boolean allowed = allowedTools.contains(tool.name());
            if (!allowed) {
                log.info("Strumento MCP scartato dal filtro: {}", tool.name());
            }
            return allowed;
        };
    }

    // 3. Spring AI inoltrerebbe ai server MCP il contenuto del ToolContext (campo nascosto "_meta"):
    //    id, reparto e ruolo dell'utente finirebbero a n8n. Spegniamo l'inoltro.
    @Bean
    public ToolContextToMcpMetaConverter toolContextToMcpMetaConverter() {
        return ToolContextToMcpMetaConverter.noOp();
    }
}
