package it.badoni.ivibe;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;

/**
 * Plugin di iVibe: fa girare yt-dlp DENTRO il telefono (libreria youtubedl-android),
 * così l'app scarica le canzoni senza bisogno di un server.
 *
 * Dal JavaScript:  Capacitor.Plugins.YtDlp.scarica({ id, videoId, formato })  ->  { path }
 * Eventi:          "progresso"  { id, perc }
 */
@CapacitorPlugin(name = "YtDlp")
public class YtDlpPlugin extends Plugin {

    // fino a 3 download insieme, ognuno nel suo thread
    private final ExecutorService download = Executors.newFixedThreadPool(3);
    private final ExecutorService avvio = Executors.newSingleThreadExecutor();
    private Future<?> preparazione;
    private volatile String erroreAvvio = null;

    @Override
    public void load() {
        // la prima volta yt-dlp (e Python) vengono estratti: ci vuole qualche secondo
        preparazione = avvio.submit(() -> {
            try {
                YoutubeDL.getInstance().init(getContext());
            } catch (Exception e) {
                erroreAvvio = e.getMessage();
                return;
            }
            try {
                // YouTube cambia spesso: si aggiorna yt-dlp a ogni avvio (se c'è internet)
                YoutubeDL.getInstance().updateYoutubeDL(getContext(), YoutubeDL.UpdateChannel._STABLE);
            } catch (Exception ignored) {
                // senza internet si usa la versione già presente
            }
        });
    }

    private void attendiPreparazione() throws Exception {
        try {
            preparazione.get(90, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            // l'aggiornamento è lento: si prova lo stesso con la versione presente
        }
        if (erroreAvvio != null) throw new Exception("yt-dlp non si avvia: " + erroreAvvio);
    }

    @PluginMethod
    public void stato(PluginCall call) {
        JSObject r = new JSObject();
        r.put("pronto", preparazione != null && preparazione.isDone() && erroreAvvio == null);
        r.put("errore", erroreAvvio);
        call.resolve(r);
    }

    @PluginMethod
    public void scarica(PluginCall call) {
        final String id = call.getString("id", "x");
        final String videoId = call.getString("videoId");
        final String formato = "video".equals(call.getString("formato")) ? "video" : "audio";
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            call.reject("Video non valido");
            return;
        }
        call.setKeepAlive(true);

        download.submit(() -> {
            try {
                attendiPreparazione();

                File cartella = new File(getContext().getCacheDir(), "ivibe-download");
                if (!cartella.exists() && !cartella.mkdirs()) throw new Exception("impossibile creare la cartella");
                String ext = formato.equals("video") ? "mp4" : "m4a";
                File file = new File(cartella, videoId + "." + ext);
                if (file.exists()) file.delete();

                YoutubeDLRequest req = new YoutubeDLRequest("https://www.youtube.com/watch?v=" + videoId);
                req.addOption("-f", formato.equals("video")
                        ? "18/best[ext=mp4][vcodec^=avc1][acodec!=none]/best[ext=mp4][acodec!=none]"
                        : "140/bestaudio[ext=m4a]/bestaudio[acodec^=mp4a]");
                req.addOption("--no-playlist");
                req.addOption("--no-mtime");
                req.addOption("--no-part");
                req.addOption("--socket-timeout", "20");
                req.addOption("--retries", "3");
                req.addOption("-o", file.getAbsolutePath());

                YoutubeDL.getInstance().execute(req, id, (Float perc, Long eta, String riga) -> {
                    JSObject ev = new JSObject();
                    ev.put("id", id);
                    ev.put("perc", perc);
                    notifyListeners("progresso", ev);
                    return Unit.INSTANCE;
                });

                if (!file.exists() || file.length() == 0) throw new Exception("file non creato");
                JSObject r = new JSObject();
                r.put("path", file.getAbsolutePath());
                r.put("size", file.length());
                call.resolve(r);
            } catch (Throwable e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                call.reject(msg);
            } finally {
                call.setKeepAlive(false);
                getBridge().releaseCall(call);
            }
        });
    }

    @PluginMethod
    public void annulla(PluginCall call) {
        String id = call.getString("id");
        try {
            if (id != null) YoutubeDL.getInstance().destroyProcessById(id);
        } catch (Exception ignored) {
        }
        call.resolve();
    }

    @PluginMethod
    public void elimina(PluginCall call) {
        String path = call.getString("path");
        File cartella = new File(getContext().getCacheDir(), "ivibe-download");
        if (path != null) {
            File f = new File(path);
            // si cancella solo dentro la nostra cartella
            if (f.getParentFile() != null && f.getParentFile().equals(cartella)) f.delete();
        }
        call.resolve();
    }
}
