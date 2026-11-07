package com.umutk.pulse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * The text search of the Office viewers (Word and PDF). By default it does not mind capitals and the Turkish letters: "ogrenci" finds "ÖĞRENCİ", "irmak" finds "IRMAK" and "ırmak".
 * With [case] on the letters must be the very same. Every letter folds to exactly one letter, so a position in the folded text is a position in the original.
 */
object OfFind {
    /** Match case: set by the search bar of the open viewer. */
    var case by mutableStateOf(false)

    private fun foldChar(c: Char): Char = when (c) {
        'İ', 'I', 'ı', 'i' -> 'i'
        'Ş', 'ş' -> 's'
        'Ğ', 'ğ' -> 'g'
        'Ü', 'ü' -> 'u'
        'Ö', 'ö' -> 'o'
        'Ç', 'ç' -> 'c'
        'Â', 'â', 'Ä', 'ä', 'Á', 'á', 'À', 'à' -> 'a'
        'Î', 'î', 'Í', 'í' -> 'i'
        'Û', 'û', 'Ú', 'ú' -> 'u'
        'É', 'é', 'È', 'è', 'Ê', 'ê' -> 'e'
        'Ó', 'ó' -> 'o'
        ' ', ' ', ' ' -> ' '
        else -> c.lowercaseChar()
    }

    /** [s] folded letter by letter (same length); with [matchCase] it stays as it is. */
    fun fold(s: CharSequence, matchCase: Boolean = case): String =
        if (matchCase) s.toString() else buildString(s.length) { for (ch in s) append(foldChar(ch)) }

    /** The start of every match of [query] in [text] (the matches do not overlap); empty for an empty query. */
    fun starts(text: CharSequence, query: String, matchCase: Boolean = case): List<Int> {
        val q = fold(query.trim(), matchCase)
        if (q.isEmpty()) return emptyList()
        val t = fold(text, matchCase)
        val out = ArrayList<Int>()
        var at = t.indexOf(q)
        while (at >= 0) { out.add(at); at = t.indexOf(q, at + q.length) }
        return out
    }

    /** Length of a match of [query] (the same as the trimmed, folded query). */
    fun length(query: String): Int = query.trim().length
}
