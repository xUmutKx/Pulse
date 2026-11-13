package com.umutk.pulse.extras.keyboard

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** The symbols and functions kept on the corners of a letter key: swipe the key toward its top-left corner, its top-right corner or down to type what is written there. */
class KeyCorner(val nw: String, val ne: String, val s: String)

object KeyCorners {
    // "@name" is a key of the key bar (Esc, Tab, Copy ...), anything else is typed as it is
    private val map: Map<Char, KeyCorner> = mapOf(
        'q' to KeyCorner("!", "1", "@esc"), 'w' to KeyCorner("@", "2", ""), 'e' to KeyCorner("#", "3", ""), 'r' to KeyCorner("$", "4", ""),
        't' to KeyCorner("%", "5", ""), 'y' to KeyCorner("^", "6", ""), 'u' to KeyCorner("&", "7", ""), 'i' to KeyCorner("*", "8", ""),
        'o' to KeyCorner("(", "9", ""), 'p' to KeyCorner(")", "0", ""),
        'a' to KeyCorner("@tab", "`", "@selectAll"), 's' to KeyCorner("\"", "'", ""), 'd' to KeyCorner("\\", "/", ""), 'f' to KeyCorner("_", "-", ""),
        'g' to KeyCorner("+", "=", ""), 'h' to KeyCorner(">", "<", ""), 'j' to KeyCorner("{", "}", ""), 'k' to KeyCorner("[", "]", ""), 'l' to KeyCorner(":", ";", ""),
        'z' to KeyCorner("~", "€", ""), 'x' to KeyCorner("×", "…", "@cut"), 'c' to KeyCorner("©", "¢", "@copy"), 'v' to KeyCorner("?", "¿", "@paste"),
        'b' to KeyCorner("|", "¦", ""), 'n' to KeyCorner("ñ", "¡", ""), 'm' to KeyCorner("µ", "™", ""),
    )
    private val names = mapOf("esc" to "Esc", "tab" to "Tab", "selectAll" to "All", "cut" to "Cut", "copy" to "Copy", "paste" to "Paste")

    fun of(label: String): KeyCorner? = if (label.length == 1) map[label[0].lowercaseChar()] else null

    /** What a swipe from [label]'s key by [d] (pixels) lands on, or null when it is too short or the corner is empty. */
    fun pick(label: String, d: Offset, min: Float): String? {
        val c = of(label) ?: return null
        if (d.getDistance() < min) return null
        val t = if (d.y < 0f && abs(d.y) >= abs(d.x) * .5f) (if (d.x >= 0f) c.ne else c.nw) else if (d.y > 0f && d.y >= abs(d.x)) c.s else ""
        return t.ifEmpty { null }
    }

    /** The text shown on the corner or in the pop-up: a function shows its short name. */
    fun show(t: String): String = if (t.startsWith("@")) names[t.drop(1)] ?: t.drop(1) else t
}
