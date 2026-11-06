@echo off
title iVibe
cd /d "%~dp0"
echo.
echo  === iVibe ===
echo  Cartella: "%CD%"
echo.

set "NODE="
if exist "node.exe" set "NODE=%CD%\node.exe"
if defined NODE goto :ytdlp
where node >nul 2>nul
if errorlevel 1 goto :manca_node
set "NODE=node"
goto :ytdlp

:manca_node
echo  Manca node.exe in questa cartella.
echo  1. Si apre il browser e scarica node.exe
echo  2. Spostalo nella cartella scritta sopra, accanto a avvia-windows.bat
echo  3. Riapri avvia-windows.bat
echo.
start "" "https://nodejs.org/dist/latest-v22.x/win-x64/node.exe"
pause
exit /b

:ytdlp
if exist "web\bin\yt-dlp.exe" goto :parti
if not exist "web\bin" mkdir "web\bin"
copy /y "src\ivibe\resources\tools\yt-dlp.exe" "web\bin\yt-dlp.exe" >nul

:parti
echo  Node: "%NODE%"
echo  iVibe parte su http://localhost:3000
echo  Lascia aperta questa finestra: chiudendola si spegne il sito.
echo.
start "" "http://localhost:3000"
"%NODE%" "web\server.js"
echo.
echo  Il server si e' fermato. Se c'e' un errore qui sopra, fai uno screenshot.
pause
