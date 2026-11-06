'use strict';
// Copia l'interfaccia del sito (web/public) in mobile/www, che finisce dentro l'APK.
const fs = require('node:fs');
const path = require('node:path');

const da = path.join(__dirname, '..', '..', 'web', 'public');
const a = path.join(__dirname, '..', 'www');

fs.rmSync(a, { recursive: true, force: true });
fs.cpSync(da, a, { recursive: true });
fs.rmSync(path.join(a, 'sw.js'), { force: true }); // nell'app i file sono già sul telefono
// catalogo e account iniziali: l'app li carica al primo avvio (poi tutto resta nel telefono)
fs.copyFileSync(path.join(__dirname, '..', '..', 'web', 'seed', 'seed.json'), path.join(a, 'seed.json'));
console.log('Interfaccia copiata in', a);
