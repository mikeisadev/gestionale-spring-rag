import { useEffect, useState, useRef } from 'react';
import axios from "axios";
import EmployeeCard from './components/EmployeeCard';
import EmployeeForm from './components/EmployeeForm';
import EmployeeUpdateModal from './components/EmployeeUpdateModal';

function App() {
  const [dipendenti, aggiornaDipendenti] = useState(null);
  const [formDipendente, setFormDipendente] = useState({
    nome: "",
    cognome: "",
    email: ""
  });

  const [mostraPopupAggiorna, modificaPopupAggiorna] = useState(false);

  // Riferimento al DOM virtuale
  const formRef = useRef(null);

  useEffect(() => {
    console.log(formRef.current);

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

        formRef.current.reset();
        
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

  function handleDelete(id) {
    console.log("Stai eliminando il dipendente", id)

    if (!confirm("Sei sicuro di voler eliminare il dipendente"))
    {
      return;
    }

    axios.delete(`http://localhost:8081/api/dipendenti/${id}`)
      .then(response => getEmployees());
  }

  function handleUpdate(dati) {
    console.log("Stai aggiornando il dipendente", dati)

    modificaPopupAggiorna(true)
  }

  return (
    <>
      <div>
        <h1 className='heading'>Gestionale <i class="bi bi-8-square-fill"></i></h1>

        <h2>Dipendenti</h2>

        <div>
          <EmployeeForm 
            formRef={formRef}
            handleSubmit={handleSubmit}
            handleInput={handleInput}
          />
        </div>

        <div>
          {
            dipendenti ?
            dipendenti.map((dipendente, i) => <EmployeeCard 
                                                  key={i} 
                                                  dati={dipendente}
                                                  onAggiorna={handleUpdate}
                                                  onElimina={() => handleDelete(dipendente.id)}
                                                  />)
            :
            <p>Stiamo caricando i dipendenti</p>
          }
        </div>
      </div>

      {mostraPopupAggiorna ? <EmployeeUpdateModal onClose={() => modificaPopupAggiorna(false)}/> : null}
    </>
  )
}

export default App
