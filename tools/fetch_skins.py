"""Downloads candidate .wsz skins into a raw folder (arg 1). Run with python3 -I."""
import urllib.request, urllib.parse, json, sys, os
out = sys.argv[1]
H = {"User-Agent": "Mozilla/5.0"}
def g(u):
    return urllib.request.urlopen(urllib.request.Request(u, headers=H), timeout=60).read()
gh = json.loads(g("https://api.github.com/repos/captbaritone/webamp/contents/packages/webamp-demo/skins"))
skip = {"MacOSXAqua1-5.wsz", "rei_1.wsz", ".gitignore"}  # brand / anime themed
for x in gh:
    if x["name"] in skip: continue
    open(os.path.join(out, x["name"]), "wb").write(g(x["download_url"])); print("gh", x["name"])
ia = ["winampskin_Windows_Classic","winampskin_MpAmp","winampskin_3D_Show_II","winampskin_Simple_Orange","winampskins_Mistletoe",
 "winampskins_HackerS_kin_1.3_-_by_Mickey_Knox","winampskins_Italo_Disco_Yellow_Skin","winampskins_amp_ayu11","winampskins_bunji_GEM-water",
 "winampskins_je_taime","winampskins_jww_boreas","winampskins_Chimera1-0","winampskin_Minima","winampskin_Auriga_V5","winampskin_Helios",
 "winampskins_DENON_PRESTA_ver1.0_-_by_Akane_Ikuta"]
for i in ia:
    try:
        meta = json.loads(g("https://archive.org/metadata/" + i))
        fs = [f for f in meta["files"] if f["name"].lower().endswith((".wsz", ".wal", ".zip")) and f.get("source") == "original"]
        f = fs[0]["name"]
        open(os.path.join(out, i.split("_", 1)[1][:40] + os.path.splitext(f)[1].lower()), "wb").write(g("https://archive.org/download/%s/%s" % (i, urllib.parse.quote(f)))); print("ia", i, f)
    except Exception as e: print("FAIL", i, e)
