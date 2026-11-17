package com.umutk.pulse.extras.lyrics

/**
 * Lyrics with a time for every word (or syllable), as an enhanced LRC: `[mm:ss.xx]<mm:ss.xx>word <mm:ss.xx>word <mm:ss.xx>` (the last tag has no text and ends the last word).
 * Two sources give such lyrics: LRCLIB's word-synced records ("lyricsfile", a small YAML) and NetEase's "yrc". Both are turned into this one text,
 * which [parseLrc] reads and the lyric views draw word by word; a song without word times falls back to its lines, and then to plain text.
 */
object WordLyrics {
    private val wordTag = Regex("""<\d+:\d{1,2}(?:[.:]\d{1,3})?>""")

    fun hasWordTags(lrc: String) = wordTag.containsMatchIn(lrc)

    fun stamp(ms: Long): String { val m = ms.coerceAtLeast(0L); return "%02d:%02d.%02d".format(m / 60000, m / 1000 % 60, m / 10 % 100) }

    /** One enhanced line: the line's start, each word with its start, and a closing tag with the end of the last word. */
    fun line(startMs: Long, words: List<LyricWord>, endMs: Long = 0L): String {
        val sb = StringBuilder("[").append(stamp(startMs)).append(']')
        words.forEach { sb.append('<').append(stamp(it.timeMs)).append('>').append(it.text) }
        val end = endMs.takeIf { it > 0 } ?: words.lastOrNull()?.endMs?.takeIf { it > 0 }
        if (end != null) sb.append('<').append(stamp(end)).append('>')
        return sb.toString()
    }

    /**
     * LRCLIB's "lyricsfile": a YAML with `lines:` of `- text:`, `start_ms:`, `end_ms:` and `words:` of `- text:`, `start_ms:` (the text may be quoted with '...' and ends in a space
     * between words). Read line by line for exactly that shape (no YAML library); null when no line has two words with times.
     */
    fun fromLyricsFile(yaml: String): String? {
        class W(val text: String, var start: Long = -1)
        class L(val text: String, var start: Long = -1, var end: Long = -1, val words: ArrayList<W> = ArrayList())
        val lines = ArrayList<L>()
        var cur: L? = null; var word: W? = null; var inLines = false
        fun unq(v: String): String {
            val t = v.trim()
            return when {
                t.length >= 2 && t.startsWith("'") && t.endsWith("'") -> t.substring(1, t.length - 1).replace("''", "'")
                t.length >= 2 && t.startsWith("\"") && t.endsWith("\"") -> t.substring(1, t.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
                else -> t
            }
        }
        for (raw in yaml.lineSequence()) {
            if (raw.isBlank()) continue
            val indent = raw.length - raw.trimStart().length
            val t = raw.trim()
            if (!inLines) { if (t == "lines:") inLines = true; continue }
            if (indent == 0 && !t.startsWith("-")) break   // the next top-level key ends the list
            val num = { key: String -> t.removePrefix(key).trim().toLongOrNull() ?: -1L }
            when {
                // the list of lines sits at the left edge, its fields and its list of words two spaces in, the words' fields four in
                indent == 0 && t.startsWith("- text:") -> { cur = L(unq(t.removePrefix("- text:"))); lines += cur; word = null }
                indent == 2 && t.startsWith("- text:") -> { word = W(unq(t.removePrefix("- text:"))); cur?.words?.add(word) }
                indent == 2 && t.startsWith("start_ms:") -> cur?.start = num("start_ms:")
                indent == 2 && t.startsWith("end_ms:") -> cur?.end = num("end_ms:")
                indent == 4 && t.startsWith("start_ms:") -> word?.start = num("start_ms:")
            }
        }
        val out = ArrayList<String>()
        var anyWords = false
        for (l in lines) {
            if (l.start < 0) continue
            val ws = l.words.filter { it.start >= 0 && it.text.isNotEmpty() }.map { LyricWord(it.start, it.text) }
            if (ws.size >= 2) { anyWords = true; out += line(l.start, ws, l.end) }
            else out += "[" + stamp(l.start) + "]" + l.text.trim()
        }
        return if (anyWords) out.joinToString("\n") else null
    }

    /**
     * NetEase's "yrc": `[lineStart,lineDuration](wordStart,wordDuration,0)word(wordStart,wordDuration,0)word ...`, times in milliseconds. The credit lines at the top
     * (composer, lyricist: lines with a colon and only one or two words in the first seconds) are dropped.
     */
    fun fromYrc(yrc: String): String? {
        val lineRe = Regex("""^\[(\d+),(\d+)]""")
        val wordRe = Regex("""\((\d+),(\d+),-?\d+\)""")
        val out = ArrayList<String>()
        var anyWords = false
        for (raw in yrc.lineSequence()) {
            val m = lineRe.find(raw.trim()) ?: continue
            val start = m.groupValues[1].toLong(); val dur = m.groupValues[2].toLong()
            val body = raw.trim().substring(m.range.last + 1)
            val tags = wordRe.findAll(body).toList()
            val words = tags.mapIndexedNotNull { i, t ->
                val txt = body.substring(t.range.last + 1, if (i + 1 < tags.size) tags[i + 1].range.first else body.length)
                if (txt.isEmpty()) null else LyricWord(t.groupValues[1].toLong(), txt, t.groupValues[1].toLong() + t.groupValues[2].toLong())
            }
            val text = words.joinToString("") { it.text }.trim()
            if (text.isEmpty()) continue
            if (isCredit(text)) continue
            if (words.size >= 2) { anyWords = true; out += line(start, words, words.last().endMs) }
            else out += "[" + stamp(start) + "]" + text
            if (dur <= 0 && words.isEmpty()) continue
        }
        return if (anyWords) out.joinToString("\n") else null
    }

    private val creditRe = Regex("""^(作词|作曲|编曲|制作人|制作|监制|混音|母带|录音|吉他|贝斯|鼓|和声|词|曲|lyrics?|music|composer|lyricist|arranger|arranged|producer|produced|mixed|mastering|written)\s*[:：]""", RegexOption.IGNORE_CASE)
    private fun isCredit(t: String) = creditRe.containsMatchIn(t)

    /** A plain LRC (NetEase "lrc"), credit lines dropped. */
    fun cleanLrc(lrc: String): String? {
        val keep = lrc.lineSequence().filter { l ->
            val body = l.substringAfter(']', l).trim()
            body.isNotEmpty() && !isCredit(body)
        }.toList()
        return if (keep.any { Regex("""^\[\d+:\d{1,2}""").containsMatchIn(it) }) keep.joinToString("\n") else null
    }
}
