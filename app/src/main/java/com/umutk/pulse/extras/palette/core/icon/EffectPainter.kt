package com.umutk.pulse.extras.palette.core.icon

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.umutk.pulse.extras.palette.core.color.darken
import com.umutk.pulse.extras.palette.core.color.lighten
import com.umutk.pulse.extras.palette.core.color.withAlpha
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every surface treatment the Tiles and Styled renderers can stack.
 * All of it is plain Canvas work, so it runs on any device, offline, and is
 * hardware-accelerated when drawn straight into a Compose layer.
 */
object EffectPainter {

    fun fill(c: Canvas, path: Path, color: Int, alpha: Float = 1f) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.withAlpha((255 * alpha).toInt())
        }
        c.drawPath(path, p)
    }

    fun gradient(c: Canvas, path: Path, r: RectF, from: Int, to: Int, angleDeg: Float, alpha: Float = 1f) {
        val a = Math.toRadians(angleDeg.toDouble())
        val dx = cos(a).toFloat() * r.width() / 2f
        val dy = sin(a).toFloat() * r.height() / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                r.centerX() - dx, r.centerY() - dy,
                r.centerX() + dx, r.centerY() + dy,
                from.withAlpha((255 * alpha).toInt()), to.withAlpha((255 * alpha).toInt()),
                Shader.TileMode.CLAMP
            )
        }
        c.drawPath(path, p)
    }

    fun dropShadow(c: Canvas, path: Path, color: Int, strength: Float, size: Float) {
        if (strength <= 0.01f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.BLACK.withAlpha((90 * strength).toInt().coerceIn(0, 255))
            maskFilter = BlurMaskFilter(size * 0.10f * strength + 1f, BlurMaskFilter.Blur.NORMAL)
        }
        c.save()
        c.translate(0f, size * 0.035f * strength)
        c.drawPath(path, p)
        c.restore()
    }

    /** Inner shadow via CLEAR-blurred inverse — no RenderScript needed. */
    fun innerShadow(c: Canvas, path: Path, r: RectF, strength: Float, size: Float) {
        if (strength <= 0.01f) return
        val layer = c.saveLayer(r, null)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK.withAlpha((150 * strength).toInt())
            maskFilter = BlurMaskFilter(size * 0.09f * strength + 1f, BlurMaskFilter.Blur.NORMAL)
            style = Paint.Style.STROKE
            strokeWidth = size * 0.10f
        }
        c.clipPath(path)
        c.drawPath(path, p)
        c.restoreToCount(layer)
    }

    fun stroke(c: Canvas, path: Path, color: Int, width: Float, alpha: Float = 1f) {
        if (width <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            this.color = color.withAlpha((255 * alpha).toInt())
        }
        c.drawPath(path, p)
    }

    fun glow(c: Canvas, path: Path, color: Int, strength: Float, size: Float) {
        if (strength <= 0.01f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.withAlpha((170 * strength).toInt())
            maskFilter = BlurMaskFilter(size * 0.16f * strength + 1f, BlurMaskFilter.Blur.OUTER)
        }
        c.drawPath(path, p)
    }

    /** Specular sheen used by glossy / glass / liquid finishes. */
    fun sheen(c: Canvas, path: Path, r: RectF, strength: Float, warp: Float = 0f) {
        if (strength <= 0.01f) return
        val save = c.save()
        c.clipPath(path)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                r.left, r.top, r.left + r.width() * 0.35f, r.bottom,
                intArrayOf(
                    Color.WHITE.withAlpha((165 * strength).toInt()),
                    Color.WHITE.withAlpha((40 * strength).toInt()),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.42f, 1f), Shader.TileMode.CLAMP
            )
        }
        if (warp > 0f) {
            val arc = Path()
            arc.moveTo(r.left, r.top + r.height() * (0.62f - warp * 0.2f))
            arc.quadTo(r.centerX(), r.top - r.height() * 0.10f,
                r.right, r.top + r.height() * (0.30f - warp * 0.1f))
            arc.lineTo(r.right, r.top); arc.lineTo(r.left, r.top); arc.close()
            c.drawPath(arc, p)
        } else {
            c.drawRect(r, p)
        }
        c.restoreToCount(save)
    }

    /** Liquid-glass rim light + chromatic edge, Apple-style. */
    fun liquidRim(c: Canvas, path: Path, r: RectF, tint: Int, strength: Float) {
        val save = c.save()
        c.clipPath(path)
        val w = min(r.width(), r.height()) * 0.07f
        val top = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = w
            shader = LinearGradient(
                r.left, r.top, r.right, r.bottom,
                intArrayOf(
                    Color.WHITE.withAlpha((215 * strength).toInt()),
                    Color.WHITE.withAlpha((30 * strength).toInt()),
                    tint.lighten(0.25f).withAlpha((120 * strength).toInt())
                ),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawPath(path, top)
        val caustic = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                r.left + r.width() * 0.25f, r.bottom - r.height() * 0.18f,
                r.width() * 0.55f,
                intArrayOf(Color.WHITE.withAlpha((90 * strength).toInt()), Color.TRANSPARENT),
                null, Shader.TileMode.CLAMP
            )
        }
        c.drawRect(r, caustic)
        c.restoreToCount(save)
    }

    /** Deterministic film grain; keeps flat tiles from looking like plastic. */
    fun noise(c: Canvas, path: Path, r: RectF, strength: Float, seed: Long) {
        if (strength <= 0.005f) return
        val save = c.save()
        c.clipPath(path)
        val rnd = Random(seed)
        val p = Paint()
        val cell = (min(r.width(), r.height()) / 46f).coerceAtLeast(1.4f)
        var y = r.top
        while (y < r.bottom) {
            var x = r.left
            while (x < r.right) {
                val v = rnd.nextInt(256)
                p.color = (if (v > 127) Color.WHITE else Color.BLACK)
                    .withAlpha((rnd.nextInt(40) * strength).toInt())
                c.drawRect(x, y, x + cell, y + cell, p)
                x += cell
            }
            y += cell
        }
        c.restoreToCount(save)
    }

    /** Neumorphism: dual light source baked into the surface. */
    fun neumorphic(c: Canvas, path: Path, r: RectF, base: Int, depth: Float) {
        val size = min(r.width(), r.height())
        val off = size * 0.05f * depth
        val dark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = base.darken(0.18f)
            maskFilter = BlurMaskFilter(size * 0.09f * depth + 1f, BlurMaskFilter.Blur.NORMAL)
        }
        val light = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = base.lighten(0.20f)
            maskFilter = BlurMaskFilter(size * 0.09f * depth + 1f, BlurMaskFilter.Blur.NORMAL)
        }
        c.save(); c.translate(off, off); c.drawPath(path, dark); c.restore()
        c.save(); c.translate(-off, -off); c.drawPath(path, light); c.restore()
    }

    /** Cheap separable box blur — used for frosted glass over the wallpaper. */
    fun fastBlur(src: Bitmap, radiusFactor: Float = 0.12f): Bitmap {
        val down = (1f / (1f + radiusFactor * 22f)).coerceIn(0.06f, 1f)
        val w = (src.width * down).toInt().coerceAtLeast(2)
        val h = (src.height * down).toInt().coerceAtLeast(2)
        var b = Bitmap.createScaledBitmap(src, w, h, true)
        repeat(2) { b = Bitmap.createScaledBitmap(b, w, h, true) }
        return Bitmap.createScaledBitmap(b, src.width, src.height, true)
    }

    fun maskInto(dst: Canvas, path: Path, block: (Canvas) -> Unit, bounds: RectF) {
        val layer = dst.saveLayer(bounds, null)
        block(dst)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        dst.drawPath(path, p)
        dst.restoreToCount(layer)
    }
}
