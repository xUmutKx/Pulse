package com.umutk.pulse

import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One look: the Windows versions of Task Manager, Material You and an AMOLED black one. */
class PTheme(
    val id: String,
    val name: String,
    val dark: Boolean,
    val bg: Color,            // window / sidebar
    val panel: Color,         // content panel, cards
    val text: Color,
    val sub: Color,
    val accent: Color,
    val line: Color,
    val radius: Dp,
    val tabs: Boolean = false,       // classic top tabs + title bar (7, XP, 95) instead of the left sidebar
    val material: Boolean = false,   // Material icons + dynamic colour
    val classicGraph: Boolean = false, // black graph with green line like the old task manager
    val bevel: Boolean = false,      // Windows 95 raised / sunken edges
    val title: Brush? = null,
    val titleText: Color = Color.White,
    val titleName: String = "",
    val tabSel: Color = Color.White,
)

private fun c(v: Long) = Color(v)

val Themes = listOf(
    PTheme("win11", "Windows 11", false, c(0xFFF3F3F3), c(0xFFFFFFFF), c(0xFF1A1A1A), c(0xFF5F5F5F), c(0xFF0067C0), c(0xFFE0E0E0), 8.dp),
    PTheme("win10", "Windows 10", false, c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFF000000), c(0xFF6D6D6D), c(0xFF0078D7), c(0xFFD9D9D9), 0.dp, tabs = true, titleName = "Task Manager"),
    PTheme("win7", "Windows 7", false, c(0xFFF0F0F0), c(0xFFFFFFFF), c(0xFF000000), c(0xFF444444), c(0xFF3399FF), c(0xFF9AA7B8), 3.dp,
        tabs = true, classicGraph = true,
        title = Brush.verticalGradient(listOf(c(0xFFDCE9F7), c(0xFFB9D1EA), c(0xFFA7C4E5))), titleText = Color.Black, titleName = "Windows Task Manager"),
    PTheme("xp", "Windows XP", false, c(0xFFECE9D8), c(0xFFFFFFFF), c(0xFF000000), c(0xFF404040), c(0xFF316AC5), c(0xFF7F9DB9), 3.dp,
        tabs = true, classicGraph = true, tabSel = c(0xFFFCFCFE),
        title = Brush.verticalGradient(listOf(c(0xFF0A5AE0), c(0xFF0054E3), c(0xFF0A246A).copy(alpha = 1f), c(0xFF0054E3))), titleName = "Windows Task Manager"),
    PTheme("win95", "Windows 95", false, c(0xFFC0C0C0), c(0xFFFFFFFF), c(0xFF000000), c(0xFF404040), c(0xFF000080), c(0xFF808080), 0.dp,
        tabs = true, classicGraph = true, bevel = true, tabSel = c(0xFFC0C0C0),
        title = Brush.horizontalGradient(listOf(c(0xFF000080), c(0xFF1084D0))), titleName = "Windows Task Manager"),
    PTheme("material", "Material You", false, c(0xFFFEF7FF), c(0xFFF3EDF7), c(0xFF1D1B20), c(0xFF49454F), c(0xFF6750A4), c(0xFFCAC4D0), 20.dp, material = true),
    PTheme("amoled", "AMOLED", true, c(0xFF000000), c(0xFF0D0D0D), c(0xFFFFFFFF), c(0xFF9E9E9E), c(0xFF4DD0E1), c(0xFF242424), 16.dp, material = true),
)

val Accents = listOf(0xFF0067C0, 0xFF00A388, 0xFF6750A4, 0xFFD13438, 0xFFE67E22, 0xFF2E7D32, 0xFFC2185B).map { Color(it) }

/** Resource colours, as in Windows Task Manager. */
fun resColor(kind: String): Color = when (kind) {
    "cpu" -> Color(0xFF1B8DBE); "memory" -> Color(0xFF8B12AE); "disk" -> Color(0xFF4DA64D)
    "net" -> Color(0xFFD97B29); "gpu" -> Color(0xFF1F6FBF); "battery" -> Color(0xFF2E9E5B); else -> Color(0xFFC0392B)
}

object Look {
    var theme by mutableStateOf(Themes[0])
    var mode by mutableStateOf("system")     // system / light / dark (the classic Windows looks stay as they were)
    var effect by mutableStateOf("mica")     // none / mica / micaAlt / acrylic / tabbed (sidebar looks only)
    var accentIdx by mutableStateOf(-1)      // -1: the theme's own accent
    @Volatile var resolved: PTheme? = null   // Material You with the system's dynamic colours, set while composing
    val current get() = resolved ?: theme
    val accent get() = if (!current.material && (!current.tabs || current.id == "win10") && accentIdx in Accents.indices) Accents[accentIdx] else current.accent
}

/** Windows 95 raised / sunken edge. */
fun Modifier.bevel(raised: Boolean = true): Modifier = drawBehind {
    val w = 1f * density * 1f; val s = size
    val hi = Color.White; val sh = Color(0xFF808080); val dk = Color.Black
    val (tl, br) = if (raised) hi to sh else sh to hi
    fun l(a: Offset, b: Offset, col: Color) = drawLine(col, a, b, w)
    l(Offset(0f, 0f), Offset(s.width, 0f), if (raised) hi else sh); l(Offset(0f, 0f), Offset(0f, s.height), if (raised) hi else sh)
    l(Offset(0f, s.height - w / 2), Offset(s.width, s.height - w / 2), if (raised) dk else hi); l(Offset(s.width - w / 2, 0f), Offset(s.width - w / 2, s.height), if (raised) dk else hi)
    l(Offset(w, w), Offset(s.width - w, w), tl); l(Offset(w, w), Offset(w, s.height - w), tl)
    l(Offset(w, s.height - w * 1.5f), Offset(s.width - w, s.height - w * 1.5f), br); l(Offset(s.width - w * 1.5f, w), Offset(s.width - w * 1.5f, s.height - w), br)
}

fun Modifier.frame(t: PTheme, raised: Boolean = false): Modifier =
    if (t.bevel) bevel(raised) else if (t.tabs) border(1.dp, t.line) else this

/** The dark twin of a light sidebar theme (Windows 11 / 10); others have none. */
fun darkTwin(t: PTheme): PTheme? = when (t.id) {
    "win11" -> PTheme(t.id, t.name, true, c(0xFF050505), c(0xFF111111), c(0xFFFFFFFF), c(0xFFB8B8B8), c(0xFF60CDFF), c(0xFF2A2A2A), t.radius)
    "win10" -> PTheme(t.id, t.name, true, c(0xFF000000), c(0xFF0E0E0E), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFF4CC2FF), c(0xFF2A2A2A), t.radius, tabs = true, titleName = t.titleName)
    else -> null
}
