#!/usr/bin/env bash
# Bringt die Uhr-App einmalig per WLAN-adb auf die Galaxy Watch. Alles Weitere (Watchface installieren,
# Termine, Zähler) läuft danach über die Handy-App – ohne adb.
#
#   ./install.sh pair  <IP:KOPPLUNGS-PORT> <CODE>   einmalig koppeln
#   ./install.sh       <IP:VERBINDUNGS-PORT>        Uhr-App installieren
#
# IP, Ports und Code zeigt die Uhr unter Einstellungen > Entwickleroptionen > Debugging über WLAN.
set -euo pipefail
cd "$(dirname "$0")"

ADB="$(command -v adb || true)"
[ -z "$ADB" ] && ADB="$HOME/Library/Android/sdk/platform-tools/adb"
[ -x "$ADB" ] || { echo "adb nicht gefunden (Android platform-tools installieren)"; exit 1; }

PKG="de.mb.tagesring"

if [ "${1:-}" = "pair" ]; then
  [ $# -eq 3 ] || { echo "Aufruf: $0 pair <IP:KOPPLUNGS-PORT> <CODE>"; exit 1; }
  "$ADB" pair "$2" "$3"
  echo "Gekoppelt. Jetzt: $0 <IP:VERBINDUNGS-PORT>"
  exit 0
fi

TARGET="${1:-}"
if [ -n "$TARGET" ]; then
  "$ADB" connect "$TARGET"
  A=("$ADB" -s "$TARGET")
else
  A=("$ADB")
fi

APK="neXartWatch-Uhr.apk"
[ -f "$APK" ] || { echo "$APK fehlt – erst ./build.sh ausführen"; exit 1; }

echo "== Installiere $APK"
"${A[@]}" install -r "$APK"

echo "== Erlaube das automatische Aktivieren des Watchfaces"
"${A[@]}" shell pm grant "$PKG" com.google.wear.permission.SET_PUSHED_WATCH_FACE_AS_ACTIVE || true

echo "== Öffne die App auf der Uhr"
"${A[@]}" shell am start -n "$PKG/.ui.SetupActivity" >/dev/null || true

cat <<'TXT'

Fertig. Auf der Uhr zeigt die App neXart Watch jetzt "Watchface installieren" –
oder in der Handy-App (neXartWatch-Handy.apk) "Watchface auf Uhr installieren" tippen.
TXT
