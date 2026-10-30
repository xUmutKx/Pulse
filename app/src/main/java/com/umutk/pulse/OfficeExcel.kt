package com.umutk.pulse

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import com.umutk.pulse.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Excel viewer / editor: owned by the Excel agent. Model, parser, formulas and writer live in OfficeExcelModel.kt.

/** The Excel window in the look chosen for it (theme = ofTheme(OfKind.EXCEL)): the shared themed chrome (ribbon tabs, or menus + toolbar in 2003 / 95). */
@Composable
fun ExcelChrome(theme: OfTheme, title: String, acts: List<OAct>, status: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    OfChromeBasic(theme, title, acts, status, onBack, content)

/** Copied cells (row / column offsets from the top-left of the copied range). */
class XClip(val h: Int, val w: Int, val items: List<Triple<Int, Int, XCell>>)

/** Undo snapshot of one sheet (cells, merges, column widths; pictures and tables are not part of it). */
class XSnap(val cells: Map<Long, XCell>, val merges: List<IntArray>, val colW: Map<Int, Float>)
class XHist { val undo = ArrayList<XSnap>(); val redo = ArrayList<XSnap>(); var cur: XSnap? = null }
// shortcut: sheets over 20000 cells have no undo (a full copy per edit), when needed switch to per-edit deltas
private fun xSnap(sh: XSheet): XSnap? = if (sh.cells.size > 20000) null else XSnap(sh.cells.mapValues { (_, x) -> XCell(x.s, x.kind, x.f, x.st) }, sh.merges.map { it.clone() }, HashMap(sh.colW))
private val X_FONTS = listOf("Calibri", "Arial", "Times New Roman", "Courier New", "Verdana", "Georgia", "Tahoma", "Cambria", "Consolas", "Comic Sans MS")

class XState {
    var sheet by mutableIntStateOf(0)
    var ver by mutableIntStateOf(0)
    var r by mutableIntStateOf(0); var c by mutableIntStateOf(0)
    var r2 by mutableIntStateOf(0); var c2 by mutableIntStateOf(0)
    var zoom by mutableFloatStateOf(1f)
    var gx by mutableFloatStateOf(0f); var gy by mutableFloatStateOf(0f)
    var lines by mutableStateOf(true)
    var extend by mutableStateOf(false)
    var focusReq by mutableIntStateOf(0)
    var menu by mutableStateOf<Offset?>(null)       // long-press menu position in the grid
    var pic by mutableStateOf<XPic?>(null)          // selected picture
    var pv by mutableIntStateOf(0)                  // redraw tick while a picture is dragged
    var present by mutableStateOf(false)
    var clip: XClip? = null
    var clipText = ""
    val r1 get() = minOf(r, r2); val rb get() = maxOf(r, r2)
    val c1 get() = minOf(c, c2); val cb get() = maxOf(c, c2)
    fun pick(rr: Int, cc: Int) { r = rr; c = cc; r2 = rr; c2 = cc }
}

/** Text of a cell as shown (second = right-aligned by default). */
private fun xShow(b: XBook, ev: XEval, x: XCell, r: Int, c: Int): Pair<String, Boolean> {
    val code = b.style(x.st).fmt
    if (x.f != null) return when (val v = ev.value(r, c)) { is Double -> xFormat(code, v) to true; null -> "" to false; else -> v.toString() to false }
    if (x.kind == 'n') { val d = x.s.toDoubleOrNull(); return if (d != null) xFormat(code, d) to true else x.s to false }
    return x.s to (x.kind == 'b')
}

private fun xRaw(x: XCell?) = if (x == null) "" else if (x.f != null) "=" + x.f else x.s

@Composable
fun OfExcelScreen(doc: OfDoc, theme: OfTheme) {
    var book by remember { mutableStateOf<XBook?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc) {
        val r = withContext(Dispatchers.Default) { try { Result.success(xLoad(doc.file, doc.name)) } catch (e: Throwable) { Result.failure(e) } }
        r.onSuccess { book = it }.onFailure { err = it.message ?: it.javaClass.simpleName }
    }
    val b = book
    if (b == null) {
        ExcelChrome(theme, doc.name, listOf(OAct("Close", "File", Icons.Outlined.Close) { doc.close() }), if (err == null) tr("Loading...", "Yükleniyor...") else "", doc.close) {
            err?.let { OfErrorCard(theme, it) }
        }
    } else XScreen(doc, theme, b)
}

private val X_PALETTE = listOf(0xFFFFFF00, 0xFFFFC000, 0xFFFF0000, 0xFF92D050, 0xFF00B0F0, 0xFF0070C0, 0xFF7030A0, 0xFFFFFFFF,
    0xFFFFE699, 0xFFF8CBAD, 0xFFC6E0B4, 0xFFBDD7EE, 0xFFD9D2E9, 0xFFD9D9D9, 0xFF404040, 0xFF000000).map { it.toInt() }
private val X_SIZES = listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 24, 28, 36, 48, 72)
private val X_FORMATS = listOf("General" to "General", "Number" to "0.00", "Integer" to "0", "Thousands" to "#,##0", "Currency" to "\$#,##0.00",
    "Percent" to "0.00%", "Date" to "yyyy-mm-dd", "Time" to "h:mm:ss", "Text" to "@")

@Composable
private fun XScreen(doc: OfDoc, t: OfTheme, book: XBook) {
    val pal = t.pal; val ctx = LocalContext.current
    val st = remember { XState() }
    val sh = book.sheets[st.sheet.coerceIn(0, book.sheets.size - 1)]
    val ev = remember(st.ver, st.sheet) { XEval(sh) }
    var dlg by remember { mutableStateOf("") }
    val ed = doc.canEdit
    fun toast(m: String) { Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show() } }
    val hist = remember(sh) { XHist().also { it.cur = xSnap(sh) } }
    fun touch() {
        hist.cur?.let { hist.undo.add(it); if (hist.undo.size > 50) hist.undo.removeAt(0) }
        hist.redo.clear(); hist.cur = xSnap(sh); if (hist.cur == null) hist.undo.clear()
        sh.dirty = true; st.ver++; doc.dirty = true
    }
    fun restore(a: XSnap) {
        sh.cells.clear(); a.cells.forEach { (k, x) -> sh.cells[k] = XCell(x.s, x.kind, x.f, x.st) }
        sh.merges.clear(); a.merges.forEach { sh.merges.add(it.clone()) }; sh.colW.clear(); sh.colW.putAll(a.colW)
        sh.shifted = true; sh.dirty = true; hist.cur = a; st.ver++; doc.dirty = true
    }
    fun undo() { if (hist.undo.isEmpty()) return; val a = hist.undo.removeAt(hist.undo.size - 1); xSnap(sh)?.let { hist.redo.add(it) }; restore(a) }
    fun redo() { if (hist.redo.isEmpty()) return; val a = hist.redo.removeAt(hist.redo.size - 1); xSnap(sh)?.let { hist.undo.add(it) }; restore(a) }
    var findQ by remember { mutableStateOf("") }
    val dens = LocalDensity.current.density

    /** [f] on every cell of the selection (empty cells are created for ranges up to 10000 cells, bigger ranges touch only filled cells). */
    fun forSelRC(create: Boolean, f: (XCell, Int, Int) -> Unit) {
        val area = (st.rb - st.r1 + 1).toLong() * (st.cb - st.c1 + 1)
        if (area <= 10000) { for (r in st.r1..st.rb) for (c in st.c1..st.cb) { val k = xKey(r, c); val x = sh.cells[k] ?: if (create) XCell("", 's').also { sh.put(r, c, it) } else continue; f(x, r, c) } }
        else for ((k, x) in sh.cells) if (xRow(k) in st.r1..st.rb && xCol(k) in st.c1..st.cb) f(x, xRow(k), xCol(k))
        touch()
    }
    fun forSel(create: Boolean, f: (XCell) -> Unit) = forSelRC(create) { x, _, _ -> f(x) }
    fun style(f: (XStyle) -> XStyle) = forSel(true) { it.st = book.restyle(it.st, f) }
    val anchor = sh.cells[xKey(st.r, st.c)]
    val aSt = book.style(anchor?.st ?: 0)

    fun sizeBy(d: Int) = style { val n = ((it.fsz.toFloatOrNull() ?: 11f) + d).coerceIn(6f, 72f); it.copy(fsz = n.toInt().toString(), fontId = -1) }
    fun border(kind: String) {
        val r1 = st.r1; val rb = st.rb; val c1 = st.c1; val cb = st.cb
        forSelRC(true) { x, r, c ->
            val cur = book.style(x.st).bm.coerceAtLeast(0)
            val m = when (kind) {
                "all" -> 15; "none" -> 0
                "out" -> cur or (if (c == c1) 1 else 0) or (if (c == cb) 2 else 0) or (if (r == r1) 4 else 0) or (if (r == rb) 8 else 0)
                "top" -> if (r == r1) cur or 4 else cur
                "bottom" -> if (r == rb) cur or 8 else cur
                "left" -> if (c == c1) cur or 1 else cur
                else -> if (c == cb) cur or 2 else cur
            }
            x.st = book.restyle(x.st) { it.copy(bm = m) }
        }
    }
    fun clearSel() {
        val rm = ArrayList<Long>()
        forSel(false) { x -> x.s = ""; x.f = null; x.kind = 's' }
        for ((k, x) in sh.cells) if (x.s.isEmpty() && x.f == null && x.st == 0) rm.add(k)
        rm.forEach { sh.cells.remove(it) }
    }
    fun sysClip() = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    fun copySel() {
        st.clip = XClip(st.rb - st.r1 + 1, st.cb - st.c1 + 1, sh.cells.filter { xRow(it.key) in st.r1..st.rb && xCol(it.key) in st.c1..st.cb }.map { (k, x) -> Triple(xRow(k) - st.r1, xCol(k) - st.c1, XCell(x.s, x.kind, x.f, x.st)) })
        st.clipText = ""
        if ((st.rb - st.r1 + 1).toLong() * (st.cb - st.c1 + 1) <= 10000) runCatching {
            val tsv = (st.r1..st.rb).joinToString("\n") { r -> (st.c1..st.cb).joinToString("\t") { c -> sh.cells[xKey(r, c)]?.let { xShow(book, ev, it, r, c).first } ?: "" } }
            st.clipText = tsv; sysClip().setPrimaryClip(android.content.ClipData.newPlainText("", tsv))
        }
    }
    fun cutSel() { copySel(); clearSel() }
    fun pasteSel() {
        val sys = runCatching { sysClip().primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() }.getOrNull()
        val cl = st.clip
        fun ok(r: Int, c: Int) = r in 0..1048575 && c in 0..16383
        if (cl != null && (sys == null || st.clipText.isEmpty() || sys == st.clipText)) { for ((dr, dc, x) in cl.items) if (ok(st.r1 + dr, st.c1 + dc)) sh.put(st.r1 + dr, st.c1 + dc, XCell(x.s, x.kind, x.f, x.st)) }
        else if (sys != null) sys.split('\n').forEachIndexed { i, line -> line.trimEnd('\r').split('\t').forEachIndexed { j, v -> if (v.isNotEmpty() && ok(st.r1 + i, st.c1 + j)) sh.put(st.r1 + i, st.c1 + j, xTyped(v, 0).first) } }
        else return
        touch()
    }
    fun clearFmt(all: Boolean) {
        forSel(false) { x -> x.st = 0; if (all) { x.s = ""; x.f = null; x.kind = 's' } }
        sh.cells.entries.removeAll { it.value.s.isEmpty() && it.value.f == null && it.value.st == 0 }
    }
    fun decBy(f: String, d: Int): String {
        val base = if (f == "General") "0" else f
        val m = Regex("0(\\.(0+))?").find(base) ?: return f
        val n = ((m.groups[2]?.value?.length ?: 0) + d).coerceIn(0, 15)
        return base.replaceRange(m.range, if (n == 0) "0" else "0." + "0".repeat(n))
    }
    fun thousands() { val on = aSt.fmt.contains("#,##0"); style { it.copy(fmt = if (on) it.fmt.replace("#,##0", "0") else if (it.fmt == "General") "#,##0" else if (it.fmt.startsWith("0")) "#,##" + it.fmt else it.fmt) } }
    fun merge() {
        if (st.r1 == st.rb && st.c1 == st.cb) return
        sh.merges.removeAll { it[0] <= st.rb && it[2] >= st.r1 && it[1] <= st.cb && it[3] >= st.c1 }
        sh.merges.add(intArrayOf(st.r1, st.c1, st.rb, st.cb))
        val x = sh.cells[xKey(st.r1, st.c1)] ?: XCell("", 's').also { sh.put(st.r1, st.c1, it) }
        x.st = book.restyle(x.st) { it.copy(align = "center") }; sh.shifted = true; touch()
    }
    fun unmerge() { if (sh.merges.removeAll { it[0] <= st.rb && it[2] >= st.r1 && it[1] <= st.cb && it[3] >= st.c1 }) { sh.shifted = true; touch() } }
    fun colWidth(w: Float?) {
        for (c in st.c1..minOf(st.cb, st.c1 + 200)) {
            val nw = w ?: ((sh.cells.filter { xCol(it.key) == c }.maxOfOrNull { (k, x) -> xShow(book, ev, x, xRow(k), c).first.length } ?: 6) * 1.15f + 2f).coerceIn(4f, 80f)
            sh.colW[c] = nw
        }
        sh.shifted = true; touch()
    }
    fun sortSel(asc: Boolean) {
        val single = st.r1 == st.rb && st.c1 == st.cb
        // shortcut: one selected cell sorts the whole used range under row 1 (header kept), formulas are moved as text without fixing references
        val ra = if (single) 1 else st.r1; val rz = if (single) sh.maxR else st.rb; val ca = if (single) 0 else st.c1; val cz = if (single) sh.maxC else st.cb
        if (rz <= ra) return
        val rows = (ra..rz).map { r -> r to (ca..cz).map { c -> sh.cells[xKey(r, c)]?.let { XCell(it.s, it.kind, it.f, it.st) } } }
        val kc = (st.c - ca).coerceIn(0, cz - ca)
        fun v(r: Int) = ev.value(r, ca + kc)
        val sorted = rows.sortedWith { a, b ->
            val x = v(a.first); val y = v(b.first)
            val xe = x == null || x == ""; val ye = y == null || y == ""
            if (xe || ye) (if (xe && ye) 0 else if (xe) 1 else -1)
            else { val q = if (x is Double && y is Double) x.compareTo(y) else if (x is Double) -1 else if (y is Double) 1 else x.toString().compareTo(y.toString(), true); if (asc) q else -q }
        }
        sorted.forEachIndexed { i, (_, cs) -> cs.forEachIndexed { j, x -> val k = xKey(ra + i, ca + j); if (x == null) sh.cells.remove(k) else sh.cells[k] = x } }
        touch()
    }
    fun fill(down: Boolean) {
        val single = st.r1 == st.rb && st.c1 == st.cb
        fun cp(sr: Int, sc: Int, tr: Int, tc: Int) { val x = sh.cells[xKey(sr, sc)]; if (x == null) sh.cells.remove(xKey(tr, tc)) else sh.put(tr, tc, XCell(x.s, x.kind, x.f, x.st)) }
        if (down) { if (single) { if (st.r1 > 0) cp(st.r1 - 1, st.c1, st.r1, st.c1) } else for (c in st.c1..st.cb) for (r in st.r1 + 1..st.rb) cp(st.r1, c, r, c) }
        else { if (single) { if (st.c1 > 0) cp(st.r1, st.c1 - 1, st.r1, st.c1) } else for (r in st.r1..st.rb) for (c in st.c1 + 1..st.cb) cp(r, st.c1, r, c) }
        touch()
    }
    fun findNext(q: String) {
        if (q.isEmpty()) return
        val keys = sh.cells.keys.sorted(); val cur = xKey(st.r, st.c)
        val hit = (keys.filter { it > cur } + keys.filter { it <= cur }).firstOrNull { xRaw(sh.cells[it]).contains(q, true) }
        if (hit == null) { toast(tr("Not found", "Bulunamadı")); return }
        st.pick(xRow(hit), xCol(hit)); val hc = xCol(hit)
        st.gx = ((0 until hc).sumOf { ((sh.colW[it] ?: 8.43f) * 7f + 5f).toDouble() }.toFloat() * dens * st.zoom - 60f * dens).coerceAtLeast(0f)
        st.gy = (xRow(hit) * 20f * dens * st.zoom - 60f * dens).coerceAtLeast(0f)
    }
    fun delRC(row: Boolean) { val a = if (row) st.r1 else st.c1; xDelete(sh, row, a, (if (row) st.rb else st.cb) - a + 1); touch(); st.pick(st.r1, st.c1) }
    fun insertPic(uri: android.net.Uri) {
        try {
            val raw = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val bmp = xDecode(raw) ?: throw IllegalStateException("image")
            val png = raw.size > 3 && raw[0] == 0x89.toByte() && raw[1] == 'P'.code.toByte()
            val jpg = raw.size > 3 && raw[0] == 0xFF.toByte() && raw[1] == 0xD8.toByte()
            val (bytes, ext) = if (png) (raw to "png") else if (jpg) (raw to "jpeg") else (java.io.ByteArrayOutputStream().also { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() to "png")
            val k = minOf(1f, 320f / bmp.width, 300f / bmp.height)
            val pc = xNewPic(sh, bmp, bytes, ext, st.r1, st.c1, bmp.width * k, bmp.height * k)
            sh.pics.add(pc); sh.picsDirty = true; doc.dirty = true; st.pic = pc; st.ver++
        } catch (e: Throwable) { toast(tr("Picture failed", "Resim eklenemedi")) }
    }
    fun delPic() {
        val pc = st.pic ?: return
        if (pc.anchor < 0) sh.pics.remove(pc) else pc.deleted = true
        st.pic = null; sh.picsDirty = true; doc.dirty = true; st.ver++
    }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) insertPic(uri) }

    fun commit(text: String) {
        val old = sh.cells[xKey(st.r, st.c)]; val os = old?.st ?: 0
        if (text.isEmpty()) { if (os == 0) sh.cells.remove(xKey(st.r, st.c)) else sh.put(st.r, st.c, XCell("", 's', null, os)) }
        else { val (x, pct) = xTyped(text, os); if (pct) x.st = book.restyle(os) { it.copy(fmt = "0%") }; sh.put(st.r, st.c, x) }
        touch(); st.pick(st.r + 1, st.c)
    }
    fun fn(name: String) {
        var r1 = st.r1; var r2 = st.rb; var c1 = st.c1; var c2 = st.cb
        val single = r1 == r2 && c1 == c2
        fun num(r: Int, c: Int) = r >= 0 && c >= 0 && ev.value(r, c) is Double
        if (single) {
            if (num(r1 - 1, c1)) { while (num(r1 - 1, c1)) r1--; r2 = st.r - 1; c2 = c1 }
            else if (num(r1, c1 - 1)) { while (num(r1, c1 - 1)) c1--; c2 = st.c - 1; r2 = r1 }
            else { sh.put(st.r, st.c, XCell("", 'n', "$name()", 0)); touch(); return }
        }
        val target = if (single) st.r to st.c else if (r1 == r2) r1 to c2 + 1 else r2 + 1 to c1
        sh.put(target.first, target.second, XCell("", 'n', "$name(${xRef(r1, c1)}:${xRef(r2, c2)})", 0))
        touch(); st.pick(target.first, target.second)
    }
    fun save(copy: Boolean) {
        val w: (java.io.File) -> Unit = { f -> xSave(book, f) }
        val done: (String) -> Unit = { m -> toast(m); if (!copy) doc.dirty = false }
        if (copy) doc.saveCopy(w, done) else doc.save(w, done)
    }

    val acts = buildList {
        add(OAct(tr("Save", "Kaydet"), "File", Icons.Outlined.Save, enabled = ed) { save(false) })
        add(OAct(tr("Save a copy", "Kopya kaydet"), "File", Icons.Outlined.SaveAs) { save(true) })
        add(OAct(tr("Close", "Kapat"), "File", Icons.Outlined.Close) { doc.close() })
        add(OAct(tr("Cut", "Kes"), "Home", Icons.Outlined.ContentCut, enabled = ed) { cutSel() })
        add(OAct(tr("Copy", "Kopyala"), "Home", Icons.Outlined.ContentCopy) { copySel() })
        add(OAct(tr("Paste", "Yapıştır"), "Home", Icons.Outlined.ContentPaste, enabled = ed) { pasteSel() })
        add(OAct(tr("Bold", "Kalın"), "Home", Icons.Outlined.FormatBold, on = aSt.bold, enabled = ed) { val v = !aSt.bold; style { it.copy(bold = v, fontId = -1) } })
        add(OAct(tr("Italic", "İtalik"), "Home", Icons.Outlined.FormatItalic, on = aSt.italic, enabled = ed) { val v = !aSt.italic; style { it.copy(italic = v, fontId = -1) } })
        add(OAct(tr("Underline", "Altı çizili"), "Home", Icons.Outlined.FormatUnderlined, on = aSt.underline, enabled = ed) { val v = !aSt.underline; style { it.copy(underline = v, fontId = -1) } })
        add(OAct(tr("Smaller", "Küçült"), "Home", Icons.Outlined.Remove, enabled = ed) { sizeBy(-1) })
        add(OAct(aSt.fsz, "Home", Icons.Outlined.FormatSize, enabled = ed) { dlg = "size" })
        add(OAct(tr("Larger", "Büyült"), "Home", Icons.Outlined.Add, enabled = ed) { sizeBy(1) })
        add(OAct(tr("Fill colour", "Dolgu rengi"), "Home", Icons.Outlined.FormatColorFill, enabled = ed) { dlg = "fill" })
        add(OAct(tr("Font colour", "Yazı rengi"), "Home", Icons.Outlined.FormatColorText, enabled = ed) { dlg = "color" })
        add(OAct(tr("Number format", "Sayı biçimi"), "Home", Icons.Outlined.Tag, enabled = ed) { dlg = "fmt" })
        add(OAct(tr("Align left", "Sola yasla"), "Home", Icons.Outlined.FormatAlignLeft, on = aSt.align == "left", enabled = ed) { style { it.copy(align = "left") } })
        add(OAct(tr("Center", "Ortala"), "Home", Icons.Outlined.FormatAlignCenter, on = aSt.align == "center", enabled = ed) { style { it.copy(align = "center") } })
        add(OAct(tr("Align right", "Sağa yasla"), "Home", Icons.Outlined.FormatAlignRight, on = aSt.align == "right", enabled = ed) { style { it.copy(align = "right") } })
        add(OAct(tr("Wrap", "Kaydır"), "Home", Icons.Outlined.WrapText, on = aSt.wrap, enabled = ed) { val v = !aSt.wrap; style { it.copy(wrap = v) } })
        add(OAct(tr("Borders", "Kenarlık"), "Home", Icons.Outlined.BorderAll, enabled = ed) { dlg = "border" })
        add(OAct(tr("Undo", "Geri al"), "Home", Icons.Outlined.Undo, enabled = ed && hist.undo.isNotEmpty()) { undo() })
        add(OAct(tr("Redo", "Yinele"), "Home", Icons.Outlined.Redo, enabled = ed && hist.redo.isNotEmpty()) { redo() })
        add(OAct(tr("Strikethrough", "Üstü çizili"), "Home", Icons.Outlined.FormatStrikethrough, on = aSt.strike, enabled = ed) { val v = !aSt.strike; style { it.copy(strike = v, fontId = -1) } })
        add(OAct(tr("Merge & center", "Birleştir ve ortala"), "Home", Icons.Outlined.MergeType, enabled = ed) { merge() })
        add(OAct(tr("Unmerge", "Birleştirmeyi kaldır"), "Home", Icons.Outlined.CallSplit, enabled = ed) { unmerge() })
        add(OAct(tr("More decimals", "Ondalık artır"), "Home", Icons.Outlined.ExposurePlus1, enabled = ed) { style { it.copy(fmt = decBy(it.fmt, 1)) } })
        add(OAct(tr("Fewer decimals", "Ondalık azalt"), "Home", Icons.Outlined.ExposureNeg1, enabled = ed) { style { it.copy(fmt = decBy(it.fmt, -1)) } })
        add(OAct(tr("Find", "Bul"), "Home", Icons.Outlined.Search) { dlg = "find" })
        add(OAct(tr("Sort A-Z", "A-Z sırala"), "Data", Icons.Outlined.SortByAlpha, enabled = ed) { sortSel(true) })
        add(OAct(tr("Sort Z-A", "Z-A sırala"), "Data", Icons.Outlined.Sort, enabled = ed) { sortSel(false) })
        add(OAct(tr("Fill down", "Aşağı doldur"), "Data", Icons.Outlined.ArrowDownward, enabled = ed) { fill(true) })
        add(OAct(tr("Fill right", "Sağa doldur"), "Data", Icons.Outlined.ArrowForward, enabled = ed) { fill(false) })
        add(OAct(tr("Select range", "Aralık seç"), "Home", Icons.Outlined.SelectAll, on = st.extend) { st.extend = !st.extend; if (!st.extend) st.pick(st.r, st.c) })
        add(OAct(tr("Clear", "Temizle"), "Home", Icons.Outlined.Delete, enabled = ed) { dlg = "clear" })
        add(OAct(tr("Column width", "Sütun genişliği"), "Insert", Icons.Outlined.ViewColumn, enabled = ed) { dlg = "colw" })
        add(OAct(tr("Insert row", "Satır ekle"), "Insert", Icons.Outlined.TableRows, enabled = ed) { xInsert(book, sh, true, st.r1); touch() })
        add(OAct(tr("Insert column", "Sütun ekle"), "Insert", Icons.Outlined.ViewColumn, enabled = ed) { xInsert(book, sh, false, st.c1); touch() })
        add(OAct(tr("Delete row", "Satır sil"), "Insert", Icons.Outlined.TableRows, enabled = ed) { delRC(true) })
        add(OAct(tr("Delete column", "Sütun sil"), "Insert", Icons.Outlined.ViewColumn, enabled = ed) { delRC(false) })
        add(OAct(tr("Picture", "Resim"), "Insert", Icons.Outlined.AddPhotoAlternate, enabled = ed && !book.csv) { pickImg.launch("image/*") })
        add(OAct(tr("Remove picture", "Resmi sil"), "Insert", Icons.Outlined.BrokenImage, enabled = ed && st.pic != null) { delPic() })
        listOf("SUM" to Icons.Outlined.Functions, "AVERAGE" to Icons.Outlined.Calculate, "MIN" to Icons.Outlined.ArrowDownward, "MAX" to Icons.Outlined.ArrowUpward, "COUNT" to Icons.Outlined.Tag)
            .forEach { (n, ic) -> add(OAct(n.lowercase().replaceFirstChar { it.uppercase() }, "Formulas", ic, enabled = ed) { fn(n) }) }
        add(OAct(tr("Zoom in", "Yakınlaştır"), "View", Icons.Outlined.ZoomIn) { st.zoom = (st.zoom * 1.2f).coerceAtMost(3f) })
        add(OAct(tr("Zoom out", "Uzaklaştır"), "View", Icons.Outlined.ZoomOut) { st.zoom = (st.zoom / 1.2f).coerceAtLeast(.4f) })
        add(OAct(tr("Present", "Sunum"), "View", Icons.Outlined.Fullscreen) { st.menu = null; st.pic = null; st.present = true })
        add(OAct(tr("Gridlines", "Kılavuz"), "View", Icons.Outlined.GridOn, on = st.lines) { st.lines = !st.lines })
    }

    // status: sum / average / count of the selection
    val stat = remember(st.ver, st.sheet, st.r, st.c, st.r2, st.c2) {
        var sum = 0.0; var n = 0
        val area = (st.rb - st.r1 + 1).toLong() * (st.cb - st.c1 + 1)
        if (area > 1) {
            fun add(r: Int, c: Int) { (ev.value(r, c) as? Double)?.let { sum += it; n++ } }
            if (area <= sh.cells.size) { for (r in st.r1..st.rb) for (c in st.c1..st.cb) add(r, c) }
            else for (k in sh.cells.keys) if (xRow(k) in st.r1..st.rb && xCol(k) in st.c1..st.cb) add(xRow(k), xCol(k))
        }
        if (n == 0) tr("Ready", "Hazır") else "${tr("Average", "Ortalama")}: ${xGeneral(sum / n)}    ${tr("Count", "Sayı")}: $n    ${tr("Sum", "Toplam")}: ${xGeneral(sum)}"
    }
    val status = stat + "    " + (st.zoom * 100).toInt() + "%"

    // Word-style stacked Home ribbon (same cells / chips / groups; ink always comes from the look's palette)
    // the ribbon's ink comes from the strip it is drawn on (light or dark), so it can never be black on black
    val rInk = ofInkOn(if (t.base == "365" || t.base == "2017") ofBelow(t) else pal.ribbonBg)
    val ribbon: @Composable () -> Unit = {
        @Composable fun Cell(icon: ImageVector, label: String, on: Boolean = false, en: Boolean = ed, bar: Color? = null, badge: ImageVector? = null, click: () -> Unit) {
            Box(Modifier.size(28.dp).clip(RoundedCornerShape(3.dp)).background(if (on) pal.accent.copy(alpha = .22f) else Color.Transparent).alpha(if (en) 1f else .4f)
                .clickable(enabled = en, onClick = click), contentAlignment = Alignment.Center) {
                Icon(icon, label, Modifier.size(19.dp), rInk)
                if (badge != null) Icon(badge, null, Modifier.align(Alignment.BottomEnd).padding(1.dp).size(10.dp), rInk)
                if (bar != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(15.dp).height(3.dp).background(bar))
            }
        }
        @Composable fun Chip(text: String, w: androidx.compose.ui.unit.Dp, click: () -> Unit) {
            Row(Modifier.height(26.dp).width(w).clip(RoundedCornerShape(3.dp)).border(1.dp, pal.line, RoundedCornerShape(3.dp)).alpha(if (ed) 1f else .4f)
                .clickable(enabled = ed, onClick = click).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text, color = rInk, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(16.dp), rInk)
            }
        }
        @Composable fun Group(cap: String, sep: Boolean = true, body: @Composable ColumnScope.() -> Unit) {
            if (sep) Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(66.dp).background(pal.line))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = body)
                Text(cap, color = rInk.copy(alpha = .6f), fontSize = 9.sp, maxLines = 1)
            }
        }
        val fmtName = X_FORMATS.firstOrNull { it.second == aSt.fmt }?.first ?: aSt.fmt
        CompositionLocalProvider(LocalContentColor provides rInk) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Group(tr("Edit", "Düzenle"), sep = false) {
                Row { Cell(Icons.Outlined.Undo, tr("Undo", "Geri al"), en = ed && hist.undo.isNotEmpty()) { undo() }; Cell(Icons.Outlined.Redo, tr("Redo", "Yinele"), en = ed && hist.redo.isNotEmpty()) { redo() }; Cell(Icons.Outlined.Search, tr("Find", "Bul"), en = true) { dlg = "find" } }
                Row { Cell(Icons.Outlined.ContentCut, tr("Cut", "Kes")) { cutSel() }; Cell(Icons.Outlined.ContentCopy, tr("Copy", "Kopyala"), en = true) { copySel() }; Cell(Icons.Outlined.ContentPaste, tr("Paste", "Yapıştır")) { pasteSel() } }
            }
            Group(tr("Font", "Yazı tipi")) {
                Row(verticalAlignment = Alignment.CenterVertically) { Chip(aSt.fname, 104.dp) { dlg = "font" }; Spacer(Modifier.width(3.dp)); Chip(aSt.fsz, 48.dp) { dlg = "size" }; Cell(Icons.Outlined.Add, tr("Larger", "Büyült")) { sizeBy(1) }; Cell(Icons.Outlined.Remove, tr("Smaller", "Küçült")) { sizeBy(-1) } }
                Row {
                    Cell(Icons.Outlined.FormatBold, tr("Bold", "Kalın"), aSt.bold) { val v = !aSt.bold; style { it.copy(bold = v, fontId = -1) } }
                    Cell(Icons.Outlined.FormatItalic, tr("Italic", "İtalik"), aSt.italic) { val v = !aSt.italic; style { it.copy(italic = v, fontId = -1) } }
                    Cell(Icons.Outlined.FormatUnderlined, tr("Underline", "Altı çizili"), aSt.underline) { val v = !aSt.underline; style { it.copy(underline = v, fontId = -1) } }
                    Cell(Icons.Outlined.FormatStrikethrough, tr("Strikethrough", "Üstü çizili"), aSt.strike) { val v = !aSt.strike; style { it.copy(strike = v, fontId = -1) } }
                    Cell(Icons.Outlined.FormatColorText, tr("Font colour", "Yazı rengi"), bar = if (aSt.color != 0) Color(aSt.color) else rInk) { dlg = "color" }
                    Cell(Icons.Outlined.FormatColorFill, tr("Fill colour", "Dolgu rengi"), bar = if (aSt.fill != 0) Color(aSt.fill) else Color.Transparent) { dlg = "fill" }
                    Cell(Icons.Outlined.BorderAll, tr("Borders", "Kenarlık")) { dlg = "border" }
                }
            }
            Group(tr("Alignment", "Hizalama")) {
                Row {
                    Cell(Icons.Outlined.VerticalAlignTop, tr("Top", "Üst"), aSt.valign == "top") { style { it.copy(valign = "top") } }
                    Cell(Icons.Outlined.VerticalAlignCenter, tr("Middle", "Orta"), aSt.valign == "center") { style { it.copy(valign = "center") } }
                    Cell(Icons.Outlined.VerticalAlignBottom, tr("Bottom", "Alt"), aSt.valign == "bottom") { style { it.copy(valign = "bottom") } }
                    Cell(Icons.Outlined.WrapText, tr("Wrap", "Kaydır"), aSt.wrap) { val v = !aSt.wrap; style { it.copy(wrap = v) } }
                    Cell(Icons.Outlined.FormatIndentDecrease, tr("Indent -", "Girinti -")) { style { it.copy(indent = (it.indent - 1).coerceAtLeast(0)) } }
                    Cell(Icons.Outlined.FormatIndentIncrease, tr("Indent +", "Girinti +")) { style { it.copy(indent = (it.indent + 1).coerceAtMost(15), align = it.align.ifEmpty { "left" }) } }
                }
                Row {
                    Cell(Icons.Outlined.FormatAlignLeft, tr("Align left", "Sola yasla"), aSt.align == "left") { style { it.copy(align = "left") } }
                    Cell(Icons.Outlined.FormatAlignCenter, tr("Center", "Ortala"), aSt.align == "center") { style { it.copy(align = "center") } }
                    Cell(Icons.Outlined.FormatAlignRight, tr("Align right", "Sağa yasla"), aSt.align == "right") { style { it.copy(align = "right") } }
                    Cell(Icons.Outlined.MergeType, tr("Merge & center", "Birleştir ve ortala")) { merge() }
                    Cell(Icons.Outlined.CallSplit, tr("Unmerge", "Birleştirmeyi kaldır")) { unmerge() }
                }
            }
            Group(tr("Number", "Sayı")) {
                Row { Chip(fmtName, 96.dp) { dlg = "fmt" } }
                Row {
                    Cell(Icons.Outlined.Tag, tr("Thousands", "Binlik")) { thousands() }
                    Cell(Icons.Outlined.ExposurePlus1, tr("More decimals", "Ondalık artır")) { style { it.copy(fmt = decBy(it.fmt, 1)) } }
                    Cell(Icons.Outlined.ExposureNeg1, tr("Fewer decimals", "Ondalık azalt")) { style { it.copy(fmt = decBy(it.fmt, -1)) } }
                }
            }
            Group(tr("Cells", "Hücreler")) {
                Row {
                    Cell(Icons.Outlined.TableRows, tr("Insert row", "Satır ekle"), badge = Icons.Outlined.Add) { xInsert(book, sh, true, st.r1); touch() }
                    Cell(Icons.Outlined.ViewColumn, tr("Insert column", "Sütun ekle"), badge = Icons.Outlined.Add) { xInsert(book, sh, false, st.c1); touch() }
                    Cell(Icons.Outlined.Straighten, tr("Column width", "Sütun genişliği")) { dlg = "colw" }
                }
                Row {
                    Cell(Icons.Outlined.TableRows, tr("Delete row", "Satır sil"), badge = Icons.Outlined.Remove) { delRC(true) }
                    Cell(Icons.Outlined.ViewColumn, tr("Delete column", "Sütun sil"), badge = Icons.Outlined.Remove) { delRC(false) }
                }
            }
            Group(tr("Editing", "Düzenleme")) {
                Row {
                    Cell(Icons.Outlined.Functions, tr("Sum", "Toplam")) { fn("SUM") }
                    Cell(Icons.Outlined.SortByAlpha, tr("Sort A-Z", "A-Z sırala")) { sortSel(true) }
                    Cell(Icons.Outlined.Sort, tr("Sort Z-A", "Z-A sırala")) { sortSel(false) }
                }
                Row {
                    Cell(Icons.Outlined.ArrowDownward, tr("Fill down", "Aşağı doldur")) { fill(true) }
                    Cell(Icons.Outlined.ArrowForward, tr("Fill right", "Sağa doldur")) { fill(false) }
                    Cell(Icons.Outlined.FormatClear, tr("Clear", "Temizle")) { dlg = "clear" }
                    Cell(Icons.Outlined.SelectAll, tr("Select range", "Aralık seç"), st.extend, en = true) { st.extend = !st.extend; if (!st.extend) st.pick(st.r, st.c) }
                }
            }
        }
        }
    }
    CompositionLocalProvider(LocalOfRibbon provides mapOf("Home" to ribbon), LocalContentColor provides pal.ink) {
    if (st.present) XPresent(st, book, sh, ev, t) else ExcelChrome(t, doc.name + if (doc.dirty) " *" else "", acts, status, doc.close) {
        Column(Modifier.fillMaxSize()) {
            // formula bar
            val fieldBg = if (OfficePrefs.bars == 3) Color(0xFF111111) else if (t.dark) Color(0xFF3B3B3B) else Color.White; val fieldInk = if (t.dark) Color.White else Color.Black
            val fr = remember { FocusRequester() }
            LaunchedEffect(st.focusReq) { if (st.focusReq > 0 && ed) runCatching { fr.requestFocus() } }
            var text by remember(st.r, st.c, st.ver, st.sheet) { mutableStateOf(xRaw(sh.cells[xKey(st.r, st.c)])) }
            var name by remember(st.r, st.c, st.r2, st.c2) { mutableStateOf(if (st.r == st.r2 && st.c == st.c2) xRef(st.r, st.c) else xRef(st.r1, st.c1) + ":" + xRef(st.rb, st.cb)) }
            val box = Modifier.background(fieldBg).then(if (t.base == "95") Modifier.bevel(false) else Modifier.border(1.dp, pal.line))
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(name, { name = it }, Modifier.width(78.dp).then(box).padding(horizontal = 6.dp, vertical = 6.dp), singleLine = true,
                    textStyle = TextStyle(color = fieldInk, fontSize = 13.sp), cursorBrush = SolidColor(fieldInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val q = name.uppercase().split(':'); val a = xParseRef(q[0]); val z = if (q.size > 1) xParseRef(q[1]) else a
                        if (a != null && z != null) { st.r = a.first; st.c = a.second; st.r2 = z.first; st.c2 = z.second }
                    }))
                Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(18.dp), pal.ink)
                Text("fx", color = pal.ink, fontSize = 15.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp))
                BasicTextField(text, { text = it }, Modifier.weight(1f).then(box).padding(horizontal = 6.dp, vertical = 6.dp).focusRequester(fr), singleLine = true, readOnly = !ed,
                    textStyle = TextStyle(color = fieldInk, fontSize = 13.sp), cursorBrush = SolidColor(fieldInk),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { commit(text) }))
                if (ed) {
                    Icon(Icons.Outlined.Check, null, Modifier.clickable { commit(text) }.padding(6.dp).size(20.dp), pal.ink)
                    Icon(Icons.Outlined.Close, null, Modifier.clickable { text = xRaw(sh.cells[xKey(st.r, st.c)]) }.padding(6.dp).size(20.dp), pal.ink)
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                XGrid(st, book, sh, ev, t, onPic = { sh.picsDirty = true; doc.dirty = true; st.ver++ })
                st.menu?.let { o ->
                    val mbg = if (OfficePrefs.bars == 3) Color.Black else if (t.dark) Color(0xFF2B2B2B) else Color.White; val mi = if (t.dark) Color.White else Color.Black
                    Box(Modifier.offset { IntOffset(o.x.toInt(), o.y.toInt()) }) {
                        DropdownMenu(true, { st.menu = null }, modifier = Modifier.background(mbg)) {
                            fun go(f: () -> Unit): () -> Unit = { st.menu = null; f() }
                            if (st.pic != null) Row(Modifier.padding(horizontal = 4.dp)) { XMi(Icons.Outlined.Delete, mi, enabled = ed, click = go { delPic() }) }
                            else {
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    XMi(Icons.Outlined.ContentCut, mi, enabled = ed, click = go { cutSel() }); XMi(Icons.Outlined.ContentCopy, mi, click = go { copySel() })
                                    XMi(Icons.Outlined.ContentPaste, mi, enabled = ed, click = go { pasteSel() }); XMi(Icons.Outlined.Delete, mi, enabled = ed, click = go { clearSel() })
                                }
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    XMi(Icons.Outlined.FormatBold, mi, aSt.bold, ed, click = go { val v = !aSt.bold; style { it.copy(bold = v, fontId = -1) } })
                                    XMi(Icons.Outlined.FormatItalic, mi, aSt.italic, ed, click = go { val v = !aSt.italic; style { it.copy(italic = v, fontId = -1) } })
                                    XMi(Icons.Outlined.FormatUnderlined, mi, aSt.underline, ed, click = go { val v = !aSt.underline; style { it.copy(underline = v, fontId = -1) } })
                                    XMi(Icons.Outlined.WrapText, mi, aSt.wrap, ed, click = go { val v = !aSt.wrap; style { it.copy(wrap = v) } })
                                    XMi(Icons.Outlined.BorderAll, mi, enabled = ed, click = go { dlg = "border" })
                                }
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    XMi(Icons.Outlined.FormatAlignLeft, mi, aSt.align == "left", ed, click = go { style { it.copy(align = "left") } })
                                    XMi(Icons.Outlined.FormatAlignCenter, mi, aSt.align == "center", ed, click = go { style { it.copy(align = "center") } })
                                    XMi(Icons.Outlined.FormatAlignRight, mi, aSt.align == "right", ed, click = go { style { it.copy(align = "right") } })
                                    XMi(Icons.Outlined.FormatSize, mi, enabled = ed, badge = Icons.Outlined.Remove, click = go { sizeBy(-1) })
                                    XMi(Icons.Outlined.FormatSize, mi, enabled = ed, badge = Icons.Outlined.Add, click = go { sizeBy(1) })
                                }
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    XMi(Icons.Outlined.FormatColorFill, mi, enabled = ed, click = go { dlg = "fill" }); XMi(Icons.Outlined.FormatColorText, mi, enabled = ed, click = go { dlg = "color" })
                                    XMi(Icons.Outlined.Tag, mi, enabled = ed, click = go { dlg = "fmt" }); XMi(Icons.Outlined.FormatSize, mi, enabled = ed, click = go { dlg = "size" })
                                }
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    XMi(Icons.Outlined.TableRows, mi, enabled = ed, badge = Icons.Outlined.Add, click = go { xInsert(book, sh, true, st.r1); touch() })
                                    XMi(Icons.Outlined.TableRows, mi, enabled = ed, badge = Icons.Outlined.Remove, click = go { delRC(true) })
                                    XMi(Icons.Outlined.ViewColumn, mi, enabled = ed, badge = Icons.Outlined.Add, click = go { xInsert(book, sh, false, st.c1); touch() })
                                    XMi(Icons.Outlined.ViewColumn, mi, enabled = ed, badge = Icons.Outlined.Remove, click = go { delRC(false) })
                                }
                            }
                        }
                    }
                }
            }
            // sheet tabs
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                book.sheets.forEachIndexed { i, s ->
                    val sel = i == st.sheet
                    Box(Modifier.padding(horizontal = 1.dp).then(if (t.base == "95") Modifier.background(if (sel) Color.White else pal.ribbonBg).bevel(!sel) else Modifier.background(if (sel) Color.White else Color.Transparent))
                        .clickable { st.sheet = i; st.pick(0, 0); st.gx = 0f; st.gy = 0f; st.pic = null; st.menu = null }.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text(s.name, color = if (sel) (if (t.dark) pal.accent else if (t.base == "365" || t.base == "2017" || t.base == "2013") pal.accent else Color.Black) else pal.ink, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                        if (sel && !t.classic) Box(Modifier.align(Alignment.BottomCenter).width(24.dp).height(2.dp).background(pal.accent))
                    }
                }
            }
        }
        if (dlg.isNotEmpty()) {
            val bg = if (OfficePrefs.bars == 3) Color.Black else if (t.dark) Color(0xFF2B2B2B) else Color.White; val ink = if (t.dark) Color.White else Color.Black
            com.umutk.pulse.WinAlert(onDismissRequest = { dlg = "" }, confirmButton = {}, containerColor = bg, text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (dlg == "size") X_SIZES.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { n ->
                            Text(n.toString(), color = ink, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.width(52.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (aSt.fsz == n.toString()) ink.copy(alpha = .15f) else Color.Transparent).clickable { dlg = ""; style { it.copy(fsz = n.toString(), fontId = -1) } }.padding(vertical = 10.dp))
                        } }
                    } else if (dlg == "border") listOf("all" to Icons.Outlined.BorderAll, "out" to Icons.Outlined.BorderOuter, "top" to Icons.Outlined.BorderTop, "bottom" to Icons.Outlined.BorderBottom,
                        "left" to Icons.Outlined.BorderLeft, "right" to Icons.Outlined.BorderRight, "none" to Icons.Outlined.BorderClear).chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { row.forEach { (k, ic) ->
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).clickable { dlg = ""; border(k) }, contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(24.dp), ink) }
                        } }
                    } else if (dlg == "font") X_FONTS.forEach { f ->
                        Text(f, color = ink, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { dlg = ""; style { it.copy(fname = f, fontId = -1) } }.padding(vertical = 8.dp))
                    } else if (dlg == "colw") listOf(null to tr("Autofit", "Otomatik sığdır"), 6f to "6", 8.43f to "8.43", 12f to "12", 16f to "16", 24f to "24", 40f to "40").forEach { (w, label) ->
                        Text(label, color = ink, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { dlg = ""; colWidth(w) }.padding(vertical = 8.dp))
                    } else if (dlg == "clear") listOf(tr("Clear contents", "İçeriği temizle") to 0, tr("Clear formats", "Biçimleri temizle") to 1, tr("Clear all", "Tümünü temizle") to 2).forEach { (label, k) ->
                        Text(label, color = ink, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { dlg = ""; if (k == 0) clearSel() else clearFmt(k == 2) }.padding(vertical = 8.dp))
                    } else if (dlg == "find") Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(findQ, { findQ = it }, Modifier.weight(1f).border(1.dp, Color(0xFF888888)).padding(8.dp), singleLine = true, textStyle = TextStyle(color = ink, fontSize = 15.sp), cursorBrush = SolidColor(ink),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { findNext(findQ) }))
                        Icon(Icons.Outlined.Search, null, Modifier.clickable { findNext(findQ) }.padding(8.dp).size(24.dp), ink)
                    } else if (dlg == "fmt") X_FORMATS.forEach { (label, code) ->
                        Text(label, color = ink, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { dlg = ""; style { it.copy(fmt = code) } }.padding(vertical = 8.dp))
                    } else {
                        val fill = dlg == "fill"
                        X_PALETTE.chunked(8).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { col ->
                                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(if (t.classic) 0.dp else 6.dp)).background(Color(col)).border(1.dp, Color(0xFF888888), RoundedCornerShape(if (t.classic) 0.dp else 6.dp))
                                        .clickable { dlg = ""; if (fill) style { it.copy(fill = col, fillId = -1) } else style { it.copy(color = col, fontId = -1) } })
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().clickable { dlg = ""; if (fill) style { it.copy(fill = 0, fillId = 0) } else style { it.copy(color = 0, fontId = -1) } }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FormatColorReset, null, Modifier.size(20.dp), ink); Spacer(Modifier.width(8.dp)); Text(if (fill) tr("No fill", "Dolgu yok") else tr("Automatic", "Otomatik"), color = ink, fontSize = 14.sp)
                        }
                    }
                }
            })
        }
    } }
}

/** One icon button of the long-press menu; [badge] is a small plus / minus in the corner. */
@Composable
private fun XMi(icon: ImageVector, ink: Color, on: Boolean = false, enabled: Boolean = true, badge: ImageVector? = null, click: () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(if (on) ink.copy(alpha = .15f) else Color.Transparent).alpha(if (enabled) 1f else .35f).clickable(enabled = enabled, onClick = click), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), ink)
        if (badge != null) Icon(badge, null, Modifier.align(Alignment.BottomEnd).padding(4.dp).size(11.dp), ink)
    }
}

/** Full-screen read-only view of the sheet: no chrome, pinch zoom, drag to scroll; a small pill switches sheets and leaves. */
@Composable
private fun XPresent(st: XState, book: XBook, sh: XSheet, ev: XEval, t: OfTheme) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val ic = WindowCompat.getInsetsController((view.context as Activity).window, view)
        ic.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ic.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { ic.show(WindowInsetsCompat.Type.systemBars()) }
    }
    androidx.activity.compose.BackHandler { st.present = false }
    fun go(i: Int) { st.sheet = i.coerceIn(0, book.sheets.size - 1); st.pick(0, 0); st.gx = 0f; st.gy = 0f }
    Box(Modifier.fillMaxSize().background(Color.White)) {
        XGrid(st, book, sh, ev, t, ro = true)
        Row(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(20.dp)).background(Color(0x66000000)), verticalAlignment = Alignment.CenterVertically) {
            if (book.sheets.size > 1) {
                Icon(Icons.Outlined.ChevronLeft, null, Modifier.clickable { go(st.sheet - 1) }.padding(8.dp).size(24.dp), Color.White)
                Icon(Icons.Outlined.ChevronRight, null, Modifier.clickable { go(st.sheet + 1) }.padding(8.dp).size(24.dp), Color.White)
            }
            Icon(Icons.Outlined.FullscreenExit, null, Modifier.clickable { st.present = false }.padding(8.dp).size(24.dp), Color.White)
        }
    }
}

@Composable
private fun XGrid(st: XState, book: XBook, sh: XSheet, ev: XEval, t: OfTheme, ro: Boolean = false, onPic: () -> Unit = {}) {
    val d = LocalDensity.current.density; val pal = t.pal; val z = st.zoom
    val nCols = (sh.maxC + 12).coerceIn(26, 16384); val nRows = (sh.maxR + 40).coerceIn(100, 1048576)
    val colX = remember(sh, st.ver, z, d, nCols) { FloatArray(nCols + 1).also { for (c in 0 until nCols) it[c + 1] = it[c] + ((sh.colW[c] ?: 8.43f) * 7f + 5f) * d * z } }
    val covered = remember(sh, st.ver) { HashSet<Long>().also { s -> for (m in sh.merges) { if ((m[2] - m[0] + 1).toLong() * (m[3] - m[1] + 1) > 10000) continue; for (r in m[0]..m[2]) for (c in m[1]..m[3]) if (r != m[0] || c != m[1]) s.add(xKey(r, c)) } } }
    var vp by remember { mutableStateOf(IntSize.Zero) }
    val rowH = 20f * d * z; val hdrW = if (ro) 0f else 34f * d; val hdrH = if (ro) 0f else 20f * d
    val lim = remember { FloatArray(2) }
    lim[0] = (colX[nCols] - (vp.width - hdrW)).coerceAtLeast(0f); lim[1] = (nRows * rowH - (vp.height - hdrH)).coerceAtLeast(0f)
    val tp = remember { Paint(Paint.ANTI_ALIAS_FLAG) }; val fp = remember { Paint() }
    val faces = remember { arrayOf(Typeface.DEFAULT, Typeface.DEFAULT_BOLD, Typeface.create(Typeface.DEFAULT, Typeface.ITALIC), Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)) }
    fun colAt(x: Float): Int { var lo = 0; var hi = nCols - 1; while (lo < hi) { val m = (lo + hi + 1) / 2; if (colX[m] <= x) lo = m else hi = m - 1 }; return lo }

    val u = d * z / 9525f
    val pp = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    /** Picture rectangle in grid space (zoomed pixels). */
    fun picRect(p: XPic) = floatArrayOf(colX[p.c1.coerceIn(0, nCols)] + p.co1 * u, p.r1 * rowH + p.ro1 * u, colX[p.c2.coerceIn(0, nCols)] + p.co2 * u, p.r2 * rowH + p.ro2 * u)
    fun hitPic(o: Offset): XPic? {
        if (o.x < hdrW || o.y < hdrH) return null
        val gx = o.x - hdrW + st.gx; val gy = o.y - hdrH + st.gy
        return sh.pics.lastOrNull { !it.deleted && picRect(it).let { r -> gx >= r[0] && gx <= r[2] && gy >= r[1] && gy <= r[3] } }
    }
    fun applyRect(p: XPic, a: FloatArray) {
        val x0 = a[0].coerceAtLeast(0f); val y0 = a[1].coerceAtLeast(0f); val x1 = maxOf(a[2], x0 + 12f * d * z); val y1 = maxOf(a[3], y0 + 12f * d * z)
        p.c1 = colAt(x0); p.co1 = ((x0 - colX[p.c1]) / u).toInt(); p.r1 = (y0 / rowH).toInt(); p.ro1 = ((y0 - p.r1 * rowH) / u).toInt()
        p.c2 = colAt(x1); p.co2 = ((x1 - colX[p.c2]) / u).toInt(); p.r2 = (y1 / rowH).toInt(); p.ro2 = ((y1 - p.r2 * rowH) / u).toInt()
    }
    fun cellOf(o: Offset) = ((o.y - hdrH + st.gy) / rowH).toInt().coerceIn(0, nRows - 1) to colAt(o.x - hdrW + st.gx).coerceIn(0, nCols - 1)

    Canvas(Modifier.fillMaxSize().clipToBounds().onSizeChanged { vp = it }
        .ofPinch { f -> val n = (st.zoom * f).coerceIn(.4f, 3f); val k = n / st.zoom; st.zoom = n; st.gx = (st.gx * k).coerceIn(0f, lim[0] * k); st.gy = (st.gy * k).coerceIn(0f, lim[1] * k) }
        .then(if (ro) Modifier else Modifier.pointerInput(colX, rowH) {
            fun tap(o: Offset) {
                if (o.x >= hdrW && o.y >= hdrH) { val hp = hitPic(o); st.pic = hp; if (hp != null) return } else st.pic = null
                val (r, c) = cellOf(o)
                when {
                    o.x < hdrW && o.y < hdrH -> { st.r = 0; st.c = 0; st.r2 = nRows - 1; st.c2 = nCols - 1 }
                    o.y < hdrH -> { st.r = 0; st.c = c; st.r2 = nRows - 1; st.c2 = c }
                    o.x < hdrW -> { st.r = r; st.c = 0; st.r2 = r; st.c2 = nCols - 1 }
                    st.extend -> { st.r2 = r; st.c2 = c }
                    else -> st.pick(r, c)
                }
            }
            detectTapGestures(onTap = { tap(it) }, onDoubleTap = { tap(it); st.focusReq++ }, onLongPress = { o ->
                if (o.x >= hdrW && o.y >= hdrH) {
                    val hp = hitPic(o); st.pic = hp
                    if (hp == null) { val (r, c) = cellOf(o); if (r !in st.r1..st.rb || c !in st.c1..st.cb) st.pick(r, c) }
                    st.menu = o
                }
            })
        })
        .pointerInput(colX, rowH, ro) {
            var mode = 0   // 0 scroll, 1 move picture, 2 resize picture, 3 select range
            val da = FloatArray(4); var pic: XPic? = null
            detectDragGestures(
                onDragStart = { o ->
                    mode = 0; pic = null
                    if (!ro) {
                        val sp = st.pic?.takeIf { !it.deleted }
                        if (sp != null) {
                            val r = picRect(sp)
                            if (Math.abs(o.x - (hdrW - st.gx + r[2])) < 28f * d && Math.abs(o.y - (hdrH - st.gy + r[3])) < 28f * d) mode = 2 else if (hitPic(o) === sp) mode = 1
                            if (mode != 0) { pic = sp; r.copyInto(da) }
                        }
                        if (mode == 0 && o.x > hdrW && o.y > hdrH) {
                            val near = Math.abs(o.x - (hdrW - st.gx + colX[(st.cb + 1).coerceAtMost(nCols)])) < 28f * d && Math.abs(o.y - (hdrH - st.gy + (st.rb + 1) * rowH)) < 28f * d
                            if (near || st.extend) { mode = 3; if (!near) { val (r, c) = cellOf(o); st.pick(r, c) } }
                        }
                    }
                },
                onDragEnd = { if (mode == 1 || mode == 2) { pic?.moved = true; onPic() }; mode = 0 },
                onDragCancel = { mode = 0 },
            ) { ch, drag ->
                ch.consume()
                when (mode) {
                    1 -> { val sx = if (da[0] + drag.x < 0f) -da[0] else drag.x; val sy = if (da[1] + drag.y < 0f) -da[1] else drag.y
                        da[0] += sx; da[2] += sx; da[1] += sy; da[3] += sy; pic?.let { applyRect(it, da) }; st.pv++ }
                    2 -> { da[2] += drag.x; da[3] += drag.y; pic?.let { applyRect(it, da) }; st.pv++ }
                    3 -> { val (r, c) = cellOf(ch.position); st.r2 = r; st.c2 = c }
                    else -> { st.gx = (st.gx - drag.x).coerceIn(0f, lim[0]); st.gy = (st.gy - drag.y).coerceIn(0f, lim[1]) }
                }
            }
        }
    ) {
        st.ver; st.pv
        val gx = st.gx; val gy = st.gy; val W = size.width; val H = size.height
        val ox = hdrW - gx; val oy = hdrH - gy
        val c0 = colAt(gx); var c1 = c0; while (c1 < nCols - 1 && colX[c1 + 1] - gx < W - hdrW) c1++
        val r0 = (gy / rowH).toInt().coerceAtLeast(0); val r1 = (((gy + H - hdrH) / rowH).toInt() + 1).coerceAtMost(nRows - 1)
        val m365 = t.base == "365"
        val acc = (if (m365) ofBrand(t) else pal.accent).toArgb(); val ink = (if (m365) Color(0xFFEDEDED) else pal.ink).toArgb(); val line = (if (m365) Color(0xFF5A5A5A) else pal.line).toArgb()
        drawIntoCanvas { cv ->
            val n = cv.nativeCanvas
            val dk = OfficePrefs.darkPages
            n.drawColor((if (dk) OfficePrefs.pageBgC else Color.White).toArgb())
            n.save(); n.clipRect(hdrW, hdrH, W, H)
            fp.style = Paint.Style.FILL
            if (st.lines) { fp.color = if (dk) 0xFF3A3A3A.toInt() else 0xFFD4D4D4.toInt(); fp.strokeWidth = 1f
                for (c in c0..c1 + 1) { val x = ox + colX[c.coerceAtMost(nCols)]; n.drawLine(x, hdrH, x, H, fp) }
                for (r in r0..r1 + 1) { val y = oy + r * rowH; n.drawLine(hdrW, y, W, y, fp) } }
            tp.textSize = (13f * d * z).coerceIn(6f * d, 40f * d)
            val base = tp.textSize * .35f
            val ah = 14f * d * z
            for (tb in sh.tables) {
                if (tb.r1 > r1 || tb.r2 < r0 || tb.c1 > c1 || tb.c2 < c0) continue
                val x0 = ox + colX[tb.c1]; val x1 = ox + colX[(tb.c2 + 1).coerceAtMost(nCols)]
                for (r in maxOf(tb.r1, r0)..minOf(tb.r2, r1)) { val col = tb.rowFill(r); if (col != 0) { fp.color = col; n.drawRect(x0, oy + r * rowH, x1, oy + (r + 1) * rowH, fp) } }
                fp.color = tb.look.line; fp.style = Paint.Style.STROKE; fp.strokeWidth = 1f * d
                if (tb.look.grid) for (r in maxOf(tb.r1, r0)..minOf(tb.r2, r1) + 1) { val y = oy + r * rowH; n.drawLine(x0, y, x1, y, fp) }
                n.drawRect(x0, oy + tb.r1 * rowH, x1, oy + (tb.r2 + 1) * rowH, fp); fp.style = Paint.Style.FILL
            }
            fun drawCell(x: XCell?, x0: Float, y0: Float, x1: Float, y1: Float, r: Int, c: Int, mergedEndC: Int) {
                var s = book.style(x?.st ?: 0)
                var x1 = x1
                val tb = if (sh.tables.isEmpty()) null else sh.tables.firstOrNull { r in it.r1..it.r2 && c in it.c1..it.c2 }
                val hd = tb != null && tb.header && r == tb.r1
                if (hd && tb!!.filter) x1 -= ah + 2f * d
                if (tb != null && (hd || (tb.firstCol && c == tb.c1)) && !s.bold) s = s.copy(bold = true)
                if (s.fill != 0) { fp.color = s.fill; n.drawRect(x0, y0, x1, y1, fp) }
                if (s.bm > 0) {
                    fp.color = 0xFF000000.toInt(); fp.style = Paint.Style.STROKE; fp.strokeWidth = 1.2f * d
                    if ((s.bm and 1) != 0) n.drawLine(x0, y0, x0, y1, fp); if ((s.bm and 2) != 0) n.drawLine(x1, y0, x1, y1, fp)
                    if ((s.bm and 4) != 0) n.drawLine(x0, y0, x1, y0, fp); if ((s.bm and 8) != 0) n.drawLine(x0, y1, x1, y1, fp)
                    fp.style = Paint.Style.FILL
                }
                if (x == null) return
                val (txt, num) = xShow(book, ev, x, r, c)
                if (txt.isEmpty()) return
                tp.typeface = faces[(if (s.bold) 1 else 0) + (if (s.italic) 2 else 0)]; tp.color = if (s.color != 0) (if (dk && s.fill == 0 && Color(s.color).luminance() < .3f) OfficePrefs.pageFg else s.color) else if (hd && tb!!.look.head != 0) tb.look.headInk else if (dk && s.fill == 0) OfficePrefs.pageFg else Color.Black.toArgb()
                tp.textSize = (13f * d * z * ((s.fsz.toFloatOrNull() ?: 11f) / 11f)).coerceIn(6f * d, 90f * d); tp.isUnderlineText = s.underline; tp.isStrikeThruText = s.strike
                val base = tp.textSize * .35f
                val w = tp.measureText(txt); val pad = 3f * d
                val al = s.align.ifEmpty { if (num) "right" else "left" }
                if (s.wrap && !num) {
                    val maxW = (x1 - x0 - 2 * pad).coerceAtLeast(10f); val lines = ArrayList<String>()
                    for (para in txt.split('\n')) { var cur = ""; for (wd in para.split(' ')) { val tt = if (cur.isEmpty()) wd else "$cur $wd"; if (cur.isNotEmpty() && tp.measureText(tt) > maxW) { lines.add(cur); cur = wd } else cur = tt }; lines.add(cur) }
                    val lh = tp.textSize * 1.2f
                    n.save(); n.clipRect(x0, y0, x1, y1)
                    lines.forEachIndexed { i, ln -> val lw = tp.measureText(ln); n.drawText(ln, when (al) { "center", "centerContinuous" -> (x0 + x1 - lw) / 2; "right" -> x1 - lw - pad; else -> x0 + pad }, y0 + lh * (i + .85f), tp) }
                    n.restore(); return
                }
                var ex = x1
                if (al == "left" && mergedEndC < 0) { var cc = c + 1; while (ex - x0 < w + 2 * pad && cc < nCols && sh.cells[xKey(r, cc)] == null) { ex = ox + colX[cc + 1]; cc++ } }
                val tx = when (al) { "center", "centerContinuous" -> (x0 + x1 - w) / 2; "right" -> x1 - w - pad; else -> x0 + pad + s.indent * 8f * d * z }
                val clip = w + 2 * pad > ex - x0 || tx < x0
                if (clip) { n.save(); n.clipRect(x0, y0, ex, y1) }
                n.drawText(txt, tx.coerceAtLeast(x0 + 1f), when (s.valign) { "top" -> y0 + tp.textSize * .95f; "bottom" -> y1 - tp.textSize * .2f; else -> (y0 + y1) / 2 + base }, tp)
                if (clip) n.restore()
            }
            for (r in r0..r1) for (c in c0..c1) {
                val k = xKey(r, c)
                if (k in covered) continue
                val x = sh.cells[k] ?: continue
                drawCell(x, ox + colX[c], oy + r * rowH, ox + colX[c + 1], oy + (r + 1) * rowH, r, c, -1)
            }
            for (tb in sh.tables) {
                if (!tb.filter || tb.r1 < r0 || tb.r1 > r1) continue
                val y0 = oy + tb.r1 * rowH; val a = minOf(ah, rowH - 2f * d)
                for (c in maxOf(tb.c1, c0)..minOf(tb.c2, c1)) {
                    val bx = ox + colX[c + 1] - a - d; val by = y0 + (rowH - a) / 2
                    fp.color = 0xFFF2F2F2.toInt(); n.drawRect(bx, by, bx + a, by + a, fp)
                    fp.color = 0xFF404040.toInt(); val path = android.graphics.Path(); path.moveTo(bx + a * .27f, by + a * .4f); path.lineTo(bx + a * .73f, by + a * .4f); path.lineTo(bx + a * .5f, by + a * .66f); path.close(); n.drawPath(path, fp)
                }
            }
            for (m in sh.merges) {
                if (m[0] > r1 || m[2] < r0 || m[1] > c1 || m[3] < c0) continue
                val x0 = ox + colX[m[1].coerceAtMost(nCols)]; val x1 = ox + colX[(m[3] + 1).coerceAtMost(nCols)]; val y0 = oy + m[0] * rowH; val y1 = oy + (m[2] + 1) * rowH
                fp.color = Color.White.toArgb(); n.drawRect(x0, y0, x1, y1, fp)
                drawCell(sh.cells[xKey(m[0], m[1])], x0, y0, x1, y1, m[0], m[1], m[3])
                fp.color = 0xFFD4D4D4.toInt(); fp.style = Paint.Style.STROKE; n.drawRect(x0, y0, x1, y1, fp); fp.style = Paint.Style.FILL
            }
            for (pc in sh.pics) {
                if (pc.deleted) continue
                val rc = picRect(pc); val l = ox + rc[0]; val tt = oy + rc[1]; val rr = ox + rc[2]; val bb = oy + rc[3]
                if (rr < hdrW || l > W || bb < hdrH || tt > H) continue
                val bm = pc.bmp
                if (bm != null) n.drawBitmap(bm, null, android.graphics.RectF(l, tt, rr, bb), pp) else { fp.color = 0xFFDDDDDD.toInt(); n.drawRect(l, tt, rr, bb, fp) }
                if (pc === st.pic) {
                    fp.color = acc; fp.style = Paint.Style.STROKE; fp.strokeWidth = 2f * d; n.drawRect(l, tt, rr, bb, fp); fp.style = Paint.Style.FILL
                    n.drawRect(rr - 7f * d, bb - 7f * d, rr + 7f * d, bb + 7f * d, fp)
                }
            }
            // selection
            if (!ro) {
            val sx0 = ox + colX[st.c1.coerceAtMost(nCols)]; val sx1 = ox + colX[(st.cb + 1).coerceAtMost(nCols)]; val sy0 = oy + st.r1 * rowH; val sy1 = oy + (st.rb + 1) * rowH
            fp.color = acc; fp.alpha = 40; n.drawRect(sx0, sy0, sx1, sy1, fp)
            fp.alpha = 255; fp.style = Paint.Style.STROKE; fp.strokeWidth = 2f * d; n.drawRect(sx0, sy0, sx1, sy1, fp); fp.style = Paint.Style.FILL
            n.drawRect(sx1 - 5f * d, sy1 - 5f * d, sx1 + 5f * d, sy1 + 5f * d, fp)
            }
            n.restore()
            // headers
            if (!ro) {
            tp.textSize = 11f * d; tp.isUnderlineText = false; tp.typeface = Typeface.DEFAULT; tp.textAlign = Paint.Align.CENTER
            val hb = if (m365) 0xFF3A3A3A.toInt() else pal.ribbonBg.toArgb()
            n.save(); n.clipRect(hdrW, 0f, W, hdrH); fp.color = hb; n.drawRect(hdrW, 0f, W, hdrH, fp)
            for (c in c0..c1) {
                val x0 = ox + colX[c]; val x1 = ox + colX[c + 1]; val sel = c in st.c1..st.cb
                if (sel) { fp.color = acc; fp.alpha = 70; n.drawRect(x0, 0f, x1, hdrH, fp); fp.alpha = 255 }
                fp.color = line; n.drawLine(x1, 0f, x1, hdrH, fp)
                tp.color = ink; tp.isFakeBoldText = sel; n.drawText(xColName(c), (x0 + x1) / 2, hdrH / 2 + tp.textSize * .35f, tp)
            }
            n.restore()
            n.save(); n.clipRect(0f, hdrH, hdrW, H); fp.color = hb; n.drawRect(0f, hdrH, hdrW, H, fp)
            for (r in r0..r1) {
                val y0 = oy + r * rowH; val y1 = y0 + rowH; val sel = r in st.r1..st.rb
                if (sel) { fp.color = acc; fp.alpha = 70; n.drawRect(0f, y0, hdrW, y1, fp); fp.alpha = 255 }
                fp.color = line; n.drawLine(0f, y1, hdrW, y1, fp)
                tp.color = ink; tp.isFakeBoldText = sel; n.drawText((r + 1).toString(), hdrW / 2, (y0 + y1) / 2 + tp.textSize * .35f, tp)
            }
            n.restore()
            tp.isFakeBoldText = false; tp.textAlign = Paint.Align.LEFT
            fp.color = hb; n.drawRect(0f, 0f, hdrW, hdrH, fp); fp.color = line; n.drawLine(hdrW, 0f, hdrW, H, fp); n.drawLine(0f, hdrH, W, hdrH, fp)
            }
        }
    }
}
