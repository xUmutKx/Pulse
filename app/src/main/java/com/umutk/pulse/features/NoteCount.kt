package com.umutk.pulse.features

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Counts the notifications posted today (not Pulse's own, not ongoing ones). Counts from the moment access is switched on. */
class NoteCounter : NotificationListenerService() {
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = com.umutk.pulse.extras.launcher.LauncherBadges.update(this)
    override fun onListenerConnected() = com.umutk.pulse.extras.launcher.LauncherBadges.update(this)
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        com.umutk.pulse.extras.launcher.LauncherBadges.update(this)
        if (sbn == null || sbn.packageName == packageName || sbn.isOngoing) return
        val p = getSharedPreferences(NoteCount.PREFS, Context.MODE_PRIVATE)
        val k = NoteCount.key()
        p.edit().putInt(k, p.getInt(k, 0) + 1).apply()
    }
}

object NoteCount {
    const val PREFS = "notes"
    fun key(): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
    fun today(ctx: Context): Int = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(), 0)
    fun enabled(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
}
