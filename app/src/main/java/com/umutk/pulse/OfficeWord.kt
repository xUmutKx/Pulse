package com.umutk.pulse

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.material3.DropdownMenu
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.IdentityHashMap

// Word viewer / editor. Model + parsing in OfficeWordModel.kt.

/** The Word window in the look chosen for it: shared chrome (tabs / menus per look) plus a zoom slider bar when [onZoom] is given. */
@Composable
fun WordChrome(theme: OfTheme, title: String, acts: List<OAct>, status: String, onBack: () -> Unit, zoom: Float = 1f, onZoom: ((Float) -> Unit)? = null, homeRibbon: (@Composable () -> Unit)? = null, content: @Composable BoxScope.() -> Unit) =
    CompositionLocalProvider(LocalOfRibbon provides (if (homeRibbon != null) mapOf("Home" to homeRibbon) else emptyMap())) {
        OfChromeBasic(theme, title, acts, status, onBack) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth(), content = content)
                if (onZoom != null) WordZoomBar(theme, zoom, onZoom)
            }
        }
    }

/** Office-style stacked Home ribbon: Edit, Font, Paragraph, Styles groups, two rows of 28dp cells each, tiny captions underneath. [f] is the screen's fmtActs list in its fixed order. */
@Composable
private fun WordHomeRibbon(t: OfTheme, rp: RP?, f: List<OAct>, ur: List<OAct>, rest: List<OAct>) {
    val pal = t.pal
    @Composable fun Cell(a: OAct, bar: Color? = null) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(3.dp)).background(if (a.on) pal.accent.copy(alpha = .22f) else Color.Transparent).alpha(if (a.enabled) 1f else .4f)
            .clickable(enabled = a.enabled, onClick = a.click), contentAlignment = Alignment.Center) {
            Icon(a.icon, a.label, Modifier.size(19.dp), pal.ink)
            if (bar != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp).width(15.dp).height(3.dp).background(bar))
        }
    }
    @Composable fun Chip(text: String, a: OAct, w: Dp, fam: FontFamily? = null) {
        Row(Modifier.height(26.dp).width(w).clip(RoundedCornerShape(3.dp)).border(1.dp, pal.line, RoundedCornerShape(3.dp)).alpha(if (a.enabled) 1f else .4f)
            .clickable(enabled = a.enabled, onClick = a.click).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = pal.ink, fontSize = 12.sp, maxLines = 1, fontFamily = fam, modifier = Modifier.weight(1f))
            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(16.dp), pal.ink)
        }
    }
    @Composable fun Group(cap: String, sep: Boolean = true, body: @Composable ColumnScope.() -> Unit) {
        if (sep) Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(66.dp).background(pal.line))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = body)
            Text(cap, color = pal.ink.copy(alpha = .6f), fontSize = 9.sp, maxLines = 1)
        }
    }
    val sz = (rp?.sz ?: 22).let { if (it % 2 == 0) "${it / 2}" else "${it / 2f}" }
    val font = rp?.font ?: "Calibri"
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Group(tr("Edit", "Düzenle"), sep = false) {
            Row { Cell(ur[0]); Cell(ur[1]) }
            Row { Cell(rest.last()) }
        }
        Group(tr("Font", "Yazı tipi")) {
            Row(verticalAlignment = Alignment.CenterVertically) { Chip(font, f[13], 104.dp); Spacer(Modifier.width(3.dp)); Chip(sz, f[0], 48.dp); Cell(f[6]); Cell(f[7]) }
            Row {
                Cell(f[3]); Cell(f[4]); Cell(f[5]); Cell(f[14])
                Cell(f[1], rp?.color?.let { c -> hexColor(c) } ?: pal.ink)
                Cell(f[2], rp?.hl?.let { hlColor(it) } ?: Color.Transparent)
                Cell(f[15])
            }
        }
        Group(tr("Paragraph", "Paragraf")) {
            Row { Cell(f[12]); Cell(f[16]); Cell(f[17]) }
            Row { Cell(f[8]); Cell(f[9]); Cell(f[10]); Cell(f[11]) }
        }
        Group(tr("Styles", "Stiller")) {
            val st = rest.dropLast(1)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { st.take(3).forEach { StyleChip(pal, it) } }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { st.drop(3).forEach { StyleChip(pal, it) } }
        }
    }
}

@Composable
private fun StyleChip(pal: OfPal, a: OAct) =
    Box(Modifier.height(26.dp).clip(RoundedCornerShape(3.dp)).border(1.dp, pal.line, RoundedCornerShape(3.dp)).alpha(if (a.enabled) 1f else .4f)
        .clickable(enabled = a.enabled, onClick = a.click).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) { Text(a.label, color = pal.ink, fontSize = 11.sp, maxLines = 1) }

@Composable
private fun WordZoomBar(t: OfTheme, zoom: Float, onZoom: (Float) -> Unit) {
    val pal = t.pal; val upd = rememberUpdatedState(onZoom)
    fun at(x: Float, w: Int) = (.5f * Math.pow(8.0, (x / w).coerceIn(0f, 1f).toDouble()).toFloat())
    Row(Modifier.fillMaxWidth().height(28.dp).background(pal.statusBg).then(if (t.base == "95") Modifier.bevel(true) else Modifier.border(1.dp, pal.line)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Icon(Icons.Outlined.Remove, null, Modifier.size(22.dp).clickable { onZoom((zoom / 1.15f).coerceIn(.5f, 4f)) }, pal.statusInk)
        Canvas(Modifier.width(140.dp).height(28.dp)
            .pointerInput(Unit) { detectTapGestures { upd.value(at(it.x, size.width)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> upd.value(at(ch.position.x, size.width)) } }) {
            val y = size.height / 2; val d = density
            drawRect(pal.statusInk.copy(alpha = .5f), Offset(6 * d, y - d), Size(size.width - 12 * d, 2 * d))
            val f = (Math.log((zoom / .5f).toDouble()) / Math.log(8.0)).toFloat().coerceIn(0f, 1f)
            val x = 6 * d + f * (size.width - 12 * d)
            if (t.classic) { drawRect(if (t.base == "95") Color(0xFFC0C0C0) else Color(0xFFECE9D8), Offset(x - 4 * d, y - 8 * d), Size(8 * d, 16 * d)); drawRect(Color(0xFF404040), Offset(x - 4 * d, y - 8 * d), Size(8 * d, 16 * d), style = androidx.compose.ui.graphics.drawscope.Stroke(d)) }
            else drawCircle(pal.accent, 7 * d, Offset(x, y))
        }
        Icon(Icons.Outlined.Add, null, Modifier.size(22.dp).clickable { onZoom((zoom * 1.15f).coerceIn(.5f, 4f)) }, pal.statusInk)
        Text("${(zoom * 100).toInt()}%", color = pal.statusInk, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp).width(36.dp))
    }
}

/** Everything the paragraph views need; rebuilt on every change. */
private class WCtx(
    val w: WDoc, val scale: Float, val t: OfTheme, val editable: Boolean, val labels: IdentityHashMap<XN, String>, val flat: WFlat, val textWpt: Float,
    val matches: Set<Int>, val cur: Int, val query: String, val sel: Int, val ed: TextFieldValue, val onEd: (TextFieldValue) -> Unit, val onSel: (Int) -> Unit,
    val tc: TCtx? = null, val menu: List<OAct> = emptyList(),
) {
    fun sub(tc: TCtx?, wpt: Float) = WCtx(w, scale, t, editable, labels, flat, wpt, matches, cur, query, sel, ed, onEd, onSel, tc, menu)
    fun view(editable: Boolean, scale: Float) = WCtx(w, scale, t, editable, labels, flat, textWpt, matches, cur, query, if (editable) sel else -1, ed, onEd, onSel, null, menu)
}

private fun hexColor(h: String) = runCatching { Color(android.graphics.Color.parseColor("#$h")) }.getOrDefault(Color.Black)
private fun hlColor(n: String) = when (n) {
    "yellow" -> Color(0xFFFFFF00); "green" -> Color(0xFF00FF00); "cyan" -> Color(0xFF00FFFF); "magenta" -> Color(0xFFFF00FF); "red" -> Color(0xFFFF0000)
    "blue" -> Color(0xFF0000FF); "lightGray" -> Color(0xFFD3D3D3); "darkGray" -> Color(0xFF808080); else -> Color(0xFFFFFF00)
}
private fun fam(n: String?): FontFamily? = when {
    n == null -> null
    n.contains("Courier", true) || n.contains("Consolas", true) || n.contains("Mono", true) -> FontFamily.Monospace
    n.contains("Times", true) || n.contains("Cambria", true) || n.contains("Georgia", true) || n.contains("Garamond", true) -> FontFamily.Serif
    else -> null
}

@Composable
private fun WBlock(c: WCtx, n: XN) {
    when (n.name) {
        "w:p" -> WParaView(c, n)
        "w:tbl" -> WTableView(c, n)
        else -> contOf(n)?.let { cont -> Column { cont.kids.forEach { WBlock(c, it) } } }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WParaView(c: WCtx, p: XN) {
    val w = c.w; val d = LocalDensity.current
    fun sp(pt: Float) = with(d) { (pt * c.scale).dp.toSp() }
    val i = c.flat.idx[p] ?: -1
    val pp = w.pp(p, c.tc); val frp = w.firstRp(p, c.tc)
    // dark pages: black paper, so auto / black text turns white (except on a light cell fill); explicit colours stay
    val dp = OfficePrefs.darkPages && !(c.tc?.filled == true && c.tc.dark != true)
    val ink = if (c.tc?.dark == true) Color.White else if (dp) OfficePrefs.pageFgC else Color.Black
    var menuOn by remember { mutableStateOf(false) }
    val size = (frp.sz ?: 22) / 2f
    val editing = c.editable && i >= 0 && i == c.sel
    val align = when (pp.jc) { "center" -> TextAlign.Center; "right", "end" -> TextAlign.End; "both", "distribute" -> TextAlign.Justify; else -> TextAlign.Start }
    val label = c.labels[p]
    val hang = if (label != null) (if (pp.hang > 0f) pp.hang else 360f) / 20f else 0f
    val start = (if (label != null) (pp.left / 20f - hang).coerceAtLeast(0f) else pp.left / 20f + pp.first / 20f).coerceAtLeast(0f)
    Row(Modifier.fillMaxWidth().padding(start = (start * c.scale).dp, top = (pp.before / 20f * c.scale).dp, bottom = (pp.after / 20f * c.scale).dp)) {
        if (label != null) Text(label, color = ink, fontSize = sp(size), modifier = Modifier.width((hang * c.scale).dp))
        Column(Modifier.weight(1f)) {
            if (editing) {
                val fr = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                BasicTextField(c.ed, c.onEd, Modifier.fillMaxWidth().focusRequester(fr).border(1.dp, c.t.pal.accent.copy(alpha = .5f)),
                    textStyle = TextStyle(color = ink, fontSize = sp(size), fontWeight = if (frp.b == true) FontWeight.Bold else null, fontStyle = if (frp.i == true) FontStyle.Italic else null, textAlign = align, fontFamily = fam(frp.font)),
                    cursorBrush = SolidColor(ink))
            } else {
                val all = StringBuilder()
                val ann = buildAnnotatedString {
                    for (r in runsOf(p)) {
                        val t = runText(r); if (t.isEmpty()) continue
                        val rp = w.effRp(p, r, c.tc)
                        val deco = when { rp.u == true && rp.strike == true -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough)); rp.u == true -> TextDecoration.Underline; rp.strike == true -> TextDecoration.LineThrough; else -> null }
                        pushStyle(SpanStyle(fontSize = sp((rp.sz ?: 22) / 2f), fontWeight = if (rp.b == true) FontWeight.Bold else null, fontStyle = if (rp.i == true) FontStyle.Italic else null,
                            textDecoration = deco, color = rp.color?.let { if (dp && (it.equals("auto", true) || it.all { ch -> ch == '0' })) Color.Unspecified else hexColor(it) } ?: Color.Unspecified, background = rp.hl?.let { hlColor(it) } ?: Color.Unspecified, fontFamily = fam(rp.font)))
                        append(t); all.append(t); pop()
                    }
                    if (c.query.isNotEmpty()) {
                        val low = all.toString().lowercase(); val q = c.query.lowercase(); var at = low.indexOf(q)
                        while (at >= 0) { addStyle(SpanStyle(background = if (i == c.cur) Color(0xFFFFA040) else Color(0xFFFFFF00)), at, at + q.length); at = low.indexOf(q, at + q.length) }
                    }
                }
                val big = maxOf(size, runsOf(p).maxOfOrNull { r -> (w.effRp(p, r, c.tc).sz ?: 22) / 2f } ?: size) // M3 default lineHeight is a fixed 24sp: double spacing for small text
                Text(if (ann.text.isEmpty()) AnnotatedString("​") else ann, color = ink, fontSize = sp(size), lineHeight = sp(big * 1.2f * pp.line), textAlign = align,
                    modifier = Modifier.fillMaxWidth().then(if (c.editable && i >= 0) Modifier.combinedClickable(onLongClick = { c.onSel(i); menuOn = true }) { c.onSel(i) } else Modifier))
            }
            if (menuOn) DropdownMenu(true, { menuOn = false }) {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    c.menu.chunked(6).forEach { rw -> Row { rw.forEach { a ->
                        Icon(a.icon, null, Modifier.size(40.dp).clickable(enabled = a.enabled) { menuOn = false; a.click() }.padding(8.dp),
                            if (a.on) c.t.pal.accent else c.t.pal.ink.copy(alpha = if (a.enabled) 1f else .35f))
                    } } }
                }
            }
            imagesOf(p).forEach { (id, wp, hp) -> WImage(c, id, wp, hp) }
            if (hasPageBreak(p) && runsOf(p).any { r -> r.kids.any { it.name == "w:br" && it.attrs["w:type"] == "page" } }) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(Color(0x33000000)))
        }
    }
}

@Composable
private fun WImage(c: WCtx, id: String, wp0: Float, hp0: Float) {
    // false = the image part is missing: draw nothing
    val bmp by produceState<Any?>(null, id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val b = c.w.imageBytes(id) ?: return@runCatching false
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(b, 0, b.size, o)
                var s = 1; while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
                BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap() ?: false
            }.getOrDefault(false)
        }
    }
    val wp = wp0.coerceAtLeast(1f); val hp = hp0.coerceAtLeast(1f)
    val wr = minOf(wp, c.textWpt.coerceAtLeast(10f)); val hr = (hp * wr / wp).coerceAtMost(3000f)
    val m = Modifier.padding(vertical = 2.dp).width((wr * c.scale).dp).height((hr * c.scale).dp)
    val b = bmp
    if (b is ImageBitmap) Image(b, null, m, contentScale = ContentScale.Fit) else if (b == null) Box(m.background(Color(0xFFEEEEEE)))
}

/** all rows at once: nested tables, tables in content controls (top level tables are drawn row by row in the lazy list) */
@Composable
private fun WTableView(c: WCtx, t: XN) {
    val tb = WTbl(c.w, t, c.textWpt * 20f) // shortcut: rebuilt on every recomposition, fine for small nested tables
    Column(Modifier.padding(vertical = 2.dp)) { for (r in 0 until minOf(tb.rows.size, 300)) WTblRow(c, tb, r) }
}

@Composable
private fun WTblRow(c: WCtx, tb: WTbl, r: Int) {
    val row = remember(tb, r) { tb.row(r) }
    val dens = LocalDensity.current.density
    val al = when (tb.jc) { "center" -> Alignment.CenterHorizontally; "right" -> Alignment.End; else -> Alignment.Start }
    val ind = if (tb.jc == "left") tb.indTw / 20f * c.scale else 0f // negative indent pulls the table into the margin
    Row(Modifier.fillMaxWidth().wrapContentWidth(al, unbounded = true).offset(x = ind.dp)
        .then(if (row.exact) Modifier.height((row.minH / 20f * c.scale).dp) else Modifier.height(IntrinsicSize.Min))) {
        if (row.before > 0f) Spacer(Modifier.width((row.before / 20f * c.scale).dp))
        for (cell in row.cells) {
            val ml = cell.mar[0] / 20f; val mr = cell.mar[1] / 20f
            val cw = cell.wTw / 20f
            val cc = c.sub(cell.t, (cw - ml - mr).coerceAtLeast(10f))
            Box(Modifier.width((cw * c.scale).dp).fillMaxHeight().heightIn(min = (row.minH / 20f * c.scale).dp)
                .then(if (row.exact) Modifier.clipToBounds() else Modifier)
                .then(cell.fill?.let { Modifier.background(hexColor(it)) } ?: Modifier)
                .drawBehind {
                    for (k in 0..3) {
                        val b = cell.b[k] ?: continue; if (b.none) continue
                        val col = b.color?.let { hexColor(it) } ?: if (OfficePrefs.darkPages) Color(0xFFBBBBBB) else Color.Black; val sw = (b.pt * c.scale * dens).coerceAtLeast(1f)
                        // drawn inside the cell so the neighbour's fill cannot paint over half of it
                        when (k) {
                            0 -> drawRect(col, Offset(0f, 0f), Size(size.width, sw))
                            1 -> drawRect(col, Offset(0f, 0f), Size(sw, size.height))
                            2 -> drawRect(col, Offset(0f, size.height - sw), Size(size.width, sw))
                            else -> drawRect(col, Offset(size.width - sw, 0f), Size(sw, size.height))
                        }
                    }
                }
                .padding(start = (ml * c.scale).dp, end = (mr * c.scale).dp, top = (cell.mar[2] / 20f * c.scale).dp, bottom = (cell.mar[3] / 20f * c.scale).dp),
                contentAlignment = when (cell.valign) { "center" -> Alignment.CenterStart; "bottom" -> Alignment.BottomStart; else -> Alignment.TopStart }) {
                if (cell.vm != 2) Column(Modifier.fillMaxWidth()) { cell.tc.kids.forEach { WBlock(cc, it) } }
            }
        }
    }
}

@Composable
private fun WRuler(t: OfTheme, scale: Float, pg: WPage) {
    val paint = remember { android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER } }
    Canvas(Modifier.width((pg.w * scale).dp).height(20.dp)) {
        val d = density; val ppt = scale * d; val h = size.height
        val margin = if (t.base == "95") Color(0xFF808080) else Color(0xFF8FAADC)
        drawRect(margin, Offset.Zero, Size(size.width, h))
        drawRect(Color.White, Offset(pg.l * ppt, 3 * d), Size((pg.w - pg.l - pg.r) * ppt, h - 6 * d))
        paint.textSize = 9 * d; paint.color = android.graphics.Color.BLACK
        drawIntoCanvas { cv0 -> val cv = cv0
            var q = 0
            while (true) {
                val x = pg.l * ppt + q * 9f * ppt
                if (x > (pg.w - pg.r) * ppt) break
                if (q % 8 == 0 && q > 0) cv.nativeCanvas.drawText("${q / 8}", x, h / 2 + 3 * d, paint)
                else if (q % 4 == 0) drawRect(Color.Black, Offset(x, h / 2 - 3 * d), Size(d, 6 * d))
                else drawRect(Color.Black, Offset(x, h / 2 - d), Size(d, 2 * d))
                q++
            }
        }
    }
}

@Composable
fun OfWordScreen(doc: OfDoc, theme: OfTheme) {
    val st = produceState<Result<WDoc>?>(null, doc) { value = withContext(Dispatchers.IO) { runCatching { WDoc.open(doc.file) } } }
    val r = st.value
    when {
        r == null -> OfSplash(theme, doc.name)
        r.isFailure -> WordChrome(theme, doc.name, listOf(OAct("Close", "File", Icons.Outlined.Close, png = "exit") { doc.close() }), "", doc.close) {
            OfErrorCard(theme, r.exceptionOrNull()?.message ?: "Word")
        }
        else -> WordBody(doc, theme, r.getOrThrow())
    }
}

@Composable
private fun WordBody(doc: OfDoc, theme: OfTheme, w: WDoc) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val editable = doc.canEdit && w.editableFmt
    var ver by remember { mutableIntStateOf(0) }
    var sel by remember { mutableIntStateOf(-1) }
    var ed by remember { mutableStateOf(TextFieldValue("")) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var findOn by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }; var cur by remember { mutableIntStateOf(-1) }
    var countDlg by remember { mutableStateOf(false) }
    var pick by remember { mutableIntStateOf(0) } // 1 size, 2 colour, 3 highlight
    var present by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val v = ver

    val flat = remember(v) { w.flatten() }
    val blocks = remember(v) { w.blocks() }
    val pg = remember(v) { w.page() }
    val labels = remember(v) {
        val m = IdentityHashMap<XN, String>(); val cnt = HashMap<String, IntArray>()
        for (p in flat.ps) {
            val pp = w.pp(p); val id = pp.numId
            if (id != null) { val a = cnt.getOrPut(id) { IntArray(10) }; val l = pp.ilvl.coerceIn(0, 8); a[l]++; for (k in l + 1..9) a[k] = 0; m[p] = w.label(id, l, a[l]) }
        }
        m
    }
    val cum = remember(v) { w.estimate(blocks, pg) }
    // top level tables go into the lazy list row by row, so a huge table is never laid out at once
    val items = remember(v) {
        val o = ArrayList<WItem>(); val st = IntArray(blocks.size)
        blocks.forEachIndexed { i, b ->
            st[i] = o.size
            val tb = if (b.name == "w:tbl") runCatching { WTbl(w, b, (pg.w - pg.l - pg.r) * 20f) }.getOrNull() else null
            if (tb == null || tb.rows.isEmpty()) o.add(WItem(i, b, null, 0)) else for (r in tb.rows.indices) o.add(WItem(i, b, tb, r))
        }
        o to st
    }
    val words = remember(v) { var n = 0; for (p in flat.ps) { var inW = false; for (ch in paraText(p)) { if (ch.isWhitespace()) inW = false else if (!inW) { inW = true; n++ } } }; n }
    val matches = remember(v, query) { if (query.isEmpty()) emptyList() else flat.ps.indices.filter { paraText(flat.ps[it]).contains(query, true) } }

    fun fresh() = w.flatten()
    fun syncEd() { val f = fresh(); ed = if (sel in f.ps.indices) TextFieldValue(paraText(f.ps[sel]), androidx.compose.ui.text.TextRange(paraText(f.ps[sel]).length)) else TextFieldValue("") }
    fun commit() {
        val f = fresh(); if (sel !in f.ps.indices || !editable) return
        if (ed.text != paraText(f.ps[sel])) { w.pushUndo(); w.setParaText(f.ps[sel], f.parents[sel], ed.text); ver++; doc.dirty = true }
    }
    fun mut(block: (XN, XN) -> Unit) {
        if (!editable) return
        commit(); val f = fresh()
        if (sel !in f.ps.indices) return
        w.pushUndo(); block(f.ps[sel], f.parents[sel]); ver++; doc.dirty = true
        if (sel >= 0) syncEd()
    }
    fun select(i: Int) { commit(); sel = i; syncEd() }
    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun gotoMatch(k: Int) {
        if (matches.isEmpty()) return
        cur = ((k % matches.size) + matches.size) % matches.size
        val top = flat.tops.getOrNull(matches[cur]) ?: 0
        scope.launch { list.animateScrollToItem(items.second.getOrElse(top) { 0 }) }
    }
    fun save(copy: Boolean) {
        commit()
        val wr = try { w.writer() } catch (e: Exception) { toast(e.message ?: "Save failed"); return }
        (if (copy) doc.saveCopy else doc.save)(wr) { msg -> if (!copy && !msg.startsWith("Save failed")) doc.dirty = false; toast(msg) }
    }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val pic = withContext(Dispatchers.IO) { runCatching { prepImage(ctx, uri) }.getOrNull() }
            if (pic == null) { toast(tr("Cannot read image", "Resim okunamadı")); return@launch }
            val wPt = minOf(pic.w * .75f, pg.w - pg.l - pg.r, 450f).coerceAtLeast(20f)
            try { mut { p, _ -> w.addImage(p, pic.bytes, pic.ext, wPt, wPt * pic.h / pic.w) } } catch (e: Exception) { toast(e.message ?: "Error") }
        }
    }
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
    fun selText(): Pair<Int, Int> = if (ed.selection.collapsed) 0 to ed.text.length else ed.selection.min to ed.selection.max
    fun clipSet(cut: Boolean) {
        val (a, b) = selText(); cm?.setPrimaryClip(android.content.ClipData.newPlainText("", ed.text.substring(a, b)))
        if (cut && editable) { ed = TextFieldValue(ed.text.removeRange(a, b), androidx.compose.ui.text.TextRange(a)); commit() }
    }
    fun paste() {
        val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() ?: return
        val a = ed.selection.min; val b = ed.selection.max
        ed = TextFieldValue(ed.text.replaceRange(a, b, t), androidx.compose.ui.text.TextRange(a + t.length)); commit()
    }
    fun undoRedo(un: Boolean) { commit(); if (if (un) w.undo() else w.redo()) { sel = -1; ed = TextFieldValue(""); ver++; doc.dirty = true } }

    val cp = flat.ps.getOrNull(sel)
    fun on(tag: String) = cp != null && w.isOn(cp, tag)
    val jc = cp?.let { w.pp(it).jc }
    val E = editable
    val fmtActs = listOf(
        OAct(tr("Font size", "Yazı boyutu"), "Home", Icons.Outlined.FormatSize, enabled = E && cp != null) { commit(); pick = 1 },
        OAct(tr("Text colour", "Yazı rengi"), "Home", Icons.Outlined.FormatColorText, enabled = E && cp != null) { commit(); pick = 2 },
        OAct(tr("Highlight", "Vurgu"), "Home", Icons.Outlined.FormatColorFill, enabled = E && cp != null) { commit(); pick = 3 },
        OAct(tr("Bold", "Kalın"), "Home", Icons.Outlined.FormatBold, on = on("w:b"), enabled = E && cp != null) { mut { p, _ -> w.toggle(p, "w:b") } },
        OAct(tr("Italic", "İtalik"), "Home", Icons.Outlined.FormatItalic, on = on("w:i"), enabled = E && cp != null) { mut { p, _ -> w.toggle(p, "w:i") } },
        OAct(tr("Underline", "Altı çizili"), "Home", Icons.Outlined.FormatUnderlined, on = on("w:u"), enabled = E && cp != null) { mut { p, _ -> w.toggle(p, "w:u") } },
        OAct(tr("Bigger", "Büyüt"), "Home", Icons.Outlined.KeyboardArrowUp, enabled = E && cp != null) { mut { p, _ -> w.changeSize(p, 2) } },
        OAct(tr("Smaller", "Küçült"), "Home", Icons.Outlined.KeyboardArrowDown, enabled = E && cp != null) { mut { p, _ -> w.changeSize(p, -2) } },
        OAct(tr("Left", "Sola"), "Home", Icons.Outlined.FormatAlignLeft, on = cp != null && (jc == null || jc == "left" || jc == "start"), enabled = E && cp != null) { mut { p, _ -> w.setJc(p, "left") } },
        OAct(tr("Center", "Ortala"), "Home", Icons.Outlined.FormatAlignCenter, on = jc == "center", enabled = E && cp != null) { mut { p, _ -> w.setJc(p, "center") } },
        OAct(tr("Right", "Sağa"), "Home", Icons.Outlined.FormatAlignRight, on = jc == "right" || jc == "end", enabled = E && cp != null) { mut { p, _ -> w.setJc(p, "right") } },
        OAct(tr("Justify", "Yasla"), "Home", Icons.Outlined.FormatAlignJustify, on = jc == "both", enabled = E && cp != null) { mut { p, _ -> w.setJc(p, "both") } },
        OAct(tr("Bullets", "Madde işaretleri"), "Home", Icons.Outlined.FormatListBulleted, on = cp != null && w.isBullet(cp), enabled = E && cp != null) { mut { p, par -> w.toggleBullet(p, par) } },
        OAct(tr("Font", "Yazı tipi"), "Home", Icons.Outlined.FontDownload, enabled = E && cp != null) { commit(); pick = 4 },
        OAct(tr("Strikethrough", "Üstü çizili"), "Home", Icons.Outlined.StrikethroughS, on = on("w:strike"), enabled = E && cp != null) { mut { p, _ -> w.toggle(p, "w:strike") } },
        OAct(tr("Clear formatting", "Biçimi temizle"), "Home", Icons.Outlined.FormatClear, enabled = E && cp != null) { mut { p, _ -> w.clearFormat(p) } },
        OAct(tr("Decrease indent", "Girintiyi azalt"), "Home", Icons.Outlined.FormatIndentDecrease, enabled = E && cp != null) { mut { p, _ -> w.indent(p, -360) } },
        OAct(tr("Increase indent", "Girintiyi artır"), "Home", Icons.Outlined.FormatIndentIncrease, enabled = E && cp != null) { mut { p, _ -> w.indent(p, 360) } },
    )
    val menuActs = listOf(
        OAct(tr("Cut", "Kes"), "Menu", Icons.Outlined.ContentCut, enabled = E && cp != null) { clipSet(true) },
        OAct(tr("Copy", "Kopyala"), "Menu", Icons.Outlined.ContentCopy, enabled = cp != null, png = "copy") { clipSet(false) },
        OAct(tr("Paste", "Yapıştır"), "Menu", Icons.Outlined.ContentPaste, enabled = E && cp != null) { paste() },
        OAct(tr("Select", "Seç"), "Menu", Icons.Outlined.SelectAll, enabled = E && cp != null) { ed = TextFieldValue(ed.text, androidx.compose.ui.text.TextRange(0, ed.text.length)) },
    ) + fmtActs
    val inTbl = E && w.fmt == "docx" && flat.parents.getOrNull(sel)?.name == "w:tc"
    val acts = listOf(
        OAct(tr("Save", "Kaydet"), "File", Icons.Outlined.Save, enabled = E && doc.dirty) { save(false) },
        OAct(tr("Save a copy", "Kopya kaydet"), "File", Icons.Outlined.ContentCopy, enabled = w.editableFmt, png = "copy") { save(true) },
        OAct(tr("Word count", "Sözcük sayısı"), "File", Icons.Outlined.FormatListNumbered) { commit(); countDlg = true },
        OAct(tr("Close", "Kapat"), "File", Icons.Outlined.Close, png = "exit") { doc.close() },
        OAct(tr("Undo", "Geri al"), "Home", Icons.Outlined.Undo, enabled = E && w.canUndo) { undoRedo(true) },
        OAct(tr("Redo", "Yinele"), "Home", Icons.Outlined.Redo, enabled = E && w.canRedo) { undoRedo(false) },
        *fmtActs.toTypedArray(),
        OAct(tr("Normal", "Normal"), "Home", Icons.Outlined.Notes, enabled = E && cp != null) { mut { p, _ -> w.setStyle(p, "Normal") } },
        OAct(tr("Title", "Başlık"), "Home", Icons.Outlined.Title, enabled = E && cp != null) { mut { p, _ -> w.setStyle(p, "Title") } },
        OAct(tr("Heading 1", "Başlık 1"), "Home", Icons.Outlined.TextFields, enabled = E && cp != null) { mut { p, _ -> w.setStyle(p, "Heading1") } },
        OAct(tr("Heading 2", "Başlık 2"), "Home", Icons.Outlined.TextFields, enabled = E && cp != null) { mut { p, _ -> w.setStyle(p, "Heading2") } },
        OAct(tr("Heading 3", "Başlık 3"), "Home", Icons.Outlined.TextFields, enabled = E && cp != null) { mut { p, _ -> w.setStyle(p, "Heading3") } },
        OAct(tr("Find", "Bul"), "Home", Icons.Outlined.Search, on = findOn, png = "search") { findOn = !findOn },
        OAct(tr("New paragraph", "Yeni paragraf"), "Insert", Icons.Outlined.Add, enabled = E && cp != null) { mut { p, par -> w.insertParaAfter(p, par); sel++ } },
        OAct(tr("Table", "Tablo"), "Insert", Icons.Outlined.TableChart, enabled = E && cp != null) { mut { p, par -> w.insertTableAfter(p, par); sel = -1 } },
        OAct(tr("Picture", "Resim"), "Insert", Icons.Outlined.Image, enabled = E && w.fmt == "docx" && cp != null) { commit(); pickImg.launch("image/*") },
        OAct(tr("Picture bigger", "Resmi büyüt"), "Insert", Icons.Outlined.ZoomIn, enabled = E && cp != null && imagesOf(cp).isNotEmpty()) { mut { p, _ -> w.scaleImages(p, 1.25f) } },
        OAct(tr("Picture smaller", "Resmi küçült"), "Insert", Icons.Outlined.ZoomOut, enabled = E && cp != null && imagesOf(cp).isNotEmpty()) { mut { p, _ -> w.scaleImages(p, .8f) } },
        OAct(tr("Page break", "Sayfa sonu"), "Insert", Icons.Outlined.InsertPageBreak, enabled = E && cp != null) { mut { p, par -> w.insertBreakAfter(p, par); sel = -1 } },
        OAct(tr("Delete paragraph", "Paragrafı sil"), "Insert", Icons.Outlined.Delete, enabled = E && cp != null, png = "delete") { mut { p, par -> w.deletePara(p, par); sel = -1 } },
        OAct(tr("Insert row", "Satır ekle"), "Table", Icons.Outlined.TableRows) { mut { p, _ -> w.tableOp(p, "rowAfter"); sel = -1 } },
        OAct(tr("Delete row", "Satırı sil"), "Table", Icons.Outlined.Delete, png = "delete") { mut { p, _ -> w.tableOp(p, "rowDel"); sel = -1 } },
        OAct(tr("Insert column", "Sütun ekle"), "Table", Icons.Outlined.ViewColumn) { mut { p, _ -> w.tableOp(p, "colAfter"); sel = -1 } },
        OAct(tr("Delete column", "Sütunu sil"), "Table", Icons.Outlined.Delete, png = "delete") { mut { p, _ -> w.tableOp(p, "colDel"); sel = -1 } },
        OAct(tr("Orientation", "Yön"), "Layout", Icons.Outlined.ScreenRotation, enabled = w.fmt == "docx" && E) { mut { _, _ -> w.toggleOrientation() } },
        OAct(tr("Narrow margins", "Dar kenarlık"), "Layout", Icons.Outlined.Straighten, enabled = w.fmt == "docx" && E) { mut { _, _ -> w.setMargins(720) } },
        OAct(tr("Normal margins", "Normal kenarlık"), "Layout", Icons.Outlined.Straighten, enabled = w.fmt == "docx" && E) { mut { _, _ -> w.setMargins(1440) } },
        OAct(tr("Wide margins", "Geniş kenarlık"), "Layout", Icons.Outlined.Straighten, enabled = w.fmt == "docx" && E) { mut { _, _ -> w.setMargins(2160) } },
        OAct(tr("Present", "Sunum"), "View", Icons.Outlined.Slideshow) { commit(); sel = -1; present = true },
        OAct(tr("Zoom in", "Yakınlaştır"), "View", Icons.Outlined.ZoomIn) { zoom = (zoom * 1.2f).coerceAtMost(4f) },
        OAct(tr("Zoom out", "Uzaklaştır"), "View", Icons.Outlined.ZoomOut) { zoom = (zoom / 1.2f).coerceAtLeast(.5f) },
        OAct("100%", "View", Icons.Outlined.Fullscreen) { zoom = 1f },
        OAct(tr("Find", "Bul"), "View", Icons.Outlined.Search, on = findOn, png = "search") { findOn = !findOn },
    ).let { if (w.fmt == "docx" && E) it else it.filter { a -> a.group != "Layout" } }.filter { inTbl || it.group != "Table" }

    val screenW = LocalConfiguration.current.screenWidthDp.toFloat()
    val pageDp = screenW * zoom; val scale = pageDp / pg.w
    val first = items.first.getOrNull(list.firstVisibleItemIndex)?.bi ?: 0
    val ph = (pg.h - pg.t - pg.b).coerceAtLeast(50f)
    val pages = Math.ceil((cum.last() / ph).toDouble()).toInt().coerceAtLeast(1)
    val pageNo = ((cum.getOrElse(first) { 0f } / ph).toInt() + 1).coerceIn(1, pages)
    val status = "${tr("Page", "Sayfa")} $pageNo ${tr("of", "/")} $pages     $words ${tr("words", "sözcük")}" + if (!E) "     ${tr("Read only", "Salt okunur")}" else if (doc.dirty && w.converted) "     ${tr("Edited \u2014 save as new file", "Düzenlendi \u2014 yeni dosya kaydet")}" else if (doc.dirty) "     *" else ""

    val ctx2 = WCtx(w, scale, theme, E, labels, flat, pg.w - pg.l - pg.r, matches.toSet(), matches.getOrNull(cur) ?: -1, if (findOn) query else "", sel, ed, { ed = it }, { select(it) }, menu = menuActs)
    if (present) WordPresent(ctx2.view(false, 1f), blocks, cum, pg, pages) { present = false }
    else WordChrome(theme, doc.name, acts, status, doc.close, zoom, { zoom = it }, homeRibbon = {
        val h = acts.filter { it.group == "Home" }
        WordHomeRibbon(theme, cp?.let { w.effRp(it, null) }, fmtActs, h.take(2), h.drop(2 + fmtActs.size))
    }) {
        Column(Modifier.fillMaxSize().imePadding()) {
            if (findOn) Row(Modifier.fillMaxWidth().background(theme.pal.ribbonBg).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                PField(query, { query = it; cur = -1 }, tr("Find", "Bul"), Modifier.weight(1f), onDone = { gotoMatch(cur + 1) })
                Text(if (matches.isEmpty()) "0" else "${cur + 1}/${matches.size}", color = theme.pal.ink, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp))
                Icon(Icons.Outlined.KeyboardArrowUp, null, Modifier.size(32.dp).clickable { gotoMatch(if (cur < 0) -1 else cur - 1) }.padding(4.dp), theme.pal.ink)
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(32.dp).clickable { gotoMatch(cur + 1) }.padding(4.dp), theme.pal.ink)
                Icon(Icons.Outlined.Close, null, Modifier.size(32.dp).clickable { findOn = false }.padding(4.dp), theme.pal.ink)
            }
            Box(Modifier.weight(1f).fillMaxWidth().ofPinch { zoom = (zoom * it).coerceIn(.5f, 4f) }) {
                Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                    Column(Modifier.width(maxOf(screenW, pageDp).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (theme.classic) WRuler(theme, scale, pg)
                        LazyColumn(Modifier.width(pageDp.dp).weight(1f).then(if (theme.base == "365" || theme.base == "2017") Modifier else Modifier.shadow(3.dp)).background(if (OfficePrefs.darkPages) OfficePrefs.pageBgC else theme.pal.paper),
                            state = list, contentPadding = PaddingValues(start = (pg.l * scale).dp, end = (pg.r * scale).dp, top = (pg.t * scale).dp, bottom = (pg.b * scale).dp)) {
                            items(items.first.size) { k -> val it0 = items.first[k]; key(v) { if (it0.tb != null) WTblRow(ctx2, it0.tb, it0.r) else WBlock(ctx2, it0.b) } }
                        }
                    }
                }
            }
        }
    }
    if (pick != 0) {
        fun doIt(f: (XN) -> Unit) { pick = 0; mut { p, _ -> f(p) } }
        val sw = Modifier.size(36.dp)
        WinAlert(onDismissRequest = { pick = 0 }, title = { Text(listOf("", tr("Font size", "Yazı boyutu"), tr("Text colour", "Yazı rengi"), tr("Highlight", "Vurgu"), tr("Font", "Yazı tipi"))[pick]) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when (pick) {
                        1 -> listOf(8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24, 26, 28, 32, 36, 40, 48, 60, 72).chunked(5).forEach { rw -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { rw.forEach { n ->
                            Box(sw.border(1.dp, theme.pal.line).clickable { doIt { p -> w.setSize(p, n * 2) } }, contentAlignment = Alignment.Center) { Text("$n", fontSize = 13.sp) } } } }
                        2 -> listOf("000000", "7F7F7F", "C00000", "FF0000", "FFC000", "FFFF00", "92D050", "00B050", "00B0F0", "0070C0", "002060", "7030A0", "FFFFFF", null).chunked(5).forEach { rw -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { rw.forEach { h ->
                            Box(sw.background(h?.let { hexColor(it) } ?: Color.White).border(1.dp, theme.pal.line).clickable { doIt { p -> w.setColor(p, h) } }, contentAlignment = Alignment.Center) { if (h == null) Text("A", fontSize = 14.sp, color = Color.Black) } } } }
                        4 -> listOf("Calibri", "Arial", "Times New Roman", "Cambria", "Courier New", "Verdana", "Georgia", "Comic Sans MS").forEach { n ->
                            Text(n, fontFamily = fam(n), fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable { doIt { p -> w.setFont(p, n) } }.padding(horizontal = 8.dp, vertical = 7.dp)) }
                        else -> listOf("yellow", "green", "cyan", "magenta", "red", "blue", "lightGray", "darkGray", null).chunked(5).forEach { rw -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { rw.forEach { h ->
                            Box(sw.background(h?.let { hlColor(it) } ?: Color.White).border(1.dp, theme.pal.line).clickable { doIt { p -> w.setHighlight(p, h) } }, contentAlignment = Alignment.Center) { if (h == null) Icon(Icons.Outlined.Close, null, tint = Color.Black) } } } }
                    }
                }
            },
            confirmButton = { TextButton({ pick = 0 }) { Text("OK") } })
    }
    if (countDlg) {
        val chars = flat.ps.sumOf { paraText(it).length }
        WinAlert(onDismissRequest = { countDlg = false }, title = { Text(tr("Word count", "Sözcük sayısı")) },
            text = { Column { Text("${tr("Pages", "Sayfa")}: $pages"); Text("${tr("Words", "Sözcük")}: $words"); Text("${tr("Characters", "Karakter")}: $chars"); Text("${tr("Paragraphs", "Paragraf")}: ${flat.ps.size}") } },
            confirmButton = { TextButton({ countDlg = false }) { Text("OK") } })
    }
}

private class WItem(val bi: Int, val b: XN, val tb: WTbl?, val r: Int)
private class Pic(val bytes: ByteArray, val ext: String, val w: Int, val h: Int)

/** gallery image -> downscaled png (with alpha) or jpeg bytes */
private fun prepImage(ctx: android.content.Context, uri: android.net.Uri): Pic? {
    val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(raw, 0, raw.size, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return null
    var s = 1; while (o.outWidth / s > 2000 || o.outHeight / s > 2000) s *= 2
    val bm = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
    val png = bm.hasAlpha(); val out = java.io.ByteArrayOutputStream()
    bm.compress(if (png) android.graphics.Bitmap.CompressFormat.PNG else android.graphics.Bitmap.CompressFormat.JPEG, 88, out)
    return Pic(out.toByteArray(), if (png) "png" else "jpeg", bm.width, bm.height)
}

/** Full screen, no chrome: one page at a time, tap right / left part to turn, back to leave. */
@Composable
private fun WordPresent(c: WCtx, blocks: List<XN>, cum: FloatArray, pg: WPage, pages: Int, onExit: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onExit)
    val ph = (pg.h - pg.t - pg.b).coerceAtLeast(50f)
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).pointerInput(pages) {
        detectTapGestures { o -> page = if (o.x < size.width / 3f) (page - 1).coerceAtLeast(0) else (page + 1).coerceAtMost(pages - 1) }
    }, contentAlignment = Alignment.Center) {
        val scale = minOf(maxWidth.value / pg.w, maxHeight.value / pg.h)
        val cc = remember(scale, c) { c.view(false, scale) }
        Column(Modifier.size((pg.w * scale).dp, (pg.h * scale).dp).background(if (OfficePrefs.darkPages) OfficePrefs.pageBgC else c.t.pal.paper).verticalScroll(rememberScrollState())
            .padding(start = (pg.l * scale).dp, end = (pg.r * scale).dp, top = (pg.t * scale).dp, bottom = (pg.b * scale).dp)) {
            blocks.indices.filter { (cum[it] / ph).toInt().coerceAtMost(pages - 1) == page }.forEach { WBlock(cc, blocks[it]) }
        }
        Text("${page + 1}/$pages", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(2.dp))
    }
}
