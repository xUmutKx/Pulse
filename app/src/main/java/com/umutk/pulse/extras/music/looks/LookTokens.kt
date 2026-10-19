package com.umutk.pulse.extras.music.looks

import android.graphics.Typeface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.Look
import com.umutk.pulse.PTheme
import com.umutk.pulse.onAccent

/** How a music window's edge is drawn. */
enum class LookFrame { NONE, BORDER, BEVEL, GLASS }

/**
 * The only look values the music screens (Poweramp, Winamp, WMP7) read.
 * Build it with [musicLook]. Adding a field here means telling the screen owners in YAPILACAKLAR.md.
 */
data class MusicLook(
    val id: String,
    val dark: Boolean,
    val bg: Color,              // screen background
    val panel: Color,           // window body, cards
    val text: Color,
    val sub: Color,             // secondary text
    val accent: Color,          // progress, selection, active tab
    val onAccent: Color,        // text on the accent
    val line: Color,            // separators
    val radius: Dp,             // window and button corners
    val frame: LookFrame,
    val glassAlpha: Float,      // body alpha: 1 = opaque, below 1 = glass / mica
    val glassEdge: Color,       // light edge of a glass body
    val titleH: Dp,             // 0 = no title bar (Material, AMOLED)
    val titleBrush: Brush,      // title bar fill
    val titleText: Color,
    val filled: Boolean,        // true: primary button is an accent-filled pill (Material, AMOLED, other modern looks)
    val buttonFace: Brush,      // push-button face of the Windows looks
    val buttonEdge: Color,
    val buttonText: Color,
    val font: FontFamily,       // every text on a music screen
)

private val sansFont by lazy { FontFamily(Typeface.create("sans-serif", Typeface.NORMAL)) }
private val lightFont by lazy { FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL)) }
private val mediumFont by lazy { FontFamily(Typeface.create("sans-serif-medium", Typeface.NORMAL)) }
private val condensedFont by lazy { FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL)) }

/** The music screens' look: the current look (Look.current) turned into [MusicLook] values. */
fun musicLook(t: PTheme = Look.current): MusicLook {
    val accent = Look.accent
    val on = onAccent(accent)
    val glow = if (t.dark) Color.White.copy(alpha = .22f) else Color.White.copy(alpha = .75f)
    val base = MusicLook(t.id, t.dark, t.bg, t.panel, t.text, t.sub, accent, on, t.line, t.radius,
        LookFrame.NONE, 1f, glow, 0.dp, SolidColor(t.panel), t.text, true,
        SolidColor(t.panel), t.line, t.text, FontFamily.Default)
    return when (t.id) {
        // Windows 95: grey raised bevels, navy title gradient, square corners
        "win95" -> base.copy(bg = Color(0xFFC0C0C0), panel = Color(0xFFC0C0C0), text = Color.Black, sub = Color(0xFF404040),
            accent = Color(0xFF000080), onAccent = Color.White, line = Color(0xFF808080), radius = 0.dp, dark = false,
            frame = LookFrame.BEVEL, titleH = 20.dp, titleBrush = Brush.horizontalGradient(listOf(Color(0xFF000080), Color(0xFF1084D0))),
            titleText = Color.White, filled = false, buttonFace = SolidColor(Color(0xFFC0C0C0)),
            buttonEdge = Color.Black, buttonText = Color.Black, font = sansFont)
        // Windows XP: Luna blue title, 3 px corners, white-to-beige button
        "xp" -> base.copy(frame = LookFrame.BORDER, radius = 3.dp, titleH = 22.dp, titleBrush = t.title ?: SolidColor(t.accent),
            titleText = Color.White, filled = false,
            buttonFace = Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F3EE), Color(0xFFECEBE6), Color(0xFFD6D0C5))),
            buttonEdge = Color(0xFF003C74), buttonText = Color.Black, font = sansFont)
        // Windows 7: Aero glass body, pale title glass, green glass buttons
        "win7" -> base.copy(frame = LookFrame.GLASS, glassAlpha = .72f, radius = 3.dp, titleH = 26.dp,
            titleBrush = t.title ?: SolidColor(t.accent), titleText = Color.Black, filled = false,
            buttonFace = Brush.verticalGradient(0f to Color(0xFFEEF8E2), .5f to Color(0xFFBFE49A), .5f to Color(0xFF9ACD5E), 1f to Color(0xFFCDEBA8)),
            buttonEdge = Color(0xFF4E8A2B), buttonText = Color.Black, font = lightFont)
        // Windows 10: flat, thin 1 px lines, no corners
        "win10" -> base.copy(frame = LookFrame.BORDER, radius = 0.dp, titleH = 26.dp, titleBrush = SolidColor(t.panel),
            titleText = t.text, filled = false, buttonFace = SolidColor(if (t.dark) Color(0xFF333333) else Color(0xFFE1E1E1)),
            buttonEdge = t.line, buttonText = t.text, font = sansFont)
        // Windows 11: rounded, translucent mica body
        "win11" -> base.copy(frame = LookFrame.GLASS, glassAlpha = t.glassA, radius = 8.dp, titleH = 32.dp,
            titleBrush = SolidColor(t.panel), titleText = t.text, filled = true, font = sansFont,
            buttonFace = SolidColor(if (t.dark) Color.White.copy(alpha = .08f) else Color.Black.copy(alpha = .04f)),
            buttonEdge = t.line, buttonText = t.text)
        // AMOLED: black, white accent, rounded, no title bar
        "amoled" -> base.copy(radius = 16.dp, font = condensedFont)
        // Material You (default): wallpaper colours already applied in Look.current
        "material" -> base.copy(radius = Look.cornerDp, font = mediumFont)
        // One UI, ColorOS, Aurora, Sakura, Ocean, Ember, Frost: their own colours and radius, Material-style controls
        else -> base.copy(radius = t.radius)
    }
}
