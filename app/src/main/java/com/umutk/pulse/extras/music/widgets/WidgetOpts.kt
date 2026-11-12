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
    /** The text, the buttons and what is shown on the Poweramp widget; the cover, the corners and the background are the options above. */
    fun look(c: Context): WidgetLook = p(c).let { q ->
        WidgetLook(
            font = q.getString("w_font", "bold") ?: "bold",
            title = q.getInt("w_title", WidgetLook().title), sub = q.getInt("w_sub", WidgetLook().sub),
            button = q.getInt("w_button", WidgetLook().button), accent = q.getInt("w_accent", 0),
            outline = q.getBoolean("w_outline", false), btnPct = q.getInt("w_btn_pct", 100).coerceIn(70, 140),
            textPct = q.getInt("w_text_pct", 100).coerceIn(70, 150), align = q.getInt("w_align", 0).coerceIn(0, 2), artRight = q.getBoolean("w_art_right", false),
            album = q.getBoolean("w_album", true), counter = q.getBoolean("w_counter", true), marks = q.getBoolean("w_marks", true), seek = q.getBoolean("w_seek", true),
        )
    }
    fun setLook(c: Context, l: WidgetLook) = p(c).edit()
        .putString("w_font", l.font).putInt("w_title", l.title).putInt("w_sub", l.sub).putInt("w_button", l.button).putInt("w_accent", l.accent)
        .putBoolean("w_outline", l.outline).putInt("w_btn_pct", l.btnPct).putInt("w_text_pct", l.textPct).putInt("w_align", l.align).putBoolean("w_art_right", l.artRight)
        .putBoolean("w_album", l.album).putBoolean("w_counter", l.counter).putBoolean("w_marks", l.marks).putBoolean("w_seek", l.seek).apply()
    fun refresh(c: Context) { PowerampWidget.refresh(c); WindowsPlayerWidget.refresh(c) }
}

/**
 * How the text and the buttons of the Poweramp widget look. [font] is a key of [WIDGET_FONTS]; the colours are ARGB; an [accent] of 0 follows the skin;
 * [outline] draws the buttons as outlines; [btnPct] is the size of the transport buttons (70 to 140); [textPct] the size of the text (70 to 150);
 * [align] puts the text at the left (0), in the middle (1) or at the right (2) of its place; [artRight] puts the cover at the right and the text at the left;
 * the rest switch parts of the widget on or off.
 */
data class WidgetLook(
    val font: String = "bold", val title: Int = 0xFFFFFFFF.toInt(), val sub: Int = 0xFFA8A8A8.toInt(), val button: Int = 0xFFD2D2D2.toInt(), val accent: Int = 0,
    val outline: Boolean = false, val btnPct: Int = 100, val textPct: Int = 100, val align: Int = 0, val artRight: Boolean = false, val album: Boolean = true, val counter: Boolean = true, val marks: Boolean = true, val seek: Boolean = true,
)

/** The typefaces the widget offers: the key, its name on the settings page and the system family it uses. */
val WIDGET_FONTS = listOf(
    Triple("bold", "Sans", "sans-serif"), Triple("condensed", "Condensed", "sans-serif-condensed"), Triple("light", "Light", "sans-serif-light"),
    Triple("serif", "Serif", "serif"), Triple("mono", "Mono", "monospace"), Triple("casual", "Casual", "casual"), Triple("cursive", "Cursive", "cursive"),
)
