package com.umutk.pulse.extras.screenrec

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.umutk.pulse.tr

data class Recording(val uri: Uri, val name: String, val added: Long, val durationMs: Long, val size: Long)

/** The recordings this app saved, read back from MediaStore; newest first. */
object RecordingList {
    const val RELATIVE_PATH = "Movies/Pulse Recordings"

    fun list(ctx: Context): List<Recording> {
        val proj = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.SIZE)
        val out = mutableListOf<Recording>()
        runCatching {
            ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Video.Media.IS_PENDING} = 0",
                arrayOf("$RELATIVE_PATH%"), "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    out += Recording(
                        uri = android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                        name = c.getString(1).orEmpty(),
                        added = c.getLong(2) * 1000L,
                        durationMs = c.getLong(3),
                        size = c.getLong(4),
                    )
                }
            }
        }
        return out
    }

    fun delete(ctx: Context, r: Recording): Boolean = runCatching { ctx.contentResolver.delete(r.uri, null, null) > 0 }.getOrDefault(false)

    /** Renames the file, keeping .mp4; returns the new name, or null when the name is empty or the update fails. */
    fun rename(ctx: Context, r: Recording, typed: String): String? {
        val base = typed.trim().removeSuffix(".mp4").replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
        if (base.isEmpty()) return null
        val name = "$base.mp4"
        val n = runCatching { ctx.contentResolver.update(r.uri, ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, name) }, null, null) }.getOrDefault(0)
        return if (n > 0) name else null
    }

    fun play(r: Recording): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(r.uri, "video/mp4")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    fun share(r: Recording): Intent = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, r.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        tr("Share recording", "Kaydı paylaş")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
