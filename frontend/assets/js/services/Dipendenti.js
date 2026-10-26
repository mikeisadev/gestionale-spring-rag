class Dipendenti {

    // URL ENDPOINT API
    API_URL = "http://localhost:8081/api/dipendenti";

    // Elementi HTML che mi serviranno dopo
    employeeForm = document.querySelector("#form-data-employee");

    /**
     * Il costruttore è un metodo che si avvia
     * all'istanza della classe, ovvero quando
     * uso la keyword "new NomeClasse()"
     */
    constructor() {
        // Carico i dipendenti
        this.ottieniDipendenti();

        // Aggancio il metodo che avvia la chiamata POST
        this.gestisciFormDipendente();
    }

    // 1. Chiamata GET dipendenti
    async ottieniDipendenti() {
        try {
            const request = await fetch(this.API_URL);
            const response = await request.json();

            const dataBox = document.querySelector(".data-box");

            dataBox.innerHTML = ""; // Ricorda! Dati sempre freschi!

            console.log(response, response);

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
                deleteButton.addEventListener("click", () => this.deleteEmployeeById(employeeId, deleteButton));

                // Gestire l'aggiornamento dipendente
                updateButton.addEventListener("click", () => this.updateEmployeeById(employeeId, updateButton, response));
            });
        } catch (e) {
            throw new Error(e);
        }
    }
    
    // 2 - Chiamata POST con fetch + form html -> creare dipendente
    gestisciFormDipendente() {
        try {
            this.employeeForm.addEventListener("submit", async (event) => {
                // Prevengo il reload dela pagina quando invio il form
                event.preventDefault(); 

                // Estraggo i dati dal form
                const nomeDipendente = document.querySelector("#form-data-employee #nome").value;
                const cognomeDipendente = document.querySelector("#form-data-employee #cognome").value;
                const emailDipendente = document.querySelector("#form-data-employee #email").value;

                console.log(nomeDipendente, cognomeDipendente, emailDipendente);

                const request = await fetch("http://localhost:8081/api/dipendenti", {
                    method: "POST",
                    body: JSON.stringify({
                        nome: nomeDipendente,
                        cognome: cognomeDipendente,
                        email: emailDipendente,
                    }),
                    headers: {
                        "Content-Type": "application/json"
                    }
                });
                const response = await request.json();

                if (response.status != "ok") {
                    alert(response.message);
                    return;
                }

                alert(response.message);

                // Ricarico i dipendenti nell'HTML per aggiornare la lista
                this.ottieniDipendenti();

                this.employeeForm.reset(); 
            });
        } catch (e) {
            throw new Error(e);
        }
    }
    
    // 3 - Chiamata DELETE -> eliminare dipendente
    async deleteEmployeeById(id, deleteButton) {
        try {
            // TODO: RIFARE MEGLIO - Evita il doppio click
            deleteButton.setAttribute("disabled", true);

            console.log("VUOI ELIMINARE IL DIPENDENTE:", id)

            if (!confirm("Sei sicuro di voler eliminare questo dipendente?")) {
                return;
            }

            const request = await fetch(this.API_URL + "/" + id, {
                method: "DELETE"
            });
            const response = await request.json();

            // Ricarica tutti i dipendenti
            this.ottieniDipendenti();
        } catch (e) {
            throw new Error(e);
        }
    }

    // 4 - Chiamata PUT -> aggiornare dipendente
    updateEmployeeById(id, updateButton, response) {
        try {
            console.log("Sto gestendo l'update del dipendente")

            // RICERCA DIPENDENTE CON ID
            const dipendenteTrovato = response.filter(d => d.id === Number(id));

            console.log(dipendenteTrovato);

            // 1. Aprire PopUp
            employeeUpdateForm(dipendenteTrovato[0]);

            const popupForm = document.querySelector(".popup #update-form-data-employee")

            // 2. Effettuare HTTP PUT
            popupForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                console.log("Stai inviando il popup");

                const formData = {
                    nome: popupForm.querySelector('#nome').value,
                    cognome: popupForm.querySelector('#cognome').value,
                    email: popupForm.querySelector('#email').value
                }

                const request = await fetch(this.API_URL + "/" +  id, {
                    method: 'PUT',
                    body: JSON.stringify(formData),
                    headers: {
                        "Content-Type": "application/json"
                    }
                });
                const response = await request.json();
                
                alert("Utente aggiornato con successo")

                document.body.querySelector(".popup").remove();
                
                // Ricarica i dipendenti
                this.ottieniDipendenti();
            });
        } catch (e) {
            throw new Error(e);
        }
    }

    // 5 - Chiamata GET - Cerca dipendente

}