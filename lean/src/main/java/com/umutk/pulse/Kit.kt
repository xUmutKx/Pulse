package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The stand-ins of Pulse's look kit for the lean apps (see lean/build.gradle.kts): the app's code asks for T, Look, tr, PIcon, PChip, PField and frame, and gets these
 * small versions (Material and AMOLED black, no Windows looks) instead of Pulse's look system, which is most of the size of Pulse.
 */

/** The colours of the look: AMOLED black in the dark, Material in the light. */
object T {
    var dark by mutableStateOf(true)
    val id: String get() = if (dark) "amoled" else "material"
    val tabs: Boolean get() = false
    val bg: Color get() = if (dark) Color.Black else Color(0xFFFFFBFE)
    val panel: Color get() = if (dark) Color(0xFF141414) else Color(0xFFF1EDF7)
    val text: Color get() = if (dark) Color(0xFFEDEDED) else Color(0xFF1C1B1F)
    val sub: Color get() = if (dark) Color(0xFF9A9AA2) else Color(0xFF605D66)
}

/** Text on a surface of colour [c]: black on a light one, white on a dark one. */
fun onAccent(c: Color): Color = if (c.luminance() > .5f) Color.Black else Color.White

object Look { val accent: Color get() = Color(0xFF4FC3F7) }

/** English or Turkish by the phone's language. */
fun tr(en: String, tr: String = en): String = if (java.util.Locale.getDefault().language == "tr") tr else en

/** The icons the lean apps use, as plain glyphs (no icon library). */
@Composable
fun PIcon(key: String, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    val g = when (key) { "back" -> "←"; "menu" -> "≡"; "function" -> "ƒ"; "history" -> "↺"; "image" -> "▣"; "palette" -> "◐"; "rotate" -> "⇄"; "scale" -> "⚖"; "close" -> "×"; "search" -> "⌕"; else -> "•" }
    Box(modifier, contentAlignment = Alignment.Center) { Text(g, color = tint, fontSize = (size.value * .9f).sp) }
}

@Composable
fun PChip(on: Boolean, label: String, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp)).background(if (on) Look.accent else T.text.copy(alpha = .08f)).clickable(onClick = onClick).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, color = if (on) Color.Black else T.text, fontSize = 13.sp)
    }
}

@Composable
fun PField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    BasicTextField(value, onChange, modifier.clip(RoundedCornerShape(12.dp)).background(T.text.copy(alpha = .07f)), singleLine = true,
        textStyle = TextStyle(color = T.text, fontSize = 15.sp), cursorBrush = SolidColor(Look.accent),
        decorationBox = { inner -> Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) { if (value.isEmpty()) Text(hint, color = T.sub, fontSize = 15.sp); inner() } })
}

/** A frame round a part of the screen: the Windows looks draw one; the lean apps draw none. */
fun Modifier.frame(@Suppress("UNUSED_PARAMETER") t: Any?, raised: Boolean = false): Modifier = this


// ---- the rest of the kit that the lean apps use (the same names and shapes as Pulse's) -------------------------------------------------------------------------------

val pageGutter: Dp get() = 16.dp
val pageBg: Color get() = T.bg

/** The look of the window in front (the lean apps have one: the colours of T). */
class PTheme {
    val id: String get() = T.id
    val text: Color get() = T.text
    val sub: Color get() = T.sub
    val bg: Color get() = T.bg
    val panel: Color get() = T.panel
    val line: Color get() = T.text.copy(alpha = .15f)
    val accent: Color get() = Look.accent
    val dark: Boolean get() = T.dark
    val material: Boolean get() = true
    val tabs: Boolean get() = false
    val radius: Dp get() = 12.dp
}
val LocalWindowLook = androidx.compose.runtime.staticCompositionLocalOf { PTheme() }

@Composable
fun PButton(text: String, modifier: Modifier = Modifier, danger: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, compact: Boolean = false, onClick: () -> Unit) {
    val fg = if (danger) Color(0xFFE5484D) else T.text
    Box(modifier.clip(RoundedCornerShape(50)).background(fg.copy(alpha = .1f)).clickable(onClick = onClick).padding(horizontal = if (compact) 12.dp else 22.dp, vertical = if (compact) 6.dp else 10.dp), contentAlignment = Alignment.Center) {
        Text(text, color = fg, fontSize = if (compact) 12.sp else 14.sp, maxLines = 1)
    }
}

@Composable
fun PCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(T.panel).padding(14.dp), content = content)
}

@Composable
fun PHead(text: String, trailing: String = "", guide: String = "") {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = T.text, fontSize = 22.sp, modifier = Modifier.weight(1f))
        if (trailing.isNotEmpty()) Text(trailing, color = T.sub, fontSize = 13.sp)
    }
}

@Composable
fun PSwitch(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.Switch(on, onChange, enabled = enabled, colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Look.accent, checkedThumbColor = Color.White))
}

@Composable
fun PCheck(on: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    androidx.compose.material3.Checkbox(on, onChange, enabled = enabled, colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = Look.accent))
}

@Composable
fun PRadio(on: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.RadioButton(on, onClick, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = Look.accent))
}

@Composable
fun PSlider(value: Float, onChange: (Float) -> Unit, range: ClosedFloatingPointRange<Float>, thick: Boolean = false, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    androidx.compose.material3.Slider(value, onChange, modifier, valueRange = range, onValueChangeFinished = onDone,
        colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent))
}

@Composable
fun PDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, info: Boolean = false) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { androidx.compose.material3.TextButton(onConfirm) { Text(confirm) } },
        dismissButton = if (info) null else ({ androidx.compose.material3.TextButton(onDismiss) { Text(tr("Cancel", "Vazgeç")) } }),
        containerColor = T.panel, titleContentColor = T.text, textContentColor = T.sub)
}

@Composable
fun WinAlert(onDismissRequest: () -> Unit, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null, confirmButton: @Composable () -> Unit, dismissButton: (@Composable () -> Unit)? = null,
             containerColor: Color? = null, noDim: Boolean = false, warn: Boolean = false, iconKey: String? = null) {
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismissRequest, title = title, text = text, confirmButton = confirmButton, dismissButton = dismissButton,
        containerColor = containerColor ?: T.panel, titleContentColor = T.text, textContentColor = T.sub)
}

/** Pulse's per-tool look chooser: a lean app has one look (the phone's light or dark), so these draw nothing. */
@Composable fun ExtraLookRow(ink: Color = T.text) {}
@Composable fun ExtraLookIcon(tint: Color = T.text) {}
