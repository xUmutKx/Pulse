#!/usr/bin/env python3
"""Builds the downloadable "ambient" relaxation sound pack (module id "ambient", tur "veri").

Run with:  python3 -I tools/build_ambient_pack.py
Needs ffmpeg (libvorbis) on PATH, stdlib only. Inputs are the raw downloads staged by
tools/dl_ambient_candidates.py (freesound previews, archive.org, Commons), looked up in SRC.
Every sound is cut to a segment, made seamless (equal-power crossfade of the tail into the head),
loudness matched, encoded as mono Ogg Vorbis, then zipped with pack.json.
Sounds that have no good source are synthesised here (style of tools/gen_whitenoise.py).

Outputs (OUT): <id>.ogg files, pack.json, ambient.zip, SOURCES.md, and ../.scratch manifest-ambient.json.
"""
import array
import hashlib
import json
import math
import os
import random
import subprocess
import sys
import wave
import zipfile

SRC = "/sdcard/Download/projects/_downloads/ambient-pack"
OUT = "/sdcard/Download/projects/paket/ambient"
MANIFEST = "/sdcard/Download/projects/.scratch/extra-apps/manifest-ambient.json"
URL = "https://github.com/xUmutKx/Pulse/releases/download/extras-ambient-1/ambient.zip"
TMP = os.path.join(SRC, "work")
RATE = 32000          # decode/encode rate for recordings
SRATE = 22050         # rate for synthesised sounds
XF_S = 3.0            # seam crossfade seconds
TARGET_RMS = 0.075    # about -22.5 dBFS
TWO_PI = 2.0 * math.pi

F = "https://freesound.org/s/"
C = "https://commons.wikimedia.org/wiki/File:"
A = "https://archive.org/details/"


def sh(args):
    subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def decode(path, start, dur, rate, filt=None):
    """Decodes [path] to mono float samples at [rate] using ffmpeg."""
    cmd = ["ffmpeg", "-v", "error", "-ss", str(start)]
    if dur:
        cmd += ["-t", str(dur)]
    cmd += ["-i", path, "-ac", "1", "-ar", str(rate)]
    if filt:
        cmd += ["-af", filt]
    cmd += ["-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout
    a = array.array("h")
    a.frombytes(raw)
    return [v / 32768.0 for v in a]


def seamless(x, rate, xf_s=XF_S):
    """Returns a loop of len(x)-xf samples whose end flows into its start (equal-power crossfade)."""
    xf = int(rate * xf_s)
    xf = min(xf, len(x) // 3)
    n = len(x) - xf
    out = x[:n]
    for i in range(xf):
        t = (i + 0.5) / xf
        fi = math.sin(t * math.pi / 2)      # head fades in
        fo = math.cos(t * math.pi / 2)      # tail (continuation of the loop end) fades out
        out[i] = x[i] * fi + x[n + i] * fo
    return out


def rms(x):
    return math.sqrt(sum(v * v for v in x) / max(1, len(x)))


def level(x, target=TARGET_RMS):
    """Scales to the target RMS, then soft-limits peaks so loud events (thunder, knocks) do not clip."""
    r = rms(x) or 1.0
    g = target / r
    return [math.tanh(v * g * 0.9) / 0.9 if abs(v * g) > 0.6 else v * g for v in x]


def write_wav(path, x, rate):
    a = array.array("h", [max(-32767, min(32767, int(v * 32767))) for v in x])
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(rate)
        w.writeframes(a.tobytes())


def encode(x, rate, out_ogg, q="3"):
    os.makedirs(TMP, exist_ok=True)
    wav = os.path.join(TMP, "enc.wav")
    write_wav(wav, x, rate)
    sh(["ffmpeg", "-y", "-v", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", q, out_ogg])


# ------------------------------------------------------------------ synthesis helpers

def lowpass(x, a):
    y = 0.0
    o = [0.0] * len(x)
    for i, v in enumerate(x):
        y += a * (v - y)
        o[i] = y
    return o


def noise_ffmpeg(color, dur, rate, seed):
    cmd = ["ffmpeg", "-v", "error", "-f", "lavfi", "-i",
           "anoisesrc=color=%s:duration=%d:sample_rate=%d:seed=%d" % (color, dur, rate, seed),
           "-ac", "1", "-f", "s16le", "-"]
    raw = subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL).stdout
    a = array.array("h")
    a.frombytes(raw)
    return [v / 32768.0 for v in a]


def snap(f, period):
    """Rounds [f] to a whole number of cycles per [period] seconds, so a tone loops with no seam."""
    return max(1, round(f * period)) / period


def synth_bowl_rub(r):
    """Singing bowl rubbed around the rim with a stick: steady partials with slow beating,
    swelling once per stick revolution, plus a faint friction hiss. Periodic, so the loop is exact."""
    T = 48.0
    n = int(SRATE * T)
    f1 = snap(196.0, T)
    modes = [(1.0, 1.0), (2.71, 0.55), (5.12, 0.30), (8.4, 0.12), (12.0, 0.05)]
    rev = T / 20.0     # one stick revolution every 2.4 s
    x = [0.0] * n
    for k, (ratio, amp) in enumerate(modes):
        fa = snap(f1 * ratio, T)
        fb = fa + snap(0.35 + 0.23 * k, T)          # split mode: the beating that makes a bowl "sing"
        ph = r.uniform(0, TWO_PI)
        sw = TWO_PI / rev
        sh_ = r.uniform(0, TWO_PI)
        for i in range(n):
            t = i / SRATE
            env = 0.62 + 0.38 * math.sin(sw * t + sh_ * 0.3 * k)
            x[i] += amp * env * (math.sin(TWO_PI * fa * t + ph) + math.sin(TWO_PI * fb * t + ph * 0.7))
    hiss = noise_ffmpeg("white", int(T), SRATE, 7)[:n]
    lp = lowpass(hiss, 0.45)
    hp = [a - b for a, b in zip(lp, lowpass(lp, 0.06))]
    for i in range(n):
        t = i / SRATE
        x[i] += 0.5 * hp[i] * (0.5 + 0.5 * math.sin(TWO_PI * t / rev))
    return x + x[:int(SRATE * XF_S)]


def synth_fan(r):
    """Desk fan: broadband air rush plus a low motor hum and a slow blade-pass flutter."""
    T = 40.0
    n = int(SRATE * T)
    air = noise_ffmpeg("pink", int(T) + 4, SRATE, 11)
    air = [a - b for a, b in zip(lowpass(air, 0.5), lowpass(air, 0.012))]
    x = [0.0] * len(air)
    f = snap(120.0, T)
    fb = snap(17.0, T)      # blade passes
    for i in range(len(air)):
        t = i / SRATE
        flutter = 1.0 + 0.12 * math.sin(TWO_PI * fb * t) + 0.05 * math.sin(TWO_PI * snap(0.4, T) * t)
        x[i] = air[i] * flutter + 0.035 * math.sin(TWO_PI * f * t) + 0.02 * math.sin(TWO_PI * 2 * f * t)
    return x[:n + int(SRATE * XF_S)]


def synth_murmur(r, voices=9, T=45.0, dense=1.0):
    """Quiet speech murmur: several far-off voices, each a stream of syllable bursts shaped by
    vowel formants with a wandering pitch, low-passed and blended. No words, just talk."""
    n = int(SRATE * (T + XF_S))
    formants = [(700, 1200), (500, 1800), (300, 2300), (450, 900), (600, 1500)]
    mix = [0.0] * n
    base = noise_ffmpeg("brown", int(T) + 4, SRATE, 3)
    for v in range(voices):
        f0 = r.uniform(95, 230)
        vib = r.uniform(0.2, 0.5)
        # syllable envelope: bursts ~ 4 per second, phrases separated by short pauses
        env = [0.0] * n
        t = r.uniform(0, 1.0)
        while t < T + XF_S:
            plen = r.uniform(0.8, 3.0) / dense
            end = t + plen
            while t < end and t < T + XF_S:
                d = r.uniform(0.09, 0.22)
                a0 = int(t * SRATE)
                a1 = min(n, int((t + d) * SRATE))
                amp = r.uniform(0.5, 1.0)
                for i in range(a0, a1):
                    env[i] = amp * math.sin(math.pi * (i - a0) / max(1, a1 - a0)) ** 1.5
                t += d + r.uniform(0.02, 0.1)
            t += r.uniform(0.6, 3.0) * 1.0 / dense
        f1, f2 = r.choice(formants)
        ph = [0.0, 0.0, 0.0]
        sig = [0.0] * n
        seg_f = (f1, f2)
        for i in range(n):
            if env[i] <= 0.0:
                continue
            tt = i / SRATE
            f = f0 * (1 + 0.06 * math.sin(TWO_PI * vib * tt + v))
            ph[0] += TWO_PI * f / SRATE
            glottal = math.sin(ph[0]) + 0.5 * math.sin(2 * ph[0]) + 0.3 * math.sin(3 * ph[0])
            form = math.sin(TWO_PI * seg_f[0] * tt) * 0.4 + math.sin(TWO_PI * seg_f[1] * tt) * 0.2
            sig[i] = env[i] * (glottal * (0.6 + form) + 0.15 * base[i % len(base)])
        sig = lowpass(sig, 0.30)
        g = r.uniform(0.5, 1.0) / math.sqrt(voices)
        for i in range(n):
            mix[i] += g * sig[i]
    return mix


# ------------------------------------------------------------------ the sounds

# Real recordings: (id, en, tr, cat, source file, start s, length s, ffmpeg filter or None,
#                   author, licence, source URL)
REAL = []
SYNTH = []


def real(id_, en, tr, cat, f, ss, dur, filt, author, lic, url, note=""):
    REAL.append(dict(id=id_, en=en, tr=tr, cat=cat, file=f, ss=ss, dur=dur, filt=filt,
                     author=author, lic=lic, url=url, note=note))


def synth(id_, en, tr, cat, fn, how):
    SYNTH.append(dict(id=id_, en=en, tr=tr, cat=cat, fn=fn, how=how))


def define():
    # nature
    real("rain", "Rain", "Yağmur", "nature", "raw/rain_723703.mp3", 0, 0, None,
         "orb1t", "CC0", F + "723703/")
    real("heavy_rain", "Heavy rain", "Sağanak", "nature", "cm/cm_Heavy_rain_in_Glenshaw_PA.ogg", 215, 60, None,
         "Sage Ross", "CC BY-SA 3.0", C + "Heavy_rain_in_Glenshaw,_PA.ogg")
    real("drizzle", "Steady rain", "Düzenli yağmur", "nature", "raw/rain_234317.mp3", 0, 0, "lowpass=f=9000",
         "nick121087", "CC0", F + "234317/")
    real("rain_roof", "Rain on the roof", "Çatıda yağmur", "nature", "raw/rain_518863.mp3", 2, 50, "lowpass=f=7000",
         "idomusics", "CC0", F + "518863/")
    real("gok_gultusu", "Thunderstorm", "Gök gürültülü fırtına", "nature", "raw/storm_704603.mp3", 0, 60, None,
         "VKProduktion", "CC0", F + "704603/")
    real("wind", "Wind", "Rüzgar", "nature", "raw/wind_697217.mp3", 0, 0, None,
         "dhallcomposer", "CC0", F + "697217/")
    real("wind_trees", "Wind in the trees", "Ağaçlarda rüzgar", "nature", "raw/wind_531016.mp3", 5, 60, None,
         "Noted451", "CC0", F + "531016/")
    real("orman", "Forest birds", "Orman kuşları", "nature", "raw/forest_723913.mp3", 0, 0, None,
         "Magnesus", "CC0", F + "723913/")
    real("forest_night", "Forest at night", "Gece ormanı", "nature", "raw/forestnight_719558.mp3", 0, 0, None,
         "fribergmusic2024", "CC0", F + "719558/")
    real("crickets", "Night crickets", "Gece cırcır böcekleri", "nature", "raw/cricket_175020.mp3", 0, 60, None,
         "sengjinn", "CC0", F + "175020/")
    # water
    real("waves", "Ocean waves", "Okyanus dalgaları", "water", "raw/waves_531015.mp3", 4, 60, None,
         "Noted451", "CC0", F + "531015/")
    real("beach", "Quiet beach", "Sakin sahil", "water", "raw/waves_376795.mp3", 5, 60, None,
         "amholma", "CC0", F + "376795/")
    real("selale", "Waterfall", "Şelale", "water", "raw/fall_274259.mp3", 0, 60, None,
         "kwahmah_02", "CC0", F + "274259/")
    real("dere", "Stream", "Dere", "water", "cm/cm_Water_flowing_pouring_trickling.ogg", 0, 60, None,
         "stephan", "Public domain", C + "Water_flowing_pouring_trickling.ogg")
    real("damlama", "Water dripping", "Su damlaması", "water", "raw/drip_177958.mp3", 5, 60, None,
         "Sclolex", "CC0", F + "177958/")
    # fire
    real("fire", "Campfire", "Kamp ateşi", "fire", "raw/fire_414767.mp3", 0, 0, None,
         "samarobryn", "CC0", F + "414767/")
    real("fireplace", "Fireplace", "Şömine", "fire", "raw/fire_370938.mp3", 0, 0, "lowpass=f=6000,volume=1.0",
         "jmehlferber", "CC0", F + "370938/")
    # city
    real("coffee", "Coffee shop", "Kahve dükkanı", "city", "raw/cafe_260062.mp3", 20, 60, None,
         "mhtaylor67", "CC0", F + "260062/")
    real("cafe_jazz", "Jazz café", "Caz kafe", "city", "raw/cafe_421647.mp3", 0, 60, None,
         "dersinnsspace", "CC0", F + "421647/")
    real("restaurant", "Restaurant", "Restoran", "city", "raw/rest_ssel.mp3", 0, 0, None,
         "SSE Library (USC)", "CC0", A + "SSE_Library_AMBIENCE")
    real("tren", "Train ride", "Tren yolculuğu", "city", "cm/cm_South_Western_Railway_456013_between_Ewell_West_and_Bookham_Jan_2022.ogg", 350, 60, None,
         "TheFrog001", "CC0", C + "South_Western_Railway_456013_between_Ewell_West_and_Bookham,_Jan_2022.ogg")
    real("crowd", "Crowd murmur", "Kalabalık uğultusu", "city", "cm/cm_Festival_concert_people_crowd.ogg", 0, 0, None,
         "stephan", "Public domain", C + "Festival_concert_people_crowd.ogg")
    real("cinema", "Cinema hall", "Sinema salonu", "city", "cm/cm_Festival_concert_people_crowd.ogg", 0, 0,
         "lowpass=f=1100,aecho=0.8:0.6:60|120:0.35|0.2",
         "stephan", "Public domain", C + "Festival_concert_people_crowd.ogg",
         "derived: the crowd recording muffled and given room echo")
    real("station", "Train station", "Tren garı", "city", "raw/station_193791.mp3", 0, 0, None,
         "qubodup", "CC0", F + "193791/")
    # cozy
    real("typing", "Keyboard typing", "Klavye sesi", "cozy", "cm/cm_Laptop_keyboard.ogg", 0, 60, None,
         "russiandoll", "Public domain", C + "Laptop_keyboard.ogg")
    # instruments
    real("bell", "Wind chimes", "Rüzgar çanı", "instruments", "raw/chimes_440929.mp3", 0, 60, None,
         "mooncubedesign", "CC0", F + "440929/")
    real("bowl", "Singing bowl", "Şarkı söyleyen çanak", "instruments", "raw/bowl_449950.mp3", 0, 0, None,
         "steffcaffrey", "CC0", F + "449950/")
    # synthesised
    synth("bowl_rub", "Tibetan bowl, rubbed", "Tibet çanağı, sürtme", "instruments", synth_bowl_rub,
          "additive bowl modes with beating, swelling once per stick revolution, plus friction hiss")
    synth("fan", "Desk fan", "Vantilatör", "cozy", synth_fan,
          "filtered pink noise air rush, motor hum and blade-pass flutter")
    synth("konusma", "Quiet murmur", "Sessiz mırıltı", "city", synth_murmur,
          "nine distant voices built from formant-shaped syllable bursts (no words)")
    synth("pembe_gurultu", "Pink noise", "Pembe gürültü", "noise", lambda r: noise_ffmpeg("pink", 43, SRATE, 21),
          "ffmpeg anoisesrc pink")
    synth("brown", "Brown noise", "Kahverengi gürültü", "noise", lambda r: noise_ffmpeg("brown", 43, SRATE, 22),
          "ffmpeg anoisesrc brown")
    synth("white", "Soft white noise", "Yumuşak beyaz gürültü", "noise",
          lambda r: lowpass(noise_ffmpeg("white", 43, SRATE, 23), 0.35),
          "white noise, gentle low-pass")


def licence_summary():
    return sorted({s["lic"] for s in REAL})


def main():
    define()
    only = set(sys.argv[1:])
    os.makedirs(OUT, exist_ok=True)
    os.makedirs(TMP, exist_ok=True)
    sounds = []
    failed = []
    for s in REAL:
        if only and s["id"] not in only:
            continue
        path = os.path.join(SRC, s["file"])
        if not os.path.exists(path) or os.path.getsize(path) == 0:
            failed.append(s["id"])
            print("MISSING", s["id"], s["file"])
            continue
        x = decode(path, s["ss"], s["dur"], RATE, s["filt"])
        x = seamless(x, RATE)
        x = level(x)
        encode(x, RATE, os.path.join(OUT, s["id"] + ".ogg"))
        print("real", s["id"], round(len(x) / RATE, 1), "s")
    for s in SYNTH:
        if only and s["id"] not in only:
            continue
        r = random.Random(hash(s["id"]) & 0xFFFF if False else sum(map(ord, s["id"])))
        x = s["fn"](r)
        x = seamless(x, SRATE) if s["id"] not in ("bowl_rub", "fan") else x[:len(x) - int(SRATE * XF_S)]
        x = level(x)
        encode(x, SRATE, os.path.join(OUT, s["id"] + ".ogg"), q="3")
        print("synth", s["id"], round(len(x) / SRATE, 1), "s")
    if only:
        return
    # pack.json lists only what exists
    entries = []
    for s in REAL + SYNTH:
        if os.path.exists(os.path.join(OUT, s["id"] + ".ogg")) and s["id"] not in failed:
            entries.append({"id": s["id"], "en": s["en"], "tr": s["tr"], "file": s["id"] + ".ogg", "cat": s["cat"]})
    pj = os.path.join(OUT, "pack.json")
    with open(pj, "w", encoding="utf-8") as f:
        json.dump({"sounds": entries}, f, ensure_ascii=False, indent=1)
    zp = os.path.join(OUT, "ambient.zip")
    with zipfile.ZipFile(zp, "w", zipfile.ZIP_STORED) as z:     # ogg is already compressed
        z.write(pj, "pack.json")
        for e in entries:
            z.write(os.path.join(OUT, e["file"]), e["file"])
    data = open(zp, "rb").read()
    sha = hashlib.sha256(data).hexdigest()
    ok_ids = {e["id"] for e in entries}
    authors = sorted({"%s (%s)" % (s["author"], s["lic"]) for s in REAL if s["id"] in ok_ids})
    write_sources(entries)
    mod = {
        "id": "ambient", "ad_en": "Relaxation sounds", "ad_tr": "Rahatlatıcı sesler",
        "tur": "veri", "kategori": "ses", "boyut": len(data), "surum": "1.0.0", "url": URL, "sha256": sha,
        "lisans": "CC0-1.0",
        "lisans_metni": "https://github.com/xUmutKx/Pulse/blob/main/extras/licenses/ambient-SOURCES.md",
        "atif": "Recordings by " + ", ".join(authors) + ". Synthesised sounds by Pulse. Full list in SOURCES.md.",
        "min_pulse": 10094, "guncellendi": "2026-10-18T00:00:00Z",
        "icerik": [{"yol": e["file"], "boyut": os.path.getsize(os.path.join(OUT, e["file"]))} for e in entries],
    }
    with open(MANIFEST, "w", encoding="utf-8") as f:
        json.dump(mod, f, ensure_ascii=False, indent=1)
    print("sounds", len(entries), "zip", len(data), "sha256", sha, "missing", failed)


def write_sources(entries):
    ok = {e["id"] for e in entries}
    L = ["# Ambient pack sources", "",
         "Pack `ambient` 1.0.0. Recordings were cut, crossfaded into seamless loops, level-matched and encoded",
         "as mono Ogg Vorbis by `tools/build_ambient_pack.py`. Freesound items were taken from the public HQ",
         "preview (the original WAV needs a login), so they are 128 kbps MP3 sources re-encoded.", "",
         "## Real recordings", "", "| id | author | licence | source |", "|---|---|---|---|"]
    for s in REAL:
        if s["id"] in ok:
            L.append("| %s | %s | %s | %s |" % (s["id"], s["author"], s["lic"], s["url"]))
    L += ["", "## Synthesised (no licence needed, made by Pulse)", "", "| id | how |", "|---|---|"]
    for s in SYNTH:
        if s["id"] in ok:
            L.append("| %s | %s |" % (s["id"], s["how"]))
    L += ["", "Licences: CC0 and public domain need no credit; any CC BY / CC BY-SA entries above must keep their credit.", ""]
    with open(os.path.join(OUT, "SOURCES.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(L))


if __name__ == "__main__":
    main()
