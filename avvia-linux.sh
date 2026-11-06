#!/usr/bin/env bash
# Avvia iVibe in locale: ./avvia-linux.sh  (poi apri http://localhost:3000)
cd "$(dirname "$0")"
if ! command -v node >/dev/null; then
  echo "Node.js non è installato. Installa la versione 22 con:"
  echo "  curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash"
  echo "  (chiudi e riapri il terminale) e poi:  nvm install 22"
  exit 1
fi
[ -d node_modules ] || npm install
echo
echo " iVibe parte su http://localhost:3000 — lascia aperto il terminale (Ctrl+C per spegnere)"
echo
(sleep 2; xdg-open http://localhost:3000 >/dev/null 2>&1) &
exec node web/server.js
