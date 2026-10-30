import { useState } from "react";

function EmployeeDeleteModal({ dipendente, onClose, onConfirm }) {
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState("");

  async function handleConfirm() {
    setIsDeleting(true);
    setDeleteError("");

    try {
      await onConfirm();
      setIsDeleting(false);
      onClose();
    } catch (error) {
      setDeleteError(
        error.response?.data?.message ??
        "Impossibile eliminare il dipendente. Riprova."
      );
      setIsDeleting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !isDeleting) onClose();
      }}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="delete-modal-title"
        aria-describedby="delete-modal-description"
        className="w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start gap-4 p-6">
          <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-red-100 text-red-600">
            <i className="bi bi-trash3 text-xl" aria-hidden="true" />
          </div>
          <div>
            <h2
              id="delete-modal-title"
              className="text-xl font-bold tracking-tight text-slate-900"
            >
              Conferma eliminazione
            </h2>
            <p
              id="delete-modal-description"
              className="mt-2 text-sm leading-6 text-slate-600"
            >
              Vuoi eliminare il dipendente{" "}
              <span className="font-semibold text-slate-900">
                {dipendente.nome} {dipendente.cognome}
              </span>
              ? L'operazione non può essere annullata.
            </p>
          </div>
        </div>

        <div className="space-y-4 border-t border-slate-100 p-6">
          {deleteError && (
            <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              {deleteError}
            </p>
          )}

          <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <button
              type="button"
              onClick={onClose}
              disabled={isDeleting}
              className="rounded-xl border border-slate-200 px-5 py-3 text-sm font-semibold text-slate-600 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              Annulla
            </button>
            <button
              type="button"
              onClick={handleConfirm}
              disabled={isDeleting}
              className="inline-flex items-center justify-center gap-2 rounded-xl bg-red-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-red-700 focus:outline-none focus:ring-4 focus:ring-red-200 disabled:cursor-not-allowed disabled:opacity-50"
            >
              <i className="bi bi-trash3" aria-hidden="true" />
              {isDeleting ? "Eliminazione..." : "Elimina dipendente"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default EmployeeDeleteModal;
