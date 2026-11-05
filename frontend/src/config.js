// Indirizzo del backend Spring Boot.
//
// - Sviluppo (npm run dev): VITE_API_URL non è impostata -> http://localhost:8081
// - Docker: l'immagine viene compilata con VITE_API_URL="" (stringa vuota), quindi le
//   chiamate diventano relative ("/api/...") e nginx le inoltra al container del backend.
//
// Le variabili VITE_* vengono "incollate" nel codice al momento della build (npm run build),
// non lette a runtime: per cambiarle bisogna ricompilare il frontend.
export const API_BASE_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8081';
