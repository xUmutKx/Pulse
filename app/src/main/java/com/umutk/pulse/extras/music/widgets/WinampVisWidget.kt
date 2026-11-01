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
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextPaint
import android.text.TextUtils
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicLevels
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.skin.DefaultVis
import com.umutk.pulse.extras.music.skin.installedSkins
import com.umutk.pulse.extras.music.skin.loadWinampSkin
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

/**
 * Winamp's visualizer window as a home-screen widget: the title row over 19 bars drawn in the classic
 * green, yellow and red segments. The bars follow the engine's live levels; the whole window is fitted to the box.
 */
class WinampVisWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { loop.start(c); ids.forEach { m.updateAppWidget(it, build(c, it)) } }

    override fun onEnabled(c: Context) = loop.start(c)

    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) { m.updateAppWidget(id, build(c, id)) }

    override fun onReceive(c: Context, i: Intent) {
        when (i.action) {
            A_TOGGLE -> MusicPlayerEngine.ready(c).togglePlayPause()
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_TOGGLE = "com.umutk.pulse.winampvis.TOGGLE"

        /** The window in its own units. */
        const val W = 300
        const val H = 130
        private const val BARS = 19
        private const val SEG_H = 4f
        private const val SEG_GAP = 1f
        private const val AREA_TOP = 22f
        private const val AREA_BOTTOM = 122f
        private const val SIDE = 12f

        private const val BG_DEFAULT = 0xFF0B0B0B.toInt()

        /** A 300 ms redraw is smooth enough for the bars and keeps the widget's bitmap updates light. */
        private val loop = LiveLoop(300) { refresh(it) }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, WinampVisWidget::class.java)).forEach { m.updateAppWidget(it, build(c, it)) }
        }

        private fun pi(c: Context, a: String) =
            PendingIntent.getBroadcast(c, a.hashCode(), Intent(a).setComponent(ComponentName(c, WinampVisWidget::class.java)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun build(c: Context, id: Int): RemoteViews {
            val (bw, bh) = boxPx(c, id)
            val e = MusicPlayerEngine.ready(c)
            val st = e.state.value
            val title = st.current?.let { it.title ?: it.path.substringAfterLast('/') } ?: ""
            val bmp = render(MusicLevels.snapshot(), title, bw, bh, skinVis(c))
            return RemoteViews(c.packageName, R.layout.widget_winamp_vis).apply {
                setImageViewBitmap(R.id.wv_img, bmp)
                setOnClickPendingIntent(R.id.wv_img, pi(c, A_TOGGLE))
            }
        }

        /** Same box rule as the Poweramp widget: portrait uses the narrow width and the tall height. */
        private fun boxPx(c: Context, id: Int): Pair<Int, Int> {
            val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val portrait = c.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0))
                .takeIf { it > 0 } ?: 250
            val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0))
                .takeIf { it > 0 } ?: 110
            val pw = w * d; val ph = h * d
            // RemoteViews bitmaps are limited by the binder: about 350k pixels of RGB_565 at most
            val shrink = kotlin.math.sqrt(pw * ph / 900_000f).coerceAtLeast(1f)
            return (pw / shrink).toInt().coerceAtLeast(1) to (ph / shrink).toInt().coerceAtLeast(1)
        }

        /**
         * The visualizer window at the box size, letterboxed on black. [levels] are bar heights 0..1 (any count; resampled
         * to 19 bars). The picker passes a sample curve.
         */
        fun render(levels: List<Float>, title: String, bw: Int, bh: Int, vis: List<Int> = skinVisDefault()): Bitmap {
            val out = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            // the window fills the whole widget edge to edge (stretched on its two axes), no black band around it
            cv.scale(bw / W.toFloat(), bh / H.toFloat())
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = vis[0]; cv.drawRect(0f, 0f, W.toFloat(), H.toFloat(), p)

            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = vis[18]; textSize = 12f; typeface = Typeface.DEFAULT_BOLD }
            cv.drawText(TextUtils.ellipsize(title, tp, W - 2 * SIDE, TextUtils.TruncateAt.END).toString(), SIDE, 14f, tp)

            val area = AREA_BOTTOM - AREA_TOP
            val gap = 4f
            val barW = (W - 2 * SIDE - gap * (BARS - 1)) / BARS
            val segs = ((area + SEG_GAP) / (SEG_H + SEG_GAP)).toInt()
            for (i in 0 until BARS) {
                val v = levelAt(levels, i)
                val lit = (v * segs).toInt().coerceIn(0, segs)
                val x = SIDE + i * (barW + gap)
                for (k in 0 until lit) {
                    val frac = (k + 1f) / segs
                    // the skin's own bar colours: viscolor rows 2 (top, hottest) to 17 (bottom), the segment's height picks the row
                    p.color = vis[(2 + ((1f - frac) * 15f).roundToInt()).coerceIn(2, 17)]
                    val top = AREA_BOTTOM - (k + 1) * (SEG_H + SEG_GAP) + SEG_GAP
                    cv.drawRect(x, top, x + barW, top + SEG_H, p)
                }
            }
            return out
        }

        private var visName = ""
        private var visCache: List<Int> = emptyList()

        /** The bar colours of the Winamp skin picked in the Winamp screen (read once per skin, not on every redraw). */
        fun skinVis(c: Context): List<Int> {
            val name = c.getSharedPreferences("winamp_look", Context.MODE_PRIVATE).getString("skin", "")?.ifEmpty { "base-2.91.wsz" } ?: "base-2.91.wsz"
            if (name == visName && visCache.isNotEmpty()) return visCache
            val file = runCatching { installedSkins(c).firstOrNull { it.file.name == name }?.file }.getOrNull()
            val colours = file?.let { runCatching { loadWinampSkin(it) }.getOrNull() }?.vis?.map { it.toArgb() } ?: skinVisDefault()
            visName = name; visCache = colours
            return colours
        }

        /** The classic base skin's bars when no skin can be read: the green, yellow and red of the first Winamp widgets. */
        fun skinVisDefault(): List<Int> = DefaultVis.map { it.toArgb() }

        private fun levelAt(levels: List<Float>, bar: Int): Float {
            if (levels.isEmpty()) return 0f
            return levels[(bar * levels.size / BARS).coerceIn(0, levels.size - 1)].coerceIn(0f, 1f)
        }
    }
}
