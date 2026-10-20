package com.umutk.pulse.extras.music.poweramp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.umutk.pulse.extras.music.neighbourCovers
import com.umutk.pulse.tr
import com.umutk.pulse.extras.music.stats.MusicStats
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The whole Poweramp-style player. The caller owns [engine] (hold it in a foreground service) and the
 * [library]. [art] returns the cover for a track, or null. Pass a [style] to theme it.
 */
@Composable
fun PowerampApp(
    engine: MusicPlayerEngine,
    library: List<Track>,
    skinStyle: PowerampStyle,
    art: (Track) -> ImageBitmap?,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
) {
    val st by engine.state.collectAsState()
    // the player's copy has no genre or year, so the page and the song menu use the library's copy of the same file
    val libTracks by com.umutk.pulse.extras.music.MusicLibrary.tracks.collectAsState()
    val track = remember(st.current, libTracks) { st.current?.inLibrary(libTracks) }
    // Null means the now-playing screen. A tab opens its list, and the mini bar or a second tap goes back.
    var tab by remember { mutableStateOf<PowerampTab?>(null) }
    // the page the user was on last: a downward swipe on the cover goes back to it
    var lastTab by remember { mutableStateOf<PowerampTab?>(null) }
    LaunchedEffect(tab) { tab?.let { lastTab = it } }
    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var shuffle by remember { mutableStateOf(engine.player.shuffleModeEnabled) }
    var repeat by remember { mutableIntStateOf(engine.player.repeatMode) }
    val eqUi = remember { EqState() }
    val ctx = LocalContext.current
    var ratings by remember { mutableStateOf(MusicStats.ratings(ctx)) }
    var favourites by remember { mutableStateOf(MusicStats.favourites(ctx)) }
    val sleepLeft by engine.sleepLeftMs.collectAsState()
    var lyricsOpen by remember { mutableStateOf(false) }
    var tagEdit by remember { mutableStateOf(false) }
    var artOpen by remember { mutableStateOf(false) }
    var coverRev by remember { mutableIntStateOf(0) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    if (tagEdit && track != null) com.umutk.pulse.extras.music.TagEditDialog(track, onDone = { scope.launch { com.umutk.pulse.extras.music.MusicLibrary.refresh(ctx) } }, onClose = { tagEdit = false })
    BackHandler(enabled = lyricsOpen) { lyricsOpen = false }
    BackHandler(enabled = tab != null && !lyricsOpen) { tab = null }
    DisposableEffect(engine) {
        val l = object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) { shuffle = shuffleModeEnabled }
            override fun onRepeatModeChanged(repeatMode: Int) { repeat = repeatMode }
            override fun onAudioSessionIdChanged(audioSessionId: Int) { PowerampReverb.attach(ctx, audioSessionId) }
        }
        engine.player.addListener(l)
        PowerampReverb.attach(ctx, engine.player.audioSessionId)
        onDispose { engine.player.removeListener(l) }
    }

    // The engine has no position flow, so poll it. Slower while paused.
    LaunchedEffect(track, st.isPlaying) {
        while (true) {
            pos = engine.positionMs
            dur = engine.durationMs
            delay(if (st.isPlaying) 120L else 1000L)
        }
    }

    // a cover picked online (saved under files/covers) wins over the embedded one; coverRev reloads it after a pick
    // read off the main thread: a cover missing here is looked up online once and saved, so it stays after a restart
    val cover by produceState<ImageBitmap?>(null, track?.path, coverRev) {
        value = track?.let { t -> withContext(Dispatchers.IO) { runCatching { PowerampCovers.saved(ctx, t.path) ?: art(t) }.getOrNull() } }
    }
    val (artPrev, artNext) = neighbourCovers(st.queue, st.index) { t -> PowerampCovers.saved(ctx, t.path) ?: art(t) }

    // every page is pure black, edges included; the skin's colours stay as accents and text
    val style = remember(skinStyle) { skinStyle.copy(backdrop = PowerampBackdrop.BLACK, solid = Color.Black, gradientTo = null) }
    if (artOpen && track != null) CoverArtDialog(track, style, onSaved = { coverRev++ }, onClose = { artOpen = false })
    val artBg = PowerampPrefs.playerArt(ctx)
    Box(modifier.fillMaxSize().background(Color.Black)) {
        // only the now-playing page takes the blurred cover as its backdrop, when the player setting allows it
        // one layer only: the blurred cover at full strength, edge to edge outside the safe-area inset; black when off
        // each page can keep or drop the blurred cover (Settings > Art background); a page left out is plain black
        val pageKey = tab?.name?.lowercase() ?: "now"
        if (artBg && PowerampPrefs.artPage(ctx, pageKey)) cover?.let { Image(it, null, Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Box(Modifier.weight(1f)) {
                Crossfade(tab, label = "poweramp-tab") { t ->
                    when (t) {
                        null -> NowPlayingScreen(
                            track = track,
                            index = st.index,
                            total = st.queue.size,
                            art = cover,
                            artPrev = artPrev,
                            artNext = artNext,
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
                            rating = track?.let { ratings[it.path] } ?: 0,
                            onRate = { r -> track?.let { MusicStats.setRating(ctx, it.path, r); ratings = MusicStats.ratings(ctx) } },
                            favourite = track != null && track.path in favourites,
                            onFavourite = { track?.let { MusicStats.toggleFavourite(ctx, it.path); favourites = MusicStats.favourites(ctx) } },
                            sleepMinutes = if (sleepLeft < 0) -1 else ((sleepLeft + 59_999L) / 60_000L).toInt(),
                            onSleepSet = { m, end -> engine.setSleep(m, end) },
                            onLyrics = { lyricsOpen = !lyricsOpen },
                            onPullDown = { tab = lastTab ?: PowerampTab.LIBRARY },
                            lyricsOpen = lyricsOpen,
                            onEditTags = { tagEdit = true },
                            onFindArt = { artOpen = true },
                        )
                        PowerampTab.LIBRARY -> LibraryScreen(library, track, style, onPlay = { engine.playQueue(library, it) }, queue = st.queue, onPlayTracks = { l, i -> engine.playQueue(l, i) })
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
            // one panel for the whole bottom: while a track plays its bar sits on top of the tabs and the menu button.
            // The now-playing page has its own controls, so there the panel holds only the tab row.
            val pill = style.tabs == PowerampTabs.PILL
            Column(
                Modifier.fillMaxWidth().padding(horizontal = if (pill) 8.dp else 0.dp, vertical = if (pill) 8.dp else 0.dp)
                    .let { if (pill) it.clip(RoundedCornerShape(24.dp)) else it },
                // no solid plate here: the bottom area shows the blurred cover, or the black page when the art background is off
            ) {
                if (tab != null && track != null) {
                    PowerampMiniBar(
                        track = track,
                        art = cover,
                        playing = st.isPlaying,
                        style = style,
                        pill = false,
                        onOpen = { tab = null },
                        onPlayPause = engine::togglePlayPause,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        PowerampTabBar(
                            selected = tab,
                            style = style,
                            onSelect = { tab = if (tab == it) null else it },
                            plate = false,
                        )
                    }
                    if (onSettings != null) {
                        Box(
                            Modifier.size(48.dp).semantics { role = Role.Button }.clickable(onClick = onSettings),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.Menu, tr("Settings", "Ayarlar"), tint = style.textDim, modifier = Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }
}
