package com.umutk.pulse.extras.music.poweramp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track

/** A named, ready-made look. [build] gets the accent and the colour on it. */
class PowerampSkin(val id: String, val name: String, val build: (accent: Color, onAccent: Color) -> PowerampStyle)

/** Accent of the Poweramp skin: light grey. */
internal val NeutralAccent = Color(0xFFE6E6E6)

/** The one fixed Poweramp skin. The skin chooser is gone, so every page uses it. */
private const val SKIN_ID = "black"

// the real thing: black, bare glyphs, waveform seek bar, floating pill dock
private val powerampSkins: List<PowerampSkin> = listOf(
    PowerampSkin(SKIN_ID, "Black") { a, oa -> PowerampStyle(accent = a, onAccent = oa, panel = Color(0xFF1A1A1A)) },
)

fun powerampSkin(id: String): PowerampSkin = powerampSkins.firstOrNull { it.id == id } ?: powerampSkins[0]

/** Saved Poweramp settings. */
object PowerampPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("pulse_poweramp", Context.MODE_PRIVATE)
    /** Always the fixed skin: the choice is no longer saved or changed. */
    fun skin(c: Context): String = SKIN_ID
    /** Now-playing page only: true = blurred album cover behind it, false = black. Every other page is black.
     *  The same switch as the player menu's "Art background" (music_hub, artBg), so there is one setting, not two. */
    fun playerArt(c: Context): Boolean = c.getSharedPreferences("music_hub", Context.MODE_PRIVATE).getBoolean("artBg", false)
    fun setPlayerArt(c: Context, on: Boolean) = c.getSharedPreferences("music_hub", Context.MODE_PRIVATE).edit().putBoolean("artBg", on).apply()
    /** Which pages show the blurred cover while the art background is on. Keys: now, library, search, effects, queue. Only now playing is on by default. */
    fun artPage(c: Context, key: String): Boolean = sp(c).getBoolean("artPage_$key", key == "now")
    fun setArtPage(c: Context, key: String, on: Boolean) = sp(c).edit().putBoolean("artPage_$key", on).apply()
    /** The "Blended" cover of the now-playing page: the cover edge to edge at the top, fading into its own blurred copy that fills the screen. Off by default. */
    fun blended(c: Context): Boolean = sp(c).getBoolean("blended", false)
    fun setBlended(c: Context, on: Boolean) = sp(c).edit().putBoolean("blended", on).apply()
    /** How strongly the Blended cover and the lyrics lying on it are blurred (dp). The lyrics follow the same value, so the two never differ. */
    fun blendBlur(c: Context): Int = sp(c).getInt("blendBlur", 36).coerceIn(10, 90)
    fun setBlendBlur(c: Context, v: Int) = sp(c).edit().putInt("blendBlur", v.coerceIn(10, 90)).apply()
    /** A second row of buttons under the mode row (play all, play all shuffled, back and forward 10 s): eight buttons in all. Off by default. */
    fun twoRows(c: Context): Boolean = sp(c).getBoolean("twoRows", false)
    fun setTwoRows(c: Context, on: Boolean) = sp(c).edit().putBoolean("twoRows", on).apply()
    /** Lyrics button on the cover: off = only when the track has lyrics; on = always. */
    fun lyricsAlways(c: Context): Boolean = sp(c).getBoolean("lyrics_always", false)
    fun setLyricsAlways(c: Context, on: Boolean) = sp(c).edit().putBoolean("lyrics_always", on).apply()
}

/** Poweramp look with the fixed skin applied. */
@Composable
fun PowerampPlayer(engine: MusicPlayerEngine, library: List<Track>, art: (Track) -> ImageBitmap?, modifier: Modifier = Modifier, onSettings: (() -> Unit)? = null) {
    PowerampApp(engine, library, powerampSkin(SKIN_ID).build(NeutralAccent, Color.Black), art, modifier, onSettings = onSettings)
}
