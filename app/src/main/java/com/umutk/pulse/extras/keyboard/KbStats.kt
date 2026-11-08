package com.umutk.pulse.extras.keyboard

import android.content.Context

/** Running counts of how the keyboard does for this person: words written, automatic corrections, the ones taken back, suggestions tapped. */
object KbStats {
    private fun sp(c: Context) = c.getSharedPreferences("kbd_stats", Context.MODE_PRIVATE)
    fun add(c: Context, key: String, n: Int = 1) {
        if (n == 0) return
        val d = "d" + java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date()) + "_" + key
        sp(c).let { it.edit().putInt(key, it.getInt(key, 0) + n).putInt(d, it.getInt(d, 0) + n).apply() }
    }
    /** The counts of the last [days] days for [key], oldest first. */
    fun days(c: Context, key: String, days: Int = 14): List<Int> {
        val f = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US); val cal = java.util.Calendar.getInstance()
        val out = ArrayList<Int>()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(days - 1))
        repeat(days) { out += sp(c).getInt("d" + f.format(cal.time) + "_" + key, 0); cal.add(java.util.Calendar.DAY_OF_YEAR, 1) }
        return out
    }
    fun get(c: Context, key: String): Int = sp(c).getInt(key, 0)
    fun clear(c: Context) { sp(c).edit().clear().apply() }
}
