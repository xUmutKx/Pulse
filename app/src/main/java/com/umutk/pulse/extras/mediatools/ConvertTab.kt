package com.umutk.pulse.extras.mediatools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/** What a kind of file can be turned into: id and name. */
private fun targets(kind: String): List<Pair<String, String>> = when (kind) {
    "image" -> listOf("png" to "PNG", "jpg" to "JPG", "webp" to "WEBP", "pdf" to "PDF")
    "gif" -> listOf("png" to "PNG", "jpg" to "JPG", "webp" to "WEBP")
    "video" -> listOf("mp4" to "MP4", "m4a" to tr("Audio (M4A)", "Ses (M4A)"))
    "audio" -> listOf("m4a" to "M4A")
    "pdf" -> listOf("png" to tr("PNG pictures", "PNG resimler"), "jpg" to tr("JPG pictures", "JPG resimler"))
    "docx" -> listOf("pdf" to "PDF", "txt" to "TXT")
    "xlsx" -> listOf("csv" to "CSV")
    "text" -> listOf("pdf" to "PDF", "txt" to "TXT")
    else -> emptyList()
}

/** The Convert page: a file (or several pictures) goes in, a file of another kind comes out in Download/Pulse. */
@Composable
internal fun ConvertTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var kind by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { list ->
        if (list.isEmpty()) return@rememberLauncherForActivityResult
        files = list; note = ""
        kind = kindOf(nameOf(ctx, list.first())); to = targets(kind).firstOrNull()?.first.orEmpty()
    }
    val names = files.map { nameOf(ctx, it) }
    val opts = targets(kind) + (if (kind == "image" && files.size > 1) listOf("onepdf" to tr("One PDF", "Tek PDF")) else emptyList())
    PCard {
        Text(tr("Change a file into another kind", "Dosyayı başka türe çevir"), color = T.text, fontSize = 16.sp)
        PButton(tr("Choose files", "Dosya seç"), compact = true) { pick.launch(arrayOf("*/*")) }
        if (files.isNotEmpty()) Text(if (files.size == 1) names.first() + "  (" + fmt(sizeOf(ctx, files.first())) + ")" else "${files.size} " + tr("files", "dosya"), color = T.sub, fontSize = 13.sp)
        if (files.isNotEmpty() && opts.isEmpty()) Text(tr("This kind of file cannot be changed here.", "Bu tür dosya burada çevrilemiyor."), color = T.sub, fontSize = 13.sp)
        if (opts.isNotEmpty()) {
            Text(tr("Change to", "Şuna çevir"), color = T.sub, fontSize = 12.sp)
            Row(androidx.compose.ui.Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                opts.forEach { (id, n) -> PChip(to == id, n) { to = id } }
            }
            if (kind == "docx" || kind == "xlsx") Text(tr("Text and table cells only: pictures and page layout are not kept.", "Yalnız yazı ve tablo hücreleri: resimler ve sayfa düzeni korunmaz."), color = T.sub, fontSize = 12.sp)
            PButton(if (busy) tr("Working…", "Çalışıyor…") else tr("Convert", "Çevir")) {
                if (busy || to.isEmpty()) return@PButton
                busy = true; note = ""
                scope.launch {
                    note = runCatching { convert(ctx, files, names, kind, to) }.getOrElse { tr("Failed: ", "Olmadı: ") + (it.message ?: it.javaClass.simpleName) }
                    busy = false
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = T.text, fontSize = 13.sp)
    }
}

private suspend fun convert(ctx: android.content.Context, files: List<Uri>, names: List<String>, kind: String, to: String): String {
    val first = files.first(); val name = names.first(); val base = baseOf(name)
    return when {
        to == "onepdf" -> { val n = withContext(Dispatchers.IO) { imagesToPdf(ctx, files, "${baseOf(names.first())}.pdf") }; tr("Saved ", "Kaydedildi: ") + n }
        kind == "image" && to == "pdf" -> { withContext(Dispatchers.IO) { imagesToPdf(ctx, files, "$base.pdf") }; tr("Saved ", "Kaydedildi: ") + "$base.pdf" }
        kind == "image" || kind == "gif" -> {
            var count = 0
            files.forEachIndexed { i, u ->
                val nm = names[i]
                withContext(Dispatchers.IO) {
                    val bmp = if (kindOf(nm) == "gif") gifFirstFrame(ctx, u) else decodeUpright(ctx, u, 6000)
                    val bytes = ByteArrayOutputStream()
                    encodeAs(bmp, to, bytes)
                    com.umutk.pulse.docScanSave(ctx, baseOf(nm) + "." + to, mimeOf(to)) { it.write(bytes.toByteArray()) }
                }
                count++
            }
            tr("Saved $count in Download/Pulse", "$count dosya Download/Pulse içine kaydedildi")
        }
        kind == "video" || kind == "audio" -> {
            val out = File(File(ctx.cacheDir, "mediatools").apply { mkdirs() }, "conv_${System.nanoTime()}.mp4")
            val audio = to == "m4a"
            val err = transform(ctx, first, out, 0, 8_000_000, 160_000, audio)
            if (err != null) { out.delete(); err } else {
                saveFile(ctx, "$base." + (if (audio) "m4a" else "mp4"), if (audio) "audio/mp4" else "video/mp4", out); out.delete()
                tr("Saved ", "Kaydedildi: ") + "$base." + (if (audio) "m4a" else "mp4")
            }
        }
        kind == "pdf" -> {
            val n = withContext(Dispatchers.IO) { pdfToPictures(ctx, first, base, to) }
            tr("Saved $n pages in Download/Pulse", "$n sayfa Download/Pulse içine kaydedildi")
        }
        kind == "docx" || kind == "text" || kind == "xlsx" -> {
            val text = withContext(Dispatchers.IO) {
                when (kind) {
                    "docx" -> docxText(cacheCopy(ctx, first, name))
                    "xlsx" -> xlsxCsv(cacheCopy(ctx, first, name))
                    else -> ctx.contentResolver.openInputStream(first)!!.use { it.readBytes() }.toString(Charsets.UTF_8).let { if (extOf(name) in setOf("html", "htm")) androidx.core.text.HtmlCompat.fromHtml(it, androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY).toString() else it }
                }
            }
            if (to == "pdf") withContext(Dispatchers.IO) { textToPdf(ctx, text, "$base.pdf") }
            else withContext(Dispatchers.IO) { com.umutk.pulse.docScanSave(ctx, "$base.$to", mimeOf(to)) { it.write(text.toByteArray(Charsets.UTF_8)) } }
            tr("Saved ", "Kaydedildi: ") + "$base.$to"
        }
        else -> tr("This kind of file cannot be changed here.", "Bu tür dosya burada çevrilemiyor.")
    }
}

private fun mimeOf(ext: String) = when (ext) { "png" -> "image/png"; "jpg" -> "image/jpeg"; "webp" -> "image/webp"; "pdf" -> "application/pdf"; "csv" -> "text/csv"; "txt" -> "text/plain"; else -> "application/octet-stream" }

@Suppress("DEPRECATION")
private fun encodeAs(b: Bitmap, ext: String, out: java.io.OutputStream) {
    when (ext) {
        "png" -> b.compress(Bitmap.CompressFormat.PNG, 100, out)
        "webp" -> b.compress(if (android.os.Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP, 92, out)
        else -> {
            // JPEG has no see-through: the picture goes on white
            val flat = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(b, 0f, 0f, null) }
            flat.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
    }
}

/** A picture at a size that fits [maxSide], turned upright by its EXIF mark. */
internal fun decodeUpright(ctx: android.content.Context, u: Uri, maxSide: Int): Bitmap {
    val bo = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bo) }
    var step = 1
    while (maxOf(bo.outWidth, bo.outHeight) / (step * 2) >= maxSide) step *= 2
    val raw = ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = step }) } ?: throw java.io.IOException("unreadable")
    val deg = when (if (android.os.Build.VERSION.SDK_INT < 24) null else runCatching { ctx.contentResolver.openInputStream(u)?.use { android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull()) { 6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f }
    return if (deg == 0f) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(deg) }, true)
}

@Suppress("DEPRECATION")
private fun gifFirstFrame(ctx: android.content.Context, u: Uri): Bitmap {
    val bytes = ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() }
    val m = android.graphics.Movie.decodeByteArray(bytes, 0, bytes.size) ?: throw java.io.IOException("not a GIF")
    val b = Bitmap.createBitmap(m.width().coerceAtLeast(1), m.height().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    m.setTime(0); m.draw(Canvas(b), 0f, 0f)
    return b
}

/** Every picture on its own page (the page is as wide as an A4 and as tall as the picture needs). */
private fun imagesToPdf(ctx: android.content.Context, files: List<Uri>, name: String): String {
    val doc = PdfDocument()
    try {
        files.forEachIndexed { i, u ->
            val b = decodeUpright(ctx, u, 2000)
            val w = 595; val h = (595f * b.height / b.width).toInt().coerceAtLeast(1)
            val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, i + 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawBitmap(b, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            doc.finishPage(page)
        }
        val bytes = ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        kotlinx.coroutines.runBlocking { com.umutk.pulse.docScanSave(ctx, name, "application/pdf") { it.write(bytes) } }
    } finally { doc.close() }
    return name
}

/** A PDF page by page into pictures (twice the page size), saved as name_page_1 ... in Download/Pulse. */
private fun pdfToPictures(ctx: android.content.Context, u: Uri, base: String, ext: String): Int {
    val f = cacheCopy(ctx, u, "in.pdf")
    val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    val r = PdfRenderer(pfd)
    try {
        val n = minOf(r.pageCount, 100)
        for (i in 0 until n) {
            val p = r.openPage(i)
            val b = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.WHITE)
            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); p.close()
            val bytes = ByteArrayOutputStream().also { encodeAs(b, ext, it) }.toByteArray()
            kotlinx.coroutines.runBlocking { com.umutk.pulse.docScanSave(ctx, "${base}_page_${i + 1}.$ext", mimeOf(ext)) { it.write(bytes) } }
            b.recycle()
        }
        return n
    } finally { r.close(); pfd.close(); f.delete() }
}

/** Plain text on A4 pages: 12 pt, a 40 pt margin, long lines wrapped. */
private fun textToPdf(ctx: android.content.Context, text: String, name: String) {
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
    val pw = 595; val ph = 842; val m = 40
    @Suppress("DEPRECATION") val lay = StaticLayout(text.ifEmpty { " " }, paint, pw - 2 * m, Layout.Alignment.ALIGN_NORMAL, 1.15f, 0f, false)
    val doc = PdfDocument()
    try {
        val body = ph - 2 * m
        var line = 0; var pageNo = 1
        while (line < lay.lineCount) {
            val top = lay.getLineTop(line)
            var end = line
            while (end < lay.lineCount && lay.getLineBottom(end) - top <= body) end++
            if (end == line) end = line + 1
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo++).create())
            val c = page.canvas
            c.drawColor(Color.WHITE)
            c.save(); c.clipRect(m.toFloat(), m.toFloat(), (pw - m).toFloat(), (ph - m).toFloat()); c.translate(m.toFloat(), (m - top).toFloat()); lay.draw(c); c.restore()
            doc.finishPage(page)
            line = end
        }
        val bytes = ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        kotlinx.coroutines.runBlocking { com.umutk.pulse.docScanSave(ctx, name, "application/pdf") { it.write(bytes) } }
    } finally { doc.close() }
}

private fun unxml(s: String) = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

/** The paragraphs of a .docx as plain text. */
private fun docxText(f: File): String = ZipFile(f).use { z ->
    val xml = z.getEntry("word/document.xml")?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) } ?: throw java.io.IOException("not a Word file")
    xml.split("</w:p>").joinToString("\n") { p ->
        Regex("<w:t(?: [^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>").findAll(p).joinToString("") { m -> if (m.groupValues[1].isNotEmpty() || m.value.startsWith("<w:t")) unxml(m.groupValues[1]) else if (m.value == "<w:tab/>") "\t" else "\n" }
    }.trim()
}

/** The first sheet of an .xlsx as CSV. */
private fun xlsxCsv(f: File): String = ZipFile(f).use { z ->
    val shared = z.getEntry("xl/sharedStrings.xml")?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) }?.let { xml ->
        Regex("<si>(.*?)</si>", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { si -> Regex("<t(?: [^>]*)?>([^<]*)</t>").findAll(si.groupValues[1]).joinToString("") { unxml(it.groupValues[1]) } }.toList()
    } ?: emptyList()
    val sheet = (z.getEntry("xl/worksheets/sheet1.xml") ?: z.entries().asSequence().firstOrNull { it.name.startsWith("xl/worksheets/sheet") })?.let { z.getInputStream(it).readBytes().toString(Charsets.UTF_8) } ?: throw java.io.IOException("not an Excel file")
    fun col(ref: String): Int { var n = 0; for (ch in ref) { if (ch in 'A'..'Z') n = n * 26 + (ch - 'A' + 1) else break }; return n - 1 }
    val rows = ArrayList<List<String>>()
    Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheet).forEach { r ->
        val cells = HashMap<Int, String>()
        Regex("<c ([^>]*?)(?:/>|>(.*?)</c>)", RegexOption.DOT_MATCHES_ALL).findAll(r.groupValues[1]).forEach { c ->
            val attrs = c.groupValues[1]; val inner = c.groupValues[2]
            val ref = Regex("r=\"([A-Z]+)\\d+\"").find(attrs)?.groupValues?.get(1) ?: return@forEach
            val t = Regex("t=\"(\\w+)\"").find(attrs)?.groupValues?.get(1)
            val v = Regex("<v>([^<]*)</v>").find(inner)?.groupValues?.get(1)
            cells[col(ref)] = when (t) {
                "s" -> shared.getOrNull(v?.toIntOrNull() ?: -1).orEmpty()
                "inlineStr" -> Regex("<t(?: [^>]*)?>([^<]*)</t>").findAll(inner).joinToString("") { unxml(it.groupValues[1]) }
                else -> unxml(v.orEmpty())
            }
        }
        val width = (cells.keys.maxOrNull() ?: -1) + 1
        rows += List(width) { cells[it].orEmpty() }
    }
    rows.joinToString("\n") { row -> row.joinToString(",") { c -> if (c.any { it == ',' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c } }
}
