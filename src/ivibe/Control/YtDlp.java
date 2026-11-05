package ivibe.Control;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

/**
 * Trova (o prepara) l'eseguibile di yt-dlp.
 *
 * - Windows: l'exe incluso nelle risorse viene copiato UNA volta in
 *   tools/yt-dlp.exe (prima veniva copiato in un file temporaneo a ogni canzone).
 * - Linux / macOS: si usa tools/yt-dlp se presente, altrimenti "yt-dlp"
 *   installato nel sistema (es. sudo apt install yt-dlp).
 *
 * All'avvio viene lanciato "yt-dlp -U" in background: YouTube cambia spesso
 * e una versione vecchia di yt-dlp smette di scaricare.
 */
public class YtDlp {

    private static final String RISORSA_EXE = "/ivibe/resources/tools/yt-dlp.exe";

    private static String eseguibile;
    private static Thread threadAggiornamento;

    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    public static synchronized String getEseguibile() throws Exception {

        if (eseguibile != null) {
            return eseguibile;
        }

        if (isWindows()) {
            File exe = new File("tools/yt-dlp.exe");

            if (!exe.exists()) {
                exe.getParentFile().mkdirs();
                try (InputStream in = YtDlp.class.getResourceAsStream(RISORSA_EXE)) {
                    if (in == null) {
                        throw new IllegalStateException("yt-dlp.exe non trovato nelle risorse");
                    }
                    Files.copy(in, exe.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            eseguibile = exe.getAbsolutePath();

        } else {
            File locale = new File("tools/yt-dlp");
            if (locale.exists() && locale.canExecute()) {
                eseguibile = locale.getAbsolutePath();
            } else {
                eseguibile = "yt-dlp";
            }
        }

        return eseguibile;
    }

    /** Aggiorna yt-dlp in background (una volta per avvio). Gli errori vengono ignorati. */
    public static synchronized void aggiornaInBackground() {

        if (threadAggiornamento != null) {
            return;
        }

        threadAggiornamento = new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(getEseguibile(), "-U");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                // svuota l'output, altrimenti il processo può bloccarsi
                try (InputStream out = p.getInputStream()) {
                    byte[] buf = new byte[1024];
                    while (out.read(buf) != -1) {
                        // ignora
                    }
                }
                if (!p.waitFor(60, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (Exception e) {
                System.out.println("Aggiornamento yt-dlp non riuscito: " + e.getMessage());
            }
        });
        threadAggiornamento.setDaemon(true);
        threadAggiornamento.start();
    }

    /** Se l'aggiornamento è ancora in corso, aspetta (al massimo 30 secondi). */
    public static void attendiAggiornamento() {
        Thread t = threadAggiornamento;
        if (t != null && t.isAlive()) {
            try {
                t.join(30000);
            } catch (InterruptedException ignored) {
            }
        }
    }
}
