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
    .then(response => {
        // 2. Log completo della risposta del server
        console.log(response);

        // 3. Loop di ogni singolo oggetto
        response.forEach(function(obj) {
            // 4. Analisi dei dati con log
            // console.log(obj);
            console.table(obj);

            console.log(
                "ID:", obj.id, 
                "Nome:", obj.nome, 
                "Cognome:", obj.cognome, 
                "Email:", obj.email
            );
        
            // Seleziono il box
            const dataBox = document.querySelector(".data-box");

            dataBox.innerHTML += employeeCardTemplate(obj);
        });
    });