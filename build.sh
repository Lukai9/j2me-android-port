#!/usr/bin/env bash
# Builds j2me-touch-port.apk. No games are bundled; players import their own .jar in the app.
# Needs: JDK 17+, Android SDK with build-tools 34.0.0 and platforms/android-34, curl.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))"
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*) SDK_DEFAULT="$(cygpath -m "$LOCALAPPDATA")/Android/Sdk" ;;
  Darwin) SDK_DEFAULT="$HOME/Library/Android/sdk" ;;
  *) SDK_DEFAULT="$HOME/Android/Sdk" ;;
esac
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$SDK_DEFAULT}}"
BT="$SDK/build-tools/34.0.0"
ANDROID_JAR="$SDK/platforms/android-34/android.jar"
OUT="$HERE/build"
EXE=""; [ -f "$BT/aapt2.exe" ] && EXE=".exe"
BAT=""; [ -f "$BT/d8.bat" ] && BAT=".bat"

# dx converts game bytecode on the phone. Apache 2.0, from AOSP via Maven Central.
DX_VER="11.0.0_r3"
DX_SHA256="37581a56fded15ec6f88b46d3c7704cc700a3371f9e75f41b2121e3287769ba9"
DX_JAR="$HERE/libs/dalvik-dx-$DX_VER.jar"
if [ ! -f "$DX_JAR" ]; then
  echo "== Downloading dalvik-dx $DX_VER"
  mkdir -p "$HERE/libs"
  curl -sSfL -o "$DX_JAR" \
    "https://repo1.maven.org/maven2/com/jakewharton/android/repackaged/dalvik-dx/$DX_VER/dalvik-dx-$DX_VER.jar"
fi
echo "$DX_SHA256  $DX_JAR" | sha256sum -c --quiet - || { echo "dx checksum mismatch"; rm -f "$DX_JAR"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

echo "== Launcher icons"
for d in mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192; do
  java "$HERE/tools/MakeIcon.java" "$OUT/res/mipmap-${d%%:*}/ic_launcher.png" "${d##*:}"
done

echo "== aapt2 compile + link"
"$BT/aapt2$EXE" compile --dir "$OUT/res" -o "$OUT/res.zip"
"$BT/aapt2$EXE" link -I "$ANDROID_JAR" --manifest "$HERE/AndroidManifest.xml" \
  --min-sdk-version 21 --target-sdk-version 34 --java "$OUT/gen" \
  -o "$OUT/base.apk" "$OUT/res.zip"

echo "== javac"
find "$HERE/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
  -cp "$DX_JAR" -d "$OUT/classes" @"$OUT/sources.txt"

echo "== d8"
# dx's multidex helper isn't used and trips d8, so leave it out.
(mkdir -p "$OUT/dx" && cd "$OUT/dx" && unzip -q "$DX_JAR" -x 'com/android/multidex/*' 'META-INF/*')
find "$OUT/classes" "$OUT/dx" -name '*.class' > "$OUT/classlist.txt"
"$BT/d8$BAT" --release --min-api 21 --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classlist.txt"

echo "== Packaging"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && "$BT/aapt$EXE" add "$OUT/unsigned.apk" classes*.dex >/dev/null)
"$BT/zipalign$EXE" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# Your signing key. Keep it private and keep it: updates must be signed with the same key.
KS="${KEYSTORE:-$HERE/release.keystore}"
KS_PASS="${KEYSTORE_PASS:-android}"
if [ ! -f "$KS" ]; then
  echo "== Creating signing key $KS"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias j2me \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=J2ME Touch Port" >/dev/null 2>&1
fi
"$BT/apksigner$BAT" sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$HERE/j2me-touch-port.apk" "$OUT/aligned.apk"
"$BT/apksigner$BAT" verify "$HERE/j2me-touch-port.apk"
echo "== Done: $HERE/j2me-touch-port.apk"
