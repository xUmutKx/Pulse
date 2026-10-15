package com.umutk.pulse

import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.ui.window.DialogWindowProvider
import android.view.WindowManager
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Warning
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke

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
    PTheme("win11", "Windows 11", true, c(0xFF000000), c(0xFF1C1C1C), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFF0067C0), c(0xFF3A3A3A), 8.dp, glassA = .85f),
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
    amoledWindow(),   // the app's own AMOLED black (pure black background, grey panels)
)

/** Material accents: pastel, plus the four wallpaper themes A-D (indices 7..10). */
val MaterialAccents = listOf(0xFFA8D8EA, 0xFFB5EAD7, 0xFFC7CEEA, 0xFFFFDAC1, 0xFFFFB7B2, 0xFFE2F0CB, 0xFFF8BBD0,
    0xFF2D58C8, 0xFFEE3836, 0xFF3B828F, 0xFF3C698C,
    0xFFD1C4E9, 0xFFB2DFDB, 0xFFFFF9C4, 0xFFBBDEFB, 0xFFC8E6C9, 0xFFF0C6DA, 0xFFD7CCC8, 0xFFFFE0B2, 0xFFB3E5FC, 0xFFE1BEE7).map { Color(it) }

/** Windows accents: the classic Task Manager colours, with the wallpaper themes A-D at 7..10. */
val WinAccents = listOf(0xFF0067C0, 0xFF00A388, 0xFF6750A4, 0xFFD13438, 0xFFE67E22, 0xFF2E7D32, 0xFFC2185B,
    0xFF2D58C8, 0xFFEE3836, 0xFF3B828F, 0xFF3C698C).map { Color(it) }

/** The accent swatches for a look: pastel for Material, the Windows colours for the Windows looks. */
fun accentsFor(material: Boolean): List<Color> = if (material) MaterialAccents else WinAccents

/** Resource colours, as in Windows Task Manager. */
fun resColor(kind: String): Color = when (kind) {
    "cpu" -> Color(0xFF1B8DBE); "memory" -> Color(0xFF8B12AE); "disk" -> Color(0xFF4DA64D)
    "net" -> Color(0xFFD97B29); "gpu" -> Color(0xFF1F6FBF); "battery" -> Color(0xFF2E9E5B); else -> Color(0xFFC0392B)
}

/** Whether the "More themes" group in Settings > Appearance is unfolded. */
object ThemeFold { var open by mutableStateOf(false) }

object Look {
    var theme by mutableStateOf(Themes[0])
    var mode by mutableStateOf("system")     // system / light / dark (the classic Windows looks stay as they were)
    var effect by mutableStateOf("mica")     // none / mica / micaAlt / acrylic / tabbed (sidebar looks only)
    var blurDp by mutableStateOf(8f)        // "Blur" material: blur radius 0..40
    var blurTint by mutableStateOf(.2f)      // "Blur" material: darkness 0..1
    var accentIdx by mutableStateOf(-1)      // -1: the theme's own accent
    var windowTheme by mutableStateOf("win") // win (default: each look draws its own dialogs) / material / amoled: pop-up windows, dialogs, menus
    var dynamic by mutableStateOf(true)      // Material You: the wallpaper colours on Android 12+
    var corner by mutableStateOf("medium")   // Material corners: small / medium / large
    val cornerDp get() = when (corner) { "small" -> 8.dp; "large" -> 28.dp; else -> 20.dp }
    @Volatile var resolved: PTheme? = null   // the Material look as resolved for this composition (see effective in MainActivity)
    val current get() = resolved ?: theme
    val accent get() = (if (current.material || !current.tabs || current.id == "win10" || current.id == "win11") accentsFor(current.material).getOrNull(accentIdx) ?: current.accent else current.accent)
        // Windows 11: the accent is drawn half see-through
        .let { if (current.id == "win11") it.copy(alpha = .5f) else it }
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

/** The light twin of Windows 11: the base look is the dark one, so the light mode needs its own colours. */
fun lightTwin(t: PTheme): PTheme = PTheme(t.id, t.name, false, c(0xFFF3F3F3), c(0xFFFFFFFF), c(0xFF000000), c(0xFF5C5C5C), c(0xFF0067C0), c(0xFFE5E5E5), t.radius, glassA = t.glassA)

/** The dark twin of a light sidebar theme (Windows 11 / 10); others have none. */
fun darkTwin(t: PTheme): PTheme? = when (t.id) {
    // Windows 11 takes the accent swatch too (PTheme.accent); the background is black (AMOLED) or dark grey
    "win11" -> if (Opt.amoledBg) PTheme(t.id, t.name, true, c(0xFF000000), c(0xFF111111), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFFD0D0D0), c(0xFF2A2A2A), t.radius)
        else PTheme(t.id, t.name, true, c(0xFF202020), c(0xFF2B2B2B), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFFD0D0D0), c(0xFF3A3A3A), t.radius)
    "win10" -> PTheme(t.id, t.name, true, c(0xFF000000), c(0xFF0E0E0E), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFF4CC2FF), c(0xFF2A2A2A), t.radius, tabs = true, titleName = t.titleName, glassA = t.glassA)
    else -> null
}

/** The look of pop-up windows, dialogs and drop-down menus; Root provides it from Look.windowTheme. */
val LocalWindowLook = compositionLocalOf<PTheme> { Themes[0] }

/** The AMOLED window: pure black, panels #111111, white text and accent, no colour. */
fun amoledWindow(): PTheme = PTheme("amoled", "AMOLED", true, c(0xFF000000), c(0xFF111111), c(0xFFFFFFFF), c(0xFF9E9E9E), c(0xFFFFFFFF), c(0xFF2A2A2A), 16.dp, material = true)

/** Material pop-up window in black and white only: no tint, accent black on light or white on dark. */
fun monoWindow(t: PTheme): PTheme = if (t.dark) PTheme(t.id, t.name, true, c(0xFF000000), c(0xFF111111), c(0xFFFFFFFF), c(0xFF9E9E9E), c(0xFFFFFFFF), c(0xFF2A2A2A), t.radius, material = true)
    else PTheme(t.id, t.name, false, c(0xFFFFFFFF), c(0xFFF2F2F2), c(0xFF000000), c(0xFF5C5C5C), c(0xFF000000), c(0xFFCCCCCC), t.radius, material = true)

/** A Material AlertDialog drawn in the window look: its panel, text and corners. */
@Composable
fun WinAlert(onDismissRequest: () -> Unit, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)? = null, containerColor: Color? = null, noDim: Boolean = false, warn: Boolean = false, iconKey: String? = null) {
    val w = LocalWindowLook.current
    // noDim: the window behind the dialog stays as it is, no dark scrim (the installer)
    val view = LocalView.current
    if (noDim) SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { it.setDimAmount(0f); it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); it.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)) } }
    if (w.id in winButtonLooks) { WinDialog(w, onDismissRequest, title, text, confirmButton, dismissButton, containerColor, noDim, warn, iconKey); return }
    // Material and AMOLED windows use their own accent (black or white); the Windows looks keep the app's accent
    val primary = when { w.tabs -> w.text; w.material -> w.accent; else -> Look.accent }
    val cs = if (w.dark) darkColorScheme(primary = primary, background = w.bg, surface = w.panel, surfaceContainerHigh = w.panel, onSurface = w.text, onSurfaceVariant = w.sub, outline = w.line)
        else lightColorScheme(primary = primary, background = w.bg, surface = w.panel, surfaceContainerHigh = w.panel, onSurface = w.text, onSurfaceVariant = w.sub, outline = w.line)
    MaterialTheme(colorScheme = cs) {
        // the Windows looks get a flat window: no tonal lift, the look's own corners, and a thin edge like a real dialog
        val flat = !w.material
        val classicBar = w.tabs && title != null
        val barTitle: (@Composable () -> Unit)? = if (!classicBar) title else ({
            Row(Modifier.fillMaxWidth().height(26.dp)
                .then(w.title?.let { Modifier.background(it) } ?: Modifier.background(w.panel))
                .padding(start = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides w.titleText) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { title!!() }
                }
                // XP and 7 close with a red square, as in the reference dialogs; the others keep the plain cross
                val redClose = w.id == "xp" || w.id == "win7"
                androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 4.dp).size(if (redClose) 18.dp else 20.dp)
                    .then(if (redClose) Modifier.background(Color(0xFFE0523E), RoundedCornerShape(3.dp)) else Modifier),
                    contentAlignment = Alignment.Center) {
                    Text("\u2715", color = if (redClose) Color.White else w.titleText, fontSize = 10.sp)
                }
            }
        })
        // warn: the look's own alert glyph beside the text, as the real Windows dialogs show it (yellow triangle on the classic looks, blue i otherwise)
        val body: (@Composable () -> Unit)? = if (!warn || text == null || w.material) text else ({
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AlertGlyph(w)
                Box(Modifier.weight(1f)) { text() }
            }
        })
        AlertDialog(onDismissRequest, title = barTitle, text = body,
            icon = if (!w.material || noDim) null else ({ androidx.compose.material3.Icon(if (warn) androidx.compose.material.icons.Icons.Outlined.Warning else androidx.compose.material.icons.Icons.Outlined.Info, null, Modifier.size(24.dp), tint = if (warn) Color(0xFFE5A100) else w.text) }),
            confirmButton = { WinButton(w, primary = true, content = confirmButton) },
            dismissButton = if (dismissButton == null) null else ({ WinButton(w, primary = false, content = dismissButton) }),
            // the look's glass amount makes the box see-through (mica / acrylic); the accent is only a 1 px edge
            containerColor = containerColor ?: (if (w.glassA > 0f) w.panel.copy(alpha = w.glassA) else w.panel), shape = RoundedCornerShape(if (w.material) 28.dp else w.radius),
            tonalElevation = if (flat) 0.dp else AlertDialogDefaults.TonalElevation,
            modifier = if (flat) Modifier.border(1.dp, w.line, RoundedCornerShape(w.radius)) else if (w.id == "amoled") Modifier.border(1.dp, w.line, RoundedCornerShape(28.dp)) else Modifier)
    }
}

/** Looks that draw a real Windows message box and button frame in WinAlert; the material and other looks keep the AlertDialog and the caller's button. */
val winButtonLooks = setOf("win95", "xp", "win7", "win10", "win11")

/** A Windows message box in the version's look: frame, title bar with caption buttons, icon beside the text, buttons at the bottom right. */
@Composable
private fun WinDialog(w: PTheme, onDismissRequest: () -> Unit, title: (@Composable () -> Unit)?, text: (@Composable () -> Unit)?, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)?, containerColor: Color?, noDim: Boolean, warn: Boolean, iconKey: String? = null) {
    Dialog(onDismissRequest, DialogProperties(usePlatformDefaultWidth = false)) {
        val view = LocalView.current
        if (noDim) SideEffect { (view.parent as? DialogWindowProvider)?.window?.let { it.setDimAmount(0f); it.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND) } }
        val id = w.id
        val classic = id == "win95" || id == "xp"
        // Windows 11 is acrylic: the panel and the button strip let the screen behind show through, and the window blurs it where the phone can
        if (id == "win11") SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { win ->
                runCatching {
                    if (android.os.Build.VERSION.SDK_INT >= 31) { win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND); win.attributes = win.attributes.apply { blurBehindRadius = 40 } }
                }
            }
        }
        val body = containerColor ?: when (id) { "win95" -> Color(0xFFC0C0C0); "xp" -> Color(0xFFECE9D8); "win7" -> Color.White; "win11" -> w.panel.copy(alpha = .72f); else -> w.panel }
        val footer = when (id) { "win7" -> Color(0xFFF0F0F0); "win10" -> if (w.dark) Color(0xFF1F1F1F) else Color(0xFFF0F0F0); "win11" -> w.bg.copy(alpha = .5f); else -> body }
        val titleCol = if (classic) Color.White else w.text
        val outer = when (id) {
            "win95" -> Modifier.background(body).bevel(true).padding(3.dp)
            "xp" -> Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(Color(0xFF0054E3)).padding(start = 3.dp, end = 3.dp, bottom = 3.dp)
            // Aero: a light blue glass frame around the white body
            "win7" -> Modifier.clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(Color(0xF0C9DEF3), Color(0xE89DBDE0), Color(0xE8B4CEEB))))
                .border(1.dp, Color(0xFF3A4A5E), RoundedCornerShape(6.dp)).padding(1.dp).border(1.dp, Color.White.copy(alpha = .55f), RoundedCornerShape(5.dp))
                .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
            "win10" -> Modifier.background(body).border(1.dp, Look.accent.copy(alpha = 1f))
            else -> Modifier.clip(RoundedCornerShape(8.dp)).background(if (id == "win11") Color.Transparent else body).border(1.dp, w.line, RoundedCornerShape(8.dp))
        }
        Column(Modifier.padding(16.dp).widthIn(min = 280.dp, max = 480.dp).then(outer)) {
            Row(Modifier.fillMaxWidth().height(when (id) { "win95" -> 20.dp; "xp", "win7" -> 28.dp; else -> 32.dp })
                .then(if (id == "xp") Modifier.background(Brush.verticalGradient(0f to Color(0xFF0058EE), .14f to Color(0xFF3A93FF), .32f to Color(0xFF0A60EE), .85f to Color(0xFF0050E0), 1f to Color(0xFF0046C8)))
                .drawBehind { drawLine(Color(0xFF7DB4FF), Offset(0f, 1.dp.toPx() / 2), Offset(size.width, 1.dp.toPx() / 2), 1.dp.toPx()) } else if (id == "win95") Modifier.background(Color(0xFF000080)) else if (classic) w.title?.let { Modifier.background(it) } ?: Modifier else if (id == "win11") Modifier.background(body) else Modifier)
                .padding(start = if (id == "win95") 4.dp else 10.dp, end = if (classic || id == "win7") 2.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (id == "win95") { TitleIcon95(); Spacer(Modifier.width(3.dp)) }
                Box(Modifier.weight(1f)) {
                    if (title != null) CompositionLocalProvider(LocalContentColor provides titleCol) {
                        ProvideTextStyle(TextStyle(color = titleCol, fontSize = if (id == "xp") 13.sp else 12.sp, fontWeight = if (classic) FontWeight.Bold else FontWeight.Normal,
                            shadow = if (id == "xp") androidx.compose.ui.graphics.Shadow(Color(0xFF0A1E6E), Offset(1.5f, 1.5f), 1.5f) else null)) { title() }
                    }
                }
                CaptionButtons(w, titleCol, onDismissRequest)
            }
            Column(Modifier.weight(1f, fill = false).background(if (id == "win11") Color.Transparent else body).then(if (id == "win7") Modifier.border(1.dp, Color(0xFF8A9BB0)) else Modifier)) {
                if (text != null) Row(Modifier.weight(1f, fill = false).fillMaxWidth().then(if (id == "win11") Modifier.background(body) else Modifier).padding(if (classic) 14.dp else 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (iconKey != null) Box(Modifier.size(32.dp)) { PIcon(iconKey, if (iconKey == "close") Color(0xFFC42B1C) else w.text, 32.dp) } else ThemeMsgIcon(id, warn)
                    Box(Modifier.weight(1f).padding(top = 6.dp)) {
                        CompositionLocalProvider(LocalContentColor provides w.text) {
                            ProvideTextStyle(TextStyle(color = w.text, fontSize = if (id == "win95") 12.sp else 14.sp)) { text() }
                        }
                    }
                }
                // win7/10/11: the buttons sit on their own grey strip with a hairline above it
                val strip = id == "win7" || id == "win10" || id == "win11"
                Row(Modifier.fillMaxWidth().background(footer)
                    .then(if (strip) Modifier.drawBehind { drawLine(if (w.dark) Color(0xFF3A3A3A) else Color(0xFFDFDFDF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) } else Modifier)
                    .padding(horizontal = 12.dp, vertical = if (strip) 12.dp else 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    WinButton(w, primary = true, content = confirmButton)
                    if (dismissButton != null) WinButton(w, primary = false, content = dismissButton)
                }
            }
        }
    }
}

/** The title bar's caption buttons: 95 bevelled − and × with pixel glyphs, XP and 7 their coloured − and ×, 10 and 11 plain glyphs. − dismisses too (a dialog has nowhere to minimise to). */
@Composable
private fun CaptionButtons(w: PTheme, ink: Color, onClose: () -> Unit) {
    // shortcut: no □ maximise, a fixed-size message box has none in real Windows either
    val id = w.id
    @Composable
    fun cap(close: Boolean, box: Modifier, glyph: Color, low: Boolean, stroke: Dp, g: Dp = 9.dp) {
        // Windows 10 / 11: the × turns red with a white glyph while pressed, the − a faint grey
        val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val down by src.collectIsPressedAsState()
        val fluent = id == "win10" || id == "win11"
        val hot = fluent && close && down
        val hotBg = if (id == "win11") Color(0xFFC42B1C) else Color(0xFFE81123)
        Box(box.then(if (hot) Modifier.background(hotBg) else if (fluent && down) Modifier.background(ink.copy(alpha = .1f)) else Modifier).clickable(interactionSource = src, indication = null, onClick = onClose).semantics { contentDescription = if (close) "Close" else "Minimise" }, contentAlignment = Alignment.Center) {
            val glyph = if (hot) Color.White else glyph
            if (id == "win95") CapGlyph95(close) else Canvas(Modifier.size(g)) {
                val s = stroke.toPx(); val wd = size.width; val ht = size.height
                if (fluent) {
                    // Segoe Fluent glyphs: whole-pixel stroke on the pixel grid, square ends, so the lines stay sharp
                    val p = kotlin.math.max(1f, kotlin.math.round(s)); val e = kotlin.math.floor(wd); val o = if (p.toInt() % 2 == 1) .5f else 0f
                    if (close) { drawLine(glyph, Offset(o, o), Offset(e - o, e - o), p); drawLine(glyph, Offset(e - o, o), Offset(o, e - o), p) }
                    else { val y = kotlin.math.floor(ht / 2) + o; drawLine(glyph, Offset(0f, y), Offset(e, y), p) }
                }
                else if (close) { drawLine(glyph, Offset(0f, 0f), Offset(wd, ht), s); drawLine(glyph, Offset(wd, 0f), Offset(0f, ht), s) }
                else { val y = if (low) ht - s / 2 else ht / 2; drawLine(glyph, Offset(if (low) wd * .1f else 0f, y), Offset(if (low) wd * .6f else wd, y), s) }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (id == "xp") 2.dp else 0.dp)) {
        when (id) {
            "win95" -> {
                // − and × as separate bevelled buttons, 2 px apart like the real caption
                cap(false, Modifier.size(16.dp, 14.dp).background(Color(0xFFC0C0C0)).bevel(true), Color.Black, false, 1.dp)
                Spacer(Modifier.width(2.dp))
                cap(true, Modifier.size(16.dp, 14.dp).background(Color(0xFFC0C0C0)).bevel(true), Color.Black, false, 1.dp)
            }
            "xp" -> {
                // the whole button is painted here; cap() only gives it the click and a transparent glyph
                cap(false, Modifier.size(21.dp).drawBehind { xpCaption(false) }, Color.Transparent, true, 0.dp)
                cap(true, Modifier.size(21.dp).drawBehind { xpCaption(true) }, Color.Transparent, false, 0.dp)
            }
            // hang from the frame top, just under its outline: the 28 dp title row centres us, so lift by the gap
            "win7" -> Row(Modifier.offset(y = (-4.5).dp)) { Cap7(false, onClose); Cap7(true, onClose) }
            else -> {
                val hov = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val red = hov.collectIsHoveredAsState().value
                // real caption buttons: 46 x 32 with a 10 px glyph drawn in 1 px lines
                cap(false, Modifier.size(46.dp, 32.dp), ink, false, 1.dp, 10.dp)
                cap(true, Modifier.size(46.dp, 32.dp).then(if (id == "win11") Modifier.hoverable(hov).background(if (red) Color(0xFFC42B1C) else Color.Transparent) else Modifier), if (id == "win11" && red) Color.White else ink, false, 1.dp, 10.dp)
            }
        }
    }
}

/** XP Luna caption button art: white-rimmed rounded square, glossy orange-red (×) or blue (−) face, thick white glyph with a dark drop shadow. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.xpCaption(close: Boolean, max: Boolean = false) {
    val p = 1.dp.toPx(); val s = size.width; val r = CornerRadius(3.dp.toPx())
    val (hi, mid, lo) = if (close) Triple(Color(0xFFF4B7A2), Color(0xFFE2582F), Color(0xFFB8300C)) else Triple(Color(0xFFB4CFFB), Color(0xFF3D7BF0), Color(0xFF1846C8))
    drawRoundRect(Color(0x55001C6B), Offset(0f, p), Size(s, s), r)
    drawRoundRect(Color.White, cornerRadius = r)
    // face: light top-left corner fading to the deep bottom-right, like the Luna bitmap
    drawRoundRect(Brush.linearGradient(listOf(hi, mid, lo), Offset(0f, 0f), Offset(s, s)), Offset(p, p), Size(s - 2 * p, s - 2 * p), CornerRadius(r.x - p))
    drawRoundRect(Brush.radialGradient(listOf(Color.White.copy(alpha = .45f), Color.Transparent), Offset(s * .3f, s * .25f), s * .55f), Offset(p, p), Size(s - 2 * p, s - 2 * p), CornerRadius(r.x - p))
    val w = s * .12f; val cap = androidx.compose.ui.graphics.StrokeCap.Square
    fun glyph(c: Color, o: Float) {
        if (close) { val a = s * .3f; val b = s * .7f
            drawLine(c, Offset(a + o, a + o), Offset(b + o, b + o), w, cap); drawLine(c, Offset(b + o, a + o), Offset(a + o, b + o), w, cap) }
        else if (max) { // maximise: a box with a thick top edge
            drawRect(c, Offset(s * .27f + o, s * .27f + o), Size(s * .46f, s * .46f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * .6f))
            drawRect(c, Offset(s * .27f + o, s * .27f + o), Size(s * .46f, w)) }
        else drawLine(c, Offset(s * .27f + o, s * .7f + o), Offset(s * .55f + o, s * .7f + o), w * 1.15f, cap)
    }
    glyph(if (close) Color(0x887A1800) else Color(0x88002A90), p * .8f)
    glyph(Color.White, 0f)
}

/** The message box icon: a yellow warning triangle with "!" for warnings, a blue round "i" for information. */
@Composable
internal fun MsgIconDefault(warn: Boolean) {
    Canvas(Modifier.size(32.dp)) {
        val s = size.width; val bar = s * .12f
        if (warn) {
            val tri = Path().apply { moveTo(s / 2, s * .06f); lineTo(s * .97f, s * .9f); lineTo(s * .03f, s * .9f); close() }
            drawPath(tri, Color(0xFFFFC83D)); drawPath(tri, Color(0xFF9A6A00), style = Stroke(s * .04f, join = StrokeJoin.Round))
            drawRoundRect(Color.Black, Offset(s / 2 - bar / 2, s * .34f), Size(bar, s * .3f), CornerRadius(bar / 2))
            drawCircle(Color.Black, bar * .6f, Offset(s / 2, s * .77f))
        } else {
            drawCircle(Brush.verticalGradient(listOf(Color(0xFF4FA3F7), Color(0xFF1C5FC2))), s / 2)
            drawCircle(Color.White, bar * .62f, Offset(s / 2, s * .28f))
            drawRoundRect(Color.White, Offset(s / 2 - bar / 2, s * .42f), Size(bar, s * .36f), CornerRadius(bar / 3))
        }
    }
}

/** Frames a dialog button in its look: 95 grey bevel, XP and 7 gradients, 10 flat, 11 panel (accent fill on the confirm button). */
@Composable
private fun WinButton(w: PTheme, primary: Boolean, content: @Composable () -> Unit) {
    if (w.id !in winButtonLooks) { content(); return }
    var focused by remember { mutableStateOf(false) }
    val accentFill = primary && w.id == "win11"
    val fill = Look.accent.copy(alpha = 1f)
    // the caller's text colour: it must read on the accent fill, and the Win11 cancel stays in the text colour
    val text = if (accentFill) onAccent(fill) else if (w.id == "win11") w.text else if (w.id == "xp" || w.id == "win95") Color.Black else MaterialTheme.colorScheme.primary
    val radius = when (w.id) { "xp", "win7" -> 3.dp; "win11" -> 6.dp; "win10" -> 4.dp; else -> 0.dp }
    val edge = when (w.id) { "win95" -> Color.Black; "xp" -> Color(0xFF003C74); "win7" -> if (primary) Color(0xFF3C7FB1) else Color(0xFF707070); "win10" -> if (primary) Look.accent.copy(alpha = 1f) else Color(0xFFADADAD); else -> w.line }
    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = text), typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Box(Modifier.clip(RoundedCornerShape(radius)).onFocusChanged { focused = it.hasFocus }.drawBehind {
            val p = 1.dp.toPx(); val r = radius.toPx(); val wd = size.width; val ht = size.height
            val inner: Brush = when (w.id) {
                "win95" -> SolidColor(Color(0xFFC0C0C0))
                "xp" -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F3EE), Color(0xFFECEBE6), Color(0xFFD6D0C5)))
                // Aero: grey glass, lighter top half with a sharp gloss line at mid height
                "win7" -> Brush.verticalGradient(0f to Color(0xFFF2F2F2), .5f to Color(0xFFEBEBEB), .5f to Color(0xFFDDDDDD), 1f to Color(0xFFCFCFCF))
                "win10" -> SolidColor(if (w.dark) Color(0xFF333333) else Color(0xFFE1E1E1))
                else -> SolidColor(if (accentFill) fill else Color.White.copy(alpha = if (w.dark) .08f else .6f))
            }
            drawRoundRect(brush = inner, cornerRadius = CornerRadius(r))
            if (w.id == "win95") {
                // default button: a 1-px black outline outside the bevel; bevel = white top-left, black then grey bottom-right
                val o = if (primary) p else 0f
                if (primary) drawRect(Color.Black, Offset(p / 2, p / 2), Size(wd - p, ht - p), style = Stroke(p))
                drawRect(Color.White, Offset(o, o), Size(wd - 2 * o - p, p)); drawRect(Color.White, Offset(o, o), Size(p, ht - 2 * o - p))
                drawRect(Color.Black, Offset(o, ht - o - p), Size(wd - 2 * o, p)); drawRect(Color.Black, Offset(wd - o - p, o), Size(p, ht - 2 * o))
                drawRect(Color(0xFF808080), Offset(o + p, ht - o - 2 * p), Size(wd - 2 * o - 2 * p, p)); drawRect(Color(0xFF808080), Offset(wd - o - 2 * p, o + p), Size(p, ht - 2 * o - 2 * p))
            } else {
                drawRoundRect(color = edge, topLeft = Offset(p / 2, p / 2), size = Size(wd - p, ht - p), cornerRadius = CornerRadius(r), style = Stroke(p))
            }
            // XP default button: pale-to-deep blue inner ring, and the dotted focus box it always shows
            if (w.id == "xp" && primary) drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFCEE7FF), Color(0xFF6982EE))), Offset(1.5f * p, 1.5f * p), Size(wd - 3 * p, ht - 3 * p), CornerRadius(r - p), style = Stroke(2f * p))
            // win7 default button: a soft blue glow inside the blue edge; the others get a white inner edge
            if (w.id == "win7") drawRoundRect(if (primary) Color(0xFF7FD3F8).copy(alpha = .8f) else Color.White.copy(alpha = .7f), Offset(1.5f * p, 1.5f * p), Size(wd - 3 * p, ht - 3 * p), CornerRadius(r - p), style = Stroke(if (primary) 2f * p else p))
            // keyboard focus: a dotted rectangle inside the frame
            val f = if (w.id == "win95") 4 * p else 3 * p
            if (focused || ((w.id == "xp" || w.id == "win95") && primary)) drawRect(Color.Black, Offset(f, f), Size(wd - 2 * f, ht - 2 * f), style = Stroke(p, pathEffect = PathEffect.dashPathEffect(if (w.id == "win95") floatArrayOf(p, p) else floatArrayOf(2 * p, 2 * p))))
        }.then(if (w.id != "win95") Modifier else Modifier.layout { m, c ->
            // 95 buttons are 75x23: the caller's button is centred in that box and clipped by it
            val pl = m.measure(c.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
            val bw = maxOf(pl.width, 75.dp.roundToPx()).coerceIn(c.minWidth, c.maxWidth); val bh = 23.dp.roundToPx()
            layout(bw, bh) { pl.place((bw - pl.width) / 2, (bh - pl.height) / 2) }
        })) { content() }
    }
}

/** The alert glyph of a pop-up:a yellow warning triangle with a black mark on the Windows 95 / 7 / XP looks, a blue round i otherwise. */
@Composable
fun AlertGlyph(w: PTheme) {
    if (w.tabs) {
        androidx.compose.foundation.Canvas(Modifier.size(34.dp)) {
            val tri = androidx.compose.ui.graphics.Path().apply { moveTo(size.width / 2, 2f); lineTo(size.width - 2f, size.height - 3f); lineTo(2f, size.height - 3f); close() }
            drawPath(tri, Color(0xFFFFD800)); drawPath(tri, Color.Black, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawRect(Color.Black, Offset(size.width / 2 - 2.5f, size.height * .36f), Size(5f, size.height * .34f))
            drawRect(Color.Black, Offset(size.width / 2 - 2.5f, size.height * .76f), Size(5f, 4f))
        }
    } else {
        androidx.compose.foundation.Canvas(Modifier.size(34.dp)) {
            drawCircle(Color(0xFF2F7DD6), radius = size.width / 2)
            drawRect(Color.White, Offset(size.width / 2 - 2.5f, size.height * .45f), Size(5f, size.height * .3f))
            drawCircle(Color.White, radius = 2.8f, center = Offset(size.width / 2, size.height * .3f))
        }
    }
}
