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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
                (previewOf(ctx, f) ?: if (android.os.Build.VERSION.SDK_INT >= 29) ThumbnailUtils.createImageThumbnail(f, Size(PX, PX), null) else decodeSmall(f)).also { b ->
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

private val PV_IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val PV_VIDEO = setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v")
private val PV_AUDIO = setOf("mp3", "m4a", "flac", "ogg", "opus", "wav", "aac")
private val PV_ZIPDOC = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp")

/** Whether a preview can be made for [f]: pictures, video frames, the first page of a PDF, the cover of a song, an APK's icon, the thumbnail inside an Office / Open Document file. */
fun canPreview(f: File): Boolean = f.extension.lowercase().let { it in PV_IMAGE || it in PV_VIDEO || it in PV_AUDIO || it in PV_ZIPDOC || it == "pdf" || it == "apk" }

/** A preview for the kinds that are not plain pictures; null for a picture (the caller decodes it) or when nothing could be made. */
private fun previewOf(ctx: Context, f: File): Bitmap? {
    val e = f.extension.lowercase()
    return when {
        e == "pdf" -> runCatching {
            android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { r ->
                    r.openPage(0).use { pg ->
                        val w = 480; val h = (w * pg.height / pg.width.toFloat()).toInt().coerceIn(120, 960)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b -> b.eraseColor(android.graphics.Color.WHITE); pg.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    }
                }
            }
        }.getOrNull()
        e in PV_VIDEO -> runCatching {
            val m = android.media.MediaMetadataRetriever()
            try { m.setDataSource(f.path); m.getFrameAtTime(1_000_000L)?.let { Bitmap.createScaledBitmap(it, PX, (PX * it.height / it.width.toFloat()).toInt().coerceAtLeast(1), true) } } finally { m.release() }
        }.getOrNull()
        e in PV_AUDIO -> runCatching {
            val m = android.media.MediaMetadataRetriever()
            try { m.setDataSource(f.path); m.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } } finally { m.release() }
        }.getOrNull()
        e == "apk" -> runCatching {
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(f.path, 0)?.applicationInfo ?: return@runCatching null
            info.sourceDir = f.path; info.publicSourceDir = f.path
            val d = info.loadIcon(pm)
            Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888).also { b -> val c = android.graphics.Canvas(b); d.setBounds(0, 0, 192, 192); d.draw(c) }
        }.getOrNull()
        e in PV_ZIPDOC -> runCatching {
            java.util.zip.ZipFile(f).use { z ->
                val en = listOf("docProps/thumbnail.jpeg", "docProps/thumbnail.png", "Thumbnails/thumbnail.png").firstNotNullOfOrNull { z.getEntry(it) }
                en?.let { z.getInputStream(it).use { i -> BitmapFactory.decodeStream(i) } }
            }
        }.getOrNull()
        else -> null
    }
}

/** The key of the line icon for a kind of file. */
fun fileTypeKey(f: File): String = when (f.extension.lowercase()) {
    in PV_IMAGE -> "image"
    in PV_VIDEO -> "video"
    in PV_AUDIO -> "volume"
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> "archive"
    "xls", "xlsx", "ods", "csv" -> "table"
    "apk", "xapk", "apks" -> "apps"
    else -> "document"
}

/** The small type mark of a file: white line icon on pure black. */
@androidx.compose.runtime.Composable
fun FileTypeBadge(f: File, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, size: androidx.compose.ui.unit.Dp = 22.dp) {
    androidx.compose.foundation.layout.Box(
        modifier.size(size).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color.Black)
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .35f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) { com.umutk.pulse.PIcon(fileTypeKey(f), androidx.compose.ui.graphics.Color.White, size * 0.62f) }
}
