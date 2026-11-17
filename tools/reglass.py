"""Run on the ORIGINAL glass PNGs (git history before the first redraw): the masks are read from their alpha.
Redraws the Material glass launcher icons: a dark tinted glass squircle (no glare), a thin light rim and a soft light glyph.
The squircle and the glyph shapes are read from the current PNGs (glyph = the opaque part, glass = the see-through part)."""
import sys, glob, os, math
from PIL import Image, ImageFilter, ImageDraw, ImageChops

D = sys.argv[1]; OUT = sys.argv[2] if len(sys.argv) > 2 else D
# tone suffix -> (body top, body bottom, glyph top, glyph bottom, rim)
TONES = {
    "":        ((19, 38, 66), (12, 23, 42), (150, 174, 220), (128, 151, 198)),
    "_purple": ((54, 42, 84), (32, 24, 54), (214, 200, 244), (182, 164, 222)),
    "_pink":   ((80, 40, 62), (50, 24, 40), (244, 200, 222), (220, 168, 196)),
    "_cyan":   ((26, 60, 74), (14, 38, 50), (182, 226, 238), (146, 198, 214)),
    "_green":  ((30, 64, 46), (16, 40, 28), (190, 236, 204), (154, 208, 172)),
    "_amber":  ((78, 60, 30), (50, 36, 16), (244, 220, 170), (218, 188, 132)),
}
BODY_A = 0.88   # the glass lets the wallpaper through a little, as Android's Material glass icons do

def lerp(a, b, t): return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))

def masks(im):
    a = im.split()[3]
    shape = a.point(lambda v: 255 if v > 40 else int(v * 255 / 40))
    glyph = a.point(lambda v: 0 if v < 140 else 255 if v > 185 else int((v - 140) * 255 / 45))
    return shape, glyph

def pulse_glyph(size):
    k = 4; S = size * k
    g = Image.new("L", (S, S), 0); d = ImageDraw.Draw(g)
    c = S / 2; r = S * 0.245; w = int(S * 0.055)
    # the ring, open at the left where the line comes in
    d.arc([c - r, c - r, c + r, c + r], start=205, end=155, fill=255, width=w)
    pts = [(c - r - w * 0.2, c + S * 0.02), (c - S * 0.07, c + S * 0.02), (c + S * 0.0, c - S * 0.17),
           (c + S * 0.08, c + S * 0.13), (c + S * 0.14, c - S * 0.03), (c + S * 0.2, c - S * 0.03)]
    d.line(pts, fill=255, width=w, joint="curve")
    for p in (pts[0], pts[1], pts[2], pts[3], pts[4]):
        d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=255)
    dr = S * 0.045
    d.ellipse([c + S * 0.2 - dr, c - S * 0.03 - dr, c + S * 0.2 + dr, c - S * 0.03 + dr], fill=255)
    return g.resize((size, size), Image.LANCZOS)

def render(src, dst, tone, glyph_override=None):
    im = Image.open(src).convert("RGBA"); W, H = im.size
    shape, glyph = masks(im)
    if glyph_override is not None: glyph = glyph_override(W)
    top, bot, gtop, gbot = TONES[tone]
    body = Image.new("RGB", (W, H)); bp = body.load(); gl = Image.new("RGB", (W, H)); gp = gl.load()
    for y in range(H):
        t = y / (H - 1)
        cb = lerp(top, bot, t); cg = lerp(gtop, gbot, t)
        for x in range(W): bp[x, y] = cb; gp[x, y] = cg
    # thin light rim just inside the edge, stronger at the top
    inner = shape.filter(ImageFilter.MinFilter(5))
    rim = ImageChops.subtract(shape, inner)
    rimc = lerp(top, (170, 192, 232), 0.55)
    out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    body_a = shape.point(lambda v: int(v * BODY_A))
    out.paste(body, (0, 0), body_a)
    rim_layer = Image.new("RGBA", (W, H), rimc + (0,))
    rim_layer.putalpha(rim.point(lambda v: int(v * 0.45)))
    out = Image.alpha_composite(out, rim_layer)
    g_layer = gl.convert("RGBA"); g_layer.putalpha(ImageChops.multiply(glyph, shape))
    out = Image.alpha_composite(out, g_layer)
    out.save(dst, optimize=True)

n = 0
for f in sorted(glob.glob(os.path.join(D, "launch_*_glass*.png"))):
    base = os.path.basename(f)[:-4]
    tone = base[base.index("_glass") + 6:]
    tone = tone if tone in TONES else ""
    over = pulse_glyph if base.startswith("launch_pulse_glass") else None
    render(f, os.path.join(OUT, base + ".png"), tone, over); n += 1
print("redrawn", n)
