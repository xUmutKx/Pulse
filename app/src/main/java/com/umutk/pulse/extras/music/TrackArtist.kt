package com.umutk.pulse.extras.music

import com.umutk.pulse.tr

/** Folder names that say nothing about who made the music. */
private val GENERIC_FOLDERS = setOf("download", "downloads", "music", "musics", "audio", "media", "sdcard", "emulated", "0", "storage", "documents")

/**
 * The artist to show for a track, never blank: the tag (MediaStore's "<unknown>" counts as none), else the "Artist - Title"
 * part of the title or file name, else the folder name when it is not a generic one, else "Unknown artist".
 */
fun Track.shownArtist(): String {
    artist?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", true) && !it.equals("unknown", true) }?.let { return it }
    val name = (title ?: path.substringAfterLast('/').substringBeforeLast('.')).trim()
    if (name.contains(" - ")) name.substringBefore(" - ").trim().takeIf { it.isNotEmpty() }?.let { return it }
    if (!path.contains("://")) {
        val folder = path.substringBeforeLast('/', "").substringAfterLast('/').trim()
        if (folder.isNotEmpty() && folder.lowercase() !in GENERIC_FOLDERS) return folder
    }
    return tr("Unknown artist", "Bilinmeyen sanatçı")
}

private val tagArtists = java.util.concurrent.ConcurrentHashMap<String, String>()

/**
 * [shownArtist] with the file's own tag as a last resort before "Unknown artist": the track the player holds may carry no artist (a widget, a restored queue),
 * while the file and the library know it. Read once per file and kept.
 */
fun Track.artistFor(ctx: android.content.Context): String {
    val plain = artist?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", true) && !it.equals("unknown", true) }
    if (plain != null) return plain
    // the scanned library has it
    MusicLibrary.tracks.value.firstOrNull { it.path == path }?.artist?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", true) && !it.equals("unknown", true) }?.let { return it }
    tagArtists[path]?.let { if (it.isNotEmpty()) return it }
    if (!path.contains("://") && !tagArtists.containsKey(path)) {
        val a = runCatching {
            val r = android.media.MediaMetadataRetriever()
            try { r.setDataSource(path); (r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)).orEmpty().trim() } finally { r.release() }
        }.getOrDefault("")
        tagArtists[path] = a
        if (a.isNotEmpty()) return a
    }
    return shownArtist()
}
