package com.umutk.pulse.extras.keyboard

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.atan2

/**
 * The symbols and functions written on the corners of a letter key: swipe the key toward a corner (or any of the eight directions) to type what is
 * there. "@name" is a key of the key bar (Esc, Tab, Copy ...); anything else is typed as it is.
 */
class KeyCorner(val nw: String = "", val n: String = "", val ne: String = "", val w: String = "", val e: String = "", val sw: String = "", val s: String = "", val se: String = "") {
    /** The directions that have something on them: the angle (degrees, 0 = right, counter-clockwise) and the text. */
    val slots: List<Pair<Int, String>> = listOf(0 to e, 45 to ne, 90 to n, 135 to nw, 180 to w, 225 to sw, 270 to s, 315 to se).filter { it.second.isNotEmpty() }
}

object KeyCorners {
    // The QWERTY (US) symbols are the layout of Unexpected Keyboard (srcs/layouts/latn_qwerty_us.xml, https://github.com/Julow/Unexpected-Keyboard),
    // whose layout definitions are released under CC0 1.0 (public domain): the same symbols on the same corners. Added here: Select all, Cut, Copy
    // and Paste on the bottom of A, X, C and V.
    private val map: Map<Char, KeyCorner> = mapOf(
        'q' to KeyCorner(ne = "1", se = "@esc"),
        'w' to KeyCorner(nw = "~", ne = "2", sw = "@"),
        'e' to KeyCorner(nw = "!", ne = "3", sw = "#", se = "€"),
        'r' to KeyCorner(ne = "4", sw = "\$"),
        't' to KeyCorner(ne = "5", sw = "%"),
        'y' to KeyCorner(ne = "6", sw = "^"),
        'u' to KeyCorner(ne = "7", sw = "&"),
        'i' to KeyCorner(ne = "8", sw = "*"),
        'o' to KeyCorner(ne = "9", sw = "(", se = ")"),
        'p' to KeyCorner(ne = "0"),
        'a' to KeyCorner(nw = "@tab", ne = "`", s = "@selectAll"),
        's' to KeyCorner(ne = "§", sw = "ß"),
        'd' to KeyCorner(),
        'f' to KeyCorner(),
        'g' to KeyCorner(ne = "-", sw = "_"),
        'h' to KeyCorner(ne = "=", sw = "+"),
        'j' to KeyCorner(sw = "{", se = "}"),
        'k' to KeyCorner(sw = "[", se = "]"),
        'l' to KeyCorner(ne = "|", sw = "\\"),
        'z' to KeyCorner(),
        'x' to KeyCorner(ne = "†", s = "@cut"),
        'c' to KeyCorner(ne = "<", sw = ".", s = "@copy"),
        'v' to KeyCorner(ne = ">", sw = ",", s = "@paste"),
        'b' to KeyCorner(ne = "?", sw = "/"),
        'n' to KeyCorner(ne = ":", sw = ";"),
        'm' to KeyCorner(ne = "\"", sw = "'"),
    )
    private val names = mapOf("esc" to "Esc", "tab" to "Tab", "selectAll" to "All", "cut" to "Cut", "copy" to "Copy", "paste" to "Paste")

    fun of(label: String): KeyCorner? = if (label.length == 1) map[label[0].lowercaseChar()] else null

    /** What a swipe from [label]'s key by [d] (pixels) lands on: the nearest direction that has a symbol (within 55 degrees), or null when it is too short or nothing is there. */
    fun pick(label: String, d: Offset, min: Float): String? {
        val c = of(label) ?: return null
        if (d.getDistance() < min || c.slots.isEmpty()) return null
        val ang = ((Math.toDegrees(atan2((-d.y).toDouble(), d.x.toDouble())) + 360.0) % 360.0)
        val best = c.slots.minByOrNull { val diff = abs(it.first - ang); minOf(diff, 360.0 - diff) } ?: return null
        val diff = abs(best.first - ang).let { minOf(it, 360.0 - it) }
        return if (diff <= 55.0) best.second else null
    }

    /** The text shown on the corner or in the pop-up: a function shows its short name. */
    fun show(t: String): String = if (t.startsWith("@")) names[t.drop(1)] ?: t.drop(1) else t
}
