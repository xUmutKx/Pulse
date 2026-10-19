"""Validates raw Winamp skins and builds the bundled set (mirrors extras/music/skin/WszParser.kt requirements).
Usage: python3 -I build_skin_pack.py <raw_dir> [max_total_bytes]
Writes app/src/main/assets/skins/<id>.wsz + index.json and paket/skins/SOURCES.md. Nothing is published.
Each accepted skin is re-zipped flat with only the files the parser reads, so junk/readme/exe/cursors never ship."""
import hashlib, io, json, os, re, struct, sys, zipfile

RAW = sys.argv[1]
CAP = int(sys.argv[2]) if len(sys.argv) > 2 else 7_800_000
ASSETS = "/root/projects/Pulse/app/src/main/assets/skins"
PAKET = "/sdcard/Download/projects/paket/skins"
MAX_ENTRY = 8 << 20
WANTED = {"main.bmp", "cbuttons.bmp", "pledit.txt", "viscolor.txt", "region.txt", "titlebar.bmp", "posbar.bmp", "volume.bmp",
          "balance.bmp", "numbers.bmp", "nums_ex.bmp", "text.bmp", "shufrep.bmp", "monoster.bmp", "playpaus.bmp", "eqmain.bmp", "pledit.bmp"}
# name -> min (w, h); balance is optional (parser falls back to the painted one)
NEED = {"main.bmp": (275, 116), "cbuttons.bmp": (136, 36), "text.bmp": (155, 18), "posbar.bmp": (277, 10), "volume.bmp": (68, 433),
        "titlebar.bmp": (302, 15), "playpaus.bmp": (42, 9), "shufrep.bmp": (75, 45), "monoster.bmp": (56, 24), "eqmain.bmp": (275, 116)}
NUMS = ("numbers.bmp", "nums_ex.bmp")

def bmp_ok(b):
    """Same acceptance rules as decodeBmp(): BM header, dib>=40, size 1..4096, bpp, compression, data in range."""
    if len(b) < 54 or b[:2] != b"BM": return None
    off, dib, w, h = struct.unpack("<IIii", b[10:14] + b[14:18] + b[18:26])
    bpp, comp = struct.unpack("<H", b[28:30])[0], struct.unpack("<I", b[30:34])[0]
    h = abs(h)
    if dib < 40 or not (1 <= w <= 4096 and 1 <= h <= 4096) or bpp not in (1, 4, 8, 16, 24, 32): return None
    rle = (comp == 1 and bpp == 8) or (comp == 2 and bpp == 4)
    if comp != 0 and not rle and not (comp == 3 and bpp in (16, 32)): return None
    stride = ((w * bpp + 31) // 32) * 4
    if off > len(b) or (not rle and off + stride * h > len(b)): return None
    if bpp <= 8 and 14 + dib + 4 > len(b): return None
    return w, h

def check(path):
    """Returns (files dict, reason). files empty -> rejected with reason."""
    try:
        z = zipfile.ZipFile(path)
    except Exception: return None, "badzip"
    files = {}
    for i in z.infolist():
        n = i.filename.replace("\\", "/")
        if n.startswith("/") or ".." in n.split("/") or re.match(r"^[A-Za-z]:", n): return None, "zipslip"
        if i.is_dir(): continue
        base = n.rsplit("/", 1)[-1].lower()
        if base in WANTED and base not in files:
            if i.file_size > MAX_ENTRY: return None, "big-entry"
            try: files[base] = z.read(i)
            except Exception: return None, "unreadable"
    for k, (mw, mh) in NEED.items():
        if k not in files: return None, "missing " + k
        d = bmp_ok(files[k])
        if not d: return None, "bad bmp " + k
        if d[0] < mw or d[1] < mh: return None, "small " + k
    n = next((k for k in NUMS if k in files and bmp_ok(files[k]) and bmp_ok(files[k])[0] >= 99 and bmp_ok(files[k])[1] >= 13), None)
    if not n: return None, "missing numbers"
    if "pledit.txt" not in files: return None, "missing pledit.txt"
    if "balance.bmp" in files and not bmp_ok(files["balance.bmp"]): del files["balance.bmp"]
    for k in [k for k in files if k.endswith(".bmp") and not bmp_ok(files[k])]: del files[k]
    return files, "ok"

# fan art / game / show / band / brand skins: the owner wants generic themes
SKIP = re.compile(r"castlevania|chips.?challenge|dig.?dug|donkey|doritos|deus.?ex|springfield|frasier|gir.?amp|half.?life|puffy|jackie|kirby|law.?and|marijuana|max.?payne|mgs2|kid.?icarus|playstation|radiohead|sim.?city|star.?trek|stuart|weezer|aphex|johnny.?rebel|(?<![a-z])lain|american.?psycho|coca|megaman|playboy|spider|unreal|mine.?amp|scughost|memorial|makemesad|kari\b|georgeskin|tostie|winzip|realplayer|explorer|netscape|excel|tricorder|all.?your.?base", re.I)

def pretty(fn):
    s = re.sub(r"^[a-z\-]+__", "", os.path.splitext(fn)[0])
    s = re.sub(r"^winampskins?_", "", s, flags=re.I)
    by = ""
    m = re.search(r"[_ -]+by[_ -]+(.+)$", s, re.I)
    if m: by, s = m.group(1).replace("_", " "), s[:m.start()]
    s = re.sub(r"[_]+", " ", s).strip(" -")
    return (s or fn)[:40], by

src = json.load(open(os.path.join(RAW, "sources.json")))
cands, seen, rej = [], set(), {}
for fn in sorted(os.listdir(RAW)):
    if fn == "sources.json": continue
    files, why = check(os.path.join(RAW, fn))
    if not files: rej[fn] = why; continue
    key = hashlib.sha256(files["main.bmp"] + files["cbuttons.bmp"]).hexdigest()
    if SKIP.search(fn): rej[fn] = "theme"; continue
    if key in seen: rej[fn] = "duplicate"; continue
    seen.add(key)
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as o:
        for k in sorted(files): o.writestr(zipfile.ZipInfo(k, (1980, 1, 1, 0, 0, 0)), files[k], zipfile.ZIP_DEFLATED, 9)
    name, by = pretty(fn)
    cands.append(dict(fn=fn, name=name, author=by, data=buf.getvalue(), source=src.get(fn, {}).get("url", ""), site=src.get(fn, {}).get("site", "")))
# round-robin by site keeps the mix varied if the cap bites; smallest first inside a site
by_site = {}
for c in sorted(cands, key=lambda c: len(c["data"])): by_site.setdefault(c["site"], []).append(c)
order = []
while any(by_site.values()):
    for s in list(by_site):
        if by_site[s]: order.append(by_site[s].pop(0))
os.makedirs(ASSETS, exist_ok=True)
for f in os.listdir(ASSETS): os.remove(os.path.join(ASSETS, f))
idx, total, ids = [], 0, set()
for c in order:
    if total + len(c["data"]) > CAP: continue
    i = re.sub(r"[^a-z0-9]+", "-", c["name"].lower()).strip("-") or "skin"
    while i in ids: i += "-2"
    ids.add(i); total += len(c["data"])
    open(os.path.join(ASSETS, i + ".wsz"), "wb").write(c["data"])
    idx.append({"id": i, "name": c["name"], "author": c["author"], "source": c["source"],
                "licence": "community skin, licence not stated by the author", "bytes": len(c["data"])})
idx.sort(key=lambda e: e["name"].lower())
json.dump(idx, open(os.path.join(ASSETS, "index.json"), "w"), ensure_ascii=False, indent=1)
os.makedirs(PAKET, exist_ok=True)
with open(os.path.join(PAKET, "SOURCES.md"), "w") as f:
    f.write("# Bundled Winamp skins (assets/skins). Licences are not stated by the authors; classic skins are community art.\n\n")
    for e in idx: f.write("- %s%s: %s\n" % (e["name"], " (%s)" % e["author"] if e["author"] else "", e["source"]))
print(len(idx), "skins", total, "bytes; rejected:", len(rej))
for k, v in sorted(rej.items(), key=lambda x: x[1]): print(" ", v, k)
