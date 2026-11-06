package com.gestionale.industriatech.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.gestionale.industriatech.config.SecurityConfig;
import com.gestionale.industriatech.controller.AuthController;
import com.gestionale.industriatech.entity.Account;
import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.repository.DipendentiRepository;
import com.gestionale.industriatech.service.AccountService;

import jakarta.servlet.http.Cookie;

/**
 * LIVELLO 1 (lezione 13): registrazione, login, sessione, logout, CSRF e CORS.
 *
 * Tutto è vero (SecurityConfig, AuthController, AccountService, BCrypt) tranne il database:
 * l'AccountRepository è un mock che tiene gli account in una mappa.
 */
@WebMvcTest(controllers = { AuthController.class, com.gestionale.industriatech.controller.DipendentiController.class,
        com.gestionale.industriatech.controller.AdminAccountController.class })
@Import({ SecurityConfig.class, AccountService.class, AccountUserDetailsService.class, LoginRateLimiter.class })
class AuthFlowTest {

    @Autowired MockMvc mockMvc;
    @Autowired PasswordEncoder passwordEncoder;

    @MockitoBean AccountRepository accountRepository;
    @MockitoBean DipendentiRepository dipendentiRepository;

    // Il "database" degli account per questo test
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @BeforeEach
    void inMemoryRepository() {
        when(accountRepository.findByEmail(anyString())).thenAnswer(inv -> Optional.ofNullable(accounts.get(inv.getArgument(0))));
        when(accountRepository.existsByEmail(anyString())).thenAnswer(inv -> accounts.containsKey(inv.getArgument(0)));
        when(accountRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Account account = inv.getArgument(0);
            account.setId(ids.incrementAndGet());
            accounts.put(account.getEmail(), account);
            return account;
        });
        when(accountRepository.findById(any())).thenAnswer(inv ->
                accounts.values().stream().filter(a -> a.getId().equals(inv.getArgument(0))).findFirst());
    }

    // ===== CSRF: il flusso vero della SPA =====

    /** GET /api/auth/csrf: il cookie XSRF-TOKEN che React rimanda nell'header X-XSRF-TOKEN. */
    private Cookie csrfCookie(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf").session(session)).andExpect(status().isOk()).andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("cookie XSRF-TOKEN").isNotNull();
        assertThat(cookie.isHttpOnly()).as("il cookie CSRF deve essere leggibile da JavaScript").isFalse();
        return cookie;
    }

    private MvcResult postJson(String url, String json, MockHttpSession session) throws Exception {
        Cookie csrf = csrfCookie(session);
        return mockMvc.perform(post(url).session(session)
                        .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private MvcResult register(String email, String password) throws Exception {
        return postJson("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"nome\":\"Mario\"}", new MockHttpSession());
    }

    private MvcResult login(String email, String password, MockHttpSession session) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", session);
    }

    @Test
    void unaRichiestaCheModificaDatiSenzaTokenCsrfVieneRespinta() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.it\",\"password\":\"qualsiasi-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CSRF")));
        // con un token inventato
        mockMvc.perform(post("/api/auth/login").cookie(new Cookie("XSRF-TOKEN", "vero")).header("X-XSRF-TOKEN", "falso")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@b.it\",\"password\":\"qualsiasi-password\"}"))
                .andExpect(status().isForbidden());
    }

    // ===== Registrazione =====

    @Test
    void laRegistrazioneCreaSempreUnUtenteNormaleConLaPasswordCifrata() throws Exception {
        MvcResult result = postJson("/api/auth/register",
                "{\"email\":\"  Mario.Rossi@Azienda.IT \",\"password\":\"una-password-lunga\",\"nome\":\"Mario\","
                        + "\"ruolo\":\"ADMIN\",\"reparto\":\"HR\",\"id\":99}", // campi che il browser NON può decidere
                new MockHttpSession());

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"ruolo\":\"USER\"").contains("\"email\":\"mario.rossi@azienda.it\"")
                .doesNotContain("password").doesNotContain("bcrypt").doesNotContain("HR");

        Account saved = accounts.get("mario.rossi@azienda.it");
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain("una-password-lunga");
        assertThat(passwordEncoder.matches("una-password-lunga", saved.getPasswordHash())).isTrue();
        assertThat(saved.getReparto()).isNull();
        assertThat(saved.getId()).isNotEqualTo(99L);
    }

    @Test
    void datiNonValidiEEmailDuplicata() throws Exception {
        assertThat(register("non-una-email", "una-password-lunga").getResponse().getStatus()).isEqualTo(400);
        MvcResult corta = register("a@azienda.it", "corta");
        assertThat(corta.getResponse().getStatus()).isEqualTo(400);
        assertThat(corta.getResponse().getContentAsString()).contains("da 12 a 128 caratteri");

        assertThat(register("doppia@azienda.it", "una-password-lunga").getResponse().getStatus()).isEqualTo(201);
        MvcResult doppia = register("DOPPIA@azienda.it", "un'altra-password");
        assertThat(doppia.getResponse().getStatus()).isEqualTo(409);
    }

    // ===== Login, profilo, logout =====

    @Test
    void loginRiuscitoCambiaLIdDiSessioneEApreIlProfilo() throws Exception {
        register("luigi@azienda.it", "una-password-lunga");

        MockHttpSession session = new MockHttpSession();
        String idPrimaDelLogin = session.getId();
        MvcResult result = login("luigi@azienda.it", "una-password-lunga", session);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString()).contains("luigi@azienda.it").doesNotContain("password");
        // Difesa dalla "session fixation": l'ID conosciuto prima del login non vale più
        assertThat(session.getId()).isNotEqualTo(idPrimaDelLogin);
        // Il token CSRF viene azzerato: il frontend ne chiede uno nuovo
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getMaxAge()).isZero();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("luigi@azienda.it"))
                .andExpect(jsonPath("$.ruolo").value("USER"));
    }

    @Test
    void passwordSbagliataEdEmailInesistenteDannoLoStessoErrore() throws Exception {
        register("anna@azienda.it", "una-password-lunga");

        MvcResult sbagliata = login("anna@azienda.it", "password-sbagliata", new MockHttpSession());
        MvcResult inesistente = login("nessuno@azienda.it", "password-sbagliata", new MockHttpSession());

        assertThat(sbagliata.getResponse().getStatus()).isEqualTo(401);
        assertThat(inesistente.getResponse().getStatus()).isEqualTo(401);
        assertThat(sbagliata.getResponse().getContentAsString()).isEqualTo(inesistente.getResponse().getContentAsString());
    }

    @Test
    void ilProfiloSenzaLoginRisponde401InJson() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION)); // niente redirect a una pagina HTML
    }

    @Test
    void ilLogoutInvalidaLaSessione() throws Exception {
        register("paola@azienda.it", "una-password-lunga");
        MockHttpSession session = new MockHttpSession();
        login("paola@azienda.it", "una-password-lunga", session);

        MvcResult logout = postJson("/api/auth/logout", "{}", session);

        assertThat(logout.getResponse().getStatus()).isEqualTo(204);
        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void dopoCinqueTentativiSbagliatiIlLoginSiFerma() throws Exception {
        // NOTA: il limitatore vive nel contesto Spring, condiviso tra i test: ogni test usa la SUA email
        register("bruto@azienda.it", "una-password-lunga");
        for (int i = 0; i < 5; i++) {
            assertThat(login("bruto@azienda.it", "sbagliata-" + i, new MockHttpSession()).getResponse().getStatus()).isEqualTo(401);
        }
        // Il sesto tentativo è bloccato anche se la password è giusta
        assertThat(login("bruto@azienda.it", "una-password-lunga", new MockHttpSession()).getResponse().getStatus()).isEqualTo(429);
    }

    // ===== Fase 2: l'account viene ricaricato dal database a ogni richiesta =====

    @Test
    void unaPromozioneAdAdminValeSubitoSenzaRifareIlLogin() throws Exception {
        register("promosso@azienda.it", "una-password-lunga");
        MockHttpSession session = new MockHttpSession();
        login("promosso@azienda.it", "una-password-lunga", session);
        mockMvc.perform(get("/api/admin/accounts").session(session)).andExpect(status().isForbidden());

        // Un amministratore lo promuove (qui: direttamente nel "database")
        accounts.get("promosso@azienda.it").setRuolo(com.gestionale.industriatech.entity.RuoloAccount.ADMIN);

        mockMvc.perform(get("/api/admin/accounts").session(session)).andExpect(status().isOk());
    }

    @Test
    void unAccountDisabilitatoVieneButtatoFuoriSubito() throws Exception {
        register("disabilitato@azienda.it", "una-password-lunga");
        MockHttpSession session = new MockHttpSession();
        login("disabilitato@azienda.it", "una-password-lunga", session);
        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        accounts.get("disabilitato@azienda.it").setAbilitato(false);

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).as("sessione chiusa").isTrue();
        // e non può rientrare
        assertThat(login("disabilitato@azienda.it", "una-password-lunga", new MockHttpSession()).getResponse().getStatus()).isEqualTo(401);
    }

    // ===== CORS =====

    @Test
    void ilPreflightDallOrigineDiViteVieneAccettatoConLeCredenziali() throws Exception {
        mockMvc.perform(options("/api/dipendenti")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    void unOrigineNonConfigurataVieneRifiutata() throws Exception {
        mockMvc.perform(options("/api/dipendenti")
                        .header(HttpHeaders.ORIGIN, "https://sito-malevolo.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        // Anche una richiesta "vera" con il cookie di sessione: niente header CORS, il browser non mostra la risposta
        mockMvc.perform(get("/api/auth/csrf").header(HttpHeaders.ORIGIN, "https://sito-malevolo.example"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unHeaderNonPrevistoNonPassaIlPreflight() throws Exception {
        mockMvc.perform(options("/api/dipendenti")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "x-ruolo-utente"))
                .andExpect(status().isForbidden());
    }
}
