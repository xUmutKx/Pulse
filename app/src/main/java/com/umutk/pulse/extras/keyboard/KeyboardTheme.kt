package com.umutk.pulse.extras.keyboard

import androidx.compose.ui.graphics.Color

/**
 * The colours and shape of the keyboard. [bevel] draws the raised 3D key of the old Windows looks (95, XP, 7); the newer looks are flat.
 * [menu] is the toolbar menu's own background (AMOLED: pure black).
 */
class KbTheme(
    val id: String, val bg: Color, val key: Color, val text: Color, val special: Color, val specialText: Color, val accent: Color, val onAccent: Color,
    val menu: Color, val popup: Color, val sub: Color, val radius: Int, val bevel: Boolean = false, val border: Color = Color.Transparent,
)

object KeyboardThemes {
    /** The picker's order. */
    val ids = listOf("amoled", "material", "win11", "win10", "win7", "xp", "win95", "custom")

    fun name(id: String) = when (id) {
        "amoled" -> "AMOLED"; "material" -> "Material"; "win11" -> "Windows 11"; "win10" -> "Windows 10"; "win7" -> "Windows 7"
        "xp" -> "Windows XP"; "win95" -> "Windows 95"; else -> "Custom"
    }

    private fun c(v: Long) = Color(v)

    /** The theme for [id]; [dark] is the phone's dark mode (Material, Windows 10 and Windows 11 follow it). */
    fun of(id: String, dark: Boolean): KbTheme = when (id) {
        "amoled" -> KbTheme(id, c(0xFF000000), c(0xFF1F1F1F), c(0xFFFFFFFF), c(0xFF141414), c(0xFFFFFFFF), c(0xFF4FC3F7), c(0xFF000000), c(0xFF000000), c(0xFF2B2B2B), c(0xFF9A9A9A), 10)
        "material" -> if (dark) KbTheme(id, c(0xFF1C1B1F), c(0xFF36343B), c(0xFFE6E1E5), c(0xFF2B2930), c(0xFFE6E1E5), c(0xFFD0BCFF), c(0xFF381E72), c(0xFF2B2930), c(0xFF49454F), c(0xFFCAC4D0), 12)
        else KbTheme(id, c(0xFFFEF7FF), c(0xFFFFFFFF), c(0xFF1D1B20), c(0xFFE7E0EC), c(0xFF1D1B20), c(0xFF6750A4), c(0xFFFFFFFF), c(0xFFF3EDF7), c(0xFFE6E0E9), c(0xFF49454F), 12)
        "win11" -> if (dark) KbTheme(id, c(0xFF202020), c(0xFF3B3B3B), c(0xFFFFFFFF), c(0xFF2B2B2B), c(0xFFFFFFFF), c(0xFF60CDFF), c(0xFF000000), c(0xFF2B2B2B), c(0xFF444444), c(0xFFC5C5C5), 6, border = c(0xFF1A1A1A))
        else KbTheme(id, c(0xFFF3F3F3), c(0xFFFFFFFF), c(0xFF1B1B1B), c(0xFFE5E5E5), c(0xFF1B1B1B), c(0xFF005FB8), c(0xFFFFFFFF), c(0xFFF9F9F9), c(0xFFFFFFFF), c(0xFF5D5D5D), 6, border = c(0xFFD6D6D6))
        "win10" -> if (dark) KbTheme(id, c(0xFF1F1F1F), c(0xFF3C3C3C), c(0xFFFFFFFF), c(0xFF2B2B2B), c(0xFFFFFFFF), c(0xFF0078D7), c(0xFFFFFFFF), c(0xFF2B2B2B), c(0xFF4A4A4A), c(0xFFBDBDBD), 0)
        else KbTheme(id, c(0xFFE6E6E6), c(0xFFF8F8F8), c(0xFF000000), c(0xFFD2D2D2), c(0xFF000000), c(0xFF0078D7), c(0xFFFFFFFF), c(0xFFF2F2F2), c(0xFFFFFFFF), c(0xFF5A5A5A), 0)
        "win7" -> KbTheme(id, c(0xFFC9D8EC), c(0xFFF2F6FB), c(0xFF1E1E1E), c(0xFFDCE6F4), c(0xFF1E1E1E), c(0xFF3399FF), c(0xFFFFFFFF), c(0xFFDCE6F4), c(0xFFFFFFFF), c(0xFF45536B), 4, border = c(0xFF7F9DB9))
        "xp" -> KbTheme(id, c(0xFFECE9D8), c(0xFFFFFFFF), c(0xFF000000), c(0xFFF1EFE2), c(0xFF000000), c(0xFF316AC5), c(0xFFFFFFFF), c(0xFFF1EFE2), c(0xFFFFFFE1), c(0xFF4A4A4A), 3, border = c(0xFF003C74))
        "win95" -> KbTheme(id, c(0xFFC0C0C0), c(0xFFC0C0C0), c(0xFF000000), c(0xFFC0C0C0), c(0xFF000000), c(0xFF000080), c(0xFFFFFFFF), c(0xFFC0C0C0), c(0xFFFFFFE1), c(0xFF404040), 0, bevel = true)
        else -> KbTheme("custom", Color(KeyboardPrefs.cBg), Color(KeyboardPrefs.cKey), Color(KeyboardPrefs.cText), Color(KeyboardPrefs.cSpecial), Color(KeyboardPrefs.cText), Color(KeyboardPrefs.cAccent),
            if (Color(KeyboardPrefs.cAccent).luminanceApprox() > .55f) Color.Black else Color.White, Color(KeyboardPrefs.cMenu), Color(KeyboardPrefs.cKey), Color(KeyboardPrefs.cText).copy(alpha = .65f), KeyboardPrefs.cRadius)
    }

    private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue
}
