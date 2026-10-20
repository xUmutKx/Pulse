package com.umutk.pulse.extras.music

import android.content.Context

/**
 * What a sideways drag on the album cover does: change track (default, as before) or nothing.
 * Up still opens the lyrics and down still goes back, whatever this is set to.
 */
object CoverGestures {
    private fun sp(c: Context) = c.getSharedPreferences("music_settings", Context.MODE_PRIVATE)

    fun swipeChangesTrack(c: Context) = sp(c).getBoolean("coverSwipe", true)
    fun setSwipeChangesTrack(c: Context, on: Boolean) = sp(c).edit().putBoolean("coverSwipe", on).apply()
}
