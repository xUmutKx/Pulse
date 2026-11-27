package com.umutk.pulse.extras.keyboard

import androidx.compose.ui.graphics.Color

/**
 * The colours and shape of the keyboard. [bevel] draws the raised 3D key of the old Windows looks (95, XP, 7); the newer looks are flat.
 * [menu] is the toolbar menu's own background (AMOLED: pure black).
 */
class KbTheme(
    val id: String, val bg: Color, val key: Color, val text: Color, val special: Color, val specialText: Color, val accent: Color, val onAccent: Color,
    val menu: Color, val popup: Color, val sub: Color, val radius: Int, val bevel: Boolean = false, val border: Color = Color.Transparent,
    /** The icon family of the theme: filled, sharp, rounded or outlined (see KbIcons). */
    val icons: String = "outlined",
) {
    /** The Samsung look: its own bottom row (!#1, comma, the space bar with the language, period, the action key in the accent colour), the number row, round toolbar buttons. */
    val samsung: Boolean get() = id == "samsung"

    /** The see-through form: keys, function keys and board take an alpha and the keys a pale edge (a frosted pane; the window itself is not blurred). */
    fun glassed(): KbTheme = KbTheme(id, bg.copy(alpha = .62f), key.copy(alpha = .5f), text, special.copy(alpha = .42f), specialText, accent, onAccent, menu.copy(alpha = .62f), popup, sub, radius, bevel, Color.White.copy(alpha = .16f), icons)

    /** The user's own key corners and icon family over the theme's (both off by default: the theme stays as it is). */
    fun tuned(): KbTheme {
        val r = KeyboardPrefs.keyRadius; val ic = KeyboardPrefs.keyIcons
        if (r < 0 && ic == "theme") return this
        return KbTheme(id, bg, key, text, special, specialText, accent, onAccent, menu, popup, sub, if (r < 0) radius else r, bevel, border, if (ic == "theme") icons else ic)
    }
}

object KeyboardThemes {
    /** The picker's order. */
    val ids = listOf("samsung", "amoled", "gboard", "material", "win11", "win10", "win7", "xp", "win95", "retro", "midnight", "forest", "rose", "sunset", "contrast", "cvd", "custom")

    fun name(id: String) = when (id) {
        "amoled" -> "SwiftKey"; "gboard" -> "Google Keyboard"; "material" -> "Material"; "samsung" -> "Samsung"; "win11" -> "Windows 11"; "win10" -> "Windows 10"; "win7" -> "Windows 7"
        "xp" -> "Windows XP"; "win95" -> "Windows 95"; "retro" -> "Old phone"; "midnight" -> "Midnight"; "forest" -> "Forest"; "rose" -> "Rosé"; "sunset" -> "Sunset"; "contrast" -> "High contrast"; "cvd" -> "Colour-blind safe"; else -> "Custom"
    }

    private fun c(v: Long) = Color(v)

    /** The theme for [id]; [dark] is the phone's dark mode (Material, Windows 10 and Windows 11 follow it). */
    fun of(id: String, dark: Boolean): KbTheme = base(id, dark).let { if (id == "gboard" && KeyboardPrefs.gbGlass) it.glassed() else it }

    private fun base(id: String, dark: Boolean): KbTheme = when (id) {
        // an old phone's keypad: silver grey keys with a raised edge on a grey body, dark ink, an olive highlight (its small screen)
        "retro" -> KbTheme(id, c(0xFF8D9193), c(0xFFC9CCCE), c(0xFF1C1F21), c(0xFFA9ADB0), c(0xFF1C1F21), c(0xFFB5BE7A), c(0xFF1C1F21), c(0xFF8D9193), c(0xFFDADDDF), c(0xFF3A3F42), 18, bevel = true, border = c(0xFF5E6264), icons = "outlined")
        // new looks (the default stays as it was)
        "midnight" -> KbTheme(id, c(0xFF0B1226), c(0xFF18223F), c(0xFFE8EEFF), c(0xFF121B33), c(0xFFE8EEFF), c(0xFF7AA2FF), c(0xFF0B1226), c(0xFF0B1226), c(0xFF243257), c(0xFF8FA0CC), 12, icons = "rounded")
        "forest" -> KbTheme(id, c(0xFF0E1A14), c(0xFF1B2D23), c(0xFFE6F2EA), c(0xFF14241B), c(0xFFE6F2EA), c(0xFF6FCF97), c(0xFF0E1A14), c(0xFF0E1A14), c(0xFF27402F), c(0xFF8FB39C), 12, icons = "rounded")
        "rose" -> KbTheme(id, c(0xFFFBEFF1), c(0xFFFFFFFF), c(0xFF3A1F27), c(0xFFF3D9DE), c(0xFF3A1F27), c(0xFFD6456B), c(0xFFFFFFFF), c(0xFFFBEFF1), c(0xFFFFFFFF), c(0xFF8A5F6A), 14, icons = "rounded")
        "sunset" -> KbTheme(id, c(0xFF2A1420), c(0xFF40202E), c(0xFFFFE9DE), c(0xFF341A28), c(0xFFFFE9DE), c(0xFFFF8A4C), c(0xFF2A1420), c(0xFF2A1420), c(0xFF55303F), c(0xFFD1A39A), 14, icons = "rounded")
        // pure black and white with a thick yellow accent: the strongest contrast
        "contrast" -> KbTheme(id, c(0xFF000000), c(0xFF000000), c(0xFFFFFFFF), c(0xFF000000), c(0xFFFFFF00), c(0xFFFFFF00), c(0xFF000000), c(0xFF000000), c(0xFF202020), c(0xFFFFFFFF), 6, border = c(0xFFFFFFFF), icons = "filled")
        // for red-green colour blindness: blue and orange only (never red against green), light and dark parts clearly apart
        "cvd" -> KbTheme(id, c(0xFF101820), c(0xFF22303F), c(0xFFFFFFFF), c(0xFF182430), c(0xFFFFFFFF), c(0xFFFFB000), c(0xFF101820), c(0xFF101820), c(0xFF2E4156), c(0xFFB5C6D8), 10, border = c(0xFF648FFF), icons = "outlined")
        // Google Keyboard: rounded keys, a calm blue-grey board with the lighter letter keys and a mint action key; four tones (the first is the look of the picture) and key borders that can be switched off
        "gboard" -> {
            val flat = !KeyboardPrefs.gbBorders
            if (!dark) {
                val bg = c(0xFFE8EDF0)
                KbTheme(id, bg, if (flat) bg else c(0xFFFFFFFF), c(0xFF1F2933), if (flat) bg else c(0xFFD3DADF), c(0xFF1F2933), c(0xFF1A73E8), c(0xFFFFFFFF), bg, c(0xFFFFFFFF), c(0xFF5B6770), 22, icons = "rounded")
            } else when (KeyboardPrefs.gbTone) {
                "black" -> { val bg = c(0xFF000000); KbTheme(id, bg, if (flat) bg else c(0xFF202124), c(0xFFE8EAED), if (flat) bg else c(0xFF171717), c(0xFFE8EAED), c(0xFF8AB4F8), c(0xFF0B1A33), bg, c(0xFF3C4043), c(0xFF9AA0A6), 22, icons = "rounded") }
                "smoke" -> { val bg = c(0xFF2B2B2E); KbTheme(id, bg, if (flat) bg else c(0xFF48484C), c(0xFFEDEDF0), if (flat) bg else c(0xFF38383B), c(0xFFEDEDF0), c(0xFFA8C7FA), c(0xFF062E6F), bg, c(0xFF5A5A5F), c(0xFFB0B0B6), 22, icons = "rounded") }
                "material" -> { val bg = c(0xFF1C1B1F); KbTheme(id, bg, if (flat) bg else c(0xFF36343B), c(0xFFE6E1E5), if (flat) bg else c(0xFF2B2930), c(0xFFE6E1E5), c(0xFFD0BCFF), c(0xFF381E72), bg, c(0xFF49454F), c(0xFFCAC4D0), 22, icons = "rounded") }
                else -> { val bg = c(0xFF28343A); KbTheme(id, bg, if (flat) bg else c(0xFF424D54), c(0xFFE3E8EB), if (flat) bg else c(0xFF313C42), c(0xFFE3E8EB), c(0xFF6DB1A8), c(0xFF0B2B27), bg, c(0xFF4F5B62), c(0xFFAAB6BD), 22, icons = "rounded") }
            }
        }
        "amoled" -> KbTheme(id, c(0xFF000000), c(0xFF1F1F1F), c(0xFFFFFFFF), c(0xFF141414), c(0xFFFFFFFF), c(0xFFB0B0B0), c(0xFF000000), c(0xFF000000), c(0xFF2B2B2B), c(0xFF9A9A9A), 10, icons = "outlined")
        "material" -> if (dark) KbTheme(id, c(0xFF1C1B1F), c(0xFF36343B), c(0xFFE6E1E5), c(0xFF2B2930), c(0xFFE6E1E5), c(0xFFD0BCFF), c(0xFF381E72), c(0xFF2B2930), c(0xFF49454F), c(0xFFCAC4D0), 12, icons = "rounded")
        else KbTheme(id, c(0xFFFEF7FF), c(0xFFFFFFFF), c(0xFF1D1B20), c(0xFFE7E0EC), c(0xFF1D1B20), c(0xFF6750A4), c(0xFFFFFFFF), c(0xFFF3EDF7), c(0xFFE6E0E9), c(0xFF49454F), 12, icons = "rounded")
        // the Samsung keyboard's own greys: black board, lighter letter keys, darker function keys, the blue of its action key
        "samsung" -> if (dark) KbTheme(id, c(0xFF010101), c(0xFF3A3A3C), c(0xFFFFFFFF), c(0xFF171719), c(0xFFFFFFFF), c(0xFF4A8FFF), c(0xFFFFFFFF), c(0xFF171719), c(0xFF4C4C4E), c(0xFFB9B9BE), 7, icons = "outlined")
        else KbTheme(id, c(0xFFECEDF0), c(0xFFFFFFFF), c(0xFF000000), c(0xFFD2D5DB), c(0xFF000000), c(0xFF0381FE), c(0xFFFFFFFF), c(0xFFF6F6F8), c(0xFFFFFFFF), c(0xFF5F6368), 9, icons = "outlined")
        "win11" -> if (dark) KbTheme(id, c(0xFF202020), c(0xFF3B3B3B), c(0xFFFFFFFF), c(0xFF2B2B2B), c(0xFFFFFFFF), c(0xFF60CDFF), c(0xFF000000), c(0xFF2B2B2B), c(0xFF444444), c(0xFFC5C5C5), 6, border = c(0xFF1A1A1A), icons = "outlined")
        else KbTheme(id, c(0xFFF3F3F3), c(0xFFFFFFFF), c(0xFF1B1B1B), c(0xFFE5E5E5), c(0xFF1B1B1B), c(0xFF005FB8), c(0xFFFFFFFF), c(0xFFF9F9F9), c(0xFFFFFFFF), c(0xFF5D5D5D), 6, border = c(0xFFD6D6D6), icons = "outlined")
        "win10" -> if (dark) KbTheme(id, c(0xFF1F1F1F), c(0xFF3C3C3C), c(0xFFFFFFFF), c(0xFF2B2B2B), c(0xFFFFFFFF), c(0xFF0078D7), c(0xFFFFFFFF), c(0xFF2B2B2B), c(0xFF4A4A4A), c(0xFFBDBDBD), 0, icons = "sharp")
        else KbTheme(id, c(0xFFE6E6E6), c(0xFFF8F8F8), c(0xFF000000), c(0xFFD2D2D2), c(0xFF000000), c(0xFF0078D7), c(0xFFFFFFFF), c(0xFFF2F2F2), c(0xFFFFFFFF), c(0xFF5A5A5A), 0, icons = "sharp")
        "win7" -> KbTheme(id, c(0xFFC9D8EC), c(0xFFF2F6FB), c(0xFF1E1E1E), c(0xFFDCE6F4), c(0xFF1E1E1E), c(0xFF3399FF), c(0xFFFFFFFF), c(0xFFDCE6F4), c(0xFFFFFFFF), c(0xFF45536B), 4, border = c(0xFF7F9DB9), icons = "filled")
        "xp" -> KbTheme(id, c(0xFFECE9D8), c(0xFFFFFFFF), c(0xFF000000), c(0xFFF1EFE2), c(0xFF000000), c(0xFF316AC5), c(0xFFFFFFFF), c(0xFFF1EFE2), c(0xFFFFFFE1), c(0xFF4A4A4A), 3, border = c(0xFF003C74), icons = "filled")
        "win95" -> KbTheme(id, c(0xFFC0C0C0), c(0xFFC0C0C0), c(0xFF000000), c(0xFFC0C0C0), c(0xFF000000), c(0xFF000080), c(0xFFFFFFFF), c(0xFFC0C0C0), c(0xFFFFFFE1), c(0xFF404040), 0, bevel = true, icons = "sharp")
        else -> KbTheme("custom", Color(KeyboardPrefs.cBg), Color(KeyboardPrefs.cKey), Color(KeyboardPrefs.cText), Color(KeyboardPrefs.cSpecial), Color(KeyboardPrefs.cText), Color(KeyboardPrefs.cAccent),
            if (Color(KeyboardPrefs.cAccent).luminanceApprox() > .55f) Color.Black else Color.White, Color(KeyboardPrefs.cMenu), Color(KeyboardPrefs.cKey), Color(KeyboardPrefs.cText).copy(alpha = .65f), KeyboardPrefs.cRadius)
    }

    private fun Color.luminanceApprox() = 0.299f * red + 0.587f * green + 0.114f * blue
}
