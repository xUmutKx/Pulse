package com.umutk.pulse.extras.notes

import android.graphics.Bitmap
import android.graphics.Canvas as ACanvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.Path as APath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import java.io.FileOutputStream
import java.util.UUID

/** One pen stroke: its colour and points in canvas pixels. */
class Pen(val color: Int, val pts: SnapshotStateList<Offset>)

private val PENS = listOf(0xFF000000.toInt(), 0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt())

/** Full-screen sketch pad. Done saves a PNG attachment and gives its file name; Close gives null. */
@Composable
fun NSketch(onDone: (String?) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val strokes = remember { mutableStateListOf<Pen>() }
    var ink by remember { mutableStateOf(PENS[0]) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(Modifier.fillMaxSize().background(T.bg).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PIcon("close", T.text, 22.dp, Modifier.clickable2 { onDone(null) })
            Box(Modifier.weight(1f))
            PENS.forEach { c ->
                Box(Modifier.size(if (c == ink) 26.dp else 22.dp).clickable2 { ink = c }.background(Color(c)))
            }
            PIcon("undo", T.text, 22.dp, Modifier.clickable2 { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) })
            PIcon("delete", T.text, 22.dp, Modifier.clickable2 { strokes.clear() })
            PIcon("check", T.accent, 22.dp, Modifier.clickable2 {
                val name = renderSketch(ctx, strokes.toList(), size.width, size.height)
                onDone(name)
            })
        }
        Box(
            Modifier.fillMaxSize().padding(top = 10.dp).background(Color.White)
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { o -> strokes.add(Pen(ink, mutableStateListOf(o))) },
                        onDrag = { change, _ -> strokes.lastOrNull()?.pts?.add(change.position); change.consume() },
                    )
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                strokes.forEach { s ->
                    if (s.pts.size > 1) {
                        val p = Path()
                        s.pts.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
                        drawPath(p, Color(s.color), style = Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

private fun Modifier.clickable2(onClick: () -> Unit): Modifier = this.then(Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) })

/** Draws the strokes on a white PNG, scaled to 1000 px wide, into the attachment folder. */
fun renderSketch(c: android.content.Context, strokes: List<Pen>, w: Int, h: Int): String? {
    if (w <= 0 || h <= 0 || strokes.none { it.pts.size > 1 }) return null
    val scale = 1000f / w
    val bmp = Bitmap.createBitmap(1000, (h * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val cv = ACanvas(bmp)
    cv.drawColor(AColor.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f * scale; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    strokes.forEach { s ->
        if (s.pts.size > 1) {
            paint.color = s.color
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
