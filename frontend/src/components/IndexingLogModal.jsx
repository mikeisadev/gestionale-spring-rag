import { useEffect, useRef, useState } from 'react';

const LEVEL_STYLES = {
  INFO: 'bg-sky-50 text-sky-700',
  WARN: 'bg-amber-50 text-amber-700',
  ERROR: 'bg-red-50 text-red-700',
};

const PHASE_LABELS = {
  UPLOAD: 'Upload',
  LETTURA: 'Lettura',
  CHUNKING: 'Chunking',
  EMBEDDING: 'Embedding',
  VERSIONI: 'Versioni', // lezione 10: sostituzione della versione precedente
  FINE: 'Fine',
};

// Finestra con i log di un caricamento (lezione 7, pulsante "Vedi log").
// Se il caricamento è ancora in corso ("live"), ricarica i log ogni 2 secondi.
function IndexingLogModal({ apiUrl, documentId, filename, live, onClose }) {
  const [logs, setLogs] = useState([]);
  const [error, setError] = useState('');
  const endRef = useRef(null);

  useEffect(() => {
    let ignore = false;

    const load = () =>
      fetch(`${apiUrl}/history/${documentId}/logs`)
        .then(response => {
          if (!response.ok) throw new Error();
          return response.json();
        })
        .then(data => { if (!ignore) { setLogs(data); setError(''); } })
        .catch(() => { if (!ignore) setError('Impossibile caricare i log.'); });

    load();
    const intervalId = live ? setInterval(load, 2000) : null;

    return () => {
      ignore = true;
      if (intervalId) clearInterval(intervalId);
    };
  }, [apiUrl, documentId, live]);

  // Scorre in fondo quando arrivano nuove righe
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [logs.length]);

  // Chiusura con il tasto Esc
  useEffect(() => {
    const onKeyDown = event => { if (event.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [onClose]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 p-4 backdrop-blur-sm"
      onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="log-title"
        className="flex max-h-[85vh] w-full max-w-3xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between gap-4 border-b border-slate-100 px-6 py-5">
          <div className="min-w-0">
            <h2 id="log-title" className="text-lg font-bold tracking-tight text-slate-900">
              Log di indicizzazione
            </h2>
            <p className="mt-1 truncate text-sm text-slate-500" title={filename}>
              {filename}
              {live && <span className="ml-2 text-indigo-600">· aggiornamento in tempo reale</span>}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Chiudi log"
            className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
          >
            <i className="bi bi-x-lg" aria-hidden="true" />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto bg-slate-50 p-4">
          {error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}
          {!error && logs.length === 0 && <p className="py-8 text-center text-sm text-slate-400">Nessun log disponibile.</p>}

          <ol className="space-y-1.5 font-mono text-xs">
            {logs.map((line, index) => (
              <li key={index} className="flex flex-wrap items-start gap-2 rounded-lg bg-white px-3 py-2 shadow-sm">
                <time className="shrink-0 text-slate-400">
                  {new Date(line.time).toLocaleTimeString('it-IT')}
                </time>
                <span className={`shrink-0 rounded px-1.5 font-semibold ${LEVEL_STYLES[line.level] ?? LEVEL_STYLES.INFO}`}>
                  {line.level}
                </span>
                <span className="w-20 shrink-0 font-semibold text-slate-600">
                  {PHASE_LABELS[line.phase] ?? line.phase}
                </span>
                <span className="min-w-0 flex-1 whitespace-pre-wrap break-words text-slate-800">{line.message}</span>
              </li>
            ))}
          </ol>
          <div ref={endRef} />
        </div>
      </div>
    </div>
  );
}

export default IndexingLogModal;
