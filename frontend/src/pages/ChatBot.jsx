import { useEffect, useRef, useState } from 'react';
import Markdown from 'react-markdown'


const CHAT_API_URL = 'http://localhost:8081/api/chat';

// Invia il messaggio e legge la risposta in streaming (Server-Sent Events).
// Ogni evento SSE è separato da una riga vuota e contiene una o più righe "data:".
// Esempio di ciò che arriva dal backend:
//   data:La\n\n
//   data: configur\n\n      <- lo spazio fa parte del token: NON va tolto
//   data::\ndata:\n\n        <- più righe "data:" = il token contiene degli a capo
async function streamChat(message, history, onToken) {
  const response = await fetch(CHAT_API_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify({ message, history }),
  });

  if (!response.ok) {
    const data = await response.json().catch(() => null);
    throw new Error(
      data?.message ||
      (response.status === 400 ? 'Il messaggio non può essere vuoto.' : `Errore del server (${response.status}).`)
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

function ChatBot() {
  const [messages, setMessages] = useState([]);
  const [messageInput, setMessageInput] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [requestError, setRequestError] = useState('');
  const messagesEndRef = useRef(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isSending]);

  async function sendMessage(event) {
    event.preventDefault();
    const message = messageInput.trim();

    if (!message || isSending) return;

    // Lo storico viene già inviato, ma il backend per ora non lo usa
    const history = messages.map(({ role, content }) => ({ role, content }));

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
      await streamChat(message, history, token => {
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
      <div className="mx-auto flex min-h-[calc(100vh-2rem)] max-w-4xl flex-col sm:min-h-[calc(100vh-3rem)] lg:min-h-[calc(100vh-5rem)]">
        <header className="mb-6 border-b border-slate-200 pb-6">
          <p className="mb-2 text-sm font-semibold uppercase tracking-[0.16em] text-indigo-600">
            Gestionale
          </p>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
            Assistente AI
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-600 sm:text-base">
            Fai una domanda sui documenti caricati: l'assistente risponde usando la base di conoscenza del gestionale.
          </p>
        </header>

        <section
          aria-label="Conversazione con l'assistente"
          className="flex min-h-[28rem] flex-1 flex-col overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm"
        >
          <div
            className="flex-1 space-y-5 overflow-y-auto p-4 sm:p-6"
            aria-live="polite"
            aria-relevant="additions"
          >
            {messages.length === 0 && (
              <div className="flex h-full min-h-64 flex-col items-center justify-center px-4 text-center">
                <span className="flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-50 text-indigo-600">
                  <i className="bi bi-chat-dots text-2xl" aria-hidden="true" />
                </span>
                <h2 className="mt-4 text-base font-semibold text-slate-900">
                  Come posso aiutarti?
                </h2>
                <p className="mt-1 max-w-sm text-sm leading-6 text-slate-500">
                  Scrivi un messaggio qui sotto per iniziare la conversazione.
                </p>
              </div>
            )}

            {messages.map((message, index) => {
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
                    <Markdown>{message.content}</Markdown>
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
                disabled={isSending}
                className="max-h-40 min-h-12 flex-1 resize-y rounded-xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-900 outline-none transition placeholder:text-slate-400 focus:border-indigo-500 focus:bg-white focus:ring-4 focus:ring-indigo-100 disabled:opacity-60"
              />
              <button
                type="submit"
                disabled={isSending || !messageInput.trim()}
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
    </main>
  );
}

export default ChatBot;
