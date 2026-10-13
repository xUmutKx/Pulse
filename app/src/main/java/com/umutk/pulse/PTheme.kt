package com.umutk.pulse

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
    val glassA: Float = 0f,          // liquid glass: panel alpha, 0 = opaque; the panels get a light edge
    val wall: Brush? = null,         // coloured backdrop the glass floats over (the window behind the panels)
)

/** Light edge of a glass panel. */
fun glassEdge(t: PTheme): Color = Color.White.copy(alpha = if (t.dark) .22f else .75f)

private fun c(v: Long) = Color(v)

val Themes = listOf(
    PTheme("win11", "Windows 11", false, c(0xFFF3F3F3), c(0xFFFFFFFF), c(0xFF1A1A1A), c(0xFF5F5F5F), c(0xFF0067C0), c(0xFFE0E0E0), 8.dp),
    PTheme("win10", "Windows 10", false, c(0xFFFFFFFF), c(0xFFFFFFFF), c(0xFF000000), c(0xFF6D6D6D), c(0xFF0078D7), c(0xFFD9D9D9), 0.dp, tabs = true, titleName = "Task Manager", glassA = .9f),
    PTheme("win7", "Windows 7", false, c(0xFFDCE9F7), c(0xFFFFFFFF), c(0xFF000000), c(0xFF444444), c(0xFF3399FF), c(0xFF9AA7B8), 3.dp,
        tabs = true, classicGraph = true, glassA = .72f, wall = Brush.verticalGradient(listOf(c(0xFF7FB2E5), c(0xFFDCE9F7))),
        title = Brush.verticalGradient(listOf(c(0xFFDCE9F7), c(0xFFB9D1EA), c(0xFFA7C4E5))), titleText = Color.Black, titleName = "Windows Task Manager"),
    PTheme("xp", "Windows XP", false, c(0xFFECE9D8), c(0xFFFFFFFF), c(0xFF000000), c(0xFF404040), c(0xFF316AC5), c(0xFF7F9DB9), 3.dp,
        tabs = true, classicGraph = true, glassA = .9f, tabSel = c(0xFFFCFCFE),
        title = Brush.verticalGradient(listOf(c(0xFF0A5AE0), c(0xFF0054E3), c(0xFF0A246A).copy(alpha = 1f), c(0xFF0054E3))), titleName = "Windows Task Manager"),
    PTheme("win95", "Windows 95", false, c(0xFFC0C0C0), c(0xFFFFFFFF), c(0xFF000000), c(0xFF404040), c(0xFF000080), c(0xFF808080), 0.dp,
        tabs = true, classicGraph = true, bevel = true, glassA = .85f, tabSel = c(0xFFC0C0C0),
        title = Brush.horizontalGradient(listOf(c(0xFF000080), c(0xFF1084D0))), titleName = "Windows Task Manager"),
    PTheme("material", "Material You", false, c(0xFFFEF7FF), c(0xFFF3EDF7), c(0xFF1D1B20), c(0xFF49454F), c(0xFFFFFFFF), c(0xFFCAC4D0), 20.dp, material = true, glassA = .9f),
    PTheme("aurora", "Aurora", true, c(0xFF0A0F1F), c(0xFF1B2540), c(0xFFFFFFFF), c(0xFFA9B4D0), c(0xFF5EEAD4), c(0xFF2C3A5C), 16.dp,
        glassA = .55f, wall = Brush.linearGradient(listOf(c(0xFF0B1B3A), c(0xFF12506B), c(0xFF3B1F6B)))),
    PTheme("sakura", "Sakura", false, c(0xFFFCEFF5), c(0xFFFFFFFF), c(0xFF2B1B2E), c(0xFF7A5C73), c(0xFFD6336C), c(0xFFE8C9DB), 16.dp,
        glassA = .55f, wall = Brush.linearGradient(listOf(c(0xFFFFD6E8), c(0xFFF9C6DD), c(0xFFD4C4FF)))),
    PTheme("ocean", "Ocean", true, c(0xFF04141F), c(0xFF0C2A3D), c(0xFFFFFFFF), c(0xFF8FB8CC), c(0xFF22D3EE), c(0xFF1D4A62), 12.dp,
        glassA = .5f, wall = Brush.linearGradient(listOf(c(0xFF03324A), c(0xFF0B6E8A), c(0xFF062A3E)))),
    PTheme("ember", "Ember", true, c(0xFF140A08), c(0xFF2A1A16), c(0xFFF5EDE8), c(0xFFBFA69C), c(0xFFFF7A3D), c(0xFF4A2E26), 12.dp,
        glassA = .5f, wall = Brush.linearGradient(listOf(c(0xFF3A1206), c(0xFF8A2E12), c(0xFF2A0B1E)))),
    PTheme("frost", "Frost", false, c(0xFFE9EEF5), c(0xFFFFFFFF), c(0xFF1C2433), c(0xFF5B6678), c(0xFF4F6DDB), c(0xFFD0D8E6), 14.dp,
        glassA = .6f, wall = Brush.linearGradient(listOf(c(0xFFDDE7F5), c(0xFFB8C9E8), c(0xFFE5D9F2)))),
)

val Accents = listOf(0xFF0067C0, 0xFF00A388, 0xFF6750A4, 0xFFD13438, 0xFFE67E22, 0xFF2E7D32, 0xFFC2185B,
    0xFF2D58C8, 0xFFEE3836, 0xFF3B828F, 0xFF3C698C).map { Color(it) }  // 7..10: the wallpaper themes A, B, C, D

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
    var windowTheme by mutableStateOf("win") // win (follows the app) / material / amoled: pop-up windows, dialogs, menus
    var dynamic by mutableStateOf(true)      // Material You: the wallpaper colours on Android 12+
    var corner by mutableStateOf("medium")   // Material corners: small / medium / large
    val cornerDp get() = when (corner) { "small" -> 8.dp; "large" -> 28.dp; else -> 20.dp }
    @Volatile var resolved: PTheme? = null   // the Material look as resolved for this composition (see effective in MainActivity)
    val current get() = resolved ?: theme
    val accent get() = if ((current.material || !current.tabs || current.id == "win10") && accentIdx in Accents.indices) Accents[accentIdx] else current.accent
}

/** Text colour on an accent: black on light accents, white on dark ones. */
fun onAccent(c: Color): Color = if (c.luminance() > .45f) Color.Black else Color.White

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
    "win10" -> PTheme(t.id, t.name, true, c(0xFF000000), c(0xFF0E0E0E), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFF4CC2FF), c(0xFF2A2A2A), t.radius, tabs = true, titleName = t.titleName, glassA = t.glassA)
    else -> null
}

/** The look of pop-up windows, dialogs and drop-down menus; Root provides it from Look.windowTheme. */
val LocalWindowLook = compositionLocalOf<PTheme> { Themes[0] }

/** The AMOLED window: pure black, panels #111111, the app accent. */
fun amoledWindow(): PTheme = PTheme("amoled", "AMOLED", true, c(0xFF000000), c(0xFF111111), c(0xFFFFFFFF), c(0xFF9E9E9E), c(0xFFFFFFFF), c(0xFF2A2A2A), 16.dp, material = true)

/** A Material AlertDialog drawn in the window look: its panel, text and corners. */
@Composable
fun WinAlert(onDismissRequest: () -> Unit, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)? = null, containerColor: Color? = null) {
    val w = LocalWindowLook.current
    val cs = if (w.dark) darkColorScheme(primary = Look.accent, background = w.bg, surface = w.panel, surfaceContainerHigh = w.panel, onSurface = w.text, onSurfaceVariant = w.sub, outline = w.line)
        else lightColorScheme(primary = Look.accent, background = w.bg, surface = w.panel, surfaceContainerHigh = w.panel, onSurface = w.text, onSurfaceVariant = w.sub, outline = w.line)
    MaterialTheme(colorScheme = cs) {
        AlertDialog(onDismissRequest, title = title, text = text, confirmButton = confirmButton, dismissButton = dismissButton, containerColor = containerColor ?: w.panel, shape = RoundedCornerShape(w.radius))
    }
}
