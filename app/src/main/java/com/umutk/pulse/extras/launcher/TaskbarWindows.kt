package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.umutk.pulse.ProgressState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.bevel

/**
 * The open windows of the desktop as buttons on the taskbar, each in the look's own style: a tap on the one in front sends it down, a tap on any other
 * brings it up (also from the taskbar when it was minimised). [kind] is "95", "xp", "7", "10" or "11".
 */
@Composable
fun TaskbarWindows(kind: String, ink: Color, height: Dp, modifier: Modifier = Modifier) {
    val list = DesktopWindows.list
    if (list.isEmpty()) return
    val topZ = list.filter { !it.min }.maxOfOrNull { it.z }
    // the moving band of a progress that has no end in sight, read only where it is drawn
    val phase = androidx.compose.animation.core.rememberInfiniteTransition(label = "tbp").animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.LinearEasing)), label = "tbpp")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(if (kind == "95") 2.dp else 3.dp), verticalAlignment = Alignment.CenterVertically) {
        list.forEach { w ->
            val active = !w.min && w.z == topZ
            val shape = RoundedCornerShape(when (kind) { "95", "10" -> 0.dp; "11" -> 6.dp; else -> 3.dp })
            val look: Modifier = when (kind) {
                "95" -> Modifier.background(Color(0xFFC0C0C0)).bevel(!active)
                "xp" -> Modifier.clip(shape).background(Brush.verticalGradient(if (active) listOf(Color(0xFF1B47A8), Color(0xFF2557C4)) else listOf(Color(0xFF4A8FF5), Color(0xFF2C66DD)))).border(1.dp, Color(0x55FFFFFF), shape)
                "7" -> Modifier.clip(shape).background(if (active) Color(0x88A9CBEB) else Color(0x553E6E9E)).border(1.dp, Color(0x88A9CBEB), shape)
                "10" -> Modifier.background(if (active) Color.White.copy(alpha = .2f) else Color.White.copy(alpha = .07f))
                else -> Modifier.clip(shape).background(Color.White.copy(alpha = if (active) .14f else .06f))
            }
            Box(
                Modifier.width(if (kind == "95") 120.dp else 112.dp).height(height).then(look).drawBehind {
                    // what the window is doing, on its button: Windows 7 fills the button with glossy colour, the others a bar along its foot
                    val f = w.progress ?: return@drawBehind
                    val bad = w.pstate == ProgressState.Error; val pz = w.pstate == ProgressState.Paused
                    val top = if (bad) Color(0xFFFF8A8A) else if (pz) Color(0xFFFFF27A) else Color(0xFF7BE67B)
                    val bot = if (bad) Color(0xFFD02020) else if (pz) Color(0xFFE2C700) else Color(0xFF1FB51F)
                    val x0: Float; val wd: Float
                    if (f < 0f) { val bw = size.width * .35f; x0 = phase.value * (size.width + bw) - bw; wd = bw } else { x0 = 0f; wd = size.width * f.coerceIn(0f, 1f) }
                    val l = x0.coerceAtLeast(0f); val r = (x0 + wd).coerceAtMost(size.width)
                    if (r > l) {
                        if (kind == "7") {
                            drawRect(Brush.verticalGradient(listOf(top, bot), 0f, size.height), Offset(l, 0f), Size(r - l, size.height), alpha = .85f)
                            drawRect(Color.White.copy(alpha = .28f), Offset(l, 0f), Size(r - l, size.height / 2f))
                        } else {
                            val bh = 3.dp.toPx()
                            val c = if (kind == "95") Color(0xFF000080) else null
                            if (c != null) drawRect(c, Offset(l, size.height - bh), Size(r - l, bh))
                            else drawRect(Brush.verticalGradient(listOf(top, bot), size.height - bh, size.height), Offset(l, size.height - bh), Size(r - l, bh))
                        }
                    }
                }.clickable { if (active) DesktopWindows.minimize(w) else { w.min = false; DesktopWindows.front(w) } }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(w.title, color = if (kind == "95") Color.Black else ink, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (kind == "95" && active) androidx.compose.ui.text.font.FontWeight.Bold else null)
                // the underline of Windows 10 / 11: the open window, longer when it is in front
                if (kind == "10" || kind == "11") Box(Modifier.align(Alignment.BottomCenter).width(if (active) 40.dp else 14.dp).height(2.dp).background(Color(0xFF4CC2FF)))
            }
        }
    }
}
