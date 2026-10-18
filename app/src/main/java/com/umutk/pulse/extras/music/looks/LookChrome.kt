package com.umutk.pulse.extras.music.looks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.bevel
import com.umutk.pulse.xpCaption

/** Window body in the look's own frame (bevel, border or glass), with its title bar when the look has one. */
@Composable
fun MusicWindow(m: MusicLook, title: String, modifier: Modifier = Modifier, onClose: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(m.radius)
    ProvideTextStyle(TextStyle(color = m.text, fontFamily = m.font)) {
        Column(modifier.then(frameOf(m)).clip(shape).background(m.panel.copy(alpha = m.glassAlpha))) {
            if (m.titleH > 0.dp) {
                Row(Modifier.fillMaxWidth().height(m.titleH).background(m.titleBrush).padding(start = 6.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = m.titleText, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (onClose != null) CloseBox(m, onClose)
                }
            }
            Column(Modifier.fillMaxWidth().weight(1f), content = content)
        }
    }
}

private fun frameOf(m: MusicLook): Modifier = when (m.frame) {
    LookFrame.NONE -> Modifier
    LookFrame.BORDER -> Modifier.border(1.dp, m.line, RoundedCornerShape(m.radius))
    LookFrame.BEVEL -> Modifier.bevel(raised = true)
    LookFrame.GLASS -> Modifier.border(1.dp, m.glassEdge, RoundedCornerShape(m.radius))
}

/** XP draws its red Luna close button; the others a plain cross. */
@Composable
private fun CloseBox(m: MusicLook, onClose: () -> Unit) {
    val tap = Modifier.padding(start = 4.dp).clickable(role = Role.Button, onClick = onClose)
    if (m.id == "xp") {
        Canvas(tap.size(16.dp)) { xpCaption(close = true) }
    } else {
        Box(tap.size(18.dp), contentAlignment = Alignment.Center) { Text("✕", color = m.titleText, fontSize = 10.sp) }
    }
}

/** Push button in the look's own style: bevel and grey face on 95, Luna and Aero faces, accent pill on the modern looks. */
@Composable
fun MusicButton(m: MusicLook, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(m.radius)
    val filled = m.filled && primary
    val face: Brush = if (filled) SolidColor(m.accent) else m.buttonFace
    val text = if (filled) m.onAccent else m.buttonText
    val edge = if (m.filled) (if (primary) m.accent else m.line) else m.buttonEdge
    val frame = if (m.frame == LookFrame.BEVEL) Modifier.bevel(raised = true) else Modifier.border(1.dp, edge, shape)
    Box(modifier.clip(shape).background(face, shape).then(frame)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center) {
        CompositionLocalProvider(LocalContentColor provides text) {
            ProvideTextStyle(TextStyle(color = text, fontFamily = m.font, fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)) {
                content()
            }
        }
    }
}
