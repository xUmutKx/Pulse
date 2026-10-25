package com.umutk.pulse.extras.palette.core.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import com.umutk.pulse.extras.palette.core.color.LINEAR_TABLE
import com.umutk.pulse.extras.palette.core.color.Lab
import com.umutk.pulse.extras.palette.core.color.darken
import com.umutk.pulse.extras.palette.core.color.lighten
import com.umutk.pulse.extras.palette.core.color.mix
import com.umutk.pulse.extras.palette.core.color.toColor
import com.umutk.pulse.extras.palette.core.color.toLab
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import kotlin.math.cbrt

/**
 * ORIGINAL mode — recolour without destroying the icon.
 *
 * Naive tinting flattens a logo into one blob. Instead we build a 256-entry
 * *tone ramp LUT* in Oklab: each source pixel keeps its own perceptual lightness
 * position and receives the palette colour that sits at that position. Layers,
 * gradients, shadows, anti-aliased edges and transparency all survive because
 * they are all just lightness variations.
 *
 * [hueRetention] blends the brand hue back in, so "0" is a full palette takeover
 * and "1" keeps the app instantly recognisable.
 */
object Recolorizer {

    /** LUT: index = perceptual lightness 0..255 -> palette colour. */
    fun buildLut(
        palette: ColorPalette,
        contrast: Float = 1f,
        brightness: Float = 0f,
        chroma: Float = 1f
    ): IntArray {
        val ramp = palette.ramp.map { it.toLab() }
        if (ramp.isEmpty()) return IntArray(256) { Color.GRAY }
        return IntArray(256) { i ->
            var t = i / 255f
            t = ((t - 0.5f) * contrast + 0.5f + brightness).coerceIn(0f, 1f)
            val lab = sampleRamp(ramp, t)
            lab.scaleChroma(chroma).toColor()
        }
    }

    private fun sampleRamp(ramp: List<Lab>, t: Float): Lab {
        if (ramp.size == 1) return ramp[0]
        val x = t * (ramp.size - 1)
        val i = x.toInt().coerceAtMost(ramp.size - 2)
        return mix(ramp[i], ramp[i + 1], x - i)
    }

    /** Perceptual lightness of a pixel, cheap: cube root of linear luminance. */
    private fun toneOf(c: Int): Int {
        val y = 0.2126f * LINEAR_TABLE[(c shr 16) and 0xFF] +
                0.7152f * LINEAR_TABLE[(c shr 8) and 0xFF] +
                0.0722f * LINEAR_TABLE[c and 0xFF]
        return (cbrt(y) * 255f).toInt().coerceIn(0, 255)
    }

    fun recolor(src: Bitmap, lut: IntArray, hueRetention: Float = 0f): Bitmap {
        val w = src.width; val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val keep = hueRetention.coerceIn(0f, 1f)
        for (i in px.indices) {
            val p = px[i]
            val a = p ushr 24
            if (a == 0) continue
            val mapped = lut[toneOf(p)]
            px[i] = if (keep <= 0.01f) {
                (a shl 24) or (mapped and 0x00FFFFFF)
            } else {
                val m = mapped.toLab()
                val o = p.toLab()
                // keep mapped lightness, blend the original chroma/hue back in
                val blended = Lab(
                    m.l,
                    m.a + (o.a - m.a) * keep,
                    m.b + (o.b - m.b) * keep
                )
                blended.toColor(a)
            }
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * Adaptive icons are recoloured layer by layer: the background becomes a flat
     * palette tone (so the grid reads as one system) while the foreground keeps
     * its internal structure through the LUT.
     */
    fun recolorAdaptive(
        d: AdaptiveIconDrawable,
        spec: ThemeSpec,
        lut: IntArray,
        bgColor: Int,
        size: Int
    ): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val inset = size * (1f - spec.iconScale) / 2f
        val r = android.graphics.RectF(inset, inset, size - inset, size - inset)
        val path = ShapeFactory.path(spec.shape, r, spec.cornerRadius, spec.squircleExponent)

        EffectPainter.dropShadow(c, path, bgColor, spec.shadow, size.toFloat())
        if (spec.gradientIntensity > 0.01f) {
            EffectPainter.gradient(c, path, r,
                bgColor.lighten(0.14f * spec.gradientIntensity),
                bgColor.darken(0.18f * spec.gradientIntensity),
                spec.gradientAngle, spec.opacity)
        } else {
            EffectPainter.fill(c, path, bgColor, spec.opacity)
        }

        val fgSrc = d.foreground
        if (fgSrc != null) {
            val raw = GlyphExtractor.rasterize(fgSrc, (size * 1.5f).toInt())
            val recolored = recolor(raw, lut, spec.hueRetention)
            val save = c.saveLayer(0f, 0f, size.toFloat(), size.toFloat(), null)
            c.clipPath(path)
            val inset2 = -(size * 0.25f)
            c.drawBitmap(
                recolored,
                android.graphics.Rect(0, 0, recolored.width, recolored.height),
                android.graphics.RectF(inset2, inset2, size - inset2, size - inset2),
                android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            )
            c.restoreToCount(save)
        }
        if (spec.stroke > 0f) EffectPainter.stroke(c, path, 0xFFFFFFFF.toInt(), size * 0.012f * spec.stroke * 4f, 0.4f)
        if (spec.noise > 0f) EffectPainter.noise(c, path, r, spec.noise, spec.seed)
        return out
    }

    /** Legacy (non-adaptive) icons: recolour then place on a palette tile. */
    fun recolorLegacy(
        d: Drawable, spec: ThemeSpec, lut: IntArray, bgColor: Int, size: Int
    ): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val inset = size * (1f - spec.iconScale) / 2f
        val r = android.graphics.RectF(inset, inset, size - inset, size - inset)
        val path = ShapeFactory.path(spec.shape, r, spec.cornerRadius, spec.squircleExponent)
        EffectPainter.dropShadow(c, path, bgColor, spec.shadow, size.toFloat())
        EffectPainter.fill(c, path, bgColor, spec.opacity)
        val raw = GlyphExtractor.rasterize(d, size)
        val recolored = recolor(raw, lut, spec.hueRetention)
        val save = c.saveLayer(0f, 0f, size.toFloat(), size.toFloat(), null)
        c.clipPath(path)
        val pad = size * 0.16f
        c.drawBitmap(recolored,
            android.graphics.Rect(0, 0, recolored.width, recolored.height),
            android.graphics.RectF(pad, pad, size - pad, size - pad),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        c.restoreToCount(save)
        return out
    }
}
