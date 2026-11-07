package com.umutk.pulse.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.umutk.pulse.R
import com.umutk.pulse.features.StandByPrefs
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The screen cover's own pages drawn into a picture for the home-screen widgets, with the same colours, fonts and layout as the cover:
 * the blank black, the digital clock, the analog clocks (plain, clean, no markers, square), the matrix and the pixel clock, and the ticks faces.
 * The widget shows the first page the cover has. The other pages of the cover show the digital clock here.
 */
object CoverFaces {
    private const val PI_F = 3.1415927f
    private val RED = 0xFFFF1010.toInt()
    private val ORANGE = 0xFFFF9F0A.toInt()
    private val GREEN = 0xFF00FF66.toInt()

    /** The page the widgets show: the first page the screen cover has. */
    fun page(ctx: Context): String = StandByPrefs.pages(ctx).firstOrNull() ?: "blank"

    fun draw(ctx: Context, page: String, w: Int, h: Int, now: Long): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // the black background is the default; off, the picture has no background and the wallpaper shows through
        if (StandByPrefs.widgetBlack(ctx)) c.drawColor(Color.BLACK)
        val ink = StandByPrefs.ink(ctx).toArgb()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val h24 = StandByPrefs.h24(ctx)
        when (page) {
            "blank" -> Unit
            "analog" -> analog(c, w, h, cal, ink, "")
            "analog_clean" -> analog(c, w, h, cal, ink, "clean")
            "analog_nomark" -> analog(c, w, h, cal, ink, "nomark")
            "analog_square" -> analog(c, w, h, cal, ink, "square")
            "matrix" -> matrix(ctx, c, w, h, cal, h24)
            "pixel" -> pixel(c, w, h, cal, h24, ink, now)
            "tick_analog" -> tickAnalog(ctx, c, w, h, cal, ink)
            "tick_digital" -> tickDigital(ctx, c, w, h, cal, ink, h24)
            else -> digital(ctx, c, w, h, cal, ink, h24)
        }
        return bmp
    }

    // ------------------------------------------------------------------ helpers

    private fun hourNumber(cal: Calendar, h24: Boolean): Int =
        if (h24) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }

    private fun hourText(cal: Calendar, h24: Boolean): String {
        val hh = hourNumber(cal, h24)
        return if (h24) "%02d".format(Locale.US, hh) else hh.toString()
    }

    private fun minuteText(cal: Calendar): String = "%02d".format(Locale.US, cal.get(Calendar.MINUTE))

    private fun withAlpha(color: Int, a: Int) = Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))

    private fun stroke(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun text(color: Int, size: Float, tf: Typeface, align: Paint.Align = Paint.Align.CENTER) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; textSize = size; typeface = tf; textAlign = align
    }

    /** The cover's font by its id; the fonts the cover ships are used as they are, the others by the system family. */
    private fun typeface(ctx: Context, id: String): Typeface {
        FONT_RES[id]?.let { r ->
            runCatching { ResourcesCompat.getFont(ctx, r) }.getOrNull()?.let { tf ->
                return if (id == "interb") Typeface.create(tf, Typeface.BOLD) else tf
            }
        }
        return when (id) {
            "bold", "interb" -> Typeface.DEFAULT_BOLD
            "serif" -> Typeface.SERIF
            "mono" -> Typeface.MONOSPACE
            "rounded" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> Typeface.DEFAULT
        }
    }

    private val FONT_RES: Map<String, Int> = mapOf(
        "inter" to R.font.inter_regular, "interb" to R.font.inter_regular,
        "seg7" to R.font.sb_segments_7_classic_regular, "seg7b" to R.font.sb_segments_7_bold, "seg7r" to R.font.sb_segments_7_round_regular,
        "seg8" to R.font.sb_segments_8_regular, "seg9" to R.font.sb_segments_9_regular, "seg13" to R.font.sb_segments_13_regular,
        "seg13r" to R.font.sb_segments_13_round, "seg15" to R.font.sb_segments_15_cube, "seg15l" to R.font.sb_segments_15_line,
        "seg15r" to R.font.sb_segments_15_round, "seg17" to R.font.sb_segments_17_regular, "pixel" to R.font.sb_segments_23_pixel,
        "segc" to R.font.sb_segmented_classic, "kode" to R.font.sb_kode_mono, "bebas" to R.font.sb_bebas, "antonio" to R.font.sb_antonio,
        "gothic" to R.font.sb_league_gothic, "economica" to R.font.sb_economica, "barlow" to R.font.sb_barlow,
        "montserrat" to R.font.sb_montserrat, "rubik" to R.font.sb_rubik, "saira" to R.font.sb_saira, "lilita" to R.font.sb_lilita,
    )

    // ------------------------------------------------------------------ digital

    private fun digital(ctx: Context, c: Canvas, w: Int, h: Int, cal: Calendar, ink: Int, h24: Boolean) {
        val sec = StandByPrefs.seconds(ctx)
        val outline = StandByPrefs.outline(ctx)
        val hour = hourText(cal, h24); val mm = minuteText(cal)
        val ss = "%02d".format(Locale.US, cal.get(Calendar.SECOND))
        val parts = listOfNotNull(hour, mm, if (sec) ss else null)
        val portrait = h > w * 1.1f
        // an upright screen: the hours over the minutes (over the seconds); a wide one: all on one line
        val lines = if (portrait) parts else listOf(parts.joinToString(":"))
        val p = text(ink, 100f, typeface(ctx, StandByPrefs.font(ctx)))
        p.textSize = 100f
        val widest = max(lines.maxOf { p.measureText(it) }, 1f)
        val size = min(w * .9f / widest * 100f, if (portrait) h * .85f / lines.size / 1.05f else h * .7f)
        p.textSize = size
        if (outline) { p.style = Paint.Style.STROKE; p.strokeWidth = size * .045f }
        val lineH = size * 1.05f
        var y = h / 2f - (lines.size - 1) * lineH / 2f - (p.descent() + p.ascent()) / 2f
        lines.forEach { l -> c.drawText(l, w / 2f, y, p); y += lineH }
    }

    // ------------------------------------------------------------------ analog

    /** [variant] "" is the plain analog clock (sixty ticks); "clean", "nomark" and "square" are the other dials. */
    private fun analog(c: Canvas, w: Int, h: Int, cal: Calendar, ink: Int, variant: String) {
        val mn = min(w, h).toFloat()
        val cx = w / 2f; val cy = h / 2f
        val r = mn / 2f - mn * .02f
        val s = cal.get(Calendar.SECOND)
        val m = cal.get(Calendar.MINUTE) + s / 60f
        val hr = (cal.get(Calendar.HOUR) % 12) + m / 60f
        if (variant.isEmpty()) {
            for (i in 0 until 60) {
                val big = i % 5 == 0
                val a = i * PI_F / 30f
                val dx = sin(a); val dy = -cos(a)
                val from = r - (if (big) mn * .09f else mn * .04f)
                c.drawLine(cx + dx * from, cy + dy * from, cx + dx * r, cy + dy * r,
                    stroke(withAlpha(ink, if (big) 242 else 89), if (big) mn * .02f else mn * .006f))
            }
        } else {
            if (variant == "square") c.drawRoundRect(cx - r, cy - r, cx + r, cy + r, r * .28f, r * .28f, stroke(withAlpha(ink, 230), mn * .017f))
            else if (variant != "nomark") c.drawCircle(cx, cy, r, stroke(withAlpha(ink, 64), mn * .007f))
            if (variant != "nomark") for (i in 0 until 12) {
                val ang = i * PI_F / 6f
                val dx = sin(ang); val dy = -cos(ang)
                // on the square dial the dots sit on the square's own edge, on the others on the circle
                val k = if (variant == "square") r / max(abs(dx), abs(dy)) * .86f else r - mn * .027f
                c.drawCircle(cx + dx * k, cy + dy * k, if (i % 3 == 0) mn * .023f else mn * .013f,
                    fill(withAlpha(ink, if (i % 3 == 0) 255 else 128)))
            }
        }
        val plain = variant.isEmpty()
        fun hand(frac: Float, len: Float, width: Float, col: Int) {
            val a = frac * 2f * PI_F
            c.drawLine(cx, cy, cx + sin(a) * len, cy - cos(a) * len, stroke(col, width))
        }
        if (plain) {
            hand(hr / 12f, r * .5f, mn * .04f, ink); hand(m / 60f, r * .78f, mn * .027f, ink); hand(s / 60f, r * .86f, mn * .01f, ORANGE)
            c.drawCircle(cx, cy, mn * .027f, fill(ORANGE))
        } else {
            hand(hr / 12f, r * .48f, mn * .03f, ink); hand(m / 60f, r * .72f, mn * .02f, ink); hand(s / 60f, r * .8f, mn * .008f, ORANGE)
            c.drawCircle(cx, cy, mn * .02f, fill(ORANGE))
        }
    }

    // ------------------------------------------------------------------ ticks

    /** Sixty ticks along the edge of the picture, each pointing at the middle; [dim] is the one of the current second (drawn faint). */
    private fun ticks(c: Canvas, w: Int, h: Int, ink: Int, dim: Int, margin: Float, long: Boolean) {
        val mn = min(w, h).toFloat()
        val cx = w / 2f; val cy = h / 2f
        val a = w / 2f - margin; val b = h / 2f - margin
        val len = mn * .075f
        for (i in 0 until 60) {
            val t = i * 2f * PI_F / 60f
            val s = sin(t); val k = cos(t)
            val f = 1f / max(abs(s), abs(k))
            val px = cx + a * s * f; val py = cy - b * k * f
            val dx = cx - px; val dy = cy - py
            val d = sqrt(dx * dx + dy * dy)
            val l = if (long && i % 5 == 0) len * 1.8f else len
            c.drawLine(px, py, px + dx / d * l, py + dy / d * l, stroke(if (i == dim) withAlpha(ink, 64) else ink, mn * .0095f))
        }
    }

    private fun weekday(cal: Calendar): String =
        cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())?.uppercase(Locale.getDefault())?.take(3) ?: ""

    private fun tickDigital(ctx: Context, c: Canvas, w: Int, h: Int, cal: Calendar, ink: Int, h24: Boolean) {
        val mn = min(w, h).toFloat()
        val tf = typeface(ctx, "bebas")
        ticks(c, w, h, ink, cal.get(Calendar.SECOND), mn * .035f, false)
        val time = "%s:%s".format(hourText(cal, h24).padStart(2, '0'), minuteText(cal))
        val p = text(ink, h * .78f, tf)
        val room = w * .72f
        val wide = p.measureText(time)
        if (wide > room) p.textSize = h * .78f * room / wide
        c.drawText(time, w / 2f, h / 2f + p.textSize * .36f, p)
        // the day at the top: the weekday red, the number white
        val day = cal.get(Calendar.DAY_OF_MONTH).toString()
        val dp = text(RED, h * .11f, tf, Paint.Align.LEFT)
        val wd = weekday(cal)
        val dw = dp.measureText(wd); val nw = dp.measureText(" $day")
        val x0 = w / 2f - (dw + nw) / 2f; val y0 = h * .2f
        c.drawText(wd, x0, y0, dp)
        dp.color = ink
        c.drawText(" $day", x0 + dw, y0, dp)
    }

    private fun tickAnalog(ctx: Context, c: Canvas, w: Int, h: Int, cal: Calendar, ink: Int) {
        val mn = min(w, h).toFloat()
        val cx = w / 2f; val cy = h / 2f
        val tf = typeface(ctx, "bebas")
        ticks(c, w, h, withAlpha(ink, 230), -1, mn * .035f, true)
        // 12, 3, 6 and 9 inside the ticks
        val p = text(ink, mn * .2f, tf)
        val ry = h / 2f - mn * .2f; val rx = w / 2f - mn * .22f
        c.drawText("12", cx, cy - ry + p.textSize * .85f, p)
        c.drawText("6", cx, cy + ry, p)
        c.drawText("9", cx - rx, cy + p.textSize * .36f, p)
        c.drawText("3", cx + rx, cy + p.textSize * .36f, p)
        // the date beside the 3
        val day = cal.get(Calendar.DAY_OF_MONTH).toString()
        val dp = text(ink, mn * .11f, tf, Paint.Align.RIGHT)
        val nw = dp.measureText(" $day")
        val xr = cx + rx - p.textSize * .55f
        val by = cy + dp.textSize * .36f
        c.drawText(" $day", xr, by, dp)
        dp.color = RED
        c.drawText(weekday(cal), xr - nw, by, dp)
        // the hands
        val s = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        val m = cal.get(Calendar.MINUTE) + s / 60f
        val hr = (cal.get(Calendar.HOUR) % 12) + m / 60f
        val r = mn / 2f
        fun hand(frac: Float, from: Float, to: Float, width: Float, col: Int) {
            val a = frac * 2f * PI_F
            val dx = sin(a); val dy = -cos(a)
            c.drawLine(cx + dx * from, cy + dy * from, cx + dx * to, cy + dy * to, stroke(col, width))
        }
        hand(hr / 12f, 0f, r * .5f, mn * .028f, ink)
        hand(m / 60f, 0f, r * .8f, mn * .028f, ink)
        hand(s / 60f, -r * .08f, r * .86f, mn * .008f, RED)
        c.drawCircle(cx, cy, mn * .02f, stroke(RED, mn * .008f))
    }

    // ------------------------------------------------------------------ matrix

    private const val GLYPHS = "01アイウエオカキクケコサシスセソタチツテト"

    /** The rain of the matrix face, drawn still (the cover moves it; a widget shows one frame), with the time over it. */
    private fun matrix(ctx: Context, c: Canvas, w: Int, h: Int, cal: Calendar, h24: Boolean) {
        val cols = 20
        val line = h / 14f
        val rain = text(GREEN, line * .9f, Typeface.MONOSPACE)
        for (i in 0 until cols) {
            val head = ((i * 37) % 100) / 100f
            val x = w * (i + .5f) / cols
            for (k in 0 until 10) {
                val y = head * h * 1.2f - k * line
                if (y < 0f || y > h) continue
                rain.color = Color.argb(((1f - k / 10f) * 200).toInt(), 0, 255, 102)
                c.drawText(GLYPHS[(i * 7 + k * 3) % GLYPHS.length].toString(), x, y, rain)
            }
        }
        val lines = if (h > w * 1.1f) listOf(hourText(cal, h24), minuteText(cal)) else listOf(hourText(cal, h24) + ":" + minuteText(cal))
        val p = text(GREEN, 100f, typeface(ctx, "kode"))
        p.setShadowLayer(h * .08f, 0f, 0f, GREEN)
        val widest = max(lines.maxOf { p.measureText(it) }, 1f)
        val size = min(w * .8f / widest * 100f, if (lines.size > 1) h * .42f / lines.size else h * .4f)
        p.textSize = size
        val lineH = size * 1.05f
        val top = h / 2f - (lines.size - 1) * lineH / 2f - (p.descent() + p.ascent()) / 2f
        // a dark plate behind the digits, so the rain does not cut through them
        val pad = size * .2f
        val tw = widest * size / 100f
        c.drawRect(w / 2f - tw / 2f - pad, top + p.ascent() - pad, w / 2f + tw / 2f + pad, top + (lines.size - 1) * lineH + p.descent() + pad, fill(withAlpha(Color.BLACK, 140)))
        var y = top
        lines.forEach { l -> c.drawText(l, w / 2f, y, p); y += lineH }
    }

    // ------------------------------------------------------------------ pixel art

    private val PIXEL_DIGITS = listOf(
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111",
    )

    private fun pixel(c: Canvas, w: Int, h: Int, cal: Calendar, h24: Boolean, ink: Int, now: Long) {
        val digits = "%s%s".format(hourText(cal, h24).padStart(2, '0'), minuteText(cal)).map { it - '0' }
        val colonOn = (now / 1000L) % 2 == 0L
        // wide: HH : MM on one line (17 x 5 cells); upright: HH over MM (7 x 11 cells)
        val portrait = h > w * 1.1f
        val colsTotal = if (portrait) 7 else 17
        val rows = if (portrait) 11 else 5
        val cell = min(w / colsTotal.toFloat(), h / (rows * (if (portrait) 1.15f else 1.6f)))
        val x0 = (w - cell * colsTotal) / 2f; val y0 = (h - cell * rows) / 2f
        fun px(col: Int, row: Int, on: Boolean) = c.drawRoundRect(
            x0 + col * cell + cell * .06f, y0 + row * cell + cell * .06f,
            x0 + col * cell + cell * .94f, y0 + row * cell + cell * .94f,
            cell * .12f, cell * .12f, fill(if (on) ink else withAlpha(ink, 18)))
        fun digit(d: Int, col: Int, row: Int) {
            val g = PIXEL_DIGITS[d]
            for (r in 0 until 5) for (cc in 0 until 3) px(col + cc, row + r, g[r * 3 + cc] == '1')
        }
        if (portrait) {
            digit(digits[0], 0, 0); digit(digits[1], 4, 0)
            digit(digits[2], 0, 6); digit(digits[3], 4, 6)
            px(3, 5, colonOn)
        } else {
            digit(digits[0], 0, 0); digit(digits[1], 4, 0)
            px(8, 1, colonOn); px(8, 3, colonOn)
            digit(digits[2], 10, 0); digit(digits[3], 14, 0)
        }
    }
}
