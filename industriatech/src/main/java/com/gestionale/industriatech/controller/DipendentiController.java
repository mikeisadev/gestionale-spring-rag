package com.gestionale.industriatech.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.entity.Dipendenti;
import com.gestionale.industriatech.repository.DipendentiRepository;

@RestController
@RequestMapping("/api")
public class DipendentiController {

    private static final Logger log = LoggerFactory.getLogger(DipendentiController.class);

    /**
     * Stiamo costruendo il controller per sbloccare le operazioni CRUD
     * 
     * Create  -> HTTP POST    -> @PostMapping      [DA VEDERE]
     * Read    -> HTTP GET     -> @GetMapping       [OK]
     * Update  -> HTTP PUT     -> @PutMapping       [DA VEDERE]
     * Delete  -> HTTP DELETE  -> @DeleteMapping    [DA VEDERE]
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

        // 2) Uso la entity per raccogliere i dati dalla richiesta HTTP
        Dipendenti nuovoDipendente = new Dipendenti();
        nuovoDipendente.setNome( request.getNome() );
        nuovoDipendente.setCognome( request.getCognome() );
        nuovoDipendente.setEmail( request.getEmail() );

        // 3) Uso la repository per salvare il nuovo dipendente nel database
        dipendentiRepository.save(nuovoDipendente);

        // 4) Creo l'oggetto di risposta
        Map<String, Object> response = Map.of(
            "data", nuovoDipendente,
            "status", "ok",
            "request_date", LocalDateTime.now()
        );

        // 5) Rispondo al client
        return ResponseEntity.ok(response);
    }
    
}
