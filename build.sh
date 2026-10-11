#!/bin/bash
# Builds Fanfold.apk without Gradle, using the Ubuntu/Debian Android packages:
#   sudo apt install default-jdk aapt dalvik-exchange apksigner zipalign android-sdk-platform-23
#
# Signing:
#   KEYSTORE=/path/to/release.jks KEYSTORE_PASS=... KEY_ALIAS=... ./build.sh   -> release-signed APK
#   ./build.sh                                                                -> signed with a local
#       throwaway key (build/debug.jks). Fine for testing, but it can't update an install made with
#       a different key.
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_TOOL_OPTIONS=

AJ=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
OUT=build
SRC=app/src/main

# Read package id / versions / SDK levels from app/build.gradle so there's one source of truth.
g() { grep -E "^\s*$1\s" app/build.gradle | head -1 | sed -E "s/^\s*$1\s+\"?([^\"]*)\"?\s*$/\1/"; }
APP_ID=$(g applicationId); VCODE=$(g versionCode); VNAME=$(g versionName)
MIN_SDK=$(g minSdk); TARGET_SDK=$(g targetSdk)
echo "== $APP_ID $VNAME ($VCODE), minSdk $MIN_SDK, targetSdk $TARGET_SDK"

rm -rf "$OUT"; mkdir -p "$OUT/gen" "$OUT/classes"

# Gradle keeps package/version/sdk out of the manifest; aapt needs them in it.
python3 - "$SRC/AndroidManifest.xml" "$OUT/AndroidManifest.xml" "$APP_ID" "$VCODE" "$VNAME" "$MIN_SDK" "$TARGET_SDK" <<'PY'
import sys
src, dst, pkg, vc, vn, mn, tg = sys.argv[1:]
s = open(src, encoding="utf-8").read()
tag = '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
assert tag in s, "unexpected <manifest> tag"
s = s.replace(tag, f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}" '
                   f'android:versionCode="{vc}" android:versionName="{vn}">\n    '
                   f'<uses-sdk android:minSdkVersion="{mn}" android:targetSdkVersion="{tg}" />', 1)
open(dst, "w", encoding="utf-8").write(s)
PY

echo "== resources"
aapt package -f -m -M "$OUT/AndroidManifest.xml" -S "$SRC/res" -I "$AJ" -J "$OUT/gen" -F "$OUT/unsigned.apk"

echo "== compile"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$AJ" \
      -d "$OUT/classes" $(find "$SRC/java" "$OUT/gen" -name '*.java')

echo "== dex"
dalvik-exchange --dex --min-sdk-version="$MIN_SDK" --output="$OUT/classes.dex" "$OUT/classes"

echo "== package"
(cd "$OUT" && aapt add unsigned.apk classes.dex > /dev/null)
zipalign -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ -n "${KEYSTORE:-}" ]; then
  KS=$KEYSTORE; KS_PASS=${KEYSTORE_PASS:?set KEYSTORE_PASS}; ALIAS=${KEY_ALIAS:-fanfold}
else
  KS=$OUT/debug.jks; KS_PASS=debugpass; ALIAS=debug
  echo "== no KEYSTORE set: signing with a throwaway local key"
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" -alias "$ALIAS" \
          -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Fanfold debug" > /dev/null 2>&1
fi

echo "== sign"
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --ks-key-alias "$ALIAS" --out "$OUT/Fanfold.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/Fanfold.apk"
ls -la "$OUT/Fanfold.apk"
