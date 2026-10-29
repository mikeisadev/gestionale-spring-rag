function EmployeeForm({formRef, handleSubmit, handleInput}) {

    return (
        <form
          ref={formRef}
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
    )
}

export default EmployeeForm;