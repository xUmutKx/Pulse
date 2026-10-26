package com.umutk.pulse.extras.music

import android.content.Context
import android.media.MediaMetadataRetriever
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import java.io.File

/**
 * Writes a song's saved cover into the mp3 itself, with a way back at every step: the first song is tried on a copy (a failure there stops the whole run), every song is
 * copied aside before it is touched, and afterwards the file must still play for as long as before and must hold the picture; if not, the copy goes back over it.
 */
object CoverEmbed {
    private fun duration(f: File): Long = runCatching {
        val r = MediaMetadataRetriever()
        try { r.setDataSource(f.path); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: -1L } finally { r.release() }
    }.getOrDefault(-1L)

    private fun hasPicture(f: File): Boolean = runCatching {
        val r = MediaMetadataRetriever()
        try { r.setDataSource(f.path); r.embeddedPicture != null } finally { r.release() }
    }.getOrDefault(false)

    /** Mp3 files that have a saved cover picture but no picture of their own. */
    fun candidates(c: Context, tracks: List<Track>): List<Track> = tracks.filter {
        !it.path.startsWith("http") && it.path.endsWith(".mp3", true) && File(it.path).canWrite() && PowerampCovers.jpgOf(c, it.path) != null && embeddedArt(c, it.path) == null
    }

    private fun put(f: File, jpg: File): Boolean {
        val before = duration(f); val sizeBefore = f.length()
        if (!Id3Frames.putFrame(f.path, "APIC", Id3Frames.coverBody(jpg.readBytes()))) return false
        val after = duration(f)
        return hasPicture(f) && f.length() >= sizeBefore && (before < 0 || (after >= 0 && kotlin.math.abs(after - before) <= 1500))
    }

    /** Tries the whole thing on a copy of one song; true when the copy came out right. */
    fun rehearse(c: Context, t: Track): Boolean = runCatching {
        val jpg = PowerampCovers.jpgOf(c, t.path) ?: return false
        val dir = File(c.cacheDir, "embed_test").apply { deleteRecursively(); mkdirs() }
        val copy = File(dir, "test.mp3"); File(t.path).copyTo(copy, overwrite = true)
        val ok = put(copy, jpg)
        dir.deleteRecursively()
        ok
    }.getOrDefault(false)

    /** One song: true when the picture is in and checked, false when nothing changed (the copy was put back if the file had been touched). */
    fun embed(c: Context, t: Track): Boolean {
        val f = File(t.path); val jpg = PowerampCovers.jpgOf(c, t.path) ?: return false
        val bak = File(c.filesDir, "cover_backup/${f.name}.bak").apply { parentFile?.mkdirs() }
        return runCatching {
            f.copyTo(bak, overwrite = true)
            if (bak.length() != f.length()) { bak.delete(); return false }
            if (put(f, jpg)) { bak.delete(); forgetArt(t.path); true }
            else { bak.copyTo(f, overwrite = true); bak.delete(); false }
        }.getOrElse { if (bak.isFile && bak.length() > 0) runCatching { bak.copyTo(f, overwrite = true); bak.delete() }; false }
    }
}
