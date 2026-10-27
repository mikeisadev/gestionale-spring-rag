import { useEffect, useState } from 'react';
import './App.css'
import axios from "axios";

function App() {
  //let dipendenti = null;
  const [dipendenti, aggiornaDipendenti] = useState(null);

  useEffect(() => {
    axios.get("http://localhost:8081/api/dipendenti")
      .then(response => {
        aggiornaDipendenti(response.data)
        console.log(response.data)
      })
  }, []);

  return (
    <>
      <div>
        <h1>Gestionale</h1>

        <h2>Dipendenti</h2>

        <div>
          {
            dipendenti ?
            <p>Stiamo caricando i dipendenti</p>
            :
            dipendenti?.map(dipendente => (
              <div>{dipendente.nome}</div>
            ))
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
