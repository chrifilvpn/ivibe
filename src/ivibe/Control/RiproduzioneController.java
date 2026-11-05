package ivibe.Control;

import ivibe.Model.Canzone;
import ivibe.Model.Utente;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Scanner;

public class RiproduzioneController implements Initializable {

    private List<String> codaUrl = new ArrayList<>();
    private int indiceCoda = 0;
    @FXML
    private MediaView mediaView;
    @FXML
    private Label titolo_lbl;
    @FXML
    private Slider progressSlider;
    @FXML
    private Slider volumeSlider;

    private MediaPlayer mediaPlayer;

    private volatile boolean stopDownload = false;
    private volatile Process downloadProcess = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        volumeSlider.setValue(50);

        volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newV.doubleValue() / 100.0);
            }
        });

        progressSlider.setOnMousePressed(e -> seek());
        progressSlider.setOnMouseDragged(e -> seek());

        Platform.runLater(() -> {
            mediaView.getScene().getWindow().setOnHidden(e -> {
                stopAndDispose();
                stopDownload();
            });
        });
    }

    public void startDownload(String url, List<Canzone> coda) {

        codaUrl.clear();
        indiceCoda = 0;

        if (coda != null) {
            for (Canzone c : coda) {
                if (c != null && c.getUrl() != null) {
                    codaUrl.add(c.getUrl());
                }
            }
            for (int i = 0; i < codaUrl.size(); i++) {
                if (codaUrl.get(i).equals(url)) {
                    indiceCoda = i;
                    break;
                }
            }
        }

        scaricaERiproduci(url);
    }

    public void startDownload(String url) {
        startDownload(url, null);
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

    private void seek() {
        if (mediaPlayer == null) {
            return;
        }

        double pos = progressSlider.getValue() / 100.0;

        mediaPlayer.seek(
                mediaPlayer.getTotalDuration().multiply(pos)
        );
    }

    private void stopAndDispose() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    public void stopDownload() {

        stopDownload = true;

        if (downloadProcess != null) {
            downloadProcess.destroyForcibly();
            downloadProcess = null;
        }
    }

    private void scaricaERiproduci(String url) {

        stopDownload = true;
        if (downloadProcess != null) {
            downloadProcess.destroyForcibly();
            downloadProcess = null;
        }

        Platform.runLater(() -> stopAndDispose());

        final String nomeFile = "downloads/canzone.mp4";

        new Thread(() -> {

            try {
                Thread.sleep(300);

                stopDownload = false;

                Platform.runLater(() -> titolo_lbl.setText("Download in corso..."));

                InputStream inputStream = getClass().getResourceAsStream(
                        "/ivibe/resources/tools/yt-dlp.exe"
                );

                if (inputStream == null) {
                    Platform.runLater(() -> titolo_lbl.setText("yt-dlp non trovato"));
                    return;
                }

                Path tempExe = Files.createTempFile("yt-dlp-", ".exe");
                Files.copy(inputStream, tempExe, StandardCopyOption.REPLACE_EXISTING);
                tempExe.toFile().deleteOnExit();

                ProcessBuilder pb = new ProcessBuilder(
                        tempExe.toString(),
                        "-f", "best[ext=mp4]",
                        "-o", nomeFile,
                        "--newline",
                        "--force-overwrites",
                        url
                );

                downloadProcess = pb.start();
                Scanner scanner = new Scanner(downloadProcess.getInputStream());

                while (scanner.hasNextLine()) {
                    if (stopDownload) {
                        if (downloadProcess != null) {
                            downloadProcess.destroyForcibly();
                        }
                        return;
                    }
                    String line = scanner.nextLine();
                    if (line.contains("%")) {
                        try {
                            String percent = line.replaceAll(".*?(\\d+\\.\\d+)%.*", "$1");
                            double progress = Double.parseDouble(percent) / 100.0;
                            Platform.runLater(() -> titolo_lbl.setText("Download: " + (int) (progress * 100) + "%"));
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (downloadProcess != null && !stopDownload) {
                    downloadProcess.waitFor();
                }

                if (stopDownload) {
                    return;
                }

                Platform.runLater(() -> {

                    titolo_lbl.setText("Riproduzione...");

                    File file = new File(nomeFile);
                    if (!file.exists()) {
                        titolo_lbl.setText("File non trovato");
                        return;
                    }

                    Media media = new Media(file.toURI().toString());
                    mediaPlayer = new MediaPlayer(media);
                    mediaView.setMediaPlayer(mediaPlayer);

                    progressSlider.setMin(0);
                    progressSlider.setMax(100);

                    mediaPlayer.setOnReady(() -> {
                        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {

                            if (mediaPlayer == null) {
                                return;
                            }
                            if (progressSlider.isValueChanging()) {
                                return;
                            }

                            javafx.util.Duration total = mediaPlayer.getTotalDuration();
                            if (total == null || total.isUnknown() || total.isIndefinite()) {
                                return;
                            }

                            double totalMs = total.toMillis();
                            if (totalMs <= 0) {
                                return;
                            }

                            double progress = (newTime.toMillis() / totalMs) * 100.0;
                            progressSlider.setValue(progress);
                        });
                    });

                    mediaPlayer.setOnEndOfMedia(() -> {
                        indiceCoda++;
                        if (indiceCoda < codaUrl.size()) {
                            scaricaERiproduci(codaUrl.get(indiceCoda));
                        } else {
                            titolo_lbl.setText("Playlist terminata");
                            progressSlider.setValue(0);
                        }
                    });

                    mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                    mediaPlayer.play();
                });

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }
}
