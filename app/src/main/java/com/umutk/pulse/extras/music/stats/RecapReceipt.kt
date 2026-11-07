package com.umutk.pulse.extras.music.stats

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import com.umutk.pulse.tr
import java.text.DateFormat
import java.util.Date

/**
 * The recap as a shop receipt: cream paper with torn edges, a monospace list of the best songs as items, the minutes as the total and a real Code 39 barcode at the foot.
 * One picture is drawn for the page and for the saved PNG, so what you see is what you save.
 */
private val CODE39 = mapOf(
    '0' to "nnnwwnwnn", '1' to "wnnwnnnnw", '2' to "nnwwnnnnw", '3' to "wnwwnnnnn", '4' to "nnnwwnnnw", '5' to "wnnwwnnnn", '6' to "nnwwwnnnn", '7' to "nnnwnnwnw",
    '8' to "wnnwnnwnn", '9' to "nnwwnnwnn", 'A' to "wnnnnwnnw", 'B' to "nnwnnwnnw", 'C' to "wnwnnwnnn", 'D' to "nnnnwwnnw", 'E' to "wnnnwwnnn", 'F' to "nnwnwwnnn",
    'G' to "nnnnnwwnw", 'H' to "wnnnnwwnn", 'I' to "nnwnnwwnn", 'J' to "nnnnwwwnn", 'K' to "wnnnnnnww", 'L' to "nnwnnnnww", 'M' to "wnwnnnnwn", 'N' to "nnnnwnnww",
    'O' to "wnnnwnnwn", 'P' to "nnwnwnnwn", 'Q' to "nnnnnnwww", 'R' to "wnnnnnwwn", 'S' to "nnwnnnwwn", 'T' to "nnnnwnwwn", 'U' to "wwnnnnnnw", 'V' to "nwwnnnnnw",
    'W' to "wwwnnnnnn", 'X' to "nwnnwnnnw", 'Y' to "wwnnwnnnn", 'Z' to "nwwnwnnnn", '-' to "nwnnnnwnw", '.' to "wwnnnnwnn", ' ' to "nwwnnnwnn", '*' to "nwnnwnwnn",
)

private fun clip(s: String, n: Int) = if (s.length <= n) s else s.take(n - 1).trimEnd() + "…"

/** A line of [width] characters: [left] at the start, [right] at the end, spaces between. */
private fun row(left: String, right: String, width: Int): String {
    val l = clip(left, (width - right.length - 1).coerceAtLeast(1))
    return l + " ".repeat((width - l.length - right.length).coerceAtLeast(1)) + right
}

/** One item of the receipt: a count at the left, a name, and a small line under it. */
internal class ReceiptItem(val qty: String, val name: String, val sub: String = "")
/** A block of "name ... value" rows under a heading. */
internal class ReceiptSection(val head: String, val rows: List<Pair<String, String>>)
/** Everything a receipt shows; the music recap and the gym recap both fill one. */
internal class ReceiptSpec(
    val label: String, val seed: Long, val qtyHead: String, val items: List<ReceiptItem>, val sections: List<ReceiptSection>, val stats: List<Pair<String, String>>,
    val totalLabel: String, val totalValue: String, val subTotal: List<Pair<String, String>>, val payLabel: String, val payValue: String, val barcode: String,
    val title: String = "PULSE", val kind: String = tr("MUSIC RECEIPT", "MÜZİK FİŞİ"), val empty: String = tr("NOTHING BOUGHT YET", "HENÜZ ALIŞVERİŞ YOK"),
)

internal fun receiptBitmap(r: Recap, label: String, days: Int): Bitmap = renderReceipt(ReceiptSpec(
    label = label, seed = r.minutes * 31 + r.plays * 17 + r.distinctSongs * 7, qtyHead = tr("QTY  ITEM", "ADET  ŞARKI"),
    items = r.songs10.map { (title, artist, plays) -> ReceiptItem("%02d".format(plays.coerceAtMost(99)), title, artist) },
    sections = if (r.artists10.isEmpty()) emptyList() else listOf(ReceiptSection(tr("ARTISTS", "SANATÇILAR"), r.artists10.take(5).map { (a, ms) -> a to "${ms / 60_000L} " + tr("MIN", "DK") })),
    stats = listOf(tr("ITEMS", "ÇEŞİT") to "${r.distinctSongs}", tr("ARTISTS", "SANATÇI") to "${r.distinctArtists}", tr("PLAYS", "DİNLEME") to "${r.plays}", tr("DAYS", "GÜN") to "${r.activeDays}/$days") +
        (if (r.minutes > 0) listOf(tr("BUSIEST HOUR", "EN YOĞUN SAAT") to "%02d:00".format(r.busiestHour)) else emptyList()),
    totalLabel = tr("TOTAL", "TOPLAM"), totalValue = "${r.minutes} " + tr("MIN", "DK"),
    subTotal = if (r.minutes >= 60) listOf(tr("HOURS", "SAAT") to "%.1f".format(r.minutes / 60f)) else emptyList(),
    payLabel = tr("PAID WITH", "ÖDEME"), payValue = tr("EARS", "KULAK"), barcode = "PULSE-${r.minutes}-${r.plays}",
))

/** Draws a [ReceiptSpec] as the receipt picture. */
internal fun renderReceipt(s: ReceiptSpec): Bitmap {
    val w = 900; val pad = 56f; val tooth = 22f
    val cols = 36
    val ink = 0xFF1B1B1B.toInt(); val soft = 0xFF6B6B6B.toInt()
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = ink }
    // every line with its size, weight and colour; measured first so the paper is exactly as tall as the list
    class Ln(val text: String, val size: Float = 31f, val bold: Boolean = false, val center: Boolean = false, val color: Int = ink, val gap: Float = 0f)
    val lines = ArrayList<Ln>()
    val order = ((s.seed % 90000 + 90000) % 90000 + 10000).toString()
    val dash = "-".repeat(cols)
    lines += Ln(s.title, 64f, true, true)
    lines += Ln(s.kind, 34f, true, true, gap = 4f)
    lines += Ln(s.label.uppercase(), 28f, false, true, soft, gap = 2f)
    lines += Ln(dash, gap = 14f)
    lines += Ln(tr("ORDER", "FİŞ NO") + " #$order", 28f, color = soft)
    lines += Ln(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date()).uppercase(), 28f, color = soft)
    lines += Ln(dash, gap = 8f)
    lines += Ln(s.qtyHead, 28f, true)
    lines += Ln(dash)
    if (s.items.isEmpty()) lines += Ln(s.empty, 30f, false, true, gap = 10f)
    s.items.forEach { it ->
        lines += Ln(it.qty.padStart(2, '0').take(4) + "   " + clip(it.name.uppercase(), cols - 7), gap = 9f)
        if (it.sub.isNotBlank()) lines += Ln("     " + clip(it.sub.uppercase(), cols - 5), 25f, color = soft)
    }
    lines += Ln(dash, gap = 12f)
    s.sections.forEach { sec ->
        lines += Ln(sec.head, 28f, true, gap = 4f)
        sec.rows.forEach { (a, v) -> lines += Ln(row(clip(a.uppercase(), cols - v.length - 2), v, cols), 28f, gap = 6f) }
        lines += Ln(dash, gap = 10f)
    }
    s.stats.forEach { (a, v) -> lines += Ln(row(a, v, cols), gap = 6f) }
    lines += Ln(dash, gap = 8f)
    lines += Ln(row(s.totalLabel, s.totalValue, 22), 44f, true, gap = 10f)
    s.subTotal.forEach { (a, v) -> lines += Ln(row(a, v, cols), 28f, color = soft, gap = 2f) }
    lines += Ln(row(s.payLabel, s.payValue, cols), 28f, color = soft, gap = 2f)
    lines += Ln(dash, gap = 14f)
    lines += Ln(tr("THANK YOU FOR COMING", "GELDİĞİN İÇİN TEŞEKKÜRLER"), 28f, true, true, gap = 8f)
    val barH = 150f
    var h = tooth * 2 + 70f
    lines.forEach { l -> p.textSize = l.size; h += l.size * 1.28f + l.gap }
    h += 36f + barH + 54f + 50f
    val bmp = Bitmap.createBitmap(w, h.toInt(), Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    // the paper: torn zigzag along the top and the foot
    val paper = Path().apply {
        moveTo(0f, tooth)
        var x = 0f; var up = false
        while (x < w) { x += tooth; lineTo(minOf(x, w.toFloat()), if (up) tooth else 0f); up = !up }
        lineTo(w.toFloat(), h - tooth)
        x = w.toFloat(); up = false
        while (x > 0) { x -= tooth; lineTo(maxOf(x, 0f), if (up) h - tooth else h); up = !up }
        close()
    }
    cv.drawPath(paper, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8F5EC.toInt() })
    var y = tooth + 70f
    lines.forEach { l ->
        p.textSize = l.size; p.color = l.color; p.typeface = Typeface.create(Typeface.MONOSPACE, if (l.bold) Typeface.BOLD else Typeface.NORMAL)
        val tw = p.measureText(l.text)
        cv.drawText(l.text, if (l.center) (w - tw) / 2f else pad, y, p)
        y += l.size * 1.28f + l.gap
    }
    // Code 39: the narrow bar is 3 px, the wide one 8 px; start and stop are the asterisk
    val code = s.barcode.uppercase().filter { it in CODE39 }
    val full = "*$code*"
    val narrow = 3f; val wide = 8f
    val total = full.sumOf { ch -> CODE39.getValue(ch).sumOf { e -> if (e == 'w') wide.toDouble() else narrow.toDouble() } + narrow }.toFloat()
    val scale = ((w - pad * 2) / total).coerceAtMost(1.6f)
    var bx = (w - total * scale) / 2f
    val by = y + 22f
    val bar = Paint().apply { color = ink; style = Paint.Style.FILL }
    full.forEach { ch ->
        CODE39.getValue(ch).forEachIndexed { i, e ->
            val bw = (if (e == 'w') wide else narrow) * scale
            if (i % 2 == 0) cv.drawRect(bx, by, bx + bw, by + barH, bar)
            bx += bw
        }
        bx += narrow * scale
    }
    p.typeface = Typeface.MONOSPACE; p.textSize = 26f; p.color = soft
    cv.drawText(full, (w - p.measureText(full)) / 2f, by + barH + 36f, p)
    return bmp
}
