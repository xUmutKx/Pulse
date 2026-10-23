package com.umutk.pulse.extras.messages

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.umutk.pulse.MsgPrefs
import com.umutk.pulse.R
import com.umutk.pulse.ToolOpenActivity
import com.umutk.pulse.contactName
import com.umutk.pulse.tr

/** Pulse's own notification for an incoming message: text or "New message", copy code, reply, mark read, chat bubble. */
object MsgNotifier {
    const val KEY_REPLY = "reply_text"
    const val A_REPLY = "com.umutk.pulse.msg.REPLY"
    const val A_READ = "com.umutk.pulse.msg.READ"
    const val A_COPY = "com.umutk.pulse.msg.COPY"

    private fun channelId(c: Context) = "pulse_msgs_" + (if (MsgPrefs.sound(c)) "s" else "q") + (if (MsgPrefs.vibrate(c)) "v" else "n")

    private fun ensureChannel(c: Context): String {
        val id = channelId(c)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = c.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(id) == null) {
                val ch = NotificationChannel(id, tr("Messages", "Mesajlar"),
                    if (MsgPrefs.sound(c) || MsgPrefs.vibrate(c)) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW)
                if (!MsgPrefs.sound(c)) ch.setSound(null, null)
                ch.enableVibration(MsgPrefs.vibrate(c))
                if (Build.VERSION.SDK_INT >= 29) ch.setAllowBubbles(true)
                nm.createNotificationChannel(ch)
            }
        }
        return id
    }

    private fun notifId(address: String) = 7000 + (address.hashCode() and 0xFFFF)

    private fun canPost(c: Context) =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun flags(mutable: Boolean): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 31) f = f or (if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE)
        else if (Build.VERSION.SDK_INT >= 23 && !mutable) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }

    private fun openIntent(c: Context, address: String) = Intent(c, ToolOpenActivity::class.java).putExtra("route", "messages")
        .putExtra("msgAddress", address).setData(Uri.parse("pulse-msg://" + Uri.encode(address)))

    private fun action(c: Context, a: String, address: String, body: String? = null) =
        Intent(c, MsgActionReceiver::class.java).setAction(a).putExtra("address", address).also { i -> body?.let { i.putExtra("body", it) } }
            .setData(Uri.parse("pulse-msg-act://" + a + "/" + Uri.encode(address)))

    fun copy(c: Context, text: String) {
        c.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("code", text))
    }

    /** An incoming message from [address]: copies its code when the setting is on and shows the notification. */
    fun incoming(c: Context, address: String, body: String) {
        val code = if (MsgPrefs.autoCopy(c)) MsgCore.otp(body) else null
        val muted = address in MsgPrefs.muted(c)
        if (code != null) copy(c, code)
        if (muted || !MsgPrefs.notify(c) || !canPost(c)) return
        val name = contactName(c, address) ?: address
        val ch = ensureChannel(c)
        val text = if (MsgPrefs.showText(c)) body else tr("New message", "Yeni mesaj")
        val me = Person.Builder().setName(tr("Me", "Ben")).build()
        val sender = Person.Builder().setName(name).setKey(address).build()
        val style = NotificationCompat.MessagingStyle(me).addMessage(text, System.currentTimeMillis(), sender)
        val open = PendingIntent.getActivity(c, notifId(address), openIntent(c, address), flags(false))
        val b = NotificationCompat.Builder(c, ch)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(name).setContentText(text).setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE).setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setContentIntent(open)
            .setOnlyAlertOnce(false)
        if (Build.VERSION.SDK_INT < 26) {
            var d = 0
            if (MsgPrefs.sound(c)) d = d or NotificationCompat.DEFAULT_SOUND
            if (MsgPrefs.vibrate(c)) d = d or NotificationCompat.DEFAULT_VIBRATE
            b.setDefaults(d)
        }
        if (code != null) b.addAction(0, tr("Copy $code", "$code kopyala"),
            PendingIntent.getBroadcast(c, notifId(address) + 1, action(c, A_COPY, address, code), flags(false)))
        val remote = RemoteInput.Builder(KEY_REPLY).setLabel(tr("Reply", "Yanıtla")).build()
        b.addAction(NotificationCompat.Action.Builder(0, tr("Reply", "Yanıtla"),
            PendingIntent.getBroadcast(c, notifId(address) + 2, action(c, A_REPLY, address), flags(true))).addRemoteInput(remote).setAllowGeneratedReplies(true).build())
        b.addAction(0, tr("Mark as read", "Okundu"), PendingIntent.getBroadcast(c, notifId(address) + 3, action(c, A_READ, address), flags(false)))
        if (Build.VERSION.SDK_INT >= 30 && MsgPrefs.bubbles(c)) bubble(c, b, address, name, sender)
        runCatching { NotificationManagerCompat.from(c).notify(notifId(address), b.build()) }
    }

    /** A chat bubble needs a long-lived conversation shortcut and an activity that may be embedded. */
    private fun bubble(c: Context, b: NotificationCompat.Builder, address: String, name: String, person: Person) {
        runCatching {
            val sid = "msg_" + address
            val view = Intent(c, MsgBubbleActivity::class.java).setAction(Intent.ACTION_VIEW).putExtra("msgAddress", address)
                .setData(Uri.parse("pulse-msg://" + Uri.encode(address)))
            ShortcutManagerCompat.pushDynamicShortcut(c, ShortcutInfoCompat.Builder(c, sid)
                .setShortLabel(name).setLongLived(true).setIntent(view).setPerson(person)
                .setIcon(IconCompat.createWithResource(c, R.mipmap.ic_ad_messages)).build())
            val pi = PendingIntent.getActivity(c, notifId(address) + 4, view, flags(true))
            b.setShortcutId(sid)
            b.addPerson(person)
            b.setBubbleMetadata(NotificationCompat.BubbleMetadata.Builder(pi, IconCompat.createWithResource(c, R.mipmap.ic_ad_messages))
                .setDesiredHeight(600).setAutoExpandBubble(false).setSuppressNotification(false).build())
        }
    }

    fun cancel(c: Context, address: String) { runCatching { NotificationManagerCompat.from(c).cancel(notifId(address)) } }

    /** Shows the sent reply under the notification so the reply spinner stops. */
    fun repliedTo(c: Context, address: String, text: String) {
        if (!canPost(c)) return
        val ch = ensureChannel(c)
        val me = Person.Builder().setName(tr("Me", "Ben")).build()
        val b = NotificationCompat.Builder(c, ch).setSmallIcon(android.R.drawable.stat_notify_chat)
            .setStyle(NotificationCompat.MessagingStyle(me).addMessage(text, System.currentTimeMillis(), me))
            .setOnlyAlertOnce(true).setTimeoutAfter(4000)
            .setContentIntent(PendingIntent.getActivity(c, notifId(address), openIntent(c, address), flags(false)))
        runCatching { NotificationManagerCompat.from(c).notify(notifId(address), b.build()) }
    }
}
