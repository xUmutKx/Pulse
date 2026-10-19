"""Rebuilds the gentle rain set and unpacks packs into app assets. Needs ffmpeg (libvorbis). Run by hand.
Inputs: paket/ambient/*.ogg and paket/birds/birds.zip. Output: app/src/main/assets/{ambient,birds}/ (+ pack.json)."""
import json, os, shutil, subprocess, zipfile
P = "/sdcard/Download/projects/paket"
A = "/root/projects/Pulse/app/src/main/assets"
W = "/sdcard/Download/projects/_downloads/ambient-fix/rain"

def ff(*a): subprocess.run(["ffmpeg", "-y", "-v", "error", *a], check=True)
def enc(args, out): ff(*args, "-ac", "1", "-ar", "32000", "-c:a", "libvorbis", "-q:a", "3", out)

def loop(src, a, b, xf, out):
    """Cuts [a,b] from src and crossfades the last xf s into the first xf s: a seamless loop of b-a-xf s."""
    ff("-i", src, "-filter_complex",
       f"[0]atrim={a}:{b},asetpts=PTS-STARTPTS,asplit=3[x][y][z];"
       f"[x]atrim={b-a-xf}:{b-a},asetpts=PTS-STARTPTS[t];[y]atrim=0:{xf},asetpts=PTS-STARTPTS[h];"
       f"[z]atrim={xf}:{b-a-xf},asetpts=PTS-STARTPTS[m];[t][h]acrossfade=d={xf}:c1=qsin:c2=qsin[c];[c][m]concat=n=2:v=0:a=1",
       "-ar", "32000", out)

def main():
    for d in ("ambient", "birds"):
        shutil.rmtree(f"{A}/{d}", ignore_errors=True); os.makedirs(f"{A}/{d}")
    zipfile.ZipFile(f"{P}/birds/birds.zip").extractall(f"{A}/birds")
    for f in os.listdir(f"{P}/ambient"):
        if f.endswith(".ogg") or f == "pack.json": shutil.copy(f"{P}/ambient/{f}", f"{A}/ambient/{f}")
    S = f"{P}/ambient"
    # gentle rain: soft low-passed bed + sparse drops
    loop(f"{S}/damlama.ogg", 0, 40, 2, f"{W}/drops.wav")
    loop(f"{S}/drizzle.ogg", 0, 40, 2, f"{W}/bed.wav")
    enc(["-i", f"{W}/bed.wav", "-i", f"{W}/drops.wav", "-filter_complex",
         "[0]lowpass=f=2800,volume=-9dB[b];[1]lowpass=f=5000,volume=-8dB[d];"
         "[b][d]amix=inputs=2:normalize=0,alimiter=limit=0.5,volume=-3dB"], f"{A}/ambient/rain.ogg")
    # other water sounds: no true clipping, softer top end
    for i, (src, flt) in {"heavy_rain": ("heavy_rain", "lowpass=f=4500,volume=-6dB"), "drizzle": ("drizzle", "lowpass=f=3500,volume=-5dB"),
                          "rain_roof": ("rain_roof", "lowpass=f=3500,volume=-5dB"), "damlama": ("damlama", "volume=-7dB,alimiter=limit=0.7"),
                          "dere": ("dere", "volume=-7dB,alimiter=limit=0.7"), "selale": ("selale", "lowpass=f=5000,volume=-7dB,alimiter=limit=0.7")}.items():
        enc(["-i", f"{S}/{src}.ogg", "-af", flt], f"{A}/ambient/{i}.ogg")
    # pack.json: relabel
    pj = f"{A}/ambient/pack.json"; d = json.load(open(pj))
    ren = {"rain": ("Rain", "Yağmur"), "drizzle": ("Light rain", "Hafif yağmur"), "heavy_rain": ("Downpour", "Sağanak"),
           "gok_gultusu": ("Thunderstorm", "Gök gürültülü fırtına")}
    for s in d["sounds"]:
        if s["id"] in ren: s["en"], s["tr"] = ren[s["id"]]
        if s["id"] in ("rain", "drizzle", "damlama", "dere", "selale", "rain_roof", "heavy_rain", "gok_gultusu"): s["cat"] = "water"
    json.dump(d, open(pj, "w"), ensure_ascii=False, indent=1)

main()
