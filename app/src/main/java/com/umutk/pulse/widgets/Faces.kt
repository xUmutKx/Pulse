package com.umutk.pulse.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Calendar
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * The faces drawn into the face widgets: eleven Windows 7 gadget faces (light and dark versions among them),
 * iOS-style tiles (clock, day, calendar, battery ring, sun and time) and a light frosted Windows 11 clock.
 * Each face is a [Look]; [draw] renders the current time into a bitmap.
 */
object Faces {
    /** One face: colours, numerals and hands. Transparent backgrounds use [Color.TRANSPARENT]. */
    data class Look(
        val face: Int, val ring: Int, val ticks: Int, val num: Int, val hour: Int, val min: Int, val sec: Int,
        val roman: Boolean = false, val dots: Boolean = false, val tile: Boolean = false, val day: Boolean = false,
        val cal: Boolean = false, val battery: Boolean = false, val sun: Boolean = false, val frost: Boolean = false,
    )

    // iOS blue; declared before [looks], which uses it
    private val BLUE = Color.rgb(100, 181, 246)

    /**
     * Look 1..11 are the Windows 7 gadget faces (open and closed versions among them); 12 to 16 are iOS tiles;
     * 17 is the Windows 11 clock on a light frosted blue panel ([frost], see res/drawable/widget_win11_frost.xml).
     */
    val looks: Map<Int, Look> = mapOf(
        1 to Look(Color.WHITE, Color.rgb(26, 26, 26), Color.rgb(26, 26, 26), Color.rgb(26, 26, 26), Color.rgb(26, 26, 26), Color.rgb(26, 26, 26), Color.rgb(200, 16, 46)),
        2 to Look(Color.rgb(30, 30, 30), Color.rgb(68, 68, 68), Color.rgb(221, 221, 221), Color.rgb(238, 238, 238), Color.rgb(240, 240, 240), Color.rgb(240, 240, 240), Color.rgb(255, 106, 0)),
        3 to Look(Color.rgb(244, 249, 255), Color.rgb(127, 178, 229), Color.rgb(63, 129, 198), Color.rgb(27, 79, 138), Color.rgb(27, 79, 138), Color.rgb(27, 79, 138), Color.rgb(46, 134, 222)),
        4 to Look(Color.rgb(250, 250, 250), Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, roman = true),
        5 to Look(Color.WHITE, Color.TRANSPARENT, Color.BLACK, Color.TRANSPARENT, Color.rgb(34, 34, 34), Color.rgb(34, 34, 34), Color.rgb(34, 34, 34), dots = true),
        6 to Look(Color.WHITE, Color.rgb(153, 153, 153), Color.rgb(51, 51, 51), Color.rgb(51, 51, 51), Color.rgb(51, 51, 51), Color.rgb(51, 51, 51), Color.rgb(229, 57, 53)),
        7 to Look(Color.rgb(230, 233, 238), Color.rgb(154, 163, 174), Color.rgb(90, 98, 110), Color.rgb(34, 34, 34), Color.rgb(42, 42, 42), Color.rgb(42, 42, 42), Color.rgb(217, 83, 79)),
        8 to Look(Color.rgb(207, 230, 251), Color.rgb(63, 129, 198), Color.rgb(63, 129, 198), Color.rgb(14, 58, 102), Color.rgb(14, 58, 102), Color.rgb(14, 58, 102), Color.rgb(63, 129, 198)),
        9 to Look(Color.rgb(184, 190, 198), Color.TRANSPARENT, Color.rgb(31, 35, 40), Color.rgb(31, 35, 40), Color.rgb(31, 35, 40), Color.rgb(31, 35, 40), Color.rgb(217, 83, 79)),
        10 to Look(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE, Color.rgb(255, 138, 101)),
        11 to Look(Color.rgb(200, 162, 122), Color.rgb(93, 64, 55), Color.rgb(62, 39, 35), Color.rgb(62, 39, 35), Color.rgb(62, 39, 35), Color.rgb(62, 39, 35), Color.rgb(183, 28, 28)),
        12 to Look(Color.TRANSPARENT, Color.TRANSPARENT, BLUE, BLUE, BLUE, BLUE, Color.WHITE, tile = true),
        13 to Look(Color.rgb(28, 28, 30), Color.TRANSPARENT, BLUE, BLUE, BLUE, BLUE, Color.WHITE, tile = true, day = true),
        14 to Look(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, BLUE, BLUE, Color.WHITE, tile = true, cal = true),
        15 to Look(Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, BLUE, BLUE, Color.WHITE, tile = true, battery = true),
        16 to Look(Color.rgb(52, 66, 80), Color.TRANSPARENT, Color.TRANSPARENT, Color.TRANSPARENT, BLUE, BLUE, Color.WHITE, tile = true, sun = true),
        17 to Look(Color.TRANSPARENT, Color.TRANSPARENT, Color.rgb(18, 62, 120), Color.rgb(18, 62, 120), Color.rgb(18, 62, 120), Color.rgb(18, 62, 120), Color.rgb(0, 120, 215), frost = true),
    )

    /** True for the faces with hands, which the widget runs as a live AnalogClock from [draw]'s parts. */
    fun analog(look: Int) = looks[look]?.let { !it.day && !it.cal && !it.battery && !it.sun } == true

    /** Renders the look at the current time into a square bitmap of [size] pixels. [battery] is the charge in percent (battery look only); a non-zero [accent] replaces the iOS blue. [part] 0 = everything; 1 = the dial without hands; 2, 3, 4 = the hour, minute or second hand alone pointing up (with the centre cap on 4), for AnalogClock. */
    fun draw(look: Int, size: Int, battery: Int = 0, accent: Int = 0, part: Int = 0): Bitmap {
        val l = (looks[look] ?: looks.getValue(1)).let {
            if (accent != 0 && it.frost) it.copy(ticks = accent, num = accent, hour = accent, min = accent, sec = Color.WHITE) else if (accent == 0 || !it.tile) it else Skins.readable(accent).let { a ->
                it.copy(ticks = if (it.ticks == BLUE) a else it.ticks, num = if (it.num == BLUE) a else it.num, hour = a, min = a)
            }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cx = size / 2f; val cy = size / 2f
        val r = size * 0.46f
        val now = Calendar.getInstance()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        if (part >= 2) {
            val up = -90.0
            when (part) {
                2 -> hand(c, p, cx, cy, up, r * .50f, size * .05f, l.hour)
                3 -> hand(c, p, cx, cy, up, r * .74f, size * .034f, l.min)
                else -> { hand(c, p, cx, cy, up, r * .82f, size * .012f, l.sec); p.color = l.sec; p.style = Paint.Style.FILL; c.drawCircle(cx, cy, size * .03f, p) }
            }
            return bmp
        }

        if (l.tile) {
            // iOS tile: a rounded dark square; the clock, day, calendar, battery or sun sits in it
            p.color = Color.rgb(28, 28, 30)
            c.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * .22f, size * .22f, p)
            when {
                l.day -> { drawDay(c, l, now, size); return bmp }
                l.cal -> { drawCalendar(c, l, now, size); return bmp }
                l.battery -> { drawBattery(c, l, battery, size); return bmp }
                l.sun -> { drawSunTime(c, l, now, size); return bmp }
            }
        }

        if (l.face != Color.TRANSPARENT) { p.color = l.face; p.style = Paint.Style.FILL; c.drawCircle(cx, cy, r, p) }
        if (l.ring != Color.TRANSPARENT) { p.color = l.ring; p.style = Paint.Style.STROKE; p.strokeWidth = size * .028f; c.drawCircle(cx, cy, r, p) }

        // ticks: twelve marks, or small dots for the minimal face
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0 - 90)
            val major = i % 5 == 0
            val inner = if (major) r * .84f else r * .92f
            p.color = l.ticks; p.style = Paint.Style.STROKE
            p.strokeWidth = if (major) size * .024f else size * .010f
            if (l.dots) { if (major) { p.style = Paint.Style.FILL; c.drawCircle(cx + cos(a).toFloat() * r * .82f, cy + sin(a).toFloat() * r * .82f, size * .02f, p) } }
            else c.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner, cx + cos(a).toFloat() * r * .95f, cy + sin(a).toFloat() * r * .95f, p)
        }

        // numerals at the hours
        p.style = Paint.Style.FILL; p.color = l.num; p.textAlign = Paint.Align.CENTER
        p.textSize = size * .13f; p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        if (!l.dots && l.num != Color.TRANSPARENT) for (h in 1..12) {
            val a = Math.toRadians(h * 30.0 - 90)
            val x = cx + cos(a).toFloat() * r * .68f
            val y = cy + sin(a).toFloat() * r * .68f + p.textSize * .35f
            c.drawText(if (l.roman) ROMAN[h - 1] else h.toString(), x, y, p)
        }

        if (part == 1) return bmp
        // hands: hour, minute, second
        val hr = (now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f) * 30.0 - 90
        val mn = (now.get(Calendar.MINUTE) + now.get(Calendar.SECOND) / 60f) * 6.0 - 90
        val sc = now.get(Calendar.SECOND) * 6.0 - 90
        hand(c, p, cx, cy, hr, r * .50f, size * .05f, l.hour)
        hand(c, p, cx, cy, mn, r * .74f, size * .034f, l.min)
        hand(c, p, cx, cy, sc, r * .82f, size * .012f, l.sec)
        p.color = l.sec; p.style = Paint.Style.FILL; c.drawCircle(cx, cy, size * .03f, p)
        return bmp
    }

    /** The day tile: weekday in red, the date number in the iOS blue. */
    private fun drawDay(c: Canvas, l: Look, now: Calendar, size: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        val wd = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()).orEmpty().uppercase()
        p.color = Color.rgb(255, 69, 58); p.textSize = size * .14f; p.typeface = Typeface.DEFAULT_BOLD
        c.drawText(wd, size / 2f, size * .30f, p)
        p.color = l.hour; p.textSize = size * .48f
        c.drawText(now.get(Calendar.DAY_OF_MONTH).toString(), size / 2f, size * .78f, p)
    }

    /** The calendar tile: red month name, weekday letters, the month's days with today in a blue disc. */
    private fun drawCalendar(c: Canvas, l: Look, now: Calendar, size: Int) {
        val s = size.toFloat()
        val loc = Locale.getDefault()
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.LEFT }
        p.color = Color.rgb(255, 69, 58); p.textSize = s * .09f
        c.drawText(now.getDisplayName(Calendar.MONTH, Calendar.LONG, loc).orEmpty().uppercase(), s * .08f, s * .17f, p)

        val colW = s * .12f
        val x0 = s * .08f + colW / 2
        p.textAlign = Paint.Align.CENTER; p.textSize = s * .065f
        for (col in 0 until 7) { // Monday first
            val dow = (Calendar.MONDAY + col - 1) % 7 + 1
            val label = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, dow) }.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, loc).orEmpty().take(1).uppercase()
            p.color = Color.rgb(142, 142, 147)
            c.drawText(label, x0 + col * colW, s * .34f, p)
        }

        val firstDow = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK)
        val offset = (firstDow - Calendar.MONDAY + 7) % 7 // empty cells before day 1, Monday first
        val today = now.get(Calendar.DAY_OF_MONTH)
        val rowH = s * .085f
        for (d in 1..now.getActualMaximum(Calendar.DAY_OF_MONTH)) {
            val col = (offset + d - 1) % 7
            val x = x0 + col * colW
            val y = s * .47f + (offset + d - 1) / 7 * rowH
            if (d == today) { p.color = l.hour; p.style = Paint.Style.FILL; c.drawCircle(x, y - s * .022f, s * .042f, p) }
            p.color = if (d == today) l.sec else if (col >= 5) Color.rgb(110, 110, 118) else Color.rgb(235, 235, 245)
            c.drawText(d.toString(), x, y, p)
        }
    }

    /** The battery tile: a blue ring that fills with the charge, and the percentage inside. */
    private fun drawBattery(c: Canvas, l: Look, pct: Int, size: Int) {
        val s = size.toFloat()
        val level = pct.coerceIn(0, 100)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = s * .075f; strokeCap = Paint.Cap.ROUND }
        val ring = RectF(s * .2f, s * .2f, s * .8f, s * .8f)
        p.color = Color.rgb(58, 58, 62); c.drawArc(ring, 135f, 270f, false, p)
        if (level > 0) { p.color = l.hour; c.drawArc(ring, 135f, 270f * level / 100f, false, p) }
        p.style = Paint.Style.FILL; p.color = l.hour; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD; p.textSize = s * .17f
        c.drawText("$level%", s / 2f, s / 2f + s * .08f, p)
    }

    /** The sun tile: a sun over a horizon, the time under it, inside a slate disc. */
    private fun drawSunTime(c: Canvas, l: Look, now: Calendar, size: Int) {
        val s = size.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = l.face; p.style = Paint.Style.FILL; c.drawCircle(s / 2, s / 2, s * .42f, p)

        val sy = s * .40f; val sr = s * .075f
        p.color = l.hour
        c.drawArc(RectF(s / 2 - sr, sy - sr, s / 2 + sr, sy + sr), 180f, 180f, true, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = s * .02f; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(s * .36f, sy, s * .64f, sy, p)
        for (deg in listOf(-150.0, -90.0, -30.0)) {
            val a = Math.toRadians(deg)
            c.drawLine(s / 2 + cos(a).toFloat() * s * .12f, sy + sin(a).toFloat() * s * .12f,
                s / 2 + cos(a).toFloat() * s * .16f, sy + sin(a).toFloat() * s * .16f, p)
        }

        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD; p.textSize = s * .22f
        c.drawText("%02d:%02d".format(now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)), s / 2, s * .62f, p)
    }

    private fun hand(c: Canvas, p: Paint, cx: Float, cy: Float, deg: Double, len: Float, width: Float, color: Int) {
        val a = Math.toRadians(deg)
        p.color = color; p.style = Paint.Style.STROKE; p.strokeWidth = width; p.strokeCap = Paint.Cap.ROUND
        c.drawLine(cx, cy, cx + cos(a).toFloat() * len, cy + sin(a).toFloat() * len, p)
    }

    private val ROMAN = listOf("I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII")
}
