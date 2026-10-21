#!/usr/bin/env python3
"""Erzeugt watchface/src/main/res/raw/watchface.xml (Watch Face Format v4).

Koordinatenraum 450 x 450 (wird auf die echte Uhr skaliert). Der Tag beginnt unten in der Mitte
(0 Uhr = 180 deg) und läuft im Uhrzeigersinn (15 deg / Stunde).

Aufbau (max. 8 Komplikations-Slots, Reihenfolge = Zeichenreihenfolge von unten nach oben):

  Slot 4  Hauptebene: Ring-Spur, Fortschritt, Stundenmarken, Uhrzeit, Wetter, Akku, Zähler.
          Datenquelle CounterService (RANGED_VALUE): Zähler in min/value/max und die sieben
          Theme-Farben der Hauptebene als Farbverlauf (RANGED_VALUE_COLORS).
  Slot 0,1,5,6  Termine (gewichtete Elemente = farbige Bogenstücke), Spur A (3 Slots) und B (1 Slot)
  Slot 7  dünner Außenring für ganztägige Termine + Marker "jetzt"
  Slot 2  nächster Termin, Zeile 1+2 (Text) – Farben aus den gewichteten Elementen
  Slot 3  nächster Termin, Zeile 3+4

Alle Farben des Themes kommen aus den Daten der Komplikationen (WFF v4: Farb-Transformation mit
extractColorFromColors), damit die Handy-App sie ändern kann. Ohne Daten gelten die Standardfarben.
Der Ruhemodus (AOD) ist fest schwarz-weiß, dünn gezeichnet und wird minütlich um wenige Pixel
verschoben (Einbrennschutz).

Achtung: Der Inhalt eines Slots wird auf der Samsung-Laufzeit (Wear OS 6) auf die Tippfläche
(BoundingShape) beschnitten – deshalb haben alle Ring-Slots die Vollfläche; BoundingArc schneidet
den Ring ab. Textboxen und Bilder nur mit ganzzahligen Koordinaten (sonst falsch platziert).

Aufruf:  python3 tools/gen_watchface.py
"""
import math
import os
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "watchface/src/main/res/raw/watchface.xml"
PKG = "de.mb.tagesring"
# WF_STATIC=1: feste Standardfarben ohne Farb-Transformationen (Rückfall/Fehlersuche auf der Uhr)
STATIC = bool(os.environ.get("WF_STATIC"))
# WF_FORCE_AMBIENT=1: zeigt immer die Ruhemodus-Ebene (zum Prüfen per Screenshot; adb kann AOD nicht auslösen)
FORCE_AMBIENT = bool(os.environ.get("WF_FORCE_AMBIENT"))
S = 450.0
C = S / 2

# ---------------------------------------------------------------- Geometrie
RING_R = 212.4          # Mittellinie des Rings
BAND = 14.4             # Dicke der Termin-Segmente (Spur A)
BAND_B = 6.5            # Spur B (überschneidende Termine), innen daneben
ALLDAY_R = 221.5        # dünner Außenring für ganztägige Termine
ALLDAY_T = 3.2
RING_B_R = RING_R - BAND / 2 - 2.5 - BAND_B / 2
TRACK = 3.2
GAP_BOTTOM = 12.0
GAP_QUARTER = 1.8
TICK_R = 192.0          # äußeres Ende der Stundenmarken
NUMERAL_R = 176.0       # Mittelpunkt der Zahlen 12 / 6 / 18

# Zeilenmitten (Anteile der Breite aus der Vorlage * 450)
Y_TIME = 0.247 * S
Y_DATE = 160.0
Y_LABEL = 0.421 * S
Y_TITLE = 0.475 * S
Y_RANGE = 0.535 * S
Y_INFO = 0.592 * S
Y_ROW = 0.732 * S
Y_NOTIF = 371.0
Y_PILL = 0.914 * S
X0 = 0.17 * S
TEXT_W = 0.66 * S
TITLE_W = 186            # Titel des Termins: schmaler, damit rechts Platz für die Zusatzinfos bleibt
CELL_X = 270             # Zusatzinfos rechts neben dem Termin: linke Kante
CELL_ICON = 20
CELL_TEXT_X = CELL_X + CELL_ICON + 5
CELL_TEXT_W = 90
Y_INFO1 = 213.75         # Höhe der Titelzeile
Y_INFO2 = 240.75         # Höhe der Uhrzeitzeile

DIM_ICON = 120          # Alpha der Symbole bei Zähler 0

FULL = f'x="0" y="0" width="{S:g}" height="{S:g}"'

# Standardfarben des Themes (Farbwelt von nexart.de) – müssen mit shared/.../sync/Theme.kt übereinstimmen
THEME_DEFAULT = {
    "time": "#ffeef3f8", "text": "#ffdfe6ee", "marks": "#ff71869a", "track": "#ff203240", "progress": "#ff3fa9f5",
    "badge": "#ff203240", "date": "#ffa9b7c6", "label": "#ff3fa9f5", "title": "#ffeef3f8",
}

# Ruhemodus: nur Grautöne bis Weiß
A_TIME = "#ffffffff"
A_TEXT = "#ffd0d0d0"
A_MARKS = "#ff909090"
A_TRACK = "#ff606060"
A_LABEL = "#ffa8a8a8"
A_TITLE = "#ffffffff"
A_RANGE = "#ffa0a0a0"
A_INFO = "#ff808080"
WHITE = "#ffffffff"

# Einbrennschutz: Verschiebung der Ruhemodus-Ebene um -3..+3 Pixel, jede Minute ein anderer Punkt
SHIFT_X = "(([MINUTE] % 4) - 1.5) * 2"
SHIFT_Y = "((floor([MINUTE] / 4) % 4) - 1.5) * 2"


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


class Pal:
    """Farben für Elemente. Mit [src] kommen sie dynamisch aus dem Farbverlauf der Komplikation,
    sonst sind es feste Standardfarben (Rückfall, wenn keine Daten da sind)."""

    def __init__(self, src, names):
        self.src = src
        self.names = names

    def hex(self, name):
        return THEME_DEFAULT[name]

    def expr(self, name):
        n = len(self.names)
        ratio = (self.names.index(name) + 0.5) / n
        return f"extractColorFromColors({self.src}, false, {ratio:.4f})"

    def tr(self, name, target="color"):
        if not self.src or STATIC:
            return ""
        return f'<Transform target="{target}" value="{esc(self.expr(name))}"/>'


MAIN = ["time", "text", "marks", "track", "progress", "badge", "date"]
TITLES = ["label", "title"]
SRC_RANGED = "[COMPLICATION.RANGED_VALUE_COLORS]"
SRC_WEIGHTED = "[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]"


def pal_main(dyn=True):
    return Pal(SRC_RANGED if dyn else None, MAIN)


def pal_titles():
    return Pal(SRC_WEIGHTED, TITLES)


# ---------------------------------------------------------------- Bausteine
def text_part(x, y_center, w, h, family, size, color, content, align="START", ellipsis=False,
              tr="", part_tr=""):
    """PartText mit fester Box; [content] ist roher XML-Inhalt innerhalb <Font>, [tr] ein Farb-Transform."""
    top = y_center - h / 2
    # Wichtig: WFF platziert Textboxen mit Nachkommastellen falsch -> ganze Zahlen verwenden
    return (f'<PartText x="{round(x)}" y="{round(top)}" width="{round(w)}" height="{round(h)}">{part_tr}'
            f'<Text align="{align}" ellipsis="{"TRUE" if ellipsis else "FALSE"}">'
            f'<Font family="{family}" size="{round(size)}" color="{color}">{tr}{content}</Font></Text></PartText>')


def static_text(x, y_center, w, h, family, size, color, s, align="START", tr=""):
    return text_part(x, y_center, w, h, family, size, color, esc(s), align, tr=tr)


def template(fmt, *params):
    ps = "".join(f'<Parameter expression="{esc(p)}"/>' for p in params)
    return f"<Template>{esc(fmt)}{ps}</Template>"


def stroke(color, thickness, tr="", cap="BUTT"):
    return f'<Stroke color="{color}" thickness="{f(thickness)}" cap="{cap}">{tr}</Stroke>'


def fill(color, tr=""):
    return f'<Fill color="{color}">{tr}</Fill>'


def arc(cx, cy, r, start, end, st, extra=""):
    return (f'<PartDraw {FULL}><Arc centerX="{f(cx)}" centerY="{f(cy)}" width="{f(2 * r)}" height="{f(2 * r)}" '
            f'startAngle="{f(start)}" endAngle="{f(end)}" direction="CLOCKWISE">{st}{extra}</Arc></PartDraw>')


def line(x0, y0, x1, y1, st):
    return f'<Line startX="{f(x0)}" startY="{f(y0)}" endX="{f(x1)}" endY="{f(y1)}">{st}</Line>'


def group(name, inner, ambient=None, shift=False):
    """Ebene, die nur interaktiv (ambient=0) oder nur im Ruhemodus (ambient=255) sichtbar ist.
    [shift]: Einbrennschutz-Verschiebung."""
    alpha = v = tr = ""
    if FORCE_AMBIENT and ambient is not None:
        alpha = ' alpha="0"' if ambient == 0 else ""
        ambient = None
    if ambient == 0:
        v = '<Variant mode="AMBIENT" target="alpha" value="0"/>'
    elif ambient is not None:
        alpha = ' alpha="0"'
        v = f'<Variant mode="AMBIENT" target="alpha" value="{ambient}"/>'
    if shift:
        tr = (f'<Transform target="x" value="{esc(SHIFT_X)}"/>'
              f'<Transform target="y" value="{esc(SHIFT_Y)}"/>')
    return f'<Group name="{name}" {FULL}{alpha}>{v}{tr}{inner}</Group>'


def polar(r, hour):
    """Punkt bei [hour] auf Radius r; 0 Uhr = unten, im Uhrzeigersinn."""
    a = math.radians(90 + 15 * hour)
    return C + r * math.cos(a), C + r * math.sin(a)


# ---------------------------------------------------------------- Lünette (Hauptebene)
def quarter(q):
    gs = GAP_BOTTOM if q == 0 else GAP_QUARTER
    ge = GAP_BOTTOM if q == 3 else GAP_QUARTER
    start = (180 + 90 * q) % 360 + gs / 2
    sweep = 90 - gs / 2 - ge / 2
    return start, sweep, gs


def ring_track(color, thickness, tr=""):
    out = ""
    for q in range(4):
        start, sweep, _ = quarter(q)
        out += arc(C, C, RING_R, start, start + sweep, stroke(color, thickness, tr))
    return out


def ring_progress(pal):
    out = ""
    for q in range(4):
        start, sweep, gs = quarter(q)
        expr = f"{f(start)} + clamp(15 * [HOUR_0_23_MINUTE] - {90 * q} - {f(gs / 2)}, 0, {f(sweep)})"
        out += arc(C, C, RING_R, start, start + 1,
                   stroke(pal.hex("progress"), TRACK, pal.tr("progress")),
                   f'<Transform target="endAngle" value="{esc(expr)}"/>')
    return out


def ticks(pal):
    """Stundenmarken: lange Striche bei 3/9/15/21, kurze bei den übrigen vollen Stunden.
    Bei 0/6/12/18 stehen Zahlen. So kommt im Zentrum (Uhrzeit, Termin) kein Text in die Nähe."""
    major = minor = ""
    for h in range(24):
        if h % 6 == 0:
            continue
        x0, y0 = polar(TICK_R, h)
        if h % 3 == 0:
            x1, y1 = polar(TICK_R - 10, h)
            major += line(x0, y0, x1, y1, stroke(pal.hex("marks"), 2.8, pal.tr("marks")))
        else:
            x1, y1 = polar(TICK_R - 5, h)
            minor += line(x0, y0, x1, y1, stroke(pal.hex("marks"), 1.5, pal.tr("marks")))
    return (f'<PartDraw {FULL}>{major}</PartDraw>'
            f'<PartDraw {FULL} alpha="140">{minor}</PartDraw>')


def numerals(color, tr, family):
    """Zahlen 6 / 18 und 0; bei 12 Uhr steht das Logo (siehe logo_normal)."""
    out = ""
    for h, label in ((6, "6"), (18, "18")):
        x, y = polar(NUMERAL_R, h)
        out += static_text(x - 22, y, 44, 26, family, 21, color, label, "CENTER", tr)
    # 0 Uhr sitzt in der Lücke des Rings unten
    x, y = polar(RING_R, 0)
    out += static_text(x - 22, y, 44, 26, family, 20, color, "0", "CENTER", tr)
    return out


def logo_normal():
    """neXart-Logo (N) bei 12 Uhr statt der Zahl 12; feste Markenfarben."""
    return (f'<PartImage x="211" y="35" width="28" height="28"><Image resource="ic_logo_n"/></PartImage>')


def logo_ambient():
    return (f'<PartImage x="212" y="36" width="26" height="26" alpha="204" tintColor="{WHITE}">'
            f'<Image resource="ic_logo_n_mono"/></PartImage>')


# Wochentag ohne Locale-Abhängigkeit aus Jahr und Tag im Jahr (1.1.1970 war ein Donnerstag): 0 = Sonntag ... 6 = Samstag
WEEKDAY = ("(365 * ([YEAR] - 1970) + floor(([YEAR] - 1) / 4) - floor(([YEAR] - 1) / 100)"
           " + floor(([YEAR] - 1) / 400) + [DAY_OF_YEAR] - 474) % 7")
WEEKDAY_NAMES = ["So", "Mo", "Di", "Mi", "Do", "Fr", "Sa"]
DATE_CH = 20                       # Schriftgröße; Plex Mono ist 0,6 em breit
DATE_W = round(DATE_CH * 0.6 * 13)  # "Mi 21.10.2026" = 13 Zeichen


def date_line(pal, ambient=False):
    """Wochentag (Mo, Di, ...) und Datum (25.03.2026) in einer Zeile unter der Uhrzeit."""
    left = round(C - DATE_W / 2)
    cw = DATE_CH * 0.6
    if ambient:
        color, wfam, dfam, wtr, dtr = A_TEXT, "ibm_plex_mono_regular", "ibm_plex_mono_light", "", ""
    else:
        color, wfam, dfam = pal.hex("date"), "ibm_plex_mono_semibold", "ibm_plex_mono_regular"
        wtr = dtr = pal.tr("date")
    out = ""
    for k, name in enumerate(WEEKDAY_NAMES):
        out += text_part(left, Y_DATE, round(cw * 2) + 6, 26, wfam, DATE_CH, color, esc(name), tr=wtr,
                         part_tr=f'<Transform target="alpha" value="{esc(f"({WEEKDAY}) == {k} ? 255 : 0")}"/>')
    out += text_part(left + round(cw * 3), Y_DATE, round(cw * 10) + 6, 26, dfam, DATE_CH, color,
                     template("%s.%s.%s", "[DAY_Z]", "[MONTH_Z]", "[YEAR]"), tr=dtr)
    return out


def clock(pal):
    return text_part(C - 150, Y_TIME, 300, 100, "ibm_plex_mono_semibold", 77, pal.hex("time"),
                     template("%s:%s", "[HOUR_0_23_Z]", "[MINUTE_Z]"), "CENTER", tr=pal.tr("time"))


def clock_ambient():
    return text_part(C - 150, Y_TIME, 300, 100, "ibm_plex_mono_light", 72, A_TIME,
                     template("%s:%s", "[HOUR_0_23_Z]", "[MINUTE_Z]"), "CENTER")


def weather_battery(pal):
    cond = "[WEATHER.CONDITION] <= 15 ? [WEATHER.CONDITION] : 0"
    unit = '[WEATHER.TEMPERATURE_UNIT] == 1 ? "C" : "F"'
    ix, iy = 0.2 * S, Y_ROW
    icon_px = 28
    tx = pal.tr("text")
    icon_d = (f'<PartText x="{round(ix)}" y="{round(iy - icon_px / 2)}" width="{icon_px}" height="{icon_px}">'
              f'<Text><BitmapFont family="wx_day" size="{icon_px}">{template("%s", cond)}</BitmapFont></Text></PartText>')
    icon_n = (f'<PartText x="{round(ix)}" y="{round(iy - icon_px / 2)}" width="{icon_px}" height="{icon_px}">'
              f'<Text><BitmapFont family="wx_night" size="{icon_px}">{template("%s", cond)}</BitmapFont></Text></PartText>')
    day_night = (f'<Condition><Expressions><Expression name="day">[WEATHER.IS_DAY]</Expression></Expressions>'
                 f'<Compare expression="day">{icon_d}</Compare><Default>{icon_n}</Default></Condition>')
    temp = text_part(ix + 37, Y_ROW, 110, 34, "ibm_plex_mono_regular", 25, pal.hex("text"),
                     template("%s°%s", "round([WEATHER.TEMPERATURE])", unit), tr=tx)
    none = text_part(ix + 37, Y_ROW, 110, 34, "ibm_plex_mono_regular", 25, pal.hex("marks"), esc("--°C"),
                     tr=pal.tr("marks"))
    weather = (f'<Condition><Expressions><Expression name="wx">[WEATHER.IS_AVAILABLE]</Expression></Expressions>'
               f'<Compare expression="wx">{day_night}{temp}</Compare><Default>{none}</Default></Condition>')

    w, h = 27.9, 15.3
    left = 0.765 * S
    y = Y_ROW
    c = pal.hex("text")
    body = (f'<PartDraw {FULL}><RoundRectangle x="{f(left)}" y="{f(y - h / 2)}" width="{w}" height="{h}" '
            f'cornerRadiusX="3" cornerRadiusY="3">{stroke(c, 1.6, tx)}</RoundRectangle></PartDraw>'
            f'<PartDraw {FULL}><RoundRectangle x="{f(left + w + 1.3)}" y="{f(y - 3.6)}" width="2.7" height="7.2" '
            f'cornerRadiusX="1" cornerRadiusY="1">{fill(c, tx)}</RoundRectangle></PartDraw>')
    fill_w = f"max(2, ({f(w - 6)}) * [BATTERY_PERCENT] / 100)"
    level = (f'<PartDraw {FULL}><RoundRectangle x="{f(left + 3)}" y="{f(y - h / 2 + 3)}" width="{f(w - 6)}" '
             f'height="{f(h - 6)}" cornerRadiusX="1.4" cornerRadiusY="1.4">{fill(c, tx)}'
             f'<Transform target="width" value="{esc(fill_w)}"/></RoundRectangle></PartDraw>')
    pct = text_part(0.745 * S - 110, Y_ROW, 110, 34, "ibm_plex_mono_regular", 25, c,
                    template("%s%%", "[BATTERY_PERCENT]"), align="END", tr=tx)
    return weather + body + level + pct


def weather_battery_ambient():
    unit = '[WEATHER.TEMPERATURE_UNIT] == 1 ? "C" : "F"'
    ix = 0.2 * S
    temp = text_part(ix, Y_ROW, 120, 34, "ibm_plex_mono_light", 25, A_TEXT,
                     template("%s°%s", "round([WEATHER.TEMPERATURE])", unit))
    none = text_part(ix, Y_ROW, 120, 34, "ibm_plex_mono_light", 25, A_INFO, esc("--°C"))
    weather = (f'<Condition><Expressions><Expression name="wx">[WEATHER.IS_AVAILABLE]</Expression></Expressions>'
               f'<Compare expression="wx">{temp}</Compare><Default>{none}</Default></Condition>')
    pct = text_part(0.80 * S - 110, Y_ROW, 110, 34, "ibm_plex_mono_light", 25, A_TEXT,
                    template("%s%%", "[BATTERY_PERCENT]"), align="END")
    return weather + pct


# Zähler unten: große Symbole mit der Zahl direkt daneben (besser lesbar als kleine Marken an der Ecke)
CNT_X = [140, 215, 290]          # Mitte der drei Symbole (eng beieinander, weit weg vom runden Rand)
CNT_ICON = 30
CNT_NUM = 22


def counters(pal):
    A, V, M = ("[COMPLICATION.RANGED_VALUE_MIN]", "[COMPLICATION.RANGED_VALUE_VALUE]",
               "[COMPLICATION.RANGED_VALUE_MAX]")
    calls = f"floor({A} / 100)"
    whats = f"({A} % 100)"
    mail = f"floor(({V} - {A}) / 100)"
    other = f"(({V} - {A}) % 100)"
    conn = f"({M} - {V} - 1)"      # 1 = Zähler aktiv, 0 = unbekannt
    items = [("ic_phone", calls), ("ic_whatsapp", whats), ("ic_mail", mail)]
    tx = pal.tr("text")
    act = ""
    for (res, cnt), cx in zip(items, CNT_X):
        alpha = f"({conn} == 1 && {cnt} > 0) ? 255 : {DIM_ICON}"
        act += (f'<PartImage x="{round(cx - CNT_ICON / 2)}" y="{round(Y_NOTIF - CNT_ICON / 2)}" width="{CNT_ICON}" '
                f'height="{CNT_ICON}" tintColor="{pal.hex("text")}">'
                f'<Transform target="alpha" value="{esc(alpha)}"/>{pal.tr("text", "tintColor")}'
                f'<Image resource="{res}"/></PartImage>')
        nx = cx + CNT_ICON / 2 + 4
        act += text_part(nx, Y_NOTIF, 36, 30, "ibm_plex_mono_medium", CNT_NUM, pal.hex("text"),
                         template("%s", f'numberFormat("0", {cnt})'), tr=tx,
                         part_tr=f'<Transform target="alpha" value="{esc(f"({conn} == 1 && {cnt} > 0) ? 255 : 0")}"/>')
        act += text_part(nx, Y_NOTIF, 36, 30, "ibm_plex_mono_medium", CNT_NUM, pal.hex("marks"), "–", tr=pal.tr("marks"),
                         part_tr=f'<Transform target="alpha" value="{esc(f"{conn} == 0 ? 255 : 0")}"/>')
    # Pille "Sonstige" unten in der Mitte
    px, py = C, Y_PILL
    for w, vis in ((46, f"({conn} == 1 && {other} > 0 && {other} < 10)"), (60, f"({conn} == 1 && {other} >= 10)")):
        act += (f'<PartDraw {FULL}><RoundRectangle x="{f(px - w / 2)}" y="{f(py - 15)}" width="{w}" height="30" '
                f'cornerRadiusX="15" cornerRadiusY="15">{fill(pal.hex("badge"), pal.tr("badge"))}</RoundRectangle>'
                f'<Transform target="alpha" value="{esc(vis + " ? 255 : 0")}"/></PartDraw>')
    act += text_part(px - 30, py, 60, 30, "ibm_plex_mono_medium", 21, pal.hex("text"),
                     template("%s", f'numberFormat("0", {other})'), align="CENTER", tr=tx,
                     part_tr=f'<Transform target="alpha" value="{esc(f"({conn} == 1 && {other} > 0) ? 255 : 0")}"/>')

    # ---- Ruhemodus: weiße Symbole + Zahl, fest angeordnet
    amb = ""
    for (res, cnt), cx in zip(items, CNT_X):
        amb += (f'<PartImage x="{round(cx - 13)}" y="{round(Y_NOTIF - 13)}" width="26" height="26" tintColor="{WHITE}">'
                f'<Transform target="alpha" value="{esc(f"({conn} == 1 && {cnt} > 0) ? 240 : 110")}"/>'
                f'<Image resource="{res}"/></PartImage>')
        amb += text_part(cx + 17, Y_NOTIF, 36, 28, "ibm_plex_mono_regular", 21, A_TIME,
                         template("%s", f'numberFormat("0", {cnt})'),
                         part_tr=f'<Transform target="alpha" value="{esc(f"({conn} == 1 && {cnt} > 0) ? 255 : 0")}"/>')
    amb += text_part(C - 40, Y_PILL, 80, 28, "ibm_plex_mono_regular", 20, A_TEXT,
                     template("·  %s  ·", f'numberFormat("0", {other})'), align="CENTER",
                     part_tr=f'<Transform target="alpha" value="{esc(f"({conn} == 1 && {other} > 0) ? 255 : 0")}"/>')
    return act, amb


def ambient_ticks():
    major = ""
    for h in range(24):
        if h % 3 == 0 and h % 6 != 0:
            x0, y0 = polar(TICK_R, h)
            x1, y1 = polar(TICK_R - 10, h)
            major += line(x0, y0, x1, y1, stroke(A_MARKS, 1.6))
    return f'<PartDraw {FULL}>{major}</PartDraw>'


# ---------------------------------------------------------------- Slots
def slot(slot_id, name, types, primary, primary_type, content, bounding=None, custom=False):
    """Slot mit Standardquelle = Datenquelle der Uhr-App; [custom]: der Nutzer kann ihn über "Anpassen" ändern."""
    bounding = bounding or f'<BoundingBox {FULL}/>'
    return (f'<ComplicationSlot slotId="{slot_id}" name="{name}" displayName="{name}" {FULL} '
            f'supportedTypes="{types} EMPTY" isCustomizable="{"TRUE" if custom else "FALSE"}">'
            f'<DefaultProviderPolicy defaultSystemProvider="EMPTY" defaultSystemProviderType="EMPTY" '
            f'primaryProvider="{PKG}/{PKG}.provider.{primary}" primaryProviderType="{primary_type}"/>'
            f'{bounding}{content}</ComplicationSlot>')


def range_info(pal, ambient=False):
    """Zeile 3 (Uhrzeit des Termins, aus dem Text der Hauptebene) und Zeile 4 (Kalender · Ort, aus dem Titel)."""
    if ambient:
        return (text_part(X0, Y_RANGE, TITLE_W, 30, "ibm_plex_mono_light", 21, A_RANGE,
                          template("%s", "[COMPLICATION.TEXT]"), ellipsis=True)
                + text_part(X0, Y_INFO, TEXT_W, 26, "ibm_plex_mono_light", 18, A_INFO,
                            template("%s", "[COMPLICATION.TITLE]"), ellipsis=True))
    return (text_part(X0, Y_RANGE, TITLE_W, 30, "ibm_plex_mono_regular", 21, pal.hex("date"),
                      template("%s", "[COMPLICATION.TEXT]"), ellipsis=True, tr=pal.tr("date"))
            + text_part(X0, Y_INFO, TEXT_W, 26, "ibm_plex_mono_regular", 18, pal.hex("marks"),
                        template("%s", "[COMPLICATION.TITLE]"), ellipsis=True, tr=pal.tr("marks")))


def main_slot():
    """Hauptebene mit Theme-Farben aus den Daten (RANGED_VALUE); Rückfall (EMPTY) mit Standardfarben."""
    def layers(pal, with_counters):
        act = (ring_track(pal.hex("track"), TRACK, pal.tr("track")) + ring_progress(pal) + ticks(pal)
               + numerals(pal.hex("marks"), pal.tr("marks"), "ibm_plex_mono_regular") + logo_normal() + clock(pal)
               + date_line(pal) + weather_battery(pal))
        amb = (ring_track(A_TRACK, 1.6) + ambient_ticks() + numerals(A_MARKS, "", "ibm_plex_mono_light")
               + logo_ambient() + clock_ambient() + date_line(pal, True) + weather_battery_ambient())
        if with_counters:
            ca, cb = counters(pal)
            act += ca + range_info(pal)
            amb += cb + range_info(pal, True)
        return group("act", act, 0) + group("amb", amb, 255, shift=True)

    content = (f'<Complication type="RANGED_VALUE">{layers(pal_main(True), True)}</Complication>'
               f'<Complication type="EMPTY">{layers(pal_main(False), False)}</Complication>')
    return slot(4, "Hauptebene", "RANGED_VALUE", "CounterService", "RANGED_VALUE", content)


def weighted_ring(r, thickness, thin, ambient_color):
    """Bogen mit gewichteten Strichen; interaktiv farbig, im Ruhemodus dünn und einfarbig ([ambient_color])."""
    def arc_for(th, extra_attr=""):
        st = (f'<WeightedStroke colors="[COMPLICATION.WEIGHTED_ELEMENTS_COLORS]" '
              f'weights="[COMPLICATION.WEIGHTED_ELEMENTS_WEIGHTS]" thickness="{f(th)}" cap="BUTT"/>')
        return (f'<PartDraw {FULL} angle="180" pivotX="0.5" pivotY="0.5"{extra_attr}>'
                f'<Arc centerX="{f(C)}" centerY="{f(C)}" width="{f(2 * r)}" height="{f(2 * r)}" '
                f'startAngle="0" endAngle="360" direction="CLOCKWISE">{st}</Arc></PartDraw>')
    return (group("act", arc_for(thickness), 0)
            + group("amb", arc_for(thin) + whiten(ambient_color), 255, shift=True))


def ring_slot(slot_id, name, primary, lane):
    r, thick, thin = (RING_R, BAND, 4.5) if lane == 0 else (RING_B_R, BAND_B, 2.2)
    content = f'<Complication type="WEIGHTED_ELEMENTS">{weighted_ring(r, thick, thin, WHITE)}</Complication>'
    return slot(slot_id, name, "WEIGHTED_ELEMENTS", primary, "WEIGHTED_ELEMENTS", content)


def now_marker():
    """Punkt am unteren Rand (= 0 Uhr); die Gruppe dreht sich um die Mitte: 15 deg pro Stunde im Uhrzeigersinn."""
    x, y = C, C + RING_R
    dot = (f'<PartDraw {FULL}><Ellipse x="{f(x - 7.4)}" y="{f(y - 7.4)}" width="14.8" height="14.8">'
           f'{fill("#ff000000")}</Ellipse></PartDraw>'
           f'<PartDraw {FULL}><Ellipse x="{f(x - 5.4)}" y="{f(y - 5.4)}" width="10.8" height="10.8">'
           f'{fill(WHITE)}</Ellipse></PartDraw>')
    return (f'<Group name="now" {FULL} pivotX="0.5" pivotY="0.5" angle="0">'
            f'<Transform target="angle" value="15 * [HOUR_0_23_MINUTE]"/>{dot}</Group>')


def now_marker_ambient():
    x, y = C, C + RING_R
    tick = f'<PartDraw {FULL}>{line(x, y - 14, x, y + 14, stroke(WHITE, 2.4))}</PartDraw>'
    return (f'<Group name="nowAmbient" {FULL} pivotX="0.5" pivotY="0.5" angle="0">'
            f'<Transform target="angle" value="15 * [HOUR_0_23_MINUTE]"/>{tick}</Group>')


def whiten(color=WHITE):
    """Färbt im Ruhemodus alles bisher im Slot Gezeichnete einfarbig (SRC_ATOP: nur dort, wo schon etwas ist).
    Ein Mischmodus wirkt nur innerhalb des Slots – jeder Slot wird einzeln gerendert."""
    return f'<PartDraw {FULL} blendMode="SRC_ATOP"><Rectangle {FULL}>{fill(color)}</Rectangle></PartDraw>'


def all_day_slot(slot_id, name, primary):
    """Dünner Außenring für ganztägige Termine; trägt außerdem den Marker "jetzt" (liegt zuoberst)
    und entsättigt im Ruhemodus alle Termin-Ringe darunter."""
    content = (f'<Complication type="WEIGHTED_ELEMENTS">{weighted_ring(ALLDAY_R, ALLDAY_T, 2.0, A_TEXT)}'
               f'{group("marker", now_marker(), 0)}'
               f'{group("markerA", now_marker_ambient(), 255, shift=True)}</Complication>')
    return slot(slot_id, name, "WEIGHTED_ELEMENTS", primary, "WEIGHTED_ELEMENTS", content)


def next_title_slot():
    def text_block(pal, fl, ft, amb):
        label = text_part(X0, Y_LABEL, TEXT_W, 26, fl, 16, A_LABEL if amb else pal.hex("label"),
                          template("%s", "[COMPLICATION.TITLE]"), ellipsis=True,
                          tr="" if amb else pal.tr("label"))
        title = text_part(X0, Y_TITLE, TITLE_W, 30, ft, 21, A_TITLE if amb else pal.hex("title"),
                          template("%s", "[COMPLICATION.TEXT]"), ellipsis=True,
                          tr="" if amb else pal.tr("title"))
        return label + title

    pal = pal_titles()
    content = (f'<Complication type="WEIGHTED_ELEMENTS">'
               f'{group("act", text_block(pal, "ibm_plex_mono_regular", "ibm_plex_mono_semibold", False), 0)}'
               f'{group("amb", text_block(pal, "ibm_plex_mono_light", "ibm_plex_mono_regular", True), 255, shift=True)}'
               f'</Complication>')
    # Tippfläche über alle vier Zeilen des Termins (Zeile 3 und 4 zeichnet die Hauptebene)
    box = (f'<BoundingBox x="{round(X0 - 16)}" y="{round(Y_LABEL - 20)}" width="{round(TEXT_W + 36)}" '
           f'height="{round(Y_INFO + 14 - (Y_LABEL - 20))}"/>')
    return slot(2, "Nächster Termin", "WEIGHTED_ELEMENTS", "NextTitleService", "WEIGHTED_ELEMENTS", content, box)


# ---------------------------------------------------------------- Zusatzinfos (bearbeitbare Slots)
INFO_TEXT = "#ffdfe6ee"
INFO_ACCENT = "#ff3fa9f5"


def info_cell(kind, cy):
    """Eine Zusatzinfo: Symbol + Wert. [kind] = Typ der Komplikation (SHORT_TEXT, RANGED_VALUE, GOAL_PROGRESS).
    Die Farben sind fest (Daten fremder Datenquellen enthalten keine Theme-Farben)."""
    value_src = {"SHORT_TEXT": None, "RANGED_VALUE": "[COMPLICATION.RANGED_VALUE_VALUE]",
                 "GOAL_PROGRESS": "[COMPLICATION.GOAL_PROGRESS_VALUE]"}[kind]

    def cell(amb):
        fam = "ibm_plex_mono_light" if amb else "ibm_plex_mono_medium"
        col = A_TEXT if amb else INFO_TEXT
        icon_col = WHITE if amb else INFO_ACCENT
        out = (f'<PartImage x="{CELL_X}" y="{round(cy - CELL_ICON / 2)}" width="{CELL_ICON}" height="{CELL_ICON}" '
               f'tintColor="{icon_col}"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage>')
        if value_src is None:
            out += text_part(CELL_TEXT_X, cy, CELL_TEXT_W, 28, fam, 19, col, template("%s", "[COMPLICATION.TEXT]"))
        else:
            # Hat die Datenquelle keinen Text, zeigen wir den Zahlenwert
            has_text = "textLength([COMPLICATION.TEXT]) > 0"
            out += text_part(CELL_TEXT_X, cy, CELL_TEXT_W, 28, fam, 19, col, template("%s", "[COMPLICATION.TEXT]"),
                             part_tr=f'<Transform target="alpha" value="{esc(f"({has_text}) ? 255 : 0")}"/>')
            out += text_part(CELL_TEXT_X, cy, CELL_TEXT_W, 28, fam, 19, col,
                             template("%s", f'numberFormat("0", {value_src})'),
                             part_tr=f'<Transform target="alpha" value="{esc(f"({has_text}) ? 0 : 255")}"/>')
        return out

    return group("act", cell(False), 0) + group("amb", cell(True), 255, shift=True)


def info_slot(slot_id, name, cy, primary):
    """Rechts neben dem Termin; der Nutzer kann die Komplikation über "Anpassen" ändern (Puls, Timer, ...)."""
    kinds = ("SHORT_TEXT", "RANGED_VALUE", "GOAL_PROGRESS")
    content = "".join(f'<Complication type="{k}">{info_cell(k, cy)}</Complication>' for k in kinds)
    content += '<Complication type="EMPTY"><Group name="leer" x="0" y="0" width="1" height="1"/></Complication>'
    box = f'<BoundingBox x="{CELL_X - 6}" y="{round(cy - 16)}" width="{CELL_TEXT_X + CELL_TEXT_W - CELL_X + 8}" height="32"/>'
    return slot(slot_id, name, " ".join(kinds), primary, "SHORT_TEXT", content, box, custom=True)


def weather_bitmap_fonts():
    names = {0: "wx_unknown", 1: "wx_sun", 2: "wx_cloud", 3: "wx_fog", 4: "wx_rain", 5: "wx_snow", 6: "wx_rain",
             7: "wx_snow", 8: "wx_sun", 9: "wx_thunder", 10: "wx_rain", 11: "wx_snow", 12: "wx_rain",
             13: "wx_fog", 14: "wx_partly_day", 15: "wx_wind"}

    def font_for(name, night):
        chars = ""
        for k, res in names.items():
            if night and res == "wx_sun":
                res = "wx_moon"
            if night and res == "wx_partly_day":
                res = "wx_partly_night"
            tag = "Character" if k < 10 else "Word"
            chars += f'<{tag} name="{k}" resource="{res}" width="96" height="96"/>'
        return f'<BitmapFont name="{name}">{chars}</BitmapFont>'

    return f"<BitmapFonts>{font_for('wx_day', False)}{font_for('wx_night', True)}</BitmapFonts>"


# ---------------------------------------------------------------- Zusammenbau
def build():
    # Ein Tipp irgendwo auf das Zifferblatt öffnet den Kalender (Komplikationen darüber haben Vorrang)
    tap_background = (f'<PartDraw {FULL}><Launch target="com.samsung.android.calendar"/>'
                      f'<Rectangle {FULL}>{fill("#00000000")}</Rectangle></PartDraw>')
    scene = (
        tap_background
        + main_slot()
        + ring_slot(0, "Termine A1", "RingA1Service", 0)
        + ring_slot(1, "Termine A2", "RingA2Service", 0)
        + ring_slot(6, "Termine B1", "RingB1Service", 1)
        + all_day_slot(7, "Ganztägig", "AllDayRingService")
        + next_title_slot()
        + info_slot(3, "Zusatzinfo 1", Y_INFO1, "Info1Service")
        + info_slot(5, "Zusatzinfo 2", Y_INFO2, "Info2Service")
    )
    return (f'<?xml version="1.0" encoding="UTF-8"?>\n'
            f'<WatchFace width="450" height="450" clipShape="CIRCLE">{weather_bitmap_fonts()}'
            f'<Scene backgroundColor="#ff000000">{scene}</Scene></WatchFace>\n')


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    test_xml = os.environ.get("WF_TEST_XML")   # Experimente: fertige XML-Datei statt generierter
    OUT.write_text(Path(test_xml).read_text(encoding="utf-8") if test_xml else build(), encoding="utf-8")
    print("geschrieben:", OUT, f"({OUT.stat().st_size} Bytes)")
