package com.umutk.pulse.ads

import android.content.Context
import com.umutk.pulse.Look

/**
 * Theme locks (play build): the look in use stays open; every other look is locked. One short ad opens all of them
 * for [MINUTES]; after that only the look in use is open again. The foss build and Premium never lock anything.
 */
object ThemeLock {
    const val KEY = "theme_unlock"
    const val MINUTES = 2

    /** True while the theme may not be picked: play build, no Premium, not the look in use, and no ad window open. */
    fun isLocked(c: Context, id: String): Boolean {
        if (!PulseAds.ENABLED || Premium.on(c)) return false
        if (id == Look.theme.id) return false
        return !AdUnlocks.isOpen(c, KEY)
    }
}
