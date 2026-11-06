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

internal fun receiptBitmap(r: Recap, label: String, days: Int): Bitmap {
    val w = 900; val pad = 56f; val tooth = 22f
    val cols = 36
    val ink = 0xFF1B1B1B.toInt(); val soft = 0xFF6B6B6B.toInt()
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = ink }
    // every line with its size, weight and colour; measured first so the paper is exactly as tall as the list
    class Ln(val text: String, val size: Float = 31f, val bold: Boolean = false, val center: Boolean = false, val color: Int = ink, val gap: Float = 0f)
    val lines = ArrayList<Ln>()
    val now = Date()
    val order = ((r.minutes * 31 + r.plays * 17 + r.distinctSongs * 7) % 90000 + 10000).toString()
    val dash = "-".repeat(cols)
    lines += Ln("PULSE", 64f, true, true)
    lines += Ln(tr("MUSIC RECEIPT", "MÜZİK FİŞİ"), 34f, true, true, gap = 4f)
    lines += Ln(label.uppercase(), 28f, false, true, soft, gap = 2f)
    lines += Ln(dash, gap = 14f)
    lines += Ln(row(tr("ORDER", "FİŞ NO") + " #$order", "", cols), 28f, color = soft)
    lines += Ln(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(now).uppercase(), 28f, color = soft)
    lines += Ln(dash, gap = 8f)
    lines += Ln(row(tr("QTY  ITEM", "ADET  ŞARKI"), "", cols), 28f, true)
    lines += Ln(dash)
    if (r.songs10.isEmpty()) lines += Ln(tr("NOTHING BOUGHT YET", "HENÜZ ALIŞVERİŞ YOK"), 30f, false, true, gap = 10f)
    r.songs10.forEach { (title, artist, plays) ->
        lines += Ln("%02d".format(plays.coerceAtMost(99)) + "   " + clip(title.uppercase(), cols - 5), gap = 9f)
        if (artist.isNotBlank()) lines += Ln("     " + clip(artist.uppercase(), cols - 5), 25f, color = soft)
    }
    lines += Ln(dash, gap = 12f)
    if (r.artists10.isNotEmpty()) {
        lines += Ln(tr("ARTISTS", "SANATÇILAR"), 28f, true, gap = 4f)
        r.artists10.take(5).forEach { (a, ms) -> lines += Ln(row(clip(a.uppercase(), cols - 9), "${ms / 60_000L} " + tr("MIN", "DK"), cols), 28f, gap = 6f) }
        lines += Ln(dash, gap = 10f)
    }
    lines += Ln(row(tr("ITEMS", "ÇEŞİT"), "${r.distinctSongs}", cols), gap = 6f)
    lines += Ln(row(tr("ARTISTS", "SANATÇI"), "${r.distinctArtists}", cols), gap = 6f)
    lines += Ln(row(tr("PLAYS", "DİNLEME"), "${r.plays}", cols), gap = 6f)
    lines += Ln(row(tr("DAYS", "GÜN"), "${r.activeDays}/$days", cols), gap = 6f)
    if (r.minutes > 0) lines += Ln(row(tr("BUSIEST HOUR", "EN YOĞUN SAAT"), "%02d:00".format(r.busiestHour), cols), gap = 6f)
    lines += Ln(dash, gap = 8f)
    lines += Ln(row(tr("TOTAL", "TOPLAM"), "${r.minutes} " + tr("MIN", "DK"), 22), 44f, true, gap = 10f)
    if (r.minutes >= 60) lines += Ln(row(tr("HOURS", "SAAT"), "%.1f".format(r.minutes / 60f), cols), 28f, color = soft, gap = 2f)
    lines += Ln(row(tr("PAID WITH", "ÖDEME"), tr("EARS", "KULAK"), cols), 28f, color = soft, gap = 2f)
    lines += Ln(dash, gap = 14f)
    lines += Ln(tr("THANK YOU FOR LISTENING", "DİNLEDİĞİN İÇİN TEŞEKKÜRLER"), 28f, true, true, gap = 8f)
    // heights
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
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF8F5EC.toInt() }
    cv.drawPath(paper, fill)
    var y = tooth + 70f
    lines.forEach { l ->
        p.textSize = l.size; p.color = l.color; p.typeface = Typeface.create(Typeface.MONOSPACE, if (l.bold) Typeface.BOLD else Typeface.NORMAL)
        val tw = p.measureText(l.text)
        val x = if (l.center) (w - tw) / 2f else pad
        cv.drawText(l.text, x, y, p)
        y += l.size * 1.28f + l.gap
    }
    // Code 39: the narrow bar is 3 px, the wide one 8 px; start and stop are the asterisk
    val code = "PULSE-${r.minutes}-${r.plays}".uppercase().filter { it in CODE39 }
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
    val ct = "*$code*"
    cv.drawText(ct, (w - p.measureText(ct)) / 2f, by + barH + 36f, p)
    return bmp
}
