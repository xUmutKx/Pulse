package com.umutk.pulse.extras.keyboard

/**
 * The text of the keyboard's "Text and art" page, in the styles Unicode has for letters: bold, italic, script, fraktur,
 * double-struck, sans, monospace, full width, circled, small caps and a few decorations. Every style is plain text, not emoji.
 */
object FancyText {
    // a style: the code point of A, of a, and of 0 (-1 when the style has no digits); letters it leaves out live elsewhere in Unicode
    private class Style(val name: String, val upper: Int, val lower: Int, val digit: Int, val except: Map<Char, Char> = emptyMap())

    private val styles = listOf(
        Style("bold", 0x1D400, 0x1D41A, 0x1D7CE),
        Style("italic", 0x1D434, 0x1D44E, -1),
        Style("bold italic", 0x1D468, 0x1D482, -1),
        Style("script", 0x1D49C, 0x1D4B6, -1, mapOf('B' to 'ℬ', 'E' to 'ℰ', 'F' to 'ℱ', 'H' to 'ℋ', 'I' to 'ℐ', 'L' to 'ℒ', 'M' to 'ℳ', 'R' to 'ℛ', 'e' to 'ℯ', 'g' to 'ℊ', 'o' to 'ℴ')),
        Style("bold script", 0x1D4D0, 0x1D4EA, -1),
        Style("fraktur", 0x1D504, 0x1D51E, -1, mapOf('C' to 'ℭ', 'H' to 'ℌ', 'I' to 'ℑ', 'R' to 'ℜ', 'Z' to 'ℨ')),
        Style("bold fraktur", 0x1D56C, 0x1D586, -1),
        Style("double-struck", 0x1D538, 0x1D552, 0x1D7D8, mapOf('C' to 'ℂ', 'H' to 'ℍ', 'N' to 'ℕ', 'P' to 'ℙ', 'Q' to 'ℚ', 'R' to 'ℝ', 'Z' to 'ℤ')),
        Style("sans", 0x1D5A0, 0x1D5BA, 0x1D7E2),
        Style("sans bold", 0x1D5D4, 0x1D5EE, 0x1D7EC),
        Style("sans italic", 0x1D608, 0x1D622, -1),
        Style("sans bold italic", 0x1D63C, 0x1D656, -1),
        Style("monospace", 0x1D670, 0x1D68A, 0x1D7F6),
    )

    private val small = mapOf(
        'a' to 'ᴀ', 'b' to 'ʙ', 'c' to 'ᴄ', 'd' to 'ᴅ', 'e' to 'ᴇ', 'f' to 'ꜰ', 'g' to 'ɢ', 'h' to 'ʜ',
        'i' to 'ɪ', 'j' to 'ᴊ', 'k' to 'ᴋ', 'l' to 'ʟ', 'm' to 'ᴍ', 'n' to 'ɴ', 'o' to 'ᴏ', 'p' to 'ᴘ',
        'q' to 'ǫ', 'r' to 'ʀ', 's' to 'ꜱ', 't' to 'ᴛ', 'u' to 'ᴜ', 'v' to 'ᴠ', 'w' to 'ᴡ', 'y' to 'ʏ', 'z' to 'ᴢ',
    )

    private fun block(ch: Char, s: Style): String {
        s.except[ch]?.let { return it.toString() }
        return when {
            ch in 'A'..'Z' -> String(Character.toChars(s.upper + (ch - 'A')))
            ch in 'a'..'z' -> String(Character.toChars(s.lower + (ch - 'a')))
            ch in '0'..'9' && s.digit >= 0 -> String(Character.toChars(s.digit + (ch - '0')))
            else -> ch.toString()
        }
    }

    private fun fullWidth(text: String) = buildString {
        for (ch in text) when {
            ch == ' ' -> append('　')
            ch in '!'..'~' -> append((ch.code - 0x21 + 0xFF01).toChar())
            else -> append(ch)
        }
    }

    private fun circled(text: String) = buildString {
        for (ch in text) when {
            ch in 'A'..'Z' -> append((0x24B6 + (ch - 'A')).toChar())
            ch in 'a'..'z' -> append((0x24D0 + (ch - 'a')).toChar())
            ch in '1'..'9' -> append((0x2460 + (ch - '1')).toChar())
            ch == '0' -> append('⓪')
            else -> append(ch)
        }
    }

    /** Every style for [text], as (style name, text) pairs; the first is the plain text itself. */
    fun all(text: String): List<Pair<String, String>> {
        val t = text.ifEmpty { " " }
        val out = ArrayList<Pair<String, String>>()
        out += "plain" to t
        for (s in styles) out += s.name to t.map { block(it, s) }.joinToString("")
        out += "fullwidth" to fullWidth(t)
        out += "circled" to circled(t)
        out += "small caps" to t.map { small[it.lowercaseChar()]?.toString() ?: it.toString() }.joinToString("")
        out += "reversed" to t.reversed()
        out += "spaced" to t.toList().joinToString(" ")
        out += "sparkle" to "✧ $t ✧"
        return out
    }
}
