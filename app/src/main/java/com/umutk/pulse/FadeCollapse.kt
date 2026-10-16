package com.umutk.pulse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** Shows only the top half of the first of [rows] rows, fading out; the arrow below opens the rest downwards. */
@Composable
fun FadeCollapse(rows: Int, key: String, content: @Composable () -> Unit) {
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val f by animateFloatAsState(if (open) 1f else 0f, label = "fadeCollapse")
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().clipToBounds()
            .layout { m, c ->
                val p = m.measure(Constraints(c.minWidth, c.maxWidth, 0, Constraints.Infinity))
                val small = p.height / rows.coerceAtLeast(1) / 2
                val h = (small + (p.height - small) * f).toInt()
                layout(p.width, h) { p.place(0, 0) }
            }
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                if (f < 1f) drawRect(Brush.verticalGradient(listOf(Color.Black, Color.Transparent)), alpha = 1f - f, blendMode = BlendMode.DstIn)
            }) { content() }
        Box(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            PIcon("chevron", T.sub, 24.dp, Modifier.graphicsLayer { rotationZ = 180f * f })
        }
    }
}
