package com.umutk.pulse.extras.palette.core.icon

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import com.umutk.pulse.extras.palette.core.color.darken
import com.umutk.pulse.extras.palette.core.color.ensureContrast
import com.umutk.pulse.extras.palette.core.color.lighten
import com.umutk.pulse.extras.palette.core.color.withAlpha
import kotlin.math.min

/**
 * STYLED mode — every app becomes the same design language.
 * A styled icon = tile background (TileRenderer) + glyph (GlyphExtractor) drawn
 * with a style-specific technique. Contrast between the two is enforced, so no
 * combination of palette and style can produce an unreadable icon.
 */
object StyledRenderer {

    fun render(
        spec: ThemeSpec,
        style: IconStyle,
        glyph: Bitmap,
        bgColor: Int,
        fgColorIn: Int,
        size: Int,
        backdrop: Bitmap? = null,
        seed: Long = 0L
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val fg = ensureContrast(fgColorIn, bgColor, 3.2f)

        // 1. surface
        val tileSpec = spec.copy(
            shape = style.shape,
            cornerRadius = style.corner,
            finish = style.finish,
            gradientIntensity = maxOf(spec.gradientIntensity, style.gradient),
            shadow = maxOf(spec.shadow, style.shadow),
            stroke = maxOf(spec.stroke, style.stroke),
            noise = maxOf(spec.noise, style.noise),
            innerShadow = maxOf(spec.innerShadow, style.innerShadow),
            glow = maxOf(spec.glow, style.glow),
            squircleExponent = style.squircleN,
            iconScale = style.iconScale
        )
        TileRenderer.draw(c, tileSpec, bgColor, size.toFloat(), backdrop, seed)

        // 2. glyph
        val gScale = style.glyphScale * (spec.glyphScale / 0.52f)
        val gs = (size * gScale).toInt().coerceAtLeast(8)
        val off = (size - gs) / 2f
        val dst = RectF(off, off, off + gs, off + gs)
        val src = Rect(0, 0, glyph.width, glyph.height)

        when (style.glyph) {
            GlyphKind.NONE -> {}
            GlyphKind.SOLID -> drawTinted(c, glyph, src, dst, fg)
            GlyphKind.OUTLINE -> drawTinted(c, GlyphExtractor.outline(glyph, 0.18f), src, dst, fg)
            GlyphKind.PIXEL -> drawTinted(c, GlyphExtractor.pixelate(glyph, 16), src, dst, fg)
            GlyphKind.DOT_MATRIX -> dotMatrix(c, glyph, dst, fg, 22)
            GlyphKind.EXTRUDE -> {
                val depth = size * 0.035f
                for (i in 6 downTo 1) {
                    val t = i / 6f
                    drawTinted(c, glyph, src,
                        RectF(dst.left + depth * t, dst.top + depth * t,
                            dst.right + depth * t, dst.bottom + depth * t),
                        fg.darken(0.10f + 0.22f * t))
                }
                drawTinted(c, glyph, src, dst, fg.lighten(0.06f))
            }
            GlyphKind.RETRO -> {
                val o = size * 0.018f
                drawTinted(c, glyph, src, RectF(dst.left - o, dst.top, dst.right - o, dst.bottom),
                    0xFFE04B4B.toInt().withAlpha(190))
                drawTinted(c, glyph, src, RectF(dst.left + o, dst.top, dst.right + o, dst.bottom),
                    0xFF4BC9E0.toInt().withAlpha(190))
                drawTinted(c, glyph, src, dst, fg)
            }
            GlyphKind.DUOTONE -> {
                drawTinted(c, glyph, src,
                    RectF(dst.left, dst.top + size * 0.02f, dst.right, dst.bottom + size * 0.02f),
                    fg.darken(0.22f).withAlpha(170))
                drawTinted(c, glyph, src, dst, fg)
            }
        }
        return bmp
    }

    private fun drawTinted(c: Canvas, bmp: Bitmap, src: Rect, dst: RectF, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        c.drawBitmap(bmp, src, dst, p)
    }

    /** Nothing-OS dot matrix: sample glyph coverage per cell, draw a dot. */
    private fun dotMatrix(c: Canvas, glyph: Bitmap, dst: RectF, color: Int, grid: Int) {
        val small = Bitmap.createScaledBitmap(glyph, grid, grid, true)
        val px = IntArray(grid * grid)
        small.getPixels(px, 0, grid, 0, 0, grid, grid)
        val cell = dst.width() / grid
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in 0 until grid) for (x in 0 until grid) {
            val a = (px[y * grid + x] ushr 24) and 0xFF
            if (a < 26) continue
            val cov = a / 255f
            p.color = color.withAlpha((255 * min(1f, cov * 1.35f)).toInt())
            c.drawCircle(
                dst.left + cell * (x + 0.5f),
                dst.top + cell * (y + 0.5f),
                cell * 0.44f * (0.55f + 0.45f * cov),
                p
            )
        }
    }
}
