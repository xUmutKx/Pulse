package com.umutk.pulse.extras.notes

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.umutk.pulse.tr

/** Reminders: an inexact alarm per note (no exact-alarm permission needed), shown as a notification with a snooze button. */
object NotesReminders {
    private const val CH = "notes_reminders"
    const val ACTION_FIRE = "com.umutk.pulse.notes.REMIND"
    const val ACTION_SNOOZE = "com.umutk.pulse.notes.SNOOZE"
    private const val SNOOZE_MS = 10 * 60_000L

    private fun alarmIntent(c: Context, id: String, flags: Int): PendingIntent? =
        PendingIntent.getBroadcast(c, id.hashCode(), Intent(c, NotesReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra("id", id), flags or PendingIntent.FLAG_IMMUTABLE)

    fun schedule(c: Context, n: Note) {
        cancel(c, n.id)
        if (n.trashedAt > 0 || n.remindAt <= System.currentTimeMillis()) return
        val pi = alarmIntent(c, n.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.remindAt, pi)
    }

    fun cancel(c: Context, id: String) {
        val pi = alarmIntent(c, id, PendingIntent.FLAG_NO_CREATE) ?: return
        c.getSystemService(AlarmManager::class.java).cancel(pi)
        pi.cancel()
    }

    fun snoozeAt(now: Long = System.currentTimeMillis()) = now + SNOOZE_MS

    fun show(c: Context, n: Note) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(com.umutk.pulse.QuietChannel(CH, tr("Note reminders", "Not hatırlatmaları"), NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(c, n.id.hashCode(), Intent(c, NotesEntryActivity::class.java).putExtra("open", n.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val snooze = PendingIntent.getBroadcast(c, n.id.hashCode() + 1,
            Intent(c, NotesReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("id", n.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // a locked note never shows its text on the lock screen
        val title = if (n.locked) tr("Locked note", "Kilitli not") else NotesStore.titleOf(n).ifBlank { tr("Reminder", "Hatırlatma") }
        val text = if (n.locked) "" else n.body.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        val note = NotificationCompat.Builder(c, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(text).setContentIntent(open).setAutoCancel(true)
            .addAction(0, tr("Snooze 10 min", "10 dk ertele"), snooze).build()
        // without the notification permission the reminder is dropped quietly; the alarm itself already fired
        try { NotificationManagerCompat.from(c).notify(n.id.hashCode(), note) } catch (e: SecurityException) { }
    }
}

/** Fires a reminder, snoozes it, and sets the alarms again after a reboot. */
class NotesReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val now = System.currentTimeMillis()
        if (i.action == Intent.ACTION_BOOT_COMPLETED) {
            NotesStore.load(c).filter { it.remindAt > now && it.trashedAt == 0L }.forEach { NotesReminders.schedule(c, it) }
            return
        }
        val id = i.getStringExtra("id") ?: return
        val list = NotesStore.load(c)
        val n = list.firstOrNull { it.id == id } ?: return
        if (n.trashedAt > 0) return
        if (i.action == NotesReminders.ACTION_SNOOZE) {
            val moved = n.copy(remindAt = NotesReminders.snoozeAt(now), updated = n.updated)
            NotesStore.save(c, list.map { if (it.id == id) moved else it })
            NotesReminders.schedule(c, moved)
        } else {
            // the reminder has fired: clear it, then show it
            NotesStore.save(c, list.map { if (it.id == id) it.copy(remindAt = 0L) else it })
            NotesReminders.show(c, n)
        }
    }
}
