#!/usr/bin/env bash
# Sistema il progetto iOS creato da Capacitor: icona, musica in background, server http.
set -ex
PLIST=ios/App/App/Info.plist
PB=/usr/libexec/PlistBuddy
# la musica continua con lo schermo bloccato
$PB -c "Add :UIBackgroundModes array" "$PLIST" || true
$PB -c "Add :UIBackgroundModes:0 string audio" "$PLIST"
# permette un server in http:// (es. il PC di casa)
$PB -c "Add :NSAppTransportSecurity dict" "$PLIST" || true
$PB -c "Add :NSAppTransportSecurity:NSAllowsArbitraryLoads bool true" "$PLIST"
$PB -c "Set :CFBundleDisplayName iVibe" "$PLIST" || $PB -c "Add :CFBundleDisplayName string iVibe" "$PLIST"
# icona
ICONE=ios/App/App/Assets.xcassets/AppIcon.appiconset
for f in "$ICONE"/*.png; do cp assets/icona-ios-1024.png "$f"; done
ls "$ICONE"
echo "Progetto iOS pronto"
