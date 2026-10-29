package com.umutk.pulse.extras.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * The alarms of the Clock. Each alarm is set with AlarmManager.setAlarmClock (the real alarm mechanism: it wakes the phone out of Doze, shows in the status bar and
 * the system clock) and set again after a reboot (see [AlarmBootReceiver]) and whenever Pulse starts. The time maths follows Lucid Alarm (GPL-3.0, DarkAspect1),
 * written again here for Pulse's own storage. What Android does not allow: after "Force stop" no alarm of any app fires until the app is opened again.
 */
class AlarmItem(
    val id: Int, val hour: Int, val minute: Int, val label: String = "", val enabled: Boolean = true,
    /** Calendar.SUNDAY..SATURDAY (1..7); empty means once. */
    val days: Set<Int> = emptySet(), val snoozeMin: Int = 10, val vibrate: Boolean = true,
) {
    fun copy(hour: Int = this.hour, minute: Int = this.minute, label: String = this.label, enabled: Boolean = this.enabled, days: Set<Int> = this.days, snoozeMin: Int = this.snoozeMin, vibrate: Boolean = this.vibrate) =
        AlarmItem(id, hour, minute, label, enabled, days, snoozeMin, vibrate)
}

object AlarmStore {
    private fun sp(c: Context) = c.getSharedPreferences("clock_alarms", Context.MODE_PRIVATE)

    fun all(c: Context): List<AlarmItem> = runCatching {
        val a = JSONArray(sp(c).getString("list", "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i).let { o ->
            AlarmItem(o.getInt("id"), o.getInt("h"), o.getInt("m"), o.optString("l"), o.optBoolean("e", true),
                o.optString("d").split(',').mapNotNull { it.toIntOrNull() }.toSet(), o.optInt("s", 10), o.optBoolean("v", true))
        } }
    }.getOrDefault(emptyList()).sortedWith(compareBy({ it.hour }, { it.minute }))

    private fun write(c: Context, l: List<AlarmItem>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("h", it.hour).put("m", it.minute).put("l", it.label).put("e", it.enabled).put("d", it.days.sorted().joinToString(",")).put("s", it.snoozeMin).put("v", it.vibrate)) }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    fun nextId(c: Context) = (all(c).maxOfOrNull { it.id } ?: 0) + 1
    fun get(c: Context, id: Int) = all(c).firstOrNull { it.id == id }

    /** Saves the alarm (new or changed) and sets or cancels it with the system. */
    fun save(c: Context, item: AlarmItem) {
        write(c, all(c).filter { it.id != item.id } + item)
        AlarmEngine.schedule(c, item)
    }

    fun delete(c: Context, id: Int) { AlarmEngine.cancel(c, id); write(c, all(c).filter { it.id != id }) }
}

object AlarmEngine {
    const val ACTION_FIRE = "com.umutk.pulse.clock.FIRE"
    private const val BASE = 61000

    fun nextTrigger(item: AlarmItem, from: Long = System.currentTimeMillis()): Long {
        val now = Calendar.getInstance().apply { timeInMillis = from }
        val t = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, item.hour); set(Calendar.MINUTE, item.minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        if (item.days.isNotEmpty()) { var guard = 0; while (guard++ < 8 && t.get(Calendar.DAY_OF_WEEK) !in item.days) t.add(Calendar.DAY_OF_YEAR, 1) }
        return t.timeInMillis
    }

    private fun fire(c: Context, id: Int, snoozed: Boolean = false) = PendingIntent.getBroadcast(
        c, BASE + id, Intent(c, AlarmReceiver::class.java).setAction(ACTION_FIRE).putExtra("id", id).putExtra("snoozed", snoozed), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun canExact(c: Context) = android.os.Build.VERSION.SDK_INT < 31 || (c.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun schedule(c: Context, item: AlarmItem) {
        val app = c.applicationContext
        if (!item.enabled) { cancel(app, item.id); return }
        setAt(app, item.id, nextTrigger(item))
    }

    private fun setAt(app: Context, id: Int, at: Long) {
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val show = PendingIntent.getActivity(app, BASE - 1, Intent(app, com.umutk.pulse.MainActivity::class.java).putExtra("start", "tool:clock").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        runCatching { am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire(app, id)) }
            .onFailure { runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire(app, id)) } }
    }

    fun cancel(c: Context, id: Int) { val app = c.applicationContext; (app.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(fire(app, id)) }

    /** After a reboot, an update, or when Pulse starts: every enabled alarm is set again. */
    fun rescheduleAll(c: Context) { AlarmStore.all(c).forEach { schedule(c, it) } }

    /** Rings again in [minutes] minutes (the alarm itself stays as it was). */
    fun snooze(c: Context, id: Int, minutes: Int) { setAt(c.applicationContext, id, System.currentTimeMillis() + minutes * 60_000L) }

    /** An alarm that has rung: a repeating one is set for its next day, a one-off one switches itself off. */
    fun afterRing(c: Context, id: Int) {
        val item = AlarmStore.get(c, id) ?: return
        if (item.days.isEmpty()) AlarmStore.save(c, item.copy(enabled = false)) else schedule(c, item)
    }
}

/** The system calls this at the alarm's time: the ring service starts (a foreground service, so the sound and the screen come at once). */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val id = i?.getIntExtra("id", -1) ?: -1
        if (id < 0) return
        androidx.core.content.ContextCompat.startForegroundService(c, Intent(c, AlarmRingService::class.java).putExtra("id", id))
    }
}

class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent?) {
        val a = i?.action ?: return
        if (a == Intent.ACTION_BOOT_COMPLETED || a == Intent.ACTION_MY_PACKAGE_REPLACED || a == "android.intent.action.LOCKED_BOOT_COMPLETED" || a == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) AlarmEngine.rescheduleAll(c)
    }
}
