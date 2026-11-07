#!/usr/bin/env bash
# Avvia iVibe su Linux SENZA installare niente e senza essere amministratore.
# Uso: apri un terminale in questa cartella e scrivi   bash avvia-linux.sh
# La prima volta scarica Node.js e yt-dlp dentro la cartella "tools" (serve internet).
set -e
cd "$(dirname "$0")"
DIR="$(pwd)"
mkdir -p tools web/bin

scarica() { # url file
  if command -v curl >/dev/null; then curl -fL --progress-bar -o "$2" "$1"
  elif command -v wget >/dev/null; then wget -q --show-progress -O "$2" "$1"
  else echo "Serve curl o wget per scaricare"; exit 1; fi
}

case "$(uname -m)" in
  x86_64|amd64) ARCH=x64; YT=yt-dlp_linux ;;
  aarch64|arm64) ARCH=arm64; YT=yt-dlp_linux_aarch64 ;;
  *) echo "Processore non supportato: $(uname -m)"; exit 1 ;;
esac

# ---- Node.js: quello del sistema (se è almeno la 18) oppure una copia in tools/
NODE=""
if command -v node >/dev/null && [ "$(node -p 'process.versions.node.split(".")[0]')" -ge 18 ] 2>/dev/null; then
  NODE="$(command -v node)"
fi
if [ -z "$NODE" ]; then
  NODE="$(ls -d "$DIR"/tools/node-*/bin/node 2>/dev/null | head -n1 || true)"
fi
if [ -z "$NODE" ]; then
  echo "== Scarico Node.js (una volta sola, circa 30 MB)..."
  BASE=https://nodejs.org/dist/latest-v22.x
  NOME=$( (curl -fsL "$BASE/SHASUMS256.txt" 2>/dev/null || wget -qO- "$BASE/SHASUMS256.txt") | grep -o "node-v[0-9.]*-linux-$ARCH.tar.xz" | head -n1)
  [ -n "$NOME" ] || { echo "Non riesco a contattare nodejs.org: controlla internet"; exit 1; }
  scarica "$BASE/$NOME" "tools/$NOME"
  tar -xJf "tools/$NOME" -C tools
  rm -f "tools/$NOME"
  NODE="$(ls -d "$DIR"/tools/node-*/bin/node | head -n1)"
fi
chmod +x "$NODE" web/bin/yt-dlp 2>/dev/null || true   # se lo ZIP ha perso i permessi
echo "== Node: $("$NODE" -v)"

# ---- yt-dlp per i download (si aggiorna da solo a ogni avvio)
if [ ! -x web/bin/yt-dlp ] && ! command -v yt-dlp >/dev/null; then
  echo "== Scarico yt-dlp (una volta sola)..."
  scarica "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$YT" web/bin/yt-dlp || echo "   (yt-dlp non scaricato: i download non funzioneranno, lo streaming sì)"
  chmod +x web/bin/yt-dlp 2>/dev/null || true
fi

echo
echo " iVibe parte su http://localhost:3000"
echo " Lascia aperto questo terminale (Ctrl+C per spegnere)."
echo
(sleep 2; xdg-open http://localhost:3000 >/dev/null 2>&1 || true) &
exec "$NODE" web/server.js
