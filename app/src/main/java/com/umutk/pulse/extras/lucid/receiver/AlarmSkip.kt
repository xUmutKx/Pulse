package com.umutk.pulse.extras.lucid.receiver

import android.content.Context
import java.util.Calendar

/**
 * "Turn back on for Friday": a repeating alarm that was switched off for its next ring and is switched on again keeps that one ring skipped.
 * The moment of the skipped ring is kept per alarm; scheduling and the countdown move past it, and it clears itself once that moment is over.
 */
object AlarmSkip {
    private fun sp(c: Context) = c.getSharedPreferences("lucid_alarm_skip", Context.MODE_PRIVATE)

    fun set(c: Context, id: Int, at: Long) { sp(c).edit().putLong("skip_$id", at).commit() }
    fun clear(c: Context, id: Int) { sp(c).edit().remove("skip_$id").apply() }
    fun get(c: Context, id: Int): Long = sp(c).getLong("skip_$id", 0L)

    /** [trigger] moved to the first ring after the skipped one when it is that ring (or before it); other times are left alone. */
    fun adjust(c: Context, id: Int, hour: Int, minute: Int, days: Set<Int>, trigger: Long): Long {
        val skip = get(c, id)
        if (skip <= 0L || days.isEmpty()) return trigger
        if (skip < System.currentTimeMillis()) { clear(c, id); return trigger }
        if (trigger > skip) return trigger
        val t = Calendar.getInstance().apply {
            timeInMillis = skip
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23)); set(Calendar.MINUTE, minute.coerceIn(0, 59)); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        var guard = 0
        while (guard < 8 && !days.contains(t.get(Calendar.DAY_OF_WEEK))) { t.add(Calendar.DAY_OF_YEAR, 1); guard++ }
        return t.timeInMillis
    }
}
