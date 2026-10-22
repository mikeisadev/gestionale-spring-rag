/**
 * Esempi di codice javascript
 */
// console.log("Hello, world");
// console.log("Ciao, sto imparando javascript");

// let nome = "Michele";
// let cognome = "Mincone";

// console.log("Ciao, il mio nome è " + nome + " " + cognome);

// alert("Attenzione, sei stato hackerato");

/**
 * Differenza tra: var, let e const
 * 
 * DARE PRIORITà A:
 * - LET: una variabile, posso riassegnare il suo valore
 * - CONST: una costante, NON POSSO riassegnare il suo valore
 */

// NON PUOI ACCEDERE A LET E CONST PRIMA DELL'INIZIALIZZAZIONE
// MA INVENCE CON "VAR" PUOI FARLO OTTENENDO UNDEFINED MA OTTIENI IL RIFERIMENTO IN MEMORIA
// console.log(variabileUno, variabileDue, variabileTre);

// LET è una variabile sovrascrivibile
let variabileUno = "pizza";
let variabileDue = "ciao";
let variabileTre = "Hello";

variabileUno = null;
variabileDue = 34;

// console.log(variabileUno, variabileDue, variabileTre);

// CONST è una costante (NON SOVRASCRIVIBILE)
// è stata pensata per proteggere valori CHE NON DEVONO MUTARE
const PI = 3.14;
const IVA22 = 22;
const IVA10 = 10;

// PI = null; ERRORE! NON POSSO SOVRASCRIVERE UNA const

// console.log("PI:", PI)