package com.umutk.pulse.extras.music.poweramp

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material.icons.rounded.Subject
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.umutk.pulse.Look
import com.umutk.pulse.T
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import kotlin.math.atan2
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
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
    val dens = LocalDensity.current
    val under = style.tabs == PowerampTabs.UNDERLINE
    // each tab's x and width in the row, measured once; the indicator slides between them
    val bounds = remember { mutableStateMapOf<PowerampTab, Pair<Float, Float>>() }
    // the indicator stays under the last open tab while it fades out on the now-playing page
    var last by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) { if (selected != null) last = selected }
    // underline: a 22 dp bar centred under the tab; pill: the whole tab
    val rect: Pair<Float, Float>? = last?.let { bounds[it] }?.let { (x, w) ->
        if (under) {
            val uw = with(dens) { 22.dp.toPx() }
            (x + (w - uw) / 2f) to uw
        } else x to w
    }
    val ix by animateFloatAsState(rect?.first ?: 0f, label = "dock-x")
    val iw by animateFloatAsState(rect?.second ?: 0f, label = "dock-w")
    val ia by animateFloatAsState(if (selected != null && style.tabs != PowerampTabs.FLAT) 1f else 0f, label = "dock-a")
    val ic by animateColorAsState(if (under) style.accent else style.accent.copy(alpha = .18f), label = "dock-c")
    val indY = with(dens) { (if (under) 45.dp else 0.dp).roundToPx() }
    val indH = if (under) 3.dp else 48.dp
    Box(base.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Box(
            Modifier.offset { IntOffset(ix.roundToInt(), indY) }.width(with(dens) { iw.toDp() }).height(indH)
                .alpha(ia).clip(CircleShape).background(ic),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            PowerampTab.entries.forEach { t ->
                // a light squeeze on press; the icon colour eases between the selected and idle tints
                val press = remember { MutableInteractionSource() }
                val pressed by press.collectIsPressedAsState()
                val squeeze by animateFloatAsState(if (pressed) .9f else 1f, label = "dock-press")
                val tint by animateColorAsState(if (t == selected) style.accent else style.textDim, label = "dock-tint")
                Box(
                    Modifier.weight(1f).height(48.dp).graphicsLayer { scaleX = squeeze; scaleY = squeeze }.clip(CircleShape)
                        .onGloballyPositioned { c -> bounds[t] = c.positionInParent().x to c.size.width.toFloat() }
                        .semantics { contentDescription = t.label; role = Role.Tab }
                        .clickable(interactionSource = press, indication = LocalIndication.current) { onSelect(t) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(t.icon, null, tint = tint, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

/**
 * Now playing: square cover on a plain background, title and artist chips with previous/next beside them,
 * mode row (equalizer, sleep timer, count, repeat, shuffle), one play button, seek bar and a volume bar at the bottom edge.
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
    favourite: Boolean = false,
    onFavourite: (() -> Unit)? = null,
    sleepMinutes: Int = 0,
    onSleepSet: ((minutes: Int, endOfTrack: Boolean) -> Unit)? = null,
    onLyrics: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onFindArt: (() -> Unit)? = null,
    lyricsOpen: Boolean = false,
    onSeekMs: ((Int) -> Unit)? = null,
) {
    val fg = style.onBackdrop
    fun IV(n: String) = com.umutk.pulse.extras.ui.IconSet.vec(n, style.icons)
    var drag by remember { mutableStateOf<Float?>(null) }
    val shownMs = drag?.let { (it * durationMs).roundToInt() } ?: positionMs
    val rawProgress = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // eases the bar between the position polls; a drag follows the finger directly
    val animProgress by animateFloatAsState(rawProgress, label = "seek")
    val (vol, setVol) = rememberVolume()
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
            // square cover, the biggest element; the lyrics sheet opens over it. An upward swipe opens lyrics; a downward
            // one does nothing. Vertical drags are consumed here so the page neither scrolls nor closes from the cover.
            val lyricsNow by rememberUpdatedState(onLyrics)
            val slop = with(LocalDensity.current) { 72.dp.toPx() }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(style.artCorner.coerceAtMost(24.dp))).background(style.panel)
                    .pointerInput(Unit) {
                        var startY = 0f
                        var sum = 0f
                        detectVerticalDragGestures(
                            onDragStart = { startY = it.y; sum = 0f },
                            onDragEnd = { if (startY > size.height * .55f && sum < -slop) lyricsNow?.invoke() },
                            onDragCancel = { sum = 0f },
                            onVerticalDrag = { c, d -> c.consume(); sum += d },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(art, Modifier.fillMaxSize(), label = "cover") { a ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = style.textDim, modifier = Modifier.size(96.dp))
                    }
                }
                if (track != null) TrackMenu(favourite, onFavourite, onEditTags, onFindArt, style, Modifier.align(Alignment.BottomEnd).padding(8.dp))
                if (lyricsOpen && track != null) com.umutk.pulse.extras.lyrics.LyricsSheet(
                    track.displayName(), track.artist.orEmpty(), track.album, durationMs.toLong(), track.path.takeIf { !it.contains("://") }, positionMs.toLong(),
                    onDismiss = { onLyrics?.invoke() }, onSeek = { ms -> onSeekMs?.invoke(ms.toInt()) ?: onSeek(ms.toInt()) },
                )
            }
            Spacer(Modifier.height(16.dp))
            // title and artist; favourite and lyrics sit flat beside them
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (track != null) {
                        Text(
                            track.displayName(), color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = if (onEditTags != null) Modifier.clickable(onClick = onEditTags) else Modifier,
                        )
                        Text(
                            listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.album?.takeIf { it.isNotBlank() }).joinToString(" - ").ifEmpty { track.folderName() },
                            color = style.textDim, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onLyrics != null && track != null && !lyricsOpen) LyricsPill(onLyrics)
            }
            Spacer(Modifier.height(12.dp))
            // transport: play sits on the centre axis, previous and next on either side; shuffle and repeat at the ends
            Box(Modifier.fillMaxWidth().height(96.dp)) {
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Round(IV("prev"), tr("Previous", "Önceki"), 48.dp, 28.dp, fg, null, CircleShape, onPrev)
                    val plain = style.buttons == PowerampButtons.PLAIN
                    Round(
                        IV(if (playing) "pause" else "play"),
                        if (playing) tr("Pause", "Duraklat") else tr("Play", "Oynat"),
                        68.dp, if (plain) 50.dp else 34.dp,
                        if (plain) fg else style.onAccent, if (plain) null else style.accent,
                        when (style.buttons) {
                            PowerampButtons.SOFT -> RoundedCornerShape(24.dp)
                            PowerampButtons.SQUARE -> RoundedCornerShape(10.dp)
                            else -> CircleShape
                        },
                        onPlayPause,
                    )
                    Round(IV("next"), tr("Next", "Sonraki"), 48.dp, 28.dp, fg, null, CircleShape, onNext)
                }
                Box(Modifier.align(Alignment.CenterStart)) { FlatBtn(IV("shuffle"), tr("Shuffle", "Karıştır"), shuffle, style, onShuffle) }
                Box(Modifier.align(Alignment.CenterEnd)) { FlatBtn(IV("repeat"), tr("Repeat", "Tekrar"), repeatMode != Player.REPEAT_MODE_OFF, style, onRepeat) }
            }
            // waveform and time sit directly under play, on the same centre axis
            SeekBar(
                progress = { if (drag != null) rawProgress else animProgress },
                seed = track?.path?.hashCode() ?: 0,
                style = style,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                onScrub = { drag = it },
                onCommit = { f -> drag = null; if (durationMs > 0) onSeek((f * durationMs).roundToInt()) },
            )
            Text(
                "${formatMs(shownMs)} / ${formatMs(durationMs)}",
                color = style.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            // secondary: sleep timer (opens the knob), the track count and the equalizer
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                var sleepOpen by remember { mutableStateOf(false) }
                FlatBtn(Icons.Rounded.Bedtime, tr("Sleep timer", "Uyku zamanlayıcı"), sleepMinutes != 0, style) { sleepOpen = true }
                Text(
                    if (sleepMinutes > 0) "$sleepMinutes min" else if (sleepMinutes < 0) tr("Track end", "Parça sonu") else "${index + 1} / $total",
                    color = if (sleepMinutes != 0) style.accent else style.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                )
                FlatBtn(Icons.Rounded.Equalizer, tr("Equalizer", "Ekolayzır"), false, style, onOpenEffects)
                if (sleepOpen && onSleepSet != null) SleepDialog(sleepMinutes, style, onSleepSet) { sleepOpen = false }
            }
            Spacer(Modifier.height(8.dp))
            VolumeBar(vol, setVol, style, Modifier.fillMaxWidth().height(28.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Thin volume level bar with a speaker icon; a touch or drag sets the system music volume. */
@Composable
internal fun VolumeBar(value: Float, onChange: (Float) -> Unit, style: PowerampStyle, modifier: Modifier = Modifier) {
    val set by rememberUpdatedState(onChange)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VolumeUp, tr("Volume", "Ses"), tint = style.textDim, modifier = Modifier.size(17.dp))
        Canvas(
            Modifier.weight(1f).fillMaxHeight().padding(start = 10.dp).pointerInput(Unit) {
                awaitEachGesture {
                    val d = awaitFirstDown()
                    set((d.position.x / size.width).coerceIn(0f, 1f))
                    drag(d.id) { set((it.position.x / size.width).coerceIn(0f, 1f)); it.consume() }
                }
            },
        ) {
            val t = 6.dp.toPx(); val cy = size.height / 2
            drawLine(style.textDim.copy(alpha = .35f), Offset(0f, cy), Offset(size.width, cy), t, StrokeCap.Round)
            drawLine(style.accent, Offset(0f, cy), Offset(size.width * value.coerceIn(0f, 1f), cy), t, StrokeCap.Round)
        }
    }
}

/** Waveform, block or line seek bar. [onScrub] gets the fraction while dragging, [onCommit] the final one. */
@Composable
internal fun SeekBar(
    progress: () -> Float,
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
        // read in the draw phase so the eased value does not recompose the screen
        val p = progress()
        when (style.seek) {
            PowerampSeek.LINE -> {
                val t = 4.dp.toPx()
                drawLine(off, Offset(0f, cy), Offset(size.width, cy), t, StrokeCap.Round)
                drawLine(style.accent, Offset(0f, cy), Offset(size.width * p, cy), t, StrokeCap.Round)
                drawCircle(style.accent, 8.dp.toPx(), Offset(size.width * p, cy))
            }
            else -> {
                // waveform: fewer, thicker bars so each one reads at a glance
                val n = if (style.seek == PowerampSeek.WAVE) 40 else 32
                val slot = size.width / n
                for (i in 0 until n) {
                    val h = (if (style.seek == PowerampSeek.WAVE) heights[i] else .55f) * size.height
                    val x = slot * (i + .5f)
                    drawLine(
                        if ((i + .5f) / n <= p) style.accent else off,
                        Offset(x, cy - h / 2), Offset(x, cy + h / 2), slot * (if (style.seek == PowerampSeek.WAVE) .95f else .55f),
                        if (style.seek == PowerampSeek.WAVE) StrokeCap.Round else StrokeCap.Butt,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlatBtn(icon: ImageVector, label: String, active: Boolean, style: PowerampStyle, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (active) style.accent else style.textDim, modifier = Modifier.size(20.dp)) }
}

@Composable
internal fun Round(icon: ImageVector, label: String, box: Dp, glyph: Dp, tint: Color, fill: Color?, shape: Shape, onClick: () -> Unit) {
    // a light press squeeze; the glyph cross-fades when the icon changes (play to pause)
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val squeeze by animateFloatAsState(if (pressed) .92f else 1f, label = "press")
    Box(
        Modifier.size(box).graphicsLayer { scaleX = squeeze; scaleY = squeeze }.clip(shape).let { if (fill != null) it.background(fill) else it }
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(icon, label = "glyph") { g -> Icon(g, null, tint = tint, modifier = Modifier.size(glyph)) }
    }
}

/**
 * Settings choice for the now-playing backdrop: the blurred album cover or pure black. The preview draws both
 * the way the player shows them; every other Poweramp page is black whichever is picked.
 */
@Composable
fun PlayerBgPicker(c: Context) {
    var art by remember { mutableStateOf(PowerampPrefs.playerArt(c)) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(true to tr("Album cover", "Albüm kapağı"), false to tr("Black", "Siyah")).forEach { (v, label) ->
            val on = art == v
            val shape = RoundedCornerShape(12.dp)
            Column(Modifier.weight(1f).clickable { art = v; PowerampPrefs.setPlayerArt(c, v) }, horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(shape).background(Color.Black)
                        .border(if (on) 2.dp else 1.dp, if (on) Look.accent else T.line, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (v) {
                        // stands in for a blurred cover: a soft colour wash behind a square cover
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3B5BDB), Color(0xFFE8590C)))))
                        Box(Modifier.fillMaxWidth(.6f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF1B1B2A)))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(label, color = if (on) Look.accent else T.sub, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Lyrics button: a light pill with dark ink, so it reads on any cover or backdrop. */
@Composable
private fun LyricsPill(onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .85f)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Subject, null, tint = Color.Black, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(tr("Lyrics", "Sözler"), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Three dots at the cover's bottom-right: find album art online, edit tags, favourite. */
@Composable
private fun TrackMenu(
    favourite: Boolean, onFavourite: (() -> Unit)?, onEditTags: (() -> Unit)?, onFindArt: (() -> Unit)?, style: PowerampStyle, modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MoreVert, tr("More", "Daha fazla"), tint = Color.White, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = style.panel) {
            if (onFindArt != null) DropdownMenuItem(
                text = { Text(tr("Find album art online", "Albüm kapağını internetten ara"), color = style.onBackdrop) },
                onClick = { open = false; onFindArt() },
            )
            if (onEditTags != null) DropdownMenuItem(
                text = { Text(tr("Edit tags", "Etiketleri düzenle"), color = style.onBackdrop) },
                onClick = { open = false; onEditTags() },
            )
            if (onFavourite != null) DropdownMenuItem(
                text = { Text(if (favourite) tr("Remove favourite", "Favoriden çıkar") else tr("Favourite", "Favori"), color = if (favourite) style.accent else style.onBackdrop) },
                onClick = { open = false; onFavourite() },
            )
        }
    }
}

/** Sleep timer as a floating panel: a dial for 0 to 120 minutes, and quick end of track / off. */
@Composable
private fun SleepDialog(current: Int, style: PowerampStyle, onSet: (Int, Boolean) -> Unit, onClose: () -> Unit) {
    var value by remember { mutableStateOf(if (current > 0) current else 30) }
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xFF161616)).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr("Sleep timer", "Uyku zamanlayıcı"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            SleepKnob(value, style, Modifier.size(168.dp)) { value = it }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepChip(tr("Off", "Kapalı"), false, style) { onSet(0, false); onClose() }
                SleepChip(tr("End of track", "Parça sonu"), false, style) { onSet(0, true); onClose() }
                SleepChip(tr("Set", "Ayarla"), true, style) { onSet(value, false); onClose() }
            }
        }
    }
}

@Composable
private fun SleepChip(label: String, filled: Boolean, style: PowerampStyle, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) style.accent else Color(0xFF2A2A2A)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, color = if (filled) style.onAccent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}

/** Rotary knob: drag around the dial to pick 0 to 120 minutes in 5-minute steps. The sweep runs from 7:30 round to 4:30. */
@Composable
private fun SleepKnob(minutes: Int, style: PowerampStyle, modifier: Modifier, onChange: (Int) -> Unit) {
    val set by rememberUpdatedState(onChange)
    val start = 135f
    val sweep = 270f
    val maxMin = 120
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val d = awaitFirstDown()
                fun at(p: Offset) {
                    val a = Math.toDegrees(atan2((p.y - size.height / 2f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()
                    // clockwise from the start of the sweep; the gap at the bottom goes to the nearer end
                    val t = ((a - start) % 360f + 360f) % 360f
                    val f = if (t > sweep) (if (t - sweep < 360f - t) 1f else 0f) else t / sweep
                    set(((f * maxMin) / 5).roundToInt() * 5)
                }
                at(d.position)
                drag(d.id) { at(it.position); it.consume() }
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(size.width - stroke, size.height - stroke)
            val at = Offset(inset, inset)
            drawArc(Color.White.copy(alpha = .12f), start, sweep, false, at, arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(style.accent, start, sweep * minutes.coerceIn(0, maxMin) / maxMin, false, at, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(
            if (minutes <= 0) tr("Off", "Kapalı") else "$minutes min",
            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center),
        )
    }
}
