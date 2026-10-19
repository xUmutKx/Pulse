"""Downloads candidate Winamp skins into <raw dir> (arg 1) with a sources.json sidecar. Run: python3 -I fetch_skins.py <raw>
Sources: skins.webamp.org museum (api.webamp.org graphql + r2), archive.org winampskins, GitHub webamp-demo, macamp, ytamp.
Files are untrusted; build_skin_pack.py validates them. Nothing is published."""
import urllib.request, urllib.parse, json, sys, os, re
out = sys.argv[1]
H = {"User-Agent": "Mozilla/5.0"}
BAD = re.compile(r"(anime|evangelion|asuka|rei_|chobits|ff7|final.?fantasy|bioshock|beatles|doors|star.?wars|leia|jedi|baldur|aragorn|lord.?of|dawson|nikki|minecraft|duke|absol|pokemon|aqua|classified|bento|modern|sex|nude|porn|hentai|girl|naruto|zelda|mario|sonic|simpsons|garfield|matrix|nsfw|britney|spears|eminem|metallica|linkin|disney|pepsi|coke|nike|bmw|ferrari|dragon.?ball|digimon|halo|warcraft|diablo|quake|doom|starcraft)", re.I)
MODE = sys.argv[2] if len(sys.argv) > 2 else "all"  # "ia2" = second wave: archive.org rows 260..900 only
src = json.load(open(os.path.join(out, "sources.json"))) if MODE == "ia2" and os.path.exists(os.path.join(out, "sources.json")) else {}
def g(u, data=None, hd=H):
    return urllib.request.urlopen(urllib.request.Request(u, data, hd), timeout=60).read()
def save(name, blob, site, url):
    name = re.sub(r"[^A-Za-z0-9._-]", "_", name)[:60]
    if not name.lower().endswith((".wsz", ".zip")): name += ".wsz"
    n = "%s__%s" % (site, name)
    open(os.path.join(out, n), "wb").write(blob); src[n] = {"site": site, "url": url}
    print("ok", n, len(blob))
# 1 museum
got = 0
for off in (range(0, 600, 100) if MODE == "all" else []):
    q = {"query": "{ skins(first: 100, offset: %d, sort: MUSEUM) { nodes { md5 filename download_url nsfw } } }" % off}
    try:
        nodes = json.loads(g("https://api.webamp.org/graphql", json.dumps(q).encode(), {**H, "Content-Type": "application/json"}))["data"]["skins"]["nodes"]
    except Exception as e: print("museum FAIL", off, e); continue
    for n in nodes:
        if n["nsfw"] or BAD.search(n["filename"]): continue
        try:
            b = g(n["download_url"])
            if len(b) <= 160_000: save(n["filename"], b, "museum", "https://skins.webamp.org/skin/%s/" % n["md5"]); got += 1
        except Exception as e: print("FAIL", n["filename"], e)
    if got >= 90: break
# 2 archive.org winampskins (most downloaded)
rows = json.loads(g("https://archive.org/advancedsearch.php?q=collection%3Awinampskins&fl%5B%5D=identifier&rows=ROWS&output=json&sort%5B%5D=downloads+desc".replace("ROWS", "260" if MODE == "all" else "900")))["response"]["docs"][(0 if MODE == "all" else 260):]
ia = 0
for r in rows:
    i = r["identifier"]
    if BAD.search(i) or ia >= (60 if MODE == "all" else 40): continue
    try:
        fs = [f for f in json.loads(g("https://archive.org/metadata/" + i))["files"] if f["name"].lower().endswith((".wsz", ".zip")) and f.get("source") == "original"]
        if not fs or int(fs[0].get("size", 1 << 30)) > 160_000: continue
        save(fs[0]["name"], g("https://archive.org/download/%s/%s" % (i, urllib.parse.quote(fs[0]["name"]))), "archive", "https://archive.org/details/" + i); ia += 1
    except Exception as e: print("FAIL", i, e)
# 3 GitHub
for repo, path, site in ([] if MODE != "all" else [("captbaritone/webamp", "packages/webamp-demo/skins", "webamp-demo"), ("andrehickmann/macamp", "Resources/Skins", "macamp")]):
    try:
        for x in json.loads(g("https://api.github.com/repos/%s/contents/%s" % (repo, path))):
            if x["name"].lower().endswith((".wsz", ".zip")) and not BAD.search(x["name"]) and x["size"] <= 160_000:
                save(x["name"], g(x["download_url"]), site, "https://github.com/%s/blob/master/%s/%s" % (repo, path, x["name"]))
    except Exception as e: print("gh FAIL", repo, e)
json.dump(src, open(os.path.join(out, "sources.json"), "w"), indent=1)
