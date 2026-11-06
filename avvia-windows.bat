@echo off
title iVibe
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo.
  echo  Node.js non e' installato.
  echo  Scaricalo da https://nodejs.org  ^(versione LTS^), installalo e riapri questo file.
  echo.
  start https://nodejs.org
  pause
  exit /b
)
if not exist node_modules (
  echo Prima installazione: scarico pg e yt-dlp...
  call npm install
)
echo.
echo  iVibe parte su http://localhost:3000
echo  Lascia aperta questa finestra: chiudendola si spegne il sito.
echo.
start "" http://localhost:3000
node web\server.js
pause
