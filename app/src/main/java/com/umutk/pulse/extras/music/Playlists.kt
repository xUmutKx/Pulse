package com.umutk.pulse.extras.music

import android.content.Context
import android.net.Uri
import java.io.File

/** Named playlists are .m3u8 files in filesDir/playlists, so export is a plain copy. Also holds the resume state. */
object Playlists {

    private fun dir(c: Context) = File(c.filesDir, "playlists").apply { mkdirs() }
    private fun safe(name: String) = name.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)

    fun names(c: Context): List<String> =
        dir(c).listFiles { f -> f.extension == "m3u8" }.orEmpty().map { it.nameWithoutExtension }.sortedBy { it.lowercase() }

    fun save(c: Context, name: String, tracks: List<Track>) {
        if (safe(name).isEmpty()) return
        File(dir(c), safe(name) + ".m3u8").writeText(M3uParser.write(tracks), Charsets.UTF_8)
    }

    fun load(c: Context, name: String): List<Track> =
        File(dir(c), safe(name) + ".m3u8").takeIf { it.exists() }?.let { M3uParser.parse(it.readText(Charsets.UTF_8)) }.orEmpty()

    fun delete(c: Context, name: String) { File(dir(c), safe(name) + ".m3u8").delete() }

    /** Reads a playlist picked with the document picker. Relative entries resolve against the file's folder when it is a primary-storage path. */
    fun import(c: Context, uri: Uri): List<Track> {
        val text = c.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return emptyList()
        // shortcut: only primary-storage document ids give a folder; other providers keep relative entries unresolved
        val doc = Regex("primary:(.+)/[^/]+$").find(Uri.decode(uri.lastPathSegment.orEmpty()))?.groupValues?.get(1)
        return M3uParser.parse(text, doc?.let { "/storage/emulated/0/$it" })
    }

    /** Writes [tracks] as UTF-8 M3U8 to a document created with CreateDocument. */
    fun export(c: Context, uri: Uri, tracks: List<Track>) {
        c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(M3uParser.write(tracks).toByteArray(Charsets.UTF_8)) }
    }

    // ---- resume ----
    private const val P = "music_resume"

    fun saveResume(c: Context, queue: List<Track>, index: Int, positionMs: Long) {
        if (queue.isEmpty()) return
        runCatching {
            File(c.filesDir, "resume.m3u8").writeText(M3uParser.write(queue), Charsets.UTF_8)
            c.getSharedPreferences(P, Context.MODE_PRIVATE).edit().putInt("i", index).putLong("p", positionMs).apply()
        }
    }

    /** Restores the last queue paused at its position, only when the engine is empty. */
    fun restore(c: Context, engine: MusicPlayerEngine) {
        val f = File(c.filesDir, "resume.m3u8")
        if (!f.exists() || engine.player.mediaItemCount > 0) return
        val sp = c.getSharedPreferences(P, Context.MODE_PRIVATE)
        engine.loadQueue(M3uParser.parse(f.readText(Charsets.UTF_8)), sp.getInt("i", 0), sp.getLong("p", 0))
    }
}
