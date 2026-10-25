package com.umutk.pulse.extras.palette.core.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.max
import kotlin.math.min

/**
 * Reduces any app icon to a clean monochrome glyph.
 *
 * Ladder (best result first):
 *  1. the OS themed-icon monochrome layer (API 33+) — already a perfect glyph
 *  2. the adaptive foreground layer, alpha-thresholded and auto-cropped
 *  3. the legacy icon, background-subtracted by dominant colour
 *
 * The result is an alpha mask, which every Styled renderer then paints.
 */
object GlyphExtractor {

    data class Glyph(val mask: Bitmap, val coverage: Float, val quality: Float)

    fun extract(drawable: Drawable, size: Int = 192): Glyph {
        val src: Bitmap = when {
            Build.VERSION.SDK_INT >= 33 && drawable is AdaptiveIconDrawable && drawable.monochrome != null ->
                rasterize(drawable.monochrome!!, size)
            drawable is AdaptiveIconDrawable -> {
                val fg = drawable.foreground
                if (fg != null) cropAdaptive(rasterize(fg, (size * 1.5f).toInt()), size)
                else rasterize(drawable, size)
            }
            else -> rasterize(drawable, size)
        }
        val mask = toAlphaMask(src)
        val cropped = autoCrop(mask)
        val coverage = coverageOf(cropped)
        val quality = qualityOf(cropped, coverage)
        return Glyph(cropped, coverage, quality)
    }

    fun rasterize(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return bmp
    }

    /** Adaptive foregrounds are drawn at 150%; the safe zone is the middle 66%. */
    private fun cropAdaptive(b: Bitmap, out: Int): Bitmap {
        val inset = (b.width * (1f - 1f / 1.5f) / 2f).toInt()
        val src = Bitmap.createBitmap(b, inset, inset, b.width - inset * 2, b.height - inset * 2)
        return Bitmap.createScaledBitmap(src, out, out, true)
    }

    /**
     * Converts colour art into a binary-ish alpha mask. We keep pixels that are
     * both opaque and distinct from the icon's own background tone, which
     * survives white-on-colour and colour-on-white logos equally well.
     */
    private fun toAlphaMask(b: Bitmap): Bitmap {
        val w = b.width; val h = b.height
        val px = IntArray(w * h)
        b.getPixels(px, 0, w, 0, 0, w, h)

        // background estimate = median luminance of the outer ring
        var sum = 0f; var n = 0
        for (x in 0 until w) { sum += lum(px[x]); sum += lum(px[(h - 1) * w + x]); n += 2 }
        for (y in 0 until h) { sum += lum(px[y * w]); sum += lum(px[y * w + w - 1]); n += 2 }
        val bg = sum / n

        val out = IntArray(w * h)
        for (i in px.indices) {
            val a = (px[i] ushr 24) and 0xFF
            if (a < 24) { out[i] = 0; continue }
            val d = kotlin.math.abs(lum(px[i]) - bg)
            val alpha = ((d / 0.45f).coerceIn(0f, 1f) * a).toInt()
            out[i] = Color.argb(alpha, 255, 255, 255)
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun lum(c: Int): Float =
        (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f

    fun autoCrop(mask: Bitmap, threshold: Int = 40): Bitmap {
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h)
        mask.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = 0; var b = 0
        for (y in 0 until h) for (x in 0 until w) {
            if (((px[y * w + x] ushr 24) and 0xFF) > threshold) {
                l = min(l, x); t = min(t, y); r = max(r, x); b = max(b, y)
            }
        }
        if (r <= l || b <= t) return mask
        val side = max(r - l, b - t) + 1
        val cx = (l + r) / 2; val cy = (t + b) / 2
        val x0 = (cx - side / 2).coerceIn(0, w - 1)
        val y0 = (cy - side / 2).coerceIn(0, h - 1)
        val sw = side.coerceAtMost(w - x0)
        val sh = side.coerceAtMost(h - y0)
        val cut = Bitmap.createBitmap(mask, x0, y0, sw, sh)
        return Bitmap.createScaledBitmap(cut, mask.width, mask.height, true)
    }

    private fun coverageOf(mask: Bitmap): Float {
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h)
        mask.getPixels(px, 0, w, 0, 0, w, h)
        var on = 0
        for (p in px) if (((p ushr 24) and 0xFF) > 96) on++
        return on.toFloat() / px.size
    }

    /**
     * Heuristic legibility score. Very sparse or almost-solid masks make bad
     * glyphs; that is the signal the engine uses to fall back to a tile.
     */
    private fun qualityOf(mask: Bitmap, coverage: Float): Float {
        val ideal = 0.34f
        val spread = 1f - (kotlin.math.abs(coverage - ideal) / ideal).coerceIn(0f, 1f)
        val edges = edgeDensity(mask)
        val complexityPenalty = (1f - ((edges - 0.18f) / 0.5f).coerceIn(0f, 1f))
        return (spread * 0.6f + complexityPenalty * 0.4f).coerceIn(0f, 1f)
    }

    private fun edgeDensity(mask: Bitmap): Float {
        val s = 48
        val small = Bitmap.createScaledBitmap(mask, s, s, true)
        val px = IntArray(s * s)
        small.getPixels(px, 0, s, 0, 0, s, s)
        var edges = 0
        for (y in 1 until s) for (x in 1 until s) {
            val a = (px[y * s + x] ushr 24) and 0xFF
            val b = (px[y * s + x - 1] ushr 24) and 0xFF
            val c = (px[(y - 1) * s + x] ushr 24) and 0xFF
            if (kotlin.math.abs(a - b) > 80 || kotlin.math.abs(a - c) > 80) edges++
        }
        return edges.toFloat() / (s * s)
    }

    /** Downsamples a mask to a pixel-art grid and back, nearest-neighbour. */
    fun pixelate(mask: Bitmap, grid: Int): Bitmap {
        val small = Bitmap.createScaledBitmap(mask, grid, grid, false)
        return Bitmap.createScaledBitmap(small, mask.width, mask.height, false)
    }

    /** Hollow ring version of a mask, for Outline style. */
    fun outline(mask: Bitmap, thickness: Float): Bitmap {
        val w = mask.width; val h = mask.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(mask, 0f, 0f, null)
        val inner = (1f - thickness).coerceIn(0.55f, 0.95f)
        val scaled = Bitmap.createScaledBitmap(mask, (w * inner).toInt().coerceAtLeast(1),
            (h * inner).toInt().coerceAtLeast(1), true)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
        }
        c.drawBitmap(scaled, Rect(0, 0, scaled.width, scaled.height),
            android.graphics.RectF(w * (1 - inner) / 2f, h * (1 - inner) / 2f,
                w - w * (1 - inner) / 2f, h - h * (1 - inner) / 2f), paint)
        return out
    }
}
