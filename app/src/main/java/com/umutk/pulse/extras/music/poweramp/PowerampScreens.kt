package com.umutk.pulse.extras.music.poweramp

import com.umutk.pulse.extras.music.shownArtist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.foundation.layout.fillMaxHeight
import com.umutk.pulse.Icon
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
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.boundsInWindow
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.window.Dialog
import java.io.File
import kotlin.math.atan2
import com.umutk.pulse.extras.music.CoverCarousel
import com.umutk.pulse.extras.music.rememberTrackArt
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.MusicLevels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.geometry.CornerRadius
import kotlinx.coroutines.delay
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
    // the bar is exactly the 48 dp tab row (no inset), so the tabs' measured x and the indicator share one origin
    val indY = with(dens) { (if (under) 45.dp else 0.dp).roundToPx() }
    val indH = if (under) 3.dp else 48.dp
    Box(base) {
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
 * Now playing: square cover over its own blurred copy filling the page, title and artist chips with previous/next beside them,
 * mode row (equalizer, sleep timer, count, repeat, shuffle), one play button, seek bar and a volume bar at the bottom edge.
 */
@Composable
fun NowPlayingScreen(
    track: Track?,
    index: Int,
    total: Int,
    art: ImageBitmap?,
    artPrev: ImageBitmap?,
    artNext: ImageBitmap?,
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
    onRepeatMode: ((Int) -> Unit)? = null,
    onPlayAll: ((shuffled: Boolean) -> Unit)? = null,
    onOpenEffects: () -> Unit = {},
    rating: Int = 0,
    onRate: ((Int) -> Unit)? = null,
    favourite: Boolean = false,
    onFavourite: (() -> Unit)? = null,
    sleepMinutes: Int = 0,
    onSleepSet: ((minutes: Int, endOfTrack: Boolean) -> Unit)? = null,
    onLyrics: (() -> Unit)? = null,
    onEditTags: (() -> Unit)? = null,
    onFindArt: (() -> Unit)? = null,
    lyricsOpen: Boolean = false,
    onSeekMs: ((Int) -> Unit)? = null,
    onPullDown: (() -> Unit)? = null,
    suggestions: (@Composable () -> Unit)? = null,
) {
    val fg = style.onBackdrop
    fun IV(n: String) = com.umutk.pulse.extras.ui.IconSet.vec(n, style.icons)
    var drag by remember { mutableStateOf<Float?>(null) }
    val shownMs = drag?.let { (it * durationMs).roundToInt() } ?: positionMs
    val rawProgress = if (durationMs > 0) (shownMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // eases the bar between the position polls; a drag follows the finger directly
    val animProgress by animateFloatAsState(rawProgress, label = "seek")
    val ctx = LocalContext.current
    // the lyrics button shows only when the track has lyrics on the device, unless the player setting keeps it always
    val hasLyrics by produceState(false, track?.path) {
        value = false
        value = track?.let { t ->
            withContext(Dispatchers.IO) {
                val artist = t.artist.orEmpty(); val title = t.displayName()
                com.umutk.pulse.extras.lyrics.Lyrics.localRecord(t.path, title, artist) != null ||
                    com.umutk.pulse.extras.lyrics.LyricsStore(ctx).load(t.path, artist, title) != null
            }
        } ?: false
    }
    // the blurred backdrop is drawn once by PowerampApp, edge to edge; this page draws no second copy
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 13.dp, vertical = 13.dp)) {
            // square cover, the biggest element; the lyrics sheet opens over it. An upward swipe opens lyrics; a downward
            // one does nothing. Vertical drags are consumed here so the page neither scrolls nor closes from the cover.
            // up on the cover opens the lyrics, down goes back to the page the user came from (the carousel reads the drag)
            val lyricsNow by rememberUpdatedState(onLyrics)
            val pullNow by rememberUpdatedState(onPullDown)
            // the cover's band across the window is excluded from the system back gesture, so a downward drag that starts
            // near a screen edge stays on the cover instead of going back out of the player
            val view = LocalView.current
            DisposableEffect(view) { onDispose { view.systemGestureExclusionRects = emptyList() } }
            Box(
                // not clipped here: each cover is clipped itself, so one pulled up or down can leave the square
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .onGloballyPositioned { c ->
                        val b = c.boundsInWindow()
                        view.systemGestureExclusionRects = listOf(android.graphics.Rect(0, b.top.roundToInt(), view.width, b.bottom.roundToInt()))
                    },
                contentAlignment = Alignment.Center,
            ) {
                // swipe left or right to change track: the neighbour cover slides in from the side of the drag
                CoverCarousel(
                    art, artPrev, artNext, index > 0, index < total - 1, onPrev, onNext, Modifier.fillMaxSize(),
                    onSwipeUp = { if (!lyricsOpen) lyricsNow?.invoke() },
                    onSwipeDown = { pullNow?.invoke() },
                ) { a ->
                    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(style.artCorner.coerceAtMost(4.dp))).background(style.panel), contentAlignment = Alignment.Center) {
                        if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = style.textDim, modifier = Modifier.size(96.dp))
                    }
                }
                if (track != null) {
                    RatingStar(rating, onRate, favourite, onFavourite, style, Modifier.align(Alignment.BottomStart).padding(start = 4.dp, bottom = 2.dp))
                    TrackMenu(track, onEditTags, onFindArt, onLyrics, style, Modifier.align(Alignment.BottomEnd).padding(end = 2.dp, bottom = 2.dp))
                    if (onLyrics != null && !lyricsOpen && (hasLyrics || PowerampPrefs.lyricsAlways(ctx))) LyricsIcon(onLyrics, Modifier.align(Alignment.BottomEnd).padding(end = 38.dp, bottom = 2.dp))
                }
                if (lyricsOpen && track != null) com.umutk.pulse.extras.lyrics.LyricsSheet(
                    track.displayName(), track.artist.orEmpty(), track.album, durationMs.toLong(), track.path.takeIf { !it.contains("://") }, positionMs.toLong(),
                    onDismiss = { onLyrics?.invoke() }, onSeek = { ms -> onSeekMs?.invoke(ms.toInt()) ?: onSeek(ms.toInt()) },
                    // kept inside the cover square: while it rises it must not slide over the controls under the cover
                    modifier = Modifier.clip(RoundedCornerShape(style.artCorner.coerceAtMost(4.dp))),
                )
            }
            Spacer(Modifier.height(20.dp))
            // the artist / album line's long press runs the action picked in settings (PowerampLineAction)
            val effectsNow by rememberUpdatedState(onOpenEffects)
            var sleepOpen by remember { mutableStateOf(false) }
            // title and artist; favourite and lyrics sit flat beside them
            // title and artist sit in their own rounded chips, the way the reference player shows them
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(start = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (track != null) {
                        // both chips share one shape and inset, so the title and artist read as a matched pair
                        val chipShape = RoundedCornerShape(12.dp)
                        // darker than the panel so the text reads on any backdrop; each chip wraps its own text
                        val chipFill = Color.Black.copy(alpha = .6f)
                        Text(
                            track.displayName(), color = fg, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = (if (onEditTags != null) Modifier.clickable(onClick = onEditTags) else Modifier)
                                .clip(chipShape).background(chipFill).padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        // artist and album only; no folder name here, so a blank tag shows no chip rather than "Audio"
                        val sub = listOfNotNull(track.artistTag(), track.album?.takeIf { it.isNotBlank() }).joinToString(" - ")
                        if (sub.isNotEmpty()) Text(
                            sub,
                            color = style.textDim, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clip(chipShape).background(chipFill).padding(horizontal = 16.dp, vertical = 6.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(onLongPress = {
                                        when (PowerampLineAction.longPress(ctx)) {
                                            LineChoice.NONE -> Unit
                                            LineChoice.LYRICS -> lyricsNow?.invoke()
                                            LineChoice.EQUALIZER -> effectsNow()
                                            LineChoice.SLEEP -> { sleepOpen = true }
                                        }
                                    })
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(19.dp))
            // mode row as pills: equalizer, sleep timer, track count centred, repeat and shuffle at the ends
            Row(Modifier.fillMaxWidth().padding(start = 1.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                LiveBarsBtn(playing, tr("Equalizer", "Ekolayzır"), style, 65.dp, onOpenEffects)
                Spacer(Modifier.width(4.5.dp))
                FlatBtn(Icons.Rounded.Timer, tr("Sleep timer", "Uyku zamanlayıcı"), sleepMinutes != 0, style) { sleepOpen = true }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (sleepMinutes > 0) "$sleepMinutes min" else if (sleepMinutes < 0) tr("Track end", "Parça sonu") else "${index + 1} / $total",
                        color = if (sleepMinutes != 0) style.accent else style.textDim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, softWrap = false, // "1234 / 5678" stays on one line
                        modifier = Modifier.clip(CircleShape).background(style.panel).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                ModeMenuBtn(IV("repeat"), tr("Repeat", "Tekrar"), repeatMode != Player.REPEAT_MODE_OFF, style, onClick = onRepeat, items = listOf(
                    ModeItem(tr("Repeat off", "Tekrar kapalı"), repeatMode == Player.REPEAT_MODE_OFF) { onRepeatMode?.invoke(Player.REPEAT_MODE_OFF) },
                    ModeItem(tr("Repeat all", "Tümünü tekrarla"), repeatMode == Player.REPEAT_MODE_ALL) { onRepeatMode?.invoke(Player.REPEAT_MODE_ALL) },
                    ModeItem(tr("Repeat one", "Tek parçayı tekrarla"), repeatMode == Player.REPEAT_MODE_ONE) { onRepeatMode?.invoke(Player.REPEAT_MODE_ONE) },
                ))
                Spacer(Modifier.width(4.5.dp))
                ModeMenuBtn(IV("shuffle"), tr("Shuffle", "Karıştır"), shuffle, style, onClick = onShuffle, items = listOf(
                    ModeItem(tr("Play all", "Hepsini çal"), false) { onPlayAll?.invoke(false) },
                    ModeItem(tr("Play all shuffled", "Hepsini karışık çal"), false) { onPlayAll?.invoke(true) },
                    ModeItem(tr("Shuffle on", "Karıştır açık"), shuffle, onShuffle),
                ))
            }
            if (sleepOpen && onSleepSet != null) SleepDialog(sleepMinutes, style, onSleepSet) { sleepOpen = false }
            Spacer(Modifier.height(48.dp))
            // transport: play sits on the centre axis, previous and next on either side, over a tall waveform
            BoxWithConstraints(Modifier.fillMaxWidth().height(TRANSPORT_H + SEEK_EXTRA)) {
                // the waveform sits in the same row, behind the buttons: it shows on both sides of play
                val seekAt: () -> Float = { if (drag != null) rawProgress else animProgress }
                val waveMod = Modifier.align(Alignment.Center).requiredWidth(maxWidth).fillMaxHeight()
                SeekBar(
                    progress = seekAt,
                    seed = track?.path?.hashCode() ?: 0,
                    style = style,
                    modifier = waveMod,
                    drawHeight = WAVE_H,
                    onScrub = { drag = it },
                    onCommit = { f -> drag = null; if (durationMs > 0) onSeek((f * durationMs).roundToInt()) },
                    // about half a minute of the song is visible; short tracks and unknown lengths show the whole wave
                    windowFrac = if (durationMs > 60_000) 30_000f / durationMs else 1f,
                )
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    // every transport button sits on a solid black disc, as in the reference player
                    Round(IV("prev"), tr("Previous", "Önceki"), 70.dp, 38.dp, fg, Color.Black, CircleShape, onPrev)
                    Round(
                        IV(if (playing) "pause" else "play"),
                        if (playing) tr("Pause", "Duraklat") else tr("Play", "Oynat"),
                        84.dp, 40.dp, fg, Color.Black, CircleShape, onPlayPause,
                    )
                    Round(IV("next"), tr("Next", "Sonraki"), 70.dp, 38.dp, fg, Color.Black, CircleShape, onNext)
                }
            }
            // elapsed time on the left edge, total on the right, like the reference player
            // elapsed time and total sit at the seek bar's two ends, on the same edges
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMs(shownMs), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(formatMs(durationMs), color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            // a small chip under the transport; what it says is picked in the player settings (the sound quality of the file by default)
            if (track != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val which = remember { PowerampBottomLabel.get(ctx) }
                val quality by produceState("", track.path, which) {
                    value = if (which == BottomLabel.QUALITY) withContext(Dispatchers.IO) { com.umutk.pulse.extras.music.AudioQuality.describe(ctx, track) } else ""
                }
                val text = when (which) {
                    BottomLabel.QUALITY -> quality.ifEmpty { track.shownArtist() }
                    BottomLabel.ARTIST -> track.shownArtist()
                    BottomLabel.ALBUM -> track.album?.takeIf { it.isNotBlank() } ?: track.folderName()
                    BottomLabel.TITLE -> track.displayName()
                    BottomLabel.FOLDER -> track.folderName()
                }
                Box(Modifier.clip(CircleShape).background(style.panel).height(19.dp).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
                    Text(text, color = style.textDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))
            // suggested songs and artists, back at the bottom of the page
            suggestions?.invoke()
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
    // the share of the track the wave shows at once; below 1 the wave scrolls past a fixed play position (drag it to seek)
    windowFrac: Float = 1f,
    // the bars are drawn this tall while the touch area is the whole (taller) modifier, so a drag beside the bars still seeks
    drawHeight: androidx.compose.ui.unit.Dp? = null,
) {
    val progressNow by rememberUpdatedState(progress)
    val heights = remember(seed) {
        val r = Random(seed)
        var h = .5f
        FloatArray(56) { h = (h + (r.nextFloat() - .5f) * .7f).coerceIn(.2f, 1f); h }
    }
    val scrub by rememberUpdatedState(onScrub)
    val commit by rememberUpdatedState(onCommit)
    val windowed = style.seek == PowerampSeek.WAVE && windowFrac < .999f
    Canvas(
        modifier.pointerInput(windowed, windowFrac) {
            awaitEachGesture {
                val d = awaitFirstDown()
                if (windowed) {
                    // the wave moves with the finger: dragging left plays on, dragging right goes back; a tap jumps to the tapped bar
                    val barFrac = windowFrac / WAVE_BARS
                    val slotPx = size.width / WAVE_BARS.toFloat()
                    val p0 = progressNow()
                    var total = 0f
                    var moved = false
                    var f = p0
                    drag(d.id) {
                        total += it.position.x - it.previousPosition.x
                        if (kotlin.math.abs(total) > viewConfiguration.touchSlop) moved = true
                        if (moved) { f = (p0 - total / slotPx * barFrac).coerceIn(0f, 1f); scrub(f) }
                        it.consume()
                    }
                    if (!moved) f = (p0 + (d.position.x - size.width / 2f) / slotPx * barFrac).coerceIn(0f, 1f)
                    commit(f)
                } else {
                    var f = (d.position.x / size.width).coerceIn(0f, 1f)
                    scrub(f)
                    drag(d.id) { f = (it.position.x / size.width).coerceIn(0f, 1f); scrub(f); it.consume() }
                    commit(f)
                }
            }
        },
    ) {
        val off = style.textDim.copy(alpha = .35f)
        val cy = size.height / 2
        val barsH = drawHeight?.toPx() ?: size.height
        // read in the draw phase so the eased value does not recompose the screen
        val p = progress()
        if (windowed) {
            // a short stretch of the track around the play position: the bars scroll past the centre as the song plays, and fade out towards both ends
            val slot = size.width / WAVE_BARS
            val barFrac = windowFrac / WAVE_BARS
            val cx = size.width / 2f
            val centre = p / barFrac
            val first = kotlin.math.floor(centre - WAVE_BARS / 2f).toInt() - 1
            for (k in first..first + WAVE_BARS + 2) {
                if (k < 0 || k * barFrac >= 1f) continue
                val x = cx + (k + .5f - centre) * slot
                if (x < -slot || x > size.width + slot) continue
                val h = waveHeight(seed, k) * barsH
                val edge = (kotlin.math.abs(x - cx) / cx).coerceIn(0f, 1f)
                val c = if (x <= cx) style.accent else off
                drawLine(c.copy(alpha = c.alpha * (1f - edge * edge * edge * .85f)), Offset(x, cy - h / 2), Offset(x, cy + h / 2), slot * .7f, StrokeCap.Round)
            }
            return@Canvas
        }
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
                    val h = (if (style.seek == PowerampSeek.WAVE) heights[i] else .55f) * barsH
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

/** How many bars the scrolling wave shows at once. */
private const val WAVE_BARS = 40

/** Height (0.2..1) of bar [k] of the scrolling wave: a smooth, repeatable pattern per track, so the same stretch always looks the same. */
private fun waveHeight(seed: Int, k: Int): Float {
    val a = kotlin.math.sin(k * .53f + (seed and 0xFF))
    val b = kotlin.math.sin(k * 1.37f + ((seed shr 8) and 0xFF))
    val c = kotlin.math.sin(k * .19f + ((seed shr 16) and 0xFF))
    return (.28f + .72f * kotlin.math.abs(a * .5f + b * .3f + c * .2f)).coerceIn(.2f, 1f)
}

/** Height of the transport row (buttons over the bars) and of the bars inside it. */
private val TRANSPORT_H = 96.dp
private val WAVE_H = 64.dp
/** Extra height around the bars that also takes the seek drag (the bars keep their size). */
private val SEEK_EXTRA = 48.dp

/** Thin bars that fill the button height and move with the live level while playing, and sit at their low stop when paused. Taps go to [onClick]. */
@Composable
private fun LiveBarsBtn(playing: Boolean, label: String, style: PowerampStyle, width: Dp, onClick: () -> Unit) {
    val n = 12
    var levels by remember { mutableStateOf(List(n) { 0f }) }
    LaunchedEffect(playing) {
        while (playing) {
            val s = MusicLevels.snapshot()
            levels = List(n) { g -> s.subList(g * MusicLevels.BANDS / n, (g + 1) * MusicLevels.BANDS / n).average().toFloat() }
            delay(50)
        }
        levels = List(n) { 0f }
    }
    Box(
        Modifier.width(width).height(36.dp).clip(CircleShape).background(style.panel)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val slot = size.width / n
            val bw = slot * 0.5f
            val tint = if (playing) style.accent else style.textDim
            levels.forEachIndexed { i, v ->
                val h = size.height * (0.08f + 0.92f * v.coerceIn(0f, 1f))
                drawRoundRect(tint, Offset(i * slot + (slot - bw) / 2, size.height - h), Size(bw, h), CornerRadius(bw / 2))
            }
        }
    }
}

private class ModeItem(val label: String, val checked: Boolean, val action: () -> Unit)

/** Tap runs [onClick]; hold opens [items] in a menu that animates open. A checked item shows a tick. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModeMenuBtn(icon: ImageVector, label: String, active: Boolean, style: PowerampStyle, onClick: () -> Unit, items: List<ModeItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.width(68.dp).height(36.dp).clip(CircleShape).background(style.panel)
                .semantics { contentDescription = label; role = Role.Button }
                .combinedClickable(onClick = onClick, onLongClick = { open = true }),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (active) style.accent else style.textDim, modifier = Modifier.size(24.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text((if (item.checked) "✓  " else "    ") + item.label, color = if (item.checked) style.accent else style.text) },
                    onClick = { open = false; item.action() },
                )
            }
        }
    }
}

@Composable
private fun FlatBtn(icon: ImageVector, label: String, active: Boolean, style: PowerampStyle, width: Dp = 68.dp, onClick: () -> Unit) {
    // a pill with the panel fill, like the mode buttons in the reference
    Box(
        Modifier.width(width).height(36.dp).clip(CircleShape).background(style.panel)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (active) style.accent else style.textDim, modifier = Modifier.size(24.dp)) }
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
    if (art) {
        Spacer(Modifier.height(14.dp))
        Text(tr("Where the cover shows", "Kapak nerede görünsün"), color = T.sub, fontSize = 12.sp)
        listOf(
            "now" to tr("Now playing", "Şimdi çalıyor"), "library" to tr("Library", "Kütüphane"), "search" to tr("Search", "Arama"),
            "effects" to tr("Effects", "Efektler"), "queue" to tr("Queue", "Kuyruk"),
        ).forEach { (key, label) -> ArtPageRow(c, key, label) }
    }
    Spacer(Modifier.height(14.dp))
    LyricsButtonRow(c)
}

/** One page of the art background: a small preview of the page (blurred cover or black) and a switch for it. */
@Composable
private fun ArtPageRow(c: Context, key: String, label: String) {
    var on by remember(key) { mutableStateOf(PowerampPrefs.artPage(c, key)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        ArtPagePreview(key, on)
        Spacer(Modifier.width(12.dp))
        Text(label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        com.umutk.pulse.PSwitch(on, { on = it; PowerampPrefs.setArtPage(c, key, it) })
    }
}

/** A stand-in for the page: the blurred cover is a colour wash behind its layout, black when the page is left out. */
@Composable
private fun ArtPagePreview(key: String, on: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    val dot = Color.White.copy(alpha = .35f)
    Box(Modifier.width(54.dp).aspectRatio(9f / 16f).clip(shape).background(Color.Black).border(1.dp, T.line, shape)) {
        if (on) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF3B5BDB), Color(0xFFE8590C)))))
        Column(Modifier.fillMaxSize().padding(5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when (key) {
                "now" -> {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp)).background(Color(0xFF1B1B2A)))
                    Box(Modifier.fillMaxWidth(.6f).height(3.dp).clip(CircleShape).background(dot))
                }
                "search" -> {
                    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(dot))
                    repeat(5) { Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(dot)) }
                }
                "effects" -> repeat(3) { Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(dot)) }
                else -> repeat(5) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(dot))
                        Spacer(Modifier.width(3.dp))
                        Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(dot))
                    }
                }
            }
        }
    }
}

/** Lyrics button rule: off = the button shows only for tracks with lyrics; on = always. */
@Composable
private fun LyricsButtonRow(c: Context) {
    var always by remember { mutableStateOf(PowerampPrefs.lyricsAlways(c)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(tr("Lyrics button always shown", "Sözler düğmesi her zaman görünsün"), color = T.text, fontSize = 15.sp)
            Text(tr("Off: only on tracks that have lyrics", "Kapalı: yalnızca sözü olan şarkılarda"), color = T.sub, fontSize = 12.sp)
        }
        com.umutk.pulse.PSwitch(always, { always = it; PowerampPrefs.setLyricsAlways(c, it) })
    }
}

/** Lyrics button: an icon-only disc on the cover's bottom-right, same size and style as the three dots beside it. */
@Composable
private fun LyricsIcon(onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Subject, tr("Lyrics", "Sözler"), tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Three dots on the cover: opens a centred panel with the cover, title and artist, and the track's actions. */
@Composable
private fun TrackMenu(
    track: Track, onEditTags: (() -> Unit)?, onFindArt: (() -> Unit)?, onLyrics: (() -> Unit)?, style: PowerampStyle, modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    val art = rememberTrackArt(track)
    Box(modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)).clickable { open = true }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MoreVert, tr("More", "Daha fazla"), tint = Color.White, modifier = Modifier.size(20.dp))
    }
    if (open) TrackSheet(track, art, onDismiss = { open = false }, onLyrics = onLyrics, onFindArt = onFindArt, onInfo = { info = true })
    if (info) AudioInfoDialog(track, style) { info = false }
}

@Composable
private fun TrackAction(icon: ImageVector, label: String, style: PowerampStyle, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = style.onBackdrop, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = style.onBackdrop, fontSize = 15.sp)
    }
}

/** Poweramp's Audio Info: the tags, length, folder, file name and size of the track. */
@Composable
private fun AudioInfoDialog(track: Track, style: PowerampStyle, onClose: () -> Unit) {
    val size = remember(track.path) { if (track.path.contains("://")) -1L else File(track.path).length() }
    val rows = listOfNotNull(
        tr("Title", "Başlık") to track.displayName(),
        track.artist?.takeIf { it.isNotBlank() }?.let { tr("Artist", "Sanatçı") to it },
        track.album?.takeIf { it.isNotBlank() }?.let { tr("Album", "Albüm") to it },
        track.genre?.takeIf { it.isNotBlank() }?.let { tr("Genre", "Tür") to it },
        track.composer?.takeIf { it.isNotBlank() }?.let { tr("Composer", "Besteci") to it },
        track.year.takeIf { it > 0 }?.let { tr("Year", "Yıl") to it.toString() },
        track.durationSec.takeIf { it >= 0 }?.let { tr("Length", "Süre") to formatMs(it * 1000) },
        tr("Folder", "Klasör") to track.folderName(),
        tr("File", "Dosya") to track.path.substringAfterLast('/'),
        size.takeIf { it >= 0 }?.let { tr("Size", "Boyut") to "%.1f MB".format(it / 1048576.0) },
    )
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.fillMaxWidth(.92f).clip(RoundedCornerShape(22.dp)).background(style.panel).padding(16.dp),
        ) {
            Text(tr("Audio info", "Ses bilgisi"), color = style.onBackdrop, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            rows.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(k, color = style.textDim, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
                    Text(v, color = style.onBackdrop, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.align(Alignment.End).clip(CircleShape).background(style.accent).clickable(onClick = onClose).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text(tr("Close", "Kapat"), color = style.onAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Star at the cover's bottom-left: a tap toggles the favourite. A long press opens a row of five and a slide along it
 * picks the rating, which is saved when the finger lifts.
 */
@Composable
private fun RatingStar(rating: Int, onRate: ((Int) -> Unit)?, favourite: Boolean, onFavourite: (() -> Unit)?, style: PowerampStyle, modifier: Modifier) {
    var picking by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Int?>(null) }
    val cellPx = with(LocalDensity.current) { 30.dp.toPx() }
    val slop = LocalViewConfiguration.current.touchSlop
    val shown = preview ?: rating
    // the pill grows sideways, the stars slide in from the left and the star slides out to the right
    // a rated song keeps its stars on show (filled, as many as it was given); an unrated one is the single favourite star
    val width by animateDpAsState(if (picking) 158.dp else if (rating > 0) (rating * 17 + 14).dp else 36.dp, label = "ratingWidth")
    Box(
        modifier.height(36.dp).width(width)
            .clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = .5f))
            // no wait: the five stars open on touch and a slide along them picks the rating; a tap without a slide toggles the favourite
            .pointerInput(onRate, onFavourite) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    picking = true; preview = starAt(down.position.x, cellPx)
                    var moved = false
                    while (true) {
                        val ch = awaitPointerEvent().changes.first()
                        if (!ch.pressed) break
                        if (!moved && (ch.position - down.position).getDistance() > slop) moved = true
                        if (moved) { ch.consume(); preview = starAt(ch.position.x, cellPx) }
                    }
                    picking = false
                    if (moved) preview?.let { onRate?.invoke(it) } else onFavourite?.invoke()
                    preview = null
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = picking,
            enter = slideInHorizontally { -it / 2 } + fadeIn(),
            exit = slideOutHorizontally { -it / 2 } + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i ->
                    Icon(if (i < shown) Icons.Rounded.Star else Icons.Rounded.StarBorder, null, tint = style.accent, modifier = Modifier.size(30.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = !picking,
            enter = slideInHorizontally { it / 2 } + fadeIn(),
            exit = slideOutHorizontally { it / 2 } + fadeOut(),
        ) {
            if (rating > 0) Row(verticalAlignment = Alignment.CenterVertically) { repeat(rating.coerceAtMost(5)) { Icon(Icons.Rounded.Star, null, tint = style.accent, modifier = Modifier.size(17.dp)) } }
            else Icon(if (favourite) Icons.Rounded.Star else Icons.Rounded.StarBorder, tr("Favourite", "Favori"), tint = if (favourite) style.accent else Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** The star (1 to 5) under a finger at [x] in a row of 30dp cells. */
private fun starAt(x: Float, cellPx: Float): Int = (x / cellPx).toInt().plus(1).coerceIn(1, 5)

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
        Modifier.clip(RoundedCornerShape(50)).background(if (filled) style.accent else style.chip).clickable(onClick = onClick)
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
