import { useEffect, useState } from 'react';
import axios from 'axios';

function Dashboard() {
  const [numeroDipendenti, setNumeroDipendenti] = useState(null);
  const [erroreCaricamento, setErroreCaricamento] = useState('');

  useEffect(() => {
    axios.get('http://localhost:8081/api/dipendenti')
      .then(response => setNumeroDipendenti(response.data.length))
      .catch(error => {
        setErroreCaricamento(
          error.response?.data?.message ??
          'Non è stato possibile caricare il conteggio dei dipendenti.'
        );
      });
  }, []);

  const metriche = [
    {
      label: 'Dipendenti',
      valore: numeroDipendenti ?? '—',
      descrizione: erroreCaricamento
        ? 'Conteggio non disponibile'
        : numeroDipendenti === null
          ? 'Conteggio in caricamento'
          : 'Membri registrati nel team',
      icona: 'bi-people',
    },
    {
      label: 'Contratti',
      valore: '—',
      descrizione: 'Conteggio disponibile con una API dedicata',
      icona: 'bi-file-earmark-text',
    },
    {
      label: 'Progetti',
      valore: '—',
      descrizione: 'Conteggio disponibile quando verrà attivata la sezione',
      icona: 'bi-kanban',
    },
    {
      label: 'Reparti',
      valore: '—',
      descrizione: 'Conteggio disponibile quando verrà attivata la sezione',
      icona: 'bi-diagram-3',
    },
  ];

  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 lg:p-10">
      <div className="mx-auto max-w-7xl">
        <header className="mb-8 border-b border-slate-200 pb-7">
          <p className="mb-2 text-sm font-semibold uppercase tracking-[0.16em] text-indigo-600">
            Gestionale
          </p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
            Dashboard
          </h1>
          <p className="mt-2 max-w-xl text-sm leading-6 text-slate-600 sm:text-base">
            Riepilogo delle principali entità del gestionale.
          </p>
        </header>

        {erroreCaricamento && (
          <p role="alert" className="mb-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
            {erroreCaricamento}
          </p>
        )}

        <section aria-labelledby="dashboard-metrics-title">
          <div className="mb-5">
            <h2 id="dashboard-metrics-title" className="text-xl font-semibold text-slate-900">
              Panoramica
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              I valori non ancora collegati a una API sono indicati con un trattino.
            </p>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {metriche.map(metrica => (
              <article
                key={metrica.label}
                className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <p className="text-sm font-medium text-slate-600">{metrica.label}</p>
                    <p className="mt-3 text-3xl font-bold tracking-tight text-slate-950">
                      {metrica.valore}
                    </p>
                  </div>
                  <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-50 text-indigo-600">
                    <i className={`bi ${metrica.icona} text-xl`} aria-hidden="true" />
                  </span>
                </div>
                <p className="mt-4 text-xs leading-5 text-slate-500">
                  {metrica.descrizione}
                </p>
              </article>
            ))}
          </div>
        </section>
      </div>
    </main>
  );
}

export default Dashboard;
