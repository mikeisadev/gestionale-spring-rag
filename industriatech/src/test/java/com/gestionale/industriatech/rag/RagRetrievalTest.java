package com.gestionale.industriatech.rag;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.gestionale.industriatech.service.RagService;

/**
 * LIVELLO 2 (lezione 9): la qualità della RICERCA, con le "domande d'oro".
 *
 * Per ogni domanda indichiamo il documento che ci aspettiamo di trovare.
 * Test di integrazione: servono pgvector con i documenti indicizzati e Ollama (embedding).
 *   ./mvnw test -DexcludedTestGroups=llm
 *
 * Servono anche per calibrare topK e similarityThreshold: se cambi un valore e una
 * domanda d'oro smette di trovare il documento giusto, lo scopri subito.
 *
 * NOTA: le domande dipendono dai documenti caricati (qui: il manuale di prova e il PDF OWASP).
 */
@Tag("integration")
@SpringBootTest
class RagRetrievalTest {

    @Autowired
    RagService ragService;

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', textBlock = """
            Come funziona la configurazione automatica di Spring Boot?      | manuale-spring.pdf
            Cosa fa il repository nel pattern Controller Service Repository? | manuale-spring.pdf
            Come si finanzia la OWASP Foundation?                           | OWASP-Foundation-Strategic-Plan.pdf
            Quali attività formative offre OWASP ai suoi membri?            | OWASP-Foundation-Strategic-Plan.pdf
            """)
    void laDomandaDOroTrovaIlDocumentoGiusto(String question, String expectedFile) {
        List<Document> documents = ragService.search(question, 5);

        assertThat(documents)
                .as("documenti trovati per: " + question)
                .extracting(document -> document.getMetadata().get("original_filename"))
                .contains(expectedFile);
    }
}
