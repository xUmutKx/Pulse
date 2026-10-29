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

private enum class PdfJob { MERGE, SPLIT, IMAGES }

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
        if (u != null) { pickedUris = listOf(u); nameText = OtPrefs.prefix(ctx).ifEmpty { "Split" }; rangeText = ""; job = PdfJob.SPLIT }
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
    }

    job?.let { j ->
        WinAlert(onDismissRequest = { job = null },
            title = { Text(when (j) { PdfJob.MERGE -> tr("Merge PDFs", "PDF'leri birleştir"); PdfJob.SPLIT -> tr("Split / extract pages", "Böl / sayfa çıkar"); PdfJob.IMAGES -> tr("Images to PDF", "Resimleri PDF yap") }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (j == PdfJob.MERGE) Text(tr("${pickedUris.size} files, in the order picked", "${pickedUris.size} dosya, seçilen sırayla"), color = T.sub, fontSize = 12.sp)
                    if (j == PdfJob.IMAGES) Text(tr("${pickedUris.size} images", "${pickedUris.size} resim"), color = T.sub, fontSize = 12.sp)
                    if (j == PdfJob.SPLIT) PField(rangeText, { rangeText = it }, tr("Pages (e.g. 1-3,5) — blank = all", "Sayfalar (örn. 1-3,5) — boş = hepsi"))
                    PField(nameText, { nameText = it }, tr("File name", "Dosya adı"))
                }
            },
            confirmButton = { TextButton({
                val n = nameText.ifBlank { "PDF" }; val us = pickedUris; val rg = rangeText
                job = null
                when (j) { PdfJob.MERGE -> doJob { pdfMerge(ctx, us, n) }; PdfJob.SPLIT -> doJob { pdfSplit(ctx, us.first(), rg, n) }; PdfJob.IMAGES -> doJob { imagesToPdf(ctx, us, n) } }
            }) { Text(tr("Create", "Oluştur")) } },
            dismissButton = { TextButton({ job = null }) { Text(tr("Cancel", "İptal")) } })
    }

    if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}
