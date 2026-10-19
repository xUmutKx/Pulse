"""Validate bundled Winamp skins. Usage: python3 -I tools/validate_skins.py <assets/skins dir> [--apply]
Flags skins whose main.bmp is not 275x116 or whose key sprites are missing/too small for what WinampMain blits,
and near-duplicates (same main.bmp look). With --apply deletes them from the dir and index.json."""
import sys, os, io, json, zipfile
from PIL import Image

# sprite -> (min width, min height), the largest cell WinampMain/WinampWidget reads
NEED = {"cbuttons": (136, 36), "titlebar": (302, 27), "posbar": (307, 10), "volume": (68, 433)}

def load(z, names, *keys):
    for k in keys:
        if k in names:
            return Image.open(io.BytesIO(z.read(names[k]))).convert("RGB")

def check(path):
    z = zipfile.ZipFile(path)
    names = {n.split("/")[-1].lower(): n for n in z.namelist() if not n.endswith("/")}
    bad = []
    main = load(z, names, "main.bmp")
    if main is None: return ["no main.bmp"], None
    if main.size != (275, 116): bad.append("main %dx%d" % main.size)
    for k, (w, h) in NEED.items():
        im = load(z, names, k + ".bmp")
        if im is None: bad.append(k + " missing")
        elif im.width < w or im.height < h: bad.append("%s %dx%d" % (k, *im.size))
    nums = load(z, names, "numbers.bmp", "nums_ex.bmp")
    if nums is None or nums.width < 99 or nums.height < 13: bad.append("numbers missing/small")
    txt = load(z, names, "text.bmp")
    if txt is None or txt.width < 155 or txt.height < 18: bad.append("text missing/small")
    return bad, main

def sig(im):
    return list(im.resize((32, 12)).convert("L").getdata())

def main():
    d = sys.argv[1]; apply = "--apply" in sys.argv
    idx_path = os.path.join(d, "index.json")
    index = json.load(open(idx_path))
    drop, keep_sigs = {}, []
    for f in sorted(os.listdir(d)):
        if not f.endswith(".wsz"): continue
        try: bad, m = check(os.path.join(d, f))
        except Exception as e: drop[f] = "unreadable: %s" % e; continue
        if bad: drop[f] = ", ".join(bad); continue
        s = sig(m)
        dup = next((o for o, t in keep_sigs if sum(abs(a - b) for a, b in zip(s, t)) / len(s) < 3), None)
        if dup: drop[f] = "near-duplicate of " + dup; continue
        keep_sigs.append((f, s))
    for f, why in drop.items(): print("DROP", f, "-", why)
    print("keep %d, drop %d" % (len(keep_sigs), len(drop)))
    if apply:
        for f in drop: os.remove(os.path.join(d, f))
        index = [e for e in index if e["id"] + ".wsz" not in drop]
        json.dump(index, open(idx_path, "w"), indent=1, ensure_ascii=False)
        print("index.json now has", len(index))

main()
