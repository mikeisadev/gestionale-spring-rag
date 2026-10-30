import { useState } from "react";

function EmployeeForm({ onClose, onSubmit }) {
  const [formDipendente, setFormDipendente] = useState({
    nome: "",
    cognome: "",
    email: "",
  });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");

  function handleInput(event) {
    setFormDipendente(form => ({
      ...form,
      [event.target.name]: event.target.value,
    }));
    setSubmitError("");
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setIsSubmitting(true);
    setSubmitError("");

    try {
      await onSubmit(formDipendente);
    } catch (error) {
      setSubmitError(
        error.response?.data?.message ??
        "Impossibile aggiungere il dipendente. Controlla i dati e riprova."
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm"
      onMouseDown={event => {
        if (event.target === event.currentTarget && !isSubmitting) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="create-employee-title"
        className="w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <h2 id="create-employee-title" className="text-xl font-bold tracking-tight text-slate-900">
              Aggiungi dipendente
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Inserisci i dati anagrafici e i contatti del nuovo membro del team.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            disabled={isSubmitting}
            aria-label="Chiudi finestra"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-5 p-6">
          {submitError && (
            <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              {submitError}
            </p>
          )}
          <div className="grid gap-5 sm:grid-cols-2">
            <div>
              <label htmlFor="new-employee-nome" className="mb-2 block text-sm font-medium text-slate-700">
                Nome
              </label>
              <div className='relative'>
                <i className="bi bi-person absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                  type="text"
                  id="new-employee-nome"
                  name="nome"
                  autoComplete="given-name"
                  placeholder="Es. Maria"
                  value={formDipendente.nome}
                  onChange={handleInput}
                  required
                  className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-100"
                />
              </div>
            </div>

            <div>
              <label htmlFor="new-employee-cognome" className="mb-2 block text-sm font-medium text-slate-700">
                Cognome
              </label>
              <div className="relative">
                <i className="bi bi-person-badge absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                  type="text"
                  id="new-employee-cognome"
                  name="cognome"
                  autoComplete="family-name"
                  placeholder="Es. Rossi"
                  value={formDipendente.cognome}
                  onChange={handleInput}
                  required
                  className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-100"
                />
              </div>
            </div>

            <div className="sm:col-span-2">
              <label htmlFor="new-employee-email" className="mb-2 block text-sm font-medium text-slate-700">
                Email
              </label>
              <div className="relative">
                <i className="bi bi-envelope absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
                <input
                  type="email"
                  id="new-employee-email"
                  name="email"
                  autoComplete="email"
                  placeholder="nome@azienda.it"
                  value={formDipendente.email}
                  onChange={handleInput}
                  required
                  className="w-full rounded-xl border border-slate-300 bg-white py-2.5 pl-10 pr-3 text-sm text-slate-900 placeholder:text-slate-400 transition focus:border-indigo-500 focus:outline-none focus:ring-4 focus:ring-indigo-100"
                />
              </div>
            </div>
          </div>

          <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={onClose}
              disabled={isSubmitting}
              className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Annulla
            </button>
            <button
              type="submit"
              disabled={isSubmitting}
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <i className="bi bi-person-plus" aria-hidden="true" />
              {isSubmitting ? "Aggiunta in corso..." : "Aggiungi dipendente"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EmployeeForm;