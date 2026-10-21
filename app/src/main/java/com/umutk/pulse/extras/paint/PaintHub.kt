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
enum class PaintTool { PENCIL, BRUSH, ERASER, FILL, PICK, LINE, RECT, ELLIPSE }

/** The classic 28 colours of Paint: two rows of 14. */
private val PALETTE = listOf(
    0x000000, 0x808080, 0x800000, 0x808000, 0x008000, 0x008080, 0x000080, 0x800080, 0x808040, 0x004040, 0x0080FF, 0x004080, 0x8000FF, 0x804000,
    0xFFFFFF, 0xC0C0C0, 0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF, 0xFFFF80, 0x00FF80, 0x80FFFF, 0x8080FF, 0xFF0080, 0xFF8040,
).map { 0xFF000000.toInt() or it }

private const val W = 900
private const val H = 1200
private const val UNDO_MAX = 10

/** The picture, the undo steps and the pen. Every change to the bitmap bumps [rev] so the canvas draws it again. */
private class PaintState {
    var bmp: Bitmap = blank()
    private val undoStack = ArrayDeque<Bitmap>()
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
    }

    fun undo() { if (undoStack.isNotEmpty()) { bmp = undoStack.removeLast().copy(Bitmap.Config.ARGB_8888, true); rev++ } }

    fun clear() { push(); bmp.eraseColor(android.graphics.Color.WHITE); rev++ }

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
        strokeWidth = if (tool == PaintTool.PENCIL) 2f else max(1f, size)
        this.color = if (tool == PaintTool.ERASER) android.graphics.Color.WHITE else this@PaintState.color
    }

    fun tap(p: Offset) {
        val x = p.x.toInt(); val y = p.y.toInt()
        if (x !in 0 until W || y !in 0 until H) return
        when (tool) {
            PaintTool.FILL -> { push(); floodFill(bmp, x, y, color); rev++ }
            PaintTool.PICK -> color = bmp.getPixel(x, y) or (0xFF shl 24)
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER -> { push(); ACanvas(bmp).drawPoint(p.x, p.y, pen().apply { style = APaint.Style.FILL_AND_STROKE }); rev++ }
            else -> Unit
        }
    }

    fun begin(p: Offset) {
        when (tool) {
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER -> { push(); last = p; ACanvas(bmp).drawPoint(p.x, p.y, pen().apply { style = APaint.Style.FILL_AND_STROKE }); rev++ }
            PaintTool.LINE, PaintTool.RECT, PaintTool.ELLIPSE -> { push(); a = p; b = p }
            else -> Unit
        }
    }

    fun move(p: Offset) {
        when (tool) {
            PaintTool.PENCIL, PaintTool.BRUSH, PaintTool.ERASER -> { last?.let { ACanvas(bmp).drawLine(it.x, it.y, p.x, p.y, pen()) }; last = p; rev++ }
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

/** Paint: a sheet to draw on, a tool box, the classic palette, undo, open and save. Drawn in the look chosen for this app. */
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
    Column(Modifier.fillMaxSize().background(T.bg)) {
        // title and file actions
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text("Paint", color = T.text, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
            ExtraLookIcon()
            Box(Modifier.size(44.dp).clickable { st.undo() }, contentAlignment = Alignment.Center) { PIcon("undo", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { st.clear() }, contentAlignment = Alignment.Center) { PIcon("newfile", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { pick.launch("image/*") }, contentAlignment = Alignment.Center) { PIcon("folder", T.text, 22.dp) }
            Box(Modifier.size(44.dp).clickable { save() }, contentAlignment = Alignment.Center) { PIcon("save", T.text, 22.dp) }
        }
        // the tool box
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PaintTool.entries.forEach { t ->
                val on = st.tool == t
                Box(
                    Modifier.size(42.dp).background(if (on) Look.accent.copy(alpha = .22f) else T.panel).frame(T, raised = !on).clickable { st.tool = t },
                    contentAlignment = Alignment.Center,
                ) { ToolGlyph(t, if (on) Look.accent else T.text) }
            }
        }
        // line width
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${st.size.toInt()}", color = T.sub, fontSize = 13.sp, modifier = Modifier.width(28.dp))
            PSlider(st.size, { st.size = it }, 1f..40f, modifier = Modifier.weight(1f)) { }
        }
        // the sheet
        SheetView(st, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
        // colours: the current one, then the 28 of the classic palette
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(Color(st.color)).frame(T))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PALETTE.chunked(14).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { c ->
                            Box(Modifier.weight(1f).height(20.dp).background(Color(c)).frame(T).clickable { st.color = c })
                        }
                    }
                }
            }
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
        }
    }
}
