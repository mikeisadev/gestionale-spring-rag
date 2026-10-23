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
    async function ottieniDipendenti() {
        const response = await fetch("http://localhost:8081/api/dipendenti")
        const data = await response.json();

        const dataBox = document.querySelector(".data-box");

        dataBox.innerHTML = ""; // Ricorda! Dati sempre freschi!

        response.forEach(function(obj) {
            dataBox.innerHTML += employeeCardTemplate(obj);
        });

        // Prima seleziono tutte le card HTML appena renderizzate
        // e dopo aggancio le azioni della card: elimina, aggiorna e contatta
        const allCards = document.querySelectorAll(".employee-card");

        // Loop di ogni card
        allCards.forEach(card => {
            const employeeId = Number( card.getAttribute("data-card-id") );

            const deleteButton = card.querySelector(".employee-card__button--delete");
            const updateButton = card.querySelector(".employee-card__button--update");
            // const contactButton = card.querySelector("");

            console.log(employeeId);
            console.log(deleteButton);
            console.log(updateButton);

            // Gestire l'eliminazione
            deleteButton.addEventListener("click", () => deleteEmployeeById(employeeId, deleteButton));

            // Gestire l'aggiornamento dipendente
            updateButton.addEventListener("click", () => updateEmployeeById(employeeId, updateButton, data));
        })
    }
    ottieniDipendenti(); // Carico i dipendenti

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
                ottieniDipendenti();

                employeeForm.reset();
            })
            .catch(response => alert("Si è verificato un errore generico. Riprova!"))
    });

    // 3 - Chiamata DELETE -> eliminare dipendente
    function deleteEmployeeById(id, deleteButton) {
        // TODO: RIFARE MEGLIO - Evita il doppio click
        deleteButton.setAttribute("disabled", true);

        console.log("VUOI ELIMINARE IL DIPENDENTE:", id)

        if (!confirm("Sei sicuro di voler eliminare questo dipendente?")) {
            return;
        }

        fetch("http://localhost:8081/api/dipendenti/" + id, {
            method: "DELETE"
        })
            .then(response => response.json())
            .then(response => {
                // Ricarica tutti i dipendenti
                ottieniDipendenti();
            })
    }

    // 4 - Chiamata PUT -> aggiornare dipendente
    function updateEmployeeById(id, updateButton, response) {
        console.log("Sto gestendo l'update del dipendente")

        // RICERCA DIPENDENTE CON ID
        const dipendenteTrovato = response.filter(d => d.id === Number(id));

        console.log(dipendenteTrovato);

        // 1. Aprire PopUp
        employeeUpdateForm(dipendenteTrovato[0]);

        const popupForm = document.querySelector(".popup #update-form-data-employee")

        // 2. Effettuare HTTP PUT
        popupForm.addEventListener('submit', function(event) {
            event.preventDefault();
            console.log("Stai inviando il popup");

            const formData = {
                nome: popupForm.querySelector('#nome').value,
                cognome: popupForm.querySelector('#cognome').value,
                email: popupForm.querySelector('#email').value
            }

            fetch("http://localhost:8081/api/dipendenti/" + id, {
                method: 'PUT',
                body: JSON.stringify(formData),
                headers: {
                    "Content-Type": "application/json"
                }
            })
                .then(response => response.json())
                .then(response => {
                    alert("Utente aggiornato con successo")

                    document.body.querySelector(".popup").remove();
                    
                    // Ricarica i dipendenti
                    ottieniDipendenti();
                })
                .catch(error => {
                    alert("Si è verificato un errore durante l'aggiornamento del dipendente")
                })
        });
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