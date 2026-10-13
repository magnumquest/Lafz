#!/usr/bin/env bash
# Run once with:  sudo bash tools/install-jdk21.sh
# Robolectric (the SDK-36 unit tests) needs a full JDK 21; only JDK 17 (no sudo) and JRE-only 21/25 are installed.
set -euo pipefail
apt-get update
apt-get install -y openjdk-21-jdk-headless
echo "Installed. Then run tests with:  JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew testDebugUnitTest"
