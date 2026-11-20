package com.umutk.pulse.extras.keyboard

/** The grammar check, offline and rule-based (a small set of the plain mistakes): spaces, repeated words, capitals at the start of a sentence, "i" and the apostrophes of English. */
object KbGrammar {
    private val en = mapOf(
        "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "wasnt" to "wasn't", "arent" to "aren't", "werent" to "weren't",
        "shouldnt" to "shouldn't", "wouldnt" to "wouldn't", "couldnt" to "couldn't", "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't", "im" to "I'm", "ive" to "I've",
        "youre" to "you're", "theyre" to "they're", "thats" to "that's", "whats" to "what's", "alot" to "a lot", "ill" to "I'll",
    )

    /** [text] put right for the language [lang] ("tr" or "en"); the same text when nothing needs it. */
    fun fix(text: String, lang: String): String {
        if (text.isBlank()) return text
        val loc = if (lang == "tr") java.util.Locale("tr", "TR") else java.util.Locale.ENGLISH
        var t = text
        // a link or an address is left as it is
        if (t.contains("://") || t.contains('@')) return text
        t = t.replace(Regex("[ \\t]{2,}"), " ")
        t = t.replace(Regex("\\s+([,.;:!?])"), "$1")
        // a space behind a mark that is followed by a letter (not in numbers like 3.5 or in "a.m.")
        t = t.replace(Regex("([,;:!?])(\\p{L})"), "$1 $2")
        t = t.replace(Regex("(\\p{L}{2,})\\.(\\p{Lu}\\p{Ll})"), "$1. $2")
        // a word said twice in a row
        t = t.replace(Regex("\\b(\\p{L}+)\\s+\\1\\b", RegexOption.IGNORE_CASE)) { m -> val w = m.groupValues[1]; if (w.length >= 2) w else m.value }
        if (lang != "tr") {
            t = t.replace(Regex("\\bi\\b"), "I")
            t = t.replace(Regex("\\b(\\p{L}+)\\b")) { m -> val w = m.value; val lo = w.lowercase(); val r = en[lo]; if (r != null && (lo != "ill" && lo != "im" || w.length == lo.length && w == lo)) (if (w[0].isUpperCase() && r[0].isLowerCase()) r.replaceFirstChar { it.uppercase() } else r) else w }
        }
        // the first letter of the text and of every sentence is a capital
        val sb = StringBuilder(t)
        var up = true
        for (i in sb.indices) {
            val ch = sb[i]
            if (up && ch.isLetter()) { sb.setCharAt(i, ch.toString().uppercase(loc)[0]); up = false }
            else if (ch.isLetter() || ch.isDigit()) up = false
            if (ch == '.' || ch == '!' || ch == '?') up = true
        }
        return sb.toString()
    }
}
