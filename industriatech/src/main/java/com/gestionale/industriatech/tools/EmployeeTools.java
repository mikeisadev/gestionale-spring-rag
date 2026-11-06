package com.gestionale.industriatech.tools;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.gestionale.industriatech.entity.Dipendenti;
import com.gestionale.industriatech.repository.DipendentiRepository;
import com.gestionale.industriatech.security.UserContext;

/**
 * Strumenti che l'agente può usare per consultare i dipendenti del gestionale.
 *
 * Il modello NON accede al database: può solo CHIEDERE di eseguire questi metodi.
 * Siamo noi a decidere cosa fanno, quali dati restituiscono e chi può usarli.
 */
@Component
public class EmployeeTools {

    private static final Logger log = LoggerFactory.getLogger(EmployeeTools.class);

    private final DipendentiRepository dipendentiRepository;

    public EmployeeTools(DipendentiRepository dipendentiRepository) {
        this.dipendentiRepository = dipendentiRepository;
    }

    @Tool(description = """
            Restituisce i dipendenti di un reparto aziendale: nome, cognome, ruolo ed email aziendale.
            Usalo quando l'utente chiede chi lavora in un reparto o come contattare i colleghi di un reparto.""")
    public List<EmployeeSummary> findEmployeesByDepartment(
            @ToolParam(description = "Nome del reparto, ad esempio IT, HR o Vendite") String department,
            ToolContext toolContext) {

        UserContext user = currentUser(toolContext);
        log.info("[TOOL] findEmployeesByDepartment(\"{}\") richiesto da {}", department, user.userId());

        if (!user.canAccessDepartment(department)) {
            throw new ToolAccessDeniedException(
                    "Accesso negato: puoi consultare solo i colleghi del tuo reparto (" + user.department() + ").");
        }

        return dipendentiRepository.findTop50ByRepartoIgnoreCase(department.trim())
                .stream()
                .map(EmployeeSummary::from)
                .toList();
    }

    @Tool(description = """
            Cerca un dipendente per nome o cognome e restituisce nome, cognome, ruolo, reparto ed email aziendale.
            Usalo quando l'utente chiede informazioni o i contatti di una persona specifica.""")
    public List<EmployeeSummary> findEmployeesByName(
            @ToolParam(description = "Nome o cognome (anche parziale) della persona da cercare") String name,
            ToolContext toolContext) {

        UserContext user = currentUser(toolContext);
        log.info("[TOOL] findEmployeesByName(\"{}\") richiesto da {}", name, user.userId());

        String text = name.trim().replaceAll("\\s+", " ");
        return dipendentiRepository.cercaPerNome(text)
                .stream()
                .limit(20)
                // Stesse regole dell'altro strumento: chi non è admin vede solo il suo reparto
                .filter(d -> user.canAccessDepartment(d.getReparto()))
                .map(EmployeeSummary::from)
                .toList();
    }

    @Tool(description = """
            Restituisce l'elenco dei reparti aziendali esistenti.
            Usalo per scoprire il nome esatto di un reparto prima di cercarne i dipendenti.""")
    public List<String> listDepartments(ToolContext toolContext) {
        UserContext user = currentUser(toolContext);
        log.info("[TOOL] listDepartments() richiesto da {}", user.userId());
        return dipendentiRepository.findDistinctReparti();
    }

    private static UserContext currentUser(ToolContext toolContext) {
        UserContext user = (UserContext) toolContext.getContext().get(UserContext.KEY);
        if (user == null) {
            throw new ToolAccessDeniedException("Utente non riconosciuto.");
        }
        return user;
    }

    /**
     * Solo i dati che è giusto mostrare. Tutto quello che restituiamo finisce
     * nel contesto del modello (e quindi a OpenAI): niente stipendi, codici fiscali,
     * indirizzi di casa o id interni. Per questo non restituiamo l'entity.
     */
    public record EmployeeSummary(String nome, String cognome, String ruolo, String reparto, String email) {

        static EmployeeSummary from(Dipendenti dipendente) {
            return new EmployeeSummary(
                    dipendente.getNome(),
                    dipendente.getCognome(),
                    dipendente.getRuolo(),
                    dipendente.getReparto(),
                    dipendente.getEmail());
        }
    }
}
