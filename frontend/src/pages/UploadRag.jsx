import { useCallback, useEffect, useRef, useState } from 'react';
import { API_BASE_URL } from '../config';
import IndexingLogModal from '../components/IndexingLogModal';
import WikiPanel from '../components/WikiPanel';

const DOCUMENTS_API_URL = `${API_BASE_URL}/api/documents`;
const MAX_FILE_SIZE_MB = 100; // deve corrispondere a spring.servlet.multipart.max-file-size
const POLLING_INTERVAL_MS = 2000; // ogni quanto chiediamo lo stato al backend
const RETRY_INTERVAL_MS = 5000;   // se il backend non risponde, riproviamo ogni 5 secondi
const BACKEND_DOWN_MESSAGE =
  `Il backend non risponde (${API_BASE_URL}): avvia Spring Boot. La pagina riprova da sola ogni 5 secondi.`;

const ACTIVE_STATUSES = ['IN_CODA', 'IN_ELABORAZIONE'];

// Lezione 10: formati accettati (stessa lista di DocumentReaderService nel backend)
const SUPPORTED_EXTENSIONS = ['pdf', 'docx', 'doc', 'odt', 'rtf', 'pptx', 'odp', 'xlsx', 'ods', 'html', 'htm', 'md', 'txt'];
const ACCEPT_ATTRIBUTE = SUPPORTED_EXTENSIONS.map(extension => `.${extension}`).join(',');

function extensionOf(filename) {
  const dot = filename.lastIndexOf('.');
  return dot < 0 ? '' : filename.slice(dot + 1).toLowerCase();
}

// Un'icona (e un colore) per ogni famiglia di formati
function fileIcon(filename) {
  switch (extensionOf(filename)) {
    case 'pdf': return 'bi-file-earmark-pdf text-red-500';
    case 'docx': case 'doc': case 'odt': case 'rtf': return 'bi-file-earmark-word text-blue-600';
    case 'pptx': case 'odp': return 'bi-file-earmark-ppt text-orange-500';
    case 'xlsx': case 'ods': return 'bi-file-earmark-excel text-emerald-600';
    case 'html': case 'htm': return 'bi-file-earmark-code text-violet-600';
    default: return 'bi-file-earmark-text text-slate-500';
  }
}

const STATUS_STYLES = {
  IN_CODA: { label: 'In coda', className: 'bg-slate-100 text-slate-600', icon: 'bi-hourglass' },
  IN_ELABORAZIONE: { label: 'In elaborazione', className: 'bg-indigo-50 text-indigo-700', icon: 'bi-arrow-repeat' },
  COMPLETATO: { label: 'Completato', className: 'bg-emerald-50 text-emerald-700', icon: 'bi-check-circle-fill' },
  ERRORE: { label: 'Errore', className: 'bg-red-50 text-red-700', icon: 'bi-x-circle-fill' },
  // Lezione 10: versione vecchia, rimpiazzata da una più recente
  SOSTITUITO: { label: 'Sostituito', className: 'bg-slate-100 text-slate-500', icon: 'bi-arrow-left-right' },
};

function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatDateTime(isoDate) {
  return new Date(isoDate).toLocaleString('it-IT', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' });
}

function formatDuration(ms) {
  const totalSeconds = Math.max(0, Math.round(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes} min ${seconds} s` : `${seconds} s`;
}

// 1. Invia il file: il backend lo salva e risponde SUBITO (202) con il job di indicizzazione
async function uploadDocument(file) {
  const formData = new FormData();
  formData.append('file', file); // "file" = nome usato da @RequestPart("file")

  let response;
  try {
    response = await fetch(DOCUMENTS_API_URL, {
      method: 'POST',
      body: formData,
    });
  } catch {
    // fetch fallisce SENZA risposta solo se il server non è raggiungibile (o per CORS)
    throw new Error(BACKEND_DOWN_MESSAGE);
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 413) {
      throw new Error(`Il file supera il limite di ${MAX_FILE_SIZE_MB} MB.`);
    }
    // Lezione 10: 409 = file già caricato (stesso contenuto) o caricamento già in corso
    if (response.status === 409) {
      throw new Error(data?.message || 'Questo file è già stato caricato.');
    }
    throw new Error(data?.message || 'Errore durante il caricamento');
  }

  return data; // { id, filename, status: "IN_CODA", ... }
}

// 2. Chiede al backend lo stato di tutti i job (polling)
async function fetchJobs() {
  const response = await fetch(`${DOCUMENTS_API_URL}/jobs`);
  if (!response.ok) throw new Error(`Errore del server (${response.status})`);
  return response.json();
}

// 3. Lezione 7: storico permanente dei file caricati (dal database)
async function fetchHistory() {
  const response = await fetch(`${DOCUMENTS_API_URL}/history`);
  if (!response.ok) throw new Error(`Errore del server (${response.status})`);
  return response.json();
}

// 4. Lezione 10: chi è l'utente? (serve per mostrare il pulsante "Elimina" solo agli admin)
async function fetchCurrentUser() {
  const response = await fetch(`${API_BASE_URL}/api/me`);
  if (!response.ok) throw new Error(`Errore del server (${response.status})`);
  return response.json(); // { userId, department, admin }
}

// 5. Lezione 10: elimina un documento con tutte le sue versioni (solo admin)
async function deleteDocument(documentId) {
  const response = await fetch(`${DOCUMENTS_API_URL}/${documentId}`, { method: 'DELETE' });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || `Errore del server (${response.status})`);
  return data; // { message }
}

function VersionBadge({ version }) {
  if (!version || version < 2) return null;
  return (
    <span className="rounded-md bg-indigo-50 px-1.5 py-0.5 text-[11px] font-semibold text-indigo-700" title={`Versione ${version} del documento`}>
      v{version}
    </span>
  );
}

function ShowLogButton({ onClick }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700"
    >
      <i className="bi bi-journal-text" aria-hidden="true" />
      Vedi log
    </button>
  );
}

function JobCard({ job, now, onShowLog }) {
  const style = STATUS_STYLES[job.status] ?? STATUS_STYLES.IN_CODA;
  const isActive = ACTIVE_STATUSES.includes(job.status);
  const progress = job.chunks > 0 ? Math.round((job.chunksIndexed / job.chunks) * 100) : 0;

  // "now" = ora dell'ultimo aggiornamento ricevuto dal backend
  const elapsedMs = job.startedAt ? (job.finishedAt ?? now) - job.startedAt : 0;

  // Stima del tempo residuo: tempo medio per chunk finora × chunk mancanti
  const remainingMs = job.status === 'IN_ELABORAZIONE' && job.chunksIndexed > 0
    ? (elapsedMs / job.chunksIndexed) * (job.chunks - job.chunksIndexed)
    : null;

  return (
    <li className="rounded-xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-center gap-3">
        <i className={`bi ${fileIcon(job.filename)} text-2xl`} aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">
            <span className="truncate" title={job.filename}>{job.filename}</span>
            <VersionBadge version={job.version} />
          </p>
          <p className="text-xs text-slate-500">
            {job.phase}
            {job.pages > 0 && ` · ${job.pages} pagine`}
            {job.chunks > 0 && ` · ${job.chunks} chunk`}
          </p>
        </div>
        <span className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${style.className}`}>
          <i className={`bi ${style.icon} ${job.status === 'IN_ELABORAZIONE' ? 'animate-spin' : ''}`} aria-hidden="true" />
          {style.label}
        </span>
        <ShowLogButton onClick={() => onShowLog(job)} />
      </div>

      {(isActive || job.status === 'COMPLETATO') && job.chunks > 0 && (
        <div className="mt-3">
          <div
            className="h-2 overflow-hidden rounded-full bg-slate-100"
            role="progressbar"
            aria-valuenow={progress}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={`Avanzamento indicizzazione ${job.filename}`}
          >
            <div
              className={`h-full rounded-full transition-[width] duration-500 ${
                job.status === 'COMPLETATO' ? 'bg-emerald-500' : 'bg-indigo-500'
              }`}
              style={{ width: `${progress}%` }}
            />
          </div>
          <p className="mt-1.5 text-xs text-slate-500">
            {job.chunksIndexed}/{job.chunks} chunk indicizzati ({progress}%)
          </p>
        </div>
      )}

      <p className={`mt-3 text-sm ${job.status === 'ERRORE' ? 'text-red-700' : 'text-slate-600'}`}>
        {job.message}
      </p>

      <p className="mt-1 text-xs text-slate-400">
        Caricato alle {new Date(job.createdAt).toLocaleTimeString('it-IT')}
        {elapsedMs > 0 && ` · ${isActive ? 'in corso da' : 'durata'} ${formatDuration(elapsedMs)}`}
        {remainingMs !== null && ` · circa ${formatDuration(remainingMs)} rimanenti`}
      </p>
    </li>
  );
}

function HistoryRow({ item, onShowLog, canDelete, isDeleting, onDelete }) {
  const style = STATUS_STYLES[item.status] ?? STATUS_STYLES.COMPLETATO;
  const durationMs = item.startedAt && item.finishedAt
    ? new Date(item.finishedAt) - new Date(item.startedAt)
    : null;

  return (
    <li className="flex flex-wrap items-center gap-3 py-3">
      <i className={`bi ${fileIcon(item.filename)} text-xl ${item.status === 'SOSTITUITO' ? 'opacity-50' : ''}`} aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <p className={`flex items-center gap-2 text-sm font-semibold ${item.status === 'SOSTITUITO' ? 'text-slate-500' : 'text-slate-900'}`}>
          <span className="truncate" title={item.filename}>{item.filename}</span>
          <VersionBadge version={item.version} />
        </p>
        <p className="text-xs text-slate-500">
          {formatDateTime(item.uploadedAt)}
          {item.sizeBytes != null && ` · ${formatFileSize(item.sizeBytes)}`}
          {item.pages > 0 && ` · ${item.pages} pagine`}
          {item.chunks > 0 && ` · ${item.chunks} chunk`}
          {durationMs !== null && ` · ${formatDuration(durationMs)}`}
        </p>
        {item.status === 'ERRORE' && (
          <p className="mt-0.5 truncate text-xs text-red-600" title={item.message}>{item.message}</p>
        )}
        {item.status === 'SOSTITUITO' && (
          <p className="mt-0.5 truncate text-xs text-slate-500">{item.message}</p>
        )}
      </div>
      <span className={`flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold ${style.className}`}>
        <i className={`bi ${style.icon}`} aria-hidden="true" />
        {style.label}
      </span>
      <ShowLogButton onClick={() => onShowLog(item)} />
      {canDelete && (
        <button
          type="button"
          onClick={() => onDelete(item)}
          disabled={isDeleting}
          className="flex shrink-0 items-center gap-1.5 rounded-lg border border-slate-200 px-2.5 py-1.5 text-xs font-semibold text-slate-600 transition hover:border-red-300 hover:bg-red-50 hover:text-red-700 disabled:opacity-50"
        >
          <i className={`bi ${isDeleting ? 'bi-hourglass-split' : 'bi-trash'}`} aria-hidden="true" />
          {isDeleting ? 'Eliminazione...' : 'Elimina'}
        </button>
      )}
    </li>
  );
}

function UploadRag() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [pollingError, setPollingError] = useState('');
  const [jobs, setJobs] = useState([]);
  const [history, setHistory] = useState([]);
  const [logTarget, setLogTarget] = useState(null); // { id, filename, live } del log aperto
  const [isAdmin, setIsAdmin] = useState(false);     // lezione 10
  const [deletingId, setDeletingId] = useState(null); // documentId in eliminazione
  const [notice, setNotice] = useState('');           // messaggio di conferma (es. documento eliminato)
  const [now, setNow] = useState(0);
  const fileInputRef = useRef(null);

  // Aggiorniamo insieme i job in corso (memoria) e lo storico (database):
  // quando un job finisce, sparisce da "in corso" e compare nello storico.
  const refreshJobs = useCallback(() =>
    Promise.all([fetchJobs(), fetchHistory()])
      .then(([jobsData, historyData]) => {
        setJobs(jobsData);
        setHistory(historyData);
        setNow(Date.now());
        setPollingError('');
      })
      .catch(() => setPollingError(BACKEND_DOWN_MESSAGE)),
  []);

  const closeLog = useCallback(() => setLogTarget(null), []);

  // All'apertura della pagina recuperiamo i job dal backend:
  // l'indicizzazione continua sul server anche se l'utente cambia pagina.
  useEffect(() => {
    refreshJobs();
  }, [refreshJobs]);

  // Lezione 10: l'utente è un amministratore? (se il backend non risponde, resta false)
  // Lo richiediamo anche quando il backend torna raggiungibile (pollingError torna vuoto).
  const backendReachable = pollingError === '';
  useEffect(() => {
    if (!backendReachable) return;
    fetchCurrentUser().then(user => setIsAdmin(user.admin)).catch(() => setIsAdmin(false));
  }, [backendReachable]);

  // Polling: finché c'è almeno un job attivo, chiediamo lo stato ogni 2 secondi
  const activeJobs = jobs.filter(job => ACTIVE_STATUSES.includes(job.status));
  const hasActiveJobs = activeJobs.length > 0;
  const finishedHistory = history.filter(item => !ACTIVE_STATUSES.includes(item.status));

  // Se il backend non risponde riproviamo comunque, più lentamente:
  // così la pagina si "riprende" da sola appena Spring Boot è di nuovo attivo.
  const backendDown = pollingError !== '';

  useEffect(() => {
    if (!hasActiveJobs && !backendDown) return undefined;

    const intervalId = setInterval(refreshJobs, backendDown ? RETRY_INTERVAL_MS : POLLING_INTERVAL_MS);
    return () => clearInterval(intervalId); // pulizia quando il polling non serve più
  }, [hasActiveJobs, backendDown, refreshJobs]);

  function selectFile(file) {
    setUploadError('');

    if (!file) return;

    if (!SUPPORTED_EXTENSIONS.includes(extensionOf(file.name))) {
      setSelectedFile(null);
      setUploadError(`Formato non supportato. Puoi caricare: ${ACCEPT_ATTRIBUTE.replaceAll(',', ', ')}.`);
      return;
    }

    if (file.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
      setSelectedFile(null);
      setUploadError(`Il file supera il limite di ${MAX_FILE_SIZE_MB} MB.`);
      return;
    }

    setSelectedFile(file);
  }

  function resetSelection() {
    setSelectedFile(null);
    if (fileInputRef.current) fileInputRef.current.value = '';
  }

  async function handleUpload() {
    if (!selectedFile || isUploading) return;

    setIsUploading(true);
    setUploadError('');
    setNotice('');

    try {
      const job = await uploadDocument(selectedFile);
      // Mostriamo subito il nuovo job: il polling penserà ad aggiornarlo
      setJobs(current => [job, ...current.filter(existing => existing.id !== job.id)]);
      setNow(Date.now());
      resetSelection();
    } catch (error) {
      setUploadError(error.message || 'Errore durante il caricamento');
    } finally {
      setIsUploading(false);
    }
  }

  // Lezione 10: elimina il documento (tutte le versioni) dopo una conferma
  async function handleDelete(item) {
    const confirmed = window.confirm(
      `Eliminare "${item.filename}" dalla base di conoscenza?\n\n` +
      'Verranno eliminati il file, tutte le sue versioni, i log e i chunk in pgvector. ' +
      "L'assistente non potrà più usarlo per rispondere."
    );
    if (!confirmed) return;

    setDeletingId(item.documentId);
    setNotice('');
    setUploadError('');
    try {
      const result = await deleteDocument(item.documentId);
      setNotice(result.message);
      await refreshJobs();
    } catch (error) {
      setUploadError(error.message);
    } finally {
      setDeletingId(null);
    }
  }

  function handleDrop(event) {
    event.preventDefault();
    setIsDragging(false);
    if (!isUploading) selectFile(event.dataTransfer.files?.[0]);
  }

  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 lg:p-10">
      <div className="mx-auto max-w-4xl">
        <header className="mb-6 border-b border-slate-200 pb-6">
          <div className="mb-2 flex items-center gap-3">
            <p className="text-sm font-semibold uppercase tracking-[0.16em] text-indigo-600">
              Gestionale
            </p>
            <span
              className="rounded-full bg-amber-50 px-2.5 py-0.5 text-xs font-semibold text-amber-700"
              title="La protezione con login arriverà quando aggiungeremo l'autenticazione"
            >
              Solo Admin
            </span>
          </div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
            Upload file per sistema RAG
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-600 sm:text-base">
            Carica un documento (PDF, Word, PowerPoint, Excel, Markdown, testo...): il backend lo salverà e lo indicizzerà in background nella base di conoscenza dell'assistente.
            Se carichi un file con lo stesso nome di uno già presente, diventa la sua nuova versione.
          </p>
        </header>

        <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
          <label
            htmlFor="document-file"
            onDragOver={event => { event.preventDefault(); setIsDragging(true); }}
            onDragLeave={() => setIsDragging(false)}
            onDrop={handleDrop}
            className={`flex cursor-pointer flex-col items-center justify-center rounded-2xl border-2 border-dashed px-6 py-12 text-center transition ${
              isDragging
                ? 'border-indigo-500 bg-indigo-50'
                : 'border-slate-200 bg-slate-50 hover:border-indigo-300 hover:bg-indigo-50/40'
            } ${isUploading ? 'pointer-events-none opacity-60' : ''}`}
          >
            <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
              <i className="bi bi-file-earmark-arrow-up text-2xl" aria-hidden="true" />
            </span>
            <span className="mt-4 text-base font-semibold text-slate-900">
              Trascina qui il documento oppure clicca per sceglierlo
            </span>
            <span className="mt-1 text-sm text-slate-500">
              PDF, DOCX, PPTX, XLSX, ODT, RTF, HTML, MD, TXT · massimo {MAX_FILE_SIZE_MB} MB
            </span>
            <input
              ref={fileInputRef}
              id="document-file"
              type="file"
              accept={ACCEPT_ATTRIBUTE}
              className="sr-only"
              disabled={isUploading}
              onChange={event => selectFile(event.target.files?.[0])}
            />
          </label>

          {selectedFile && (
            <div className="mt-4 flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 px-4 py-3">
              <i className={`bi ${fileIcon(selectedFile.name)} text-2xl`} aria-hidden="true" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-slate-900">{selectedFile.name}</p>
                <p className="text-xs text-slate-500">{formatFileSize(selectedFile.size)}</p>
              </div>
              <button
                type="button"
                onClick={resetSelection}
                disabled={isUploading}
                className="rounded-xl px-3 py-2 text-sm font-semibold text-slate-600 transition hover:bg-slate-100 disabled:opacity-50"
              >
                Annulla
              </button>
              <button
                type="button"
                onClick={handleUpload}
                disabled={isUploading}
                className="flex items-center gap-2 rounded-xl bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <i className={`bi ${isUploading ? 'bi-hourglass-split' : 'bi-cloud-arrow-up'}`} aria-hidden="true" />
                {isUploading ? 'Invio del file...' : 'Carica documento'}
              </button>
            </div>
          )}

          {uploadError && (
            <p role="alert" className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              {uploadError}
            </p>
          )}

          {notice && (
            <p role="status" className="mt-4 rounded-xl bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
              {notice}
            </p>
          )}
        </section>

        {/* ===== CARICAMENTI IN CORSO ===== */}
        {hasActiveJobs && (
          <section
            aria-live="polite"
            className="mt-6 rounded-2xl border border-indigo-100 bg-white p-4 shadow-sm sm:p-6"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="text-base font-semibold text-slate-900">Caricamenti in corso</h2>
              <p className="text-xs text-slate-500">
                Puoi cambiare pagina: l'indicizzazione continua sul server.
              </p>
            </div>

            <ul className="mt-4 space-y-3">
              {activeJobs.map(job => (
                <JobCard
                  key={job.id}
                  job={job}
                  now={now}
                  onShowLog={j => setLogTarget({ id: j.id, filename: j.filename, live: true })}
                />
              ))}
            </ul>
          </section>
        )}

        {/* Avviso sempre visibile se il backend non risponde */}
        {backendDown && (
          <div role="alert" className="mt-6 flex flex-wrap items-center gap-3 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
            <i className="bi bi-plug text-lg" aria-hidden="true" />
            <span className="min-w-0 flex-1">{pollingError}</span>
            <button
              type="button"
              onClick={refreshJobs}
              className="rounded-lg border border-amber-300 bg-white px-3 py-1.5 text-xs font-semibold text-amber-800 transition hover:bg-amber-100"
            >
              Riprova ora
            </button>
          </div>
        )}

        {/* ===== STORICO DEI FILE CARICATI (lezione 7) ===== */}
        <section className="mt-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h2 className="text-base font-semibold text-slate-900">Storico dei file caricati</h2>
            <p className="text-xs text-slate-500">{backendDown ? '–' : `${finishedHistory.length} caricamenti`}</p>
          </div>

          {!isAdmin && !backendDown && finishedHistory.length > 0 && (
            <p className="mt-1 text-xs text-slate-400">
              Per eliminare un documento serve un amministratore (in sviluppo: app.demo-user.admin=true).
            </p>
          )}

          {finishedHistory.length === 0 ? (
            <p className="py-6 text-center text-sm text-slate-400">
              {backendDown ? 'Storico non disponibile: il backend non risponde.' : 'Nessun file caricato finora.'}
            </p>
          ) : (
            <ul className="mt-2 divide-y divide-slate-100">
              {finishedHistory.map(item => (
                <HistoryRow
                  key={item.id}
                  item={item}
                  onShowLog={i => setLogTarget({ id: i.id, filename: i.filename, live: false })}
                  // Si elimina il DOCUMENTO (tutte le versioni): il pulsante è sulla versione attiva
                  canDelete={isAdmin && item.status === 'COMPLETATO'}
                  isDeleting={deletingId === item.documentId}
                  onDelete={handleDelete}
                />
              ))}
            </ul>
          )}
        </section>

        {/* ===== LLM WIKI (lezione 11) ===== */}
        {!backendDown && <WikiPanel apiUrl={DOCUMENTS_API_URL} />}

        {logTarget && (
          <IndexingLogModal
            apiUrl={DOCUMENTS_API_URL}
            documentId={logTarget.id}
            filename={logTarget.filename}
            live={logTarget.live && activeJobs.some(job => job.id === logTarget.id)}
            onClose={closeLog}
          />
        )}
      </div>
    </main>
  );
}

export default UploadRag;
