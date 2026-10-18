package com.umutk.pulse.extras.music.winamp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.skin.Transport
import com.umutk.pulse.extras.music.skin.WinampMain
import com.umutk.pulse.extras.music.skin.WinampSkin

/**
 * Classic Winamp main window (275x116 skin pixels) drawn from [skin]: display, spectrum, seek bar, volume,
 * transport, shuffle/repeat and the EQ/PL toggles. [levels] are the 19 visualizer bar heights 0..1, [volume] 0..1,
 * [positionMs]/[durationMs] in ms. [scaleDp] (when above 0) overrides the whole-number [scale] with dp per skin pixel.
 */
@Composable
fun WinampClassic(
    skin: WinampSkin,
    track: Track?,
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    levels: List<Float>,
    volume: Float,
    onPrev: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onEject: () -> Unit,
    onSeek: (Int) -> Unit,
    onVolume: (Float) -> Unit,
    onTogglePlaylist: () -> Unit,
    onToggleEq: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Int = 1,
    scaleDp: Float = 0f,
    shuffle: Boolean = false,
    repeat: Boolean = false,
    eqOn: Boolean = false,
    plOn: Boolean = false,
    shade: Boolean = false,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onShade: () -> Unit = {},
) {
    WinampMain(
        skin = skin,
        title = trackLabel(track),
        timeText = formatTime(positionMs),
        progress = if (track == null) -1f else if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
        playing = playing,
        levels = levels,
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
        modifier = modifier,
        scale = if (scaleDp > 0f) scaleDp else scale.toFloat(),
        volume = volume,
        shuffle = shuffle, repeat = repeat, eqOn = eqOn, plOn = plOn, shade = shade,
        onSeek = { onSeek((it * durationMs).toInt()) },
        onVolume = onVolume,
        onShuffle = onShuffle, onRepeat = onRepeat,
        onToggleEq = onToggleEq, onTogglePlaylist = onTogglePlaylist, onShade = onShade,
    )
}

/** Widget form: the 275x14 shade strip with the title, time and three keys. */
@Composable
fun WinampShade(
    skin: WinampSkin,
    track: Track?,
    playing: Boolean,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Int = 1,
) {
    WinampMain(
        skin = skin, title = trackLabel(track), timeText = "", progress = -1f, playing = playing, levels = emptyList(),
        onTransport = {
            when (it) {
                Transport.PREV -> onPrev()
                Transport.NEXT -> onNext()
                else -> onPlayPause()
            }
        },
        modifier = modifier, scale = scale.toFloat(), shade = true,
    )
}

internal fun trackLabel(t: Track?): String =
    t?.let { it.title ?: it.path.substringAfterLast('/').substringBeforeLast('.') } ?: "PULSE"

internal fun formatTime(ms: Int): String {
    val sec = ms.coerceAtLeast(0) / 1000
    return "%02d:%02d".format(sec / 60, sec % 60)
}
