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

export default EmployeeCard;