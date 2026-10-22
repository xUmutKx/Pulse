package com.umutk.pulse.extras.music.widgets

import android.content.Context

/** The look options of the Poweramp and Windows-player widgets (Music settings > Misc): background and how big the cover is. */
object WidgetOpts {
    private fun p(c: Context) = c.getSharedPreferences("music_widgets", Context.MODE_PRIVATE)
    /** "none" (transparent, the default), "black" or "stars" (Poweramp's starry sky). */
    fun bg(c: Context): String = p(c).getString("bg", "none") ?: "none"
    fun setBg(c: Context, v: String) = p(c).edit().putString("bg", v).apply()
    /** The cover's size as a share of its full square, 30..100; 75 by default. */
    fun artPct(c: Context): Int = p(c).getInt("art_pct", 75).coerceIn(30, 100)
    fun setArtPct(c: Context, v: Int) = p(c).edit().putInt("art_pct", v.coerceIn(30, 100)).apply()
    fun refresh(c: Context) { PowerampWidget.refresh(c); WindowsPlayerWidget.refresh(c) }
}
