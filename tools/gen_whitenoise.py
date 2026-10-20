#!/usr/bin/env python3
"""Generates the extra ambient loops into app/src/main/assets/ambient (stdlib only, run with python3 -I).

Mono 22050 Hz, 16-bit PCM WAV, 12 s each, listed in that folder's pack.json. Each loop is built as a
longer stream and its first XF seconds are equal-power crossfaded with the stream's continuation, so
the end joins the start. LFO periods divide the loop length so the swells repeat exactly.
"""
import math
import os
import random
import struct
import wave

RATE = 22050
LOOP = int(RATE * 12.0)      # loop length in samples
XF = int(RATE * 1.0)         # seam crossfade in samples
TOTAL = LOOP + XF            # stream length the generators fill
PEAK_DB = -3.0
TWO_PI = 2.0 * math.pi
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "ambient")


def white(r):
    return [r.uniform(-1.0, 1.0) for _ in range(TOTAL)]


def lowpass(x, a):
    y = 0.0
    out = [0.0] * len(x)
    for i, v in enumerate(x):
        y += a * (v - y)
        out[i] = y
    return out


def highpass(x, a):
    lp = lowpass(x, a)
    return [v - l for v, l in zip(x, lp)]


def brown(r):
    b = 0.0
    out = [0.0] * TOTAL
    for i in range(TOTAL):
        b = (b + 0.02 * r.uniform(-1.0, 1.0)) / 1.02
        out[i] = b
    return out


def add_burst(x, start, length, amp, tau, r, tone_hz=None):
    """Adds one decaying transient at [start]; noise by default, a sine when tone_hz is given."""
    for j in range(min(length, TOTAL - start)):
        env = amp * math.exp(-j / tau)
        if tone_hz is None:
            x[start + j] += env * r.uniform(-1.0, 1.0)
        else:
            x[start + j] += env * math.sin(TWO_PI * tone_hz * j / RATE)


def lfo(t, period, phase=0.0):
    return 0.5 + 0.5 * math.sin(TWO_PI * t / period + phase)


def gen_steam(r):
    # kettle-like hiss: 1-3 kHz band with a slow boil flutter
    band = [a - b for a, b in zip(lowpass(white(r), 0.9), lowpass(white(r), 0.3))]
    out = []
    for i in range(TOTAL):
        t = i / RATE
        out.append(band[i] * (0.5 + 0.3 * lfo(t, 3.0) + 0.2 * lfo(t, 6.0, 0.4)))
    return out


def gen_heartbeat(r):
    # soft body bed with a lub-dub at 60 bpm; beats run to 12 s so the seam carries one into the start
    x = [0.3 * v for v in brown(r)]
    for k in range(13):
        s = k * RATE
        add_burst(x, s, int(RATE * 0.25), 1.0, RATE * 0.04, r, tone_hz=55.0)
        add_burst(x, s + int(RATE * 0.3), int(RATE * 0.2), 0.7, RATE * 0.035, r, tone_hz=60.0)
    return x


def gen_vinyl(r):
    # record surface: quiet high hiss, about 40 tiny clicks per second and a few louder pops
    hiss = [0.03 * v for v in highpass(white(r), 0.4)]
    for _ in range(int(12.0 * 40)):
        k = r.randrange(TOTAL)
        add_burst(hiss, k, int(RATE * 0.002), r.uniform(0.2, 1.0) ** 2, RATE * 0.0004, r)
    for _ in range(12):
        k = r.randrange(TOTAL)
        add_burst(hiss, k, int(RATE * 0.02), 1.5, RATE * 0.004, r)
    return hiss


def gen_traffic(r):
    # distant road: low rumble with slow swells
    rumble = lowpass(white(r), 0.02)
    out = []
    for i in range(TOTAL):
        t = i / RATE
        out.append(rumble[i] * (0.35 + 0.4 * lfo(t, 6.0) + 0.25 * lfo(t, 4.0, 2.0)))
    return out


GENERATORS = [
    ("steam", gen_steam),
    ("heartbeat", gen_heartbeat),
    ("vinyl", gen_vinyl),
    ("traffic", gen_traffic),
]


def make_loop(x):
    """Crossfades the first XF samples with the stream continuation so sample LOOP-1 joins sample 0."""
    out = x[:LOOP]
    for i in range(XF):
        t = i / XF
        out[i] = x[i] * math.sin(t * math.pi / 2) + x[LOOP + i] * math.cos(t * math.pi / 2)
    return out


def write_wav(path, samples):
    peak = max(abs(v) for v in samples) or 1.0
    scale = (10.0 ** (PEAK_DB / 20.0)) / peak
    pcm = struct.pack("<%dh" % len(samples), *[int(round(v * scale * 32767)) for v in samples])
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(pcm)


def main():
    os.makedirs(OUT, exist_ok=True)
    total = 0
    for seed, (name, gen) in enumerate(GENERATORS, start=1):
        r = random.Random(seed)
        path = os.path.join(OUT, name + ".wav")
        write_wav(path, make_loop(gen(r)))
        size = os.path.getsize(path)
        total += size
        print("%-12s %8d bytes" % (name, size))
    print("total        %8d bytes (%.2f MB)" % (total, total / 1e6))


if __name__ == "__main__":
    main()
