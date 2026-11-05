package ivibe.Control;

import ivibe.Model.Canzone;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RiproduzioneController implements Initializable {

    private static final Pattern PERCENTUALE = Pattern.compile("(\\d+(?:\\.\\d+)?)%");

    @FXML
    private MediaView mediaView;
    @FXML
    private Label titolo_lbl;
    @FXML
    private Label stato_lbl;
    @FXML
    private Label tempo_lbl;
    @FXML
    private Slider progressSlider;
    @FXML
    private Slider volumeSlider;
    @FXML
    private Button prev_btn;
    @FXML
    private Button next_btn;

    private MediaPlayer mediaPlayer;

    private final List<Canzone> coda = new ArrayList<>();
    private int indiceCoda = 0;

    /**
     * Ogni nuova canzone incrementa questo numero: un download vecchio
     * che finisce dopo vede che il numero è cambiato e non fa niente.
     * (prima si usava un solo flag boolean e due download potevano accavallarsi)
     */
    private volatile int generazione = 0;
    private volatile Process downloadProcess = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        volumeSlider.setValue(50);
        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newV.doubleValue() / 100.0);
            }
        });

        progressSlider.setMin(0);
        progressSlider.setMax(100);
        // il seek avviene quando l'utente rilascia (o clicca) lo slider
        progressSlider.setOnMouseReleased(e -> seek());
        progressSlider.valueChangingProperty().addListener((obs, eraInCambio, inCambio) -> {
            if (!inCambio) {
                seek();
            }
        });

        Platform.runLater(() -> {
            if (mediaView.getScene() != null && mediaView.getScene().getWindow() != null) {
                mediaView.getScene().getWindow().setOnHidden(e -> chiudi());
            }
        });

        aggiornaPulsanti();
    }

    /**
     * Riproduce la canzone scelta. La coda (es. la playlist o la lista
     * visualizzata) serve per passare alla canzone successiva in automatico.
     */
    public void riproduci(Canzone canzone, List<Canzone> nuovaCoda) {

        coda.clear();
        if (nuovaCoda != null) {
            for (Canzone c : nuovaCoda) {
                if (c != null && c.getUrl() != null) {
                    coda.add(c);
                }
            }
        }

        if (!coda.contains(canzone)) {
            coda.add(0, canzone);
        }
        indiceCoda = coda.indexOf(canzone);

        avvia(canzone);
    }

    @FXML
    public void play() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    @FXML
    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    @FXML
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            progressSlider.setValue(0);
        }
    }

    @FXML
    public void successiva() {
        if (indiceCoda + 1 < coda.size()) {
            indiceCoda++;
            avvia(coda.get(indiceCoda));
        }
    }

    @FXML
    public void precedente() {
        // se la canzone è avanti di più di 3 secondi, ricomincia da capo
        if (mediaPlayer != null && mediaPlayer.getCurrentTime().toSeconds() > 3) {
            mediaPlayer.seek(Duration.ZERO);
            return;
        }
        if (indiceCoda > 0) {
            indiceCoda--;
            avvia(coda.get(indiceCoda));
        }
    }

    private void aggiornaPulsanti() {
        if (next_btn != null) {
            next_btn.setDisable(indiceCoda + 1 >= coda.size());
        }
    }

    private void seek() {
        if (mediaPlayer == null) {
            return;
        }
        Duration totale = mediaPlayer.getTotalDuration();
        if (totale == null || totale.isUnknown() || totale.isIndefinite()) {
            return;
        }
        mediaPlayer.seek(totale.multiply(progressSlider.getValue() / 100.0));
    }

    /** Ferma tutto: chiamato quando la finestra viene chiusa. */
    public void chiudi() {
        generazione++;
        fermaDownload();
        fermaPlayer();
    }

    private void fermaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private void fermaDownload() {
        Process p = downloadProcess;
        if (p != null) {
            p.destroyForcibly();
            downloadProcess = null;
        }
    }

    private void stato(String testo) {
        if (stato_lbl != null) {
            stato_lbl.setText(testo);
        }
    }

    /** Ogni canzone ha il suo file: se è già stata scaricata parte subito. */
    private File fileCanzone(Canzone c) {
        return new File("downloads", "canzone_" + c.getN() + ".mp4");
    }

    private void avvia(Canzone canzone) {

        final int mia = ++generazione;

        fermaDownload();
        fermaPlayer();

        titolo_lbl.setText("🎵 " + canzone.getTitolo() + " — " + canzone.getArtista());
        if (titolo_lbl.getScene() != null && titolo_lbl.getScene().getWindow() instanceof Stage) {
            ((Stage) titolo_lbl.getScene().getWindow()).setTitle(canzone.getTitolo());
        }
        progressSlider.setValue(0);
        if (tempo_lbl != null) {
            tempo_lbl.setText("0:00 / 0:00");
        }
        aggiornaPulsanti();

        final File file = fileCanzone(canzone);

        if (file.exists() && file.length() > 0) {
            avviaPlayer(file, mia);
            return;
        }

        stato("Preparazione download...");

        Thread t = new Thread(() -> {
            try {
                YtDlp.attendiAggiornamento();
                if (mia != generazione) {
                    return;
                }

                file.getParentFile().mkdirs();

                ProcessBuilder pb = new ProcessBuilder(
                        YtDlp.getEseguibile(),
                        // JavaFX riproduce solo MP4 (H.264 + AAC): il formato 18 c'è quasi sempre
                        "-f", "best[ext=mp4][vcodec!=none][acodec!=none]/18",
                        "-o", file.getPath(),
                        "--no-playlist",
                        "--newline",
                        "--force-overwrites",
                        canzone.getUrl()
                );
                pb.redirectErrorStream(true); // altrimenti stderr pieno può bloccare il processo

                Process p = pb.start();
                downloadProcess = p;

                StringBuilder ultimaRiga = new StringBuilder();

                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (mia != generazione) {
                            p.destroyForcibly();
                            return;
                        }
                        if (line.contains("ERROR")) {
                            ultimaRiga.setLength(0);
                            ultimaRiga.append(line);
                        }
                        Matcher m = PERCENTUALE.matcher(line);
                        if (line.contains("[download]") && m.find()) {
                            final String perc = m.group(1);
                            Platform.runLater(() -> {
                                if (mia == generazione) {
                                    stato("Download: " + (int) Double.parseDouble(perc) + "%");
                                }
                            });
                        }
                    }
                }

                int codice = p.waitFor();
                downloadProcess = null;

                if (mia != generazione) {
                    return;
                }

                if (codice != 0 || !file.exists()) {
                    String errore = ultimaRiga.length() > 0 ? ultimaRiga.toString() : "codice " + codice;
                    System.out.println("yt-dlp: " + errore);
                    Platform.runLater(() -> stato("❌ Download non riuscito (" + errore + ")"));
                    return;
                }

                Platform.runLater(() -> avviaPlayer(file, mia));

            } catch (java.io.IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> stato(YtDlp.isWindows()
                        ? "❌ Impossibile avviare yt-dlp"
                        : "❌ yt-dlp non trovato: installalo con 'sudo apt install yt-dlp'"));
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> stato("❌ Errore: " + e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void avviaPlayer(File file, int mia) {

        if (mia != generazione) {
            return;
        }

        try {
            Media media = new Media(file.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            mediaPlayer = player;
            mediaView.setMediaPlayer(player);

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                Duration total = player.getTotalDuration();
                if (total == null || total.isUnknown() || total.isIndefinite() || total.toMillis() <= 0) {
                    return;
                }
                if (!progressSlider.isValueChanging() && !progressSlider.isPressed()) {
                    progressSlider.setValue(newTime.toMillis() / total.toMillis() * 100.0);
                }
                if (tempo_lbl != null) {
                    tempo_lbl.setText(formatta(newTime) + " / " + formatta(total));
                }
            });

            player.setOnError(() -> {
                stato("❌ Impossibile riprodurre il file");
                // file probabilmente rovinato: lo cancelliamo così la prossima volta si riscarica
                if (mediaPlayer == player) {
                    mediaPlayer = null;
                }
                player.dispose();
                file.delete();
            });

            player.setOnEndOfMedia(() -> {
                if (indiceCoda + 1 < coda.size()) {
                    successiva();
                } else {
                    stato("Fine della coda");
                    progressSlider.setValue(0);
                }
            });

            player.setVolume(volumeSlider.getValue() / 100.0);
            player.play();
            stato("In riproduzione" + (coda.size() > 1 ? "  (" + (indiceCoda + 1) + "/" + coda.size() + ")" : ""));
            aggiornaPulsanti();

        } catch (Exception e) {
            e.printStackTrace();
            stato("❌ Impossibile riprodurre il file");
        }
    }

    private static String formatta(Duration d) {
        int sec = (int) Math.floor(d.toSeconds());
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }
}
