package com.umutk.pulse.extras.weather

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The real moon of tonight, drawn (no photograph): its face with the dark seas where they really are, craters and Tycho's rays; the part the sun lights by the true phase,
 * and the night side not black but faintly lit by the earth (earthshine), the face still showing through. On a plain Canvas, so the weather page, the scenery and the widget share it.
 */
object MoonArt {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The share of the disc that is lit (0 new .. 1 full) for a moon [age] in days. */
    fun lit(age: Double): Float = ((1 - cos(2 * PI * age / WeatherFmt.SYNODIC)) / 2).toFloat()

    private fun face(c: Canvas, cx: Float, cy: Float, r: Float) {
        p.shader = RadialGradient(cx - r * .25f, cy - r * .3f, r * 1.45f, intArrayOf(0xFFF6F2DE.toInt(), 0xFFE7E1C9.toInt(), 0xFFBEB8A1.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p); p.shader = null
        fun sea(x: Float, y: Float, rx: Float, ry: Float, a: Float) {
            val ox = cx + r * x; val oy = cy + r * y
            val sc = Color.argb((a * 255).toInt(), 0x62, 0x60, 0x5A)
            p.shader = RadialGradient(ox, oy, r * maxOf(rx, ry), intArrayOf(sc, Color.argb((a * 200).toInt(), 0x75, 0x72, 0x6A), 0), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
            c.drawOval(RectF(ox - r * rx, oy - r * ry, ox + r * rx, oy + r * ry), p); p.shader = null
        }
        sea(-.52f, -.12f, .34f, .40f, .72f); sea(-.62f, .10f, .20f, .26f, .62f)
        sea(-.14f, -.52f, .27f, .22f, .80f)
        sea(.17f, -.40f, .16f, .15f, .80f); sea(.02f, -.36f, .10f, .10f, .62f)
        sea(.38f, -.14f, .22f, .17f, .75f); sea(.62f, .14f, .10f, .15f, .60f); sea(.70f, -.34f, .10f, .08f, .80f)
        sea(-.28f, .32f, .17f, .13f, .60f); sea(-.66f, .34f, .09f, .09f, .65f); sea(.05f, -.84f, .30f, .06f, .45f)
        val craters = floatArrayOf(-.36f, -.58f, .05f, .30f, .40f, .06f, .52f, .50f, .045f, -.05f, .12f, .04f, .14f, .22f, .035f, -.78f, -.05f, .04f, .80f, .12f, .035f, -.12f, .62f, .04f, .40f, -.62f, .035f, -.45f, .55f, .035f)
        for (i in craters.indices step 3) {
            val ox = cx + r * craters[i]; val oy = cy + r * craters[i + 1]; val k = craters[i + 2]
            p.style = Paint.Style.FILL; p.color = Color.argb(60, 0x6E, 0x6A, 0x5C); c.drawCircle(ox, oy, r * k, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * .012f; p.color = Color.argb(100, 255, 255, 255); c.drawCircle(ox - r * k * .12f, oy - r * k * .12f, r * k, p); p.style = Paint.Style.FILL
        }
        val tx = cx - r * .27f; val ty = cy + r * .60f
        p.strokeWidth = r * .014f; p.strokeCap = Paint.Cap.ROUND; p.color = Color.argb(66, 255, 255, 255)
        for (i in 0 until 16) { val a = i * (2 * PI.toFloat() / 16f) + .2f; val len = r * (.30f + .22f * ((i * 37) % 7) / 7f); c.drawLine(tx, ty, tx + cos(a) * len, ty + sin(a) * len, p) }
        p.color = Color.argb(240, 255, 255, 255); c.drawCircle(tx, ty, r * .035f, p)
        p.color = Color.argb(225, 255, 255, 255); c.drawCircle(cx - r * .22f, cy - r * .28f, r * .028f, p)
        // a darker edge, so the disc reads as a ball
        p.shader = RadialGradient(cx, cy, r, intArrayOf(0, 0, 0x4A000000), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP); c.drawCircle(cx, cy, r, p); p.shader = null
    }

    /** Draws the moon of [age] days at ([cx], [cy]) with radius [r]; [alpha] (0..1) fades the whole of it (behind a cloudy sky). */
    fun draw(c: Canvas, cx: Float, cy: Float, r: Float, age: Double, alpha: Float = 1f) {
        if (r <= 0f || alpha <= 0f) return
        val disc = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }
        c.saveLayerAlpha(cx - r - 2f, cy - r - 2f, cx + r + 2f, cy + r + 2f, (alpha * 255).toInt().coerceIn(0, 255))
        face(c, cx, cy, r)
        // the lit part: the half toward the sun closed by the terminator (an ellipse, wider the nearer to the quarter); waning moons are lit from the left
        val k = cos(2 * PI * age / WeatherFmt.SYNODIC).toFloat()
        val rx = r * abs(k)
        val lit = Path()
        lit.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), -90f, 180f, true)
        lit.arcTo(RectF(cx - rx, cy - r, cx + rx, cy + r), 90f, if (k < 0) 180f else -180f, false)
        lit.close()
        if (age >= WeatherFmt.SYNODIC / 2) lit.transform(Matrix().apply { setScale(-1f, 1f, cx, cy) })
        val dark = Path(disc); dark.op(lit, Path.Op.DIFFERENCE)
        // the night side: the face under a deep blue shade, a little earthshine left on it
        p.color = 0xD90B1020.toInt(); c.drawPath(dark, p)
        // a soft edge along the terminator: the lit side dims a little toward the night
        c.save(); c.clipPath(lit)
        val towardDark = if (age >= WeatherFmt.SYNODIC / 2) 1f else -1f
        val ex = cx + towardDark * rx * (if (k < 0) -1f else 1f)
        p.shader = LinearGradient(ex - towardDark * r * .25f, cy, ex + towardDark * r * .05f, cy, 0, 0x55000000, Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p); p.shader = null
        c.restore()
        c.restore()
    }
}
