'use strict';
// Service worker di iVibe: permette di aprire il sito anche senza internet.
//  - pagina, stile e codice: prima la rete, se manca si usa la copia salvata
//  - /api/me e /api/catalogo: idem, così offline si vedono catalogo e playlist
//  - copertine di YouTube: copia salvata se c'è (vengono salvate quando scarichi una canzone)
// I file audio/video scaricati NON passano da qui: stanno in IndexedDB.

const VERSIONE = 'ivibe-v4';
const GUSCIO = ['/', '/app.css?v=4', '/app.js?v=4', '/logo-192.png', '/logo-512.png', '/favicon.png', '/manifest.webmanifest'];
const API_OFFLINE = ['/api/me', '/api/catalogo', '/api/scarica-stato'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSIONE).then(c => c.addAll(GUSCIO)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(nomi => Promise.all(nomi.filter(n => n.startsWith('ivibe-v') && n !== VERSIONE).map(n => caches.delete(n))))
    .then(() => self.clients.claim()));
});

async function primaLaRete(req, nomeCache, chiave = req) {
  try {
    const res = await fetch(req);
    if (res.ok) {
      const copia = res.clone();
      caches.open(nomeCache).then(c => c.put(chiave, copia));
    }
    return res;
  } catch {
    const salvata = await caches.match(chiave, { ignoreSearch: false });
    if (salvata) return salvata;
    throw new Error('offline');
  }
}

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);

  // copertine
  if (url.hostname === 'i.ytimg.com') {
    e.respondWith(caches.open('ivibe-img').then(async c => {
      const salvata = await c.match(req.url);
      if (salvata) return salvata;
      return fetch(req);
    }));
    return;
  }

  if (url.origin !== self.location.origin) return;

  if (url.pathname.startsWith('/api/')) {
    if (!API_OFFLINE.includes(url.pathname)) return; // download e modifiche: solo rete
    e.respondWith(primaLaRete(req, 'ivibe-api').catch(() => new Response(
      JSON.stringify({ errore: 'Sei offline' }), { status: 503, headers: { 'Content-Type': 'application/json' } })));
    return;
  }

  if (req.mode === 'navigate') {
    // qualsiasi pagina del sito = index.html
    e.respondWith(primaLaRete(req, VERSIONE, '/').catch(() => caches.match('/')));
    return;
  }

  e.respondWith(primaLaRete(req, VERSIONE).catch(() => caches.match(req, { ignoreSearch: true })
    .then(r => r || new Response('', { status: 504 }))));
});
