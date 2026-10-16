# Spring Boot, React e sistemi RAG
Questa repository comprende un semplice progetto Java Spring Boot con React (ancora da implementare) per lo sviluppo di un gestionale a scopo didattico.

Più avanti nel corso, il gestionale includerà lo sviluppo di un sistema RAG collegato a uno o più LLM.

- **Docente**: Michele Mincone
- **Data**: 15/10/2026

## Tecnologie
- **Java 25**
- **Spring Boot v4.1.1 (*framework*)**
- **React (*ancora da implementare*)**

## Ripasso protocollo HTTP, metodi e mapping Spring Boot
Stiamo costruendo il controller per sbloccare le operazioni CRUD

| **Azione**          | **Metodo HTTP** |   **Annotazione Spring Boot**       |
|---------------------|-----------------|-------------------------------------|
|   Create (CREA)     |    HTTP POST    |     @PostMapping      [OK]          |
|   Read  (LEGGI)     |    HTTP GET     |     @GetMapping       [OK]          |
|   Update (AGGIORNA) |    HTTP PUT     |     @PutMapping       [OK]          |
|   Delete (ELIMINA)  |    HTTP DELETE  |     @DeleteMapping    [OK]          |