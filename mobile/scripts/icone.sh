#!/usr/bin/env bash
# Mette il logo di iVibe come icona dell'app (tutte le densità) e toglie l'icona "adattiva" predefinita.
set -ex
RES=android/app/src/main/res
LOGO=../web/public/logo-192.png
for d in "$RES"/mipmap-*dpi; do
  cp "$LOGO" "$d/ic_launcher.png"
  cp "$LOGO" "$d/ic_launcher_round.png"
  cp "$LOGO" "$d/ic_launcher_foreground.png"
done
rm -rf "$RES/mipmap-anydpi-v26"
# permette di usare un server in http:// (es. il PC di casa: http://192.168.1.20:3000)
# (nel manifest "<application" va a capo subito dopo, quindi si cerca solo la parola)
sed -i '0,/<application/s//<application android:usesCleartextTraffic="true"/' android/app/src/main/AndroidManifest.xml
grep -q usesCleartextTraffic android/app/src/main/AndroidManifest.xml
echo "Icone e manifest pronti"
