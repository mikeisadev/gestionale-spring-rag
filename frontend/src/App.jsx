import { useEffect, useState } from 'react';
import axios from "axios";

function App() {
  const [dipendenti, aggiornaDipendenti] = useState(null);
  const [formDipendente, setFormDipendente] = useState({
    nome: "",
    cognome: "",
    email: ""
  });

  useEffect(() => {
    getEmployees();
  }, []);

  function getEmployees() {
    axios.get("http://localhost:8081/api/dipendenti")
      .then(response => {
        aggiornaDipendenti(response.data)
        console.log(response.data)
      });
  }

  function handleSubmit(e) {
    e.preventDefault();
    console.log(formDipendente);

    axios.post("http://localhost:8081/api/dipendenti", formDipendente)
      .then(response => {
        console.log(response.data)
        
        getEmployees();
      })
  }

  function handleInput(e) {
    console.log(e.target.name, e.target.value)

    setFormDipendente({
      ...formDipendente,
      [e.target.name]: e.target.value
    });
  }

  return (
    <>
      <div>
        <h1 className='heading'>Gestionale <i class="bi bi-8-square-fill"></i></h1>

        <h2>Dipendenti</h2>

        <form
          onSubmit={handleSubmit}
          className="mb-8 max-w-2xl rounded-2xl border border-slate-200 bg-white p-6 shadow-sm sm:p-8"
        >
          <div className="mb-6">
            <h3 className="text-lg font-semibold text-slate-900">Nuovo dipendente</h3>
            <p className="mt-1 text-sm text-slate-500">Inserisci i dati per aggiungere una persona al team.</p>
          </div>

          <div className="grid gap-5 sm:grid-cols-2">
            <div>
              <label htmlFor="nome" className="mb-2 block text-sm font-medium text-slate-700">
                Nome
              </label>
              <div className='relative'>
                <i className="bi bi-person absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                  type="text"
                  id="nome"
                  name="nome"
                  autoComplete="given-name"
                  placeholder="Es. Maria"
                  className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-100"
                  onInput={handleInput}
                />
              </div>
            </div>

            <div>
              <label htmlFor="cognome" className="mb-2 block text-sm font-medium text-slate-700">
                Cognome
              </label>
              <div className="relative">
                <i className="bi bi-person-badge absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                  type="text"
                  id="cognome"
                  name="cognome"
                  autoComplete="family-name"
                  placeholder="Es. Rossi"
                  className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-100"
                  onInput={handleInput}
                />
              </div>
            </div>

            <div className="sm:col-span-2">
              <label htmlFor="email" className="mb-2 block text-sm font-medium text-slate-700">
                Email
              </label>
              <div className="relative">
                <i className="bi bi-envelope absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                  type="email"
                  id="email"
                  name="email"
                  autoComplete="email"
                  placeholder="nome@azienda.it"
                  className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-100"
                  onInput={handleInput}
                />
              </div>
            </div>
          </div>

          <button
            type="submit"
            className="mt-6 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 sm:w-auto"
          >
            <i className="bi bi-person-plus" aria-hidden="true" />
            Aggiungi dipendente
          </button>
        </form>

        <div>
          {
            dipendenti ?
            dipendenti.map((dipendente, i) => <EmployeeCard 
                                                  key={i} 
                                                  dati={dipendente}
                                                  onAggiorna={(dati) => {
                                                    console.log("Stai aggiornando il dipendente", dati)
                                                  }}
                                                  onElimina={(dati) => {
                                                    console.log("Stai eliminando il dipendente", dati)
                                                  }}
                                                  />)
            :
            <p>Stiamo caricando i dipendenti</p>
          }
        </div>
      </div>

      <div>
        <h1>Gestionale</h1>
      </div>
    </>
  )
}

function EmployeeCard({ dati, onAggiorna, onElimina }) {
  return (
    <article className="group w-full max-w-sm overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm transition-all duration-300 hover:-translate-y-1 hover:border-indigo-200 hover:shadow-xl hover:shadow-slate-200/60">

      {/* Header */}
      <div className="flex items-start justify-between gap-4 border-b border-slate-100 bg-gradient-to-br from-slate-50 to-white p-5">
        <div className="flex min-w-0 items-center gap-3">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-indigo-100 text-lg font-bold uppercase text-indigo-700 ring-4 ring-indigo-50">
            {dati.nome?.charAt(0)}
            {dati.cognome?.charAt(0)}
          </div>

          <div className="min-w-0">
            <h3 className="truncate text-base font-bold text-slate-900">
              {dati.nome} {dati.cognome}
            </h3>

            <p className="mt-1 text-xs font-medium uppercase tracking-wider text-slate-400">
              Dipendente
            </p>
          </div>
        </div>

        <span className="mt-1 inline-flex h-2.5 w-2.5 shrink-0 rounded-full bg-emerald-500 ring-4 ring-emerald-100" />
      </div>

      {/* Contenuto */}
      <div className="p-5">
        <div className="flex items-center gap-3 rounded-xl bg-slate-50 p-3">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-white text-slate-500 shadow-sm">
            <i className="bi bi-envelope text-lg" aria-hidden="true" />
          </div>

          <div className="min-w-0">
            <p className="text-xs font-medium text-slate-400">
              Indirizzo email
            </p>
            <p className="mt-0.5 truncate text-sm font-medium text-slate-700">
              {dati.email}
            </p>
          </div>
        </div>
      </div>

      {/* Azioni */}
      <div className="flex gap-3 px-5 pb-5">
        <button
          type="button"
          onClick={() => onAggiorna?.(dati)}
          className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl border border-indigo-200 bg-indigo-50 px-4 py-2.5 text-sm font-semibold text-indigo-700 transition-colors hover:border-indigo-600 hover:bg-indigo-600 hover:text-white focus:outline-none focus:ring-4 focus:ring-indigo-100"
        >
          <i className="bi bi-pencil-square text-base" aria-hidden="true" />
          Aggiorna
        </button>

        <button
          type="button"
          onClick={() => onElimina?.(dati)}
          className="inline-flex flex-1 items-center justify-center gap-2 rounded-xl border border-red-200 bg-white px-4 py-2.5 text-sm font-semibold text-red-600 transition-colors hover:border-red-600 hover:bg-red-600 hover:text-white focus:outline-none focus:ring-4 focus:ring-red-100"
        >
          <i className="bi bi-trash3 text-base" aria-hidden="true" />
          Elimina
        </button>
      </div>
    </article>
  );
}

export default App
