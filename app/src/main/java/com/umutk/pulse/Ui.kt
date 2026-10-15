package com.umutk.pulse

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import com.umutk.pulse.features.ThemeIcons
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val T get() = Look.current

/** Page margin: 16 dp in the Material look (M3), 12 dp in the Windows looks. */
val pageGutter get() = if (T.material) 16.dp else 12.dp

private fun material(k: String): ImageVector = when (k) {
    "processes" -> Icons.Outlined.Apps; "apps" -> Icons.Outlined.Apps; "performance" -> Icons.Outlined.ShowChart; "history" -> Icons.Outlined.History
    "startup" -> Icons.Outlined.RocketLaunch; "users" -> Icons.Outlined.People; "details" -> Icons.Outlined.FormatListBulleted
    "services" -> Icons.Outlined.Extension; "settings" -> Icons.Outlined.Settings; "menu" -> Icons.Rounded.Menu
    "cpu" -> Icons.Outlined.DeveloperBoard; "memory" -> Icons.Outlined.Memory; "disk" -> Icons.Outlined.Storage
    "wifi" -> Icons.Outlined.Wifi; "gpu" -> Icons.Outlined.Tv; "battery" -> Icons.Outlined.BatteryFull
    "thermal" -> Icons.Outlined.Thermostat; "search" -> Icons.Outlined.Search; "close" -> Icons.Outlined.Close; "check" -> Icons.Outlined.Check
    "palette" -> Icons.Outlined.Palette; "speed" -> Icons.Outlined.Timer; "info" -> Icons.Outlined.Info
    "table" -> Icons.Outlined.TableChart; "heart" -> Icons.Outlined.FavoriteBorder
    "download" -> Icons.Outlined.Download; "delete", "recycle" -> Icons.Outlined.Delete; "folder" -> Icons.Outlined.Folder
    "filetypes" -> Icons.Outlined.PieChart; "clean" -> Icons.Outlined.CleaningServices; "chevron" -> Icons.Outlined.ExpandMore; "back" -> Icons.AutoMirrored.Outlined.ArrowBack
    "copy" -> Icons.Outlined.ContentCopy; "move" -> Icons.Outlined.DriveFileMove; "rename" -> Icons.Outlined.DriveFileRenameOutline
    "newfolder" -> Icons.Outlined.CreateNewFolder; "paste" -> Icons.Outlined.ContentPaste
    "extract" -> Icons.Outlined.Archive; "view" -> Icons.Outlined.Visibility
    "compass" -> Icons.Outlined.Explore; "level" -> Icons.Outlined.Straighten; "flash" -> Icons.Outlined.FlashlightOn
    "light" -> Icons.Outlined.LightMode; "dice" -> Icons.Outlined.Casino; "coin" -> Icons.Outlined.Paid; "clipboard" -> Icons.Outlined.ContentPaste
    "screenshot" -> Icons.Outlined.Screenshot; "sensors" -> Icons.Outlined.Sensors; "vibrate" -> Icons.Outlined.Vibration; "stopwatch" -> Icons.Outlined.Timer
    "timer" -> Icons.Outlined.HourglassBottom; "calc" -> Icons.Outlined.Calculate; "scale" -> Icons.Outlined.AspectRatio; "power" -> Icons.Outlined.PowerSettingsNew
    "volume" -> Icons.Outlined.VolumeUp
    "compass" -> Icons.Outlined.Explore; "level" -> Icons.Outlined.Balance; "flash" -> Icons.Outlined.FlashlightOn
    "light" -> Icons.Outlined.LightMode; "dice" -> Icons.Outlined.Casino; "coin" -> Icons.Outlined.MonetizationOn
    "clipboard" -> Icons.Outlined.ContentPaste; "screenshot" -> Icons.Outlined.Screenshot; "sensors" -> Icons.Outlined.Sensors
    "volume" -> Icons.Outlined.VolumeUp; "vibrate" -> Icons.Outlined.Vibration; "stopwatch" -> Icons.Outlined.Schedule
    "timer" -> Icons.Outlined.HourglassEmpty; "calc" -> Icons.Outlined.Calculate; "scale" -> Icons.Outlined.FormatSize
    "power" -> Icons.Outlined.PowerSettingsNew
    "phone" -> Icons.Outlined.PhoneAndroid; "play" -> Icons.Outlined.PlayArrow; "run" -> Icons.Outlined.PlayArrow; "pause" -> Icons.Outlined.Pause; "sort" -> Icons.Outlined.SwapVert; "book" -> Icons.Outlined.MenuBook; "snail" -> Icons.Outlined.HourglassEmpty; else -> Icons.Outlined.Info
}

/** Material icons for the Material / AMOLED themes, Fluent (Windows 11 style) outlines for the Windows ones. */
@Composable
fun PIcon(k: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    if (k == "recycle" && !T.material) { BinIcon(tint, size, modifier); return }
    // Windows 11: the Fluency PNG pack (one distinct icon per key) first, FluentPaths vectors only for a key without a PNG
    if (T.id == "win11" && !T.material) ThemeIcons.bitmap(ctx, "win11", k)?.let { Image(it, null, modifier.size(size)); return }
    // the bin is always the plain bin icon, never the Fluent glyph that has a cross in it
    if (k == "delete" && !T.material) { BinIcon(tint, size, modifier); return }
    if (T.material) {
        // downloaded Material Symbols (res/drawable/mat_<key>.xml); the built-in set is the fallback for a missing one
        val id = remember(k) { ctx.resources.getIdentifier("mat_$k", "drawable", ctx.packageName) }
        if (id != 0) Image(painterResource(id), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
        else Icon(material(k), null, modifier.size(size), tint)
        return
    }
    // each design keeps its own icon set: Win95 / XP / Win7 their PNG packs, Win10 and Win11 only the Fluent vectors (Fluent is the last resort for the rest)
    val pack = when (T.id) {
        "win95" -> ThemeIcons.bitmap(ctx, "win95", k)
        "xp" -> ThemeIcons.bitmap(ctx, "xp", k) ?: XP_ICON_ALIAS[k]?.let { ThemeIcons.bitmap(ctx, "xp", it) }
        "win7" -> ThemeIcons.bitmap(ctx, "win7", k) ?: ICON7_ALIAS[k]?.let { ThemeIcons.bitmap(ctx, "win7", it) }
        else -> null
    }
    if (pack != null) { Image(pack, null, modifier.size(size)); return }
    if (T.id == "win11" && FluentPaths.map[k] == null) ThemeIcons.bitmap(ctx, "win11", k)?.let { Image(it, null, modifier.size(size)); return }
    val paths = remember(k) { (FluentPaths.map[k] ?: FluentPaths.map["info"]!!).map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}

/** An icon of the left menu: on Windows 11 the first icon set while "Old icons" is on in Settings > Functions, otherwise the look's own icon. */
@Composable
fun PNavIcon(k: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    if (T.id == "win11" && !T.material && Opt.oldNavIcons) {
        val ctx = LocalContext.current
        val old = ThemeIcons.bitmap(ctx, "win11old", k)
        if (old != null) { Image(old, null, modifier.size(size)); return }
    }
    PIcon(k, tint, size, modifier)
}

/** Windows-like running graph: 60 s of history, grid, filled area. [max] is the full-scale value. */
@Composable
fun Graph(series: List<List<Float>>, colors: List<Color>, max: Float, modifier: Modifier = Modifier, height: Dp = 120.dp, grid: Boolean = true, n: Int = 60) {
    val t = T
    if (t.material) {
        // M3 Expressive: tonal rounded surface, dotted guides, smooth lines with a fading fill
        val r = if (height < 60.dp) 12.dp else 20.dp
        Canvas(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(r)).background(if (t.id == "amoled") Color.Black else colors[0].copy(alpha = .08f))) {
            val w = size.width; val h = size.height
            if (grid) m3Guides(t.line)
            series.forEachIndexed { si, v ->
                if (v.size < 2) return@forEachIndexed
                val step = w / (n - 1f); val x0 = w - step * (v.size - 1)
                val pad = 3.dp.toPx() // keeps the round cap inside the clip at full scale
                val pts = v.mapIndexed { i, y -> Offset(x0 + step * i, pad + (h - 2 * pad) * (1 - (y / max).coerceIn(0f, 1f))) }
                m3Line(pts, colors[si.coerceAtMost(colors.lastIndex)], h, fill = si == 0)
            }
        }
        return
    }
    val classic = t.classicGraph
    val bgc = if (classic) Color.Black else colors[0].copy(alpha = if (t.dark) .08f else .06f)
    val gridc = if (win7Authentic || xpAuthentic) Color(0xFF008040) else if (classic) Color(0xFF0B5D0B) else colors[0].copy(alpha = .22f)
    Canvas(modifier.fillMaxWidth().height(height).background(bgc).then(if (win95Authentic) Modifier.bevel(false) else Modifier.border(1.dp, if (classic) Color(0xFF808080) else colors[0].copy(alpha = .8f)))) {
        val w = size.width; val h = size.height
        if (grid && (win7Authentic || xpAuthentic)) { // Win7 / XP Task Manager: square cells from the bottom-right corner
            val c = 12.dp.toPx()
            var x = w; while (x > 0f) { drawLine(gridc, Offset(x, 0f), Offset(x, h)); x -= c }
            var y = h; while (y > 0f) { drawLine(gridc, Offset(0f, y), Offset(w, y)); y -= c }
        } else if (grid) {
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

/** M3 chart guides: three faint dotted horizontal lines. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.m3Guides(c: Color) {
    val dots = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(1f, 6.dp.toPx()))
    for (i in 1..3) { val y = size.height * i / 4; drawLine(c.copy(alpha = .5f), Offset(0f, y), Offset(size.width, y), 1.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round, dots) }
}

/** M3 chart line: cubic curve through [pts], round caps, optional accent-to-transparent fill down to [bottom]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.m3Line(pts: List<Offset>, color: Color, bottom: Float, fill: Boolean = true) {
    val line = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        // horizontal tangents: smooth without overshooting the data
        for (i in 1 until pts.size) { val a = pts[i - 1]; val b = pts[i]; val mx = (a.x + b.x) / 2; cubicTo(mx, a.y, mx, b.y, b.x, b.y) }
    }
    if (fill) {
        val area = Path().apply { addPath(line); lineTo(pts.last().x, bottom); lineTo(pts[0].x, bottom); close() }
        drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = .38f), Color.Transparent), 0f, bottom))
    }
    drawPath(line, color, style = Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

@Composable
fun LegacyUsageBar(frac: Float, color: Color, modifier: Modifier = Modifier, h: Dp = 10.dp) {
    val t = T
    if (t.material) {
        // M3 Expressive progress: rounded active part, a gap, then a tonal track
        val f = frac.coerceIn(0f, 1f)
        Canvas(modifier.fillMaxWidth().height(h)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2); val gap = if (f in .001f..0.999f) 4.dp.toPx() else 0f
            val aw = size.width * f
            drawRoundRect(color.copy(alpha = .24f), Offset(aw + gap, 0f), Size((size.width - aw - gap).coerceAtLeast(0f), size.height), r)
            if (aw > 0f) drawRoundRect(color, Offset.Zero, Size(aw.coerceAtLeast(size.height), size.height), r)
        }
        return
    }
    if (win95Authentic) { // 95 progress bar: sunken grey well filled with navy blocks
        val f = frac.coerceIn(0f, 1f)
        Canvas(modifier.fillMaxWidth().height(h.coerceAtLeast(8.dp)).background(Color(0xFFC0C0C0)).bevel(false)) {
            val p = 2.dp.toPx(); val bh = size.height - 2 * p; val bw = (bh * .6f).coerceAtLeast(3.dp.toPx()); val end = p + (size.width - 2 * p) * f
            var x = p
            while (x < end) { drawRect(Color(0xFF000080), Offset(x, p), Size(minOf(bw, end - x), bh)); x += bw + 1.dp.toPx() }
        }
        return
    }
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
    val shape = RoundedCornerShape(if (t.material) t.radius.coerceIn(16.dp, 28.dp) else t.radius) // M3 cards: 16 to 28 dp corners
    val fx = if (t.id == "win11") Look.effect else "none"
    val surface = when {
        // Material first: its glassA is for the window backdrop only; cards stay tonal with a soft elevation
        t.material -> Modifier.shadow(2.dp, shape).background(t.panel, shape)
        // Win7 group box: white with a pale blue-grey rounded edge
        win7Authentic -> Modifier.background(Color.White, shape).border(1.dp, Color(0xFFD5DFE5), shape)
        // 95 group box: grey, etched edge (grey line with a white line 1 px inside)
        win95Authentic -> Modifier.background(Color(0xFFC0C0C0)).drawBehind {
            val p = 1.dp.toPx()
            drawRect(Color.White, Offset(p, p), Size(size.width - p, size.height - p), style = Stroke(p))
            drawRect(Color(0xFF808080), Offset(0f, 0f), Size(size.width - p, size.height - p), style = Stroke(p))
        }
        // liquid glass: the panel is see-through with a light edge (Win 7 Aero keeps its bevel-free look, Win 95 its bevel)
        t.glassA > 0f -> Modifier.background(t.panel.copy(alpha = t.glassA), shape).then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, glassEdge(t), shape))
        t.tabs -> Modifier.background(t.bg, shape).then(if (t.bevel) Modifier.bevel(false) else Modifier.border(1.dp, t.line))
        fx != "none" -> Modifier.layer(fx, shape).border(1.dp, t.line, shape)
        else -> Modifier.background(t.panel.copy(alpha = .9f), shape).border(1.dp, t.line, shape)
    }
    Column(modifier.fillMaxWidth().then(surface).padding(if (t.material) 16.dp else 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
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

/** The spinner used by every "loading" state: the text colour and a thin ring, so Material does not show its pink accent. */
@Composable
fun PLoader(modifier: Modifier = Modifier) {
    androidx.compose.material3.CircularProgressIndicator(modifier.size(28.dp), color = T.text, strokeWidth = 2.dp)
}

/** XP icon keys the pack lacks, drawn with the nearest XP icon it has. */
val XP_ICON_ALIAS = mapOf("power" to "run", "speed" to "performance", "bookmark" to "heart", "view" to "search", "copy" to "folder", "paste" to "folder", "rename" to "details", "extract" to "folder")
