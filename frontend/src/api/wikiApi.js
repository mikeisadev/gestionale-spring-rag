import { API_BASE_URL } from '../config';

// Lezione 12: tutte le chiamate del Wiki Explorer.
//
// La lezione originale usa il proxy di Vite ("/api" -> backend). Il nostro gestionale invece
// usa API_BASE_URL (lezione 4): http://localhost:8081 in sviluppo, "" (stesso sito) in Docker.
const WIKI_URL = `${API_BASE_URL}/api/wiki`;

async function requestJson(url, options) {
  let response;
  try {
    response = await fetch(url, options);
  } catch {
    throw new Error(`Il backend non risponde (${API_BASE_URL || 'stesso sito'}).`);
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    throw new Error(data?.message || `Richiesta fallita (${response.status})`);
  }
  return data;
}

// La wiki è configurata? (endpoint della lezione 11)
export function getWikiStatus() {
  return requestJson(`${API_BASE_URL}/api/documents/wiki/status`);
}

export function searchWiki(query) {
  return requestJson(`${WIKI_URL}/search?q=${encodeURIComponent(query)}`);
}

export function getPage(path) {
  return requestJson(`${WIKI_URL}/page?path=${encodeURIComponent(path)}`);
}

export function reloadWiki() {
  return requestJson(`${WIKI_URL}/reload`, { method: 'POST' });
}

export function askWiki(question) {
  return requestJson(`${WIKI_URL}/ask`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question }),
  });
}

// Gli asset arrivano dal backend come "/api/wiki/assets?name=...": in sviluppo vanno sul backend (8081)
export function assetUrl(src) {
  return src.startsWith('/api/') ? `${API_BASE_URL}${src}` : src;
}
