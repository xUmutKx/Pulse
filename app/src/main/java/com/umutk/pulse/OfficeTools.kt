package com.umutk.pulse

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The settings of Office tools (scan and the PDF tools), one place: how pages are laid out, how sharp they are, and how a scan is cut and filtered. */
object OtPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("office_tools", Context.MODE_PRIVATE)
    /** 0 as the picture is, 1 A4, 2 Letter. */
    fun pageSize(c: Context) = sp(c).getInt("page", 1)
    /** 0 small (fast), 1 normal, 2 sharp. */
    fun quality(c: Context) = sp(c).getInt("quality", 1)
    /** 0 none, 1 narrow, 2 wide: the white edge around a picture on a fixed page size. */
    fun margin(c: Context) = sp(c).getInt("margin", 1)
    /** The filter a new scan starts with: see [DocFilter]. */
    fun filter(c: Context) = sp(c).getInt("filter", 0)
    fun autoCrop(c: Context) = sp(c).getBoolean("crop", true)
    /** Percent of the page cut away from every side after the page is flattened, so no table or shadow edge is left. */
    fun trim(c: Context) = sp(c).getInt("trim", 2)
    fun openAfter(c: Context) = sp(c).getBoolean("open", true)
    /** Page numbers at the bottom of a merged or scanned PDF. */
    fun numbers(c: Context) = sp(c).getBoolean("numbers", false)
    fun prefix(c: Context) = sp(c).getString("prefix", "")?.trim().orEmpty()
    fun setInt(c: Context, k: String, v: Int) = sp(c).edit().putInt(k, v).apply()
    fun setBool(c: Context, k: String, v: Boolean) = sp(c).edit().putBoolean(k, v).apply()
    fun setStr(c: Context, k: String, v: String) = sp(c).edit().putString(k, v).apply()
}

/** How PDF pages are written: the page in points (0 = the picture's own shape), the white margin, the sharpness of a rendered page, page numbers. */
class OtOut(val pageW: Int, val pageH: Int, val margin: Int, val renderW: Int, val numbers: Boolean)

fun otOut(c: Context): OtOut {
    val (w, h) = when (OtPrefs.pageSize(c)) { 1 -> 595 to 842; 2 -> 612 to 792; else -> 595 to 0 }
    return OtOut(w, h, listOf(0, 18, 40)[OtPrefs.margin(c).coerceIn(0, 2)], listOf(900, 1240, 1800)[OtPrefs.quality(c).coerceIn(0, 2)], OtPrefs.numbers(c))
}

/** Writes [bm] as page [pageNo]: on a fixed page it is fitted inside the margin and centred, otherwise the page takes the picture's shape. */
fun android.graphics.pdf.PdfDocument.addPicturePage(bm: android.graphics.Bitmap, pageNo: Int, o: OtOut) {
    val fixed = o.pageH > 0
    val pw = o.pageW
    val ph = if (fixed) o.pageH else (bm.height * (pw - 2 * o.margin) / bm.width.toFloat() + 2 * o.margin).toInt().coerceAtLeast(1)
    val page = startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(pw, ph, pageNo).create())
    val c = page.canvas
    val availW = (pw - 2 * o.margin).toFloat(); val availH = (ph - 2 * o.margin).toFloat()
    val s = minOf(availW / bm.width, availH / bm.height)
    val dw = bm.width * s; val dh = bm.height * s
    val l = (pw - dw) / 2f; val t = if (fixed) (ph - dh) / 2f else o.margin.toFloat()
    c.drawBitmap(bm, null, android.graphics.RectF(l, t, l + dw, t + dh), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    if (o.numbers) c.drawText("$pageNo", pw / 2f, ph - 14f, android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = 0xFF555555.toInt(); textAlign = android.graphics.Paint.Align.CENTER })
    finishPage(page)
}

private class OtTile(val key: String, val en: String, val tr: String, val subEn: String, val subTr: String, val go: () -> Unit)

/** Office tools: scan a document, merge, split, pictures to PDF, with the settings they share. Reached from the Office page. */
@Composable
fun OfficeToolsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var sub by remember { mutableIntStateOf(0) }   // 0 hub, 1 scan, 2 PDF tools
    BackHandler { if (sub != 0) sub = 0 else onBack() }
    if (sub == 1) { DocScanPage { sub = 0 }; return }
    if (sub == 2) { PdfToolsPage { sub = 0 }; return }
    var page by remember { mutableIntStateOf(OtPrefs.pageSize(ctx)) }
    var quality by remember { mutableIntStateOf(OtPrefs.quality(ctx)) }
    var margin by remember { mutableIntStateOf(OtPrefs.margin(ctx)) }
    var filter by remember { mutableIntStateOf(OtPrefs.filter(ctx)) }
    var crop by remember { mutableStateOf(OtPrefs.autoCrop(ctx)) }
    var trim by remember { mutableIntStateOf(OtPrefs.trim(ctx)) }
    var open by remember { mutableStateOf(OtPrefs.openAfter(ctx)) }
    var numbers by remember { mutableStateOf(OtPrefs.numbers(ctx)) }
    var prefix by remember { mutableStateOf(OtPrefs.prefix(ctx)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            PIcon("office", T.accent, 26.dp)
            Text(tr("Office tools", "Office araçları"), Modifier.padding(start = 8.dp), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        val tiles = listOf(
            OtTile("photos", "Scan document", "Belge tara", "Camera or gallery, cut out and flattened", "Kamera veya galeri, kesilir ve düzleştirilir") { sub = 1 },
            OtTile("convert", "PDF tools", "PDF araçları", "Merge, split pages, pictures to PDF", "Birleştir, sayfa böl, resimleri PDF yap") { sub = 2 },
        )
        tiles.forEach { t ->
            PCard(Modifier.fillMaxWidth().clickable { t.go() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(t.key, T.accent, 30.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(tr(t.en, t.tr), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr(t.subEn, t.subTr), color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) { PIcon("tune", T.accent, 22.dp); Text(tr("PDF pages", "PDF sayfaları"), Modifier.padding(start = 8.dp), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Text(tr("Page size", "Sayfa boyutu"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("As the picture", "Resim gibi"), "A4", "Letter").forEachIndexed { i, n -> PChip(page == i, n) { page = i; OtPrefs.setInt(ctx, "page", i) } }
            }
            Text(tr("Margin", "Kenar boşluğu"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("None", "Yok"), tr("Narrow", "Dar"), tr("Wide", "Geniş")).forEachIndexed { i, n -> PChip(margin == i, n) { margin = i; OtPrefs.setInt(ctx, "margin", i) } }
            }
            Text(tr("Sharpness of redrawn pages", "Yeniden çizilen sayfaların netliği"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(tr("Small file", "Küçük dosya"), tr("Normal", "Normal"), tr("Sharp", "Net")).forEachIndexed { i, n -> PChip(quality == i, n) { quality = i; OtPrefs.setInt(ctx, "quality", i) } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Page numbers", "Sayfa numaraları"), Modifier.weight(1f), color = T.text, fontSize = 14.sp); PSwitch(numbers, { numbers = it; OtPrefs.setBool(ctx, "numbers", it) })
            }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) { PIcon("photos", T.accent, 22.dp); Text(tr("Scanning", "Tarama"), Modifier.padding(start = 8.dp), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            Text(tr("Starting filter", "Başlangıç filtresi"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DocFilter.entries.forEachIndexed { i, f -> PChip(filter == i, tr(f.en, f.tr)) { filter = i; OtPrefs.setInt(ctx, "filter", i) } }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Cut out and flatten the paper", "Kağıdı kes ve düzleştir"), Modifier.weight(1f), color = T.text, fontSize = 14.sp); PSwitch(crop, { crop = it; OtPrefs.setBool(ctx, "crop", it) })
            }
            Text(tr("Trim from every edge: $trim%", "Her kenardan kırp: %$trim"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            PSlider(trim / 8f, { trim = (it * 8).toInt().coerceIn(0, 8) }, 0f..1f, onDone = { OtPrefs.setInt(ctx, "trim", trim) })
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Open the file after saving", "Kaydedince dosyayı aç"), Modifier.weight(1f), color = T.text, fontSize = 14.sp); PSwitch(open, { open = it; OtPrefs.setBool(ctx, "open", it) })
            }
            PField(prefix, { prefix = it; OtPrefs.setStr(ctx, "prefix", it) }, tr("File name start (e.g. Invoice)", "Dosya adı başı (örn. Fatura)"), Modifier.fillMaxWidth().padding(top = 6.dp))
        }
        Box(Modifier.height(8.dp))
    }
}
