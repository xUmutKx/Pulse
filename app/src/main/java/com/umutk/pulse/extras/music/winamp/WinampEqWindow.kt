package com.umutk.pulse.extras.music.winamp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.umutk.pulse.extras.music.skin.PH_DOWN
import com.umutk.pulse.extras.music.skin.PH_MOVE
import com.umutk.pulse.extras.music.skin.PH_UP
import com.umutk.pulse.extras.music.skin.WINAMP_H
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.Zone
import com.umutk.pulse.extras.music.skin.blit
import com.umutk.pulse.extras.music.skin.skinText
import com.umutk.pulse.extras.music.skin.skinZones
import kotlin.math.roundToInt

private const val Z_CLOSE = 1
private const val Z_ON = 2
private const val Z_PRESETS = 3
private const val Z_BAND = 100

/**
 * The Winamp equalizer window (275x116 skin pixels) drawn from eqmain.bmp. The caller owns the state: [levels] are
 * band levels in millibels, [range] is the device band range from Equalizer.levelRange, [bandHz] the centre
 * frequencies (any count; the slider cells are spread over the band area). Dragging a slider calls [onBand].
 * The preamp slider is drawn at 0 dB because the platform equalizer has no preamp. [scaleDp] above 0 overrides [scale].
 */
@Composable
fun WinampEqWindow(
    skin: WinampSkin,
    enabled: Boolean,
    bandHz: List<Int>,
    range: Pair<Int, Int>,
    levels: List<Int>,
    presets: List<String>,
    onEnabled: (Boolean) -> Unit,
    onBand: (band: Int, levelMb: Int) -> Unit,
    onPreset: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Int = 1,
    scaleDp: Float = 0f,
) {
    val dpPer = if (scaleDp > 0f) scaleDp else scale.toFloat()
    val sc = with(LocalDensity.current) { dpPer.dp.toPx() }
    var pressed by remember { mutableIntStateOf(-1) }
    var preset by remember { mutableIntStateOf(0) }
    val n = bandHz.size
    val lo = range.first
    val span = (range.second - lo).coerceAtLeast(1)
    fun cellX(i: Int) = 78f + (i + .5f) * 180f / n - 7f
    val zones = remember(n) {
        val zw = (180f / n.coerceAtLeast(1)).coerceAtMost(26f)
        buildList {
            add(Zone(Z_CLOSE, 250f, 0f, 25f, 14f))
            add(Zone(Z_ON, 14f, 18f, 26f, 12f, padY = 4f))
            add(Zone(Z_PRESETS, 217f, 18f, 44f, 12f, padY = 4f))
            for (i in 0 until n) add(Zone(Z_BAND + i, cellX(i) + 7f - zw / 2, 38f, zw, 63f, drag = true, inset = 5.5f, padY = 6f, vertical = true))
        }
    }
    Canvas(
        modifier.size((WINAMP_W * dpPer).dp, (WINAMP_H * dpPer).dp)
            .skinZones(sc, zones, { pressed = it }) { id, f, ph ->
                when {
                    id >= Z_BAND && (ph == PH_DOWN || ph == PH_MOVE || ph == PH_UP) ->
                        onBand(id - Z_BAND, (range.second - f * span).roundToInt().coerceIn(lo, range.second))
                    ph == PH_UP && id == Z_CLOSE -> onClose()
                    ph == PH_UP && id == Z_ON -> onEnabled(!enabled)
                    ph == PH_UP && id == Z_PRESETS && presets.isNotEmpty() -> {
                        preset = (preset + 1) % presets.size
                        onPreset(preset)
                    }
                }
            },
    ) {
        val u = size.width / WINAMP_W
        val e = skin.eq
        blit(e, 0, 0, WINAMP_W, WINAMP_H, 0f, 0f, u)
        blit(e, 0, 134, WINAMP_W, 14, 0f, 0f, u)
        blit(skin.titlebar, 18, if (pressed == Z_CLOSE) 9 else 0, 9, 9, 264f, 3f, u)
        val onX = if (enabled) (if (pressed == Z_ON) 187 else 69) else (if (pressed == Z_ON) 128 else 10)
        blit(e, onX, 119, 26, 12, 14f, 18f, u)
        blit(e, 36, 119, 32, 12, 39f, 18f, u)
        blit(e, 224, if (pressed == Z_PRESETS) 176 else 164, 44, 12, 217f, 18f, u)
        // preamp at the middle
        blit(e, 13 + (14 % 14) * 15, 164 + 65, 14, 63, 21f, 38f, u)
        blit(e, 0, 164, 11, 11, 22.5f, 38f + 26f, u)
        if (n == 0) return@Canvas
        val pts = ArrayList<Offset>(n)
        for (i in 0 until n) {
            val up = ((levels.getOrElse(i) { 0 } - lo).toFloat() / span).coerceIn(0f, 1f)
            val frame = (up * 27).roundToInt()
            val x = cellX(i)
            blit(e, 13 + (frame % 14) * 15, 164 + (frame / 14) * 65, 14, 63, x, 38f, u)
            blit(e, 0, if (pressed == Z_BAND + i) 176 else 164, 11, 11, x + 1.5f, 38f + (1f - up) * 52f, u)
            val hz = bandHz[i]
            val label = if (hz >= 1000) "${hz / 1000}K" else "$hz"
            skinText(skin, label, x + 7f - label.length * 2.5f, 105f, u)
            pts += Offset((x + 7f) * u, (17f + (1f - up) * 18f + .5f) * u)
        }
        // response curve over the graph box (86,17) 113x19, coloured by the skin's analyzer colours
        val gx0 = 86f * u
        val gx1 = 199f * u
        val line = skin.vis[2]
        for (k in 0 until pts.size - 1) {
            val a = pts[k]; val b = pts[k + 1]
            drawLine(skin.vis[2 + (k * 15 / (pts.size - 1).coerceAtLeast(1))], Offset(a.x.coerceIn(gx0, gx1), a.y), Offset(b.x.coerceIn(gx0, gx1), b.y), strokeWidth = u)
        }
        if (pts.size == 1) drawLine(line, Offset(gx0, pts[0].y), Offset(gx1, pts[0].y), strokeWidth = u)
    }
}
