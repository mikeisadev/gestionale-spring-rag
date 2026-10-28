import { useEffect, useState } from 'react';
import './App.css'
import axios from "axios";

function App() {
  //let dipendenti = null;
  const [dipendenti, aggiornaDipendenti] = useState(null);
  const [nomi, impostaNomi] = useState(["Michele Mincone", "Dario Amodei", "Qualsiasi Nome", "Ciao Hello"]);

  useEffect(() => {

    setTimeout(
      () => {
        axios.get("http://localhost:8081/api/dipendenti")
          .then(response => {
            aggiornaDipendenti(response.data)
            console.log(response.data)
          })
      }, 5000
    );

  }, []);

  function ritornaJSX(nome, cognome) {
    return (
      <p>Ciao, {nome} {cognome}!</p>
    )
  }

  return (
    <>
      <div>
        {ritornaJSX("Michele", "Mincone")}

        {
          nomi.map(n => ritornaJSX(n))
        }

        <h1>Gestionale</h1>

        <h2>Dipendenti</h2>

        <div>
          {
            dipendenti ?
            dipendenti.map(dipendente => {
              return (
                <div>{dipendente.nome} {dipendente.cognome}</div>
              )
            })
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

export default App
