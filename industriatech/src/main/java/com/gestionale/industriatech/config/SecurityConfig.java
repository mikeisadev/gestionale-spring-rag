package com.gestionale.industriatech.config;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.gestionale.industriatech.repository.AccountRepository;
import com.gestionale.industriatech.security.AccountRefreshFilter;
import com.gestionale.industriatech.security.AccountUserDetailsService;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lezione 13: la configurazione di sicurezza, tutta in un posto.
 *
 * Richiesta del browser
 *   -> CORS (da quale sito arriva?)           -> CsrfFilter (ha il token anti-CSRF?)
 *   -> SecurityContext dalla sessione (chi è?) -> regole qui sotto (può farlo?)
 *   -> controller
 *
 * Niente WebSecurityConfigurerAdapter (rimosso da anni): si dichiara un bean SecurityFilterChain.
 */
@Configuration
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AccountRepository accountRepository,
                                                   SecurityContextRepository securityContextRepository) throws Exception {
        http
                // Fase 2: dopo aver letto l'utente dalla sessione, lo aggiorniamo dal database
                // (ruolo, reparto, account disabilitato) PRIMA dei controlli sui ruoli
                .addFilterAfter(new AccountRefreshFilter(accountRepository, securityContextRepository),
                        SecurityContextHolderFilter.class)
                // CORS: usa il bean corsConfigurationSource qui sotto (unica configurazione del progetto)
                .cors(Customizer.withDefaults())

                // CSRF ATTIVO. spa(): token in un cookie "XSRF-TOKEN" leggibile da JavaScript;
                // React lo rimanda nell'header "X-XSRF-TOKEN" in ogni POST/PUT/PATCH/DELETE.
                // Un sito estraneo può far partire una richiesta con i nostri cookie, ma non può
                // LEGGERE il cookie del token: la sua richiesta arriva senza header e viene rifiutata.
                .csrf(csrf -> csrf.spa())

                // Il login lo gestisce AuthController (JSON), non la pagina HTML di Spring
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // API: 401 JSON se manca il login, 403 JSON se il ruolo non basta. Mai redirect a /login.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED, "Accesso richiesto: effettua il login."))
                        // Anche un token CSRF mancante produce un 403: messaggio diverso, così chi fa il debug
                        // non lo scambia per un problema di ruolo
                        .accessDeniedHandler((request, response, ex) -> writeJson(response, HttpStatus.FORBIDDEN,
                                ex instanceof CsrfException
                                        ? "Token di sicurezza (CSRF) mancante o scaduto: ricarica la pagina."
                                        : "Non hai i permessi per questa operazione.")))

                .authorizeHttpRequests(auth -> auth
                        // La chat in streaming (SSE) continua in una richiesta "asincrona" e gli errori
                        // passano da /error: la prima metà della richiesta è già stata autorizzata.
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()

                        // ----- Autenticazione -----
                        .requestMatchers(POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(GET, "/api/auth/me").authenticated()
                        .requestMatchers(POST, "/api/auth/logout").authenticated()

                        // ----- Actuator: lo stato è pubblico, le metriche (token, tempi) no -----
                        .requestMatchers(GET, "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole(ADMIN)

                        // ----- Dipendenti: tutti leggono, solo gli admin modificano -----
                        .requestMatchers(GET, "/api/dipendenti", "/api/dipendenti/*").authenticated()
                        .requestMatchers(POST, "/api/dipendenti").hasRole(ADMIN)
                        .requestMatchers(PUT, "/api/dipendenti/*").hasRole(ADMIN)
                        .requestMatchers(DELETE, "/api/dipendenti/*").hasRole(ADMIN)

                        // ----- Chat, bozze email, ricerca: ogni utente autenticato (sui propri dati) -----
                        .requestMatchers(POST, "/api/chat").authenticated()
                        .requestMatchers(GET, "/api/chat/conversations", "/api/chat/conversations/*/messages").authenticated()
                        .requestMatchers(DELETE, "/api/chat/conversations/*").authenticated()
                        .requestMatchers(GET, "/api/email-drafts").authenticated()
                        .requestMatchers(POST, "/api/email-drafts/*/send").authenticated()
                        .requestMatchers(DELETE, "/api/email-drafts/*").authenticated()
                        .requestMatchers(POST, "/api/rag/search").authenticated()

                        // ----- Documenti RAG: area "Solo Admin" -----
                        // Eccezione: lo stato della wiki serve anche al Wiki Explorer (lezione 12)
                        .requestMatchers(GET, "/api/documents/wiki/status").authenticated()
                        .requestMatchers("/api/documents", "/api/documents/**").hasRole(ADMIN)

                        // ----- Wiki Explorer -----
                        .requestMatchers(POST, "/api/wiki/reload").hasRole(ADMIN)
                        .requestMatchers(GET, "/api/wiki/search", "/api/wiki/page", "/api/wiki/assets").authenticated()
                        .requestMatchers(POST, "/api/wiki/ask").authenticated()

                        // ----- Gestione account (decisione B del piano) -----
                        .requestMatchers(GET, "/api/admin/accounts").hasRole(ADMIN)
                        .requestMatchers(PATCH, "/api/admin/accounts/*").hasRole(ADMIN)

                        // Tutto il resto è NEGATO: un endpoint nuovo o dimenticato (es. /dati-generici)
                        // non diventa pubblico per sbaglio. Per aprirlo bisogna aggiungerlo qui.
                        .anyRequest().denyAll());

        return http.build();
    }

    /**
     * L'algoritmo consigliato dalla versione di Spring Security in uso (oggi BCrypt).
     * L'hash salvato ha un prefisso, es. "{bcrypt}$2a$10$...": se un giorno cambierà
     * l'algoritmo predefinito, le password vecchie continueranno a funzionare.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Usato da AuthController per verificare email e password. */
    @Bean
    public AuthenticationManager authenticationManager(AccountUserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /** Dove vive l'utente autenticato tra una richiesta e l'altra: nella sessione HTTP (cookie JSESSIONID). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * CORS, una volta sola per tutto il backend (prima c'era un @CrossOrigin su 9 controller).
     *
     * - origini ESPLICITE, configurabili per ambiente (app.cors.allowed-origins);
     * - credenziali (cookie) consentite: per questo l'origine non può mai essere "*";
     * - solo i metodi e gli header che il frontend usa davvero.
     *
     * In Docker frontend e API passano entrambi da nginx (stessa origine): CORS non entra in gioco.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Accept"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeJson(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
