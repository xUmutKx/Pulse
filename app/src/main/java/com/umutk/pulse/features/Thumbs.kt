package com.umutk.pulse.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Small picture previews for the file list: memory cache, then a disk cache in cacheDir/thumbs, then a sampled decode. */
object Thumbs {
    private const val PX = 160
    private val mem = LruCache<String, ImageBitmap>(300)

    // a few decodes at a time, so a folder like DCIM/Camera does not swamp the IO pool
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(3)

    private fun key(f: File): String {
        val raw = "${f.path}|${f.lastModified()}|${f.length()}"
        return MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** The cached preview, or null while it is not decoded yet. */
    fun cached(f: File): ImageBitmap? = mem.get(key(f))

    /** Decodes (or reads back) a preview that keeps the picture's aspect ratio. Null when the device cannot decode it. */
    suspend fun load(ctx: Context, f: File): ImageBitmap? = withContext(io) {
        val k = key(f)
        mem.get(k)?.let { return@withContext it }
        val dir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
        val disk = File(dir, "$k.webp")
        val bmp = runCatching { if (disk.exists()) BitmapFactory.decodeFile(disk.path) else null }.getOrNull()
            ?: runCatching {
                // keeps the aspect ratio and reads the EXIF rotation; HEIC included
                ThumbnailUtils.createImageThumbnail(f, Size(PX, PX), null).also { b ->
                    runCatching { disk.outputStream().use { b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, it) } }
                }
            }.getOrNull()
        bmp?.asImageBitmap()?.also { mem.put(k, it) }
    }
}
