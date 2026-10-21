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

    /** Applies the saved tags (edits and the ones found online) to the loaded list again, so new tags show without a rescan. */
    fun reapplyTags(c: Context) { _tracks.value = MusicTags.apply(c.applicationContext, _tracks.value) }

    /** Loads the cache (if nothing is loaded yet), then rescans when permitted. */
    suspend fun refresh(c: Context) = withContext(Dispatchers.IO) {
        val app = c.applicationContext
        if (_tracks.value.isEmpty()) _tracks.value = MusicTags.apply(app, readCache(app))
        if (!hasPermission(app)) return@withContext
        val list = MusicTags.apply(app, scan(app))
        if (list.isNotEmpty() || _tracks.value.isNotEmpty()) {
            _tracks.value = list
            runCatching { cacheFile(app).writeText(list.joinToString("\n") { t ->
                listOf(t.path, t.title.orEmpty(), t.durationSec, t.artist.orEmpty(), t.album.orEmpty(), t.albumId, t.genre.orEmpty(), t.year, t.composer.orEmpty(), t.dateAdded).joinToString("\t") { it.toString().replace('\t', ' ').replace('\n', ' ') }
            }) }
        }
    }

    private fun readCache(c: Context): List<Track> = runCatching {
        cacheFile(c).readLines().mapNotNull { l ->
            val f = l.split('\t')
            if (f.size < 6) null else Track(f[0], f[1].ifEmpty { null }, f[2].toIntOrNull() ?: -1, f[3].ifEmpty { null }, f[4].ifEmpty { null }, f[5].toLongOrNull() ?: -1,
                f.getOrNull(6)?.ifEmpty { null }, f.getOrNull(7)?.toIntOrNull() ?: 0, f.getOrNull(8)?.ifEmpty { null }, f.getOrNull(9)?.toLongOrNull() ?: 0)
        }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION") // DATA gives the real path, which M3U export and folder grouping need
    private fun scan(c: Context): List<Track> {
        val out = ArrayList<Track>()
        val genreCol = if (Build.VERSION.SDK_INT >= 30) MediaStore.Audio.Media.GENRE else MediaStore.Audio.Media.TITLE
        val cols = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID,
            genreCol, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.COMPOSER, MediaStore.Audio.Media.DATE_ADDED,
        )
        val minMs = MusicSettings.minSeconds(c) * 1000L
        val excluded = MusicSettings.excluded(c)
        val hidden = MusicSettings.showHidden(c)
        try {
            c.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols,
                // IS_MUSIC alone drops songs that came from downloads or messengers (they are not flagged): anything over a minute that is not a ringtone, alert or recording counts too
                "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR (${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0 AND ${MediaStore.Audio.Media.IS_RECORDING} = 0 AND ${MediaStore.Audio.Media.DURATION} > 60000)) AND ${MediaStore.Audio.Media.DURATION} > $minMs", null,
                "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE, ${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.TRACK}",
            )?.use { q ->
                while (q.moveToNext()) {
                    val raw = q.getString(1).orEmpty()
                    if (excluded.any { raw.startsWith(it) }) continue
                    if (!hidden && raw.contains("/.")) continue
                    val path = q.getString(1)?.takeIf { File(it).exists() }
                        ?: android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, q.getLong(0)).toString()
                    val artist = q.getString(4)?.takeIf { it != "<unknown>" }
                    val genre = if (Build.VERSION.SDK_INT >= 30) q.getString(7)?.takeIf { it.isNotBlank() } else null
                    out += Track(path, q.getString(2), (q.getLong(3) / 1000).toInt(), artist, q.getString(5), q.getLong(6),
                        genre, q.getInt(8).takeIf { it > 0 } ?: 0, q.getString(9)?.takeIf { it.isNotBlank() && it != "<unknown>" }, q.getLong(10))
                }
            }
        } catch (e: SecurityException) {
            // permission revoked mid-scan: keep what the cache has
        }
        return out
    }

    fun byAlbum(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.album ?: "?" }
    fun byArtist(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.artist ?: "?" }
    fun byGenre(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.genre ?: "?" }
    fun byYear(l: List<Track>): Map<String, List<Track>> = l.groupBy { if (it.year > 0) it.year.toString() else "?" }
    fun byComposer(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.composer ?: "?" }
    fun byFolder(l: List<Track>): Map<String, List<Track>> = l.groupBy { it.path.substringBeforeLast('/', "").substringAfterLast('/').ifEmpty { "?" } }
}
