package com.umutk.pulse

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Stopwatch time kept in preferences: the Toolkit page and the Quick Settings tile share one clock, and it survives a restart. */
object Stopwatch {
    private fun p(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)

    /** Time counted before the current run; [sw_start] is the wall-clock start of the current run, 0 when stopped. */
    fun running(c: Context) = p(c).getLong("sw_start", 0L) > 0L

    fun elapsed(c: Context): Long {
        val s = p(c)
        val st = s.getLong("sw_start", 0L)
        return s.getLong("sw_base", 0L) + if (st > 0L) System.currentTimeMillis() - st else 0L
    }

    fun toggle(c: Context) {
        val now = System.currentTimeMillis()
        val s = p(c)
        val st = s.getLong("sw_start", 0L)
        if (st > 0L) s.edit().putLong("sw_base", s.getLong("sw_base", 0L) + now - st).putLong("sw_start", 0L).apply()
        else s.edit().putLong("sw_start", now).apply()
    }

    fun reset(c: Context) { p(c).edit().putLong("sw_base", 0L).putLong("sw_start", 0L).apply() }

    fun clock(ms: Long): String { val t = ms / 10; return "%02d:%02d.%02d".format(t / 6000, t / 100 % 60, t % 100) }
}

/** Quick Settings tile: a tap starts or stops the stopwatch right there, without opening the app. The line under the name shows the time. */
class StopwatchTile : TileService() {
    private val main = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { paint(); main.postDelayed(this, 1000) }
    }

    override fun onStartListening() { paint(); restartTicking() }
    override fun onStopListening() { main.removeCallbacks(tick) }

    override fun onClick() {
        Stopwatch.toggle(this)
        paint()
        restartTicking()
    }

    private fun restartTicking() {
        main.removeCallbacks(tick)
        if (Stopwatch.running(this)) main.postDelayed(tick, 1000)
    }

    private fun paint() {
        qsTile?.apply {
            state = if (Stopwatch.running(this@StopwatchTile)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Stopwatch"
            subtitle = Stopwatch.clock(Stopwatch.elapsed(this@StopwatchTile))
            updateTile()
        }
    }
}
