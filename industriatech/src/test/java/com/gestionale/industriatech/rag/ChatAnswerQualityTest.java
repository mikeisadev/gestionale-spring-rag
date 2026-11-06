package com.gestionale.industriatech.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.gestionale.industriatech.security.UserContext;
import com.gestionale.industriatech.service.RagService;

/**
 * LIVELLO 3 (lezione 9): la qualità della RISPOSTA, con un secondo modello come giudice
 * ("LLM-as-a-judge"). RelevancyEvaluator controlla se la risposta è pertinente alla
 * domanda e ai documenti recuperati.
 *
 * Costa token e il risultato può variare: non va eseguito a ogni modifica, ma come
 * controllo periodico (per esempio prima di cambiare modello o prompt).
 *   ./mvnw test -DexcludedTestGroups=none -Dtest=ChatAnswerQualityTest
 */
@Tag("llm")
@SpringBootTest
class ChatAnswerQualityTest {

    @Autowired
    RagService ragService;

    @Autowired
    ChatClient chatClient;

    @Autowired
    ChatClient.Builder chatClientBuilder;

    @Autowired
    ChatMemory chatMemory;

    @Test
    void laRispostaEPertinenteAiDocumenti() {
        String question = "Cosa fa il repository nel pattern Controller Service Repository?";
        String conversationId = UUID.randomUUID().toString();
        UserContext admin = new UserContext("test-qualita", "IT", true);

        List<Document> documents = ragService.search(question, 5);

        try {
            ChatResponse response = chatClient.prompt()
                    .user(question)
                    .advisors(advisor -> advisor
                            .param(ChatMemory.CONVERSATION_ID, conversationId)
                            .param(DocumentContextAdvisor.DOCUMENTS, documents))
                    .toolContext(Map.of(UserContext.KEY, admin))
                    .call()
                    .chatResponse();

            String answer = response.getResult().getOutput().getText();

            // Lezione 9.5: i token di una risposta, letti nel codice
            Usage usage = response.getMetadata().getUsage();
            System.out.println("Risposta: " + answer);
            System.out.println("Token in ingresso: " + usage.getPromptTokens());
            System.out.println("Token in uscita: " + usage.getCompletionTokens());
            System.out.println("Token letti dalla cache: " + usage.getCacheReadInputTokens());

            EvaluationResponse result = new RelevancyEvaluator(chatClientBuilder)
                    .evaluate(new EvaluationRequest(question, documents, answer));

            System.out.println("Giudizio: " + (result.isPass() ? "PERTINENTE" : "NON PERTINENTE") + " " + result.getFeedback());
            assertThat(result.isPass()).as("risposta giudicata pertinente: " + answer).isTrue();
        } finally {
            chatMemory.clear(conversationId); // non lasciamo conversazioni di test nella memoria
        }
    }
}
