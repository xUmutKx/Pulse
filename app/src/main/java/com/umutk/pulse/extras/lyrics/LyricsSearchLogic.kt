package com.umutk.pulse.extras.lyrics

import java.util.Locale

/**
 * Port of LrcLrc's SearchLogic: plain text is one phrase, commas split AND-parts, words under 3 letters are dropped
 * from the query and from the lyrics, matching runs on Turkish-lowercased letters and digits, phrases may span lines.
 */
internal object LyricsSearchLogic {
    const val MIN_WORD = 3

    class Hit(val before: String?, val line: String, val after: String?)

    private fun isWordChar(c: Char) = Character.isLetterOrDigit(c) || Character.isSurrogate(c) || Character.getType(c) == Character.NON_SPACING_MARK.toInt()

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            if (!isWordChar(raw[i])) { i++; continue }
            var j = i
            while (j < raw.length && isWordChar(raw[j])) j++
            if (j - i >= MIN_WORD) {
                if (sb.isNotEmpty()) sb.append(' ')
                for (k in i until j) sb.append(if (raw[k] == 'I') 'ı' else if (raw[k] == 'İ') 'i' else raw[k])
            }
            i = j
        }
        return sb.toString().lowercase(Locale.ROOT)
    }

    /** Normalized AND-parts; parts without a word of 3+ letters are ignored. */
    fun parse(q: String): List<String> = q.split(',', '،', ';').map { normalize(it.trim()) }.filter { it.isNotEmpty() }.distinct()

    /** The first place where every part occurs, shown as the first part's line with its neighbours; null when a part is missing. */
    fun firstHit(lines: List<String>, parts: List<String>): Hit? {
        if (parts.isEmpty()) return null
        val sb = StringBuilder()
        val segLine = ArrayList<Int>(); val segEnd = ArrayList<Int>()
        for ((i, l) in lines.withIndex()) {
            val t = normalize(l)
            if (t.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(t); segLine += i; segEnd += sb.length
        }
        val hay = sb.toString()
        if (parts.any { !hay.contains(it) }) return null
        val at = hay.indexOf(parts[0])
        val seg = segEnd.indexOfFirst { it > at }.takeIf { it >= 0 } ?: return null
        val idx = segLine[seg]
        fun shown(i: Int) = lines.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
        val before = (idx - 1 downTo 0).firstNotNullOfOrNull { shown(it) }
        val after = (idx + 1 until lines.size).firstNotNullOfOrNull { shown(it) }
        return Hit(before, lines[idx].trim(), after)
    }
}
