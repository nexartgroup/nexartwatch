# neXart Watch – 24-Stunden-Watchface für die Samsung Galaxy Watch 7 (Wear OS 6)

Besteht aus drei Teilen, die zusammenarbeiten:

| Datei | Wo | Aufgabe |
|---|---|---|
| `neXartWatch-Handy.apk` | Handy | Kalender wählen, Farben ändern, „Ganztägig“ ein/aus, Termine und Benachrichtigungszähler an die Uhr senden, Watchface auf der Uhr installieren |
| `neXartWatch-Uhr.apk` | Uhr | Datenquellen für das Watchface, Empfang der Handy-Daten, **Installation des Watchfaces über Watch Face Push** |
| Watchface | steckt in der Uhr-App | reines Watch-Face-Format-Paket (`de.mb.tagesring.watchfacepush.tagesring`) |

## Was das Watchface zeigt

* **Mitte:** digitale Uhr (24 h, keine Zeiger), darunter **Wochentag und Datum** („Mi 21.10.2026“) und dann der **nächste Termin** mit Titel, Zeit,
  Restzeit („NÄCHSTER · in 39 Min.“ bzw. „JETZT · noch 27 Min.“), Kalender und Ort.
* **Lünette:** die heutigen Termine als farbige Segmente. Der Tag beginnt **unten in der Mitte
  (0 Uhr = 180°)** und läuft im Uhrzeigersinn, 15° pro Stunde: 6 Uhr links, 12 Uhr oben, 18 Uhr rechts.
  Beispiel: 00:00–12:00 → 180°–0°, 12:00–14:00 → 0°–30°.
  Vergangene Termine sind abgedunkelt, überschneidende liegen in einer dünnen inneren Spur, ein weißer
  Punkt markiert die aktuelle Zeit, ein blauer Strich den bereits vergangenen Teil des Tages.
  **Ganztägige Termine** (optional) erscheinen als dünner Außenring.
* **Stundenmarken:** 0, 6 und 18 Uhr sind als Zahlen am Rand beschriftet (die 0 sitzt in der Lücke unten), bei 12 Uhr oben steht
  das neXart-„N“; 3, 9, 15 und 21 Uhr sind kräftige Striche, die übrigen Stunden feine Striche. Nichts davon ragt in die große
  Uhrzeit hinein.
* **Design:** Farben und Schrift (IBM Plex Mono) lehnen sich an nexart.de an – dunkles Marineblau-Grau, Akzentblau #3FA9F5, kühle Grautöne.
  Die Handy-App trägt dasselbe Farbschema und zeigt oben immer eine Vorschau des Watchfaces mit den aktuellen Einstellungen
  (Tippen schaltet auf den Ruhemodus um; Wetter und Akku sind dort Beispielwerte).
* **Zusatzinfos:** Rechts neben dem Termin zwei kleine Felder (standardmäßig **Schritte** und **Puls**, aus Health Services der Uhr).
  Beide sind normale Komplikationen und lassen sich auf der Uhr wie bei jedem Zifferblatt ändern: Display lange drücken →
  „Anpassen“ → Feld antippen → z. B. Puls von Samsung Health, Timer, Stoppuhr. Das Symbol behält bei fremden Komplikationen
  seine eigene Farbe (Puls rot, Schritte grün …); bei den eigenen Feldern legst du die Farbe in der Handy-App fest
  („Symbol Zusatzinfo 1/2“). Im Ruhemodus sind die Symbole grau.
* **Zähler:** Anrufe in Abwesenheit / WhatsApp / Mail (Nachrichten, nicht nur Benachrichtigungen) und – als Pille
  unten – alle übrigen aktiven Benachrichtigungen **des Handys**.
* **Wetter** und **Akku** der Uhr.
* **Ruhemodus (AOD):** rein schwarz-weiß mit dünnen Linien. Zum Einbrennschutz wandert der gesamte Inhalt jede Minute um
  wenige Pixel (±3 px) und es leuchten nur wenige Flächen.
* **Tippen:** Logo → Kalender, Uhrzeit → Timer, Temperatur → Wetter, nächster Termin → genau dieser Termin, übrige Fläche → Kalender (Samsung-Apps der Uhr).

## Einrichtung

1. **Handy:** `neXartWatch-Handy.apk` installieren (APK antippen; ggf. „Unbekannte Apps installieren“ erlauben).
2. **Uhr-App einmalig auf die Uhr bringen.** Für eine neue Wear-App gibt es ohne Play Store nur diesen Weg
   (alles Weitere läuft danach über das Handy, ohne adb):
   * Uhr: *Einstellungen → Über die Uhr → Softwareinformationen* → mehrfach auf *Softwareversion* tippen,
     dann *Einstellungen → Entwickleroptionen*: **ADB-Debugging** und **Debugging über WLAN** an (Uhr im WLAN).
   * *Debugging über WLAN → Neues Gerät koppeln* zeigt IP, Kopplungs-Port und Code:
     ```bash
     ./install.sh pair 192.168.1.50:37123 123456
     ./install.sh 192.168.1.50:41234      # Verbindungs-Port vom Bildschirm „Debugging über WLAN“
     ```
     (Alternativ: Veröffentlichung über Google Play – dann installiert Play die Uhr-App automatisch.)
3. **Handy-App öffnen:**
   * *Uhr* → **Watchface auf Uhr installieren** (Watch Face Push, ohne adb). Auf der Uhr in der App „neXart Watch“
     einmal **Watchface aktivieren** tippen (Berechtigung „Aktives Zifferblatt ändern“) – oder Display lange drücken
     und „neXart Watch“ wählen.
   * *Kalender auf der Uhr*: Kalender anhaken, mit Tipp auf den Farbpunkt die **Farbe** ändern, **Ganztägige Termine** ein/aus.
   * *Aussehen*: **alle Farben** des Zifferblatts einzeln ändern (Uhrzeit, Texte, Marken, Ring-Spur, vergangene Zeit, Zähler …)
     und zwischen **Kalenderfarben** und **Palette** wählen. Die Palette gibt den Terminen der Reihe nach sechs aufeinander
     abgestimmte Farben (Blau, Bernstein, Türkis, Lachs, Lavendel, Limette – immer im Wechsel zum Komplementärton), danach
     beginnt die Folge von vorn; Nachbarn sind so immer klar zu unterscheiden. Die Startfarbe lässt sich per Tipp wählen.
   * *Schritte und Puls*: einmalig auf der Uhr in der App „neXart Watch“ **„Schritte und Puls erlauben“** tippen
     (Berechtigungen für Health Services).
   * *Benachrichtigungen*: **Benachrichtigungszugriff erlauben** (Handy-Einstellungen). Bei von Hand installierten Apps
     vorher in den App-Infos über ⋮ „Eingeschränkte Einstellungen zulassen“.
   * *Benachrichtigungen* – damit die Zähler auch ankommen, wenn das Handy im Ruhezustand (Doze) liegt:
     **Akku-Optimierung für neXart Watch ausschalten** (Knopf in der Karte; Samsung: „Nicht eingeschränkt“).
4. Änderungen gehen sofort an die Uhr, außerdem alle 15 Minuten und bei Kalenderänderungen.

## Wie es funktioniert

* **Watch Face Push** (Wear OS 6): Die Uhr-App enthält das Watchface-Paket samt Prüf-Token (Googles
  `validator-push-cli` erzeugt es beim Bauen) und übergibt es über `WatchFacePushManager` an das System – so wird es
  legitim als Watchface installiert und aktualisiert. Das Watchface-Paket ist reines Watch Face Format (kein Code),
  mit eigenem Schlüssel signiert (`keystores/watchface.jks`).
* **Termine:** Die Handy-App liest die gewählten Kalender (Standard-Kalender-Provider des Handys, also auch Samsung-
  und lokale Kalender) und sendet sie per Wearable Data Layer. Die Uhr speichert sie und das Watchface liest sie über
  Komplikations-Datenquellen. Ohne Handy-App fällt die Uhr auf ihren eigenen Wear-Kalender zurück
  (dort sind meist nur Google-Kalender, und ohne Auswahl).
* **Zähler:** Die Handy-App zählt die aktiven Benachrichtigungen (WhatsApp, Mail-Apps, Telefon-Apps, Rest) und sendet sie
  sofort an die Uhr (kurzer Wake-Lock im Benachrichtigungsdienst, kein Timer – Timer stehen im Tiefschlaf still).
* **Farben:** Die Handy-App sendet ein Farbschema (11 Farben, inkl. der Symbolfarben der Zusatzinfos). Die Uhr reicht es dem Watchface über die Komplikationsdaten
  weiter (Farbverlauf der Hauptebene bzw. Farben der gewichteten Elemente); das Watchface (Watch Face Format 4, Wear OS 6)
  wendet sie per Farb-Transformation `extractColorFromColors` an. Ohne Daten gelten die Standardfarben.
* **Kapazität des Rings:** Wear OS erlaubt pro Komplikation höchstens 7 gewichtete Elemente (Lücken zählen mit) und ein
  WFF-Watchface höchstens 8 Komplikationen. Belegung: Hauptebene, 2× Termin-Ring (Spur A), 1× innere Spur (B, für
  überlappende Termine und Überlauf), Ganztägig, nächster Termin und die beiden Zusatzinfos. Es passen etwa 9 Termine
  gleichzeitig auf den Ring; bei mehr werden vergangene und späte Termine weggelassen.
* **Zusatzinfos:** Zwei bearbeitbare Komplikations-Felder (`isCustomizable`). Standardquellen sind `Info1Service` (Schritte) und
  `Info2Service` (Puls) der Uhr-App; sie lesen passiv über Health Services (`HealthListenerService`) und liefern Bereichswerte mit
  Kennzeichen `-12345` und einem Farbverlauf mit der Symbolfarbe aus dem Farbschema. Das Watchface zeigt bei diesem Kennzeichen
  die gewählte Symbolfarbe, sonst die Originalfarben des Symbols der gewählten Komplikation.

## Bauen

```bash
./build.sh   # JDK 17 + Android SDK nötig → neXartWatch-Uhr.apk und neXartWatch-Handy.apk
python3 tools/gen_assets.py      # Icons neu zeichnen (Pillow)
python3 tools/gen_watchface.py   # watchface/src/main/res/raw/watchface.xml neu erzeugen
python3 tools/gen_icon.py        # App-Symbol (neXart-N im 24-h-Termin-Ring) für Handy- und Uhr-App
```

Das Projekt liegt in `/Users/mb/claude/neXartWatch`; die Paket-ID bleibt `de.mb.tagesring` (damit Handy, Uhr und das bereits installierte Watchface zusammenpassen).

Struktur: `watchface/` (WFF-Paket), `watch/` (Uhr-App), `phone/` (Handy-App), `shared/` (gemeinsame Nachrichten-
Vereinbarung). Beide Apps haben dieselbe Paket-ID (`de.mb.tagesring`) und denselben Schlüssel, sonst verbindet der Data Layer sie nicht.

## Hinweise

* `WF_STATIC=1 python3 tools/gen_watchface.py` erzeugt eine Variante ohne dynamische Farben (Fehlersuche).
* **Wear OS 6 erforderlich** (Watch Face Push; Galaxy Watch 7 mit One UI 8 Watch). Auf Wear OS 5 lässt sich das Watchface
  nicht per Push installieren.
* Die Samsung-Laufzeit zeichnet Slot-Inhalte nur innerhalb der Tippfläche; `BoundingArc` schneidet auf Wear OS 6 den Ring ab –
  deshalb sind die Ring-Slots vollflächig (siehe Kommentar in `tools/gen_watchface.py`).
* Wetter liefert das System (WFF-Datenquelle `WEATHER`).
* Das System begrenzt, wie oft eine App ein Watchface automatisch aktivieren darf; ist das Limit erreicht, einmal von Hand in der Zifferblatt-Auswahl wählen.
* Schrift: IBM Plex Mono (SIL Open Font License 1.1, siehe `licenses/`). Das „N“-Logo stammt von nexart.de. Icons sind eigene Zeichnungen bzw. aus dem Material-Symbol „call“ (Apache 2.0) abgeleitet.
  Prüf-Token: `validator-push-cli` von Google (nur beim Bauen).
