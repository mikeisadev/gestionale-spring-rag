/**
 * ESERCITAZIONE
 * 
 * Chiamate di rete verso 
 * il backend Spring Boot
 * 
 * Metodi per effettuare chiamate di rete in JS:
 * - fetch
 * - XMLHttpRequest -> axios 
 * 
 * Con React installaremo la libreria Axios per
 * effettuare chiamate di rete
 */
console.log("[FILE JS CHIAMATE DI RETE] Chiamate di rete");

/**
 * TODO:
 * - Refactoring massivo
 * - Nuova architettura per il codice frontend
 */

window.addEventListener("DOMContentLoaded", () => {
    console.log("Si è caricata tutta la pagina HTML");

    /**
     * Per ogni controller del backend crea
     * una classe che gestisca le CRUD.
     * 
     * BONUS: anche usando l'AI cerca di farti spiegare
     * porzioni di codice per risolvere i bug presenti nella 
     * classe Dipendenti.
     * 
     * INDICAZIONE: dopo aver risolto i bug nella classe Dipendenti,
     * usa la stessa logica nelle altre classi.
     */
    try {
        new Dipendenti();
        // new Contratti();
        // new DatoriLavoro();
    } catch(e) {
        throw new Error(e);
    }
});

window.addEventListener("scroll", function(event) {
    // console.log("STO SCROLLANDO", event);
});

window.addEventListener("click", function(event) {
    // console.log("HO CLICCATO", event);
});

window.addEventListener("resize", function(event) {
    // console.log("Hai ridimensionato la pagina", event);
});