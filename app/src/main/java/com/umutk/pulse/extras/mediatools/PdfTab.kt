package com.umutk.pulse.extras.mediatools

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * PDF tools: join several PDFs into one, or take a range of pages out of one. Android can only draw a PDF page, not copy it, so every page is drawn onto a new
 * page of the same size at twice the resolution: the result looks the same, but its text can no longer be selected and the file is larger. The pictures to
 * PDF way is in the Convert tab.
 */
@Composable
internal fun PdfTab() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { l -> if (l.isNotEmpty()) { files = l; note = "" } }
    PCard {
        Text(tr("Join or cut PDFs", "PDF birleştir veya kes"), color = T.text, fontSize = 16.sp)
        PButton(tr("Choose PDFs", "PDF seç"), compact = true) { pick.launch(arrayOf("application/pdf")) }
        if (files.isNotEmpty()) files.forEach { Text(nameOf(ctx, it) + "  (" + fmt(sizeOf(ctx, it)) + ")", color = T.sub, fontSize = 12.sp) }
        Text(tr("The pages are copied as pictures: the text can no longer be selected.", "Sayfalar resim olarak kopyalanır: yazı artık seçilemez."), color = T.sub, fontSize = 11.sp)
        if (files.size > 1) PButton(tr("Join into one PDF", "Tek PDF'de birleştir"), Modifier.fillMaxWidth(), compact = true) {
            if (busy) return@PButton
            busy = true; note = tr("Working…", "Çalışıyor…")
            scope.launch {
                note = withContext(Dispatchers.IO) { runCatching { val n = "pulse-joined-" + System.currentTimeMillis() / 1000 + ".pdf"; val pages = build(ctx, files.map { it to (1..Int.MAX_VALUE) }, n); tr("Saved $n ($pages pages) in Download/Pulse", "$n kaydedildi ($pages sayfa), Download/Pulse içinde") }.getOrElse { tr("Could not join: ", "Birleştirilemedi: ") + (it.message ?: it.javaClass.simpleName) } }
                busy = false
            }
        }
        if (files.size == 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PField(from, { from = it.filter { c -> c.isDigit() } }, tr("From page", "İlk sayfa"), Modifier.weight(1f))
                PField(to, { to = it.filter { c -> c.isDigit() } }, tr("To page (empty = end)", "Son sayfa (boş = sona kadar)"), Modifier.weight(1f))
            }
            PButton(tr("Save these pages", "Bu sayfaları kaydet"), Modifier.fillMaxWidth(), compact = true) {
                if (busy) return@PButton
                val a = from.toIntOrNull()?.coerceAtLeast(1) ?: 1; val b = to.toIntOrNull() ?: Int.MAX_VALUE
                if (b < a) { note = tr("The last page comes before the first.", "Son sayfa ilk sayfadan önce."); return@PButton }
                busy = true; note = tr("Working…", "Çalışıyor…")
                scope.launch {
                    note = withContext(Dispatchers.IO) { runCatching { val n = "pulse-pages-" + System.currentTimeMillis() / 1000 + ".pdf"; val pages = build(ctx, listOf(files[0] to (a..b)), n); tr("Saved $n ($pages pages) in Download/Pulse", "$n kaydedildi ($pages sayfa), Download/Pulse içinde") }.getOrElse { tr("Could not cut: ", "Kesilemedi: ") + (it.message ?: it.javaClass.simpleName) } }
                    busy = false
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
    }
}

/** Draws the chosen pages (1-based, clipped to what each file has) into one new PDF in Download/Pulse; returns the page count. */
private fun build(ctx: android.content.Context, parts: List<Pair<Uri, IntRange>>, name: String): Int {
    val doc = PdfDocument()
    var n = 0
    try {
        parts.forEach { (u, range) ->
            val f = cacheCopy(ctx, u, "pdf-in.pdf")
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            try {
                for (i in (range.first - 1).coerceAtLeast(0)..minOf(range.last - 1, r.pageCount - 1)) {
                    val p = r.openPage(i)
                    val w = p.width; val h = p.height
                    val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); p.close()
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, ++n).create())
                    page.canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
                    doc.finishPage(page); bmp.recycle()
                    if (n >= 400) break
                }
            } finally { r.close(); pfd.close(); f.delete() }
        }
        if (n == 0) throw java.io.IOException(tr("no such page", "böyle bir sayfa yok"))
        val bytes = ByteArrayOutputStream().also { doc.writeTo(it) }.toByteArray()
        kotlinx.coroutines.runBlocking { com.umutk.pulse.docScanSave(ctx, name, "application/pdf") { it.write(bytes) } }
    } finally { doc.close() }
    return n
}
