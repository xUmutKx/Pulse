package com.umutk.pulse.extras.palette.core.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.umutk.pulse.extras.palette.core.color.lighten
import com.umutk.pulse.extras.palette.core.color.withAlpha
import com.umutk.pulse.extras.palette.core.icon.EffectPainter
import com.umutk.pulse.extras.palette.core.icon.WallpaperStyle
import com.umutk.pulse.extras.palette.core.palette.ColorPalette
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * Turns a [ColorPalette] into a full-bleed wallpaper bitmap. Reuses the same
 * Canvas-based EffectPainter primitives the icon renderer draws with, so a
 * wallpaper always reads as the same "material" as the icons sitting on it.
 */
object WallpaperGenerator {

    fun generate(
        palette: ColorPalette,
        style: WallpaperStyle,
        width: Int,
        height: Int,
        seed: Long = 0L
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(1, width), max(1, height), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
        val full = Path().apply { addRect(r, Path.Direction.CW) }

        when (style) {
            WallpaperStyle.GRADIENT_MESH -> gradientMesh(c, full, r, palette)
            WallpaperStyle.ANGLED_BANDS -> angledBands(c, r, palette)
            WallpaperStyle.SOFT_BLOBS -> softBlobs(c, full, r, palette, seed)
            WallpaperStyle.MINIMAL_FADE -> minimalFade(c, full, r, palette)
            WallpaperStyle.AURORA_WAVES -> auroraWaves(c, full, r, palette)
            WallpaperStyle.CONFETTI_DOTS -> confettiDots(c, full, r, palette, seed)
        }
        return bmp
    }

    private fun gradientMesh(c: Canvas, full: Path, r: RectF, palette: ColorPalette) {
        val ramp = palette.ramp
        EffectPainter.gradient(c, full, r, ramp.first(), ramp.last(), 125f)
        if (ramp.size >= 3) {
            EffectPainter.gradient(c, full, r, palette.accent, ramp.first(), 35f, alpha = 0.30f)
        }
        EffectPainter.noise(c, full, r, 0.10f, seed = 7L)
    }

    private fun angledBands(c: Canvas, r: RectF, palette: ColorPalette) {
        val ramp = palette.ramp.ifEmpty { listOf(palette.darkest) }
        val n = ramp.size
        val bandWidth = (r.width() + r.height()) / n
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.rotate(-18f, r.centerX(), r.centerY())
        val span = r.width() + r.height() * 2f
        var x = -r.height()
        var i = 0
        while (x < span) {
            paint.color = ramp[i % n]
            c.drawRect(x, -r.height(), x + bandWidth, r.height() * 2f, paint)
            x += bandWidth
            i++
        }
        c.restore()
    }

    private fun softBlobs(c: Canvas, full: Path, r: RectF, palette: ColorPalette, seed: Long) {
        val rnd = Random(seed)
        // The ramp's darkest stop is often near-black; lighten the base fill a
        // touch so the blobs sit on a moody but not pure-black backdrop.
        EffectPainter.fill(c, full, palette.darkest.lighten(0.12f))
        val ramp = palette.ramp
        val blobCount = (ramp.size).coerceIn(3, 7)
        repeat(blobCount) { i ->
            val color = ramp[i % ramp.size]
            val cx = rnd.nextFloat() * r.width()
            val cy = rnd.nextFloat() * r.height()
            val radius = (0.28f + rnd.nextFloat() * 0.30f) * max(r.width(), r.height())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    cx, cy, radius,
                    color.withAlpha(200), color.withAlpha(0),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            c.drawCircle(cx, cy, radius, paint)
        }
        EffectPainter.noise(c, full, r, 0.06f, seed = seed + 1)
    }

    private fun minimalFade(c: Canvas, full: Path, r: RectF, palette: ColorPalette) {
        EffectPainter.gradient(c, full, r, palette.darkest, palette.lightest, 90f)
    }

    /** Ribbons of the ramp's colours drifting across a dark base, phase = 0 for the static render. */
    private fun auroraWaves(c: Canvas, full: Path, r: RectF, palette: ColorPalette, phase: Float = 0f) {
        EffectPainter.gradient(c, full, r, palette.darkest, palette.sample(0.32f), 100f)
        val ramp = palette.ramp.ifEmpty { listOf(palette.accent) }
        val bands = ramp.size.coerceIn(3, 6)
        val wavelength = r.width() * 0.8f
        repeat(bands) { i ->
            val color = ramp[i % ramp.size]
            val yCenter = r.top + r.height() * (0.15f + 0.7f * i / bands.toFloat())
            val amplitude = r.height() * 0.06f
            val thickness = r.height() * 0.14f
            val steps = 40
            val path = Path()
            for (s in 0..steps) {
                val x = r.left + r.width() * s / steps
                val y = yCenter - thickness / 2f + amplitude * sin((2f * Math.PI.toFloat() * x / wavelength + phase + i))
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            for (s in steps downTo 0) {
                val x = r.left + r.width() * s / steps
                val y = yCenter + thickness / 2f + amplitude * sin((2f * Math.PI.toFloat() * x / wavelength + phase + i))
                path.lineTo(x, y)
            }
            path.close()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.withAlpha(150) }
            c.drawPath(path, paint)
        }
        EffectPainter.noise(c, full, r, 0.05f, seed = 11L)
    }

    /** Scattered dots of the ramp's colours over a light base — a playful, offline "confetti" look. */
    private fun confettiDots(c: Canvas, full: Path, r: RectF, palette: ColorPalette, seed: Long) {
        EffectPainter.fill(c, full, palette.lightest)
        val rnd = Random(seed)
        val ramp = palette.ramp.ifEmpty { listOf(palette.accent) }
        repeat(60) {
            val color = ramp[rnd.nextInt(ramp.size)]
            val cx = rnd.nextFloat() * r.width()
            val cy = rnd.nextFloat() * r.height()
            val radius = (0.015f + rnd.nextFloat() * 0.035f) * max(r.width(), r.height())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color.withAlpha(210) }
            c.drawCircle(cx, cy, radius, paint)
        }
        EffectPainter.noise(c, full, r, 0.04f, seed = seed + 3)
    }
}
