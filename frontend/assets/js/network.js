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

window.addEventListener("DOMContentLoaded", function() {
    console.log("Si è caricata tutta la pagina HTML");

    // 1. Chiamata GET dipendenti
    fetch("http://localhost:8081/api/dipendenti")
        .then(response => response.json())
        .then(response => {
            // 2. Log completo della risposta del server
            // console.log(response);

            // 3. Loop di ogni singolo oggetto
            response.forEach(function(obj) {
                // 4. Analisi dei dati con log
                // console.log(obj);
                // console.table(obj);

                // console.log(
                //     "ID:", obj.id, 
                //     "Nome:", obj.nome, 
                //     "Cognome:", obj.cognome, 
                //     "Email:", obj.email
                // );
            
                // Seleziono il box
                const dataBox = document.querySelector(".data-box");

                dataBox.innerHTML += employeeCardTemplate(obj);
            });
        });

    // 2 - Chiamata POST con fetch + form html -> creare dipendente
    const employeeForm = document.querySelector("#form-data-employee");

    console.log(employeeForm);

    // Ascolto l'evento di invio del form
    employeeForm.addEventListener("submit", function(event) {
        // Prevengo il reload dela pagina quando invio il form
        event.preventDefault(); 

        // Estraggo i dati dal form
        const nomeDipendente = document.querySelector("#form-data-employee #nome").value;
        const cognomeDipendente = document.querySelector("#form-data-employee #cognome").value;
        const emailDipendente = document.querySelector("#form-data-employee #email").value;

        console.log(nomeDipendente, cognomeDipendente, emailDipendente);

        fetch("http://localhost:8081/api/dipendenti", {
            method: "POST",
            body: JSON.stringify({
                nome: nomeDipendente,
                cognome: cognomeDipendente,
                email: emailDipendente,
            }),
            headers: {
                "Content-Type": "application/json"
            }
        })
            .then(response => response.json())
            .then(response => {
                if (response.status != "ok") {
                    alert(response.message);
                    return;
                } 

                alert(response.message);

                // Ricarico i dipendenti nell'HTML per aggiornare la lista
            })
            .catch(response => alert("Si è verificato un errore generico. Riprova!"))
    });

    // 3 - Chiamata DELETE -> eliminare dipendente

    // 4 - Chiamata PUT -> aggiornare dipendente

    // 5 - Chiamata GET - Cerca dipendente
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