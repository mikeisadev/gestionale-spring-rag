package com.gestionale.industriatech.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dati-generici")
public class ControllerGenerico {
    
    @GetMapping("/dati-utente")
    public ResponseEntity<Object> fornisciUtente()
    {
        return ResponseEntity.ok("Michele Mincone ciao bla bla bla web development");
    }

    @GetMapping("/oggetto-utente")
    public ResponseEntity<Object> ottieniOggettoUtente()
    {
        // var oggettoUtente = new HashMap<>() {{
        //     put("nome", "Michele");
        //     put("cognome", "Mincone");
        //     put("eta", 24);
        //     put("codice_fiscale", "AABBB3689SCI");
        //     put("email", "ciao@gmail.com");
        // }};

        var oggettoUtente = Map.of(
            "nome", "Michele",
            "cognome", "Mincone"
        );

        return ResponseEntity.ok(oggettoUtente);
    }

    @GetMapping("/lista-utenti")
    public ResponseEntity<Object> ottieniListaUtenti()
    {
        var listaUtenti = List.of(
            Map.of(
                "nome", "Michele",
                "cognome", "Mincone",
                "competenze", List.of("PHP", "C", "Java", "Python")
            ),
            Map.of(
                "nome", "Luca",
                "cognome", "Smith",
                "competenze", List.of("Excel", "Word", "PowerPoint")
            ),
            Map.of(
                "nome", "Alberto",
                "cognome", "Ciao",
                "competenze", List.of()
            )
        );

        return ResponseEntity.ok(listaUtenti);
    }

}
