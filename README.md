# iVibe 🎵

La tua musica, senza confini. Il progetto ha due versioni che condividono gli stessi account:

- **Sito web** (cartella [`web/`](web)) — da mettere online su **Render**, si usa da PC e telefono.
- **App desktop** in **Java + JavaFX** (progetto NetBeans) con database **MySQL**.

Gruppo: Christian Pala, Filippo Meoli, Giovan Battista Mazzoleni, Giacomo Marzani.

---

## 🌐 Sito web su Render

### Mettere online (una volta sola)
1. Vai su [dashboard.render.com](https://dashboard.render.com) e accedi con GitHub.
2. **New → Blueprint**, scegli la repository **ivibe** e premi **Apply**.
   Render legge [`render.yaml`](render.yaml) e crea da solo:
   - il sito `ivibe` (Node, piano gratuito);
   - il database Postgres gratuito `ivibe-db`, dove vengono salvati utenti, preferiti e playlist;
   - la chiave segreta delle sessioni (`SESSION_SECRET`).
3. Dopo 2-3 minuti il sito è su `https://ivibe.onrender.com` (o un nome simile, lo vedi nella dashboard).
   Ogni `git push` su `main` lo aggiorna in automatico.

Gli account iniziali e le loro password sono nel file `credenziali-ivibe.txt`, che **non** è nel
repository (le password sono salvate solo come hash).

### Cose da sapere sul piano gratuito
- Il sito si **addormenta dopo 15 minuti** senza visite: la prima apertura dopo può metterci ~1 minuto.
- Il **database gratuito scade dopo 30 giorni** (Render avvisa per email). Prima della scadenza
  puoi passare al piano a pagamento oppure cancellarlo e ricrearlo (New → Postgres, piano Free,
  poi copia l'*Internal Database URL* nella variabile `DATABASE_URL` del sito): al primo avvio
  iVibe ricarica da solo catalogo e account iniziali.
- Se Render dice che hai già un database gratuito, togli la parte `databases:` (e `DATABASE_URL`)
  da `render.yaml`: il sito funziona lo stesso salvando su file, ma ogni riavvio cancella
  registrazioni, preferiti e playlist nuove (catalogo e account iniziali restano). Il pannello
  admin mostra un avviso quando è in questa modalità.

### Download e ascolto offline
- Accanto a ogni canzone c'è **↓**: il server la scarica da YouTube con **yt-dlp** e il browser la
  salva sul dispositivo. Nella sezione **Scaricati** trovi tutto, puoi scegliere *solo audio*
  (m4a, ~4 MB) o *video* (mp4 360p) e attivare "scarica in automatico quello che ascolti".
- Le canzoni che YouTube non lascia riprodurre fuori dal suo sito (errore "impossibile
  riprodurre fuori da YouTube") vengono scaricate e fatte partire da sole.
- Senza internet il sito si apre lo stesso (service worker) e suonano le canzoni scaricate.
  Sul telefono puoi "installarlo": menu del browser → *Aggiungi a schermata Home*.
- I download partono in parallelo (3 dal browser, fino a 4 processi yt-dlp sul server).
- yt-dlp viene scaricato in automatico da `npm install` (`web/bin/`) e aggiornato a ogni avvio.

Variabili utili su Render (*Environment*):

| Variabile | A cosa serve |
|---|---|
| `DOWNLOAD_ATTIVI=false` | spegne i download (restano solo lo streaming e i file già scaricati) |
| Secret File `cookies.txt` | se YouTube blocca il server ("YouTube ha bloccato il download"): *Environment → Secret Files → Add*, nome `cookies.txt`, contenuto = file esportato dal browser (vedi sotto). In alternativa la variabile `YTDLP_COOKIES` con lo stesso contenuto |
| `DOWNLOAD_PARALLELI` | quanti yt-dlp insieme sul server (predefinito 4) |

**Esportare i cookie di YouTube** (usa un account Google *secondario*, non il tuo principale):
1. In Chrome installa l'estensione **Get cookies.txt LOCALLY**.
2. Apri una finestra in incognito, consenti l'estensione in incognito, accedi a YouTube con l'account secondario.
3. Su youtube.com apri l'estensione → **Export** (formato Netscape) → salva `cookies.txt`.
4. Chiudi la finestra in incognito *senza uscire* dall'account (se esci, i cookie smettono di valere).
5. Su Render carica il file come Secret File `cookies.txt` e fai *Manual Deploy*. Nel log deve
   comparire `download attivi (con cookie YouTube)`.

> ⚠ YouTube blocca spesso i download dai server cloud: se succede, i download funzionano
> comunque avviando iVibe sul tuo PC (`npm install` e `npm start`). Scaricare da YouTube va
> contro i suoi termini d'uso: tieni i download per uso personale e per il progetto.

### Usarlo su un PC senza essere amministratore (es. a scuola)
1. Scarica lo ZIP del progetto ed estrailo.
2. Scarica **node.exe** da https://nodejs.org/dist/latest-v22.x/win-x64/node.exe e mettilo nella
   cartella del progetto, accanto a `avvia-windows.bat`.
3. Doppio clic su `avvia-windows.bat`: si apre `http://localhost:3000`.
   Non serve installare niente: yt-dlp è già nel progetto e si aggiorna da solo.

### Provarlo sul tuo PC
```bash
cd web
node server.js          # poi apri http://localhost:3000
npm test                # test automatici delle API
```
Serve Node 20 o più recente. Senza `DATABASE_URL` i dati vanno in `web/data/ivibe.json`.

### Come funziona
- **Server** ([`web/server.js`](web/server.js)) scritto solo con i moduli di Node, senza framework;
  l'unica dipendenza è `pg`, usata solo se c'è il database.
- **Riproduzione** con il player ufficiale di YouTube (IFrame API): niente download, niente
  yt-dlp sul server (YouTube blocca i download dai server cloud). Coda, casuale, ripeti,
  avanti/indietro, volume, comandi dal blocco schermo del telefono, tasti `spazio` / `N` / `P`.
- **Dati** in [`web/lib/archivio.js`](web/lib/archivio.js): un unico documento JSON salvato su
  Postgres (o su file); al primo avvio viene caricato [`web/seed/seed.json`](web/seed/seed.json).
- **Sicurezza**: password con PBKDF2 (stesso formato dell'app desktop), sessione in cookie
  firmato `HttpOnly`, limite ai tentativi di login, Content-Security-Policy, controlli sul server
  su ogni richiesta (un utente non può toccare le playlist degli altri, solo gli admin
  gestiscono catalogo e utenti).
- **Admin**: incolli il link YouTube e titolo/artista si compilano da soli; si possono
  modificare ed eliminare canzoni e gestire i ruoli degli utenti.

---

## 🖥 App desktop (JavaFX + MySQL)

### Come avviarla

#### 1. Database
1. Avvia MySQL (o MariaDB, ad es. con XAMPP).
2. Apri **HeidiSQL**, collegati al server e fai **File → Esegui file SQL…** scegliendo
   [`database/ivibe.sql`](database/ivibe.sql).
   Lo script crea il database `ivibe`, tutte le tabelle, il catalogo e gli account iniziali.
3. Se il tuo MySQL **non** è `localhost:3306` con utente `root` senza password, copia
   `db.properties.example` in `db.properties` e cambia i valori.

#### 2. Driver MySQL
Scarica `mysql-connector-j-8.4.0.jar` e mettilo nella cartella [`lib/`](lib/LEGGIMI.txt)
(il progetto NetBeans lo cerca già lì).

#### 3. Avvio
Apri la cartella come progetto in NetBeans ed esegui (JDK 8 con JavaFX, come prima).

Gli account sono gli stessi del sito, con le password del file `credenziali-ivibe.txt`.

> Su **Linux** l'app usa `yt-dlp` installato nel sistema (`sudo apt install yt-dlp` oppure
> `pipx install yt-dlp`); su **Windows** usa `yt-dlp.exe` incluso nel progetto.

---

### Struttura

```
src/ivibe/
├── IVibe.java                 avvio dell'app
├── Database/                  accesso al database (JDBC)
│   ├── Database.java          connessione + lettura di db.properties
│   ├── UtenteDAO.java         tabella utenti
│   ├── CanzoneDAO.java        tabella canzoni
│   ├── PreferitiDAO.java      tabella preferiti
│   └── PlaylistDAO.java       tabelle playlist e playlist_canzoni
├── Model/                     Canzone, Utente, Playlist, Sessione, PasswordEncoder, Validatore
├── Control/                   controller JavaFX + Finestre (apertura finestre) + YtDlp
└── View/                      file FXML
database/
└── ivibe.sql                  schema + dati
```

#### Schema del database

| Tabella            | Contenuto                                                        |
|--------------------|------------------------------------------------------------------|
| `utenti`           | id, nickname (unico), email (unica), password (hash), data di nascita, is_admin |
| `canzoni`          | id, titolo, artista, durata, url (unico)                         |
| `preferiti`        | utente_id + canzone_id                                           |
| `playlist`         | id, utente_id, nome (unico per utente)                           |
| `playlist_canzoni` | playlist_id + canzone_id + posizione                             |

Le chiavi esterne hanno `ON DELETE CASCADE`: se si elimina un utente o una canzone,
spariscono anche i relativi preferiti e le voci nelle playlist.

---

### Cosa è stato sistemato

**Dati / database**
- Tutti i dati (utenti, canzoni, preferiti, playlist) passano da CSV a MySQL, con query
  `PreparedStatement` (niente SQL injection).
- Cancellando un account restavano preferiti e playlist "orfani" nei CSV (c'erano già righe di
  utenti inesistenti): ora li cancella il database.
- Nel catalogo c'erano 2 canzoni con URL duplicato: *Rolling Stone* (doppione di
  *Like a Rolling Stone*) e *Bad Habit* di Steve Lacy, che aveva il link di *Bad Habits* di Ed
  Sheeran. Non sono state importate: *Bad Habit* va riaggiunta dall'admin con il link giusto.
- I preferiti puntavano a canzoni inesistenti (id 3 e 4): ripuliti.
- Il metodo `scriviCSV` di `IVibe` scriveva le colonne in un ordine diverso da quello letto
  (url al posto dell'artista): rimosso.

**Sicurezza**
- Le password erano solo in Base64 (si potevano decodificare, c'era perfino un metodo
  `decode`): ora sono salvate con **PBKDF2 + salt casuale**. Le vecchie password funzionano
  ancora e vengono convertite in automatico al primo login.
- L'account `ivibe_admin` aveva la password in chiaro nel file e non riusciva a fare login.
- Le password vecchie (Base64, cioè leggibili da chiunque) sono state sostituite con password nuove.
- Cambio password: ora valgono le stesse regole della registrazione (maiuscola, numero, simbolo).
- Non si può più eliminare l'ultimo admin rimasto; conferme prima di ogni eliminazione.

**Bug dell'interfaccia**
- Logout dalla schermata utente e dalle impostazioni cercava `Login.fxml` (con la L maiuscola):
  dentro il `.jar` non funzionava.
- Ogni click su una playlist aggiungeva un nuovo listener alla ricerca: dopo un po' la ricerca
  agiva su playlist sbagliate e rallentava.
- Togliendo un cuore nella pagina Preferiti la canzone restava in lista.
- Tornando alla Home la ricerca scritta veniva ignorata.
- Il pulsante ▶ nella barra in basso non faceva nulla.
- Il colore delle righe selezionate nelle tabelle admin restava "attaccato" a righe sbagliate
  scorrendo.
- Dalle impostazioni dell'admin, il logout lasciava aperta la finestra admin.
- La validazione email accettava solo `.com` e `.it`: ora accetta qualsiasi email valida.

**Riproduzione**
- Tutte le canzoni venivano scaricate sullo stesso file `canzone.mp4`: aprendo due lettori il
  download falliva (file in uso). Ora c'è **un solo lettore** e ogni canzone ha il suo file in
  `downloads/`, così se l'hai già ascoltata **parte subito** senza riscaricarla.
- `yt-dlp.exe` veniva copiato in un file temporaneo a ogni canzone: ora una volta sola, e viene
  **aggiornato in automatico** all'avvio (le versioni vecchie smettono di funzionare con YouTube).
- L'output di errore di yt-dlp non veniva letto e poteva bloccare il processo; ora gli errori
  vengono mostrati nel lettore.
- Due download potevano accavallarsi cambiando canzone velocemente.
- Il controllo "URL raggiungibile" dell'admin bloccava la finestra: ora è in background.

**Novità**
- Pulsanti ⏮ / ⏭, tempo trascorso/totale, titolo della canzone nel lettore.
- Riproducendo dalla tabella, le canzoni successive partono in automatico (coda).
- Doppio click su una riga per riprodurla, Invio per fare login.
- Funziona anche su Linux (yt-dlp di sistema).

---

## Idee per migliorarlo ancora

- **Thread separati per il database**: ora le query sono veloci perché il DB è locale, ma con
  un server remoto conviene usare `Task` di JavaFX per non bloccare la grafica.
- **Pool di connessioni** (es. HikariCP) invece di aprire una connessione per ogni query.
- **Durata automatica**: leggere titolo, artista e durata direttamente da yt-dlp
  (`yt-dlp --print "%(title)s|%(duration)s" URL`) quando l'admin incolla il link.
- **Solo audio**: scaricare l'audio (`-f 140`, m4a) invece del video: file 5-10 volte più
  piccoli e download più rapidi.
- Pulizia della cartella `downloads/` quando supera una certa dimensione.
- Riordinare le canzoni di una playlist con il drag & drop (la colonna `posizione` c'è già).
- Modifica di una canzone dal pannello admin (ora si può solo aggiungere/rimuovere) e ricerca
  nelle tabelle admin.
- Spostare gli stili ripetuti nei controller in un file `.css` unico.
- Test JUnit per `PasswordEncoder`, `Validatore` e i DAO.
- Passare a Maven/Gradle e a JavaFX 21 (OpenJFX) per non dipendere dal JDK 8 di Oracle.
