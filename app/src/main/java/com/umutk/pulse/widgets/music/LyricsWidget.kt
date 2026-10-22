package com.umutk.pulse.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.lyrics.LiveLyrics
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.WidgetShape
import com.umutk.pulse.extras.music.poweramp.displayName
import com.umutk.pulse.tr

/** The current lyric line of the playing track, in the same look as the music widget (the app's look, or the music look's). */
class LyricsWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ids.forEach { m.updateAppWidget(it, build(c)) }
    }

    companion object {
        fun placed(c: Context): Boolean =
            AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, LyricsWidget::class.java)).isNotEmpty()

        /** Redraws every placed lyrics widget; called when the lyric line changes. */
        fun refresh(c: Context) {
            if (!placed(c)) return
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, LyricsWidget::class.java))
            if (ids.isNotEmpty()) m.partiallyUpdateAppWidget(ids, build(c))
        }

        fun build(c: Context): RemoteViews {
            val th = MusicWidget.lookTheme(c)
            val text = th?.text ?: com.umutk.pulse.widgets.music.WidgetUi.text(c)
            val sub = th?.sub ?: com.umutk.pulse.widgets.music.WidgetUi.sub(c)
            val track = MusicPlayerEngine.get(c).state.value.current
            val line = LiveLyrics.currentLine
            return RemoteViews(c.packageName, R.layout.widget_lyrics).apply {
                setTextViewText(R.id.lw_title, track?.displayName() ?: "Pulse")
                // bold through bold letters: a widget's font may have no bold weight of its own
                setTextViewText(R.id.lw_line, line?.let { LiveLyrics.bold(it) } ?: tr("No synced lyrics", "Senkron söz yok"))
                setTextColor(R.id.lw_title, sub)
                setTextColor(R.id.lw_line, text)
                if (th != null) {
                    setInt(R.id.lw_root, "setBackgroundResource", when (th.shape) {
                        WidgetShape.ROUND -> R.drawable.widget_music_bg
                        WidgetShape.SHARP -> R.drawable.widget_music_bg_sharp
                        WidgetShape.PILL -> R.drawable.widget_music_bg_pill
                        WidgetShape.BEVEL -> R.drawable.widget_music_bg_w95
                    })
                    setColorStateList(R.id.lw_root, "setBackgroundTintList", if (th.shape == WidgetShape.BEVEL) null else ColorStateList.valueOf(th.bg))
                }
                setOnClickPendingIntent(R.id.lw_root, WidgetUi.launch(c, "com.umutk.pulse.ExtraToolActivity", 81, "route" to "music"))
            }
        }
    }
}
