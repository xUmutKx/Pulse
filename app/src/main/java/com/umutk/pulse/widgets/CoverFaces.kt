package com.umutk.pulse.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The screen cover's clock faces drawn into a picture for the home-screen widgets: analog, digital and matrix. */
object CoverFaces {
    fun draw(ctx: Context, face: String, w: Int, h: Int, now: Long): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // the black background is the default; off, the picture has no background and the wallpaper shows through
        if (com.umutk.pulse.features.StandByPrefs.widgetBlack(ctx)) c.drawColor(Color.BLACK)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        when (face) {
            "digital" -> digital(ctx, c, w, h, now, cal)
            "matrix" -> matrix(c, w, h, cal)
            else -> analog(c, w, h, cal)
        }
        return bmp
    }

    private fun analog(c: Canvas, w: Int, h: Int, cal: Calendar) {
        val cx = w / 2f; val cy = h / 2f
        val r = min(w, h) * .44f
        val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        white.strokeWidth = r * .035f
        c.drawCircle(cx, cy, r, white)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val inner = if (i % 3 == 0) r * .8f else r * .9f
            c.drawLine(cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner, cx + cos(a).toFloat() * r * .97f, cy + sin(a).toFloat() * r * .97f, white)
        }
        val hourF = (cal.get(Calendar.HOUR) + cal.get(Calendar.MINUTE) / 60f)
        val minF = cal.get(Calendar.MINUTE) + cal.get(Calendar.SECOND) / 60f
        val secF = cal.get(Calendar.SECOND).toFloat()
        hand(c, cx, cy, hourF / 12f * 360f, r * .5f, r * .07f, white)
        hand(c, cx, cy, minF / 60f * 360f, r * .8f, r * .045f, white)
        val red = Paint(white).apply { color = Color.rgb(255, 64, 64); strokeWidth = r * .018f }
        hand(c, cx, cy, secF / 60f * 360f, r * .85f, r * .018f, red)
    }

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, width: Float, p: Paint) {
        val a = Math.toRadians(deg.toDouble() - 90)
        val q = Paint(p).apply { strokeWidth = width }
        c.drawLine(cx, cy, cx + cos(a).toFloat() * len, cy + sin(a).toFloat() * len, q)
    }

    private fun digital(ctx: Context, c: Canvas, w: Int, h: Int, now: Long, cal: Calendar) {
        val fmt = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
        val time = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = h * .42f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); textAlign = Paint.Align.CENTER }
        c.drawText(SimpleDateFormat(fmt, Locale.getDefault()).format(now), w / 2f, h * .56f, time)
        val date = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 255, 255, 255); textSize = h * .12f; textAlign = Paint.Align.CENTER }
        c.drawText(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now), w / 2f, h * .84f, date)
    }

    /** Falling green characters behind the time, the way the matrix face of the screen cover looks. */
    private fun matrix(c: Canvas, w: Int, h: Int, cal: Calendar) {
        val rnd = Random(cal.get(Calendar.DAY_OF_YEAR).toLong() * 1000 + cal.get(Calendar.HOUR_OF_DAY))
        val size = h * .09f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = Typeface.MONOSPACE }
        var x = 0f
        while (x < w) {
            val start = rnd.nextInt(h)
            var y = start.toFloat()
            var k = 0
            while (y < h + size) {
                p.color = Color.argb(if (k < 3) 255 else 90, 0, 255, 70)
                c.drawText(if (rnd.nextBoolean()) "1" else "0", x, y, p)
                y += size; k++
            }
            x += size * .9f
        }
        val time = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(120, 255, 150); textSize = h * .3f; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER; setShadowLayer(h * .05f, 0f, 0f, Color.rgb(0, 255, 70)) }
        c.drawText(SimpleDateFormat("HH:mm", Locale.getDefault()).format(cal.time), w / 2f, h * .56f, time)
    }
}
