'use strict';
/* iVibe — interfaccia web (JavaScript senza librerie) */

// ===================== UTILITÀ =====================

const $ = sel => document.querySelector(sel);

/** Crea un elemento: h('div', {class: 'x', onclick: fn}, figli...). Il testo non viene mai interpretato come HTML. */
function h(tag, attrs, ...figli) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'style') el.style.cssText = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else if (k === 'dataset') Object.assign(el.dataset, v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const f of figli.flat()) {
    if (f === null || f === undefined || f === false) continue;
    el.append(f instanceof Node ? f : document.createTextNode(String(f)));
  }
  return el;
}

const durata = sec => {
  sec = Math.max(0, Math.floor(sec || 0));
  const h_ = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60), s = sec % 60;
  return h_ ? `${h_}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
};
const thumb = (videoId, q = 'default') => `https://i.ytimg.com/vi/${videoId}/${q}.jpg`;
const SEGNAPOSTO = 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 4 4"><defs><linearGradient id="g"><stop offset="0" stop-color="#7b3fe4"/><stop offset="1" stop-color="#d22a9c"/></linearGradient></defs><rect width="4" height="4" fill="url(#g)"/></svg>');
document.addEventListener('error', e => {
  const img = e.target;
  if (img.tagName === 'IMG' && img.src !== SEGNAPOSTO) img.src = SEGNAPOSTO;
}, true);
const norm = s => (s || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');

function salvaPref(k, v) { try { localStorage.setItem('ivibe.' + k, JSON.stringify(v)); } catch { /* niente */ } }
function leggiPref(k, def) { try { const v = localStorage.getItem('ivibe.' + k); return v === null ? def : JSON.parse(v); } catch { return def; } }

function toast(msg, errore = false) {
  const t = h('div', { class: 'toast' + (errore ? ' err' : ''), text: msg });
  $('#toasts').append(t);
  setTimeout(() => t.remove(), errore ? 4500 : 2600);
}

async function api(metodo, url, corpo) {
  const opzioni = { method: metodo, credentials: 'same-origin', headers: {} };
  if (corpo !== undefined) {
    opzioni.headers['Content-Type'] = 'application/json';
    opzioni.body = JSON.stringify(corpo);
  }
  let r;
  try { r = await fetch(url, opzioni); } catch {
    throw new Error(navigator.onLine ? 'Connessione al server non riuscita' : 'Sei offline: questa operazione richiede internet');
  }
  let j = {};
  try { j = await r.json(); } catch { /* risposta vuota */ }
  if (r.status === 401 && stato.utente && !url.startsWith('/api/login') && !url.startsWith('/api/account')) {
    toast('Sessione scaduta: rifai il login', true);
    esciLocale();
  }
  if (!r.ok) throw new Error(j.errore || `Errore ${r.status}`);
  return j;
}

// ===================== STATO =====================

const stato = {
  utente: null,
  canzoni: [],
  perId: new Map(),
  preferiti: [],          // id in ordine di aggiunta
  playlist: [],
  filtri: { home: '', preferiti: '', aggiungi: '', admin: '' },
};
const isPreferito = id => stato.preferiti.includes(id);
const canzoniDi = ids => ids.map(id => stato.perId.get(id)).filter(Boolean);

async function caricaCatalogo() {
  const d = await api('GET', '/api/catalogo');
  stato.canzoni = d.canzoni;
  stato.perId = new Map(d.canzoni.map(c => [c.id, c]));
  stato.preferiti = d.preferiti;
  stato.playlist = d.playlist;
}

// ===================== DIALOG E MENU =====================

/**
 * Finestra di conferma / richiesta dati.
 * azione(valori) può lanciare un errore: viene mostrato e il dialog resta aperto.
 */
function dialogo({ titolo, testo = '', campi = [], ok = 'OK', pericolo = false, azione }) {
  const dlg = $('#dialog');
  $('#dialog-title').textContent = titolo;
  $('#dialog-text').textContent = testo;
  $('#dialog-err').textContent = '';
  const box = $('#dialog-fields');
  box.replaceChildren(...campi.map(c => h('label', {}, c.label,
    h('input', { name: c.name, type: c.type || 'text', value: c.value || '', maxlength: c.max || 200, autocomplete: c.autocomplete || 'off' }))));
  const btnOk = $('#dialog-ok');
  btnOk.textContent = ok;
  btnOk.className = 'btn ' + (pericolo ? 'btn-danger' : 'btn-primary');

  return new Promise(resolve => {
    const chiudi = v => { dlg.close(); pulisci(); resolve(v); };
    const onOk = async e => {
      e.preventDefault();
      const valori = Object.fromEntries([...box.querySelectorAll('input')].map(i => [i.name, i.value]));
      if (!azione) return chiudi(valori);
      btnOk.disabled = true;
      try { chiudi((await azione(valori)) ?? true); } catch (err) { $('#dialog-err').textContent = err.message; } finally { btnOk.disabled = false; }
    };
    const onCancel = e => { e.preventDefault(); chiudi(null); };
    const onEnter = e => { if (e.key === 'Enter' && e.target.tagName === 'INPUT') onOk(e); };
    const pulisci = () => {
      btnOk.removeEventListener('click', onOk);
      $('#dialog-cancel').removeEventListener('click', onCancel);
      dlg.removeEventListener('cancel', onCancel);
      dlg.removeEventListener('keydown', onEnter);
    };
    btnOk.addEventListener('click', onOk);
    $('#dialog-cancel').addEventListener('click', onCancel);
    dlg.addEventListener('cancel', onCancel);
    dlg.addEventListener('keydown', onEnter);
    dlg.showModal();
    (box.querySelector('input') || btnOk).focus();
  });
}

let menuAperto = null;
function apriMenu(ancora, voci) {
  const m = $('#menu');
  m.replaceChildren(...voci.map(v => {
    if (v === '-') return h('div', { class: 'sep' });
    if (v.titolo) return h('div', { class: 'menu-title', text: v.titolo });
    return h('button', { role: 'menuitem', onclick: e => { e.stopPropagation(); chiudiMenu(); v.azione(); } },
      h('span', { class: 'check', text: v.check ? '✓' : '' }), v.testo);
  }));
  m.hidden = false;
  const r = ancora.getBoundingClientRect();
  const w = m.offsetWidth, hh = m.offsetHeight;
  let left = Math.min(r.right - w, window.innerWidth - w - 8);
  let top = r.bottom + 6;
  if (top + hh > window.innerHeight - 8) top = Math.max(8, r.top - hh - 6);
  m.style.left = Math.max(8, left) + 'px';
  m.style.top = top + 'px';
  menuAperto = ancora;
  (m.querySelector('button') || m).focus();
}
function chiudiMenu() { $('#menu').hidden = true; menuAperto = null; }
document.addEventListener('click', e => { if (menuAperto && !$('#menu').contains(e.target)) chiudiMenu(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') chiudiMenu(); });
window.addEventListener('resize', chiudiMenu);
document.addEventListener('scroll', chiudiMenu, true);

// ===================== OFFLINE: FILE SCARICATI =====================
// I file scaricati stanno nel browser (IndexedDB), separati per utente.
// Restano anche chiudendo il sito e si ascoltano senza internet.

const Offline = (() => {
  let dbPromessa = null;
  let uid = null;
  const elenco = new Map(); // canzoneId -> { formato, size, quando, titolo, artista, videoId, durata }

  function db() {
    if (!dbPromessa) {
      dbPromessa = new Promise((resolve, reject) => {
        const r = indexedDB.open('ivibe-offline', 1);
        r.onupgradeneeded = () => r.result.createObjectStore('file', { keyPath: 'chiave' });
        r.onsuccess = () => resolve(r.result);
        r.onerror = () => reject(r.error);
      });
    }
    return dbPromessa;
  }

  async function operazione(modo, fn) {
    const d = await db();
    return new Promise((resolve, reject) => {
      const t = d.transaction('file', modo);
      const risultato = fn(t.objectStore('file'));
      t.oncomplete = () => resolve(risultato && 'result' in risultato ? risultato.result : undefined);
      t.onerror = () => reject(t.error);
      t.onabort = () => reject(t.error || new Error('Spazio esaurito sul dispositivo'));
    });
  }

  const chiave = id => `${uid}:${id}`;

  /** Legge l'elenco dei file dell'utente (senza caricare i file in memoria). */
  async function apri(utenteId) {
    uid = utenteId;
    elenco.clear();
    if (!('indexedDB' in window)) return;
    const d = await db();
    await new Promise((resolve, reject) => {
      const req = d.transaction('file').objectStore('file').openCursor();
      req.onsuccess = () => {
        const cur = req.result;
        if (!cur) return resolve();
        const v = cur.value;
        if (v.uid === uid) elenco.set(v.id, { formato: v.formato, size: v.size, quando: v.quando, titolo: v.titolo, artista: v.artista, videoId: v.videoId, durata: v.durata });
        cur.continue();
      };
      req.onerror = () => reject(req.error);
    });
  }

  async function salva(c, formato, blob) {
    const meta = { formato, size: blob.size, quando: Date.now(), titolo: c.titolo, artista: c.artista, videoId: c.videoId, durata: c.durata };
    await operazione('readwrite', s => s.put({ chiave: chiave(c.id), uid, id: c.id, blob, ...meta }));
    elenco.set(c.id, meta);
  }

  async function file(id) {
    const r = await operazione('readonly', s => s.get(chiave(id)));
    return r ? r.blob : null;
  }

  async function elimina(id) {
    await operazione('readwrite', s => s.delete(chiave(id)));
    elenco.delete(id);
  }

  return {
    apri, salva, file, elimina,
    ha: id => elenco.has(id),
    info: id => elenco.get(id),
    ids: () => [...elenco.keys()],
    totale: () => [...elenco.values()].reduce((t, m) => t + (m.size || 0), 0),
  };
})();

const mb = byte => (byte / 1048576).toFixed(byte < 10 * 1048576 ? 1 : 0).replace('.', ',') + ' MB';

/** Canzone da mostrare anche se offline il catalogo non c'è. */
function canzoneScaricata(id) {
  if (stato.perId.has(id)) return stato.perId.get(id);
  const m = Offline.info(id);
  return m ? { id, titolo: m.titolo, artista: m.artista, videoId: m.videoId, durata: m.durata } : null;
}

// ---------- coda dei download ----------

const Download = (() => {
  const stati = new Map(); // id -> { fase: 'attesa'|'preparazione'|'scarico', perc, ctrl }
  const coda = [];
  const inAttesaDi = new Map(); // id -> [funzioni da chiamare quando il file è pronto (true) o fallito (false)]
  const MAX_PARALLELI = 3; // download contemporanei (sul server ognuno è un processo yt-dlp separato)
  let attivi = 0;
  let disponibili = true; // il server ha i download attivi?
  let pausaFino = 0;       // dopo un blocco di YouTube i download automatici si fermano un po'

  function aggiornaUI() {
    document.querySelectorAll('[data-dl]').forEach(disegna);
    const nav = $('#nav-scaricati-n');
    if (nav) nav.textContent = Offline.ids().length ? String(Offline.ids().length) : '';
    document.body.classList.toggle('download-attivi', disponibili);
  }

  function disegna(b) {
    {
      const id = Number(b.dataset.dl);
      const st = stati.get(id);
      b.classList.toggle('scaricata', Offline.ha(id));
      b.classList.toggle('in-corso', !!st);
      if (st) {
        b.textContent = st.fase === 'scarico' ? `${st.perc}%` : '…';
        b.title = st.fase === 'attesa' ? 'In coda (tocca per annullare)' : st.fase === 'preparazione' ? 'Il server sta scaricando da YouTube… (tocca per annullare)' : 'Download in corso (tocca per annullare)';
      } else if (Offline.ha(id)) {
        b.textContent = '✓';
        b.title = 'Scaricata: disponibile offline';
      } else {
        b.textContent = '↓';
        b.title = 'Scarica per ascoltare offline';
      }
      b.setAttribute('aria-label', b.title);
      b.hidden = !disponibili && !Offline.ha(id);
    }
  }

  function formato() { return leggiPref('formato', 'audio'); }

  async function esegui(c) {
    const st = stati.get(c.id);
    st.fase = 'preparazione';
    aggiornaUI();
    const fmt = formato();
    const r = await fetch(`/api/scarica/${c.id}?formato=${fmt}`, { credentials: 'same-origin', signal: st.ctrl.signal });
    if (!r.ok) {
      let msg = `Errore ${r.status}`;
      try { msg = (await r.json()).errore || msg; } catch { /* niente */ }
      throw new Error(msg);
    }
    const tot = Number(r.headers.get('content-length')) || 0;
    const tipo = r.headers.get('content-type') || 'audio/mp4';
    st.fase = 'scarico';
    st.perc = 0;
    const lettore = r.body.getReader();
    const pezzi = [];
    let letti = 0, ultimo = 0;
    for (;;) {
      const { done, value } = await lettore.read();
      if (done) break;
      pezzi.push(value);
      letti += value.length;
      if (tot && Date.now() - ultimo > 250) { st.perc = Math.min(99, Math.floor((letti / tot) * 100)); ultimo = Date.now(); aggiornaUI(); }
    }
    await Offline.salva(c, fmt, new Blob(pezzi, { type: tipo }));
    // salva anche la copertina, così si vede offline
    if ('caches' in window) {
      caches.open('ivibe-img').then(cache => Promise.all(['default', 'hqdefault'].map(q =>
        fetch(thumb(c.videoId, q), { mode: 'no-cors' }).then(res => cache.put(thumb(c.videoId, q), res))))).catch(() => {});
    }
  }

  function prossimo() {
    while (attivi < MAX_PARALLELI && coda.length) avviaDownload(coda.shift());
  }

  async function avviaDownload(c) {
    attivi++;
    let ok = false;
    try {
      await esegui(c);
      ok = true;
      if (!stati.get(c.id)?.silenzioso) toast(`"${c.titolo}" disponibile offline ✓`);
    } catch (e) {
      if (e.name !== 'AbortError') {
        const silenzioso = stati.get(c.id)?.silenzioso;
        // se YouTube blocca il server è inutile continuare: stop ai download automatici per 10 minuti
        if (/bloccato|disattivati|non è installato/.test(e.message)) {
          if (Date.now() > pausaFino) toast(e.message, true);
          pausaFino = Date.now() + 10 * 60 * 1000;
          for (const x of coda.splice(0)) {
            stati.delete(x.id);
            for (const fn of inAttesaDi.get(x.id) || []) fn(false);
            inAttesaDi.delete(x.id);
          }
        } else if (!silenzioso) {
          toast(`"${c.titolo}": ${e.message}`, true);
        }
      }
    } finally {
      stati.delete(c.id);
      for (const fn of inAttesaDi.get(c.id) || []) fn(ok);
      inAttesaDi.delete(c.id);
      attivi--;
      aggiornaUI();
      if (rottaCorrente().vista === 'scaricati') render();
      prossimo();
    }
  }

  /**
   * silenzioso: nessun messaggio (download automatici)
   * subito: passa davanti agli altri in coda (canzone che si vuole ascoltare adesso)
   */
  function scarica(c, silenzioso = false, subito = false) {
    if (Offline.ha(c.id)) return false;
    if (stati.has(c.id)) {
      if (subito) { // ancora in attesa: la faccio partire subito
        const i = coda.findIndex(x => x.id === c.id);
        if (i !== -1) { coda.splice(i, 1); avviaDownload(c); }
      }
      return false;
    }
    if (!navigator.onLine) { if (!silenzioso) toast('Sei offline: collegati a internet per scaricare', true); return false; }
    if (!disponibili) { if (!silenzioso) toast('I download non sono disponibili su questo sito', true); return false; }
    if (silenzioso && !subito && Date.now() < pausaFino) return false;
    if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
    stati.set(c.id, { fase: 'attesa', perc: 0, ctrl: new AbortController(), silenzioso });
    if (subito) { avviaDownload(c); return true; } // non aspetta il proprio turno
    coda.push(c);
    aggiornaUI();
    prossimo();
    return true;
  }

  function annulla(id) {
    const st = stati.get(id);
    if (!st) return;
    st.ctrl.abort();
    const i = coda.findIndex(x => x.id === id);
    if (i !== -1) coda.splice(i, 1);
    stati.delete(id);
    aggiornaUI();
  }

  function scaricaTutte(lista) {
    const n = lista.filter(c => scarica(c, true)).length;
    toast(n ? `${n} ${n === 1 ? 'canzone' : 'canzoni'} in download` : 'Sono già tutte scaricate');
  }

  async function rimuovi(c) {
    annulla(c.id);
    await Offline.elimina(c.id);
    if (Player.corrente() && Player.corrente().id === c.id) toast('Rimossa: finirà di suonare questa volta');
    aggiornaUI();
  }

  async function controllaServer() {
    try { disponibili = !!(await api('GET', '/api/scarica-stato')).attivi; } catch { /* offline: lasciamo com'è */ }
    aggiornaUI();
  }

  /** Chiama fn(true/false) quando il download della canzone finisce. */
  function quandoPronta(id, fn) {
    if (!inAttesaDi.has(id)) inAttesaDi.set(id, []);
    inAttesaDi.get(id).push(fn);
  }

  const automatico = () => leggiPref('autoScarica', true);

  return { scarica, scaricaTutte, annulla, rimuovi, quandoPronta, automatico, inCorso: id => stati.has(id), aggiornaUI, disegna, controllaServer, formato, disponibili: () => disponibili };
})();

function pulsanteDownload(c) {
  const b = h('button', {
    class: 'btn-icon dl', dataset: { dl: c.id },
    onclick: e => {
      e.stopPropagation();
      if (Download.inCorso(c.id)) { Download.annulla(c.id); toast('Download annullato'); }
      else if (Offline.ha(c.id)) menuCanzone(e.currentTarget, c);
      else Download.scarica(c);
    },
  });
  Download.disegna(b);
  return b;
}

// ===================== AZIONI SU PREFERITI E PLAYLIST =====================

async function togglePreferito(c) {
  const era = isPreferito(c.id);
  try {
    const d = await api(era ? 'DELETE' : 'PUT', `/api/preferiti/${c.id}`);
    stato.preferiti = d.preferiti;
    toast(era ? 'Tolta dai preferiti' : 'Aggiunta ai preferiti ♥');
    aggiornaCuori();
    if (rottaCorrente().vista === 'preferiti') render();
  } catch (e) { toast(e.message, true); }
}

function aggiornaCuori() {
  document.querySelectorAll('[data-like]').forEach(b => {
    const on = isPreferito(Number(b.dataset.like));
    b.classList.toggle('on', on);
    b.textContent = on ? '♥' : '♡';
    b.setAttribute('aria-label', on ? 'Togli dai preferiti' : 'Aggiungi ai preferiti');
  });
  const cur = Player.corrente();
  const np = $('#np-like');
  if (cur) {
    np.dataset.like = cur.id;
    const on = isPreferito(cur.id);
    np.classList.toggle('on', on);
    np.textContent = on ? '♥' : '♡';
  }
}

function aggiornaPlaylistLocale(pl) {
  const i = stato.playlist.findIndex(p => p.id === pl.id);
  if (i === -1) stato.playlist.push(pl); else stato.playlist[i] = pl;
}

async function creaPlaylist(conCanzone) {
  const pl = await dialogo({
    titolo: 'Nuova playlist', campi: [{ name: 'nome', label: 'Nome', max: 60 }], ok: 'Crea',
    azione: async v => {
      const d = await api('POST', '/api/playlist', { nome: v.nome });
      let p = d.playlist;
      if (conCanzone) p = (await api('PUT', `/api/playlist/${p.id}/canzoni/${conCanzone.id}`)).playlist;
      return p;
    },
  });
  if (!pl) return null;
  aggiornaPlaylistLocale(pl);
  toast(conCanzone ? `Aggiunta a "${pl.nome}"` : 'Playlist creata');
  return pl;
}

async function impostaInPlaylist(pl, c, presente) {
  try {
    const d = await api(presente ? 'PUT' : 'DELETE', `/api/playlist/${pl.id}/canzoni/${c.id}`);
    aggiornaPlaylistLocale(d.playlist);
    toast(presente ? `Aggiunta a "${pl.nome}"` : `Tolta da "${pl.nome}"`);
    return d.playlist;
  } catch (e) { toast(e.message, true); return null; }
}

/** Fa scaricare il file "vero" (finisce nella cartella Download del dispositivo). */
function salvaFile(c) {
  toast('Preparazione del file… può volerci qualche secondo');
  const a = h('a', { href: `/api/scarica/${c.id}?formato=${Download.formato()}`, download: '' });
  document.body.append(a);
  a.click();
  a.remove();
}

function menuCanzone(btn, c) {
  const voci = [{ titolo: 'Aggiungi a playlist' }];
  for (const pl of stato.playlist) {
    const dentro = pl.canzoni.includes(c.id);
    voci.push({ testo: pl.nome, check: dentro, azione: async () => { await impostaInPlaylist(pl, c, !dentro); if (rottaCorrente().vista === 'playlist') render(); } });
  }
  voci.push({ testo: '+ Nuova playlist…', azione: async () => { await creaPlaylist(c); if (rottaCorrente().vista === 'playlist') render(); } });
  voci.push('-');
  voci.push({ testo: isPreferito(c.id) ? 'Togli dai preferiti' : 'Aggiungi ai preferiti', azione: () => togglePreferito(c) });
  voci.push({ testo: 'Riproduci dopo', azione: () => { Player.accoda(c); toast('Aggiunta alla coda'); } });
  if (Offline.ha(c.id)) {
    const info = Offline.info(c.id);
    voci.push('-', { titolo: `Offline · ${info.formato === 'video' ? 'video' : 'audio'} · ${mb(info.size)}` });
    voci.push({ testo: 'Rimuovi dal dispositivo', azione: () => Download.rimuovi(c).then(() => { toast('Download rimosso'); if (rottaCorrente().vista === 'scaricati') render(); }) });
  } else if (Download.inCorso(c.id)) {
    voci.push('-', { testo: 'Annulla download', azione: () => Download.annulla(c.id) });
  } else if (Download.disponibili()) {
    voci.push('-', { testo: '↓ Scarica per ascoltare offline', azione: () => Download.scarica(c) });
  }
  if (Download.disponibili() && navigator.onLine) {
    voci.push({ testo: `Salva il file sul dispositivo (.${Download.formato() === 'video' ? 'mp4' : 'm4a'})`, azione: () => salvaFile(c) });
  }
  voci.push({ testo: 'Apri su YouTube ↗', azione: () => window.open(`https://www.youtube.com/watch?v=${c.videoId}`, '_blank', 'noopener') });
  apriMenu(btn, voci);
}

// ===================== LISTA CANZONI =====================

/**
 * opzioni.extra(c, i) -> pulsanti aggiuntivi (es. sposta / rimuovi nella playlist)
 */
function listaCanzoni(canzoni, opzioni = {}) {
  if (!canzoni.length) return h('div', { class: 'empty', text: opzioni.vuoto || 'Nessuna canzone' });
  const cur = Player.corrente();
  return h('div', { class: 'songs', role: 'list' }, canzoni.map((c, i) => {
    const like = h('button', {
      class: 'btn-icon like' + (isPreferito(c.id) ? ' on' : ''), dataset: { like: c.id },
      text: isPreferito(c.id) ? '♥' : '♡', 'aria-label': 'Preferito',
      onclick: e => { e.stopPropagation(); togglePreferito(c); },
    });
    const altro = h('button', {
      class: 'btn-icon', text: '⋯', title: 'Altro', 'aria-label': 'Altre azioni',
      onclick: e => { e.stopPropagation(); menuCanzone(e.currentTarget, c); },
    });
    return h('div', {
      class: 'song' + (cur && cur.id === c.id ? ' playing' : '') + (Offline.ha(c.id) ? '' : ' non-scaricata'), role: 'listitem', tabindex: 0, dataset: { song: c.id },
      onclick: () => Player.riproduci(c, canzoni),
      onkeydown: e => { if (e.key === 'Enter' && e.target === e.currentTarget) Player.riproduci(c, canzoni); },
    },
      h('span', { class: 's-n', text: i + 1 }),
      h('img', { class: 's-thumb', src: thumb(c.videoId), alt: '', loading: 'lazy', width: 44, height: 44 }),
      h('div', { class: 's-text' }, h('div', { class: 's-title', text: c.titolo }), h('div', { class: 's-artist', text: c.artista })),
      h('span', { class: 's-dur', text: c.durata ? durata(c.durata) : '' }),
      h('div', { class: 's-actions' }, pulsanteDownload(c), like, opzioni.extra ? opzioni.extra(c, i) : null, altro),
    );
  }));
}

function evidenziaInRiproduzione() {
  const cur = Player.corrente();
  document.querySelectorAll('[data-song]').forEach(el => el.classList.toggle('playing', !!cur && Number(el.dataset.song) === cur.id));
}

function filtra(canzoni, testo) {
  const f = norm(testo.trim());
  if (!f) return canzoni;
  return canzoni.filter(c => norm(c.titolo).includes(f) || norm(c.artista).includes(f));
}

function campoRicerca(chiave, onCambia, placeholder = 'Cerca titolo o artista…') {
  const input = h('input', { type: 'search', placeholder, value: stato.filtri[chiave], 'aria-label': 'Cerca' });
  input.addEventListener('input', () => { stato.filtri[chiave] = input.value; onCambia(); });
  return h('div', { class: 'search' }, input);
}

// ===================== VISTE =====================

function rottaCorrente() {
  const parti = location.hash.replace(/^#\/?/, '').split('/');
  return { vista: parti[0] || 'home', id: parti[1] ? Number(parti[1]) : null };
}

function intestazioneMobile() {
  return h('div', { class: 'mobile-head' },
    h('a', { class: 'brand', href: '#/home' }, h('img', { src: '/logo-192.png', alt: '', width: 30, height: 30 }), h('span', { class: 'brand-name', text: 'iVibe' })),
    h('button', { class: 'btn btn-ghost btn-sm', text: 'Esci', onclick: logout }));
}

function vistaElenco(chiave, titolo, sottotitolo, sorgente, vuoto, conScaricaTutte = false) {
  const elenco = h('div');
  const conta = h('p', { class: 'sub' });
  const aggiorna = () => {
    const lista = filtra(sorgente(), stato.filtri[chiave]);
    conta.textContent = sottotitolo(lista);
    elenco.replaceChildren(listaCanzoni(lista, { vuoto: stato.filtri[chiave] ? 'Nessun risultato' : vuoto }));
    return lista;
  };
  const corrente = () => filtra(sorgente(), stato.filtri[chiave]);
  const nodo = h('section', {},
    intestazioneMobile(),
    h('div', { class: 'page-head' },
      h('div', {}, h('h1', { text: titolo }), conta),
      h('div', { class: 'actions' },
        h('button', { class: 'btn btn-primary', text: '▶ Riproduci', onclick: () => { const l = corrente(); if (l.length) Player.riproduci(l[0], l); } }),
        h('button', { class: 'btn btn-ghost', text: '⤮ Casuale', onclick: () => { const l = corrente(); if (l.length) Player.casuale(l); } }),
        conScaricaTutte ? h('button', { class: 'btn btn-ghost solo-download', text: '↓ Scarica tutte', onclick: () => Download.scaricaTutte(corrente()) }) : null)),
    h('div', { class: 'actions', style: 'margin-bottom:14px' }, campoRicerca(chiave, aggiorna)),
    elenco);
  aggiorna();
  return nodo;
}

function vistaHome() {
  const ora = new Date().getHours();
  const saluto = ora < 6 ? 'Buonanotte' : ora < 13 ? 'Buongiorno' : ora < 18 ? 'Buon pomeriggio' : 'Buonasera';
  return vistaElenco('home', `${saluto}, ${stato.utente.nickname}`,
    l => `${l.length} canzoni nel catalogo`, () => stato.canzoni, 'Il catalogo è vuoto');
}

function vistaPreferiti() {
  return vistaElenco('preferiti', 'Preferiti',
    l => l.length === 1 ? '1 canzone' : `${l.length} canzoni`, () => canzoniDi(stato.preferiti).reverse(),
    'Tocca ♡ su una canzone per aggiungerla qui', true);
}

function copertina(pl) {
  const cs = canzoniDi(pl.canzoni).slice(0, 4);
  if (!cs.length) return h('div', { class: 'pl-cover single' }, h('div', { class: 'ph', text: '♪' }));
  if (cs.length < 4) return h('div', { class: 'pl-cover single' }, h('img', { src: thumb(cs[0].videoId, 'mqdefault'), alt: '', loading: 'lazy' }));
  return h('div', { class: 'pl-cover' }, cs.map(c => h('img', { src: thumb(c.videoId, 'mqdefault'), alt: '', loading: 'lazy' })));
}

function vistaPlaylist() {
  const input = h('input', { placeholder: 'Nome della nuova playlist', maxlength: 60, 'aria-label': 'Nome playlist' });
  const crea = async e => {
    e.preventDefault();
    try {
      const d = await api('POST', '/api/playlist', { nome: input.value });
      aggiornaPlaylistLocale(d.playlist);
      location.hash = `#/playlist/${d.playlist.id}`;
    } catch (err) { toast(err.message, true); }
  };
  return h('section', {},
    intestazioneMobile(),
    h('div', { class: 'page-head' },
      h('div', {}, h('h1', { text: 'Le tue playlist' }), h('p', { class: 'sub', text: stato.playlist.length ? `${stato.playlist.length} playlist` : 'Non hai ancora nessuna playlist' }))),
    h('form', { class: 'inline-form', style: 'max-width:480px;margin-bottom:22px', onsubmit: crea }, input, h('button', { class: 'btn btn-primary', type: 'submit', text: 'Crea' })),
    stato.playlist.length
      ? h('div', { class: 'pl-grid' }, stato.playlist.map(pl => h('a', { class: 'pl-card', href: `#/playlist/${pl.id}` },
        copertina(pl), h('div', { class: 'pl-name', text: pl.nome }), h('div', { class: 'pl-count', text: `${pl.canzoni.length} ${pl.canzoni.length === 1 ? 'canzone' : 'canzoni'}` }))))
      : h('div', { class: 'empty', text: 'Crea la tua prima playlist qui sopra ↑' }));
}

function vistaDettaglioPlaylist(id) {
  const pl = stato.playlist.find(p => p.id === id);
  if (!pl) return h('section', {}, h('a', { class: 'back', href: '#/playlist', text: '← Playlist' }), h('div', { class: 'empty', text: 'Playlist non trovata' }));

  const canzoni = canzoniDi(pl.canzoni);
  const totale = canzoni.reduce((s, c) => s + (c.durata || 0), 0);

  const sposta = async (i, delta) => {
    const ids = [...pl.canzoni];
    const j = i + delta;
    if (j < 0 || j >= ids.length) return;
    [ids[i], ids[j]] = [ids[j], ids[i]];
    try { aggiornaPlaylistLocale((await api('PATCH', `/api/playlist/${pl.id}`, { canzoni: ids })).playlist); render(); } catch (e) { toast(e.message, true); }
  };

  const extra = (c, i) => [
    h('button', { class: 'btn-icon', text: '↑', title: 'Sposta su', 'aria-label': 'Sposta su', disabled: i === 0, onclick: e => { e.stopPropagation(); sposta(pl.canzoni.indexOf(c.id), -1); } }),
    h('button', { class: 'btn-icon', text: '↓', title: 'Sposta giù', 'aria-label': 'Sposta giù', disabled: i === canzoni.length - 1, onclick: e => { e.stopPropagation(); sposta(pl.canzoni.indexOf(c.id), 1); } }),
    h('button', { class: 'btn-icon', text: '✕', title: 'Togli dalla playlist', 'aria-label': 'Togli dalla playlist', onclick: async e => { e.stopPropagation(); if (await impostaInPlaylist(pl, c, false)) render(); } }),
  ];

  // pannello per aggiungere canzoni
  const risultati = h('div');
  const aggiornaRisultati = () => {
    const attuale = stato.playlist.find(p => p.id === id) || pl;
    const lista = filtra(stato.canzoni, stato.filtri.aggiungi).filter(c => !attuale.canzoni.includes(c.id)).slice(0, 40);
    risultati.replaceChildren(lista.length ? h('div', { class: 'songs' }, lista.map(c => h('div', { class: 'song', style: 'grid-template-columns:44px minmax(0,1fr) auto', onclick: async () => { if (await impostaInPlaylist(attuale, c, true)) render(); } },
      h('img', { class: 's-thumb', src: thumb(c.videoId), alt: '', loading: 'lazy', width: 44, height: 44 }),
      h('div', { class: 's-text' }, h('div', { class: 's-title', text: c.titolo }), h('div', { class: 's-artist', text: c.artista })),
      h('button', { class: 'btn btn-ghost btn-sm', text: '+ Aggiungi', tabindex: -1 })))) : h('div', { class: 'empty', text: 'Nessun risultato' }));
  };
  aggiornaRisultati();

  const rinomina = () => dialogo({
    titolo: 'Rinomina playlist', campi: [{ name: 'nome', label: 'Nome', value: pl.nome, max: 60 }], ok: 'Salva',
    azione: async v => { aggiornaPlaylistLocale((await api('PATCH', `/api/playlist/${pl.id}`, { nome: v.nome })).playlist); },
  }).then(r => { if (r) render(); });

  const elimina = () => dialogo({
    titolo: 'Eliminare la playlist?', testo: `"${pl.nome}" verrà eliminata. Le canzoni restano nel catalogo.`, ok: 'Elimina', pericolo: true,
    azione: async () => { await api('DELETE', `/api/playlist/${pl.id}`); },
  }).then(r => {
    if (!r) return;
    stato.playlist = stato.playlist.filter(p => p.id !== pl.id);
    toast('Playlist eliminata');
    location.hash = '#/playlist';
  });

  return h('section', {},
    intestazioneMobile(),
    h('a', { class: 'back', href: '#/playlist', text: '← Playlist' }),
    h('div', { class: 'page-head' },
      h('div', { style: 'display:flex;gap:18px;align-items:flex-end' },
        h('div', { style: 'width:120px;flex:none' }, copertina(pl)),
        h('div', {}, h('h1', { text: pl.nome }), h('p', { class: 'sub', text: `${canzoni.length} ${canzoni.length === 1 ? 'canzone' : 'canzoni'}${totale ? ' · ' + durata(totale) : ''}` }))),
      h('div', { class: 'actions' },
        h('button', { class: 'btn btn-primary', text: '▶ Riproduci', disabled: !canzoni.length, onclick: () => Player.riproduci(canzoni[0], canzoni) }),
        h('button', { class: 'btn btn-ghost', text: '⤮ Casuale', disabled: !canzoni.length, onclick: () => Player.casuale(canzoni) }),
        h('button', { class: 'btn btn-ghost solo-download', text: '↓ Scarica', disabled: !canzoni.length, onclick: () => Download.scaricaTutte(canzoni) }),
        h('button', { class: 'btn btn-ghost', text: 'Rinomina', onclick: rinomina }),
        h('button', { class: 'btn btn-danger', text: 'Elimina', onclick: elimina }))),
    listaCanzoni(canzoni, { extra, vuoto: 'Playlist vuota: aggiungi canzoni qui sotto' }),
    h('div', { class: 'card', style: 'margin-top:26px' },
      h('h2', { text: 'Aggiungi canzoni' }),
      h('div', { style: 'margin-bottom:10px' }, campoRicerca('aggiungi', aggiornaRisultati)),
      risultati));
}

// ---------- Scaricati (offline) ----------

function vistaScaricati() {
  const lista = Offline.ids().map(canzoneScaricata).filter(Boolean).reverse();
  const fmt = Download.formato();
  const scelta = (val, testo, nota) => h('label', { class: 'scelta' + (fmt === val ? ' on' : '') },
    h('input', { type: 'radio', name: 'formato', value: val, checked: fmt === val, onchange: () => { salvaPref('formato', val); render(); } }),
    h('span', {}, h('strong', { text: testo }), h('small', { text: nota })));

  const svuota = () => dialogo({
    titolo: 'Eliminare tutti i download?', testo: `Verranno liberati ${mb(Offline.totale())} sul dispositivo. Le canzoni restano nel catalogo.`,
    ok: 'Elimina tutti', pericolo: true,
    azione: async () => { for (const id of Offline.ids()) await Offline.elimina(id); },
  }).then(r => { if (r) { toast('Download eliminati'); Download.aggiornaUI(); render(); } });

  const spazio = h('span');
  if (navigator.storage && navigator.storage.estimate) {
    navigator.storage.estimate().then(e => { if (e.quota) spazio.textContent = ` · spazio libero circa ${mb(Math.max(0, e.quota - e.usage))}`; }).catch(() => {});
  }

  return h('section', {},
    intestazioneMobile(),
    h('div', { class: 'page-head' },
      h('div', {}, h('h1', { text: 'Scaricati' }),
        h('p', { class: 'sub' }, lista.length ? `${lista.length} ${lista.length === 1 ? 'canzone' : 'canzoni'} · ${mb(Offline.totale())}` : 'Nessuna canzone scaricata', spazio)),
      h('div', { class: 'actions' },
        h('button', { class: 'btn btn-primary', text: '▶ Riproduci', disabled: !lista.length, onclick: () => Player.riproduci(lista[0], lista) }),
        h('button', { class: 'btn btn-ghost', text: '⤮ Casuale', disabled: !lista.length, onclick: () => Player.casuale(lista) }),
        lista.length ? h('button', { class: 'btn btn-danger', text: 'Elimina tutti', onclick: svuota }) : null)),
    Download.disponibili() ? h('div', { class: 'card', style: 'margin-bottom:20px' },
      h('h2', { text: 'Cosa scaricare' }),
      h('div', { class: 'scelte' },
        scelta('audio', 'Solo audio', 'M4A · circa 4 MB a canzone · consigliato'),
        scelta('video', 'Video', 'MP4 360p · circa 15-25 MB a canzone')),
      h('label', { class: 'scelta' + (Download.automatico() ? ' on' : ''), style: 'margin-top:10px' },
        h('input', { type: 'checkbox', checked: Download.automatico(), onchange: e => { salvaPref('autoScarica', e.target.checked); render(); } }),
        h('span', {}, h('strong', { text: 'Scarica in automatico quello che ascolti' }),
          h('small', { text: 'Ogni canzone che fai partire viene salvata sul dispositivo: la volta dopo parte subito e funziona offline.' }))),
      h('p', { class: 'muted', style: 'margin:12px 0 0;font-size:14px', text: 'Puoi anche toccare ↓ accanto a una canzone, oppure "Scarica" in una playlist o nei preferiti. Le canzoni che YouTube non fa riprodurre sul sito vengono scaricate e fatte partire in automatico.' }))
      : h('div', { class: 'notice', text: 'I download non sono disponibili su questo sito: puoi ascoltare solo quello che hai già scaricato.' }),
    listaCanzoni(lista, { vuoto: 'Qui trovi le canzoni che scarichi per ascoltarle offline' }));
}

// ---------- Impostazioni ----------

function vistaImpostazioni() {
  const u = stato.utente;
  const errPw = h('p', { class: 'form-err' });
  const formPw = h('form', { class: 'form', novalidate: true, onsubmit: async e => {
    e.preventDefault();
    const f = new FormData(formPw);
    errPw.textContent = '';
    if (f.get('nuova') !== f.get('conferma')) { errPw.textContent = 'Le password non coincidono'; return; }
    try {
      await api('POST', '/api/account/password', { attuale: f.get('attuale'), nuova: f.get('nuova') });
      formPw.reset();
      toast('Password aggiornata ✓');
    } catch (err) { errPw.textContent = err.message; }
  } },
  h('label', {}, 'Password attuale', h('input', { name: 'attuale', type: 'password', autocomplete: 'current-password' })),
  h('label', {}, 'Nuova password', h('input', { name: 'nuova', type: 'password', autocomplete: 'new-password' }), h('small', { class: 'hint', text: 'Almeno 7 caratteri, con una maiuscola, un numero e un simbolo.' })),
  h('label', {}, 'Conferma nuova password', h('input', { name: 'conferma', type: 'password', autocomplete: 'new-password' })),
  errPw,
  h('button', { class: 'btn btn-primary', type: 'submit', text: 'Aggiorna password' }));

  const elimina = () => dialogo({
    titolo: 'Eliminare il tuo account?', testo: 'Preferiti e playlist verranno cancellati definitivamente.\nPer confermare scrivi la tua password.',
    campi: [{ name: 'password', label: 'Password', type: 'password', autocomplete: 'current-password' }], ok: 'Elimina account', pericolo: true,
    azione: async v => { await api('POST', '/api/account/elimina', { password: v.password }); },
  }).then(r => { if (r) { toast('Account eliminato'); esciLocale(); } });

  const riga = (k, v) => h('div', { style: 'display:flex;justify-content:space-between;gap:12px;padding:8px 0;border-bottom:1px solid var(--line)' }, h('span', { class: 'muted', text: k }), h('strong', { text: v }));

  return h('section', {},
    intestazioneMobile(),
    h('div', { class: 'page-head' }, h('div', {}, h('h1', { text: 'Impostazioni' }))),
    h('div', { class: 'grid-2' },
      h('div', { class: 'card' }, h('h2', { text: 'Il tuo account' }),
        riga('Username', u.nickname), riga('Email', u.email), riga('Ruolo', u.isAdmin ? 'Admin' : 'Utente'),
        u.dataNascita ? riga('Data di nascita', new Date(u.dataNascita + 'T00:00:00').toLocaleDateString('it-IT')) : null,
        h('div', { class: 'actions', style: 'margin-top:16px' },
          h('button', { class: 'btn btn-ghost', text: 'Esci', onclick: logout }),
          h('button', { class: 'btn btn-danger', text: 'Elimina account', onclick: elimina }))),
      h('div', { class: 'card' }, h('h2', { text: 'Cambia password' }), formPw)));
}

// ---------- Admin ----------

function pulisciTitolo(t) {
  return t.replace(/\s*[([](official|video|audio|lyric|lyrics|testo|hd|hq|4k|remaster|visualizer|music video)[^)\]]*[)\]]/gi, '').trim();
}

let schedaAdmin = 'canzoni';

function vistaAdmin() {
  if (!stato.utente.isAdmin) return h('div', { class: 'empty', text: 'Solo per admin' });
  const contenitore = h('div');
  const tab = (id, testo) => h('button', { class: 'tab', role: 'tab', 'aria-selected': String(schedaAdmin === id), text: testo,
    onclick: () => { schedaAdmin = id; render(); } });

  const nodo = h('section', {},
    intestazioneMobile(),
    h('div', { class: 'page-head' }, h('div', {}, h('h1', { text: 'Pannello admin' }), h('p', { class: 'sub', id: 'admin-sub' }))),
    h('div', { class: 'tabs', style: 'max-width:320px;margin-bottom:18px' }, tab('canzoni', 'Canzoni'), tab('utenti', 'Utenti')),
    h('div', { id: 'admin-notice' }),
    contenitore);

  api('GET', '/api/admin/utenti').then(d => {
    if (d.archivio === 'file') {
      $('#admin-notice')?.replaceChildren(h('div', { class: 'notice', text:
        '⚠ I dati sono salvati su file. Su Render gratis il file si azzera a ogni riavvio: nuovi utenti, preferiti e playlist si perdono. Collega un database Postgres (vedi README) per tenerli.' }));
    }
    if (schedaAdmin === 'utenti') contenitore.replaceChildren(adminUtenti(d.utenti));
    const sub = $('#admin-sub');
    if (sub) sub.textContent = `${stato.canzoni.length} canzoni · ${d.utenti.length} utenti`;
  }).catch(e => toast(e.message, true));

  if (schedaAdmin === 'canzoni') contenitore.replaceChildren(adminCanzoni());
  else contenitore.replaceChildren(h('div', { class: 'empty', text: 'Caricamento…' }));
  return nodo;
}

function adminCanzoni() {
  const err = h('p', { class: 'form-err' });
  const anteprima = h('div');
  const f = {
    url: h('input', { name: 'url', placeholder: 'https://www.youtube.com/watch?v=…', 'aria-label': 'Link YouTube' }),
    titolo: h('input', { name: 'titolo', maxlength: 150 }),
    artista: h('input', { name: 'artista', maxlength: 150 }),
    durata: h('input', { name: 'durata', placeholder: '3:45', maxlength: 8 }),
  };

  let ultimaUrl = '';
  const cercaInfo = async () => {
    const url = f.url.value.trim();
    if (!url || url === ultimaUrl) return;
    ultimaUrl = url;
    err.textContent = '';
    try {
      const d = await api('POST', '/api/admin/anteprima', { url });
      const info = d.info;
      anteprima.replaceChildren(h('div', { class: 'preview' },
        h('img', { src: thumb(d.videoId, 'mqdefault'), alt: '' }),
        h('div', {},
          h('div', { style: 'font-weight:700', text: info && info.titolo ? info.titolo : 'Video ' + d.videoId }),
          h('div', { class: 'muted', text: d.gia ? `⚠ Già nel catalogo come "${d.gia}"` : info && info.incorporabile === false ? '⚠ Questo video non si può riprodurre fuori da YouTube' : info && info.canale ? info.canale : '' }))));
      if (info && info.titolo && !f.titolo.value && !f.artista.value) {
        const pulito = pulisciTitolo(info.titolo);
        const i = pulito.indexOf(' - ');
        if (i > 0) { f.artista.value = pulito.slice(0, i).trim(); f.titolo.value = pulito.slice(i + 3).trim(); }
        else { f.titolo.value = pulito; f.artista.value = (info.canale || '').replace(/\s*-\s*Topic$|VEVO$/i, '').trim(); }
      }
    } catch (e) { anteprima.replaceChildren(); err.textContent = e.message; }
  };
  f.url.addEventListener('change', cercaInfo);
  f.url.addEventListener('paste', () => setTimeout(cercaInfo, 0));

  const form = h('form', { class: 'form', novalidate: true, onsubmit: async e => {
    e.preventDefault();
    err.textContent = '';
    const btn = form.querySelector('button[type=submit]');
    btn.disabled = true;
    try {
      const d = await api('POST', '/api/admin/canzoni', { url: f.url.value, titolo: f.titolo.value, artista: f.artista.value, durata: f.durata.value });
      stato.canzoni.push(d.canzone);
      stato.perId.set(d.canzone.id, d.canzone);
      toast(`"${d.canzone.titolo}" aggiunta ✓`);
      render();
    } catch (ex) { err.textContent = ex.message; } finally { btn.disabled = false; }
  } },
  h('label', {}, 'Link YouTube', f.url),
  anteprima,
  h('div', { class: 'row-3' }, h('label', {}, 'Titolo', f.titolo), h('label', {}, 'Artista', f.artista), h('label', {}, 'Durata (facoltativa)', f.durata)),
  err,
  h('div', {}, h('button', { class: 'btn btn-primary', type: 'submit', text: '+ Aggiungi canzone' })));

  const corpo = h('tbody');
  const disegna = () => {
    const lista = filtra(stato.canzoni, stato.filtri.admin).slice().reverse();
    corpo.replaceChildren(...lista.map(c => h('tr', {},
      h('td', { class: 'num', text: c.id }),
      h('td', {}, h('img', { src: thumb(c.videoId), alt: '', width: 48, height: 36, loading: 'lazy', style: 'border-radius:6px;object-fit:cover' })),
      h('td', {}, h('strong', { text: c.titolo })),
      h('td', { text: c.artista }),
      h('td', { class: 'num', text: c.durata ? durata(c.durata) : '—' }),
      h('td', { style: 'white-space:nowrap;text-align:right' },
        h('button', { class: 'btn btn-ghost btn-sm', text: 'Modifica', onclick: () => modificaCanzone(c) }), ' ',
        h('button', { class: 'btn btn-danger btn-sm', text: 'Elimina', onclick: () => eliminaCanzone(c) })))));
  };
  disegna();

  return h('div', { class: 'grid-2', style: 'grid-template-columns:1fr' },
    h('div', { class: 'card' }, h('h2', { text: 'Aggiungi una canzone' }), form),
    h('div', { class: 'card' },
      h('div', { class: 'page-head', style: 'margin-bottom:10px' }, h('h2', { text: 'Catalogo', style: 'margin:0' }), campoRicerca('admin', disegna)),
      h('div', { class: 'table-wrap' }, h('table', {},
        h('thead', {}, h('tr', {}, ['ID', '', 'Titolo', 'Artista', 'Durata', ''].map(t => h('th', { text: t })))), corpo))));
}

function modificaCanzone(c) {
  dialogo({
    titolo: 'Modifica canzone',
    campi: [
      { name: 'titolo', label: 'Titolo', value: c.titolo, max: 150 },
      { name: 'artista', label: 'Artista', value: c.artista, max: 150 },
      { name: 'durata', label: 'Durata (es. 3:45)', value: c.durata ? durata(c.durata) : '', max: 8 },
    ],
    ok: 'Salva',
    azione: async v => {
      const d = await api('PATCH', `/api/admin/canzoni/${c.id}`, v);
      Object.assign(c, d.canzone);
    },
  }).then(r => { if (r) { toast('Salvato ✓'); render(); } });
}

function eliminaCanzone(c) {
  dialogo({
    titolo: 'Eliminare la canzone?', testo: `"${c.titolo}" verrà tolta dal catalogo, dai preferiti e dalle playlist di tutti.`,
    ok: 'Elimina', pericolo: true, azione: async () => { await api('DELETE', `/api/admin/canzoni/${c.id}`); },
  }).then(async r => {
    if (!r) return;
    await caricaCatalogo().catch(() => {});
    toast('Canzone eliminata');
    render();
  });
}

function adminUtenti(utenti) {
  return h('div', { class: 'card' }, h('div', { class: 'table-wrap' }, h('table', {},
    h('thead', {}, h('tr', {}, ['ID', 'Username', 'Email', 'Ruolo', ''].map(t => h('th', { text: t })))),
    h('tbody', {}, utenti.map(u => {
      const io = u.id === stato.utente.id;
      return h('tr', {},
        h('td', { class: 'num', text: u.id }),
        h('td', {}, h('strong', { text: u.nickname }), io ? h('span', { class: 'muted', text: ' (tu)' }) : null),
        h('td', { text: u.email }),
        h('td', {}, h('span', { class: 'badge' + (u.isAdmin ? ' admin' : ''), text: u.isAdmin ? 'Admin' : 'Utente' })),
        h('td', { style: 'white-space:nowrap;text-align:right' },
          io ? null : h('button', { class: 'btn btn-ghost btn-sm', text: u.isAdmin ? 'Togli admin' : 'Rendi admin', onclick: async () => {
            try { await api('PATCH', `/api/admin/utenti/${u.id}`, { isAdmin: !u.isAdmin }); toast('Ruolo aggiornato'); render(); } catch (e) { toast(e.message, true); }
          } }), ' ',
          io || u.isAdmin ? null : h('button', { class: 'btn btn-danger btn-sm', text: 'Elimina', onclick: () => dialogo({
            titolo: 'Eliminare l\'utente?', testo: `"${u.nickname}" e tutti i suoi preferiti e playlist verranno cancellati.`, ok: 'Elimina', pericolo: true,
            azione: async () => { await api('DELETE', `/api/admin/utenti/${u.id}`); },
          }).then(r => { if (r) { toast('Utente eliminato'); render(); } }) })));
    })))));
}

// ===================== ROUTER =====================

function render() {
  if (!stato.utente) return;
  chiudiMenu();
  const { vista, id } = rottaCorrente();
  let nodo;
  if (vista === 'home') nodo = vistaHome();
  else if (vista === 'preferiti') nodo = vistaPreferiti();
  else if (vista === 'playlist') nodo = id ? vistaDettaglioPlaylist(id) : vistaPlaylist();
  else if (vista === 'admin') nodo = vistaAdmin();
  else if (vista === 'impostazioni') nodo = vistaImpostazioni();
  else if (vista === 'scaricati') nodo = vistaScaricati();
  else { location.hash = '#/home'; return; }

  const content = $('#content');
  const scroll = window.scrollY;
  const stessa = content.dataset.vista === location.hash;
  content.replaceChildren(nodo);
  content.dataset.vista = location.hash;
  if (stessa) window.scrollTo(0, scroll); else window.scrollTo(0, 0);

  document.querySelectorAll('[data-nav]').forEach(a => a.classList.toggle('active', a.dataset.nav === vista));
  document.title = { home: 'iVibe', preferiti: 'Preferiti · iVibe', playlist: 'Playlist · iVibe', admin: 'Admin · iVibe', impostazioni: 'Impostazioni · iVibe', scaricati: 'Scaricati · iVibe' }[vista];
  Download.aggiornaUI();
}
window.addEventListener('hashchange', render);

// ===================== PLAYER =====================
// Due "motori": il player di YouTube (online) e un elemento <video> che suona
// i file scaricati sul dispositivo (funziona anche senza internet).

const Player = (() => {
  let yt = null, pronto = false, inAttesa = null;
  let motore = null; // 'yt' | 'locale'
  let urlLocale = null;
  let coda = [], ordineOriginale = [], indice = -1;
  let casuale = leggiPref('casuale', false), ripeti = leggiPref('ripeti', false);
  let inRiproduzione = false, trascinando = false, erroriDiFila = 0;
  let videoVisibile = leggiPref('video', false); // di default si sente solo l'audio
  const loc = () => $('#locale');

  function mostraVideo(si, salva = true) {
    videoVisibile = si;
    if (salva) salvaPref('video', si);
    $('#video').classList.toggle('nascosto', !si);
    document.body.classList.toggle('has-video', si && !!corrente());
    const b = $('#btn-video');
    b.setAttribute('aria-pressed', String(si));
    b.title = si ? 'Nascondi video' : 'Mostra video';
    b.setAttribute('aria-label', b.title);
  }

  const corrente = () => (indice >= 0 ? coda[indice] : null);

  // ---- operazioni comuni ai due motori ----
  const tempo = () => (motore === 'locale' ? loc().currentTime : pronto && yt.getCurrentTime ? yt.getCurrentTime() : 0) || 0;
  const totale = () => (motore === 'locale' ? (Number.isFinite(loc().duration) ? loc().duration : 0) : pronto && yt.getDuration ? yt.getDuration() : 0) || 0;
  function vaiA(sec) { if (motore === 'locale') loc().currentTime = sec; else if (pronto) yt.seekTo(sec, true); }
  function impostaVolume(v) { loc().volume = v / 100; if (pronto) yt.setVolume(v); }

  function quandoSuona(si) { inRiproduzione = si; if (si) erroriDiFila = 0; aggiornaPulsantePlay(); }

  function erroreRiproduzione(msg) {
    const c = corrente();
    toast(`"${c ? c.titolo : 'La canzone'}" ${msg}`, true);
    erroriDiFila++;
    if (erroriDiFila < Math.min(5, coda.length)) setTimeout(() => successiva(true), 1200);
    else { inRiproduzione = false; aggiornaPulsantePlay(); }
  }

  function caricaApi() {
    if (window.YT || document.getElementById('yt-api')) return;
    window.onYouTubeIframeAPIReady = () => {
      yt = new YT.Player('yt', {
        host: 'https://www.youtube-nocookie.com',
        playerVars: { autoplay: 1, playsinline: 1, rel: 0, modestbranding: 1, origin: location.origin },
        events: {
          onReady: () => {
            pronto = true;
            yt.setVolume(Number($('#volume').value));
            if (inAttesa && motore === 'yt') yt.loadVideoById(inAttesa);
            inAttesa = null;
          },
          onStateChange: e => {
            if (motore !== 'yt') return;
            if (e.data === YT.PlayerState.PLAYING) quandoSuona(true);
            if (e.data === YT.PlayerState.PAUSED) quandoSuona(false);
            if (e.data === YT.PlayerState.ENDED) { quandoSuona(false); successiva(true); }
          },
          onError: e => {
            if (motore !== 'yt') return;
            const c = corrente();
            // 101/150 = il proprietario non permette di vederlo fuori da YouTube: lo scarichiamo noi
            if ((e.data === 101 || e.data === 150 || e.data === 5) && c && Download.disponibili() && navigator.onLine) {
              suonaDopoDownload(c);
              return;
            }
            erroreRiproduzione(e.data === 101 || e.data === 150 ? 'non si può riprodurre fuori da YouTube'
              : e.data === 100 ? 'non è più disponibile' : 'non si riesce a riprodurre');
          },
        },
      });
    };
    document.head.append(h('script', { id: 'yt-api', src: 'https://www.youtube.com/iframe_api', async: true }));
  }

  /** Scarica la canzone (prima di tutte le altre) e appena è pronta la fa partire. */
  function suonaDopoDownload(c) {
    if (pronto) yt.stopVideo();
    quandoSuona(false);
    toast(`Preparo "${c.titolo}" sul tuo dispositivo, parte tra pochi secondi…`);
    Download.quandoPronta(c.id, ok => {
      if (corrente() !== c) return;
      if (ok) carica(c);
      else erroreRiproduzione('non si riesce a scaricare');
    });
    Download.scarica(c, true, true);
  }

  async function carica(c) {
    if (!c) return;
    $('#player').hidden = false;
    $('#video').hidden = false;
    document.body.classList.add('playing-ui');
    document.body.classList.toggle('has-video', videoVisibile);
    $('#np-title').textContent = c.titolo;
    $('#np-artist').textContent = c.artista;
    $('#np-thumb').src = thumb(c.videoId);
    $('#t-cur').textContent = '0:00';
    $('#t-tot').textContent = c.durata ? durata(c.durata) : '0:00';
    $('#seek').value = 0;
    aggiornaCuori();
    evidenziaInRiproduzione();
    if ('mediaSession' in navigator) {
      try {
        navigator.mediaSession.metadata = new MediaMetadata({ title: c.titolo, artist: c.artista, album: 'iVibe', artwork: [{ src: thumb(c.videoId, 'hqdefault'), sizes: '480x360', type: 'image/jpeg' }] });
      } catch { /* non supportato */ }
    }

    const blob = Offline.ha(c.id) ? await Offline.file(c.id).catch(() => null) : null;
    if (corrente() !== c) return; // nel frattempo è stata scelta un'altra canzone

    if (blob) {
      // file scaricato: suona dal dispositivo, anche offline
      if (pronto) yt.pauseVideo();
      motore = 'locale';
      $('#video').classList.add('motore-locale');
      if (urlLocale) URL.revokeObjectURL(urlLocale);
      urlLocale = URL.createObjectURL(blob);
      const v = loc();
      v.poster = thumb(c.videoId, 'hqdefault');
      v.src = urlLocale;
      v.play().catch(() => quandoSuona(false));
      return;
    }

    // opzione "scarica quello che ascolti": la prossima volta parte dal dispositivo (anche offline)
    if (Download.automatico()) Download.scarica(c, true);

    loc().pause();
    motore = 'yt';
    $('#video').classList.remove('motore-locale');
    if (!navigator.onLine) { erroreRiproduzione('non è scaricata: senza internet non si può ascoltare'); return; }
    if (pronto) yt.loadVideoById(c.videoId);
    else { inAttesa = c.videoId; caricaApi(); }
  }

  function mescola(arr) {
    const a = arr.slice();
    for (let i = a.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i + 1)); [a[i], a[j]] = [a[j], a[i]]; }
    return a;
  }

  function riproduci(c, lista) {
    ordineOriginale = (lista && lista.length ? lista : [c]).slice();
    // offline si saltano le canzoni non scaricate
    if (!navigator.onLine) ordineOriginale = ordineOriginale.filter(x => x.id === c.id || Offline.ha(x.id));
    if (casuale) coda = [c, ...mescola(ordineOriginale.filter(x => x.id !== c.id))];
    else coda = ordineOriginale.slice();
    indice = coda.findIndex(x => x.id === c.id);
    if (indice === -1) { coda.unshift(c); indice = 0; }
    erroriDiFila = 0;
    carica(c);
  }

  function riproduciCasuale(lista) {
    if (!casuale) { casuale = true; salvaPref('casuale', true); aggiornaPulsanti(); }
    const pool = navigator.onLine ? lista : lista.filter(x => Offline.ha(x.id));
    if (!pool.length) { toast('Nessuna canzone scaricata in questa lista', true); return; }
    riproduci(pool[Math.floor(Math.random() * pool.length)], pool);
  }

  function successiva(automatica = false) {
    if (!coda.length) return;
    if (indice + 1 < coda.length) indice++;
    else if (ripeti) indice = 0;
    else { if (automatica) { inRiproduzione = false; aggiornaPulsantePlay(); } return; }
    carica(coda[indice]);
  }

  function precedente() {
    if (tempo() > 3) { vaiA(0); return; }
    if (indice > 0) { indice--; carica(coda[indice]); } else vaiA(0);
  }

  function playPausa() {
    if (motore === 'locale') { const v = loc(); if (v.paused) v.play().catch(() => {}); else v.pause(); return; }
    if (!pronto) return;
    if (inRiproduzione) yt.pauseVideo(); else yt.playVideo();
  }

  function accoda(c) {
    if (!corrente()) { riproduci(c, [c]); return; }
    coda.splice(indice + 1, 0, c);
    ordineOriginale.push(c);
  }

  function aggiornaPulsantePlay() {
    const b = $('#btn-play');
    b.textContent = inRiproduzione ? '❚❚' : '▶';
    b.setAttribute('aria-label', inRiproduzione ? 'Pausa' : 'Play');
    if ('mediaSession' in navigator) navigator.mediaSession.playbackState = inRiproduzione ? 'playing' : 'paused';
  }

  function aggiornaPulsanti() {
    $('#btn-shuffle').setAttribute('aria-pressed', String(casuale));
    $('#btn-repeat').setAttribute('aria-pressed', String(ripeti));
  }

  function toggleCasuale() {
    casuale = !casuale;
    salvaPref('casuale', casuale);
    const c = corrente();
    if (c) {
      if (casuale) coda = [...coda.slice(0, indice + 1), ...mescola(coda.slice(indice + 1))];
      else { coda = ordineOriginale.slice(); indice = Math.max(0, coda.findIndex(x => x.id === c.id)); }
    }
    aggiornaPulsanti();
    toast(casuale ? 'Riproduzione casuale attiva' : 'Riproduzione in ordine');
  }

  function toggleRipeti() {
    ripeti = !ripeti;
    salvaPref('ripeti', ripeti);
    aggiornaPulsanti();
    toast(ripeti ? 'Ripeti la coda: attivo' : 'Ripeti la coda: disattivo');
  }

  function ferma() {
    if (pronto) yt.stopVideo();
    loc().pause();
    loc().removeAttribute('src');
    if (urlLocale) { URL.revokeObjectURL(urlLocale); urlLocale = null; }
    coda = []; indice = -1; inRiproduzione = false; motore = null;
    $('#player').hidden = true;
    $('#video').hidden = true;
    document.body.classList.remove('playing-ui', 'has-video');
  }

  // aggiornamento della barra di avanzamento
  setInterval(() => {
    if (!motore || !corrente() || trascinando) return;
    const tot = totale(), ora = tempo();
    if (tot > 0) {
      $('#seek').value = Math.round((ora / tot) * 1000);
      $('#t-tot').textContent = durata(tot);
    }
    $('#t-cur').textContent = durata(ora);
  }, 500);

  function collega() {
    const v = loc();
    v.addEventListener('play', () => { if (motore === 'locale') quandoSuona(true); });
    v.addEventListener('pause', () => { if (motore === 'locale') quandoSuona(false); });
    v.addEventListener('ended', () => { if (motore === 'locale') { quandoSuona(false); successiva(true); } });
    v.addEventListener('error', () => { if (motore === 'locale' && v.getAttribute('src')) erroreRiproduzione('non si riesce a riprodurre dal file scaricato'); });

    $('#btn-play').onclick = playPausa;
    $('#btn-next').onclick = () => successiva(false);
    $('#btn-prev').onclick = precedente;
    $('#btn-shuffle').onclick = toggleCasuale;
    $('#btn-repeat').onclick = toggleRipeti;
    $('#np-like').onclick = () => { const c = corrente(); if (c) togglePreferito(c); };
    $('#btn-video-size').onclick = () => document.body.classList.toggle('video-big', $('#video').classList.toggle('big'));
    $('#btn-video').onclick = () => mostraVideo(!videoVisibile);
    $('#btn-video-close').onclick = () => mostraVideo(false);
    mostraVideo(videoVisibile, false);

    const seek = $('#seek');
    seek.addEventListener('input', () => { trascinando = true; $('#t-cur').textContent = durata((seek.value / 1000) * totale()); });
    seek.addEventListener('change', () => { vaiA((seek.value / 1000) * totale()); trascinando = false; });

    const vol = $('#volume');
    vol.value = leggiPref('volume', 70);
    impostaVolume(Number(vol.value));
    vol.addEventListener('input', () => { impostaVolume(Number(vol.value)); salvaPref('volume', Number(vol.value)); });

    if ('mediaSession' in navigator) {
      const ms = navigator.mediaSession;
      try {
        ms.setActionHandler('play', () => { if (!inRiproduzione) playPausa(); });
        ms.setActionHandler('pause', () => { if (inRiproduzione) playPausa(); });
        ms.setActionHandler('nexttrack', () => successiva(false));
        ms.setActionHandler('previoustrack', precedente);
      } catch { /* non supportato */ }
    }

    // scorciatoie da tastiera (non mentre si scrive)
    document.addEventListener('keydown', e => {
      if (e.target.closest('input, textarea, select, dialog') || e.ctrlKey || e.metaKey || e.altKey) return;
      if (e.code === 'Space' && corrente()) { e.preventDefault(); playPausa(); }
      else if (e.key === 'n' || e.key === 'N') successiva(false);
      else if (e.key === 'p' || e.key === 'P') precedente();
    });
    aggiornaPulsanti();
  }

  return { riproduci, casuale: riproduciCasuale, accoda, corrente, ferma, collega };
})();

// ===================== ACCESSO =====================

function mostraAuth(scheda = 'login') {
  $('#app').hidden = true;
  $('#auth').hidden = false;
  document.querySelectorAll('[data-auth-tab]').forEach(t => t.setAttribute('aria-selected', String(t.dataset.authTab === scheda)));
  $('#form-login').hidden = scheda !== 'login';
  $('#form-registrazione').hidden = scheda !== 'registrazione';
  document.title = 'iVibe — Accedi';
  ($(`#form-${scheda} input`)).focus();
}

async function entra(utente) {
  stato.utente = utente;
  await Offline.apri(utente.id).catch(() => {});
  try {
    await caricaCatalogo();
  } catch (e) {
    // offline e senza catalogo salvato: si possono comunque ascoltare i download
    if (navigator.onLine) throw e;
    stato.canzoni = []; stato.perId = new Map(); stato.preferiti = []; stato.playlist = [];
    location.hash = '#/scaricati';
  }
  Download.controllaServer();
  $('#auth').hidden = true;
  $('#app').hidden = false;
  $('#who').textContent = utente.nickname;
  $('#avatar').textContent = utente.nickname.slice(0, 1).toUpperCase();
  $('#nav-admin').hidden = !utente.isAdmin;
  if (!location.hash || location.hash === '#') location.hash = '#/home';
  render();
}

function esciLocale() {
  stato.utente = null;
  // i dati dell'account salvati per l'offline non devono restare per il prossimo utente
  if ('caches' in window) caches.delete('ivibe-api').catch(() => {});
  Player.ferma();
  $('#content').replaceChildren();
  delete $('#content').dataset.vista;
  document.querySelectorAll('form').forEach(f => f.reset());
  mostraAuth('login');
}

async function logout() {
  try { await api('POST', '/api/logout'); } catch { /* niente */ }
  esciLocale();
}

function collegaAuth() {
  document.querySelectorAll('[data-auth-tab]').forEach(t => t.addEventListener('click', () => mostraAuth(t.dataset.authTab)));
  document.querySelectorAll('.pw-toggle').forEach(b => b.addEventListener('click', () => {
    const form = b.closest('form');
    const mostra = form.querySelector('input[name=password]').type === 'password';
    form.querySelectorAll('input[name=password], input[name=conferma]').forEach(i => { i.type = mostra ? 'text' : 'password'; });
    b.style.opacity = mostra ? 1 : '';
  }));

  const invia = (form, url, campi) => form.addEventListener('submit', async e => {
    e.preventDefault();
    const err = form.querySelector('.form-err');
    const btn = form.querySelector('button[type=submit]');
    err.textContent = '';
    const dati = Object.fromEntries(campi.map(k => [k, form.elements[k].value]));
    if (Object.values(dati).some(v => !v)) { err.textContent = 'Compila tutti i campi'; return; }
    btn.disabled = true;
    try {
      const d = await api('POST', url, dati);
      await entra(d.utente);
      if (url.includes('registrazione')) toast(`Benvenuto su iVibe, ${d.utente.nickname}!`);
    } catch (ex) { err.textContent = ex.message; } finally { btn.disabled = false; }
  });

  invia($('#form-login'), '/api/login', ['login', 'password']);
  invia($('#form-registrazione'), '/api/registrazione', ['nickname', 'email', 'dataNascita', 'password', 'conferma']);
  $('#form-registrazione').elements.dataNascita.max = new Date().toISOString().slice(0, 10);
  $('#btn-logout').addEventListener('click', logout);
}

// ===================== AVVIO =====================

function aggiornaStatoRete() {
  const offline = !navigator.onLine;
  document.body.classList.toggle('offline', offline);
  $('#offline-banner').hidden = !offline;
}

(async function avvio() {
  collegaAuth();
  Player.collega();
  aggiornaStatoRete();
  window.addEventListener('online', () => { aggiornaStatoRete(); toast('Di nuovo online'); if (stato.utente) caricaCatalogo().then(render).catch(() => {}); Download.controllaServer(); });
  window.addEventListener('offline', () => { aggiornaStatoRete(); toast('Sei offline: puoi ascoltare le canzoni scaricate'); });
  if ('serviceWorker' in navigator) navigator.serviceWorker.register('/sw.js').catch(e => console.warn('service worker:', e));
  try {
    const d = await api('GET', '/api/me');
    if (d.utente) await entra(d.utente);
    else mostraAuth('login');
  } catch (e) {
    mostraAuth('login');
    toast(e.message, true);
  }
})();
