"""Builds the live analog clock widget art: clk_<skin>_dial/_hour/_minute[/_second][/_cap].png in drawable-nodpi.

AnalogClock centres every drawable on the view centre at its intrinsic size (scaling all of them down together),
so each hand is a full-frame image the size of the dial with the hand pointing at twelve around the image centre.
The drawn faces copy the bitmap designs in widgets/ThemeWidgets.kt. Run: python3 -I tools/gen_clock_assets.py
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable-nodpi")
FONTS = "/usr/share/fonts/truetype/dejavu/"
SS = 4  # supersampling for the drawn faces


def font(size, bold=False):
    return ImageFont.truetype(FONTS + ("DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"), max(1, round(size)))


def save(img, skin, part):
    path = os.path.join(RES, f"clk_{skin}_{part}.png")
    img.save(path, optimize=True)
    return path


# ---------------------------------------------------------------- Windows 7 gadgets: the real art upscaled 4x

GADGETS = {  # skin: (face, left, top, pw, ph) as in the `clocks` map of ThemeWidgets.kt
    "w7_clock_trad": ("trad", 57, -1, 13, 129),
    "w7_clock_system": ("system", 58, 0, 13, 129),
    "w7_clock_crono": ("cronometer", 57, -1, 13, 129),
    "w7_clock_diner": ("diner", 58, -1, 13, 129),
    "w7_clock_flower": ("flower", 59, 0, 13, 129),
    "w7_clock_modern": ("modern", 58, -1, 13, 129),
    "w7_clock_square": ("square", 57, -1, 13, 129),
    "w7_clock_novelty": ("novelty", 59, 46, 7, 81),
    "w7_clock_monitor": ("monitor", 57, -1, 13, 129),
}


def gadget(skin, face, left, top, pw, ph):
    k = 4
    px, py = left + pw / 2, top + ph / 2  # the hands' pivot in the 130 frame becomes the image centre
    half = max(px, 130 - px, py, 130 - py)
    s = round(2 * half * k)
    ox, oy = round(s / 2 - px * k), round(s / 2 - py * k)

    def layer(*items):
        im = Image.new("RGBA", (s, s))
        for name, x, y, w, h in items:
            src = Image.open(os.path.join(RES, f"gadget_clk_{name}.png")).convert("RGBA")
            im.alpha_composite(src.resize((round(w * k), round(h * k)), Image.LANCZOS), (ox + round(x * k), oy + round(y * k)))
        return im

    hand = lambda n: layer((f"{face}_{n}", left, top, pw, ph))
    save(layer((face, 0, 0, 130, 130)), skin, "dial")  # the _top overlay is left out: it shows as a grey box
    save(hand("h"), skin, "hour")
    save(hand("m"), skin, "minute")
    save(hand("s"), skin, "second")
    save(layer((f"{face}_dot", left, top, pw, ph), ("highlights", 3, 3, 124, 124)), skin, "cap")
    assert Image.open(os.path.join(RES, f"clk_{skin}_dial.png")).size == (s, s)


# ---------------------------------------------------------------- drawing helpers for the drawn faces (design units, scaled by f)

class Pen:
    def __init__(self, w, h, f):
        self.f = f * SS
        self.im = Image.new("RGBA", (round(w * self.f), round(h * self.f)))
        self.d = ImageDraw.Draw(self.im)

    def p(self, *v):
        return [x * self.f for x in v]

    def rect(self, x0, y0, x1, y1, col, radius=0):
        if radius:
            self.d.rounded_rectangle(self.p(x0, y0, x1 - 1e-3, y1 - 1e-3), radius * self.f, fill=col)
        else:
            self.d.rectangle(self.p(x0, y0, x1, y1), fill=col)

    def circle(self, cx, cy, r, col):
        self.d.ellipse(self.p(cx - r, cy - r, cx + r, cy + r), fill=col)

    def line(self, x0, y0, x1, y1, w, col, round_cap=True):
        self.d.line(self.p(x0, y0, x1, y1), fill=col, width=max(1, round(w * self.f)))
        if round_cap:
            for x, y in ((x0, y0), (x1, y1)):
                self.circle(x, y, w / 2, col)

    def text(self, s, x, y, size, col, bold=False, anchor="ms"):
        self.d.text((x * self.f, y * self.f), s, font=font(size * self.f, bold), fill=col, anchor=anchor)

    def gradient(self, box, stops, mask_fn, horizontal=False, diagonal=False):
        """Fills the shape mask_fn draws with a gradient through (pos, rgba) stops across box."""
        w, h = self.im.size
        g = Image.new("RGBA", (w, h))
        x0, y0, x1, y1 = self.p(*box)
        gd = ImageDraw.Draw(g)

        def at(t):
            t = min(1, max(0, t))
            for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
                if t <= p1:
                    u = 0 if p1 == p0 else (t - p0) / (p1 - p0)
                    return tuple(round(a + (b - a) * u) for a, b in zip(c0, c1))
            return stops[-1][1]

        if diagonal:  # along the box diagonal, like LinearGradient(0, 0, w, h)
            dx, dy = x1 - x0, y1 - y0
            # project each pixel onto the diagonal on a small copy, then scale it up (the ramp is smooth)
            small = Image.new("RGBA", (64, 64))
            sp = small.load()
            for y in range(64):
                for x in range(64):
                    px, py = x0 + (x + .5) / 64 * (x1 - x0), y0 + (y + .5) / 64 * (y1 - y0)
                    sp[x, y] = at(((px - x0) * dx + (py - y0) * dy) / (dx * dx + dy * dy))
            g.paste(small.resize((round(x1 - x0), round(y1 - y0)), Image.BILINEAR), (round(x0), round(y0)))
        else:
            n = round((x1 - x0) if horizontal else (y1 - y0))
            for i in range(n):
                c = at(i / max(1, n - 1))
                if horizontal:
                    gd.line([(x0 + i, y0), (x0 + i, y1)], fill=c)
                else:
                    gd.line([(x0, y0 + i), (x1, y0 + i)], fill=c)
        mask = Image.new("L", (w, h))
        mask_fn(ImageDraw.Draw(mask), self)
        self.im.alpha_composite(Image.composite(g, Image.new("RGBA", (w, h)), mask))

    def out(self):
        return self.im.resize((self.im.width // SS, self.im.height // SS), Image.LANCZOS)


def ticks(pen, cx, cy, r, col, numerals=None, bold=False):
    """The `analog` face of ThemeWidgets.kt: minute ticks (hour ticks unless numerals) and the numerals."""
    for i in range(60):
        a = math.radians(i * 6)
        big = i % 5 == 0
        if numerals and big:
            continue
        w = r * (.035 if big else .014)
        r0 = r * (.84 if big else .9)
        pen.line(cx + r0 * math.sin(a), cy - r0 * math.cos(a), cx + r * .96 * math.sin(a), cy - r * .96 * math.cos(a), w, col)
    if numerals:
        for n in range(1, 13):
            a = math.radians(n * 30)
            pen.text(str(n), cx + r * .76 * math.sin(a), cy - r * .76 * math.cos(a) + r * .085, r * .23, numerals, bold)


def stick_hands(skin, w, h, f, cx, cy, hour, minute, col, dot):
    """Round-capped line hands pointing at twelve; the centre dot rides on the minute hand (it is round, so turning does not show)."""
    for part, (length, width) in (("hour", hour), ("minute", minute)):
        pen = Pen(w, h, f)
        pen.line(cx, cy, cx, cy - length, width, col)
        if part == "minute":
            pen.circle(cx, cy, dot, col)
        save(pen.out(), skin, part)


# ---------------------------------------------------------------- Windows 11 clock (360 x 360)

def w11_clock():
    w = h = 360
    f = 520 / w
    blue = (96, 182, 246, 255)
    pen = Pen(w, h, f)
    pen.rect(0, 0, w, h, (44, 44, 44, 240), radius=w * .16)
    r = w * .4
    pen.circle(w / 2, h / 2, r, (42, 54, 64, 255))
    ticks(pen, w / 2, h / 2, r, blue, numerals=blue)
    save(pen.out(), "w11_clock", "dial")
    mw = w * .016
    stick_hands("w11_clock", w, h, f, w / 2, h / 2, (r * .5, mw * 1.4), (r * .75, mw), blue, mw * 1.4)


# ---------------------------------------------------------------- Windows XP clock: a Luna window, the face at its centre (360 x 390)

def luna_window(pen, w, h, title):
    u = w / 160
    blue = (0, 61, 220, 255)
    pen.rect(0, 0, w, h, blue, radius=8 * u)
    pen.rect(0, 10 * u, w, h, blue)
    bar = 24 * u

    def bar_mask(md, pn):
        md.rounded_rectangle(pn.p(0, 0, w, bar + 10 * u), 8 * u * pn.f, fill=255)
        md.rectangle(pn.p(0, bar, w, h), fill=0)

    pen.gradient((0, 0, w, bar), [(0, (9, 151, 255, 255)), (.12, (0, 83, 238, 255)), (.6, (0, 80, 238, 255)), (1, (6, 111, 252, 255))], bar_mask)
    size = 9.5 * u
    pen.text(title, 7 * u, 16.5 * u + size * .05, size, (10, 24, 131, 255), True, "ls")
    pen.text(title, 7 * u, 16.5 * u, size, (255, 255, 255, 255), True, "ls")
    x0, y0, x1, y1 = w - 21 * u, 3 * u, w - 3 * u, 21 * u
    pen.rect(x0, y0, x1, y1, (255, 255, 255, 255), radius=3 * u)
    pen.gradient((x0 + u, y0 + u, x1 - u, y1 - u), [(0, (231, 131, 98, 255)), (1, (208, 59, 22, 255))],
                 lambda md, pn: md.rounded_rectangle(pn.p(x0 + u, y0 + u, x1 - u, y1 - u), 2.5 * u * pn.f, fill=255))
    white = (255, 255, 255, 255)
    pen.line(x0 + 5 * u, y0 + 5 * u, x1 - 5 * u, y1 - 5 * u, 2 * u, white)
    pen.line(x1 - 5 * u, y0 + 5 * u, x0 + 5 * u, y1 - 5 * u, 2 * u, white)
    body = (3 * u, 24 * u, w - 3 * u, h - 3 * u)
    pen.rect(*body, (236, 233, 216, 255))
    return body


def xp_clock():
    w, h = 360, 390
    f = 520 / h
    u = w / 160
    pen = Pen(w, h, f)
    b = luna_window(pen, w, h, "Clock")
    r = min(b[2] - b[0], b[3] - b[1] - 22 * u) * .42
    cx, cy = w / 2, h / 2  # the bitmap's digital readout under the face is left out, so the face sits at the window centre
    navy = (0, 51, 153, 255)
    pen.circle(cx, cy, r * 1.04, (127, 157, 185, 255))
    pen.circle(cx, cy, r, (255, 255, 255, 255))
    ticks(pen, cx, cy, r, navy, numerals=navy, bold=True)
    save(pen.out(), "xp_clock", "dial")
    mw = r * .05
    stick_hands("xp_clock", w, h, f, cx, cy, (r * .5, mw * 1.4), (r * .75, mw), (0, 0, 0, 255), mw * 1.4)


# ---------------------------------------------------------------- Windows 95 time: the grey window and its "Time" group box (140 x 140 grid)

def w95_time():
    lw = 140
    f = 4  # whole pixels per grid unit, so the bevels stay crisp (560 px)
    face, dark, teal, black, white = (192, 192, 192, 255), (128, 128, 128, 255), (0, 128, 128, 255), (0, 0, 0, 255), (255, 255, 255, 255)
    pen = Pen(lw, lw, f)

    def px(x0, y0, x1, y1, col):
        pen.rect(x0, y0, x1, y1, col)

    # raised bevel
    px(0, 0, lw, lw, face)
    px(0, 0, lw, 1, white); px(0, 0, 1, lw, white)
    px(0, lw - 1, lw, lw, black); px(lw - 1, 0, lw, lw, black)
    px(1, 1, lw - 1, 2, (223, 223, 223, 255)); px(1, 1, 2, lw - 1, (223, 223, 223, 255))
    px(1, lw - 2, lw - 1, lw - 1, dark); px(lw - 2, 1, lw - 1, lw - 1, dark)
    # etched group box with its label on the top line, first letter underlined
    l, t, r_, b = 6, 9, lw - 6, lw - 6
    for (x0, y0, x1, y1), col in (((l, t, r_ - 1, b - 1), dark), ((l + 1, t + 1, r_, b), white)):
        px(x0, y0, x1, y0 + 1, col); px(x0, y1, x1 + 1, y1 + 1, col); px(x0, y0, x0 + 1, y1, col); px(x1, y0, x1 + 1, y1, col)
    fnt = font(9)
    tw = fnt.getlength("Time")
    px(l + 6, t - 5, l + 10 + tw, t + 5, face)
    pen.text("Time", l + 8, t + 3.5, 9, black, anchor="ls")
    px(l + 8, t + 5, l + 8 + fnt.getlength("T"), t + 5.6, black)
    # twelve teal dots with a black shadow; centred on the window (the bitmap sits 3.5 units lower) so AnalogClock turns the hands about it
    cx = cy = lw / 2
    r = (lw - 26) / 2
    for i in range(12):
        a = math.radians(i * 30)
        x, y = cx + r * math.sin(a), cy - r * math.cos(a)
        z = 2.2 if i % 3 == 0 else 1.6
        px(x - z + .8, y - z + .8, x + z + .8, y + z + .8, black)
        px(x - z, y - z, x + z, y + z, teal)
    save(pen.out(), "w95_time", "dial")
    for part, length, width in (("hour", r * .55, 3), ("minute", r * .85, 2.4)):
        hp = Pen(lw, lw, f)
        pts = [(cx + width, cy), (cx, cy - length), (cx - width, cy), (cx, cy + length * .12)]
        flat = [hp.p(x, y) for x, y in pts]
        hp.d.polygon([tuple(q) for q in flat], fill=teal)
        hp.d.line([tuple(q) for q in flat + flat[:1]], fill=black, width=round(.7 * hp.f), joint="curve")
        save(hp.out(), "w95_time", part)


# ---------------------------------------------------------------- Glass clock: frosted panel, white hands (the digits are TextClocks over it)

def gl_clock():
    w = h = 360
    f = 520 / w
    pen = Pen(w, h, f)
    rad = w * .14
    pen.gradient((0, 0, w, h), [(0, (255, 255, 255, 80)), (1, (255, 255, 255, 30))],
                 lambda md, pn: md.rounded_rectangle(pn.p(2, 2, w - 2, h - 2), rad * pn.f, fill=255), diagonal=True)
    pen.d.rounded_rectangle(pen.p(2, 2, w - 2, h - 2), rad * pen.f, outline=(255, 255, 255, 140), width=round(3 * pen.f))
    save(pen.out(), "gl_clock", "dial")
    stick_hands("gl_clock", w, h, f, w / 2, h / 2, (w * .22, w * .025), (w * .36, w * .016), (255, 255, 255, 255), w * .025)


if __name__ == "__main__":
    for skin, args in GADGETS.items():
        gadget(skin, *args)
    w11_clock()
    xp_clock()
    w95_time()
    gl_clock()
    # self-check: every hand is exactly its dial's size, so AnalogClock scales them together
    for name in sorted(os.listdir(RES)):
        if name.startswith("clk_") and not name.endswith("_dial.png"):
            dial = name.rsplit("_", 1)[0] + "_dial.png"
            assert Image.open(os.path.join(RES, name)).size == Image.open(os.path.join(RES, dial)).size, name
    print("ok", file=sys.stderr)
