package com.umutk.pulse.extras.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** Device music from MediaStore, cached in filesDir so the page opens instantly and the scan refreshes it. */
object MusicLibrary {

    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    fun hasPermission(c: Context) = c.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun cacheFile(c: Context) = File(c.filesDir, "music_library.tsv")

    /** Loads the cache (if nothing is loaded yet), then rescans when permitted. */
    suspend fun refresh(c: Context) = withContext(Dispatchers.IO) {
        val app = c.applicationContext
        if (_tracks.value.isEmpty()) _tracks.value = readCache(app)
        if (!hasPermission(app)) return@withContext
        val list = scan(app)
        if (list.isNotEmpty() || _tracks.value.isNotEmpty()) {
            _tracks.value = list
            runCatching { cacheFile(app).writeText(list.joinToString("\n") { t ->
                listOf(t.path, t.title.orEmpty(), t.durationSec, t.artist.orEmpty(), t.album.orEmpty(), t.albumId).joinToString("\t") { it.toString().replace('\t', ' ').replace('\n', ' ') }
            }) }
        }
    }

    private fun readCache(c: Context): List<Track> = runCatching {
        cacheFile(c).readLines().mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 6) null else Track(f[0], f[1].ifEmpty { null }, f[2].toIntOrNull() ?: -1, f[3].ifEmpty { null }, f[4].ifEmpty { null }, f[5].toLongOrNull() ?: -1)
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION") // DATA gives the real path, which M3U export and folder grouping need
    private fun scan(c: Context): List<Track> {
        val out = ArrayList<Track>()
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
        )
        try {
            c.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000", null,
                "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE, ${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.TRACK}",
            )?.use { q ->
                while (q.moveToNext()) {
                    val path = q.getString(1)?.takeIf { File(it).exists() }
                        ?: android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, q.getLong(0)).toString()
                    val artist = q.getString(4)?.takeIf { it != "<unknown>" }
                    out += Track(path, q.getString(2), (q.getLong(3) / 1000).toInt(), artist, q.getString(5), q.getLong(6))
                }
            }
        } catch (e: SecurityException) {
            // permission revoked mid-scan: keep what the cache has
        }
        return out
    }

    fun byAlbum(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.album ?: "?" }
    fun byArtist(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.artist ?: "?" }
    fun byFolder(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.path.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { "?" } }
}
