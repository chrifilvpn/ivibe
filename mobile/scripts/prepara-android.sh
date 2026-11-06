#!/usr/bin/env bash
# Sistema il progetto Android creato da Capacitor: icona, plugin yt-dlp, gradle, manifest.
set -ex
APP=android/app
RES=$APP/src/main/res
JAVA=$APP/src/main/java/it/badoni/ivibe

# --- icona
LOGO=../web/public/logo-192.png
for d in "$RES"/mipmap-*dpi; do
  cp "$LOGO" "$d/ic_launcher.png"
  cp "$LOGO" "$d/ic_launcher_round.png"
  cp "$LOGO" "$d/ic_launcher_foreground.png"
done
rm -rf "$RES/mipmap-anydpi-v26"

# --- plugin yt-dlp
cp android-extra/YtDlpPlugin.java "$JAVA/"
cp android-extra/MainActivity.java "$JAVA/MainActivity.java"

# --- manifest: librerie native estratte (servono a yt-dlp/Python) e http in chiaro
MAN=$APP/src/main/AndroidManifest.xml
sed -i '0,/<application/s//<application android:usesCleartextTraffic="true" android:extractNativeLibs="true"/' "$MAN"
grep -q extractNativeLibs "$MAN"

# --- gradle: dipendenza youtubedl-android, solo telefoni ARM (APK più leggero), librerie non compresse
cat >> $APP/build.gradle <<'GRADLE'

// ---- aggiunto da prepara-android.sh (iVibe) ----
android {
    defaultConfig {
        ndk { abiFilters 'arm64-v8a', 'armeabi-v7a' }
    }
    packagingOptions {
        jniLibs { useLegacyPackaging true }
    }
}
dependencies {
    implementation "io.github.junkfood02.youtubedl-android:library:0.18.1"
}
GRADLE

# minSdk 24 (richiesto da youtubedl-android)
sed -i 's/minSdkVersion = [0-9]*/minSdkVersion = 24/' android/variables.gradle
grep minSdkVersion android/variables.gradle
echo "Progetto Android pronto"
