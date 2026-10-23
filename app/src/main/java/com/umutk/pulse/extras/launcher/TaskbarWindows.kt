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
                Modifier.width(if (kind == "95") 120.dp else 112.dp).height(height).then(look).clickable { if (active) DesktopWindows.minimize(w) else { w.min = false; DesktopWindows.front(w) } }.padding(horizontal = 8.dp),
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
