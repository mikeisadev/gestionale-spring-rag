import { useState, useEffect } from "react";

function EmployeeUpdateModal({ dipendente, onClose, onSubmit }) {
  const [form, setForm] = useState({
    nome: dipendente?.nome ?? "",
    cognome: dipendente?.cognome ?? "",
    email: dipendente?.email ?? "",
  });

  useEffect(() => {
    setForm({
      nome: dipendente?.nome ?? "",
      cognome: dipendente?.cognome ?? "",
      email: dipendente?.email ?? "",
    });
  }, [dipendente]);

  function handleChange(e) {
    setForm({
      ...form,
      [e.target.name]: e.target.value,
    });
  }

  function handleSubmit(e) {
    e.preventDefault();
    onSubmit?.({ ...dipendente, ...form });
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose?.();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
        className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-100 px-6 py-5">
          <div>
            <h2
              id="modal-title"
              className="text-xl font-bold tracking-tight text-slate-900"
            >
              Aggiorna dipendente
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Modifica i dati anagrafici del dipendente.
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Chiudi finestra"
            className="flex h-9 w-9 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-5 p-6">
          <div>
            <label
              htmlFor="nome"
              className="mb-2 block text-sm font-semibold text-slate-700"
            >
              Nome
            </label>
            <input
              id="nome"
              name="nome"
              type="text"
              value={form.nome}
              onChange={handleChange}
              placeholder="Inserisci il nome"
              required
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-100"
            />
          </div>

          <div>
            <label
              htmlFor="cognome"
              className="mb-2 block text-sm font-semibold text-slate-700"
            >
              Cognome
            </label>
            <input
              id="cognome"
              name="cognome"
              type="text"
              value={form.cognome}
              onChange={handleChange}
              placeholder="Inserisci il cognome"
              required
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-100"
            />
          </div>

          <div>
            <label
              htmlFor="email"
              className="mb-2 block text-sm font-semibold text-slate-700"
            >
              Email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              value={form.email}
              onChange={handleChange}
              placeholder="nome@esempio.it"
              required
              className="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-100"
            />
          </div>

          {/* Footer */}
          <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={onClose}
              className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-600 transition hover:bg-slate-50"
            >
              Annulla
            </button>

            <button
              type="submit"
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200"
            >
              <i className="bi bi-check2-circle" aria-hidden="true" />
              Aggiorna dipendente
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

export default EmployeeUpdateModal;