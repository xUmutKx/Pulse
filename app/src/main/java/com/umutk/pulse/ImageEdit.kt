package com.umutk.pulse

import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.CircularProgressIndicator
import com.umutk.pulse.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.ColorMatrix as CMatrix
import androidx.compose.ui.graphics.Path as CPath

/** One pen stroke; points and width are relative to the picture (0..1, width as a fraction of the picture width). */
internal class ImgStroke(val color: Int, val w: Float, val pts: List<Offset>)

internal class ImgSnap(val bmp: Bitmap, val br: Float, val ct: Float, val sat: Float, val fx: Int, val strokes: List<ImgStroke>)

/** The editor's document: a bitmap with committed transforms, live colour settings and pen strokes, plus an undo history. */
internal class ImgEditState(val original: Bitmap) {
    var bmp by mutableStateOf(original)
    var br by mutableFloatStateOf(0f)
    var ct by mutableFloatStateOf(1f)
    var sat by mutableFloatStateOf(1f)
    var fx by mutableIntStateOf(0)
    var strokes by mutableStateOf<List<ImgStroke>>(emptyList())
    val history = mutableStateListOf<ImgSnap>()
    private var tweaking = false

    fun push() { history.add(ImgSnap(bmp, br, ct, sat, fx, strokes)); if (history.size > 12) history.removeAt(0) }
    fun undo() {
        if (history.isEmpty()) return
        val s = history.removeAt(history.lastIndex)
        bmp = s.bmp; br = s.br; ct = s.ct; sat = s.sat; fx = s.fx; strokes = s.strokes; tweaking = false
    }
    fun reset() { bmp = original; br = 0f; ct = 1f; sat = 1f; fx = 0; strokes = emptyList(); history.clear(); tweaking = false }
    /** A slider moves: the state before the first change goes into the history once per drag. */
    fun tweak(block: () -> Unit) { if (!tweaking) { push(); tweaking = true }; block() }
    fun endTweak() { tweaking = false }

    private fun baked(): Bitmap = if (strokes.isEmpty()) bmp else imgBake(bmp, strokes)
    fun transform(m: android.graphics.Matrix) {
        val b = baked()
        val nb = runCatching { Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true) }.getOrNull() ?: return
        push(); bmp = nb; strokes = emptyList()
    }
    fun rotate(deg: Float) = transform(android.graphics.Matrix().apply { postRotate(deg) })
    fun flip(horizontal: Boolean) = transform(android.graphics.Matrix().apply { if (horizontal) postScale(-1f, 1f) else postScale(1f, -1f) })
    fun crop(r: Rect) {
        val b = baked()
        val x = (r.left * b.width).roundToInt().coerceIn(0, b.width - 1)
        val y = (r.top * b.height).roundToInt().coerceIn(0, b.height - 1)
        val w = (r.width * b.width).roundToInt().coerceIn(1, b.width - x)
        val h = (r.height * b.height).roundToInt().coerceIn(1, b.height - y)
        val nb = runCatching { Bitmap.createBitmap(b, x, y, w, h) }.getOrNull() ?: return
        push(); bmp = nb; strokes = emptyList()
    }
    fun addStroke(s: ImgStroke) { push(); strokes = strokes + s }
}

private fun imgDrawStrokes(c: android.graphics.Canvas, strokes: List<ImgStroke>, w: Int, h: Int) {
    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    p.strokeCap = android.graphics.Paint.Cap.ROUND; p.strokeJoin = android.graphics.Paint.Join.ROUND
    strokes.forEach { s ->
        if (s.pts.isEmpty()) return@forEach
        p.color = s.color; p.strokeWidth = max(1f, s.w * w)
        if (s.pts.size == 1) {
            p.style = android.graphics.Paint.Style.FILL
            c.drawCircle(s.pts[0].x * w, s.pts[0].y * h, p.strokeWidth / 2f, p)
        } else {
            p.style = android.graphics.Paint.Style.STROKE
            val path = android.graphics.Path()
            s.pts.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x * w, o.y * h) else path.lineTo(o.x * w, o.y * h) }
            c.drawPath(path, p)
        }
    }
}

private fun imgBake(b: Bitmap, strokes: List<ImgStroke>): Bitmap =
    runCatching { b.copy(Bitmap.Config.ARGB_8888, true).also { imgDrawStrokes(android.graphics.Canvas(it), strokes, it.width, it.height) } }.getOrDefault(b)

/** Brightness (-0.5..0.5), contrast (0.5..1.5), saturation (0..2) and a filter (0 none, 1 mono, 2 sepia, 3 warm, 4 cool, 5 vivid) as one colour matrix. */
internal fun imgMatrix(br: Float, ct: Float, sat: Float, fx: Int): android.graphics.ColorMatrix {
    val m = android.graphics.ColorMatrix()
    m.setSaturation(sat)
    val t = (0.5f - 0.5f * ct + br) * 255f
    m.postConcat(android.graphics.ColorMatrix(floatArrayOf(ct, 0f, 0f, 0f, t, 0f, ct, 0f, 0f, t, 0f, 0f, ct, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    when (fx) {
        1 -> m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(0f) })
        2 -> m.postConcat(android.graphics.ColorMatrix(floatArrayOf(.393f, .769f, .189f, 0f, 0f, .349f, .686f, .168f, 0f, 0f, .272f, .534f, .131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        3 -> m.postConcat(android.graphics.ColorMatrix().apply { setScale(1.1f, 1.0f, .86f, 1f) })
        4 -> m.postConcat(android.graphics.ColorMatrix().apply { setScale(.88f, 1.0f, 1.12f, 1f) })
        5 -> {
            m.postConcat(android.graphics.ColorMatrix().apply { setSaturation(1.5f) })
            m.postConcat(android.graphics.ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, -12f, 0f, 1.1f, 0f, 0f, -12f, 0f, 0f, 1.1f, 0f, -12f, 0f, 0f, 0f, 1f, 0f)))
        }
    }
    return m
}

/** The finished picture: colours applied to the bitmap, strokes on top. */
private fun imgRender(s: ImgEditState): Bitmap? = runCatching {
    val b = s.bmp
    val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val p = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
    p.colorFilter = android.graphics.ColorMatrixColorFilter(imgMatrix(s.br, s.ct, s.sat, s.fx))
    c.drawBitmap(b, 0f, 0f, p)
    imgDrawStrokes(c, s.strokes, out.width, out.height)
    out
}.getOrNull()

/** Saves as "<name> edited.jpg" beside the original, or over the original (same format) when [overwrite] and the format allows it. Null on failure. */
private fun imgSave(ctx: android.content.Context, src: ImgSrc, s: ImgEditState, overwrite: Boolean): File? = runCatching {
    val out = imgRender(s) ?: return@runCatching null
    val orig = src.file
    val canOver = overwrite && orig != null && orig.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
    val f: File = if (canOver && orig != null) orig else {
        val dir = orig?.parentFile ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Pulse")
        dir.mkdirs()
        val base = src.name.substringBeforeLast('.')
        var n = File(dir, "$base edited.jpg"); var i = 2
        while (n.exists()) { n = File(dir, "$base edited $i.jpg"); i++ }
        n
    }
    val fmt = when (f.extension.lowercase()) {
        "png" -> Bitmap.CompressFormat.PNG
        "webp" -> Bitmap.CompressFormat.WEBP_LOSSY
        else -> Bitmap.CompressFormat.JPEG
    }
    f.outputStream().use { out.compress(fmt, 95, it) }
    MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
    f
}.getOrNull()

private fun imgFit(cw: Float, ch: Float, bw: Int, bh: Int): Rect {
    val s = min(cw / bw, ch / bh)
    val w = bw * s; val h = bh * s
    return Rect((cw - w) / 2f, (ch - h) / 2f, (cw + w) / 2f, (ch + h) / 2f)
}

/** Largest centred crop with pixel ratio [r] (width / height), as a rectangle relative to the picture. */
private fun imgRatioRect(r: Float, bw: Int, bh: Int): Rect {
    var w = 1f; var h = (bw / r) / bh
    if (h > 1f) { h = 1f; w = (bh * r) / bw }
    return Rect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
}

/** Moves the dragged corner (px, py) so that the box with the fixed corner (ax, ay) keeps width / height = [r] (relative units) and stays inside 0..1. */
private fun imgLock(ax: Float, ay: Float, px: Float, py: Float, r: Float): Offset {
    val sx = if (px >= ax) 1f else -1f; val sy = if (py >= ay) 1f else -1f
    var w = abs(px - ax); var h = abs(py - ay)
    if (w / max(h, 1e-4f) > r) w = h * r else h = w / r
    val mw = if (sx > 0) 1f - ax else ax; val mh = if (sy > 0) 1f - ay else ay
    val k = min(1f, min(mw / max(w, 1e-4f), mh / max(h, 1e-4f)))
    return Offset(ax + sx * w * k, ay + sy * h * k)
}

private fun DrawScope.imgStrokeLine(pts: List<Offset>, color: Color, wPx: Float, fit: Rect) {
    if (pts.isEmpty()) return
    val d = pts.map { Offset(fit.left + it.x * fit.width, fit.top + it.y * fit.height) }
    if (d.size == 1) { drawCircle(color, wPx / 2f, d[0]); return }
    val path = CPath()
    d.forEachIndexed { i, o -> if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
    drawPath(path, color, style = Stroke(wPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** The editor: a full-screen overlay. [onSaved] gets the written file and whether it replaced the original. */
@Composable
internal fun ImageEditor(src: ImgSrc, onClose: () -> Unit, onSaved: (File, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val loaded by produceState<Pair<Boolean, Bitmap?>>(false to null, src) { value = true to withContext(Dispatchers.IO) { imgDecode(ctx, src, 2560) } }
    BackHandler(true) { onClose() }
    Column(Modifier.fillMaxSize().background(T.bg).pointerInput(Unit) {}.statusBarsPadding().navigationBarsPadding()) {
        val b = loaded.second
        if (b != null) ImgEditBody(src, b, onClose, onSaved)
        else {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ImgIcBtn(onClose) { ImgIc("close", Icons.Outlined.Close, T.text) }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!loaded.first) CircularProgressIndicator(Modifier.size(28.dp), color = Look.accent, strokeWidth = 2.dp)
                else Icon(Icons.Outlined.BrokenImage, null, Modifier.size(48.dp), T.sub)
            }
        }
    }
}

private val ImgRatios = listOf(0f, 1f, 4f / 3f, 16f / 9f, 3f / 4f, 9f / 16f)
private val ImgRatioNames get() = listOf(tr("Free", "Serbest"), "1:1", "4:3", "16:9", "3:4", "9:16")
private val ImgPenColors = listOf(0xFFFFFFFFL, 0xFF000000L, 0xFFE53935L, 0xFFFB8C00L, 0xFFFDD835L, 0xFF43A047L, 0xFF1E88E5L, 0xFF8E24AAL).map { it.toInt() }
private val ImgPenSizes = listOf(.006f, .012f, .024f)

@Composable
private fun ColumnScope.ImgEditBody(src: ImgSrc, b0: Bitmap, onClose: () -> Unit, onSaved: (File, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = remember(b0) { ImgEditState(b0) }
    var tab by remember { mutableIntStateOf(0) }
    var ratio by remember { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var color by remember { mutableIntStateOf(ImgPenColors[2]) }
    var pen by remember { mutableFloatStateOf(ImgPenSizes[1]) }
    var live by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var dragMode by remember { mutableIntStateOf(0) }
    var saveAsk by remember { mutableStateOf(false) }
    var overAsk by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val acc = Look.accent.copy(alpha = 1f)

    LaunchedEffect(st.bmp) { crop = if (ratio > 0) imgRatioRect(ImgRatios[ratio], st.bmp.width, st.bmp.height) else Rect(0f, 0f, 1f, 1f) }
    val img = remember(st.bmp) { st.bmp.asImageBitmap() }
    val cf = remember(st.br, st.ct, st.sat, st.fx) { ColorFilter.colorMatrix(CMatrix(imgMatrix(st.br, st.ct, st.sat, st.fx).array)) }

    fun doSave(over: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val f = withContext(Dispatchers.IO) { imgSave(ctx, src, st, over) }
            busy = false
            if (f == null) Toast.makeText(ctx, tr("Could not save", "Kaydedilemedi"), Toast.LENGTH_SHORT).show()
            else {
                Toast.makeText(ctx, tr("Saved: ${f.name}", "Kaydedildi: ${f.name}"), Toast.LENGTH_SHORT).show()
                onSaved(f, over && f.absolutePath == src.file?.absolutePath)
            }
        }
    }

    ImgEditTop(onClose, st.history.isNotEmpty(), { st.undo() }, { st.reset() }, { saveAsk = true })

    Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
        Canvas(Modifier.fillMaxSize().pointerInput(tab, st.bmp) {
            val fit = imgFit(size.width.toFloat(), size.height.toFloat(), st.bmp.width, st.bmp.height)
            fun toN(p: Offset) = Offset(((p.x - fit.left) / fit.width).coerceIn(0f, 1f), ((p.y - fit.top) / fit.height).coerceIn(0f, 1f))
            if (tab == 0) {
                detectDragGestures(
                    onDragStart = { p ->
                        val cs = listOf(Offset(crop.left, crop.top), Offset(crop.right, crop.top), Offset(crop.left, crop.bottom), Offset(crop.right, crop.bottom))
                        val dist = cs.map { (Offset(fit.left + it.x * fit.width, fit.top + it.y * fit.height) - p).getDistance() }
                        val idx = dist.indices.minByOrNull { dist[it] } ?: 0
                        dragMode = if (dist[idx] < 64f) idx + 2 else if (crop.contains(toN(p))) 1 else 0
                    },
                    onDragEnd = { dragMode = 0 }, onDragCancel = { dragMode = 0 },
                    onDrag = { ch, d ->
                        ch.consume()
                        val dn = Offset(d.x / fit.width, d.y / fit.height)
                        if (dragMode == 1) {
                            crop = crop.translate(dn.x.coerceIn(-crop.left, 1f - crop.right), dn.y.coerceIn(-crop.top, 1f - crop.bottom))
                        } else if (dragMode >= 2) {
                            val mv = when (dragMode) { 2 -> Offset(crop.left, crop.top); 3 -> Offset(crop.right, crop.top); 4 -> Offset(crop.left, crop.bottom); else -> Offset(crop.right, crop.bottom) }
                            val fx = when (dragMode) { 2 -> Offset(crop.right, crop.bottom); 3 -> Offset(crop.left, crop.bottom); 4 -> Offset(crop.right, crop.top); else -> Offset(crop.left, crop.top) }
                            var np = Offset((mv.x + dn.x).coerceIn(0f, 1f), (mv.y + dn.y).coerceIn(0f, 1f))
                            if (ratio > 0) np = imgLock(fx.x, fx.y, np.x, np.y, ImgRatios[ratio] * st.bmp.height / st.bmp.width)
                            if (abs(np.x - fx.x) >= .04f && abs(np.y - fx.y) >= .04f) crop = Rect(min(fx.x, np.x), min(fx.y, np.y), max(fx.x, np.x), max(fx.y, np.y))
                        }
                    })
            } else if (tab == 3) {
                detectDragGestures(
                    onDragStart = { p -> live = listOf(toN(p)) },
                    onDragEnd = { if (live.isNotEmpty()) st.addStroke(ImgStroke(color, pen, live)); live = emptyList() },
                    onDragCancel = { live = emptyList() },
                    onDrag = { ch, _ -> ch.consume(); live = live + toN(ch.position) })
            }
        }) {
            val fit = imgFit(size.width, size.height, st.bmp.width, st.bmp.height)
            drawImage(img, dstOffset = IntOffset(fit.left.roundToInt(), fit.top.roundToInt()), dstSize = IntSize(fit.width.roundToInt(), fit.height.roundToInt()), colorFilter = cf, filterQuality = FilterQuality.Medium)
            st.strokes.forEach { imgStrokeLine(it.pts, Color(it.color), max(1f, it.w * fit.width), fit) }
            if (live.isNotEmpty()) imgStrokeLine(live, Color(color), max(1f, pen * fit.width), fit)
            if (tab == 0) {
                val l = fit.left + crop.left * fit.width; val r = fit.left + crop.right * fit.width
                val t = fit.top + crop.top * fit.height; val bt = fit.top + crop.bottom * fit.height
                val dim = Color.Black.copy(alpha = .55f)
                drawRect(dim, Offset(fit.left, fit.top), Size(fit.width, t - fit.top))
                drawRect(dim, Offset(fit.left, bt), Size(fit.width, fit.bottom - bt))
                drawRect(dim, Offset(fit.left, t), Size(l - fit.left, bt - t))
                drawRect(dim, Offset(r, t), Size(fit.right - r, bt - t))
                val thin = Color.White.copy(alpha = .4f)
                for (i in 1..2) {
                    drawLine(thin, Offset(l + (r - l) * i / 3f, t), Offset(l + (r - l) * i / 3f, bt), 1.dp.toPx())
                    drawLine(thin, Offset(l, t + (bt - t) * i / 3f), Offset(r, t + (bt - t) * i / 3f), 1.dp.toPx())
                }
                drawRect(Color.White, Offset(l, t), Size(r - l, bt - t), style = Stroke(2.dp.toPx()))
                listOf(Offset(l, t), Offset(r, t), Offset(l, bt), Offset(r, bt)).forEach { drawCircle(Color.White, 9.dp.toPx(), it); drawCircle(acc, 5.dp.toPx(), it) }
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center).size(32.dp), color = acc, strokeWidth = 3.dp)
    }

    Column(Modifier.fillMaxWidth().background(T.panel).padding(horizontal = 12.dp, vertical = 8.dp).heightIn(min = 92.dp), verticalArrangement = Arrangement.Center) {
        when (tab) {
            0 -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImgRatioNames.forEachIndexed { i, n ->
                        PChip(ratio == i, n) { ratio = i; crop = if (i == 0) crop else imgRatioRect(ImgRatios[i], st.bmp.width, st.bmp.height) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ImgIcBtn({ st.rotate(-90f) }) { Icon(Icons.Outlined.RotateLeft, null, Modifier.size(24.dp), T.text) }
                    ImgIcBtn({ st.rotate(90f) }) { Icon(Icons.Outlined.RotateRight, null, Modifier.size(24.dp), T.text) }
                    ImgIcBtn({ st.flip(true) }) { Icon(Icons.Outlined.Flip, null, Modifier.size(24.dp), T.text) }
                    ImgIcBtn({ st.flip(false) }) { Icon(Icons.Outlined.Flip, null, Modifier.size(24.dp).rotate(90f), T.text) }
                    val full = crop.left <= .001f && crop.top <= .001f && crop.right >= .999f && crop.bottom >= .999f
                    ImgIcBtn({ if (!full) st.crop(crop) }, bg = if (full) Color.Transparent else acc.copy(alpha = .25f)) { Icon(Icons.Outlined.Check, null, Modifier.size(24.dp), if (full) T.sub.copy(alpha = .4f) else T.text) }
                }
            }
            1 -> {
                val sc = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WbSunny, null, Modifier.size(22.dp), T.text); Spacer(Modifier.width(10.dp))
                    Slider(st.br, { v -> st.tweak { st.br = v } }, Modifier.weight(1f), valueRange = -.5f..0.5f, onValueChangeFinished = { st.endTweak() }, colors = sc)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Contrast, null, Modifier.size(22.dp), T.text); Spacer(Modifier.width(10.dp))
                    Slider(st.ct, { v -> st.tweak { st.ct = v } }, Modifier.weight(1f), valueRange = .5f..1.5f, onValueChangeFinished = { st.endTweak() }, colors = sc)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, Modifier.size(22.dp), T.text); Spacer(Modifier.width(10.dp))
                    Slider(st.sat, { v -> st.tweak { st.sat = v } }, Modifier.weight(1f), valueRange = 0f..2f, onValueChangeFinished = { st.endTweak() }, colors = sc)
                }
            }
            2 -> {
                val names = listOf(tr("Original", "Orijinal"), tr("Mono", "Siyah beyaz"), tr("Sepia", "Sepya"), tr("Warm", "Sıcak"), tr("Cool", "Soğuk"), tr("Vivid", "Canlı"))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    names.forEachIndexed { i, n -> PChip(st.fx == i, n) { if (st.fx != i) { st.push(); st.fx = i } } }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImgPenColors.forEach { c ->
                        val on = c == color
                        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (on) 3.dp else 1.dp, if (on) acc else T.line, CircleShape).clickable { color = c })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    ImgPenSizes.forEachIndexed { i, s ->
                        val on = s == pen
                        Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) acc.copy(alpha = .22f) else Color.Transparent).clickable { pen = s }, contentAlignment = Alignment.Center) {
                            Box(Modifier.size((6 + i * 6).dp).clip(CircleShape).background(T.text))
                        }
                    }
                }
            }
        }
    }
    ImgToolStrip(tab) { tab = it }

    if (saveAsk) WinAlert(onDismissRequest = { saveAsk = false },
        title = { Text(tr("Save", "Kaydet")) },
        text = { Text("${src.name.substringBeforeLast('.')} edited.jpg", color = T.sub, fontSize = 13.sp) },
        confirmButton = {
            Row {
                TextButton({ saveAsk = false; doSave(false) }) { Text(tr("Save copy", "Kopya kaydet")) }
                if (src.file != null) TextButton({ saveAsk = false; overAsk = true }) { Text(tr("Overwrite", "Üzerine yaz")) }
            }
        },
        dismissButton = { TextButton({ saveAsk = false }) { Text(tr("Cancel", "İptal")) } })
    if (overAsk) WinAlert(onDismissRequest = { overAsk = false },
        title = { Text(tr("Overwrite the original?", "Orijinal üzerine yazılsın mı?")) },
        text = { Text(src.name, color = T.sub, fontSize = 13.sp) },
        confirmButton = { TextButton({ overAsk = false; doSave(true) }) { Text(tr("Overwrite", "Üzerine yaz")) } },
        dismissButton = { TextButton({ overAsk = false }) { Text(tr("Cancel", "İptal")) } })
}
