package com.umutk.pulse.extras.music.visual

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt

// viscolor.txt: 0 background, 2..17 bar rows (top to bottom), 18 scope line, 23 peak cap.

/** Analyser bars in the skin's colours. [u] is device pixels per skin pixel. */
fun DrawScope.drawVisBars(v: VisState, vis: List<Color>, u: Float) {
    drawRect(vis[0], Offset(VIS_X * u, VIS_Y * u), Size(VIS_W * u, VIS_H * u))
    for (i in 0 until VIS_BARS) {
        val x = (VIS_X + i * (VIS_BAR_W + VIS_GAP)) * u
        val rows = (v.level[i] * VIS_H).roundToInt()
        for (t in VIS_H - rows until VIS_H) drawRect(vis[2 + t], Offset(x, (VIS_Y + t) * u), Size(VIS_BAR_W * u, u))
        val peakRows = (v.peak[i] * VIS_H).roundToInt()
        if (peakRows > rows) drawRect(vis[23], Offset(x, (VIS_Y + VIS_H - peakRows) * u), Size(VIS_BAR_W * u, u))
    }
}

/** Oscilloscope line in the skin's scope colour, one point per skin pixel column. */
fun DrawScope.drawVisScope(v: VisState, vis: List<Color>, u: Float) {
    drawRect(vis[0], Offset(VIS_X * u, VIS_Y * u), Size(VIS_W * u, VIS_H * u))
    val path = Path()
    for (i in 0 until VIS_W) {
        val x = (VIS_X + i + 0.5f) * u
        val y = (VIS_Y + 0.5f + (1f - v.scope[i]) * (VIS_H - 1)) * u
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, vis[18], style = Stroke(width = u))
}
