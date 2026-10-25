package com.umutk.pulse.extras.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Epoch millis of a local "yyyy-MM-ddTHH:mm" time, or null when it does not parse. */
private fun localMs(s: String): Long? = runCatching {
    LocalDateTime.parse(s).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

/** The moon now: the lit part is drawn from the phase, with its share of light and the day of the cycle. */
@Composable
fun MoonPhase(nowMs: Long, fg: Color, sub: Color) {
    val age = WeatherFmt.moonAge(nowMs)
    val syn = WeatherFmt.SYNODIC
    // cos is 1 at new moon and -1 at full: below 0 the lit part is more than half
    val c = cos(2 * PI * age / syn).toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(Modifier.size(84.dp)) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            // the unlit disc first, faintly lit by the earth, then the lit part cut from the phase and drawn with the moon's seas and craters
            drawCircle(Color(0xFF232A3D), r, center)
            drawCircle(Color(0xFF3A4259).copy(alpha = .5f), r * .3f, Offset(center.x - r * .3f, center.y - r * .2f))
            val rx = r * abs(c)
            val lit = androidx.compose.ui.graphics.Path().apply {
                val circle = androidx.compose.ui.geometry.Rect(Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
                val ell = androidx.compose.ui.geometry.Rect(Offset(center.x - rx, center.y - r), Size(2 * rx, 2 * r))
                // right side lit (waxing): the right half of the disc, closed by the terminator that bulges left when more than half is lit, right when less
                arcTo(circle, -90f, 180f, true)
                arcTo(ell, 90f, if (c < 0) 180f else -180f, false)
                close()
            }
            val flip = age >= syn / 2
            withTransform({ if (flip) scale(-1f, 1f, center) }) {
                clipPath(lit) {
                    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFFFFBEA), Color(0xFFE9E3CC), Color(0xFFC9C3AD)), Offset(center.x - r * .3f, center.y - r * .3f), r * 1.5f), r, center)
                    listOf(Triple(-.28f, -.2f, .3f), Triple(.12f, -.35f, .22f), Triple(.25f, .08f, .26f), Triple(-.1f, .3f, .2f)).forEach { (x, y, k) -> drawCircle(Color(0xFF8C8672).copy(alpha = .35f), r * k, Offset(center.x + r * x, center.y + r * y)) }
                    listOf(Triple(.4f, -.45f, .09f), Triple(-.5f, .15f, .08f), Triple(.05f, .55f, .07f), Triple(.55f, .42f, .06f)).forEach { (x, y, k) -> drawCircle(Color(0xFF8C8672).copy(alpha = .45f), r * k, Offset(center.x + r * x, center.y + r * y), style = Stroke(r * .03f)) }
                }
            }
            drawCircle(sub.copy(alpha = .35f), r, center, style = Stroke(1.dp.toPx()))
        }
        Column {
            Text(WeatherFmt.moon(nowMs), color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(tr("%d%% lit", "%%%d aydınlık").format(((1 - c) / 2 * 100).toInt()), color = sub, fontSize = 13.sp)
            Text(tr("Day %d of 30", "Döngünün %d. günü").format(age.toInt() + 1), color = sub, fontSize = 13.sp)
        }
    }
}

/** The sun's path over the day: an arc from sunrise (left) to sunset (right), with the sun where it is now. */
@Composable
fun SunArc(sunrise: String, sunset: String, nowMs: Long, fg: Color, sub: Color) {
    val rise = localMs(sunrise) ?: return
    val set = localMs(sunset) ?: return
    if (set <= rise) return
    // 0 before sunrise, 1 after sunset
    val p = ((nowMs - rise).toFloat() / (set - rise)).coerceIn(0f, 1f)
    val sunColor = Color(0xFFFFC233)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val cx = size.width / 2
            val base = size.height - 6.dp.toPx()
            val rx = size.width / 2 - 14.dp.toPx()
            val ry = base - 8.dp.toPx()
            drawLine(sub.copy(alpha = .3f), Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
            drawArc(sub.copy(alpha = .45f), 180f, 180f, false, Offset(cx - rx, base - ry), Size(2 * rx, 2 * ry), style = Stroke(1.5.dp.toPx()))
            // the sun moves along the upper half of the ellipse, from the left horizon at p = 0 to the right one at p = 1
            val a = PI * p
            val sun = Offset(cx - rx * cos(a).toFloat(), base - ry * sin(a).toFloat())
            drawCircle(sunColor.copy(alpha = .25f), 12.dp.toPx(), sun)
            drawCircle(sunColor, 6.dp.toPx(), sun)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tr("Sunrise %s", "Gündoğumu %s").format(WeatherFmt.clock(sunrise)), color = sub, fontSize = 12.sp)
            Text(tr("Sunset %s", "Gün batımı %s").format(WeatherFmt.clock(sunset)), color = sub, fontSize = 12.sp)
        }
    }
}
