#!/usr/bin/env bash
# Builds the signed, zip-aligned production APK of Lafz and prints its path.
#   tools/build-release.sh          -> dist/Lafz_<version>-release.apk
#
# Signing key: ~/.android/lafz-release.jks (password in ~/.android/lafz-release.properties, chmod 600).
# Created on first run. BACK BOTH FILES UP and never commit them: every future update must be signed with the
# same key, otherwise phones refuse to update the app (you would have to uninstall it first).
set -euo pipefail
cd "$(dirname "$0")/.."
# shellcheck disable=SC1090
[ -f "$HOME/.android-env.sh" ] && . "$HOME/.android-env.sh"
: "${ANDROID_HOME:=$HOME/Android/Sdk}"

KEYSTORE="$HOME/.android/lafz-release.jks"
KEYPROPS="$HOME/.android/lafz-release.properties"
ALIAS=lafz

if [ ! -f "$KEYSTORE" ]; then
    echo ">> No release key yet, creating $KEYSTORE"
    mkdir -p "$HOME/.android"
    PASS=$(head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)
    keytool -genkeypair -keystore "$KEYSTORE" -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 36500 \
        -storepass "$PASS" -keypass "$PASS" -dname "CN=Lafz Keyboard, O=Lafz"
    printf 'storePassword=%s\nkeyAlias=%s\n' "$PASS" "$ALIAS" > "$KEYPROPS"
    chmod 600 "$KEYSTORE" "$KEYPROPS"
    echo ">> Created. Back up $KEYSTORE and $KEYPROPS somewhere safe."
fi
PASS=$(sed -n 's/^storePassword=//p' "$KEYPROPS")

BT=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)
./gradlew assembleRelease -q

SRC=$(ls app/build/outputs/apk/release/*.apk | head -1)
mkdir -p dist
NAME=$(basename "$SRC" | sed 's/-unsigned//')
OUT="dist/$NAME"
rm -f "$OUT" "$OUT.idsig"
"$BT/zipalign" -f -p 4 "$SRC" "$OUT.aligned"
"$BT/apksigner" sign --v3-signing-enabled false --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass "pass:$PASS" --out "$OUT" "$OUT.aligned"
rm -f "$OUT.aligned" "$OUT.idsig"
"$BT/apksigner" verify --print-certs "$OUT" | head -3
echo
echo "Release APK: $(pwd)/$OUT ($(du -h "$OUT" | cut -f1))"
