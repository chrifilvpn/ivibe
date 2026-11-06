'use strict';
// Download delle canzoni con yt-dlp.
//
// Il server scarica il file da YouTube in una cartella temporanea (cache) e lo
// manda al browser, che lo salva sul dispositivo per ascoltarlo offline.
//  - "audio": m4a (AAC), leggero (~4 MB a canzone)
//  - "video": mp4 360p con audio (~15-25 MB a canzone)
// Non serve ffmpeg: in entrambi i casi YouTube fornisce un file unico.

const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { spawn, execFile } = require('node:child_process');

const CARTELLA_CACHE = process.env.CACHE_DIR || path.join(__dirname, '..', 'data', 'cache');
const MAX_CACHE_BYTE = 400 * 1024 * 1024; // oltre questa dimensione si cancellano i file più vecchi
const MAX_CONTEMPORANEI = parseInt(process.env.DOWNLOAD_PARALLELI || '4', 10); // processi yt-dlp insieme
const TIMEOUT_MS = 4 * 60 * 1000;

const FORMATI = {
  audio: { ext: 'm4a', mime: 'audio/mp4', f: '140/bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]' },
  video: { ext: 'mp4', mime: 'video/mp4', f: '18/best[ext=mp4][vcodec^=avc1][acodec!=none]/best[ext=mp4][acodec!=none]' },
};

class ErroreDownload extends Error {
  constructor(stato, messaggio) { super(messaggio); this.stato = stato; }
}

// ---------- dove si trova yt-dlp ----------

let eseguibile = null;
function trovaYtDlp() {
  if (eseguibile) return eseguibile;
  const candidati = [
    process.env.YTDLP_PATH,
    path.join(__dirname, '..', 'bin', process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp'),
    // su Windows va bene anche quello incluso nell'app desktop (così non serve installare niente)
    process.platform === 'win32' ? path.join(__dirname, '..', '..', 'src', 'ivibe', 'resources', 'tools', 'yt-dlp.exe') : null,
  ].filter(Boolean);
  for (const c of candidati) if (fs.existsSync(c)) return (eseguibile = c);
  return (eseguibile = 'yt-dlp'); // installato nel sistema (es. sudo apt install yt-dlp)
}

/** Versione di yt-dlp, oppure null se non c'è. */
const usaCookie = () => fs.existsSync(FILE_SEGRETO) || !!process.env.YTDLP_COOKIES;

function versione() {
  return new Promise(resolve => {
    execFile(trovaYtDlp(), ['--version'], { timeout: 15000 }, (err, out) => resolve(err ? null : out.trim()));
  });
}

/** Aggiorna yt-dlp all'avvio (YouTube cambia spesso e le versioni vecchie smettono di funzionare). */
function aggiornaInBackground() {
  const exe = trovaYtDlp();
  if (exe === 'yt-dlp' || process.env.YTDLP_NO_UPDATE) return; // quello di sistema lo aggiorna il sistema
  execFile(exe, ['-U'], { timeout: 120000 }, (err, out) => {
    if (err) console.warn('[iVibe] aggiornamento yt-dlp non riuscito:', err.message);
    else console.log('[iVibe] yt-dlp:', (out || '').trim().split('\n').pop());
  });
}

// ---------- cookie (facoltativi) ----------
// Se YouTube blocca il server ("Sign in to confirm you're not a bot") si possono
// passare i cookie di un account YouTube nella variabile YTDLP_COOKIES
// (contenuto del file cookies.txt in formato Netscape).

// In alternativa (più comodo) su Render: Environment > Secret Files > file "cookies.txt",
// che Render mette in /etc/secrets/cookies.txt.
const FILE_SEGRETO = process.env.YTDLP_COOKIES_FILE || '/etc/secrets/cookies.txt';

let fileCookie = null;
function argomentiCookie() {
  if (fs.existsSync(FILE_SEGRETO)) {
    // yt-dlp riscrive il file dei cookie: /etc/secrets è di sola lettura, quindi uso una copia
    if (!fileCookie) {
      fileCookie = path.join(os.tmpdir(), 'ivibe-cookies.txt');
      fs.copyFileSync(FILE_SEGRETO, fileCookie);
      fs.chmodSync(fileCookie, 0o600);
    }
    return ['--cookies', fileCookie];
  }
  const c = process.env.YTDLP_COOKIES;
  if (!c) return [];
  if (!fileCookie) {
    fileCookie = path.join(os.tmpdir(), 'ivibe-cookies.txt');
    fs.writeFileSync(fileCookie, c.replace(/\\n/g, '\n'), { mode: 0o600 });
  }
  return ['--cookies', fileCookie];
}

// ---------- cache ----------

function pulisciCache() {
  try {
    const file = fs.readdirSync(CARTELLA_CACHE)
      .filter(n => !n.endsWith('.part') && !n.endsWith('.tmp'))
      .map(n => { const p = path.join(CARTELLA_CACHE, n); const s = fs.statSync(p); return { p, size: s.size, t: s.mtimeMs }; })
      .sort((a, b) => a.t - b.t);
    let totale = file.reduce((s, f) => s + f.size, 0);
    while (totale > MAX_CACHE_BYTE && file.length) {
      const f = file.shift();
      fs.rmSync(f.p, { force: true });
      totale -= f.size;
    }
  } catch { /* cartella non ancora creata */ }
}

// ---------- download ----------

const inCorso = new Map(); // "videoId.ext" -> Promise<percorso>
let attivi = 0;
const attesa = [];

function slot() {
  if (attivi < MAX_CONTEMPORANEI) { attivi++; return Promise.resolve(); }
  return new Promise(resolve => attesa.push(resolve));
}
function liberaSlot() {
  const prossimo = attesa.shift();
  if (prossimo) prossimo(); else attivi--;
}

function traduciErrore(testo) {
  if (/Sign in to confirm|not a bot|HTTP Error 429|blocked/i.test(testo)) {
    return new ErroreDownload(503, 'YouTube ha bloccato il download da questo server. Riprova più tardi o usa iVibe sul tuo PC (vedi README).');
  }
  if (/Video unavailable|Private video|has been removed|not available/i.test(testo)) {
    return new ErroreDownload(404, 'Questo video non è più disponibile su YouTube');
  }
  if (/Requested format is not available/i.test(testo)) {
    return new ErroreDownload(415, 'Formato non disponibile per questo video: prova con l\'altro formato');
  }
  if (/File is larger than max-filesize|max-filesize/i.test(testo)) {
    return new ErroreDownload(413, 'Il file è troppo grande');
  }
  return new ErroreDownload(502, 'Download non riuscito (' + (testo.trim().split('\n').pop() || 'errore sconosciuto').slice(0, 160) + ')');
}

function eseguiYtDlp(videoId, formato, finale) {
  return new Promise((resolve, reject) => {
    const tmp = finale + '.tmp';
    const args = [
      '-f', FORMATI[formato].f,
      '--no-playlist', '--no-progress', '--no-warnings', '--no-mtime',
      '--max-filesize', formato === 'video' ? '120M' : '40M',
      '--socket-timeout', '20', '--retries', '3',
      ...argomentiCookie(),
      '-o', tmp,
      '--', videoId,
    ];
    let errori = '';
    let proc;
    try {
      proc = spawn(trovaYtDlp(), args, { stdio: ['ignore', 'ignore', 'pipe'] });
    } catch (e) {
      return reject(new ErroreDownload(503, 'yt-dlp non è installato sul server'));
    }
    const timer = setTimeout(() => { proc.kill('SIGKILL'); errori += '\ntimeout'; }, TIMEOUT_MS);
    proc.stderr.on('data', d => { errori = (errori + d).slice(-4000); });
    proc.on('error', e => {
      clearTimeout(timer);
      reject(e.code === 'ENOENT' ? new ErroreDownload(503, 'yt-dlp non è installato sul server') : e);
    });
    proc.on('close', codice => {
      clearTimeout(timer);
      if (codice === 0 && fs.existsSync(tmp) && fs.statSync(tmp).size > 0) {
        fs.renameSync(tmp, finale);
        return resolve(finale);
      }
      fs.rmSync(tmp, { force: true });
      fs.rmSync(tmp + '.part', { force: true });
      console.warn(`[iVibe] yt-dlp ${videoId} (${formato}) fallito:`, errori.trim().split('\n').slice(-3).join(' | '));
      reject(/timeout$/.test(errori) ? new ErroreDownload(504, 'Il download ci ha messo troppo, riprova') : traduciErrore(errori));
    });
  });
}

/** Restituisce { percorso, mime, ext } del file pronto (lo scarica se serve). */
async function prepara(videoId, formato) {
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) throw new ErroreDownload(400, 'Video non valido');
  if (!FORMATI[formato]) throw new ErroreDownload(400, 'Formato non valido');
  const { ext, mime } = FORMATI[formato];
  fs.mkdirSync(CARTELLA_CACHE, { recursive: true });
  const finale = path.join(CARTELLA_CACHE, `${videoId}.${ext}`);

  if (fs.existsSync(finale)) {
    const ora = new Date();
    fs.utimesSync(finale, ora, ora); // "usato di recente": non viene cancellato per primo
    return { percorso: finale, mime, ext };
  }

  const chiave = `${videoId}.${ext}`;
  if (!inCorso.has(chiave)) {
    const p = (async () => {
      await slot();
      try {
        return await eseguiYtDlp(videoId, formato, finale);
      } finally {
        liberaSlot();
        inCorso.delete(chiave);
        pulisciCache();
      }
    })();
    inCorso.set(chiave, p);
  }
  await inCorso.get(chiave);
  return { percorso: finale, mime, ext };
}

module.exports = { prepara, versione, usaCookie, aggiornaInBackground, ErroreDownload, FORMATI };
