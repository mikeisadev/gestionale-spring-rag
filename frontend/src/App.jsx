import { useEffect, useState } from 'react';
import axios from "axios";

function App() {
  //let dipendenti = null;
  const [dipendenti, aggiornaDipendenti] = useState(null);
  
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

  return (
    <>
      <div>
        <h1 className='titolo'>Gestionale</h1>

        <h2>Dipendenti</h2>

        <div>
          {
            dipendenti ?
            dipendenti.map(dipendente => <EmployeeCard dipendente={dipendente} />)
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

function EmployeeCard(dipendente) {
  return (
    <div>{dipendente.nome} {dipendente.cognome}</div>
  )
}

export default App
