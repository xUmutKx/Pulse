package com.umutk.pulse.extras.music.poweramp

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.Track
import java.io.File

/** What a bulk delete did: how many files went at once, and the system's one confirmation for the rest (Android 11+), if it is needed. */
internal class DeleteOutcome(val deleted: Int, val failed: Int, val ask: IntentSender?)

/**
 * Deletes songs from the phone. Files the app may remove are deleted straight away; for the others Android 11 and newer get ONE system
 * request that covers all of them (a single confirmation, not one per song). Call it off the main thread.
 */
internal fun deleteTracks(ctx: Context, tracks: List<Track>): DeleteOutcome {
    var deleted = 0
    val left = ArrayList<Track>()
    for (t in tracks) {
        if (t.path.contains("://")) { left += t; continue }
        val f = File(t.path)
        if (f.delete() || !f.exists()) deleted++ else left += t
    }
    MusicLibrary.forget(ctx, tracks.map { it.path }.filter { !File(it).exists() })
    if (left.isEmpty()) return DeleteOutcome(deleted, 0, null)
    val uris = left.mapNotNull { t -> if (t.path.contains("://")) null else audioUri(ctx, t.path) }
    if (Build.VERSION.SDK_INT >= 30 && uris.isNotEmpty()) {
        val sender = runCatching { MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender }.getOrNull()
        if (sender != null) return DeleteOutcome(deleted, left.size - uris.size, sender)
    }
    // older Android: the media store row of each file is removed (the file goes with it when the app owns it)
    var ok = 0
    for (u in uris) if (runCatching { ctx.contentResolver.delete(u, null, null) }.getOrDefault(0) > 0) ok++
    return DeleteOutcome(deleted + ok, left.size - ok, null)
}

private fun audioUri(ctx: Context, path: String): Uri? = runCatching {
    ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID), MediaStore.Audio.Media.DATA + "=?", arrayOf(path), null)?.use { c ->
        if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) else null
    }
}.getOrNull()
