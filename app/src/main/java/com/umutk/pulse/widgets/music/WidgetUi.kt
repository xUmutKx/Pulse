package com.umutk.pulse.widgets.music

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color

/** Colours and intents shared by the music, video and noise widgets (light and dark follow the system). */
internal object WidgetUi {
    fun dark(c: Context) = c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    fun text(c: Context) = if (dark(c)) Color.parseColor("#E6E0E9") else Color.parseColor("#1D1B20")
    fun sub(c: Context) = if (dark(c)) Color.parseColor("#CAC4D0") else Color.parseColor("#49454F")
    fun accent(c: Context) = if (dark(c)) Color.parseColor("#D0BCFF") else Color.parseColor("#6750A4")
    /** Icon colour on the accent circle. */
    fun onAccent(c: Context) = if (dark(c)) Color.parseColor("#381E72") else Color.WHITE

    fun launch(c: Context, cls: String, code: Int, vararg extra: Pair<String, String>): PendingIntent =
        PendingIntent.getActivity(
            c, code, Intent().setClassName(c, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION).also { i -> extra.forEach { i.putExtra(it.first, it.second) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun broadcast(c: Context, receiver: Class<*>, action: String, code: Int = action.hashCode(), extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(c, code, Intent(action).setComponent(ComponentName(c, receiver)).apply(extras), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** Keeps RemoteViews bitmaps small (binder limit). */
    fun shrink(b: Bitmap, max: Int): Bitmap {
        val s = max.toFloat() / maxOf(b.width, b.height)
        return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }
}
