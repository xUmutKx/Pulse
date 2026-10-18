"""Builds paket/skins/skins.zip (pack.json + files/*.wsz), SOURCES.md and manifest-skins.json.
Usage: python3 -I build_skin_pack.py <raw_dir_with_wsz>   (fetch them first with fetch_skins.py)
Skins whose zip has no main.bmp are skipped. Nothing is published."""
import hashlib, json, os, sys, zipfile

RAW = sys.argv[1]
OUT = "/sdcard/Download/projects/paket/skins"
MANIFEST = "/sdcard/Download/projects/.scratch/extra-apps/manifest-skins.json"
# file -> (id, en, tr, author/source)
SKINS = {
    "base-2.91.wsz": ("base", "Winamp Base 2.91", "Winamp Temel 2.91", "Nullsoft, via webamp-demo"),
    "Windows_Classic.wsz": ("windows-classic", "Windows Classic", "Windows Klasik", "archive.org winampskin_Windows_Classic"),
    "Internet-Archive.wsz": ("internet-archive", "Internet Archive", "Internet Archive", "webamp-demo"),
    "Skinner_Atlas.wsz": ("skinner-atlas", "Skinner Atlas", "Skinner Atlas", "webamp-demo"),
    "TopazAmp1-2.wsz": ("topazamp", "TopazAmp", "TopazAmp", "webamp-demo"),
    "Vizor1-01.wsz": ("vizor", "Vizor", "Vizor", "webamp-demo"),
    "XMMS-Turquoise.wsz": ("xmms-turquoise", "XMMS Turquoise", "XMMS Turkuaz", "webamp-demo"),
    "ZaxonRemake1-0.wsz": ("zaxon", "Zaxon Remake", "Zaxon Remake", "webamp-demo"),
    "Green-Dimension-V2.wsz": ("green-dimension", "Green Dimension", "Yeşil Boyut", "webamp-demo"),
    "AmigaPPC-dark.wsz": ("amiga-dark", "Amiga PPC Dark", "Amiga PPC Koyu", "webamp-demo"),
    "My_Funny_Valentine.wsz": ("valentine", "My Funny Valentine", "My Funny Valentine", "webamp-demo"),
    "MpAmp.wsz": ("mpamp", "MpAmp", "MpAmp", "archive.org winampskin_MpAmp"),
    "3D_Show_II.wsz": ("3d-show", "3D Show II", "3D Show II", "archive.org winampskin_3D_Show_II"),
    "Mistletoe.wsz": ("mistletoe", "Mistletoe", "Mistletoe", "archive.org winampskins_Mistletoe"),
    "HackerS_kin_1.3_-_by_Mickey_Knox.wsz": ("hackerskin", "HackerS_kin", "HackerS_kin", "Mickey Knox, archive.org"),
    "Italo_Disco_Yellow_Skin.wsz": ("italo-disco", "Italo Disco Yellow", "Italo Disco Sarı", "archive.org"),
    "amp_ayu11.wsz": ("amp-ayu", "Amp Ayu", "Amp Ayu", "archive.org winampskins_amp_ayu11"),
    "bunji_GEM-water.wsz": ("gem-water", "GEM Water", "GEM Su", "bunji, archive.org"),
    "je_taime.wsz": ("je-taime", "Je t'aime", "Je t'aime", "archive.org winampskins_je_taime"),
    "jww_boreas.wsz": ("boreas", "Boreas", "Boreas", "jww, archive.org"),
    "Chimera1-0.wsz": ("chimera", "Chimera", "Chimera", "archive.org winampskins_Chimera1-0"),
}

os.makedirs(OUT, exist_ok=True)
pack, src = [], ["# Skin sources (licences not stated by the authors; classic Winamp skins are community art)\n"]
zpath = os.path.join(OUT, "skins.zip")
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_STORED) as z:  # wsz is already deflated
    for f, (i, en, tr, by) in SKINS.items():
        p = os.path.join(RAW, f)
        if not os.path.exists(p):
            print("missing", f); continue
        try:
            names = {n.split("/")[-1].lower() for n in zipfile.ZipFile(p).namelist()}
        except zipfile.BadZipFile:
            print("bad zip", f); continue
        if "main.bmp" not in names:
            print("no main.bmp", f); continue
        z.write(p, "files/%s.wsz" % i)
        pack.append({"id": i, "en": en, "tr": tr, "file": "files/%s.wsz" % i})
        src.append("- %s: %s\n" % (en, by))
    z.writestr("pack.json", json.dumps({"skins": pack}, ensure_ascii=False, indent=1))
open(os.path.join(OUT, "SOURCES.md"), "w").write("".join(src))
data = open(zpath, "rb").read()
man = {"id": "skins", "ad_en": "Winamp skins", "ad_tr": "Winamp temaları", "tur": "veri", "kategori": "skin",
       "boyut": len(data), "surum": "1.0.0",
       "url": "https://github.com/xUmutKx/Pulse/releases/download/extras-skins-1/skins.zip",
       "sha256": hashlib.sha256(data).hexdigest(), "lisans": "LicenseRef-community-skins",
       "lisans_metni": "https://github.com/xUmutKx/Pulse/blob/main/extras/licenses/skins-SOURCES.md",
       "atif": "Winamp skin authors; see SOURCES.md. Sources: Webamp demo skins, Internet Archive winampskins",
       "min_pulse": 120, "guncellendi": "2026-10-18T00:00:00Z", "yayinlandi": False}
json.dump(man, open(MANIFEST, "w"), ensure_ascii=False, indent=1)
print(len(pack), "skins,", len(data), "bytes")
