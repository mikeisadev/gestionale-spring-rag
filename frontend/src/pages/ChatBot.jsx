import { Link } from 'react-router';

function ChatBot() {
  return (
    <main className="min-h-screen bg-slate-50 p-4 sm:p-6 lg:p-10">
      <div className="mx-auto max-w-4xl">
        <header className="mb-8 border-b border-slate-200 pb-7">
          <Link
            to="/"
            className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-indigo-600 hover:text-indigo-700"
          >
            <i className="bi bi-arrow-left" aria-hidden="true" />
            Dashboard
          </Link>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950 sm:text-4xl">
            Chat
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-600 sm:text-base">
            Assistente conversazionale del gestionale.
          </p>
        </header>

        <section className="rounded-2xl border border-slate-200 bg-white px-5 py-12 text-center shadow-sm">
          <i className="bi bi-chat-dots mb-3 text-3xl text-indigo-600" aria-hidden="true" />
          <h2 className="text-lg font-semibold text-slate-900">ChatBot</h2>
          <p className="mt-2 text-sm text-slate-600">
            La pagina è pronta per integrare la funzionalità di chat.
          </p>
        </section>
      </div>
    </main>
  );
}

export default ChatBot;
