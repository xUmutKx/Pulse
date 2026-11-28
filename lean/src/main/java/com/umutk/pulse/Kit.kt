package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
