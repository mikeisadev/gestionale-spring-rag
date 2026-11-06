import { useState } from 'react';

// Bozze email preparate dall'agente (lezione 6.4, "prepara, poi conferma").
// L'agente NON può inviare: l'invio parte solo quando l'utente preme "Conferma invio".
function EmailDraftsPanel({ drafts, onSend, onDiscard }) {
  const [busyDraftId, setBusyDraftId] = useState(null);
  const [feedback, setFeedback] = useState('');

  if (drafts.length === 0 && !feedback) return null;

  async function handle(action, draft) {
    setBusyDraftId(draft.id);
    setFeedback('');
    try {
      await action(draft);
      setFeedback(action === onSend
        ? `Email inviata a ${draft.to}.`
        : `Bozza "${draft.subject}" annullata.`);
    } catch (error) {
      setFeedback(error.message || 'Operazione non riuscita.');
    } finally {
      setBusyDraftId(null);
    }
  }

  return (
    <div className="border-t border-amber-100 bg-amber-50/60 p-4 sm:px-5">
      {feedback && (
        <p role="status" className="mb-3 flex items-center gap-2 text-sm text-slate-700">
          <i className="bi bi-info-circle text-amber-600" aria-hidden="true" />
          {feedback}
          <button type="button" onClick={() => setFeedback('')} className="ml-auto text-xs text-slate-400 hover:text-slate-600">
            Chiudi
          </button>
        </p>
      )}

      {drafts.length > 0 && (
        <>
          <h3 className="mb-3 flex items-center gap-2 text-sm font-semibold text-amber-800">
            <i className="bi bi-envelope-paper" aria-hidden="true" />
            Bozze email da confermare ({drafts.length})
          </h3>
          <ul className="max-h-64 space-y-3 overflow-y-auto">
            {drafts.map(draft => (
              <li key={draft.id} className="rounded-xl border border-amber-200 bg-white p-4 shadow-sm">
                <dl className="space-y-1 text-sm">
                  <div className="flex gap-2">
                    <dt className="w-16 shrink-0 text-slate-400">A</dt>
                    <dd className="min-w-0 truncate font-medium text-slate-900">{draft.to}</dd>
                  </div>
                  <div className="flex gap-2">
                    <dt className="w-16 shrink-0 text-slate-400">Oggetto</dt>
                    <dd className="min-w-0 truncate font-medium text-slate-900">{draft.subject}</dd>
                  </div>
                </dl>
                <p className="mt-2 max-h-28 overflow-y-auto whitespace-pre-wrap rounded-lg bg-slate-50 p-3 text-sm text-slate-700">
                  {draft.body}
                </p>
                <div className="mt-3 flex justify-end gap-2">
                  <button
                    type="button"
                    onClick={() => handle(onDiscard, draft)}
                    disabled={busyDraftId !== null}
                    className="rounded-lg px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 disabled:opacity-50"
                  >
                    Annulla
                  </button>
                  <button
                    type="button"
                    onClick={() => handle(onSend, draft)}
                    disabled={busyDraftId !== null}
                    className="flex items-center gap-2 rounded-lg bg-amber-600 px-3 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-amber-700 focus:outline-none focus:ring-4 focus:ring-amber-200 disabled:opacity-50"
                  >
                    <i className={`bi ${busyDraftId === draft.id ? 'bi-hourglass-split' : 'bi-send-check'}`} aria-hidden="true" />
                    Conferma invio
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

export default EmailDraftsPanel;
