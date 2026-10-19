package com.umutk.pulse.extras.whitenoise

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.umutk.pulse.bevel

/** One noise look: palette plus a [style] (0 soft, 1 pill, 2 block, 3 outline, 4 glass, 5 winamp, 6 Windows 95). Colours are ARGB ints so the widget can use them too. */
class NoiseLook(val id: String, val name: String, val bg1: Int, val bg2: Int, val accent: Int, val text: Int, val style: Int) {
    val winamp get() = style == 5
    val win95 get() = style == 6
    val cBg1 get() = Color(bg1); val cBg2 get() = Color(bg2); val cAccent get() = Color(accent); val cText get() = Color(text)
    val brush get() = Brush.verticalGradient(listOf(cBg1, cBg2))

    /** Corner radius as a fraction of the shorter side (0.5 = pill). */
    val radius get() = when (style) { 0 -> .28f; 1 -> .5f; 2 -> 0f; 3 -> .18f; 4 -> .35f; else -> 0f }
    val rowShape: Shape get() = when (style) {
        0 -> RoundedCornerShape(16.dp); 1 -> RoundedCornerShape(50); 2 -> RoundedCornerShape(0.dp)
        3 -> RoundedCornerShape(10.dp); 4 -> RoundedCornerShape(22.dp); else -> CutCornerShape(0.dp)
    }
    val iconShape: Shape? get() = when (style) { 0, 4 -> CircleShape; 2 -> RoundedCornerShape(4.dp); else -> null }
    fun rowBg(on: Boolean): Color = when (style) {
        3 -> Color.Transparent
        6 -> Color(0xFFC0C0C0)
        5 -> if (on) cAccent.copy(alpha = .16f) else Color.White.copy(alpha = .05f)
        4 -> cText.copy(alpha = if (on) .22f else .1f)
        else -> if (on) cAccent.copy(alpha = .22f) else cText.copy(alpha = .08f)
    }
    fun rowBorder(on: Boolean): BorderStroke? = when (style) {
        3 -> BorderStroke(1.dp, cAccent.copy(alpha = if (on) 1f else .35f))
        5 -> BorderStroke(1.dp, if (on) cAccent else cText.copy(alpha = .25f))
        4 -> BorderStroke(1.dp, cText.copy(alpha = .3f))
        else -> null
    }
}

private class Pal(val n: String, val b1: Long, val b2: Long, val ac: Long, val tx: Long)

private val PALS = listOf(
    Pal("Midnight", 0xFF0D1B2A, 0xFF1B263B, 0xFF4CC9F0, 0xFFE0E1DD),
    Pal("Sunset", 0xFF4A1942, 0xFFC2410C, 0xFFFFD166, 0xFFFFF3E8),
    Pal("Forest", 0xFF0B2B1E, 0xFF1F4D36, 0xFF7FD99A, 0xFFE6F4EA),
    Pal("Ocean", 0xFF06304B, 0xFF0B6E8C, 0xFF7FE0F5, 0xFFEAFBFF),
    Pal("Rose", 0xFFF8E1E7, 0xFFF3B6C8, 0xFFC2185B, 0xFF3B1624),
    Pal("Sand", 0xFFF5EBD8, 0xFFE3CFA6, 0xFF9A5B13, 0xFF2E2415),
    Pal("Lavender", 0xFFEDE7F6, 0xFFD1C4E9, 0xFF6750A4, 0xFF1D1B20),
    Pal("Mint", 0xFFE0F5EC, 0xFFB5E8D1, 0xFF00796B, 0xFF0F2A22),
    Pal("Graphite", 0xFF18181B, 0xFF2A2A2E, 0xFFA1A1AA, 0xFFF4F4F5),
    Pal("Amoled", 0xFF000000, 0xFF0A0A0A, 0xFFFF9800, 0xFFFFFFFF),
    Pal("Neon", 0xFF12002B, 0xFF2B0057, 0xFFFF2BD6, 0xFFF0E6FF),
    Pal("Ember", 0xFF1C0A00, 0xFF5A1E00, 0xFFFFB300, 0xFFFFF3E0),
)
private val STYLES = listOf("Soft", "Pill", "Block", "Outline", "Glass", "Winamp")

object NoiseThemes {
    const val APP = "app"
    private const val PREFS = "noise_ui"

    /** Windows 95 plus 12 palettes x 6 styles = 72 generated looks. */
    val all: List<NoiseLook> = listOf(NoiseLook("win95", "Windows 95", 0xFFC0C0C0.toInt(), 0xFFC0C0C0.toInt(), 0xFF000080.toInt(), 0xFF000000.toInt(), 6)) + PALS.flatMapIndexed { pi, p ->
        STYLES.mapIndexed { si, s ->
            if (si == 5) NoiseLook("p${pi}s5", "${p.n} $s", 0xFF1A1A22.toInt(), 0xFF0B0B10.toInt(), p.ac.toInt(), p.ac.toInt(), 5)
            else NoiseLook("p${pi}s$si", "${p.n} $s", p.b1.toInt(), p.b2.toInt(), p.ac.toInt(), p.tx.toInt(), si)
        }
    }

    fun byId(id: String): NoiseLook? = all.firstOrNull { it.id == id }

    private fun sp(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun selectedId(c: Context): String = sp(c).getString("theme", APP) ?: APP
    fun select(c: Context, id: String) { sp(c).edit().putString("theme", id).apply() }
    /** null = follow the app look. */
    fun current(c: Context): NoiseLook? = byId(selectedId(c))

    fun bool(c: Context, k: String, def: Boolean) = sp(c).getBoolean(k, def)
    fun setBool(c: Context, k: String, v: Boolean) { sp(c).edit().putBoolean(k, v).apply() }
    fun int(c: Context, k: String, def: Int) = sp(c).getInt(k, def)
    fun setInt(c: Context, k: String, v: Int) { sp(c).edit().putInt(k, v).apply() }
}

/** Mini sound row for the gallery: background, an icon blob, a name bar and a slim slider, in the look's own shapes. */
@Composable
fun NoiseLookPreview(l: NoiseLook?) {
    val fg = l?.cText ?: com.umutk.pulse.T.text
    val ac = l?.cAccent ?: com.umutk.pulse.T.accent
    Box(Modifier.fillMaxSize().background(l?.brush ?: Brush.verticalGradient(listOf(com.umutk.pulse.T.bg, com.umutk.pulse.T.bg))).padding(6.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) { i ->
                val on = i == 0
                val shape: Shape = l?.rowShape ?: RoundedCornerShape(6.dp)
                val stroke = l?.rowBorder(on)
                Row(
                    Modifier.fillMaxWidth().height(20.dp)
                        .clip(shape)
                        .background(l?.rowBg(on) ?: fg.copy(alpha = .1f))
                        .then(if (stroke != null) Modifier.border(stroke, shape) else Modifier)
                        .then(if (l?.win95 == true) Modifier.bevel(raised = !on) else Modifier)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(9.dp).clip(l?.iconShape ?: CircleShape).background(if (on) ac else fg.copy(alpha = .4f)))
                    Box(Modifier.height(if (l?.style == 1) 6.dp else if (l?.style == 5) 2.dp else 3.dp).weight(1f).clip(RoundedCornerShape(2.dp)).background(fg.copy(alpha = .25f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(if (on) .7f else .3f).background(ac))
                    }
                }
            }
        }
    }
}
