package com.umutk.pulse.extras.launcher

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Properties

/**
 * Notification counts for the app pictures. The notification listeners (they live in Pulse's main process) call [update]; the launcher (a process of its own)
 * reads the small file they write. Needs the notification access of one of Pulse's listeners to be on.
 */
object LauncherBadges {
    private const val FILE = "badges.properties"

    /** package -> number of notifications showing now; read by the launcher. */
    var counts by mutableStateOf<Map<String, Int>>(emptyMap())

    /** Called by a listener when a notification came, went, or the listener connected: counts what is showing and writes it. */
    fun update(svc: NotificationListenerService) {
        runCatching {
            val n = HashMap<String, Int>()
            svc.activeNotifications?.forEach { sbn ->
                if (sbn.isOngoing || sbn.packageName == svc.packageName) return@forEach
                if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return@forEach
                n[sbn.packageName] = (n[sbn.packageName] ?: 0) + 1
            }
            val p = Properties(); n.forEach { (k, v) -> p.setProperty(k, v.toString()) }
            val f = File(svc.filesDir, FILE); val tmp = File(f.path + ".tmp")
            tmp.outputStream().use { p.store(it, null) }; tmp.renameTo(f)
        }
    }

    /** Reads the file (launcher side). */
    fun read(c: Context) {
        val f = File(c.filesDir, FILE)
        val p = Properties()
        if (f.isFile) runCatching { f.inputStream().use { p.load(it) } }
        val next = p.stringPropertyNames().associateWith { p.getProperty(it).toIntOrNull() ?: 0 }.filterValues { it > 0 }
        if (next != counts) counts = next
    }
}
