package com.umutk.pulse.features

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import com.umutk.pulse.widgets.WidgetData
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Everything the Health screen and the Health widgets share, kept in the "health" SharedPreferences (readable while Pulse is closed). */
object HealthStore {
    fun prefs(c: Context): SharedPreferences = c.getSharedPreferences("health", Context.MODE_PRIVATE)

    // ---- goals
    fun goalSteps(p: SharedPreferences) = p.getInt("g_steps", 10_000).coerceAtLeast(1)
    fun goalMinutes(p: SharedPreferences) = p.getInt("g_min", 30).coerceAtLeast(1)
    fun goalKcal(p: SharedPreferences) = p.getInt("g_kcal", 500).coerceAtLeast(1)
    /** Target body weight in kg; 0 = none set. */
    fun goalWeight(p: SharedPreferences) = p.getFloat("g_weight", 0f)
    fun saveGoals(p: SharedPreferences, steps: Int, minutes: Int, kcal: Int, weight: Float) =
        p.edit().putInt("g_steps", steps.coerceIn(1, 200_000)).putInt("g_min", minutes.coerceIn(1, 1440)).putInt("g_kcal", kcal.coerceIn(1, 20_000)).putFloat("g_weight", weight).apply()

    // ---- body
    fun weight(p: SharedPreferences) = p.getFloat("weight", 70f).takeIf { it in 20f..300f } ?: 70f
    fun height(p: SharedPreferences) = p.getFloat("height", 0f).takeIf { it in 100f..250f } ?: 0f
    fun strideM(p: SharedPreferences) = height(p).let { if (it > 0f) it * 0.415f / 100f else 0.75f }
    fun km(p: SharedPreferences, steps: Long) = steps * strideM(p) / 1000f
    fun kcal(p: SharedPreferences, steps: Long) = (steps * 0.04f * weight(p) / 70f).toInt()
    /** Body mass index, or 0 without a height. */
    fun bmi(p: SharedPreferences): Float = height(p).let { if (it > 0f) weight(p) / ((it / 100f) * (it / 100f)) else 0f }

    // ---- steps
    /** Today's steps from the stored counter values (0 when the last reading is from another day). */
    fun today(p: SharedPreferences): Long {
        if (p.getString("day", "") != LocalDate.now().toString()) return 0L
        val b = p.getLong("base", -1L); val l = p.getLong("last", -1L)
        return if (b < 0 || l < b) 0L else l - b
    }

    /** Feeds one cumulative counter reading (steps since boot); returns today's steps. The day's total is also kept in the history. */
    fun onCounter(p: SharedPreferences, total: Long): Long {
        val now = LocalDate.now(); val day = now.toString()
        val prevDay = p.getString("day", "")
        val last = p.getLong("last", -1L)
        var base = p.getLong("base", -1L)
        // shortcut: steps before the app first reads a day are not counted; a new day starts from yesterday's last reading
        if (prevDay != day || base < 0 || total < base) {
            base = if (prevDay == now.minusDays(1).toString() && last >= 0 && last <= total) last else total
        }
        val steps = total - base
        val e = p.edit().putString("day", day).putLong("base", base).putLong("last", total).putInt("h_$day", steps.toInt())
        if (p.getString("trim", "") != day) { // once a day: the history keeps one year
            val old = now.minusDays(365).toString()
            p.all.keys.filter { it.startsWith("h_") && it.substring(2) < old }.forEach { e.remove(it) }
            e.putString("trim", day)
        }
        e.apply()
        return steps
    }

    fun steps(p: SharedPreferences, d: LocalDate): Long = if (d == LocalDate.now()) today(p) else p.getInt("h_$d", 0).toLong()

    /** The last [n] days, oldest first. */
    fun days(p: SharedPreferences, n: Int): List<Pair<LocalDate, Long>> {
        val t = LocalDate.now()
        return (n - 1 downTo 0).map { t.minusDays(it.toLong()).let { d -> d to steps(p, d) } }
    }

    /** Whole days in a row that reached the step goal; today counts once it is reached, else the run up to yesterday. */
    fun streak(p: SharedPreferences): Int {
        val g = goalSteps(p); var d = LocalDate.now()
        if (steps(p, d) < g) d = d.minusDays(1)
        var n = 0
        while (n < 366 && steps(p, d) >= g) { n++; d = d.minusDays(1) }
        return n
    }

    fun best(p: SharedPreferences): Pair<LocalDate, Long>? =
        p.all.entries.filter { it.key.startsWith("h_") && it.value is Int }.maxByOrNull { it.value as Int }
            ?.let { runCatching { LocalDate.parse(it.key.substring(2)) }.getOrNull()?.let { d -> d to maxOf(steps(p, d), (it.value as Int).toLong()) } }
            ?.let { b -> val t = today(p); if (t > b.second) LocalDate.now() to t else b }

    // ---- activity
    fun activeMinutesToday(p: SharedPreferences): Int {
        val t = LocalDate.now()
        return (loadActLog(p).filter { Instant.ofEpochMilli(it.start).atZone(ZoneId.systemDefault()).toLocalDate() == t }.sumOf { it.dur } / 60_000L).toInt()
    }

    // ---- heart rate
    /** Readings as (time ms, bpm), oldest first; the newest 60 are kept. */
    fun hrLog(p: SharedPreferences): List<Pair<Long, Int>> = p.getString("hr_log", "").orEmpty().split(';').mapNotNull {
        val a = it.split(':'); val t = a.getOrNull(0)?.toLongOrNull(); val b = a.getOrNull(1)?.toIntOrNull()
        if (t != null && b != null) t to b else null
    }
    fun addHr(p: SharedPreferences, bpm: Int) {
        val now = System.currentTimeMillis()
        p.edit().putInt("bpm", bpm).putLong("bpm_t", now).putString("hr_log", (hrLog(p) + (now to bpm)).takeLast(60).joinToString(";") { "${it.first}:${it.second}" }).apply()
    }

    // ---- widgets
    private var lastPush = 0L
    /** Redraws the placed widgets after new steps; at most every 20 s, and no loop of its own. */
    fun push(c: Context) {
        val n = System.currentTimeMillis(); if (n - lastPush < 20_000) return
        lastPush = n; WidgetData.refreshNow(c.applicationContext)
    }

    private var lastPeek = -60_000L
    /**
     * For a widget redraw while Pulse is closed: one short read of the step counter (it reports its latest value when a listener registers).
     * shortcut: runs at most once a minute and waits up to 0.4 s; skipped without the activity permission.
     */
    fun peek(c: Context) {
        val now = SystemClock.elapsedRealtime(); if (now - lastPeek < 60_000) return
        lastPeek = now
        if (Build.VERSION.SDK_INT >= 29 && c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sm = c.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        val latch = CountDownLatch(1); var v = -1L
        val th = HandlerThread("health-peek").also { it.start() }
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { v = e.values[0].toLong(); latch.countDown() }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        try { sm.registerListener(l, s, SensorManager.SENSOR_DELAY_NORMAL, Handler(th.looper)); latch.await(400, TimeUnit.MILLISECONDS) }
        finally { sm.unregisterListener(l); th.quitSafely() }
        if (v >= 0) onCounter(prefs(c), v)
    }
}
