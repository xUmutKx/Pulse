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

    /** Why the last online lookup failed (network error), or null when it got an answer. Shown under "No lyrics" so a blocked network is not mistaken for a missing song. */
    @Volatile var lastError: String? = null

    suspend fun forTrack(ctx: Context, title: String, artist: String, album: String?, durationMs: Long, localPath: String?): LyricsResult? =
        withContext(Dispatchers.IO) {
            val app = ctx.applicationContext
            // the phone first: the tag, a lyrics file named like the song, then any lyrics file that carries the song's title; the net comes after
            ((localPath?.let { readEmbedded(it) }) ?: readSidecar(localPath.orEmpty(), title) ?: localPath?.let { LyricsStore(app).load(it, artist, title)?.let { r -> toResult(r, "saved") } })
                ?.let { return@withContext it }
            val key = "t|${artist.trim().lowercase()}|${title.trim().lowercase()}"
            cacheGet(app, key)?.let { return@withContext it }
            // a song that had no lyrics online is not asked again for three days
            if (missed(app, key)) return@withContext null
            val client = LrclibClient(LyricsCache(File(app.cacheDir, "lyrics")))
            val sec = (durationMs / 1000).toInt().takeIf { it > 0 }
            val asked = runCatching { smartFind(client, title, artist, album, sec)?.let { it to (if (it.second) "ovh" else "lrclib") }?.let { it.first.first to it.second } }
            // a network error is not "no lyrics": nothing is remembered then; an answer without lyrics is remembered
            lastError = asked.exceptionOrNull()?.let { "${it.javaClass.simpleName}: ${it.message}" }
            val rec = asked.getOrNull() ?: return@withContext null.also { if (asked.isSuccess) markMiss(app, key) }
            val r = toResult(rec.first, rec.second) ?: return@withContext null
            cachePut(app, key, rec.first.syncedLyrics ?: rec.first.plainLyrics.orEmpty(), rec.second)
            r
        }

    /** What the lyrics lookup sees for [path], as a few short lines: the song file, the same-name .lrc / .txt and whether the folder can be read. */
    fun diag(path: String?): String {
        if (path == null) return "no file path (online stream)"
        val f = File(path)
        val dir = f.parentFile
        val lrc = sidecarFile(path, "lrc"); val txt = sidecarFile(path, "txt")
        return "file: ${f.name} (${if (f.isFile) "readable" else "not readable"})\n" +
            "folder listed: ${dir?.list()?.size ?: "no"} files\n" +
            "lrc: ${lrc?.name ?: "none"}  txt: ${txt?.name ?: "none"}\n" +
            "tag lyrics: ${if (runCatching { readEmbedded(path) }.getOrNull() != null) "yes" else "none"}  root: ${com.umutk.pulse.Shell.root == true}"
    }

    /** The lyrics as .lrc text: stamped lines when they are synced, plain lines otherwise. */
    private fun lrcText(r: LyricsResult) = r.lines.joinToString("\n") { l ->
        if (r.synced && l.timeMs >= 0) "[%02d:%02d.%02d]%s".format(l.timeMs / 60000, l.timeMs / 1000 % 60, l.timeMs / 10 % 100, l.text) else l.text
    }

    // ---- fuzzy lookup: spelling, several artists, decorations in the title ----

    private fun fold(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().replace('ı', 'i')
        .filter { it.isLetterOrDigit() }

    /** The title without "(feat. ..)", "[..]", "- Remastered", "- Live" and similar tails. */
    private fun cleanTitle(t: String): String = t.replace(Regex("[\\(\\[][^\\)\\]]*[\\)\\]]"), " ").replace(Regex("(?i)\\s+-\\s+(remaster\\w*|live|radio edit|mono|stereo|version).*$"), "")
        .replace(Regex("(?i)\\s(feat\\.?|ft\\.?|featuring)\\s.*$"), "").trim()

    /** "A, B & C feat. D" -> A, B, C, D. */
    private fun splitArtists(a: String): List<String> = a.split(Regex("(?i)\\s*(,|&|;|/|\\bfeat\\.?\\b|\\bft\\.?\\b|\\band\\b|\\bve\\b| x )\\s*")).map { it.trim() }.filter { it.length > 1 }

    /** 0..1: how alike two names are (edit distance on letters and digits only). */
    private fun alike(a: String, b: String): Float {
        val x = fold(a); val y = fold(b)
        if (x.isEmpty() || y.isEmpty()) return 0f
        if (x == y) return 1f
        if (x.contains(y) || y.contains(x)) return .85f
        val d = Array(x.length + 1) { IntArray(y.length + 1) }
        for (i in 0..x.length) d[i][0] = i
        for (j in 0..y.length) d[0][j] = j
        for (i in 1..x.length) for (j in 1..y.length) d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (x[i - 1] == y[j - 1]) 0 else 1)
        return 1f - d[x.length][y.length].toFloat() / maxOf(x.length, y.length)
    }

    /**
     * The best hit for a song, trying in turn: the names as they are, the clean title with each artist alone, the title alone, then the best look-alike
     * among the search results (title and artist compared by spelling). Returns the record and whether it came from lyrics.ovh.
     */
    private suspend fun smartFind(client: LrclibClient, title: String, artist: String, album: String?, sec: Int?): Pair<LyricsRecord, Boolean>? {
        val titles = listOf(title, cleanTitle(title)).filter { it.isNotBlank() }.distinct()
        val artists = (listOf(artist) + splitArtists(artist)).filter { it.isNotBlank() }.distinct()
        for (t in titles) for (a in artists) client.get(t, a, album, sec)?.takeIf { it.hasLyrics }?.let { return it to false }
        // searches: artist + title, then the title alone; the results are ranked by how alike they are
        val pool = LinkedHashMap<Long, LyricsRecord>()
        val queries = buildList {
            for (t in titles) { for (a in artists.take(3)) add("$a $t"); add(t) }
        }.distinct().take(6)
        for (q in queries) {
            client.search(q.trim()).filter { it.hasLyrics }.forEach { pool.putIfAbsent(it.id, it) }
            if (pool.size >= 8) break
        }
        val wantArtists = (listOf(artist) + splitArtists(artist)).filter { it.isNotBlank() }
        val best = pool.values.map { r ->
            val ts = titles.maxOf { alike(it, r.trackName) }
            val ars = if (wantArtists.isEmpty()) .5f else wantArtists.maxOf { a -> maxOf(alike(a, r.artistName), splitArtists(r.artistName).maxOfOrNull { alike(a, it) } ?: 0f) }
            val dur = 0f
            r to (ts * 0.65f + ars * 0.35f + dur + if (r.syncedLyrics != null) .03f else 0f)
        }.maxByOrNull { it.second }
        if (best != null && best.second >= .62f) return best.first to false
        for (a in artists) for (t in titles) ovhLyrics(a, t)?.let { return it to true }
        return null
    }

    /** Writes [r] as a .lrc next to the song (synced or not), unless one is there already. True when a file was written. */
    fun saveSidecar(path: String, r: LyricsResult): Boolean = runCatching {
        val f = File(path.substringBeforeLast('.') + ".lrc")
        if (f.exists() || sidecarFile(path, "lrc") != null) return@runCatching false
        f.writeText(lrcText(r))
        dirNames.remove(f.parent)
        true
    }.getOrDefault(false)

    /**
     * Puts the lyrics into an MP3's ID3v2 tag (a USLT frame), keeping every other frame. The file is rewritten through a temp copy and swapped in only when
     * the copy is complete. Only plain ID3v2.3 / 2.4 tags are touched (no unsynchronisation, no extended header); anything else returns false and leaves the file alone.
     */
    fun embedMp3(path: String, r: LyricsResult): Boolean = runCatching {
        val f = File(path)
        if (!f.extension.equals("mp3", true) || !f.canWrite()) return@runCatching false
        val text = lrcText(r)
        val head = ByteArray(10)
        val old: ByteArray; val oldLen: Int; val ver: Int
        f.inputStream().use { i ->
            val n = i.read(head)
            if (n == 10 && head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                ver = head[3].toInt()
                if ((ver != 3 && ver != 4) || (head[5].toInt() and 0xC0) != 0) return@runCatching false   // unsynchronised or extended header
                oldLen = (head[6].toInt() shl 21) or (head[7].toInt() shl 14) or (head[8].toInt() shl 7) or head[9].toInt()
                old = ByteArray(oldLen); var got = 0
                while (got < oldLen) { val k = i.read(old, got, oldLen - got); if (k < 0) return@runCatching false; got += k }
            } else { ver = 3; old = ByteArray(0); oldLen = -1 }
        }
        fun size4(v: Int, sync: Boolean) = if (sync) byteArrayOf((v shr 21 and 127).toByte(), (v shr 14 and 127).toByte(), (v shr 7 and 127).toByte(), (v and 127).toByte())
            else byteArrayOf((v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        val out = java.io.ByteArrayOutputStream()
        // the old frames except any USLT; padding (zero ids) is dropped
        var p = 0
        while (p + 10 <= old.size && old[p].toInt() != 0) {
            val id = String(old, p, 4, Charsets.ISO_8859_1)
            val sz = if (ver == 4) (old[p + 4].toInt() shl 21) or (old[p + 5].toInt() shl 14) or (old[p + 6].toInt() shl 7) or old[p + 7].toInt()
                else ((old[p + 4].toInt() and 255) shl 24) or ((old[p + 5].toInt() and 255) shl 16) or ((old[p + 6].toInt() and 255) shl 8) or (old[p + 7].toInt() and 255)
            if (sz < 0 || p + 10 + sz > old.size) return@runCatching false
            if (id != "USLT") out.write(old, p, 10 + sz)
            p += 10 + sz
        }
        // USLT: UTF-16 with a byte order mark, language "eng", empty description, the text
        val body = java.io.ByteArrayOutputStream().apply {
            write(1); write("eng".toByteArray(Charsets.ISO_8859_1))
            write(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0, 0))
            write(byteArrayOf(0xFF.toByte(), 0xFE.toByte())); write(text.toByteArray(Charsets.UTF_16LE))
            write(byteArrayOf(0, 0))
        }.toByteArray()
        out.write("USLT".toByteArray(Charsets.ISO_8859_1)); out.write(size4(body.size, ver == 4)); out.write(byteArrayOf(0, 0)); out.write(body)
        val frames = out.toByteArray()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.outputStream().buffered().use { o ->
            o.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), ver.toByte(), 0, 0)); o.write(size4(frames.size, true)); o.write(frames)
            f.inputStream().buffered().use { i ->
                if (oldLen >= 0) i.skip(10L + oldLen) // the old tag
                i.copyTo(o)
            }
        }
        // swap only a copy that is at least as long as the audio it carries
        if (tmp.length() < f.length() - (if (oldLen >= 0) 10 + oldLen else 0)) { tmp.delete(); return@runCatching false }
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        true
    }.getOrDefault(false)

    /** Reads the lyrics that are on the phone once, in the background, so the first lyrics search is instant (see [LyricsIndex]). */
    suspend fun warmIndex(ctx: Context, tracks: List<com.umutk.pulse.extras.music.Track>) {
        runCatching { LyricsIndex.ensure(ctx.applicationContext, tracks) { p -> sidecarText(p)?.let { stripStamps(it) } ?: embeddedText(p) } }
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
        // read once for the whole library (LyricsIndex), so a search does not open every song file again
        val tracks = MusicLibrary.tracks.value
        val idx = LyricsIndex.ensure(app, tracks) { p -> sidecarText(p)?.let { stripStamps(it) } ?: embeddedText(p) }
        tracks.forEach { t ->
            val text = idx[t.path]?.takeIf { it.isNotEmpty() } ?: return@forEach
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
        return listOf("lrc", "txt").firstNotNullOfOrNull { e -> sidecarFile(path, e)?.let { f -> runCatching { f.readText() }.getOrNull() }?.takeIf { it.isNotBlank() } }
    }

    private fun readSidecar(path: String, title: String? = null): LyricsResult? {
        for (e in listOf("lrc", "txt")) {
            val t = sidecarFile(path, e, title)?.let { runCatching { it.readText() }.getOrNull() } ?: rootSidecar(path, e) ?: continue
            fromText(t, e)?.let { return it }
        }
        return null
    }

    /** When the app may not read the folder (no all-files access), root lists it and reads the same-name file. */
    private fun rootSidecar(path: String, ext: String): String? {
        if (path.isEmpty()) return null
        if (com.umutk.pulse.Shell.root != true) return null
        val song = File(path)
        val want = norm(song.name.substringBeforeLast('.'))
        val names = com.umutk.pulse.Shell.runRoot("ls -1 '${song.parent}' 2>/dev/null").lines()
        val hit = names.firstOrNull { it.endsWith(".$ext", true) && norm(it.substringBeforeLast('.')) == want } ?: return null
        val out = com.umutk.pulse.Shell.runRoot("cat '${song.parent}/${hit.replace("'", "'\\''")}'")
        return out.takeIf { it.isNotBlank() && it.length < 1_000_000 }
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Folders other than the song's own where lyrics files are usually kept. */
    private val lyricDirs = listOf("Lyrics", "lyrics", "Music/Lyrics", "Music/lyrics", "Download/Lyrics", "Download", "Documents/Lyrics", "Documents", "LRC", "Music")

    private val dirNames = HashMap<String, Pair<Long, List<File>>>()

    private fun filesIn(dir: File): List<File> {
        val now = System.currentTimeMillis()
        synchronized(dirNames) {
            dirNames[dir.path]?.takeIf { now - it.first < 60_000 }?.let { return it.second }
            val l = dir.listFiles()?.filter { it.isFile }.orEmpty()
            dirNames[dir.path] = now to l
            return l
        }
    }

    /**
     * The lyrics file of a song: same name next to it (any letter case, spaces and dashes ignored), then the usual
     * lyrics folders by the same rule. Only files below 1 MB count.
     */
    private fun sidecarFile(path: String, ext: String, title: String? = null): File? {
        val song = File(path)
        val base = song.name.substringBeforeLast('.')
        val want = norm(base)
        fun ok(f: File) = f.length() < 1_000_000 && f.extension.equals(ext, true)
        File(song.parentFile, "$base.$ext").takeIf { it.isFile && it.length() < 1_000_000 }?.let { return it }
        song.parentFile?.let { d -> filesIn(d).firstOrNull { ok(it) && norm(it.name.substringBeforeLast('.')) == want }?.let { return it } }
        val root = android.os.Environment.getExternalStorageDirectory()
        for (d in lyricDirs) {
            val dir = File(root, d)
            if (dir.path == song.parent) continue
            filesIn(dir).firstOrNull { ok(it) && norm(it.name.substringBeforeLast('.')) == want }?.let { return it }
        }
        // no file named like the song: one whose name holds the song's title ("Artist - Title.lrc"), next to the song or in the usual folders
        val t = title?.let { norm(it) }?.takeIf { it.length >= 3 }
        if (t != null) {
            val dirs = listOfNotNull(song.parentFile?.takeIf { path.isNotEmpty() }) + lyricDirs.map { File(root, it) }
            for (dir in dirs) filesIn(dir).firstOrNull { ok(it) && norm(it.name.substringBeforeLast('.')).let { n -> n == t || n.endsWith(t) || n.startsWith(t) } }?.let { return it }
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

    private fun missPrefs(c: Context) = c.getSharedPreferences("lyrics_miss", Context.MODE_PRIVATE)
    private fun missed(c: Context, key: String) = System.currentTimeMillis() - missPrefs(c).getLong(sha(key), 0L) < 3L * 24 * 3600 * 1000
    private fun markMiss(c: Context, key: String) = missPrefs(c).edit().putLong(sha(key), System.currentTimeMillis()).apply()

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
