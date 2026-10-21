package com.umutk.pulse.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.util.LruCache
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.umutk.pulse.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Previews of PDF / Office / video / audio / APK files for the Files list: lazy (produceState dies when the row scrolls away), memory capped at 16 MB, plus a PNG disk cache. */
object FilePreviews {
    private const val PX = 400
    private val mem = object : LruCache<String, Bitmap>(32 shl 20) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val miss = HashSet<String>()
    @OptIn(ExperimentalCoroutinesApi::class) private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(2)

    val OFFICE = setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "dotx")
    fun supports(e: String) = e == "pdf" || e in OFFICE || e == "apk" || e == "xapk" || e in VIDEO || e in AUDIO
    val VIDEO = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
    val AUDIO = setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav")

    private fun key(f: File) = MessageDigest.getInstance("MD5").digest("v2|${f.path}|${f.lastModified()}|${f.length()}".toByteArray()).joinToString("") { "%02x".format(it) }
    fun cached(f: File): Bitmap? = mem.get(key(f))

    suspend fun load(ctx: Context, f: File): Bitmap? = withContext(io) {
        val k = key(f)
        mem.get(k)?.let { return@withContext it }
        synchronized(miss) { if (k in miss) return@withContext null }
        val disk = File(File(ctx.cacheDir, "prev").apply { mkdirs() }, "$k.png")
        val bmp = (if (disk.exists()) runCatching { android.graphics.BitmapFactory.decodeFile(disk.path) }.getOrNull() else null)
            ?: runCatching { render(ctx, f) }.getOrNull()?.also { b -> runCatching { disk.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) } } }
        if (bmp != null) mem.put(k, bmp) else synchronized(miss) { miss.add(k) }
        bmp
    }

    private fun scaled(b: Bitmap): Bitmap {
        val s = PX.toFloat() / maxOf(b.width, b.height)
        return if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    private fun render(ctx: Context, f: File): Bitmap? {
        val e = f.extension.lowercase()
        return when {
            e == "pdf" -> android.os.ParcelFileDescriptor.open(f, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                android.graphics.pdf.PdfRenderer(fd).use { r ->
                    if (r.pageCount == 0) return null
                    r.openPage(0).use { p ->
                        val w = PX; val h = (PX.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); b.eraseColor(AColor.WHITE)
                        p.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); b
                    }
                }
            }
            e in OFFICE -> java.util.zip.ZipFile(f).use { z ->
                val en = listOf("docProps/thumbnail.jpeg", "docProps/thumbnail.jpg", "docProps/thumbnail.png", "Thumbnails/thumbnail.png").firstNotNullOfOrNull { z.getEntry(it) } ?: return null
                z.getInputStream(en).use { android.graphics.BitmapFactory.decodeStream(it) }?.let { scaled(it) }
            }
            e == "xapk" -> java.util.zip.ZipFile(f).use { z ->
                val en = z.getEntry("icon.png") ?: return null
                z.getInputStream(en).use { android.graphics.BitmapFactory.decodeStream(it) }?.let { scaled(it) }
            }
            e == "apk" -> {
                val pm = ctx.packageManager
                val pi = pm.getPackageArchiveInfo(f.path, 0) ?: return null
                val ai = pi.applicationInfo ?: return null
                ai.sourceDir = f.path; ai.publicSourceDir = f.path
                val d = ai.loadIcon(pm)
                val b = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 256, 256); d.draw(Canvas(b)); b
            }
            e in VIDEO || e in AUDIO -> {
                val m = android.media.MediaMetadataRetriever()
                try {
                    m.setDataSource(f.path)
                    (if (e in AUDIO) m.embeddedPicture?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) } else m.getFrameAtTime(1_000_000))?.let { scaled(it) }
                } finally { runCatching { m.release() } }
            }
            else -> null
        }
    }
}

/** The preview of a non-image file (null until decoded, or when there is none). */
@Composable
fun exPreview(f: File): ImageBitmap? {
    if (!FilesPrefs.previews || f.isDirectory || !FilePreviews.supports(f.extension.lowercase())) return null
    val ctx = LocalContext.current
    val first = remember(f.path) { FilePreviews.cached(f)?.asImageBitmap() }
    return produceState(first, f.path) { if (value == null) value = FilePreviews.load(ctx, f)?.asImageBitmap() }.value
}

private class Badge(val color: Color, val glyph: ImageVector)

private fun badgeOf(f: File): Badge? {
    if (f.isDirectory) return null
    return when (f.extension.lowercase()) {
        "pdf" -> Badge(Color(0xFFD93025), Icons.Filled.PictureAsPdf)
        "doc", "docx", "odt", "rtf", "dotx" -> Badge(Color(0xFF2B579A), Icons.Filled.Description)
        "xls", "xlsx", "ods", "csv", "tsv" -> Badge(Color(0xFF1E7145), Icons.Filled.TableChart)
        "ppt", "pptx", "odp" -> Badge(Color(0xFFD24726), Icons.Filled.Slideshow)
        "txt", "md", "log", "lrc" -> Badge(Color(0xFF757575), Icons.Filled.Notes)
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar" -> Badge(Color(0xFF8D6E63), Icons.Filled.FolderZip)
        "mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma" -> Badge(Color(0xFF8E44AD), Icons.Filled.MusicNote)
        "mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v" -> Badge(Color(0xFFE91E63), Icons.Filled.PlayArrow)
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg" -> Badge(Color(0xFF00897B), Icons.Filled.Image)
        "apk", "apks", "xapk", "apkm" -> Badge(Color(0xFF3DDC84), Icons.Filled.Android)
        "json", "xml", "html", "js", "kt", "java", "py", "sh", "css", "yml", "yaml" -> Badge(Color(0xFF455A64), Icons.Filled.Code)
        else -> null
    }
}

/** [content] with a small file-type badge at its bottom-right corner (files only; round in Material, square in the Windows looks). */
@Composable
fun ExBadged(f: File, size: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    val b = if (FilesPrefs.previews) badgeOf(f) else null
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        content()
        if (b != null) Box(Modifier.align(Alignment.BottomEnd).size(17.dp).clip(if (com.umutk.pulse.T.material) RoundedCornerShape(50) else RoundedCornerShape(2.dp)).background(b.color), contentAlignment = Alignment.Center) {
            Icon(b.glyph, null, Modifier.size(12.dp), Color.White)
        }
    }
}
