package com.gestionale.industriatech.tools;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.gestionale.industriatech.security.UserContext;

/**
 * Apre un ticket di assistenza avviando un workflow di n8n tramite WEBHOOK.
 *
 * In n8n: workflow con nodo "Webhook" (POST, percorso "apri-ticket",
 * autenticazione "Header Auth" con header X-Api-Key) + i nodi che servono
 * (creazione del ticket, notifica...). Vedi lezione 6.
 *
 * Aprire un ticket è un'azione "leggera" e annullabile: per questo qui non
 * chiediamo conferma (a differenza delle email, paragrafo 6.4).
 */
@Component
public class SupportTicketTools {

    private static final Logger log = LoggerFactory.getLogger(SupportTicketTools.class);

    private final RestClient n8nClient;
    private final String webhookPath;
    private final boolean configured;

    public SupportTicketTools(
            @Value("${app.n8n.base-url:http://localhost:5678}") String baseUrl,
            @Value("${app.n8n.api-key:}") String apiKey,
            @Value("${app.n8n.ticket-webhook-path:/webhook/apri-ticket}") String webhookPath
    ) {
        this.n8nClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-Api-Key", apiKey)   // la chiave arriva da una variabile d'ambiente, mai dal codice
                .build();
        this.webhookPath = webhookPath;
        this.configured = !apiKey.isBlank();
    }

    @Tool(description = "Apre un ticket di assistenza tecnica a nome dell'utente corrente.")
    public String openSupportTicket(
            @ToolParam(description = "Titolo breve del problema") String title,
            @ToolParam(description = "Descrizione dettagliata del problema") String description,
            ToolContext toolContext) {

        UserContext user = (UserContext) toolContext.getContext().get(UserContext.KEY);
        if (user == null) {
            throw new ToolAccessDeniedException("Utente non riconosciuto.");
        }

        log.info("[TOOL] openSupportTicket(\"{}\") richiesto da {}", title, user.userId());

        if (!configured) {
            throw new IllegalStateException(
                    "Il sistema dei ticket non è ancora configurato. Contatta l'amministratore.");
        }

        try {
            n8nClient.post()
                    .uri(webhookPath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "userId", user.userId(),      // dal ToolContext, non dal modello
                            "department", user.department(),
                            "title", title,
                            "description", description))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            // Il messaggio torna al modello: niente dettagli tecnici (URL, stack trace)
            log.error("Chiamata al webhook n8n fallita", ex);
            throw new IllegalStateException(
                    "Il sistema di assistenza non è raggiungibile in questo momento. Riprova più tardi.");
        }

        return "Ticket inviato al sistema di assistenza.";
    }
}
