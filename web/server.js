'use strict';
// iVibe - versione web.
// Server HTTP scritto solo con i moduli di Node (nessun framework):
// serve i file in public/ e le API JSON sotto /api.

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const archivio = require('./lib/archivio');
const sic = require('./lib/sicurezza');
const val = require('./lib/validatore');

const PORTA = parseInt(process.env.PORT || '3000', 10);
const PRODUZIONE = process.env.NODE_ENV === 'production';
const CARTELLA_PUBBLICA = path.join(__dirname, 'public');
const NOME_COOKIE = 'ivibe_sessione';

// ===================== UTILITÀ HTTP =====================

class ErroreHttp extends Error {
  constructor(stato, messaggio) { super(messaggio); this.stato = stato; }
}

const TIPI = {
  '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml', '.png': 'image/png',
  '.ico': 'image/x-icon', '.json': 'application/json', '.webmanifest': 'application/manifest+json',
};

const CSP = [
  "default-src 'self'",
  "script-src 'self' https://www.youtube.com https://s.ytimg.com",
  "frame-src https://www.youtube.com https://www.youtube-nocookie.com",
  "img-src 'self' https://i.ytimg.com data:",
  "style-src 'self' 'unsafe-inline'",
  "connect-src 'self'",
  "base-uri 'none'",
  "form-action 'self'",
  "frame-ancestors 'none'",
].join('; ');

function intestazioniSicurezza(res) {
  res.setHeader('Content-Security-Policy', CSP);
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
  res.setHeader('X-Frame-Options', 'DENY');
  if (PRODUZIONE) res.setHeader('Strict-Transport-Security', 'max-age=31536000');
}

function inviaJson(res, stato, oggetto) {
  const corpo = JSON.stringify(oggetto);
  res.writeHead(stato, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(corpo);
}

function leggiCookie(req) {
  const out = {};
  for (const parte of (req.headers.cookie || '').split(';')) {
    const i = parte.indexOf('=');
    if (i > 0) out[parte.slice(0, i).trim()] = decodeURIComponent(parte.slice(i + 1).trim());
  }
  return out;
}

function impostaSessione(res, utente) {
  const opzioni = ['Path=/', 'HttpOnly', 'SameSite=Lax', `Max-Age=${Math.floor(sic.DURATA_SESSIONE_MS / 1000)}`];
  if (PRODUZIONE) opzioni.push('Secure');
  res.setHeader('Set-Cookie', `${NOME_COOKIE}=${sic.creaToken(utente)}; ${opzioni.join('; ')}`);
}

function cancellaSessione(res) {
  res.setHeader('Set-Cookie', `${NOME_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${PRODUZIONE ? '; Secure' : ''}`);
}

function leggiCorpo(req) {
  return new Promise((resolve, reject) => {
    // Accettiamo solo JSON: un form di un altro sito non può mandarlo (protezione CSRF)
    if (!(req.headers['content-type'] || '').startsWith('application/json')) {
      return reject(new ErroreHttp(415, 'Formato richiesta non valido'));
    }
    let dati = '';
    req.setEncoding('utf8');
    req.on('data', pezzo => {
      dati += pezzo;
      if (dati.length > 20000) { reject(new ErroreHttp(413, 'Richiesta troppo grande')); req.destroy(); }
    });
    req.on('end', () => {
      try { resolve(dati ? JSON.parse(dati) : {}); } catch { reject(new ErroreHttp(400, 'JSON non valido')); }
    });
    req.on('error', reject);
  });
}

function ipDi(req) {
  // Render mette l'IP vero del visitatore in X-Forwarded-For
  return (req.headers['x-forwarded-for'] || '').split(',')[0].trim() || req.socket.remoteAddress || '?';
}

function testo(v, max, campo) {
  if (typeof v !== 'string' || !v.trim()) throw new ErroreHttp(400, `Il campo ${campo} è obbligatorio`);
  const t = v.trim();
  if (t.length > max) throw new ErroreHttp(400, `Il campo ${campo} è troppo lungo (max ${max})`);
  return t;
}

// ===================== FORMATO DATI VERSO IL BROWSER =====================

const utentePubblico = u => ({ id: u.id, nickname: u.nickname, email: u.email, isAdmin: u.isAdmin, dataNascita: u.dataNascita });
const playlistPubblica = p => ({ id: p.id, nome: p.nome, canzoni: p.canzoni });

// ===================== ROTTE API =====================
// Ogni rotta: [metodo, percorso, gestore, livello]  livello: 'pubblico' | 'utente' | 'admin'

const rotte = [];
function rotta(metodo, percorso, livello, gestore) {
  const nomi = [];
  const regex = new RegExp('^' + percorso.replace(/:(\w+)/g, (_, n) => { nomi.push(n); return '(\\d+)'; }) + '$');
  rotte.push({ metodo, regex, nomi, livello, gestore });
}

// ---- Account ----

rotta('GET', '/api/me', 'pubblico', async ({ utente }) => ({ utente: utente ? utentePubblico(utente) : null }));

rotta('POST', '/api/login', 'pubblico', async ({ req, res, corpo }) => {
  const ip = ipDi(req);
  if (sic.troppiTentativi(ip)) throw new ErroreHttp(429, 'Troppi tentativi: riprova tra qualche minuto');

  const login = typeof corpo.login === 'string' ? corpo.login.trim() : '';
  const password = typeof corpo.password === 'string' ? corpo.password : '';
  if (!login || !password) throw new ErroreHttp(400, 'Compila tutti i campi');

  const u = archivio.utenti.perLogin(login);
  if (!u || !sic.verificaPassword(password, u.password)) {
    sic.registraTentativoFallito(ip);
    throw new ErroreHttp(401, 'Credenziali errate');
  }
  sic.azzeraTentativi(ip);
  impostaSessione(res, u);
  return { utente: utentePubblico(u) };
});

rotta('POST', '/api/registrazione', 'pubblico', async ({ res, corpo }) => {
  const nickname = typeof corpo.nickname === 'string' ? corpo.nickname.trim() : '';
  const email = typeof corpo.email === 'string' ? corpo.email.trim() : '';
  const { password, conferma, dataNascita } = corpo;

  const errore = val.controllaNickname(nickname) || val.controllaEmail(email) || val.controllaData(dataNascita) ||
    (password !== conferma ? 'Le password non coincidono' : null) || val.controllaPassword(password);
  if (errore) throw new ErroreHttp(400, errore);
  if (archivio.utenti.esiste(nickname, email)) throw new ErroreHttp(409, 'Username o email già registrati');

  const u = archivio.utenti.crea({ nickname, email, password: sic.hashPassword(password), dataNascita });
  impostaSessione(res, u);
  return { utente: utentePubblico(u) };
});

rotta('POST', '/api/logout', 'pubblico', async ({ res }) => {
  cancellaSessione(res);
  return { ok: true };
});

rotta('POST', '/api/account/password', 'utente', async ({ res, utente, corpo }) => {
  if (!sic.verificaPassword(corpo.attuale || '', utente.password)) throw new ErroreHttp(400, 'Password attuale errata');
  if (corpo.nuova === corpo.attuale) throw new ErroreHttp(400, 'La nuova password è uguale a quella attuale');
  const errore = val.controllaPassword(corpo.nuova);
  if (errore) throw new ErroreHttp(400, errore);
  archivio.utenti.aggiorna(utente, { password: sic.hashPassword(corpo.nuova) });
  impostaSessione(res, utente); // le altre sessioni aperte decadono
  return { ok: true };
});

rotta('POST', '/api/account/elimina', 'utente', async ({ res, utente, corpo }) => {
  if (!sic.verificaPassword(corpo.password || '', utente.password)) throw new ErroreHttp(400, 'Password errata');
  if (utente.isAdmin && archivio.utenti.contaAdmin() <= 1) {
    throw new ErroreHttp(400, "Sei l'unico admin: non puoi eliminare l'account");
  }
  archivio.utenti.elimina(utente.id);
  cancellaSessione(res);
  return { ok: true };
});

// ---- Catalogo, preferiti, playlist ----

rotta('GET', '/api/catalogo', 'utente', async ({ utente }) => ({
  canzoni: archivio.canzoni.tutte(),
  preferiti: archivio.preferiti.di(utente.id),
  playlist: archivio.playlist.di(utente.id).map(playlistPubblica),
}));

function canzoneEsistente(id) {
  const c = archivio.canzoni.perId(id);
  if (!c) throw new ErroreHttp(404, 'Canzone non trovata');
  return c;
}

rotta('PUT', '/api/preferiti/:id', 'utente', async ({ utente, p }) => {
  canzoneEsistente(p.id);
  return { preferiti: archivio.preferiti.imposta(utente.id, p.id, true) };
});

rotta('DELETE', '/api/preferiti/:id', 'utente', async ({ utente, p }) => ({
  preferiti: archivio.preferiti.imposta(utente.id, p.id, false),
}));

function miaPlaylist(utente, id) {
  const pl = archivio.playlist.perId(id);
  if (!pl || pl.utenteId !== utente.id) throw new ErroreHttp(404, 'Playlist non trovata');
  return pl;
}

function nomePlaylist(utente, nome, escludiId) {
  const n = testo(nome, 60, 'nome');
  if (archivio.playlist.di(utente.id).some(x => x.id !== escludiId && x.nome.toLowerCase() === n.toLowerCase())) {
    throw new ErroreHttp(409, 'Hai già una playlist con questo nome');
  }
  return n;
}

rotta('POST', '/api/playlist', 'utente', async ({ utente, corpo }) => {
  if (archivio.playlist.di(utente.id).length >= 100) throw new ErroreHttp(400, 'Hai raggiunto il limite di playlist');
  return { playlist: playlistPubblica(archivio.playlist.crea(utente.id, nomePlaylist(utente, corpo.nome))) };
});

rotta('PATCH', '/api/playlist/:id', 'utente', async ({ utente, p, corpo }) => {
  const pl = miaPlaylist(utente, p.id);
  if (corpo.nome !== undefined) archivio.playlist.rinomina(pl, nomePlaylist(utente, corpo.nome, pl.id));
  if (Array.isArray(corpo.canzoni)) {
    // nuovo ordine: deve contenere esattamente le stesse canzoni
    const nuovo = corpo.canzoni.map(Number);
    const uguali = nuovo.length === pl.canzoni.length && new Set(nuovo).size === nuovo.length &&
      nuovo.every(id => pl.canzoni.includes(id));
    if (!uguali) throw new ErroreHttp(400, 'Ordine non valido');
    archivio.playlist.riordina(pl, nuovo);
  }
  return { playlist: playlistPubblica(pl) };
});

rotta('DELETE', '/api/playlist/:id', 'utente', async ({ utente, p }) => {
  archivio.playlist.elimina(miaPlaylist(utente, p.id).id);
  return { ok: true };
});

rotta('PUT', '/api/playlist/:id/canzoni/:cid', 'utente', async ({ utente, p }) => {
  const pl = miaPlaylist(utente, p.id);
  canzoneEsistente(p.cid);
  if (pl.canzoni.length >= 500) throw new ErroreHttp(400, 'Playlist piena');
  archivio.playlist.imposta(pl, p.cid, true);
  return { playlist: playlistPubblica(pl) };
});

rotta('DELETE', '/api/playlist/:id/canzoni/:cid', 'utente', async ({ utente, p }) => {
  const pl = miaPlaylist(utente, p.id);
  archivio.playlist.imposta(pl, p.cid, false);
  return { playlist: playlistPubblica(pl) };
});

// ---- Admin ----

async function infoYouTube(videoId) {
  // oEmbed di YouTube: dà titolo e canale del video senza chiave API
  const url = `https://www.youtube.com/oembed?format=json&url=${encodeURIComponent('https://www.youtube.com/watch?v=' + videoId)}`;
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(5000) });
    if (r.status === 401 || r.status === 403) return { incorporabile: false };
    if (!r.ok) return null;
    const j = await r.json();
    return { incorporabile: true, titolo: j.title, canale: j.author_name };
  } catch {
    return undefined; // rete non disponibile: non sappiamo
  }
}

rotta('POST', '/api/admin/anteprima', 'admin', async ({ corpo }) => {
  const videoId = val.idYouTube(corpo.url);
  if (!videoId) throw new ErroreHttp(400, 'Link YouTube non valido');
  const esistente = archivio.canzoni.perVideo(videoId);
  const info = await infoYouTube(videoId);
  return { videoId, gia: esistente ? esistente.titolo : null, info: info || null };
});

rotta('POST', '/api/admin/canzoni', 'admin', async ({ corpo }) => {
  const titolo = testo(corpo.titolo, 150, 'titolo');
  const artista = testo(corpo.artista, 150, 'artista');
  const videoId = val.idYouTube(corpo.url);
  if (!videoId) throw new ErroreHttp(400, 'Link YouTube non valido');
  const durata = val.durataInSecondi(corpo.durata);
  if (Number.isNaN(durata) || durata > 6 * 3600) throw new ErroreHttp(400, 'Durata non valida (es. 3:45)');
  const gia = archivio.canzoni.perVideo(videoId);
  if (gia) throw new ErroreHttp(409, `Questo video è già nel catalogo ("${gia.titolo}")`);
  const info = await infoYouTube(videoId);
  if (info === null) throw new ErroreHttp(400, 'Video non trovato su YouTube');
  if (info && info.incorporabile === false) throw new ErroreHttp(400, 'Questo video non si può riprodurre fuori da YouTube');
  return { canzone: archivio.canzoni.crea({ titolo, artista, durata, videoId }) };
});

rotta('PATCH', '/api/admin/canzoni/:id', 'admin', async ({ p, corpo }) => {
  const c = canzoneEsistente(p.id);
  const campi = {};
  if (corpo.titolo !== undefined) campi.titolo = testo(corpo.titolo, 150, 'titolo');
  if (corpo.artista !== undefined) campi.artista = testo(corpo.artista, 150, 'artista');
  if (corpo.durata !== undefined) {
    const d = val.durataInSecondi(corpo.durata);
    if (Number.isNaN(d)) throw new ErroreHttp(400, 'Durata non valida (es. 3:45)');
    campi.durata = d;
  }
  archivio.canzoni.aggiorna(c, campi);
  return { canzone: c };
});

rotta('DELETE', '/api/admin/canzoni/:id', 'admin', async ({ p }) => {
  canzoneEsistente(p.id);
  archivio.canzoni.elimina(p.id);
  return { ok: true };
});

rotta('GET', '/api/admin/utenti', 'admin', async () => ({
  utenti: archivio.utenti.tutti().map(u => ({ ...utentePubblico(u), creato: u.creato || null })),
  archivio: archivio.modalita(),
}));

rotta('PATCH', '/api/admin/utenti/:id', 'admin', async ({ utente, p, corpo }) => {
  const u = archivio.utenti.perId(p.id);
  if (!u) throw new ErroreHttp(404, 'Utente non trovato');
  if (typeof corpo.isAdmin !== 'boolean') throw new ErroreHttp(400, 'Richiesta non valida');
  if (u.id === utente.id && !corpo.isAdmin) throw new ErroreHttp(400, 'Non puoi togliere il ruolo admin a te stesso');
  archivio.utenti.aggiorna(u, { isAdmin: corpo.isAdmin });
  return { utente: utentePubblico(u) };
});

rotta('DELETE', '/api/admin/utenti/:id', 'admin', async ({ utente, p }) => {
  const u = archivio.utenti.perId(p.id);
  if (!u) throw new ErroreHttp(404, 'Utente non trovato');
  if (u.id === utente.id) throw new ErroreHttp(400, 'Per eliminare il tuo account usa le Impostazioni');
  if (u.isAdmin) throw new ErroreHttp(400, 'Non puoi eliminare un admin: prima togli il ruolo');
  archivio.utenti.elimina(u.id);
  return { ok: true };
});

// ===================== FILE STATICI =====================

function serviStatico(req, res, percorso) {
  let rel = decodeURIComponent(percorso);
  if (rel === '/' || !path.extname(rel)) rel = '/index.html'; // app a pagina singola
  const file = path.normalize(path.join(CARTELLA_PUBBLICA, rel));
  if (!file.startsWith(CARTELLA_PUBBLICA + path.sep)) { res.writeHead(403); return res.end(); }

  fs.stat(file, (err, st) => {
    if (err || !st.isFile()) { res.writeHead(404, { 'Content-Type': 'text/plain' }); return res.end('Non trovato'); }
    const ext = path.extname(file);
    res.writeHead(200, {
      'Content-Type': TIPI[ext] || 'application/octet-stream',
      'Content-Length': st.size,
      'Cache-Control': ext === '.html' ? 'no-cache' : 'public, max-age=3600',
    });
    if (req.method === 'HEAD') return res.end();
    fs.createReadStream(file).pipe(res);
  });
}

// ===================== SERVER =====================

const server = http.createServer(async (req, res) => {
  intestazioniSicurezza(res);
  const { pathname } = new URL(req.url, 'http://localhost');

  if (pathname === '/healthz') return inviaJson(res, 200, { ok: true });

  if (!pathname.startsWith('/api/')) {
    if (req.method !== 'GET' && req.method !== 'HEAD') { res.writeHead(405); return res.end(); }
    return serviStatico(req, res, pathname);
  }

  try {
    let trovata = null, parametri = {}, metodoSbagliato = false;
    for (const r of rotte) {
      const m = pathname.match(r.regex);
      if (!m) continue;
      if (r.metodo !== req.method) { metodoSbagliato = true; continue; }
      trovata = r;
      r.nomi.forEach((n, i) => { parametri[n] = parseInt(m[i + 1], 10); });
      break;
    }
    if (!trovata) throw new ErroreHttp(metodoSbagliato ? 405 : 404, 'Rotta non trovata');

    const utente = sic.leggiToken(leggiCookie(req)[NOME_COOKIE], id => archivio.utenti.perId(id));
    if (trovata.livello !== 'pubblico' && !utente) throw new ErroreHttp(401, 'Devi fare il login');
    if (trovata.livello === 'admin' && !utente.isAdmin) throw new ErroreHttp(403, 'Solo per admin');

    const lunghezza = req.headers['content-length'];
    const haCorpo = (lunghezza && lunghezza !== '0') || req.headers['transfer-encoding'];
    const corpo = haCorpo ? await leggiCorpo(req) : {};
    if (corpo === null || typeof corpo !== 'object' || Array.isArray(corpo)) throw new ErroreHttp(400, 'Richiesta non valida');

    const risposta = await trovata.gestore({ req, res, utente, corpo, p: parametri });
    inviaJson(res, 200, risposta);
  } catch (e) {
    if (e instanceof ErroreHttp) return inviaJson(res, e.stato, { errore: e.message });
    console.error(e);
    inviaJson(res, 500, { errore: 'Errore interno del server' });
  }
});

archivio.inizializza().then(() => {
  server.listen(PORTA, () => console.log(`[iVibe] in ascolto su http://localhost:${PORTA} (archivio: ${archivio.modalita()})`));
}).catch(e => {
  console.error('[iVibe] avvio fallito:', e);
  process.exit(1);
});

async function spegni() {
  server.close();
  await archivio.chiudi();
  process.exit(0);
}
process.on('SIGTERM', spegni);
process.on('SIGINT', spegni);
