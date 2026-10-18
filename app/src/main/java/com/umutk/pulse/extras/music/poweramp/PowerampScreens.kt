package com.umutk.pulse.extras.music.poweramp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlin.math.roundToInt
import kotlin.random.Random

enum class PowerampTab(val icon: ImageVector) {
    LIBRARY(Icons.Rounded.GridView),
    EFFECTS(Icons.Rounded.Equalizer),
    SEARCH(Icons.Rounded.Search),
    QUEUE(Icons.Rounded.QueueMusic);

    val label: String get() = when (this) {
        LIBRARY -> tr("Library", "Kütüphane")
        EFFECTS -> tr("Effects", "Efektler")
        SEARCH -> tr("Search", "Ara")
        QUEUE -> tr("Queue", "Kuyruk")
    }
}

/**
 * The four sections. [selected] is null on the now-playing screen. [plate] false leaves the background to the
 * caller (the mini player and the tabs share one plate).
 */
@Composable
fun PowerampTabBar(
    selected: PowerampTab?,
    style: PowerampStyle,
    onSelect: (PowerampTab) -> Unit,
    modifier: Modifier = Modifier,
    plate: Boolean = true,
) {
    val base = modifier.fillMaxWidth().let { if (plate) it.clip(if (style.tabs == PowerampTabs.PILL) CircleShape else RoundedCornerShape(0.dp)).background(style.panel) else it }
    Row(base.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        PowerampTab.entries.forEach { t ->
            Box(
                Modifier.weight(1f).height(48.dp).clip(CircleShape)
                    .semantics { contentDescription = t.label; role = Role.Tab }
                    .clickable { onSelect(t) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(t.icon, null, tint = if (t == selected) style.accent else style.textDim, modifier = Modifier.size(26.dp))
                if (t == selected && style.tabs == PowerampTabs.UNDERLINE) {
                    Box(Modifier.align(Alignment.BottomCenter).width(22.dp).height(3.dp).clip(CircleShape).background(style.accent))
                }
            }
        }
    }
}

/**
 * Now playing: cover, title and artist chips, mode row (equalizer, count, repeat, shuffle), transport,
 * waveform seek bar, volume and folder chip.
 */
@Composable
fun NowPlayingScreen(
    track: Track?,
    index: Int,
    total: Int,
    art: ImageBitmap?,
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    style: PowerampStyle,
    onSeek: (Int) -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    shuffle: Boolean = false,
    repeatMode: Int = Player.REPEAT_MODE_OFF,
    onShuffle: () -> Unit = {},
    onRepeat: () -> Unit = {},
    onOpenEffects: () -> Unit = {},
) {
    val fg = style.onBackdrop
    var drag by remember { mutableStateOf<Float?>(null) }
    val shownMs = drag?.let { (it * durationMs).roundToInt() } ?: positionMs
    val (vol, setVol) = rememberVolume()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val artSize = minOf(maxWidth - 32.dp, maxHeight - 400.dp).coerceAtLeast(120.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(artSize).clip(RoundedCornerShape(style.artCorner)).background(style.panel),
                    contentAlignment = Alignment.Center,
                ) {
                    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(Icons.Rounded.MusicNote, null, tint = style.textDim, modifier = Modifier.size(artSize / 3))
                }
            }
            Spacer(Modifier.height(14.dp))
            if (track != null) {
                Pill(style) { Text(track.displayName(), color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Spacer(Modifier.height(6.dp))
                Pill(style) { Text(track.folderName(), color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ModeBtn(Icons.Rounded.Equalizer, tr("Equalizer", "Ekolayzır"), false, style, onOpenEffects)
                Pill(style) { Text("${index + 1} / $total", color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                ModeBtn(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    tr("Repeat", "Tekrar"), repeatMode != Player.REPEAT_MODE_OFF, style, onRepeat,
                )
                ModeBtn(Icons.Rounded.Shuffle, tr("Shuffle", "Karıştır"), shuffle, style, onShuffle)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Round(Icons.Rounded.SkipPrevious, tr("Previous", "Önceki"), 48.dp, 28.dp, style.textDim, null, CircleShape, onPrev)
                Round(Icons.Rounded.FastRewind, tr("Rewind", "Geri sar"), 52.dp, 34.dp, fg, null, CircleShape) { onSeek((positionMs - 10_000).coerceAtLeast(0)) }
                val plain = style.buttons == PowerampButtons.PLAIN
                Round(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) tr("Pause", "Duraklat") else tr("Play", "Oynat"),
                    80.dp, if (plain) 64.dp else 44.dp,
                    if (plain) fg else style.onAccent, if (plain) null else style.accent,
                    when (style.buttons) {
                        PowerampButtons.SOFT -> RoundedCornerShape(26.dp)
                        PowerampButtons.SQUARE -> RoundedCornerShape(10.dp)
                        else -> CircleShape
                    },
                    onPlayPause,
                )
                Round(Icons.Rounded.FastForward, tr("Forward", "İleri sar"), 52.dp, 34.dp, fg, null, CircleShape) {
                    onSeek((positionMs + 10_000).coerceAtMost(durationMs.coerceAtLeast(0)))
                }
                Round(Icons.Rounded.SkipNext, tr("Next", "Sonraki"), 48.dp, 28.dp, style.textDim, null, CircleShape, onNext)
            }
            Spacer(Modifier.height(14.dp))
            SeekBar(
                progress = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
                seed = track?.path?.hashCode() ?: 0,
                style = style,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                onScrub = { drag = it },
                onCommit = { f -> drag = null; if (durationMs > 0) onSeek((f * durationMs).roundToInt()) },
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(shownMs), color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(formatMs(durationMs), color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VolumeUp, tr("Volume", "Ses"), tint = style.textDim, modifier = Modifier.size(22.dp))
                Slider(
                    value = vol, onValueChange = setVol, modifier = Modifier.weight(1f).padding(start = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = style.accent, activeTrackColor = style.accent, inactiveTrackColor = style.panel),
                )
            }
            if (track != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Pill(style) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Folder, null, tint = style.textDim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(track.folderName().uppercase(), color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Waveform, block or line seek bar. [onScrub] gets the fraction while dragging, [onCommit] the final one. */
@Composable
internal fun SeekBar(
    progress: Float,
    seed: Int,
    style: PowerampStyle,
    modifier: Modifier,
    onScrub: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val heights = remember(seed) {
        val r = Random(seed)
        var h = .5f
        FloatArray(56) { h = (h + (r.nextFloat() - .5f) * .7f).coerceIn(.2f, 1f); h }
    }
    val scrub by rememberUpdatedState(onScrub)
    val commit by rememberUpdatedState(onCommit)
    Canvas(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val d = awaitFirstDown()
                var f = (d.position.x / size.width).coerceIn(0f, 1f)
                scrub(f)
                drag(d.id) { f = (it.position.x / size.width).coerceIn(0f, 1f); scrub(f); it.consume() }
                commit(f)
            }
        },
    ) {
        val off = style.textDim.copy(alpha = .35f)
        val cy = size.height / 2
        when (style.seek) {
            PowerampSeek.LINE -> {
                val t = 4.dp.toPx()
                drawLine(off, Offset(0f, cy), Offset(size.width, cy), t, StrokeCap.Round)
                drawLine(style.accent, Offset(0f, cy), Offset(size.width * progress, cy), t, StrokeCap.Round)
                drawCircle(style.accent, 8.dp.toPx(), Offset(size.width * progress, cy))
            }
            else -> {
                val n = if (style.seek == PowerampSeek.WAVE) 56 else 32
                val slot = size.width / n
                for (i in 0 until n) {
                    val h = (if (style.seek == PowerampSeek.WAVE) heights[i] else .55f) * size.height
                    val x = slot * (i + .5f)
                    drawLine(
                        if ((i + .5f) / n <= progress) style.accent else off,
                        Offset(x, cy - h / 2), Offset(x, cy + h / 2), slot * .55f,
                        if (style.seek == PowerampSeek.WAVE) StrokeCap.Round else StrokeCap.Butt,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeBtn(icon: ImageVector, label: String, active: Boolean, style: PowerampStyle, onClick: () -> Unit) {
    Box(
        Modifier.width(60.dp).height(42.dp).clip(CircleShape).background(style.chip)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (active) style.accent else style.textDim, modifier = Modifier.size(24.dp)) }
}

@Composable
internal fun Pill(style: PowerampStyle, content: @Composable () -> Unit) {
    Box(Modifier.clip(CircleShape).background(style.chip).padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
}

@Composable
internal fun Round(icon: ImageVector, label: String, box: Dp, glyph: Dp, tint: Color, fill: Color?, shape: Shape, onClick: () -> Unit) {
    Box(
        Modifier.size(box).clip(shape).let { if (fill != null) it.background(fill) else it }
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(glyph))
    }
}
