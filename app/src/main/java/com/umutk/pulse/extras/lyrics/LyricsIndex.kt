package com.umutk.pulse.extras.lyrics

import android.content.Context
import com.umutk.pulse.extras.music.Track
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The lyrics that are already on the phone (a .lrc / .txt beside the song, or inside its tag), read once and kept in one file.
 * Searching the lyrics then never opens a song file again: only the songs that are new since the last pass are read.
 */
object LyricsIndex {
    private val text = HashMap<String, String>()
    private var loaded = false
    private val lock = Any()

    private fun file(c: Context) = File(c.filesDir, "lyrics_index.gz")

    private fun load(c: Context) {
        if (loaded) return
        loaded = true
        runCatching {
            DataInputStream(GZIPInputStream(file(c).inputStream().buffered())).use { i ->
                repeat(i.readInt()) { text[i.readUTF()] = i.readUTF().let { s -> if (s.length == 1 && s[0] == '\u0000') "" else s } }
            }
        }
    }

    private fun save(c: Context) = runCatching {
        val tmp = File(file(c).path + ".tmp")
        DataOutputStream(GZIPOutputStream(tmp.outputStream().buffered())).use { o ->
            o.writeInt(text.size)
            text.forEach { (p, t) ->
                o.writeUTF(p)
                // writeUTF holds 64 KB at most; a song's lyrics are far below that
                o.writeUTF(if (t.isEmpty()) "\u0000" else t.take(20_000))
            }
        }
        tmp.renameTo(file(c))
    }

    /** The index for [tracks]: the new ones are read now (off the main thread), the rest comes from the file. [read] gives a song's lyric text or null. */
    suspend fun ensure(c: Context, tracks: List<Track>, read: (String) -> String?): Map<String, String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            load(c)
            var changed = false
            for (t in tracks) {
                if (t.path in text) continue
                text[t.path] = runCatching { read(t.path) }.getOrNull().orEmpty()
                changed = true
            }
            if (changed) save(c)
            text
        }
    }

    /** Lyrics written next to or inside [path] as the index knows them (empty before the first pass). */
    fun has(path: String): Boolean = text[path]?.isNotEmpty() == true
}
