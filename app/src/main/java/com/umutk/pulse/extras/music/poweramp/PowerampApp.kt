package com.umutk.pulse.extras.music.poweramp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/**
 * The whole Poweramp-style player. The caller owns [engine] (hold it in a foreground service) and the
 * [library]. [art] returns the cover for a track, or null. Pass a [style] to theme it.
 */
@Composable
fun PowerampApp(
    engine: MusicPlayerEngine,
    library: List<Track>,
    style: PowerampStyle,
    art: (Track) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    skinId: String? = null,
    onSkin: ((String) -> Unit)? = null,
) {
    val st by engine.state.collectAsState()
    val track = st.current
    // Null means the now-playing screen. A tab opens its list, and the mini bar or a second tap goes back.
    var tab by remember { mutableStateOf<PowerampTab?>(null) }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var shuffle by remember { mutableStateOf(engine.player.shuffleModeEnabled) }
    var repeat by remember { mutableIntStateOf(engine.player.repeatMode) }
    val eqUi = remember { EqState() }
    BackHandler(enabled = tab != null) { tab = null }
    DisposableEffect(engine) {
        val l = object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
            override fun onRepeatModeChanged(repeatMode: Int) { repeat = repeatMode }
        }
        engine.player.addListener(l)
        onDispose { engine.player.removeListener(l) }
    }

    // The engine has no position flow, so poll it. Slower while paused.
    LaunchedEffect(track, st.isPlaying) {
        while (true) {
            pos = engine.positionMs
            dur = engine.durationMs
            delay(if (st.isPlaying) 250L else 1000L)
        }
    }

    val cover = remember(track) { track?.let(art) }

    Box(modifier.fillMaxSize().skinBackdrop(style)) {
        if (style.backdrop == PowerampBackdrop.BLURRED_ART && cover != null) {
            Image(cover, null, Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f)) {
                Crossfade(tab, label = "poweramp-tab") { t ->
                    when (t) {
                        null -> NowPlayingScreen(
                            track = track,
                            index = st.index,
                            total = st.queue.size,
                            art = cover,
                            playing = st.isPlaying,
                            positionMs = pos,
                            durationMs = dur,
                            style = style,
                            onSeek = { engine.seekTo(it); pos = it },
                            onPrev = engine::previous,
                            onPlayPause = engine::togglePlayPause,
                            onNext = engine::next,
                            shuffle = shuffle,
                            repeatMode = repeat,
                            onShuffle = { engine.player.shuffleModeEnabled = !engine.player.shuffleModeEnabled },
                            onRepeat = {
                                engine.player.repeatMode = when (engine.player.repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            },
                            onOpenEffects = { tab = PowerampTab.EFFECTS },
                        )
                        PowerampTab.LIBRARY -> LibraryScreen(library, track, style, onPlay = { engine.playQueue(library, it) }, skinId = skinId, onSkin = onSkin)
                        PowerampTab.SEARCH -> SearchScreen(library, track, style, onPlay = { engine.playQueue(library, it) })
                        PowerampTab.EFFECTS -> EffectsScreen(engine.equalizer, style, engine = engine, ui = eqUi)
                        PowerampTab.QUEUE -> QueueScreen(
                            tracks = st.queue,
                            currentIndex = st.index,
                            style = style,
                            onPlay = engine::playAt,
                            onRemove = engine::removeFromQueue,
                            onMove = engine::moveInQueue,
                        )
                    }
                }
            }
            // mini player and tabs share one plate, as in Poweramp
            val pill = style.tabs == PowerampTabs.PILL
            Column(
                Modifier.fillMaxWidth().padding(horizontal = if (pill) 12.dp else 0.dp, vertical = if (pill) 8.dp else 0.dp)
                    .clip(if (pill) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp)).background(style.panel),
            ) {
                if (tab != null && track != null) {
                    MiniBar(track, cover, st.isPlaying, pos, dur, style, engine::togglePlayPause) { tab = null }
                }
                PowerampTabBar(
                    selected = tab,
                    style = style,
                    onSelect = { tab = if (tab == it) null else it },
                    plate = false,
                )
            }
        }
    }
}

@Composable
private fun MiniBar(
    track: Track,
    cover: ImageBitmap?,
    playing: Boolean,
    positionMs: Int,
    durationMs: Int,
    style: PowerampStyle,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                if (cover != null) {
                    Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Rounded.MusicNote, null, tint = style.textDim, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.displayName(), color = style.onBackdrop, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.folderName(), color = style.textDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .semantics { contentDescription = if (playing) tr("Pause", "Duraklat") else tr("Play", "Oynat"); role = Role.Button }
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = style.accent, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape),
            color = style.accent,
            trackColor = style.textDim.copy(alpha = .25f),
        )
    }
}
