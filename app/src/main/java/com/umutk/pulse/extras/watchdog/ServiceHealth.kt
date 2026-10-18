package com.umutk.pulse.extras.watchdog

import android.app.Service
import android.content.Context

/**
 * Last heartbeat of each watched service. A running service calls [beat] about once a minute;
 * a service Android killed stops beating, and [ServiceWatchdog] restarts it once the beat is stale.
 */
object ServiceHealth {
    private const val PREFS = "service_health"

    /** Longer than the beat interval, shorter than the watchdog period (15 min). */
    const val STALE_MS = 5 * 60 * 1000L

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun beat(c: Context, cls: Class<out Service>) {
        p(c).edit().putLong(cls.name, System.currentTimeMillis()).apply()
    }

    /** Millis of the last heartbeat, 0 when the service never beat or was cleared. */
    fun lastBeat(c: Context, cls: Class<out Service>): Long = p(c).getLong(cls.name, 0L)

    fun isStale(c: Context, cls: Class<out Service>, now: Long = System.currentTimeMillis()): Boolean =
        now - lastBeat(c, cls) > STALE_MS

    /** Forgets the heartbeat when a service stops on purpose. */
    fun clear(c: Context, cls: Class<out Service>) {
        p(c).edit().remove(cls.name).apply()
    }
}
