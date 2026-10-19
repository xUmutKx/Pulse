package com.umutk.pulse.widgets.music

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Size
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.video.VideoWidgetLooks
import com.umutk.pulse.extras.video.clock

/** Read-only view of the last watched video. "last_uri" and "last_dur" are written by VideoPrefs.setResume (see needs-widgets-more.md); the position is its "r_<hash>" entry. */
class VideoLast(val uri: String, val durMs: Long, val posMs: Long) {
    companion object {
        fun read(c: Context): VideoLast? {
            val sp = c.getSharedPreferences("video_player", Context.MODE_PRIVATE)
            val u = sp.getString("last_uri", null) ?: return null
            return VideoLast(u, sp.getLong("last_dur", 0L), sp.getLong("r_" + u.hashCode(), 0L))
        }
    }
}

/** Last watched video with thumbnail and title; resume button opens the video player, the grid button its library. */
class VideoWidget : AppWidgetProvider() {

    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { m.updateAppWidget(it, build(c, it)) } }

    companion object {
        /** Call after a video stops to redraw (the manifest-declared widget also refreshes on its update period). */
        fun refresh(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            m.getAppWidgetIds(ComponentName(c, VideoWidget::class.java)).forEach { m.updateAppWidget(it, build(c, it)) }
        }

        fun lookPrefs(c: Context) = c.getSharedPreferences("video_widget", Context.MODE_PRIVATE)

        fun build(c: Context, id: Int = 0): RemoteViews {
            val look = VideoWidgetLooks[lookPrefs(c).getInt("look_$id", 0).coerceIn(VideoWidgetLooks.indices)]
            val p = look.pal
            val last = VideoLast.read(c)
            val uri = last?.let { runCatching { Uri.parse(it.uri) }.getOrNull() }
            val name = uri?.let {
                runCatching { c.contentResolver.query(it, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { q -> if (q.moveToFirst()) q.getString(0) else null } }.getOrNull()
            } ?: uri?.lastPathSegment
            // shortcut: thumbnail loaded on the calling thread (small, system-cached); move off main if it lags
            val thumb = uri?.let { runCatching { c.contentResolver.loadThumbnail(it, Size(320, 180), null) }.getOrNull() }
            return RemoteViews(c.packageName, R.layout.widget_video_main).apply {
                setTextViewText(R.id.vw_title, name ?: "Pulse Video")
                setTextViewText(R.id.vw_time, if (last != null && last.durMs > 0) "${clock(last.posMs)} / ${clock(last.durMs)}" else "")
                setInt(R.id.vw_root, "setBackgroundResource", if (look.square) R.drawable.widget_video_bg_sq else R.drawable.widget_video_bg_round)
                setColorStateList(R.id.vw_root, "setBackgroundTintList", ColorStateList.valueOf(p.bg.toInt()))
                setColorStateList(R.id.vw_resume, "setBackgroundTintList", ColorStateList.valueOf(p.accent.toInt()))
                setTextColor(R.id.vw_title, p.fg.toInt()); setTextColor(R.id.vw_time, p.sub.toInt())
                if (thumb != null) setImageViewBitmap(R.id.vw_thumb, WidgetUi.shrink(thumb, 320))
                val prog = if (last != null && last.durMs > 0) (last.posMs * 1000 / last.durMs).toInt().coerceIn(0, 1000) else 0
                setProgressBar(R.id.vw_prog, 1000, prog, false)
                setColorStateList(R.id.vw_prog, "setProgressTintList", ColorStateList.valueOf(p.accent.toInt()))
                setInt(R.id.vw_resume, "setColorFilter", if (p.light) android.graphics.Color.WHITE else p.bg.toInt()); setInt(R.id.vw_lib, "setColorFilter", p.fg.toInt())
                setOnClickPendingIntent(R.id.vw_resume, if (last != null) WidgetUi.launch(c, "com.umutk.pulse.VideoLauncher", 81, "resume_video" to last.uri) else WidgetUi.launch(c, "com.umutk.pulse.VideoLauncher", 82))
                setOnClickPendingIntent(R.id.vw_thumb, WidgetUi.launch(c, "com.umutk.pulse.VideoLauncher", 82))
                setOnClickPendingIntent(R.id.vw_lib, WidgetUi.launch(c, "com.umutk.pulse.VideoLauncher", 82))
            }
        }
    }
}
