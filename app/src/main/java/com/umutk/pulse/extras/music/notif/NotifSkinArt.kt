package com.umutk.pulse.extras.music.notif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kotlin.math.cos
import kotlin.math.sin

/**
 * The pictures of the cassette, turntable and iPod notification cards, drawn with android.graphics (a notification cannot hold Compose).
 * [art] is the player itself with the song on it; the reels and the record turn by [angle] each time the card is refreshed. [button] is one key in the look's own style.
 */
object NotifSkinArt {
    private const val AW = 700; private const val AH = 240

    private fun k(col: androidx.compose.ui.graphics.Color): Int = col.toArgb()
    private fun p(c: Int, style: Paint.Style = Paint.Style.FILL, w: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; this.style = style; strokeWidth = w }
    private fun text(c: Int, size: Float, face: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c; textSize = size; typeface = face }
    private fun fit(pt: Paint, s: String, w: Float): String { var t = s; while (t.length > 1 && pt.measureText(t) > w) t = t.dropLast(2); return if (t.length < s.length) "$t…" else t }

    fun art(kind: String, variant: String, title: String, artist: String, angle: Float, progress: Float): Bitmap {
        val b = Bitmap.createBitmap(AW, AH, Bitmap.Config.ARGB_8888); val c = Canvas(b)
        when (kind) { "TURNTABLE" -> turntable(c, variant, title, artist, angle, progress); "IPOD" -> ipod(c, variant, title, artist, progress); else -> cassette(c, variant, title, artist, angle, progress) }
        return b
    }

    private fun cassette(c: Canvas, variant: String, title: String, artist: String, angle: Float, progress: Float) {
        val th = com.umutk.pulse.extras.music.cassette.CASSETTE_THEMES.firstOrNull { it.id == variant } ?: com.umutk.pulse.extras.music.cassette.CASSETTE_THEMES[0]
        val paper = k(th.paper); val ink = k(th.ink); val main = k(th.main)
        val w = AW.toFloat(); val h = AH.toFloat()
        c.drawRoundRect(RectF(0f, 0f, w, h), 18f, 18f, p(paper))
        c.drawRoundRect(RectF(1f, 1f, w - 1f, h - 1f), 18f, 18f, p(Color.argb(200, Color.red(ink), Color.green(ink), Color.blue(ink)), Paint.Style.STROKE, 3f))
        // the label with its colour band and ruled lines
        c.drawRoundRect(RectF(14f, 12f, w - 14f, h * .46f), 8f, 8f, p(Color.rgb(251, 247, 232)))
        c.drawRect(14f, 12f, w - 14f, 12f + h * .08f, p(main)); c.drawRect(14f, 12f + h * .08f, w - 14f, 12f + h * .10f, p(ink))
        val tp = text(ink, 34f, Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)); c.drawText(fit(tp, title, w - 80f), 34f, h * .30f, tp)
        val ap = text(Color.argb(190, Color.red(ink), Color.green(ink), Color.blue(ink)), 20f, Typeface.MONOSPACE); c.drawText(fit(ap, artist.uppercase(), w - 80f), 34f, h * .40f, ap)
        // the window and the two reels, the tape packs growing and shrinking with the song
        val win = RectF(w * .17f, h * .50f, w * .83f, h * .86f)
        c.drawRoundRect(win, 40f, 40f, p(Color.rgb(48, 46, 34)))
        val wh = win.height(); val rl = win.left + win.width() * .26f; val rr = win.left + win.width() * .74f; val cy = win.centerY()
        val full = wh * .47f; val empty = wh * .24f
        val pack = p(Color.rgb(110, 70, 40))
        c.drawCircle(rl, cy, empty + (full - empty) * (1f - progress), pack); c.drawCircle(rr, cy, empty + (full - empty) * progress, pack)
        for (x in listOf(rl, rr)) {
            c.drawCircle(x, cy, wh * .27f, p(Color.WHITE)); c.drawCircle(x, cy, wh * .20f, p(ink))
            c.save(); c.rotate(angle, x, cy)
            for (i in 0 until 6) { c.save(); c.rotate(i * 60f, x, cy); c.drawRect(x - 3f, cy - wh * .20f, x + 3f, cy - wh * .10f, p(Color.WHITE)); c.restore() }
            c.restore(); c.drawCircle(x, cy, wh * .07f, p(Color.WHITE))
        }
        for (sx in listOf(14f, w - 14f)) for (sy in listOf(14f, h - 14f)) { c.drawCircle(sx, sy, 7f, p(Color.rgb(190, 188, 180))); c.drawLine(sx - 4f, sy, sx + 4f, sy, p(Color.DKGRAY, Paint.Style.STROKE, 2f)) }
    }

    private fun turntable(c: Canvas, variant: String, title: String, artist: String, angle: Float, progress: Float) {
        val th = com.umutk.pulse.extras.music.cassette.TURNTABLE_THEMES.firstOrNull { it.id == variant } ?: com.umutk.pulse.extras.music.cassette.TURNTABLE_THEMES[0]
        val w = AW.toFloat(); val h = AH.toFloat(); val base = k(th.base); val dark = k(th.baseDark)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h, base, dark, Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, w, h), 18f, 18f, bg)
        val light = if (th.base.luminance() > .5f) Color.rgb(27, 27, 27) else Color.WHITE
        // the record, on the left, with grooves and the turning label
        val cx = h * .52f; val cy = h * .5f; val r = h * .44f
        c.drawCircle(cx, cy, r * 1.07f, p(k(th.metal)))
        c.drawCircle(cx, cy, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, r, Color.rgb(28, 28, 30), Color.rgb(7, 7, 8), Shader.TileMode.CLAMP) })
        for (i in 1..9) c.drawCircle(cx, cy, r * (.40f + i * .06f), p(Color.argb(24, 255, 255, 255), Paint.Style.STROKE, 1f))
        c.save(); c.rotate(angle, cx, cy)
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), -22f, 44f, true, p(Color.argb(40, 255, 255, 255)))
        c.drawArc(RectF(cx - r, cy - r, cx + r, cy + r), 158f, 44f, true, p(Color.argb(40, 255, 255, 255)))
        c.drawCircle(cx, cy, r * .36f, p(k(th.label))); c.drawLine(cx, cy - r * .30f, cx, cy - r * .12f, p(Color.argb(150, 255, 255, 255), Paint.Style.STROKE, 3f))
        c.restore(); c.drawCircle(cx, cy, r * .05f, p(Color.WHITE))
        // the arm moves in with the song
        val pv = floatArrayOf(cx + r * 1.55f, h * .14f); val a = Math.toRadians(100.0 + (8f + 22f * progress) * 1.9)
        val hx = pv[0] + (cos(a) * h * .70f).toFloat(); val hy = pv[1] + (sin(a) * h * .70f).toFloat()
        c.drawLine(pv[0], pv[1], hx, hy, p(k(th.metal), Paint.Style.STROKE, 7f).apply { strokeCap = Paint.Cap.ROUND })
        c.drawCircle(pv[0], pv[1], 15f, p(k(th.metal))); c.drawCircle(pv[0], pv[1], 7f, p(dark)); c.drawCircle(hx, hy, 9f, p(Color.rgb(42, 42, 44)))
        // the song, right of the record
        val tx = cx + r * 1.55f + 46f
        val tp = text(light, 34f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); c.drawText(fit(tp, title, w - tx - 20f), tx, h * .45f, tp)
        val ap = text(Color.argb(190, Color.red(light), Color.green(light), Color.blue(light)), 21f, Typeface.MONOSPACE); c.drawText(fit(ap, artist.uppercase(), w - tx - 20f), tx, h * .62f, ap)
    }

    private fun ipod(c: Canvas, variant: String, title: String, artist: String, progress: Float) {
        val th = com.umutk.pulse.extras.music.cassette.IPOD_THEMES.firstOrNull { it.id == variant } ?: com.umutk.pulse.extras.music.cassette.IPOD_THEMES[0]
        val w = AW.toFloat(); val h = AH.toFloat()
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, 0f, 0f, h, k(th.body), Color.argb(255, (Color.red(k(th.body)) * .86f).toInt(), (Color.green(k(th.body)) * .86f).toInt(), (Color.blue(k(th.body)) * .86f).toInt()), Shader.TileMode.CLAMP) }
        c.drawRoundRect(RectF(0f, 0f, w, h), 30f, 30f, body)
        c.drawRoundRect(RectF(1.5f, 1.5f, w - 1.5f, h - 1.5f), 30f, 30f, p(Color.argb(130, 255, 255, 255), Paint.Style.STROKE, 3f))
        // the screen: dark bezel, a green-grey LCD with its title bar, the song and a progress bar
        val sc = RectF(w * .05f, h * .12f, w * .70f, h * .88f)
        c.drawRoundRect(sc, 14f, 14f, p(Color.rgb(26, 26, 28)))
        val lcd = RectF(sc.left + 8f, sc.top + 8f, sc.right - 8f, sc.bottom - 8f)
        c.drawRoundRect(lcd, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, lcd.top, 0f, lcd.bottom, Color.rgb(241, 246, 238), Color.rgb(217, 227, 212), Shader.TileMode.CLAMP) })
        c.drawRect(lcd.left, lcd.top, lcd.right, lcd.top + 34f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, lcd.top, 0f, lcd.top + 34f, Color.rgb(227, 233, 224), Color.rgb(169, 184, 165), Shader.TileMode.CLAMP) })
        val hp = text(Color.rgb(27, 27, 27), 22f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); c.drawText("iPod", lcd.left + 12f, lcd.top + 25f, hp)
        val tp = text(Color.rgb(27, 27, 27), 28f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)); c.drawText(fit(tp, title, lcd.width() - 24f), lcd.left + 12f, lcd.top + 76f, tp)
        val ap = text(Color.rgb(58, 58, 58), 22f, Typeface.SANS_SERIF); c.drawText(fit(ap, artist, lcd.width() - 24f), lcd.left + 12f, lcd.top + 106f, ap)
        val bar = RectF(lcd.left + 12f, lcd.bottom - 30f, lcd.right - 12f, lcd.bottom - 14f)
        c.drawRect(bar, p(Color.rgb(27, 27, 27), Paint.Style.STROKE, 2f))
        c.drawRect(bar.left + 2f, bar.top + 2f, bar.left + 2f + (bar.width() - 4f) * progress.coerceIn(0f, 1f), bar.bottom - 2f, p(Color.rgb(46, 104, 196)))
        // the click wheel, round, at the right
        val wx = w * .86f; val wy = h * .5f; val wr = h * .42f
        c.drawCircle(wx, wy, wr, p(k(th.wheel))); c.drawCircle(wx, wy, wr, p(Color.argb(60, 0, 0, 0), Paint.Style.STROKE, 2f))
        c.drawCircle(wx, wy, wr * .40f, p(k(th.body))); c.drawCircle(wx, wy, wr * .40f, p(Color.argb(60, 0, 0, 0), Paint.Style.STROKE, 2f))
    }

    /** One key of the card in the look's style: "prev", "play", "pause" or "next". */
    fun button(kind: String, variant: String, which: String, size: Int = 96): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); val c = Canvas(b); val s = size.toFloat()
        val face: Int; val glyph: Int; val round: Boolean
        when (kind) {
            "TURNTABLE" -> { face = Color.argb(60, 128, 128, 128); glyph = Color.WHITE; round = true }
            "IPOD" -> { val th = com.umutk.pulse.extras.music.cassette.IPOD_THEMES.firstOrNull { it.id == variant } ?: com.umutk.pulse.extras.music.cassette.IPOD_THEMES[0]; face = k(th.wheel); glyph = k(th.wheelInk); round = true }
            else -> { val th = com.umutk.pulse.extras.music.cassette.CASSETTE_THEMES.firstOrNull { it.id == variant } ?: com.umutk.pulse.extras.music.cassette.CASSETTE_THEMES[0]; face = if (which == "pause") Color.rgb(201, 48, 44) else k(th.key); glyph = if (which == "pause") Color.WHITE else k(th.main); round = false }
        }
        val rect = RectF(s * .06f, s * .06f, s * .94f, s * .94f)
        if (round) c.drawCircle(s / 2, s / 2, s * .44f, p(face)) else c.drawRoundRect(rect, s * .16f, s * .16f, p(face))
        val g = p(glyph); val m = s / 2
        fun tri(x: Float, dir: Int) { val pa = Path(); pa.moveTo(x - dir * s * .11f, m - s * .17f); pa.lineTo(x + dir * s * .11f, m); pa.lineTo(x - dir * s * .11f, m + s * .17f); pa.close(); c.drawPath(pa, g) }
        when (which) {
            "play" -> tri(m, 1)
            "pause" -> { c.drawRect(m - s * .15f, m - s * .17f, m - s * .04f, m + s * .17f, g); c.drawRect(m + s * .04f, m - s * .17f, m + s * .15f, m + s * .17f, g) }
            "prev" -> { tri(m - s * .02f, -1); c.drawRect(m - s * .20f, m - s * .17f, m - s * .15f, m + s * .17f, g) }
            else -> { tri(m + s * .02f, 1); c.drawRect(m + s * .15f, m - s * .17f, m + s * .20f, m + s * .17f, g) }
        }
        return b
    }
}
