package com.umutk.pulse.extras.music.skin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.visual.VIS_H
import com.umutk.pulse.extras.music.visual.VIS_W
import com.umutk.pulse.extras.music.visual.VIS_X
import com.umutk.pulse.extras.music.visual.VIS_Y
import com.umutk.pulse.extras.music.visual.VisState
import com.umutk.pulse.extras.music.visual.drawVisBars
import com.umutk.pulse.extras.music.visual.drawVisScope
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Zone ids of the main window. */
private const val Z_SEEK = 10
private const val Z_VOL = 11
private const val Z_SHUF = 12
private const val Z_REP = 13
private const val Z_EQ = 14
private const val Z_PL = 15
private const val Z_SHADE = 16
private const val Z_VIS = 17
private const val Z_LYR = 18

private val MAIN_ZONES = listOf(
    Zone(Z_SHADE, 248f, 0f, 26f, 14f),
    *WinampButton.entries.map { Zone(it.ordinal + 1, it.x.toFloat(), it.y.toFloat(), it.w.toFloat(), SPRITE_H.toFloat(), padY = 7f) }.toTypedArray(),
    Zone(Z_SEEK, 16f, 72f, 248f, 10f, drag = true, inset = 14.5f, padY = 4f),
    Zone(Z_VOL, 107f, 57f, 68f, 13f, drag = true, inset = 7f, padY = 4f),
    Zone(Z_EQ, 219f, 58f, 23f, 12f, padY = 4f),
    Zone(Z_PL, 242f, 58f, 23f, 12f, padY = 4f),
    Zone(Z_SHUF, 164f, 89f, 47f, 15f, padY = 2f),
    Zone(Z_REP, 211f, 89f, 28f, 15f, padY = 2f),
    Zone(Z_VIS, VIS_X.toFloat(), VIS_Y.toFloat(), VIS_W.toFloat(), VIS_H.toFloat()),
    Zone(Z_LYR, 196f, 0f, 38f, 14f), // title bar, free between the title and the shade key
)

private val SHADE_ZONES = listOf(
    Zone(Z_SHADE, 248f, 0f, 26f, 14f),
    Zone(WinampButton.PREV.ordinal + 1, 167f, 0f, 11f, 14f),
    Zone(WinampButton.PLAY.ordinal + 1, 178f, 0f, 18f, 14f),
    Zone(WinampButton.NEXT.ordinal + 1, 196f, 0f, 20f, 14f),
)

/**
 * The whole classic main window, drawn from the sprites of [skin] ([DefaultWinampSkin] has painted ones).
 * [scale] is dp per skin pixel (a Float, so the window can fit the phone width). [shade] is the 14 px strip.
 * Positions follow the Winamp 2.x skin spec: time digits at (48,26), title at (111,27), spectrum at (24,43).
 */
@Composable
fun WinampMain(
    skin: WinampSkin,
    title: String,
    timeText: String,
    progress: Float,
    playing: Boolean,
    levels: List<Float>,
    onTransport: (Transport) -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    volume: Float = 1f,
    shuffle: Boolean = false,
    repeat: Boolean = false,
    eqOn: Boolean = false,
    plOn: Boolean = false,
    shade: Boolean = false,
    stereo: Boolean = true,
    kbps: Int = 0,
    khz: Int = 0,
    onSeek: (Float) -> Unit = {},
    onVolume: (Float) -> Unit = {},
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onToggleEq: () -> Unit = {},
    onTogglePlaylist: () -> Unit = {},
    onShade: () -> Unit = {},
    /** Oscilloscope line instead of bars; the caller keeps the choice. */
    scope: Boolean = false,
    /** Waveform points for the oscilloscope, [VIS_W] values 0..1 (0.5 centre). */
    wave: List<Float> = emptyList(),
    /** Tap on the vis area. */
    onVisToggle: () -> Unit = {},
    /** The LYRICS label in the title bar; [lyricsOn] draws it on a dark patch. */
    lyricsOn: Boolean = false,
    onLyrics: () -> Unit = {},
) {
    val sc = with(LocalDensity.current) { scale.dp.toPx() }
    var pressed by remember { mutableIntStateOf(-1) }
    var seekDrag by remember { mutableFloatStateOf(-1f) }
    var volDrag by remember { mutableFloatStateOf(-1f) }
    // vis smoothing runs in its own frame loop; it writes [visFrame] (draw phase only), so the window never recomposes for it
    val visState = remember { VisState() }
    val levelsNow by rememberUpdatedState(levels)
    val waveNow by rememberUpdatedState(wave)
    var visFrame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(shade) {
        if (shade) return@LaunchedEffect
        while (true) withFrameNanos { t -> if (visState.step(levelsNow, waveNow, t)) visFrame++ }
    }
    val width = title.length * 5
    val shown = if (shade) 53 else 153
    var tick by remember { mutableIntStateOf(0) }
    val scrolls = width > shown
    LaunchedEffect(title, scrolls, playing) {
        tick = 0
        while (scrolls) { delay(90); tick++ }
    }
    val shape = remember(skin.region) { skin.region?.takeIf { !shade }?.let(::polygonShape) ?: RectangleShape }
    val zones = if (shade) SHADE_ZONES else MAIN_ZONES
    val h = if (shade) 14 else WINAMP_H
    val vol = if (volDrag >= 0f) volDrag else volume
    val pos = if (seekDrag >= 0f) seekDrag else progress

    Canvas(
        modifier.size((WINAMP_W * scale).dp, (h * scale).dp).clip(shape)
            .skinZones(sc, zones, { pressed = it }) { id, f, ph ->
                when {
                    id == Z_SEEK -> when (ph) {
                        PH_DOWN, PH_MOVE -> seekDrag = f
                        PH_UP -> { onSeek(f); seekDrag = -1f }
                        else -> seekDrag = -1f
                    }
                    id == Z_VOL -> when (ph) {
                        PH_DOWN, PH_MOVE -> { volDrag = f; onVolume(f) }
                        else -> volDrag = -1f
                    }
                    ph == PH_UP -> when (id) {
                        Z_SHADE -> onShade()
                        Z_SHUF -> onShuffle()
                        Z_REP -> onRepeat()
                        Z_EQ -> onToggleEq()
                        Z_PL -> onTogglePlaylist()
                        Z_VIS -> onVisToggle()
                        Z_LYR -> onLyrics()
                        else -> onTransport(WinampButton.entries[id - 1].t.let { if (shade && playing && it == Transport.PLAY) Transport.PAUSE else it })
                    }
                }
            },
    ) {
        val u = size.width / WINAMP_W
        if (shade) {
            blit(skin.titlebar, 27, 29, 275, 14, 0f, 0f, u)
            clipRect(66 * u, 2 * u, (66 + shown) * u, 10 * u) { skinTextScrolled(skin, title, 66f, 4f, u, tick, shown, scrolls) }
            skinText(skin, timeText, 126f, 4f, u)
            val sh = pressed == Z_SHADE
            blit(skin.titlebar, if (sh) 9 else 0, 18, 9, 9, 254f, 3f, u)
            return@Canvas
        }
        blitFit(skin.main, u)
        blit(skin.titlebar, 27, 0, 275, 14, 0f, 0f, u)
        blit(skin.titlebar, if (pressed == Z_SHADE) 9 else 0, 18, 9, 9, 254f, 3f, u)
        if (lyricsOn) drawRect(skin.playlist.normalBg, Offset(196f * u, 0f), Size(38f * u, 14f * u))
        skinText(skin, "LYRICS", 200f, 4f, u)
        // time mm:ss
        val d = timeText.filter { it.isDigit() }.padStart(4, '0').takeLast(4).map { it - '0' }
        listOf(48f, 60f, 78f, 90f).forEachIndexed { i, x -> skinDigit(skin, d[i], x, 26f, u) }
        clipRect(111 * u, 27 * u, (111 + shown) * u, 33 * u) { skinTextScrolled(skin, title, 111f, 27f, u, tick, shown, scrolls) }
        if (kbps > 0) skinText(skin, kbps.toString().takeLast(3), 111f, 43f, u)
        if (khz > 0) skinText(skin, khz.toString().takeLast(2), 156f, 43f, u)
        blit(skin.monoster, 29, if (stereo) 12 else 0, 27, 12, 212f, 41f, u)
        blit(skin.monoster, 0, if (stereo) 0 else 12, 29, 12, 239f, 41f, u)
        blit(skin.playpaus, if (playing) 0 else 18, 0, 9, 9, 26f, 28f, u)
        // reading visFrame here redraws this Canvas when the vis state moves on
        if (visFrame >= 0L) {
            if (scope) drawVisScope(visState, skin.vis, u) else drawVisBars(visState, skin.vis, u)
        }
        if (pos >= 0f) { // a negative progress means no track: the bar is hidden
            blit(skin.posbar, 0, 0, 248, 10, 16f, 72f, u)
            blit(skin.posbar, if (pressed == Z_SEEK) 278 else 248, 0, 29, 10, 16f + pos.coerceIn(0f, 1f) * (248 - 29), 72f, u)
        }
        blit(skin.volume, 0, (vol * 27).roundToInt() * 15, 68, 13, 107f, 57f, u)
        blit(skin.volume, if (pressed == Z_VOL) 0 else 15, 422, 14, 11, 107f + vol * (68 - 14), 58f, u)
        blit(skin.balance, 9, 0, 38, 13, 177f, 57f, u)
        blit(skin.balance, 15, 422, 14, 11, 177f + 12f, 58f, u)
        WinampButton.entries.forEach { b ->
            blit(skin.buttons, b.sx, if (pressed == b.ordinal + 1) SPRITE_H else 0, b.w, SPRITE_H, b.x.toFloat(), b.y.toFloat(), u)
        }
        // shuffle/repeat: x 28/0, rows y 0 (off), 15 (off pressed), 30 (on), 45 (on pressed)
        blit(skin.shufrep, 28, (if (shuffle) 30 else 0) + if (pressed == Z_SHUF) 15 else 0, 47, 15, 164f, 89f, u)
        blit(skin.shufrep, 0, (if (repeat) 30 else 0) + if (pressed == Z_REP) 15 else 0, 28, 15, 211f, 89f, u)
        // EQ/PL: x 0/23 (pressed +46), rows y 61 (off), 73 (on)
        blit(skin.shufrep, if (pressed == Z_EQ) 46 else 0, if (eqOn) 73 else 61, 23, 12, 219f, 58f, u)
        blit(skin.shufrep, if (pressed == Z_PL) 69 else 23, if (plOn) 73 else 61, 23, 12, 242f, 58f, u)
    }
}

/** Title text that wraps around as [tick] grows when it is wider than the window [shown]. */
private fun DrawScope.skinTextScrolled(
    skin: WinampSkin, title: String, x: Float, y: Float, u: Float, tick: Int, shown: Int, scrolls: Boolean,
) {
    if (!scrolls) { skinText(skin, title, x, y, u); return }
    val loop = "$title   ***   "
    val off = (tick % loop.length) * 5f
    skinText(skin, loop + loop, x - off, y, u) // title is wider than the window, so two copies always cover it
}

/**
 * Classic Winamp main window. Positions are skin pixels, drawn at [scale]. [levels]: 19 bar heights 0..1.
 * Kept for older callers; [WinampMain] is the full version with seek, volume and toggles.
 */
@Composable
fun WinampPlayer(
    skin: WinampSkin,
    title: String,
    timeText: String,
    playing: Boolean,
    levels: List<Float>,
    onPrev: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onEject: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Int = 2,
) {
    WinampMain(
        skin, title, timeText, 0f, playing, levels,
        onTransport = {
            when (it) {
                Transport.PREV -> onPrev()
                Transport.PLAY -> onPlay()
                Transport.PAUSE -> onPause()
                Transport.STOP -> onStop()
                Transport.NEXT -> onNext()
                Transport.EJECT -> onEject()
            }
        },
        modifier = modifier, scale = scale.toFloat(),
    )
}

/** Playlist window in the colours of pledit.txt. Tapping a row calls [onPick] with its index. */
@Composable
fun WinampPlaylist(
    skin: WinampSkin,
    items: List<String>,
    current: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = skin.playlist
    LazyColumn(modifier.background(c.normalBg)) {
        itemsIndexed(items) { i, name ->
            val on = i == current
            Text(
                "${i + 1}. $name",
                color = if (on) c.current else c.normal,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
                    .background(if (on) c.selectedBg else c.normalBg)
                    .clickable { onPick(i) }
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

/** region.txt polygon, scaled from skin pixels to the drawn size. */
private fun polygonShape(pts: List<Offset>): Shape = GenericShape { size, _ ->
    val sx = size.width / WINAMP_W
    val sy = size.height / WINAMP_H
    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x * sx, p.y * sy) else lineTo(p.x * sx, p.y * sy) }
    close()
}
