package com.gestionale.industriatech.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.time.LocalDateTime;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.gestionale.industriatech.config.SecurityConfig;
import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.entity.RuoloAccount;
import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.repository.DipendentiRepository;
import com.gestionale.industriatech.service.AccountService;
import com.gestionale.industriatech.service.ChatService;
import com.gestionale.industriatech.service.ConversationService;
import com.gestionale.industriatech.service.DocumentCatalogService;
import com.gestionale.industriatech.service.DocumentIndexingService;
import com.gestionale.industriatech.service.EmailDraftService;
import com.gestionale.industriatech.service.IndexingHistoryService;
import com.gestionale.industriatech.service.PdfProcessingService;
import com.gestionale.industriatech.service.RagService;
import com.gestionale.industriatech.wiki.WikiAskService;
import com.gestionale.industriatech.wiki.WikiCatalog;
import com.gestionale.industriatech.wiki.WikiSearchService;
import com.gestionale.industriatech.wiki.WikiVault;

/**
 * LIVELLO 1 (lezione 13): la MATRICE DEI PERMESSI, endpoint per endpoint.
 *
 * @WebMvcTest carica i controller veri e la SecurityConfig vera, ma sostituisce i service con
 * dei mock: niente database, niente modello. Ci interessa una sola domanda per ogni riga:
 * Spring Security lascia passare la richiesta (✓) oppure risponde 401 / 403?
 *
 * Il CSRF qui è sempre valido (csrf()): lo verifica CsrfCorsTest. Questo test controlla i RUOLI.
 */
@WebMvcTest(properties = "app.wiki.vault-path=/tmp") // carica anche i controller della wiki
@Import(SecurityConfig.class)
class SecurityMatrixTest {

    @Autowired
    MockMvc mockMvc;

    // I collaboratori dei controller: finti, perché qui non ci interessa cosa fanno
    @MockitoBean AccountUserDetailsService accountUserDetailsService;
    @MockitoBean AccountService accountService;
    @MockitoBean AccountRepository accountRepository;
    @MockitoBean LoginRateLimiter loginRateLimiter;
    @MockitoBean ChatService chatService;
    @MockitoBean ConversationService conversationService;
    @MockitoBean DipendentiRepository dipendentiRepository;
    @MockitoBean DocumentIndexingService documentIndexingService;
    @MockitoBean IndexingHistoryService indexingHistoryService;
    @MockitoBean PdfProcessingService pdfProcessingService;
    @MockitoBean DocumentCatalogService documentCatalogService;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean EmailDraftService emailDraftService;
    @MockitoBean RagService ragService;
    @MockitoBean WikiAskService wikiAskService;
    @MockitoBean WikiCatalog wikiCatalog;
    @MockitoBean WikiSearchService wikiSearchService;
    @MockitoBean WikiVault wikiVault;
    @MockitoBean ChatRateLimiter chatRateLimiter;
    @MockitoBean InputGuard inputGuard;

    // AccountRefreshFilter (fase 2) ricarica l'account dal database a ogni richiesta:
    // il repository finto restituisce lo stesso account, così la "fotografia" resta valida
    @org.junit.jupiter.api.BeforeEach
    void accountsExist() {
        for (RuoloAccount ruolo : RuoloAccount.values()) {
            Account account = new Account();
            account.setId(ruolo == RuoloAccount.ADMIN ? 1L : 2L);
            account.setRuolo(ruolo);
            account.setAbilitato(true);
            org.mockito.Mockito.when(accountRepository.findById(account.getId())).thenReturn(java.util.Optional.of(account));
        }
    }

    private static AccountUserDetails principal(RuoloAccount ruolo) {
        Account account = new Account();
        account.setId(ruolo == RuoloAccount.ADMIN ? 1L : 2L);
        account.setEmail(ruolo.name().toLowerCase() + "@azienda.it");
        account.setPasswordHash("{noop}non-usata");
        account.setNome(ruolo.name());
        account.setRuolo(ruolo);
        account.setCreatoIl(LocalDateTime.now());
        return new AccountUserDetails(account);
    }

    /**
     * Esegue la richiesta come "anonimo", "user" o "admin" e dice cosa ha deciso la sicurezza:
     * "401", "403", oppure "ok" se la richiesta è arrivata al controller.
     */
    private String outcome(String method, String path, String who) {
        MockHttpServletRequestBuilder request = request(HttpMethod.valueOf(method), path)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (!who.equals("anonimo")) {
            var details = principal(who.equals("admin") ? RuoloAccount.ADMIN : RuoloAccount.USER);
            request.with(authentication(new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities())));
        }
        try {
            int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
            return status == 401 || status == 403 ? String.valueOf(status) : "ok";
        } catch (Exception e) {
            // Il controller ha lanciato un'eccezione (i service sono mock vuoti):
            // vuol dire che la sicurezza lo ha lasciato eseguire.
            return "ok";
        }
    }

    @ParameterizedTest(name = "{0} {1} -> anonimo {2}, user {3}, admin {4}")
    @CsvSource(delimiter = '|', textBlock = """
            GET    | /api/auth/csrf                         | ok  | ok  | ok
            POST   | /api/auth/register                     | ok  | ok  | ok
            POST   | /api/auth/login                        | ok  | ok  | ok
            GET    | /api/auth/me                           | 401 | ok  | ok
            POST   | /api/auth/logout                       | 401 | ok  | ok
            GET    | /api/dipendenti                        | 401 | ok  | ok
            GET    | /api/dipendenti/mario@azienda.it       | 401 | ok  | ok
            POST   | /api/dipendenti                        | 401 | 403 | ok
            PUT    | /api/dipendenti/5                      | 401 | 403 | ok
            DELETE | /api/dipendenti/5                      | 401 | 403 | ok
            POST   | /api/chat                              | 401 | ok  | ok
            GET    | /api/chat/conversations                | 401 | ok  | ok
            GET    | /api/chat/conversations/abc/messages   | 401 | ok  | ok
            DELETE | /api/chat/conversations/abc            | 401 | ok  | ok
            GET    | /api/email-drafts                      | 401 | ok  | ok
            POST   | /api/email-drafts/3/send               | 401 | ok  | ok
            DELETE | /api/email-drafts/3                    | 401 | ok  | ok
            POST   | /api/rag/search                        | 401 | ok  | ok
            POST   | /api/documents                         | 401 | 403 | ok
            DELETE | /api/documents/abc                     | 401 | 403 | ok
            GET    | /api/documents/jobs                    | 401 | 403 | ok
            GET    | /api/documents/jobs/abc                | 401 | 403 | ok
            GET    | /api/documents/history                 | 401 | 403 | ok
            GET    | /api/documents/history/abc/logs        | 401 | 403 | ok
            GET    | /api/documents/test-embedding          | 401 | 403 | ok
            POST   | /api/documents/wiki/reindex            | 401 | 403 | ok
            GET    | /api/documents/wiki/status             | 401 | ok  | ok
            GET    | /api/wiki/search?q=rag                 | 401 | ok  | ok
            GET    | /api/wiki/page?path=index.md           | 401 | ok  | ok
            GET    | /api/wiki/assets?name=a.png            | 401 | ok  | ok
            POST   | /api/wiki/ask                          | 401 | ok  | ok
            POST   | /api/wiki/reload                       | 401 | 403 | ok
            GET    | /api/admin/accounts                    | 401 | 403 | ok
            PATCH  | /api/admin/accounts/2                  | 401 | 403 | ok
            GET    | /dati-generici/dati-utente             | 401 | 403 | 403
            GET    | /api/me                                | 401 | 403 | 403
            GET    | /api/endpoint-mai-esistito             | 401 | 403 | 403
            """)
    void matriceDeiPermessi(String method, String path, String anonimo, String user, String admin) {
        assertThat(outcome(method, path, "anonimo")).as("anonimo").isEqualTo(anonimo);
        assertThat(outcome(method, path, "user")).as("ROLE_USER").isEqualTo(user);
        assertThat(outcome(method, path, "admin")).as("ROLE_ADMIN").isEqualTo(admin);
    }
}
