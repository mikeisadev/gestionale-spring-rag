import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { getPage, searchWiki } from '../../api/wikiApi';
import WikiMarkdown from '../../components/WikiMarkdown';

const MAX_QUERY_LENGTH = 200; // come il backend
const DEBOUNCE_MS = 250;      // aspettiamo che l'utente smetta di scrivere

// Lezione 12: ricerca per parole chiave. Senza ricerca mostra index.md, il catalogo della wiki.
function WikiSearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q') ?? ''; // la ricerca è nell'indirizzo: il tasto "indietro" funziona
  // I risultati ricordano a quale ricerca appartengono: così "in corso" si ricava, non si imposta
  const [found, setFound] = useState({ query: '', results: [], error: '' });
  const [indexPage, setIndexPage] = useState(null);

  useEffect(() => {
    getPage('index.md').then(setIndexPage).catch(() => setIndexPage(null));
  }, []);

  useEffect(() => {
    if (!query.trim()) return undefined;
    const timer = setTimeout(() => {
      searchWiki(query)
        .then(results => setFound({ query, results, error: '' }))
        .catch(e => setFound({ query, results: [], error: e.message }));
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer); // l'utente scrive ancora: annulliamo la ricerca precedente
  }, [query]);

  const isSearching = query.trim() !== '' && found.query !== query;
  const results = found.query === query ? found.results : [];
  const error = found.query === query ? found.error : '';

  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
        <label htmlFor="wiki-search" className="text-sm font-semibold text-slate-900">Cerca nella wiki</label>
        <div className="relative mt-2">
          <i className="bi bi-search pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-slate-400" aria-hidden="true" />
          <input
            id="wiki-search"
            type="search"
            value={query}
            maxLength={MAX_QUERY_LENGTH}
            onChange={event => setSearchParams(event.target.value ? { q: event.target.value } : {}, { replace: true })}
            placeholder="Titolo, alias, tag o testo: ad esempio ransomware, MFA, NIS2..."
            className="w-full rounded-xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 text-sm outline-none transition focus:border-violet-400 focus:bg-white focus:ring-4 focus:ring-violet-100"
            autoFocus
          />
        </div>
        <p className="mt-2 text-xs text-slate-400">
          Nessun database vettoriale: il titolo pesa più degli alias, gli alias più dei tag, i tag più del testo.
        </p>
      </section>

      {error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

      {query.trim() ? (
        <section aria-live="polite" className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
          <p className="text-xs text-slate-500">
            {isSearching ? 'Ricerca...' : `${results.length} risultati per "${query}"`}
          </p>
          <ul className="mt-2 divide-y divide-slate-100">
            {results.map(hit => (
              <li key={hit.path} className="py-3">
                <Link to={`/wiki/page?path=${encodeURIComponent(hit.path)}`} className="group block">
                  <div className="flex items-baseline justify-between gap-3">
                    <p className="font-semibold text-slate-900 group-hover:text-violet-700">{hit.title}</p>
                    <span className="shrink-0 rounded-full bg-slate-100 px-2 py-0.5 text-[11px] text-slate-500" title="Punteggio">
                      {hit.score}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400">{hit.path}</p>
                  {hit.snippet && <p className="mt-1 line-clamp-2 text-sm text-slate-600">{hit.snippet}</p>}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : indexPage && (
        <article className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-8">
          <p className="mb-2 text-xs font-semibold uppercase tracking-wide text-slate-400">index.md</p>
          <WikiMarkdown markdown={indexPage.markdown} />
        </article>
      )}
    </div>
  );
}

export default WikiSearchPage;
