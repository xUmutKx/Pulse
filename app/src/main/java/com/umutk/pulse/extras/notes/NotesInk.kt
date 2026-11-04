package com.umutk.pulse.extras.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import org.json.JSONArray
import org.json.JSONObject

/** One stroke of a note's own ink: colour, width in pixels, and its points in the note's pixels. */
data class InkStroke(val color: Int, val width: Float, val pts: List<Offset>)

/** The ink of a note, kept as JSON in the note's own field. */
object NotesInk {
    fun decode(s: String): List<InkStroke> = runCatching {
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pa = o.getJSONArray("p")
            InkStroke(o.getInt("c"), o.getDouble("w").toFloat(), (0 until pa.length()).map { k ->
                val p = pa.getJSONArray(k)
                Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
            })
        }
    }.getOrDefault(emptyList())

    fun encode(list: List<InkStroke>): String = JSONArray().also { arr ->
        list.forEach { st ->
            val pa = JSONArray()
            st.pts.forEach { pt -> pa.put(JSONArray().put(pt.x.toDouble()).put(pt.y.toDouble())) }
            arr.put(JSONObject().put("c", st.color).put("w", st.width.toDouble()).put("p", pa))
        }
    }.toString()
}

/**
 * The note's drawing layer over its text. With the pen on it takes only the S Pen (and the eraser end): a finger or a palm
 * is not taken, so it still scrolls the note and never draws. Strokes are kept as they are drawn, so the note can be edited again.
 */
@Composable
fun InkLayer(strokes: List<InkStroke>, active: Boolean, color: Int, width: Float, onStroke: (InkStroke) -> Unit) {
    val current = remember { mutableStateListOf<Offset>() }
    Canvas(
        Modifier.fillMaxSize().pointerInput(active, color, width) {
            if (!active) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                // only the pen: a finger and a palm pass to the scroll below
                if (down.type != PointerType.Stylus && down.type != PointerType.Eraser) return@awaitEachGesture
                current.clear(); current.add(down.position); down.consume()
                while (true) {
                    val ev = awaitPointerEvent()
                    val c = ev.changes.firstOrNull() ?: break
                    if (c.pressed) { current.add(c.position); c.consume() } else break
                }
                if (current.size > 1) onStroke(InkStroke(color, width, current.toList()))
                current.clear()
            }
        },
    ) {
        strokes.forEach { st -> drawInk(st.pts, st.color, st.width) }
        if (current.size > 1) drawInk(current.toList(), color, width)
    }
}

private fun DrawScope.drawInk(pts: List<Offset>, color: Int, width: Float) {
    if (pts.size < 2) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
    }
    // a see-through stroke (the highlighter) is multiplied onto what is under it, so the words stay readable
    val blend = if ((color ushr 24) < 0xFF) BlendMode.Multiply else BlendMode.SrcOver
    drawPath(path, Color(color), style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}
