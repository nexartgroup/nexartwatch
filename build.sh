#!/usr/bin/env bash
# Baut die beiden Apps:
#   neXartWatch-Uhr.apk    Uhr-App (enthält das Watchface samt Prüf-Token für Watch Face Push)
#   neXartWatch-Handy.apk  Handy-App (Kalenderauswahl, Termine/Zähler an die Uhr, Installation anstoßen)
set -euo pipefail
cd "$(dirname "$0")"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
if [ -z "$JAVA_HOME" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
fi
if [ -z "${ANDROID_HOME:-}" ] && [ ! -f local.properties ]; then
  echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
fi

./gradlew --console=plain :watch:assembleRelease :phone:assembleRelease
cp watch/build/outputs/apk/release/watch-release.apk neXartWatch-Uhr.apk
cp phone/build/outputs/apk/release/phone-release.apk neXartWatch-Handy.apk
echo "Fertig:"
ls -la neXartWatch-Uhr.apk neXartWatch-Handy.apk
