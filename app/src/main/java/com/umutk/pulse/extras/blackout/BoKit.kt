package com.umutk.pulse.extras.blackout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T

/** The pieces of the BlackOut pages: cards, rows, buttons and sliders that take their look from the look the page is in. */

val OkGreen = Color(0xFF4CAF50)
val WarnAmber = Color(0xFFFFB74D)
val BadRed = Color(0xFFF44336)

/** The grey a page writes small notes in. */
val dimColor: Color get() = T.sub

@Composable
fun BoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    PCard(modifier.fillMaxWidth().padding(vertical = 4.dp)) { content() }
}

@Composable
fun BoText(t: String, size: Int = 13, color: Color = T.sub, bold: Boolean = false, modifier: Modifier = Modifier, mono: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Text(t, color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = modifier, maxLines = maxLines, fontFamily = if (mono) FontFamily.Monospace else null)
}

/** A title with a note under it, and a switch at the end. */
@Composable
fun BoSwitchRow(title: String, sub: String = "", on: Boolean, enabled: Boolean = true, bold: Boolean = false, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = if (enabled) T.text else T.sub, fontSize = 15.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
            if (sub.isNotEmpty()) BoText(sub)
        }
        PSwitch(on, onChange, enabled)
    }
}

/** A button; a disabled one is drawn faint and does nothing. */
@Composable
fun BoBtn(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.alpha(if (enabled) 1f else .4f)) { PButton(text, compact = true) { if (enabled) onClick() } }
}

/** A quiet text button. */
@Composable
fun BoLink(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(text, color = if (enabled) T.accent else T.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled) { onClick() }.padding(horizontal = 6.dp, vertical = 8.dp))
}

@Composable
fun BoButtons(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun BoSlider(value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean = true, onChange: (Float) -> Unit, onDone: () -> Unit = {}) {
    Box(Modifier.alpha(if (enabled) 1f else .4f)) { PSlider(value, { if (enabled) onChange(it) }, range, onDone = { if (enabled) onDone() }) }
}

/** A round colour sample. */
@Composable
fun BoDot(argb: Int, size: Dp = 26.dp, ring: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(argb or -0x1000000)).border(if (ring) 3.dp else 1.dp, if (ring) T.accent else Color(0x55FFFFFF), CircleShape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier))
}

/** A status dot and a line: green when [ok]. */
@Composable
fun BoStatus(ok: Boolean, yes: String, no: String, modifier: Modifier = Modifier) {
    val c = if (ok) OkGreen else WarnAmber
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(10.dp))
        Text(if (ok) yes else no, color = c, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A thin progress strip; no value = just the track. */
@Composable
fun BoProgress(frac: Float?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(T.line)) {
        if (frac != null) Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(6.dp).background(T.accent))
    }
}

/** A category row: an icon in a round tint, a title, a note, an optional ON/OFF badge and a chevron. */
@Composable
fun BoNavRow(icon: String, title: String, sub: String, big: Boolean = false, badge: String? = null, onClick: () -> Unit) {
    PCard(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = if (big) 6.dp else 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(if (big) 48.dp else 40.dp).clip(CircleShape).background(T.accent.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                if (icon == "moon") MoonLogo(Modifier.size(26.dp)) else PIcon(icon, T.accent, 22.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = T.text, fontSize = if (big) 18.sp else 16.sp, fontWeight = FontWeight.Medium)
                BoText(sub, 14)
            }
            if (badge != null) Text(badge, color = if (badge == "ON") OkGreen else T.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(8.dp)).background(T.text.copy(alpha = .1f)).padding(horizontal = 8.dp, vertical = 3.dp))
            PIcon("chevron", T.sub, 22.dp, Modifier.rotate(-90f))
        }
    }
}

private const val MOON = "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4C12.92,3.04 12.46,3 12,3z"

/** The BlackOut mark: an outlined crescent moon with two small stars. */
@Composable
fun MoonLogo(modifier: Modifier, color: Color = T.accent) {
    val path = remember { PathParser().parsePathString(MOON).toPath() }
    Canvas(modifier) {
        scale(size.width / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(color, 0.6f, Offset(18.5f, 5f)); drawCircle(color, 0.4f, Offset(21f, 8.5f))
        }
    }
}
