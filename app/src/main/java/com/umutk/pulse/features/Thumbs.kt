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
    // 480 px keeps the grid icons sharp when they are enlarged (the viewer itself decodes the original)
    private const val PX = 480
    // a byte budget, not a count: 480 px previews are ~0.9 MB each in memory
    private val mem = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 10).toInt().coerceAtLeast(8 shl 20)) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    // a few decodes at a time, so a folder like DCIM/Camera does not swamp the IO pool
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(3)

    @Volatile private var trimmed = false
    private const val MAX_BYTES = 96L shl 20

    /** The disk cache had no limit: once per run the oldest previews go until it is under 96 MB. */
    private fun trim(dir: File) = runCatching {
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return@runCatching
        var total = files.sumOf { it.length() }
        for (f in files) { if (total <= MAX_BYTES) break; total -= f.length(); f.delete() }
    }

    private fun key(f: File): String {
        val raw = "v2|${f.path}|${f.lastModified()}|${f.length()}"
        return MessageDigest.getInstance("MD5").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /** The cached preview, or null while it is not decoded yet. */
    fun cached(f: File): ImageBitmap? = mem.get(key(f))

    /** Decodes (or reads back) a preview that keeps the picture's aspect ratio. Null when the device cannot decode it. */
    suspend fun load(ctx: Context, f: File): ImageBitmap? = withContext(io) {
        val k = key(f)
        mem.get(k)?.let { return@withContext it }
        val dir = File(ctx.cacheDir, "thumbs").apply { mkdirs() }
        if (!trimmed) { trimmed = true; trim(dir) }
        val disk = File(dir, "$k.webp")
        val bmp = runCatching { if (disk.exists()) BitmapFactory.decodeFile(disk.path) else null }.getOrNull()
            ?: runCatching {
                // keeps the aspect ratio and reads the EXIF rotation; HEIC included
                (if (android.os.Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createImageThumbnail(f, Size(PX, PX), null) else decodeSmall(f)).also { b ->
                    runCatching { disk.outputStream().use { b.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP, 92, it) } }
                }
            }.getOrNull()
        bmp?.asImageBitmap()?.also { mem.put(k, it) }
    }
}

/** Android 5 to 9: a small version of the picture with the factory (the EXIF turn is not applied). */
private fun decodeSmall(f: File): Bitmap {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    var step = 1
    while (maxOf(o.outWidth, o.outHeight) / (step * 2) >= 256) step *= 2
    return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = step }) ?: throw java.io.IOException("unreadable")
}
