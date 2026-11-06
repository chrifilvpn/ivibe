@echo off
title iVibe
cd /d "%~dp0"

rem ---- 1. Node: quello installato oppure node.exe messo in questa cartella (non serve essere amministratore)
set "NODE="
if exist "%~dp0node.exe" set "NODE=%~dp0node.exe"
if not defined NODE (
  where node >nul 2>nul && set "NODE=node"
)
if not defined NODE (
  echo.
  echo  Manca node.exe.
  echo  1. Si apre il browser e scarica il file node.exe
  echo  2. Spostalo in questa cartella:  %~dp0
  echo  3. Riapri avvia-windows.bat
  echo.
  start "" https://nodejs.org/dist/latest-v22.x/win-x64/node.exe
  pause
  exit /b
)

rem ---- 2. yt-dlp: usa quello incluso nel progetto (si aggiorna da solo all'avvio)
if not exist "web\bin\yt-dlp.exe" (
  if not exist "web\bin" mkdir "web\bin"
  copy /y "src\ivibe\resources\tools\yt-dlp.exe" "web\bin\yt-dlp.exe" >nul
)

echo.
echo  iVibe parte su http://localhost:3000
echo  Lascia aperta questa finestra: chiudendola si spegne il sito.
echo.
start "" http://localhost:3000
"%NODE%" web\server.js
pause
