package com.umutk.pulse.extras.lyrics

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One LRCLIB record. [syncedLyrics] is LRC text (`[mm:ss.xx]line`), [plainLyrics] is the unsynced text. */
data class LyricsRecord(
    val id: Long,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val instrumental: Boolean,
    val plainLyrics: String?,
    val syncedLyrics: String?,
) {
    val hasLyrics get() = !instrumental && (!syncedLyrics.isNullOrBlank() || !plainLyrics.isNullOrBlank())

    companion object {
        fun fromJson(o: JSONObject) = LyricsRecord(
            id = o.optLong("id"),
            trackName = o.optString("trackName"),
            artistName = o.optString("artistName"),
            albumName = o.optString("albumName", "").ifBlank { null },
            instrumental = o.optBoolean("instrumental"),
            plainLyrics = o.optString("plainLyrics", "").ifBlank { null },
            syncedLyrics = o.optString("syncedLyrics", "").ifBlank { null },
        )
    }
}

data class LrcLine(val timeMs: Long, val text: String)

private val wordTag = Regex("""<\d+:\d{1,2}(?:[.:]\d{1,3})?>""")
private val onlyNumbers = Regex("""[\d:.,\s\-–>\[\]]+""")
private val stamp = Regex("""\[(\d+):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** Timed lines sorted by time. A line with several stamps is repeated for each one. Header tags like `[ti:...]` are skipped. */
fun parseLrc(lrc: String): List<LrcLine> {
    val out = ArrayList<LrcLine>()
    for (raw in lrc.lines()) {
        val stamps = stamp.findAll(raw).toList()
        if (stamps.isEmpty()) continue
        // word-by-word tags <00:12.30>, stray stamps in the text, and lines that are only numbers (a broken file shows seconds) are not lyrics
        val text = raw.substring(stamps.last().range.last + 1).replace(wordTag, "").replace(stamp, "").trim()
        if (text.isNotEmpty() && onlyNumbers.matches(text)) continue
        for (m in stamps) {
            val fraction = m.groupValues[3].padEnd(3, '0').toLong() // "05" is 50 ms, "5" is 500 ms
            val ms = m.groupValues[1].toLong() * 60_000 + m.groupValues[2].toLong() * 1_000 + fraction
            out += LrcLine(ms, text)
        }
    }
    return out.sortedBy { it.timeMs }
}

/** Index of the line that is current at [posMs], or -1 before the first line. */
fun lineIndexAt(lines: List<LrcLine>, posMs: Long): Int = lines.indexOfLast { it.timeMs <= posMs }

private val illegal = Regex("""[/\\:*?"<>|\p{Cntrl}]""")

/** "Artist - Track" with forbidden characters removed, the whole name (extension included) at most 200 characters. */
fun lyricsFileName(artist: String, track: String, ext: String, n: Int = 1): String {
    val suffix = if (n > 1) " ($n)" else ""
    val base = listOf(artist, track).map { it.replace(illegal, " ").replace(Regex("\\s+"), " ").trim() }
        .filter { it.isNotEmpty() }.joinToString(" - ").ifEmpty { "lyrics" }
    val room = 200 - suffix.length - ext.length - 1
    return base.take(room).trimEnd() + suffix + "." + ext
}

/** Writes the synced lyrics as .lrc, or the plain text as .txt. Null for instrumental tracks or when there is no text. */
fun saveLyrics(dir: File, record: LyricsRecord): File? {
    if (record.instrumental) return null
    val synced = record.syncedLyrics?.takeIf { it.isNotBlank() }
    val (ext, body) = when {
        synced != null -> "lrc" to synced
        record.plainLyrics != null -> "txt" to record.plainLyrics
        else -> return null
    }
    dir.mkdirs()
    var n = 1
    var target = File(dir, lyricsFileName(record.artistName, record.trackName, ext, n))
    while (target.exists()) {
        n++
        target = File(dir, lyricsFileName(record.artistName, record.trackName, ext, n))
    }
    target.writeText(body, Charsets.UTF_8)
    return target
}

/** Saved lyrics go to app storage, which needs no permission and is removed with the app. */
fun defaultLyricsDir(ctx: Context): File = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "lyrics")
