#!/bin/sh
# Builds out/tv-home.apk with the Android SDK tools directly (no Gradle needed).
set -e
cd "$(dirname "$0")"
SDK="${ANDROID_HOME:-$LOCALAPPDATA/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-34/android.jar"

rm -rf out && mkdir -p out/classes out/dex
"$BT/aapt2.exe" compile --dir res -o out/res.zip
"$BT/aapt2.exe" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml out/res.zip
javac -nowarn -Xlint:-options --release 8 -cp "$JAR" -d out/classes src/com/baskar/tvhome/*.java
"$BT/d8.bat" --release --min-api 26 --lib "$JAR" --output out/dex out/classes/com/baskar/tvhome/*.class
cp out/base.apk out/unsigned.apk
(cd out/dex && jar uf ../unsigned.apk classes.dex)
"$BT/zipalign.exe" -f 4 out/unsigned.apk out/aligned.apk
"$BT/apksigner.bat" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out out/tv-home.apk out/aligned.apk
echo "Built out/tv-home.apk"
