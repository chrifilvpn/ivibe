'use strict';
/*
 * iVibe — "server" locale per l'app Android.
 *
 * Nell'app non c'è nessun server: questo file risponde alle stesse richieste /api/...
 * del server Node (web/server.js), ma salva tutto nel telefono (localStorage):
 * utenti, catalogo, preferiti, playlist. I dati restano anche spegnendo il telefono
 * e si perdono solo disinstallando l'app (o cancellando i suoi dati).
 * Le password usano lo stesso formato PBKDF2 del server, quindi gli account del seed funzionano.
 */
window.LocaleAPI = (() => {
  const CHIAVE_DATI = 'ivibe.dati';
  const CHIAVE_SESSIONE = 'ivibe.sessione';
  let dati = null;

  class ErroreApi extends Error {
    constructor(stato, messaggio) { super(messaggio); this.stato = stato; }
  }

  // ===================== SALVATAGGIO =====================

  async function carica() {
    if (dati) return;
    try { dati = JSON.parse(localStorage.getItem(CHIAVE_DATI)); } catch { dati = null; }
    if (!dati || !Array.isArray(dati.utenti)) {
      const seed = await (await fetch('/seed.json')).json();
      dati = {
        versione: 1,
        utenti: seed.utenti, canzoni: seed.canzoni, preferiti: seed.preferiti || {}, playlist: seed.playlist || [],
        prossimiId: {
          utente: Math.max(0, ...seed.utenti.map(u => u.id)) + 1,
          canzone: Math.max(0, ...seed.canzoni.map(c => c.id)) + 1,
          playlist: Math.max(0, ...(seed.playlist || []).map(p => p.id)) + 1,
        },
      };
      salva();
    }
  }

  function salva() {
    try {
      localStorage.setItem(CHIAVE_DATI, JSON.stringify(dati));
    } catch {
      throw new ErroreApi(507, 'Memoria del telefono piena: impossibile salvare');
    }
  }

  // ===================== PASSWORD (PBKDF2, come il server) =====================

  const b64 = buf => btoa(String.fromCharCode(...new Uint8Array(buf)));
  const daB64 = s => Uint8Array.from(atob(s), c => c.charCodeAt(0));

  async function pbkdf2(password, salt, iterazioni, byte) {
    const chiave = await crypto.subtle.importKey('raw', new TextEncoder().encode(password), 'PBKDF2', false, ['deriveBits']);
    return new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt, iterations: iterazioni }, chiave, byte * 8));
  }

  async function hashPassword(password) {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    return `pbkdf2$65536$${b64(salt)}$${b64(await pbkdf2(password, salt, 65536, 32))}`;
  }

  async function verificaPassword(password, salvata) {
    if (typeof password !== 'string' || typeof salvata !== 'string') return false;
    const p = salvata.split('$');
    if (p.length !== 4 || p[0] !== 'pbkdf2') return false;
    const atteso = daB64(p[3]);
    const calcolato = await pbkdf2(password, daB64(p[2]), parseInt(p[1], 10), atteso.length);
    let diverso = 0;
    for (let i = 0; i < atteso.length; i++) diverso |= atteso[i] ^ calcolato[i];
    return atteso.length > 0 && diverso === 0;
  }

  // ===================== VALIDAZIONE (come web/lib/validatore.js) =====================

  function controllaPassword(p) {
    if (typeof p !== 'string' || p.length < 7) return 'Password troppo corta (minimo 7 caratteri)';
    if (p.length > 64) return 'Password troppo lunga (massimo 64 caratteri)';
    if (/\s/.test(p)) return 'La password non può contenere spazi';
    if (!/[A-Z]/.test(p) || !/[0-9]/.test(p) || !/[^A-Za-z0-9]/.test(p)) return 'La password deve avere una maiuscola, un numero e un simbolo';
    return null;
  }
  const controllaEmail = e => (typeof e !== 'string' || e.length > 100 || !/^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(e) ? 'Email non valida (es. nome@dominio.it)' : null);
  const controllaNickname = n => (typeof n !== 'string' || !/^[A-Za-z0-9._-]{3,30}$/.test(n) ? 'Username: 3-30 caratteri tra lettere, numeri, . _ -' : null);
  function controllaData(d) {
    if (typeof d !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(d)) return 'Data di nascita non valida';
    const data = new Date(d + 'T00:00:00Z');
    if (Number.isNaN(data.getTime()) || data.toISOString().slice(0, 10) !== d) return 'Data di nascita non valida';
    if (data > new Date()) return 'La data di nascita non può essere nel futuro';
    return null;
  }
  function idYouTube(url) {
    let u;
    try { u = new URL(String(url).trim()); } catch { return null; }
    const host = u.hostname.replace(/^www\.|^m\.|^music\./, '');
    let id = null;
    if (host === 'youtu.be') id = u.pathname.slice(1).split('/')[0];
    else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
      if (u.pathname === '/watch') id = u.searchParams.get('v');
      else { const m = u.pathname.match(/^\/(?:shorts|embed|live|v)\/([^/?#]+)/); if (m) id = m[1]; }
    }
    return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
  }
  function durataInSecondi(v) {
    if (v === undefined || v === null || v === '') return 0;
    const m = String(v).trim().replace(',', '.').match(/^(\d{1,3})(?:[:.](\d{1,2}))?$/);
    if (!m) return NaN;
    const sec = m[2] ? parseInt(m[2].padEnd(2, '0'), 10) : 0;
    return sec > 59 ? NaN : parseInt(m[1], 10) * 60 + sec;
  }
  function testo(v, max, campo) {
    if (typeof v !== 'string' || !v.trim()) throw new ErroreApi(400, `Il campo ${campo} è obbligatorio`);
    if (v.trim().length > max) throw new ErroreApi(400, `Il campo ${campo} è troppo lungo (max ${max})`);
    return v.trim();
  }

  // ===================== SESSIONE =====================

  const impronta = u => u.password.slice(-12);
  function utenteSessione() {
    let s;
    try { s = JSON.parse(localStorage.getItem(CHIAVE_SESSIONE)); } catch { return null; }
    if (!s) return null;
    const u = dati.utenti.find(x => x.id === s.id);
    return u && impronta(u) === s.p ? u : null;
  }
  function apriSessione(u) { localStorage.setItem(CHIAVE_SESSIONE, JSON.stringify({ id: u.id, p: impronta(u) })); }
  function chiudiSessione() { localStorage.removeItem(CHIAVE_SESSIONE); }

  const pubblico = u => ({ id: u.id, nickname: u.nickname, email: u.email, isAdmin: u.isAdmin, dataNascita: u.dataNascita });
  const plPubblica = p => ({ id: p.id, nome: p.nome, canzoni: p.canzoni.slice() });

  // ===================== ROTTE =====================

  const rotte = [];
  function rotta(metodo, percorso, livello, gestore) {
    const nomi = [];
    const regex = new RegExp('^' + percorso.replace(/:(\w+)/g, (_, n) => { nomi.push(n); return '(\\d+)'; }) + '$');
    rotte.push({ metodo, regex, nomi, livello, gestore });
  }

  const canzone = id => { const c = dati.canzoni.find(x => x.id === id); if (!c) throw new ErroreApi(404, 'Canzone non trovata'); return c; };
  const miaPlaylist = (u, id) => { const p = dati.playlist.find(x => x.id === id && x.utenteId === u.id); if (!p) throw new ErroreApi(404, 'Playlist non trovata'); return p; };
  function eliminaUtente(id) {
    dati.utenti = dati.utenti.filter(u => u.id !== id);
    delete dati.preferiti[id];
    dati.playlist = dati.playlist.filter(p => p.utenteId !== id);
  }

  rotta('GET', '/api/info', 'pubblico', () => ({ registrazioni: true, nome: 'iVibe', locale: true }));
  rotta('GET', '/api/me', 'pubblico', ({ utente }) => ({ utente: utente ? pubblico(utente) : null }));

  rotta('POST', '/api/login', 'pubblico', async ({ corpo }) => {
    const login = String(corpo.login || '').trim().toLowerCase();
    const u = dati.utenti.find(x => x.nickname.toLowerCase() === login || x.email.toLowerCase() === login);
    if (!u || !(await verificaPassword(String(corpo.password || ''), u.password))) throw new ErroreApi(401, 'Credenziali errate');
    apriSessione(u);
    return { utente: pubblico(u) };
  });

  rotta('POST', '/api/registrazione', 'pubblico', async ({ corpo }) => {
    const nickname = String(corpo.nickname || '').trim();
    const email = String(corpo.email || '').trim();
    const err = controllaNickname(nickname) || controllaEmail(email) || controllaData(corpo.dataNascita) ||
      (corpo.password !== corpo.conferma ? 'Le password non coincidono' : null) || controllaPassword(corpo.password);
    if (err) throw new ErroreApi(400, err);
    if (dati.utenti.some(u => u.nickname.toLowerCase() === nickname.toLowerCase() || u.email.toLowerCase() === email.toLowerCase())) {
      throw new ErroreApi(409, 'Username o email già registrati');
    }
    const u = { id: dati.prossimiId.utente++, nickname, email, password: await hashPassword(corpo.password),
      dataNascita: corpo.dataNascita, isAdmin: false, creato: new Date().toISOString() };
    dati.utenti.push(u);
    salva();
    apriSessione(u);
    return { utente: pubblico(u) };
  });

  rotta('POST', '/api/logout', 'pubblico', () => { chiudiSessione(); return { ok: true }; });

  rotta('POST', '/api/account/password', 'utente', async ({ utente, corpo }) => {
    if (!(await verificaPassword(corpo.attuale || '', utente.password))) throw new ErroreApi(400, 'Password attuale errata');
    if (corpo.nuova === corpo.attuale) throw new ErroreApi(400, 'La nuova password è uguale a quella attuale');
    const err = controllaPassword(corpo.nuova);
    if (err) throw new ErroreApi(400, err);
    utente.password = await hashPassword(corpo.nuova);
    salva();
    apriSessione(utente);
    return { ok: true };
  });

  rotta('POST', '/api/account/elimina', 'utente', async ({ utente, corpo }) => {
    if (!(await verificaPassword(corpo.password || '', utente.password))) throw new ErroreApi(400, 'Password errata');
    if (utente.isAdmin && dati.utenti.filter(u => u.isAdmin).length <= 1) throw new ErroreApi(400, "Sei l'unico admin: non puoi eliminare l'account");
    eliminaUtente(utente.id);
    salva();
    chiudiSessione();
    return { ok: true };
  });

  rotta('GET', '/api/catalogo', 'utente', ({ utente }) => ({
    canzoni: dati.canzoni.map(c => ({ ...c })),
    preferiti: (dati.preferiti[utente.id] || []).slice(),
    playlist: dati.playlist.filter(p => p.utenteId === utente.id).map(plPubblica),
  }));

  function preferito(utente, id, si) {
    if (si) canzone(id);
    const l = dati.preferiti[utente.id] || (dati.preferiti[utente.id] = []);
    const i = l.indexOf(id);
    if (si && i === -1) l.push(id);
    if (!si && i !== -1) l.splice(i, 1);
    salva();
    return { preferiti: l.slice() };
  }
  rotta('PUT', '/api/preferiti/:id', 'utente', ({ utente, p }) => preferito(utente, p.id, true));
  rotta('DELETE', '/api/preferiti/:id', 'utente', ({ utente, p }) => preferito(utente, p.id, false));

  function nomePlaylist(utente, nome, escludi) {
    const n = testo(nome, 60, 'nome');
    if (dati.playlist.some(x => x.utenteId === utente.id && x.id !== escludi && x.nome.toLowerCase() === n.toLowerCase())) {
      throw new ErroreApi(409, 'Hai già una playlist con questo nome');
    }
    return n;
  }
  rotta('POST', '/api/playlist', 'utente', ({ utente, corpo }) => {
    const p = { id: dati.prossimiId.playlist++, utenteId: utente.id, nome: nomePlaylist(utente, corpo.nome), canzoni: [] };
    dati.playlist.push(p);
    salva();
    return { playlist: plPubblica(p) };
  });
  rotta('PATCH', '/api/playlist/:id', 'utente', ({ utente, p, corpo }) => {
    const pl = miaPlaylist(utente, p.id);
    if (corpo.nome !== undefined) pl.nome = nomePlaylist(utente, corpo.nome, pl.id);
    if (Array.isArray(corpo.canzoni)) {
      const nuovo = corpo.canzoni.map(Number);
      if (nuovo.length !== pl.canzoni.length || new Set(nuovo).size !== nuovo.length || !nuovo.every(id => pl.canzoni.includes(id))) {
        throw new ErroreApi(400, 'Ordine non valido');
      }
      pl.canzoni = nuovo;
    }
    salva();
    return { playlist: plPubblica(pl) };
  });
  rotta('DELETE', '/api/playlist/:id', 'utente', ({ utente, p }) => {
    const pl = miaPlaylist(utente, p.id);
    dati.playlist = dati.playlist.filter(x => x !== pl);
    salva();
    return { ok: true };
  });
  function inPlaylist(utente, pid, cid, si) {
    const pl = miaPlaylist(utente, pid);
    if (si) canzone(cid);
    const i = pl.canzoni.indexOf(cid);
    if (si && i === -1) pl.canzoni.push(cid);
    if (!si && i !== -1) pl.canzoni.splice(i, 1);
    salva();
    return { playlist: plPubblica(pl) };
  }
  rotta('PUT', '/api/playlist/:id/canzoni/:cid', 'utente', ({ utente, p }) => inPlaylist(utente, p.id, p.cid, true));
  rotta('DELETE', '/api/playlist/:id/canzoni/:cid', 'utente', ({ utente, p }) => inPlaylist(utente, p.id, p.cid, false));

  // ---- admin ----

  async function infoYouTube(videoId) {
    const url = `https://www.youtube.com/oembed?format=json&url=${encodeURIComponent('https://www.youtube.com/watch?v=' + videoId)}`;
    try {
      const http = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.CapacitorHttp;
      let stato, j;
      if (http) { const r = await http.get({ url, responseType: 'json' }); stato = r.status; j = typeof r.data === 'string' ? JSON.parse(r.data) : r.data; }
      else { const r = await fetch(url); stato = r.status; j = r.ok ? await r.json() : null; }
      if (stato === 401 || stato === 403) return { incorporabile: false };
      if (stato !== 200 || !j) return null;
      return { incorporabile: true, titolo: j.title, canale: j.author_name };
    } catch {
      return undefined; // senza internet: non lo sappiamo
    }
  }

  rotta('POST', '/api/admin/anteprima', 'admin', async ({ corpo }) => {
    const videoId = idYouTube(corpo.url);
    if (!videoId) throw new ErroreApi(400, 'Link YouTube non valido');
    const gia = dati.canzoni.find(c => c.videoId === videoId);
    return { videoId, gia: gia ? gia.titolo : null, info: (await infoYouTube(videoId)) || null };
  });

  rotta('POST', '/api/admin/canzoni', 'admin', async ({ corpo }) => {
    const titolo = testo(corpo.titolo, 150, 'titolo');
    const artista = testo(corpo.artista, 150, 'artista');
    const videoId = idYouTube(corpo.url);
    if (!videoId) throw new ErroreApi(400, 'Link YouTube non valido');
    const durata = durataInSecondi(corpo.durata);
    if (Number.isNaN(durata)) throw new ErroreApi(400, 'Durata non valida (es. 3:45)');
    const gia = dati.canzoni.find(c => c.videoId === videoId);
    if (gia) throw new ErroreApi(409, `Questo video è già nel catalogo ("${gia.titolo}")`);
    const c = { id: dati.prossimiId.canzone++, titolo, artista, durata, videoId };
    dati.canzoni.push(c);
    salva();
    return { canzone: { ...c } };
  });

  rotta('PATCH', '/api/admin/canzoni/:id', 'admin', ({ p, corpo }) => {
    const c = canzone(p.id);
    if (corpo.titolo !== undefined) c.titolo = testo(corpo.titolo, 150, 'titolo');
    if (corpo.artista !== undefined) c.artista = testo(corpo.artista, 150, 'artista');
    if (corpo.durata !== undefined) {
      const d = durataInSecondi(corpo.durata);
      if (Number.isNaN(d)) throw new ErroreApi(400, 'Durata non valida (es. 3:45)');
      c.durata = d;
    }
    salva();
    return { canzone: { ...c } };
  });

  rotta('DELETE', '/api/admin/canzoni/:id', 'admin', ({ p }) => {
    canzone(p.id);
    dati.canzoni = dati.canzoni.filter(c => c.id !== p.id);
    for (const k of Object.keys(dati.preferiti)) dati.preferiti[k] = dati.preferiti[k].filter(x => x !== p.id);
    for (const pl of dati.playlist) pl.canzoni = pl.canzoni.filter(x => x !== p.id);
    salva();
    return { ok: true };
  });

  rotta('GET', '/api/admin/utenti', 'admin', () => ({
    utenti: dati.utenti.map(u => ({ ...pubblico(u), creato: u.creato || null })), archivio: 'telefono',
  }));

  rotta('PATCH', '/api/admin/utenti/:id', 'admin', ({ utente, p, corpo }) => {
    const u = dati.utenti.find(x => x.id === p.id);
    if (!u) throw new ErroreApi(404, 'Utente non trovato');
    if (typeof corpo.isAdmin !== 'boolean') throw new ErroreApi(400, 'Richiesta non valida');
    if (u.id === utente.id && !corpo.isAdmin) throw new ErroreApi(400, 'Non puoi togliere il ruolo admin a te stesso');
    u.isAdmin = corpo.isAdmin;
    salva();
    return { utente: pubblico(u) };
  });

  rotta('DELETE', '/api/admin/utenti/:id', 'admin', ({ utente, p }) => {
    const u = dati.utenti.find(x => x.id === p.id);
    if (!u) throw new ErroreApi(404, 'Utente non trovato');
    if (u.id === utente.id) throw new ErroreApi(400, 'Per eliminare il tuo account usa le Impostazioni');
    if (u.isAdmin) throw new ErroreApi(400, 'Non puoi eliminare un admin: prima togli il ruolo');
    eliminaUtente(u.id);
    salva();
    return { ok: true };
  });

  // ---- download: li fa il plugin yt-dlp nativo, qui diciamo solo se c'è ----
  rotta('GET', '/api/scarica-stato', 'utente', () => ({ attivi: !!(window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.YtDlp) }));

  // ---- backup: esporta/importa tutti i dati (non i file audio) ----
  rotta('GET', '/api/backup', 'admin', () => ({ dati }));
  rotta('POST', '/api/backup', 'admin', ({ corpo }) => {
    const d = corpo.dati;
    if (!d || !Array.isArray(d.utenti) || !Array.isArray(d.canzoni) || !d.prossimiId) throw new ErroreApi(400, 'File di backup non valido');
    dati = d;
    salva();
    return { ok: true };
  });

  // ===================== ENTRATA =====================

  /** Stessa forma della risposta del server: { stato, dati }. */
  async function richiesta(metodo, url, corpo) {
    await carica();
    const percorso = url.split('?')[0];
    let trovata = null, p = {}, metodoSbagliato = false;
    for (const r of rotte) {
      const m = percorso.match(r.regex);
      if (!m) continue;
      if (r.metodo !== metodo) { metodoSbagliato = true; continue; }
      trovata = r;
      r.nomi.forEach((n, i) => { p[n] = parseInt(m[i + 1], 10); });
      break;
    }
    try {
      if (!trovata) throw new ErroreApi(metodoSbagliato ? 405 : 404, 'Rotta non trovata');
      const utente = utenteSessione();
      if (trovata.livello !== 'pubblico' && !utente) throw new ErroreApi(401, 'Devi fare il login');
      if (trovata.livello === 'admin' && !utente.isAdmin) throw new ErroreApi(403, 'Solo per admin');
      const risposta = await trovata.gestore({ utente, corpo: corpo || {}, p });
      return { stato: 200, dati: JSON.parse(JSON.stringify(risposta)) };
    } catch (e) {
      if (e instanceof ErroreApi) return { stato: e.stato, dati: { errore: e.message } };
      console.error(e);
      return { stato: 500, dati: { errore: 'Errore interno: ' + e.message } };
    }
  }

  return { richiesta };
})();
