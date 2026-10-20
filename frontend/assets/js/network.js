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

// 1. Chiamata GET dipendenti
fetch("http://localhost:8081/api/dipendenti")
    .then(response => response.json())
    .then(response => console.log(response));