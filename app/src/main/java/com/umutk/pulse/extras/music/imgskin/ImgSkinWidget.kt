package com.umutk.pulse.extras.music.imgskin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.artistFor
import com.umutk.pulse.extras.music.poweramp.displayName
import com.umutk.pulse.extras.music.trackArt
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A picture skin as a home-screen widget: the skin drawn whole, fitted into the widget (never cut), with transparent keys over the buttons.
 * One class per skin, so each skin has its own entry in the widget list.
 */
abstract class ImgSkinWidget(private val index: Int) : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { loop.start(c); ids.forEach { m.updateAppWidget(it, build(c, it, index)) } }
    override fun onEnabled(c: Context) = loop.start(c)
    override fun onAppWidgetOptionsChanged(c: Context, m: AppWidgetManager, id: Int, o: Bundle) { m.updateAppWidget(id, build(c, id, index)) }
    override fun onReceive(c: Context, i: Intent) {
        val e = MusicPlayerEngine.ready(c)
        when (i.action) {
            A_PREV -> e.previous(); A_TOGGLE -> e.togglePlayPause(); A_NEXT -> e.next()
            A_STOP -> { e.pause(); e.seekTo(0) }
            else -> return super.onReceive(c, i)
        }
        refreshAll(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.imgskin.PREV"
        const val A_TOGGLE = "com.umutk.pulse.imgskin.TOGGLE"
        const val A_NEXT = "com.umutk.pulse.imgskin.NEXT"
        const val A_STOP = "com.umutk.pulse.imgskin.STOP"
        private val WIDGETS = listOf(WmsBlobWidget::class.java, WmsWin7Widget::class.java, WmsCandyWidget::class.java, WmsSilverWidget::class.java, WmsLunaWidget::class.java)
        private var shrink = 1f
        private val loop = com.umutk.pulse.extras.music.widgets.LiveLoop(1000) { refreshAll(it) }

        fun refreshAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            for (k in ImgSkins.all.indices) m.getAppWidgetIds(ComponentName(c, WIDGETS[k])).forEach { m.updateAppWidget(it, build(c, it, k)) }
        }

        private fun box(c: Context, id: Int): Pair<Int, Int> {
            val o = AppWidgetManager.getInstance(c).getAppWidgetOptions(id)
            val d = c.resources.displayMetrics.density
            val portrait = c.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            val w = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0)).takeIf { it > 0 } ?: 250
            val h = (if (portrait) o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0) else o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)).takeIf { it > 0 } ?: 120
            // a widget picture travels as 4 bytes a pixel and must stay under about 1 MB
            val k = sqrt(w * d * h * d / 230_000f).coerceAtLeast(1f)
            shrink = k
            return (w * d / k).toInt().coerceAtLeast(1) to (h * d / k).toInt().coerceAtLeast(1)
        }

        private fun pi(c: Context, a: String, cls: Class<*>) =
            PendingIntent.getBroadcast(c, a.hashCode() + cls.hashCode(), Intent(a).setComponent(ComponentName(c, cls)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        fun build(c: Context, id: Int, index: Int): RemoteViews {
            val skin = ImgSkins.all[index]
            val cls = WIDGETS[index]
            val (bw, bh) = box(c, id)
            val e = MusicPlayerEngine.ready(c)
            val st = e.state.value
            val art = st.current?.let { runCatching { trackArt(c, it) }.getOrNull() }?.asAndroidBitmap()
            val state = SkinState(st.current?.displayName().orEmpty(), st.current?.artistFor(c).orEmpty(), st.isPlaying, e.positionMs.toLong(), e.durationMs.toLong(), art)
            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val k = min(bw / skin.w.toFloat(), bh / skin.h.toFloat())
            val ox = (bw - skin.w * k) / 2f; val oy = (bh - skin.h * k) / 2f
            Canvas(bmp).apply { translate(ox, oy); scale(k, k); ImgSkins.paint(this, c, skin, state) }
            val sh = shrink
            return RemoteViews(c.packageName, R.layout.widget_imgskin).apply {
                setImageViewBitmap(R.id.is_img, bmp)
                setOnClickPendingIntent(R.id.is_img, com.umutk.pulse.widgets.music.WidgetUi.launch(c, "com.umutk.pulse.ToolOpenActivity", 73 + index, "route" to "music"))
                fun key(container: Int, child: Int, hitId: String, action: String) {
                    val r = skin.hits.firstOrNull { it.id == hitId }?.r
                    // a skin without that key gets a key of no size
                    if (r == null) setViewPadding(container, 0, 0, bw * 4, bh * 4)
                    else setViewPadding(container, ((ox + r.left * k) * sh).toInt(), ((oy + r.top * k) * sh).toInt(), ((bw - ox - r.right * k) * sh).toInt(), ((bh - oy - r.bottom * k) * sh).toInt())
                    setOnClickPendingIntent(child, pi(c, action, cls))
                }
                key(R.id.is_c_prev, R.id.is_prev, "prev", A_PREV)
                key(R.id.is_c_play, R.id.is_play, "play", A_TOGGLE)
                key(R.id.is_c_next, R.id.is_next, "next", A_NEXT)
                key(R.id.is_c_stop, R.id.is_stop, "stop", A_STOP)
            }
        }
    }
}

class WmsBlobWidget : ImgSkinWidget(0)
class WmsWin7Widget : ImgSkinWidget(1)
class WmsCandyWidget : ImgSkinWidget(2)
class WmsSilverWidget : ImgSkinWidget(3)
class WmsLunaWidget : ImgSkinWidget(4)
