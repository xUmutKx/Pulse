package com.umutk.pulse.extras.lyrics

import android.content.Context
import com.umutk.pulse.extras.music.Track
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The lyrics that are already on the phone (a .lrc / .txt beside the song, or inside its tag), read once and kept in one file.
 * Searching the lyrics then never opens a song file again: only the songs that are new since the last pass are read.
 *
 * Reading the files never holds the lock that a search takes: a search gets [snapshot] at once with the songs indexed so far,
 * while [ensure] reads the rest in the background and adds them as it goes.
 */
object LyricsIndex {
    private val text = HashMap<String, String>()
    private var loaded = false
    private val lock = Any()
    // one background pass at a time (two scans must not read the same songs twice)
    private val pass = Mutex()

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

    /** Writes the index. Called with the lock held. */
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

    /** The songs indexed so far, as a copy. Never reads a song file, so it returns at once. */
    fun snapshot(c: Context): Map<String, String> = synchronized(lock) {
        load(c)
        HashMap(text)
    }

    /**
     * Reads the songs of [tracks] that are not indexed yet (off the main thread) and saves the index every 200 songs.
     * [read] gives a song's lyric text or null. Searches are not blocked while this runs.
     */
    suspend fun ensure(c: Context, tracks: List<Track>, read: (String) -> String?) = withContext(Dispatchers.IO) {
        pass.withLock {
            val todo = synchronized(lock) { load(c); tracks.map { it.path }.filter { it !in text } }
            var n = 0
            for (p in todo) {
                // the file is read outside the lock, so a search is never waiting for it
                val s = runCatching { read(p) }.getOrNull().orEmpty()
                synchronized(lock) {
                    text[p] = s
                    if (++n % 200 == 0) save(c)
                }
            }
            if (n > 0) synchronized(lock) { save(c) }
        }
    }

    /** Lyrics written next to or inside [path] as the index knows them (empty before the first pass). */
    fun has(path: String): Boolean = synchronized(lock) { text[path]?.isNotEmpty() == true }
}
