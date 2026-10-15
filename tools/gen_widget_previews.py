#!/usr/bin/env python3
"""Draws the launcher preview of every themed widget (Skins in widgets/ThemeWidgets.kt) with Pillow.

Each skin is a port of its Canvas drawing, with sample data, so the widget picker shows the real look.
Run from anywhere: python3 -I tools/gen_widget_previews.py
"""
import math
import sys
import os
import datetime
from PIL import Image, ImageDraw, ImageFont, ImageChops, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
ART = os.path.join(RES, "drawable-nodpi")

# sample data
CPU, RAM, BAT, CHARGING, DISK_USED, DISK_FREE_GB = 23, 58, 76, False, 64, 40.0
NOW = datetime.datetime(2026, 10, 14, 10, 9)
MODEL = "Pixel 8"

# Material You fallback tones (a green wallpaper); Glass and iOS previews use the iOS blue as their accent
BLUE = (100, 181, 246)
TEMP, DOWN, UP, RAM_USED_MB, UPTIME_MIN = 47.0, 1_450_000, 96_000, 4710, 3 * 1440 + 5 * 60 + 12
M_CARD, M_FILL, M_ACCENT, M_ON, M_TEXT, M_SUB = (40, 48, 36), (46, 74, 31), (170, 214, 140), (28, 54, 10), (220, 240, 200), (170, 180, 160)

ROBOTO = "/system/fonts/Roboto-Regular.ttf"   # Android's variable Roboto, when the script runs on the phone
DEJAVU = "/usr/share/fonts/truetype/dejavu/"
WEIGHT = {"sans": 400, "bold": 700, "medium": 500, "light": 300, "thin": 100}
_fonts = {}


def font(face, px):
    key = (face, round(px * 4))
    if key not in _fonts:
        if face == "mono":
            f = ImageFont.truetype(DEJAVU + "DejaVuSansMono.ttf", px)
        elif os.path.exists(ROBOTO):
            f = ImageFont.truetype(ROBOTO, px)
            f.set_variation_by_axes([WEIGHT[face], 100, 0])
        else:
            f = ImageFont.truetype(DEJAVU + ("DejaVuSans-Bold.ttf" if face == "bold" else "DejaVuSans.ttf"), px)
        _fonts[key] = f
    return _fonts[key]


def measure(s, size, face):
    return font(face, size * 8).getlength(s) / 8


def rgba(c):
    return tuple(c) if len(c) == 4 else tuple(c) + (255,)


def argb(a, r, g, b):
    return (r, g, b, a)


WHITE, BLACK = (255, 255, 255), (0, 0, 0)


class Canvas:
    """A tiny android.graphics.Canvas: logical coordinates times k (canvas scale) times ss (supersampling)."""

    def __init__(self, w, h, ss=2, aa=True):
        self.w, self.h, self.ss, self.aa = w, h, ss, aa
        self.img = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
        self.k = 1.0
        self.clip = None

    def scale(self, k):
        self.k = k

    def d(self, v):
        return v * self.k * self.ss

    def _mask(self):
        return Image.new("L", self.img.size, 0)

    def _box(self, x0, y0, x1, y1):
        # a continuous rect to the inclusive pixel box Pillow wants
        return [round(self.d(x0)), round(self.d(y0)), round(self.d(x1)) - 1, round(self.d(y1)) - 1]

    def _fill(self, mask, paint):
        if self.clip is not None:
            mask = ImageChops.multiply(mask, self.clip)
        if isinstance(paint, Image.Image):
            src = paint.copy()
            src.putalpha(ImageChops.multiply(paint.getchannel("A"), mask))
        else:
            r, g, b, a = rgba(paint)
            if a < 255:
                mask = mask.point(lambda v: v * a // 255)
            src = Image.new("RGBA", self.img.size, (r, g, b, 0))
            src.putalpha(mask)
        self.img.alpha_composite(src)

    def gradient(self, x0, y0, x1, y1, colors, stops=None):
        """A LinearGradient shader as a full-size image."""
        colors = [rgba(c) for c in colors]
        stops = stops or [i / (len(colors) - 1) for i in range(len(colors))]
        X0, Y0, X1, Y1 = self.d(x0), self.d(y0), self.d(x1), self.d(y1)
        vx, vy = X1 - X0, Y1 - Y0
        L = vx * vx + vy * vy

        def col(t):
            t = min(max(t, 0.0), 1.0)
            for i in range(len(stops) - 1):
                if t <= stops[i + 1] or i == len(stops) - 2:
                    span = stops[i + 1] - stops[i]
                    f = 0 if span <= 0 else min(max((t - stops[i]) / span, 0), 1)
                    return tuple(round(a + (b - a) * f) for a, b in zip(colors[i], colors[i + 1]))
        W, H = self.img.size
        if vx == 0:   # vertical: one column, stretched
            colm = Image.new("RGBA", (1, H))
            colm.putdata([col(((y + .5) - Y0) * vy / L) for y in range(H)])
            return colm.resize((W, H), Image.NEAREST)
        g = Image.new("RGBA", (W, H))
        g.putdata([col(((x + .5 - X0) * vx + (y + .5 - Y0) * vy) / L) for y in range(H) for x in range(W)])
        return g

    # ---- shapes
    def rect(self, x0, y0, x1, y1, paint):
        m = self._mask()
        ImageDraw.Draw(m).rectangle(self._box(x0, y0, x1, y1), fill=255)
        self._fill(m, paint)

    def rect_stroke(self, x0, y0, x1, y1, sw, paint):
        m = self._mask(); dr = ImageDraw.Draw(m); h = sw / 2
        dr.rectangle(self._box(x0 - h, y0 - h, x1 + h, y1 + h), fill=255)
        dr.rectangle(self._box(x0 + h, y0 + h, x1 - h, y1 - h), fill=0)
        self._fill(m, paint)

    def _rr(self, dr, x0, y0, x1, y1, r, fill):
        r = max(0.0, min(r, (x1 - x0) / 2, (y1 - y0) / 2))
        dr.rounded_rectangle(self._box(x0, y0, x1, y1), radius=self.d(r), fill=fill)

    def rrect(self, x0, y0, x1, y1, r, paint):
        m = self._mask()
        self._rr(ImageDraw.Draw(m), x0, y0, x1, y1, r, 255)
        self._fill(m, paint)

    def rrect_stroke(self, x0, y0, x1, y1, r, sw, paint):
        m = self._mask(); dr = ImageDraw.Draw(m); h = sw / 2
        self._rr(dr, x0 - h, y0 - h, x1 + h, y1 + h, r + h, 255)
        self._rr(dr, x0 + h, y0 + h, x1 - h, y1 - h, r - h, 0)
        self._fill(m, paint)

    def circle(self, cx, cy, r, paint):
        m = self._mask()
        ImageDraw.Draw(m).ellipse(self._box(cx - r, cy - r, cx + r, cy + r), fill=255)
        self._fill(m, paint)

    def _cap(self, dr, x, y, sw):
        r = self.d(sw / 2)
        dr.ellipse([self.d(x) - r, self.d(y) - r, self.d(x) + r - 1, self.d(y) + r - 1], fill=255)

    def line(self, x0, y0, x1, y1, sw, paint, round_cap=False):
        m = self._mask(); dr = ImageDraw.Draw(m)
        # butt line as a quad, so a wide stroke ends square like Android's
        dx, dy = x1 - x0, y1 - y0
        n = math.hypot(dx, dy) or 1
        nx, ny = -dy / n * sw / 2, dx / n * sw / 2
        dr.polygon([(self.d(x), self.d(y)) for x, y in ((x0 + nx, y0 + ny), (x1 + nx, y1 + ny), (x1 - nx, y1 - ny), (x0 - nx, y0 - ny))], fill=255)
        if round_cap:
            self._cap(dr, x0, y0, sw); self._cap(dr, x1, y1, sw)
        self._fill(m, paint)

    def arc(self, x0, y0, x1, y1, start, sweep, sw, paint, round_cap=True):
        """drawArc with a centred stroke."""
        if sweep <= 0:
            return
        m = self._mask(); dr = ImageDraw.Draw(m); h = sw / 2
        box = self._box(x0 - h, y0 - h, x1 + h, y1 + h)
        if sweep >= 360:
            dr.ellipse(box, fill=255)
            dr.ellipse(self._box(x0 + h, y0 + h, x1 - h, y1 - h), fill=0)
        else:
            dr.arc(box, start, start + sweep, fill=255, width=max(1, round(self.d(sw))))
            if round_cap:
                cx, cy, rx, ry = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2, (y1 - y0) / 2
                for a in (start, start + sweep):
                    t = math.radians(a)
                    self._cap(dr, cx + rx * math.cos(t), cy + ry * math.sin(t), sw)
        self._fill(m, paint)

    def pie(self, x0, y0, x1, y1, start, sweep, paint):
        if sweep <= 0:
            return
        m = self._mask()
        ImageDraw.Draw(m).pieslice(self._box(x0, y0, x1, y1), start, start + sweep, fill=255)
        self._fill(m, paint)

    def polygon(self, pts, paint):
        m = self._mask()
        ImageDraw.Draw(m).polygon([(self.d(x), self.d(y)) for x, y in pts], fill=255)
        self._fill(m, paint)

    def polygon_stroke(self, pts, sw, paint):
        m = self._mask()
        p = [(self.d(x), self.d(y)) for x, y in pts]
        ImageDraw.Draw(m).line(p + [p[0]], fill=255, width=max(1, round(self.d(sw))), joint="curve")
        self._fill(m, paint)

    def clip_rect(self, x0, y0, x1, y1):
        self.clip = self._mask()
        ImageDraw.Draw(self.clip).rectangle(self._box(x0, y0, x1, y1), fill=255)

    def clip_rrect(self, x0, y0, x1, y1, r):
        self.clip = self._mask()
        self._rr(ImageDraw.Draw(self.clip), x0, y0, x1, y1, r, 255)

    def restore(self):
        self.clip = None

    # ---- text and bitmaps
    def text(self, s, x, y, size, color, face="sans", align="center", shadow=None, aa=True):
        anchor = {"center": "ms", "left": "ls", "right": "rs"}[align]
        f = font(face, self.d(size))

        def mask(dy=0.0):
            m = self._mask(); dr = ImageDraw.Draw(m)
            if not (aa and self.aa):
                dr.fontmode = "1"
            dr.text((self.d(x), self.d(y + dy)), s, fill=255, font=f, anchor=anchor)
            return m
        if shadow is not None:
            # setShadowLayer(size * .08, 0, size * .05): Android blurs with sigma = radius * .57735 + .5
            sigma = self.d(size * .08) * .57735 + .5 * self.ss
            self._fill(mask(size * .05).filter(ImageFilter.GaussianBlur(sigma)), shadow)
        self._fill(mask(), color)

    def bitmap(self, name, x0, y0, x1, y1, rotate=None):
        src = Image.open(os.path.join(ART, name + ".png")).convert("RGBA")
        b = self._box(x0, y0, x1, y1)
        src = src.resize((b[2] - b[0] + 1, b[3] - b[1] + 1), Image.LANCZOS)
        layer = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        layer.paste(src, (b[0], b[1]))
        if rotate:
            deg, px, py = rotate
            layer = layer.rotate(-deg, resample=Image.BICUBIC, center=(self.d(px), self.d(py)))
        if self.clip is not None:
            layer.putalpha(ImageChops.multiply(layer.getchannel("A"), self.clip))
        self.img.alpha_composite(layer)

    def result(self):
        return self.img if self.ss == 1 else self.img.resize((self.w, self.h), Image.LANCZOS)


# ---------------------------------------------------------------- shared helpers (ports of Skins)

def hh():
    return "%02d" % NOW.hour


def mm():
    return "%02d" % NOW.minute


def angles():
    h, m = NOW.hour % 12, NOW.minute
    return h * 30 + m / 2, m * 6


def gb(g):
    return "%.1f GB" % g


def fit_text(s, mx, size, face):
    w = measure(s, size, face)
    return size if w <= mx else size * mx / w


def gauge(c, r, pct, stroke, track, color, start=135.0, sweep=270.0):
    c.arc(*r, start, sweep, stroke, track)
    if pct > 0:
        c.arc(*r, start, sweep * min(max(pct, 0), 100) / 100, stroke, color)


def month_grid(c, area, head, day, mark, mark_text, dim, face, rnd):
    today = NOW.day
    first = NOW.replace(day=1).weekday()   # Monday first
    days = ((NOW.replace(day=28) + datetime.timedelta(days=4)).replace(day=1) - datetime.timedelta(days=1)).day
    l, t, r, b = area
    cw = (r - l) / 7; rows = (first + days + 6) // 7 + 1; ch = (b - t) / rows
    size = min(cw, ch) * .55
    for i, s in enumerate("MTWTFSS"):
        c.text(s, l + cw * (i + .5), t + ch * .7, size, dim if i >= 5 else head, face)
    for n in range(1, days + 1):
        idx = first + n - 1; col = idx % 7; row = idx // 7 + 1
        x = l + cw * (col + .5); y = t + ch * row
        if n == today:
            if rnd:
                c.circle(x, y + ch * .45, min(cw, ch) * .46, mark)
            else:
                c.rect(x - cw / 2, y + 1, x + cw / 2, y + ch - 1, mark)
        c.text(str(n), x, y + ch * .68, size, mark_text if n == today else dim if col >= 5 else day, face)


def analog(c, cx, cy, r, tick, hand, numerals=None, face="sans", minute_w=None, hm=None):
    minute_w = r * .06 if minute_w is None else minute_w
    for i in range(60):
        a = math.radians(i * 6)
        big = i % 5 == 0
        if numerals is not None and big:
            continue
        sw = r * .035 if big else r * .014
        r0 = r * .84 if big else r * .9
        c.line(cx + r0 * math.sin(a), cy - r0 * math.cos(a), cx + r * .96 * math.sin(a), cy - r * .96 * math.cos(a), sw, tick, True)
    if numerals is not None:
        for n in range(1, 13):
            a = math.radians(n * 30)
            c.text(str(n), cx + r * .76 * math.sin(a), cy - r * .76 * math.cos(a) + r * .085, r * .23, numerals, face)
    ha, ma = angles() if hm is None else hm

    def hand_line(deg, ln, w):
        a = math.radians(deg)
        c.line(cx, cy, cx + ln * math.sin(a), cy - ln * math.cos(a), w, hand, True)
    hand_line(ha, r * .5, minute_w * 1.4)
    hand_line(ma, r * .75, minute_w)
    c.circle(cx, cy, minute_w * 1.4, hand)


def chip(c, cx, cy, r, color):
    sw = r * .18
    c.rrect_stroke(cx - r, cy - r, cx + r, cy + r, r * .25, sw, color)
    c.rect_stroke(cx - r * .45, cy - r * .45, cx + r * .45, cy + r * .45, sw, color)
    for i in (-1, 0, 1):
        o = i * r * .5
        c.line(cx + o, cy - r, cx + o, cy - r * 1.4, sw, color); c.line(cx + o, cy + r, cx + o, cy + r * 1.4, sw, color)
        c.line(cx - r, cy + o, cx - r * 1.4, cy + o, sw, color); c.line(cx + r, cy + o, cx + r * 1.4, cy + o, sw, color)


def bolt(c, cx, cy, r, color):
    c.polygon([(cx + r * .2, cy - r), (cx - r * .6, cy + r * .15), (cx - r * .05, cy + r * .15),
               (cx - r * .2, cy + r), (cx + r * .6, cy - r * .15), (cx + r * .05, cy - r * .15)], color)


def sun(c, cx, cy, r, color):
    sw = r * .22
    c.pie(cx - r * .6, cy - r * .6, cx + r * .6, cy + r * .6, 180, 180, color)
    c.line(cx - r * 1.4, cy + r * .2, cx + r * 1.4, cy + r * .2, sw, color, True)
    for i in range(5):
        a = math.radians(180 + 45 * i)
        c.line(cx + r * .85 * math.cos(a), cy + r * .85 * math.sin(a), cx + r * 1.2 * math.cos(a), cy + r * 1.2 * math.sin(a), sw, color, True)


# ---------------------------------------------------------------- Windows 7: the real gadget art

CLOCKS = {  # skin: (art name, left, y, pw, ph)
    "W7_CLOCK_TRAD": ("trad", 57, -1, 13, 129), "W7_CLOCK_SYSTEM": ("system", 58, 0, 13, 129),
    "W7_CLOCK_CRONO": ("cronometer", 57, -1, 13, 129), "W7_CLOCK_DINER": ("diner", 58, -1, 13, 129),
    "W7_CLOCK_FLOWER": ("flower", 59, 0, 13, 129), "W7_CLOCK_MODERN": ("modern", 58, -1, 13, 129),
    "W7_CLOCK_SQUARE": ("square", 57, -1, 13, 129), "W7_CLOCK_NOVELTY": ("novelty", 59, 46, 7, 81),
    "W7_CLOCK_MONITOR": ("monitor", 57, -1, 13, 129),
}


def win7(c, s, w, h):
    if s in CLOCKS:
        n, left, y, pw, ph = CLOCKS[s]
        c.scale(w / 130)
        c.bitmap("gadget_clk_" + n, 0, 0, 130, 130)
        # the _top overlay is not drawn: it shows as a grey box
        ha, ma = angles()
        piv = (left + pw / 2, y + ph / 2)
        for part, deg in (("h", ha), ("m", ma), ("dot", 0)):
            c.bitmap("gadget_clk_%s_%s" % (n, part), left, y, left + pw, y + ph, (deg,) + piv if deg else None)
        c.bitmap("gadget_clk_highlights", 3, 3, 127, 127)
    elif s == "W7_CPU":
        c.scale(w / 198)
        c.bitmap("gadget_cpu_back", 0, 0, 198, 159)
        c.bitmap("gadget_cpu_dial", 63, 34, 73, 132, (CPU * 2.5 - 125, 68, 83))
        c.bitmap("gadget_cpu_dial_small", 137, 16, 147, 86, (RAM * 2.5 - 125, 142, 51))
        c.bitmap("gadget_cpu_dots", 0, 0, 198, 150)
        c.text("%d%%" % CPU, 68, 119, 12, WHITE, "bold", shadow=BLACK)
        c.text("%d%%" % RAM, 142, 77, 11, WHITE, "bold", shadow=BLACK)
        c.bitmap("gadget_cpu_glass", 0, 0, 198, 159)
    elif s == "W7_CALENDAR":
        c.scale(w / 130)
        c.bitmap("gadget_cal_page", 0, 0, 130, 141)
        sunday = NOW.weekday() == 6
        c.text(NOW.strftime("%B %Y"), 65, 40, 11, (64, 64, 64), "bold")
        c.text(str(NOW.day), 65, 104, 58, (214, 66, 32) if sunday else (52, 52, 52), "light")
        c.text(NOW.strftime("%A"), 65, 127, 11, (96, 96, 96), "sans")
    elif s == "W7_BATTERY":
        c.scale(w / 135)
        c.bitmap("gadget_bat_back", 0, 0, 135, 80)
        c.bitmap("gadget_bat_%d" % min(max(int((BAT + 6) / 12.5), 0), 8), 2, 3, 132, 78)
        c.text("%d%%" % BAT, 62, 49, 22, WHITE, "bold", shadow=BLACK)
        if CHARGING:
            c.bitmap("gadget_bat_charging", 104, 21, 117, 43)
    elif s == "W7_DRIVE":
        c.scale(w / 156)
        c.bitmap("gadget_drive_back", 0, 0, 156, 48)
        c.bitmap("gadget_drive_icon", 4, 6, 44, 40)
        c.text("Internal (C:)", 48, 17, 10, WHITE, "bold", "left", BLACK)
        c.text("%s free" % gb(DISK_FREE_GB), 48, 29, 9, (220, 232, 248), "sans", "left", BLACK)
        meter = "red" if DISK_USED >= 90 else "orange" if DISK_USED >= 75 else "blue"
        c.rect(48, 35, 150, 41, argb(110, 0, 0, 0))
        c.clip_rect(48, 35, 48 + 102 * DISK_USED / 100, 41)
        c.bitmap("gadget_drive_meter_" + meter, 48, 35, 150, 41); c.restore()


# ---------------------------------------------------------------- Windows 11

# see-through dark acrylic at the default .62 opacity, like Win11Material over the wallpaper
W11_TILE, W11_BLUE, W11_TRACK = argb(158, 10, 10, 10), (96, 182, 246), argb(56, 255, 255, 255)


def win11(c, s, w, h):
    c.rrect(0, 0, w, h, w * .16, W11_TILE)
    c.rrect_stroke(.75, .75, w - .75, h - .75, w * .16, 1.5, argb(60, 255, 255, 255))
    cx = w / 2
    if s == "W11_CLOCK":
        c.circle(cx, h / 2, w * .36, argb(24, 255, 255, 255))
        analog(c, cx, h / 2, w * .36, W11_BLUE, W11_BLUE, numerals=W11_BLUE, face="medium", minute_w=w * .016)
    elif s == "W11_DATE":
        c.text(NOW.strftime("%a %b"), cx, h * .23, w * .14, W11_BLUE, "bold")
        c.text(str(NOW.day), cx, h * .73, w * .5, W11_BLUE, "medium")
        c.text(NOW.strftime("%Y"), cx, h * .89, w * .1, W11_BLUE, "sans")
    elif s == "W11_CPU":
        r = (w * .12, h * .1, w * .44, h * .42)
        gauge(c, r, CPU, w * .025, W11_TRACK, W11_BLUE, -90, 360)
        chip(c, (r[0] + r[2]) / 2, (r[1] + r[3]) / 2, w * .085, W11_BLUE)
        c.text("%d%%" % CPU, w * .1, h * .85, w * .3, W11_BLUE, "light", "left")
    elif s in ("W11_MEMORY", "W11_BATTERY", "W11_STORAGE"):
        v = {"W11_MEMORY": RAM, "W11_BATTERY": BAT}.get(s, DISK_USED)
        gauge(c, (w * .14, h * .14, w * .86, h * .86), v, w * .05, W11_TRACK, W11_BLUE)
        c.text(str(v), cx, h * .56, w * .24, W11_BLUE, "medium")
        if s == "W11_MEMORY":
            chip(c, cx, h * .74, w * .05, W11_BLUE)
        elif s == "W11_BATTERY":
            bolt(c, cx, h * .74, w * .055, W11_BLUE)
        else:
            c.text("C:", cx, h * .79, w * .09, W11_BLUE, "medium")
    elif s == "W11_CALENDAR":
        c.text(NOW.strftime("%B").upper(), w * .1, h * .17, w * .07, WHITE, "medium", "left")
        month_grid(c, (w * .06, h * .2, w * .94, h * .95), WHITE, WHITE, W11_BLUE, WHITE, (150, 150, 150), "medium", True)
    elif s == "W11_TIME":
        c.circle(cx, h / 2, w * .36, argb(32, 255, 255, 255))
        sun(c, cx, h * .36, w * .06, W11_BLUE)
        c.text("%s:%s" % (hh(), mm()), cx, h * .64, w * .16, W11_BLUE, "bold")


# ---------------------------------------------------------------- Windows XP

def luna_window(c, w, h, title):
    u = w / 160
    blue = (0, 61, 220)
    c.rrect(0, 0, w, h, 8 * u, blue)
    c.rect(0, 10 * u, w, h, blue)
    bar_b = 24 * u
    g = c.gradient(0, 0, 0, bar_b, [(9, 151, 255), (0, 83, 238), (0, 80, 238), (6, 111, 252)], [0, .12, .6, 1])
    c.clip_rect(0, 0, w, bar_b); c.rrect(0, 0, w, bar_b + 10 * u, 8 * u, g); c.restore()
    c.text(title, 7 * u, 16.5 * u, 9.5 * u, WHITE, "bold", "left", (10, 24, 131))
    x0, y0, x1, y1 = w - 21 * u, 3 * u, w - 3 * u, 21 * u
    c.rrect(x0, y0, x1, y1, 3 * u, WHITE)
    c.rrect(x0 + u, y0 + u, x1 - u, y1 - u, 2.5 * u, c.gradient(0, y0, 0, y1, [(231, 131, 98), (208, 59, 22)]))
    c.line(x0 + 5 * u, y0 + 5 * u, x1 - 5 * u, y1 - 5 * u, 2 * u, WHITE, True)
    c.line(x1 - 5 * u, y0 + 5 * u, x0 + 5 * u, y1 - 5 * u, 2 * u, WHITE, True)
    body = (3 * u, 24 * u, w - 3 * u, h - 3 * u)
    c.rect(*body, (236, 233, 216))
    return body


def luna_bar(c, r, pct):
    l, t, rt, b = r
    u = (b - t) / 14
    c.rrect(l, t, rt, b, 3 * u, (104, 104, 104))
    il, it, ir, ib = l + u, t + u, rt - u, b - u
    c.rrect(il, it, ir, ib, 2 * u, WHITE)
    bw, gap = 7 * u, 2 * u
    green = c.gradient(0, it, 0, ib, [(172, 237, 173), (45, 210, 44), (36, 174, 36)], [0, .45, 1])
    x = il + 2 * u
    end = il + 2 * u + (ir - il - 4 * u) * min(max(pct, 0), 100) / 100
    while x + bw <= end:
        c.rect(x, it + 2 * u, x + bw, ib - 2 * u, green); x += bw + gap


def xp(c, s, w, h):
    u = w / 160
    if s == "XP_CLOCK":
        l, t, r_, b = luna_window(c, w, h, "Clock")
        bcx = (l + r_) / 2
        r = min(r_ - l, b - t - 22 * u) * .42
        cy = t + 8 * u + r
        c.circle(bcx, cy, r * 1.04, (127, 157, 185))
        c.circle(bcx, cy, r, WHITE)
        analog(c, bcx, cy, r, (0, 51, 153), BLACK, numerals=(0, 51, 153), face="bold", minute_w=r * .05)
        c.text("%s:%s" % (hh(), mm()), bcx, b - 6 * u, 11 * u, BLACK, "sans")
    elif s == "XP_CPU":
        l, t, r_, b = luna_window(c, w, h, "CPU Usage")
        bl, bt, br, bb = l + 12 * u, t + 10 * u, r_ - 12 * u, b - 10 * u
        c.rect(bl, bt, br, bb, BLACK)
        bcx = (bl + br) / 2
        rows = 16
        ml, mt, mr, mb = bcx - 16 * u, bt + 8 * u, bcx + 16 * u, bb - 22 * u
        rh = (mb - mt) / rows
        lit = CPU * rows // 100
        for i in range(rows):
            col = (0, 255, 0) if rows - i <= lit else (0, 64, 0)
            y = mt + i * rh
            c.rect(ml, y + rh * .2, bcx - u, y + rh * .85, col)
            c.rect(bcx + u, y + rh * .2, mr, y + rh * .85, col)
        c.text("%d %%" % CPU, bcx, bb - 7 * u, 11 * u, (0, 255, 0), "sans")
    elif s == "XP_BATTERY":
        l, t, r_, b = luna_window(c, w, h, "Power Meter")
        iy = t + 14 * u
        grey = (64, 64, 64)
        c.rrect(l + 10 * u, iy, l + 40 * u, iy + 16 * u, 2 * u, grey)
        c.rect(l + 40 * u, iy + 5 * u, l + 43 * u, iy + 11 * u, grey)
        c.rect(l + 12 * u, iy + 2 * u, l + 12 * u + 26 * u * BAT / 100, iy + 14 * u, (45, 190, 44) if BAT > 20 else (220, 50, 30))
        c.text("%d%% remaining" % BAT, l + 50 * u, iy + 8 * u, 10 * u, BLACK, "sans", "left")
        c.text("Charging" if CHARGING else "On batteries", l + 50 * u, iy + 19 * u, 8.5 * u, (80, 80, 80), "sans", "left")
        luna_bar(c, (l + 10 * u, b - 26 * u, r_ - 10 * u, b - 12 * u), BAT)
    elif s == "XP_DISK":
        l, t, r_, b = luna_window(c, w, h, "Local Disk (C:)")
        pl, pt, pr, pb = l + 10 * u, t + 14 * u, l + 72 * u, t + 44 * u
        used = 360 * DISK_USED / 100
        for k in range(8, 0, -1):
            c.pie(pl, pt + k * u, pr, pb + k * u, -90, used, (0, 0, 160))
            c.pie(pl, pt + k * u, pr, pb + k * u, -90 + used, 360 - used, (160, 0, 160))
        c.pie(pl, pt, pr, pb, -90, used, (0, 0, 255))
        c.pie(pl, pt, pr, pb, -90 + used, 360 - used, (255, 0, 255))
        lx = l + 82 * u
        c.rect(lx, t + 16 * u, lx + 8 * u, t + 24 * u, (0, 0, 255))
        c.text("Used space  %d%%" % DISK_USED, lx + 12 * u, t + 23.5 * u, 8.5 * u, BLACK, "sans", "left")
        c.rect(lx, t + 30 * u, lx + 8 * u, t + 38 * u, (255, 0, 255))
        c.text("Free  %s" % gb(DISK_FREE_GB), lx + 12 * u, t + 37.5 * u, 8.5 * u, BLACK, "sans", "left")
        c.text("Drive C:", (l + r_) / 2, b - 8 * u, 9 * u, BLACK, "sans")


# ---------------------------------------------------------------- Windows 95 (drawn without antialiasing)

W95_FACE, W95_DARK, W95_NAVY, W95_TEAL, W95_LIGHT = (192, 192, 192), (128, 128, 128), (0, 0, 128), (0, 128, 128), (223, 223, 223)


def bevel(c, r, raised, fill):
    l, t, rt, b = r
    c.rect(l, t, rt, b, fill)
    o1, o2 = (WHITE, BLACK) if raised else (W95_DARK, WHITE)
    i1, i2 = (W95_LIGHT, W95_DARK) if raised else (BLACK, W95_LIGHT)
    c.rect(l, t, rt, t + 1, o1); c.rect(l, t, l + 1, b, o1)
    c.rect(l, b - 1, rt, b, o2); c.rect(rt - 1, t, rt, b, o2)
    c.rect(l + 1, t + 1, rt - 1, t + 2, i1); c.rect(l + 1, t + 1, l + 2, b - 1, i1)
    c.rect(l + 1, b - 2, rt - 1, b - 1, i2); c.rect(rt - 2, t + 1, rt - 1, b - 1, i2)


def group_box(c, r, label):
    l, t, rt, b = r
    c.rect_stroke(l, t, rt - 1, b - 1, 1, W95_DARK)
    c.rect_stroke(l + 1, t + 1, rt, b, 1, WHITE)
    tw = measure(label, 9, "sans")
    c.rect(l + 6, t - 5, l + 10 + tw, t + 5, W95_FACE)
    c.text(label, l + 8, t + 3.5, 9, BLACK, "sans", "left")
    c.rect(l + 8, t + 5, l + 8 + measure(label[:1], 9, "sans"), t + 5.6, BLACK)


def tri(c, cx, top, up):
    if up:
        c.polygon([(cx - 3, top + 3), (cx + 3, top + 3), (cx, top)], BLACK)
    else:
        c.polygon([(cx - 3, top - 3), (cx + 3, top - 3), (cx, top)], BLACK)


def win95(c, s, w, h):
    lw = 140.0
    k = w / lw
    lh = h / k
    c.scale(k)
    bevel(c, (0, 0, lw, lh), True, W95_FACE)
    if s == "W95_TIME":
        group_box(c, (6, 9, lw - 6, lh - 6), "Time")
        cx = lw / 2; cy = 9 + (lh - 15) / 2 + 2; r = (lh - 26) / 2
        for i in range(12):
            a = math.radians(i * 30)
            x = cx + r * math.sin(a); y = cy - r * math.cos(a)
            z = 2.2 if i % 3 == 0 else 1.6
            c.rect(x - z + .8, y - z + .8, x + z + .8, y + z + .8, BLACK)
            c.rect(x - z, y - z, x + z, y + z, W95_TEAL)
        ha, ma = angles()

        def hand(deg, ln, width):
            a = math.radians(deg)
            tx = cx + ln * math.sin(a); ty = cy - ln * math.cos(a)
            nx = width * math.cos(a); ny = width * math.sin(a)
            pts = [(cx + nx, cy + ny), (tx, ty), (cx - nx, cy - ny), (cx - ln * .12 * math.sin(a), cy + ln * .12 * math.cos(a))]
            c.polygon(pts, W95_TEAL); c.polygon_stroke(pts, .7, BLACK)
        hand(ha, r * .55, 3); hand(ma, r * .85, 2.4)
    elif s in ("W95_CPU", "W95_BATTERY", "W95_MEMORY"):
        label, v = {"W95_CPU": ("CPU", CPU), "W95_BATTERY": ("Battery", BAT)}.get(s, ("Memory", RAM))
        group_box(c, (6, 9, lw - 6, lh - 6), label)
        f = (14, 20, lw - 34, 36)
        bevel(c, f, False, WHITE)
        c.text("%03d %%" % v, f[2] - 4, f[3] - 4.5, 10, BLACK, "mono", "right", aa=False)
        sl, st, sr, sb = lw - 32, 20, lw - 18, 36
        scy = (st + sb) / 2
        bevel(c, (sl, st, sr, scy), True, W95_FACE)
        bevel(c, (sl, scy, sr, sb), True, W95_FACE)
        tri(c, (sl + sr) / 2, st + 4, True); tri(c, (sl + sr) / 2, scy + 4.5, False)
        bar = (14, lh - 22, lw - 18, lh - 12)
        bevel(c, bar, False, W95_FACE)
        x = bar[0] + 2
        end = bar[0] + 2 + (bar[2] - bar[0] - 4) * min(max(v, 0), 100) / 100
        while x + 6 <= end:
            c.rect(x, bar[1] + 2, x + 6, bar[3] - 2, W95_NAVY); x += 8
    elif s == "W95_DATE":
        group_box(c, (6, 9, lw - 6, lh - 6), "Date")
        m = (12, 17, 74, 30); bevel(c, m, False, WHITE)
        c.text(NOW.strftime("%B"), m[0] + 3, m[3] - 3.5, 8.5, BLACK, "sans", "left", aa=False)
        y = (78, 17, lw - 12, 30); bevel(c, y, False, WHITE)
        c.text(NOW.strftime("%Y"), y[0] + 3, y[3] - 3.5, 8.5, BLACK, "sans", "left", aa=False)
        g = (12, 34, lw - 12, lh - 12); bevel(c, g, False, WHITE)
        month_grid(c, (g[0] + 3, g[1] + 2, g[2] - 3, g[3] - 2), BLACK, BLACK, W95_NAVY, WHITE, W95_DARK, "sans", False)


# ---------------------------------------------------------------- Material You, AMOLED, Glass

def material(c, s, w, h):
    corner = min(w, h) * .24
    if s == "M3_BATTERY":
        c.rrect(0, 0, w, h, corner, M_CARD)
        c.clip_rrect(0, 0, w, h, corner)
        c.rect(0, h * (1 - BAT / 100), w, h, M_FILL); c.restore()
        c.circle(w * .22, h * .22, w * .12, M_ACCENT)
        c.rrect(w * .19, h * .15, w * .25, h * .29, w * .012, M_ON)
        c.text(MODEL, w * .1, h * .53, fit_text(MODEL, w * .8, w * .1, "medium"), M_TEXT, "medium", "left")
        c.text("%d%%" % BAT, w * .1, h * .87, w * .22, M_ACCENT, "sans", "left")
        if CHARGING:
            bolt(c, w * .84, h * .2, w * .07, M_ACCENT)
    elif s == "M3_CLOCK":
        c.rrect(0, 0, w, h, w / 2, M_CARD)
        c.text(hh(), w / 2, h * .47, w * .36, M_ACCENT, "medium")
        c.text(mm(), w / 2, h * .84, w * .36, M_TEXT, "medium")
    elif s == "M3_DATE":
        c.circle(w / 2, h / 2, w / 2, M_CARD)
        c.text(NOW.strftime("%a").upper(), w / 2, h * .36, w * .1, M_SUB, "medium")
        c.text(str(NOW.day), w / 2, h * .7, w * .32, M_ACCENT, "medium")
        c.text(NOW.strftime("%b"), w / 2, h * .84, w * .08, M_TEXT, "medium")
    elif s in ("M3_CPU", "M3_MEMORY"):
        v = CPU if s == "M3_CPU" else RAM
        c.rrect(0, 0, w, h, corner, M_CARD)
        gauge(c, (w * .16, h * .16, w * .84, h * .84), v, w * .07, M_FILL, M_ACCENT, -90, 360)
        c.text("%d%%" % v, w / 2, h * .55, w * .17, M_TEXT, "medium")
        c.text("CPU" if s == "M3_CPU" else "RAM", w / 2, h * .68, w * .07, M_SUB, "medium")
    elif s in ("M3_STORAGE", "M3_RAM_BAR"):
        ram = s == "M3_RAM_BAR"
        c.rrect(0, 0, w, h, h * .3, M_CARD)
        c.text("Memory" if ram else "Storage", w * .08, h * .3, h * .13, M_SUB, "medium", "left")
        c.text(("%s / %s" % (mb(RAM_USED_MB), mb(7680))) if ram else "%s free" % gb(DISK_FREE_GB), w * .08, h * .52, h * .17, M_TEXT, "medium", "left")
        bar(c, (w * .08, h * .68, w * .92, h * .82), RAM if ram else DISK_USED, M_FILL, M_ACCENT)
    elif s == "M3_DATE_PILL":
        c.rrect(0, 0, w, h, h / 2, M_CARD)
        c.circle(h / 2, h / 2, h * .38, M_ACCENT)
        c.text(str(NOW.day), h / 2, h * .64, h * .4, M_ON, "medium")
        wd = NOW.strftime("%A")
        c.text(wd, h * 1.04, h * .48, fit_text(wd, w - h * 1.14, h * .2, "medium"), M_TEXT, "medium", "left")
        c.text(NOW.strftime("%B %Y"), h * 1.04, h * .7, h * .12, M_SUB, "medium", "left")
    elif s == "M3_CLOCK_DATE":
        c.rrect(0, 0, w, h, h * .3, M_CARD)
        c.text("%s:%s" % (hh(), mm()), w / 2, h * .62, h * .46, M_ACCENT, "medium")
        c.text(long_date(), w / 2, h * .84, h * .1, M_TEXT, "medium")
    elif s == "M3_BATTERY_RING":
        c.circle(w / 2, h / 2, w / 2, M_CARD)
        gauge(c, (w * .15, h * .15, w * .85, h * .85), BAT, w * .09, M_FILL, M_ACCENT, -90, 360)
        c.text("%d%%" % BAT, w / 2, h * .58, w * .19, M_TEXT, "medium")
    elif s == "M3_TEMP":
        c.rrect(0, 0, w, h, corner, M_CARD)
        pct = max(0, min(100, int((TEMP - 20) / 60 * 100)))
        l, t, r, b = w * .16, h * .14, w * .3, h * .72
        c.rrect(l, t, r, b, (r - l) / 2, M_FILL)
        c.rrect(l + w * .025, b - (b - t) * pct / 100, r - w * .025, b + w * .04, w * .05, M_ACCENT)
        c.circle((l + r) / 2, h * .76, w * .1, M_ACCENT)
        c.text("%d°" % TEMP, w * .86, h * .52, w * .26, M_TEXT, "medium", "right")
        c.text("CPU", w * .86, h * .68, w * .08, M_SUB, "medium", "right")
    elif s == "M3_NETWORK":
        c.rrect(0, 0, w, h, h * .3, M_CARD)
        for i, v in enumerate((DOWN, UP)):
            cx, down = w * (.27 + .46 * i), i == 0
            c.circle(cx, h * .33, h * .17, M_ACCENT if down else M_FILL)
            arrow(c, cx, h * .33, h * .085, down, M_ON if down else M_ACCENT)
            r = rate(v)
            c.text(r, cx, h * .7, fit_text(r, w * .4, h * .15, "medium"), M_TEXT, "medium")
            c.text("Down" if down else "Up", cx, h * .86, h * .09, M_SUB, "medium")
    elif s == "M3_UPTIME":
        c.rrect(0, 0, w, h, corner, M_CARD)
        days, hours = UPTIME_MIN // 1440, UPTIME_MIN // 60 % 24
        c.text("Uptime", w * .12, h * .24, w * .08, M_SUB, "medium", "left")
        c.text("%dd" % days, w * .12, h * .62, w * .3, M_ACCENT, "medium", "left")
        c.text("%dh %dm" % (hours, UPTIME_MIN % 60), w * .12, h * .84, w * .12, M_TEXT, "medium", "left")
    elif s == "M3_OVERVIEW":
        c.rrect(0, 0, w, h, h * .3, M_CARD)
        for i, (n, v) in enumerate((("CPU", CPU), ("RAM", RAM), ("BAT", BAT))):
            cx, cy, r = w * (.2 + .3 * i), h * .42, h * .25
            gauge(c, (cx - r, cy - r, cx + r, cy + r), v, h * .07, M_FILL, M_ACCENT, -90, 360)
            c.text(str(v), cx, cy + h * .06, h * .16, M_TEXT, "medium")
            c.text(n, cx, h * .88, h * .09, M_SUB, "medium")


def long_date():
    return NOW.strftime("%A, ") + str(NOW.day) + NOW.strftime(" %B")


def mb(v):
    return "%.1f GB" % (v / 1024) if v >= 1024 else "%d MB" % v


def rate(b):
    return "%.1f MB/s" % (b / 1048576) if b >= 1 << 20 else "%.0f KB/s" % (b / 1024)


def bar(c, r, pct, track, color):
    l, t, rt, b = r
    k = (b - t) / 2
    c.rrect(l, t, rt, b, k, track)
    if pct > 0:
        c.rrect(l, t, l + max((rt - l) * min(pct, 100) / 100, b - t), b, k, color)


def arrow(c, cx, cy, r, down, color):
    k = 1 if down else -1
    sw = r * .32
    c.line(cx, cy - r * k, cx, cy + r * k, sw, color, True)
    c.line(cx - r * .7, cy + r * .3 * k, cx, cy + r * k, sw, color, True)
    c.line(cx, cy + r * k, cx + r * .7, cy + r * .3 * k, sw, color, True)


def amoled(c, s, w, h):
    r = min(w, h) * .14
    grey = (158, 158, 158)
    c.rrect(0, 0, w, h, r, BLACK)
    c.rrect_stroke(1.5, 1.5, w - 1.5, h - 1.5, r, 3, (40, 40, 40))
    if s == "AM_CLOCK":
        c.text("%s:%s" % (hh(), mm()), w / 2, h * .62, h * .46, WHITE, "thin")
        c.text(NOW.strftime("%A, ") + str(NOW.day) + NOW.strftime(" %B"), w / 2, h * .84, h * .1, grey, "light")
    elif s == "AM_BATTERY":
        l, t, rt, b = w * .2, h * .3, w * .76, h * .58
        c.rrect_stroke(l, t, rt, b, w * .04, w * .012, WHITE)
        c.rrect(rt + w * .015, t + (b - t) * .3, rt + w * .04, b - (b - t) * .3, w * .01, WHITE)
        il, it, ir, ib = l + w * .03, t + w * .03, rt - w * .03, b - w * .03
        c.rrect(il, it, il + (ir - il) * BAT / 100, ib, w * .015, WHITE)
        c.text("%d%%" % BAT, w / 2, h * .82, w * .15, WHITE, "light")
    elif s == "AM_CPU":
        gauge(c, (w * .16, h * .16, w * .84, h * .84), CPU, w * .02, (48, 48, 48), WHITE, -90, 360)
        c.text(str(CPU), w / 2, h * .56, w * .2, WHITE, "thin")
        c.text("CPU %", w / 2, h * .68, w * .06, grey, "light")
    elif s == "AM_DATE":
        c.text(NOW.strftime("%A").upper(), w / 2, h * .3, w * .08, grey, "light")
        c.text(str(NOW.day), w / 2, h * .7, w * .38, WHITE, "thin")
        c.text(NOW.strftime("%B"), w / 2, h * .86, w * .08, WHITE, "light")


def glass(c, s, w, h):
    r = min(w, h) * .14
    a, dim, soft, shade = BLUE, argb(70, 255, 255, 255), argb(220, 255, 255, 255), argb(90, 0, 0, 0)
    c.rrect(2, 2, w - 2, h - 2, r, c.gradient(0, 0, w, h, [argb(80, 255, 255, 255), argb(30, 255, 255, 255)]))
    c.rrect_stroke(2, 2, w - 2, h - 2, r, 3, argb(140, 255, 255, 255))
    if s == "GL_BATTERY":
        c.text("%d%%" % BAT, w / 2, h * .3, w * .17, WHITE, "bold", shadow=argb(90, 0, 0, 0))
        n, bw, gap = 10, w * .05, w * .022
        x = (w - (n * bw + (n - 1) * gap)) / 2
        for i in range(n):
            on = i < (BAT + 5) // 10
            c.rrect(x, h * .4, x + bw, h * .68, bw / 2, a if on else dim)
            x += bw + gap
        c.text("Charging" if CHARGING else "Battery Stable", w / 2, h * .85, w * .07, argb(220, 255, 255, 255), "medium")
    elif s == "GL_CLOCK":
        dim = argb(170, 255, 255, 255)
        c.text(hh(), w / 2, h * .46, w * .34, dim, "thin")
        c.text(mm(), w / 2, h * .86, w * .34, dim, "thin")
        ha, ma = angles()

        def hand(deg, ln, width, color=WHITE):
            t = math.radians(deg)
            c.line(w / 2, h / 2, w / 2 + ln * math.sin(t), h / 2 - ln * math.cos(t), width, color, True)
        hand(ha, w * .22, w * .025, a); hand(ma, w * .36, w * .016)
        c.circle(w / 2, h / 2, w * .025, WHITE)
    elif s == "GL_TIME":
        c.text("%s:%s" % (hh(), mm()), w / 2, h * .62, h * .46, WHITE, "thin")
        c.text(long_date(), w / 2, h * .84, h * .1, a, "light")
    elif s == "GL_DATE":
        c.text(NOW.strftime("%a").upper(), w / 2, h * .36, w * .1, soft, "medium")
        c.text(str(NOW.day), w / 2, h * .7, w * .32, a, "medium")
        c.text(NOW.strftime("%b"), w / 2, h * .84, w * .08, WHITE, "medium")
    elif s in ("GL_BATTERY_RING", "GL_CPU", "GL_MEMORY"):
        v = {"GL_CPU": CPU, "GL_MEMORY": RAM}.get(s, BAT)
        box = (w * .17, h * .17, w * .83, h * .83)
        if s == "GL_BATTERY_RING":
            gauge(c, box, v, w * .075, dim, a, -90, 360)
        else:
            gauge(c, box, v, w * .075, dim, a)
        c.text("%d%%" % v, w / 2, h * .56, w * .17, WHITE, "bold", shadow=shade)
        c.text({"GL_CPU": "CPU", "GL_MEMORY": "Memory"}.get(s, "Charging" if CHARGING else "Battery"), w / 2, h * .69, w * .07, soft, "medium")
    elif s == "GL_STORAGE":
        c.text("Storage", w * .08, h * .28, h * .12, soft, "medium", "left")
        c.text("%d%%" % DISK_USED, w * .92, h * .28, h * .12, a, "bold", "right")
        c.text("%s free" % gb(DISK_FREE_GB), w * .08, h * .56, h * .2, WHITE, "bold", "left", shadow=shade)
        bar(c, (w * .08, h * .68, w * .92, h * .8), DISK_USED, dim, a)


def ios(c, s, w, h):
    a, grey, track = BLUE, (142, 142, 147), (58, 58, 62)
    c.rrect(0, 0, w, h, min(w, h) * .22, (28, 28, 30))
    if s == "IOS_CLOCK_DATE":
        c.text("%s:%s" % (hh(), mm()), w / 2, h * .62, h * .46, WHITE, "medium")
        c.text(long_date(), w / 2, h * .84, h * .1, a, "medium")
    elif s == "IOS_RINGS":
        for i, v in enumerate((BAT, CPU, RAM)):
            inset = w * (.1 + .1 * i)
            col = tuple(round(x + (255 - x) * .3 * i) for x in a)
            gauge(c, (inset, inset, w - inset, h - inset), v, w * .08, col + (60,), col, -90, 360)
        c.text("%d%%" % BAT, w / 2, h * .54, w * .1, WHITE, "bold")
    elif s == "IOS_STORAGE":
        c.text("Storage", w * .1, h * .2, w * .075, grey, "bold", "left")
        c.text(gb(DISK_FREE_GB), w * .1, h * .43, w * .15, WHITE, "bold", "left")
        c.text("available", w * .1, h * .54, w * .07, grey, "sans", "left")
        bar(c, (w * .1, h * .66, w * .9, h * .78), DISK_USED, track, a)
        c.text("%d%% used" % DISK_USED, w * .1, h * .9, w * .065, grey, "sans", "left")
    elif s == "IOS_MEMORY":
        c.text("Memory", w * .1, h * .2, w * .075, grey, "bold", "left")
        gauge(c, (w * .22, h * .3, w * .78, h * .86), RAM, w * .07, track, a)
        c.text("%d%%" % RAM, w / 2, h * .62, w * .13, WHITE, "bold")
        c.text(mb(RAM_USED_MB), w / 2, h * .73, w * .06, grey, "sans")
    elif s == "IOS_UP_NEXT":
        c.text(NOW.strftime("%A").upper(), w * .1, h * .18, w * .065, a, "bold", "left")
        c.text(str(NOW.day), w * .1, h * .44, w * .24, WHITE, "sans", "left")
        for i in range(1, 4):
            day = NOW + datetime.timedelta(days=i)
            y = h * (.5 + .14 * i)
            c.rrect(w * .1, y - w * .06, w * .115, y + w * .012, w * .008, a)
            c.text("Tomorrow" if i == 1 else day.strftime("%A"), w * .15, y, w * .065, WHITE, "bold", "left")
            c.text(str(day.day), w * .9, y, w * .065, grey, "sans", "right")
    elif s == "IOS_WORLD":
        # sample: Istanbul (UTC+3) and three cities in October
        for i, (name, off) in enumerate((("Istanbul", 0), ("London", -2), ("New York", -7), ("Tokyo", 6))):
            cx, cy, cr = w * (.14 + .24 * i), h * .4, w * .095
            t = NOW + datetime.timedelta(hours=off)
            day = 6 <= t.hour <= 17
            c.circle(cx, cy, cr, WHITE if day else (60, 60, 64))
            hm = ((t.hour % 12) * 30 + t.minute / 2, t.minute * 6)
            analog(c, cx, cy, cr, grey if day else (120, 120, 126), BLACK if day else WHITE, minute_w=cr * .07, hm=hm)
            c.circle(cx, cy, cr * .06, a)
            c.text(name, cx, h * .78, fit_text(name, w * .22, h * .085, "bold"), WHITE, "bold")
            c.text("Local" if i == 0 else "%+dh" % off, cx, h * .91, h * .07, grey, "sans")


# ---------------------------------------------------------------- the Skin enum: name, family, w, h

SKINS = [
    ("W7_CLOCK_TRAD", win7, 390, 390), ("W7_CLOCK_SYSTEM", win7, 390, 390), ("W7_CLOCK_CRONO", win7, 390, 390),
    ("W7_CLOCK_DINER", win7, 390, 390), ("W7_CLOCK_FLOWER", win7, 390, 390), ("W7_CLOCK_MODERN", win7, 390, 390),
    ("W7_CLOCK_SQUARE", win7, 390, 390), ("W7_CLOCK_NOVELTY", win7, 390, 390), ("W7_CLOCK_MONITOR", win7, 390, 390),
    ("W7_CPU", win7, 396, 318), ("W7_CALENDAR", win7, 390, 423), ("W7_BATTERY", win7, 405, 240), ("W7_DRIVE", win7, 468, 144),
    ("W11_CLOCK", win11, 360, 360), ("W11_DATE", win11, 360, 360), ("W11_CPU", win11, 360, 360), ("W11_MEMORY", win11, 360, 360),
    ("W11_BATTERY", win11, 360, 360), ("W11_STORAGE", win11, 360, 360), ("W11_CALENDAR", win11, 360, 360), ("W11_TIME", win11, 360, 360),
    ("XP_CLOCK", xp, 360, 390), ("XP_CPU", xp, 360, 390), ("XP_BATTERY", xp, 480, 270), ("XP_DISK", xp, 480, 330),
    ("W95_TIME", win95, 360, 360), ("W95_CPU", win95, 420, 216), ("W95_BATTERY", win95, 420, 216), ("W95_MEMORY", win95, 420, 216),
    ("W95_DATE", win95, 420, 360),
    ("M3_BATTERY", material, 360, 360), ("M3_CLOCK", material, 360, 360), ("M3_DATE", material, 360, 360), ("M3_CPU", material, 360, 360),
    ("M3_STORAGE", material, 480, 240), ("M3_MEMORY", material, 360, 360),
    ("AM_CLOCK", amoled, 480, 240), ("AM_BATTERY", amoled, 360, 360), ("AM_CPU", amoled, 360, 360), ("AM_DATE", amoled, 360, 360),
    ("GL_BATTERY", glass, 360, 360), ("GL_CLOCK", glass, 360, 360),
    ("M3_DATE_PILL", material, 480, 240), ("M3_CLOCK_DATE", material, 480, 240), ("M3_BATTERY_RING", material, 360, 360),
    ("M3_TEMP", material, 360, 360), ("M3_RAM_BAR", material, 480, 240), ("M3_NETWORK", material, 480, 240),
    ("M3_UPTIME", material, 360, 360), ("M3_OVERVIEW", material, 480, 240),
    ("GL_TIME", glass, 480, 240), ("GL_DATE", glass, 360, 360), ("GL_BATTERY_RING", glass, 360, 360),
    ("GL_CPU", glass, 360, 360), ("GL_MEMORY", glass, 360, 360), ("GL_STORAGE", glass, 480, 240),
    ("IOS_CLOCK_DATE", ios, 480, 240), ("IOS_RINGS", ios, 360, 360), ("IOS_STORAGE", ios, 360, 360),
    ("IOS_MEMORY", ios, 360, 360), ("IOS_UP_NEXT", ios, 360, 360), ("IOS_WORLD", ios, 480, 240),
]


# ---------------------------------------------------------------- the 20 text widgets (port of TextTile in widgets/TextTiles.kt)

def vector_mask(name, size):
    """Fills an Android vector drawable's paths (M L H V C S Q T A Z) into a size x size L mask (even-odd)."""
    import re
    import xml.etree.ElementTree as ET
    root = ET.parse(os.path.join(RES, "drawable", name + ".xml")).getroot()
    A = "{http://schemas.android.com/apk/res/android}"
    vw = float(root.get(A + "viewportWidth")); vh = float(root.get(A + "viewportHeight"))
    ss = 8
    m = Image.new("L", (size * ss, size * ss), 0)
    for el in root.iter("path"):
        toks = re.findall(r"[A-Za-z]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", el.get(A + "pathData"))
        polys, cur = [], []
        x = y = sx = sy = 0.0; cx2 = cy2 = None; i = 0; cmd = None; prev = None

        def num():
            nonlocal i
            v = float(toks[i]); i += 1
            return v

        def cubic(x0, y0, x1, y1, x2, y2, x3, y3):
            for k in range(1, 13):
                t = k / 12; u = 1 - t
                cur.append((u**3*x0 + 3*u*u*t*x1 + 3*u*t*t*x2 + t**3*x3, u**3*y0 + 3*u*u*t*y1 + 3*u*t*t*y2 + t**3*y3))

        def arc(x0, y0, rx, ry, phi, fa, fs, x1, y1):
            if rx == 0 or ry == 0:
                cur.append((x1, y1)); return
            ph = math.radians(phi); co, si = math.cos(ph), math.sin(ph)
            dx, dy = (x0 - x1) / 2, (y0 - y1) / 2
            xp, yp = co*dx + si*dy, -si*dx + co*dy
            rx, ry = abs(rx), abs(ry)
            lam = xp*xp/(rx*rx) + yp*yp/(ry*ry)
            if lam > 1: rx *= math.sqrt(lam); ry *= math.sqrt(lam)
            num_ = rx*rx*ry*ry - rx*rx*yp*yp - ry*ry*xp*xp
            c_ = math.sqrt(max(0, num_ / (rx*rx*yp*yp + ry*ry*xp*xp)))
            if fa == fs: c_ = -c_
            cxp, cyp = c_*rx*yp/ry, -c_*ry*xp/rx
            ccx = co*cxp - si*cyp + (x0 + x1)/2; ccy = si*cxp + co*cyp + (y0 + y1)/2
            def ang(ux, uy, vx, vy):
                a = math.atan2(ux*vy - uy*vx, ux*vx + uy*vy); return a
            t1 = ang(1, 0, (xp - cxp)/rx, (yp - cyp)/ry)
            dt = ang((xp - cxp)/rx, (yp - cyp)/ry, (-xp - cxp)/rx, (-yp - cyp)/ry)
            if not fs and dt > 0: dt -= 2*math.pi
            elif fs and dt < 0: dt += 2*math.pi
            n = max(4, int(abs(dt) * 8))
            for k in range(1, n + 1):
                t = t1 + dt * k / n
                cur.append((ccx + rx*math.cos(t)*co - ry*math.sin(t)*si, ccy + rx*math.cos(t)*si + ry*math.sin(t)*co))
        while i < len(toks):
            if toks[i].isalpha():
                cmd = toks[i]; i += 1
            elif cmd in ("M", "m"):
                cmd = "L" if cmd == "M" else "l"
            rel = cmd.islower(); C = cmd.upper(); ox, oy = (x, y) if rel else (0.0, 0.0)
            if C == "Z":
                if cur: polys.append(cur); cur = []
                x, y = sx, sy; prev = C; continue
            if C == "M":
                if cur: polys.append(cur)
                x, y = num() + ox, num() + oy; sx, sy = x, y; cur = [(x, y)]
            elif C == "L":
                x, y = num() + ox, num() + oy; cur.append((x, y))
            elif C == "H":
                x = num() + (x if rel else 0); cur.append((x, y))
            elif C == "V":
                y = num() + (y if rel else 0); cur.append((x, y))
            elif C in ("C", "S"):
                if C == "C":
                    x1, y1 = num() + ox, num() + oy
                else:
                    x1, y1 = (2*x - cx2, 2*y - cy2) if prev in ("C", "S") else (x, y)
                x2, y2, x3, y3 = num() + ox, num() + oy, num() + ox, num() + oy
                cubic(x, y, x1, y1, x2, y2, x3, y3); cx2, cy2 = x2, y2; x, y = x3, y3
            elif C in ("Q", "T"):
                if C == "Q":
                    x1, y1 = num() + ox, num() + oy
                else:
                    x1, y1 = (2*x - cx2, 2*y - cy2) if prev in ("Q", "T") else (x, y)
                x3, y3 = num() + ox, num() + oy
                cubic(x, y, x + 2/3*(x1 - x), y + 2/3*(y1 - y), x3 + 2/3*(x1 - x3), y3 + 2/3*(y1 - y3), x3, y3); cx2, cy2 = x1, y1; x, y = x3, y3
            elif C == "A":
                rx, ry, phi, fa, fs = num(), num(), num(), num(), num()
                x3, y3 = num() + ox, num() + oy
                arc(x, y, rx, ry, phi, int(fa), int(fs), x3, y3); x, y = x3, y3
            prev = C
        if cur: polys.append(cur)
        for pl in polys:
            if len(pl) < 3: continue
            pm = Image.new("L", m.size, 0)
            ImageDraw.Draw(pm).polygon([(px / vw * size * ss, py / vh * size * ss) for px, py in pl], fill=255)
            m = ImageChops.logical_xor(m.convert("1"), pm.convert("1")).convert("L")
    return m.resize((size, size), Image.LANCZOS)


def blend(a, b, f):
    return tuple(round(x + (y - x) * f) for x, y in zip(a[:3], b[:3]))


def lum(c):
    def ch(v):
        v /= 255
        return v / 12.92 if v <= .03928 else ((v + .055) / 1.055) ** 2.4
    return .2126 * ch(c[0]) + .7152 * ch(c[1]) + .0722 * ch(c[2])


def readable(c):
    return blend(c, WHITE, .45) if lum(c) < .25 else c


def text_fit(c, s, x, y, size, color, face, maxw, minsize):
    while measure(s, size, face) > maxw and size > minsize:
        size -= .5
    while measure(s, size, face) > maxw and len(s) > 1:
        s = s[:-2] + "…"
    c.text(s, x, y, size, color, face, "left")


def text_tile(style, icon_name, title, t, W, H, d, accent=(0, 103, 192)):
    """TextTile.draw, Pillow edition: same rules, sample data. t = dict(big, sub, frac, series, series2, max, bars, lit, tone)."""
    crisp = style == "WIN95"
    c = Canvas(int(W), int(H), ss=1 if crisp else 2, aa=not crisp)
    win11 = style in ("WIN11", "WIN11_DARK"); xp = style == "XP"; w95 = style == "WIN95"; strip = xp or w95
    w10 = style == "WIN10"; w7 = style == "WIN7"; mat = style == "MATERIAL"; amo = style == "AMOLED"
    app = accent
    w10bg = accent; w10light = w10 and lum(w10bg) > .55
    mat_card, mat_chip, mat_on = blend(app, WHITE, .84), blend(app, WHITE, .5), blend(app, BLACK, .68)
    if win11:
        c.rrect(0, 0, W, H, 12 * d, argb(158, 10, 10, 10))
        c.rrect_stroke(.75, .75, W - .75, H - .75, 12 * d, 1.5, argb(60, 255, 255, 255))
    elif w10:
        c.rect(0, 0, W, H, w10bg)
    elif mat:
        c.rrect(0, 0, W, H, 24 * d, mat_card)
    elif amo:
        c.rrect(0, 0, W, H, 24 * d, BLACK)
        c.rrect_stroke(d * .5, d * .5, W - d * .5, H - d * .5, 24 * d, d, app[:3] + (70,))
    elif w7:
        c.rrect(0, 0, W, H, 10 * d, c.gradient(0, 0, 0, H, [(46, 58, 68), (12, 18, 24)]))
        c.rrect_stroke(.5 * d, .5 * d, W - .5 * d, H - .5 * d, 10 * d, d, argb(102, 255, 255, 255))
    elif xp:
        c.rrect(0, 0, W, H, 6 * d, (236, 233, 216))
        c.rrect_stroke(d, d, W - d, H - d, 6 * d, 2 * d, (0, 84, 227))
    else:
        c.rect(0, 0, W, H, BLACK); c.rect(0, 0, W - d, H - d, WHITE); c.rect(d, d, W - d, H - d, (128, 128, 128))
        c.rect(d, d, W - 2 * d, H - 2 * d, (223, 223, 223)); c.rect(2 * d, 2 * d, W - 2 * d, H - 2 * d, (192, 192, 192))
    fg = mat_on if mat else BLACK if (xp or w95 or w10light) else WHITE
    if mat: fg2 = blend(mat_on, mat_card, .3)
    elif xp: fg2 = (60, 60, 60)
    elif w95: fg2 = (32, 32, 32)
    elif w10: fg2 = fg[:3] + (210,)
    elif win11: fg2 = (200, 200, 200)
    elif w7: fg2 = (207, 232, 255)
    else: fg2 = (158, 158, 158)
    if win11: acc = blend(app, WHITE, .45) if lum(app) <= .4 else app
    elif xp: acc = (45, 181, 45)
    elif w95: acc = (0, 0, 128)
    elif w10: acc = fg
    elif w7: acc = (92, 200, 255)
    elif mat: acc = blend(app, BLACK, .25)
    else: acc = readable(app)
    fill = t.get("tone") or acc
    if mat: track = mat_chip + (150,)
    elif strip: track = WHITE
    elif w10: track = fg[:3] + (60,)
    else: track = argb(56, 255, 255, 255)
    vface = "light" if (w10 or amo) else "medium" if (win11 or mat) else "bold"

    def icon(x0, y0, sz, col):   # noqa: the vector glyph tinted [col]
        m = vector_mask(icon_name, max(1, round(c.d(sz))))
        layer = Image.new("L", c.img.size, 0); layer.paste(m, (round(c.d(x0)), round(c.d(y0))))
        c._fill(layer, col)
    compact = W < 190 * d or W < H * 1.5
    pad = (6 if w95 else 8 if xp else 12 if compact else 14) * d
    L, R = pad, W - pad
    bottom = H - pad
    if strip:
        hh_ = (18 if w95 else 22) * d
        r = (2 * d, 2 * d, W - 2 * d, 2 * d + hh_) if xp else (3 * d, 3 * d, W - 3 * d, 3 * d + hh_)
        if xp: c.rrect(*r, 4 * d, c.gradient(0, r[1], 0, r[3], [(58, 147, 255), (0, 88, 238)]))
        else: c.rect(*r, c.gradient(r[0], 0, r[2], 0, [(0, 0, 128), (16, 132, 208)]))
        isz = hh_ * .7; cy = (r[1] + r[3]) / 2
        icon(r[0] + 4 * d, cy - isz / 2, isz, WHITE)
        text_fit(c, title, r[0] + 8 * d + isz, cy + hh_ * .56 * .36, hh_ * .56, WHITE, "bold", r[2] - r[0] - 12 * d - isz, 9 * d)
        top = r[3] + (6 if w95 else 8) * d
    elif w10:
        icon(L, pad, 18 * d, fg)
        text_fit(c, title, L, H - pad, 12 * d, fg, "sans", R - L, 9 * d)
        top = pad + 26 * d; bottom = H - pad - 20 * d
    else:
        hs = (22 if compact else 24) * d
        if mat:
            c.circle(L + hs / 2, pad + hs / 2, hs / 2, mat_chip)
            icon(L + hs * .2, pad + hs * .2, hs * .6, mat_on)
        else:
            icon(L, pad + hs * .1, hs * .8, acc)
        tx = L + hs + 6 * d
        text_fit(c, title, tx, pad + hs / 2 + 12.5 * d * .36, 12.5 * d, fg2, "medium", R - tx, 9 * d)
        top = pad + hs + (4 if compact else 6) * d
    series, bars = t.get("series", []), t.get("bars", [])
    has_graph = len(series) >= 2 or bool(bars)
    frac = t.get("frac")
    ring = not strip and not compact and frac is not None and not has_graph
    panel = (L + (R - L) * .6, top, R, bottom) if (not compact and (has_graph or ring)) else None
    left_r = panel[0] - 10 * d if panel else R
    bh = (12 if xp else 14 if w95 else 8 if mat else 6 if w7 else 3 if amo else 4) * d
    show_bar = frac is not None and not ring
    bar_r = (L, bottom - bh, left_r, bottom) if show_bar else None
    mini = (L, bottom - min(26 * d, (bottom - top) * .35), R, bottom) if (compact and has_graph and not show_bar) else None
    text_bottom = (bar_r[1] if bar_r else mini[1] if mini else bottom) - 6 * d
    avail = max(12 * d, text_bottom - top)
    line_h = 15 * d
    subs = t.get("sub", [])[:1 if compact else 2]
    subs = subs[:max(0, int(avail * .45 / line_h))]
    vs = max(12 * d, min((avail - len(subs) * line_h) * .9, (34 if compact else 42) * d))
    if w95:
        fr = (L, top, left_r, top + vs * 1.15)
        c.rect(*fr, WHITE); c.rect(fr[0], fr[1], fr[2] - d, fr[3] - d, (128, 128, 128)); c.rect(fr[0] + d, fr[1] + d, fr[2] - 2 * d, fr[3] - 2 * d, (64, 64, 64)); c.rect(fr[0] + 2 * d, fr[1] + 2 * d, fr[2] - 2 * d, fr[3] - 2 * d, WHITE)
        text_fit(c, t["big"], L + 5 * d, fr[3] - vs * .28, vs * .8, fg, vface, left_r - L - 10 * d, 9 * d)
        y = fr[3]
    else:
        y = top + vs * .82
        text_fit(c, t["big"], L - (d if (w10 or amo) else 0), y, vs, fg, vface, left_r - L, 9 * d)
        y += vs * .12
    for line in subs:
        y += line_h
        text_fit(c, line, L, y - 3 * d, 12 * d, fg2, "sans", left_r - L, 9 * d)
    g = panel if (panel and has_graph) else mini
    if g:
        line_c = (0, 255, 0) if strip else fill
        if strip:
            c.rect(*g, BLACK)
            xx = g[0]
            while xx < g[2]: c.line(xx, g[1], xx, g[3], 1, (0, 128, 64)); xx += 10 * d
            yy = g[1]
            while yy < g[3]: c.line(g[0], yy, g[2], yy, 1, (0, 128, 64)); yy += 10 * d
        if bars:
            n = len(bars); gap = (2 if n > 6 else 4) * d; bw = (g[2] - g[0] - gap * (n - 1)) / n
            sig = t.get("lit", -1) >= 0
            for i, v in enumerate(bars):
                x = g[0] + i * (bw + gap); rr = min(bw / 2, 3 * d)
                full = (x, g[3] - (g[3] - g[1]) * v if sig else g[1], x + bw, g[3])
                if not strip: c.rrect(*full, rr, track)
                if sig:
                    if i < t["lit"]: c.rrect(*full, rr, line_c)
                else:
                    c.rrect(x, g[3] - (g[3] - g[1]) * max(.04, v), x + bw, g[3], rr, line_c)
        else:
            mx = t.get("max") or max([1] + series + t.get("series2", []))
            c.clip_rect(*g)
            if not strip: c.line(g[0], g[3] - d / 2, g[2], g[3] - d / 2, d, track)

            def curve(v, col, area):
                if len(v) < 2: return
                pts = [(g[0] + (g[2] - g[0]) * i / (len(v) - 1), g[3] - 2 * d - (g[3] - g[1] - 4 * d) * min(1, max(0, y0 / mx))) for i, y0 in enumerate(v)]
                if area:
                    c.polygon(pts + [(g[2], g[3]), (g[0], g[3])], c.gradient(0, g[1], 0, g[3], [col[:3] + ((70 if strip else 120),), col[:3] + (0,)]))
                for a, b in zip(pts, pts[1:]):
                    c.line(a[0], a[1], b[0], b[1], (1.5 if strip else 2) * d, col, round_cap=True)
            curve(t.get("series2", []), (255, 255, 0) if strip else fg2[:3] + (170,), False)
            curve(series, line_c, True)
            c.restore()
    if ring and panel:
        f = min(1, max(0, frac)); sz = min(panel[2] - panel[0], panel[3] - panel[1]); sw = sz * .1
        pcx, pcy = (panel[0] + panel[2]) / 2, (panel[1] + panel[3]) / 2
        rr = (pcx - sz / 2 + sw / 2, pcy - sz / 2 + sw / 2, pcx + sz / 2 - sw / 2, pcy + sz / 2 - sw / 2)
        c.arc(*rr, 0, 360, sw, track)
        c.arc(*rr, -90, 360 * f, sw, fill, round_cap=not w10)
        c.text("%d%%" % (f * 100), pcx, pcy + sz * .24 * .35, sz * .24, fg, vface)
    if bar_r:
        v = min(1, max(0, frac))
        if strip:
            if xp:
                c.rrect(*bar_r, 3 * d, WHITE); c.rrect_stroke(*bar_r, 3 * d, d, (127, 157, 185))
            else:
                c.rect(*bar_r, WHITE); c.line(bar_r[0], bar_r[1], bar_r[2], bar_r[1], d, (128, 128, 128)); c.line(bar_r[0], bar_r[1], bar_r[0], bar_r[3], d, (128, 128, 128))
            bw = (7 if xp else 9) * d; gap = 2 * d
            inner = (bar_r[0] + 2 * d, bar_r[1] + 2 * d, bar_r[2] - 2 * d, bar_r[3] - 2 * d)
            n = int((inner[2] - inner[0]) * v / (bw + gap))
            pf = c.gradient(0, inner[1], 0, inner[3], [blend(fill, WHITE, .55), fill, blend(fill, BLACK, .1)], [0, .5, 1]) if xp else fill
            for i in range(n):
                c.rect(inner[0] + i * (bw + gap), inner[1], inner[0] + i * (bw + gap) + bw, inner[3], pf)
        elif w7:
            rr = (bar_r[3] - bar_r[1]) / 2
            c.rrect(*bar_r, rr, argb(102, 0, 0, 0))
            fx = max(bar_r[0] + (bar_r[3] - bar_r[1]), bar_r[0] + (bar_r[2] - bar_r[0]) * v)
            c.rrect(bar_r[0], bar_r[1], fx, bar_r[3], rr, c.gradient(0, bar_r[1], 0, bar_r[3], [blend(fill, WHITE, .5), fill]))
            c.rrect_stroke(*bar_r, rr, d * .8, argb(85, 255, 255, 255))
        else:
            rr = 0 if w10 else (bar_r[3] - bar_r[1]) / 2
            c.rrect(*bar_r, rr, track)
            if v > 0: c.rrect(bar_r[0], bar_r[1], max(bar_r[0] + (bar_r[3] - bar_r[1]), bar_r[0] + (bar_r[2] - bar_r[0]) * v), bar_r[3], rr, fill)
    return c.result()


_wave = lambda base, amp, n=40, ph=0: [base + amp * math.sin(i * .45 + ph) + amp * .5 * math.sin(i * 1.3 + ph) for i in range(n)]
TEXT_WIDGETS = [   # provider xml / icon name, title, sample tile
    ("usage", "CPU and memory", dict(big="CPU 23%", sub=["RAM 58% · 4.6 GB / 8 GB", "CPU 47°C"], frac=.58, series=_wave(28, 12), series2=_wave(58, 3, ph=1), max=100)),
    ("battery", "Battery", dict(big="76%", sub=["On battery", "4.12 V · 420 mA · 31°C"], frac=.76, series=[90 - i * .35 for i in range(40)], max=100)),
    ("storage", "Storage", dict(big="40 GB", sub=["free of 128 GB", "69% used · 88 GB"], frac=.69)),
    ("downloads", "Downloads", dict(big="2.1 MB/s", sub=["1 active", "Last: video.mp4"], frac=1.0, series=[abs(v) for v in _wave(2, 1.2)])),
    ("memory", "Memory", dict(big="4.6 GB", sub=["of 8 GB · 58%", "Swap 0.5 GB / 4 GB"], frac=.58, series=_wave(58, 4), max=100)),
    ("temp", "Temperature", dict(big="47°C", sub=["CPU", "Battery 31°C"], frac=.47, series=_wave(47, 5), max=100)),
    ("network", "Network", dict(big="↓ 1.4 MB/s", sub=["↑ 96 KB/s", "Total ↓ 3.2 GB ↑ 0.4 GB"], series=[abs(v) for v in _wave(1.4, 1)], series2=[abs(v) * .3 for v in _wave(.3, .3, ph=2)])),
    ("clock", "Wed, 14 Oct", dict(big="10:09", sub=["Wednesday", "Week 42 · day 287"], frac=.42)),
    ("uptime", "Uptime", dict(big="3d 05h 12m", sub=["Booted Sun 11 Oct, 04:57", "Next day in 18h 48m"], frac=.22)),
    ("storagebar", "Storage free", dict(big="31%", sub=["free", "40 GB of 128 GB"], frac=.31)),
    ("ramfree", "RAM free", dict(big="3.4 GB", sub=["free of 8 GB", "42% available"], frac=.42)),
    ("cores", "Cores online", dict(big="8", sub=["cores online", "CPU 23% · up to 2850 MHz"], bars=[.35, .2, .6, .15, .4, .8, .25, .1])),
    ("brightness", "Brightness", dict(big="62%", sub=["Automatic", "level 158 / 255"], frac=.62)),
    ("wifi", "Wi-Fi network", dict(big="Home", sub=["Wi-Fi 6 · 5.2 GHz", "866 Mbps · -52 dBm"], bars=[.3, .55, .8, 1], lit=4)),
    ("signal", "Mobile signal", dict(big="4G LTE", sub=["Carrier", "-89 dBm"], bars=[.3, .55, .8, 1], lit=3)),
    ("batthealth", "Battery health", dict(big="Good", sub=["312 cycles", "Capacity 91% of design (4100 mAh)"], frac=.91)),
    ("apps", "Apps", dict(big="214", sub=["96 user", "118 system"], frac=.45)),
    ("lastdl", "Last download", dict(big="video.mp4", sub=["Nothing downloading"])),
    ("alarm", "Next alarm", dict(big="Thu 07:30", sub=["in 21 h 21 min"], frac=.9)),
    ("quick", "Quick action", dict(big="Toolkit", sub=["Tools, cleaners, shell"])),
]
TEXT_STYLES = ["MATERIAL", "AMOLED", "WIN11", "WIN11_DARK", "WIN10", "WIN7", "WIN95", "XP"]


def text_previews(sheet=None):
    """preview_widget_<name>.png in the default Material look at the 4 x 2 size (250 x 110 dp at d = 1.6); [sheet] also writes every look side by side."""
    d = 1.6
    for name, title, t in TEXT_WIDGETS:
        img = text_tile("MATERIAL", "wi_" + name, title, t, 250 * d, 110 * d, d)
        out = "preview_widget_" + name
        assert "@drawable/" + out + '"' in open(os.path.join(RES, "xml", "widget_%s.xml" % name)).read(), name
        save(img, out)
        print(out, img.size)
    if sheet:
        rows = []
        for name, title, t in TEXT_WIDGETS[:6] + TEXT_WIDGETS[11:15]:
            row = [text_tile(s, "wi_" + name, title, t, 250 * 2, 110 * 2, 2) for s in TEXT_STYLES]
            row.append(text_tile("MATERIAL", "wi_" + name, title, t, 110 * 2, 110 * 2, 2))
            row.append(text_tile("WIN11", "wi_" + name, title, t, 110 * 2, 110 * 2, 2))
            rows.append(row)
        W = sum(i.width + 12 for i in rows[0]); H = sum(r[0].height + 12 for r in rows)
        out = Image.new("RGBA", (W, H), (70, 110, 90, 255)); y = 0
        for r in rows:
            x = 0
            for i in r:
                out.alpha_composite(i, (x, y)); x += i.width + 12
            y += r[0].height + 12
        out.convert("RGB").save(sheet)


def save(img, out):
    """Writes res/drawable-nodpi/<out>.webp (lossy, alpha kept) and drops an older .png of the same name, which would clash."""
    img.save(os.path.join(ART, out + ".webp"), "WEBP", quality=88, method=6)
    png = os.path.join(ART, out + ".png")
    if os.path.exists(png):
        os.remove(png)


def main():
    assert NOW.strftime("%A") == "Wednesday"
    face = 0
    only = set(sys.argv[1:])   # optional skin names: redraw just those
    for name, fn, w, h in SKINS:
        if only and name not in only:
            continue
        crisp = fn is win95
        c = Canvas(w, h, ss=1 if crisp else 2, aa=not crisp)
        fn(c, name, w, h)
        img = c.result()
        assert img.size == (w, h)
        if name in CLOCKS:   # the gadget clocks keep their old providers and preview_face_N
            face += 1
            out, xml = "preview_face_%d" % face, "widget_face_%d.xml" % face
        else:
            out, xml = "preview_skin_" + name.lower(), "widget_skin_%s.xml" % name.lower()
        # every preview must be the one its provider XML names
        assert "@drawable/" + out + '"' in open(os.path.join(RES, "xml", xml)).read(), xml
        save(img, out)
        print(out, w, h)
        if name == "W11_CLOCK":   # the frosted clock (face 17) is the same Windows 11 tile and dial now
            save(img, "preview_face_17")
    if not only or "TEXT" in only:
        text_previews(os.environ.get("SHEET"))


if __name__ == "__main__":
    main()
