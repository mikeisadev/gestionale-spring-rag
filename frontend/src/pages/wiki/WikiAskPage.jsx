import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { askWiki } from '../../api/wikiApi';
import WikiMarkdown from '../../components/WikiMarkdown';

const MAX_QUESTION_LENGTH = 1000; // come il backend
const STORAGE_KEY = 'wiki-ask-last'; // l'ultima risposta resta se apri una pagina e torni indietro

const EXAMPLES = [
  'Quali sono le difese principali contro il ransomware per una piccola azienda?',
  "Che differenza c'è tra minaccia, vulnerabilità e rischio?",
  'Cosa prevede la NIS2 per le aziende italiane?',
];

// Il modello cita le pagine così: [concepts/Ransomware.md]. Le trasformiamo in link cliccabili.
function linkCitations(text) {
  return text.replace(/\[([^\][]+?\.md)\](?!\()/g,
    (match, path) => `[${path}](/wiki/page?path=${encodeURIComponent(path)})`);
}

// Lezione 12: "Chiedi alla wiki". Il modello NON usa il database vettoriale:
// legge index.md, cerca con searchWiki e apre le pagine con readWikiPage.
function WikiAskPage() {
  const [question, setQuestion] = useState('');
  const [result, setResult] = useState(() => JSON.parse(sessionStorage.getItem(STORAGE_KEY) ?? 'null'));
  const [error, setError] = useState('');
  const [isAsking, setIsAsking] = useState(false);
  const [elapsed, setElapsed] = useState(0);

  // Un contatore dei secondi: la risposta può richiedere diversi giri di strumenti
  useEffect(() => {
    if (!isAsking) return undefined;
    const start = Date.now();
    const timer = setInterval(() => setElapsed(Math.round((Date.now() - start) / 1000)), 500);
    return () => clearInterval(timer);
  }, [isAsking]);

  async function handleSubmit(event) {
    event.preventDefault();
    const text = question.trim();
    if (!text || isAsking) return;

    setIsAsking(true);
    setElapsed(0);
    setError('');
    try {
      const data = await askWiki(text);
      const saved = { question: text, ...data };
      setResult(saved);
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(saved));
      setQuestion('');
    } catch (e) {
      setError(e.message);
    } finally {
      setIsAsking(false);
    }
  }

  return (
    <div className="space-y-6">
      <form onSubmit={handleSubmit} className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-6">
        <label htmlFor="wiki-question" className="text-sm font-semibold text-slate-900">Fai una domanda alla wiki</label>
        <textarea
          id="wiki-question"
          rows={3}
          value={question}
          maxLength={MAX_QUESTION_LENGTH}
          onChange={event => setQuestion(event.target.value)}
          onKeyDown={event => {
            if (event.key === 'Enter' && !event.shiftKey) handleSubmit(event);
          }}
          placeholder="Ad esempio: quali sono le difese principali contro il ransomware?"
          className="mt-2 w-full resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm outline-none transition focus:border-violet-400 focus:bg-white focus:ring-4 focus:ring-violet-100"
        />
        <div className="mt-2 flex flex-wrap items-center justify-between gap-3">
          <p className="text-xs text-slate-400">
            {question.length}/{MAX_QUESTION_LENGTH} · Il modello legge l'indice, cerca e apre le pagine: niente database vettoriale.
          </p>
          <button
            type="submit"
            disabled={isAsking || !question.trim()}
            className="flex items-center gap-2 rounded-xl bg-violet-600 px-4 py-2 text-sm font-semibold text-white shadow-sm transition hover:bg-violet-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <i className={`bi ${isAsking ? 'bi-hourglass-split' : 'bi-send'}`} aria-hidden="true" />
            {isAsking ? `Consulto la wiki... ${elapsed} s` : 'Chiedi'}
          </button>
        </div>
        {!result && !isAsking && (
          <div className="mt-4 flex flex-wrap gap-2">
            {EXAMPLES.map(example => (
              <button key={example} type="button" onClick={() => setQuestion(example)} className="rounded-full border border-slate-200 px-3 py-1 text-xs text-slate-600 hover:border-violet-300 hover:bg-violet-50">
                {example}
              </button>
            ))}
          </div>
        )}
      </form>

      {error && <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">{error}</p>}

      {result && (
        <section aria-live="polite" className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm sm:p-8">
          <p className="mb-4 text-sm font-semibold text-slate-900">
            <i className="bi bi-person-circle mr-2 text-slate-400" aria-hidden="true" />{result.question}
          </p>
          <WikiMarkdown markdown={linkCitations(result.answer ?? '')} isAnswer />
          <div className="mt-6 border-t border-slate-100 pt-4">
            <p className="text-xs font-semibold text-slate-500">
              Pagine lette dal modello ({result.pagesRead.length}) · {(result.millis / 1000).toFixed(1)} s
            </p>
            <div className="mt-2 flex flex-wrap gap-1.5">
              {result.pagesRead.map(page => (
                <Link key={page.path} to={`/wiki/page?path=${encodeURIComponent(page.path)}`} title={page.path}
                  className="rounded-full bg-violet-50 px-2.5 py-1 text-xs font-medium text-violet-700 hover:bg-violet-100">
                  {page.title}
                </Link>
              ))}
            </div>
            <p className="mt-4 text-xs text-slate-400">
              Per confrontare i due approcci fai la stessa domanda nella <Link to="/chat" className="underline">Chat</Link>, che usa il RAG (lezione 11).
            </p>
          </div>
        </section>
      )}
    </div>
  );
}

export default WikiAskPage;
