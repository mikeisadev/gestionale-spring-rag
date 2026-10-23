/**
 * Funzione per ritornare il template della card dipendente
 */
function employeeCardTemplate(obj) {
    return `<article id="employee-${obj.id}" class="employee-card" aria-labelledby="employee-mario-name">
                <div class="employee-card__banner" aria-hidden="true"></div>
                <div class="employee-card__content">
                    <div class="employee-card__top">
                        <div class="employee-card__avatar" aria-hidden="true">${getInitialsFromName(obj.nome, obj.cognome)}</div>
                        <span class="employee-card__status">Attivo</span>
                    </div>

                    <h3>${obj.nome} ${obj.cognome}</h3>
                    <p class="employee-card__role">Responsabile vendite</p>

                    <div class="employee-card__details">
                        <div>
                            <span class="employee-card__detail-label">Email</span>
                            <span class="employee-card__detail-value">${obj.email}</span>
                        </div>
                        <div>
                            <span class="employee-card__detail-label">Reparto</span>
                            <span class="employee-card__detail-value">Commerciale</span>
                        </div>
                    </div>

                    <div class="employee-card__actions">
                        <a class="employee-card__button employee-card__button--primary" href="mailto:mario.rossi@azienda.it">Contatta</a>

                        <div class="employee-card__other-actions">
                            <a class="employee-card__button employee-card__button--delete" data-employee-id="${obj.id}">Elimina</a>
                            <a class="employee-card__button employee-card__button--update" data-employee-id="${obj.id}">Aggiorna</a>
                        </div>
                    </div>
                </div>
            </article>`;
}

/**
 * Funzione per precompilare il form di aggiornamento dipendente
 */
function employeeUpdateForm(employee) {
    console.log("[DATI SORGENTE]:", employee);
    
    // TODO: 0. Popup UI security
    // document.querySelectorAll(".popup").forEach(popup => popup.delete())

    // 1. Preparo il template
    const popup = `<div class="popup">
        <div class="popup-inner-content">
            <div class="popup-head">
                <h4>Aggiorna dipendente</h4>
                <button id="popup-close">Chiudi</button>
            </div>

            <form id="update-form-data-employee" class="employee-form">
                <div class="form-row">
                    <label>Nome <span class="required">*</span></label>
                    <input type="text" name="nome" id="nome" placeholder="Inserisci il nome del dipendente..." value="${employee.nome}">
                </div>

                <div class="form-row">
                    <label>Cognome <span class="required">*</span></label>
                    <input type="text" name="cognome" id="cognome" placeholder="Inserisci il cognome del dipendente..." value="${employee.cognome}">
                </div>

                <div class="form-row">
                    <label>Email <span class="required">*</span></label>
                    <input type="email" name="email" id="email" placeholder="Inserisci l'email del dipendente..." value="${employee.email}">
                </div>

                <div class="form-row">
                    <button id="popup-submit" role="submit">Aggiorna dipendente</button>
                </div>
            </form>
        </div>
    </div>`;

    // 2. Inietto l'HTML per renderlo disponibile
    // document.body.innerHTML += popup; // BUG: Crea problemi nei re-rendering
    document.body.insertAdjacentHTML("beforeend", popup);

    // 3. Seleziono gli elementi dell'HTML iniettato
    const closeButton = document.querySelector(".popup #popup-close");
    const popupForm = document.querySelector(".popup #update-form-data-employee")

    // 4. Aggancio listener o qualsiasi algoritmo
    closeButton.addEventListener('click', function() {
        console.log("Stai chiudedno il popup");

        document.body.querySelector(".popup").remove();
    });

    popupForm.addEventListener('submit', function(event) {
        event.preventDefault();
        console.log("Stai inviando il popup");

        const formData = {
            nome: popupForm.querySelector('#nome').value,
            cognome: popupForm.querySelector('#cognome').value,
            email: popupForm.querySelector('#email').value
        }

        console.log(formData);

        // prendere i dati dal form
        // fare FETCH PUT
        fetch("http://localhost:8081/api/dipendenti/" + employee.id, {
            method: 'PUT',
            body: JSON.stringify(formData),
            headers: {
                "Content-Type": "application/json"
            }
        })
            .then(response => response.json())
            .then(response => {
                alert("Utente aggiornato con successo")
                
                // Ricarica i dipendenti
            })
            .catch(error => {
                alert("Si è verificato un errore durante l'aggiornamento del dipendente")
            })
    });

    console.log(closeButton);
}