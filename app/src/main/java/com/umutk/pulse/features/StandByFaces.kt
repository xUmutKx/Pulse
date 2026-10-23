package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** The clock faces of the iOS StandBy app, redrawn here: segmented LED, big cropped digits, flip cards, neon, matrix rain, pixels, roman numerals, a word grid, a solar arc, rings... */

/** The time as the cover shows it: hours in the 12 or 24 hour form of the setting, minutes and seconds with two digits. */
internal class Clk(val hh: String, val mm: String, val ss: String, val cal: Calendar)

internal fun clk(ctx: Context, tick: Long): Clk {
    val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + tick * 0 }
    val h = if (StandByPrefs.h24(ctx)) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    return Clk(if (StandByPrefs.h24(ctx)) "%02d".format(h) else h.toString(), "%02d".format(cal.get(Calendar.MINUTE)), "%02d".format(cal.get(Calendar.SECOND)), cal)
}

@Composable
private fun Dp.sp2(): TextUnit = with(LocalDensity.current) { this@sp2.toSp() }

// ------------------------------------------------------------------ segmented LED

@Composable
internal fun SegmentedFace(ctx: Context, ink: Color, tick: Long, wide: Boolean) {
    val c = clk(ctx, tick)
    val sec = StandByPrefs.seconds(ctx) || wide
    val colon = if (tick % 2 == 0L) ":" else " "
    val txt = c.hh + colon + c.mm + (if (sec) colon + c.ss else "")
    val ghost = txt.map { if (it == ':' || it == ' ') ':' else '8' }.joinToString("")
    val f = clockFont(if (wide) "seg7b" else "seg7")
    val line = TextStyle(drawStyle = if (StandByPrefs.outline(ctx)) Stroke(width = 4f) else Fill)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (maxHeight > maxWidth * 1.1f) {
            // an upright phone: hours over minutes (over seconds)
            val parts = listOf(c.hh, c.mm) + (if (sec) listOf(c.ss) else emptyList())
            val size = min(maxWidth.value / (2 * f.adv), maxHeight.value / parts.size * .78f).coerceAtLeast(24f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                parts.forEach { t ->
                    Box(contentAlignment = Alignment.Center) {
                        Text("88", color = ink.copy(alpha = .1f), fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false)
                        Text(t, color = ink, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false, style = line)
                    }
                }
            }
        } else {
            val size = min(maxWidth.value / (txt.length * f.adv), maxHeight.value * .8f).coerceAtLeast(24f)
            Box(contentAlignment = Alignment.Center) {
                Text(ghost, color = ink.copy(alpha = .1f), fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false)
                Text(txt, color = ink, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false, style = line)
            }
        }
    }
}

// ------------------------------------------------------------------ big crop clock

@Composable
internal fun BigCropFace(ctx: Context, tick: Long, idx: Int) {
    val st = CROP_STYLES[idx.mod(CROP_STYLES.size)]
    val c = clk(ctx, tick)
    val bg = if (st.bg.size > 1) Brush.linearGradient(st.bg) else SolidColor(st.bg[0])
    val f = clockFont("bebas")
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(bg), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // two lines of digits that fill the whole box; the hours and the minutes are cropped by its edges
            val size = min(maxHeight.value * .62f, maxWidth.value * 1.1f)
            val style = if (st.text.size > 1) TextStyle(brush = Brush.verticalGradient(st.text)) else TextStyle(color = st.text[0])
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.hh, style = style, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false, modifier = Modifier.offset(y = (size * .09f).dp))
                Text(c.mm, style = style, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false, modifier = Modifier.offset(y = (-size * .09f).dp))
            }
        }
    }
}

// ------------------------------------------------------------------ retro flip

private val TopHalf = GenericShape { s, _ -> addRect(Rect(0f, 0f, s.width, s.height / 2f)) }
private val BottomHalf = GenericShape { s, _ -> addRect(Rect(0f, s.height / 2f, s.width, s.height)) }

@Composable
private fun FlipCard(value: String, st: FlipStyle, modifier: Modifier) {
    var shown by remember { mutableStateOf(value) }
    var old by remember { mutableStateOf(value) }
    val p = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value != shown) { old = shown; shown = value; p.snapTo(0f); p.animateTo(1f, tween(600)) }
    }
    val f = clockFont("rubik")
    BoxWithConstraints(modifier) {
        val fs = maxHeight.value * .62f
        @Composable fun face(t: String) = Box(Modifier.fillMaxSize().background(st.card), contentAlignment = Alignment.Center) {
            Text(t, color = st.text, fontSize = fs.sp, fontFamily = f.family, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))) {
            // the new top and the old bottom lie still; the top flap turns down over the old bottom, then the new bottom flap turns down over it
            Box(Modifier.fillMaxSize().clip(TopHalf)) { face(shown) }
            Box(Modifier.fillMaxSize().clip(BottomHalf)) { face(if (p.value < .5f) old else shown) }
            if (p.value < .5f) Box(Modifier.fillMaxSize().graphicsLayer { rotationX = -180f * p.value; cameraDistance = 14f * density }.clip(TopHalf)) { face(old) }
            else Box(Modifier.fillMaxSize().graphicsLayer { rotationX = 180f * (1f - p.value); cameraDistance = 14f * density }.clip(BottomHalf)) { face(shown) }
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Color.Black.copy(alpha = .55f)))
        }
    }
}

@Composable
internal fun RetroFlipFace(ctx: Context, tick: Long, idx: Int) {
    val st = FLIP_STYLES[idx.mod(FLIP_STYLES.size)]
    val c = clk(ctx, tick)
    val bg = if (st.glow.size > 1) Brush.radialGradient(st.glow) else SolidColor(st.bg)
    val sec = StandByPrefs.seconds(ctx)
    BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(bg).padding(18.dp)) {
        if (maxHeight > maxWidth * 1.1f) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            FlipCard(c.hh, st, Modifier.weight(1f).fillMaxWidth())
            FlipCard(c.mm, st, Modifier.weight(1f).fillMaxWidth())
            if (sec) FlipCard(c.ss, st, Modifier.weight(.5f).fillMaxWidth())
        } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            FlipCard(c.hh, st, Modifier.weight(1f).fillMaxHeight())
            FlipCard(c.mm, st, Modifier.weight(1f).fillMaxHeight())
            if (sec) FlipCard(c.ss, st, Modifier.weight(.6f).fillMaxHeight())
        }
    }
}

// ------------------------------------------------------------------ neon

@Composable
internal fun NeonFace(ctx: Context, ink: Color, tick: Long) {
    val c = clk(ctx, tick)
    val txt = c.hh + (if (tick % 2 == 0L) ":" else " ") + c.mm
    val f = clockFont("tilt")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val portrait = maxHeight > maxWidth * 1.1f
        val parts = if (portrait) listOf(c.hh, c.mm) else listOf(txt)
        val size = if (portrait) min(maxWidth.value / (2 * f.adv), maxHeight.value / 2 * .75f).coerceAtLeast(24f) else min(maxWidth.value / (txt.length * f.adv), maxHeight.value * .6f).coerceAtLeast(24f)
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { parts.forEach { Text(it, color = ink.copy(alpha = .55f), fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false, style = TextStyle(shadow = Shadow(ink, Offset.Zero, 90f))) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally) { parts.forEach { Text(it, color = Color.White.copy(alpha = .9f).compositeOver2(ink), fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false, style = TextStyle(shadow = Shadow(ink, Offset.Zero, 24f))) } }
        }
    }
}

private fun Color.compositeOver2(other: Color) = Color(
    red = (red * .35f + other.red * .65f), green = (green * .35f + other.green * .65f), blue = (blue * .35f + other.blue * .65f), alpha = 1f,
)

// ------------------------------------------------------------------ matrix rain

@Composable
internal fun MatrixFace(ctx: Context, tick: Long) {
    val c = clk(ctx, tick)
    val cols = 20
    val heads = remember { FloatArray(cols) { (it * 37 % 100) / 100f } }
    val glyphs = remember { "01アイウエオカキクケコサシスセソタチツテト".toList() }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) { while (true) { delay(70); frame++; for (i in 0 until cols) heads[i] = (heads[i] + .018f + (i % 4) * .004f) % 1.5f } }
    val green = Color(0xFF00FF66)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val f = frame
            val paint = android.graphics.Paint().apply { isAntiAlias = true; textAlign = android.graphics.Paint.Align.CENTER; typeface = android.graphics.Typeface.MONOSPACE }
            val line = size.height / 14f
            paint.textSize = line * .9f
            for (i in 0 until cols) {
                val x = size.width * (i + .5f) / cols
                for (k in 0 until 10) {
                    val y = heads[i] * size.height * 1.2f - k * line
                    if (y < 0f || y > size.height) continue
                    paint.color = android.graphics.Color.argb(((1f - k / 10f) * 200).toInt(), 0, 255, 102)
                    drawContext.canvas.nativeCanvas.drawText(glyphs[(i * 7 + k * 3 + (f / 3).toInt() * (1 + i % 3)) % glyphs.size].toString(), x, y, paint)
                }
            }
        }
        val f = clockFont("kode")
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val portrait = maxHeight > maxWidth * 1.1f
            val size = if (portrait) min(maxWidth.value / (2 * f.adv), maxHeight.value * .22f).coerceAtLeast(24f) else min(maxWidth.value / (5 * f.adv), maxHeight.value * .4f).coerceAtLeast(24f)
            Text(if (portrait) c.hh + "\n" + c.mm else c.hh + ":" + c.mm, color = green, fontSize = size.sp, fontFamily = f.family, maxLines = if (portrait) 2 else 1, softWrap = false, textAlign = TextAlign.Center, lineHeight = (size * 1.05f).sp,
                style = TextStyle(shadow = Shadow(green, Offset.Zero, 30f)), modifier = Modifier.background(Color.Black.copy(alpha = .55f)).padding(horizontal = 12.dp))
        }
    }
}

// ------------------------------------------------------------------ pixel art

private val PIXEL_DIGITS = listOf(
    "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
    "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111",
)

@Composable
internal fun PixelFace(ctx: Context, ink: Color, tick: Long) {
    val c = clk(ctx, tick)
    val digits = (("%02d".format(c.hh.toInt())) + c.mm).map { it - '0' }
    val colonOn = tick % 2 == 0L
    Canvas(Modifier.fillMaxSize()) {
        val portrait = size.height > size.width * 1.1f
        // wide: HH : MM on one line (17 x 5 cells); upright: HH over MM (7 x 11 cells)
        val colsTotal = if (portrait) 7 else 17; val rows = if (portrait) 11 else 5
        val cell = min(size.width / colsTotal, size.height / (rows * (if (portrait) 1.15f else 1.6f)))
        val x0 = (size.width - cell * colsTotal) / 2f; val y0 = (size.height - cell * rows) / 2f
        fun px(col: Int, row: Int, on: Boolean) = drawRoundRect(if (on) ink else ink.copy(alpha = .07f), Offset(x0 + col * cell + cell * .06f, y0 + row * cell + cell * .06f), Size(cell * .88f, cell * .88f), CornerRadius(cell * .12f))
        fun digit(d: Int, col: Int, row: Int) { val g = PIXEL_DIGITS[d]; for (r in 0 until 5) for (cc in 0 until 3) px(col + cc, row + r, g[r * 3 + cc] == '1') }
        if (portrait) {
            digit(digits[0], 0, 0); digit(digits[1], 4, 0)
            digit(digits[2], 0, 6); digit(digits[3], 4, 6)
            px(3, 5, colonOn)
        } else {
            digit(digits[0], 0, 0); digit(digits[1], 4, 0)
            px(8, 1, colonOn); px(8, 3, colonOn)
            digit(digits[2], 10, 0); digit(digits[3], 14, 0)
        }
    }
}

// ------------------------------------------------------------------ roman

private fun roman(n: Int): String {
    if (n <= 0) return "N"
    val t = listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    var r = n; val sb = StringBuilder()
    for ((v, s) in t) while (r >= v) { sb.append(s); r -= v }
    return sb.toString()
}

@Composable
internal fun RomanFace(ctx: Context, ink: Color, tick: Long) {
    val cal = clk(ctx, tick).cal
    val h = if (StandByPrefs.h24(ctx)) cal.get(Calendar.HOUR_OF_DAY) else cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
    val f = clockFont("bodoni")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = min(maxWidth.value / (7 * .62f), maxHeight.value * .34f).coerceAtLeast(24f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(roman(h), color = ink, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false)
            Box(Modifier.width((size * 2.4f).dp).height(2.dp).background(ink.copy(alpha = .5f)))
            Text(roman(cal.get(Calendar.MINUTE)), color = ink.copy(alpha = .85f), fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false)
        }
    }
}

// ------------------------------------------------------------------ word clock

private const val WORD_GRID = "ITLISASTIME" + "ACQUARTERDC" + "TWENTYFIVEX" + "HALFBTENFTO" + "PASTERUNINE" + "ONESIXTHREE" + "FOURFIVETWO" + "EIGHTELEVEN" + "SEVENTWELVE" + "TENSEOCLOCK"

/** (row, first column, length) of each word of the grid. */
private val W = mapOf(
    "IT" to Triple(0, 0, 2), "IS" to Triple(0, 3, 2), "A" to Triple(1, 0, 1), "QUARTER" to Triple(1, 2, 7), "TWENTY" to Triple(2, 0, 6), "MFIVE" to Triple(2, 6, 4),
    "HALF" to Triple(3, 0, 4), "MTEN" to Triple(3, 5, 3), "TO" to Triple(3, 9, 2), "PAST" to Triple(4, 0, 4), "NINE" to Triple(4, 7, 4), "ONE" to Triple(5, 0, 3),
    "SIX" to Triple(5, 3, 3), "THREE" to Triple(5, 6, 5), "FOUR" to Triple(6, 0, 4), "HFIVE" to Triple(6, 4, 4), "TWO" to Triple(6, 8, 3), "EIGHT" to Triple(7, 0, 5),
    "ELEVEN" to Triple(7, 5, 6), "SEVEN" to Triple(8, 0, 5), "TWELVE" to Triple(8, 5, 6), "HTEN" to Triple(9, 0, 3), "OCLOCK" to Triple(9, 5, 6),
)
private val HOUR_WORDS = listOf("TWELVE", "ONE", "TWO", "THREE", "FOUR", "HFIVE", "SIX", "SEVEN", "EIGHT", "NINE", "HTEN", "ELEVEN")

private fun lit(m: Int, h: Int): Set<String> {
    val s = mutableSetOf("IT", "IS")
    val m5 = (m / 5) * 5
    val next = m5 >= 35
    when (m5) {
        0 -> s += "OCLOCK"
        5 -> { s += "MFIVE"; s += "PAST" }
        10 -> { s += "MTEN"; s += "PAST" }
        15 -> { s += "A"; s += "QUARTER"; s += "PAST" }
        20 -> { s += "TWENTY"; s += "PAST" }
        25 -> { s += "TWENTY"; s += "MFIVE"; s += "PAST" }
        30 -> { s += "HALF"; s += "PAST" }
        35 -> { s += "TWENTY"; s += "MFIVE"; s += "TO" }
        40 -> { s += "TWENTY"; s += "TO" }
        45 -> { s += "A"; s += "QUARTER"; s += "TO" }
        50 -> { s += "MTEN"; s += "TO" }
        else -> { s += "MFIVE"; s += "TO" }
    }
    s += HOUR_WORDS[((h + if (next) 1 else 0) % 12)]
    return s
}

@Composable
internal fun WordFace(ctx: Context, ink: Color, tick: Long) {
    val cal = clk(ctx, tick).cal
    val on = lit(cal.get(Calendar.MINUTE), cal.get(Calendar.HOUR_OF_DAY)).flatMap { w -> W[w]?.let { (r, c, n) -> (0 until n).map { r * 11 + c + it } }.orEmpty() }.toSet()
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = min(maxWidth / 11, maxHeight / 10)
        Column {
            for (r in 0 until 10) Row {
                for (c in 0 until 11) {
                    val i = r * 11 + c
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        Text(WORD_GRID[i].toString(), color = if (i in on) ink else ink.copy(alpha = .16f), fontSize = (cell * .52f).sp2(), fontWeight = if (i in on) FontWeight.Bold else FontWeight.Normal,
                            style = if (i in on) TextStyle(shadow = Shadow(ink, Offset.Zero, 18f)) else TextStyle.Default)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ solar watch

@Composable
internal fun SolarFace(ctx: Context, tick: Long, idx: Int) {
    val st = SOLAR_STYLES[idx.mod(SOLAR_STYLES.size)]
    val cal = clk(ctx, tick).cal
    val day = (cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) / 1440f   // 0..1 over the day
    val c = clk(ctx, tick)
    val f = clockFont("montserrat")
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(st.bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val r = min(w * .42f, h * .62f)
            val c0 = Offset(w / 2f, h * .66f)
            // the arc of the sun over the horizon, filled from the sunrise to the sun's place
            drawArc(Brush.sweepGradient(listOf(st.stops[0], st.text, st.stops[0]), c0), 180f, 180f, false, Offset(c0.x - r, c0.y - r), Size(r * 2, r * 2), style = Stroke(r * .05f, cap = StrokeCap.Round), alpha = .45f)
            val a = (day - .25f) / .5f      // 06:00 -> 0, 18:00 -> 1
            if (a in 0f..1f) {
                val ang = PI.toFloat() * (1f + a)
                val p = Offset(c0.x + r * cos(ang), c0.y + r * sin(ang))
                drawCircle(Brush.radialGradient(listOf(st.text.copy(alpha = .9f), Color.Transparent), p, r * .22f), r * .22f, p)
                drawCircle(st.text, r * .07f, p)
            }
            drawLine(st.text.copy(alpha = .35f), Offset(c0.x - r * 1.15f, c0.y), Offset(c0.x + r * 1.15f, c0.y), 3f)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(c.hh + ":" + c.mm, color = st.text, fontSize = 64.sp, fontFamily = f.family, fontWeight = FontWeight.Light, maxLines = 1)
            Text(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()), color = st.text.copy(alpha = .75f), fontSize = 15.sp)
        }
    }
}

// ------------------------------------------------------------------ radial invert

@Composable
internal fun RadialFace(ctx: Context, ink: Color, tick: Long) {
    val c = clk(ctx, tick)
    val cal = c.cal
    val s = cal.get(Calendar.SECOND) / 60f
    val m = (cal.get(Calendar.MINUTE) + s) / 60f
    val h = ((cal.get(Calendar.HOUR) % 12) + m) / 12f
    val f = clockFont("rubik")
    // inverted: the page is the colour of the digits and the rings are black on it
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(28.dp)).background(ink), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val base = min(size.width, size.height) / 2f - 14f
            val w = base * .09f
            listOf(h to 0f, m to 1f, s to 2f).forEach { (v, i) ->
                val r = base - i * (w * 1.7f)
                drawCircle(Color.Black.copy(alpha = .14f), r, center, style = Stroke(w))
                drawArc(Color.Black, -90f, 360f * v, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        Text(c.hh + ":" + c.mm, color = Color.Black, fontSize = 52.sp, fontFamily = f.family, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ------------------------------------------------------------------ analog variants

/** [variant]: "clean" (slim hands, hour dots), "nomark" (no markers at all), "square" (a rounded square dial). */
@Composable
internal fun AnalogVariantFace(ink: Color, tick: Long, variant: String) {
    val cal = Calendar.getInstance().apply { timeInMillis = System.currentTimeMillis() + tick * 0 }
    val s = cal.get(Calendar.SECOND); val m = cal.get(Calendar.MINUTE) + s / 60f; val h = (cal.get(Calendar.HOUR) % 12) + m / 60f
    Canvas(Modifier.fillMaxSize()) {
        val c = center; val r = min(size.width, size.height) / 2f - 8f
        if (variant == "square") drawRoundRect(ink.copy(alpha = .9f), Offset(c.x - r, c.y - r), Size(r * 2, r * 2), CornerRadius(r * .28f), style = Stroke(5f))
        else if (variant != "nomark") drawCircle(ink.copy(alpha = .25f), r, c, style = Stroke(2f))
        if (variant != "nomark") for (i in 0 until 12) {
            val ang = i * PI.toFloat() / 6f
            val d = Offset(sin(ang), -cos(ang))
            // on the square dial the dots sit on the square's own edge, on the others on the circle
            val dot = if (variant == "square") c + d * (r / max(abs(d.x), abs(d.y))) * .86f else c + d * (r - 8f)
            drawCircle(ink.copy(alpha = if (i % 3 == 0) 1f else .5f), if (i % 3 == 0) 7f else 4f, dot)
        }
        fun hand(frac: Float, len: Float, w: Float, col: Color) { val a = frac * 2f * PI.toFloat(); drawLine(col, c, c + Offset(sin(a), -cos(a)) * len, w, StrokeCap.Round) }
        hand(h / 12f, r * .48f, 9f, ink); hand(m / 60f, r * .72f, 6f, ink); hand(s / 60f, r * .8f, 2.5f, Color(0xFFFF9F0A))
        drawCircle(Color(0xFFFF9F0A), 6f, c)
    }
}

// ------------------------------------------------------------------ clock of clocks

private const val N = 0f; private const val E = 90f; private const val S_ = 180f; private const val Wd = 270f
private val BL = 225f to 225f
/** Each digit is 2 columns by 3 rows of little clocks; a cell is the angles of its two hands (0 = up, clockwise). */
private val CLOCK_DIGITS: List<List<Pair<Float, Float>>> = listOf(
    listOf(E to S_, Wd to S_, N to S_, N to S_, N to E, Wd to N),
    listOf(BL, S_ to S_, BL, N to S_, BL, N to N),
    listOf(E to Wd, Wd to S_, E to S_, Wd to N, N to E, E to Wd),
    listOf(E to Wd, Wd to S_, E to Wd, Wd to N, E to Wd, Wd to N),
    listOf(S_ to S_, S_ to S_, N to E, N to S_, BL, N to N),
    listOf(E to S_, E to Wd, N to E, Wd to S_, E to Wd, Wd to N),
    listOf(E to S_, E to Wd, N to S_, Wd to S_, N to E, Wd to N),
    listOf(E to Wd, Wd to S_, BL, N to S_, BL, N to N),
    listOf(E to S_, Wd to S_, N to E, Wd to N, E to S_, Wd to S_),
    listOf(E to S_, Wd to S_, N to E, N to S_, E to Wd, Wd to N),
)

@Composable
private fun MiniClock(a: Float, b: Float, ink: Color, modifier: Modifier) {
    val aa by animateFloatAsState(a, tween(1400), label = "ha")
    val bb by animateFloatAsState(b, tween(1400), label = "hb")
    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f
        drawCircle(ink.copy(alpha = .22f), r * .92f, center, style = Stroke(r * .08f))
        listOf(aa, bb).forEach { ang ->
            val rad = ang * PI.toFloat() / 180f
            drawLine(ink, center, center + Offset(sin(rad), -cos(rad)) * r * .8f, r * .16f, StrokeCap.Round)
        }
    }
}

@Composable
internal fun ClockOfClocksFace(ctx: Context, ink: Color, tick: Long) {
    val c = clk(ctx, tick)
    val digits = (("%02d".format(c.hh.toInt())) + c.mm).map { it - '0' }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val portrait = maxHeight > maxWidth * 1.1f
        // wide: 4 digits in a row (8 clocks across, 3 down); upright: the hours over the minutes (4 across, 6 down)
        val cell = if (portrait) min(maxWidth / 5, maxHeight / 7.2f) else min(maxWidth / 9, maxHeight / 3.4f)
        @Composable fun pair(a: Int, b: Int) = Row(horizontalArrangement = Arrangement.spacedBy(cell * .25f)) {
            listOf(a, b).forEach { d ->
                Column {
                    for (row in 0 until 3) Row {
                        for (col in 0 until 2) {
                            val (x, y) = CLOCK_DIGITS[d][row * 2 + col]
                            MiniClock(x, y, ink, Modifier.size(cell))
                        }
                    }
                }
            }
        }
        if (portrait) Column(verticalArrangement = Arrangement.spacedBy(cell * .4f), horizontalAlignment = Alignment.CenterHorizontally) { pair(digits[0], digits[1]); pair(digits[2], digits[3]) }
        else Row(horizontalArrangement = Arrangement.spacedBy(cell * .25f)) { pair(digits[0], digits[1]); pair(digits[2], digits[3]) }
    }
}

// ------------------------------------------------------------------ world clock

private val CITIES = listOf("Istanbul" to "Europe/Istanbul", "London" to "Europe/London", "New York" to "America/New_York", "Tokyo" to "Asia/Tokyo", "Sydney" to "Australia/Sydney", "Dubai" to "Asia/Dubai")

@Composable
internal fun WorldClockFace(ctx: Context, ink: Color, tick: Long) {
    val now = Date(System.currentTimeMillis() + tick * 0)
    val here = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
    val f = clockFont("light")
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        CITIES.chunked(2).take(3).forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                row.forEach { (name, zone) ->
                    val tz = TimeZone.getTimeZone(zone)
                    val t = SimpleDateFormat(if (StandByPrefs.h24(ctx)) "HH:mm" else "h:mm a", Locale.getDefault()).apply { timeZone = tz }.format(now)
                    val day = Calendar.getInstance(tz).get(Calendar.DAY_OF_YEAR)
                    Column(Modifier.weight(1f).padding(8.dp), verticalArrangement = Arrangement.Center) {
                        Text(name, color = ink.copy(alpha = .75f), fontSize = 16.sp)
                        Text(t, color = ink, fontSize = 40.sp, fontFamily = f.family, fontWeight = f.weight, maxLines = 1, softWrap = false)
                        Text(if (day == here) tr("Today", "Bugün") else if (day > here || (here > 360 && day < 5)) tr("Tomorrow", "Yarın") else tr("Yesterday", "Dün"), color = ink.copy(alpha = .5f), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dementia / sereno

@Composable
internal fun DementiaFace(ctx: Context, ink: Color, tick: Long) {
    val c = clk(ctx, tick)
    val h = c.cal.get(Calendar.HOUR_OF_DAY)
    val part = when (h) { in 5..11 -> tr("MORNING", "SABAH"); in 12..16 -> tr("AFTERNOON", "ÖĞLEDEN SONRA"); in 17..20 -> tr("EVENING", "AKŞAM"); else -> tr("NIGHT", "GECE") }
    val day = SimpleDateFormat("EEEE", Locale.getDefault()).format(c.cal.time).uppercase()
    val date = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(c.cal.time)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val s = min(maxWidth.value / 9f, maxHeight.value / 6f).coerceAtLeast(16f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day, color = ink, fontSize = (s * 1.7f).sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(date, color = ink.copy(alpha = .85f), fontSize = (s * 1.2f).sp, maxLines = 1)
            Text(c.hh + ":" + c.mm, color = ink, fontSize = (s * 3.3f).sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text(part, color = ink.copy(alpha = .7f), fontSize = (s * 1.2f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SerenoFace(ctx: Context, ink: Color, tick: Long) {
    val c = clk(ctx, tick)
    val st = batteryInfo(ctx)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(c.hh + ":" + c.mm, color = ink.copy(alpha = .92f), fontSize = 110.sp, fontWeight = FontWeight.ExtraLight, maxLines = 1, softWrap = false)
        Text(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(c.cal.time), color = ink.copy(alpha = .55f), fontSize = 18.sp)
        Text("${st.first}%" + if (st.second) "  ·  " + tr("charging", "şarjda") else "", color = ink.copy(alpha = .35f), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    }
}

// ------------------------------------------------------------------ battery

internal fun batteryInfo(ctx: Context): Pair<Int, Boolean> {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val charging = (i?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0).let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
    return level to charging
}

@Composable
internal fun BatteryDotsFace(ctx: Context, ink: Color, tick: Long) {
    val (level, _) = batteryInfo(ctx)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cell = min(maxWidth, maxHeight * .9f) / 10
        Column {
            for (r in 0 until 10) Row {
                for (c in 0 until 10) {
                    val i = (9 - r) * 10 + c
                    Box(Modifier.size(cell).padding(cell * .18f).clip(CircleShape).background(if (i < level) ink else ink.copy(alpha = .12f)))
                }
            }
        }
    }
}

@Composable
internal fun BatteryWaveFace(ctx: Context, ink: Color, tick: Long) {
    val (level, charging) = batteryInfo(ctx)
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(40); phase += .12f } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize(.85f)) {
            val r = min(size.width, size.height) / 2f
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center.x - r, center.y - r, center.x + r, center.y + r)) }
            val top = center.y + r - 2f * r * level / 100f
            val wave = Path().apply {
                moveTo(center.x - r, center.y + r)
                lineTo(center.x - r, top)
                var x = center.x - r
                while (x <= center.x + r) { lineTo(x, top + sin(x / r * 5f + phase) * r * .04f); x += 6f }
                lineTo(center.x + r, center.y + r); close()
            }
            clipPath(clip) { drawPath(wave, ink.copy(alpha = .85f), style = Fill) }
            drawCircle(ink.copy(alpha = .35f), r, center, style = Stroke(5f))
        }
        Text("$level%" + if (charging) " ⚡" else "", color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light, style = TextStyle(shadow = Shadow(Color.Black, Offset.Zero, 14f)))
    }
}

@Composable
internal fun ChargingFace(ctx: Context, ink: Color, tick: Long) {
    val (level, charging) = batteryInfo(ctx)
    val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    val left = if (charging && android.os.Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining() else -1L
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$level%", color = ink, fontSize = 150.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(if (charging) tr("Charging", "Şarj oluyor") else tr("On battery", "Pilde"), color = ink.copy(alpha = .8f), fontSize = 22.sp)
        if (left > 0) Text(tr("Full in ", "Dolmasına ") + "%d:%02d".format(left / 3_600_000L, left / 60_000L % 60) + tr(" h", " sa"), color = ink.copy(alpha = .55f), fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

// ------------------------------------------------------------------ stopwatch, timer, pomodoro

private fun mmss(ms: Long, cs: Boolean) = "%02d:%02d".format(ms / 60_000L, ms / 1000L % 60) + if (cs) ".%02d".format(ms / 10L % 100) else ""

@Composable
private fun RoundBtn(label: String, ink: Color, onClick: () -> Unit) {
    Box(Modifier.size(64.dp).clip(CircleShape).background(ink.copy(alpha = .16f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) { Text(label, color = ink, fontSize = 22.sp) }
}

@Composable
internal fun StopwatchFace(ink: Color) {
    var running by remember { mutableStateOf(false) }
    var base by remember { mutableLongStateOf(0L) }
    var started by remember { mutableLongStateOf(0L) }
    var shown by remember { mutableLongStateOf(0L) }
    LaunchedEffect(running) { while (running) { shown = base + System.currentTimeMillis() - started; delay(33) } }
    val f = clockFont("seg7")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = min(maxWidth.value / (8 * f.adv), maxHeight.value * .35f).coerceAtLeast(24f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(mmss(shown, true), color = ink, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RoundBtn(if (running) "❚❚" else "▶", ink) { if (running) { base = shown; running = false } else { started = System.currentTimeMillis(); running = true } }
                RoundBtn("↺", ink) { running = false; base = 0L; shown = 0L }
            }
        }
    }
}

@Composable
internal fun TimerFace(ctx: Context, ink: Color) {
    var total by remember { mutableLongStateOf(5 * 60_000L) }
    var endAt by remember { mutableLongStateOf(0L) }
    var left by remember { mutableLongStateOf(total) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(endAt) {
        while (endAt > 0L) {
            left = (endAt - System.currentTimeMillis()).coerceAtLeast(0L)
            if (left == 0L) { endAt = 0L; done = true; runCatching { (ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 200, 400), -1)) } }
            delay(100)
        }
    }
    val f = clockFont("seg7")
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val size = min(maxWidth.value / (5 * f.adv), maxHeight.value * .35f).coerceAtLeast(24f)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(mmss(left, false), color = if (done) Color(0xFFFF453A) else ink, fontSize = size.sp, fontFamily = f.family, maxLines = 1, softWrap = false)
            if (endAt == 0L) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(1, 5, 10, 15, 30).forEach { m ->
                    Box(Modifier.clip(RoundedCornerShape(20.dp)).background(ink.copy(alpha = if (total == m * 60_000L) .3f else .12f)).clickable { total = m * 60_000L; left = total; done = false }.padding(horizontal = 14.dp, vertical = 8.dp)) { Text("$m", color = ink, fontSize = 16.sp) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RoundBtn(if (endAt > 0L) "❚❚" else "▶", ink) { if (endAt > 0L) { endAt = 0L } else { done = false; if (left == 0L) left = total; endAt = System.currentTimeMillis() + left } }
                RoundBtn("↺", ink) { endAt = 0L; left = total; done = false }
            }
        }
    }
}

@Composable
internal fun PomodoroFace(ink: Color) {
    var work by remember { mutableStateOf(true) }
    var left by remember { mutableLongStateOf(25 * 60_000L) }
    var running by remember { mutableStateOf(false) }
    var cycles by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000); left -= 1000
            if (left <= 0L) { if (work) cycles++; work = !work; left = if (work) 25 * 60_000L else 5 * 60_000L }
        }
    }
    val total = if (work) 25 * 60_000f else 5 * 60_000f
    val col = if (work) ink else Color(0xFF30D158)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize(.85f)) {
            val st = 18f; val r = min(size.width, size.height) / 2f - st
            drawCircle(col.copy(alpha = .15f), r, center, style = Stroke(st))
            drawArc(col, -90f, 360f * (left / total).coerceIn(0f, 1f), false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2), style = Stroke(st, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (work) tr("FOCUS", "ODAK") else tr("BREAK", "MOLA"), color = col.copy(alpha = .8f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(mmss(left, false), color = col, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(if (running) "❚❚" else "▶", col) { running = !running }
                RoundBtn("↺", col) { running = false; work = true; left = 25 * 60_000L }
                Text("× $cycles", color = col.copy(alpha = .7f), fontSize = 18.sp)
            }
        }
    }
}

// ------------------------------------------------------------------ quotes, calendar

private val QUOTES = listOf(
    "Well begun is half done." to "Aristotle", "The only way out is through." to "Robert Frost", "Small steps every day." to "",
    "What we do every day matters more than what we do once in a while." to "Gretchen Rubin", "Do what you can, with what you have, where you are." to "Theodore Roosevelt",
    "It always seems impossible until it is done." to "Nelson Mandela", "Begin anywhere." to "John Cage", "Slow is smooth, smooth is fast." to "",
    "Fall seven times, stand up eight." to "Japanese proverb", "The best time to plant a tree was twenty years ago. The second best time is now." to "Chinese proverb",
    "Simplicity is the ultimate sophistication." to "Leonardo da Vinci", "Nothing will work unless you do." to "Maya Angelou",
)

@Composable
internal fun QuotesFace(ink: Color, tick: Long) {
    val q = QUOTES[((System.currentTimeMillis() / 60_000L) % QUOTES.size).toInt()]
    BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        val s = min(maxWidth.value / 14f, maxHeight.value / 5f).coerceAtLeast(16f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("“" + q.first + "”", color = ink, fontSize = (s * 1.5f).sp, fontFamily = clockFont("playfair").family, textAlign = TextAlign.Center)
            if (q.second.isNotEmpty()) Text("— " + q.second, color = ink.copy(alpha = .6f), fontSize = s.sp, modifier = Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
internal fun StrikingCalendarFace(ink: Color) {
    val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(day.toString(), color = ink.copy(alpha = .13f), fontSize = (maxHeight.value * .95f).sp, fontFamily = clockFont("bebas").family, maxLines = 1, softWrap = false)
        }
        MonthFace(ink)
    }
}
