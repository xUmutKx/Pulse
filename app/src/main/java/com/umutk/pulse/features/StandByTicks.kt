package com.umutk.pulse.features

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.umutk.pulse.R
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Two faces with a ring of ticks along the edge of the screen: sixty short lines standing on the border of the screen, each pointing at the middle, equally
 * spaced in angle (so they are wider apart along the long sides). The first is a big digital clock, the second an analog one.
 */

private val RED = Color(0xFFFF1010)

/** The ticks: [dim] is the one of the current second (drawn faint), every fifth is longer. */
private fun DrawScope.ticks(ink: Color, dim: Int, margin: Float, long: Boolean) {
    val c = center
    val a = size.width / 2f - margin; val b = size.height / 2f - margin
    val len = min(size.width, size.height) * .075f
    for (i in 0 until 60) {
        val t = i * 2f * PI.toFloat() / 60f
        val s = sin(t); val k = cos(t)
        val f = 1f / max(abs(s), abs(k))
        val p = Offset(c.x + a * s * f, c.y - b * k * f)
        val dir = Offset(c.x - p.x, c.y - p.y); val d = dir.getDistance()
        val l = if (long && i % 5 == 0) len * 1.8f else len
        val q = Offset(p.x + dir.x / d * l, p.y + dir.y / d * l)
        drawLine(if (i == dim) ink.copy(alpha = .25f) else ink, p, q, min(size.width, size.height) * .0095f, StrokeCap.Round)
    }
}

@Composable
internal fun TickDigitalFace(ctx: Context, ink: Color, tick: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + tick * 0 }
    val face = remember { ResourcesCompat.getFont(ctx, R.font.sb_bebas) ?: Typeface.DEFAULT_BOLD }
    val h24 = StandByPrefs.h24(ctx)
    val hh = if (h24) cal.get(Calendar.HOUR_OF_DAY) else (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it })
    val time = "%02d:%02d".format(hh, cal.get(Calendar.MINUTE))
    val wd = java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time).uppercase(Locale.getDefault()).take(3)
    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
    Canvas(Modifier.fillMaxSize()) {
        ticks(ink, cal.get(Calendar.SECOND), min(size.width, size.height) * .035f, long = false)
        drawIntoCanvas { cv ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.CENTER; color = ink.toArgb() }
            // the digits fill the middle: as high as 70 % of the screen's height, or as wide as the ticks leave room for
            var size = this.size.height * .78f
            p.textSize = size
            val room = this.size.width * .72f
            val w = p.measureText(time)
            if (w > room) { size *= room / w; p.textSize = size }
            cv.nativeCanvas.drawText(time, center.x, center.y + size * .36f, p)
            // the day at the top: the weekday red, the number white
            val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = this@Canvas.size.height * .11f; color = RED.toArgb(); textAlign = Paint.Align.LEFT }
            val dw = dp.measureText(wd); val nw = dp.measureText(" $day")
            val x0 = center.x - (dw + nw) / 2f; val y0 = this.size.height * .2f
            cv.nativeCanvas.drawText(wd, x0, y0, dp)
            dp.color = ink.toArgb(); cv.nativeCanvas.drawText(" $day", x0 + dw, y0, dp)
        }
    }
}

@Composable
internal fun TickAnalogFace(ctx: Context, ink: Color, tick: Long) {
    val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + tick * 0 }
    val face = remember { ResourcesCompat.getFont(ctx, R.font.sb_bebas) ?: Typeface.DEFAULT_BOLD }
    val s = cal.get(Calendar.SECOND) + (System.currentTimeMillis() % 1000) / 1000f
    val m = cal.get(Calendar.MINUTE) + s / 60f; val h = (cal.get(Calendar.HOUR) % 12) + m / 60f
    val wd = java.text.SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time).uppercase(Locale.getDefault()).take(3)
    val day = cal.get(Calendar.DAY_OF_MONTH).toString()
    Canvas(Modifier.fillMaxSize()) {
        val mn = min(size.width, size.height)
        ticks(ink.copy(alpha = .9f), -1, mn * .035f, long = true)
        val c = center
        drawIntoCanvas { cv ->
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textAlign = Paint.Align.CENTER; color = ink.toArgb(); textSize = mn * .2f }
            // 12, 3, 6 and 9 inside the ticks
            val ry = size.height / 2f - mn * .2f; val rx = size.width / 2f - mn * .22f
            cv.nativeCanvas.drawText("12", c.x, c.y - ry + p.textSize * .85f, p)
            cv.nativeCanvas.drawText("6", c.x, c.y + ry, p)
            cv.nativeCanvas.drawText("9", c.x - rx, c.y + p.textSize * .36f, p)
            cv.nativeCanvas.drawText("3", c.x + rx, c.y + p.textSize * .36f, p)
            // the date beside the 3
            val dp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = mn * .11f; textAlign = Paint.Align.RIGHT; color = ink.toArgb() }
            val nw = dp.measureText(" $day")
            val xr = c.x + rx - p.textSize * .55f
            cv.nativeCanvas.drawText(" $day", xr, c.y + dp.textSize * .36f, dp)
            dp.color = RED.toArgb(); cv.nativeCanvas.drawText(wd, xr - nw, c.y + dp.textSize * .36f, dp)
        }
        fun hand(frac: Float, from: Float, to: Float, w: Float, col: Color) {
            val a = frac * 2f * PI.toFloat(); val d = Offset(sin(a), -cos(a))
            drawLine(col, c + d * from, c + d * to, w, StrokeCap.Round)
        }
        val r = mn / 2f
        hand(h / 12f, 0f, r * .5f, mn * .028f, ink)
        hand(m / 60f, 0f, r * .8f, mn * .028f, ink)
        hand(s / 60f, -r * .08f, r * .86f, mn * .008f, RED)
        drawCircle(RED, mn * .02f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(mn * .008f))
    }
}
