package com.umutk.pulse.extras.calculator

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.frame
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// ------------------------------------------------------------------ the display of the panes

@Composable
private fun CalcDisplay(top: String, main: String, classic: Boolean) {
    Column(
        Modifier.fillMaxWidth().then(if (classic) Modifier.background(Color.White).frame(T, raised = false) else Modifier).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(top.ifEmpty { " " }, color = if (classic) Color.Black else T.sub, fontSize = 16.sp, maxLines = 1)
        Text(main.ifEmpty { "0" }, color = if (classic) Color.Black else T.text, fontSize = if (main.length > 14) 26.sp else if (main.length > 9) 34.sp else 44.sp, fontWeight = FontWeight.Light, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
    }
}

// ------------------------------------------------------------------ scientific

/** The scientific calculator: the standard keys with the functions above them; the whole expression is shown and worked out at "=". */
@Composable
internal fun SciPane(classic: Boolean) {
    var e by rememberSaveable { mutableStateOf("") }
    var deg by rememberSaveable { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    var ans by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    val preview = if (!done && e.isNotEmpty()) Sci.eval(e, deg)?.let(Sci::show) else null
    fun press(k: String) {
        err = false
        // after "=" a number or a function starts a new line; an operator goes on with the result
        if (done && (k[0].isDigit() || k in setOf(".", "π", "(", "e", "sin", "cos", "tan", "ln", "log", "√", "|x|"))) e = ""
        done = false
        when (k) {
            "C" -> e = ""
            "⌫" -> e = e.dropLast(1)
            "DEG", "RAD" -> deg = !deg
            "=" -> if (e.isNotEmpty()) { val v = Sci.eval(e, deg); if (v == null) err = true else { ans = Sci.show(v); e = ans; done = true } }
            "±" -> if (e.isNotEmpty()) e = if (e.startsWith("-(") && e.endsWith(")")) e.drop(2).dropLast(1) else "-($e)"
            "x²" -> e += "^2"
            "xʸ" -> e += "^"
            "√" -> e += "sqrt("
            "n!" -> e += "!"
            "|x|" -> e += "abs("
            "Ans" -> e += ans
            "sin", "cos", "tan", "ln", "log" -> e += "$k("
            else -> if (e.length < 80) e += k
        }
    }
    val sci = listOf(
        listOf(if (deg) "DEG" else "RAD", "(", ")", "%", "⌫"),
        listOf("sin", "cos", "tan", "ln", "log"),
        listOf("x²", "xʸ", "√", "n!", "π"),
        listOf("C", "e", "Ans", "|x|", "÷"),
    )
    val pad = listOf(listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("±", "0", ".", "="))
    Column(Modifier.fillMaxSize()) {
        CalcDisplay(if (err) "" else preview?.let { "= $it" } ?: "", if (err) tr("Error", "Hata") else e, classic)
        Spacer(Modifier.height(6.dp))
        CalcPad(sci, classic, Modifier.weight(4f)) { press(it) }
        Spacer(Modifier.height(4.dp))
        CalcPad(pad, classic, Modifier.weight(4f)) { press(it) }
        Spacer(Modifier.height(8.dp))
    }
}

// ------------------------------------------------------------------ graphing

/**
 * The graphing calculator: up to four functions of x drawn on a grid. Drag to move, pinch to zoom, tap to read the values at that x.
 * Everything the scientific calculator understands works in a function (sin(x), x^2/4, abs(x)-2, ln(x), 2^x ...).
 */
@Composable
internal fun GraphPane() {
    val exprs = remember { mutableStateListOf("sin(x)", "x^2/4", "", "") }
    var cx by rememberSaveable { mutableFloatStateOf(0f) }
    var cy by rememberSaveable { mutableFloatStateOf(0f) }
    var scale by rememberSaveable { mutableFloatStateOf(48f) }
    var w by remember { mutableFloatStateOf(1f) }
    var h by remember { mutableFloatStateOf(1f) }
    var probe by remember { mutableStateOf<Float?>(null) }
    var deg by remember { mutableStateOf(false) }
    val colors = listOf(Color(0xFF4FC3F7), Color(0xFFFF8A65), Color(0xFF81C784), Color(0xFFBA68C8))
    val fns = exprs.map { Sci.compile(it) }
    val grid = T.text.copy(alpha = .12f); val axis = T.text.copy(alpha = .55f); val label = T.sub
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel).onSizeChanged { w = it.width.toFloat(); h = it.height.toFloat() }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        // the world point under the fingers stays under them while pinching
                        val wx = cx + (centroid.x - w / 2f) / scale; val wy = cy - (centroid.y - h / 2f) / scale
                        scale = (scale * zoom).coerceIn(2f, 20000f)
                        cx = wx - (centroid.x - w / 2f) / scale - pan.x / scale
                        cy = wy + (centroid.y - h / 2f) / scale + pan.y / scale
                    }
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { probe = cx + (it.x - w / 2f) / scale }, onDoubleTap = { probe = null }) },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val tp = android.graphics.Paint().apply { isAntiAlias = true; textSize = 11.sp.toPx(); color = android.graphics.Color.argb((label.alpha * 255).toInt(), (label.red * 255).toInt(), (label.green * 255).toInt(), (label.blue * 255).toInt()) }
                // a grid line every 1, 2 or 5 times a power of ten, about 80 px apart
                val raw = 80f / scale
                val pw = 10.0.pow(floor(log10(raw.toDouble()))).toFloat()
                val step = listOf(1f, 2f, 5f, 10f).map { it * pw }.first { it >= raw }
                val x0 = cx - w / 2f / scale; val x1 = cx + w / 2f / scale
                val y0 = cy - h / 2f / scale; val y1 = cy + h / 2f / scale
                fun sx(x: Float) = (x - cx) * scale + w / 2f
                fun sy(y: Float) = h / 2f - (y - cy) * scale
                var gx = floor(x0 / step) * step
                while (gx <= x1) { drawLine(if (abs(gx) < step / 1000f) axis else grid, Offset(sx(gx), 0f), Offset(sx(gx), h), if (abs(gx) < step / 1000f) 2f else 1f); if (abs(gx) > step / 2f) drawContext.canvas.nativeCanvas.drawText(Sci.show(gx.toDouble()), sx(gx) + 3f, min(max(sy(0f), 14f), h - 4f) - 3f, tp); gx += step }
                var gy = floor(y0 / step) * step
                while (gy <= y1) { drawLine(if (abs(gy) < step / 1000f) axis else grid, Offset(0f, sy(gy)), Offset(w, sy(gy)), if (abs(gy) < step / 1000f) 2f else 1f); if (abs(gy) > step / 2f) drawContext.canvas.nativeCanvas.drawText(Sci.show(gy.toDouble()), min(max(sx(0f), 2f), w - 40f) + 4f, sy(gy) - 3f, tp); gy += step }
                // the curves: the pen lifts at a gap or a jump (1/x, tan(x))
                fns.forEachIndexed { i, f ->
                    if (f == null) return@forEachIndexed
                    val path = Path(); var pen = false; var prev = 0f
                    var px = 0f
                    while (px <= w) {
                        val y = f.at((cx + (px - w / 2f) / scale).toDouble(), deg)
                        val py = if (y.isFinite()) sy(y.toFloat()) else Float.NaN
                        if (py.isNaN() || abs(py) > h * 8f) pen = false
                        else {
                            if (pen && abs(py - prev) > h * 1.5f) pen = false
                            if (pen) path.lineTo(px, py) else path.moveTo(px, py)
                            pen = true; prev = py
                        }
                        px += 1.5f
                    }
                    drawPath(path, colors[i], style = Stroke(3f))
                }
                probe?.let { x ->
                    drawLine(label, Offset(sx(x), 0f), Offset(sx(x), h), 1.5f)
                    fns.forEachIndexed { i, f -> f?.at(x.toDouble(), deg)?.takeIf { it.isFinite() }?.let { drawCircle(colors[i], 7f, Offset(sx(x), sy(it.toFloat()))) } }
                }
            }
            // the readout of a tap: x and each function's value there
            probe?.let { x ->
                Column(Modifier.align(Alignment.TopStart).padding(8.dp).background(T.bg.copy(alpha = .85f), RoundedCornerShape(6.dp)).padding(8.dp)) {
                    Text("x = " + Sci.show(x.toDouble()), color = T.text, fontSize = 13.sp)
                    fns.forEachIndexed { i, f -> f?.at(x.toDouble(), deg)?.let { Text("f${i + 1} = " + Sci.show(it), color = colors[i], fontSize = 13.sp) } }
                }
            }
            // zoom buttons and back to the first view
            Column(Modifier.align(Alignment.CenterEnd).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("+" to { scale = (scale * 1.5f).coerceAtMost(20000f) }, "−" to { scale = (scale / 1.5f).coerceAtLeast(2f) }, "⌂" to { cx = 0f; cy = 0f; scale = 48f; probe = null }).forEach { (t, a) ->
                    Box(Modifier.size(36.dp).clip(CircleShape).background(T.bg.copy(alpha = .8f)).clickable { a() }, contentAlignment = Alignment.Center) { Text(t, color = T.text, fontSize = 18.sp) }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        exprs.forEachIndexed { i, ex ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).clip(CircleShape).background(if (fns[i] != null || ex.isBlank()) colors[i] else Color(0xFFC42B1C)))
                Spacer(Modifier.size(8.dp))
                PField(ex, { exprs[i] = it }, "f${i + 1}(x) = ", Modifier.weight(1f))
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { PChip(deg, "DEG") { deg = !deg } }
    }
}

// ------------------------------------------------------------------ converter (currency and units)

/** Currency rates against the dollar: fetched when the converter opens (a free public feed, no account), kept on the phone for the next time offline. */
internal object Rates {
    /** Used until a first download has worked: rough, so the converter does something offline; the note says so. */
    private val FALLBACK = mapOf("USD" to 1.0, "EUR" to 0.92, "TRY" to 34.0, "GBP" to 0.79, "JPY" to 150.0, "CHF" to 0.88, "CAD" to 1.36, "AUD" to 1.52, "RUB" to 92.0,
        "CNY" to 7.2, "INR" to 83.0, "BRL" to 5.0, "SAR" to 3.75, "AED" to 3.67, "KRW" to 1330.0, "MXN" to 17.0, "SEK" to 10.5, "NOK" to 10.6, "PLN" to 4.0, "UAH" to 40.0)
    var rates by mutableStateOf<Map<String, Double>>(FALLBACK)
    var updated by mutableLongStateOf(0L)

    fun load(c: Context) {
        val p = c.getSharedPreferences("calc_rates", Context.MODE_PRIVATE)
        runCatching { JSONObject(p.getString("json", "") ?: "").let { o -> rates = o.keys().asSequence().associateWith { o.getDouble(it) }; updated = p.getLong("time", 0L) } }
    }

    suspend fun refresh(c: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val con = URL("https://open.er-api.com/v6/latest/USD").openConnection() as HttpURLConnection
            try {
                con.connectTimeout = 6000; con.readTimeout = 8000
                val r = JSONObject(con.inputStream.bufferedReader().readText()).getJSONObject("rates")
                val m = r.keys().asSequence().associateWith { r.getDouble(it) }
                if (m.size > 20) {
                    rates = m; updated = System.currentTimeMillis()
                    c.getSharedPreferences("calc_rates", Context.MODE_PRIVATE).edit().putString("json", r.toString()).putLong("time", updated).apply()
                    true
                } else false
            } finally { con.disconnect() }
        }.getOrDefault(false)
    }
}

private class UnitDef(val name: String, val factor: Double)
private val UNITS: List<Triple<String, String, List<UnitDef>>> = listOf(
    Triple("length", "Length|Uzunluk", listOf(UnitDef("mm", .001), UnitDef("cm", .01), UnitDef("m", 1.0), UnitDef("km", 1000.0), UnitDef("in", .0254), UnitDef("ft", .3048), UnitDef("yd", .9144), UnitDef("mi", 1609.344))),
    Triple("mass", "Mass|Kütle", listOf(UnitDef("mg", 1e-6), UnitDef("g", .001), UnitDef("kg", 1.0), UnitDef("t", 1000.0), UnitDef("oz", .0283495), UnitDef("lb", .45359237))),
    Triple("volume", "Volume|Hacim", listOf(UnitDef("ml", .001), UnitDef("l", 1.0), UnitDef("m³", 1000.0), UnitDef("tsp", .00492892), UnitDef("cup", .236588), UnitDef("gal", 3.78541))),
    Triple("speed", "Speed|Hız", listOf(UnitDef("m/s", 1.0), UnitDef("km/h", .277778), UnitDef("mph", .44704), UnitDef("kn", .514444))),
    Triple("area", "Area|Alan", listOf(UnitDef("cm²", 1e-4), UnitDef("m²", 1.0), UnitDef("ha", 10000.0), UnitDef("km²", 1e6), UnitDef("ft²", .092903), UnitDef("ac", 4046.86))),
    Triple("data", "Data|Veri", listOf(UnitDef("B", 1.0), UnitDef("KB", 1024.0), UnitDef("MB", 1048576.0), UnitDef("GB", 1073741824.0), UnitDef("TB", 1099511627776.0))),
    Triple("temp", "Temperature|Sıcaklık", listOf(UnitDef("°C", 0.0), UnitDef("°F", 0.0), UnitDef("K", 0.0))),
)

private fun toC(v: Double, u: String) = when (u) { "°F" -> (v - 32) * 5 / 9; "K" -> v - 273.15; else -> v }
private fun fromC(c: Double, u: String) = when (u) { "°F" -> c * 9 / 5 + 32; "K" -> c + 273.15; else -> c }

/** Currency (dollar, euro, lira ... with live rates) and the units: length, mass, volume, speed, area, data, temperature. */
@Composable
internal fun ConvertPane() {
    val ctx = LocalContext.current
    var cat by rememberSaveable { mutableStateOf("currency") }
    var amount by rememberSaveable { mutableStateOf("1") }
    var from by rememberSaveable { mutableStateOf("USD") }
    var to by rememberSaveable { mutableStateOf("TRY") }
    var ufrom by remember(cat) { mutableStateOf(UNITS.firstOrNull { it.first == cat }?.third?.getOrNull(2)?.name ?: "") }
    var uto by remember(cat) { mutableStateOf(UNITS.firstOrNull { it.first == cat }?.third?.getOrNull(3)?.name ?: "") }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        Rates.load(ctx)
        note = if (Rates.refresh(ctx)) "" else tr("Offline: the last rates are used", "Çevrimdışı: son kurlar kullanılıyor")
    }
    val a = amount.replace(',', '.').toDoubleOrNull()
    val unitList = UNITS.firstOrNull { it.first == cat }?.third.orEmpty()
    val result: Double? = a?.let {
        if (cat == "currency") { val rf = Rates.rates[from]; val rt = Rates.rates[to]; if (rf != null && rt != null && rf != 0.0) it / rf * rt else null }
        else if (cat == "temp") fromC(toC(it, ufrom), uto)
        else { val f = unitList.firstOrNull { u -> u.name == ufrom }?.factor; val t = unitList.firstOrNull { u -> u.name == uto }?.factor; if (f != null && t != null) it * f / t else null }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(cat == "currency", tr("Currency", "Döviz")) { cat = "currency" }
            UNITS.forEach { (k, n, _) -> PChip(cat == k, n.split('|').let { tr(it[0], it[1]) }) { cat = k } }
        }
        PField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' } }, tr("Amount", "Miktar"), Modifier.fillMaxWidth())
        val codes = if (cat == "currency") listOf("USD", "EUR", "TRY", "GBP", "JPY", "CHF", "CAD", "AUD", "RUB", "CNY", "INR", "BRL", "SAR", "AED", "KRW", "MXN", "SEK", "NOK", "PLN", "UAH").filter { it in Rates.rates } else unitList.map { it.name }
        val curFrom = if (cat == "currency") from else ufrom; val curTo = if (cat == "currency") to else uto
        Text(tr("From", "Şundan"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { codes.forEach { c -> PChip(curFrom == c, c) { if (cat == "currency") from = c else ufrom = c } } }
        Text(tr("To", "Şuna"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { codes.forEach { c -> PChip(curTo == c, c) { if (cat == "currency") to = c else uto = c } } }
        Row(Modifier.fillMaxWidth().clickable { if (cat == "currency") { val t = from; from = to; to = t } else { val t = ufrom; ufrom = uto; uto = t } }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            PIcon("rotate", T.sub, 22.dp)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(T.panel).padding(16.dp), horizontalAlignment = Alignment.End) {
            Text(result?.let { Sci.show(it) } ?: "—", color = T.text, fontSize = 40.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(curTo, color = Look.accent, fontSize = 16.sp)
            if (cat == "currency") {
                val r = Rates.rates[from]?.let { rf -> Rates.rates[to]?.let { rt -> rt / rf } }
                if (r != null) Text("1 $from = ${Sci.show(r)} $to", color = T.sub, fontSize = 13.sp)
            }
        }
        if (cat == "currency") {
            val t = Rates.updated
            Text(if (t > 0L) tr("Rates of ", "Kurlar: ") + java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t)) else tr("Rough rates until the first download", "İlk indirmeye kadar yaklaşık kurlar"), color = T.sub, fontSize = 12.sp)
            if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ programmer

/** Programmer: numbers in hex, decimal, octal and binary at once, and the integer operations (and, or, xor, shifts, not, mod). */
@Composable
internal fun ProgrammerPane(classic: Boolean) {
    var base by rememberSaveable { mutableIntStateOf(10) }
    var cur by rememberSaveable { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Long?>(null) }
    var op by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf(true) }
    val value = cur.toLongOrNull(base) ?: 0L
    fun apply(a: Long, o: String, b: Long): Long = when (o) {
        "+" -> a + b; "−" -> a - b; "×" -> a * b; "÷" -> if (b == 0L) 0L else a / b; "MOD" -> if (b == 0L) 0L else a % b
        "AND" -> a and b; "OR" -> a or b; "XOR" -> a xor b; "<<" -> a shl b.toInt().coerceIn(0, 63); ">>" -> a shr b.toInt().coerceIn(0, 63); else -> b
    }
    fun show(v: Long) = java.lang.Long.toString(v, base).uppercase()
    fun press(k: String) {
        when (k) {
            "AC" -> { cur = "0"; acc = null; op = ""; fresh = true }
            "⌫" -> cur = cur.dropLast(1).ifEmpty { "0" }
            "NOT" -> { cur = show(value.inv()); fresh = true }
            "+", "−", "×", "÷", "MOD", "AND", "OR", "XOR", "<<", ">>" -> { acc = if (acc != null && !fresh) apply(acc!!, op, value) else value; cur = show(acc!!); op = k; fresh = true }
            "=" -> { acc?.let { cur = show(apply(it, op, value)) }; acc = null; op = ""; fresh = true }
            else -> if (k.toIntOrNull(16)?.let { it < base } == true) { cur = if (fresh || cur == "0") k else if (cur.length < 20) cur + k else cur; fresh = false }
        }
    }
    Column(Modifier.fillMaxSize()) {
        CalcDisplay(if (op.isNotEmpty()) "${acc?.let { show(it) } ?: ""} $op" else "", cur, classic)
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(16 to "HEX", 10 to "DEC", 8 to "OCT", 2 to "BIN").forEach { (b, n) ->
                PChip(base == b, n) { val v = value; base = b; cur = show(v); fresh = true }
            }
        }
        Column(Modifier.fillMaxWidth()) {
            listOf(16 to "HEX", 10 to "DEC", 8 to "OCT", 2 to "BIN").forEach { (b, n) ->
                Row(Modifier.fillMaxWidth()) {
                    Text(n, color = T.sub, fontSize = 12.sp, modifier = Modifier.size(width = 44.dp, height = 18.dp))
                    Text(java.lang.Long.toString(value, b).uppercase().chunked(if (b == 2) 4 else 3).joinToString(" "), color = T.text, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        val rows = listOf(
            listOf("AND", "OR", "XOR", "NOT"), listOf("<<", ">>", "MOD", "÷"), listOf("D", "E", "F", "×"), listOf("A", "B", "C", "−"),
            listOf("7", "8", "9", "+"), listOf("4", "5", "6", "⌫"), listOf("1", "2", "3", "AC"), listOf("00", "0", "=", "="),
        )
        CalcPad(rows, classic, Modifier.weight(1f)) { k -> if (k == "00") { press("0"); press("0") } else press(k) }
        Spacer(Modifier.height(8.dp))
    }
}
