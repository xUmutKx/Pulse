package com.umutk.pulse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF tools (merge / split / images-to-PDF): built on android.graphics.pdf.PdfDocument + PdfRenderer only, the same
 * technique OfPdfScreen's "Save a copy" already uses — no PDF library is added, so this costs nothing in APK size.
 * The trade-off is the one that technique already has: every page is rasterized (a picture of the page), so the
 * result is not text-selectable. Fine for combining scans, printouts and photos into one PDF; not a replacement
 * for a vector-aware editor.
 */
private const val PDF_PAGE_W = 595 // A4 at 72 dpi, matching OfPdfScreen's own "Save a copy"
private const val RENDER_W = 1240 // ~150 dpi at that width

private fun uriToTemp(ctx: Context, uri: Uri, ext: String): File {
    val f = File(ctx.cacheDir, "pdftools/${System.nanoTime()}.$ext").also { it.parentFile?.mkdirs() }
    ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } } ?: throw java.io.IOException("cannot read")
    return f
}

private fun targetFile(name0: String): File {
    val dir = OfficePrefs.folder.apply { mkdirs() }
    val name = name0
    var f = File(dir, "$name.pdf"); var n = 2
    while (f.exists()) f = File(dir, "$name ($n).pdf").also { n++ }
    return f
}

/** "1-3,5,8-9" (1-based, as typed) -> a sorted, de-duplicated list of 0-based page indices within 0 until count. */
private fun parsePageRanges(text: String, count: Int): List<Int> {
    val out = sortedSetOf<Int>()
    text.split(',').forEach { part ->
        val p = part.trim(); if (p.isEmpty()) return@forEach
        val m = p.split('-')
        val a = m.getOrNull(0)?.trim()?.toIntOrNull() ?: return@forEach
        val b = m.getOrNull(1)?.trim()?.toIntOrNull() ?: a
        for (i in minOf(a, b)..maxOf(a, b)) if (i - 1 in 0 until count) out.add(i - 1)
    }
    return out.toList()
}

private fun renderPageBitmap(r: PdfRenderer, i: Int, renderW: Int = RENDER_W): Bitmap {
    val pg = r.openPage(i)
    try {
        val h = (renderW * pg.height.toFloat() / pg.width).toInt().coerceIn(1, 7000)
        val b = Bitmap.createBitmap(renderW, h, Bitmap.Config.ARGB_8888)
        b.eraseColor(android.graphics.Color.WHITE)
        pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return b
    } finally { pg.close() }
}

private fun pdfMerge(ctx: Context, uris: List<Uri>, outName: String): File {
    val o = otOut(ctx)
    val pd = PdfDocument()
    try {
        var pageNo = 1
        uris.forEach { u ->
            val tmp = uriToTemp(ctx, u, "pdf")
            val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            try { for (i in 0 until r.pageCount) { val bm = renderPageBitmap(r, i, o.renderW); pd.addPicturePage(bm, pageNo++, o); bm.recycle() } }
            finally { r.close(); pfd.close(); tmp.delete() }
        }
        val out = targetFile(outName)
        FileOutputStream(out).use { pd.writeTo(it) }
        return out
    } finally { pd.close() }
}

private fun pdfSplit(ctx: Context, uri: Uri, pages: String, outName: String): File {
    val o = otOut(ctx)
    val tmp = uriToTemp(ctx, uri, "pdf")
    val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
    val r = PdfRenderer(pfd)
    val pd = PdfDocument()
    try {
        val idx = parsePageRanges(pages, r.pageCount).ifEmpty { (0 until r.pageCount).toList() }
        idx.forEachIndexed { k, i -> val bm = renderPageBitmap(r, i, o.renderW); pd.addPicturePage(bm, k + 1, o); bm.recycle() }
        val out = targetFile(outName)
        FileOutputStream(out).use { pd.writeTo(it) }
        return out
    } finally { pd.close(); r.close(); pfd.close(); tmp.delete() }
}

/** Pages removed: the 1-based list typed by the person (blank = nothing, so the file is not changed by mistake). */
private fun pdfDelete(ctx: Context, uri: Uri, pages: String, outName: String): File {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val src = uriToTemp(ctx, uri, "pdf")
    com.tom_roush.pdfbox.pdmodel.PDDocument.load(src).use { doc ->
        val drop = parsePageRanges(pages, doc.numberOfPages)
        if (drop.isEmpty()) throw java.io.IOException(tr("Type the pages to delete", "Silinecek sayfaları yaz"))
        if (drop.size >= doc.numberOfPages) throw java.io.IOException(tr("A PDF needs at least one page", "PDF en az bir sayfa olmalı"))
        for (i in drop.sortedDescending()) doc.removePage(i)
        val out = targetFile(outName); doc.save(out); return out
    }
}

/** Pages turned by [deg] degrees clockwise (blank pages = all). */
private fun pdfRotate(ctx: Context, uri: Uri, pages: String, deg: Int, outName: String): File {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val src = uriToTemp(ctx, uri, "pdf")
    com.tom_roush.pdfbox.pdmodel.PDDocument.load(src).use { doc ->
        val which = if (pages.isBlank()) (0 until doc.numberOfPages).toList() else parsePageRanges(pages, doc.numberOfPages)
        for (i in which) { val pg = doc.getPage(i); pg.rotation = ((pg.rotation + deg) % 360 + 360) % 360 }
        val out = targetFile(outName); doc.save(out); return out
    }
}

/** The text of the document, as a plain text file. */
private fun pdfText(ctx: Context, uri: Uri, outName: String): File {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val src = uriToTemp(ctx, uri, "pdf")
    com.tom_roush.pdfbox.pdmodel.PDDocument.load(src).use { doc ->
        val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
        if (text.isBlank()) throw java.io.IOException(tr("No text in this PDF (scanned pages have only pictures)", "Bu PDF'de yazı yok (taranmış sayfalarda yalnızca resim var)"))
        val dir = OfficePrefs.folder.apply { mkdirs() }
        var f = File(dir, "$outName.txt"); var n = 2
        while (f.exists()) f = File(dir, "$outName ($n).txt").also { n++ }
        f.writeText(text); return f
    }
}

/** Every page as a PNG in a folder of its own; returns the first picture. */
private fun pdfToImages(ctx: Context, uri: Uri, outName: String): File {
    val src = uriToTemp(ctx, uri, "pdf")
    val dir = File(OfficePrefs.folder, outName).apply { mkdirs() }
    var first: File? = null
    ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { r ->
            for (i in 0 until r.pageCount) {
                val bmp = renderPageBitmap(r, i)
                val f = File(dir, "page-${(i + 1).toString().padStart(3, '0')}.png")
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
                if (first == null) first = f
            }
        }
    }
    return first ?: throw java.io.IOException(tr("No pages", "Sayfa yok"))
}

/** A password on the file (AES-256); opening it then needs the password. */
private fun pdfLock(ctx: Context, uri: Uri, password: String, outName: String): File {
    if (password.length < 4) throw java.io.IOException(tr("The password needs 4 characters or more", "Şifre en az 4 karakter olmalı"))
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val src = uriToTemp(ctx, uri, "pdf")
    com.tom_roush.pdfbox.pdmodel.PDDocument.load(src).use { doc ->
        val policy = com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy(password, password, com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission())
        policy.encryptionKeyLength = 256
        doc.protect(policy)
        val out = targetFile(outName); doc.save(out); return out
    }
}

/** Removes the password of a file (the right one must be typed). */
private fun pdfUnlock(ctx: Context, uri: Uri, password: String, outName: String): File {
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val src = uriToTemp(ctx, uri, "pdf")
    com.tom_roush.pdfbox.pdmodel.PDDocument.load(src, password).use { doc ->
        doc.setAllSecurityToBeRemoved(true)
        val out = targetFile(outName); doc.save(out); return out
    }
}

/** Writes [text] over every page, in grey, large and in the middle. */
private fun pdfWatermark(ctx: Context, uri: Uri, text: String, outName: String): File {
    if (text.isBlank()) throw java.io.IOException(tr("Type the watermark text", "Filigran yazısını yaz"))
    com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(ctx)
    val src = uriToTemp(ctx, uri, "pdf")
    com.tom_roush.pdfbox.pdmodel.PDDocument.load(src).use { doc ->
        val font = com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD
        for (pg in doc.pages) {
            val box = pg.mediaBox
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, pg, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                val size = 48f
                val w = font.getStringWidth(text) / 1000f * size
                cs.setNonStrokingColor(0.7f)
                cs.beginText(); cs.setFont(font, size)
                cs.newLineAtOffset(box.lowerLeftX + (box.width - w) / 2f, box.lowerLeftY + box.height / 2f)
                cs.showText(text); cs.endText()
            }
        }
        val out = targetFile(outName); doc.save(out); return out
    }
}

/** A smaller copy: each page is drawn again as a JPEG at a lower quality (pages become pictures, so text is no longer selectable). */
private fun pdfCompress(ctx: Context, uri: Uri, outName: String): File {
    val src = uriToTemp(ctx, uri, "pdf")
    val out = targetFile(outName)
    ParcelFileDescriptor.open(src, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { r ->
            val doc = PdfDocument()
            try {
                for (i in 0 until r.pageCount) {
                    val bmp = renderPageBitmap(r, i, 1000)
                    val jpg = java.io.ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
                    val back = BitmapFactory.decodeByteArray(jpg, 0, jpg.size)
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, (back.height * 595f / back.width).toInt().coerceAtLeast(1), i + 1).create())
                    page.canvas.drawBitmap(back, null, android.graphics.RectF(0f, 0f, 595f, (back.height * 595f / back.width)), null)
                    doc.finishPage(page); bmp.recycle(); back.recycle()
                }
                FileOutputStream(out).use { doc.writeTo(it) }
            } finally { doc.close() }
        }
    }
    return out
}

private fun imagesToPdf(ctx: Context, uris: List<Uri>, outName: String): File {
    val o = otOut(ctx)
    val pd = PdfDocument()
    try {
        uris.forEachIndexed { k, u ->
            val bytes = ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } ?: return@forEachIndexed
            val bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@forEachIndexed
            pd.addPicturePage(bm, k + 1, o); bm.recycle()
        }
        val out = targetFile(outName)
        FileOutputStream(out).use { pd.writeTo(it) }
        return out
    } finally { pd.close() }
}

private enum class PdfJob { MERGE, SPLIT, IMAGES, DELETE, ROTATE, TEXT, TOIMG, LOCK, UNLOCK, WATERMARK, COMPRESS }

/** PDF Tools: merge, split/extract pages, and images-to-PDF — reached from the Office hub. */
@Composable
fun PdfToolsPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<PdfJob?>(null) }
    var pickedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var nameText by remember { mutableStateOf("") }
    var rangeText by remember { mutableStateOf("") }
    var secretText by remember { mutableStateOf("") }
    var pendingJob by remember { mutableStateOf(PdfJob.SPLIT) }

    fun doJob(block: () -> File) {
        busy = true
        scope.launch {
            val msg = try { val f = withContext(Dispatchers.IO) { block() }; if (OtPrefs.openAfter(ctx)) ofOpen(ctx, f); tr("Saved: ${f.name}", "Kaydedildi: ${f.name}") }
            catch (e: Throwable) { tr("Could not create the PDF: ${e.message ?: e.javaClass.simpleName}", "PDF oluşturulamadı: ${e.message ?: e.javaClass.simpleName}") }
            busy = false; Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }

    val pickPdfs = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { us ->
        if (us.size >= 2) { pickedUris = us; nameText = OtPrefs.prefix(ctx).ifEmpty { "Merged" }; job = PdfJob.MERGE }
        else if (us.isNotEmpty()) Toast.makeText(ctx, tr("Pick two or more PDFs", "İki veya daha fazla PDF seç"), Toast.LENGTH_SHORT).show()
    }
    val pickPdfOne = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) { pickedUris = listOf(u); nameText = OtPrefs.prefix(ctx).ifEmpty { "Split" }; rangeText = ""; secretText = ""; job = pendingJob }
    }
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { us ->
        if (us.isNotEmpty()) { pickedUris = us; nameText = OtPrefs.prefix(ctx).ifEmpty { "Images" }; job = PdfJob.IMAGES }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            PIcon("convert", T.accent, 24.dp)
            Text(tr("PDF tools", "PDF araçları"), Modifier.padding(start = 8.dp), color = T.text, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        }
        PCard {
            Text(tr("Pages are redrawn as pictures: great for combining scans and printouts, not for text-selectable results.",
                "Sayfalar resim olarak yeniden çizilir: taramaları ve çıktıları birleştirmek için uygun, metin seçilebilir sonuç vermez."),
                color = T.sub, fontSize = 12.sp)
        }
        PButton(tr("Merge PDFs", "PDF'leri birleştir"), Modifier.fillMaxWidth(), icon = Icons.Outlined.MergeType) { pickPdfs.launch("application/pdf") }
        PButton(tr("Split / extract pages", "Böl / sayfa çıkar"), Modifier.fillMaxWidth(), icon = Icons.Outlined.ContentCut) { pickPdfOne.launch("application/pdf") }
        PButton(tr("Images to PDF", "Resimleri PDF yap"), Modifier.fillMaxWidth(), icon = Icons.Outlined.Image) { pickImages.launch("image/*") }
        // one PDF each: the file is picked, then the tool asks for what it needs
        listOf(
            PdfJob.DELETE to tr("Delete pages", "Sayfa sil"), PdfJob.ROTATE to tr("Rotate pages", "Sayfa döndür"), PdfJob.TEXT to tr("Extract text", "Metni çıkar"),
            PdfJob.TOIMG to tr("PDF to images", "PDF'den resme"), PdfJob.LOCK to tr("Lock PDF (password)", "PDF'i kilitle (şifre)"), PdfJob.UNLOCK to tr("Unlock PDF", "PDF'in kilidini aç"),
            PdfJob.WATERMARK to tr("Watermark", "Filigran"), PdfJob.COMPRESS to tr("Compress PDF", "PDF'i küçült"),
        ).forEach { (j, label) -> PButton(label, Modifier.fillMaxWidth(), compact = true) { pendingJob = j; pickPdfOne.launch("application/pdf") } }
    }

    job?.let { j ->
        WinAlert(onDismissRequest = { job = null },
            title = { Text(when (j) { PdfJob.MERGE -> tr("Merge PDFs", "PDF'leri birleştir"); PdfJob.SPLIT -> tr("Split / extract pages", "Böl / sayfa çıkar"); PdfJob.IMAGES -> tr("Images to PDF", "Resimleri PDF yap") }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (j == PdfJob.MERGE) Text(tr("${pickedUris.size} files, in the order picked", "${pickedUris.size} dosya, seçilen sırayla"), color = T.sub, fontSize = 12.sp)
                    if (j == PdfJob.IMAGES) Text(tr("${pickedUris.size} images", "${pickedUris.size} resim"), color = T.sub, fontSize = 12.sp)
                    if (j == PdfJob.SPLIT) PField(rangeText, { rangeText = it }, tr("Pages (e.g. 1-3,5) — blank = all", "Sayfalar (örn. 1-3,5) — boş = hepsi"))
                    if (j == PdfJob.DELETE) PField(rangeText, { rangeText = it }, tr("Pages to delete (e.g. 2,4-5)", "Silinecek sayfalar (örn. 2,4-5)"))
                    if (j == PdfJob.ROTATE) PField(rangeText, { rangeText = it }, tr("Pages to turn 90° (blank = all)", "90° döndürülecek sayfalar (boş = hepsi)"))
                    if (j == PdfJob.LOCK || j == PdfJob.UNLOCK) PField(secretText, { secretText = it }, tr("Password", "Şifre"))
                    if (j == PdfJob.WATERMARK) PField(secretText, { secretText = it }, tr("Watermark text", "Filigran yazısı"))
                    PField(nameText, { nameText = it }, tr("File name", "Dosya adı"))
                }
            },
            confirmButton = { TextButton({
                val n = nameText.ifBlank { "PDF" }; val us = pickedUris; val rg = rangeText
                job = null
                val sec = secretText
                when (j) {
                    PdfJob.MERGE -> doJob { pdfMerge(ctx, us, n) }; PdfJob.SPLIT -> doJob { pdfSplit(ctx, us.first(), rg, n) }; PdfJob.IMAGES -> doJob { imagesToPdf(ctx, us, n) }
                    PdfJob.DELETE -> doJob { pdfDelete(ctx, us.first(), rg, n) }; PdfJob.ROTATE -> doJob { pdfRotate(ctx, us.first(), rg, 90, n) }
                    PdfJob.TEXT -> doJob { pdfText(ctx, us.first(), n) }; PdfJob.TOIMG -> doJob { pdfToImages(ctx, us.first(), n) }
                    PdfJob.LOCK -> doJob { pdfLock(ctx, us.first(), sec, n) }; PdfJob.UNLOCK -> doJob { pdfUnlock(ctx, us.first(), sec, n) }
                    PdfJob.WATERMARK -> doJob { pdfWatermark(ctx, us.first(), sec, n) }; PdfJob.COMPRESS -> doJob { pdfCompress(ctx, us.first(), n) }
                }
            }) { Text(tr("Create", "Oluştur")) } },
            dismissButton = { TextButton({ job = null }) { Text(tr("Cancel", "İptal")) } })
    }

    if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
