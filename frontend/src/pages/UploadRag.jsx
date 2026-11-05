import { useCallback, useEffect, useRef, useState } from 'react';

const DOCUMENTS_API_URL = 'http://localhost:8081/api/documents';
const MAX_FILE_SIZE_MB = 100; // deve corrispondere a spring.servlet.multipart.max-file-size
const POLLING_INTERVAL_MS = 2000; // ogni quanto chiediamo lo stato al backend

const ACTIVE_STATUSES = ['IN_CODA', 'IN_ELABORAZIONE'];

const STATUS_STYLES = {
  IN_CODA: { label: 'In coda', className: 'bg-slate-100 text-slate-600', icon: 'bi-hourglass' },
  IN_ELABORAZIONE: { label: 'In elaborazione', className: 'bg-indigo-50 text-indigo-700', icon: 'bi-arrow-repeat' },
  COMPLETATO: { label: 'Completato', className: 'bg-emerald-50 text-emerald-700', icon: 'bi-check-circle-fill' },
  ERRORE: { label: 'Errore', className: 'bg-red-50 text-red-700', icon: 'bi-x-circle-fill' },
};

function formatFileSize(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatDuration(ms) {
  const totalSeconds = Math.max(0, Math.round(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return minutes > 0 ? `${minutes} min ${seconds} s` : `${seconds} s`;
}

// 1. Invia il PDF: il backend lo salva e risponde SUBITO (202) con il job di indicizzazione
async function uploadPdf(file) {
  const formData = new FormData();
  formData.append('file', file); // "file" = nome usato da @RequestPart("file")

  const response = await fetch(DOCUMENTS_API_URL, {
    method: 'POST',
    body: formData,
  });

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    if (response.status === 413) {
      throw new Error(`Il file supera il limite di ${MAX_FILE_SIZE_MB} MB.`);
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

function JobCard({ job, now }) {
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
        <i className="bi bi-file-earmark-pdf text-2xl text-red-500" aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-slate-900" title={job.filename}>{job.filename}</p>
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

function UploadRag() {
  const [selectedFile, setSelectedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isDragging, setIsDragging] = useState(false);
  const [uploadError, setUploadError] = useState('');
  const [pollingError, setPollingError] = useState('');
  const [jobs, setJobs] = useState([]);
  const [now, setNow] = useState(0);
  const fileInputRef = useRef(null);

  const refreshJobs = useCallback(() =>
    fetchJobs()
      .then(data => {
        setJobs(data);
        setNow(Date.now());
        setPollingError('');
      })
      .catch(() => setPollingError('Impossibile aggiornare lo stato: il backend è raggiungibile?')),
  []);

  // All'apertura della pagina recuperiamo i job dal backend:
  // l'indicizzazione continua sul server anche se l'utente cambia pagina.
  useEffect(() => {
    refreshJobs();
  }, [refreshJobs]);

  // Polling: finché c'è almeno un job attivo, chiediamo lo stato ogni 2 secondi
  const hasActiveJobs = jobs.some(job => ACTIVE_STATUSES.includes(job.status));

  useEffect(() => {
    if (!hasActiveJobs) return undefined;

    const intervalId = setInterval(refreshJobs, POLLING_INTERVAL_MS);
    return () => clearInterval(intervalId); // pulizia quando il polling non serve più
  }, [hasActiveJobs, refreshJobs]);

  function selectFile(file) {
    setUploadError('');

    if (!file) return;

    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      setSelectedFile(null);
      setUploadError('Puoi caricare solo file PDF.');
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

    try {
      const job = await uploadPdf(selectedFile);
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
            Carica un documento PDF: il backend lo salverà e lo indicizzerà in background nella base di conoscenza dell'assistente.
          </p>
        </header>

        <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
          <label
            htmlFor="pdf-file"
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
              Trascina qui il PDF oppure clicca per sceglierlo
            </span>
            <span className="mt-1 text-sm text-slate-500">
              Solo file .pdf, massimo {MAX_FILE_SIZE_MB} MB
            </span>
            <input
              ref={fileInputRef}
              id="pdf-file"
              type="file"
              accept="application/pdf"
              className="sr-only"
              disabled={isUploading}
              onChange={event => selectFile(event.target.files?.[0])}
            />
          </label>

          {selectedFile && (
            <div className="mt-4 flex flex-wrap items-center gap-3 rounded-xl border border-slate-200 px-4 py-3">
              <i className="bi bi-file-earmark-pdf text-2xl text-red-500" aria-hidden="true" />
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
                {isUploading ? 'Invio del file...' : 'Carica PDF'}
              </button>
            </div>
          )}

          {uploadError && (
            <p role="alert" className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
              {uploadError}
            </p>
          )}
        </section>

        {jobs.length > 0 && (
          <section
            aria-live="polite"
            className="mt-6 rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6"
          >
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <h2 className="text-base font-semibold text-slate-900">Indicizzazioni</h2>
              {hasActiveJobs && (
                <p className="text-xs text-slate-500">
                  Puoi cambiare pagina: l'indicizzazione continua sul server.
                </p>
              )}
            </div>

            {pollingError && (
              <p role="alert" className="mt-3 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-700">
                {pollingError}
              </p>
            )}

            <ul className="mt-4 space-y-3">
              {jobs.map(job => (
                <JobCard key={job.id} job={job} now={now} />
              ))}
            </ul>
          </section>
        )}
      </div>
    </main>
  );
}

export default UploadRag;
