package com.umutk.pulse.extras.paint

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as ACanvas
import android.graphics.Paint as APaint
import android.graphics.RectF
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.umutk.pulse.OfKind
import com.umutk.pulse.OfPal
import com.umutk.pulse.ThemedMenu
import com.umutk.pulse.ThemedMenuItem
import com.umutk.pulse.ofPal
import com.umutk.pulse.ofResolveId
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.T
import com.umutk.pulse.frame
import com.umutk.pulse.tr
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.min

/** The drawing tools, in the order of the tool box. */
enum class PaintTool { PENCIL, BRUSH, ERASER, FILL, PICK, LINE, RECT, ELLIPSE, SPRAY, WATERCOLOR }

/** The classic 28 colours of Paint: two rows of 14. */
private val PALETTE = listOf(
    0x000000, 0x808080, 0x800000, 0x808000, 0x008000, 0x008080, 0x000080, 0x800080, 0x808040, 0x004040, 0x0080FF, 0x004080, 0x8000FF, 0x804000,
    0xFFFFFF, 0xC0C0C0, 0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFFFF80, 0x00FF80, 0x80FFFF, 0x8080FF, 0xFF0080, 0xFF8040,
).map { 0xFF000000.toInt() or it }

private const val W = 900
private const val H = 1200
private const val UNDO_MAX = 20

/** The picture, the undo steps and the pen. Every change to the bitmap bumps [rev] so the canvas draws it again. */
private class PaintState {
    var bmp: Bitmap = blank()
    private val undoStack = ArrayDeque<Bitmap>()
    private val redoStack = ArrayDeque<Bitmap>()
    private val rnd = java.util.Random()
    var rev by mutableIntStateOf(0)
    var tool by mutableStateOf(PaintTool.PENCIL)
    var color by mutableIntStateOf(PALETTE[0])
    var size by mutableFloatStateOf(6f)
    /** Start and end of the shape being dragged, in picture pixels. */
    var a by mutableStateOf<Offset?>(null)
    var b by mutableStateOf<Offset?>(null)
    private var last: Offset? = null

    private fun blank() = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE) }

    fun push() {
        undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false))
        if (undoStack.size > UNDO_MAX) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() { if (undoStack.isNotEmpty()) { redoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false)); bmp = undoStack.removeLast().copy(Bitmap.Config.ARGB_8888, true); rev++ } }
    fun redo() { if (redoStack.isNotEmpty()) { undoStack.addLast(bmp.copy(Bitmap.Config.ARGB_8888, false)); bmp = redoStack.removeLast().copy(Bitmap.Config.ARGB_8888, true); rev++ } }

    /** The spray: a cloud of small dots around the point, as many as the size asks for. */
    private fun spray(p: Offset) {
        val c = ACanvas(bmp); val paint = APaint().apply { isAntiAlias = true; this.color = this@PaintState.color; style = APaint.Style.FILL }
        val r = max(4f, size * 2f)
        repeat((r * 1.6f).toInt()) {
            val a = rnd.nextDouble() * Math.PI * 2; val d = Math.sqrt(rnd.nextDouble()) * r
            c.drawCircle(p.x + (Math.cos(a) * d).toFloat(), p.y + (Math.sin(a) * d).toFloat(), 1.2f, paint)
        }
    }

    /** The colour of the sheet: New starts from it, and [cyclePaper] swaps it on the picture (white, black, cream, light blue). */
    var paper by mutableIntStateOf(android.graphics.Color.WHITE)

    fun clear() { push(); bmp.eraseColor(paper); rev++ }

    fun cyclePaper() {
        val list = listOf(android.graphics.Color.WHITE, android.graphics.Color.BLACK, 0xFFF5EBD0.toInt(), 0xFFDCEBFA.toInt())
        val next = list[(list.indexOf(paper) + 1) % list.size]
        push()
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        for (i in px.indices) if (px[i] == paper) px[i] = next
        bmp.setPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        paper = next; rev++
    }

    /** Puts a picture on a new white sheet, shrunk to fit. */
    fun load(src: Bitmap) {
        push()
        val sheet = blank()
        val s = min(W / src.width.toFloat(), H / src.height.toFloat()).coerceAtMost(1f)
        val w = (src.width * s).toInt().coerceAtLeast(1); val h = (src.height * s).toInt().coerceAtLeast(1)
        ACanvas(sheet).drawBitmap(Bitmap.createScaledBitmap(src, w, h, true), 0f, 0f, null)
        bmp = sheet; rev++
    }

    private fun pen(): APaint = APaint().apply {
        isAntiAlias = tool != PaintTool.PENCIL
        style = APaint.Style.STROKE; strokeCap = APaint.Cap.ROUND; strokeJoin = APaint.Join.ROUND
        // the watercolour: wide, see-through strokes that pile up into deeper colour where they cross
        strokeWidth = if (tool == PaintTool.PENCIL) 2f else if (tool == PaintTool.WATERCOLOR) max(6f, size * 2.2f) else max(1f, size)
        this.color = if (tool == PaintTool.ERASER) android.graphics.Color.WHITE else this@PaintState.color
        if (tool == PaintTool.WATERCOLOR) alpha = 38
    }

    fun tap(p: Offset) {
        val x = p.x.toInt(); val y = p.y.toInt()
        if (x !in 0 until W || y !in 0 until H) return
        when (tool) {
            PaintTool.FILL -> { push(); floodFill(bmp, x, y, color); rev++ }
            PaintTool.PICK -> color = bmp.getPixel(x, y) or (0xFF shl 24)
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER, PaintTool.WATERCOLOR -> { push(); ACanvas(bmp).drawPoint(p.x, p.y, pen().apply { style = APaint.Style.FILL_AND_STROKE }); rev++ }
            PaintTool.SPRAY -> { push(); spray(p); rev++ }
            else -> Unit
        }
    }

    fun begin(p: Offset) {
        when (tool) {
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER, PaintTool.WATERCOLOR -> { push(); last = p; ACanvas(bmp).drawPoint(p.x, p.y, pen().apply { style = APaint.Style.FILL_AND_STROKE }); rev++ }
            PaintTool.SPRAY -> { push(); last = p; spray(p); rev++ }
            PaintTool.LINE, PaintTool.RECT, PaintTool.ELLIPSE -> { push(); a = p; b = p }
            else -> Unit
        }
    }

    fun move(p: Offset) {
        when (tool) {
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER, PaintTool.WATERCOLOR -> { last?.let { ACanvas(bmp).drawLine(it.x, it.y, p.x, p.y, pen()) }; last = p; rev++ }
            PaintTool.SPRAY -> { spray(p); last = p; rev++ }
            PaintTool.LINE, PaintTool.RECT, PaintTool.ELLIPSE -> b = p
            else -> Unit
        }
    }

    fun end() {
        val s = a; val e = b
        if (s != null && e != null) {
            val c = ACanvas(bmp); val p = pen()
            when (tool) {
                PaintTool.LINE -> c.drawLine(s.x, s.y, e.x, e.y, p)
                PaintTool.RECT -> c.drawRect(RectF(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y)), p)
                PaintTool.ELLIPSE -> c.drawOval(RectF(min(s.x, e.x), min(s.y, e.y), max(s.x, e.x), max(s.y, e.y)), p)
                else -> Unit
            }
            rev++
        }
        a = null; b = null; last = null
    }
}

/** Scanline flood fill: every pixel connected to (sx, sy) with the same colour becomes [fill]. */
private fun floodFill(bmp: Bitmap, sx: Int, sy: Int, fill: Int) {
    val w = bmp.width; val h = bmp.height
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val target = px[sy * w + sx]
    if (target == fill) return
    val queue = ArrayDeque<Int>()
    queue.add(sy * w + sx)
    while (queue.isNotEmpty()) {
        val p = queue.removeLast()
        val x = p % w; val y = p / w
        if (px[p] != target) continue
        var l = x; while (l > 0 && px[y * w + l - 1] == target) l--
        var r = x; while (r < w - 1 && px[y * w + r + 1] == target) r++
        for (i in l..r) px[y * w + i] = fill
        for (ny in intArrayOf(y - 1, y + 1)) {
            if (ny !in 0 until h) continue
            var i = l
            while (i <= r) {
                if (px[ny * w + i] == target) { queue.add(ny * w + i); while (i <= r && px[ny * w + i] == target) i++ } else i++
            }
        }
    }
    bmp.setPixels(px, 0, w, 0, 0, w, h)
}

/** What the menus and buttons of every look do. */
private class PaintActions(val new: () -> Unit, val open: () -> Unit, val save: () -> Unit, val undo: () -> Unit, val exit: () -> Unit)

private val CLASSIC_BASES = setOf("95", "2003")
private val WINDOWS_BASES = setOf("95", "2003", "2013", "2017", "365")

/** Paint. The Windows looks get a real Paint window (95 and XP: title bar, menu bar, tool box, colour box, status bar; 7, 10 and 11: File button, tabs and a ribbon); the other looks a plain tool strip. */
@Composable
fun PaintHub(back: () -> Unit) {
    val ctx = LocalContext.current
    val st = remember { PaintState() }
    BackHandler { back() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.let { st.load(it) }
        }.onFailure { Toast.makeText(ctx, tr("Could not open it", "Açılamadı"), Toast.LENGTH_SHORT).show() }
    }
    fun save() {
        val name = "Paint-" + System.currentTimeMillis() + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Paint")
        }
        val ok = runCatching {
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no uri")
            ctx.contentResolver.openOutputStream(uri)?.use { st.bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }.isSuccess
        Toast.makeText(ctx, if (ok) "Pictures/Paint/$name" else tr("Could not save", "Kaydedilemedi"), Toast.LENGTH_SHORT).show()
    }
    val act = PaintActions({ st.clear() }, { pick.launch("image/*") }, { save() }, { st.undo() }, back)
    val base = ofResolveId("app").removeSuffix("d")
    if (base in WINDOWS_BASES) WindowsPaint(st, base, act) else PlainPaint(st, act)
}

@Composable
private fun PlainPaint(st: PaintState, act: PaintActions) {
    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { act.exit() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("Paint", color = T.text, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
            ExtraLookIcon()
            Box(Modifier.size(44.dp).clickable { act.undo() }, contentAlignment = Alignment.Center) { PIcon(if (T.id == "win11") "paint_undo" else "undo", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { st.redo() }, contentAlignment = Alignment.Center) { PIcon("redo", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { act.new() }, contentAlignment = Alignment.Center) { PIcon(if (T.id == "win11") "paint_new" else "newfile", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { act.open() }, contentAlignment = Alignment.Center) { PIcon(if (T.id == "win11") "paint_open" else "folder", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { act.save() }, contentAlignment = Alignment.Center) { PIcon(if (T.id == "win11") "paint_save" else "save", T.text, 22.dp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PaintTool.entries.forEach { t -> ToolButton(st, t, 42.dp) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${st.size.toInt()}", color = T.sub, fontSize = 13.sp, modifier = Modifier.width(28.dp))
            PSlider(st.size, { st.size = it }, 1f..40f, modifier = Modifier.weight(1f)) { }
        }
        SheetView(st, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Color(st.color)).frame(T))
            Spacer(Modifier.width(8.dp))
            PaletteGrid(st, Modifier.weight(1f), 20.dp)
        }
    }
}

@Composable
private fun ToolButton(st: PaintState, t: PaintTool, side: androidx.compose.ui.unit.Dp) {
    val on = st.tool == t
    Box(
        Modifier.size(side).background(if (on) Look.accent.copy(alpha = .22f) else T.panel).frame(T, raised = !on).clickable { st.tool = t },
        contentAlignment = Alignment.Center,
    ) { ToolGlyph(t, if (on) Look.accent else T.text) }
}

/** The 28 classic colours in two rows; a tap picks the colour. */
@Composable
private fun PaletteGrid(st: PaintState, modifier: Modifier, cell: androidx.compose.ui.unit.Dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PALETTE.chunked(14).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEach { c -> Box(Modifier.weight(1f).height(cell).background(Color(c)).frame(T).clickable { st.color = c }) }
            }
        }
    }
}

/** A menu bar word (File, Edit, ...) with its drop-down. */
@Composable
private fun MenuWord(label: String, ink: Color, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(label, color = ink, fontSize = 13.sp, modifier = Modifier.clickable { open = true }.padding(horizontal = 9.dp, vertical = 5.dp))
        ThemedMenu(open, { open = false }) { items.forEach { (l, f) -> ThemedMenuItem(l, { open = false; f() }) } }
    }
}

private val WIDTHS = listOf(1f, 2f, 4f, 8f, 16f, 32f)

/** The Paint window of the Windows looks. */
@Composable
private fun WindowsPaint(st: PaintState, base: String, act: PaintActions) {
    val pal = ofPal("app", OfKind.WORD)
    val classic = base in CLASSIC_BASES
    val ctx = LocalContext.current
    var tab by remember { mutableStateOf("Home") }
    val fileItems = listOf(tr("New", "Yeni") to act.new, tr("Open…", "Aç…") to act.open, tr("Save", "Kaydet") to act.save, tr("Paper colour", "Kâğıt rengi") to { st.cyclePaper() }, tr("Exit", "Çıkış") to act.exit)
    val editItems = listOf(tr("Undo", "Geri al") to act.undo, tr("Redo", "Yinele") to { st.redo() }, tr("Clear Image", "Resmi temizle") to act.new)
    val widthItems = WIDTHS.map { w -> "${w.toInt()} px" to { st.size = w } }
    val colorItems = listOf("Black" to 0, "White" to 14, "Red" to 16, "Yellow" to 17, "Green" to 18, "Blue" to 20).map { (n, i) -> n to { st.color = PALETTE[i] } }
    val status = if (classic) tr("For Help, click Help Topics on the Help Menu.", "Yardım için Yardım menüsünden Yardım Konuları'na tıklayın.") else "${W} × ${H} px"
    Column(Modifier.fillMaxSize().background(if (classic) pal.ribbonBg else pal.canvas)) {
        // title bar
        val band = when (base) {
            "95" -> Brush.horizontalGradient(listOf(Color(0xFF000080), Color(0xFF1084D0)))
            "2003" -> Brush.horizontalGradient(listOf(Color(0xFF0058EE), Color(0xFF3A93FF)))
            else -> Brush.horizontalGradient(listOf(pal.titleBg, pal.titleBg))
        }
        Row(Modifier.fillMaxWidth().height(if (classic) 28.dp else 36.dp).background(band).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("palette", pal.titleInk, 18.dp)
            Spacer(Modifier.width(6.dp))
            Text(tr("untitled - Paint", "adsız - Paint"), color = pal.titleInk, fontSize = 13.sp, fontWeight = if (classic) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
            ExtraLookIcon(pal.titleInk)
            Box(
                Modifier.size(22.dp).then(if (base == "95") Modifier.background(Color(0xFFC0C0C0)).frame(T, raised = true) else if (base == "2003") Modifier.background(Color(0xFFD9543C)) else Modifier)
                    .clickable { act.exit() },
                contentAlignment = Alignment.Center,
            ) { PIcon("close", if (base == "95") Color.Black else pal.titleInk, 14.dp) }
        }
        if (classic) {
            // menu bar: File Edit View Image Colors Help
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).padding(horizontal = 2.dp)) {
                MenuWord(tr("File", "Dosya"), pal.ink, fileItems)
                MenuWord(tr("Edit", "Düzen"), pal.ink, editItems)
                MenuWord(tr("View", "Görünüm"), pal.ink, widthItems)
                MenuWord(tr("Image", "Resim"), pal.ink, listOf(tr("Clear Image", "Resmi temizle") to act.new))
                MenuWord(tr("Colors", "Renkler"), pal.ink, colorItems)
                MenuWord(tr("Help", "Yardım"), pal.ink, listOf(tr("About Paint", "Paint hakkında") to { Toast.makeText(ctx, "Paint — Pulse", Toast.LENGTH_SHORT).show() }))
            }
        } else {
            // File button and the tabs
            Row(Modifier.fillMaxWidth().background(pal.tabBg), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.background(pal.accent)) { MenuWord(tr("File", "Dosya"), Color.White, fileItems) }
                listOf("Home" to tr("Home", "Giriş"), "View" to tr("View", "Görünüm")).forEach { (id, label) ->
                    val sel = tab == id
                    Text(label, color = if (sel) pal.tabSelInk else pal.tabInk, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.clickable { tab = id }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            // the ribbon
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                if (tab == "Home") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row { PaintTool.entries.take(5).forEach { ToolButton(st, it, 34.dp) } }
                        Text(tr("Tools", "Araçlar"), color = pal.ink, fontSize = 10.sp)
                    }
                    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(52.dp).background(pal.line))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row { PaintTool.entries.drop(5).forEach { ToolButton(st, it, 34.dp) } }
                        Text(tr("Shapes", "Şekiller"), color = pal.ink, fontSize = 10.sp)
                    }
                    Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(52.dp).background(pal.line))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(34.dp).background(Color(st.color)).frame(T))
                            Spacer(Modifier.width(6.dp))
                            PaletteGrid(st, Modifier.width(260.dp), 17.dp)
                        }
                        Text(tr("Colors", "Renkler"), color = pal.ink, fontSize = 10.sp)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row { WIDTHS.forEach { w ->
                            Box(Modifier.padding(horizontal = 2.dp).size(width = 44.dp, height = 34.dp).background(if (st.size == w) pal.accent.copy(alpha = .22f) else Color.Transparent).clickable { st.size = w }, contentAlignment = Alignment.Center) {
                                Text("${w.toInt()}", color = pal.ink, fontSize = 12.sp)
                            }
                        } }
                        Text(tr("Size", "Boyut"), color = pal.ink, fontSize = 10.sp)
                    }
                }
            }
        }
        // work area: the classic window has its tool box on the left and a grey workspace
        Row(Modifier.weight(1f).fillMaxWidth()) {
            if (classic) {
                Column(Modifier.width(62.dp).fillMaxHeight().background(pal.ribbonBg).padding(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PaintTool.entries.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { pair.forEach { ToolButton(st, it, 26.dp) } } }
                    Spacer(Modifier.height(6.dp))
                    // line width, as the classic tool options box
                    Column(Modifier.fillMaxWidth().background(pal.paper).frame(T), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        WIDTHS.take(5).forEach { w ->
                            Box(Modifier.fillMaxWidth().height(10.dp).background(if (st.size == w) pal.accent.copy(alpha = .35f) else Color.Transparent).clickable { st.size = w }, contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxWidth(.8f).height(max(1, (w / 4f).toInt()).dp).background(Color.Black))
                            }
                        }
                    }
                }
            }
            SheetView(st, Modifier.weight(1f).fillMaxHeight().padding(if (classic) 3.dp else 8.dp))
        }
        if (classic) {
            // the colour box: the current colour, then the palette
            Row(Modifier.fillMaxWidth().background(pal.ribbonBg).padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(Color(st.color)).frame(T))
                Spacer(Modifier.width(6.dp))
                PaletteGrid(st, Modifier.weight(1f), 18.dp)
            }
        }
        // status bar
        Row(Modifier.fillMaxWidth().background(pal.statusBg).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(status, color = pal.statusInk, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            if (classic) Text("${W} × ${H}", color = pal.statusInk, fontSize = 12.sp)
        }
    }
}

/** The sheet, fitted into the space and centred; touches are turned into picture pixels. */
@Composable
private fun SheetView(st: PaintState, modifier: Modifier) {
    var box by remember { mutableStateOf(IntSize.Zero) }
    fun scale() = min(box.width / W.toFloat(), box.height / H.toFloat())
    fun toPic(o: Offset): Offset {
        val s = scale()
        return Offset((o.x - (box.width - W * s) / 2f) / s, (o.y - (box.height - H * s) / 2f) / s)
    }
    Canvas(
        modifier.background(T.panel).frame(T)
            .pointerInput(Unit) { detectTapGestures(onTap = { st.tap(toPic(it)) }) }
            .pointerInput(st.tool) {
                detectDragGestures(
                    onDragStart = { st.begin(toPic(it)) },
                    onDrag = { c, _ -> c.consume(); st.move(toPic(c.position)) },
                    onDragEnd = { st.end() }, onDragCancel = { st.end() },
                )
            }
            .onSizeChanged { box = it },
    ) {
        st.rev // read, so a change to the picture draws it again
        val s = min(size.width / W, size.height / H)
        val ox = (size.width - W * s) / 2f; val oy = (size.height - H * s) / 2f
        drawImage(st.bmp.asImageBitmap(), dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize((W * s).toInt(), (H * s).toInt()))
        // the shape being dragged
        val a = st.a; val b = st.b
        if (a != null && b != null) {
            val p0 = Offset(ox + a.x * s, oy + a.y * s); val p1 = Offset(ox + b.x * s, oy + b.y * s)
            val w = max(1f, st.size * s)
            val col = Color(st.color)
            when (st.tool) {
                PaintTool.LINE -> drawLine(col, p0, p1, w)
                PaintTool.RECT -> drawRect(col, Offset(min(p0.x, p1.x), min(p0.y, p1.y)), Size(kotlin.math.abs(p1.x - p0.x), kotlin.math.abs(p1.y - p0.y)), style = Stroke(w))
                PaintTool.ELLIPSE -> drawOval(col, Offset(min(p0.x, p1.x), min(p0.y, p1.y)), Size(kotlin.math.abs(p1.x - p0.x), kotlin.math.abs(p1.y - p0.y)), style = Stroke(w))
                else -> Unit
            }
        }
    }
}


/** One tool's glyph, drawn flat in the given colour. */
@Composable
private fun ToolGlyph(t: PaintTool, tint: Color) {
    // Windows 11: the real Fluent icon of the tool
    if (T.id == "win11") {
        val key = when (t) {
            PaintTool.PENCIL -> "paint_pencil"; PaintTool.BRUSH -> "paint_brush"; PaintTool.ERASER -> "paint_eraser"; PaintTool.FILL -> "paint_fill"
            PaintTool.PICK -> "paint_pick"; PaintTool.LINE -> "paint_line"; PaintTool.RECT -> "paint_rect"; PaintTool.ELLIPSE -> "paint_ellipse"
            PaintTool.SPRAY -> "paint_fill"; PaintTool.WATERCOLOR -> "paint_brush"
        }
        PIcon(key, tint, 22.dp)
        return
    }
    Canvas(Modifier.size(24.dp)) {
        val u = size.minDimension / 24f
        val st = Stroke(2f * u)
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        when (t) {
            PaintTool.PENCIL -> { drawLine(tint, o(5f, 19f), o(17f, 7f), 2.5f * u); drawLine(tint, o(17f, 7f), o(20f, 4f), 4f * u); drawLine(tint, o(4f, 21f), o(8f, 21f), 2f * u) }
            PaintTool.BRUSH -> { drawLine(tint, o(6f, 18f), o(16f, 8f), 4f * u); drawCircle(tint, 3f * u, o(5f, 19f)); drawLine(tint, o(16f, 8f), o(20f, 4f), 2f * u) }
            PaintTool.ERASER -> { drawRect(tint, o(5f, 9f), Size(14f * u, 8f * u), style = st); drawLine(tint, o(12f, 9f), o(12f, 17f), 2f * u) }
            PaintTool.FILL -> { drawRect(tint, o(5f, 9f), Size(10f * u, 9f * u), style = st); drawLine(tint, o(15f, 10f), o(19f, 14f), 2f * u); drawCircle(tint, 2f * u, o(20f, 18f)) }
            PaintTool.PICK -> { drawLine(tint, o(6f, 18f), o(15f, 9f), 2.5f * u); drawCircle(tint, 3f * u, o(17f, 7f), style = st) }
            PaintTool.LINE -> drawLine(tint, o(5f, 19f), o(19f, 5f), 2f * u)
            PaintTool.RECT -> drawRect(tint, o(4f, 6f), Size(16f * u, 12f * u), style = st)
            PaintTool.ELLIPSE -> drawOval(tint, o(4f, 6f), Size(16f * u, 12f * u), style = st)
            PaintTool.SPRAY -> { drawRect(tint, o(4f, 12f), Size(7f * u, 9f * u), style = st); listOf(14f to 6f, 17f to 9f, 19f to 5f, 16f to 12f, 20f to 11f, 13f to 9f).forEach { (x, y) -> drawCircle(tint, 1.1f * u, o(x, y)) } }
            PaintTool.WATERCOLOR -> { drawLine(tint, o(5f, 17f), o(15f, 7f), 4f * u); drawLine(tint, o(15f, 7f), o(19f, 3f), 2f * u); drawCircle(tint, 2.2f * u, o(8f, 21f)); drawCircle(tint, 1.4f * u, o(14f, 20f)) }
        }
    }
}
