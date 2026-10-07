package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Windows 11 materials, drawn by hand: the tint comes from the wallpaper colour (Android 12+ dynamic colour) or the accent. */
object Effects {
    val list = listOf(
        "none" to "None",
        "mica" to "Mica",
        "micaAlt" to "Mica Alt",
        "acrylic" to "Acrylic",
        "tabbed" to "Glass",
    )
}

/** The colour the backdrop leans towards: a neutral tone of the wallpaper palette (Android 12+) or plain grey. Never the accent, so Mica stays grey, not blue. */
@Composable
fun micaTint(): Color {
    val ctx = LocalContext.current
    val dark = T.dark
    return remember(dark) {
        if (android.os.Build.VERSION.SDK_INT >= 31) Color(ctx.getColor(if (dark) android.R.color.system_neutral1_700 else android.R.color.system_neutral1_100))
        else if (dark) Color(0xFF2B2B2B) else Color(0xFFE6E6E6)
    }
}

/** Is a material active for the current theme? Only the sidebar themes can have one (the classic Windows ones never had it). */
val Look.micaOn: Boolean get() = effect != "none" && !current.tabs

/** Paints the backdrop (the window behind the content) for [kind]. */
@Composable
fun Modifier.backdrop(kind: String): Modifier {
    val t = T
    val tint = micaTint()
    val grain = remember { grainPoints(700) }
    if (kind == "none" || t.tabs) return this.background(t.bg)
    val base = t.bg
    // real Mica is grey with a faint wallpaper hue; the kinds differ in how far they lean towards the tint
    val (a, b) = when (kind) {
        "micaAlt" -> lerp(base, tint, if (t.dark) .75f else .85f) to lerp(base, tint, if (t.dark) .45f else .5f)
        "acrylic" -> lerp(base, tint, if (t.dark) .6f else .7f) to lerp(base, tint, if (t.dark) .3f else .4f)
        "tabbed" -> lerp(base, tint, .4f) to lerp(base, Color.White, if (t.dark) .06f else .45f)
        else -> lerp(base, tint, if (t.dark) .55f else .6f) to lerp(base, tint, if (t.dark) .25f else .25f)   // mica
    }
    return this
        .background(Brush.linearGradient(listOf(a, b), start = Offset(0f, 0f), end = Offset(1400f, 2400f)))
        .drawBehind {
            // fine grain, as in the real material
            val dot = if (t.dark) Color.White.copy(alpha = if (kind == "acrylic") .055f else .03f) else Color.Black.copy(alpha = if (kind == "acrylic") .05f else .028f)
            val pts = grain.map { Offset(it.x * size.width, it.y * size.height) }
            drawPoints(pts, PointMode.Points, dot, strokeWidth = 1.6f)
            if (kind == "acrylic") drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (t.dark) .05f else .12f), Color.Transparent), endY = size.height * .35f))
        }
}

/** The content surface above the backdrop: translucent so the material shows through, with the thin light edge Windows 11 draws. */
@Composable
fun Modifier.layer(kind: String, shape: androidx.compose.ui.graphics.Shape, edge: Dp = 1.dp): Modifier {
    val t = T
    if (kind == "none" || t.tabs) return this.background(t.panel, shape)
    val alpha = when (kind) { "acrylic" -> .58f; "tabbed" -> .5f; "micaAlt" -> .74f; else -> .8f }
    return this.background(t.panel.copy(alpha = alpha), shape)
        .border(edge, if (t.dark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .06f), shape)
}

private fun grainPoints(n: Int): List<Offset> {
    val r = java.util.Random(7)
    return List(n) { Offset(r.nextFloat(), r.nextFloat()) }
}
