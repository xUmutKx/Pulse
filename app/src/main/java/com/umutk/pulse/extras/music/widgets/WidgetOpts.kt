package com.umutk.pulse.extras.music.widgets

import android.content.Context

/** The look options of the Poweramp and Windows-player widgets (Music settings > Misc): background and how big the cover is. */
object WidgetOpts {
    private fun p(c: Context) = c.getSharedPreferences("music_widgets", Context.MODE_PRIVATE)
    /** "none" (transparent, the default), "black" or "stars" (Poweramp's starry sky). */
    fun bg(c: Context): String = p(c).getString("bg", "none") ?: "none"
    fun setBg(c: Context, v: String) = p(c).edit().putString("bg", v).apply()
    /** The cover's size as a share of its full square, 30..100; 95 by default (it began at 75). */
    fun artPct(c: Context): Int = p(c).getInt("art_pct3", 95).coerceIn(30, 100)
    fun setArtPct(c: Context, v: Int) = p(c).edit().putInt("art_pct3", v.coerceIn(30, 100)).apply()
    /** The corners of the widget and of the cover, as in Poweramp's widget settings: 0 square, 1 rounded (the default), 2 round. */
    fun corners(c: Context): Int = p(c).getInt("corners", 1).coerceIn(0, 2)
    fun setCorners(c: Context, v: Int) = p(c).edit().putInt("corners", v.coerceIn(0, 2)).apply()
    /** A soft shadow under the cover. */
    fun artShadow(c: Context): Boolean = p(c).getBoolean("art_shadow", false)
    fun setArtShadow(c: Context, v: Boolean) = p(c).edit().putBoolean("art_shadow", v).apply()
    /** The cover as a circle ("round icon" in Poweramp). */
    fun artCircle(c: Context): Boolean = p(c).getBoolean("art_circle", false)
    fun setArtCircle(c: Context, v: Boolean) = p(c).edit().putBoolean("art_circle", v).apply()
    fun refresh(c: Context) { PowerampWidget.refresh(c); WindowsPlayerWidget.refresh(c) }
}
