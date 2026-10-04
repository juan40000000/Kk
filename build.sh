#!/usr/bin/env bash
# Compila el APK sin Gradle ni Android SDK completo.
# Requiere: javac (JDK 11+), aapt, dalvik-exchange (dx), zipalign, apksigner, keytool
#   sudo apt-get install aapt dalvik-exchange zipalign apksigner
set -euo pipefail
cd "$(dirname "$0")"

OUT=build
APP=app
JAR=$OUT/android.jar
mkdir -p $OUT

if [ ! -f "$JAR" ]; then
  echo "Descargando android.jar (API 33)..."
  curl -fsSL -o "$JAR" https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar
fi

if [ ! -f keystore/fuegos.jks ]; then
  mkdir -p keystore
  keytool -genkeypair -keystore keystore/fuegos.jks -storepass fuegos123 -keypass fuegos123 \
    -alias fuegos -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Fuegos Artificiales, O=Kk, C=AR"
fi

rm -rf $OUT/classes $OUT/gen $OUT/*.apk $OUT/classes.dex
mkdir -p $OUT/classes $OUT/gen

echo "[1/5] Recursos (aapt)"
aapt package -f -m -J $OUT/gen -M $APP/AndroidManifest.xml -S $APP/res -I "$JAR" \
  -F $OUT/unsigned.apk

echo "[2/5] Compilando Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$JAR" -d $OUT/classes \
  $(find $APP/src $OUT/gen -name '*.java')

echo "[3/5] DEX"
dalvik-exchange --dex --min-sdk-version=21 --output=$OUT/classes.dex $OUT/classes

echo "[4/5] Empaquetando"
(cd $OUT && aapt add -f unsigned.apk classes.dex >/dev/null)
zipalign -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[5/5] Firmando"
apksigner sign --ks keystore/fuegos.jks --ks-pass pass:fuegos123 --key-pass pass:fuegos123 \
  --ks-key-alias fuegos --min-sdk-version 21 --out $OUT/FuegosArtificiales.apk $OUT/aligned.apk
apksigner verify --verbose $OUT/FuegosArtificiales.apk | head -5
mkdir -p apk
cp $OUT/FuegosArtificiales.apk apk/FuegosArtificiales.apk
ls -la apk/FuegosArtificiales.apk
