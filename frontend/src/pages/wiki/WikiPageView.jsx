import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { getPage } from '../../api/wikiApi';
import WikiMarkdown from '../../components/WikiMarkdown';

function pageUrl(path) {
  return `/wiki/page?path=${encodeURIComponent(path)}`;
}

function LinkList({ title, icon, links, emptyText }) {
  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="flex items-center gap-2 text-sm font-semibold text-slate-900">
        <i className={`bi ${icon} text-violet-600`} aria-hidden="true" /> {title}
        <span className="text-xs font-normal text-slate-400">({links.length})</span>
      </h2>
      {links.length === 0 ? (
        <p className="mt-2 text-xs text-slate-400">{emptyText}</p>
      ) : (
        <ul className="mt-2 max-h-72 space-y-1 overflow-y-auto pr-1">
          {links.map(link => (
            <li key={link.path}>
              <Link to={pageUrl(link.path)} className="block truncate rounded-lg px-2 py-1 text-sm text-slate-600 hover:bg-violet-50 hover:text-violet-700" title={link.path}>
                {link.title}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// Lezione 12: una pagina della wiki con backlink (chi la cita) e collegamenti in uscita (chi cita).
function WikiPageView() {
  const [searchParams] = useSearchParams();
  const path = searchParams.get('path') ?? '';
  const navigate = useNavigate();
  // La pagina caricata ricorda il suo percorso: cambiando pagina, quella vecchia non viene mostrata
  const [loaded, setLoaded] = useState({ path: null, page: null, error: '' });

  useEffect(() => {
    if (!path) return;
    getPage(path)
      .then(page => setLoaded({ path, page, error: '' }))
      .catch(e => setLoaded({ path, page: null, error: e.message }));
    window.scrollTo({ top: 0 });
  }, [path]);

  const page = loaded.path === path ? loaded.page : null;
  const error = loaded.path === path ? loaded.error : '';

  if (error) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <p role="alert" className="text-sm text-red-700">{error}</p>
        <Link to="/wiki" className="mt-3 inline-block text-sm font-semibold text-violet-700">Torna alla ricerca</Link>
      </div>
    );
  }

  if (!page) {
    return <p className="text-sm text-slate-400">Caricamento della pagina...</p>;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_16rem]">
      <article className="min-w-0 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-8">
        <div className="mb-4 flex flex-wrap items-center gap-2 text-xs text-slate-400">
          <button type="button" onClick={() => navigate(-1)} className="flex items-center gap-1 font-semibold text-slate-500 hover:text-violet-700">
            <i className="bi bi-arrow-left" aria-hidden="true" /> Indietro
          </button>
          <span aria-hidden="true">·</span>
          <span className="truncate">{page.path}</span>
        </div>
        {page.tags.length > 0 && (
          <div className="mb-4 flex flex-wrap gap-1.5">
            {page.tags.map(tag => (
              <Link key={tag} to={`/wiki?q=${encodeURIComponent(tag)}`} className="rounded-full bg-violet-50 px-2 py-0.5 text-xs font-medium text-violet-700 hover:bg-violet-100">
                #{tag}
              </Link>
            ))}
          </div>
        )}
        <WikiMarkdown markdown={page.markdown} />
      </article>

      <aside className="space-y-4 lg:sticky lg:top-6 lg:self-start">
        <LinkList title="Citata da" icon="bi-arrow-return-left" links={page.backlinks} emptyText="Nessuna pagina cita questa pagina." />
        <LinkList title="Collegamenti" icon="bi-arrow-right" links={page.outgoingLinks} emptyText="Questa pagina non cita altre pagine." />
      </aside>
    </div>
  );
}

export default WikiPageView;
