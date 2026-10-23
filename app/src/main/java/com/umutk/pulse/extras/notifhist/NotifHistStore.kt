package com.umutk.pulse.extras.notifhist

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class NotifHistEntry(val pkg: String, val app: String, val title: String, val text: String, val time: Long)

/** Saved notification list: newest first, capped at [MAX], one JSON file in app files. Disk work runs on [io], in order. */
object NotifHist {
    private const val FILE = "notif_history.json"
    private const val MAX = 500
    private const val MAX_TEXT = 300

    // one thread, so writes never overlap and the file always ends with the newest list
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val _entries = MutableStateFlow<List<NotifHistEntry>>(emptyList())
    val entries: StateFlow<List<NotifHistEntry>> = _entries.asStateFlow()
    private var loaded = false // touched only on [io]

    /** Off by default: nothing is saved until the user turns it on in Extra apps. */
    fun enabled(ctx: Context): Boolean = ctx.getSharedPreferences("notifhist", Context.MODE_PRIVATE).getBoolean("on", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences("notifhist", Context.MODE_PRIVATE).edit().putBoolean("on", on).apply()
        // a listener that was granted while the app was not running may stay unbound; ask the system to bind it again
        if (on && hasAccess(ctx)) runCatching { NotificationListenerService.requestRebind(ComponentName(ctx, NotifHistListener::class.java)) }
    }

    /** True when the user has allowed Pulse to read notifications (Settings > Notification access). */
    fun hasAccess(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Reads the saved list once, in the background. Every job calls it first, so nothing runs before the load. */
    fun load(ctx: Context) = io.launch { ensureLoaded(ctx.applicationContext) }

    /** Called by the listener on the main thread. Skips Pulse's own and ongoing (media, progress) notifications. */
    fun onPosted(ctx: Context, sbn: StatusBarNotification) {
        val app = ctx.applicationContext
        if (!enabled(app) || sbn.packageName == app.packageName) return
        if ((sbn.notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return
        val label = try {
            app.packageManager.getApplicationLabel(app.packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            sbn.packageName
        }
        val entry = NotifHistEntry(sbn.packageName, label, title.take(MAX_TEXT), text.take(MAX_TEXT), sbn.postTime)
        io.launch {
            ensureLoaded(app)
            val list = (listOf(entry) + _entries.value).take(MAX)
            _entries.value = list
            save(app, list)
        }
    }

    fun clear(ctx: Context) = io.launch {
        val app = ctx.applicationContext
        ensureLoaded(app)
        _entries.value = emptyList()
        save(app, emptyList())
    }

    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = File(ctx.filesDir, FILE)
        // a damaged file is treated as empty; the next save overwrites it
        _entries.value = try { if (f.exists()) parse(f.readText()) else emptyList() } catch (e: Exception) { emptyList() }
    }

    private fun save(ctx: Context, list: List<NotifHistEntry>) {
        try {
            val f = File(ctx.filesDir, FILE)
            val tmp = File(ctx.filesDir, "$FILE.tmp")
            tmp.writeText(JSONArray(list.map { toJson(it) }).toString())
            tmp.renameTo(f) // rename replaces the old file, so a crash mid-write keeps the previous list
        } catch (e: Exception) {
            // disk full or read-only: keep the in-memory list, the next notification tries again
        }
    }

    private fun toJson(e: NotifHistEntry): JSONObject = JSONObject()
        .put("pkg", e.pkg).put("app", e.app).put("title", e.title).put("text", e.text).put("time", e.time)

    private fun parse(s: String): List<NotifHistEntry> {
        val a = JSONArray(s)
        return List(a.length()) { i ->
            val o = a.getJSONObject(i)
            NotifHistEntry(o.optString("pkg"), o.optString("app"), o.optString("title"), o.optString("text"), o.optLong("time"))
        }
    }
}

/** Declared in the manifest. Receives every posted notification; the logic lives in [NotifHist]. */
class NotifHistListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) { NotifHist.onPosted(this, sbn); com.umutk.pulse.extras.launcher.LauncherBadges.update(this) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) = com.umutk.pulse.extras.launcher.LauncherBadges.update(this)
    override fun onListenerConnected() = com.umutk.pulse.extras.launcher.LauncherBadges.update(this)
}
