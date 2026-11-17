package com.umutk.pulse

import android.app.NotificationChannel

/** A notification channel that never puts a badge (dot or count) on the app's icon: Pulse's notifications are status, not unread mail. */
@androidx.annotation.RequiresApi(26)
fun QuietChannel(id: String, name: CharSequence, importance: Int): NotificationChannel =
    NotificationChannel(id, name, importance).apply { setShowBadge(false) }

/**
 * The media card of a MediaSessionService with no badge: Media3's own channel ("default_channel_id") puts a dot on the app's icon, and Pulse has many icons, so
 * every one of them wore it. The channel is made here first (Media3 only makes its own when none exists) and the old one is removed.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun android.app.Service.quietMediaCard(): androidx.media3.session.DefaultMediaNotificationProvider {
    val nm = getSystemService(android.app.NotificationManager::class.java)
    if (android.os.Build.VERSION.SDK_INT >= 26) {
        runCatching { nm.deleteNotificationChannel("default_channel_id") }
        nm.createNotificationChannel(QuietChannel("pulse_media_quiet", tr("Media playback", "Medya oynatma"), android.app.NotificationManager.IMPORTANCE_LOW))
    }
    return androidx.media3.session.DefaultMediaNotificationProvider.Builder(this).setChannelId("pulse_media_quiet").setChannelName(com.umutk.pulse.R.string.app_name).build()
}
