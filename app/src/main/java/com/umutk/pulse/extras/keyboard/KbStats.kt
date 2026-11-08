package com.umutk.pulse.extras.keyboard

import android.content.Context

/** Running counts of how the keyboard does for this person: words written, automatic corrections, the ones taken back, suggestions tapped. */
object KbStats {
    private fun sp(c: Context) = c.getSharedPreferences("kbd_stats", Context.MODE_PRIVATE)
    fun add(c: Context, key: String, n: Int = 1) { if (n != 0) sp(c).let { it.edit().putInt(key, it.getInt(key, 0) + n).apply() } }
    fun get(c: Context, key: String): Int = sp(c).getInt(key, 0)
    fun clear(c: Context) { sp(c).edit().clear().apply() }
}
