import { useState } from 'react';
import { NavLink, Route, Routes } from 'react-router';
import ChatBot from './pages/ChatBot';
import Dashboard from './pages/Dashboard';
import Dipendente from './pages/Dipendente';

function App() {
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  const navigationItems = [
    { to: '/', label: 'Dashboard', icon: 'bi-grid-1x2' },
    { to: '/dipendenti', label: 'Dipendenti', icon: 'bi-people' },
    { to: '/chat', label: 'Chat', icon: 'bi-chat-dots' },
  ];

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside
        className={`sticky top-0 flex h-screen shrink-0 flex-col border-r border-slate-200 bg-white transition-[width] duration-200 ${
          sidebarCollapsed ? 'w-20' : 'w-64'
        }`}
      >
        <div className={`flex h-20 items-center border-b border-slate-100 px-4 ${
          sidebarCollapsed ? 'justify-center' : 'justify-between'
        }`}>
          {!sidebarCollapsed && (
            <NavLink to="/" className="truncate text-lg font-bold tracking-tight text-slate-900">
              Gestionale
            </NavLink>
          )}
          <button
            type="button"
            onClick={() => setSidebarCollapsed(collapsed => !collapsed)}
            aria-label={sidebarCollapsed ? 'Espandi menu' : 'Comprimi menu'}
            aria-expanded={!sidebarCollapsed}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-slate-500 transition hover:bg-slate-100 hover:text-slate-900 focus:outline-none focus:ring-4 focus:ring-indigo-100"
          >
            <i
              className={`bi ${sidebarCollapsed ? 'bi-chevron-right' : 'bi-chevron-left'} text-lg`}
              aria-hidden="true"
            />
          </button>
        </div>

        <nav aria-label="Menu principale" className="flex-1 space-y-2 p-3 pt-5">
          {navigationItems.map(({ to, label, icon }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              title={sidebarCollapsed ? label : undefined}
              aria-label={sidebarCollapsed ? label : undefined}
              className={({ isActive }) =>
                `flex min-h-11 items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-semibold transition ${
                  isActive
                    ? 'bg-indigo-50 text-indigo-700'
                    : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
                } ${sidebarCollapsed ? 'justify-center' : ''}`
              }
            >
              <i className={`bi ${icon} shrink-0 text-lg`} aria-hidden="true" />
              {!sidebarCollapsed && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>

        {!sidebarCollapsed && (
          <p className="border-t border-slate-100 px-5 py-4 text-xs text-slate-400">
            Gestionale aziendale
          </p>
        )}
      </aside>

      <div className="min-w-0 flex-1">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/dipendenti" element={<Dipendente />} />
          <Route path="/chat" element={<ChatBot />} />
        </Routes>
      </div>
    </div>
  );
}

export default App;
