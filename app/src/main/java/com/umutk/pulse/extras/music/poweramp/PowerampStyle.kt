package com.umutk.pulse.extras.music.poweramp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.Track

enum class PowerampBackdrop { BLACK, BLURRED_ART, SOLID }

/** Seek bar look: waveform bars, evenly spaced blocks, or a thin line. */
enum class PowerampSeek { WAVE, BARS, LINE }

/** Bottom dock look: floating pill, flat full-width bar, or flat bar with an underline under the open tab. */
enum class PowerampTabs { PILL, FLAT, UNDERLINE }

/** Transport buttons: bare glyph (as in Poweramp), accent circle, accent rounded square, accent soft square. */
enum class PowerampButtons { PLAIN, ROUND, SOFT, SQUARE }

/**
 * Colours and backdrop of the Poweramp-style player. Pass explicit values to theme it;
 * [materialYou] takes the accent and button colours from the system palette.
 */
data class PowerampStyle(
    val text: Color = Color.White,
    val textDim: Color = Color(0xFF9A9A9A),
    val panel: Color = Color(0xFF1A1A1A),
    val accent: Color = Color(0xFF1DE9A8),
    val onAccent: Color = Color.Black,
    val backdrop: PowerampBackdrop = PowerampBackdrop.BLACK,
    val solid: Color = Color.Black,
    /** Fill for the title/folder chips and mode buttons. */
    val chip: Color = Color(0xFF2B2B2B),
    /** When set with a SOLID backdrop, the backdrop is a vertical gradient from [solid] to this. */
    val gradientTo: Color? = null,
    /** Corner of the big cover. A large value makes it a circle. */
    val artCorner: Dp = 16.dp,
    val seek: PowerampSeek = PowerampSeek.WAVE,
    val tabs: PowerampTabs = PowerampTabs.PILL,
    val buttons: PowerampButtons = PowerampButtons.PLAIN,
) {
    /** Text colour that stays readable on the backdrop. Only a light solid colour flips it to black. */
    val onBackdrop: Color
        get() = if (backdrop == PowerampBackdrop.SOLID && solid.luminance() > .5f) Color.Black else text

    companion object {
        @Composable
        fun materialYou(backdrop: PowerampBackdrop = PowerampBackdrop.BLACK, solid: Color = Color.Black): PowerampStyle {
            val c = MaterialTheme.colorScheme
            return PowerampStyle(
                panel = Color.White.copy(alpha = .10f),
                accent = c.primary,
                onAccent = c.onPrimary,
                backdrop = backdrop,
                solid = solid,
            )
        }
    }
}

/** m:ss for a position in milliseconds. */
fun formatMs(ms: Int): String {
    val s = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun Track.displayName(): String = title ?: path.substringAfterLast('/').substringBeforeLast('.')

/** Name of the folder that holds the file, shown as the folder chip. */
fun Track.folderName(): String = path.substringBeforeLast('/', "").substringAfterLast('/')
