package com.umutk.pulse.extras.music

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** A background colour and an accent. Poweramp skins and the music widget themes are both derived from this list. */
class MusicPalette(val id: String, val name: String, val bg: Long, val accent: Long) {
    val bgColor get() = Color(0xFF000000 or bg)
    val accentColor get() = Color(0xFF000000 or accent)
    val dark: Boolean get() = bgColor.luminance() < .5f
}

enum class WidgetShape { ROUND, SHARP, PILL, BEVEL }

/** One music widget look: colours plus the corner style. [bg]/[text]/[sub]/[accent]/[onAccent] are ARGB ints. */
class MusicWidgetTheme(val id: String, val name: String, val bg: Int, val text: Int, val sub: Int, val accent: Int, val onAccent: Int, val shape: WidgetShape, val dark: Boolean)

val musicPalettes: List<MusicPalette> = listOf(
    MusicPalette("graphite", "Graphite", 0x1C1C1E, 0xB0B0B8), MusicPalette("obsidian", "Obsidian", 0x0A0A0F, 0x7C83FF),
    MusicPalette("indigo", "Indigo night", 0x141A3A, 0x8C9EFF), MusicPalette("teal", "Deep teal", 0x06201F, 0x2DD4BF),
    MusicPalette("emerald", "Emerald gold", 0x062A1B, 0xF2C94C), MusicPalette("moss", "Moss", 0x1B2412, 0xA3E635),
    MusicPalette("olive", "Olive", 0x25250F, 0xD4D45A), MusicPalette("dune", "Dune", 0x2B2418, 0xE8C27A),
    MusicPalette("copper", "Dusk rose", 0x1D1A2B, 0xFF7A9C), MusicPalette("brick", "Brick", 0x2E1210, 0xF2603C),
    MusicPalette("wine", "Rose wine", 0x2A0E1C, 0xFB7185), MusicPalette("plum", "Plum", 0x241034, 0xD946EF),
    MusicPalette("violet", "Charcoal mint", 0x15191C, 0x7EE0B3), MusicPalette("steel", "Navy gold", 0x0A1530, 0xF5C26B),
    MusicPalette("arctic", "Arctic", 0x0D2A33, 0x67E8F9), MusicPalette("lagoon", "Lagoon", 0x062D3C, 0x22D3EE),
    MusicPalette("slate", "Slate", 0x1F2933, 0x94A3B8), MusicPalette("ember", "Ink orange", 0x0D0D12, 0xFF7A00),
    MusicPalette("cocoa", "Mocha blue", 0x241A14, 0x8FB8DE), MusicPalette("honey", "Honey", 0x2A2008, 0xFBBF24),
    MusicPalette("lime", "Lime pop", 0x0F1A05, 0xBEF264), MusicPalette("coral", "Teal coral", 0x0B3A38, 0xFF8A65),
    MusicPalette("berry", "Grape lime", 0x1E0F33, 0xB8F24A), MusicPalette("nightgold", "Night gold", 0x0B0B14, 0xFACC15),
    MusicPalette("cloud", "Cloud", 0xF2F4F7, 0x475467), MusicPalette("cream", "Cream", 0xFBF5E6, 0xB45309),
    MusicPalette("blush", "Blush", 0xFDECEF, 0xE11D48), MusicPalette("lavender", "Lavender", 0xEFEAFB, 0x7C3AED),
    MusicPalette("sage", "Sage", 0xE8F0E4, 0x4D7C0F), MusicPalette("aqua", "Aqua", 0xE0F5F7, 0x0E7490),
    MusicPalette("peach", "Peach", 0xFFE9DD, 0xEA580C), MusicPalette("lemon", "Lemon", 0xFFF9DB, 0xA16207),
    MusicPalette("ice", "Ice", 0xE7F0FA, 0x2563EB), MusicPalette("rosewater", "Stone plum", 0xE9E7E4, 0x5B4B8A),
    MusicPalette("mintcream", "Mint cream", 0xE6F7EE, 0x047857), MusicPalette("lilac", "Mauve", 0xF2E6EE, 0x8E2A6B),
    MusicPalette("skyblue", "Sky blue", 0xDDF1FF, 0x0284C7), MusicPalette("linen", "Linen", 0xF0EBE3, 0x78350F),
    MusicPalette("pearl", "Pearl", 0xF5F5F5, 0x111111), MusicPalette("white", "Paper white", 0xFFFFFF, 0x1D4ED8),
)

private fun widgetTheme(p: MusicPalette, shape: WidgetShape): MusicWidgetTheme {
    val text = if (p.dark) Color(0xFFF2F2F2) else Color(0xFF16161A)
    val sub = text.copy(alpha = .7f)
    val onAcc = if (p.accentColor.luminance() > .18f) Color.Black else Color.White
    val suffix = when (shape) { WidgetShape.ROUND -> ""; WidgetShape.SHARP -> " sharp"; WidgetShape.PILL -> " pill"; WidgetShape.BEVEL -> "" }
    val key = when (shape) { WidgetShape.ROUND -> ""; WidgetShape.SHARP -> "-s"; WidgetShape.PILL -> "-p"; WidgetShape.BEVEL -> "-w95" }
    return MusicWidgetTheme(p.id + key, p.name + suffix, p.bgColor.toArgb(), text.toArgb(), sub.toArgb(), p.accentColor.toArgb(), onAcc.toArgb(), shape, p.dark)
}

/** 40 round themes plus sharp and pill variants of the first 16 colours: 73 in all. */
val musicWidgetThemes: List<MusicWidgetTheme> =
    listOf(MusicWidgetTheme("win95", "Windows 95", 0xFFC0C0C0.toInt(), 0xFF000000.toInt(), 0xFF404040.toInt(), 0xFF000080.toInt(), 0xFFFFFFFF.toInt(), WidgetShape.BEVEL, false)) +
    musicPalettes.map { widgetTheme(it, WidgetShape.ROUND) } +
        musicPalettes.take(16).map { widgetTheme(it, WidgetShape.SHARP) } +
        musicPalettes.drop(16).take(16).map { widgetTheme(it, WidgetShape.PILL) }

/** Null = no theme chosen: the widget follows the system light/dark colours. */
fun musicWidgetTheme(id: String?): MusicWidgetTheme? = musicWidgetThemes.firstOrNull { it.id == id }
