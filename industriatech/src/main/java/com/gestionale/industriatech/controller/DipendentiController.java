package com.gestionale.industriatech.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gestionale.industriatech.entity.Dipendenti;
import com.gestionale.industriatech.repository.DipendentiRepository;

@RestController
@RequestMapping("/api")
public class DipendentiController {

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
    
}
