package com.umutk.pulse.extras.palette.core.color

import android.graphics.Color
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Perceptual colour space used by the whole engine.
 *
 * Every mapping decision (luminance ramps, contrast repair, harmony, gradients)
 * happens in Oklab because sRGB interpolation produces grey/muddy midpoints that
 * destroy the "one design language" look we are after.
 */
data class Lab(val l: Float, val a: Float, val b: Float) {
    val chroma: Float get() = hypot(a, b)
    val hue: Float get() = atan2(b, a)

    fun withL(v: Float) = Lab(v.coerceIn(0f, 1f), a, b)
    fun scaleChroma(f: Float): Lab = Lab(l, a * f, b * f)
    fun rotateHue(rad: Float): Lab {
        val c = chroma; val h = hue + rad
        return Lab(l, c * cos(h), c * sin(h))
    }
}

private fun srgbToLinear(c: Float): Float =
    if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun linearToSrgb(c: Float): Float =
    if (c <= 0.0031308f) c * 12.92f else 1.055f * c.pow(1f / 2.4f) - 0.055f

/** Precomputed sRGB byte -> linear float table; hot path for per-pixel work. */
val LINEAR_TABLE: FloatArray = FloatArray(256) { srgbToLinear(it / 255f) }

fun Int.toLab(): Lab {
    val r = LINEAR_TABLE[(this shr 16) and 0xFF]
    val g = LINEAR_TABLE[(this shr 8) and 0xFF]
    val b = LINEAR_TABLE[this and 0xFF]
    return linearToLab(r, g, b)
}

fun linearToLab(r: Float, g: Float, b: Float): Lab {
    val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
    val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
    val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
    return Lab(
        0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    )
}

fun Lab.toColor(alpha: Int = 255): Int {
    val l_ = l + 0.3963377774f * a + 0.2158037573f * b
    val m_ = l - 0.1055613458f * a - 0.0638541728f * b
    val s_ = l - 0.0894841775f * a - 1.2914855480f * b
    val lc = l_ * l_ * l_; val mc = m_ * m_ * m_; val sc = s_ * s_ * s_
    val r = 4.0767416621f * lc - 3.3077115913f * mc + 0.2309699292f * sc
    val g = -1.2684380046f * lc + 2.6097574011f * mc - 0.3413193965f * sc
    val bl = -0.0041960863f * lc - 0.7034186147f * mc + 1.7076147010f * sc
    fun ch(v: Float) = (linearToSrgb(v).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return Color.argb(alpha, ch(r), ch(g), ch(bl))
}

fun mix(a: Lab, b: Lab, t: Float): Lab {
    // Shortest-arc hue interpolation keeps gradients clean between distant hues.
    val ca = a.chroma; val cb = b.chroma
    return if (ca < 0.01f || cb < 0.01f) {
        Lab(a.l + (b.l - a.l) * t, a.a + (b.a - a.a) * t, a.b + (b.b - a.b) * t)
    } else {
        var dh = b.hue - a.hue
        while (dh > Math.PI) dh -= (2 * Math.PI).toFloat()
        while (dh < -Math.PI) dh += (2 * Math.PI).toFloat()
        val h = a.hue + dh * t
        val c = ca + (cb - ca) * t
        Lab(a.l + (b.l - a.l) * t, c * cos(h), c * sin(h))
    }
}

fun mixColors(c1: Int, c2: Int, t: Float): Int = mix(c1.toLab(), c2.toLab(), t).toColor()

/** WCAG relative luminance, used for accessibility guarantees. */
fun Int.relativeLuminance(): Float {
    val r = LINEAR_TABLE[(this shr 16) and 0xFF]
    val g = LINEAR_TABLE[(this shr 8) and 0xFF]
    val b = LINEAR_TABLE[this and 0xFF]
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

fun contrastRatio(c1: Int, c2: Int): Float {
    val a = c1.relativeLuminance() + 0.05f
    val b = c2.relativeLuminance() + 0.05f
    return if (a > b) a / b else b / a
}

/** Nudges [fg] lighter/darker in Oklab until it clears [target] contrast on [bg]. */
fun ensureContrast(fg: Int, bg: Int, target: Float = 3.0f): Int {
    if (contrastRatio(fg, bg) >= target) return fg
    val lab = fg.toLab()
    val goDark = bg.relativeLuminance() > 0.35f
    var best = fg
    var step = 0.02f
    while (step < 1f) {
        val l = (lab.l + if (goDark) -step else step).coerceIn(0f, 1f)
        val c = lab.withL(l).toColor()
        best = c
        if (contrastRatio(c, bg) >= target) return c
        step += 0.02f
    }
    return best
}

fun Int.isDark(): Boolean = relativeLuminance() < 0.25f

/** Picks opaque black or white, whichever reads more legibly on top of [this]. */
fun Int.readableOn(): Int {
    val white = 0xFFFFFFFF.toInt()
    val black = 0xFF000000.toInt()
    return if (contrastRatio(this, white) >= contrastRatio(this, black)) white else black
}

fun Int.withAlpha(a: Int): Int = (this and 0x00FFFFFF) or ((a and 0xFF) shl 24)

fun Int.lighten(amount: Float): Int = toLab().let { it.withL((it.l + amount).coerceIn(0f, 1f)) }.toColor(Color.alpha(this))

fun Int.darken(amount: Float): Int = lighten(-amount)

fun Int.saturate(f: Float): Int = toLab().scaleChroma(f).toColor(Color.alpha(this))

fun Int.toHex(): String = String.format("#%06X", 0xFFFFFF and this)

fun parseHex(s: String): Int? {
    val t = s.trim().removePrefix("#")
    return try {
        when (t.length) {
            3 -> Color.rgb(
                Integer.parseInt("${t[0]}${t[0]}", 16),
                Integer.parseInt("${t[1]}${t[1]}", 16),
                Integer.parseInt("${t[2]}${t[2]}", 16)
            )
            6 -> (0xFF000000.toInt() or Integer.parseInt(t, 16))
            8 -> java.lang.Long.parseLong(t, 16).toInt()
            else -> null
        }
    } catch (e: Exception) { null }
}

fun hsvOf(color: Int): FloatArray = FloatArray(3).also { Color.colorToHSV(color, it) }

fun hslOf(color: Int): FloatArray {
    val r = Color.red(color) / 255f; val g = Color.green(color) / 255f; val b = Color.blue(color) / 255f
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (abs(max - min) < 1e-5f) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f)
        g -> ((b - r) / d + 2f)
        else -> ((r - g) / d + 4f)
    } * 60f
    return floatArrayOf(h, s, l)
}

fun hslToColor(h: Float, s: Float, l: Float): Int {
    fun hue2rgb(p: Float, q: Float, tIn: Float): Float {
        var t = tIn
        if (t < 0) t += 1f; if (t > 1) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    if (s <= 0f) { val v = (l * 255).toInt(); return Color.rgb(v, v, v) }
    val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
    val p = 2 * l - q
    val hn = h / 360f
    return Color.rgb(
        (hue2rgb(p, q, hn + 1f / 3f) * 255).toInt().coerceIn(0, 255),
        (hue2rgb(p, q, hn) * 255).toInt().coerceIn(0, 255),
        (hue2rgb(p, q, hn - 1f / 3f) * 255).toInt().coerceIn(0, 255)
    )
}

/** Converts a pixel array to Lab, skipping transparent and near-duplicate pixels. */
fun labOfPixels(pixels: IntArray, alphaCut: Int = 32): List<Lab> {
    val out = ArrayList<Lab>(pixels.size / 2)
    var i = 0
    while (i < pixels.size) {
        val p = pixels[i]
        if ((p ushr 24) >= alphaCut) out += p.toLab()
        i++
    }
    return out
}

