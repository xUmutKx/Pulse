package com.umutk.pulse.extras.notes

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path as APath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PIcon
import java.io.FileOutputStream
import java.util.UUID

/** One pen stroke: its colour, its width in canvas pixels and its points in canvas pixels. */
class Pen(val color: Int, val width: Float, val pts: SnapshotStateList<Offset>)

/** Paint's own 16 colours. */
private val PAINT_COLORS = listOf(
    0xFF000000, 0xFF808080, 0xFF800000, 0xFF808000, 0xFF008000, 0xFF008080, 0xFF000080, 0xFF800080,
    0xFFFFFFFF, 0xFFC0C0C0, 0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF,
).map { it.toInt() }
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
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(PaintBar)) {
        // actions and line widths on the first row, the colour grid under it, like Paint's tool bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PaintTool("close") { onDone(null) }
            PaintTool("undo") { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
            PaintTool("delete") { strokes.clear() }
            Spacer(Modifier.width(8.dp))
            PAINT_WIDTHS.forEach { w -> PaintWidth(w, w == width) { width = w } }
            Spacer(Modifier.weight(1f))
            PaintTool("check") { onDone(renderSketch(ctx, strokes.toList(), size.width, size.height, paper.toArgb())) }
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp)) {
            PAINT_COLORS.chunked(8).forEach { row ->
                Row { row.forEach { c -> PaintColor(c, c == ink) { ink = c } } }
            }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth().border(1.dp, PaintLine).background(paper)
                .onSizeChanged { size = it }
                // keyed on the colour and width, so a change applies to the next stroke
                .pointerInput(ink, width) {
                    detectDragGestures(
                        onDragStart = { o -> strokes.add(Pen(ink, width.toPx(), mutableStateListOf(o))) },
                        onDrag = { change, _ -> strokes.lastOrNull()?.pts?.add(change.position); change.consume() },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                strokes.forEach { s ->
                    if (s.pts.size > 1) {
                        val p = Path()
                        s.pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                        drawPath(p, Color(s.color), style = Stroke(width = s.width, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
            }
        }
    }
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
