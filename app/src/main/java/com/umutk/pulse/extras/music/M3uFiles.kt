package com.umutk.pulse.extras.music

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Playlist files on the phone (.m3u, .m3u8): found through the media database and by walking the usual music folders,
 * read with [M3uParser]. Their entries are matched with the library, so a track keeps its tags and cover.
 */
object M3uFiles {
    class Found(val name: String, val path: String)

    private val ext = setOf("m3u", "m3u8")

    /** Every playlist file we can see, by name. Never on the main thread. */
    @Suppress("DEPRECATION") // DATA gives the real path, which is what the entries of a playlist are relative to
    fun scan(c: Context): List<Found> {
        val seen = LinkedHashMap<String, Found>()
        fun add(f: File) { if (f.isFile && f.extension.lowercase() in ext) seen.putIfAbsent(f.path, Found(f.nameWithoutExtension, f.path)) }
        runCatching {
            c.contentResolver.query(
                MediaStore.Files.getContentUri("external"), arrayOf(MediaStore.Files.FileColumns.DATA),
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} = ${MediaStore.Files.FileColumns.MEDIA_TYPE_PLAYLIST} OR " +
                    "${MediaStore.Files.FileColumns.DATA} LIKE '%.m3u' OR ${MediaStore.Files.FileColumns.DATA} LIKE '%.m3u8'", null, null,
            )?.use { q -> while (q.moveToNext()) q.getString(0)?.let { add(File(it)) } }
        }
        // the media database does not list everything (files other apps wrote, folders it skips): walk the usual places too
        val root = Environment.getExternalStorageDirectory()
        listOf("Music", "Playlists", "Download", "Documents", "Pulse").forEach { d -> runCatching { File(root, d).walk().maxDepth(3).forEach { add(it) } } }
        return seen.values.sortedBy { it.name.lowercase() }
    }

    /** The tracks of one playlist file, in its order. Entries that are in the library use the library's track; others stay if their file exists or they are streams. */
    fun load(path: String, library: List<Track>): List<Track> {
        val f = File(path)
        val text = runCatching { f.readText() }.getOrNull() ?: return emptyList()
        val byPath = library.associateBy { it.path }
        return M3uParser.parse(text, f.parent).mapNotNull { t ->
            byPath[t.path] ?: t.takeIf { it.path.contains("://") || File(it.path).exists() }
        }
    }
}
