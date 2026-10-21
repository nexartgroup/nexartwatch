#!/usr/bin/env python3
"""Erzeugt das App-Symbol (Adaptive Icon) für Handy- und Uhr-App: neXart-„N“ im 24-h-Termin-Ring einer Uhr.

Schreibt je App `res/drawable/ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml` und passt
`res/mipmap-anydpi-v26/ic_launcher.xml` sowie `res/values/colors.xml` an. Zusätzlich entsteht eine
SVG-Vorschau (`tools/icon_preview.svg`). Das N stammt von nexart.de (Pfade siehe unten).

Aufruf:  python3 tools/gen_icon.py
"""
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BG = "#0E1720"
C = 54.0                      # Mitte im 108er-Raster
R = 29.5                      # Mittellinie des Rings (Sicherheitszone des Adaptive Icons: Radius 33)
W = 5.0                       # Dicke der Segmente
TRACK = "#203240"

# (Start, Ende) in Stunden; 0 Uhr = unten, im Uhrzeigersinn - Farben wie die Palette der App
SEGMENTS = [
    (0.8, 3.6, "#3FA9F5"), (4.0, 7.6, "#E3B25E"), (8.2, 10.2, "#5FD0C0"),
    (10.8, 13.0, "#EF8A7A"), (14.0, 18.0, "#B3A6EC"), (18.7, 21.8, "#A9D36F"),
]
NOW_HOUR = 13.5               # weißer Punkt = "jetzt"

# neXart-Logo (nexart.de/assets/img/favicon.svg), Raster 0..1681,4
N_SLATE = "M1472.09,39.7c-19.2,21.28-30.89,49.48-30.89,80.4v288.89l-261.33,261.34-509.54,509.54-456.42,456.41c16.46-20.54,26.29-46.62,26.29-74.98v-289.7L1010.67,501.13,1472.09,39.7Z"
N_BLUE = "M1681.4,120.1v1201c0,92.21-35.17,184.42-105.53,254.77-140.7,140.71-368.83,140.71-509.54,0L240.2,749.74v811.56c0,28.36-9.83,54.44-26.29,74.98-5.55,6.96-11.87,13.28-18.83,18.83-20.54,16.46-46.62,26.29-74.98,26.29-66.33,0-120.1-53.77-120.1-120.1V360.3c0-92.21,35.17-184.42,105.53-254.77,35.18-35.18,75.82-61.56,119.19-79.15C354.84-26.38,509.54,0,615.07,105.53l395.6,395.6,169.2,169.2,261.33,261.33V120.1c0-30.92,11.69-59.12,30.89-80.4,2.78-3.09,5.72-6.03,8.81-8.81,21.29-19.2,49.48-30.89,80.4-30.89,33.17,0,63.2,13.44,84.93,35.17,21.73,21.74,35.17,51.76,35.17,84.93Z"
N_NAVY = "M501.25,331.4l849.24,849.24c22.24,22.24,33.94,51.04,35.08,80.17,0,.03,0,.06,0,.1.12,3.1.12,6.21,0,9.31,0,.03,0,.07,0,.1-1.15,29.13-12.84,57.92-35.08,80.17-46.9,46.9-122.95,46.9-169.85,0L331.4,501.25c-46.9-46.9-46.9-122.95,0-169.85,23.45-23.45,54.19-35.18,84.92-35.18s61.47,11.73,84.92,35.18Z"
N_SIZE = 35.0                 # Kantenlänge des N im Raster
N_SCALE = N_SIZE / 1681.4
N_OFFSET = C - N_SCALE * 840.7


def pt(hour):
    a = math.radians(90 + 15 * hour)
    return C + R * math.cos(a), C + R * math.sin(a)


def arc_path(h0, h1):
    x0, y0 = pt(h0)
    x1, y1 = pt(h1)
    large = 1 if (h1 - h0) * 15 > 180 else 0
    return f"M{x0:.2f},{y0:.2f} A{R},{R} 0 {large},1 {x1:.2f},{y1:.2f}"


def circle_path(r):
    return f"M{C - r},{C} a{r},{r} 0 1,1 {2 * r},0 a{r},{r} 0 1,1 {-2 * r},0"


def vector(width_dp, body, mono=False):
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{width_dp}dp"\n    android:height="{width_dp}dp"\n'
            f'    android:viewportWidth="108"\n    android:viewportHeight="108">\n{body}</vector>\n')


def stroke_path(d, color, width, alpha=None):
    a = f'\n        android:strokeAlpha="{alpha}"' if alpha else ""
    return (f'    <path\n        android:fillColor="#00000000"\n        android:strokeColor="{color}"\n'
            f'        android:strokeWidth="{width}"{a}\n        android:pathData="{d}" />\n')


def fill_path(d, color):
    return f'        <path\n            android:fillColor="{color}"\n            android:pathData="{d}" />\n'


def foreground():
    body = stroke_path(circle_path(R), TRACK, W)
    for h0, h1, col in SEGMENTS:
        body += stroke_path(arc_path(h0, h1), col, W)
    nx, ny = pt(NOW_HOUR)
    body += (f'    <path\n        android:fillColor="{BG}"\n        android:pathData="{circle_path_at(nx, ny, 3.3)}" />\n'
             f'    <path\n        android:fillColor="#FFFFFF"\n        android:pathData="{circle_path_at(nx, ny, 2.3)}" />\n')
    body += (f'    <group\n        android:translateX="{N_OFFSET:.3f}"\n        android:translateY="{N_OFFSET:.3f}"\n'
             f'        android:scaleX="{N_SCALE:.6f}"\n        android:scaleY="{N_SCALE:.6f}">\n'
             + fill_path(N_SLATE, "#566B80") + fill_path(N_BLUE, "#3FA9F5") + fill_path(N_NAVY, "#203240") + '    </group>\n')
    return vector(108, body)


def circle_path_at(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f} a{r},{r} 0 1,1 {2 * r},0 a{r},{r} 0 1,1 {-2 * r},0"


def monochrome():
    body = stroke_path(circle_path(R), "#000000", W, 0.25)
    for h0, h1, _ in SEGMENTS:
        body += stroke_path(arc_path(h0, h1), "#000000", W)
    body += (f'    <group\n        android:translateX="{N_OFFSET:.3f}"\n        android:translateY="{N_OFFSET:.3f}"\n'
             f'        android:scaleX="{N_SCALE:.6f}"\n        android:scaleY="{N_SCALE:.6f}">\n'
             + fill_path(N_SLATE, "#80000000") + fill_path(N_BLUE, "#FF000000") + '    </group>\n')
    return vector(108, body)


ADAPTIVE = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/icon_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
COLORS = f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="icon_background">{BG}</color>
</resources>
'''


def preview_svg():
    """Gleiche Geometrie als SVG (mit runder Maske) zum Ansehen."""
    segs = f'<circle cx="{C}" cy="{C}" r="{R}" fill="none" stroke="{TRACK}" stroke-width="{W}"/>'
    for h0, h1, col in SEGMENTS:
        segs += f'<path d="{arc_path(h0, h1)}" fill="none" stroke="{col}" stroke-width="{W}"/>'
    nx, ny = pt(NOW_HOUR)
    segs += f'<circle cx="{nx:.2f}" cy="{ny:.2f}" r="3.3" fill="{BG}"/><circle cx="{nx:.2f}" cy="{ny:.2f}" r="2.3" fill="#fff"/>'
    logo = (f'<g transform="translate({N_OFFSET:.3f} {N_OFFSET:.3f}) scale({N_SCALE:.6f})">'
            f'<path d="{N_SLATE}" fill="#566B80"/><path d="{N_BLUE}" fill="#3FA9F5"/><path d="{N_NAVY}" fill="#203240"/></g>')
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 216 108" width="864" height="432">'
            f'<rect width="216" height="108" fill="#101820"/>'
            f'<defs><clipPath id="c"><circle cx="54" cy="54" r="54"/></clipPath><clipPath id="s"><rect x="0" y="0" width="108" height="108" rx="24"/></clipPath></defs>'
            f'<g clip-path="url(#c)"><rect width="108" height="108" fill="{BG}"/>{segs}{logo}</g>'
            f'<g transform="translate(108 0)"><g clip-path="url(#s)"><rect width="108" height="108" fill="{BG}"/>{segs}{logo}</g></g></svg>')


if __name__ == "__main__":
    for app in ("phone", "watch"):
        res = ROOT / app / "src/main/res"
        (res / "drawable").mkdir(parents=True, exist_ok=True)
        (res / "drawable/ic_launcher_foreground.xml").write_text(foreground(), encoding="utf-8")
        (res / "drawable/ic_launcher_monochrome.xml").write_text(monochrome(), encoding="utf-8")
        (res / "mipmap-anydpi-v26/ic_launcher.xml").write_text(ADAPTIVE, encoding="utf-8")
        (res / "values/colors.xml").write_text(COLORS, encoding="utf-8")
    (ROOT / "tools/icon_preview.svg").write_text(preview_svg(), encoding="utf-8")
    print("Symbole geschrieben")
