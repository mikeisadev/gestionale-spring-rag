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
function employeeUpdateForm() {
    const popup = `<div class="popup">
        <div class="popup-inner-content">
            <h4>Aggiorna dipendente</h4>

            <form id="update-form-data-employee" class="employee-form">
                <div class="form-row">
                    <label>Nome <span class="required">*</span></label>
                    <input type="text" name="nome" id="nome" placeholder="Inserisci il nome del dipendente...">
                </div>

                <div class="form-row">
                    <label>Cognome <span class="required">*</span></label>
                    <input type="text" name="cognome" id="cognome" placeholder="Inserisci il cognome del dipendente...">
                </div>

                <div class="form-row">
                    <label>Email <span class="required">*</span></label>
                    <input type="email" name="email" id="email" placeholder="Inserisci l'email del dipendente...">
                </div>

                <div class="form-row">
                    <input type="submit" value="Crea dipendente">
                </div>
            </form>
        </div>
    </div>`;

    document.body.innerHTML += popup;
}