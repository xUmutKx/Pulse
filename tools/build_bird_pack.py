#!/usr/bin/env python3
"""Build paket/birds/birds.zip (Pulse bird sound pack) from Wikimedia Commons recordings.

Run:  python3 -I /root/projects/Pulse/tools/build_bird_pack.py
Needs ffmpeg (libvorbis) and network for the first run. No numpy: plain array maths.
Outputs: paket/birds/birds.zip, paket/birds/SOURCES.md,
         .scratch/extra-apps/manifest-birds.json
Every loop is 40 s, mono, 32 kHz Vorbis, with an equal-power cross-fade seam so it
repeats without a click. Short recordings are tiled with natural gaps and slight
speed/gain variation.
"""
import array, hashlib, html, json, math, os, random, re, subprocess, sys
import time, urllib.parse, urllib.request, zipfile

ROOT = "/sdcard/Download/projects"
DL = ROOT + "/_downloads/bird-pack"
RAW = DL + "/raw"
WORK = DL + "/work"
OUT = ROOT + "/paket/birds"
MANIFEST = ROOT + "/.scratch/extra-apps/manifest-birds.json"
SR = 32000
LOOP = 40.0      # seconds
XF = 1.5         # seam cross-fade seconds
UA = {"User-Agent": "PulseBirdPack/1.0 (umutsahin1950@gmail.com)"}

# id, en, tr, commons file title, highpass Hz, start s, gap range s
S = [
 ("blackbird", "Blackbird", "Karatavuk", "Turdus merula - Common Blackbird XC541784.mp3", 400, 0, (0.5, 2)),
 ("nightingale", "Nightingale", "Bülbül", "Luscinia megarhynchos - Common Nightingale XC546355.mp3", 400, 0, (0.8, 2.5)),
 ("robin", "Robin", "Kızılgerdan", "Erithacus rubecula - European Robin XC542842.mp3", 500, 0, (1, 3)),
 ("songthrush", "Song thrush", "Öter ardıç", "Turdus philomelos - Song Thrush XC434600.mp3", 500, 0, (0.8, 2.5)),
 ("greattit", "Great tit", "Büyük baştankara", "Parus major - Great Tit XC516949.mp3", 600, 0, (1, 3)),
 ("bluetit", "Blue tit", "Mavi baştankara", "Cyanistes caeruleus - Eurasian Blue Tit XC510545.mp3", 600, 0, (1, 3)),
 ("sparrow", "House sparrow", "Serçe", "Passer domesticus - House Sparrow XC433158.mp3", 500, 0, (1, 3)),
 ("wren", "Wren", "Çalıkuşu", "Troglodytes troglodytes - Eurasian Wren XC554784.mp3", 800, 0, (1, 3)),
 ("chaffinch", "Chaffinch", "İspinoz", "Fringilla coelebs - Common Chaffinch XC489896.mp3", 600, 0, (1, 3)),
 ("goldfinch", "Goldfinch", "Saka", "Carduelis carduelis - Spring call.oga", 800, 0, (1, 3)),
 ("skylark", "Skylark", "Tarlakuşu", "Alauda arvensis - Eurasian Skylark XC423566.mp3", 800, 0, (0.5, 2)),
 ("canary", "Canary", "Kanarya", "Canari hartz 2.ogg", 800, 0, (1, 3)),
 ("oriole", "Golden oriole", "Sarıasma", "Oriolus oriolus - Eurasian Golden Oriole XC488226.mp3", 400, 0, (1, 3)),
 ("starling", "Starling", "Sığırcık", "Sturnus vulgaris - Common Starling XC516954.mp3", 500, 0, (1, 3)),
 ("swallow", "Barn swallow", "Kırlangıç", "Hirundo rustica - Barn Swallow XC492101.mp3", 800, 0, (1, 3)),
 ("flycatcher", "Spotted flycatcher", "Benekli sinekkapan", "Muscicapa striata - Spotted Flycatcher XC342793.mp3", 800, 0, (1.5, 4)),
 ("redstart", "Common redstart", "Kızılkuyruk", "Phoenicurus phoenicurus - Common Redstart XC468918.mp3", 800, 0, (1, 3)),
 ("cuckoo", "Cuckoo", "Guguk", "Cuculus canorus - Common Cuckoo XC542432.mp3", 250, 0, (1, 3)),
 ("hoopoe", "Hoopoe", "İbibik", "Upupa epops - Eurasian Hoopoe XC108366.mp3", 250, 0, (1.5, 4)),
 ("dove", "Collared dove", "Kumru", "Streptopelia decaocto - Eurasian Collared Dove XC82758.mp3", 250, 0, (1.5, 4)),
 ("turtledove", "Turtle dove", "Üveyik", "Streptopelia turtur - European Turtle Dove XC481626.mp3", 200, 0, (1.5, 4)),
 ("tawnyowl", "Tawny owl", "Baykuş", "Strix aluco - Tawny Owl XC534183.mp3", 150, 0, (2, 5)),
 ("littleowl", "Little owl", "Kukumav", "Athene noctua - Little Owl XC431980.mp3", 300, 0, (2, 5)),
 ("crow", "Carrion crow", "Leş kargası", "Corvus corone - Carrion Crow XC491082.mp3", 250, 0, (1.5, 4)),
 ("jay", "Eurasian jay", "Alakarga", "Garrulus glandarius - Eurasian Jay XC395872.mp3", 300, 0, (1.5, 4)),
 ("seagull", "Yellow-legged gull", "Martı", "Yellow-legged Gull - Larus michahellis michahellis.ogg", 400, 0, (1, 3)),
 ("blackheadedgull", "Black-headed gull", "Karabaş martı", "Chroicocephalus ridibundus - Black-headed Gull XC512502.mp3", 400, 0, (1.5, 4)),
 ("woodpecker", "Great spotted woodpecker", "Ağaçkakan", "Dendrocopos major - Great Spotted Woodpecker XC542957.mp3", 300, 0, (3, 7)),
 ("parakeet", "Rose-ringed parakeet", "Yüzük boyunlu papağan", "Psittacula krameri - Rose-ringed Parakeet XC474519.mp3", 500, 0, (1, 3)),
 ("quail", "Quail", "Bıldırcın", "Coturnix coturnix - Common Quail XC549215.mp3", 300, 0, (1.5, 4)),
 ("mallard", "Mallard", "Yeşilbaş ördek", "Anas platyrhynchos - Mallard XC518422.mp3", 250, 0, (2, 5)),
 ("rooster", "Rooster", "Horoz", "Chicken 01.wav", 200, 0, (3, 7)),
]
# dawn chorus: id of layers -> gain
DAWN = [("blackbird", 1.0), ("robin", 0.8), ("songthrush", 0.9), ("greattit", 0.7),
        ("wren", 0.6), ("chaffinch", 0.6), ("nightingale", 0.7)]
DAWN_META = ("dawnchorus", "Dawn chorus", "Şafak korosu")


def fname(title):
    return title.replace(" ", "_")


def fetch_meta():
    mp = DL + "/meta.json"
    meta = json.load(open(mp)) if os.path.exists(mp) else {}
    need = [s[3] for s in S if s[3] not in meta]
    for i in range(0, len(need), 10):
        ch = need[i:i + 10]
        p = {"action": "query", "format": "json", "titles": "|".join("File:" + t for t in ch),
             "prop": "imageinfo", "iiprop": "url|extmetadata"}
        d = json.load(urllib.request.urlopen(urllib.request.Request(
            "https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(p), headers=UA), timeout=60))
        for pg in d["query"]["pages"].values():
            ii = pg["imageinfo"][0]; m = ii["extmetadata"]
            meta[pg["title"][5:]] = {
                "url": ii["url"],
                "page": "https://commons.wikimedia.org/wiki/" + urllib.parse.quote(pg["title"].replace(" ", "_")),
                "author": html.unescape(re.sub("<[^>]+>", "", m.get("Artist", {}).get("value", "?"))).strip(),
                "lic": m.get("LicenseShortName", {}).get("value", "?")}
        time.sleep(1)
    os.makedirs(RAW, exist_ok=True)
    for t, m in meta.items():
        f = RAW + "/" + fname(t)
        if not os.path.exists(f):
            open(f, "wb").write(urllib.request.urlopen(urllib.request.Request(m["url"], headers=UA), timeout=120).read())
            time.sleep(1)
    json.dump(meta, open(mp, "w"), indent=1, ensure_ascii=False)
    return meta


def decode(path, hp):
    """mono 32 kHz s16, high-passed, light denoise, loudness-normalised."""
    af = f"highpass=f={hp},afftdn=nr=8,loudnorm=I=-22:TP=-2:LRA=11"
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-vn", "-ac", "1", "-af", af,
                        "-ar", str(SR), "-f", "s16le", "-"], capture_output=True, check=True)
    a = array.array("h"); a.frombytes(r.stdout)
    return a


def trim(a):
    """drop leading/trailing dead air (frames far below the loudest frames)."""
    fr = SR // 50
    rms = []
    for i in range(0, len(a) - fr, fr):
        seg = a[i:i + fr]
        rms.append(math.sqrt(sum(x * x for x in seg) / fr))
    if not rms:
        return a
    thr = sorted(rms)[int(len(rms) * 0.9)] * 0.12
    idx = [i for i, v in enumerate(rms) if v > thr]
    if not idx:
        return a
    s = max(0, (idx[0] - 5) * fr); e = min(len(a), (idx[-1] + 6) * fr)
    return a[s:e]


def resample(a, f):
    n = int(len(a) / f)
    out = array.array("h", bytes(2 * n))
    for i in range(n):
        p = i * f; j = int(p); fr = p - j
        b = a[j + 1] if j + 1 < len(a) else a[j]
        out[i] = int(a[j] * (1 - fr) + b * fr)
    return out


def fade_unit(a, g):
    n = min(len(a) // 4, SR // 25)
    o = array.array("h", [int(x * g) for x in a])
    for i in range(n):
        k = i / n
        o[i] = int(o[i] * k); o[-1 - i] = int(o[-1 - i] * k)
    return o


def make_loop(sid, a, gap, rng):
    need = int((LOOP + XF) * SR)
    if len(a) >= need + SR:                      # long recording: take it as is
        x = a[:need]
    else:                                        # short: tile with natural gaps
        a = trim(a)
        x = array.array("h")
        k = 0
        while len(x) < need:
            f = [1.0, 1.04, 0.97, 1.02, 0.95][k % 5] if k else 1.0
            u = fade_unit(resample(a, f) if f != 1.0 else a, rng.uniform(0.8, 1.0))
            x.extend(u)
            x.extend(array.array("h", bytes(2 * int(rng.uniform(*gap) * SR))))
            k += 1
        x = x[:need]
    L = LOOP * SR
    L = int(L); F = int(XF * SR)
    out = x[:L]
    for i in range(F):                           # equal-power seam: tail fades into head
        t = i / F
        ci, co = math.sin(t * math.pi / 2), math.cos(t * math.pi / 2)
        out[i] = max(-32768, min(32767, int(x[i] * ci + x[L + i] * co)))
    return out


def encode(a, path):
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "3", path], input=a.tobytes(), check=True)


def main():
    meta = fetch_meta()
    os.makedirs(WORK, exist_ok=True); os.makedirs(OUT, exist_ok=True)
    loops = {}
    outs = []
    for sid, en, tr, title, hp, ss, gap in S:
        rng = random.Random(sid)
        a = decode(RAW + "/" + fname(title), hp)[ss * SR:]
        loops[sid] = make_loop(sid, a, gap, rng)
        p = f"{WORK}/{sid}.ogg"; encode(loops[sid], p)
        outs.append((sid, en, tr, p)); print(sid, os.path.getsize(p))
    # dawn chorus mix of rotated loops (rotation keeps the loop seamless)
    n = len(loops["blackbird"]); mix = [0.0] * n
    for k, (sid, g) in enumerate(DAWN):
        a = loops[sid]; off = (n * k) // len(DAWN)
        for i in range(n):
            mix[i] += a[(i + off) % n] * g
    pk = max(abs(v) for v in mix) or 1
    sc = 0.85 * 32767 / pk
    dawn = array.array("h", [int(v * sc) for v in mix])
    sid, en, tr = DAWN_META
    p = f"{WORK}/{sid}.ogg"; encode(dawn, p); outs.insert(0, (sid, en, tr, p)); print(sid, os.path.getsize(p))

    zp = OUT + "/birds.zip"
    if os.path.exists(zp): os.remove(zp)
    sounds = [{"id": i, "en": e, "tr": t, "file": i + ".ogg", "cat": "birds"} for i, e, t, _ in outs]
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_STORED) as z:     # ogg is already compressed
        z.writestr("pack.json", json.dumps({"sounds": sounds}, ensure_ascii=False, indent=1))
        for i, e, t, p in outs:
            z.write(p, i + ".ogg")

    # attribution
    rows = []
    for sid, en, tr, title, *_ in S:
        m = meta[title]
        rows.append((sid, en, tr, title, m["author"], m["lic"], m["page"]))
    md = ["# Bird pack sources", "",
          "All recordings are from Wikimedia Commons (mostly originally from xeno-canto). "
          "Each loop is a processed excerpt (high-pass, light denoise, loudness normalised, "
          "looped with natural gaps and a cross-fade seam, 32 kHz mono Vorbis). "
          "CC BY-SA files must stay attributed and shared alike; the pack is distributed under CC BY-SA 4.0.", "",
          "| id | Species | Turkish | Recording | Author | Licence | Source |", "|---|---|---|---|---|---|---|"]
    for r in rows:
        md.append("| %s | %s | %s | %s | %s | %s | %s |" % r)
    md += ["", "dawnchorus: a mix of the blackbird, robin, song thrush, great tit, wren, chaffinch and nightingale loops above."]
    open(OUT + "/SOURCES.md", "w").write("\n".join(md) + "\n")

    data = open(zp, "rb").read()
    atif = "; ".join("%s: %s, %s (%s)" % (en, a, l, pg) for _, en, _, _, a, l, pg in rows)
    mod = {"id": "birds", "ad_en": "Bird sounds", "ad_tr": "Kuş sesleri", "tur": "veri", "kategori": "kus",
           "boyut": len(data), "surum": "1.0.0",
           "url": "https://github.com/xUmutKx/Pulse/releases/download/extras-birds-1/birds.zip",
           "sha256": hashlib.sha256(data).hexdigest(), "lisans": "CC-BY-SA-4.0",
           "lisans_metni": "https://creativecommons.org/licenses/by-sa/4.0/legalcode",
           "atif": atif, "min_pulse": 10094, "guncellendi": time.strftime("%Y-%m-%dT00:00:00Z")}
    json.dump(mod, open(MANIFEST, "w"), indent=1, ensure_ascii=False)
    print("zip", len(data), mod["sha256"], len(sounds), "sounds")


if __name__ == "__main__":
    main()
