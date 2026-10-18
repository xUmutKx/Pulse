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

class OfPdf(file: File) {
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
    fun close() { runCatching { synchronized(r) { r.close() } }; runCatching { pfd.close() } }
    private val darkCache = android.util.LruCache<String, Bitmap>(6)
    /** Display-only dark copy of a page (never used for saving); cached per page and width. */
    fun renderDark(i: Int, w: Int): Bitmap? {
        val key = "$i:$w"; darkCache.get(key)?.let { return it }
        val b = render(i, w) ?: return null
        smartInvert(b); darkCache.put(key, b); return b
    }
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
        if (cnt >= 6 && minOf(x1 - x0 + 1, y1 - y0 + 1) >= 3 && cnt * 25 >= bw * bh) for (k in members) img[k] = true
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
            val l2 = if (l < 128) 242 - l * 38 / 128 else (255 - l) * 204 / 127 // dark ink -> light, paper -> black
            val d = l2 - l
            var nr = (r + d).coerceIn(0, 255); var ng = (g + d).coerceIn(0, 255); var nb = (bl + d).coerceIn(0, 255)
            if (keep > 0) { nr = (nr * (3 - keep) + r * keep) / 3; ng = (ng * (3 - keep) + g * keep) / 3; nb = (nb * (3 - keep) + bl * keep) / 3 }
            px[y * w + x] = (c and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
    }
    b.setPixels(px, 0, w, 0, 0, w, h)
}

/** One mark on a page; points are 0..1 of the page width / height. kind 0 pen, 1 highlighter, 2 text, 3 text on a white patch (edit text). */
class PAnn(val page: Int, val kind: Int, val pts: List<Offset>, val color: Int, val text: String = "")

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

/** [dark] is screen-only: black patch and light text for black-ink marks; the saved PDF always uses dark = false. */
private fun drawAnn(cv: android.graphics.Canvas, a: PAnn, w: Float, h: Float, pt: android.graphics.Paint, dark: Boolean = false) {
    pt.reset(); pt.isAntiAlias = true; pt.color = if (dark && a.color == 0xFF000000.toInt()) 0xFFEDEDED.toInt() else a.color
    if (a.kind >= 2) {
        pt.style = android.graphics.Paint.Style.FILL; pt.textSize = w * .03f
        val p0 = a.pts.firstOrNull() ?: return
        val box = if (a.kind == 3 && a.pts.size >= 2) annBounds(a, w, h, pt) else null
        if (a.kind == 3) { val r = box ?: annBounds(a, w, h, pt); val c = pt.color; pt.color = if (dark) android.graphics.Color.BLACK else android.graphics.Color.WHITE; cv.drawRect(r, pt); pt.color = c }
        val ox = box?.let { it.left + pt.textSize * .15f } ?: p0.x * w
        val oy = box?.let { it.top + pt.textSize } ?: p0.y * h
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
    var zoom by remember { mutableFloatStateOf(1f) }
    var committed by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(zoom) { delay(250); committed = zoom }
    val ctx = LocalContext.current
    val anns = remember { mutableStateListOf<PAnn>() }
    var tool by remember { mutableIntStateOf(0) } // 1 pen, 2 highlighter, 3 text
    var ci by remember { mutableIntStateOf(0) }
    val cols = intArrayOf(0xFFD32F2F.toInt(), 0xFFFFEB3B.toInt(), 0xFF1976D2.toInt(), 0xFF388E3C.toInt(), 0xFF000000.toInt())
    var textAt by remember { mutableStateOf<Triple<Int, Float, Float>?>(null) }; var editing by remember { mutableStateOf<PAnn?>(null) }; var textVal by remember { mutableStateOf("") }
    var jump by remember { mutableStateOf(false) }; var jumpText by remember { mutableStateOf("") }
    val widthDp = LocalConfiguration.current.screenWidthDp.dp
    val pagePx = with(LocalDensity.current) { (widthDp * committed).roundToPx() }.coerceIn(300, 2400)
    val acts = listOf(
        OAct(tr("Zoom in", "Yakınlaştır"), "View", Icons.Outlined.ZoomIn) { zoom = (zoom * 1.25f).coerceAtMost(4f) },
        OAct(tr("Zoom out", "Uzaklaştır"), "View", Icons.Outlined.ZoomOut) { zoom = (zoom / 1.25f).coerceAtLeast(1f) },
        OAct(tr("Go to page", "Sayfaya git"), "View", Icons.Outlined.FormatListNumbered) { jumpText = ""; jump = true },
        OAct(tr("Pen", "Kalem"), "Edit", Icons.Outlined.Edit, on = tool == 1) { tool = if (tool == 1) 0 else 1 },
        OAct(tr("Highlight", "Vurgu"), "Edit", Icons.Outlined.BorderColor, on = tool == 2) { tool = if (tool == 2) 0 else 2; if (tool == 2 && ci > 1) ci = 1 },
        OAct(tr("Add text", "Yazı ekle"), "Edit", Icons.Outlined.TextFields, on = tool == 3) { tool = if (tool == 3) 0 else 3 },
        OAct(tr("Edit text", "Metni düzenle"), "Edit", Icons.Outlined.EditNote, on = tool == 4) { tool = if (tool == 4) 0 else 4; if (tool == 4) ci = cols.size - 1 },
        OAct(tr("Colour", "Renk"), "Edit", Icons.Outlined.Palette) { ci = (ci + 1) % cols.size },
        OAct(tr("Undo", "Geri al"), "Edit", Icons.Outlined.Undo, enabled = anns.isNotEmpty()) { anns.removeAt(anns.size - 1) },
        OAct(tr("Save a copy", "Kopya kaydet"), "File", Icons.Outlined.ContentCopy, png = "copy") {
            val snap = anns.toList(); val paint = android.graphics.Paint()
            doc.saveCopy({ out ->
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
            }) { msg -> if (!msg.startsWith("Save failed")) doc.dirty = false; Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
        },
        OAct(tr("Close", "Kapat"), "File", Icons.Outlined.Close) { doc.close() },
    )
    PdfChrome(theme, doc.name, acts, "${tr("Page", "Sayfa")} ${list.firstVisibleItemIndex + 1} / ${p.count}   ${(zoom * 100).toInt()}%", doc.close) {
        Box(Modifier.fillMaxSize().background(if (OfficePrefs.darkPdf) Color.Black else Color.Transparent).ofPinch { zoom = (zoom * it).coerceIn(1f, 4f) }) {
            Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                LazyColumn(Modifier.width(widthDp * zoom), state = list, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(p.count) { i -> PdfPage(p, i, pagePx, anns.filter { it.page == i }, tool, cols[ci],
                        { a -> anns.add(a); doc.dirty = true }, { x, y, hit -> editing = hit; textVal = hit?.text ?: ""; textAt = Triple(i, x, y) }) }
                }
            }
        }
    }
    textAt?.let { (pg, x, y) ->
        WinAlert(onDismissRequest = { textAt = null }, title = { Text(if (tool == 4 || editing != null) tr("Edit text", "Metni düzenle") else tr("Add text", "Yazı ekle")) },
            text = { PField(textVal, { textVal = it }, "") },
            confirmButton = { TextButton({
                val old = editing; val fresh = old != null && old.pts.size >= 2 && old !in anns; if (old != null) anns.remove(old)
                if (textVal.isNotBlank() || fresh) anns.add(PAnn(pg, if (tool == 4 || old?.kind == 3) 3 else 2, if (old != null) old.pts else listOf(Offset(x, y)), old?.color ?: cols[ci], textVal))
                if (textVal.isNotBlank() || old != null) doc.dirty = true; textAt = null; editing = null }) { Text("OK") } },
            dismissButton = { TextButton({ textAt = null; editing = null }) { Text(tr("Cancel", "İptal")) } })
    }
    if (jump) WinAlert(onDismissRequest = { jump = false }, title = { Text(tr("Go to page", "Sayfaya git") + " (1-${p.count})") },
        text = { PField(jumpText, { jumpText = it.filter(Char::isDigit).take(6) }, "1-${p.count}") },
        confirmButton = { TextButton({ jump = false; jumpText.toIntOrNull()?.let { n -> scope.launch { list.scrollToItem((n - 1).coerceIn(0, p.count - 1)) } } }) { Text("OK") } },
        dismissButton = { TextButton({ jump = false }) { Text(tr("Cancel", "İptal")) } })
}

@Composable
private fun PdfPage(p: OfPdf, i: Int, px: Int, anns: List<PAnn>, tool: Int, color: Int, onAdd: (PAnn) -> Unit, onText: (Float, Float, PAnn?) -> Unit) {
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
    Box(Modifier.fillMaxWidth()) {
        if (b != null) Image(b, null, Modifier.fillMaxWidth().background(bg), contentScale = ContentScale.FillWidth)
        else Box(Modifier.fillMaxWidth().aspectRatio(.707f).background(bg), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Color.Gray) }
        if (anns.isNotEmpty() || tool != 0 || cur != null) Canvas(Modifier.matchParentSize()
            .pointerInput(tool, anns) {
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
                            else { val hit = anns.lastOrNull { it.kind >= 2 && annBounds(it, size.width.toFloat(), size.height.toFloat(), paint).contains(a.x * size.width, a.y * size.height) }
                                onText(a.x, a.y, hit) }
                        }
                    }, onDragCancel = { cur = null })
                else if (tool != 0) detectDragGestures(
                    onDragStart = { o -> cur = PAnn(i, toolNow - 1, listOf(Offset(o.x / size.width, o.y / size.height)), colNow) },
                    onDragEnd = { cur?.let(onAdd); cur = null }, onDragCancel = { cur = null },
                    onDrag = { ch, _ -> cur?.let { c -> cur = PAnn(c.page, c.kind, c.pts + Offset(ch.position.x / size.width, ch.position.y / size.height), c.color) } })
            }) {
            drawIntoCanvas { cv -> (anns + listOfNotNull(cur)).forEach { drawAnn(cv.nativeCanvas, it, size.width, size.height, paint, dark) } }
        }
    }
}
