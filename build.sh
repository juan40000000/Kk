#!/usr/bin/env bash
# Compila el APK sin Gradle ni Android SDK completo.
# Requiere: JDK, aapt, dalvik-exchange (dx), apksigner  ->  apt-get install aapt apksigner dalvik-exchange
# y un android.jar (API 34) en tools/android.jar (se descarga si falta).
set -euo pipefail
cd "$(dirname "$0")"

OUT=build
APK_NAME=PulsoGlobal.apk
ANDROID_JAR=${ANDROID_JAR:-tools/android.jar}
KEYSTORE=${KEYSTORE:-android/debug.keystore}

if [ ! -f "$ANDROID_JAR" ]; then
  mkdir -p tools
  curl -sSL -o "$ANDROID_JAR" \
    https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-34/android.jar
fi

DX=$(command -v dx || command -v dalvik-exchange)

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/assets/web"
cp -r web/. "$OUT/assets/web/"

echo "› recursos"
aapt package -f -m -J "$OUT/gen" -M android/AndroidManifest.xml -S android/res \
  -I "$ANDROID_JAR" -A "$OUT/assets" -F "$OUT/unsigned.apk"

echo "› java"
javac -nowarn -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" $(find "$OUT/gen" android/src -name '*.java') 2>&1 | grep -v "^warning" || true

echo "› dex"
"$DX" --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)

echo "› firma"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias pulso -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Pulso Global, O=Pulso Global, C=AR" >/dev/null 2>&1
fi
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias pulso --out "$OUT/$APK_NAME" "$OUT/aligned.apk"
apksigner verify "$OUT/$APK_NAME"

mkdir -p dist && cp "$OUT/$APK_NAME" dist/
echo "✓ dist/$APK_NAME ($(du -h dist/$APK_NAME | cut -f1))"
