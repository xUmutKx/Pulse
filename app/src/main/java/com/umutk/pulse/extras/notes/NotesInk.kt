package com.umutk.pulse.extras.notes

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot

/**
 * One stroke of a note's own ink: colour, width in pixels, its points in the note's pixels, and the kind (0 a pen, 1 the marker).
 * [rel] is the pen's pressure as a width factor per point (1 is the plain width); empty for a stroke drawn with a finger, the marker, or a line.
 */
data class InkStroke(val color: Int, val width: Float, val pts: List<Offset>, val rel: List<Float> = emptyList(), val kind: Int = 0)

/** What the pen layer does with the pen: the keyboard mode (only the pen draws, with the last pen tool), pen, marker, eraser, lasso. */
enum class InkMode { KEYBOARD, PEN, MARKER, ERASER, LASSO }

/** A pen of the pen panel: its width in dp at size 1, and how much the pressure changes the width (0 none, 1 a lot). */
class PenStyle(val id: Int, val en: String, val tr: String, val base: Float, val gain: Float)

val PEN_STYLES = listOf(
    PenStyle(0, "Ballpoint", "Tükenmez", 2.2f, 0f),
    PenStyle(1, "Fountain pen", "Dolma kalem", 3f, 0.7f),
    PenStyle(2, "Brush", "Fırça", 6f, 1f),
    PenStyle(3, "Pencil", "Kurşun kalem", 2f, 0.35f),
)

/** The colours of the pen panels; 0 is "auto": the text colour of the paper. */
val INK_PALETTE = listOf(
    0, 0xFFE53935.toInt(), 0xFFFB8C00.toInt(), 0xFFFDD835.toInt(), 0xFF43A047.toInt(), 0xFF1E88E5.toInt(),
    0xFF8E24AA.toInt(), 0xFFEC407A.toInt(), 0xFF6D4C41.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(),
)

/** The marker's colours (the see-through share is added when it draws). */
val MARKER_PALETTE = listOf(0xFFFDD835.toInt(), 0xFF8BC34A.toInt(), 0xFFFF80AB.toInt(), 0xFF40C4FF.toInt(), 0xFFFFAB40.toInt(), 0xFFB388FF.toInt())

/** The pen panel's own settings (kept apart from the notes): the chosen tools, colours, sizes and whether a finger draws. */
object InkPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("notes_ink", Context.MODE_PRIVATE)
    fun penStyle(c: Context) = sp(c).getInt("pen_style", 1).coerceIn(0, PEN_STYLES.lastIndex)
    fun setPenStyle(c: Context, v: Int) = sp(c).edit().putInt("pen_style", v).apply()
    fun penColor(c: Context) = sp(c).getInt("pen_color", 0)
    fun setPenColor(c: Context, v: Int) = sp(c).edit().putInt("pen_color", v).apply()
    fun penSize(c: Context) = sp(c).getFloat("pen_size", 1f)
    fun setPenSize(c: Context, v: Float) = sp(c).edit().putFloat("pen_size", v).apply()
    fun markerColor(c: Context) = sp(c).getInt("marker_color", MARKER_PALETTE[0])
    fun setMarkerColor(c: Context, v: Int) = sp(c).edit().putInt("marker_color", v).apply()
    fun markerSize(c: Context) = sp(c).getFloat("marker_size", 1f)
    fun setMarkerSize(c: Context, v: Float) = sp(c).edit().putFloat("marker_size", v).apply()
    fun eraserSize(c: Context) = sp(c).getFloat("eraser_size", 1f)
    fun setEraserSize(c: Context, v: Float) = sp(c).edit().putFloat("eraser_size", v).apply()
    /** A finger draws too (off: only the pen draws and the finger scrolls, so a palm never marks the page). */
    fun finger(c: Context) = sp(c).getBoolean("finger", false)
    fun setFinger(c: Context, v: Boolean) = sp(c).edit().putBoolean("finger", v).apply()
    /** A pen held still at the end of a stroke makes it a straight line. */
    fun snap(c: Context) = sp(c).getBoolean("snap", true)
    fun setSnap(c: Context, v: Boolean) = sp(c).edit().putBoolean("snap", v).apply()
}

/** The ink of a note, kept as JSON in the note's own field. */
object NotesInk {
    fun decode(s: String): List<InkStroke> = runCatching {
        val arr = JSONArray(s)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pa = o.getJSONArray("p")
            val pts = ArrayList<Offset>(pa.length())
            val rel = ArrayList<Float>(pa.length())
            var every = true
            for (k in 0 until pa.length()) {
                val p = pa.getJSONArray(k)
                pts += Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
                if (p.length() > 2) rel += p.getDouble(2).toFloat() else every = false
            }
            InkStroke(o.getInt("c"), o.getDouble("w").toFloat(), pts, if (every) rel else emptyList(), o.optInt("k", 0))
        }
    }.getOrDefault(emptyList())

    fun encode(list: List<InkStroke>): String = JSONArray().also { arr ->
        list.forEach { st ->
            val pa = JSONArray()
            val withRel = st.rel.size == st.pts.size
            st.pts.forEachIndexed { i, pt ->
                val p = JSONArray().put(pt.x.toDouble()).put(pt.y.toDouble())
                if (withRel) p.put((st.rel[i] * 100f).toInt() / 100.0)
                pa.put(p)
            }
            arr.put(JSONObject().put("c", st.color).put("w", st.width.toDouble()).put("p", pa).also { if (st.kind != 0) it.put("k", st.kind) })
        }
    }.toString()

    /** The box around some strokes (their points), or null when there are none. */
    fun bounds(list: List<InkStroke>): Rect? {
        var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
        var any = false
        list.forEach { st -> st.pts.forEach { p -> any = true; if (p.x < l) l = p.x; if (p.y < t) t = p.y; if (p.x > r) r = p.x; if (p.y > b) b = p.y } }
        return if (any) Rect(l, t, r, b) else null
    }

    fun moved(st: InkStroke, d: Offset) = st.copy(pts = st.pts.map { it + d })

    /** Whether some part of the stroke passes within [r] of [p]. */
    fun near(st: InkStroke, p: Offset, r: Float): Boolean {
        val pts = st.pts
        if (pts.isEmpty()) return false
        if (pts.size == 1) return hypot(p.x - pts[0].x, p.y - pts[0].y) <= r + st.width / 2
        for (i in 1 until pts.size) if (segDist(p, pts[i - 1], pts[i]) <= r + st.width / 2) return true
        return false
    }

    /** Whether [p] lies inside the closed outline [poly] (the lasso). */
    fun inside(poly: List<Offset>, p: Offset): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val a = poly[i]; val b = poly[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) c = !c
            j = i
        }
        return c
    }

    private fun segDist(p: Offset, a: Offset, b: Offset): Float {
        val dx = b.x - a.x; val dy = b.y - a.y
        val l2 = dx * dx + dy * dy
        val t = if (l2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }
}

/**
 * The note's drawing layer, over its text and scrolling with it. [tool] is what the pen does now (pen, marker, eraser, lasso); with [penOnly] (the keyboard
 * mode) only the pen draws, with the last pen tool, and [onPenUsed] then tells the bar to switch to it. A finger never draws unless [finger] is on: it scrolls
 * the note, so a palm never marks the page. The pen's eraser end erases, and so does its side button (where the device reports it).
 * Strokes are kept as they are drawn, so the note can be edited again.
 */
@Composable
fun InkLayer(
    modifier: Modifier,
    strokes: List<InkStroke>,
    tool: InkMode,
    penOnly: Boolean,
    finger: Boolean,
    color: Int,
    width: Float,
    kind: Int,
    gain: Float,
    eraser: Float,
    snap: Boolean,
    accent: Color,
    selected: Set<Int>,
    onStroke: (InkStroke) -> Unit,
    onErase: (Set<Int>) -> Unit,
    onSelect: (Set<Int>) -> Unit,
    onMove: (Set<Int>, Offset) -> Unit,
    onPenUsed: () -> Unit,
    onPenMenu: () -> Unit = {},
) {
    val live = remember { mutableStateListOf<Offset>() }
    val liveRel = remember { mutableStateListOf<Float>() }
    var lineEnd by remember { mutableStateOf<Offset?>(null) }
    val lasso = remember { mutableStateListOf<Offset>() }
    var gone by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    val strokesNow by rememberUpdatedState(strokes)
    val selectedNow by rememberUpdatedState(selected)

    fun pressureOf(c: PointerInputChange): Float = if (c.type == PointerType.Stylus) (1f + gain * (2f * c.pressure.coerceIn(0f, 1f) - 1f) * 0.75f) else 1f

    Canvas(
        modifier.pointerInput(tool, penOnly, finger, color, width, kind, gain, eraser, snap) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val byPen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                // a finger scrolls the note, unless it is allowed to draw (never in the keyboard mode)
                if (!byPen && (penOnly || !finger)) return@awaitEachGesture
                val use = when {
                    down.type == PointerType.Eraser -> InkMode.ERASER
                    tool == InkMode.KEYBOARD -> InkMode.PEN
                    else -> tool
                }
                val thresh = 6f * density
                when (use) {
                    InkMode.ERASER -> {
                        val hit = HashSet<Int>()
                        fun hitAt(p: Offset) {
                            strokesNow.forEachIndexed { i, st -> if (i !in hit && NotesInk.near(st, p, eraser)) hit += i }
                            gone = hit.toSet(); cursor = p
                        }
                        hitAt(down.position); down.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            hitAt(c.position); c.consume()
                        }
                        if (hit.isNotEmpty()) onErase(hit.toSet())
                        gone = emptySet(); cursor = null
                    }
                    InkMode.LASSO -> {
                        val sel = selectedNow
                        val box = if (sel.isEmpty()) null else NotesInk.bounds(sel.mapNotNull { strokesNow.getOrNull(it) })?.inflate(14f * density)
                        if (box != null && box.contains(down.position)) {
                            // inside the chosen strokes: they follow the pen
                            var d = Offset.Zero
                            down.consume()
                            var last = down.position
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull() ?: break
                                if (!c.pressed) break
                                d += c.position - last; last = c.position; drag = d; c.consume()
                            }
                            if (d != Offset.Zero) onMove(sel, d)
                            drag = Offset.Zero
                        } else {
                            lasso.clear(); lasso += down.position; down.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull() ?: break
                                if (!c.pressed) break
                                lasso += c.position; c.consume()
                            }
                            val poly = lasso.toList()
                            lasso.clear()
                            if (poly.size < 6) onSelect(emptySet())
                            else {
                                // a stroke is chosen when most of its points are inside the loop
                                val pick = HashSet<Int>()
                                strokesNow.forEachIndexed { i, st ->
                                    if (st.pts.isNotEmpty() && st.pts.count { NotesInk.inside(poly, it) } * 2 >= st.pts.size) pick += i
                                }
                                onSelect(pick)
                            }
                        }
                    }
                    else -> {
                        // pen or marker: the stroke, with a straight line when the pen stays still at its end
                        live.clear(); liveRel.clear(); lineEnd = null
                        live += down.position; liveRel += pressureOf(down); down.consume()
                        var anchor = down.position
                        var since = System.currentTimeMillis()
                        var erased = false
                        while (true) {
                            val ev: PointerEvent? = withTimeoutOrNull(120) { awaitPointerEvent() }
                            val now = System.currentTimeMillis()
                            if (ev == null) {
                                if (snap && use == InkMode.PEN && lineEnd == null && live.size > 2 && now - since > 450) lineEnd = live.last()
                                continue
                            }
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            // the pen's side button turns this stroke into erasing
                            if (c.type == PointerType.Stylus && ev.buttons.isSecondaryPressed && !erased) { erased = true }
                            if (lineEnd != null) lineEnd = c.position
                            else {
                                live += c.position; liveRel += pressureOf(c)
                                if ((c.position - anchor).getDistance() > thresh) { anchor = c.position; since = now }
                                else if (snap && use == InkMode.PEN && live.size > 2 && now - since > 450) lineEnd = c.position
                            }
                            c.consume()
                        }
                        val a = live.firstOrNull()
                        if (a != null) {
                            val end = lineEnd
                            val pts = when {
                                end != null -> listOf(a, end)
                                live.size == 1 -> listOf(a, a + Offset(0.1f, 0f))
                                else -> live.toList()
                            }
                            val rel = if (end == null && kind == 0 && gain > 0f && pts.size == live.size) liveRel.toList() else emptyList()
                            if (!erased) onStroke(InkStroke(color, width, pts, rel, kind))
                            else if (live.size <= 3) onPenMenu()   // the side button only tapped: the pen's menu
                            else {
                                // the side button was held: what the pen passed over is erased instead
                                val hit = HashSet<Int>()
                                pts.forEach { p -> strokesNow.forEachIndexed { i, st -> if (NotesInk.near(st, p, eraser)) hit += i } }
                                if (hit.isNotEmpty()) onErase(hit)
                            }
                        }
                        live.clear(); liveRel.clear(); lineEnd = null
                        if (penOnly) onPenUsed()
                    }
                }
            }
        },
    ) {
        strokes.forEachIndexed { i, st -> if (i !in gone && i !in selected) drawInkStroke(st, Offset.Zero) }
        selected.forEach { i -> strokes.getOrNull(i)?.let { drawInkStroke(it, drag) } }
        val box = if (selected.isEmpty()) null else NotesInk.bounds(selected.mapNotNull { strokes.getOrNull(it) })
        if (box != null) {
            val b = box.inflate(10.dp.toPx()).translate(drag)
            drawRect(accent, b.topLeft, b.size, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
        }
        if (live.size == 1) drawCircle(Color(color), width / 2, live[0])
        else if (live.size > 1) {
            val end = lineEnd
            if (end != null) drawInkStroke(InkStroke(color, width, listOf(live.first(), end), emptyList(), kind), Offset.Zero)
            else drawInkStroke(InkStroke(color, width, live.toList(), if (kind == 0 && gain > 0f) liveRel.toList() else emptyList(), kind), Offset.Zero)
        }
        if (lasso.size > 1) {
            val p = Path().apply { moveTo(lasso[0].x, lasso[0].y); for (i in 1 until lasso.size) lineTo(lasso[i].x, lasso[i].y) }
            drawPath(p, accent, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)), cap = StrokeCap.Round))
        }
        cursor?.let { drawCircle(accent, eraser, it, style = Stroke(1.5.dp.toPx())) }
    }
}

/** One stroke: a see-through one (the marker) is multiplied onto what is under it, so the words stay readable; a pen's pressure changes its width piece by piece. */
fun DrawScope.drawInkStroke(st: InkStroke, off: Offset) {
    val pts = st.pts
    if (pts.isEmpty()) return
    val blend = if (st.kind == 1 || (st.color ushr 24) < 0xFF) BlendMode.Multiply else BlendMode.SrcOver
    val col = Color(st.color)
    if (pts.size == 1) { drawCircle(col, st.width / 2, pts[0] + off, blendMode = blend); return }
    if (st.kind == 0 && st.rel.size == pts.size) {
        for (i in 1 until pts.size) {
            val f = ((st.rel[i] + st.rel[i - 1]) / 2f).coerceIn(0.2f, 2.2f)
            drawLine(col, pts[i - 1] + off, pts[i] + off, strokeWidth = st.width * f, cap = StrokeCap.Round, blendMode = blend)
        }
        return
    }
    // the curve passes through the middle of each pair of points, with the points as its guides: no corners where the pen's samples are
    val path = Path().apply {
        moveTo(pts[0].x + off.x, pts[0].y + off.y)
        if (pts.size == 2) lineTo(pts[1].x + off.x, pts[1].y + off.y)
        else {
            for (i in 1 until pts.size - 1) {
                val mx = (pts[i].x + pts[i + 1].x) / 2f + off.x; val my = (pts[i].y + pts[i + 1].y) / 2f + off.y
                quadraticBezierTo(pts[i].x + off.x, pts[i].y + off.y, mx, my)
            }
            lineTo(pts.last().x + off.x, pts.last().y + off.y)
        }
    }
    drawPath(path, col, style = Stroke(width = st.width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}
