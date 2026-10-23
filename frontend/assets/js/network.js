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

            // Prima seleziono tutte le card HTML appena renderizzate
            // e dopo aggancio le azioni della card: elimina, aggiorna e contatta
            const allCards = document.querySelectorAll(".employee-card");

            // Loop di ogni card
            allCards.forEach(card => {
                const deleteButton = card.querySelector(".employee-card__button--delete");
                const updateButton = card.querySelector(".employee-card__button--update");
                // const contactButton = card.querySelector("");

                console.log(deleteButton);
                console.log(updateButton);

                // Gestire l'eliminazione
                deleteButton.addEventListener("click", function() {
                    // TODO: RIFARE MEGLIO - Evita il doppio click
                    deleteButton.setAttribute("disabled", true);

                    const employeeId = deleteButton.getAttribute("data-employee-id")
                    console.log("VUOI ELIMINARE IL DIPENDENTE:", employeeId)

                    // TODO: Aggiungere preavviso di eliminazione
                    // la funzione "confirm()" nativa di Javascript
                    // popup scritto e implementato interamente da noi

                    fetch("http://localhost:8081/api/dipendenti/" + employeeId, {
                        method: "DELETE"
                    }).then(response => response.json())
                      .then(response => {
                        // Ricarica tutti i dipendenti
                      })
                })

                // Gestire l'aggiornamento dipendente
                updateButton.addEventListener("click", function() {
                    console.log("Sto gestendo l'update del dipendente")

                    // 1. Aprire PopUp
                    employeeUpdateForm();

                    // 2. Caricare i dati del dipendente da aggiornare nel popup
                    // 3. Gestire l'aggiornamento del dipendente nel popup
                });
            })
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
    function deleteEmployeeById(id) {

    }

    // 4 - Chiamata PUT -> aggiornare dipendente
    function updateEmployeeById(id) {

    }

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