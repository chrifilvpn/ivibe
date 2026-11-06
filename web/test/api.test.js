'use strict';
// Test delle API: avvia il server su una porta libera con dati temporanei.
// Esegui con:  npm test
const { test, before, after } = require('node:test');
const assert = require('node:assert');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { hashPassword } = require('../lib/sicurezza');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'ivibe-test-'));
const PORTA = 3900 + Math.floor(Math.random() * 90);
const BASE = `http://127.0.0.1:${PORTA}`;
let server;

const seed = {
  utenti: [{ id: 1, nickname: 'admin', email: 'admin@test.it', password: hashPassword('Admin123!'), dataNascita: '2000-01-01', isAdmin: true }],
  canzoni: [
    { id: 1, titolo: 'Uno', artista: 'A', durata: 200, videoId: 'JGwWNGJdvx8' },
    { id: 2, titolo: 'Due', artista: 'B', durata: 180, videoId: '4NRXx6U8ABQ' },
  ],
  preferiti: {}, playlist: [],
};

function client() {
  let cookie = '';
  return async (metodo, url, corpo) => {
    const r = await fetch(BASE + url, {
      method: metodo,
      headers: { ...(corpo !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(cookie ? { Cookie: cookie } : {}) },
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    });
    const sc = r.headers.get('set-cookie');
    if (sc) cookie = sc.split(';')[0];
    return { stato: r.status, dati: await r.json().catch(() => ({})) };
  };
}

before(async () => {
  fs.writeFileSync(path.join(dir, 'seed.json'), JSON.stringify(seed));
  server = spawn(process.execPath, [path.join(__dirname, '..', 'server.js')], {
    env: { ...process.env, PORT: String(PORTA), SEED_FILE: path.join(dir, 'seed.json'), DATA_FILE: path.join(dir, 'dati.json'), DATABASE_URL: '', SESSION_SECRET: 'test' },
    stdio: 'ignore',
  });
  for (let i = 0; i < 50; i++) {
    try { if ((await fetch(BASE + '/healthz')).ok) return; } catch { /* non ancora pronto */ }
    await new Promise(r => setTimeout(r, 100));
  }
  throw new Error('server non partito');
});

after(() => { server.kill(); fs.rmSync(dir, { recursive: true, force: true }); });

test('pagina e file statici', async () => {
  const r = await fetch(BASE + '/');
  assert.equal(r.status, 200);
  assert.match(r.headers.get('content-security-policy'), /frame-ancestors 'none'/);
  assert.equal((await fetch(BASE + '/../server.js')).status, 404); // non si esce dalla cartella public
  const stato = await new Promise(res => require('node:http').get({ host: '127.0.0.1', port: PORTA, path: '/..%2fserver.js' }, r => { r.resume(); res(r.statusCode); }));
  assert.equal(stato, 403); // tentativo di leggere file fuori da public
});

test('senza login le API sono protette', async () => {
  const api = client();
  assert.equal((await api('GET', '/api/catalogo')).stato, 401);
  assert.equal((await api('GET', '/api/me')).dati.utente, null);
});

test('registrazione, preferiti, playlist, password', async () => {
  const api = client();
  let r = await api('POST', '/api/registrazione', { nickname: 'mario', email: 'mario@test.it', dataNascita: '2007-03-02', password: 'debole', conferma: 'debole' });
  assert.equal(r.stato, 400);
  r = await api('POST', '/api/registrazione', { nickname: 'mario', email: 'mario@test.it', dataNascita: '2007-03-02', password: 'Forte123!', conferma: 'Forte123!' });
  assert.equal(r.stato, 200);
  r = await api('POST', '/api/registrazione', { nickname: 'MARIO', email: 'altro@test.it', dataNascita: '2007-03-02', password: 'Forte123!', conferma: 'Forte123!' });
  assert.equal(r.stato, 409);

  r = await api('GET', '/api/catalogo');
  assert.equal(r.dati.canzoni.length, 2);

  assert.deepEqual((await api('PUT', '/api/preferiti/2')).dati.preferiti, [2]);
  assert.deepEqual((await api('PUT', '/api/preferiti/2')).dati.preferiti, [2]);
  assert.equal((await api('PUT', '/api/preferiti/99')).stato, 404);
  assert.deepEqual((await api('DELETE', '/api/preferiti/2')).dati.preferiti, []);

  const pl = (await api('POST', '/api/playlist', { nome: 'Estate' })).dati.playlist;
  assert.equal((await api('POST', '/api/playlist', { nome: 'estate' })).stato, 409);
  await api('PUT', `/api/playlist/${pl.id}/canzoni/1`);
  r = await api('PUT', `/api/playlist/${pl.id}/canzoni/2`);
  assert.deepEqual(r.dati.playlist.canzoni, [1, 2]);
  r = await api('PATCH', `/api/playlist/${pl.id}`, { canzoni: [2, 1], nome: 'Estate 2027' });
  assert.deepEqual(r.dati.playlist.canzoni, [2, 1]);
  assert.equal((await api('PATCH', `/api/playlist/${pl.id}`, { canzoni: [2, 2] })).stato, 400);

  // un altro utente non vede né modifica la playlist
  const altro = client();
  await altro('POST', '/api/registrazione', { nickname: 'luigi', email: 'luigi@test.it', dataNascita: '2006-01-01', password: 'Forte123!', conferma: 'Forte123!' });
  assert.equal((await altro('DELETE', `/api/playlist/${pl.id}`)).stato, 404);
  assert.equal((await altro('GET', '/api/admin/utenti')).stato, 403);

  assert.equal((await api('POST', '/api/account/password', { attuale: 'sbagliata', nuova: 'Nuova123!' })).stato, 400);
  assert.equal((await api('POST', '/api/account/password', { attuale: 'Forte123!', nuova: 'Nuova123!' })).stato, 200);
  const nuovo = client();
  assert.equal((await nuovo('POST', '/api/login', { login: 'mario@test.it', password: 'Forte123!' })).stato, 401);
  assert.equal((await nuovo('POST', '/api/login', { login: 'mario@test.it', password: 'Nuova123!' })).stato, 200);

  assert.equal((await api('POST', '/api/account/elimina', { password: 'Nuova123!' })).stato, 200);
  assert.equal((await nuovo('GET', '/api/catalogo')).stato, 401); // sessione dell'utente eliminato non vale più
});

test('admin: canzoni e utenti', async () => {
  const api = client();
  assert.equal((await api('POST', '/api/login', { login: 'admin', password: 'Admin123!' })).stato, 200);
  assert.equal((await api('POST', '/api/admin/canzoni', { titolo: 'X', artista: 'Y', url: 'https://example.com', durata: '' })).stato, 400);
  assert.equal((await api('POST', '/api/admin/canzoni', { titolo: 'X', artista: 'Y', url: 'https://youtu.be/JGwWNGJdvx8', durata: '' })).stato, 409);
  let r = await api('POST', '/api/admin/canzoni', { titolo: 'Nuova', artista: 'Z', url: 'https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10', durata: '3:33' });
  assert.ok([200, 400].includes(r.stato)); // 400 solo se YouTube dice che il video non esiste
  if (r.stato === 200) assert.equal(r.dati.canzone.durata, 213);
  r = await api('PATCH', '/api/admin/canzoni/1', { titolo: 'Uno bis', durata: '4.05' });
  assert.equal(r.dati.canzone.durata, 245);
  assert.equal((await api('DELETE', '/api/admin/canzoni/2')).stato, 200);
  const utenti = (await api('GET', '/api/admin/utenti')).dati.utenti;
  const luigi = utenti.find(u => u.nickname === 'luigi');
  assert.equal((await api('PATCH', '/api/admin/utenti/1', { isAdmin: false })).stato, 400);
  assert.equal((await api('PATCH', `/api/admin/utenti/${luigi.id}`, { isAdmin: true })).stato, 200);
  assert.equal((await api('DELETE', `/api/admin/utenti/${luigi.id}`)).stato, 400);
  await api('PATCH', `/api/admin/utenti/${luigi.id}`, { isAdmin: false });
  assert.equal((await api('DELETE', `/api/admin/utenti/${luigi.id}`)).stato, 200);
  assert.equal((await api('POST', '/api/account/elimina', { password: 'Admin123!' })).stato, 400); // ultimo admin
});

test('i dati vengono salvati su file', async () => {
  await new Promise(r => setTimeout(r, 400));
  const salvati = JSON.parse(fs.readFileSync(path.join(dir, 'dati.json'), 'utf8'));
  assert.ok(salvati.canzoni.find(c => c.titolo === 'Uno bis'));
  assert.ok(!salvati.utenti.find(u => u.nickname === 'mario'));
});

test('app Android: token e CORS', async () => {
  const pre = await fetch(BASE + '/api/login', { method: 'OPTIONS', headers: { Origin: 'https://localhost', 'Access-Control-Request-Method': 'POST' } });
  assert.equal(pre.status, 204);
  assert.equal(pre.headers.get('access-control-allow-origin'), 'https://localhost');
  const estraneo = await fetch(BASE + '/api/me', { headers: { Origin: 'https://sito-cattivo.example' } });
  assert.equal(estraneo.headers.get('access-control-allow-origin'), null);

  const r = await fetch(BASE + '/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json', Origin: 'https://localhost' },
    body: JSON.stringify({ login: 'admin', password: 'Admin123!', app: true }) });
  const j = await r.json();
  assert.ok(j.token);
  const sito = await (await fetch(BASE + '/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ login: 'admin', password: 'Admin123!' }) })).json();
  assert.equal(sito.token, undefined); // il sito non riceve il token (resta nel cookie HttpOnly)
  const cat = await fetch(BASE + '/api/catalogo', { headers: { Authorization: 'Bearer ' + j.token } });
  assert.equal(cat.status, 200);
  assert.equal((await fetch(BASE + '/api/catalogo', { headers: { Authorization: 'Bearer falso.token' } })).status, 401);
});

test('limite tentativi di login', async () => {
  const api = client();
  let ultimo;
  for (let i = 0; i < 11; i++) ultimo = await api('POST', '/api/login', { login: 'admin', password: 'no' });
  assert.equal(ultimo.stato, 429);
});

// ---------- download (con un finto yt-dlp: in questi test non si va su YouTube) ----------
const { test: test2 } = require('node:test');

test2('download per l\'offline', { skip: process.platform === 'win32' }, async () => {
  const cartella = fs.mkdtempSync(path.join(os.tmpdir(), 'ivibe-dl-'));
  const finto = path.join(cartella, 'yt-dlp');
  fs.writeFileSync(finto, `#!/bin/sh
[ "$1" = "--version" ] && echo 2099.01.01 && exit 0
while [ $# -gt 0 ]; do case "$1" in -o) OUT="$2"; shift 2;; --) ID="$2"; shift 2;; *) shift;; esac; done
if [ "$ID" = "4NRXx6U8ABQ" ]; then echo "ERROR: [youtube] $ID: Sign in to confirm you're not a bot" >&2; exit 1; fi
head -c 5000 /dev/zero > "$OUT"
`, { mode: 0o755 });
  const porta = PORTA + 100;
  const base = `http://127.0.0.1:${porta}`;
  const srv = spawn(process.execPath, [path.join(__dirname, '..', 'server.js')], {
    env: { ...process.env, PORT: String(porta), SEED_FILE: path.join(dir, 'seed.json'), DATA_FILE: path.join(cartella, 'dati.json'),
      CACHE_DIR: path.join(cartella, 'cache'), YTDLP_PATH: finto, YTDLP_NO_UPDATE: '1', DATABASE_URL: '', SESSION_SECRET: 'test' },
    stdio: 'ignore',
  });
  try {
    for (let i = 0; i < 50; i++) {
      try { if ((await fetch(base + '/healthz')).ok) break; } catch { /* attendo */ }
      await new Promise(r => setTimeout(r, 100));
    }
    assert.equal((await fetch(base + '/api/scarica/1')).status, 401); // serve il login
    const login = await fetch(base + '/api/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ login: 'admin', password: 'Admin123!' }) });
    const cookie = login.headers.get('set-cookie').split(';')[0];
    const stato = await (await fetch(base + '/api/scarica-stato', { headers: { Cookie: cookie } })).json();
    assert.deepEqual(stato, { attivi: true, ytdlp: '2099.01.01' });

    const r = await fetch(base + '/api/scarica/1?formato=audio', { headers: { Cookie: cookie } });
    assert.equal(r.status, 200);
    assert.equal(r.headers.get('content-type'), 'audio/mp4');
    assert.match(r.headers.get('content-disposition'), /A - Uno\.m4a/);
    assert.equal((await r.arrayBuffer()).byteLength, 5000);
    assert.ok(fs.existsSync(path.join(cartella, 'cache', 'JGwWNGJdvx8.m4a'))); // resta in cache

    const v = await fetch(base + '/api/scarica/1?formato=video', { headers: { Cookie: cookie } });
    assert.equal(v.headers.get('content-type'), 'video/mp4');
    await v.arrayBuffer();

    const bloccato = await fetch(base + '/api/scarica/2', { headers: { Cookie: cookie } });
    assert.equal(bloccato.status, 503);
    assert.match((await bloccato.json()).errore, /YouTube ha bloccato/);
    assert.equal((await fetch(base + '/api/scarica/999', { headers: { Cookie: cookie } })).status, 404);
  } finally {
    srv.kill();
    fs.rmSync(cartella, { recursive: true, force: true });
  }
});
