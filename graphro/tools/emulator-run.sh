#!/usr/bin/env bash
# Installs the debug build, or the release build (R8-shrunk, signed with a throwaway key),
# then runs the smoke test. Usage: emulator-run.sh <lang> <debug|release> <apk dir> <out dir>
set -u
LANG_ARG=$1; VARIANT=$2; APKS=$3; OUT=$4
if [ "$VARIANT" = release ]; then
  BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
  keytool -genkeypair -keystore /tmp/test.jks -alias t -storepass testpass -keypass testpass -keyalg RSA -keysize 2048 -validity 2 -dname "CN=test" >/dev/null 2>&1
  "$BT/zipalign" -f 4 "$APKS"/release/app-release-unsigned.apk /tmp/aligned.apk
  "$BT/apksigner" sign --ks /tmp/test.jks --ks-pass pass:testpass --out /tmp/release.apk /tmp/aligned.apk
  adb install -r /tmp/release.apk && bash graphro/tools/smoke.sh "$OUT" "$LANG_ARG" com.wertvoll.graphro
else
  adb install -r "$APKS"/debug/app-debug.apk && bash graphro/tools/smoke.sh "$OUT" "$LANG_ARG" com.wertvoll.graphro.debug
fi
