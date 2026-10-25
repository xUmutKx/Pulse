package com.umutk.pulse.extras.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlin.math.abs

/** The rounded card every fact of the weather page sits in. */
@Composable
private fun WxCard(title: String, fg: Color, sub: Color, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.background(WxTheme.panel, RoundedCornerShape(20.dp)).padding(14.dp)) {
        Text(title, color = sub, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun VsYesterday(delta: Double, imp: Boolean, fg: Color, sub: Color) {
    val d = if (imp) delta * 9 / 5 else delta
    val text = when {
        abs(d) < 1.5 -> tr("Almost the same as yesterday", "Dünle neredeyse aynı")
        d > 0 -> tr("Warmer than yesterday", "Dünden daha sıcak")
        else -> tr("Cooler than yesterday", "Dünden daha serin")
    }
    WxCard(tr("Today's temperature", "Bugünün sıcaklığı"), fg, sub, Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("%.0f°".format(abs(d)) + (if (d > 0.5) " ↑" else if (d < -0.5) " ↓" else ""), color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun level(grains: Int) = when { grains <= 0 -> 0; grains < 30 -> 1; grains < 100 -> 2; else -> 3 }
private fun levelName(l: Int) = when (l) { 0 -> tr("None", "Yok"); 1 -> tr("Low", "Düşük"); 2 -> tr("Moderate", "Orta"); else -> tr("High", "Yüksek") }
private fun levelColor(l: Int) = when (l) { 0 -> Color(0xFF9BB3C7); 1 -> Color(0xFF8BC34A); 2 -> Color(0xFFFFA726); else -> Color(0xFFEF5350) }

/** Tree, grass and weed pollen of now: a coloured dot and the level for each. */
@Composable
fun PollenCard(p: Map<String, Int>, fg: Color, sub: Color) {
    val tree = maxOf(p["alder"] ?: 0, p["birch"] ?: 0, p["olive"] ?: 0)
    val grass = p["grass"] ?: 0
    val weed = maxOf(p["ragweed"] ?: 0, p["mugwort"] ?: 0)
    WxCard(tr("Pollen", "Polen"), fg, sub, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(tr("Tree", "Ağaç") to tree, tr("Grass", "Çim") to grass, tr("Weed", "Ot") to weed).forEach { (n, g) ->
                val l = level(g)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(34.dp).background(levelColor(l), androidx.compose.foundation.shape.CircleShape))
                    Text(n, color = sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    Text(levelName(l), color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun UvCard(uv: Double, fg: Color, sub: Color, modifier: Modifier) {
    WxCard(tr("UV index", "UV indeksi"), fg, sub, modifier) {
        Text(WeatherFmt.uv(uv), color = fg, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFF8BC34A), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFF44336), Color(0xFF9C27B0))), cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx()))
            val x = (uv / 11.0).coerceIn(0.0, 1.0).toFloat() * size.width
            drawCircle(Color.White, 8.dp.toPx(), Offset(x.coerceIn(8.dp.toPx(), size.width - 8.dp.toPx()), size.height / 2))
        }
        Text("%.0f".format(uv), color = sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun HumidityCard(pct: Int, title: String, fg: Color, sub: Color, modifier: Modifier) {
    WxCard(title, fg, sub, modifier) {
        Text("$pct%", color = fg, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            drawRoundRect(Color.White.copy(alpha = .25f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
            drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFB3E5FC), Color(0xFF29B6F6))), size = Size(size.width * pct.coerceIn(0, 100) / 100f, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
        }
    }
}

@Composable
fun WindCard(speed: Double, dir: Int, imp: Boolean, fg: Color, sub: Color, modifier: Modifier) {
    WxCard(tr("Wind", "Rüzgâr"), fg, sub, modifier) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(92.dp)) {
                drawCircle(Color.White.copy(alpha = .35f), size.minDimension / 2 - 3.dp.toPx(), style = Stroke(5.dp.toPx()))
            }
            // the arrow points the way the wind blows to: the compass angle is where it comes from, so the arrow turns half a circle
            Box(Modifier.size(92.dp).rotate((dir + 180).toFloat()), contentAlignment = Alignment.TopCenter) { Text("▲", color = fg, fontSize = 14.sp) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(WeatherFmt.wind(speed, imp), color = fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(WeatherFmt.compass(dir), color = sub, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun PressureCard(hpa: Double, fg: Color, sub: Color, modifier: Modifier) {
    WxCard(tr("Pressure", "Basınç"), fg, sub, modifier) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(92.dp)) {
                val st = Stroke(7.dp.toPx(), cap = StrokeCap.Round)
                val inset = 6.dp.toPx()
                drawArc(Color.White.copy(alpha = .3f), 135f, 270f, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
                val frac = ((hpa - 960.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
                drawArc(Color.White, 135f, 270f * frac, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), style = st)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("%.0f".format(hpa), color = fg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text("hPa", color = sub, fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun ValueCard(title: String, value: String, note: String, fg: Color, sub: Color, modifier: Modifier) {
    WxCard(title, fg, sub, modifier) {
        Text(note, color = sub, fontSize = 13.sp, minLines = 2, maxLines = 2)
        Spacer(Modifier.height(18.dp))
        Text(value, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Medium)
    }
}
