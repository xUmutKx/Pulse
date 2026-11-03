package com.umutk.pulse.extras.notes

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path as APath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import android.view.MotionEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.io.FileOutputStream
import java.util.UUID

/** One pen stroke: its colour, its width in canvas pixels and its points in canvas pixels. */
/** kind 1 is the highlighter (wide and see-through); straight keeps only its first and last point, so it draws a line. */
class Pen(val color: Int, val width: Float, val pts: SnapshotStateList<Offset>, val kind: Int = 0, val straight: Boolean = false)

/** Paint's own 16 colours. */
private val PAINT_COLORS = listOf(
    0xFF000000, 0xFF808080, 0xFF800000, 0xFF808000, 0xFF008000, 0xFF008080, 0xFF000080, 0xFF800080,
    0xFFFFFFFF, 0xFFC0C0C0, 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF,
).map { it.toInt() }
/** Favourite pens: a pen kept as "colour:width index:tool", up to six, in the phone's own preferences. */
private object PenFavs {
    private fun p(c: android.content.Context) = c.getSharedPreferences("notes_pens", android.content.Context.MODE_PRIVATE)
    fun load(c: android.content.Context): List<String> = p(c).getString("list", "").orEmpty().split(";").filter { it.isNotBlank() }
    fun save(c: android.content.Context, l: List<String>) { p(c).edit().putString("list", l.take(6).joinToString(";")).apply() }
}

/** Paint's three line widths: thin, medium, thick. */
private val PAINT_WIDTHS = listOf(2.dp, 5.dp, 10.dp)
/** Paint's grey bar in the Windows looks; the other looks draw the bar in their own panel, line and text colours. */
private val winLook get() = com.umutk.pulse.T.id in setOf("win95", "xp", "win7", "win10", "win11")
private val PaintBar get() = if (winLook) Color(0xFFF0F0F0) else com.umutk.pulse.T.panel
private val PaintLine get() = if (winLook) Color(0xFF9A9A9A) else com.umutk.pulse.T.line
private val PaintInk get() = if (winLook) Color.Black else com.umutk.pulse.T.text

/** Full-screen sketch pad in Paint's style. Done saves a PNG attachment and gives its file name; Close gives null. */
@Composable
fun NSketch(onDone: (String?) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val strokes = remember { mutableStateListOf<Pen>() }
    // the page takes the look's own page colour (dark in the dark looks), so it does not flash white; the picture is saved on that same colour
    val paper = com.umutk.pulse.pageBg.let { if (it.alpha < .99f) com.umutk.pulse.T.panel.copy(alpha = 1f) else it }
    val darkPaper = paper.luminance() < .5f
    var ink by remember { mutableStateOf(if (darkPaper) PAINT_COLORS[8] else PAINT_COLORS[0]) }
    val fade = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) { fade.animateTo(1f, androidx.compose.animation.core.tween(220)) }
    var width by remember { mutableStateOf(PAINT_WIDTHS[1]) }
    // 0 pen, 1 highlighter, 2 straight line
    var tool by remember { mutableStateOf(0) }
    var favs by remember { mutableStateOf(PenFavs.load(ctx)) }
    val redoList = remember { mutableStateListOf<Pen>() }
    var fingerDraw by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var paletteAt by remember { mutableStateOf<Offset?>(null) }
    val buttonClock = remember { mutableStateOf(0L) }
    val penSeen = remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(PaintBar)) {
        // actions and line widths on the first row, the colour grid under it, like Paint's tool bar
        fun addFavourite() {
            val e = "$ink:${PAINT_WIDTHS.indexOf(width)}:$tool"
            favs = (listOf(e) + favs.filter { it != e }).take(6); PenFavs.save(ctx, favs)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            PaintTool("close") { onDone(null) }
            Spacer(Modifier.width(6.dp))
            // the tools: pen, highlighter, eraser, straight line
            listOf(0 to tr("Pen", "Kalem"), 1 to tr("Highlight", "Vurgu"), 3 to tr("Eraser", "Silgi"), 2 to tr("Line", "Çizgi")).forEach { (k, label) ->
                Text(label, color = if (tool == k) com.umutk.pulse.T.accent else PaintInk, fontSize = 13.sp, fontWeight = if (tool == k) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp).clickable2 { tool = k })
            }
            Spacer(Modifier.width(6.dp))
            // the five colours of the bar; the whole palette is under it
            listOf(PAINT_COLORS[10], PAINT_COLORS[12], PAINT_COLORS[8], PAINT_COLORS[14], PAINT_COLORS[15]).forEach { c ->
                Box(Modifier.size(28.dp).clickable2 { ink = c; if (tool == 3) tool = 0 }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (ink == c) 20.dp else 18.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                        .border(if (ink == c) 2.dp else 0.dp, PaintInk, androidx.compose.foundation.shape.CircleShape))
                }
            }
            Spacer(Modifier.width(6.dp))
            PAINT_WIDTHS.forEach { w -> PaintWidth(w, w == width) { width = w } }
            Spacer(Modifier.width(6.dp))
            PaintTool("undo") { if (strokes.isNotEmpty()) redoList.add(strokes.removeAt(strokes.lastIndex)) }
            PaintTool("redo") { if (redoList.isNotEmpty()) strokes.add(redoList.removeAt(redoList.lastIndex)) }
            Spacer(Modifier.weight(1f))
            Text(tr("Save", "Kaydet"), color = com.umutk.pulse.T.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp).clickable2 { onDone(renderSketch(ctx, strokes.toList(), size.width, size.height, paper.toArgb())) })
            Box {
                PaintTool("more") { menu = true }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(if (fingerDraw) tr("Turn off finger drawing", "Parmakla çizimi kapat") else tr("Turn on finger drawing", "Parmakla çizimi aç")) },
                        onClick = { fingerDraw = !fingerDraw; menu = false })
                    DropdownMenuItem(text = { Text(tr("Save as favourite pen", "Favori kalem olarak kaydet")) }, onClick = { addFavourite(); menu = false })
                    DropdownMenuItem(text = { Text(tr("Tool panel", "Araç paneli")) }, onClick = { paletteAt = Offset(40f, 40f); menu = false })
                    DropdownMenuItem(text = { Text(tr("Clear", "Temizle")) }, onClick = { strokes.clear(); redoList.clear(); menu = false })
                }
            }
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp)) {
            PAINT_COLORS.chunked(8).forEach { row ->
                Row { row.forEach { c -> PaintColor(c, c == ink) { ink = c } } }
            }
            if (favs.isNotEmpty()) Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                favs.forEach { f ->
                    val parts = f.split(":")
                    val fc = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val fw = PAINT_WIDTHS.getOrNull(parts.getOrNull(1)?.toIntOrNull() ?: 1) ?: PAINT_WIDTHS[1]
                    val ft = parts.getOrNull(2)?.toIntOrNull() ?: 0
                    Box(Modifier.size(30.dp).clickable2 { ink = fc; width = fw; tool = ft }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(fc)).border(1.dp, PaintLine, androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Box(
            Modifier.fillMaxSize().border(1.dp, PaintLine).background(paper)
                .onSizeChanged { size = it }
                // keyed on the colour and width, so a change applies to the next stroke
                .pointerInput(ink, width, tool, fingerDraw) {
                    // the pen draws; once a pen has been used, a finger is ignored (the palm rests on the screen). The pen's button held erases
                    awaitEachGesture {
                        val first = awaitPointerEvent(PointerEventPass.Main)
                        val c0 = first.changes.first()
                        val stylus = c0.type == PointerType.Stylus
                        if (stylus) penSeen.value = true else if (penSeen.value && !fingerDraw) return@awaitEachGesture
                        // the pen's button pressed twice quickly opens the tool panel beside the pen
                        var btnDown = stylusButton(first)
                        if (btnDown) { if (System.currentTimeMillis() - buttonClock.value < 450) paletteAt = c0.position; buttonClock.value = System.currentTimeMillis() }
                        val eraser = tool == 3 || (stylus && (((first.nativeEvent as? MotionEvent)?.buttonState ?: 0) and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0)
                        if (!eraser) redoList.clear()
                        if (!eraser) strokes.add(Pen(ink, if (tool == 1) width.toPx() * 3f else width.toPx(), mutableStateListOf(c0.position), if (tool == 1) 1 else 0, tool == 2)) else eraseAt(strokes, c0.position, 28.dp.toPx())
                        while (true) {
                            val ev = awaitPointerEvent(PointerEventPass.Main)
                            val c = ev.changes.firstOrNull { it.id == c0.id } ?: break
                            val nowBtn = stylusButton(ev)
                            if (nowBtn && !btnDown) { if (System.currentTimeMillis() - buttonClock.value < 450) paletteAt = c.position; buttonClock.value = System.currentTimeMillis() }
                            btnDown = nowBtn
                            if (!c.pressed) break
                            c.consume()
                            if (eraser) eraseAt(strokes, c.position, 28.dp.toPx()) else strokes.lastOrNull()?.let { s ->
                                // a straight line keeps only its start and its end
                                if (s.straight) { if (s.pts.size > 1) s.pts[1] = c.position else s.pts.add(c.position) } else s.pts.add(c.position)
                            }
                        }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                strokes.forEach { s ->
                    if (s.pts.size > 1) {
                        val p = Path()
                        s.pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                        if (s.kind == 1) drawPath(p, Color(s.color).copy(alpha = .35f), style = Stroke(width = s.width, cap = androidx.compose.ui.graphics.StrokeCap.Square, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                        else drawPath(p, Color(s.color), style = Stroke(width = s.width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
            }
        }
        // the tool panel: opened by pressing the pen's button twice quickly, or from the menu; a tap outside closes it
        paletteAt?.let { at ->
            ToolPanel(at, ink, width, tool,
                onInk = { ink = it }, onWidth = { width = it }, onTool = { tool = it },
                onDismiss = { paletteAt = null })
        }
        }
    }
}

/** The floating tool panel: tools, colours and widths in one place, beside the pen. */
@Composable
private fun ToolPanel(at: Offset, ink: Int, width: Dp, tool: Int, onInk: (Int) -> Unit, onWidth: (Dp) -> Unit, onTool: (Int) -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { onDismiss() } })
    Column(
        Modifier.offset { androidx.compose.ui.unit.IntOffset(at.x.toInt(), at.y.toInt()) }
            .clip(RoundedCornerShape(16.dp)).background(Color(0xE6202020)).padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(0 to tr("Pen", "Kalem"), 1 to tr("Highlight", "Vurgu"), 3 to tr("Eraser", "Silgi"), 2 to tr("Line", "Çizgi")).forEach { (k, label) ->
                Text(label, color = if (tool == k) com.umutk.pulse.T.accent else Color.White, fontSize = 13.sp, fontWeight = if (tool == k) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp).clickable2 { onTool(k) })
            }
        }
        PAINT_COLORS.chunked(8).forEach { row ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                row.forEach { c ->
                    Box(Modifier.size(30.dp).clickable2 { onInk(c); if (tool == 3) onTool(0) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(if (ink == c) 22.dp else 20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(c))
                            .border(if (ink == c) 2.dp else 0.dp, Color.White, androidx.compose.foundation.shape.CircleShape))
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PAINT_WIDTHS.forEach { w -> PaintWidth(w, w == width) { onWidth(w) } }
        }
    }
}

/** Whether the pen's button is held in this event. */
private fun stylusButton(ev: androidx.compose.ui.input.pointer.PointerEvent): Boolean =
    (((ev.nativeEvent as? MotionEvent)?.buttonState ?: 0) and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0

/** Removes the strokes that pass under [p] (the eraser of the pen's button). */
private fun eraseAt(strokes: MutableList<Pen>, p: Offset, radius: Float) {
    strokes.removeAll { s -> s.pts.any { (it - p).getDistance() < radius } }
}

/** A square tool button of the Paint bar, drawn in black on Paint's grey. */
@Composable
private fun PaintTool(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clickable2(onClick), contentAlignment = Alignment.Center) { PIcon(icon, PaintInk, 20.dp) }
}

/** A line-width button: a black bar as thick as the pen; the chosen one sits on a white, outlined square. */
@Composable
private fun PaintWidth(w: Dp, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).background(if (on) (if (winLook) Color.White else com.umutk.pulse.T.accent.copy(alpha = .25f)) else PaintBar).border(1.dp, if (on) PaintLine else PaintBar).clickable2(onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(22.dp).height(w).background(PaintInk)) }
}

/** One colour of the grid; the chosen one has a black outline. */
@Composable
private fun PaintColor(c: Int, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.padding(1.dp).size(18.dp).background(Color(c)).border(if (on) 2.dp else 1.dp, if (on) PaintInk else PaintLine).clickable2(onClick))
}

private fun Modifier.clickable2(onClick: () -> Unit): Modifier = this.then(Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) })

/** Draws the strokes on a PNG of the page colour, scaled to 1000 px wide, into the attachment folder. */
fun renderSketch(c: android.content.Context, strokes: List<Pen>, w: Int, h: Int, bg: Int = AColor.WHITE): String? {
    if (w <= 0 || h <= 0 || strokes.none { it.pts.size > 1 }) return null
    val scale = 1000f / w
    val bmp = Bitmap.createBitmap(1000, (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val cv = ACanvas(bmp)
    cv.drawColor(bg)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    strokes.forEach { s ->
        if (s.pts.size > 1) {
            paint.color = s.color
            paint.strokeWidth = s.width * scale
            val p = APath()
            s.pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x * scale, o.y * scale) else p.lineTo(o.x * scale, o.y * scale) }
            cv.drawPath(p, paint)
        }
    }
    val name = "sk_${UUID.randomUUID()}.png"
    FileOutputStream(java.io.File(NotesStore.attachments(c), name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bmp.recycle()
    return name
}
