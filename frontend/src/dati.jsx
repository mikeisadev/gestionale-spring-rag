export const dipendenti = [
  {
    id: 0,
    nome: "Michele",
    cognome: "Micone",
    ruolo: "Full stack web developer",
    email: "michele.micone@example.com",
    reparto: "Sviluppo",
    attivo: true
  },
  {
    id: 1,
    nome: "Giulia",
    cognome: "Rossi",
    ruolo: "UX/UI designer",
    email: "giulia.rossi@example.com",
    reparto: "Design",
    attivo: true
  },
  {
    id: 2,
    nome: "Luca",
    cognome: "Bianchi",
    ruolo: "Frontend developer",
    email: "luca.bianchi@example.com",
    reparto: "Sviluppo",
    attivo: true
  },
  {
    id: 3,
    nome: "Sara",
    cognome: "Conti",
    ruolo: "Project manager",
    email: "sara.conti@example.com",
    reparto: "Amministrazione",
    attivo: true
  },
  {
    id: 4,
    nome: "Marco",
    cognome: "Esposito",
    ruolo: "Backend developer",
    email: "marco.esposito@example.com",
    reparto: "Sviluppo",
    attivo: true
  },
  {
    id: 5,
    nome: "Elena",
    cognome: "Romano",
    ruolo: "QA engineer",
    email: "elena.romano@example.com",
    reparto: "Qualità",
    attivo: true
  },
  {
    id: 6,
    nome: "Andrea",
    cognome: "Ferrari",
    ruolo: "DevOps engineer",
    email: "andrea.ferrari@example.com",
    reparto: "Sviluppo",
    attivo: true
  },
  {
    id: 7,
    nome: "Chiara",
    cognome: "Greco",
    ruolo: "Data analyst",
    email: "chiara.greco@example.com",
    reparto: "Analisi dati",
    attivo: false
  }
];

export const prodotti = [
  { id: 1, nome: "Tappetini universali", categoria: "Accessori", prezzo: 39.9, quantita: 24, disponibile: true },
  { id: 2, nome: "Portapacchi da tetto", categoria: "Accessori", prezzo: 189.0, quantita: 8, disponibile: true },
  { id: 3, nome: "Seggiolino auto", categoria: "Sicurezza", prezzo: 129.9, quantita: 12, disponibile: true },
  { id: 4, nome: "Kit lampadine LED", categoria: "Elettronica", prezzo: 59.9, quantita: 0, disponibile: false },
  { id: 5, nome: "Dash cam", categoria: "Elettronica", prezzo: 99.0, quantita: 15, disponibile: true },
  { id: 6, nome: "Catene da neve", categoria: "Sicurezza", prezzo: 74.5, quantita: 6, disponibile: true },
  { id: 7, nome: "Kit pulizia auto", categoria: "Manutenzione", prezzo: 24.9, quantita: 32, disponibile: true },
  { id: 8, nome: "Copertura per sedili", categoria: "Accessori", prezzo: 89.9, quantita: 10, disponibile: true }
];

export const auto = [
  { id: 1, marca: "Fiat", modello: "Panda", anno: 2022, prezzo: 14500, chilometraggio: 18000, alimentazione: "Ibrida", colore: "Bianco", disponibile: true },
  { id: 2, marca: "Toyota", modello: "Yaris", anno: 2023, prezzo: 19800, chilometraggio: 9500, alimentazione: "Ibrida", colore: "Blu", disponibile: true },
  { id: 3, marca: "Volkswagen", modello: "Golf", anno: 2021, prezzo: 23900, chilometraggio: 32000, alimentazione: "Benzina", colore: "Grigio", disponibile: true },
  { id: 4, marca: "Renault", modello: "Clio", anno: 2020, prezzo: 13900, chilometraggio: 41000, alimentazione: "Benzina", colore: "Rosso", disponibile: false },
  { id: 5, marca: "Tesla", modello: "Model 3", anno: 2022, prezzo: 35900, chilometraggio: 22000, alimentazione: "Elettrica", colore: "Nero", disponibile: true },
  { id: 6, marca: "Jeep", modello: "Avenger", anno: 2024, prezzo: 28700, chilometraggio: 1200, alimentazione: "Elettrica", colore: "Verde", disponibile: true },
  { id: 7, marca: "Peugeot", modello: "208", anno: 2021, prezzo: 15900, chilometraggio: 27500, alimentazione: "Diesel", colore: "Argento", disponibile: true },
  { id: 8, marca: "BMW", modello: "Serie 1", anno: 2023, prezzo: 31900, chilometraggio: 8000, alimentazione: "Benzina", colore: "Blu", disponibile: true }
];

export const clienti = [
  { id: 1, nome: "Paolo", cognome: "Neri", email: "paolo.neri@example.com", telefono: "+39 333 1234567", citta: "Milano" },
  { id: 2, nome: "Francesca", cognome: "Gallo", email: "francesca.gallo@example.com", telefono: "+39 334 2345678", citta: "Torino" },
  { id: 3, nome: "Davide", cognome: "Costa", email: "davide.costa@example.com", telefono: "+39 335 3456789", citta: "Bologna" },
  { id: 4, nome: "Alessia", cognome: "Fontana", email: "alessia.fontana@example.com", telefono: "+39 336 4567890", citta: "Firenze" },
  { id: 5, nome: "Matteo", cognome: "Rinaldi", email: "matteo.rinaldi@example.com", telefono: "+39 337 5678901", citta: "Roma" },
  { id: 6, nome: "Valentina", cognome: "Moretti", email: "valentina.moretti@example.com", telefono: "+39 338 6789012", citta: "Napoli" }
];

export const vendite = [
  { id: 1, clienteId: 1, dipendenteId: 3, autoId: 4, prodottiIds: [1, 7], data: "2025-01-15", totale: 13964.8, stato: "Completata", metodoPagamento: "Bonifico" },
  { id: 2, clienteId: 2, dipendenteId: 0, autoId: 1, prodottiIds: [3], data: "2025-02-03", totale: 14629.9, stato: "Completata", metodoPagamento: "Finanziamento" },
  { id: 3, clienteId: 3, dipendenteId: 3, autoId: 3, prodottiIds: [], data: "2025-02-18", totale: 23900, stato: "In attesa", metodoPagamento: "Bonifico" },
  { id: 4, clienteId: 4, dipendenteId: 1, autoId: 2, prodottiIds: [5, 8], data: "2025-03-06", totale: 19989.8, stato: "Completata", metodoPagamento: "Carta" },
  { id: 5, clienteId: 5, dipendenteId: 3, autoId: 5, prodottiIds: [2], data: "2025-03-21", totale: 36089, stato: "In attesa", metodoPagamento: "Finanziamento" },
  { id: 6, clienteId: 6, dipendenteId: 0, autoId: 6, prodottiIds: [6], data: "2025-04-02", totale: 28774.5, stato: "Completata", metodoPagamento: "Bonifico" }
];