'use strict';
// Scarica l'eseguibile di yt-dlp in web/bin/ (viene lanciato da "npm install").
// È la versione "standalone": non serve Python. Se qualcosa va storto non blocca
// l'installazione: il sito funziona lo stesso, solo senza download.

const fs = require('node:fs');
const path = require('node:path');
const https = require('node:https');
const { execFileSync } = require('node:child_process');

const NOMI = { linux: 'yt-dlp_linux', darwin: 'yt-dlp_macos', win32: 'yt-dlp.exe' };
const nomeRilascio = NOMI[process.platform];
const cartella = path.join(__dirname, '..', 'bin');
const destinazione = path.join(cartella, process.platform === 'win32' ? 'yt-dlp.exe' : 'yt-dlp');

function giaInstallato() {
  if (fs.existsSync(destinazione)) return true;
  try { execFileSync('yt-dlp', ['--version'], { stdio: 'ignore' }); return true; } catch { return false; }
}

function scarica(url, file, salti = 0) {
  return new Promise((resolve, reject) => {
    if (salti > 5) return reject(new Error('troppi redirect'));
    https.get(url, { headers: { 'User-Agent': 'ivibe-installer' } }, res => {
      if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location) {
        res.resume();
        return resolve(scarica(new URL(res.headers.location, url).toString(), file, salti + 1));
      }
      if (res.statusCode !== 200) { res.resume(); return reject(new Error('HTTP ' + res.statusCode)); }
      const tmp = file + '.tmp';
      const out = fs.createWriteStream(tmp);
      res.pipe(out);
      out.on('finish', () => out.close(() => { fs.renameSync(tmp, file); resolve(); }));
      out.on('error', reject);
    }).on('error', reject);
  });
}

(async () => {
  if (process.env.IVIBE_SALTA_YTDLP) return;
  if (!nomeRilascio) return console.log('[iVibe] sistema non supportato per yt-dlp automatico');
  if (giaInstallato()) return console.log('[iVibe] yt-dlp già presente');
  try {
    fs.mkdirSync(cartella, { recursive: true });
    console.log('[iVibe] scarico yt-dlp...');
    await scarica(`https://github.com/yt-dlp/yt-dlp/releases/latest/download/${nomeRilascio}`, destinazione);
    fs.chmodSync(destinazione, 0o755);
    console.log('[iVibe] yt-dlp installato in', destinazione);
  } catch (e) {
    console.warn('[iVibe] impossibile scaricare yt-dlp (' + e.message + '): i download non saranno disponibili');
  }
})();
