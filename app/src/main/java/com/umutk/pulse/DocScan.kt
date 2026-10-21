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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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

/** Colour scan: one levels stretch on the brightness (paper goes white, ink dark) applied to all three channels, so colours stay true, then a little more colour. */
fun docScanColor(src: Bitmap): Bitmap {
    val w = src.width; val h = src.height
    val px = IntArray(w * h).also { src.getPixels(it, 0, w, 0, 0, w, h) }
    val hist = IntArray(256)
    for (c in px) hist[((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000]++
    fun level(p: Double): Int { var acc = 0; val cut = px.size * p; for (v in 0..255) { acc += hist[v]; if (acc >= cut) return v }; return 255 }
    val lo = level(0.02); val hi = level(0.97).coerceAtLeast(lo + 1)
    val lut = IntArray(256) { v -> ((v - lo) * 255 / (hi - lo)).coerceIn(0, 255) }
    val out = IntArray(px.size) { i ->
        val c = px[i]
        val r = lut[c shr 16 and 0xFF]; val g = lut[c shr 8 and 0xFF]; val b = lut[c and 0xFF]
        val l = (r * 299 + g * 587 + b * 114) / 1000
        fun sat(v: Int) = (l + (v - l) * 1.18f).toInt().coerceIn(0, 255)
        (0xFF shl 24) or (sat(r) shl 16) or (sat(g) shl 8) or sat(b)
    }
    return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
}

/** Black and white scan: the stretched grey, pushed to clean white paper and solid ink. */
fun docScanBw(src: Bitmap): Bitmap {
    val g = docScanEnhance(src)
    val w = g.width; val h = g.height
    val px = IntArray(w * h).also { g.getPixels(it, 0, w, 0, 0, w, h) }
    for (i in px.indices) { val v = px[i] and 0xFF; val o = if (v > 175) 255 else if (v < 95) 0 else (v - 95) * 255 / 80; px[i] = (0xFF shl 24) or (o shl 16) or (o shl 8) or o }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/**
 * Finds the page in the photo and straightens it: the bright paper area is cut out from a small copy (a brightness threshold, the biggest connected
 * region), its four corners are the extremes along both diagonals, and a perspective warp turns that quadrilateral into a flat rectangle.
 * A photo where no clear page is found (the page fills the frame, or sits on a light table) is returned as it is.
 */
fun docScanCrop(src: Bitmap): Bitmap {
    val sc = 360f / maxOf(src.width, src.height)
    if (sc >= 1f) return src
    val sw = (src.width * sc).toInt().coerceAtLeast(8); val sh = (src.height * sc).toInt().coerceAtLeast(8)
    val small = Bitmap.createScaledBitmap(src, sw, sh, true)
    val px = IntArray(sw * sh).also { small.getPixels(it, 0, sw, 0, 0, sw, sh) }
    val lum = IntArray(px.size) { i -> val c = px[i]; ((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000 }
    // Otsu threshold
    val hist = IntArray(256).also { hh -> lum.forEach { hh[it]++ } }
    var sum = 0L; for (v in 0..255) sum += v.toLong() * hist[v]
    var wB = 0L; var sB = 0L; var best = 0.0; var thr = 128
    for (v in 0..255) { wB += hist[v]; if (wB == 0L) continue; val wF = lum.size - wB; if (wF == 0L) break; sB += v.toLong() * hist[v]
        val mB = sB.toDouble() / wB; val mF = (sum - sB).toDouble() / wF; val bt = wB.toDouble() * wF * (mB - mF) * (mB - mF); if (bt > best) { best = bt; thr = v } }
    // biggest connected bright region (4-neighbour flood fill)
    val seen = BooleanArray(px.size); var bestRegion = IntArray(0)
    val stack = IntArray(px.size)
    for (start in px.indices) {
        if (seen[start] || lum[start] <= thr) continue
        var sp = 0; stack[sp++] = start; seen[start] = true; val region = ArrayList<Int>()
        while (sp > 0) {
            val i = stack[--sp]; region.add(i); val x = i % sw; val y = i / sw
            if (x > 0 && !seen[i - 1] && lum[i - 1] > thr) { seen[i - 1] = true; stack[sp++] = i - 1 }
            if (x < sw - 1 && !seen[i + 1] && lum[i + 1] > thr) { seen[i + 1] = true; stack[sp++] = i + 1 }
            if (y > 0 && !seen[i - sw] && lum[i - sw] > thr) { seen[i - sw] = true; stack[sp++] = i - sw }
            if (y < sh - 1 && !seen[i + sw] && lum[i + sw] > thr) { seen[i + sw] = true; stack[sp++] = i + sw }
        }
        if (region.size > bestRegion.size) bestRegion = region.toIntArray()
    }
    val frac = bestRegion.size.toFloat() / px.size
    if (frac < .2f || frac > .96f) return src
    var tl = 0; var br = 0; var tr = 0; var bl = 0
    var mnS = Int.MAX_VALUE; var mxS = Int.MIN_VALUE; var mnD = Int.MAX_VALUE; var mxD = Int.MIN_VALUE
    for (i in bestRegion) { val x = i % sw; val y = i / sw; val s = x + y; val d = x - y
        if (s < mnS) { mnS = s; tl = i }; if (s > mxS) { mxS = s; br = i }; if (d > mxD) { mxD = d; tr = i }; if (d < mnD) { mnD = d; bl = i } }
    fun pt(i: Int) = floatArrayOf((i % sw) / sc, (i / sw) / sc)
    val a = pt(tl); val b = pt(tr); val c = pt(br); val d = pt(bl)
    fun dist(p: FloatArray, q: FloatArray) = kotlin.math.hypot((p[0] - q[0]).toDouble(), (p[1] - q[1]).toDouble()).toFloat()
    val ow = ((dist(a, b) + dist(d, c)) / 2f).toInt(); val oh = ((dist(a, d) + dist(b, c)) / 2f).toInt()
    if (ow < 200 || oh < 200) return src
    val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
    val m = android.graphics.Matrix()
    m.setPolyToPoly(floatArrayOf(a[0], a[1], b[0], b[1], c[0], c[1], d[0], d[1]), 0, floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat()), 0, 4)
    android.graphics.Canvas(out).drawBitmap(src, m, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
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
    // the pages are kept as the straightened photo; the look (colour, grey, black and white) is applied to the preview and when saving
    var mode by remember { mutableIntStateOf(0) }
    var autoCrop by remember { mutableStateOf(true) }
    fun shape(b: Bitmap): Bitmap = when (mode) { 0 -> docScanColor(b); 1 -> docScanEnhance(b); else -> docScanBw(b) }

    // a camera photo or a picture from the gallery: both become a page the same way
    fun addPage(f: File) {
        busy = true
        scope.launch {
            val page = withContext(Dispatchers.IO) { runCatching { docScanDecode(f).let { if (autoCrop) docScanCrop(it) else it } }.getOrNull() }
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
        val raw = pages.toList()
        if (raw.isEmpty() || busy) return
        busy = true
        scope.launch {
            val list = withContext(Dispatchers.Default) { raw.map { shape(it) } }
            val last = list.last()
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
            val shown by produceState<Bitmap?>(null, last, mode) { value = last?.let { withContext(Dispatchers.Default) { shape(it) } } }
            // the page sits on a soft shadow with rounded corners, like paper on a desk
            shown?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(6.dp).shadow(6.dp, androidx.compose.foundation.shape.RoundedCornerShape(4.dp)).clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp)), contentScale = ContentScale.Fit) }
            if (last == null) Text(tr("No pages yet", "Henüz sayfa yok"), color = T.sub, fontSize = 13.sp)
            if (pages.isNotEmpty()) Text(tr("${pages.size} page(s)", "${pages.size} sayfa"), color = T.sub, fontSize = 12.sp)
            if (busy) Text(tr("Working...", "İşleniyor..."), color = T.sub, fontSize = 12.sp)
            if (status.isNotEmpty()) Text(status, color = T.text, fontSize = 13.sp)
        }
        // how the page looks, and whether the page is cut out of the photo
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PChip(mode == 0, tr("Colour", "Renkli")) { mode = 0 }
            PChip(mode == 1, tr("Grey", "Gri")) { mode = 1 }
            PChip(mode == 2, tr("Black & white", "Siyah beyaz")) { mode = 2 }
            Spacer(Modifier.weight(1f))
            Text(tr("Crop to page", "Sayfayı kırp"), color = T.sub, fontSize = 12.sp)
            PSwitch(autoCrop, { autoCrop = it })
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
