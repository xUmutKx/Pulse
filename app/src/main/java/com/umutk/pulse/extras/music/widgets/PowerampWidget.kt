package com.umutk.pulse.extras.music.widgets

import com.umutk.pulse.extras.music.artistFor
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
            A_REW -> e.player.seekTo((e.player.currentPosition - 10_000L).coerceAtLeast(0L))
            A_FF -> e.player.let { it.seekTo(if (it.duration > 0) (it.currentPosition + 10_000L).coerceAtMost(it.duration) else it.currentPosition) }
            A_SHUF -> e.player.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
            A_LOOP -> e.player.let { it.repeatMode = when (it.repeatMode) { 0 -> 2; 2 -> 1; else -> 0 } }
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.poweramp.PREV"
        const val A_TOGGLE = "com.umutk.pulse.poweramp.TOGGLE"
        const val A_NEXT = "com.umutk.pulse.poweramp.NEXT"
        const val A_REW = "com.umutk.pulse.poweramp.REW"
        const val A_FF = "com.umutk.pulse.poweramp.FF"
        const val A_SHUF = "com.umutk.pulse.poweramp.SHUF"
        const val A_LOOP = "com.umutk.pulse.poweramp.LOOP"

        /** The window in its own units; every widget size shows all of it. */
        const val W = 320
        const val H = 200
        /** Share of the window height the transport row occupies (top and bottom edges). */
        private const val BTN_TOP = 0.74f
        private const val BTN_BOTTOM = 0.98f

        /** The starry sky behind the widget can be switched off (plain black) in the music settings. */
        fun stars(c: Context) = WidgetOpts.bg(c) == "stars"
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
            val bmp = render(
                style, title, st.current?.artistFor(c).orEmpty(), st.current?.album.orEmpty(), frac, st.isPlaying, e.player.currentPosition, dur, cover,
                st.index, st.queue.size, e.player.shuffleModeEnabled, e.player.repeatMode, bw, bh, stars(c), WidgetOpts.bg(c), WidgetOpts.artPct(c),
            )
            val f = fit(bw, bh)
            return RemoteViews(c.packageName, R.layout.widget_poweramp).apply {
                setImageViewBitmap(R.id.pa_img, bmp)
                // the transport row sits under the drawn buttons: pad the overlay to the fitted window
                val k = lastShrink
                setViewPadding(
                    R.id.pa_overlay,
                    (f.x * k).toInt(), ((f.y + BTN_TOP * f.h) * k).toInt(), ((bw - f.x - f.w) * k).toInt(), ((bh - f.y - BTN_BOTTOM * f.h) * k).toInt(),
                )
                // a tap anywhere else opens the player, straight on the now playing page and without an opening animation
                setOnClickPendingIntent(R.id.pa_img, com.umutk.pulse.widgets.music.WidgetUi.launch(c, "com.umutk.pulse.ExtraToolActivity", 72, "route" to "music"))
                // loop and shuffle sit at the left of the counter line, between the text and the seek line
                setViewPadding(
                    R.id.pa_mid,
                    ((f.x + 152 * f.scale) * k).toInt(), ((f.y + 108 * f.scale) * k).toInt(), ((bw - f.x - 210 * f.scale) * k).toInt(), ((bh - f.y - 140 * f.scale) * k).toInt(),
                )
                setOnClickPendingIntent(R.id.pa_prev, pi(c, A_PREV))
                setOnClickPendingIntent(R.id.pa_rew, pi(c, A_REW))
                setOnClickPendingIntent(R.id.pa_play, pi(c, A_TOGGLE))
                setOnClickPendingIntent(R.id.pa_ff, pi(c, A_FF))
                setOnClickPendingIntent(R.id.pa_next, pi(c, A_NEXT))
                setOnClickPendingIntent(R.id.pa_loop, pi(c, A_LOOP))
                setOnClickPendingIntent(R.id.pa_shuf, pi(c, A_SHUF))
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
        /** How much smaller the bitmap is than the widget on screen (set by [boxPx]); the touch padding is in screen pixels. */
        private var lastShrink = 1f

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
            lastShrink = shrink
            return (pw / shrink).toInt().coerceAtLeast(1) to (ph / shrink).toInt().coerceAtLeast(1)
        }

        private val GREY = 0xFFD2D2D2.toInt()

        /**
         * One Poweramp window at the box size: a black starfield, the rounded cover on the left, title / artist / album on the
         * right with shuffle, loop and the "n/total" counter, and a row of five grey transport buttons. [style] only tints the
         * active shuffle / loop marks and the seek line. Everything is drawn in W x H units and scaled with [fit].
         */
        fun render(
            style: PowerampStyle, title: String, artist: String, album: String, frac: Float, playing: Boolean,
            posMs: Long, durMs: Long, cover: Bitmap?, index: Int, total: Int, shuffle: Boolean, repeat: Int, bw: Int, bh: Int, stars: Boolean = true,
            bg: String = if (stars) "stars" else "black", artPct: Int = 100,
        ): Bitmap {
            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val f = fit(bw, bh)
            if (bg != "none") cv.drawColor(Color.Black.toArgb())
            cv.translate(f.x.toFloat(), f.y.toFloat()); cv.scale(f.scale, f.scale)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val dim = 0xFFA8A8A8.toInt()
            val accent = style.accent.toArgb()

            // starfield: fixed seed, so the sky does not flicker between redraws
            val rnd = java.util.Random(11)
            p.color = android.graphics.Color.WHITE
            for (n in 0 until (if (bg == "stars") 90 else 0)) {
                val x = rnd.nextFloat() * W; val y = rnd.nextFloat() * H
                p.alpha = 40 + rnd.nextInt(180)
                cv.drawCircle(x, y, 0.4f + rnd.nextFloat() * 0.9f, p)
            }
            p.alpha = 255

            // cover: rounded, centre-cropped to a square; a note disc when the track has none
            // the cover keeps its centre and takes the chosen share of its square
            val half = 63f * artPct.coerceIn(30, 100) / 100f
            val art = RectF(75f - half, 75f - half, 75f + half, 75f + half)
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(art, 16f, 16f, Path.Direction.CW) })
            if (cover != null) {
                val side = minOf(cover.width, cover.height)
                val src = android.graphics.Rect((cover.width - side) / 2, (cover.height - side) / 2, (cover.width + side) / 2, (cover.height + side) / 2)
                cv.drawBitmap(cover, src, art, p)
            } else {
                p.color = style.panel.toArgb(); cv.drawRect(art, p)
                p.color = accent; cv.drawCircle(art.centerX(), art.centerY(), 34f, p)
                p.color = android.graphics.Color.BLACK; cv.drawCircle(art.centerX(), art.centerY(), 8f, p)
            }
            cv.restore()

            val x0 = 152f
            val maxW = W - x0 - 12f
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; textSize = 21f; typeface = Typeface.DEFAULT_BOLD }
            val lay = android.text.StaticLayout.Builder.obtain(title, 0, title.length, tp, maxW.toInt())
                .setMaxLines(2).setEllipsize(TextUtils.TruncateAt.END).build()
            cv.save(); cv.translate(x0, 12f); lay.draw(cv); cv.restore()
            var y = 12f + lay.height + 17f
            val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; textSize = 15f }
            cv.drawText(TextUtils.ellipsize(artist, ap, maxW, TextUtils.TruncateAt.END).toString(), x0, y, ap)
            y += 18f
            ap.textSize = 13f
            cv.drawText(TextUtils.ellipsize(album, ap, maxW, TextUtils.TruncateAt.END).toString(), x0, y, ap)

            // loop, shuffle and the counter, on one line above the buttons
            val iy = 124f
            glyphLoop(cv, p, x0 + 12f, iy, if (repeat != 0) accent else dim, repeat == 1)
            glyphShuffle(cv, p, x0 + 46f, iy, if (shuffle) accent else dim)
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; textSize = 14f; textAlign = Paint.Align.RIGHT }
            cv.drawText(if (total > 0) "${index + 1}/$total" else "", W - 12f, iy + 5f, cp)

            // thin seek line
            p.color = dim; p.alpha = 70; cv.drawRect(12f, 146f, W - 12f, 148f, p); p.alpha = 255
            p.color = accent; cv.drawRect(12f, 146f, 12f + (W - 24f) * frac, 148f, p)

            // transport: five buttons on the fifths of the window (the overlay views sit on these)
            val cy = 174f
            val c = GREY
            glyphSkip(cv, p, W * 0.1f, cy, c, back = true)
            glyphSeek(cv, p, W * 0.3f, cy, c, back = true)
            if (playing) glyphPause(cv, p, W * 0.5f, cy, c) else glyphPlay(cv, p, W * 0.5f, cy, c)
            glyphSeek(cv, p, W * 0.7f, cy, c, back = false)
            glyphSkip(cv, p, W * 0.9f, cy, c, back = false)
            return out
        }

        private fun glyphPlay(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col
            cv.drawPath(Path().apply { moveTo(cx - 11, cy - 17); lineTo(cx - 11, cy + 17); lineTo(cx + 17, cy); close() }, p)
        }

        private fun glyphPause(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col
            cv.drawRect(cx - 13f, cy - 16f, cx - 4f, cy + 16f, p)
            cv.drawRect(cx + 4f, cy - 16f, cx + 13f, cy + 16f, p)
        }

        /** Previous / next track: a bar and one triangle. */
        private fun glyphSkip(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int, back: Boolean) {
            p.color = col
            if (back) {
                cv.drawRect(cx - 11f, cy - 11f, cx - 8f, cy + 11f, p)
                cv.drawPath(Path().apply { moveTo(cx + 11, cy - 11); lineTo(cx - 8, cy); lineTo(cx + 11, cy + 11); close() }, p)
            } else {
                cv.drawRect(cx + 8f, cy - 11f, cx + 11f, cy + 11f, p)
                cv.drawPath(Path().apply { moveTo(cx - 11, cy - 11); lineTo(cx + 8, cy); lineTo(cx - 11, cy + 11); close() }, p)
            }
        }

        /** Rewind / fast forward: two triangles. */
        private fun glyphSeek(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int, back: Boolean) {
            p.color = col
            for (x in floatArrayOf(cx - 10f, cx + 1f)) {
                cv.drawPath(Path().apply {
                    if (back) { moveTo(x, cy); lineTo(x + 10f, cy - 11f); lineTo(x + 10f, cy + 11f) } else { moveTo(x + 10f, cy); lineTo(x, cy - 11f); lineTo(x, cy + 11f) }
                    close()
                }, p)
            }
        }

        private fun glyphShuffle(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int) {
            p.color = col; p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.strokeCap = Paint.Cap.ROUND
            cv.drawPath(Path().apply { moveTo(cx - 10, cy + 6); lineTo(cx - 4, cy + 6); lineTo(cx + 4, cy - 6); lineTo(cx + 8, cy - 6) }, p)
            cv.drawPath(Path().apply { moveTo(cx - 10, cy - 6); lineTo(cx - 4, cy - 6); lineTo(cx + 4, cy + 6); lineTo(cx + 8, cy + 6) }, p)
            p.style = Paint.Style.FILL
            cv.drawPath(Path().apply { moveTo(cx + 12, cy - 6); lineTo(cx + 7, cy - 10); lineTo(cx + 7, cy - 2); close() }, p)
            cv.drawPath(Path().apply { moveTo(cx + 12, cy + 6); lineTo(cx + 7, cy + 2); lineTo(cx + 7, cy + 10); close() }, p)
        }

        private fun glyphLoop(cv: Canvas, p: Paint, cx: Float, cy: Float, col: Int, one: Boolean) {
            p.color = col; p.style = Paint.Style.STROKE; p.strokeWidth = 2f; p.strokeCap = Paint.Cap.ROUND
            cv.drawRoundRect(RectF(cx - 10, cy - 6, cx + 10, cy + 6), 6f, 6f, p)
            p.style = Paint.Style.FILL
            cv.drawPath(Path().apply { moveTo(cx + 3, cy - 10); lineTo(cx + 9, cy - 6); lineTo(cx + 3, cy - 2); close() }, p)
            if (one) {
                val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col; textSize = 9f; textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
                cv.drawText("1", cx, cy + 3.5f, t)
            }
        }
    }
}
