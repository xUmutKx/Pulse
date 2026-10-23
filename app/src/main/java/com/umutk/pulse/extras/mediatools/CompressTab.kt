package com.umutk.pulse.extras.mediatools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

private class Item(val uri: Uri, val name: String, val kind: String, val size: Long) {
    var state by mutableStateOf("wait")     // wait, busy, done, fail
    var outSize by mutableStateOf(0L)
    var note by mutableStateOf("")
}

/** Makes pictures, videos and music smaller: one strength for the whole list, the results are saved next to each other in Download/Pulse. */
@Composable
internal fun CompressTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Item>() }
    var level by remember { mutableStateOf(1) }
    var running by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        list.forEach { u -> val n = nameOf(ctx, u); val k = kindOf(n); if (k == "image" || k == "video" || k == "audio") items += Item(u, n, k, sizeOf(ctx, u)) }
    }
    PCard {
        Text(tr("Make files smaller", "Dosyaları küçült"), color = T.text, fontSize = 16.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(level == 0, tr("Light", "Hafif")) { level = 0 }
            PChip(level == 1, tr("Medium", "Orta")) { level = 1 }
            PChip(level == 2, tr("Strong", "Güçlü")) { level = 2 }
        }
        Text(when (level) {
            0 -> tr("Pictures 85%, videos 1080p", "Resim %85, video 1080p")
            1 -> tr("Pictures 70% and at most 2560 px, videos 720p", "Resim %70 ve en fazla 2560 px, video 720p")
            else -> tr("Pictures 50% and at most 1600 px, videos 480p", "Resim %50 ve en fazla 1600 px, video 480p")
        }, color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Add files", "Dosya ekle"), compact = true) { pick.launch(arrayOf("image/*", "video/*", "audio/*")) }
            if (items.isNotEmpty()) PButton(tr("Clear", "Temizle"), compact = true) { if (!running) items.clear() }
        }
    }
    if (items.isNotEmpty()) PCard {
        items.forEach { it ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon(when (it.kind) { "image" -> "image"; "video" -> "video"; else -> "music" }, T.sub, 22.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(it.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when (it.state) {
                        "busy" -> fmt(it.size) + " …"
                        "done" -> fmt(it.size) + " → " + fmt(it.outSize) + "  (" + (if (it.size > 0) (100 - it.outSize * 100 / it.size) else 0) + "%)"
                        "fail" -> it.note
                        else -> fmt(it.size)
                    }, color = if (it.state == "fail") androidx.compose.ui.graphics.Color(0xFFC42B1C) else T.sub, fontSize = 12.sp)
                }
                if (it.state == "done") PIcon("check", T.accent, 20.dp)
            }
        }
        Spacer(Modifier.height(6.dp))
        val saved = items.filter { it.state == "done" }.sumOf { it.size - it.outSize }
        if (saved > 0) Text(tr("Saved ", "Kazanılan ") + fmt(saved), color = T.text, fontSize = 14.sp)
        PButton(if (running) tr("Working…", "Çalışıyor…") else tr("Compress", "Sıkıştır")) {
            if (running) return@PButton
            running = true
            scope.launch {
                items.forEach { it ->
                    if (it.state == "done") return@forEach
                    it.state = "busy"
                    val r = runCatching { compressOne(ctx, it.uri, it.name, it.kind, level) }.getOrElse { e -> null to (e.message ?: "failed") }
                    if (r.first == null) { it.state = "fail"; it.note = r.second } else { it.state = "done"; it.outSize = r.first!! }
                }
                running = false
            }
        }
        Text(tr("The new files are in Download/Pulse, the originals stay.", "Yeni dosyalar Download/Pulse içinde, özgünler kalır."), color = T.sub, fontSize = 12.sp)
    }
}

/** One file smaller; returns its new size, or null and the reason. */
private suspend fun compressOne(ctx: android.content.Context, uri: Uri, name: String, kind: String, level: Int): Pair<Long?, String> = withContext(Dispatchers.IO) {
    when (kind) {
        "image" -> {
            val q = intArrayOf(85, 70, 50)[level]; val maxSide = intArrayOf(4096, 2560, 1600)[level]
            val bytes = ByteArrayOutputStream()
            val ext = shrinkImage(ctx, uri, maxSide, q, bytes)
            val data = bytes.toByteArray()
            com.umutk.pulse.docScanSave(ctx, baseOf(name) + "_small." + ext, if (ext == "jpg") "image/jpeg" else "image/webp") { it.write(data) }
            data.size.toLong() to ""
        }
        else -> {
            val out = File(File(ctx.cacheDir, "mediatools").apply { mkdirs() }, "out_${System.nanoTime()}.mp4")
            val audio = kind == "audio"
            val h = if (audio) 0 else intArrayOf(1080, 720, 480)[level].let { target -> if (videoHeight(ctx, uri) in 1..target) 0 else target }
            val vbps = intArrayOf(5_000_000, 2_500_000, 1_200_000)[level]; val abps = if (audio) intArrayOf(192_000, 128_000, 64_000)[level] else intArrayOf(160_000, 128_000, 96_000)[level]
            val err = transform(ctx, uri, out, h, vbps, abps, audio)
            if (err != null) { out.delete(); return@withContext null to err }
            val size = out.length()
            saveFile(ctx, baseOf(name) + "_small." + (if (audio) "m4a" else "mp4"), if (audio) "audio/mp4" else "video/mp4", out)
            out.delete()
            size to ""
        }
    }
}

/** Decodes [uri] at a size that fits [maxSide], turns it upright and writes it as a JPEG (a WebP when it has see-through parts); returns the extension. */
internal fun shrinkImage(ctx: android.content.Context, uri: Uri, maxSide: Int, quality: Int, out: java.io.OutputStream): String {
    val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bo) }
    var step = 1
    while (maxOf(bo.outWidth, bo.outHeight) / (step * 2) >= maxSide) step *= 2
    var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = step }) } ?: throw java.io.IOException("unreadable")
    val deg = when (if (android.os.Build.VERSION.SDK_INT < 24) null else runCatching { ctx.contentResolver.openInputStream(uri)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull()) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
    val big = maxOf(bmp.width, bmp.height)
    val k = if (big > maxSide) maxSide.toFloat() / big else 1f
    if (deg != 0f || k < 1f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg); postScale(k, k) }, true)
    val alpha = bmp.hasAlpha() && bmp.config == Bitmap.Config.ARGB_8888 && (0 until minOf(bmp.width, 64)).any { x -> (0 until minOf(bmp.height, 64)).any { y -> (bmp.getPixel(x * bmp.width / minOf(bmp.width, 64), y * bmp.height / minOf(bmp.height, 64)) ushr 24) < 250 } }
    return if (alpha) {
        @Suppress("DEPRECATION") bmp.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, quality, out); "webp"
    } else { bmp.compress(Bitmap.CompressFormat.JPEG, quality, out); "jpg" }
}
