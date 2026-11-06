import { useCallback, useEffect, useState } from 'react';

const POLLING_INTERVAL_MS = 2000;

/**
 * Lezione 11: la LLM Wiki nella base di conoscenza.
 *
 * Mostra quante pagine della wiki sono indicizzate in pgvector e permette di
 * aggiornare l'indice. La reindicizzazione gira in background sul server:
 * come per i PDF, chiediamo lo stato ogni 2 secondi finché è in corso.
 */
function WikiPanel({ apiUrl }) {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [isStarting, setIsStarting] = useState(false);

  const refresh = useCallback(() =>
    fetch(`${apiUrl}/wiki/status`)
      .then(response => {
        if (!response.ok) throw new Error(`Errore del server (${response.status})`);
        return response.json();
      })
      .then(data => { setStatus(data); setError(''); })
      .catch(() => setError('Stato della wiki non disponibile: il backend non risponde.')),
  [apiUrl]);

  useEffect(() => { refresh(); }, [refresh]);

  const running = status?.running ?? false;
  useEffect(() => {
    if (!running) return undefined;
    const intervalId = setInterval(refresh, POLLING_INTERVAL_MS);
    return () => clearInterval(intervalId);
  }, [running, refresh]);

  async function startReindex() {
    setIsStarting(true);
    setError('');
    try {
      const response = await fetch(`${apiUrl}/wiki/reindex`, { method: 'POST' });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.message || `Errore del server (${response.status})`);
      setStatus(data);
    } catch (e) {
      setError(e.message);
    } finally {
      setIsStarting(false);
    }
  }

  if (!status) {
    return error ? <WikiCard><p className="text-sm text-slate-400">{error}</p></WikiCard> : null;
  }

  if (!status.enabled) {
    return (
      <WikiCard>
        <p className="text-sm text-slate-500">
          Wiki non configurata. Imposta la variabile <code className="rounded bg-slate-100 px-1">WIKI_VAULT_PATH</code> con
          la cartella del tuo vault di Obsidian e riavvia il backend.
        </p>
      </WikiCard>
    );
  }

  const progress = status.totalPages > 0 ? Math.round((status.processedPages / status.totalPages) * 100) : 0;
  const report = status.lastReport;

  return (
    <WikiCard vaultName={status.vaultName}>
      <div className="flex flex-wrap items-center gap-3">
        <p className="min-w-0 flex-1 text-sm text-slate-600">
          <span className="font-semibold text-slate-900">{status.indexedPages}</span> pagine indicizzate
          {' · '}
          <span className="font-semibold text-slate-900">{status.indexedChunks}</span> chunk in pgvector
        </p>
        <button
          type="button"
          onClick={startReindex}
          disabled={running || isStarting}
          className="flex items-center gap-2 rounded-xl border border-indigo-200 bg-indigo-50 px-3 py-2 text-sm font-semibold text-indigo-700 transition hover:bg-indigo-100 disabled:cursor-not-allowed disabled:opacity-60"
        >
          <i className={`bi bi-arrow-repeat ${running ? 'animate-spin' : ''}`} aria-hidden="true" />
          {running ? 'Aggiornamento in corso...' : 'Aggiorna indice della wiki'}
        </button>
      </div>

      {running && (
        <div className="mt-3" aria-live="polite">
          <div
            className="h-2 overflow-hidden rounded-full bg-slate-100"
            role="progressbar"
            aria-valuenow={progress}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label="Avanzamento indicizzazione della wiki"
          >
            <div className="h-full rounded-full bg-indigo-500 transition-[width] duration-500" style={{ width: `${progress}%` }} />
          </div>
          <p className="mt-1.5 truncate text-xs text-slate-500" title={status.currentPage}>
            {status.processedPages}/{status.totalPages} pagine controllate
            {status.currentPage && ` · ${status.currentPage}`}
          </p>
        </div>
      )}

      {!running && report && (
        <p className="mt-3 text-xs text-slate-500">
          Ultimo aggiornamento: {report.updated} pagine indicizzate ({report.chunks} chunk), {report.unchanged} invariate,
          {' '}{report.removed} rimosse{report.failed > 0 && `, ${report.failed} non riuscite`}.
        </p>
      )}

      {!running && status.failedPages?.length > 0 && (
        <details className="mt-2 text-xs text-amber-700">
          <summary className="cursor-pointer">Pagine non indicizzate ({status.failedPages.length})</summary>
          <ul className="mt-1 list-disc pl-5">
            {status.failedPages.slice(0, 20).map(page => <li key={page}>{page}</li>)}
          </ul>
        </details>
      )}

      {(status.error || error) && (
        <p role="alert" className="mt-3 rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700">{status.error || error}</p>
      )}
    </WikiCard>
  );
}

function WikiCard({ vaultName, children }) {
  return (
    <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
      <div className="mb-3 flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="flex items-center gap-2 text-base font-semibold text-slate-900">
          <i className="bi bi-journal-bookmark text-violet-600" aria-hidden="true" />
          LLM Wiki
        </h2>
        {vaultName && <p className="text-xs text-slate-500">Vault: {vaultName}</p>}
      </div>
      {children}
      <p className="mt-3 text-xs text-slate-400">
        Le pagine Markdown della wiki entrano nella base di conoscenza accanto ai documenti caricati.
        Si reindicizzano solo le pagine cambiate.
      </p>
    </section>
  );
}

export default WikiPanel;
