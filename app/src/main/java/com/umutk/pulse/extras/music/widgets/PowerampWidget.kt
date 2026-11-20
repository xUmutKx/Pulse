package com.umutk.pulse.extras.music.widgets

import com.umutk.pulse.extras.music.PlayModes
import com.umutk.pulse.extras.music.ShuffleKind
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
            A_SHUF -> PlayModes.cycleShuffle(c)
            A_LOOP -> PlayModes.cycleRepeat(c)
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

        /**
         * The widget for every size the home screen may show it at (Android 12 and later tell both, upright and sideways): the home screen picks the one that fits,
         * so the window is never drawn for the wrong one (it used to be drawn small when the phone had been held the other way a moment before).
         */
        fun build(c: Context, id: Int): RemoteViews = buildFor(c, id, boxPx(c, id))

        private fun buildFor(c: Context, id: Int, box: WBox): RemoteViews {
            val bw = box.bw; val bh = box.bh
            val e = MusicPlayerEngine.ready(c)
            val st = e.state.value
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val frac = if (dur > 0) (e.player.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val cover = st.current?.let { trackArt(c, it)?.asAndroidBitmap() }
            val title = st.current?.let { it.title ?: it.path.substringAfterLast('/') } ?: ""
            val style = powerampSkin(PowerampPrefs.skin(c)).build(NeutralAccent, Color.Black)
            val bmp = render(
                style, title, st.current?.artistFor(c).orEmpty(), st.current?.album.orEmpty(), frac, st.isPlaying, e.player.currentPosition, dur, cover,
                st.index, st.queue.size, PlayModes.shuffle != ShuffleKind.OFF, e.player.repeatMode, bw, bh, stars(c), WidgetOpts.bg(c), WidgetOpts.artPct(c), WidgetOpts.corners(c), WidgetOpts.artShadow(c), WidgetOpts.artCircle(c), WidgetOpts.look(c),
            )
            val lay = layoutFor(bw, bh)
            val f = fit(lay, bw, bh)
            val k = box.shrink
            return RemoteViews(c.packageName, R.layout.widget_poweramp).apply {
                setImageViewBitmap(R.id.pa_img, bmp)
                // the transport row sits under the drawn buttons: pad the overlay to the fitted window (the buttons are spaced evenly between btnL and btnR)
                setViewPadding(
                    R.id.pa_overlay,
                    ((f.x + lay.btnL * f.scale) * k).toInt(), ((f.y + lay.ovT * f.scale) * k).toInt(), ((bw - f.x - lay.btnR * f.scale) * k).toInt(), ((bh - f.y - lay.ovB * f.scale) * k).toInt(),
                )
                // a tap anywhere else opens the player, straight on the now playing page and without an opening animation
                // straight to the activity, like the music widget (the no-display trampoline is dropped by some launchers); each widget has its own request code
                setOnClickPendingIntent(R.id.pa_img, android.app.PendingIntent.getActivity(c, 74000 + id,
                    Intent().setClassName(c, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "music").putExtra("notif_open", "player").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT))
                // loop and shuffle sit at the left of the counter line, between the text and the seek line
                // (they follow the text to the left when the cover sits at the right)
                val right = WidgetOpts.look(c).artRight && lay.artRightOk
                val mx = if (right) 8f else lay.textL - 4f
                setViewPadding(
                    R.id.pa_mid,
                    ((f.x + mx * f.scale) * k).toInt(), ((f.y + lay.marksY * f.scale) * k).toInt(), ((bw - f.x - (mx + lay.marksW) * f.scale) * k).toInt(), ((bh - f.y - (lay.marksY + lay.marksH) * f.scale) * k).toInt(),
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

        /**
         * Where everything sits in one window, in the window's own units. The widget picks the layout that suits its shape (so a wide, short widget is a row and a tall one
         * a column, instead of the standard window floating small in the middle with empty sides): [STD] is the 320 x 200 window, [WIDE] a row, [TALL] a column.
         */
        class Lay(
            val w: Int, val h: Int, val cx: Float, val cy: Float, val half: Float, val textL: Float, val textW: Float, val titleY: Float, val textMaxY: Float, val iy: Float,
            val seekY: Float, val seekL: Float, val seekR: Float, val btnY: Float, val btnL: Float, val btnR: Float, val ovT: Float, val ovB: Float,
            val marksY: Float, val marksW: Float, val marksH: Float, val artRightOk: Boolean,
        ) {
            /** The centre of button [i] (0..4): the five are spaced evenly between [btnL] and [btnR]. */
            fun bx(i: Int) = btnL + (i + .5f) * (btnR - btnL) / 5f
        }
        val STD = Lay(W, H, 75f, 75f, 63f, 152f, 156f, 12f, 112f, 124f, 146f, 12f, 308f, 174f, 24f, 296f, 148f, 196f, 100f, 78f, 48f, true)
        val WIDE = Lay(520, 120, 60f, 60f, 50f, 124f, 170f, 10f, 90f, 98f, 112f, 12f, 508f, 60f, 304f, 512f, 34f, 88f, 80f, 78f, 32f, false)
        val TALL = Lay(240, 360, 120f, 100f, 88f, 14f, 212f, 204f, 292f, 306f, 322f, 14f, 226f, 344f, 12f, 228f, 326f, 358f, 292f, 78f, 28f, false)

        /** The layout for a box of [bw] x [bh] pixels: a row when it is much wider than the standard window, a column when it is nearly square or taller. */
        fun layoutFor(bw: Int, bh: Int): Lay {
            val r = bw.toFloat() / bh.coerceAtLeast(1)
            return if (r > 2.35f) WIDE else if (r < 1.2f) TALL else STD
        }

        /** Whole-window fit: the largest scale at which the window of [l] fits the box, centred. (Nothing is kept between two sizes: each box carries its own numbers.) */
        fun fit(l: Lay, bw: Int, bh: Int): Fit {
            val s = minOf(bw / l.w.toFloat(), bh / l.h.toFloat())
            val w = (l.w * s).toInt(); val h = (l.h * s).toInt()
            return Fit(s, (bw - w) / 2, (bh - h) / 2, w, h)
        }

        data class Fit(val scale: Float, val x: Int, val y: Int, val w: Int, val h: Int)

        /** A widget's box in bitmap pixels ([bw] x [bh]) and how much smaller than the widget on screen the bitmap is ([shrink]; the touch padding is in screen pixels). */
        class WBox(val bw: Int, val bh: Int, val shrink: Float)

        /** The widget's box from its options: portrait uses the narrow width and the tall height, landscape the reverse. */
        private fun boxPx(c: Context, id: Int): WBox {
            val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val portrait = c.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0))
                .takeIf { it > 0 } ?: 250
            val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
                .takeIf { it > 0 } ?: 110
            return boxOf(c, w.toFloat(), h.toFloat())
        }

        /** The box of a widget [w] x [h] dp in bitmap pixels; also sets how much smaller than the screen the bitmap is. */
        private fun boxOf(c: Context, w: Float, h: Float): WBox {
            val d = c.resources.displayMetrics.density
            val pw = w * d; val ph = h * d
            // the bitmaps of a widget have a memory limit (about one and a half screens for all of them, and there is one per size): half a million pixels each stays well under it
            val shrink = kotlin.math.sqrt(pw * ph / 500_000f).coerceAtLeast(1f)
            return WBox((pw / shrink).toInt().coerceAtLeast(1), (ph / shrink).toInt().coerceAtLeast(1), shrink)
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
            bg: String = if (stars) "stars" else "black", artPct: Int = 100, corners: Int = 1, artShadow: Boolean = false, artCircle: Boolean = false,
            look: WidgetLook = WidgetLook(),
        ): Bitmap {
            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val lay = layoutFor(bw, bh)
            val f = fit(lay, bw, bh)
            // the corners of the black or starry background (transparent has none to round)
            if (bg != "none") {
                val r = listOf(0f, 26f, 60f)[corners.coerceIn(0, 2)] * f.scale
                cv.drawRoundRect(RectF(0f, 0f, bw.toFloat(), bh.toFloat()), r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.Black.toArgb() })
            }
            cv.translate(f.x.toFloat(), f.y.toFloat()); cv.scale(f.scale, f.scale)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val dim = look.sub
            val accent = if (look.accent != 0) look.accent else style.accent.toArgb()
            // the typeface of the text: the family chosen on the settings page, bold for the title
            val family = WIDGET_FONTS.firstOrNull { it.first == look.font }?.third ?: "sans-serif"
            val titleFace = Typeface.create(family, if (look.font == "casual" || look.font == "cursive" || look.font == "light") Typeface.NORMAL else Typeface.BOLD)
            val textFace = Typeface.create(family, Typeface.NORMAL)

            // starfield: fixed seed, so the sky does not flicker between redraws
            val rnd = java.util.Random(11)
            p.color = android.graphics.Color.WHITE
            for (n in 0 until (if (bg == "stars") 90 else 0)) {
                val x = rnd.nextFloat() * lay.w; val y = rnd.nextFloat() * lay.h
                p.alpha = 40 + rnd.nextInt(180)
                cv.drawCircle(x, y, 0.4f + rnd.nextFloat() * 0.9f, p)
            }
            p.alpha = 255

            // cover: rounded, centre-cropped to a square; a note disc when the track has none
            // the cover keeps its centre and takes the chosen share of its square
            val half = lay.half * artPct.coerceIn(30, 100) / 100f
            // (the cover at the left, or at the right with the text at the left; only the standard window has the cover at the right)
            val right = look.artRight && lay.artRightOk
            val acx = if (right) lay.w - lay.cx else lay.cx
            val art = RectF(acx - half, lay.cy - half, acx + half, lay.cy + half)
            val rad = if (artCircle) half else listOf(0f, 16f, half * .5f)[corners.coerceIn(0, 2)]
            if (artShadow) cv.drawRoundRect(art, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.BLACK; setShadowLayer(12f, 0f, 5f, 0xB0000000.toInt()) })
            cv.save()
            cv.clipPath(Path().apply { addRoundRect(art, rad, rad, Path.Direction.CW) })
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

            val x0 = if (right) 12f else lay.textL
            val maxW = lay.textW
            // the size of the text and where it sits in its place are the person's choice; a bigger title keeps to one line so the artist stays in the window
            val ts = look.textPct.coerceIn(70, 150) / 100f
            val layAlign = when (look.align) { 1 -> android.text.Layout.Alignment.ALIGN_CENTER; 2 -> android.text.Layout.Alignment.ALIGN_OPPOSITE; else -> android.text.Layout.Alignment.ALIGN_NORMAL }
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = look.title; textSize = 21f * ts; typeface = titleFace }
            val tl = if (android.os.Build.VERSION.SDK_INT >= 23) android.text.StaticLayout.Builder.obtain(title, 0, title.length, tp, maxW.toInt())
                .setAlignment(layAlign).setMaxLines(if (ts > 1.15f) 1 else 2).setEllipsize(TextUtils.TruncateAt.END).build()
            else @Suppress("DEPRECATION") android.text.StaticLayout(TextUtils.ellipsize(title, tp, maxW * 1.9f, TextUtils.TruncateAt.END), tp, maxW.toInt(), layAlign, 1f, 0f, false)
            cv.save(); cv.translate(x0, lay.titleY); tl.draw(cv); cv.restore()
            var y = lay.titleY + tl.height + 17f * ts
            val ap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; textSize = 15f * ts; typeface = textFace; textAlign = when (look.align) { 1 -> Paint.Align.CENTER; 2 -> Paint.Align.RIGHT; else -> Paint.Align.LEFT } }
            val tx = when (look.align) { 1 -> x0 + maxW / 2f; 2 -> x0 + maxW; else -> x0 }
            // (the lines stay above the marks and the counter)
            if (y < lay.textMaxY) cv.drawText(TextUtils.ellipsize(artist, ap, maxW, TextUtils.TruncateAt.END).toString(), tx, y, ap)
            y += 18f * ts
            ap.textSize = 13f * ts
            if (look.album && y < lay.textMaxY) cv.drawText(TextUtils.ellipsize(album, ap, maxW, TextUtils.TruncateAt.END).toString(), tx, y, ap)

            // loop, shuffle and the counter, on one line above the buttons
            val iy = lay.iy
            // the two marks are a third bigger, and their touch areas (pa_mid, below) a good deal larger
            if (look.marks) {
                cv.save(); cv.scale(1.38f, 1.38f, x0 + 15f, iy); glyphLoop(cv, p, x0 + 15f, iy, if (repeat != 0) accent else dim, repeat == 1); cv.restore()
                cv.save(); cv.scale(1.38f, 1.38f, x0 + 54f, iy); glyphShuffle(cv, p, x0 + 54f, iy, if (shuffle) accent else dim); cv.restore()
            }
            val cp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dim; textSize = 14f; textAlign = Paint.Align.RIGHT; typeface = textFace }
            if (look.counter) cv.drawText(if (total > 0) "${index + 1}/$total" else "", x0 + maxW, iy + 5f, cp)

            // thin seek line
            if (look.seek) {
                p.color = dim; p.alpha = 70; cv.drawRect(lay.seekL, lay.seekY, lay.seekR, lay.seekY + 2f, p); p.alpha = 255
                p.color = accent; cv.drawRect(lay.seekL, lay.seekY, lay.seekL + (lay.seekR - lay.seekL) * frac, lay.seekY + 2f, p)
            }

            // transport: five buttons on the fifths of the window (the overlay views sit on these)
            val cy = lay.btnY
            val c = look.button
            // filled buttons, or only their outlines; every button has the size chosen on the settings page
            p.style = if (look.outline) Paint.Style.STROKE else Paint.Style.FILL
            p.strokeWidth = 2.2f; p.strokeJoin = Paint.Join.ROUND
            val k = look.btnPct / 100f
            fun big(x: Float, scale: Float = 1f, draw: () -> Unit) { cv.save(); cv.scale(k * scale, k * scale, x, cy); draw(); cv.restore() }
            big(lay.bx(0)) { glyphSkip(cv, p, lay.bx(0), cy, c, back = true) }
            big(lay.bx(1)) { glyphSeek(cv, p, lay.bx(1), cy, c, back = true) }
            big(lay.bx(2), 0.9f) { if (playing) glyphPause(cv, p, lay.bx(2), cy, c) else glyphPlay(cv, p, lay.bx(2), cy, c) }
            big(lay.bx(3)) { glyphSeek(cv, p, lay.bx(3), cy, c, back = false) }
            big(lay.bx(4)) { glyphSkip(cv, p, lay.bx(4), cy, c, back = false) }
            p.style = Paint.Style.FILL
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
