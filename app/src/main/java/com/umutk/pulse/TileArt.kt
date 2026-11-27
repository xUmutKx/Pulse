package com.umutk.pulse

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The tap animations of the Toolkit quick tiles, each drawn for its own tool (white on the tile, 24 units wide, [t] runs 0..1):
 * the network bars switch on one by one, the battery fills, the thermometer rises, the timer's hand goes round, the bell rings …
 */
object TileArt {
    private fun ease(t: Float) = 1f - (1f - t) * (1f - t) * (1f - t)
    private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    /** Draws frame [t] of [kind]; false when there is no drawing of its own for it. */
    fun draw(kind: String, cv: Canvas, size: Int, t: Float): Boolean {
        val k = size / 24f
        cv.save(); cv.scale(k, k)
        val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
        val dim = Paint(fill).apply { alpha = 70 }
        val ok = true
        when (kind) {
            // network: the four signal bars go off, then come on one after the other
            "net" -> for (i in 0 until 4) {
                val on = t > 0.15f + i * 0.18f
                val h = 4f + i * 4f
                cv.drawRoundRect(RectF(3f + i * 5f, 20f - h, 6.5f + i * 5f, 20f), 0.8f, 0.8f, if (on) fill else dim)
            }
            // battery: empties, then fills up again with a bolt at the end
            "battery" -> {
                cv.drawRoundRect(RectF(4f, 7f, 19f, 17f), 1.8f, 1.8f, st)
                cv.drawRect(19.5f, 10f, 21f, 14f, fill)
                val w = 13f * ease(t)
                cv.drawRect(5.5f, 8.5f, 5.5f + w, 15.5f, fill)
            }
            // thermometer: the mercury rises and settles
            "thermal" -> {
                cv.drawRoundRect(RectF(10f, 3f, 14f, 16f), 2f, 2f, st)
                cv.drawCircle(12f, 18f, 3f, fill)
                val h = 11f * ease(t) * (1f + 0.08f * sin(t * 6f * PI.toFloat()) * (1f - t))
                cv.drawRect(11.2f, 16f - h, 12.8f, 16f, fill)
            }
            // timer: the hand sweeps round once, the marks light as it passes
            "timer" -> {
                cv.drawCircle(12f, 13f, 8f, st)
                cv.drawLine(10f, 3f, 14f, 3f, st)
                val a = (-90f + 360f * ease(t)) * PI.toFloat() / 180f
                cv.drawLine(12f, 13f, 12f + 6f * cos(a), 13f + 6f * sin(a), st)
                for (m in 0 until 12) if (m / 12f <= ease(t)) { val b = (m * 30f - 90f) * PI.toFloat() / 180f; cv.drawCircle(12f + 8f * cos(b), 13f + 8f * sin(b), 0.6f, fill) }
            }
            // compass: the needle swings and settles on north
            "compass" -> {
                cv.drawCircle(12f, 12f, 9f, st)
                val a = 70f * sin(t * 5f * PI.toFloat()) * (1f - t)
                cv.save(); cv.rotate(a, 12f, 12f)
                cv.drawPath(Path().apply { moveTo(12f, 5f); lineTo(14f, 12f); lineTo(10f, 12f); close() }, fill)
                cv.drawPath(Path().apply { moveTo(12f, 19f); lineTo(14f, 12f); lineTo(10f, 12f); close() }, dim)
                cv.restore()
            }
            // spirit level: the bubble runs to one side, then the other, and stops in the middle
            "level" -> {
                cv.drawRoundRect(RectF(2f, 9f, 22f, 15f), 3f, 3f, st)
                cv.drawLine(10f, 9f, 10f, 15f, st); cv.drawLine(14f, 9f, 14f, 15f, st)
                cv.drawCircle(12f + 7f * sin(t * 4f * PI.toFloat()) * (1f - t), 12f, 1.8f, fill)
            }
            // light meter: the sun's rays grow out
            "lux" -> {
                cv.drawCircle(12f, 12f, 3.5f, fill)
                val r = 5.5f + 3f * ease(t)
                for (i in 0 until 8) { val a = i * PI.toFloat() / 4f + t; cv.drawLine(12f + 5.5f * cos(a), 12f + 5.5f * sin(a), 12f + r * cos(a), 12f + r * sin(a), st) }
            }
            // screenshot: the corners close in, a flash
            "shot" -> {
                val d = 4f * (1f - ease(t))
                for ((x, y, sx, sy) in listOf(listOf(3f, 3f, 1f, 1f), listOf(21f, 3f, -1f, 1f), listOf(3f, 21f, 1f, -1f), listOf(21f, 21f, -1f, -1f))) {
                    val px = x + sx * d; val py = y + sy * d
                    cv.drawLine(px, py, px + sx * 5f, py, st); cv.drawLine(px, py, px, py + sy * 5f, st)
                }
                if (t in 0.55f..0.8f) cv.drawCircle(12f, 12f, 6f, fill)
            }
            // sound mode: the bell rings, with the waves either side
            "sound" -> {
                cv.save(); cv.rotate(25f * sin(t * 8f * PI.toFloat()) * (1f - t), 12f, 4f)
                cv.drawPath(Path().apply { moveTo(6f, 17f); lineTo(18f, 17f); lineTo(16.5f, 15f); lineTo(16.5f, 10f); cubicTo(16.5f, 4f, 7.5f, 4f, 7.5f, 10f); lineTo(7.5f, 15f); close() }, st)
                cv.drawCircle(12f, 19.5f, 1.4f, fill)
                cv.restore()
                if (t < 0.8f) { val w = Paint(st).apply { alpha = (255 * (1f - t)).toInt() }; cv.drawArc(RectF(1f, 6f, 6f, 16f), 120f, 120f, false, w); cv.drawArc(RectF(18f, 6f, 23f, 16f), -60f, 120f, false, w) }
            }
            // volume: the speaker, its waves appear one by one
            "volume" -> {
                cv.drawPath(Path().apply { moveTo(4f, 10f); lineTo(7f, 10f); lineTo(11f, 6f); lineTo(11f, 18f); lineTo(7f, 14f); lineTo(4f, 14f); close() }, fill)
                for (i in 0 until 3) if (t > 0.2f + i * 0.22f) cv.drawArc(RectF(9f - i * 3f, 8f - i * 3f, 15f + i * 3f, 16f + i * 3f), -45f, 90f, false, st)
            }
            // clipboard: lines are written on it
            "clip" -> {
                cv.drawRoundRect(RectF(5f, 4f, 19f, 21f), 1.8f, 1.8f, st)
                cv.drawRoundRect(RectF(9f, 2.5f, 15f, 5.5f), 1f, 1f, fill)
                for (i in 0 until 3) { val p = seg(t, i * 0.28f, i * 0.28f + 0.3f); if (p > 0f) cv.drawLine(8f, 10f + i * 3.5f, 8f + (i.let { if (it == 2) 5f else 8f }) * p, 10f + i * 3.5f, st) }
            }
            // sensors off: the circle, then the slash is drawn across
            "sensors" -> {
                cv.drawCircle(12f, 12f, 8f, st)
                val p = ease(t)
                cv.drawLine(6.3f, 6.3f, 6.3f + 11.4f * p, 6.3f + 11.4f * p, st)
            }
            // memory: the chip's bars fill from left to right
            "memory" -> {
                cv.drawRoundRect(RectF(4f, 6f, 20f, 18f), 1.5f, 1.5f, st)
                for (i in 0 until 4) { cv.drawLine(7f + i * 3.5f, 4f, 7f + i * 3.5f, 6f, st); cv.drawLine(7f + i * 3.5f, 18f, 7f + i * 3.5f, 20f, st) }
                for (i in 0 until 4) cv.drawRect(6.2f + i * 3.2f, 8.5f, 8.4f + i * 3.2f, 15.5f, if (t > 0.15f + i * 0.2f) fill else dim)
            }
            // calculator: the keys light one after the other
            "calc" -> {
                cv.drawRoundRect(RectF(5f, 2f, 19f, 22f), 2f, 2f, st)
                cv.drawRect(7.5f, 4.5f, 16.5f, 8f, fill)
                for (i in 0 until 9) { val x = 7.5f + (i % 3) * 3.3f; val y = 10.5f + (i / 3) * 3.5f; cv.drawRect(x, y, x + 2.4f, y + 2.4f, if ((t * 9f).toInt() == i || t >= 0.99f) fill else dim) }
            }
            // finder: the magnifier circles round and looks
            "find" -> {
                val a = t * 2f * PI.toFloat(); val r = 2.5f * (1f - t)
                val cx = 10f + r * cos(a); val cy = 10f + r * sin(a)
                cv.drawCircle(cx, cy, 5.5f, st); cv.drawLine(cx + 4f, cy + 4f, cx + 9f, cy + 9f, Paint(st).apply { strokeWidth = 2.6f })
            }
            // dink: a drop falls and its rings spread
            "dink" -> {
                if (t < 0.4f) cv.drawCircle(12f, 3f + 12f * (t / 0.4f), 1.8f, fill)
                else for (i in 0 until 2) { val p = seg(t, 0.4f + i * 0.15f, 1f); cv.drawOval(RectF(12f - 9f * p, 16f - 3f * p, 12f + 9f * p, 16f + 3f * p), Paint(st).apply { alpha = (255 * (1f - p)).toInt() }) }
            }
            // scale: the square grows out to its corners and back
            "scale" -> {
                val g = 3f + 4f * sin(t * PI.toFloat())
                cv.drawRect(12f - g, 12f - g, 12f + g, 12f + g, st)
                for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) { val x = 12f + sx * 9f; val y = 12f + sy * 9f; cv.drawLine(x, y, x - sx * 3f, y, st); cv.drawLine(x, y, x, y - sy * 3f, st) }
            }
            // rotation: the phone turns a quarter and the arrow goes round it
            "rotate" -> {
                cv.save(); cv.rotate(90f * ease(t), 12f, 12f)
                cv.drawRoundRect(RectF(8f, 4f, 16f, 20f), 1.6f, 1.6f, st)
                cv.restore()
                cv.drawArc(RectF(2f, 2f, 22f, 22f), -100f, 80f * ease(t) + 10f, false, st)
            }
            // touches: a finger taps and its ring spreads
            "touch" -> {
                val p = seg(t, 0.25f, 1f)
                cv.drawCircle(12f, 12f, 2.5f, fill)
                if (p > 0f) cv.drawCircle(12f, 12f, 3f + 7f * p, Paint(st).apply { alpha = (255 * (1f - p)).toInt() })
                if (p > 0.3f) cv.drawCircle(12f, 12f, 3f + 7f * (p - 0.3f), Paint(st).apply { alpha = (255 * (1f - p)).toInt() })
            }
            // pointer: the arrow moves up to the middle and clicks
            "pointer" -> {
                val p = ease(seg(t, 0f, 0.6f)); val x = 3f + 6f * p; val y = 3f + 6f * p
                cv.drawPath(Path().apply { moveTo(x, y); lineTo(x, y + 12f); lineTo(x + 3.2f, y + 9f); lineTo(x + 6f, y + 14f); lineTo(x + 8f, y + 13f); lineTo(x + 5.3f, y + 8f); lineTo(x + 9f, y + 8f); close() }, fill)
                if (t > 0.65f) cv.drawCircle(x, y, 2f + 4f * seg(t, 0.65f, 1f), Paint(st).apply { alpha = (255 * (1f - seg(t, 0.65f, 1f))).toInt() })
            }
            // motion cues: the dots at the edges drift with the car
            "motion" -> {
                for (i in 0 until 3) { val y = 6f + i * 6f; val dx = 3f * sin(t * 2f * PI.toFloat() + i); cv.drawCircle(3f + dx, y, 1.3f, fill); cv.drawCircle(21f + dx, y, 1.3f, fill) }
                cv.drawRoundRect(RectF(8f, 6f, 16f, 18f), 2f, 2f, st)
            }
            // power: the ring is drawn round, then the line
            "power" -> {
                cv.drawArc(RectF(4f, 5f, 20f, 21f), -60f, 300f * ease(seg(t, 0f, 0.7f)), false, st)
                cv.drawLine(12f, 12f, 12f, 12f - 9f * seg(t, 0.5f, 1f), st)
            }
            // floating timer: the hourglass turns over and its sand runs down
            "hourglass" -> {
                val turn = 180f * ease(seg(t, 0f, 0.4f))
                cv.save(); cv.rotate(turn, 12f, 12f)
                cv.drawPath(Path().apply { moveTo(6f, 3f); lineTo(18f, 3f); lineTo(18f, 6f); lineTo(14f, 12f); lineTo(18f, 18f); lineTo(18f, 21f); lineTo(6f, 21f); lineTo(6f, 18f); lineTo(10f, 12f); lineTo(6f, 6f); close() }, st)
                cv.restore()
                val sand = seg(t, 0.4f, 1f)
                cv.drawPath(Path().apply { moveTo(8f, 5f + 5f * sand); lineTo(16f, 5f + 5f * sand); lineTo(12f, 11f); close() }, fill)
                if (sand > 0f) { cv.drawLine(12f, 12f, 12f, 17f, Paint(st).apply { strokeWidth = 1f }); cv.drawPath(Path().apply { moveTo(12f, 19.5f - 3f * sand); lineTo(16f, 19.5f); lineTo(8f, 19.5f); close() }, fill) }
            }
            // the toolkit: four tiles pop in one after the other
            "toolkit" -> for (i in 0 until 4) {
                val p = ease(seg(t, i * 0.15f, i * 0.15f + 0.45f)); val x = 4f + (i % 2) * 9f; val y = 4f + (i / 2) * 9f; val h = 3.5f * p
                if (p > 0f) cv.drawRoundRect(RectF(x + 3.5f - h, y + 3.5f - h, x + 3.5f + h, y + 3.5f + h), 1.2f, 1.2f, fill)
            }
            else -> { cv.restore(); return false }
        }
        cv.restore()
        return ok
    }
}


/** Plays [kind] of [TileArt] on the tile of any quick tile service (not only the Toolkit's), then [then]. */
fun android.service.quicksettings.TileService.playTileArt(kind: String, then: () -> Unit) {
    val main = android.os.Handler(android.os.Looper.getMainLooper())
    val size = 128; val frames = 16
    var i = 0
    val step = object : Runnable {
        override fun run() {
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            if (!TileArt.draw(kind, android.graphics.Canvas(bmp), size, i / frames.toFloat())) { then(); return }
            qsTile?.apply { state = android.service.quicksettings.Tile.STATE_ACTIVE; icon = android.graphics.drawable.Icon.createWithBitmap(bmp); updateTile() }
            if (i < frames) { i++; main.postDelayed(this, 45L) }
            else {
                // the tile's own icon again, then its job
                qsTile?.apply { icon = android.graphics.drawable.Icon.createWithResource(this@playTileArt, runCatching { packageManager.getServiceInfo(android.content.ComponentName(this@playTileArt, this@playTileArt.javaClass), 0).icon }.getOrDefault(0)); updateTile() }
                then()
            }
        }
    }
    main.post(step)
}
