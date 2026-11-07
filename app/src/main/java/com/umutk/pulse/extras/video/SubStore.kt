package com.umutk.pulse.extras.video

import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * The subtitle files the user picked or downloaded for a video, kept on the phone: a copy in the app's own folder (and one next to the video when
 * all-files access allows) and a note per video. Opening the video again loads it at once, with its delay; picking "Off" is remembered too, and
 * a subtitle goes away with its video ([prune] / [forget]).
 */
internal object SubStore {
    private fun sp(c: Context) = c.getSharedPreferences("video_sub_saved", Context.MODE_PRIVATE)
    private fun dir(c: Context) = File(c.filesDir, "subs").also { it.mkdirs() }

    /** Copies the subtitle at [src] for [v]; returns the kept file's address (null when it could not be read). */
    fun save(c: Context, v: VideoItem, src: Uri, name: String): Uri? = runCatching {
        val bytes = (if (src.scheme == "file") File(src.path!!).inputStream() else c.contentResolver.openInputStream(src))?.use { it.readBytes() } ?: return null
        val ext = name.substringAfterLast('.', "srt").lowercase().takeIf { it in subExts } ?: "srt"
        forget(c, v.subKey)
        val out = File(dir(c), (v.subKey.hashCode().toLong() and 0xffffffffL).toString() + "." + ext)
        out.writeBytes(bytes)
        val e = sp(c).edit().putString("f:" + v.subKey, out.path).putString("n:" + v.subKey, name).remove("off:" + v.subKey)
        // next to the video, so other players find it too
        if (v.local && v.path.isNotBlank() && com.umutk.pulse.Compat.isStorageManager()) runCatching {
            val side = File(File(Environment.getExternalStorageDirectory(), v.path), v.name.substringBeforeLast('.') + "." + ext)
            if (!side.exists()) { side.writeBytes(bytes); e.putString("s:" + v.subKey, side.path) }
        }
        e.apply()
        Uri.fromFile(out)
    }.getOrNull()

    /** The kept subtitle of [key] and its shown name. */
    fun saved(c: Context, key: String): Pair<Uri, String>? {
        val f = sp(c).getString("f:$key", null)?.let { File(it) }?.takeIf { it.exists() } ?: return null
        return Uri.fromFile(f) to sp(c).getString("n:$key", f.name).orEmpty()
    }

    fun isOff(c: Context, key: String) = sp(c).getBoolean("off:$key", false)
    fun setOff(c: Context, key: String, off: Boolean) { sp(c).edit().apply { if (off) putBoolean("off:$key", true) else remove("off:$key") }.apply() }
    fun delay(c: Context, key: String) = sp(c).getLong("d:$key", 0L)
    fun setDelay(c: Context, key: String, ms: Long) { sp(c).edit().apply { if (ms == 0L) remove("d:$key") else putLong("d:$key", ms) }.apply() }

    /** Drops the kept subtitle of a video (the copy beside the video too). */
    fun forget(c: Context, key: String) {
        val p = sp(c)
        p.getString("f:$key", null)?.let { runCatching { File(it).delete() } }
        p.getString("s:$key", null)?.let { runCatching { File(it).delete() } }
        p.edit().remove("f:$key").remove("n:$key").remove("s:$key").remove("d:$key").remove("off:$key").apply()
    }

    /** Subtitles of device videos that are gone are deleted; [alive] is every video the media index lists now. */
    fun prune(c: Context, alive: Set<String>) {
        if (alive.isEmpty()) return
        // by name and size ("nm:") only for a video whose folder was read: a subtitle is dropped when its video is gone from the phone
        sp(c).all.keys.filter { it.startsWith("f:content://media/") || it.startsWith("f:nm:") }.map { it.substring(2) }.filter { it !in alive }.forEach { forget(c, it) }
    }
}

/** The languages the player shows a subtitle in by default: the chosen one, the app's own, and English. */
internal fun subLangs(p: VideoPrefs): Array<String> =
    listOf(p.prefSub, java.util.Locale.getDefault().language, "en").filter { it.isNotBlank() }.distinct().toTypedArray()
