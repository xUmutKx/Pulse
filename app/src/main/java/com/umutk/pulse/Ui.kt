package com.umutk.pulse

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val T get() = Look.current

private fun material(k: String): ImageVector = when (k) {
    "processes" -> Icons.Outlined.Apps; "performance" -> Icons.Outlined.ShowChart; "history" -> Icons.Outlined.History
    "startup" -> Icons.Outlined.RocketLaunch; "users" -> Icons.Outlined.People; "details" -> Icons.Outlined.FormatListBulleted
    "services" -> Icons.Outlined.Extension; "settings" -> Icons.Outlined.Settings; "menu" -> Icons.Outlined.Menu
    "cpu" -> Icons.Outlined.DeveloperBoard; "memory" -> Icons.Outlined.Memory; "disk" -> Icons.Outlined.Storage
    "wifi" -> Icons.Outlined.Wifi; "gpu" -> Icons.Outlined.Tv; "battery" -> Icons.Outlined.BatteryFull
    "thermal" -> Icons.Outlined.Thermostat; "search" -> Icons.Outlined.Search; "close" -> Icons.Outlined.Close
    "palette" -> Icons.Outlined.Palette; "speed" -> Icons.Outlined.Timer; "info" -> Icons.Outlined.Info
    "table" -> Icons.Outlined.TableChart; "shield" -> Icons.Outlined.Shield; else -> Icons.Outlined.Info
}

/** Material icons for the Material / AMOLED themes, Fluent (Windows 11 style) outlines for the Windows ones. */
@Composable
fun PIcon(k: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    if (T.material) { Icon(material(k), null, modifier.size(size), tint); return }
    val paths = remember(k) { (FluentPaths.map[k] ?: FluentPaths.map["info"]!!).map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}

/** Windows-like running graph: 60 s of history, grid, filled area. [max] is the full-scale value. */
@Composable
fun Graph(series: List<List<Float>>, colors: List<Color>, max: Float, modifier: Modifier = Modifier, height: Dp = 120.dp, grid: Boolean = true, n: Int = 60) {
    val t = T
    val classic = t.classicGraph
    val bgc = if (classic) Color.Black else colors[0].copy(alpha = if (t.dark) .08f else .06f)
    val gridc = if (classic) Color(0xFF0B5D0B) else colors[0].copy(alpha = .22f)
    Canvas(modifier.fillMaxWidth().height(height).background(bgc).border(1.dp, if (classic) Color(0xFF808080) else colors[0].copy(alpha = .8f))) {
        val w = size.width; val h = size.height
        if (grid) {
            for (i in 1..9) drawLine(gridc, Offset(w * i / 10, 0f), Offset(w * i / 10, h))
            for (i in 1..3) drawLine(gridc, Offset(0f, h * i / 4), Offset(w, h * i / 4))
        }
        series.forEachIndexed { si, v ->
            if (v.size < 2) return@forEachIndexed
            val col = if (classic) (if (si == 0) Color(0xFF00FF00) else Color(0xFFFFFF00)) else colors[si.coerceAtMost(colors.lastIndex)]
            val line = Path(); val fill = Path()
            val step = w / (n - 1f); val x0 = w - step * (v.size - 1)
            v.forEachIndexed { i, y ->
                val p = Offset(x0 + step * i, h * (1 - (y / max).coerceIn(0f, 1f)))
                if (i == 0) { line.moveTo(p.x, p.y); fill.moveTo(p.x, h); fill.lineTo(p.x, p.y) } else { line.lineTo(p.x, p.y); fill.lineTo(p.x, p.y) }
            }
            fill.lineTo(w, h); fill.close()
            if (si == 0 && !classic) drawPath(fill, col.copy(alpha = .28f))
            drawPath(line, col, style = Stroke(if (classic) 2f else 2.5f))
        }
    }
}

@Composable
fun BarMeter(frac: Float, color: Color, modifier: Modifier = Modifier, h: Dp = 10.dp) {
    val t = T
    val shape = RoundedCornerShape(if (t.tabs) 0.dp else h / 2)
    Box(modifier.fillMaxWidth().height(h).background(if (t.dark) Color(0xFF2A2A2A) else Color(0x22000000), shape)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().background(color, shape))
    }
}

@Composable
fun Stat(label: String, value: String, big: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = T.sub, fontSize = 12.sp)
        FitText(value, T.text, if (big) 24.sp else 14.sp)
    }
}

@Composable
fun InfoRows(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (k, v) -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Text(k, Modifier.weight(1f), color = T.sub, fontSize = 13.sp); Text(v, Modifier.weight(1.4f), color = T.text, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End) } }
    }
}

/** A card in the current theme: rounded for 11 / Material, square with an edge for the old ones. */
@Composable
fun PCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val t = T
    val shape = RoundedCornerShape(t.radius)
    val fx = if (t.id == "win11") Look.effect else "none"
    Column(modifier.fillMaxWidth().then(if (t.tabs) Modifier.background(t.bg, shape) else if (fx != "none") Modifier.layer(fx, shape) else Modifier.background(t.panel, shape)).then(if (t.bevel) Modifier.bevel(false) else if (t.tabs) Modifier.border(1.dp, t.line) else if (!t.material) Modifier.border(1.dp, t.line, shape) else Modifier)
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

fun rate(b: Float) = if (b >= 1 shl 20) "%.1f MB/s".format(b / 1048576f) else "%.0f KB/s".format(b / 1024f)
fun mb(v: Int) = if (v >= 1024) "%.1f GB".format(v / 1024f) else "$v MB"
fun hms(ms: Long): String { val s = ms / 1000; return if (s >= 3600) "${s / 3600} h ${s % 3600 / 60} min" else if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s" }

@Composable
fun Center(text: String, modifier: Modifier = Modifier) =
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = T.sub, fontSize = 14.sp) }

/** One-line text that shrinks to fit instead of wrapping onto a hidden second line. */
@Composable
fun FitText(text: String, color: Color, size: androidx.compose.ui.unit.TextUnit, modifier: Modifier = Modifier, weight: androidx.compose.ui.text.font.FontWeight? = null, minSize: Float = 9f) {
    var sz by remember(text, size) { mutableStateOf(size) }
    androidx.compose.material3.Text(text, modifier, color = color, fontSize = sz, fontWeight = weight, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        onTextLayout = { if (it.didOverflowWidth && sz.value > minSize) sz = androidx.compose.ui.unit.TextUnit(sz.value * 0.92f, androidx.compose.ui.unit.TextUnitType.Sp) })
}
