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
