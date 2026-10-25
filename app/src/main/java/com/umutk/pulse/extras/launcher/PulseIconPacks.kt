package com.umutk.pulse.extras.launcher

import android.content.Context
import androidx.compose.ui.graphics.toArgb
import com.umutk.pulse.Look
import com.umutk.pulse.extras.palette.AppInfo
import com.umutk.pulse.extras.palette.PaletteStudio
import com.umutk.pulse.extras.palette.core.icon.ColorAssignment
import com.umutk.pulse.extras.palette.core.icon.IconMode
import com.umutk.pulse.extras.palette.core.icon.ThemeSpec
import com.umutk.pulse.extras.palette.core.palette.PaletteGenerator
import com.umutk.pulse.tr

/**
 * Icon packs that live inside Pulse: no package to install. They are drawn by the Palette engine for the apps of the phone, as the launcher lists them.
 *  - pulse:palette: the look saved in Palette (its palette, style and colour rule),
 *  - pulse:glass: that palette in the Material glass style,
 *  - pulse:theme: the real icons in the theme colour of the app, nothing else.
 */
object PulseIconPacks {
    val ids = listOf("pulse:palette", "pulse:glass", "pulse:theme")

    fun isBuiltin(id: String) = id in ids

    fun label(id: String): String = when (id) {
        "pulse:palette" -> tr("Pulse Palette (my look)", "Pulse Palette (benim görünümüm)")
        "pulse:glass" -> tr("Pulse Material glass", "Pulse Material cam")
        else -> tr("Pulse theme colour only", "Pulse yalnızca tema rengi")
    }

    fun spec(ctx: Context, id: String): ThemeSpec {
        val saved = PaletteStudio.spec(ctx)
        return when (id) {
            "pulse:glass" -> saved.copy(mode = IconMode.STYLED, style = "material_glass")
            "pulse:theme" -> ThemeSpec(
                palette = PaletteGenerator.fromSeedColor(Look.accent.copy(alpha = 1f).toArgb(), 5, PaletteGenerator.Harmony.MONOCHROME),
                mode = IconMode.ORIGINAL, assignment = ColorAssignment.SINGLE, singleColorIndex = 2,
            )
            else -> saved
        }
    }

    /** The picture of every app (package, activity, label) in the look of [id], in the order given. */
    fun render(ctx: Context, id: String, apps: List<Triple<String, String, String>>, size: Int): List<androidx.compose.ui.graphics.ImageBitmap?> {
        val engine = PaletteStudio.engine(ctx)
        val infos = apps.map { AppInfo(it.first, it.second, it.third) }
        engine.prepareLuminanceRanks(infos)
        val spec = spec(ctx, id)
        return infos.mapIndexed { i, a -> runCatching { kotlinx.coroutines.runBlocking { engine.render(a, spec, i, size) }.let { androidx.compose.ui.graphics.asImageBitmap(it) } }.getOrNull() }
    }
}
