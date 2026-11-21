/*
 * Adapted for Pulse from KMP Odd Scroll's CD Rack by mejdi14 (https://github.com/mejdi14/KMPOddScroll), GPL-3.0.
 * Changes for Pulse: moved into this package, the Kotlin Multiplatform expect/actual of the shader merged into one Android file.
 */
package com.umutk.pulse.extras.gamehub.cdshelf

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap

/**
 * A spark is drawn into its layer as a fraction of this: past it, the
 * star it throws is as large as it gets.
 */
private const val SPARK = """
const float SPARK_RANGE = 32.0;
"""

/*
 * The reference's shaders, carried over to AGSL. Its studio is analytic — a
 * dark room with softboxes and flags, a lit backdrop behind the case — so it
 * is evaluated here as it stands, in world space, rather than baked to a cube
 * map. World is the camera's own frame: y up, z towards the viewer.
 */
private const val STUDIO = SPARK + """
const float IOR = 1.59;
const float ABSORB = 0.00327;
const float3 KEY = float3(-0.625, 0.218, 0.75);
const float3 STRIP = float3(0.725, 0.028, 0.688);
const float3 TOP = float3(0.031, 0.786, 0.618);
const float3 BOUNCE = float3(0.069, -0.722, 0.688);

float box(float2 p, float2 c, float2 hs, float soft) {
    float2 q = abs(p - c) - hs;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
    return 1.0 - smoothstep(-soft, soft, d);
}

float band(float q, float c, float hw, float soft) {
    return 1.0 - smoothstep(hw - soft, hw + soft, abs(q - c));
}

float flags(float q) {
    float k = band(q, 0.27, 0.07, 0.02);
    k = max(k, band(q, 0.74, 0.12, 0.03));
    k = max(k, band(q, -0.32, 0.08, 0.02));
    k = max(k, band(q, -0.86, 0.14, 0.03));
    return k;
}

float hot(float q) {
    return band(q, 0.48, 0.07, 0.02) + band(q, 1.08, 0.14, 0.03) + band(q, -0.56, 0.08, 0.02)
        + 0.8 * band(q, -1.25, 0.15, 0.03);
}

float3 room(float3 d) {
    d = normalize(d);
    float2 p = d.xy / max(abs(d.z), 0.08);
    float3 dim = float3(0.02, 0.021, 0.026);
    if (d.z < 0.0) {
        float3 c = float3(0.1, 0.103, 0.112) * (1.0 - 0.5 * smoothstep(0.4, 1.6, length(p)));
        c += float3(0.8) * (1.0 - smoothstep(0.05, 0.14, length(p)));
        c *= 1.0 - 0.97 * max(flags(p.x), flags(p.y));
        c += float3(2.4, 2.6, 3.0) * (hot(p.x) + hot(p.y));
        return mix(dim, c, smoothstep(0.0, 0.2, -d.z));
    }
    float3 c = dim * (0.5 + 0.5 * smoothstep(-1.2, 1.6, p.y));
    c += float3(0.38) * smoothstep(-0.7, 0.8, p.y) * (1.0 - smoothstep(0.5, 1.0, length(p)));
    c += float3(1.0, 0.99, 0.97) * 24.0 * box(p, float2(-0.86, 0.3), float2(0.065, 0.85), 0.035);
    c += float3(0.96, 0.98, 1.0) * 20.0 * box(p, float2(1.06, 0.04), float2(0.06, 1.15), 0.03);
    c += float3(1.0) * 13.0 * box(p, float2(0.05, 1.28), float2(1.25, 0.055), 0.03);
    c += float3(0.92, 0.94, 0.97) * 2.4 * box(p, float2(0.1, -1.05), float2(1.15, 0.24), 0.28);
    c *= 1.0 - 0.92 * box(p, float2(-0.2, 0.5), float2(0.055, 1.4), 0.02);
    c *= 1.0 - 0.85 * box(p, float2(0.62, -0.35), float2(0.04, 0.9), 0.02);
    return mix(dim, c, smoothstep(0.0, 0.2, d.z));
}

float backdrop(float3 d) {
    d = normalize(d);
    float2 p = d.xy / max(abs(d.z), 0.08);
    float lit = 1.0 - smoothstep(0.3, 0.68, length(p * float2(0.92, 1.0)));
    return d.z < 0.0 ? lit * smoothstep(0.0, 0.2, -d.z) : 0.0;
}

float3 studio(float3 d) {
    return mix(room(d), float3(1.0), backdrop(d));
}

/*
 * The reference's small lamps, hung in the studio apart from its softboxes:
 * too small to show in the room, and what the rods' sparks are of. A lamp
 * is kept to its own little disc while that is wider than what a pixel sees
 * of the room, [spread]; once the pixel sees more, its light is spread into
 * a soft spot as wide, all of it kept, or a glint on a rod's rolled edge
 * would fall between pixels.
 */
const float LAMP = 12000.0;
const float LAMP_R = 0.01;

float lamp(float2 p, float2 at, float spread) {
    float s = max(spread, LAMP_R * 0.55);
    float2 q = p - at;
    return min(1.0, 0.28 * LAMP_R * LAMP_R / (s * s)) * exp(-dot(q, q) / (2.0 * s * s));
}

/** How much lamp light comes in along [d], the mean of its colour, as the reference keeps it beside the room. */
float lamps(float3 d, float spread) {
    d = normalize(d);
    float2 p = d.xy / max(abs(d.z), 0.08);
    if (d.z > 0.0) {
        float c = 1.123 * lamp(p, float2(-0.42, 0.98), spread) + 0.97 * lamp(p, float2(0.62, 0.72), spread)
            + 0.867 * (lamp(p, float2(-0.95, -0.5), spread) + lamp(p, float2(0.85, -0.7), spread));
        return c * smoothstep(0.0, 0.2, d.z) * LAMP;
    }
    float c = 0.173 * (lamp(p, float2(-0.62, 0.58), spread) + lamp(p, float2(0.66, -0.6), spread));
    return c * smoothstep(0.0, 0.2, -d.z) * LAMP;
}

float3 glare(float3 d) {
    return max(studio(d) - float3(0.45), float3(0.0));
}

float fresnel(float cosv, float n) {
    float f0 = (n - 1.0) / (n + 1.0);
    f0 *= f0;
    return f0 + (1.0 - f0) * pow(1.0 - clamp(cosv, 0.0, 1.0), 5.0);
}

float3 toSrgb(float3 c) {
    c = clamp(c, float3(0.0), float3(1.0));
    return mix(c * 12.92, 1.055 * pow(c, float3(1.0 / 2.4)) - 0.055, step(float3(0.0031308), c));
}

float3 toLinear(float3 c) {
    return pow(max(c, float3(0.0)), float3(2.2));
}

/** A flat sheet of the plastic: what it reflects, and how much of what is behind it it takes. */
half4 sheet(float3 N, float3 V, float thickness, float k) {
    float cosv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float F = fresnel(cosv, IOR);
    float F0 = fresnel(1.0, IOR);
    float Fall = F + (1.0 - F) * (1.0 - F) * F;
    float Fall0 = F0 + (1.0 - F0) * (1.0 - F0) * F0;
    float cosT = sqrt(max(1.0 - (1.0 - cosv * cosv) / (IOR * IOR), 0.0));
    float absorbed = 1.0 - exp(-ABSORB * thickness / max(cosT, 0.2));
    float takes = clamp(max(Fall - Fall0, 0.0) + Fall0 * 0.05 + absorbed * 0.5, 0.0, 1.0);
    float3 R = reflect(-V, N);
    float3 refl = min(glare(R) * Fall * 0.25, float3(0.1)) + min(room(R), float3(1.2)) * max(Fall - Fall0, 0.0);
    float3 col = refl + float3(0.86, 0.9, 0.93) * (1.0 - cosv) * 0.03;
    return half4(half3(toSrgb(col)), half(takes)) * half(k);
}
"""

/**
 * A layer of the case's clear plastic, over its height map.
 *
 * The map holds each pixel's signed distance in from the nearest rod's edge,
 * and the radius that rod's section is rounded to. Down the middle of a rod,
 * further in than that radius, its face is flat; towards the edge it rolls
 * over on the radius until it stands upright — so the normal is worked out
 * exactly from the distance and the way it rises, and the edge is
 * antialiased off the same distance. For the light through it the section is
 * taken for round: the view refracts in, crosses the section,
 * and either leaves — bent into the dark room, or, if it carries on near
 * enough the way it came, onto the page or the disc behind — or reflects and
 * crosses again, three times over, as the reference's rods do. What the rod
 * throws back off its surface is the studio, by Fresnel. A sheet reflects the
 * studio's glare and takes a little of what lies behind it.
 *
 * With [sparks] set, the layer is drawn for the stars instead: how much of
 * the studio's lamps the rod catches, off its surface and through it, as a
 * grey out of [SPARK_RANGE]. The sheets give none, and let what is behind
 * them through. [sparks] is how many of the map's pixels one of that layer's
 * spans, so a lamp is spread over as much of the room as the pixel sees.
 */
private const val GLASS = STUDIO + """
uniform shader height;
uniform float4 frame;
uniform float3 eye;
uniform float3x3 toWorld;
uniform float thickness;
uniform float overDisc;
uniform float sparks;

/*
 * The studio's room is all but black away from its softboxes, and a rod
 * turned side on shows little else — so its edge, and the whole of a thin
 * rod, came out as a hard black line. The darks are lifted towards a pale
 * grey instead, and further the thinner the rod is on screen; the bright
 * reflections are left as they are.
 */
const float3 LIFT_TO = float3(0.7, 0.72, 0.75);
const float LIFT_WIDE = 0.7;
const float LIFT_THIN = 0.92;

float distanceAt(float2 xy) {
    float4 c = float4(height.eval(xy));
    return c.a > 0.004 ? (c.r / c.a - 0.5) * 8.0 : -4.0;
}

/** How much of what lies behind the case a ray leaving the rod along [way] lands on, rather than the room. */
float behind(float3 way, float3 I) {
    if (way.z > -0.05) return 0.0;
    float bend = acos(clamp(dot(way, I), -1.0, 1.0));
    return 1.0 - smoothstep(0.04, 0.13, bend);
}

float3 outside(float3 way, float3 I, float3 seen, float matter) {
    float3 env = room(way);
    float w = behind(way, I);
    if (w <= 0.0) return env;
    float3 page = mix(env, seen, 0.25);
    return mix(env, mix(page, seen, matter), w);
}

half4 main(float2 xy) {
    float4 raw = float4(height.eval(xy));
    float cover = raw.a;
    if (cover < 0.004) return half4(0.0);
    float3 m = raw.rgb / cover;
    float2 mm = frame.xy + xy * frame.z;
    float3 Vl = normalize(eye - float3(mm, frame.w));
    float side = Vl.z >= 0.0 ? 1.0 : -1.0;
    float3 V = normalize(toWorld * Vl);
    float3 I = -V;

    float d = (m.r - 0.5) * 8.0;
    float rodCover = clamp(d / frame.z + 0.5, 0.0, 1.0);
    bool sparking = sparks > 0.0;
    half4 plate = m.g > 0.004 && !sparking
        ? sheet(normalize(toWorld * float3(0.0, 0.0, side)), V, thickness, m.g * cover)
        : half4(0.0);
    if (rodCover <= 0.0) return plate;

    // Which way is in, from how the distance rises; how far over the section
    // has rolled, from how near the edge this is against its corner radius.
    float s = 1.0;
    float2 rise = float2(
        distanceAt(xy + float2(s, 0.0)) - distanceAt(xy - float2(s, 0.0)),
        distanceAt(xy + float2(0.0, s)) - distanceAt(xy - float2(0.0, s)));
    float rl = length(rise);
    float2 inward = rl > 0.00001 ? rise / rl : float2(0.0);
    float rc = max(m.b * 4.0, 0.2);
    float off = clamp((rc - max(d, 0.0)) / rc, 0.0, 1.0);
    float3 Nl = float3(-inward * off, sqrt(max(1.0 - off * off, 0.0)) * side);
    float3 N = normalize(toWorld * Nl);
    float3 nz = normalize(toWorld * float3(0.0, 0.0, side));

    float F = fresnel(max(dot(N, V), 0.0), IOR);
    float3 R = reflect(I, N);
    // How far the reflection swings across a pixel: on the flat face hardly
    // at all, on the rolled edge twice as fast as the face turns.
    float upright = max(sqrt(max(1.0 - off * off, 0.0)), 0.2);
    float spread = off > 0.0 ? 2.0 * frame.z * max(sparks, 1.0) / (rc * upright) : 0.0;
    float3 refl = sparking ? float3(0.0) : mix(room(R), float3(1.0), backdrop(R));

    // The rod's section: across it, and out of the layer.
    float3 g = N - nz * dot(N, nz);
    float gl = length(g);
    g = gl > 0.001 ? g / gl : normalize(toWorld * float3(1.0, 0.0, 0.0));
    float2 e = float2(dot(N, g), dot(N, nz));

    float r = length(mm);
    float onDisc = overDisc * step(15.0, r) * (1.0 - step(60.0, r));
    float3 seen = mix(float3(1.0), float3(0.02), onDisc);

    float3 dir = refract(I, N, 1.0 / IOR);
    float3 acc = float3(0.0);
    float lit = 0.0;
    float thr = 1.0;
    for (int i = 0; i < 3; i++) {
        float2 d2 = float2(dot(dir, g), dot(dir, nz));
        float l2 = length(d2);
        float3 Ne = dir;
        if (l2 > 0.0001) {
            float2 dn = d2 / l2;
            float t = max(-2.0 * dot(e, dn), 0.0);
            e = normalize(e + dn * t);
            Ne = g * e.x + nz * e.y;
        }
        float3 leave = refract(dir, -Ne, IOR);
        bool trapped = dot(leave, leave) < 0.000001;
        float F2 = trapped ? 1.0 : fresnel(abs(dot(leave, Ne)), IOR);
        float3 way = trapped ? normalize(dir - Ne * dot(dir, Ne)) : leave;
        if (sparking) {
            lit += thr * (1.0 - F2) * lamps(way, spread) * (1.0 - behind(way, I));
        } else {
            acc += thr * (1.0 - F2) * outside(way, I, seen, onDisc);
        }
        thr *= F2;
        dir = reflect(dir, Ne);
    }
    if (sparking) {
        float spark = lamps(R, spread) * F + lit * (1.0 - F);
        return half4(half3(clamp(spark / SPARK_RANGE, 0.0, 1.0)), 1.0) * half(rodCover * cover);
    }
    acc += thr * 0.6 * room(dir);

    float3 col = refl * F + acc * (1.0 - F);
    float lum = dot(col, float3(0.2126, 0.7152, 0.0722));
    float lift = mix(LIFT_THIN, LIFT_WIDE, smoothstep(1.5, 6.0, rc / frame.z));
    col = mix(col, LIFT_TO, lift * (1.0 - smoothstep(0.0, 0.6, lum)));
    float k = rodCover * cover;
    return half4(half3(toSrgb(col)), 1.0) * half(k) + plate * half(1.0 - k);
}
"""

/**
 * The disc, after the reference's: the printed face near black where it is
 * bare, the print lit by the studio's lights with their glare across it, the
 * data face a grey mirror, and on both the rainbow its tracks split the
 * lights into — brightest along the radius that lies across the half vector
 * between light and eye, its colour set by how far that vector leans along
 * the radius, three orders of the spectrum summed. The clear plastic in the
 * middle is a sheet.
 */
private const val DISC = STUDIO + """
uniform shader art;
uniform float artSize;
uniform float3 eye;
uniform float3x3 toWorld;

float3 bump3(float3 x, float3 y0) {
    return clamp(1.0 - x * x - y0, float3(0.0), float3(1.0));
}

float3 spectral(float w) {
    float x = clamp((w - 400.0) / 300.0, 0.0, 1.0);
    float3 c1 = float3(3.54585104, 2.93225262, 2.41593945);
    float3 x1 = float3(0.69549072, 0.49228336, 0.27699880);
    float3 y1 = float3(0.02312639, 0.15225084, 0.52607955);
    float3 c2 = float3(3.90307140, 3.21182957, 3.96587128);
    float3 x2 = float3(0.11748627, 0.86755042, 0.66077860);
    float3 y2 = float3(0.84897130, 0.88445281, 0.73949448);
    float inside = step(380.0, w) * step(w, 760.0);
    return (bump3(c1 * (x - x1), y1) + bump3(c2 * (x - x2), y2)) * inside;
}

float3 grating(float3 L, float3 V, float3 rad, float3 tng, float spread) {
    float3 H = L + V;
    float along = dot(H, tng);
    float u = abs(dot(H, rad));
    float w = exp(-along * along / (spread * spread));
    float3 c = spectral(1600.0 * u) + 0.75 * spectral(800.0 * u) + 0.45 * spectral(533.3 * u);
    return c * w;
}

float3 lights(float3 N) {
    return float3(0.42) + float3(0.62) * max(dot(N, KEY), 0.0) + float3(0.28) * max(dot(N, STRIP), 0.0)
        + float3(0.18) * max(dot(N, TOP), 0.0);
}

half4 main(float2 xy) {
    float r = length(xy);
    if (r > 60.5 || r < 7.3) return half4(0.0);
    float cover = (1.0 - smoothstep(59.75, 60.25, r)) * smoothstep(7.3, 7.7, r);
    float3 Vl = normalize(eye - float3(xy, 0.0));
    bool printedFace = Vl.z >= 0.0;
    float3 V = normalize(toWorld * Vl);
    float3 N = normalize(toWorld * float3(0.0, 0.0, printedFace ? 1.0 : -1.0));

    if (r < 15.0) {
        float4 clear = float4(sheet(N, V, 1.2, cover));
        float lip = (1.0 - smoothstep(0.0, 0.35, abs(r - 7.75))) * 0.7 * cover;
        return half4(float4(float3(0.23, 0.25, 0.28) * lip, lip) + clear * (1.0 - lip));
    }

    float cosv = max(dot(N, V), 0.0);
    float3 R = reflect(-V, N);
    float2 rd = xy / r;
    float3 rad = normalize(toWorld * float3(rd, 0.0));
    float3 tng = normalize(toWorld * float3(-rd.y, rd.x, 0.0));
    float3 rainbow = grating(KEY, V, rad, tng, 0.05) * 1.6 + grating(STRIP, V, rad, tng, 0.045) * 1.4
        + grating(TOP, V, rad, tng, 0.06) * 0.9 + grating(BOUNCE, V, rad, tng, 0.16) * 0.35;
    float data = smoothstep(21.5, 23.0, r) * (1.0 - smoothstep(58.2, 58.8, r));
    float ring = 1.0 - 0.35 * (1.0 - smoothstep(0.15, 0.4, abs(r - 16.6)));
    float3 edge = studio(R) * 0.35 + float3(0.22, 0.23, 0.25);

    float3 col;
    if (!printedFace) {
        float3 mirror = min(studio(R), float3(1.6)) * 0.5
            + float3(0.42, 0.44, 0.47) * (0.6 + 0.4 * max(dot(N, KEY), 0.0));
        col = (mirror + rainbow * data * 1.3) * ring;
        col = mix(col, edge, smoothstep(58.0, 58.9, r) * 0.85);
    } else {
        col = (min(studio(R), float3(2.0)) * 0.07 + float3(0.012) + rainbow * data * 0.85) * ring;
        float4 a = float4(art.eval((xy / 120.0 + 0.5) * artSize));
        float alpha = a.a;
        float3 ink = toLinear(a.rgb / max(alpha, 0.001));
        float printed = alpha * (1.0 - smoothstep(59.1, 59.5, r)) * smoothstep(16.0, 16.4, r);
        col = mix(col, ink * lights(N), printed);
        col += min(glare(R), float3(2.5)) * fresnel(cosv, 1.5) * printed * 0.9;
        float rim = smoothstep(59.1, 59.6, r) * smoothstep(58.0, 58.9, r);
        col = mix(col, edge, rim * 0.85);
    }
    return half4(half3(toSrgb(col)), 1.0) * half(cover);
}
"""

/**
 * The reference's sparks, picked out of the layer the rods drew them into:
 * a pixel past the threshold that none of its eight neighbours outdoes, by
 * as much as it stands above the ring four pixels round it — so a glint
 * throws a star and a broad highlight does not. Out of eight, as a grey.
 */
private const val BRIGHT = SPARK + """
uniform shader mask;

const float THRESHOLD = 2.0;

float sparkAt(float2 xy) {
    return float(mask.eval(xy).r) * SPARK_RANGE;
}

half4 main(float2 xy) {
    float s = sparkAt(xy);
    if (s <= THRESHOLD) return half4(0.0, 0.0, 0.0, 1.0);
    float peak = 0.0;
    float ring = 0.0;
    for (int k = 0; k < 8; k++) {
        float a = float(k) * 0.7853982;
        float2 o = float2(cos(a), sin(a));
        peak = max(peak, sparkAt(xy + floor(o * 1.42 + 0.5)));
        ring += sparkAt(xy + o * 4.0);
    }
    if (s < peak) return half4(0.0, 0.0, 0.0, 1.0);
    float seed = min(max(s - ring * 0.25 - THRESHOLD, 0.0), 8.0) / 8.0;
    return half4(half3(seed), 1.0);
}
"""

/**
 * Each spark streaked ten steps of one and a half pixels each way, across
 * and down, falling off by 0.7 a step — the reference's four points — and
 * laid over the case as it does: the frame tinted towards a pale blue as
 * the star covers it, and the star's own light added on top.
 */
private const val STARS = """
uniform shader bright;
uniform float strength;

const float FALLOFF = 0.7;
const float STEP = 1.5;
const float3 BLUE = float3(0.68, 0.81, 1.0);
const float3 TINT = float3(0.91, 0.95, 1.0);

float seedAt(float2 xy) {
    return float(bright.eval(xy).r);
}

half4 main(float2 xy) {
    float c = 2.0 * seedAt(xy);
    float total = 1.0;
    float w = 1.0;
    for (int i = 1; i <= 10; i++) {
        w *= FALLOFF;
        float o = STEP * float(i);
        c += (seedAt(xy + float2(o, 0.0)) + seedAt(xy - float2(o, 0.0)) + seedAt(xy + float2(0.0, o)) + seedAt(xy - float2(0.0, o))) * w;
        total += 2.0 * w;
    }
    // Back out of eighths, three times the mean as the reference has it, and
    // its 1.6 for the two passes summed.
    float stars = c / total * 3.0 * 8.0 * 1.6 * strength;
    float k = 0.6 * clamp(stars, 0.0, 1.0);
    float g = clamp(stars * 0.5, 0.0, 1.0);
    float a = 1.0 - (1.0 - k) * (1.0 - g);
    return half4(half3(BLUE * k * (1.0 - g) + TINT * g), half(a));
}
"""

/**
 * AGSL arrived in 33; below that, and if either shader fails to compile, the
 * case is drawn from its baked look in common code.
 */
internal fun caseShader(): CaseShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    return runCatching { AgslCase() }.getOrNull()
}

/** Bitmaps as shaders the case can read, kept while they are in use. */
private const val MAPS_KEPT = 12

/**
 * One shader for every layer, one for the disc. Setting a uniform drops the
 * shader's native instance, so each draw takes its own snapshot and the layers
 * drawn before it keep theirs.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class AgslCase : CaseShader {
    private val glass = RuntimeShader(GLASS)
    private val glassBrush = ShaderBrush(glass)
    private val disc = RuntimeShader(DISC)
    private val discBrush = ShaderBrush(disc)
    private val bright = RuntimeShader(BRIGHT)
    private val stars = RuntimeShader(STARS)
    private val maps = HashMap<ImageBitmap, BitmapShader>()

    override fun heightMap(pixels: IntArray, width: Int, height: Int): ImageBitmap =
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

    override fun glass(
        height: ImageBitmap,
        left: Float,
        top: Float,
        mmPerPx: Float,
        z: Float,
        eye: FloatArray,
        toWorld: FloatArray,
        thickness: Float,
        overDisc: Boolean,
        sparks: Float,
    ): Brush {
        glass.setInputShader("height", map(height, Shader.TileMode.DECAL))
        glass.setFloatUniform("frame", left, top, mmPerPx, z)
        glass.setFloatUniform("eye", eye[0], eye[1], eye[2])
        glass.setFloatUniform("toWorld", toWorld)
        glass.setFloatUniform("thickness", thickness)
        glass.setFloatUniform("overDisc", if (overDisc) 1f else 0f)
        glass.setFloatUniform("sparks", sparks)
        return glassBrush
    }

    override fun stars(strength: Float): androidx.compose.ui.graphics.RenderEffect {
        stars.setFloatUniform("strength", strength)
        return RenderEffect.createChainEffect(
            RenderEffect.createRuntimeShaderEffect(stars, "bright"),
            RenderEffect.createRuntimeShaderEffect(bright, "mask"),
        ).asComposeRenderEffect()
    }

    override fun disc(art: ImageBitmap, eye: FloatArray, toWorld: FloatArray): Brush {
        disc.setInputShader("art", map(art, Shader.TileMode.CLAMP))
        disc.setFloatUniform("artSize", art.width.toFloat())
        disc.setFloatUniform("eye", eye[0], eye[1], eye[2])
        disc.setFloatUniform("toWorld", toWorld)
        return discBrush
    }

    private fun map(image: ImageBitmap, tile: Shader.TileMode): BitmapShader = maps.getOrPut(image) {
        if (maps.size >= MAPS_KEPT) maps.clear()
        BitmapShader(image.asAndroidBitmap(), tile, tile).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
    }
}
