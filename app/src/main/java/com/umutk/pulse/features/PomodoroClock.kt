package com.umutk.pulse.features

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import com.umutk.pulse.MainActivity
import com.umutk.pulse.R
import com.umutk.pulse.tr

/** Pomodoro mode of the timer, kept in prefs "pomodoro" so it goes on when the screen or the app closes.
 *  "phase" (0 work, 1 short break, 2 long break), "count" (work periods done in this set, 0..4), "work" / "short" / "long" (ms),
 *  "end" (wall-clock ms while it runs), "left" (ms while paused), "on" (the timer screen shows Pomodoro). */
object PomodoroClock {
    private const val NOTE_ID = 7105
    private const val ALARM_ID = 7104
    private val main = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private val keys = arrayOf("work", "short", "long")
    private val defaults = longArrayOf(25 * 60_000L, 5 * 60_000L, 15 * 60_000L)
    private fun p(c: Context) = c.getSharedPreferences("pomodoro", Context.MODE_PRIVATE)

    fun on(c: Context) = p(c).getBoolean("on", false)
    fun setOn(c: Context, v: Boolean) { p(c).edit().putBoolean("on", v).apply() }
    fun phase(c: Context) = p(c).getInt("phase", 0)
    fun count(c: Context) = p(c).getInt("count", 0)
    /** Length of one kind of period: 0 work, 1 short break, 2 long break. */
    fun length(c: Context, kind: Int = phase(c)) = p(c).getLong(keys[kind], defaults[kind])
    fun running(c: Context) = p(c).getLong("end", 0L) > 0L
    fun remaining(c: Context): Long {
        val s = p(c); val end = s.getLong("end", 0L)
        return if (end > 0L) (end - System.currentTimeMillis()).coerceAtLeast(0L) else s.getLong("left", length(c))
    }

    /** A new length starts the current period over when it is the one being changed. */
    fun setLength(c: Context, kind: Int, ms: Long) {
        cancel(c)
        val ed = p(c).edit().putLong(keys[kind], ms).remove("end")
        if (kind == phase(c)) ed.remove("left")
        ed.apply()
    }

    fun start(c: Context) {
        val end = System.currentTimeMillis() + (remaining(c).takeIf { it > 0L } ?: length(c))
        p(c).edit().putLong("end", end).remove("left").apply()
        schedule(c, end)
    }
    fun pause(c: Context) { val left = remaining(c); cancel(c); p(c).edit().remove("end").putLong("left", left).apply() }
    /** Back to the first work period of a new set. */
    fun reset(c: Context) { cancel(c); p(c).edit().remove("end").remove("left").putInt("phase", 0).putInt("count", 0).apply() }
    /** Ends the current period now, without the alarm; the next one waits until Start. */
    fun skip(c: Context) { cancel(c); advance(c) }

    /** Re-arms the in-app wake-up after Pulse was restarted while a period runs. */
    fun resume(c: Context) { p(c).getLong("end", 0L).takeIf { it > 0L }?.let { schedule(c, it) } }

    /** Finishes the period once its end has passed; the in-app handler, the alarm (via TimerReceiver) and the screen all call it. */
    fun check(c: Context) {
        val end = p(c).getLong("end", 0L)
        if (end == 0L || end > System.currentTimeMillis()) return
        val finished = phase(c)
        advance(c)
        notifyDone(c, finished)
    }

    /** Work, then a short break; after the 4th work period a long break; after a long break, work again. */
    private fun advance(c: Context) {
        val s = p(c); var count = s.getInt("count", 0)
        val next = when (phase(c)) {
            0 -> { count++; if (count >= 4) 2 else 1 }
            1 -> 0
            else -> { count = 0; 0 }
        }
        s.edit().putInt("phase", next).putInt("count", count).remove("end").remove("left").apply()
    }

    // shortcut: without SCHEDULE_EXACT_ALARM the alarm may come late in Doze; the in-app handler covers the time Pulse is alive
    private fun schedule(c: Context, end: Long) {
        val app = c.applicationContext
        pending?.let(main::removeCallbacks)
        pending = Runnable { check(app) }.also { main.postDelayed(it, (end - System.currentTimeMillis()).coerceAtLeast(0L)) }
        val am = app.getSystemService(AlarmManager::class.java)
        runCatching { if (am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(app)) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, alarm(app)) }
    }

    private fun cancel(c: Context) {
        val app = c.applicationContext
        pending?.let(main::removeCallbacks); pending = null
        app.getSystemService(AlarmManager::class.java).cancel(alarm(app))
        app.getSystemService(NotificationManager::class.java).cancel(NOTE_ID)
    }

    /** Same receiver as the countdown timer, which calls [check] too. */
    private fun alarm(c: Context) = PendingIntent.getBroadcast(c, ALARM_ID, Intent(c, TimerReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The same "timer" channel and alarm sound as the countdown timer. */
    private fun notifyDone(c: Context, finished: Int) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (android.os.Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(com.umutk.pulse.QuietChannel("timer", tr("Timer", "Zamanlayıcı"), NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        }) else Unit
        val open = PendingIntent.getActivity(c, 7106, Intent(c, MainActivity::class.java).putExtra("start", "toolkit:timer")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = if (finished == 0) tr("Time for a break", "Mola zamanı") else tr("Back to work", "Çalışma zamanı")
        runCatching {
            nm.notify(NOTE_ID, androidx.core.app.NotificationCompat.Builder(c, "timer").setSmallIcon(R.drawable.ic_qs_timer)
                .setContentTitle(title).setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM).setContentIntent(open).setAutoCancel(true).build())
        }
    }
}
