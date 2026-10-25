package com.umutk.pulse.extras.palette.core.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.umutk.pulse.extras.palette.core.color.darken
import com.umutk.pulse.extras.palette.core.color.lighten
import com.umutk.pulse.extras.palette.core.color.mixColors
import com.umutk.pulse.extras.palette.core.palette.ColorPalette

/**
 * COLOR TILES mode — the flagship look.
 *
 * There is deliberately no glyph, no letter, no logo: only a shape, a colour and
 * a material finish. Recognisability comes from position and label, exactly like
 * the reference home screen.
 */
object TileRenderer {

    fun render(
        spec: ThemeSpec,
        color: Int,
        size: Int,
        backdrop: Bitmap? = null,
        seed: Long = 0L
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        draw(c, spec, color, size.toFloat(), backdrop, seed)
        return bmp
    }

    fun draw(
        c: Canvas,
        spec: ThemeSpec,
        color: Int,
        size: Float,
        backdrop: Bitmap? = null,
        seed: Long = 0L
    ) {
        val inset = size * (1f - spec.iconScale) / 2f
        val r = RectF(inset, inset, size - inset, size - inset)
        val path = ShapeFactory.path(spec.shape, r, spec.cornerRadius, spec.squircleExponent)

        EffectPainter.dropShadow(c, path, color, spec.shadow, size)
        if (spec.glow > 0f) EffectPainter.glow(c, path, color, spec.glow, size)

        when (spec.finish) {
            Finish.GLASS, Finish.FROSTED, Finish.LIQUID_GLASS ->
                drawGlass(c, spec, path, r, color, size, backdrop, seed)
            Finish.NEUMORPHIC -> {
                EffectPainter.neumorphic(c, path, r, color, 1f)
                paintBody(c, spec, path, r, color, size, seed)
            }
            Finish.CLAY -> {
                EffectPainter.dropShadow(c, path, color, spec.shadow + 0.35f, size)
                paintBody(c, spec, path, r, color.lighten(0.04f), size, seed)
                EffectPainter.sheen(c, path, r, 0.30f)
                EffectPainter.innerShadow(c, path, r, 0.35f, size)
            }
            Finish.METAL -> {
                paintBody(c, spec, path, r, color, size, seed)
                EffectPainter.gradient(c, path, r, color.lighten(0.22f), color.darken(0.18f), 100f, 0.55f)
                EffectPainter.sheen(c, path, r, 0.45f, warp = 0.6f)
            }
            Finish.GLOSSY -> {
                paintBody(c, spec, path, r, color, size, seed)
                EffectPainter.sheen(c, path, r, 0.55f, warp = 0.35f)
            }
            Finish.MATTE -> paintBody(c, spec, path, r, color, size, seed)
        }

        if (spec.innerShadow > 0f) EffectPainter.innerShadow(c, path, r, spec.innerShadow, size)
        if (spec.stroke > 0f) {
            val strokeColor = mixColors(color, if (spec.strokeLightness > 0.5f) 0xFFFFFFFF.toInt()
                else 0xFF000000.toInt(), kotlin.math.abs(spec.strokeLightness - 0.5f) * 2f)
            EffectPainter.stroke(c, path, strokeColor, size * 0.012f * spec.stroke * 4f, 0.85f)
        }
        if (spec.noise > 0f) EffectPainter.noise(c, path, r, spec.noise, seed)
    }

    private fun paintBody(
        c: Canvas, spec: ThemeSpec, path: android.graphics.Path,
        r: RectF, color: Int, size: Float, seed: Long
    ) {
        if (spec.gradientIntensity > 0.01f) {
            val g = spec.gradientIntensity
            EffectPainter.gradient(
                c, path, r,
                color.lighten(0.16f * g), color.darken(0.20f * g),
                spec.gradientAngle, spec.opacity
            )
        } else {
            EffectPainter.fill(c, path, color, spec.opacity)
        }
    }

    private fun drawGlass(
        c: Canvas, spec: ThemeSpec, path: android.graphics.Path, r: RectF,
        color: Int, size: Float, backdrop: Bitmap?, seed: Long
    ) {
        // 1. blurred wallpaper behind the tile (real refraction when we have it)
        if (backdrop != null) {
            val blurred = EffectPainter.fastBlur(backdrop, if (spec.finish == Finish.FROSTED) 0.34f else 0.20f)
            EffectPainter.maskInto(c, path, { canvas ->
                canvas.drawBitmap(blurred, Rect(0, 0, blurred.width, blurred.height),
                    RectF(0f, 0f, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
            }, RectF(0f, 0f, size, size))
        }
        // 2. coloured glass body
        val bodyAlpha = when (spec.finish) {
            Finish.FROSTED -> 0.55f
            Finish.LIQUID_GLASS -> 0.38f
            else -> 0.46f
        } * spec.opacity
        EffectPainter.gradient(c, path, r, color.lighten(0.10f), color.darken(0.05f),
            spec.gradientAngle, bodyAlpha)
        // 3. finish-specific optics
        when (spec.finish) {
            Finish.FROSTED -> {
                EffectPainter.noise(c, path, r, 0.5f, seed)
                EffectPainter.stroke(c, path, 0xFFFFFFFF.toInt(), size * 0.012f, 0.35f)
                EffectPainter.sheen(c, path, r, 0.25f)
            }
            Finish.LIQUID_GLASS -> {
                EffectPainter.liquidRim(c, path, r, color, 1f)
                EffectPainter.sheen(c, path, r, 0.5f, warp = 0.8f)
                EffectPainter.innerShadow(c, path, r, 0.25f, size)
            }
            else -> {
                EffectPainter.sheen(c, path, r, 0.42f, warp = 0.25f)
                EffectPainter.stroke(c, path, 0xFFFFFFFF.toInt(), size * 0.010f, 0.45f)
            }
        }
    }

    /** Two-colour gradient tiles (the "TikTok" case from the brief). */
    fun gradientPair(palette: ColorPalette, index: Int): Pair<Int, Int> {
        if (palette.size < 2) return palette.lightest to palette.lightest
        val a = palette.ramp[index % palette.size]
        val b = palette.ramp[(index + 1 + palette.size / 2) % palette.size]
        return a to b
    }
}
