#!/usr/bin/env bash
# Recreates the Android dev env in ~/Android (no sudo needed). Prereqs: JDK 17 at ~/Android/jdk,
# cmdline-tools at ~/Android/Sdk/cmdline-tools/latest, /dev/kvm access (user in group kvm).
set -euo pipefail
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=$HOME/Android/jdk
S=$ANDROID_HOME/cmdline-tools/latest/bin
yes | $S/sdkmanager --licenses >/dev/null || true
yes | $S/sdkmanager --install platform-tools emulator "platforms;android-37.0" "platforms;android-36" \
  "build-tools;37.0.0" "build-tools;36.0.0" "ndk;28.0.13004108" "cmake;3.22.1" \
  "system-images;android-36;google_apis_playstore;x86_64"
echo no | $S/avdmanager create avd -n Pixel_API36 -k "system-images;android-36;google_apis_playstore;x86_64" --force
echo "sdk.dir=$ANDROID_HOME" > "$(dirname "$0")/../local.properties"
echo "Done. Add to ~/.bashrc: JAVA_HOME, ANDROID_HOME, and PATH entries for platform-tools/emulator (see ~/.android-env.sh)."
