package it.badoni.ivibe;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(YtDlpPlugin.class); // yt-dlp dentro l'app
        super.onCreate(savedInstanceState);
    }
}
