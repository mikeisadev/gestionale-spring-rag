/**
 * Funzione per ritornare le iniziali di un nome
 */
function getInitialsFromName(firstName, lastName) {
    try {
        const firstNameInitial = firstName.trim().toUpperCase()[0];
        const lastNameInitial = lastName.trim().toUpperCase()[0];

        return firstNameInitial + lastNameInitial;
    } catch (e) {
        return "N/A";
    }
}

