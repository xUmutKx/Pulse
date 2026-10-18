#!/usr/bin/env python3
"""Mirror FOSS APKs into our repo and keep extras/manifest.json current.

Per upstream: find the latest APK (F-Droid index or GitHub release), download it,
check its sha256 against the index (F-Droid) and its signing certificate against
the pinned one, then write the APK, size, sha256 and the manifest entry.
Usage: python3 -I tools/mirror_foss.py [--out DIR] [--manifest FILE] [--only ID]
Stdlib only. Set GITHUB_TOKEN to lift the GitHub API limit.
"""
import argparse, hashlib, json, os, shutil, subprocess, sys, urllib.request, datetime, tempfile

REPO_RAW = "https://raw.githubusercontent.com/xUmutKx/Pulse/main/"
MIN_PULSE = 0

# source "fdroid": F-Droid index-v2. source "github": latest release, first asset matching `asset`.
UPSTREAMS = [
    {
        "id": "fossify-messages", "pkg": "org.fossify.messages", "source": "fdroid",
        "cert": "affdb124d3f4720c2f98dbca9eacba0514fba4306e20a2786c861c3c0d6ff292",
        "ad_en": "Fossify Messages", "ad_tr": "Fossify Mesajlar", "kategori": "arac",
        "aciklama_en": "Open-source SMS and MMS app.", "aciklama_tr": "Açık kaynak SMS ve MMS uygulaması.",
        "upstream": "https://github.com/FossifyOrg/Messages", "lisans": "GPL-3.0-only",
        "lisans_metni": "https://github.com/FossifyOrg/Messages/blob/main/LICENSE",
    },
]

def get(url, headers=None):
    h = {"User-Agent": "pulse-mirror"}
    if "api.github.com" in url and os.environ.get("GITHUB_TOKEN"):
        h["Authorization"] = "Bearer " + os.environ["GITHUB_TOKEN"]
    h.update(headers or {})
    with urllib.request.urlopen(urllib.request.Request(url, headers=h), timeout=180) as r:
        return r.read()

def sha256(b): return hashlib.sha256(b).hexdigest()

def latest_fdroid(u):
    pk = json.loads(get("https://f-droid.org/repo/index-v2.json"))["packages"][u["pkg"]]
    v = max(pk["versions"].values(), key=lambda v: v["manifest"]["versionCode"])
    signers = v["manifest"].get("signer", {}).get("sha256", [])
    return {"url": "https://f-droid.org/repo" + v["file"]["name"], "sha256": v["file"]["sha256"],
            "version": v["manifest"]["versionName"], "signers": signers}

def latest_github(u):
    rel = json.loads(get(f"https://api.github.com/repos/{u['repo']}/releases/latest"))
    a = next(a for a in rel["assets"] if u["asset"] in a["name"])
    return {"url": a["browser_download_url"], "sha256": None, "version": rel["tag_name"].lstrip("v"), "signers": []}

def apk_certs(path):
    """Certificate sha256 list via apksigner (v2/v3 aware); [] when the tool is missing."""
    tool = shutil.which("apksigner")
    if not tool:
        home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
        bt = os.path.join(home, "build-tools")
        if os.path.isdir(bt):
            for d in sorted(os.listdir(bt), reverse=True):
                if os.path.exists(os.path.join(bt, d, "apksigner")): tool = os.path.join(bt, d, "apksigner"); break
    if not tool: return []
    out = subprocess.run([tool, "verify", "--print-certs", path], capture_output=True, text=True, check=True).stdout
    return [l.split(":")[-1].strip() for l in out.splitlines() if "certificate SHA-256 digest" in l]

def mirror(u, out_dir, entries):
    info = (latest_fdroid if u["source"] == "fdroid" else latest_github)(u)
    cur = entries.get(u["id"])
    if cur and cur.get("upstream_surum") == info["version"] and os.path.exists(os.path.join(out_dir, os.path.basename(cur["url"]))):
        print(u["id"], "up to date", info["version"]); return False
    data = get(info["url"])
    if info["sha256"] and sha256(data) != info["sha256"]: sys.exit(f"{u['id']}: sha256 differs from index")
    certs = apk_certs_bytes(data)
    if certs and u["cert"] not in certs: sys.exit(f"{u['id']}: signing certificate differs from the pinned one")
    if not certs and u["cert"] not in info["signers"]: sys.exit(f"{u['id']}: certificate not verifiable, refusing")
    name = f"{u['id']}-{info['version']}.apk"
    os.makedirs(out_dir, exist_ok=True)
    for f in os.listdir(out_dir):  # keep only the newest copy
        if f.startswith(u["id"] + "-") and f.endswith(".apk"): os.remove(os.path.join(out_dir, f))
    open(os.path.join(out_dir, name), "wb").write(data)
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    entries[u["id"]] = {
        "id": u["id"], "ad_en": u["ad_en"], "ad_tr": u["ad_tr"], "tur": "apk", "kategori": u["kategori"],
        "aciklama_en": u["aciklama_en"], "aciklama_tr": u["aciklama_tr"],
        "boyut": len(data), "surum": info["version"], "url": REPO_RAW + "extras/apks/" + name, "sha256": sha256(data),
        "paket": u["pkg"], "imza_sha256": u["cert"], "upstream": u["upstream"], "upstream_surum": info["version"],
        "kaynak_url": u["upstream"], "lisans": u["lisans"], "lisans_metni": u["lisans_metni"],
        "min_pulse": MIN_PULSE, "guncellendi": now,
    }
    print(u["id"], "mirrored", info["version"], len(data), "bytes"); return True

def apk_certs_bytes(data):
    with tempfile.NamedTemporaryFile(suffix=".apk") as t:
        t.write(data); t.flush(); return apk_certs(t.name)

def main():
    ap = argparse.ArgumentParser()
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap.add_argument("--out", default=os.path.join(root, "extras", "apks"))
    ap.add_argument("--manifest", default=os.path.join(root, "extras", "manifest.json"))
    ap.add_argument("--only")
    a = ap.parse_args()
    m = json.load(open(a.manifest)) if os.path.exists(a.manifest) else {"schema": 1, "moduller": []}
    entries = {e["id"]: e for e in m["moduller"]}
    changed = False
    for u in UPSTREAMS:
        if a.only and a.only != u["id"]: continue
        changed |= mirror(u, a.out, entries)
    if changed:
        m["moduller"] = sorted(entries.values(), key=lambda e: e["id"])
        m["guncellendi"] = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        json.dump(m, open(a.manifest, "w"), ensure_ascii=False, indent=2)

if __name__ == "__main__":
    main()
