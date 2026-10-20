package com.umutk.pulse.extras.lyrics

import android.content.Context
import com.umutk.pulse.extras.music.MusicLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** [timeMs] is the start of the line; -1 for lines of unsynced lyrics. */
data class LyricLine(val timeMs: Long, val text: String)

/** [source]: "tag", "lrc", "txt", "saved", "cache", "lrclib", "ovh" or "hit". */
data class LyricsResult(val lines: List<LyricLine>, val synced: Boolean, val source: String)

/** One phrase match: [line] contains the phrase, [before] / [after] are the neighbouring lyric lines. [id] is opaque, give it to [Lyrics.forHit]. */
data class LyricHit(val title: String, val artist: String, val before: String?, val line: String, val after: String?, val id: String)

/** Public lyrics API. Order for a track: embedded tag, sidecar .lrc/.txt, saved, disk cache, then online (LRCLIB, lyrics.ovh). */
object Lyrics {

    /** Lyrics stored with the file: the embedded tag first, then a sidecar .lrc or .txt. Null when the file has neither. */
    fun localRecord(path: String, title: String, artist: String): LyricsRecord? {
        val r = readEmbedded(path) ?: readSidecar(path) ?: return null
        if (r.lines.isEmpty()) return null
        // synced lines go back to [mm:ss.xx] text so the panel parses them the same way as a downloaded .lrc
        val text = r.lines.joinToString("\n") { l ->
            if (r.synced && l.timeMs >= 0) "[%02d:%02d.%02d]%s".format(l.timeMs / 60000, l.timeMs / 1000 % 60, l.timeMs / 10 % 100, l.text) else l.text
        }
        return if (r.synced) LyricsRecord(0, title, artist, null, false, null, text) else LyricsRecord(0, title, artist, null, false, text, null)
    }

    suspend fun forTrack(ctx: Context, title: String, artist: String, album: String?, durationMs: Long, localPath: String?): LyricsResult? =
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            if (localPath != null) {
                (readEmbedded(localPath) ?: readSidecar(localPath) ?: LyricsStore(app).load(localPath, artist, title)?.let { toResult(it, "saved") })
                    ?.let { return@withContext it }
            }
            val key = "t|${artist.trim().lowercase()}|${title.trim().lowercase()}"
            cacheGet(app, key)?.let { return@withContext it }
            val client = LrclibClient(LyricsCache(File(app.cacheDir, "lyrics")))
            val sec = (durationMs / 1000).toInt().takeIf { it > 0 }
            val rec = runCatching {
                if (artist.isNotBlank()) client.get(title, artist, album, sec)?.takeIf { it.hasLyrics }?.let { it to "lrclib" } else null
                    ?: client.search("$artist $title".trim()).let { l -> l.firstOrNull { it.hasLyrics && it.syncedLyrics != null } ?: l.firstOrNull { it.hasLyrics } }?.let { it to "lrclib" }
                    ?: if (artist.isNotBlank()) ovhLyrics(artist, title)?.let { it to "ovh" } else null
            }.getOrNull() ?: return@withContext null // shortcut: a network error looks like "not found", nothing is cached then
            val r = toResult(rec.first, rec.second) ?: return@withContext null
            cachePut(app, key, rec.first.syncedLyrics ?: rec.first.plainLyrics.orEmpty(), rec.second)
            r
        }

    /** Songs whose lyrics contain [query] (LrcLrc rules: plain text is a phrase, commas are AND, words under 3 letters are ignored). */
    suspend fun searchByText(ctx: Context, query: String): List<LyricHit> = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val q = LyricsSearchLogic.parse(query)
        if (q.isEmpty()) return@withContext emptyList()
        val out = ArrayList<LyricHit>()
        fun test(title: String, artist: String, text: String, id: String): Boolean {
            val h = LyricsSearchLogic.firstHit(text.lines(), q) ?: return false
            out += LyricHit(title, artist, h.before, h.line, h.after, id); return true
        }
        val seen = HashSet<String>()
        // device: lyrics files beside the music, then the lyrics saved from Pulse
        MusicLibrary.tracks.value.forEach { t ->
            // the lyrics beside the file, else the ones inside its tag (mp3 USLT / SYLT, flac)
            val text = sidecarText(t.path)?.let { stripStamps(it) } ?: embeddedText(t.path) ?: return@forEach
            val (a, ti) = t.artist.orEmpty() to (t.title ?: t.path.substringAfterLast('/').substringBeforeLast('.'))
            if (test(ti, a, text, "file:${t.path}")) seen += "${a.lowercase()}|${ti.lowercase()}"
        }
        runCatching {
            LyricsStore(app).list().forEach { r ->
                if (!seen.add("${r.artistName.lowercase()}|${r.trackName.lowercase()}")) return@forEach
                val text = stripStamps(r.syncedLyrics ?: r.plainLyrics ?: return@forEach)
                if (test(r.trackName, r.artistName, text, hitId(app, r))) Unit
            }
        }
        // online: LRCLIB searches titles and artists, so this finds a song when the phrase is part of its name or the artist is typed too
        runCatching {
            LrclibClient(LyricsCache(File(app.cacheDir, "lyrics"))).search(query.trim()).filter { it.hasLyrics }.forEach { r ->
                if (!seen.add("${r.artistName.lowercase()}|${r.trackName.lowercase()}")) return@forEach
                test(r.trackName, r.artistName, stripStamps(r.syncedLyrics ?: r.plainLyrics ?: return@forEach), hitId(app, r))
            }
        }
        out
    }

    suspend fun forHit(ctx: Context, hit: LyricHit): LyricsResult? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        when {
            hit.id.startsWith("file:") -> hit.id.removePrefix("file:").let { readEmbedded(it) ?: readSidecar(it) }
            hit.id.startsWith("rec:") -> runCatching { LyricsRecord.fromJson(JSONObject(File(hitDir(app), hit.id.removePrefix("rec:") + ".json").readText())) }.getOrNull()?.let { toResult(it, "hit") }
            else -> null
        }
    }

    // ---- helpers ----

    internal fun toResult(r: LyricsRecord, source: String): LyricsResult? {
        if (r.instrumental) return null
        return fromText(r.syncedLyrics?.takeIf { it.isNotBlank() } ?: r.plainLyrics ?: return null, source)
    }

    /** LRC text gives synced lines, anything else becomes unsynced lines. */
    internal fun fromText(text: String, source: String): LyricsResult? {
        if (text.isBlank()) return null
        val timed = parseLrc(text)
        if (timed.isNotEmpty()) return LyricsResult(timed.map { LyricLine(it.timeMs, it.text) }, true, source)
        return LyricsResult(text.lines().map { LyricLine(-1, it.trim()) }, false, source)
    }

    private val stampRe = Regex("""\[\d+:\d{1,2}(?:[.:]\d{1,3})?]""")
    private val tagRe = Regex("""^\s*\[[a-zA-Z]+:[^\]]*]\s*$""")

    private fun stripStamps(t: String) = t.lines().filterNot { tagRe.matches(it) }.joinToString("\n") { it.replace(stampRe, "").trim() }

    private fun sidecarText(path: String): String? {
        val base = path.substringBeforeLast('.')
        return listOf("lrc", "txt").firstNotNullOfOrNull { e -> File("$base.$e").takeIf { it.isFile && it.length() < 1_000_000 }?.let { f -> runCatching { f.readText() }.getOrNull() }?.takeIf { it.isNotBlank() } }
    }

    private fun readSidecar(path: String): LyricsResult? {
        val base = path.substringBeforeLast('.')
        for (e in listOf("lrc", "txt")) {
            val t = File("$base.$e").takeIf { it.isFile && it.length() < 1_000_000 }?.let { runCatching { it.readText() }.getOrNull() } ?: continue
            fromText(t, e)?.let { return it }
        }
        return null
    }

    private fun embeddedText(path: String): String? =
        readEmbedded(path)?.lines?.joinToString("\n") { it.text }?.takeIf { it.isNotBlank() }

    private fun readEmbedded(path: String): LyricsResult? = runCatching {
        val f = File(path).takeIf { it.isFile } ?: return null
        val tag = when (f.extension.lowercase()) {
            "mp3" -> Id3.lyrics(f)
            "flac" -> flacLyrics(f)
            else -> null // shortcut: m4a / ogg / opus tags are not read, add atom and Ogg comment parsing if needed
        } ?: return null
        tag.synced?.let { return LyricsResult(it, true, "tag") }
        fromText(tag.text ?: return null, "tag")
    }.getOrNull()

    // ---- disk cache: filesDir/lyrics-cache/<sha>.txt, first line "source", rest the text ----

    private fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
    private fun cacheFile(c: Context, key: String) = File(c.filesDir, "lyrics-cache").apply { mkdirs() }.let { File(it, sha(key) + ".txt") }

    private fun cacheGet(c: Context, key: String): LyricsResult? = runCatching {
        val t = cacheFile(c, key).takeIf { it.isFile }?.readText() ?: return null
        fromText(t.substringAfter('\n'), "cache")
    }.getOrNull()

    private fun cachePut(c: Context, key: String, text: String, source: String) {
        if (text.isNotBlank()) runCatching { cacheFile(c, key).writeText(source + "\n" + text) }
    }

    private fun hitDir(c: Context) = File(c.filesDir, "lyrics-hits").apply { mkdirs() }

    private fun hitId(c: Context, r: LyricsRecord): String {
        val id = sha("${r.artistName}|${r.trackName}|${r.id}".lowercase())
        val o = JSONObject().put("id", r.id).put("trackName", r.trackName).put("artistName", r.artistName)
            .put("plainLyrics", r.plainLyrics ?: "").put("syncedLyrics", r.syncedLyrics ?: "")
        runCatching { File(hitDir(c), "$id.json").writeText(o.toString()) }
        return "rec:$id"
    }
}

internal class TagLyrics(val text: String?, val synced: List<LyricLine>?)

private fun flacLyrics(f: File): TagLyrics? {
  RandomAccessFile(f, "r").use { r ->
    val head = ByteArray(4); r.readFully(head)
    if (String(head, Charsets.ISO_8859_1) != "fLaC") return null
    while (true) {
        val h = r.read(); if (h < 0) return null
        val len = (r.read() shl 16) or (r.read() shl 8) or r.read()
        if (h and 0x7F == 4) {
            if (len > 8_000_000) return null
            val b = ByteArray(len); r.readFully(b)
            fun le(o: Int) = (b[o].toInt() and 255) or ((b[o + 1].toInt() and 255) shl 8) or ((b[o + 2].toInt() and 255) shl 16) or ((b[o + 3].toInt() and 255) shl 24)
            var p = 4 + le(0)
            val n = le(p); p += 4
            repeat(n) {
                val l = le(p); p += 4
                if (l < 0 || p + l > b.size) return null
                val kv = String(b, p, l, Charsets.UTF_8); p += l
                val k = kv.substringBefore('=').uppercase()
                if (k == "LYRICS" || k == "UNSYNCEDLYRICS" || k == "SYNCEDLYRICS") return TagLyrics(kv.substringAfter('='), null)
            }
            return null
        }
        if (h and 0x80 != 0) return null
        r.seek(r.filePointer + len)
    }
  }
  return null
}

/** Minimal ID3v2 reader: USLT (text, may hold LRC stamps) and SYLT (millisecond timestamps). */
internal object Id3 {
    fun lyrics(f: File): TagLyrics? {
        val d = RandomAccessFile(f, "r").use { r ->
            val h = ByteArray(10); r.readFully(h)
            if (String(h, 0, 3, Charsets.ISO_8859_1) != "ID3") return null
            val size = (h[6].toInt() shl 21) or (h[7].toInt() shl 14) or (h[8].toInt() shl 7) or h[9].toInt()
            val b = ByteArray(size.coerceIn(0, 6_000_000)); r.readFully(b)
            Triple(h[3].toInt(), h[5].toInt(), b)
        }
        val (ver, flags, b) = d
        if (ver !in 2..4) return null
        var p = 0
        if (ver >= 3 && flags and 0x40 != 0 && b.size >= 4) p = (if (ver == 4) synchsafe(b, 0) else be(b, 0, 4) + 4)
        var uslt: String? = null
        var sylt: List<LyricLine>? = null
        val idLen = if (ver == 2) 3 else 4
        val hdr = if (ver == 2) 6 else 10
        while (p + hdr <= b.size && b[p].toInt() != 0) {
            val id = String(b, p, idLen, Charsets.ISO_8859_1)
            val sz = when (ver) { 2 -> be(b, p + 3, 3); 4 -> synchsafe(b, p + 4); else -> be(b, p + 4, 4) }
            val s = p + hdr
            if (sz < 0 || s + sz > b.size) break
            if (id == "USLT" || id == "ULT") uslt = uslt ?: unsyncText(b, s, sz)
            if (id == "SYLT" || id == "SLT") sylt = sylt ?: syncText(b, s, sz)
            p = s + sz
        }
        return if (uslt == null && sylt == null) null else TagLyrics(uslt, sylt)
    }

    private fun be(b: ByteArray, o: Int, n: Int): Int { var v = 0; for (i in 0 until n) v = (v shl 8) or (b[o + i].toInt() and 255); return v }
    private fun synchsafe(b: ByteArray, o: Int) = (b[o].toInt() shl 21) or (b[o + 1].toInt() shl 14) or (b[o + 2].toInt() shl 7) or b[o + 3].toInt()

    private fun charset(enc: Int) = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }

    /** Index after the terminator of the string that starts at [from]. */
    private fun skipTerm(b: ByteArray, from: Int, end: Int, enc: Int): Int {
        var i = from
        if (enc == 1 || enc == 2) { while (i + 1 < end && !(b[i].toInt() == 0 && b[i + 1].toInt() == 0)) i += 2; return i + 2 }
        while (i < end && b[i].toInt() != 0) i++
        return i + 1
    }

    private fun unsyncText(b: ByteArray, s: Int, sz: Int): String? {
        if (sz < 5) return null
        val enc = b[s].toInt()
        val t = skipTerm(b, s + 4, s + sz, enc)
        return if (t >= s + sz) null else String(b, t, s + sz - t, charset(enc))
    }

    private fun syncText(b: ByteArray, s: Int, sz: Int): List<LyricLine>? {
        if (sz < 7 || b[s + 4].toInt() != 2) return null // only millisecond stamps
        val enc = b[s].toInt()
        val end = s + sz
        var p = skipTerm(b, s + 6, end, enc)
        val out = ArrayList<LyricLine>()
        while (p < end) {
            val q = skipTerm(b, p, end, enc)
            if (q + 4 > end) break
            val txtEnd = q - (if (enc == 1 || enc == 2) 2 else 1)
            out += LyricLine(be(b, q, 4).toLong() and 0xFFFFFFFFL, String(b, p, (txtEnd - p).coerceAtLeast(0), charset(enc)).trim('\n', '\r'))
            p = q + 4
        }
        return out.takeIf { it.isNotEmpty() }?.sortedBy { it.timeMs }
    }
}
