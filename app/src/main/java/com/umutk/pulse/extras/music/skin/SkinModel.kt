package com.umutk.pulse.extras.music.skin

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/** A parsed Winamp .wsz skin. A null bitmap or region means "draw the built-in default". */
data class WinampSkin(
    val main: ImageBitmap?,      // main.bmp, 275x116
    val buttons: ImageBitmap?,   // cbuttons.bmp: normal row at y=0, pressed row at y=18
    val playlist: PlEditColors,  // pledit.txt
    val vis: List<Color>,        // viscolor.txt, always 24 entries
    val region: List<Offset>?,   // region.txt "Normal" polygon in skin pixels, null = rectangle
    val name: String = "",
    val titlebar: ImageBitmap? = null,  // titlebar.bmp, window bar at x=27
    val posbar: ImageBitmap? = null,
    val volume: ImageBitmap? = null,
    val balance: ImageBitmap? = null,
    val numbers: ImageBitmap? = null,   // numbers.bmp or nums_ex.bmp, 9x13 digits
    val text: ImageBitmap? = null,      // text.bmp, 5x6 chars
    val shufrep: ImageBitmap? = null,
    val monoster: ImageBitmap? = null,
    val playpaus: ImageBitmap? = null,
    val eq: ImageBitmap? = null,        // eqmain.bmp
    val pledit: ImageBitmap? = null,    // pledit.bmp
    val fromFile: Boolean = false,      // true when it came from a real .wsz
)

/** Playlist colours from pledit.txt [Text] section. */
data class PlEditColors(val normal: Color, val current: Color, val normalBg: Color, val selectedBg: Color) {
    companion object {
        val Default = PlEditColors(Color(0xFF00FF00), Color.White, Color.Black, Color(0xFF0000FF))
    }
}

/** Default viscolor.txt of the classic base skin: 0 background, 1 dots, 2..17 bars top to bottom, 18..22 scope, 23 peak. */
val DefaultVis: List<Color> = listOf(
    0x000000, 0x18212A, 0xEF3110, 0xCE2910, 0xD65A00, 0xD66600, 0xD67300, 0xC67B08, 0xDEA518, 0xD6B521,
    0xBDDE29, 0x94DE21, 0x29CE10, 0x32BE10, 0x39B510, 0x319C08, 0x299400, 0x188408,
    0xFFFFFF, 0xD6D6DE, 0xB5BDBD, 0xA0AAAF, 0x949CA5, 0x969696,
).map { Color(0xFF000000L or it.toLong()) }

/** The skin used when no .wsz is loaded: every sprite is painted by code (see DefaultSkin.kt). */
val DefaultWinampSkin: WinampSkin get() = paintedDefaultSkin

const val WINAMP_W = 275
const val WINAMP_H = 116
const val SPRITE_H = 18

enum class Transport { PREV, PLAY, PAUSE, STOP, NEXT, EJECT }

/** Button cell in cbuttons.bmp (sprite x, width; pressed row is y=SPRITE_H) and its place on main.bmp (x, y). */
enum class WinampButton(val t: Transport, val sx: Int, val w: Int, val x: Int, val y: Int) {
    PREV(Transport.PREV, 0, 23, 16, 88),
    PLAY(Transport.PLAY, 23, 23, 39, 88),
    PAUSE(Transport.PAUSE, 46, 23, 62, 88),
    STOP(Transport.STOP, 69, 23, 85, 88),
    NEXT(Transport.NEXT, 92, 22, 108, 88),
    EJECT(Transport.EJECT, 114, 22, 136, 88),
}
