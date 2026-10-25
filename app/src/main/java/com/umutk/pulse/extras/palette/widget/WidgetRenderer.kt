package com.umutk.pulse.extras.palette.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.umutk.pulse.extras.palette.core.color.darken
import com.umutk.pulse.extras.palette.core.color.lighten
import com.umutk.pulse.extras.palette.core.color.readableOn
import com.umutk.pulse.extras.palette.core.icon.EffectPainter
import com.umutk.pulse.extras.palette.core.icon.WidgetStyle
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import kotlin.math.max

/**
 * Draws the themed background bitmap behind a home-screen widget. Kept
 * separate from the icon renderers because a widget is a plain rounded
 * rect, not a clipped tile shape, but reuses the same Canvas painters so
 * the surface reads as the same material as the icons and wallpaper.
 */
object WidgetRenderer {

    fun background(
        palette: ColorPalette,
        style: WidgetStyle,
        widthPx: Int,
        heightPx: Int,
        cornerRadiusPx: Float,
        photo: Bitmap? = null
    ): Bitmap {
        val w = max(1, widthPx); val h = max(1, heightPx)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radius = cornerRadiusPx.coerceIn(0f, minOf(r.width(), r.height()) / 2f)
        val path = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }

        when (style) {
            WidgetStyle.GRADIENT ->
                EffectPainter.gradient(c, path, r, palette.accent.lighten(0.06f), palette.darkest, 135f)
            WidgetStyle.GLASS -> {
                EffectPainter.gradient(c, path, r, palette.accent.lighten(0.10f), palette.accent.darken(0.10f), 135f, alpha = 0.85f)
                EffectPainter.sheen(c, path, r, 0.40f, warp = 0.3f)
                EffectPainter.stroke(c, path, 0xFFFFFFFF.toInt(), w * 0.01f, 0.35f)
            }
            WidgetStyle.MINIMAL -> EffectPainter.fill(c, path, palette.accent)
            WidgetStyle.SWATCH -> swatchStrip(c, path, r, palette)
            WidgetStyle.PHOTO ->
                if (photo != null) photoCover(c, path, r, photo)
                else EffectPainter.gradient(c, path, r, palette.accent.lighten(0.06f), palette.darkest, 135f)
        }
        return bmp
    }

    /** Centre-crops [photo] to fill [r] ("cover" fit), clipped to the widget shape. */
    private fun photoCover(c: Canvas, path: Path, r: RectF, photo: Bitmap) {
        c.save()
        c.clipPath(path)
        val scale = max(r.width() / photo.width, r.height() / photo.height)
        val dw = photo.width * scale; val dh = photo.height * scale
        val left = r.centerX() - dw / 2f; val top = r.centerY() - dh / 2f
        val dst = RectF(left, top, left + dw, top + dh)
        c.drawBitmap(photo, null, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        c.restore()
    }

    /** Full palette rendered as equal-width vertical colour bars. */
    private fun swatchStrip(c: Canvas, path: Path, r: RectF, palette: ColorPalette) {
        c.save()
        c.clipPath(path)
        val ramp = palette.ramp.ifEmpty { listOf(palette.accent) }
        val step = r.width() / ramp.size
        ramp.forEachIndexed { i, color ->
            EffectPainter.fill(c, Path().apply {
                addRect(r.left + i * step, r.top, r.left + (i + 1) * step + 1f, r.bottom, Path.Direction.CW)
            }, color)
        }
        c.restore()
    }

    /** Picks black or white, whichever reads more legibly against [bg]. */
    fun readableTextColor(bg: Int): Int = bg.readableOn()
}
