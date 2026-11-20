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
    private val names = mapOf("esc" to "Esc", "tab" to "Tab", "selectAll" to "All", "cut" to "Cut", "copy" to "Copy", "paste" to "Paste", "undo" to "Undo", "home" to "Home", "end" to "End")

    fun of(label: String): KeyCorner? =
        if (label.length != 1) null
        else if (label[0].isLetter()) map[label[0].lowercaseChar()]
        // the number and symbol keys (the number pad, the number row): what the chosen preset writes on them
        else numPad(KeyboardPrefs.numCorners)[label[0]]

    /** Presets for the number keys, in the way of the letters' corners: swipe a key toward a corner to type what is written there. "off" writes nothing. */
    val NUM_PRESETS = listOf("off", "phone", "edit", "symbols", "math")
    fun presetName(p: String) = when (p) { "phone" -> "Old phone letters"; "edit" -> "Edit functions"; "symbols" -> "Symbols"; "math" -> "Maths"; else -> "Off" }

    private val numCache = HashMap<String, Map<Char, KeyCorner>>()
    private fun numPad(p: String): Map<Char, KeyCorner> = numCache.getOrPut(p) {
        when (p) {
            // the letters of an old phone's keys, one swipe each: 2 = a b c, 3 = d e f ... (no repeated pressing)
            "phone" -> mapOf(
                '2' to KeyCorner(nw = "a", n = "b", ne = "c"), '3' to KeyCorner(nw = "d", n = "e", ne = "f"),
                '4' to KeyCorner(nw = "g", n = "h", ne = "i"), '5' to KeyCorner(nw = "j", n = "k", ne = "l"),
                '6' to KeyCorner(nw = "m", n = "n", ne = "o"), '7' to KeyCorner(nw = "p", n = "q", ne = "r", e = "s"),
                '8' to KeyCorner(nw = "t", n = "u", ne = "v"), '9' to KeyCorner(nw = "w", n = "x", ne = "y", e = "z"),
                '1' to KeyCorner(n = ".", ne = ",", nw = "@"), '0' to KeyCorner(n = "+", nw = "-", ne = "_"),
            )
            // the functions of the key bar: copy, paste ... on the digits
            "edit" -> mapOf(
                '1' to KeyCorner(n = "@selectAll"), '2' to KeyCorner(n = "@copy"), '3' to KeyCorner(n = "@cut"),
                '4' to KeyCorner(n = "@paste"), '5' to KeyCorner(n = "@undo"), '6' to KeyCorner(n = "@tab"),
                '7' to KeyCorner(n = "@home"), '8' to KeyCorner(n = "@end"), '9' to KeyCorner(n = "@esc"),
            )
            "symbols" -> mapOf(
                '1' to KeyCorner(ne = "@", se = "!"), '2' to KeyCorner(ne = "#", se = "\""), '3' to KeyCorner(ne = "\$", se = "%"),
                '4' to KeyCorner(ne = "&", se = "'"), '5' to KeyCorner(ne = "(", se = ")"), '6' to KeyCorner(ne = "-", se = "_"),
                '7' to KeyCorner(ne = "/", se = "\\"), '8' to KeyCorner(ne = "=", se = "~"), '9' to KeyCorner(ne = "[", se = "]"),
                '0' to KeyCorner(ne = "{", se = "}"),
            )
            "math" -> mapOf(
                '1' to KeyCorner(n = "+"), '2' to KeyCorner(n = "-"), '3' to KeyCorner(n = "*"), '4' to KeyCorner(n = "/"), '5' to KeyCorner(n = "="),
                '6' to KeyCorner(n = "."), '7' to KeyCorner(n = ","), '8' to KeyCorner(n = "("), '9' to KeyCorner(n = ")"), '0' to KeyCorner(n = "%", e = "^"),
            )
            else -> emptyMap()
        }
    }

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

    // the glyphs of Unexpected Keyboard's icon font (res/font/kb_special.ttf) for the functions on the corners
    private val glyphs = mapOf("tab" to '\uE00F', "copy" to '\uE030', "cut" to '\uE031', "paste" to '\uE032', "selectAll" to '\uE033', "undo" to '\uE036', "home" to '\uE00B', "end" to '\uE00C')

    /** The icon of a function written on a corner (in the icon font), or null to show its text. */
    fun glyph(t: String): String? = if (t.startsWith("@")) glyphs[t.drop(1)]?.toString() else null
}
