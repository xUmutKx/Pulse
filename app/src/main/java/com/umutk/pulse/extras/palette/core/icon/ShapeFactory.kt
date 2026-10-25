package com.umutk.pulse.extras.palette.core.icon

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * All silhouettes are produced as android.graphics.Path so the same geometry can
 * be filled, stroked, clipped, blurred and used as a mask for glass effects.
 */
object ShapeFactory {

    fun path(shape: TileShape, r: RectF, cornerFraction: Float, exponent: Float = 4f): Path {
        val p = Path()
        val w = r.width(); val h = r.height()
        val radius = (cornerFraction.coerceIn(0f, 0.5f)) * minOf(w, h)
        when (shape) {
            TileShape.CIRCLE -> p.addOval(r, Path.Direction.CW)
            TileShape.ROUNDED_SQUARE -> p.addRoundRect(r, radius, radius, Path.Direction.CW)
            TileShape.PILL -> p.addRoundRect(r, minOf(w, h) / 2f, minOf(w, h) / 2f, Path.Direction.CW)
            TileShape.SQUIRCLE -> superellipse(p, r, exponent.coerceIn(2f, 12f))
            TileShape.SUPERELLIPSE -> superellipse(p, r, (exponent * 1.6f).coerceIn(2f, 16f))
            TileShape.PEBBLE -> pebble(p, r, radius)
            TileShape.TEARDROP -> teardrop(p, r, radius)
            TileShape.HEXAGON -> polygon(p, r, 6, 90f)
        }
        return p
    }

    /** |x|^n + |y|^n = 1 — the true Apple/One UI corner curve, not a rounded rect. */
    private fun superellipse(p: Path, r: RectF, n: Float, steps: Int = 180) {
        val cx = r.centerX(); val cy = r.centerY()
        val a = r.width() / 2f; val b = r.height() / 2f
        val e = 2f / n
        for (i in 0..steps) {
            val t = (i.toFloat() / steps) * (2.0 * Math.PI).toFloat()
            val ct = cos(t); val st = sin(t)
            val x = cx + a * sign(ct) * abs(ct).pow(e)
            val y = cy + b * sign(st) * abs(st).pow(e)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** Asymmetric organic blob: four corners with different radii. */
    private fun pebble(p: Path, r: RectF, radius: Float) {
        val k = radius
        val tl = k * 1.35f; val tr = k * 0.72f; val br = k * 1.35f; val bl = k * 0.72f
        p.moveTo(r.left + tl, r.top)
        p.lineTo(r.right - tr, r.top)
        p.quadTo(r.right, r.top, r.right, r.top + tr)
        p.lineTo(r.right, r.bottom - br)
        p.quadTo(r.right, r.bottom, r.right - br, r.bottom)
        p.lineTo(r.left + bl, r.bottom)
        p.quadTo(r.left, r.bottom, r.left, r.bottom - bl)
        p.lineTo(r.left, r.top + tl)
        p.quadTo(r.left, r.top, r.left + tl, r.top)
        p.close()
    }

    private fun teardrop(p: Path, r: RectF, radius: Float) {
        p.addRoundRect(r, floatArrayOf(
            radius, radius, radius, radius,
            radius, radius, minOf(r.width(), r.height()) / 2f, minOf(r.width(), r.height()) / 2f
        ), Path.Direction.CW)
    }

    private fun polygon(p: Path, r: RectF, sides: Int, rotationDeg: Float) {
        val cx = r.centerX(); val cy = r.centerY()
        val rad = minOf(r.width(), r.height()) / 2f
        for (i in 0 until sides) {
            val a = Math.toRadians((rotationDeg + i * 360f / sides).toDouble())
            val x = cx + rad * cos(a).toFloat()
            val y = cy + rad * sin(a).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }
}
