'use strict';
// Stesse regole di Validatore.java dell'app desktop.
// Ogni funzione restituisce null se va bene, altrimenti il messaggio d'errore.

const EMAIL = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;
const NICKNAME = /^[A-Za-z0-9._-]{3,30}$/;

function controllaPassword(p) {
  if (typeof p !== 'string' || p.length < 7) return 'Password troppo corta (minimo 7 caratteri)';
  if (p.length > 64) return 'Password troppo lunga (massimo 64 caratteri)';
  if (/\s/.test(p)) return 'La password non può contenere spazi';
  if (!/[A-Z]/.test(p) || !/[0-9]/.test(p) || !/[^A-Za-z0-9]/.test(p)) {
    return 'La password deve avere una maiuscola, un numero e un simbolo';
  }
  return null;
}

function controllaEmail(e) {
  if (typeof e !== 'string' || e.length > 100 || !EMAIL.test(e)) return 'Email non valida (es. nome@dominio.it)';
  return null;
}

function controllaNickname(n) {
  if (typeof n !== 'string' || !NICKNAME.test(n)) return 'Username: 3-30 caratteri tra lettere, numeri, . _ -';
  return null;
}

function controllaData(d) {
  if (typeof d !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(d)) return 'Data di nascita non valida';
  const data = new Date(d + 'T00:00:00Z');
  if (Number.isNaN(data.getTime()) || data.toISOString().slice(0, 10) !== d) return 'Data di nascita non valida';
  const oggi = new Date();
  if (data > oggi) return 'La data di nascita non può essere nel futuro';
  if (oggi.getUTCFullYear() - data.getUTCFullYear() > 120) return 'Data di nascita non valida';
  return null;
}

/** Estrae l'id del video da un link YouTube (watch, youtu.be, shorts, embed). */
function idYouTube(url) {
  if (typeof url !== 'string') return null;
  let u;
  try { u = new URL(url.trim()); } catch { return null; }
  if (!['http:', 'https:'].includes(u.protocol)) return null;
  const host = u.hostname.replace(/^www\.|^m\.|^music\./, '');
  let id = null;
  if (host === 'youtu.be') id = u.pathname.slice(1).split('/')[0];
  else if (host === 'youtube.com' || host === 'youtube-nocookie.com') {
    if (u.pathname === '/watch') id = u.searchParams.get('v');
    else {
      const m = u.pathname.match(/^\/(?:shorts|embed|live|v)\/([^/?#]+)/);
      if (m) id = m[1];
    }
  }
  return id && /^[A-Za-z0-9_-]{11}$/.test(id) ? id : null;
}

/** Durata: accetta "3:45", "3.45" o "3,45" (minuti.secondi) e restituisce i secondi. */
function durataInSecondi(v) {
  if (v === undefined || v === null || v === '') return 0;
  const s = String(v).trim().replace(',', '.');
  const m = s.match(/^(\d{1,3})(?:[:.](\d{1,2}))?$/);
  if (!m) return NaN;
  const min = parseInt(m[1], 10);
  let sec = m[2] ? parseInt(m[2].padEnd(2, '0'), 10) : 0;
  if (sec > 59) return NaN;
  return min * 60 + sec;
}

module.exports = { controllaPassword, controllaEmail, controllaNickname, controllaData, idYouTube, durataInSecondi };
