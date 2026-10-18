package com.umutk.pulse.extras.video

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One video: a MediaStore row, a picked document or a URL ([durMs] 0 when unknown). */
data class VideoItem(val uri: Uri, val name: String, val durMs: Long, val folder: String)

/** All videos on the device, newest first. Needs READ_MEDIA_VIDEO; empty when it is missing. */
fun queryVideos(ctx: Context): List<VideoItem> {
    val out = ArrayList<VideoItem>()
    val cols = arrayOf(
        MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION, MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
    )
    runCatching {
        ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cols, null, null, MediaStore.Video.Media.DATE_ADDED + " DESC")?.use { c ->
            while (c.moveToNext()) {
                out += VideoItem(
                    Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(0).toString()),
                    c.getString(1).orEmpty(), c.getLong(2), c.getString(3).orEmpty(),
                )
            }
        }
    }
    return out
}

private val thumbs = object : LruCache<String, ImageBitmap>(160) {}

/** Thumbnail of a MediaStore video, loaded off the main thread; null while loading or when none exists. */
@Composable
fun videoThumb(item: VideoItem): ImageBitmap? {
    val ctx = LocalContext.current
    val key = item.uri.toString()
    val bmp by produceState(thumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.loadThumbnail(item.uri, Size(320, 180), null).asImageBitmap() }.getOrNull()
        }?.also { thumbs.put(key, it) }
    }
    return bmp
}

/** Saved resume positions and player options. */
class VideoPrefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("video_player", Context.MODE_PRIVATE)

    fun resume(key: String): Long = sp.getLong("r_" + key.hashCode(), 0L)

    /** Forgets the position near the start or the end, so a finished film starts over. */
    fun setResume(key: String, pos: Long, dur: Long) {
        val e = sp.edit()
        if (pos < 5000 || (dur > 0 && pos > dur - 5000)) e.remove("r_" + key.hashCode()) else e.putLong("r_" + key.hashCode(), pos)
        e.apply()
    }

    var loop: Boolean
        get() = sp.getBoolean("loop", false)
        set(v) { sp.edit().putBoolean("loop", v).apply() }
    var bg: Boolean
        get() = sp.getBoolean("bg", false)
        set(v) { sp.edit().putBoolean("bg", v).apply() }
    /** 0 fit, 1 stretch, 2 crop, 3 16:9 */
    var aspect: Int
        get() = sp.getInt("aspect", 0)
        set(v) { sp.edit().putInt("aspect", v).apply() }
    /** 0 system, 1 landscape, 2 portrait, 3 by the video */
    var orient: Int
        get() = sp.getInt("orient", 3)
        set(v) { sp.edit().putInt("orient", v).apply() }
}

/** m:ss, or h:mm:ss for a film. */
fun clock(ms: Long): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
