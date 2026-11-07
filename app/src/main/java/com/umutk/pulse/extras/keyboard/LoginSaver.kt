package com.umutk.pulse.extras.keyboard

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.umutk.pulse.features.AuthPrefs
import com.umutk.pulse.features.VItem
import com.umutk.pulse.features.VaultStore
import com.umutk.pulse.tr

/**
 * "Save this login?" asked by the keyboard itself, with no autofill service: the user name typed before and the password just typed in an app or a web page.
 * The offer is a notification (it shows even when the page moves on and the keyboard closes); the password is kept in memory only until the answer.
 */
object LoginSaver {
    private class Offer(val pkg: String, val label: String, val user: String, val pass: String, val browser: Boolean, val update: String?)
    private val offers = HashMap<Int, Offer>()
    private var lastUser = ""; private var lastUserPkg = ""; private var lastUserAt = 0L
    private var lastKey = ""; private var lastKeyAt = 0L

    private val browsers = setOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "org.mozilla.firefox_beta", "com.brave.browser", "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native", "com.vivaldi.browser", "com.duckduckgo.mobile.android", "org.chromium.chrome", "com.kiwibrowser.browser", "com.google.android.apps.chrome")

    fun rememberUser(pkg: String?, text: String) {
        if (text.length > 120) return
        lastUser = text; lastUserPkg = pkg.orEmpty(); lastUserAt = android.os.SystemClock.uptimeMillis()
    }

    /** A password was typed in [pkg]: asks whether to save it, unless the vault already has this very login. */
    fun offer(c: Context, pkg: String?, pass: String) {
        val p = pkg ?: return
        if (pass.length < 3 || pass.length > 200) return
        // the vault behind the screen lock stays closed: nothing is read or written here
        if (AuthPrefs.biometric(c)) return
        val now = android.os.SystemClock.uptimeMillis()
        val user = if (lastUserPkg == p && now - lastUserAt < 10 * 60_000L) lastUser else ""
        val key = "$p|$user|${pass.hashCode()}"
        if (key == lastKey && now - lastKeyAt < 60_000L) return
        lastKey = key; lastKeyAt = now
        val all = VaultStore.all(c)
        val browser = p in browsers
        if (all.any { it.type == 1 && it.pass == pass && (user.isEmpty() || it.user == user) }) return
        // the same user name of this app with another password: the offer is to change it
        val old = if (!browser && user.isNotEmpty()) all.firstOrNull { it.type == 1 && it.user == user && it.uris.any { u -> VaultStore.packageOf(u) == p } }?.id else null
        val label = runCatching { c.packageManager.getApplicationLabel(c.packageManager.getApplicationInfo(p, 0)).toString() }.getOrDefault(p)
        val id = (p + user).hashCode()
        offers[id] = Offer(p, label, user, pass, browser, old)
        post(c, id, offers.getValue(id))
    }

    private fun post(c: Context, id: Int, o: Offer) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("login_save", "Save logins", NotificationManager.IMPORTANCE_HIGH))
        fun pi(action: String, mutable: Boolean) = PendingIntent.getBroadcast(c, id * 2 + (if (action == ACT_SAVE) 0 else 1), Intent(c, LoginSaveReceiver::class.java).setAction(action).putExtra("id", id),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (mutable && android.os.Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE))
        // in a browser the keyboard cannot read the page address: the site is typed in the notification's own box (it can stay empty)
        val save = NotificationCompat.Action.Builder(0, if (o.update != null) tr("Update", "Güncelle") else tr("Save", "Kaydet"), pi(ACT_SAVE, o.browser))
            .also { b -> if (o.browser) b.addRemoteInput(RemoteInput.Builder("site").setLabel(tr("Site (e.g. example.com)", "Site (örn. ornek.com)")).build()) }.build()
        val n = NotificationCompat.Builder(c, "login_save").setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle(if (o.update != null) tr("Update the saved password?", "Kayıtlı şifre güncellensin mi?") else tr("Save this login?", "Bu giriş kaydedilsin mi?"))
            .setContentText(listOf(o.label, o.user).filter { it.isNotBlank() }.joinToString(" · "))
            .setAutoCancel(true).setTimeoutAfter(10 * 60_000L).setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(save).addAction(0, tr("No", "Hayır"), pi(ACT_NO, false)).build()
        nm.notify(id, n)
    }

    internal const val ACT_SAVE = "com.umutk.pulse.LOGIN_SAVE"
    internal const val ACT_NO = "com.umutk.pulse.LOGIN_NO"

    internal fun answer(c: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        val o = offers.remove(id)
        c.getSystemService(NotificationManager::class.java).cancel(id)
        if (o == null || intent.action != ACT_SAVE) return
        val site = RemoteInput.getResultsFromIntent(intent)?.getCharSequence("site")?.toString()?.trim().orEmpty()
        val host = site.takeIf { it.isNotEmpty() }?.let { VaultStore.hostOf(it) }
        val uri = when { !o.browser -> "androidapp://${o.pkg}"; host != null -> "https://$host"; else -> "https://web-login" }
        val old = o.update?.let { oid -> VaultStore.all(c).firstOrNull { it.id == oid } }
        if (old != null) { old.pass = o.pass; VaultStore.upsert(c, old) }
        else VaultStore.upsert(c, VItem(name = host ?: o.label, user = o.user, pass = o.pass, uris = listOf(uri)))
        android.widget.Toast.makeText(c, tr("Saved to the vault", "Kasaya kaydedildi"), android.widget.Toast.LENGTH_SHORT).show()
    }
}

class LoginSaveReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) = LoginSaver.answer(c.applicationContext, i)
}
