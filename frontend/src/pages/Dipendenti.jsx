import { useEffect, useState } from 'react';
import axios from "axios";
import EmployeeCard from '../components/EmployeeCard';
import EmployeeForm from '../components/EmployeeForm';
import EmployeeUpdateModal from '../components/EmployeeUpdateModal';
import EmployeeDeleteModal from '../components/EmployeeDeleteModal';

function Dipendenti() {
  const [dipendenti, aggiornaDipendenti] = useState(null);
  const [mostraFormAggiunta, setMostraFormAggiunta] = useState(false);
  const [dipendenteDaAggiornare, setDipendenteDaAggiornare] = useState(null);
  const [dipendenteDaEliminare, setDipendenteDaEliminare] = useState(null);

  useEffect(() => {
    axios.get("http://localhost:8081/api/dipendenti")
      .then(response => aggiornaDipendenti(response.data));
  }, []);

  async function handleCreateSubmit(dati) {
    const response = await axios.post("http://localhost:8081/api/dipendenti", dati);
    const nuovoDipendente = response.data.data;

    aggiornaDipendenti(dipendenti => [...(dipendenti ?? []), nuovoDipendente]);
    setMostraFormAggiunta(false);
  }

  async function handleDelete(dipendente) {
    await axios.delete(`http://localhost:8081/api/dipendenti/${dipendente.id}`);
    aggiornaDipendenti(dipendenti =>
      dipendenti.filter(({ id }) => id !== dipendente.id)
    );
  }

  function handleUpdate(dati) {
    setDipendenteDaAggiornare(dati);
  }

  async function handleUpdateSubmit(dati) {
    await axios.put(
      `http://localhost:8081/api/dipendenti/${dipendenteDaAggiornare.id}`,
      {
        nome: dati.nome,
        cognome: dati.cognome,
        email: dati.email
      }
    );

    aggiornaDipendenti(dipendenti =>
      dipendenti.map(dipendente =>
        dipendente.id === dipendenteDaAggiornare.id
          ? { ...dipendente, ...dati }
          : dipendente
      )
    );
    setDipendenteDaAggiornare(null);
  }

  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 lg:p-10">
      <div className="mx-auto max-w-7xl">
        <header className="mb-8 flex flex-col gap-5 border-b border-slate-200 pb-7 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="mb-2 text-sm font-semibold uppercase tracking-[0.16em] text-indigo-600">
              Gestionale
            </p>
            <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
              Dipendenti
            </h1>
            <p className="mt-2 max-w-xl text-sm leading-6 text-slate-600 sm:text-base">
              Gestisci i membri del team e tieni aggiornati i loro dati.
            </p>
          </div>

          <button
            type="button"
            onClick={() => setMostraFormAggiunta(true)}
            disabled={dipendenti === null}
            className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"
          >
            <i className="bi bi-person-plus" aria-hidden="true" />
            Aggiungi dipendente
          </button>
        </header>

        <section aria-labelledby="employee-list-title">
          <div className="mb-5 flex flex-wrap items-baseline justify-between gap-2">
            <h2 id="employee-list-title" className="text-xl font-semibold text-slate-900">
              Elenco dipendenti
            </h2>
            {dipendenti !== null && (
              <p className="text-sm text-slate-500">
                {dipendenti.length} {dipendenti.length === 1 ? "dipendente" : "dipendenti"}
              </p>
            )}
          </div>

          {dipendenti === null ? (
            <p role="status" className="rounded-2xl border border-slate-200 bg-white px-5 py-8 text-center text-sm text-slate-600">
              Caricamento dei dipendenti in corso...
            </p>
          ) : dipendenti.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-slate-300 bg-white px-5 py-12 text-center">
              <i className="bi bi-people mb-3 text-3xl text-slate-400" aria-hidden="true" />
              <h3 className="text-base font-semibold text-slate-900">Nessun dipendente presente</h3>
              <p className="mt-1 text-sm text-slate-500">
                Aggiungi il primo membro del team per iniziare.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
              {dipendenti.map(dipendente => (
                <EmployeeCard
                  key={dipendente.id}
                  dati={dipendente}
                  onAggiorna={handleUpdate}
                  onElimina={setDipendenteDaEliminare}
                />
              ))}
            </div>
          )}
        </section>
      </div>

      {mostraFormAggiunta && (
        <EmployeeForm
          onClose={() => setMostraFormAggiunta(false)}
          onSubmit={handleCreateSubmit}
        />
      )}
      {dipendenteDaAggiornare ? (
        <EmployeeUpdateModal
          dipendente={dipendenteDaAggiornare}
          onClose={() => setDipendenteDaAggiornare(null)}
          onSubmit={handleUpdateSubmit}
        />
      ) : null}
      {dipendenteDaEliminare ? (
        <EmployeeDeleteModal
          dipendente={dipendenteDaEliminare}
          onClose={() => setDipendenteDaEliminare(null)}
          onConfirm={() => handleDelete(dipendenteDaEliminare)}
        />
      ) : null}
    </main>
  );
}

export default Dipendenti;
