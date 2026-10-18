#!/usr/bin/env python3
"""Generates the white-noise loops for app/src/main/res/raw (stdlib only, run with python3 -I).

Mono 22050 Hz, 16-bit PCM WAV, about 12 s each. Each loop is built as a longer stream and its
first XF seconds are equal-power crossfaded with the stream's continuation, so the end joins the start.
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
PEAK_DB = -6.0
TWO_PI = 2.0 * math.pi
HERE = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(HERE, "..", "app", "src", "main", "res", "raw")


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


def gen_white(r):
    return white(r)


def gen_rain(r):
    hiss = [0.35 * v for v in highpass(white(r), 0.25)]
    # droplets: short bright clicks, 60 per second on average
    for _ in range(int(12.0 * 60)):
        k = r.randrange(TOTAL)
        add_burst(hiss, k, int(RATE * 0.004), r.uniform(0.3, 1.0) ** 2, RATE * r.uniform(0.0004, 0.0012), r)
    return hiss


def gen_fire(r):
    rumble = [0.8 * v for v in brown(r)]
    hiss = [0.05 * v for v in lowpass(white(r), 0.15)]
    x = [a + b for a, b in zip(rumble, hiss)]
    # crackles: about 25 per second, power-distributed amplitude, a few ms long
    for _ in range(int(12.0 * 25)):
        k = r.randrange(TOTAL)
        amp = 1.4 * r.random() ** 3
        add_burst(x, k, r.randint(40, 200), amp, r.uniform(5.0, 40.0), r)
    return x


def gen_wind(r):
    rumble = lowpass(white(r), 0.012)
    mid = [0.3 * v for v in lowpass(white(r), 0.05)]
    out = []
    for i in range(TOTAL):
        t = i / RATE
        gust = 0.35 + 0.35 * lfo(t, 5.3) + 0.3 * lfo(t, 13.7, 1.3)
        out.append((rumble[i] + mid[i]) * gust)
    return out


def gen_waves(r):
    surf = lowpass(white(r), 0.04)
    foam = [0.25 * v for v in highpass(white(r), 0.3)]
    out = []
    for i in range(TOTAL):
        t = i / RATE
        swell = 0.15 + 0.85 * lfo(t, 7.0) ** 1.5   # ~7 s swell
        out.append((surf[i] + foam[i] * 0.4) * swell)
    return out


def gen_coffee(r):
    # murmur: band-passed noise with slow random-ish level changes
    band = [a - b for a, b in zip(lowpass(white(r), 0.15), lowpass(white(r), 0.02))]
    x = []
    for i in range(TOTAL):
        t = i / RATE
        env = 0.55 + 0.25 * lfo(t, 1.8) + 0.2 * lfo(t, 3.1, 0.7)
        x.append(band[i] * env)
    # soft cup clinks: a few per loop, 2.8-4.2 kHz, 30 ms decay
    for _ in range(5):
        k = r.randrange(TOTAL)
        add_burst(x, k, int(RATE * 0.12), 0.35, RATE * 0.03, r, tone_hz=r.uniform(2800.0, 4200.0))
    return x


def gen_bell(r):
    # 440 / 880 / 1320 Hz partials, struck every 3 s; a strike at the stream end lands on the loop start
    partials = [(440.0, 1.0, 1.6), (880.0, 0.45, 0.9), (1320.0, 0.22, 0.55)]
    x = [0.0] * TOTAL
    every = int(RATE * 3.0)
    span = int(RATE * 4.0)
    for start in range(0, TOTAL, every):
        for j in range(min(span, TOTAL - start)):
            t = j / RATE
            attack = min(1.0, t / 0.002)
            v = sum(a * math.exp(-t / d) * math.sin(TWO_PI * f * t) for f, a, d in partials)
            x[start + j] += 0.5 * attack * v
    return x


GENERATORS = [
    ("wn_white", gen_white),
    ("wn_coffee", gen_coffee),
    ("wn_bell", gen_bell),
    ("wn_rain", gen_rain),
    ("wn_fire", gen_fire),
    ("wn_wind", gen_wind),
    ("wn_waves", gen_waves),
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
    os.makedirs(RAW, exist_ok=True)
    total = 0
    for seed, (name, gen) in enumerate(GENERATORS, start=1):
        r = random.Random(seed)
        path = os.path.join(RAW, name + ".wav")
        write_wav(path, make_loop(gen(r)))
        size = os.path.getsize(path)
        total += size
        print("%-12s %8d bytes" % (name, size))
    print("total        %8d bytes (%.2f MB)" % (total, total / 1e6))


if __name__ == "__main__":
    main()
