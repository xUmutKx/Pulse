package com.umutk.pulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Decodes a camera photo (EXIF rotation applied), downsampled so the longest side is at most [maxSide] px. */
fun docScanDecode(f: File, maxSide: Int = 1800): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { d, info, _ ->
    val s = maxOf(info.size.width, info.size.height) / maxSide.toFloat()
    if (s > 1f) d.setTargetSize((info.size.width / s).toInt(), (info.size.height / s).toInt())
    d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE) // software pixels, so getPixels works
}

/** Grayscale, then a levels stretch: the 2nd..98th percentile of luminance becomes 0..255 (paper goes white, ink dark). */
fun docScanEnhance(src: Bitmap): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
    val gray = IntArray(px.size) { i -> val c = px[i]; ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 }
    val hist = IntArray(256).also { hh -> gray.forEach { hh[it]++ } }
    fun level(p: Double): Int {
        var acc = 0; val cut = gray.size * p
        for (v in 0..255) { acc += hist[v]; if (acc >= cut) return v }
        return 255
    }
    val lo = level(0.02); val hi = level(0.98).coerceAtLeast(lo + 1)
    val lut = IntArray(256) { v -> ((v - lo) * 255 / (hi - lo)).coerceIn(0, 255) }
    val out = IntArray(px.size) { i -> val g = lut[gray[i]]; (0xFF shl 24) or (g shl 16) or (g shl 8) or g }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

/** One PDF page per scan, each page sized to its bitmap (pixels as points). */
fun docScanWritePdf(pages: List<Bitmap>, out: OutputStream) {
    val doc = PdfDocument()
    try {
        pages.forEachIndexed { i, b ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(b.width, b.height, i + 1).create())
            page.canvas.drawBitmap(b, 0f, 0f, null)
            doc.finishPage(page)
        }
        doc.writeTo(out)
    } finally { doc.close() }
}

/** Writes [name] into Download/Pulse through MediaStore (minSdk 29); returns the name. */
suspend fun docScanSave(ctx: Context, name: String, mime: String, write: (OutputStream) -> Unit): String = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, name)
        put(MediaStore.Downloads.MIME_TYPE, mime)
        put(MediaStore.Downloads.RELATIVE_PATH, "Download/Pulse/")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val resolver = ctx.contentResolver
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Could not create $name")
    try {
        resolver.openOutputStream(uri)?.use(write) ?: throw IOException("Could not open $name for writing")
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
    name
}

/** Scan document: camera pages (system camera, FileProvider target) -> enhanced pages -> PDF or PNG in Download/Pulse. */
@Composable
fun DocScanPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = remember { mutableStateListOf<Bitmap>() }
    var shotFile by remember { mutableStateOf<File?>(null) }
    var finished by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    // a camera photo or a picture from the gallery: both become a page the same way
    fun addPage(f: File) {
        busy = true
        scope.launch {
            val page = withContext(Dispatchers.IO) { runCatching { docScanEnhance(docScanDecode(f)) }.getOrNull() }
            f.delete()
            busy = false
            if (page == null) status = tr("Could not read the photo", "Fotoğraf okunamadı")
            else { pages.add(page); status = tr("Page ${pages.size} added", "${pages.size}. sayfa eklendi") }
        }
    }

    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = shotFile
        shotFile = null
        if (f == null) return@rememberLauncherForActivityResult
        if (!ok) { f.delete(); status = tr("Cancelled", "İptal edildi"); return@rememberLauncherForActivityResult }
        addPage(f)
    }

    // the system picker (no storage permission needed): the picked picture is copied to the cache, then handled like a photo
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching {
                    File(ctx.cacheDir, "scan-pick-${System.currentTimeMillis()}.img").also { out ->
                        ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { i.copyTo(it) } } ?: error("cannot open")
                    }
                }.getOrNull()
            }
            if (f == null) status = tr("Could not read the picture", "Görsel okunamadı") else addPage(f)
        }
    }

    fun startShot() {
        if (busy) return
        // the photo goes to the app's own external files dir, which the "files" FileProvider already serves
        val uri = runCatching {
            val f = File(ctx.getExternalFilesDir(null) ?: error("no app storage"), "scan-${System.currentTimeMillis()}.jpg")
            shotFile = f
            FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        }.getOrNull()
        if (uri == null) { status = tr("No storage for the photo", "Fotoğraf için depolama yok"); return }
        try { shoot.launch(uri) } catch (e: Exception) { shotFile = null; status = tr("No camera app", "Kamera uygulaması yok") }
    }

    fun save(pdf: Boolean) {
        val list = pages.toList()
        val last = list.lastOrNull() ?: return
        if (busy) return
        busy = true
        scope.launch {
            val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            val name = "scan-$stamp.${if (pdf) "pdf" else "png"}"
            status = try {
                withContext(Dispatchers.IO) {
                    docScanSave(ctx, name, if (pdf) "application/pdf" else "image/png") { out ->
                        if (pdf) docScanWritePdf(list, out) else last.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
                tr("Saved to Download/Pulse: $name", "Download/Pulse içine kaydedildi: $name")
            } catch (e: Exception) {
                tr("Could not save", "Kaydedilemedi") + ": " + (e.message ?: e.javaClass.simpleName)
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Scan document", "Belge tara"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        PCard {
            val last = pages.lastOrNull()
            if (last != null) Image(last.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 460.dp), contentScale = ContentScale.Fit)
            else Text(tr("No pages yet", "Henüz sayfa yok"), color = T.sub, fontSize = 13.sp)
            if (pages.isNotEmpty()) Text(tr("${pages.size} page(s)", "${pages.size} sayfa"), color = T.sub, fontSize = 12.sp)
            if (busy) Text(tr("Working...", "İşleniyor..."), color = T.sub, fontSize = 12.sp)
            if (status.isNotEmpty()) Text(status, color = T.text, fontSize = 13.sp)
        }
        if (!finished) {
            if (pages.isNotEmpty()) Text(tr("Add another page or finish?", "Başka sayfa eklensin mi, bitirilsin mi?"), color = T.sub, fontSize = 13.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(if (pages.isEmpty()) tr("Scan page", "Sayfa tara") else tr("Add page", "Sayfa ekle"), Modifier.weight(1f)) { startShot() }
                PButton(tr("From gallery", "Galeriden"), Modifier.weight(1f)) { if (!busy) pick.launch("image/*") }
                if (pages.isNotEmpty()) PButton(tr("Finish", "Bitir"), Modifier.weight(1f)) { finished = true }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton(tr("Save PDF", "PDF kaydet"), Modifier.weight(1f)) { save(true) }
                PButton(tr("Save PNG (this page)", "PNG kaydet (bu sayfa)"), Modifier.weight(1f)) { save(false) }
            }
            PButton(tr("Add page", "Sayfa ekle"), Modifier.fillMaxWidth()) { finished = false; startShot() }
        }
    }
}
