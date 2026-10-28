import { useEffect, useState } from 'react';
import axios from "axios";

function App() {
  const [dipendenti, aggiornaDipendenti] = useState(null);
  const [formDipendente, setFormDipendente] = useState({
    nome: "",
    cognome: "",
    email: ""
  });

  useEffect(() => {
    getEmployees();
  }, []);

  function getEmployees() {
    axios.get("http://localhost:8081/api/dipendenti")
      .then(response => {
        aggiornaDipendenti(response.data)
        console.log(response.data)
      });
  }

  function handleSubmit(e) {
    e.preventDefault();
    console.log(formDipendente);

    axios.post("http://localhost:8081/api/dipendenti", formDipendente)
      .then(response => {
        console.log(response.data)
        
        getEmployees();
      })
  }

  function handleInput(e) {
    console.log(e.target.name, e.target.value)

    setFormDipendente({
      ...formDipendente,
      [e.target.name]: e.target.value
    });
  }

  return (
    <>
      <div className=''>
        <h1 className='heading'>Gestionale</h1>

        <h2>Dipendenti</h2>

        <form onSubmit={handleSubmit} className="max-w-md space-y-4">
          <div>
            <label htmlFor="nome" className="mb-1 block text-sm font-medium">
              Nome
            </label>
            <input
              type="text"
              id="nome"
              name="nome"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
              
              onInput={handleInput}
            />
          </div>

          <div>
            <label htmlFor="cognome" className="mb-1 block text-sm font-medium">
              Cognome
            </label>
            <input
              type="text"
              id="cognome"
              name="cognome"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
              
              onInput={handleInput}
            />
          </div>

          <div>
            <label htmlFor="email" className="mb-1 block text-sm font-medium">
              Email
            </label>
            <input
              type="email"
              id="email"
              name="email"
              className="w-full rounded-lg border border-gray-300 px-3 py-2 focus:border-blue-500 focus:outline-none"
              
              onInput={handleInput}
            />
          </div>

          <button
            type="submit"
            className="rounded-lg bg-blue-600 px-4 py-2 text-white hover:bg-blue-700"
          >
            Aggiungi dipendente
          </button>
        </form>

        <div>
          {
            dipendenti ?
            dipendenti.map((dipendente, i) => <EmployeeCard key={i} dati={dipendente}/>)
            :
            <p>Stiamo caricando i dipendenti</p>
          }
        </div>
      </div>

      <div>
        <h1>Gestionale</h1>
      </div>
    </>
  )
}

function EmployeeCard({ dati }) {
  console.log(dati);

  return (
    <article className="w-full max-w-sm rounded-xl border border-gray-200 bg-white p-5 shadow-sm transition-shadow hover:shadow-md">
      <h3 className="text-lg font-semibold text-gray-900">
        {dati.nome} {dati.cognome}
      </h3>
      <p className="mt-2 text-sm text-gray-600">{dati.email}</p>
    </article>
  )
}

export default App
