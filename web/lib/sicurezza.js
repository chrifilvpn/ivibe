'use strict';
// Password e sessioni.
// Le password usano lo stesso formato dell'app desktop (PasswordEncoder.java):
//   pbkdf2$iterazioni$salt(base64)$hash(base64)   con PBKDF2-HMAC-SHA256, 256 bit
// così gli stessi account funzionano sia sul sito sia nell'app JavaFX.

const crypto = require('node:crypto');

const ITERAZIONI = 65536;

function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = crypto.pbkdf2Sync(password, salt, ITERAZIONI, 32, 'sha256');
  return `pbkdf2$${ITERAZIONI}$${salt.toString('base64')}$${hash.toString('base64')}`;
}

function verificaPassword(password, salvata) {
  if (typeof password !== 'string' || typeof salvata !== 'string') return false;
  const parti = salvata.split('$');
  if (parti.length !== 4 || parti[0] !== 'pbkdf2') return false;
  const iterazioni = parseInt(parti[1], 10);
  if (!Number.isInteger(iterazioni) || iterazioni < 1000 || iterazioni > 1e6) return false;
  const salt = Buffer.from(parti[2], 'base64');
  const atteso = Buffer.from(parti[3], 'base64');
  const calcolato = crypto.pbkdf2Sync(password, salt, iterazioni, atteso.length, 'sha256');
  return atteso.length > 0 && crypto.timingSafeEqual(atteso, calcolato);
}

// ---------- Sessione in un cookie firmato (HMAC) ----------
// Il cookie contiene solo id utente, scadenza e un pezzetto dell'hash della password:
// se la password cambia, tutte le sessioni vecchie smettono di valere.

const SEGRETO = process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex');
if (!process.env.SESSION_SECRET) {
  console.warn('[iVibe] SESSION_SECRET non impostato: le sessioni scadono a ogni riavvio');
}

const DURATA_SESSIONE_MS = 7 * 24 * 60 * 60 * 1000; // 7 giorni

function impronta(utente) {
  return crypto.createHash('sha256').update(utente.password).digest('base64url').slice(0, 10);
}

function firma(testo) {
  return crypto.createHmac('sha256', SEGRETO).update(testo).digest('base64url');
}

function creaToken(utente) {
  const payload = Buffer.from(JSON.stringify({
    u: utente.id,
    e: Date.now() + DURATA_SESSIONE_MS,
    p: impronta(utente),
  })).toString('base64url');
  return `${payload}.${firma(payload)}`;
}

/** Restituisce l'utente del token, oppure null. trovaUtente(id) cerca nell'archivio. */
function leggiToken(token, trovaUtente) {
  if (typeof token !== 'string' || !token.includes('.')) return null;
  const [payload, sig] = token.split('.');
  const attesa = firma(payload);
  if (!sig || sig.length !== attesa.length ||
      !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(attesa))) return null;
  let dati;
  try { dati = JSON.parse(Buffer.from(payload, 'base64url').toString()); } catch { return null; }
  if (!dati || typeof dati.e !== 'number' || dati.e < Date.now()) return null;
  const utente = trovaUtente(dati.u);
  if (!utente || impronta(utente) !== dati.p) return null;
  return utente;
}

// ---------- Limite tentativi di login (contro chi prova password a caso) ----------
const tentativi = new Map(); // ip -> { n, reset }
const MAX_TENTATIVI = 10;
const FINESTRA_MS = 10 * 60 * 1000;

function troppiTentativi(ip) {
  const t = tentativi.get(ip);
  if (!t || t.reset < Date.now()) return false;
  return t.n >= MAX_TENTATIVI;
}

function registraTentativoFallito(ip) {
  const ora = Date.now();
  const t = tentativi.get(ip);
  if (!t || t.reset < ora) tentativi.set(ip, { n: 1, reset: ora + FINESTRA_MS });
  else t.n++;
  if (tentativi.size > 5000) { // pulizia
    for (const [k, v] of tentativi) if (v.reset < ora) tentativi.delete(k);
  }
}

function azzeraTentativi(ip) { tentativi.delete(ip); }

module.exports = {
  hashPassword, verificaPassword, creaToken, leggiToken, DURATA_SESSIONE_MS,
  troppiTentativi, registraTentativoFallito, azzeraTentativi,
};
