package com.umutk.pulse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp

/**
 * A loading bar in the look: Windows 95 gets its file-copy animation (a page flying from folder to folder over a
 * blocky navy bar), the other looks the plain bar. [progress] null = still measuring.
 */
@Composable
fun Busy(progress: Float? = null, modifier: Modifier = Modifier) {
    if (T.id != "win95") {
        PProgress(progress, modifier)
        return
    }
    val t by rememberInfiniteTransition(label = "copy95").animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "t")
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            val u = 2.dp.toPx()
            fun folder(x: Float) {
                val y = size.height - 18 * u
                drawRect(Color(0xFF808000), Offset(x, y - 2 * u), Size(7 * u, 3 * u))
                drawRect(Color(0xFFFFFF00), Offset(x, y), Size(16 * u, 12 * u))
                drawRect(Color(0xFF808000), Offset(x, y + 11 * u), Size(16 * u, u))
                drawRect(Color.Black, Offset(x + 15 * u, y), Size(u, 12 * u))
            }
            val left = 4 * u; val right = size.width - 20 * u
            folder(left); folder(right)
            // the page flies on an arc from the left folder to the right one, as in the Windows 95 copy dialog
            val x = left + 4 * u + (right - left) * t
            val y = size.height - 22 * u - kotlin.math.sin(t * Math.PI.toFloat()) * 14 * u
            val page = Path().apply { moveTo(x, y); lineTo(x + 7 * u, y); lineTo(x + 9 * u, y + 2 * u); lineTo(x + 9 * u, y + 11 * u); lineTo(x, y + 11 * u); close() }
            drawPath(page, Color.White)
            drawPath(page, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(u * .6f))
            for (i in 1..4) drawRect(Color(0xFF808080), Offset(x + 2 * u, y + (2 + i * 2) * u), Size(5 * u, u * .6f))
        }
        // the sunken bar with navy blocks
        Canvas(Modifier.fillMaxWidth().height(18.dp).bevel(false)) {
            val pad = 3.dp.toPx(); val block = 8.dp.toPx(); val gap = 2.dp.toPx()
            val w = size.width - pad * 2
            val filled = (progress ?: t) * w
            var x = 0f
            while (x + block <= filled) { drawRect(Color(0xFF000080), Offset(pad + x, pad), Size(block, size.height - pad * 2)); x += block + gap }
        }
    }
}
