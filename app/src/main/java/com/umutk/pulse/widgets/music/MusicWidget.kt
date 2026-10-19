package com.umutk.pulse.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.res.ColorStateList
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.asAndroidBitmap
import com.umutk.pulse.R
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.WidgetShape
import com.umutk.pulse.extras.music.musicWidgetTheme
import com.umutk.pulse.extras.music.trackArt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Music widget. This class is the adaptive one (2x1 art + play, 4x1 art, text and transport, 4x2 big art by resizing);
 * [MusicWidgetSquare] (2x2), [MusicWidgetBig] (4x3 with shuffle and repeat) and [MusicWidgetSlim] (4x1 without art) share the code.
 * Each placed widget has its own theme (see [MusicWidgetConfig]); without one it follows the system colours. Tapping the art opens the player.
 */
open class MusicWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { watch(c); ids.forEach { m.updateAppWidget(it, build(c, it)) } }

    override fun onEnabled(c: Context) = watch(c)

    override fun onDeleted(c: Context, ids: IntArray) { prefs(c).edit().apply { ids.forEach { remove("theme_$it") } }.apply() }

    override fun onReceive(c: Context, i: Intent) {
        val e = MusicPlayerEngine.get(c)
        when (i.action) {
            A_PREV -> e.previous()
            A_TOGGLE -> e.togglePlayPause()
            A_NEXT -> e.next()
            A_SHUF -> e.player.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
            A_REP -> e.player.let { it.repeatMode = when (it.repeatMode) { 0 -> 2; 2 -> 1; else -> 0 } }
            else -> return super.onReceive(c, i)
        }
        refresh(c)
    }

    companion object {
        const val A_PREV = "com.umutk.pulse.musicw.PREV"
        const val A_TOGGLE = "com.umutk.pulse.musicw.TOGGLE"
        const val A_NEXT = "com.umutk.pulse.musicw.NEXT"
        const val A_SHUF = "com.umutk.pulse.musicw.SHUF"
        const val A_REP = "com.umutk.pulse.musicw.REP"
        private var job: Job? = null

        /** "theme_<appWidgetId>" = theme id of [com.umutk.pulse.extras.music.musicWidgetThemes]; absent = system colours. */
        fun prefs(c: Context) = c.getSharedPreferences("music_widget", Context.MODE_PRIVATE)

        private val ALL = listOf(MusicWidget::class.java, MusicWidgetSquare::class.java, MusicWidgetBig::class.java, MusicWidgetSlim::class.java)

        /** Redraws on every playback state change while the process lives. */
        private fun watch(c: Context) {
            if (job?.isActive == true) return
            val app = c.applicationContext
            job = MusicPlayerEngine.get(app).state.drop(1).onEach { refresh(app) }.launchIn(CoroutineScope(Dispatchers.Main))
        }

        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            ALL.forEach { k -> m.getAppWidgetIds(ComponentName(c, k)).forEach { m.updateAppWidget(it, build(c, it)) } }
        }

        fun build(c: Context, id: Int): RemoteViews {
            val kind = AppWidgetManager.getInstance(c).getAppWidgetInfo(id)?.provider?.className
            val e = MusicPlayerEngine.get(c)
            val st = e.state.value
            val t = st.current
            val th = musicWidgetTheme(prefs(c).getString("theme_$id", null))
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val prog = if (dur > 0) (e.player.currentPosition * 1000 / dur).toInt().coerceIn(0, 1000) else 0
            // shortcut: art is read on the main thread (cached after the first read), move to IO if covers lag
            val art = t?.let { runCatching { trackArt(c, it)?.asAndroidBitmap() }.getOrNull() }?.let { WidgetUi.shrink(it, 240) }
            val text = th?.text ?: WidgetUi.text(c); val sub = th?.sub ?: WidgetUi.sub(c)
            val accent = th?.accent ?: WidgetUi.accent(c); val onAccent = th?.onAccent ?: WidgetUi.onAccent(c)
            fun v(layout: Int, compact: Boolean) = RemoteViews(c.packageName, layout).apply {
                setTextViewText(R.id.mw_title, t?.title ?: t?.path?.substringAfterLast('/') ?: "Pulse Music")
                setTextViewText(R.id.mw_artist, t?.artist ?: "")
                setTextColor(R.id.mw_title, text); setTextColor(R.id.mw_artist, sub)
                if (th != null) {
                    setInt(R.id.mw_root, "setBackgroundResource", when (th.shape) { WidgetShape.ROUND -> R.drawable.widget_music_bg; WidgetShape.SHARP -> R.drawable.widget_music_bg_sharp; WidgetShape.PILL -> R.drawable.widget_music_bg_pill; WidgetShape.BEVEL -> R.drawable.widget_music_bg_w95 })
                    setColorStateList(R.id.mw_root, "setBackgroundTintList", if (th.shape == WidgetShape.BEVEL) null else ColorStateList.valueOf(th.bg))
                    setColorStateList(R.id.mw_play, "setBackgroundTintList", ColorStateList.valueOf(th.accent))
                    if (layout != R.layout.widget_music_slim) setColorStateList(R.id.mw_art, "setBackgroundTintList", ColorStateList.valueOf(th.accent and 0x00FFFFFF or 0x55000000))
                }
                setProgressBar(R.id.mw_prog, 1000, prog, false)
                setColorStateList(R.id.mw_prog, "setProgressTintList", ColorStateList.valueOf(accent))
                setColorStateList(R.id.mw_prog, "setProgressBackgroundTintList", ColorStateList.valueOf(sub and 0x55FFFFFF))
                if (layout != R.layout.widget_music_slim && art != null) { setImageViewBitmap(R.id.mw_art, art); setViewPadding(R.id.mw_art, 0, 0, 0, 0) }
                setImageViewResource(R.id.mw_play, if (st.isPlaying) R.drawable.widget_music_pause else R.drawable.widget_music_play)
                setInt(R.id.mw_play, "setColorFilter", onAccent)
                setInt(R.id.mw_prev, "setColorFilter", text); setInt(R.id.mw_next, "setColorFilter", text)
                val vis = if (compact) View.GONE else View.VISIBLE
                setViewVisibility(R.id.mw_prev, vis); setViewVisibility(R.id.mw_next, vis)
                if (compact) { setViewVisibility(R.id.mw_prog, View.GONE); setViewVisibility(R.id.mw_artist, View.GONE) }
                setOnClickPendingIntent(R.id.mw_prev, WidgetUi.broadcast(c, MusicWidget::class.java, A_PREV))
                setOnClickPendingIntent(R.id.mw_play, WidgetUi.broadcast(c, MusicWidget::class.java, A_TOGGLE))
                setOnClickPendingIntent(R.id.mw_next, WidgetUi.broadcast(c, MusicWidget::class.java, A_NEXT))
                if (layout == R.layout.widget_music_big) {
                    val sh = e.player.shuffleModeEnabled; val rp = e.player.repeatMode != 0
                    setInt(R.id.mw_shuf, "setColorFilter", if (sh) accent else sub); setInt(R.id.mw_rep, "setColorFilter", if (rp) accent else sub)
                    setOnClickPendingIntent(R.id.mw_shuf, WidgetUi.broadcast(c, MusicWidget::class.java, A_SHUF))
                    setOnClickPendingIntent(R.id.mw_rep, WidgetUi.broadcast(c, MusicWidget::class.java, A_REP))
                }
                val open = WidgetUi.launch(c, "com.umutk.pulse.MusicLauncher", 71)
                setOnClickPendingIntent(if (layout == R.layout.widget_music_slim) R.id.mw_title else R.id.mw_art, open)
            }
            return when (kind) {
                MusicWidgetSquare::class.java.name -> v(R.layout.widget_music_square, false)
                MusicWidgetBig::class.java.name -> v(R.layout.widget_music_big, false)
                MusicWidgetSlim::class.java.name -> v(R.layout.widget_music_slim, false)
                else -> RemoteViews(mapOf(
                    SizeF(110f, 40f) to v(R.layout.widget_music_row, true),
                    SizeF(250f, 40f) to v(R.layout.widget_music_row, false),
                    SizeF(250f, 110f) to v(R.layout.widget_music_tall, false),
                ))
            }
        }
    }
}

/** 2x2: big cover, title, thin progress, transport. */
class MusicWidgetSquare : MusicWidget()

/** 4x3: large cover, big title, progress and a transport row with shuffle and repeat. */
class MusicWidgetBig : MusicWidget()

/** 4x1 strip without a cover: text, progress and transport. */
class MusicWidgetSlim : MusicWidget()
