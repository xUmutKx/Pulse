package com.umutk.pulse.extras.video

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * Subtitles read by Pulse itself: a downloaded or picked file becomes a list of timed lines, and the player's subtitle view shows the line for the moment
 * (see VideoPlayer). Nothing is handed to the media engine as a side track, so the video is not reloaded and no track has to be selected: it works on any video.
 */
internal class SubCue(val start: Long, val end: Long, val text: String)

/** Text of a subtitle file: UTF-8 when the bytes are valid UTF-8, else the chosen encoding, else Turkish / Western Windows pages. */
internal fun decodeSubBytes(bytes: ByteArray, enc: String): String {
    var b = bytes
    // a gzip answer
    if (b.size > 2 && b[0] == 0x1f.toByte() && b[1] == 0x8b.toByte()) b = runCatching { java.util.zip.GZIPInputStream(b.inputStream()).use { it.readBytes() } }.getOrDefault(b)
    val utf16 = b.size >= 2 && ((b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) || (b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()))
    if (utf16) return String(b, Charsets.UTF_16).removePrefix("﻿")
    val strict = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    runCatching { return strict.decode(ByteBuffer.wrap(b)).toString().removePrefix("﻿") }
    for (name in listOf(enc, "windows-1254", "windows-1252", "ISO-8859-1")) {
        if (name.equals("UTF-8", true)) continue
        val cs = runCatching { java.nio.charset.Charset.forName(name) }.getOrNull() ?: continue
        return String(b, cs)
    }
    return String(b, Charsets.ISO_8859_1)
}

private val timeRe = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})""")
private val htmlRe = Regex("<[^>]*>")
private val braceRe = Regex("""\{[^}]*\}""")

private fun ms(m: MatchResult): Long {
    val h = m.groupValues[1].toLongOrNull() ?: 0L
    val frac = m.groupValues[4].padEnd(3, '0').take(3).toLong()
    return ((h * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 + frac
}

private fun clean(t: String): String = braceRe.replace(htmlRe.replace(t, ""), "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").trim()

/** The timed lines of an .srt, .vtt or .ass / .ssa text, in time order. Empty when the text holds none. */
internal fun parseCues(text: String, name: String): List<SubCue> {
    val ext = name.substringAfterLast('.', "srt").lowercase()
    val out = ArrayList<SubCue>()
    if (ext == "ass" || ext == "ssa" || text.contains("[Events]")) {
        val assRe = Regex("""(\d+):(\d{2}):(\d{2})\.(\d{1,2})""")
        text.lineSequence().filter { it.startsWith("Dialogue:") }.forEach { l ->
            val parts = l.removePrefix("Dialogue:").split(',', limit = 10)
            if (parts.size < 10) return@forEach
            val a = assRe.find(parts[1].trim()) ?: return@forEach
            val b = assRe.find(parts[2].trim()) ?: return@forEach
            fun t(m: MatchResult) = ((m.groupValues[1].toLong() * 60 + m.groupValues[2].toLong()) * 60 + m.groupValues[3].toLong()) * 1000 + m.groupValues[4].padEnd(2, '0').take(2).toLong() * 10
            val line = clean(parts[9].replace("\\N", "\n").replace("\\n", "\n"))
            if (line.isNotEmpty()) out += SubCue(t(a), t(b), line)
        }
    } else {
        for (block in text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n{2,}"))) {
            val lines = block.split('\n')
            val ti = lines.indexOfFirst { "-->" in it }
            if (ti < 0) continue
            val times = timeRe.findAll(lines[ti]).toList()
            if (times.size < 2) continue
            val body = clean(lines.drop(ti + 1).joinToString("\n"))
            if (body.isNotEmpty()) out += SubCue(ms(times[0]), ms(times[1]), body)
        }
    }
    return out.sortedBy { it.start }
}

/** The line to show at [posMs] (the player's position minus the delay the person chose), or "" between lines. */
internal fun cueAt(cues: List<SubCue>, posMs: Long): String {
    if (cues.isEmpty()) return ""
    var lo = 0; var hi = cues.size - 1; var idx = -1
    while (lo <= hi) { val mid = (lo + hi) ushr 1; if (cues[mid].start <= posMs) { idx = mid; lo = mid + 1 } else hi = mid - 1 }
    // two lines can overlap: the nearest earlier ones are checked too
    var i = idx; var n = 0
    val sb = ArrayList<String>()
    while (i >= 0 && n < 4) { if (posMs < cues[i].end) sb.add(0, cues[i].text); i--; n++ }
    return sb.joinToString("\n")
}
