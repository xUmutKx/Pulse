package com.umutk.pulse.widgets.music

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.widgets.LiveLoop
import com.umutk.pulse.extras.music.skin.SPRITE_H
import com.umutk.pulse.extras.music.skin.WINAMP_H
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampButton
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.installedSkins
import com.umutk.pulse.extras.music.skin.skinGlyph
import com.umutk.pulse.extras.music.skin.loadWinampSkin
import java.io.File
import kotlin.math.sqrt
import com.umutk.pulse.widgets.TextTile

/** Winamp main window as a home-screen widget: skin bitmap, track title, seek bar, working transport buttons. */
open class WinampWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { watch(c); ids.forEach { m.updateAppWidget(it, build(c, it)) } }

    override fun onEnabled(c: Context) = watch(c)

    // the window is fitted to the size, so a resize redraws it
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) { m.updateAppWidget(id, build(c, id)) }

    override fun onDeleted(c: Context, ids: IntArray) { prefs(c).edit().apply { ids.forEach { remove("skin_$it") } }.apply() }

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            A_PREV -> MusicPlayerEngine.ready(c).previous()
            A_PLAY -> MusicPlayerEngine.ready(c).play()
            A_PAUSE -> MusicPlayerEngine.ready(c).pause()
            A_STOP -> MusicPlayerEngine.ready(c).player.run { pause(); seekTo(0) }
            A_NEXT -> MusicPlayerEngine.ready(c).next()
            A_SEEK -> MusicPlayerEngine.ready(c).player.let { p -> if (p.duration > 0) p.seekTo(p.duration * (i.getIntExtra("seg", 0) * 2 + 1) / 32) }
            A_SHUF -> MusicPlayerEngine.ready(c).player.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
            A_REP -> MusicPlayerEngine.ready(c).player.let { it.repeatMode = when (it.repeatMode) { 0 -> 2; 2 -> 1; else -> 0 } }
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.winamp.PREV"
        const val A_PLAY = "com.umutk.pulse.winamp.PLAY"
        const val A_PAUSE = "com.umutk.pulse.winamp.PAUSE"
        const val A_STOP = "com.umutk.pulse.winamp.STOP"
        const val A_NEXT = "com.umutk.pulse.winamp.NEXT"
        const val A_SEEK = "com.umutk.pulse.winamp.SEEK"
        const val A_SHUF = "com.umutk.pulse.winamp.SHUF"
        const val A_REP = "com.umutk.pulse.winamp.REP"
        /** Height of the 4x1 strip in window pixels: title and time rows (y 0..40) over the seek bar and buttons (y 72..106). */
        const val STRIP_H = 74
        /** Skin a widget shows until the user picks one: a bundled skin from assets/skins (listed in index.json), so the widget is drawn with real Winamp sprites. The original base-2.91.wsz could not be fetched. */
        const val DEFAULT_SKIN = "classic-ebony.wsz"
        private val SEG = intArrayOf(R.id.wa_s0, R.id.wa_s1, R.id.wa_s2, R.id.wa_s3, R.id.wa_s4, R.id.wa_s5, R.id.wa_s6, R.id.wa_s7, R.id.wa_s8, R.id.wa_s9, R.id.wa_s10, R.id.wa_s11, R.id.wa_s12, R.id.wa_s13, R.id.wa_s14, R.id.wa_s15)
        /** SharedPreferences "winamp_widget": "skin_<appWidgetId>" = skin file name (bundled filesDir/skins/<id>.wsz or an installed one), absent = drawn default. */
        fun prefs(c: Context) = c.getSharedPreferences("winamp_widget", Context.MODE_PRIVATE)
        fun skinDir(c: Context) = File(c.filesDir, "extras/skins")

        private val loop = LiveLoop(1000) { refresh(it) }

        /** Redraws on every playback state change and each second while playing, so the time and the seek knob move. */
        private fun watch(c: Context) = loop.start(c)

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            listOf(WinampWidget::class.java, WinampWidgetCompact::class.java).forEach { k ->
                m.getAppWidgetIds(ComponentName(c, k)).forEach { m.updateAppWidget(it, build(c, it)) }
            }
        }

        private val skinCache = HashMap<String, Pair<Long, WinampSkin?>>()

        private fun skinFor(c: Context, id: Int): WinampSkin? {
            // a widget without its own skin shows the original Winamp base skin; without that file the drawn default stays
            val name = prefs(c).getString("skin_$id", null) ?: DEFAULT_SKIN
            // bundled skins live in filesDir/skins (copied from assets by installedSkins); the list is only walked on a cache miss
            val f = File(c.filesDir, "skins/$name").takeIf { it.isFile } ?: installedSkins(c).firstOrNull { it.file.name == name }?.file ?: return null
            val hit = skinCache[name]
            if (hit != null && hit.first == f.lastModified()) return hit.second
            // a broken skin file must not throw here: the widget would then fail to load and show "Can't load widget"
            val s = runCatching { loadWinampSkin(f) }.getOrNull()
            skinCache[name] = f.lastModified() to s
            return s
        }

        private fun pi(c: Context, a: String) =
            PendingIntent.getBroadcast(c, a.hashCode(), Intent(a).setComponent(ComponentName(c, WinampWidget::class.java)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        /** The widget as drawn. When that fails for any reason, the default skin is drawn instead, so the launcher never shows "can't load widget". */
        fun build(c: Context, id: Int): RemoteViews = runCatching { buildFull(c, id) }.getOrElse { err ->
            android.util.Log.w("WinampWidget", "drawing the default skin: ${err.message}")
            val compact = AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className == WinampWidgetCompact::class.java.name
            RemoteViews(c.packageName, if (compact) R.layout.widget_winamp_compact else R.layout.widget_music_winamp).apply {
                setImageViewBitmap(R.id.wa_img, render(null, "Pulse", 0f, false, 0L, boxW = WINAMP_W, boxH = if (compact) STRIP_H else WINAMP_H, strip = compact))
            }
        }

        private fun buildFull(c: Context, id: Int): RemoteViews {
            val e = MusicPlayerEngine.ready(c)
            val st = e.state.value
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val frac = if (dur > 0) (e.player.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val title = st.current?.let { it.title ?: it.path.substringAfterLast('/') } ?: "Winamp"
            val sk = skinFor(c, id)
            val compact = AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className == WinampWidgetCompact::class.java.name
            val (bw, bh) = boxPx(c, id, compact)
            fun draw(k: WinampSkin?) = render(k, title, frac, st.isPlaying, e.player.currentPosition / 1000, e.player.shuffleModeEnabled, e.player.repeatMode, e.player.volume, bw, bh, compact)
            // a skin with an odd sprite size must not blank the widget: fall back to the drawn default
            // a big widget makes a bitmap over the ~1 MB RemoteViews limit, and the whole widget then fails to load: shrink it to fit the binder
            val bmp = TextTile.fitBinder(runCatching { draw(sk) }.getOrElse { draw(null) }, 900_000)
            // the window as drawn in the bitmap, scaled to the view's real pixels (the bitmap may be smaller than the view), so the touch areas sit on its buttons
            val r = drawnRect(bw, bh, if (compact) STRIP_H else WINAMP_H)
            val vs = viewPx(c, id, compact).first / bw
            val px = (r[0] * vs).toInt(); val py = (r[1] * vs).toInt(); val pw = (r[2] * vs).toInt(); val ph = (r[3] * vs).toInt()
            val vw = (bw * vs).toInt(); val vh = (bh * vs).toInt()
            return RemoteViews(c.packageName, if (compact) R.layout.widget_winamp_compact else R.layout.widget_music_winamp).apply {
                SEG.forEachIndexed { n, v -> setOnClickPendingIntent(v, WidgetUi.broadcast(c, WinampWidget::class.java, A_SEEK, 600 + n) { putExtra("seg", n) }) }
                setOnClickPendingIntent(R.id.wa_shuf, pi(c, A_SHUF)); setOnClickPendingIntent(R.id.wa_rep, pi(c, A_REP))
                setImageViewBitmap(R.id.wa_img, bmp)
                // the controls are laid out over the whole widget: pad them to the drawn window so they sit on its buttons
                setViewPadding(R.id.wa_overlay, px, py, (vw - px - pw).coerceAtLeast(0), (vh - py - ph).coerceAtLeast(0))
                setOnClickPendingIntent(R.id.wa_prev, pi(c, A_PREV)); setOnClickPendingIntent(R.id.wa_play, pi(c, A_PLAY))
                setOnClickPendingIntent(R.id.wa_pause, pi(c, A_PAUSE)); setOnClickPendingIntent(R.id.wa_stop, pi(c, A_STOP))
                setOnClickPendingIntent(R.id.wa_next, pi(c, A_NEXT))
            }
        }

        /** The widget's size in pixels from its options: the smallest it can be, so the window fits at any size; the provider's minimum when unknown. */
        /** The widget's size on screen in pixels (its smallest reported size). */
        private fun viewPx(c: Context, id: Int, compact: Boolean): Pair<Float, Float> {
            val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0).takeIf { it > 0 } ?: 250
            val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0).takeIf { it > 0 } ?: if (compact) 40 else 110
            return w * d to h * d
        }

        /** Where [render] puts the window in a boxW x boxH bitmap: x, y, width, height (whole-pixel enlargement, centred). */
        @Suppress("UNUSED_PARAMETER")
        fun drawnRect(boxW: Int, boxH: Int, winH: Int): IntArray = intArrayOf(0, 0, boxW, boxH)

        private fun boxPx(c: Context, id: Int, compact: Boolean): Pair<Int, Int> {
            val (pw, ph) = viewPx(c, id, compact)
            // RemoteViews bitmaps count against a per-update memory budget (about 1.5 screens); ARGB at 900k pixels stays far below it
            val shrink = sqrt(pw * ph / 900_000f).coerceAtLeast(1f)
            return (pw / shrink).toInt().coerceAtLeast(1) to (ph / shrink).toInt().coerceAtLeast(1)
        }

        /** The whole sw x sh window at the largest scale that fits a bw x bh box, centred; the rest is letterbox. */
        fun fitOf(bw: Int, bh: Int, sw: Int, sh: Int): WinampFit {
            val s = minOf(bw / sw.toFloat(), bh / sh.toFloat())
            val w = (sw * s).toInt(); val h = (sh * s).toInt()
            return WinampFit(s, (bw - w) / 2, (bh - h) / 2, w, h)
        }

        /** Letterbox colour: the skin's own window colour (its top-left pixel), else the drawn default. */
        private fun skinBg(skin: WinampSkin?): Int =
            runCatching { skin?.main?.asAndroidBitmap()?.getPixel(0, 0) }.getOrNull()?.takeIf { Color.alpha(it) == 255 } ?: Color.rgb(58, 58, 74)

        /**
         * The main window (or the 4x1 strip of it) fitted whole into a boxW x boxH bitmap, letterboxed with the skin colour.
         * A skin without main.bmp gets the drawn default; button cells come from cbuttons.bmp when present.
         * Also used by the config preview and the Widgets page, which pass no box size and get the window at 2x.
         */
        fun render(skin: WinampSkin?, title: String, frac: Float, playing: Boolean, posSec: Long, shuffle: Boolean = false, repeat: Int = 0, volume: Float = 1f,
                   boxW: Int = WINAMP_W * 2, boxH: Int = WINAMP_H * 2, strip: Boolean = false): Bitmap {
            val out = Bitmap.createBitmap(boxW, boxH, Bitmap.Config.ARGB_8888)
            val winH = if (strip) STRIP_H else WINAMP_H
            // the window is painted at its real 275-pixel size, enlarged by whole steps without smoothing (sharp skin pixels) and then
            // fitted to the box with smoothing, so the sprites stay crisp at any widget size
            val win = Bitmap.createBitmap(WINAMP_W, winH, Bitmap.Config.ARGB_8888)
            val wc = Canvas(win)
            fun paint() = paintWindow(wc, skin, title, frac, playing, posSec, shuffle, repeat, volume)
            if (!strip) paint() else {
                // the title and time rows, then the seek bar and buttons moved up under them (window y 72..106 lands at 40..74)
                wc.save(); wc.clipRect(0f, 0f, WINAMP_W.toFloat(), 40f); paint(); wc.restore()
                wc.save(); wc.translate(0f, -32f); wc.clipRect(0f, 72f, WINAMP_W.toFloat(), 106f); paint(); wc.restore()
            }
            // the window fills the whole widget edge to edge, no empty band around it; the skin pixels are enlarged without smoothing
            val cv = Canvas(out)
            cv.drawBitmap(win, null, Rect(0, 0, boxW, boxH), Paint().apply { isFilterBitmap = boxW < WINAMP_W })
            return out
        }

        /** The 275x116 window in its own coordinates: skin art first, then the live parts on top. */
        private fun paintWindow(cv: Canvas, skin: WinampSkin?, title: String, frac: Float, playing: Boolean, posSec: Long, shuffle: Boolean, repeat: Int, volume: Float) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            val main = skin?.main?.asAndroidBitmap()
            if (main != null) cv.drawBitmap(main, null, Rect(0, 0, WINAMP_W, WINAMP_H), p) else drawDefaultBg(cv, p)
            // active title bar, play/pause lamp, stereo lamps: the same cells WinampMain blits
            skin?.titlebar?.asAndroidBitmap()?.takeIf { it.width >= 27 + WINAMP_W && it.height >= 14 }?.let { cv.drawBitmap(it, Rect(27, 0, 27 + WINAMP_W, 14), Rect(0, 0, WINAMP_W, 14), p) }
            skin?.playpaus?.asAndroidBitmap()?.takeIf { it.width >= 27 && it.height >= 9 }?.let { cv.drawBitmap(it, Rect(if (playing) 0 else 18, 0, if (playing) 9 else 27, 9), Rect(26, 28, 35, 37), p) }
            skin?.monoster?.asAndroidBitmap()?.takeIf { it.width >= 56 && it.height >= 24 }?.let {
                cv.drawBitmap(it, Rect(29, 12, 56, 24), Rect(212, 41, 239, 53), p)
                cv.drawBitmap(it, Rect(0, 0, 29, 12), Rect(239, 41, 268, 53), p)
            }
            val btn = skin?.buttons?.asAndroidBitmap()
            WinampButton.entries.forEach { b ->
                if (btn != null) {
                    val down = (b == WinampButton.PLAY && playing) || (b == WinampButton.PAUSE && !playing && posSec > 0)
                    cv.drawBitmap(btn, Rect(b.sx, if (down) SPRITE_H else 0, b.sx + b.w, if (down) 2 * SPRITE_H else SPRITE_H), Rect(b.x, b.y, b.x + b.w, b.y + SPRITE_H), p)
                } else drawDefaultButton(cv, p, b, playing)
            }
            val vis = skin?.vis
            val fg = vis?.getOrNull(1)?.toArgb() ?: Color.rgb(0, 230, 0)
            val nums = skin?.numbers?.asAndroidBitmap()
            val txt = skin?.text?.asAndroidBitmap()
            val sp = Paint().apply { isFilterBitmap = false }
            // time: numbers.bmp digits (9x13) at the classic positions, else drawn text
            if (nums != null) {
                val mm = (posSec / 60).coerceAtMost(99); val ss = posSec % 60
                intArrayOf((mm / 10).toInt(), (mm % 10).toInt(), (ss / 10).toInt(), (ss % 10).toInt()).forEachIndexed { n, d ->
                    val x = intArrayOf(48, 60, 78, 90)[n]
                    cv.drawBitmap(nums, Rect(d * 9, 0, d * 9 + 9, 13), Rect(x, 26, x + 9, 39), sp)
                }
            } else {
                p.color = fg; p.typeface = Typeface.MONOSPACE; p.textSize = 9f
                cv.drawText("${(posSec / 60).toString().padStart(2, '0')}:${(posSec % 60).toString().padStart(2, '0')}", 50f, 36f, p)
            }
            // title: text.bmp (5x6) sprites, else drawn text
            p.color = Color.BLACK; cv.drawRect(111f, 24f, 265f, 36f, p)
            if (txt != null) {
                var x = 111
                title.uppercase().take(30).forEach { ch -> val (cx, cy) = textCell(ch); cv.drawBitmap(txt, Rect(cx * 5, cy * 6, cx * 5 + 5, cy * 6 + 6), Rect(x, 27, x + 5, 33), sp); x += 5 }
            } else {
                p.color = fg; p.typeface = Typeface.MONOSPACE; p.textSize = 9f
                cv.save(); cv.clipRect(111f, 24f, 265f, 36f); cv.drawText(title, 112f, 33f, p); cv.restore()
            }
            // shuffle (164,89) and repeat (211,89): shufrep.bmp rows off 0 / on 30, else a drawn lamp
            val sr = skin?.shufrep?.asAndroidBitmap()
            if (sr != null) {
                cv.drawBitmap(sr, Rect(28, if (shuffle) 30 else 0, 75, if (shuffle) 45 else 15), Rect(164, 89, 211, 104), sp)
                cv.drawBitmap(sr, Rect(0, if (repeat != 0) 30 else 0, 28, if (repeat != 0) 45 else 15), Rect(211, 89, 239, 104), sp)
            } else {
                p.textSize = 8f; p.typeface = Typeface.DEFAULT_BOLD
                p.color = if (shuffle) fg else Color.rgb(120, 120, 140); cv.drawText("SHUF", 170f, 100f, p)
                p.color = if (repeat != 0) fg else Color.rgb(120, 120, 140); cv.drawText(if (repeat == 1) "REP1" else "REP", 215f, 100f, p)
            }
            // seek bar (posbar slot): skin knob (posbar.bmp x=248, 29x10) over main.bmp's groove, else a drawn bar
            val pb = skin?.posbar?.asAndroidBitmap()
            if (pb != null && pb.width >= 277) {
                val kx = 16 + (219 * frac).toInt()
                cv.drawBitmap(pb, Rect(248, 0, 277, 10), Rect(kx, 72, kx + 29, 82), sp)
            } else {
                p.color = Color.rgb(24, 24, 32); cv.drawRect(16f, 72f, 264f, 82f, p)
                p.color = fg; cv.drawRect(16f, 74f, 16f + 248f * frac, 80f, p)
            }
            // volume: 28-frame groove (68x13 at 107,57) plus the knob, as in WinampMain
            skin?.volume?.asAndroidBitmap()?.takeIf { it.height >= 433 }?.let { vb ->
                val v = volume.coerceIn(0f, 1f)
                val fr = Math.round(v * 27) * 15
                cv.drawBitmap(vb, Rect(0, fr, 68, fr + 13), Rect(107, 57, 175, 70), sp)
                val kx = 107 + (v * (68 - 14)).toInt()
                cv.drawBitmap(vb, Rect(15, 422, 29, 433), Rect(kx, 58, kx + 14, 69), sp)
            }
        }

        /** Cell (column, row) of [ch] in text.bmp; unknown characters give the blank cell. */
        private fun textCell(ch: Char): Pair<Int, Int> {
            val r0 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\"@ "
            val r1 = "0123456789 .:()-'!_+\\/[]^&%.=$#"
            r0.indexOf(ch).takeIf { it >= 0 }?.let { return it to 0 }
            r1.indexOf(ch).takeIf { it >= 0 }?.let { return it to 1 }
            // Turkish letters and Ö: the same fold as the skin text (SkinDraw), not a blank
            return skinGlyph(ch) ?: (28 to 0)
        }

        private fun drawDefaultBg(cv: Canvas, p: Paint) {
            p.color = Color.rgb(58, 58, 74); cv.drawRect(0f, 0f, WINAMP_W.toFloat(), WINAMP_H.toFloat(), p)
            p.color = Color.rgb(34, 34, 48); cv.drawRect(0f, 0f, WINAMP_W.toFloat(), 14f, p)
            p.color = Color.rgb(160, 160, 190); p.textSize = 9f; p.typeface = Typeface.DEFAULT_BOLD; cv.drawText("WINAMP", 108f, 10f, p)
            p.color = Color.BLACK; cv.drawRect(10f, 20f, 100f, 62f, p)
        }

        private fun drawDefaultButton(cv: Canvas, p: Paint, b: WinampButton, playing: Boolean) {
            val on = (b == WinampButton.PLAY && playing)
            p.color = if (on) Color.rgb(90, 90, 120) else Color.rgb(78, 78, 98)
            cv.drawRect(b.x.toFloat(), b.y.toFloat(), (b.x + b.w - 1).toFloat(), (b.y + SPRITE_H - 1).toFloat(), p)
            p.color = Color.rgb(220, 220, 235)
            val cx = b.x + b.w / 2f; val cy = b.y + SPRITE_H / 2f
            when (b) {
                WinampButton.PLAY -> cv.drawPath(android.graphics.Path().apply { moveTo(cx - 3, cy - 4); lineTo(cx + 4, cy); lineTo(cx - 3, cy + 4); close() }, p)
                WinampButton.PAUSE -> { cv.drawRect(cx - 3, cy - 4, cx - 1, cy + 4, p); cv.drawRect(cx + 1, cy - 4, cx + 3, cy + 4, p) }
                WinampButton.STOP -> cv.drawRect(cx - 3, cy - 3, cx + 3, cy + 3, p)
                WinampButton.PREV -> { cv.drawRect(cx - 4, cy - 4, cx - 2, cy + 4, p); cv.drawPath(android.graphics.Path().apply { moveTo(cx + 4, cy - 4); lineTo(cx - 2, cy); lineTo(cx + 4, cy + 4); close() }, p) }
                WinampButton.NEXT -> { cv.drawRect(cx + 2, cy - 4, cx + 4, cy + 4, p); cv.drawPath(android.graphics.Path().apply { moveTo(cx - 4, cy - 4); lineTo(cx + 2, cy); lineTo(cx - 4, cy + 4); close() }, p) }
                WinampButton.EJECT -> cv.drawPath(android.graphics.Path().apply { moveTo(cx - 4, cy + 1); lineTo(cx, cy - 4); lineTo(cx + 4, cy + 1); close() }, p)
            }
        }
    }
}

/** The 4x1 variant of the Winamp widget: same receiver logic, compact strip layout (see widget_winamp_compact.xml). */
class WinampWidgetCompact : WinampWidget()

/** Where the window sits in a box: its scale, the top-left offset and the drawn size, all in box pixels. */
data class WinampFit(val scale: Float, val x: Int, val y: Int, val w: Int, val h: Int)
