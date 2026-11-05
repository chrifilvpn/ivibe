'use strict';
// Archivio dei dati di iVibe.
//
// Tutti i dati stanno in memoria in un unico oggetto e vengono salvati:
//  - su Postgres (una riga JSON) se è impostata la variabile DATABASE_URL
//  - altrimenti nel file data/ivibe.json
// Al primo avvio (archivio vuoto) vengono caricati catalogo e account da seed/seed.json.
//
// Su Render gratis il disco viene cancellato a ogni riavvio: senza Postgres
// le registrazioni nuove, i preferiti e le playlist si perdono (il catalogo no).

const fs = require('node:fs');
const path = require('node:path');

const FILE_SEED = process.env.SEED_FILE || path.join(__dirname, '..', 'seed', 'seed.json');
const FILE_DATI = process.env.DATA_FILE || path.join(__dirname, '..', 'data', 'ivibe.json');

let dati = null;
let modalita = 'file';
let pool = null;
let timerSalvataggio = null;
let salvataggioInCorso = Promise.resolve();

function vuoto() {
  return { versione: 1, prossimiId: { utente: 1, canzone: 1, playlist: 1 }, utenti: [], canzoni: [], preferiti: {}, playlist: [] };
}

function caricaSeed() {
  const seed = JSON.parse(fs.readFileSync(FILE_SEED, 'utf8'));
  const d = vuoto();
  d.utenti = seed.utenti;
  d.canzoni = seed.canzoni;
  d.preferiti = seed.preferiti || {};
  d.playlist = seed.playlist || [];
  d.prossimiId = {
    utente: Math.max(0, ...d.utenti.map(u => u.id)) + 1,
    canzone: Math.max(0, ...d.canzoni.map(c => c.id)) + 1,
    playlist: Math.max(0, ...d.playlist.map(p => p.id)) + 1,
  };
  return d;
}

async function inizializza() {
  const url = process.env.DATABASE_URL;

  if (url) {
    try {
      const { Pool } = require('pg');
      const host = new URL(url).hostname;
      // l'URL "interno" di Render (host senza punti) non usa SSL, quello esterno sì
      pool = new Pool({ connectionString: url, ssl: host.includes('.') ? { rejectUnauthorized: false } : false, max: 3 });
      await pool.query(`CREATE TABLE IF NOT EXISTS ivibe_dati (
        id INT PRIMARY KEY,
        dati JSONB NOT NULL,
        aggiornato TIMESTAMPTZ NOT NULL DEFAULT now()
      )`);
      const r = await pool.query('SELECT dati FROM ivibe_dati WHERE id = 1');
      modalita = 'postgres';
      if (r.rows.length) {
        dati = r.rows[0].dati;
      } else {
        dati = caricaSeed();
        await scriviOra();
      }
      console.log('[iVibe] dati su Postgres');
      return;
    } catch (e) {
      console.error('[iVibe] Postgres non disponibile, uso il file locale:', e.message);
      if (pool) pool.end().catch(() => {});
      pool = null;
      modalita = 'file';
    }
  }

  try {
    dati = JSON.parse(fs.readFileSync(FILE_DATI, 'utf8'));
    console.log('[iVibe] dati letti da', FILE_DATI);
  } catch {
    dati = caricaSeed();
    console.log('[iVibe] archivio nuovo creato dal seed');
    await scriviOra();
  }
}

async function scriviOra() {
  const json = JSON.stringify(dati);
  if (modalita === 'postgres') {
    await pool.query(
      `INSERT INTO ivibe_dati (id, dati, aggiornato) VALUES (1, $1::jsonb, now())
       ON CONFLICT (id) DO UPDATE SET dati = EXCLUDED.dati, aggiornato = now()`, [json]);
  } else {
    fs.mkdirSync(path.dirname(FILE_DATI), { recursive: true });
    const tmp = FILE_DATI + '.tmp';
    fs.writeFileSync(tmp, json);
    fs.renameSync(tmp, FILE_DATI); // scrittura "atomica": il file non resta mai a metà
  }
}

/** Da chiamare dopo ogni modifica: salva entro poco tempo (più modifiche = un solo salvataggio). */
function salva() {
  clearTimeout(timerSalvataggio);
  timerSalvataggio = setTimeout(() => {
    timerSalvataggio = null;
    salvataggioInCorso = salvataggioInCorso
      .then(scriviOra)
      .catch(e => console.error('[iVibe] errore salvataggio:', e.message));
  }, 200);
}

async function chiudi() {
  if (timerSalvataggio) {
    clearTimeout(timerSalvataggio);
    timerSalvataggio = null;
    salvataggioInCorso = salvataggioInCorso.then(scriviOra);
  }
  await salvataggioInCorso.catch(() => {});
  if (pool) await pool.end().catch(() => {});
}

// ===================== UTENTI =====================

const utenti = {
  perId: id => dati.utenti.find(u => u.id === id) || null,
  perLogin(login) {
    const l = String(login).toLowerCase();
    return dati.utenti.find(u => u.nickname.toLowerCase() === l || u.email.toLowerCase() === l) || null;
  },
  esiste(nickname, email) {
    return dati.utenti.some(u => u.nickname.toLowerCase() === nickname.toLowerCase() ||
                                 u.email.toLowerCase() === email.toLowerCase());
  },
  tutti: () => dati.utenti,
  crea({ nickname, email, password, dataNascita, isAdmin = false }) {
    const u = { id: dati.prossimiId.utente++, nickname, email, password, dataNascita, isAdmin, creato: new Date().toISOString() };
    dati.utenti.push(u);
    salva();
    return u;
  },
  aggiorna(u, campi) { Object.assign(u, campi); salva(); },
  elimina(id) {
    dati.utenti = dati.utenti.filter(u => u.id !== id);
    delete dati.preferiti[id];
    dati.playlist = dati.playlist.filter(p => p.utenteId !== id);
    salva();
  },
  contaAdmin: () => dati.utenti.filter(u => u.isAdmin).length,
};

// ===================== CANZONI =====================

const canzoni = {
  tutte: () => dati.canzoni,
  perId: id => dati.canzoni.find(c => c.id === id) || null,
  perVideo: v => dati.canzoni.find(c => c.videoId === v) || null,
  crea({ titolo, artista, durata, videoId }) {
    const c = { id: dati.prossimiId.canzone++, titolo, artista, durata, videoId };
    dati.canzoni.push(c);
    salva();
    return c;
  },
  aggiorna(c, campi) { Object.assign(c, campi); salva(); },
  elimina(id) {
    dati.canzoni = dati.canzoni.filter(c => c.id !== id);
    for (const k of Object.keys(dati.preferiti)) dati.preferiti[k] = dati.preferiti[k].filter(x => x !== id);
    for (const p of dati.playlist) p.canzoni = p.canzoni.filter(x => x !== id);
    salva();
  },
};

// ===================== PREFERITI =====================

const preferiti = {
  di: uid => dati.preferiti[uid] || [],
  imposta(uid, cid, attivo) {
    const lista = dati.preferiti[uid] || (dati.preferiti[uid] = []);
    const i = lista.indexOf(cid);
    if (attivo && i === -1) lista.push(cid);
    if (!attivo && i !== -1) lista.splice(i, 1);
    salva();
    return lista;
  },
};

// ===================== PLAYLIST =====================

const playlist = {
  di: uid => dati.playlist.filter(p => p.utenteId === uid),
  perId: id => dati.playlist.find(p => p.id === id) || null,
  crea(uid, nome) {
    const p = { id: dati.prossimiId.playlist++, utenteId: uid, nome, canzoni: [] };
    dati.playlist.push(p);
    salva();
    return p;
  },
  rinomina(p, nome) { p.nome = nome; salva(); },
  elimina(id) { dati.playlist = dati.playlist.filter(p => p.id !== id); salva(); },
  imposta(p, cid, presente) {
    const i = p.canzoni.indexOf(cid);
    if (presente && i === -1) p.canzoni.push(cid);
    if (!presente && i !== -1) p.canzoni.splice(i, 1);
    salva();
  },
  riordina(p, ids) { p.canzoni = ids; salva(); },
};

module.exports = { inizializza, chiudi, modalita: () => modalita, utenti, canzoni, preferiti, playlist };
