package com.umutk.pulse.features

import android.content.Context
import android.os.BatteryManager

/**
 * Battery wear from what flows in and out, the way AccuBattery works it out: the charge counter (µAh) moves with the percentage, so a stretch of 10 percent
 * of charging (or of draining) says how much the battery holds in all. Each stretch is one estimate; the middle one of the last twenty is shown.
 * Called with every sample the app takes; it needs no root.
 */
object BatteryLog {
    private fun p(c: Context) = c.getSharedPreferences("battery_log", Context.MODE_PRIVATE)
    private var lastCall = 0L

    fun sample(c: Context, charging: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastCall < 20_000) return
        lastCall = now
        val bm = c.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val counter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (pct <= 0 || counter <= 0L || counter == Long.MIN_VALUE) return
        val s = p(c)
        val mode = if (charging) 1 else -1
        val m0 = s.getInt("mode", 0); val p0 = s.getInt("pct", -1); val c0 = s.getLong("counter", 0L)
        if (m0 != mode || p0 < 0) { s.edit().putInt("mode", mode).putInt("pct", pct).putLong("counter", counter).apply(); return }
        val dPct = (pct - p0) * mode
        if (dPct < 0) { s.edit().putInt("pct", pct).putLong("counter", counter).apply(); return }   // the percentage went the other way: start again here
        if (dPct < 10) return
        val dCount = (counter - c0) * mode
        s.edit().putInt("pct", pct).putLong("counter", counter).apply()
        if (dCount <= 0L) return
        val est = dCount * 100L / dPct
        val design = designUah(c)
        // a believable figure only: between 40 percent and 130 percent of the design capacity (when that is known), or 800 to 12000 mAh
        val ok = if (design > 0) est in (design * 4 / 10)..(design * 13 / 10) else est in 800_000L..12_000_000L
        if (!ok) return
        val list = (s.getString("est", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() } + est).takeLast(20)
        s.edit().putString("est", list.joinToString(",")).apply()
    }

    /** The middle one of the estimates in mAh and how many there are, or null while there is none. */
    fun estimate(c: Context): Pair<Long, Int>? {
        val list = p(c).getString("est", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }.sorted()
        if (list.isEmpty()) return null
        return list[list.size / 2] / 1000 to list.size
    }

    fun clear(c: Context) = p(c).edit().clear().apply()

    /** The health the user typed in, in percent; null = none, the measured figure is shown. */
    fun manualPct(c: Context): Int? = p(c).getInt("manual_pct", -1).takeIf { it in 1..150 }

    fun setManualPct(c: Context, pct: Int?) {
        p(c).edit().apply { if (pct == null) remove("manual_pct") else putInt("manual_pct", pct) }.apply()
    }

    /** The health in percent from the measured capacity and the design capacity, or null while either is unknown. */
    fun measuredPct(c: Context): Int? {
        val est = estimate(c) ?: return null
        val design = designUah(c) / 1000
        return if (design > 0) (100 * est.first / design).toInt() else null
    }

    /** The capacity the battery had when new in µAh: the phone's own file, else the figure of the power profile, else 0. */
    fun designUah(c: Context): Long {
        runCatching { java.io.File("/sys/class/power_supply/battery/charge_full_design").readText().trim().toLong() }.getOrNull()?.takeIf { it > 0 }?.let { return it }
        return runCatching {
            val cls = Class.forName("com.android.internal.os.PowerProfile")
            val pp = cls.getConstructor(Context::class.java).newInstance(c)
            ((cls.getMethod("getBatteryCapacity").invoke(pp) as Number).toDouble() * 1000).toLong()
        }.getOrDefault(0L)
    }
}
