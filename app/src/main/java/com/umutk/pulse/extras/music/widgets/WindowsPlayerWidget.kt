package com.umutk.pulse.extras.music.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.artistFor
import com.umutk.pulse.extras.music.shownArtist
import com.umutk.pulse.extras.music.trackArt
import com.umutk.pulse.widgets.WidgetStyle
import com.umutk.pulse.widgets.WidgetStyles

/**
 * A media player window as a home-screen widget, drawn the way each look draws a window: the Windows 95 "CD Player" with bevelled fields and buttons,
 * the Windows XP Luna window, the Windows 7 Aero media player (dark stage, glass buttons) and a flat card for Windows 10 / 11 / Material / AMOLED.
 * Everything is drawn in W x H units and fitted into the widget (never cropped); three transparent views over the buttons take the taps.
 */
class WindowsPlayerWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { loop.start(c); ids.forEach { m.updateAppWidget(it, build(c, it)) } }
    override fun onEnabled(c: Context) = loop.start(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) { m.updateAppWidget(id, build(c, id)) }
    override fun onReceive(c: Context, i: Intent) {
        val e = MusicPlayerEngine.ready(c)
        when (i.action) {
            A_PREV -> e.previous(); A_TOGGLE -> e.togglePlayPause(); A_NEXT -> e.next()
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.winplayer.PREV"
        const val A_TOGGLE = "com.umutk.pulse.winplayer.TOGGLE"
        const val A_NEXT = "com.umutk.pulse.winplayer.NEXT"
        const val W = 320
        const val H = 150
        private const val BTN_TOP = 0.72f
        private const val BTN_BOTTOM = 0.97f
        private val loop = LiveLoop(1000) { refresh(it) }
        private var lastShrink = 1f

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WindowsPlayerWidget::class.java)).forEach { m.updateAppWidget(it, build(c, it)) }
        }

        private fun pi(c: Context, a: String) =
            PendingIntent.getBroadcast(c, a.hashCode(), Intent(a).setComponent(ComponentName(c, WindowsPlayerWidget::class.java)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        class Fit(val scale: Float, val x: Int, val y: Int, val w: Int, val h: Int)
        fun fit(bw: Int, bh: Int): Fit {
            val s = minOf(bw / W.toFloat(), bh / H.toFloat())
            val w = (W * s).toInt(); val h = (H * s).toInt()
            return Fit(s, (bw - w) / 2, (bh - h) / 2, w, h)
        }

        private fun boxPx(c: Context, id: Int): Pair<Int, Int> {
            val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val portrait = c.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)).takeIf { it > 0 } ?: 250
            val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)).takeIf { it > 0 } ?: 110
            val pw = w * d; val ph = h * d
            val shrink = kotlin.math.sqrt(pw * ph / 900_000f).coerceAtLeast(1f)
            lastShrink = shrink
            return (pw / shrink).toInt().coerceAtLeast(1) to (ph / shrink).toInt().coerceAtLeast(1)
        }

        fun build(c: Context, id: Int): RemoteViews {
            val (bw, bh) = boxPx(c, id)
            val e = MusicPlayerEngine.ready(c)
            val st = e.state.value
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val frac = if (dur > 0) (e.player.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val cover = st.current?.let { runCatching { trackArt(c, it)?.asAndroidBitmap() }.getOrNull() }
            val title = st.current?.let { it.title ?: it.path.substringAfterLast('/') } ?: ""
            val bmp = render(WidgetStyles.of(c, id), title, st.current?.artistFor(c).orEmpty(), frac, st.isPlaying, e.player.currentPosition, dur, cover, bw, bh, WidgetOpts.bg(c) != "none", WidgetOpts.artPct(c))
            val f = fit(bw, bh)
            return RemoteViews(c.packageName, R.layout.widget_winplayer).apply {
                setImageViewBitmap(R.id.wp_img, bmp)
                val k = lastShrink
                setViewPadding(R.id.wp_overlay, (f.x * k).toInt(), ((f.y + BTN_TOP * f.h) * k).toInt(), ((bw - f.x - f.w) * k).toInt(), ((bh - f.y - BTN_BOTTOM * f.h) * k).toInt())
                setOnClickPendingIntent(R.id.wp_img, com.umutk.pulse.widgets.music.WidgetUi.launch(c, "com.umutk.pulse.ToolOpenActivity", 73, "route" to "music"))
                setOnClickPendingIntent(R.id.wp_prev, pi(c, A_PREV))
                setOnClickPendingIntent(R.id.wp_play, pi(c, A_TOGGLE))
                setOnClickPendingIntent(R.id.wp_next, pi(c, A_NEXT))
            }
        }

        private fun clock(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

        private fun grad(top: Int, bottom: Int, y0: Float, y1: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = LinearGradient(0f, y0, 0f, y1, top, bottom, Shader.TileMode.CLAMP) }

        /** Raised (true) or sunken Windows 95 edge around [r]. */
        private fun bevel(cv: Canvas, r: RectF, raised: Boolean) {
            val p = Paint().apply { strokeWidth = 1f }
            val hi = 0xFFFFFFFF.toInt(); val lo = 0xFF808080.toInt(); val dk = 0xFF000000.toInt()
            p.color = if (raised) hi else dk; cv.drawLine(r.left, r.top, r.right - 1, r.top, p); cv.drawLine(r.left, r.top, r.left, r.bottom - 1, p)
            p.color = if (raised) dk else hi; cv.drawLine(r.left, r.bottom - 1, r.right, r.bottom - 1, p); cv.drawLine(r.right - 1, r.top, r.right - 1, r.bottom, p)
            p.color = if (raised) lo else lo; cv.drawLine(r.left + 1, r.bottom - 2, r.right - 1, r.bottom - 2, p); cv.drawLine(r.right - 2, r.top + 1, r.right - 2, r.bottom - 1, p)
        }

        private fun coverInto(cv: Canvas, cover: Bitmap?, r: RectF, empty: Int, round: Float = 0f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            cv.save(); if (round > 0f) cv.clipPath(Path().apply { addRoundRect(r, round, round, Path.Direction.CW) })
            if (cover != null) {
                val side = minOf(cover.width, cover.height)
                cv.drawBitmap(cover, android.graphics.Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2), r, p)
            } else { p.color = empty; cv.drawRect(r, p) }
            cv.restore()
        }

        private fun text(cv: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, maxW: Float, bold: Boolean = false, center: Boolean = false) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; textSize = size; if (bold) typeface = Typeface.DEFAULT_BOLD }
            val t = TextUtils.ellipsize(s, tp, maxW, TextUtils.TruncateAt.END).toString()
            cv.drawText(t, if (center) x - tp.measureText(t) / 2 else x, y, tp)
        }

        // the three button glyphs: previous, play or pause, next
        private fun glyphs(cv: Canvas, cx: FloatArray, cy: Float, color: Int, playing: Boolean, scale: Float = 1f) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
            val u = 7f * scale
            cv.drawRect(cx[0] - u, cy - u, cx[0] - u + 2.2f * scale, cy + u, p)
            cv.drawPath(Path().apply { moveTo(cx[0] + u, cy - u); lineTo(cx[0] - u + 2.5f * scale, cy); lineTo(cx[0] + u, cy + u); close() }, p)
            if (playing) { cv.drawRect(cx[1] - u * .9f, cy - u, cx[1] - u * .2f, cy + u, p); cv.drawRect(cx[1] + u * .2f, cy - u, cx[1] + u * .9f, cy + u, p) }
            else cv.drawPath(Path().apply { moveTo(cx[1] - u * .7f, cy - u * 1.1f); lineTo(cx[1] + u * 1.1f, cy); lineTo(cx[1] - u * .7f, cy + u * 1.1f); close() }, p)
            cv.drawRect(cx[2] + u - 2.2f * scale, cy - u, cx[2] + u, cy + u, p)
            cv.drawPath(Path().apply { moveTo(cx[2] - u, cy - u); lineTo(cx[2] + u - 2.5f * scale, cy); lineTo(cx[2] - u, cy + u); close() }, p)
        }

        fun render(style: WidgetStyle, title: String, artist: String, frac: Float, playing: Boolean, posMs: Long, durMs: Long, cover: Bitmap?, bw: Int, bh: Int, card: Boolean = true, artPct: Int = 100): Bitmap {
            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val f = fit(bw, bh)
            cv.translate(f.x.toFloat(), f.y.toFloat()); cv.scale(f.scale, f.scale)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = floatArrayOf(W / 6f, W / 2f, W * 5 / 6f)
            val cy = 127f
            val time = clock(posMs) + " / " + if (durMs > 0) clock(durMs) else "--:--"
            when (style) {
                WidgetStyle.WIN95 -> {
                    val body = RectF(0f, 0f, W.toFloat(), H.toFloat())
                    p.color = 0xFFC0C0C0.toInt(); cv.drawRect(body, p); bevel(cv, body, true)
                    // title bar
                    val bar = RectF(3f, 3f, W - 3f, 21f)
                    p.shader = LinearGradient(bar.left, 0f, bar.right, 0f, 0xFF000080.toInt(), 0xFF1084D0.toInt(), Shader.TileMode.CLAMP); cv.drawRect(bar, p); p.shader = null
                    text(cv, "CD Player", 8f, 16f, 12f, 0xFFFFFFFF.toInt(), 200f, bold = true)
                    for (i in 0..2) { val b = RectF(W - 5f - (3 - i) * 17f + (if (i == 2) 2f else 0f), 5f, W - 5f - (2 - i) * 17f + (if (i == 2) 2f else 0f) - 2f, 19f); p.color = 0xFFC0C0C0.toInt(); cv.drawRect(b, p); bevel(cv, b, true) }
                    // cover and the two fields
                    val art = RectF(8f, 27f, 80f, 99f); coverInto(cv, cover, art, 0xFF404040.toInt()); bevel(cv, RectF(art.left - 1, art.top - 1, art.right + 1, art.bottom + 1), false)
                    text(cv, "Artist:", 90f, 41f, 12f, 0xFF000000.toInt(), 50f)
                    text(cv, "Track:", 90f, 69f, 12f, 0xFF000000.toInt(), 50f)
                    for ((i, s) in listOf(artist, title).withIndex()) {
                        val fr = RectF(136f, 28f + i * 28f, W - 8f, 46f + i * 28f); p.color = 0xFFFFFFFF.toInt(); cv.drawRect(fr, p); bevel(cv, fr, false)
                        text(cv, s, fr.left + 4f, fr.bottom - 5f, 12f, 0xFF000000.toInt(), fr.width() - 8f)
                    }
                    // progress as the blue blocks of a classic bar
                    val pr = RectF(90f, 86f, W - 8f, 98f); p.color = 0xFFC0C0C0.toInt(); cv.drawRect(pr, p); bevel(cv, pr, false)
                    p.color = 0xFF000080.toInt(); var x = pr.left + 2f; val lim = pr.left + 2f + (pr.width() - 4f) * frac
                    while (x + 6f <= lim) { cv.drawRect(x, pr.top + 2f, x + 6f, pr.bottom - 2f, p); x += 8f }
                    text(cv, time, 90f, 112f, 10f, 0xFF000000.toInt(), 120f)
                    for (i in 0..2) { val b = RectF(cx[i] - 38f, cy - 14f, cx[i] + 38f, cy + 14f); p.color = 0xFFC0C0C0.toInt(); cv.drawRect(b, p); bevel(cv, b, true) }
                    glyphs(cv, cx, cy, 0xFF000000.toInt(), playing, .85f)
                }
                WidgetStyle.XP -> {
                    val w = RectF(0f, 0f, W.toFloat(), H.toFloat())
                    p.color = 0xFF0831D9.toInt(); cv.drawRoundRect(w, 9f, 9f, p)
                    val inner = RectF(3f, 26f, W - 3f, H - 3f); p.color = 0xFFECE9D8.toInt(); cv.drawRect(inner, p)
                    val bar = RectF(0f, 0f, W.toFloat(), 27f)
                    cv.save(); cv.clipPath(Path().apply { addRoundRect(bar, 9f, 9f, Path.Direction.CW); addRect(0f, 14f, W.toFloat(), 27f, Path.Direction.CW) })
                    p.shader = LinearGradient(0f, 0f, 0f, 27f, intArrayOf(0xFF0A5FF2.toInt(), 0xFF3A93FF.toInt(), 0xFF0058EE.toInt()), floatArrayOf(0f, .3f, 1f), Shader.TileMode.CLAMP); cv.drawRect(bar, p); p.shader = null; cv.restore()
                    text(cv, "Windows Media Player", 10f, 18f, 12f, 0xFFFFFFFF.toInt(), 200f, bold = true)
                    for (i in 0..2) { val b = RectF(W - 7f - (3 - i) * 22f, 4f, W - 7f - (2 - i) * 22f - 2f, 22f); p.color = if (i == 2) 0xFFD9543C.toInt() else 0xFF2A58C8.toInt(); cv.drawRoundRect(b, 3f, 3f, p); p.color = 0xFFFFFFFF.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 1f; cv.drawRoundRect(b, 3f, 3f, p); p.style = Paint.Style.FILL }
                    val art = RectF(10f, 34f, 82f, 106f); coverInto(cv, cover, art, 0xFF1B3A7A.toInt(), 4f)
                    text(cv, title, 92f, 50f, 14f, 0xFF000000.toInt(), W - 100f, bold = true)
                    text(cv, artist, 92f, 68f, 12f, 0xFF404040.toInt(), W - 100f)
                    val pr = RectF(92f, 82f, W - 10f, 90f); p.color = 0xFFFFFFFF.toInt(); cv.drawRoundRect(pr, 4f, 4f, p); p.color = 0xFF7F9DB9.toInt(); p.style = Paint.Style.STROKE; cv.drawRoundRect(pr, 4f, 4f, p); p.style = Paint.Style.FILL
                    p.color = 0xFF3AB03A.toInt(); cv.drawRoundRect(RectF(pr.left + 1, pr.top + 1, pr.left + 1 + (pr.width() - 2) * frac, pr.bottom - 1), 3f, 3f, p)
                    text(cv, time, 92f, 103f, 10f, 0xFF404040.toInt(), 150f)
                    for (i in 0..2) { p.shader = LinearGradient(0f, cy - 14f, 0f, cy + 14f, if (i == 1) 0xFF6ED86E.toInt() else 0xFF7FB0FF.toInt(), if (i == 1) 0xFF1E961E.toInt() else 0xFF2A58C8.toInt(), Shader.TileMode.CLAMP)
                        cv.drawRoundRect(RectF(cx[i] - 36f, cy - 14f, cx[i] + 36f, cy + 14f), 6f, 6f, p); p.shader = null }
                    glyphs(cv, cx, cy, 0xFFFFFFFF.toInt(), playing, .85f)
                }
                WidgetStyle.WIN7 -> {
                    val w = RectF(0f, 0f, W.toFloat(), H.toFloat())
                    // Aero frame: pale glass blue, title with the caption buttons, a black stage with a blue glow
                    p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), 0xFFB9D1EA.toInt(), 0xFF7FA3CC.toInt(), Shader.TileMode.CLAMP); cv.drawRoundRect(w, 8f, 8f, p); p.shader = null
                    text(cv, "Windows Media Player", 10f, 17f, 12f, 0xFF000000.toInt(), 190f)
                    val close = RectF(W - 48f, 0f, W - 8f, 16f); p.shader = LinearGradient(0f, 0f, 0f, 16f, 0xFFE8A99A.toInt(), 0xFFB5321E.toInt(), Shader.TileMode.CLAMP); cv.drawRoundRect(close, 4f, 4f, p); p.shader = null
                    p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 1.6f; cv.drawLine(close.centerX() - 4f, 4f, close.centerX() + 4f, 12f, p); cv.drawLine(close.centerX() + 4f, 4f, close.centerX() - 4f, 12f, p)
                    val stage = RectF(6f, 24f, W - 6f, H - 6f)
                    p.color = 0xFF000000.toInt(); cv.drawRoundRect(stage, 3f, 3f, p)
                    p.shader = LinearGradient(0f, stage.bottom - 70f, 0f, stage.bottom, 0x00007CC8, 0xFF0A5E9E.toInt(), Shader.TileMode.CLAMP); cv.drawRect(stage.left, stage.bottom - 70f, stage.right, stage.bottom, p); p.shader = null
                    val art = RectF(14f, 30f, 82f, 98f); coverInto(cv, cover, art, 0xFF1C2630.toInt())
                    text(cv, title, 94f, 46f, 14f, 0xFFFFFFFF.toInt(), W - 104f, bold = true)
                    text(cv, artist, 94f, 63f, 12f, 0xFFC8D8EA.toInt(), W - 104f)
                    p.color = 0x55FFFFFF; cv.drawRect(94f, 80f, W - 14f, 83f, p); p.color = 0xFF2FA6F0.toInt(); cv.drawRect(94f, 80f, 94f + (W - 108f) * frac, 83f, p)
                    text(cv, time, 94f, 98f, 10f, 0xFFC8D8EA.toInt(), 150f)
                    for (i in 0..2) {
                        val r = if (i == 1) 17f else 13f
                        p.shader = LinearGradient(0f, cy - r, 0f, cy + r, 0xFF6CC4FF.toInt(), 0xFF1068B8.toInt(), Shader.TileMode.CLAMP); cv.drawCircle(cx[i], cy, r, p); p.shader = null
                        p.color = 0x55FFFFFF; cv.drawOval(RectF(cx[i] - r * .8f, cy - r * .9f, cx[i] + r * .8f, cy), p)
                    }
                    glyphs(cv, cx, cy, 0xFFFFFFFF.toInt(), playing, .8f)
                }
                else -> {
                    // Windows 10 / 11 / Material / AMOLED / One UI: a flat card in the colours of the look
                    val dark = style == WidgetStyle.AMOLED || style == WidgetStyle.WIN11_DARK
                    val bg = when (style) { WidgetStyle.AMOLED -> 0xFF000000.toInt(); WidgetStyle.WIN11_DARK -> 0xFF2B2B2B.toInt(); WidgetStyle.WIN10 -> 0xFF1F1F1F.toInt(); else -> 0xFFF3EDF7.toInt() }
                    val ink = if (dark || style == WidgetStyle.WIN10) 0xFFFFFFFF.toInt() else 0xFF1D1B20.toInt()
                    val sub = if (dark || style == WidgetStyle.WIN10) 0xFFB0B0B0.toInt() else 0xFF49454F.toInt()
                    val accent = when (style) { WidgetStyle.WIN11, WidgetStyle.WIN11_DARK, WidgetStyle.WIN10 -> 0xFF0078D4.toInt(); WidgetStyle.AMOLED -> 0xFFFFFFFF.toInt(); else -> 0xFF6750A4.toInt() }
                    val corner = if (style == WidgetStyle.WIN10) 0f else if (style == WidgetStyle.WIN11 || style == WidgetStyle.WIN11_DARK) 12f else 22f
                    // the card can be taken away (transparent widget); then the text and buttons sit straight on the wallpaper in the look's text colour
                    if (card) { p.color = bg; cv.drawRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), corner, corner, p) }
                    val half = 40f * artPct.coerceIn(30, 100) / 100f
                    val art = RectF(52f - half, 52f - half, 52f + half, 52f + half); coverInto(cv, cover, art, accent and 0x00FFFFFF or 0x55000000, if (corner > 0f) 10f else 0f)
                    text(cv, title, 104f, 34f, 16f, ink, W - 116f, bold = true)
                    text(cv, artist, 104f, 54f, 13f, sub, W - 116f)
                    p.color = sub and 0x00FFFFFF or 0x44000000; cv.drawRoundRect(RectF(104f, 76f, W - 12f, 80f), 2f, 2f, p)
                    p.color = accent; cv.drawRoundRect(RectF(104f, 76f, 104f + (W - 116f) * frac, 80f), 2f, 2f, p)
                    text(cv, time, 104f, 92f, 10f, sub, 150f)
                    // side buttons in the text colour, the middle one on an accent disc
                    glyphs(cv, floatArrayOf(cx[0], -200f, cx[2]), cy, ink, playing, .9f)
                    p.color = accent; cv.drawCircle(cx[1], cy, 19f, p)
                    val on = if (style == WidgetStyle.AMOLED) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                    glyphs(cv, floatArrayOf(-200f, cx[1], -200f), cy, on, playing, .9f)
                }
            }
            return out
        }
    }
}
