#!/usr/bin/env python3
"""Erzeugt die PNG-Icons (weiß auf transparent, 96x96) für das Watchface.

Das Watch Face Format kennt nur Rastergrafiken, daher werden Symbole hier gezeichnet:
Formen als Masken, Kontur = Maske minus verkleinerte Maske (Supersampling für glatte Kanten).
"""
import math
import re
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "watchface/src/main/res/drawable-nodpi"
SS = 4            # Supersampling
N = 96 * SS       # Arbeitsgröße
STROKE = 6.5      # Strichstärke in 96er-Pixeln
WHITE = (255, 255, 255)

PHONE = ("M6.62,10.79c1.44,2.83 3.76,5.14 6.59,6.59l2.2,-2.2c0.27,-0.27 0.67,-0.36 1.02,-0.24 "
         "1.12,0.37 2.33,0.57 3.57,0.57 0.55,0 1,0.45 1,1V20c0,0.55 -0.45,1 -1,1 "
         "-9.39,0 -17,-7.61 -17,-17 0,-0.55 0.45,-1 1,-1h3.5c0.55,0 1,0.45 1,1 "
         "0,1.25 0.2,2.45 0.57,3.57 0.11,0.35 0.03,0.74 -0.25,1.02l-2.2,2.2z")


def parse_path(d, steps=14):
    """Minimaler SVG-Pfad-Parser (M m L l H h V v C c Z z) -> Polygonpunkte."""
    tokens = re.findall(r"[MmLlHhVvCcZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    pts, i, cmd = [], 0, None
    x = y = sx = sy = 0.0

    def num():
        nonlocal i
        v = float(tokens[i]); i += 1
        return v

    while i < len(tokens):
        if re.match(r"[A-Za-z]", tokens[i]):
            cmd = tokens[i]; i += 1
            if cmd in "Zz":
                x, y = sx, sy
                continue
        if cmd in "Mm":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if cmd == "m" else (nx, ny)
            sx, sy = x, y
            pts.append((x, y))
            cmd = "l" if cmd == "m" else "L"
        elif cmd in "Ll":
            nx, ny = num(), num()
            x, y = (x + nx, y + ny) if cmd == "l" else (nx, ny)
            pts.append((x, y))
        elif cmd in "Hh":
            nx = num(); x = x + nx if cmd == "h" else nx; pts.append((x, y))
        elif cmd in "Vv":
            ny = num(); y = y + ny if cmd == "v" else ny; pts.append((x, y))
        elif cmd in "Cc":
            c = [num() for _ in range(6)]
            if cmd == "c":
                c = [x + c[0], y + c[1], x + c[2], y + c[3], x + c[4], y + c[5]]
            p0 = (x, y)
            for s in range(1, steps + 1):
                t = s / steps
                u = 1 - t
                bx = u**3 * p0[0] + 3 * u * u * t * c[0] + 3 * u * t * t * c[2] + t**3 * c[4]
                by = u**3 * p0[1] + 3 * u * u * t * c[1] + 3 * u * t * t * c[3] + t**3 * c[5]
                pts.append((bx, by))
            x, y = c[4], c[5]
    return pts


def canvas():
    return Image.new("L", (N, N), 0)


def to_px(v):
    return v * N / 24.0  # Koordinaten im 24er-Raster


def outline(mask, stroke=STROKE):
    r = int(stroke * SS / 2)
    eroded = mask.filter(ImageFilter.MinFilter(2 * r + 1))
    return ImageChops.subtract(mask, eroded)


def finish(mask, name):
    img = Image.new("RGBA", (N, N), WHITE + (0,))
    img.putalpha(mask)
    img = img.resize((96, 96), Image.LANCZOS)
    OUT.mkdir(parents=True, exist_ok=True)
    img.save(OUT / f"{name}.png", optimize=True)


def poly_mask(points, scale=1.0, dx=0.0, dy=0.0):
    m = canvas()
    pp = [(to_px((px * scale) + dx), to_px((py * scale) + dy)) for px, py in points]
    ImageDraw.Draw(m).polygon(pp, fill=255)
    return m


def circle(m, cx, cy, r):
    d = ImageDraw.Draw(m)
    d.ellipse([to_px(cx - r), to_px(cy - r), to_px(cx + r), to_px(cy + r)], fill=255)


def line(m, x0, y0, x1, y1, w=1.7):
    d = ImageDraw.Draw(m)
    d.line([to_px(x0), to_px(y0), to_px(x1), to_px(y1)], fill=255, width=int(to_px(w)))
    r = to_px(w) / 2
    for (x, y) in ((x0, y0), (x1, y1)):
        d.ellipse([to_px(x) - r, to_px(y) - r, to_px(x) + r, to_px(y) + r], fill=255)


# ----------------------------------------------------------------- Benachrichtigungs-Icons

def icon_phone():
    pts = parse_path(PHONE)
    finish(outline(poly_mask(pts, 0.92, 0.95, 0.95)), "ic_phone")


def icon_whatsapp():
    m = canvas()
    circle(m, 12.2, 11.8, 9.4)
    # Schwanz unten links
    d = ImageDraw.Draw(m)
    d.polygon([(to_px(11.2), to_px(21)), (to_px(2.6), to_px(22)), (to_px(5.6), to_px(17.2))], fill=255)
    body = outline(m)
    phone = poly_mask(parse_path(PHONE), 0.5, 6.2, 5.8)
    phone = outline(phone, 5.2)
    finish(ImageChops.lighter(body, phone), "ic_whatsapp")


def icon_mail():
    m = canvas()
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([to_px(2), to_px(4.6), to_px(22), to_px(19.4)], radius=to_px(2.4), fill=255)
    body = outline(m)
    v = canvas()
    line(v, 3.2, 6.6, 12, 13.4, 1.9)
    line(v, 12, 13.4, 20.8, 6.6, 1.9)
    finish(ImageChops.lighter(body, v), "ic_mail")


# ----------------------------------------------------------------- Wetter-Icons

def cloud_mask(cx=12, cy=14, s=1.0):
    m = canvas()
    for (x, y, r) in ((-4.4, 1.4, 3.6), (-0.6, -1.4, 4.8), (3.8, 0.6, 4.0)):
        circle(m, cx + x * s, cy + y * s, r * s)
    d = ImageDraw.Draw(m)
    d.rectangle([to_px(cx - 4.4 * s), to_px(cy + 0.4 * s), to_px(cx + 3.8 * s), to_px(cy + 5.0 * s)], fill=255)
    return m


def sun_mask(cx, cy, r, rays=True):
    m = canvas()
    circle(m, cx, cy, r)
    body = outline(m)
    if rays:
        for k in range(8):
            a = math.radians(45 * k)
            line(body, cx + math.cos(a) * (r + 1.8), cy + math.sin(a) * (r + 1.8),
                 cx + math.cos(a) * (r + 3.6), cy + math.sin(a) * (r + 3.6), 1.7)
    return body


def moon_mask():
    m = canvas()
    circle(m, 12, 12, 8.2)
    cut = canvas()
    circle(cut, 16.2, 9.0, 7.2)
    return outline(ImageChops.subtract(m, cut))


def icon_weather():
    # Sonne
    finish(sun_mask(12, 12, 5.0), "wx_sun")
    # Mond
    finish(moon_mask(), "wx_moon")
    # Wolke
    finish(outline(cloud_mask(12, 12.5, 1.15)), "wx_cloud")
    # Wolke mit Sonne (Tag / Nacht)
    for name, back in (("wx_partly_day", True), ("wx_partly_night", False)):
        c = cloud_mask(14, 15, 0.82)
        cdil = c.filter(ImageFilter.MaxFilter(2 * int(1.4 * SS) + 1))
        if back:
            sm = sun_mask(8.2, 8.2, 3.6)
        else:
            sm = canvas()
            circle(sm, 8.4, 8.2, 4.4)
            cut = canvas(); circle(cut, 11.0, 6.4, 3.8)
            sm = outline(ImageChops.subtract(sm, cut), 5.5)
        sm = ImageChops.subtract(sm, cdil)
        finish(ImageChops.lighter(sm, outline(c)), name)
    # Regen
    c = outline(cloud_mask(12, 9.5, 0.95))
    for x in (7.5, 12, 16.5):
        line(c, x, 17.0, x - 1.3, 20.6, 1.8)
    finish(c, "wx_rain")
    # Schnee
    c = outline(cloud_mask(12, 9.5, 0.95))
    for x, y in ((7.5, 18.8), (12, 20.3), (16.5, 18.8)):
        circle(c, x, y, 1.25)
    finish(c, "wx_snow")
    # Gewitter
    c = outline(cloud_mask(12, 9.0, 0.95))
    bolt = poly_mask([(13.2, 14.6), (8.6, 19.6), (11.6, 19.6), (10.4, 23.4), (15.6, 17.8), (12.4, 17.8), (14.0, 14.6)])
    finish(ImageChops.lighter(c, outline(bolt, 3.2)), "wx_thunder")
    # Nebel
    m = canvas()
    for y, x0, x1 in ((8, 4, 20), (12, 2.5, 21.5), (16, 5, 19), (20, 8, 16)):
        line(m, x0, y, x1, y, 1.9)
    finish(m, "wx_fog")
    # Wind
    m = canvas()
    d = ImageDraw.Draw(m)
    line(m, 3, 9, 15, 9, 1.8); line(m, 3, 14, 19, 14, 1.8); line(m, 3, 19, 11, 19, 1.8)
    for (cx, cy, a0, a1) in ((15, 6.2, -90, 90), (19, 11.2, -90, 90), (11, 16.2, -90, 90)):
        pass
    d.arc([to_px(12.2), to_px(5), to_px(18.4), to_px(11.2)], 270, 120, fill=255, width=int(to_px(1.8)))
    d.arc([to_px(15.8), to_px(10.4), to_px(22), to_px(16.6)], 270, 120, fill=255, width=int(to_px(1.8)))
    finish(m, "wx_wind")
    # Unbekannt
    m = canvas()
    d = ImageDraw.Draw(m)
    d.arc([to_px(7.5), to_px(4.5), to_px(16.5), to_px(13.5)], 160, 60, fill=255, width=int(to_px(1.9)))
    line(m, 12, 13, 12, 15.5, 1.9)
    circle(m, 12, 19.2, 1.2)
    finish(m, "wx_unknown")


if __name__ == "__main__":
    icon_phone(); icon_whatsapp(); icon_mail(); icon_weather()
    print("Icons geschrieben nach", OUT)
