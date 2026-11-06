import { useEffect, useState } from 'react';
import { NavLink, Outlet } from 'react-router';
import { getWikiStatus, reloadWiki } from '../../api/wikiApi';

// Lezione 12: il "secondo IDE" della wiki (il primo è Obsidian), dentro il gestionale.
// Questa è la cornice comune: titolo, schede Ricerca / Chiedi e pulsante per rileggere la wiki.
function WikiExplorer() {
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [reloadMessage, setReloadMessage] = useState('');
  const [isReloading, setIsReloading] = useState(false);

  useEffect(() => {
    getWikiStatus().then(setStatus).catch(e => setError(e.message));
  }, []);

  async function handleReload() {
    setIsReloading(true);
    setReloadMessage('');
    try {
      const result = await reloadWiki();
      setReloadMessage(`Wiki riletta dal disco: ${result.pages} pagine.`);
    } catch (e) {
      setReloadMessage(e.message);
    } finally {
      setIsReloading(false);
    }
  }

  const tabClass = ({ isActive }) =>
    `flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold transition ${
      isActive ? 'bg-violet-50 text-violet-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`;

  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 lg:p-10">
      <div className="mx-auto max-w-5xl">
        <header className="mb-6 border-b border-slate-200 pb-6">
          <p className="mb-2 text-sm font-semibold uppercase tracking-[0.16em] text-violet-600">LLM Wiki</p>
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">Wiki Explorer</h1>
              <p className="mt-2 text-sm text-slate-600">
                {status?.enabled
                  ? <>Cerca, leggi e interroga la wiki <span className="font-semibold">{status.vaultName}</span>.</>
                  : 'Cerca, leggi e interroga la LLM Wiki.'}
              </p>
            </div>
            {status?.enabled && (
              <button
                type="button"
                onClick={handleReload}
                disabled={isReloading}
                title="Rilegge le pagine dal disco, ad esempio dopo un ingest in Obsidian"
                className="flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 disabled:opacity-60"
              >
                <i className={`bi bi-arrow-clockwise ${isReloading ? 'animate-spin' : ''}`} aria-hidden="true" />
                Rileggi la wiki
              </button>
            )}
          </div>
          {reloadMessage && <p className="mt-3 text-xs text-slate-500">{reloadMessage}</p>}

          {status?.enabled && (
            <nav aria-label="Sezioni del Wiki Explorer" className="mt-5 flex gap-2">
              <NavLink to="/wiki" end className={tabClass}>
                <i className="bi bi-search" aria-hidden="true" /> Ricerca
              </NavLink>
              <NavLink to="/wiki/ask" className={tabClass}>
                <i className="bi bi-chat-square-text" aria-hidden="true" /> Chiedi alla wiki
              </NavLink>
            </nav>
          )}
        </header>

        {error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

        {status && !status.enabled && (
          <section className="rounded-2xl border border-slate-200 bg-white p-6 text-sm text-slate-600 shadow-sm">
            <p className="font-semibold text-slate-900">Wiki non configurata</p>
            <p className="mt-2">
              Imposta la variabile <code className="rounded bg-slate-100 px-1">WIKI_VAULT_PATH</code> con la cartella del tuo
              vault di Obsidian (oppure con <code className="rounded bg-slate-100 px-1">llm-wiki-esempio</code>) e riavvia il backend.
            </p>
          </section>
        )}

        {status?.enabled && <Outlet />}
      </div>
    </main>
  );
}

export default WikiExplorer;
