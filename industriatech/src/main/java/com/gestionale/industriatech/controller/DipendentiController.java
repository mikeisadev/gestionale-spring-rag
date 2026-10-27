package com.gestionale.industriatech.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.gestionale.industriatech.entity.Dipendenti;
import com.gestionale.industriatech.repository.DipendentiRepository;
import org.springframework.web.bind.annotation.PutMapping;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class DipendentiController {

    private static final Logger log = LoggerFactory.getLogger(DipendentiController.class);

    /**
     * Stiamo costruendo il controller per sbloccare le operazioni CRUD
     * 
     * Create  -> HTTP POST    -> @PostMapping      [OK]
     * Read    -> HTTP GET     -> @GetMapping       [OK]
     * Update  -> HTTP PUT     -> @PutMapping       [OK]
     * Delete  -> HTTP DELETE  -> @DeleteMapping    [OK]
     */

    // 1. Dependency Injection (DI) = inieittiamo il repository nel controller usando
    // il costruttore
    DipendentiRepository dipendentiRepository;

    public DipendentiController(DipendentiRepository dipendentiRepository)
    {
        this.dipendentiRepository = dipendentiRepository;
    }

    // 2. Utilizzo la repository nel singolo endpoint del controller
    @GetMapping("/dipendenti")
    public ResponseEntity<Object> ottieniDipendenti()
    {
        // findAll() = "SELECT * FROM dipendenti"
        List<Dipendenti> dipendenti = dipendentiRepository.findAll();

        return ResponseEntity.ok(dipendenti);
    }

    @GetMapping("/dipendenti/{email}")
    public ResponseEntity<Object> getEmployeeByEmail(@PathVariable String email)
    {
        Dipendenti dipendente = dipendentiRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utente non trovato, email " + email + " non trovata"));

        return ResponseEntity.ok(dipendente);
    }

    @PostMapping("/dipendenti")
    public ResponseEntity<Object> creaDipendente(@RequestBody Dipendenti request)
    {
        // 1) Eseguo il log
        log.info(
            "\nNome: " + request.getNome() +
            "\nCognome: " + request.getCognome() +
            "\nEmail: " + request.getEmail()
        );

        if (request.getNome().isEmpty())
        {
            Map<String, Object> response = Map.of(
            "message", "Nome mancante",
            "status", "error_missing_data",
            "request_date", LocalDateTime.now());

            return ResponseEntity.status(400).body(response);
        }
        else if (request.getCognome().isEmpty())
        {
            Map<String, Object> response = Map.of(
            "message", "Cognome mancante",
            "status", "error_missing_data",
            "request_date", LocalDateTime.now());
            
            return ResponseEntity.status(400).body(response);
        }
        else if (request.getEmail().isEmpty())
        {
            Map<String, Object> response = Map.of(
            "message", "Email mancante",
            "status", "error_missing_data",
            "request_date", LocalDateTime.now());
            
            return ResponseEntity.status(400).body(response);
        }

        // 2) Uso la entity per raccogliere i dati dalla richiesta HTTP
        Dipendenti nuovoDipendente = new Dipendenti();
        nuovoDipendente.setNome( request.getNome() );
        nuovoDipendente.setCognome( request.getCognome() );
        nuovoDipendente.setEmail( request.getEmail() );

        // 3) Uso la repository per salvare il nuovo dipendente nel database
        // CREATE
        dipendentiRepository.save(nuovoDipendente);

        // 4) Creo l'oggetto di risposta
        Map<String, Object> response = Map.of(
            "data", nuovoDipendente,
            "status", "ok",
            "request_date", LocalDateTime.now(),
            "message", "Dipendente creato con successo"
        );

        // 5) Rispondo al client
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/dipendenti/{id}")
    public ResponseEntity<Object> eliminaDipendente(@PathVariable Long id)
    {
        log.info("ID da eliminare: " + id);

        if (dipendentiRepository.existsById(id))
        {
            log.info("Esiste un utente con ID: " + id);

            // DELETE
            dipendentiRepository.deleteById(id);

            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Utente eliminato con successo",
                "date", LocalDateTime.now()
            ));
        }
        else
        {
            log.info("Non esiste alcun utente con ID: " + id);

            return ResponseEntity
                        .status(400)
                        .body(Map.of(
                            "status", "error",
                            "message", "Non esiste alcun utente con ID: " + id,
                            "date", LocalDateTime.now()
                        ));
        }
    }

    @PutMapping("/dipendenti/{id}")
    public ResponseEntity<Object> aggiornaDipendente(
        @PathVariable Long id,
        @RequestBody Dipendenti request
    )
    {
        // 0) Faccio un log per verificare che ho tutti i dati
        log.info(
            "\nAggiorna per ID: " + id +
            "\nNome: " + request.getNome() +
            "\nCognome: " + request.getCognome() +
            "\nEmail: " + request.getEmail()
        );

        // 1) Verificare se il dipendente esiste nel database
        if (dipendentiRepository.existsById(id))
        {
            log.info("L'utente esiste, quindi può essere aggiornato");

            // 2) Aggiornare i dati dell'utente tramite repository
            // Prima ripeschiamo l'utente dal database con il suo ID
            Dipendenti dipendente = dipendentiRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dipendente non trovato"));

            // Dopo aggiorniamo l'utente
            // Prima di aggiornare l'utente facciamo dei check:
            // 1) Ogni campo che deriva da request NON deve essere NULL
            // 2) Il campo, non deve essere empty e nemmeno blank (con spazi vuoti)
            if (request.getNome() != null) dipendente.setNome(request.getNome().isBlank() ? dipendente.getNome() : request.getNome().trim());
            
            if (request.getCognome() != null) dipendente.setCognome(request.getCognome().isBlank() ? dipendente.getCognome() : request.getCognome().trim());
            
            if (request.getEmail() != null) dipendente.setEmail(request.getEmail().isBlank() ? dipendente.getEmail() : request.getEmail().trim());

            // Salviamo il dipendente
            dipendentiRepository.save(dipendente);

            return ResponseEntity.ok(Map.of(
                "message", "Utente " + id + " aggiornato con successo",
                "status", "ok",
                "data", LocalDateTime.now()
            ));
        }
        else {
            log.info("L'utente non esiste, quindi non può essere aggiornato");

            return ResponseEntity
                    .status(400)
                    .body(Map.of(
                        "status", "error",
                        "message", "Non esiste alcun utente con ID: " + id + ", per cui non è possibile aggiornare un utente inesistente!",
                        "date", LocalDateTime.now()
                    ));
        }
    }
    
}
