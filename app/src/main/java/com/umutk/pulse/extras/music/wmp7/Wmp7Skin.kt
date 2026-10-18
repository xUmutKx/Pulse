package com.umutk.pulse.extras.music.wmp7

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** Colours of one Windows Media Player 7 skin. Flat fills only, as WMP 7 drew them. */
data class Wmp7Skin(
    val frame: Color,
    val menuBg: Color,
    val menuText: Color,
    val treeBg: Color,
    val treeText: Color,
    val treeSel: Color,
    val treeSelText: Color,
    val vizBg: Color,
    val vizBars: List<Color>,   // low to high level
    val text: Color,
    val sub: Color,
    val btn: Color,
    val btnFg: Color,
    val seekTrack: Color,
    val seekFill: Color,
    // added with defaults: look of the era this preset copies
    val name: String = "",
    val style: Wmp7Style = Wmp7Style.FLAT,
    val frame2: Color = frame,      // bottom of the frame gradient
    val accent: Color = seekFill,   // orb glow, group headers
    val bar: Color = Color.Unspecified, // transport strip fill when it differs from the frame
    val barText: Color = text,
    val caption: String = "",       // non-empty draws a Win32 title bar (Media Player 6.x)
) {
    companion object {
        /** Blue frame, black visualisation. */
        val Default = Wmp7Skin(
            frame = Color(0xFF3F648F), menuBg = Color(0xFFDCE6F1), menuText = Color(0xFF14233A),
            treeBg = Color.White, treeText = Color(0xFF14233A), treeSel = Color(0xFF316AC5), treeSelText = Color.White,
            vizBg = Color.Black, vizBars = BlueBars, text = Color.White, sub = Color(0xFFC9D8EA),
            btn = Color(0xFFB8CBE2), btnFg = Color(0xFF14233A), seekTrack = Color(0xFF14233A), seekFill = Color(0xFF8FC1FF),
            name = "Default",
        )

        /** Same layout, grey frame. */
        val Night = Wmp7Skin(
            frame = Color(0xFF2E3238), menuBg = Color(0xFF3C4148), menuText = Color(0xFFE6E8EB),
            treeBg = Color(0xFF1C1F23), treeText = Color(0xFFE6E8EB), treeSel = Color(0xFF4A6FA5), treeSelText = Color.White,
            vizBg = Color.Black, vizBars = BlueBars, text = Color(0xFFF2F4F7), sub = Color(0xFFAEB6C1),
            btn = Color(0xFF50565F), btnFg = Color(0xFFE6E8EB), seekTrack = Color(0xFF1C1F23), seekFill = Color(0xFF8FC1FF),
            name = "Night",
        )

        /** WMP 9: royal blue gradient, glossy round buttons. */
        val Wmp9Blue = Wmp7Skin(
            frame = Color(0xFF5B8AD0), frame2 = Color(0xFF123C86), menuBg = Color(0xFF1F4E9E), menuText = Color.White,
            treeBg = Color(0xFFEAF1FB), treeText = Color(0xFF0B2350), treeSel = Color(0xFF2E6BD6), treeSelText = Color.White,
            vizBg = Color(0xFF020B1E), vizBars = List(16) { lerp(Color(0xFF1B5CFF), Color(0xFF9FF3FF), it / 15f) },
            text = Color.White, sub = Color(0xFFCFE0FA), btn = Color(0xFF8DB4EE), btnFg = Color(0xFF0B2350),
            seekTrack = Color(0xFF0B2350), seekFill = Color(0xFF9FD0FF), name = "WMP 9", style = Wmp7Style.GLOSS, accent = Color(0xFF58B4FF),
        )

        /** WMP 10: near-black, blue glowing orb. */
        val Wmp10Black = Wmp7Skin(
            frame = Color(0xFF2A2D33), frame2 = Color(0xFF07080A), menuBg = Color(0xFF15171B), menuText = Color(0xFFD9DEE6),
            treeBg = Color(0xFF0E1013), treeText = Color(0xFFD9DEE6), treeSel = Color(0xFF1D5FBF), treeSelText = Color.White,
            vizBg = Color.Black, vizBars = List(16) { lerp(Color(0xFF0A4FD6), Color(0xFF6FD6FF), it / 15f) },
            text = Color(0xFFF0F3F8), sub = Color(0xFF8B94A3), btn = Color(0xFF30343B), btnFg = Color(0xFFD9DEE6),
            seekTrack = Color(0xFF000000), seekFill = Color(0xFF3FA2FF), name = "WMP 10", style = Wmp7Style.ORB, accent = Color(0xFF2E8CFF),
        )

        /** WMP 11 (sampled from the mini-player): charcoal panel, silver orbs, blue progress and play orb. */
        val Wmp11Aero = Wmp7Skin(
            frame = Color(0xFF353535), frame2 = Color(0xFF1E1E20), menuBg = Color(0xFF2A2A2A), menuText = Color(0xFFE8E8EC),
            treeBg = Color(0xFF242424), treeText = Color(0xFFE0E0E4), treeSel = Color(0xFF3C6FD0), treeSelText = Color.White,
            vizBg = Color(0xFF111112), vizBars = List(16) { lerp(Color(0xFF2B5FD0), Color(0xFF9CC0FF), it / 15f) },
            text = Color.White, sub = Color(0xFFB4B4BC), btn = Color(0xFFBBBBC1), btnFg = Color(0xFF202024),
            seekTrack = Color(0xFF111112), seekFill = Color(0xFF5689EE), name = "WMP 11", style = Wmp7Style.AERO, accent = Color(0xFF3F7BEA),
        )

        /** WMP 12 (sampled from Windows 7): pale glass, white lists, blue Aero play orb. */
        val Wmp12 = Wmp7Skin(
            frame = Color(0xFFF4F8FC), frame2 = Color(0xFFDCE6F4), menuBg = Color(0xFFE9F2F9), menuText = Color(0xFF1E2F44),
            treeBg = Color.White, treeText = Color(0xFF1E2F44), treeSel = Color(0xFFD1E2F5), treeSelText = Color(0xFF1E2F44),
            vizBg = Color(0xFF16202C), vizBars = List(16) { lerp(Color(0xFF2E7BD6), Color(0xFFA8D4FF), it / 15f) },
            text = Color(0xFF1E2F44), sub = Color(0xFF5B6B80), btn = Color(0xFFE9F2F9), btnFg = Color(0xFF3C659A),
            seekTrack = Color(0xFFC2CAD3), seekFill = Color(0xFF408BD8), name = "WMP 12", style = Wmp7Style.AERO, accent = Color(0xFF408BD8),
            bar = Color(0xFFDCE6F4),
        )

        /** Groove Music (sampled from Windows 10): pale sky sidebar, white page, blue now-playing bar. */
        val Groove = Wmp7Skin(
            frame = Color.White, menuBg = Color(0xFFB1DCED), menuText = Color(0xFF1B2B34),
            treeBg = Color(0xFFB1DCED), treeText = Color(0xFF1B2B34), treeSel = Color(0xFF9CCFE4), treeSelText = Color.Black,
            vizBg = Color(0xFFF2F6F9), vizBars = List(16) { lerp(Color(0xFF7FB3DF), Color(0xFF3F76BB), it / 15f) },
            text = Color(0xFF1B1B1B), sub = Color(0xFF6B6B6B), btn = Color.Transparent, btnFg = Color.White,
            seekTrack = Color(0xFF8FB0DA), seekFill = Color.White, name = "Groove", style = Wmp7Style.FLAT_THIN, accent = Color(0xFF3F76BB),
            bar = Color(0xFF497DBF), barText = Color.White,
        )

        /** Media Player 5/6.x on Windows 2000/98 (sampled): flat grey, white seek trough, navy caption. */
        val Classic64 = Wmp7Skin(
            frame = Color(0xFFD4D0C8), menuBg = Color(0xFFD4D0C8), menuText = Color.Black,
            treeBg = Color.White, treeText = Color.Black, treeSel = Color(0xFF0A246A), treeSelText = Color.White,
            vizBg = Color.Black, vizBars = List(16) { lerp(Color(0xFF00A000), Color(0xFFFFFF00), it / 15f) },
            text = Color.Black, sub = Color(0xFF404040), btn = Color(0xFFD4D0C8), btnFg = Color.Black,
            seekTrack = Color.White, seekFill = Color(0xFF808080), name = "6.4", style = Wmp7Style.CLASSIC, accent = Color(0xFF0A246A),
            caption = "Media Player",
        )

        val All: List<Wmp7Skin> get() = listOf(Default, Night, Wmp9Blue, Wmp10Black, Wmp11Aero, Wmp12, Groove, Classic64)
    }
}

private val BlueBars: List<Color> = List(16) { lerp(Color(0xFF2B7BFF), Color(0xFFBFE3FF), it / 15f) }

/** How buttons, frame and bars are painted. */
enum class Wmp7Style { FLAT, GLOSS, ORB, AERO, FLAT_THIN, CLASSIC }

enum class Wmp7Mode { FULL, COMPACT }

/** m:ss, or h:mm:ss from one hour. Negative input reads as zero. */
fun wmpTime(ms: Int): String {
    val s = ms.coerceAtLeast(0) / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
