package com.umutk.pulse.extras.music.widgets

import com.umutk.pulse.extras.music.shownArtist

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.poweramp.NeutralAccent
import com.umutk.pulse.extras.music.poweramp.PowerampBackdrop
import com.umutk.pulse.extras.music.poweramp.PowerampPrefs
import com.umutk.pulse.extras.music.poweramp.PowerampStyle
import com.umutk.pulse.extras.music.poweramp.powerampSkin
import com.umutk.pulse.extras.music.trackArt

/**
 * Poweramp as a home-screen widget: the saved Poweramp skin drawn as one whole window (cover, title, artist,
 * seek line, transport), fitted into the widget at any size. The transport buttons are transparent views laid
 * over the drawn ones, so a tap lands on the glyph it sits under.
 */
class PowerampWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { loop.start(c); ids.forEach { m.updateAppWidget(it, build(c, it)) } }

    override fun onEnabled(c: Context) = loop.start(c)

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) { m.updateAppWidget(id, build(c, id)) }

    override fun onReceive(c: Context, i: Intent) {
        val e = MusicPlayerEngine.ready(c)
        when (i.action) {
            A_PREV -> e.previous()
            A_TOGGLE -> e.togglePlayPause()
            A_NEXT -> e.next()
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.poweramp.PREV"
        const val A_TOGGLE = "com.umutk.pulse.poweramp.TOGGLE"
        const val A_NEXT = "com.umutk.pulse.poweramp.NEXT"

        /** The window in its own units; every widget size shows all of it. */
        const val W = 320
        const val H = 180
        /** Share of the window height the transport row occupies (top and bottom edges). */
        private const val BTN_TOP = 0.78f
        private const val BTN_BOTTOM = 0.96f

        private val loop = LiveLoop(1000) { refresh(it) }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, PowerampWidget::class.java)).forEach { m.updateAppWidget(it, build(c, it)) }
        }

        private fun pi(c: Context, a: String) =
            PendingIntent.getBroadcast(c, a.hashCode(), Intent(a).setComponent(ComponentName(c, PowerampWidget::class.java)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun build(c: Context, id: Int): RemoteViews {
            val (bw, bh) = boxPx(c, id)
            val e = MusicPlayerEngine.ready(c)
            val st = e.state.value
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val frac = if (dur > 0) (e.player.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val cover = st.current?.let { trackArt(c, it)?.asAndroidBitmap() }
            val title = st.current?.let { it.title ?: it.path.substringAfterLast('/') } ?: ""
            val style = powerampSkin(PowerampPrefs.skin(c)).build(NeutralAccent, Color.Black)
            val bmp = render(style, title, st.current?.shownArtist().orEmpty(), frac, st.isPlaying, e.player.currentPosition, dur, cover, bw, bh)
            val f = fit(bw, bh)
            return RemoteViews(c.packageName, R.layout.widget_poweramp).apply {
                setImageViewBitmap(R.id.pa_img, bmp)
                // the transport row sits under the drawn buttons: pad the overlay to the fitted window
                setViewPadding(
                    R.id.pa_overlay,
                    f.x, f.y + (BTN_TOP * f.h).toInt(), bw - f.x - f.w, bh - f.y - (BTN_BOTTOM * f.h).toInt(),
                )
                setOnClickPendingIntent(R.id.pa_prev, pi(c, A_PREV))
                setOnClickPendingIntent(R.id.pa_play, pi(c, A_TOGGLE))
                setOnClickPendingIntent(R.id.pa_next, pi(c, A_NEXT))
            }
        }

        /** Whole-window fit: the largest scale at which W x H fits the box, centred. */
        fun fit(bw: Int, bh: Int): Fit {
            val s = minOf(bw / W.toFloat(), bh / H.toFloat())
            val w = (W * s).toInt(); val h = (H * s).toInt()
            return Fit(s, (bw - w) / 2, (bh - h) / 2, w, h)
        }

        data class Fit(val scale: Float, val x: Int, val y: Int, val w: Int, val h: Int)

        /** The widget's box in pixels from its options: portrait uses the narrow width and the tall height, landscape the reverse. */
        private fun boxPx(c: Context, id: Int): Pair<Int, Int> {
            val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val portrait = c.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0))
                .takeIf { it > 0 } ?: 250
            val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
                .takeIf { it > 0 } ?: 110
            val pw = w * d; val ph = h * d
            // RemoteViews carries bitmaps over a 1 MB binder limit; RGB_565 is 2 bytes a pixel, so keep about 350k pixels
            val shrink = kotlin.math.sqrt(pw * ph / 900_000f).coerceAtLeast(1f)
            return (pw / shrink).toInt().coerceAtLeast(1) to (ph / shrink).toInt().coerceAtLeast(1)
        }

        /**
         * One Poweramp window at the box size. [style] is the skin; the picker passes a sample title and no cover.
         * Everything is drawn in W x H units and scaled with [fit], so nothing is cut off.
         */
        fun render(
            style: PowerampStyle, title: String, artist: String, frac: Float, playing: Boolean,
            posMs: Long, durMs: Long, cover: Bitmap?, bw: Int, bh: Int,
        ): Bitmap {
            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val f = fit(bw, bh)
            cv.drawColor(style.solid.toArgb())
            cv.translate(f.x.toFloat(), f.y.toFloat()); cv.scale(f.scale, f.scale)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val text = style.onBackdrop.toArgb()
            val dim = style.textDim.toArgb()
            val accent = style.accent.toArgb()

            // backdrop: solid or gradient; the blurred-art skins get the cover faintly behind everything
            val gradTo = style.gradientTo
            if (gradTo != null && style.backdrop == PowerampBackdrop.SOLID) {
                p.shader = LinearGradient(0f, 0f, 0f, H.toFloat(), style.solid.toArgb(), gradTo.toArgb(), Shader.TileMode.CLAMP)
                cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
                p.shader = null
            } else {
                p.color = style.solid.toArgb(); cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)
            }
            if (cover != null && style.backdrop == PowerampBackdrop.BLURRED_ART) {
                p.alpha = 70; cv.drawBitmap(cover, null, RectF(0f, 0f, W.toFloat(), H.toFloat()), p); p.alpha = 255
            }

            // cover: the whole picture, square, on the left; a note disc when the track has none
            val art = RectF(16f, 12f, 136f, 132f)
            if (cover != null) cv.drawBitmap(cover, null, art, p) else {
                p.color = style.panel.toArgb(); cv.drawRoundRect(art, 10f, 10f, p)
                p.color = accent; cv.drawCircle(art.centerX(), art.centerY(), 34f, p)
                p.color = style.solid.toArgb(); cv.drawCircle(art.centerX(), art.centerY(), 8f, p)
            }

            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = text; textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
            val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; textSize = 14f }
            val maxW = W - 172f - 12f
            cv.drawText(TextUtils.ellipsize(title, tp, maxW, TextUtils.TruncateAt.END).toString(), 172f, 50f, tp)
            cv.drawText(TextUtils.ellipsize(artist, ap, maxW, TextUtils.TruncateAt.END).toString(), 172f, 72f, ap)

            // seek line
            p.color = dim; p.alpha = 90; cv.drawRect(172f, 96f, W - 12f, 100f, p); p.alpha = 255
            p.color = accent; cv.drawRect(172f, 96f, 172f + (W - 12f - 172f) * frac, 100f, p)

            val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; textSize = 12f }
            cv.drawText(clock(posMs), 172f, 118f, small)
            small.textAlign = Paint.Align.RIGHT
            cv.drawText(if (durMs > 0) clock(durMs) else "--:--", W - 12f, 118f, small)

            // transport: prev, play or pause, next, centred on the thirds of the window (the overlay views sit on these)
            val cy = 156f
            glyphPrev(cv, p, W / 6f, cy, text)
            if (playing) glyphPause(cv, p, W / 2f, cy, accent) else glyphPlay(cv, p, W / 2f, cy, accent)
            glyphNext(cv, p, W * 5 / 6f, cy, text)
            return out
        }

        private fun clock(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return "%d:%02d".format(s / 60, s % 60)
        }

        private fun glyphPlay(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col
            cv.drawPath(Path().apply { moveTo(cx - 8, cy - 11); lineTo(cx - 8, cy + 11); lineTo(cx + 11, cy); close() }, p)
        }

        private fun glyphPause(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col
            cv.drawRect(cx - 9f, cy - 10f, cx - 3f, cy + 10f, p)
            cv.drawRect(cx + 3f, cy - 10f, cx + 9f, cy + 10f, p)
        }

        private fun glyphPrev(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col
            cv.drawRect(cx - 11f, cy - 9f, cx - 8f, cy + 9f, p)
            cv.drawPath(Path().apply { moveTo(cx + 9, cy); lineTo(cx - 8, cy - 9); lineTo(cx - 8, cy + 9); close() }, p)
        }

        private fun glyphNext(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col
            cv.drawRect(cx + 8f, cy - 9f, cx + 11f, cy + 9f, p)
            cv.drawPath(Path().apply { moveTo(cx - 9, cy); lineTo(cx + 8, cy - 9); lineTo(cx + 8, cy + 9); close() }, p)
        }
    }
}
