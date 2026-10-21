package com.umutk.pulse.widgets.music

import com.umutk.pulse.extras.music.shownArtist

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
import com.umutk.pulse.extras.music.MusicWidgetTheme
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

        /**
         * One design per music look (MusicHub's "look" pref); used when the widget has no theme of its own.
         * "" (never picked) follows the Pulse app's Windows look; "MATERIAL" and "LYRICS" use the system colours.
         */
        fun lookTheme(c: Context): MusicWidgetTheme? = when (c.getSharedPreferences("music_hub", Context.MODE_PRIVATE).getString("look", "")) {
            "POWERAMP" -> MusicWidgetTheme("look_poweramp", "Poweramp", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFB0B0B0.toInt(), 0xFFFF8A00.toInt(), 0xFF000000.toInt(), WidgetShape.SHARP, true)
            "WINAMP" -> MusicWidgetTheme("look_winamp", "Winamp", 0xFF3A3A4A.toInt(), 0xFF00E600.toInt(), 0xFF9AD79A.toInt(), 0xFF00E600.toInt(), 0xFF000000.toInt(), WidgetShape.SHARP, true)
            "WMP" -> MusicWidgetTheme("look_wmp", "Windows Media Player", 0xFF1F4E9E.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFE0FA.toInt(), 0xFF8FC1FF.toInt(), 0xFF0B2350.toInt(), WidgetShape.ROUND, true)
            "", "MATERIAL", "LYRICS" -> appLookTheme(c)
            else -> null
        }

        /** The Pulse app's Windows look for the music widget (small palette, existing shapes, no bitmaps); null for Material, glass and the rest. */
        private fun appLookTheme(c: Context): MusicWidgetTheme? = when (com.umutk.pulse.ownPrefs(c, "pulse_settings").getString("theme", "win11")) {
            "win95" -> musicWidgetTheme("win95")
            "xp" -> MusicWidgetTheme("look_xp", "Windows XP", 0xFFECE9D8.toInt(), 0xFF000000.toInt(), 0xFF3C3C3C.toInt(), 0xFF0A5AE0.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.SHARP, false)
            "win7" -> MusicWidgetTheme("look_win7", "Windows 7", 0xFFDCE9F7.toInt(), 0xFF000000.toInt(), 0xFF444444.toInt(), 0xFF3399FF.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.ROUND, false)
            "win10" -> MusicWidgetTheme("look_win10", "Windows 10", 0xFF0078D7.toInt(), 0xFFFFFFFF.toInt(), 0xFFCFE8FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF0078D7.toInt(), WidgetShape.SHARP, true)
            "win11" -> MusicWidgetTheme("look_win11", "Windows 11", 0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFFC8C8C8.toInt(), 0xFF60CDFF.toInt(), 0xFF000000.toInt(), WidgetShape.ROUND, true)
            "amoled" -> MusicWidgetTheme("look_amoled", "AMOLED", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF9E9E9E.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), WidgetShape.ROUND, true)
            // the other Pulse looks: each keeps its own colours and corners, so every theme has a music widget
            "material" -> MusicWidgetTheme("look_material", "Material", 0xFFF3EDF7.toInt(), 0xFF1D1B20.toInt(), 0xFF49454F.toInt(), 0xFF6750A4.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.ROUND, false)
            "oneui" -> MusicWidgetTheme("look_oneui", "One UI", 0xFFF4F4F6.toInt(), 0xFF111111.toInt(), 0xFF707070.toInt(), 0xFF3E7BFA.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.ROUND, false)
            "coloros" -> MusicWidgetTheme("look_coloros", "ColorOS", 0xFFF2F4F7.toInt(), 0xFF1A1A1A.toInt(), 0xFF6B7280.toInt(), 0xFF1FB86A.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.ROUND, false)
            "aurora" -> MusicWidgetTheme("look_aurora", "Aurora", 0xFF0F2A2E.toInt(), 0xFFEFFFFA.toInt(), 0xFF9FD8CF.toInt(), 0xFF5EEAD4.toInt(), 0xFF042F2E.toInt(), WidgetShape.PILL, true)
            "sakura" -> MusicWidgetTheme("look_sakura", "Sakura", 0xFFFCE7F0.toInt(), 0xFF4A1530.toInt(), 0xFF8A4A66.toInt(), 0xFFE75C8F.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.PILL, false)
            "ocean" -> MusicWidgetTheme("look_ocean", "Ocean", 0xFF0B2545.toInt(), 0xFFEAF2FF.toInt(), 0xFF9DB7D9.toInt(), 0xFF38BDF8.toInt(), 0xFF04243A.toInt(), WidgetShape.ROUND, true)
            "ember" -> MusicWidgetTheme("look_ember", "Ember", 0xFF2A1208.toInt(), 0xFFFFEDE0.toInt(), 0xFFD9A38A.toInt(), 0xFFFF7A3D.toInt(), 0xFF2A1208.toInt(), WidgetShape.SHARP, true)
            "frost" -> MusicWidgetTheme("look_frost", "Frost", 0xFFEEF3F8.toInt(), 0xFF1B2A3A.toInt(), 0xFF5A6B7E.toInt(), 0xFF4C8DFF.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.ROUND, false)
            else -> null
        }

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
            val look = lookTheme(c)
            val th = musicWidgetTheme(prefs(c).getString("theme_$id", null)) ?: look
            val dur = e.player.duration.takeIf { it > 0 } ?: 0L
            val prog = if (dur > 0) (e.player.currentPosition * 1000 / dur).toInt().coerceIn(0, 1000) else 0
            // shortcut: art is read on the main thread (cached after the first read), move to IO if covers lag
            val art = t?.let { runCatching { trackArt(c, it)?.asAndroidBitmap() }.getOrNull() }?.let { WidgetUi.shrink(it, 240) }
            val text = th?.text ?: WidgetUi.text(c); val sub = th?.sub ?: WidgetUi.sub(c)
            val accent = th?.accent ?: WidgetUi.accent(c); val onAccent = th?.onAccent ?: WidgetUi.onAccent(c)
            fun v(layout: Int, compact: Boolean) = RemoteViews(c.packageName, layout).apply {
                setTextViewText(R.id.mw_title, t?.title ?: t?.path?.substringAfterLast('/') ?: look?.name ?: "Pulse Music")
                setTextViewText(R.id.mw_artist, t?.shownArtist() ?: "")
                setTextColor(R.id.mw_title, text); setTextColor(R.id.mw_artist, sub)
                if (th != null) {
                    setInt(R.id.mw_root, "setBackgroundResource", when (th.shape) { WidgetShape.ROUND -> R.drawable.widget_music_bg; WidgetShape.SHARP -> R.drawable.widget_music_bg_sharp; WidgetShape.PILL -> R.drawable.widget_music_bg_pill; WidgetShape.BEVEL -> R.drawable.widget_music_bg_w95 })
                    setColorStateList(R.id.mw_root, "setBackgroundTintList", if (th.shape == WidgetShape.BEVEL) null else ColorStateList.valueOf(th.bg))
                    setColorStateList(R.id.mw_play, "setBackgroundTintList", ColorStateList.valueOf(th.accent))
                    if (layout != R.layout.widget_music_slim) setColorStateList(R.id.mw_art, "setBackgroundTintList", ColorStateList.valueOf(th.accent and 0x00FFFFFF or 0x55000000))
                }
                setProgressBar(R.id.mw_prog, 1000, prog, false)
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
                val open = WidgetUi.launch(c, "com.umutk.pulse.ExtraToolActivity", 71, "route" to "music")
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
