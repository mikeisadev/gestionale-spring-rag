/**
 * Funzione per ritornare il template della card dipendente
 */
function employeeCardTemplate(obj) {
    return `<article class="employee-card" aria-labelledby="employee-mario-name">
                <div class="employee-card__banner" aria-hidden="true"></div>
                <div class="employee-card__content">
                    <div class="employee-card__top">
                        <div class="employee-card__avatar" aria-hidden="true">${getInitialsFromName(obj.nome, obj.cognome)}</div>
                        <span class="employee-card__status">Attivo</span>
                    </div>

                    <h3 id="employee-mario-name">${obj.nome} ${obj.cognome}</h3>
                    <p class="employee-card__role">Responsabile vendite</p>

                    <div class="employee-card__details">
                        <div>
                            <span class="employee-card__detail-label">Email</span>
                            <span class="employee-card__detail-value">mario.rossi@azienda.it</span>
                        </div>
                        <div>
                            <span class="employee-card__detail-label">Reparto</span>
                            <span class="employee-card__detail-value">Commerciale</span>
                        </div>
                    </div>

                    <div class="employee-card__actions">
                        <a class="employee-card__button employee-card__button--primary" href="mailto:mario.rossi@azienda.it">Contatta</a>

                        <div class="employee-card__other-actions">
                            <a class="employee-card__button employee-card__button--delete">Elimina</a>
                            <a class="employee-card__button employee-card__button--update">Aggiorna</a>
                        </div>
                    </div>
                </div>
            </article>`;
}