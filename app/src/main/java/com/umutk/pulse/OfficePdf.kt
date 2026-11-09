package com.umutk.pulse

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Two-finger pinch: [onZoom] gets the zoom factor of each step; one-finger scrolling is left alone. */
fun Modifier.ofPinch(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            if (e.changes.size > 1) {
                val z = e.calculateZoom()
                if (z != 1f) onZoom(z)
                e.changes.forEach { it.consume() }
            }
        } while (e.changes.any { it.pressed })
    }
}

/** A text selection: from word [a] to word [b] of page [page] (the same page). */
class PSel(val page: Int, val a: Int, val b: Int)

/** The word under a point (page fractions), or -1. */
private fun wordAt(words: List<PWord>, fx: Float, fy: Float): Int =
    words.indexOfFirst { fx >= it.x - .004f && fx <= it.x + it.w + .004f && fy >= it.y - .004f && fy <= it.y + it.h + .004f }

/** A search hit: the words [a]..[b] of page [page]. */
class PHit(val page: Int, val a: Int, val b: Int)

/** One word of a page's text layer: its text and its box as fractions of the page (top-left origin). */
class PWord(val text: String, val x: Float, val y: Float, val w: Float, val h: Float)

class OfPdf(private val file: File) {
    private val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val r = PdfRenderer(pfd)
    val count = r.pageCount
    /** one page at a time: PdfRenderer allows a single open page */
    fun render(i: Int, w: Int): Bitmap? = synchronized(r) {
        if (i !in 0 until count) return null
        val pg = r.openPage(i)
        try {
            val h = (w * pg.height.toFloat() / pg.width).toInt().coerceIn(1, 6000)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            b.eraseColor(android.graphics.Color.WHITE)
            pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            b
        } finally { pg.close() }
    }
    fun close() { runCatching { synchronized(r) { r.close() } }; runCatching { pfd.close() }; runCatching { textDoc?.close() } }

    // the text layer (for selecting and copying): read once per page, by PDFBox
    private var textDoc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
    // the words of the last pages read (a search over a huge document reads page after page: the cache is bounded)
    private val wordsCache = object : LinkedHashMap<Int, List<PWord>>(64, .75f, true) { override fun removeEldestEntry(e: MutableMap.MutableEntry<Int, List<PWord>>?) = size > 80 }
    /**
     * The PDF with the marks written into it as PDF drawing (no picture of the pages): the pages, their text and their pictures stay as they were, text taken from a page
     * is covered with a patch in the colour of the paper and the new text is written over it in a font that has Turkish letters. Returns false when it cannot be done
     * (rotated pages, no usable font, a protected file): the caller then saves the page pictures instead.
     */
    fun saveVector(out: File, anns: List<PAnn>, appCtx: android.content.Context): Boolean = runCatching {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(appCtx)
        val pd = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
        try {
            val fontFile = listOf("NotoSans-Regular.ttf", "Roboto-Regular.ttf", "DroidSans.ttf", "NotoSerif-Regular.ttf").map { File("/system/fonts/$it") }.firstOrNull { it.isFile }
            val needFont = anns.any { it.kind >= 2 && it.text.isNotBlank() }
            val font = if (needFont) fontFile?.let { f -> f.inputStream().use { com.tom_roush.pdfbox.pdmodel.font.PDType0Font.load(pd, it) } } ?: return@runCatching false else null
            for (i in 0 until pd.numberOfPages) {
                val here = anns.filter { it.page == i }; if (here.isEmpty()) continue
                val pg = pd.getPage(i)
                if (pg.rotation != 0) return@runCatching false
                val box = pg.cropBox ?: pg.mediaBox; val W = box.width; val H = box.height
                val cs = com.tom_roush.pdfbox.pdmodel.PDPageContentStream(pd, pg, com.tom_roush.pdfbox.pdmodel.PDPageContentStream.AppendMode.APPEND, true, true)
                try {
                    fun rgb(c: Int) = floatArrayOf((c shr 16 and 255) / 255f, (c shr 8 and 255) / 255f, (c and 255) / 255f)
                    for (a in here) {
                        val col = rgb(a.color)
                        if (a.kind >= 2) {
                            val lines = a.text.split('\n'); val n = lines.size
                            var size = W * .03f; var left: Float; var base: Float
                            if (a.kind == 3 && a.pts.size >= 2) {
                                val x0 = minOf(a.pts[0].x, a.pts[1].x) * W; val x1 = maxOf(a.pts[0].x, a.pts[1].x) * W
                                val yTop = minOf(a.pts[0].y, a.pts[1].y) * H; val yBot = maxOf(a.pts[0].y, a.pts[1].y) * H
                                if (a.bg != 0) size = (yBot - yTop) * .84f / n
                                val bgc = if (a.bg != 0) rgb(a.bg) else floatArrayOf(1f, 1f, 1f)
                                cs.setNonStrokingColor(bgc[0], bgc[1], bgc[2]); cs.addRect(box.lowerLeftX + x0, box.lowerLeftY + H - yBot, x1 - x0, yBot - yTop); cs.fill()
                                left = x0 + size * .15f; base = if (a.bg != 0) yTop + (yBot - yTop) * .8f / n else yTop + size
                            } else {
                                left = a.pts[0].x * W; base = a.pts[0].y * H
                                if (a.kind == 3) { // a patch from a single tap (no dragged box): behind the text, as drawn on the screen
                                    val tw = lines.maxOf { font!!.getStringWidth(it) / 1000f * size }
                                    cs.setNonStrokingColor(1f, 1f, 1f); cs.addRect(box.lowerLeftX + left - size * .15f, box.lowerLeftY + H - (base + size * .3f), tw + size * .3f, size * 1.3f + (n - 1) * size * 1.2f); cs.fill()
                                }
                            }
                            cs.setNonStrokingColor(col[0], col[1], col[2])
                            lines.forEachIndexed { k, l ->
                                if (l.isEmpty()) return@forEachIndexed
                                val safe = l.filter { ch -> runCatching { font!!.encode(ch.toString()); true }.getOrDefault(false) }
                                cs.beginText(); cs.setFont(font!!, size); cs.newLineAtOffset(box.lowerLeftX + left, box.lowerLeftY + H - (base + k * size * 1.2f)); cs.showText(safe); cs.endText()
                            }
                        } else if (a.pts.isNotEmpty()) {
                            val gs = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState()
                            if (a.kind == 1) { gs.strokingAlphaConstant = 90f / 255f; cs.setGraphicsStateParameters(gs) }
                            cs.setStrokingColor(col[0], col[1], col[2]); cs.setLineWidth(W * (if (a.kind == 1) .035f else .004f)); cs.setLineCapStyle(1); cs.setLineJoinStyle(1)
                            fun px(o: androidx.compose.ui.geometry.Offset) = floatArrayOf(box.lowerLeftX + o.x * W, box.lowerLeftY + H - o.y * H)
                            val f0 = px(a.pts[0]); cs.moveTo(f0[0], f0[1])
                            if (a.pts.size == 1) cs.lineTo(f0[0] + 1f, f0[1]) else for (k in 1 until a.pts.size) { val q = px(a.pts[k]); cs.lineTo(q[0], q[1]) }
                            cs.stroke()
                            if (a.kind == 1) { val g0 = com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState(); g0.strokingAlphaConstant = 1f; cs.setGraphicsStateParameters(g0) }
                        }
                    }
                } finally { cs.close() }
            }
            pd.save(out); true
        } finally { pd.close() }
    }.getOrDefault(false)

    fun words(i: Int, appCtx: android.content.Context): List<PWord> = synchronized(wordsCache) {
        wordsCache[i] ?: (runCatching { extractWords(i, appCtx) }.getOrDefault(emptyList())).also { wordsCache[i] = it }
    }
    private fun extractWords(i: Int, appCtx: android.content.Context): List<PWord> {
        if (i !in 0 until count) return emptyList()
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(appCtx)
        val doc = textDoc ?: com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).also { textDoc = it }
        val box = doc.getPage(i).mediaBox
        val pw = box.width; val ph = box.height
        val stripper = object : com.tom_roush.pdfbox.text.PDFTextStripper() {
            val chars = ArrayList<com.tom_roush.pdfbox.text.TextPosition>()
            override fun writeString(text: String?, textPositions: MutableList<com.tom_roush.pdfbox.text.TextPosition>?) { textPositions?.let { chars.addAll(it) } }
        }
        stripper.startPage = i + 1; stripper.endPage = i + 1
        stripper.getText(doc)
        val out = ArrayList<PWord>()
        val sb = StringBuilder(); var x0 = 0f; var y0 = 0f; var x1 = 0f; var y1 = 0f
        fun flush() {
            if (sb.isNotEmpty()) out.add(PWord(sb.toString(), x0 / pw, y0 / ph, (x1 - x0) / pw, (y1 - y0) / ph))
            sb.clear()
        }
        for (tp in stripper.chars) {
            val ch = tp.unicode ?: continue
            if (ch.isBlank()) { flush(); continue }
            val l = tp.xDirAdj; val b = tp.yDirAdj; val t = b - tp.heightDir; val r = l + tp.widthDirAdj
            if (sb.isEmpty()) { x0 = l; y0 = t; x1 = r; y1 = b }
            else { x0 = minOf(x0, l); y0 = minOf(y0, t); x1 = maxOf(x1, r); y1 = maxOf(y1, b) }
            sb.append(ch)
        }
        flush()
        return out
    }
    private val darkCache = android.util.LruCache<String, Bitmap>(6)
    /** Display-only dark copy of a page (never used for saving); cached per page and width. */
    fun renderDark(i: Int, w: Int): Bitmap? {
        val key = "$i:$w"; darkCache.get(key)?.let { return it }
        val b = render(i, w) ?: return null
        smartInvert(b); darkCache.put(key, b); return b
    }
}

/** The new lightness of a pixel of lightness l in the dark copy: ink turns light; paper and light greys go to black (a steep curve, so #E0 is nearly #000). */
private val PAPER_LUT = IntArray(256) { l ->
    if (l < 128) 242 - l * 38 / 128
    else (255.0 * Math.pow((255 - l) / 127.0, 2.4)).toInt().coerceIn(0, 255)
}

/**
 * Smart invert in place: document blocks (16px) get a per-pixel lightness inversion (white paper -> black, any text darker than 128 -> >= #CC, pure black text -> >= #E6, hue kept);
 * photos stay as they are, with a 2px feathered edge. A block is a photo only inside a LARGE connected region (>= 6 blocks, >= 3 wide/tall, >= 4% of the page) of colourful
 * (> 25% saturated pixels) or mid-tone-heavy blocks; blocks with > 40% near-white pixels are text/paper and never count as mid-tone.
 * shortcut: block grid regions (4-connected), so a photo's bounding blocks are approximate; upgrade to pixel masks if needed.
 */
fun smartInvert(b: Bitmap) {
    val w = b.width; val h = b.height; val px = IntArray(w * h); b.getPixels(px, 0, w, 0, 0, w, h)
    for (i in px.indices) { val c = px[i]; val a = c ushr 24; if (a < 255) { // transparent PDF background counts as white paper
        val r = ((c shr 16 and 255) * a + 255 * (255 - a)) / 255; val g = ((c shr 8 and 255) * a + 255 * (255 - a)) / 255; val bl = ((c and 255) * a + 255 * (255 - a)) / 255
        px[i] = (255 shl 24) or (r shl 16) or (g shl 8) or bl } }
    val bs = 16; val bw = (w + bs - 1) / bs; val bh = (h + bs - 1) / bs
    val cand = BooleanArray(bw * bh)
    for (by in 0 until bh) for (bx in 0 until bw) {
        var mid = 0; var sat = 0; var white = 0; var n = 0
        for (y in by * bs until minOf(h, by * bs + bs) step 2) for (x in bx * bs until minOf(w, bx * bs + bs) step 2) {
            val c = px[y * w + x]; val r = c shr 16 and 255; val g = c shr 8 and 255; val bl = c and 255
            val l = (r * 77 + g * 151 + bl * 28) shr 8
            if (l in 50..205) mid++
            if (l > 200) white++
            if (maxOf(r, g, bl) - minOf(r, g, bl) > 60) sat++
            n++
        }
        cand[by * bw + bx] = n > 0 && white * 10 <= n * 4 && (sat * 4 > n || mid * 2 > n)
    }
    val img = BooleanArray(bw * bh); val seen = BooleanArray(bw * bh); val stack = IntArray(bw * bh)
    for (s0 in 0 until bw * bh) if (cand[s0] && !seen[s0]) {
        var sp = 0; stack[sp++] = s0; seen[s0] = true
        var cnt = 0; var x0 = bw; var x1 = 0; var y0 = bh; var y1 = 0; val start = sp
        val members = ArrayList<Int>()
        while (sp > 0) {
            val k = stack[--sp]; members.add(k); cnt++
            val x = k % bw; val y = k / bw
            x0 = minOf(x0, x); x1 = maxOf(x1, x); y0 = minOf(y0, y); y1 = maxOf(y1, y)
            if (x > 0 && cand[k - 1] && !seen[k - 1]) { seen[k - 1] = true; stack[sp++] = k - 1 }
            if (x < bw - 1 && cand[k + 1] && !seen[k + 1]) { seen[k + 1] = true; stack[sp++] = k + 1 }
            if (y > 0 && cand[k - bw] && !seen[k - bw]) { seen[k - bw] = true; stack[sp++] = k - bw }
            if (y < bh - 1 && cand[k + bw] && !seen[k + bw]) { seen[k + bw] = true; stack[sp++] = k + bw }
        }
        if (cnt >= 6 && minOf(x1 - x0 + 1, y1 - y0 + 1) >= 3 && cnt * 25 >= bw * bh) {
            // pictures are rectangles: a region that fills most of its box is taken whole, so light parts of a photo are not turned dark in squares
            if (cnt * 100 >= (x1 - x0 + 1) * (y1 - y0 + 1) * 45) { for (yy in y0..y1) for (xx in x0..x1) img[yy * bw + xx] = true }
            else for (k in members) img[k] = true
        }
    }
    val src = img.copyOf() // close one-block holes inside photos
    for (by in 1 until bh - 1) for (bx in 1 until bw - 1) if (!src[by * bw + bx]) {
        val k = by * bw + bx
        if ((if (src[k - 1]) 1 else 0) + (if (src[k + 1]) 1 else 0) + (if (src[k - bw]) 1 else 0) + (if (src[k + bw]) 1 else 0) >= 3) img[k] = true
    }
    for (y in 0 until h) { val by = y / bs; val ly = y % bs
        for (x in 0 until w) {
            val bx = x / bs; val isImg = img[by * bw + bx]
            var keep = 0 // 0..3: how much of the original shows (photo edge feather)
            if (isImg) {
                val lx = x % bs
                var dist = 99
                if (bx > 0 && !img[by * bw + bx - 1]) dist = minOf(dist, lx)
                if (bx < bw - 1 && !img[by * bw + bx + 1]) dist = minOf(dist, bs - 1 - lx)
                if (by > 0 && !img[(by - 1) * bw + bx]) dist = minOf(dist, ly)
                if (by < bh - 1 && !img[(by + 1) * bw + bx]) dist = minOf(dist, bs - 1 - ly)
                if (dist >= 2) continue
                keep = dist + 1 // 1 or 2 of 3
            }
            val c = px[y * w + x]; val r = c shr 16 and 255; val g = c shr 8 and 255; val bl = c and 255
            val l = (r * 77 + g * 151 + bl * 28) shr 8
            val l2 = PAPER_LUT[l.coerceIn(0, 255)] // dark ink -> light, paper (and every light grey) -> black
            val d = l2 - l
            var nr = (r + d).coerceIn(0, 255); var ng = (g + d).coerceIn(0, 255); var nb = (bl + d).coerceIn(0, 255)
            if (keep > 0) { nr = (nr * (3 - keep) + r * keep) / 3; ng = (ng * (3 - keep) + g * keep) / 3; nb = (nb * (3 - keep) + bl * keep) / 3 }
            px[y * w + x] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
    }
    b.setPixels(px, 0, w, 0, 0, w, h)
}

/** The paper colour around a box (taken just outside its left edge) and the ink colour inside it (the pixel farthest from the paper): for text taken from the page. */
private fun pageColors(b: Bitmap, x0: Float, y0: Float, x1: Float, y1: Float): Pair<Int, Int> {
    val w = b.width; val h = b.height
    val l = (x0 * w).toInt().coerceIn(0, w - 1); val r = (x1 * w).toInt().coerceIn(l + 1, w)
    val t = (y0 * h).toInt().coerceIn(0, h - 1); val bt = (y1 * h).toInt().coerceIn(t + 1, h)
    val px = (l - 3).coerceAtLeast(0)
    val bg = b.getPixel(px, ((t + bt) / 2).coerceIn(0, h - 1)) or 0xFF000000.toInt()
    var best = 0; var ink = if (android.graphics.Color.luminance(bg) > .5f) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    for (y in t until bt step 2) for (x in l until r step 2) {
        val c = b.getPixel(x, y); val d = Math.abs((c shr 16 and 255) - (bg shr 16 and 255)) + Math.abs((c shr 8 and 255) - (bg shr 8 and 255)) + Math.abs((c and 255) - (bg and 255))
        if (d > best) { best = d; ink = c or 0xFF000000.toInt() }
    }
    return bg to ink
}

/** One mark on a page; points are 0..1 of the page width / height. kind 0 pen, 1 highlighter, 2 text, 3 text on a white patch (edit text). */
class PAnn(val page: Int, val kind: Int, val pts: List<Offset>, val color: Int, val text: String = "", val bg: Int = 0)

/** Box of a text mark in pixels (kind 3 paints it white first, hiding the original text under it). */
private fun annBounds(a: PAnn, w: Float, h: Float, pt: android.graphics.Paint): android.graphics.RectF {
    pt.textSize = w * .03f
    if (a.pts.size >= 2) { // a dragged cover box: the two corners, in any order
        val (p, q) = a.pts[0] to a.pts[1]
        return android.graphics.RectF(minOf(p.x, q.x) * w, minOf(p.y, q.y) * h, maxOf(p.x, q.x) * w, maxOf(p.y, q.y) * h)
    }
    val p0 = a.pts.first(); val ls = a.text.split('\n'); val pad = pt.textSize * .15f
    return android.graphics.RectF(p0.x * w - pad, p0.y * h - pt.textSize, p0.x * w + (ls.maxOf { pt.measureText(it) }) + pad, p0.y * h + (ls.size - 1) * pt.textSize * 1.2f + pt.textSize * .3f)
}

/** A colour as the dark copy of the page shows it (same lightness curve as the smart invert). */
private fun darkOf(c: Int): Int {
    val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
    val l = (r * 77 + g * 151 + b * 28) shr 8; val d = PAPER_LUT[l.coerceIn(0, 255)] - l
    return (0xFF shl 24) or ((r + d).coerceIn(0, 255) shl 16) or ((g + d).coerceIn(0, 255) shl 8) or (b + d).coerceIn(0, 255)
}

/** [dark] is screen-only: black patch and light text for black-ink marks; the saved PDF always uses dark = false. */
private fun drawAnn(cv: android.graphics.Canvas, a: PAnn, w: Float, h: Float, pt: android.graphics.Paint, dark: Boolean = false) {
    pt.reset(); pt.isAntiAlias = true; pt.color = if (dark && a.bg != 0) darkOf(a.color) else if (dark && a.color == 0xFF000000.toInt()) 0xFFEDEDED.toInt() else a.color
    if (a.kind >= 2) {
        pt.style = android.graphics.Paint.Style.FILL; pt.textSize = w * .03f
        val p0 = a.pts.firstOrNull() ?: return
        val box = if (a.kind == 3 && a.pts.size >= 2) annBounds(a, w, h, pt) else null
        val nLines = a.text.split('\n').size
        if (a.bg != 0 && box != null) pt.textSize = box.height() * .84f / nLines // a line taken from the page: the text takes the height of the line it replaces
        if (a.kind == 3) { val r = box ?: annBounds(a, w, h, pt); val c = pt.color; pt.color = if (a.bg != 0) (if (dark) darkOf(a.bg) else a.bg) else if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE; cv.drawRect(r, pt); pt.color = c }
        val ox = box?.let { it.left + pt.textSize * .15f } ?: p0.x * w
        val oy = if (a.bg != 0 && box != null) box.top + box.height() * .8f / nLines else box?.let { it.top + pt.textSize } ?: p0.y * h
        a.text.split('\n').forEachIndexed { k, l -> cv.drawText(l, ox, oy + k * pt.textSize * 1.2f, pt) }
        return
    }
    pt.style = android.graphics.Paint.Style.STROKE; pt.strokeCap = android.graphics.Paint.Cap.ROUND; pt.strokeJoin = android.graphics.Paint.Join.ROUND
    pt.strokeWidth = w * (if (a.kind == 1) .035f else .004f)
    if (a.kind == 1) pt.alpha = 90
    val path = android.graphics.Path()
    a.pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x * w, o.y * h) else path.lineTo(o.x * w, o.y * h) }
    if (a.pts.size == 1) path.lineTo(a.pts[0].x * w + 1f, a.pts[0].y * h)
    cv.drawPath(path, pt)
}

/** PDF window: same chrome as the other apps in the PDF look chosen in Settings. */
@Composable
fun PdfChrome(theme: OfTheme, title: String, acts: List<OAct>, status: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    OfChromeBasic(theme, title, acts, status, onBack, content)

@Composable
fun OfPdfScreen(doc: OfDoc, theme: OfTheme) {
    val opened = remember(doc) { runCatching { OfPdf(doc.file) } }
    DisposableEffect(opened) { onDispose { opened.getOrNull()?.close() } }
    val p = opened.getOrNull()
    if (p == null) {
        PdfChrome(theme, doc.name, listOf(OAct("Close", "File", Icons.Outlined.Close) { doc.close() }), "", doc.close) {
            OfErrorCard(theme, opened.exceptionOrNull()?.message ?: "PDF")
        }
        return
    }
    val list = rememberLazyListState(); val scope = rememberCoroutineScope()
    // the page fits the screen with a little space at both sides, and can be made smaller still
    var zoom by remember { mutableFloatStateOf(.94f) }
    var committed by remember { mutableFloatStateOf(.94f) }
    LaunchedEffect(zoom) { delay(250); committed = zoom }
    val ctx = LocalContext.current
    val anns = remember { mutableStateListOf<PAnn>() }
    var tool by remember { mutableIntStateOf(0) } // 1 pen, 2 highlighter, 3 text
    var ci by remember { mutableIntStateOf(0) }
    val cols = intArrayOf(0xFFD32F2F.toInt(), 0xFFFFEB3B.toInt(), 0xFF1976D2.toInt(), 0xFF388E3C.toInt(), 0xFF000000.toInt())
    var textAt by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }; var editing by remember { mutableStateOf<PAnn?>(null) }; var textVal by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf(false) }; var jumpText by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf<PSel?>(null) }
    // search: every page is read in turn off the main thread, the hits come in as they are found
    var findOn by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }
    val hits = remember { mutableStateListOf<PHit>() }
    var curHit by remember { mutableIntStateOf(-1) }
    var scanned by remember { mutableIntStateOf(0) }
    fun gotoHit(k: Int) {
        if (hits.isEmpty()) return
        curHit = ((k % hits.size) + hits.size) % hits.size
        scope.launch { list.animateScrollToItem(hits[curHit].page.coerceIn(0, p.count - 1)) }
    }
    LaunchedEffect(findOn, query, OfFind.case) {
        hits.clear(); curHit = -1; scanned = 0
        if (!findOn || query.isBlank()) return@LaunchedEffect
        delay(250)
        val case = OfFind.case; val ql = OfFind.length(query)
        for (pg in 0 until p.count) {
            val found = withContext(Dispatchers.Default) {
                val ws = p.words(pg, ctx)
                if (ws.isEmpty()) emptyList() else {
                    // the page as one line of text (a space between the words), so a phrase that runs over several words is found too
                    val offs = IntArray(ws.size); val sb = StringBuilder()
                    ws.forEachIndexed { k, w -> offs[k] = sb.length; sb.append(w.text); sb.append(' ') }
                    OfFind.starts(sb, query, case).map { at ->
                        var a = java.util.Arrays.binarySearch(offs, at); if (a < 0) a = -a - 2
                        var b = java.util.Arrays.binarySearch(offs, at + ql - 1); if (b < 0) b = -b - 2
                        PHit(pg, a.coerceIn(0, ws.lastIndex), b.coerceIn(0, ws.lastIndex))
                    }
                }
            }
            if (found.isNotEmpty()) { hits.addAll(found); if (curHit < 0) gotoHit(0) }
            scanned = pg + 1
            if (hits.size >= 5000) break
            kotlinx.coroutines.yield()
        }
    }
    fun copySel() {
        val s = sel ?: return
        val ws = p.words(s.page, ctx)
        val lo = minOf(s.a, s.b); val hi = maxOf(s.a, s.b).coerceAtMost(ws.lastIndex)
        if (lo > hi) return
        val txt = (lo..hi).joinToString(" ") { ws[it].text }
        (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("", txt))
        Toast.makeText(ctx, tr("Copied", "Kopyalandı"), Toast.LENGTH_SHORT).show()
    }
    val widthDp = LocalConfiguration.current.screenWidthDp.dp
    val pagePx = with(LocalDensity.current) { (widthDp * committed).roundToPx() }.coerceIn(300, 2400)
    fun goto(i: Int) { scope.launch { list.animateScrollToItem(i.coerceIn(0, p.count - 1)) } }
    val here = list.firstVisibleItemIndex
    val acts = listOf(
        OAct(tr("Zoom in", "Yakınlaştır"), "View", Icons.Outlined.ZoomIn, cap = tr("Zoom", "Yakınlaştırma")) { zoom = (zoom * 1.25f).coerceAtMost(4f) },
        OAct(tr("Zoom out", "Uzaklaştır"), "View", Icons.Outlined.ZoomOut, cap = tr("Zoom", "Yakınlaştırma")) { zoom = (zoom / 1.25f).coerceAtLeast(.4f) },
        OAct(tr("Fit width", "Genişliğe sığdır"), "View", Icons.Outlined.FitScreen, cap = tr("Zoom", "Yakınlaştırma"), enabled = zoom != .94f) { zoom = .94f },
        OAct(tr("Go to page", "Sayfaya git"), "View", Icons.Outlined.FormatListNumbered, cap = tr("Pages", "Sayfalar")) { jumpText = ""; jump = true },
        OAct(tr("First", "İlk"), "View", Icons.Outlined.FirstPage, cap = tr("Pages", "Sayfalar"), enabled = here > 0) { goto(0) },
        OAct(tr("Previous", "Önceki"), "View", Icons.Outlined.KeyboardArrowUp, cap = tr("Pages", "Sayfalar"), enabled = here > 0) { goto(here - 1) },
        OAct(tr("Next", "Sonraki"), "View", Icons.Outlined.KeyboardArrowDown, cap = tr("Pages", "Sayfalar"), enabled = here < p.count - 1) { goto(here + 1) },
        OAct(tr("Last", "Son"), "View", Icons.Outlined.LastPage, cap = tr("Pages", "Sayfalar"), enabled = here < p.count - 1) { goto(p.count - 1) },
        OAct(tr("Find", "Bul"), "View", Icons.Outlined.Search, on = findOn, cap = tr("Search", "Ara"), png = "search") { findOn = !findOn },
        OAct(tr("Dark pages", "Koyu sayfalar"), "View", Icons.Outlined.DarkMode, on = OfficePrefs.darkPdf, cap = tr("Display", "Görünüm")) { OfficePrefs.setDarkPdf(ctx, !OfficePrefs.darkPdf) },
        OAct(tr("Pen", "Kalem"), "Edit", Icons.Outlined.Edit, on = tool == 1, cap = tr("Mark up", "İşaretle")) { tool = if (tool == 1) 0 else 1 },
        OAct(tr("Highlight", "Vurgu"), "Edit", Icons.Outlined.BorderColor, on = tool == 2, cap = tr("Mark up", "İşaretle")) { tool = if (tool == 2) 0 else 2; if (tool == 2 && ci > 1) ci = 1 },
        OAct(tr("Add text", "Yazı ekle"), "Edit", Icons.Outlined.TextFields, on = tool == 3, cap = tr("Text", "Yazı")) { tool = if (tool == 3) 0 else 3 },
        OAct(tr("Edit text", "Metni düzenle"), "Edit", Icons.Outlined.EditNote, on = tool == 4, cap = tr("Text", "Yazı")) { tool = if (tool == 4) 0 else 4; if (tool == 4) ci = cols.size - 1 },
        OAct(tr("Colour", "Renk"), "Edit", Icons.Outlined.Palette, cap = tr("Colour", "Renk")) { ci = (ci + 1) % cols.size },
        OAct(tr("Select text", "Metin seç"), "Edit", Icons.Outlined.Create, on = tool == 5, cap = tr("Text", "Yazı")) { tool = if (tool == 5) 0 else 5 },
        OAct(tr("Copy text", "Metni kopyala"), "Edit", Icons.Outlined.ContentCopy, enabled = sel != null, cap = tr("Text", "Yazı")) { copySel() },
        OAct(tr("Undo", "Geri al"), "Edit", Icons.Outlined.Undo, enabled = anns.isNotEmpty(), cap = tr("Edit", "Düzenle")) { anns.removeAt(anns.size - 1) },
        OAct(tr("Clear marks", "İşaretleri sil"), "Edit", Icons.Outlined.Delete, enabled = anns.isNotEmpty(), cap = tr("Edit", "Düzenle")) { anns.clear() },
        OAct(tr("Save a copy", "Kopya kaydet"), "File", Icons.Outlined.ContentCopy, png = "copy") {
            val snap = anns.toList(); val paint = android.graphics.Paint()
            doc.saveCopy({ out ->
                // first as PDF drawing on the real pages (text stays text, nothing is turned into a picture); the page pictures only when that cannot be done
                if (!p.saveVector(out, snap, ctx.applicationContext)) {
                val pd = PdfDocument()
                try {
                    for (i in 0 until p.count) {
                        val bm = p.render(i, 1240) ?: continue
                        val sc = 595f / bm.width
                        val page = pd.startPage(PdfDocument.PageInfo.Builder(595, (bm.height * sc).toInt().coerceAtLeast(1), i + 1).create())
                        page.canvas.scale(sc, sc); page.canvas.drawBitmap(bm, 0f, 0f, null)
                        snap.filter { it.page == i }.forEach { drawAnn(page.canvas, it, bm.width.toFloat(), bm.height.toFloat(), paint) }
                        pd.finishPage(page); bm.recycle()
                    }
                    java.io.FileOutputStream(out).use { pd.writeTo(it) }
                } finally { pd.close() }
                }
            }) { msg -> if (!msg.startsWith("Save failed")) doc.dirty = false; Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
        },
        OAct(tr("Close", "Kapat"), "File", Icons.Outlined.Close) { doc.close() },
    )
    CompositionLocalProvider(LocalOfRibbon provides ofCaptionRibbons(theme, acts)) { PdfChrome(theme, doc.name, acts, "${tr("Page", "Sayfa")} ${list.firstVisibleItemIndex + 1} / ${p.count}   ${(zoom * 100).toInt()}%", doc.close) {
      Column(Modifier.fillMaxSize()) {
        if (findOn) Row(Modifier.fillMaxWidth().background(theme.pal.ribbonBg).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PField(query, { query = it }, tr("Find", "Bul"), Modifier.weight(1f), onDone = { gotoHit(curHit + 1) })
            val busy = scanned < p.count && query.isNotBlank()
            Text(if (hits.isEmpty()) (if (busy) "${scanned}/${p.count}" else "0") else "${curHit + 1}/${hits.size}${if (hits.size >= 5000) "+" else ""}${if (busy) " …" else ""}",
                color = theme.pal.ink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
            Text("Aa", color = if (OfFind.case) theme.pal.accent else theme.pal.ink.copy(alpha = .6f), fontSize = 14.sp, fontWeight = if (OfFind.case) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).clickable { OfFind.case = !OfFind.case }.padding(horizontal = 8.dp, vertical = 6.dp))
            Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.size(32.dp).clickable { gotoHit(if (curHit < 0) -1 else curHit - 1) }.padding(4.dp), theme.pal.ink)
            Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(32.dp).clickable { gotoHit(curHit + 1) }.padding(4.dp), theme.pal.ink)
            Icon(Icons.Outlined.Close, null, Modifier.size(32.dp).clickable { findOn = false }.padding(4.dp), theme.pal.ink)
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(if (OfficePrefs.darkPdf) Color.Black else Color.Transparent).ofPinch { zoom = (zoom * it).coerceIn(.4f, 4f) }) {
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                Box(Modifier.width(maxOf(widthDp, widthDp * zoom)).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.width(widthDp * zoom), state = list, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.count) { i -> PdfPage(p, i, pagePx, anns.filter { it.page == i }, tool, cols[ci],
                        { a -> anns.add(a); doc.dirty = true }, { x, y, hit -> editing = hit; textVal = hit?.text ?: ""; textAt = Triple(i, x, y) },
                        sel?.takeIf { it.page == i }, { s -> sel = s },
                        if (findOn) hits.withIndex().filter { it.value.page == i }.map { it.value to (it.index == curHit) } else emptyList()) }
                }
                }
            }
        }
      }
    } }
    textAt?.let { (pg, x, y) ->
        WinAlert(onDismissRequest = { textAt = null }, title = { Text(if (tool == 4 || editing != null) tr("Edit text", "Metni düzenle") else tr("Add text", "Yazı ekle")) },
            text = { PField(textVal, { textVal = it }, "") },
            confirmButton = { TextButton({
                val old = editing; val fresh = old != null && old.pts.size >= 2 && old !in anns; if (old != null) anns.remove(old)
                if (textVal.isNotBlank() || fresh) anns.add(PAnn(pg, if (tool == 4 || old?.kind == 3) 3 else 2, if (old != null) old.pts else listOf(Offset(x, y)), old?.color ?: cols[ci], textVal, old?.bg ?: 0))
                if (textVal.isNotBlank() || old != null) doc.dirty = true; textAt = null; editing = null }) { Text("OK") } },
            dismissButton = { TextButton({ textAt = null; editing = null }) { Text(tr("Cancel", "İptal")) } })
    }
    if (jump) WinAlert(onDismissRequest = { jump = false }, title = { Text(tr("Go to page", "Sayfaya git") + " (1-${p.count})") },
        text = { PField(jumpText, { jumpText = it.filter(Char::isDigit).take(6) }, "1-${p.count}") },
        confirmButton = { TextButton({ jump = false; jumpText.toIntOrNull()?.let { n -> scope.launch { list.scrollToItem((n - 1).coerceIn(0, p.count - 1)) } } }) { Text("OK") } },
        dismissButton = { TextButton({ jump = false }) { Text(tr("Cancel", "İptal")) } })
}

@Composable
private fun PdfPage(p: OfPdf, i: Int, px: Int, anns: List<PAnn>, tool: Int, color: Int, onAdd: (PAnn) -> Unit, onText: (Float, Float, PAnn?) -> Unit, sel: PSel?, onSel: (PSel?) -> Unit, found: List<Pair<PHit, Boolean>> = emptyList()) {
    val pdfScope = androidx.compose.runtime.rememberCoroutineScope()
    val dark = OfficePrefs.darkPdf
    val bmp by produceState<ImageBitmap?>(null, i, px, dark) {
        if (!dark) { value = withContext(Dispatchers.IO) { runCatching { p.render(i, px)?.asImageBitmap() }.getOrNull() }; return@produceState }
        if (value == null) value = withContext(Dispatchers.IO) { runCatching { p.render(i, px)?.asImageBitmap() }.getOrNull() } // original first, dark swaps in
        value = withContext(Dispatchers.IO) { runCatching { p.renderDark(i, px)?.asImageBitmap() }.getOrNull() } ?: value
    }
    val bg = if (dark) Color.Black else Color.White
    val b = bmp
    val paint = remember { android.graphics.Paint() }
    var cur by remember { mutableStateOf<PAnn?>(null) }
    val toolNow by rememberUpdatedState(tool); val colNow by rememberUpdatedState(color)
    val appCtx = LocalContext.current.applicationContext
    val words by produceState<List<PWord>>(emptyList(), i) { value = withContext(Dispatchers.IO) { p.words(i, appCtx) } }
    var selStart by remember(i) { mutableStateOf(-1) }
    Box(Modifier.fillMaxWidth()) {
        if (b != null) Image(b, null, Modifier.fillMaxWidth().background(bg), contentScale = ContentScale.FillWidth)
        else Box(Modifier.fillMaxWidth().aspectRatio(.707f).background(bg), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.Gray) }
        if (anns.isNotEmpty() || tool != 0 || cur != null || found.isNotEmpty() || sel != null) Canvas(Modifier.matchParentSize()
            .pointerInput(tool, anns, words) {
                if (tool == 3) detectTapGestures { o ->
                    val hit = anns.lastOrNull { it.kind >= 2 && annBounds(it, size.width.toFloat(), size.height.toFloat(), paint).contains(o.x, o.y) }
                    onText(o.x / size.width, o.y / size.height, hit) }
                else if (tool == 4) detectDragGestures(
                    onDragStart = { o -> cur = PAnn(i, 3, listOf(Offset(o.x / size.width, o.y / size.height), Offset(o.x / size.width, o.y / size.height)), colNow) },
                    onDrag = { ch, _ -> cur?.let { c -> cur = PAnn(c.page, 3, listOf(c.pts[0], Offset(ch.position.x / size.width, ch.position.y / size.height)), c.color) } },
                    onDragEnd = {
                        val c = cur; cur = null
                        if (c != null) {
                            val (a, b) = c.pts[0] to c.pts[1]
                            val big = Math.abs(b.x - a.x) * size.width > 12 && Math.abs(b.y - a.y) * size.height > 8
                            if (big) onText(minOf(a.x, b.x), minOf(a.y, b.y), c)
                            else if (wordAt(words, a.x, a.y).let { it >= 0 }) {
                                // a tap on printed text: the whole line becomes editable, covered with the colour of the page and written in its ink
                                val k = wordAt(words, a.x, a.y); val w0 = words[k]
                                val cy = w0.y + w0.h / 2
                                val row = words.filter { Math.abs(it.y + it.h / 2 - cy) < w0.h * .5f }.sortedBy { it.x }
                                var lo = row.indexOf(w0); var hi = lo
                                while (lo > 0 && row[lo].x - (row[lo - 1].x + row[lo - 1].w) < w0.h * 1.6f) lo--
                                while (hi < row.size - 1 && row[hi + 1].x - (row[hi].x + row[hi].w) < w0.h * 1.6f) hi++
                                val seg = row.subList(lo, hi + 1)
                                val x0 = seg.minOf { it.x } - .004f; val x1 = seg.maxOf { it.x + it.w } + .004f
                                val y0 = seg.minOf { it.y }; val y1 = seg.maxOf { it.y + it.h }
                                val line = seg.joinToString(" ") { it.text }
                                pdfScope.launch {
                                    val (bgc, ink) = withContext(Dispatchers.IO) { runCatching { p.render(i, px) }.getOrNull()?.let { pageColors(it, x0, y0, x1, y1) } } ?: (0xFFFFFFFF.toInt() to 0xFF000000.toInt())
                                    onText(x0, y0, PAnn(i, 3, listOf(Offset(x0, y0), Offset(x1, y1)), ink, line, bgc))
                                }
                            }
                            else { val hit = anns.lastOrNull { it.kind >= 2 && annBounds(it, size.width.toFloat(), size.height.toFloat(), paint).contains(a.x * size.width, a.y * size.height) }
                                onText(a.x, a.y, hit) }
                        }
                    }, onDragCancel = { cur = null })
                else if (tool == 5) detectDragGestures(
                    onDragStart = { o -> val k = wordAt(words, o.x / size.width, o.y / size.height); selStart = k; onSel(if (k >= 0) PSel(i, k, k) else null) },
                    onDrag = { ch, _ -> val k = wordAt(words, ch.position.x / size.width, ch.position.y / size.height); if (k >= 0 && selStart >= 0) onSel(PSel(i, selStart, k)) },
                    onDragEnd = { }, onDragCancel = { })
                else if (tool != 0) detectDragGestures(
                    onDragStart = { o -> cur = PAnn(i, toolNow - 1, listOf(Offset(o.x / size.width, o.y / size.height)), colNow) },
                    onDragEnd = { cur?.let(onAdd); cur = null }, onDragCancel = { cur = null },
                    onDrag = { ch, _ -> cur?.let { c -> cur = PAnn(c.page, c.kind, c.pts + Offset(ch.position.x / size.width, ch.position.y / size.height), c.color) } })
            }) {
            drawIntoCanvas { cv -> (anns + listOfNotNull(cur)).forEach { drawAnn(cv.nativeCanvas, it, size.width, size.height, paint, dark) } }
            // the words found by the search: yellow, the one in view orange
            for ((h, now) in found) for (k in h.a..h.b) if (k in words.indices) {
                val w = words[k]
                drawRect(if (now) Color(0xAAFF9800) else Color(0x88FFEB3B), Offset(w.x * size.width, w.y * size.height), Size(w.w * size.width, w.h * size.height))
            }
            // the selected words, shaded
            sel?.takeIf { it.page == i }?.let { s ->
                for (k in minOf(s.a, s.b)..maxOf(s.a, s.b)) if (k in words.indices) {
                    val w = words[k]
                    drawRect(Color(0x663D8BFD), Offset(w.x * size.width, w.y * size.height), Size(w.w * size.width, w.h * size.height))
                }
            }
        }
    }
}
