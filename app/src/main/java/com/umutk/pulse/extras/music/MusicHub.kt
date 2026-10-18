package com.umutk.pulse.extras.music

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.lyrics.LyricsPanel
import com.umutk.pulse.extras.music.looks.MaterialPlayer
import com.umutk.pulse.extras.music.poweramp.PowerampApp
import com.umutk.pulse.extras.music.poweramp.PowerampBackdrop
import com.umutk.pulse.extras.music.poweramp.PowerampStyle
import com.umutk.pulse.extras.music.poweramp.displayName
import com.umutk.pulse.extras.music.skin.DefaultWinampSkin
import com.umutk.pulse.extras.music.winamp.WinampClassic
import com.umutk.pulse.extras.music.winamp.WinampEqWindow
import com.umutk.pulse.extras.music.winamp.WinampPlaylistWindow
import com.umutk.pulse.extras.music.wmp7.Wmp7Actions
import com.umutk.pulse.extras.music.wmp7.Wmp7Mode
import com.umutk.pulse.extras.music.wmp7.Wmp7Now
import com.umutk.pulse.extras.music.wmp7.Wmp7Player
import com.umutk.pulse.extras.music.wmp7.Wmp7Skin
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** The four looks, in switcher order. Material is the default. */
private enum class MusicLook(val label: String) { MATERIAL("Material"), POWERAMP("Poweramp"), WINAMP("Winamp"), WMP("Windows Media Player") }

private fun prefs(c: Context) = c.getSharedPreferences("music_hub", Context.MODE_PRIVATE)

/**
 * Music host page: look switcher, library from MediaStore (permission asked once, picker as fallback), playlists with
 * M3U import/export, resume of the last queue. Playback lives in [MusicPlayerEngine] / [MusicPlaybackService].
 */
@Composable
fun MusicHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val openTool = com.umutk.pulse.LocalOpenTool.current
    val st by engine.state.collectAsState()
    val library by MusicLibrary.tracks.collectAsState()
    var look by remember { mutableStateOf(runCatching { MusicLook.valueOf(prefs(ctx).getString("look", "")!!) }.getOrDefault(MusicLook.MATERIAL)) }
    var artBg by remember { mutableStateOf(prefs(ctx).getBoolean("artBg", false)) }
    var lyrics by remember { mutableStateOf(false) }
    var playlistsOpen by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(MusicLibrary.hasPermission(ctx)) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        Playlists.restore(ctx, engine)
        if (!granted && !prefs(ctx).getBoolean("asked", false)) {
            prefs(ctx).edit().putBoolean("asked", true).apply()
            askPermission.launch(MusicLibrary.permission)
        }
    }
    LaunchedEffect(granted) { MusicLibrary.refresh(ctx) }
    DisposableEffect(Unit) { onDispose { engine.saveResume(); engine.equalizer.save(ctx) } }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) engine.playQueue(uris.map { Track(it.toString()) })
    }
    BackHandler(enabled = lyrics) { lyrics = false }
    BackHandler(onBack = onBack)

    val cover = remember(st.current) { st.current?.let { trackArt(ctx, it) } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (artBg && cover != null) {
            Image(cover, null, Modifier.fillMaxSize().blur(40.dp), contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", Color.White, 22.dp) }
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    MusicLook.values().forEach { l ->
                        PChip(look == l && !lyrics, l.label) { look = l; lyrics = false; prefs(ctx).edit().putString("look", l.name).apply() }
                        Spacer(Modifier.width(6.dp))
                    }
                    PChip(lyrics, tr("Lyrics", "Şarkı sözleri")) { lyrics = !lyrics }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { pick.launch(arrayOf("audio/*")) }, contentAlignment = Alignment.Center) { PIcon("folder", Color.White, 22.dp) }
                Box(Modifier.size(40.dp).clickable { playlistsOpen = true }, contentAlignment = Alignment.Center) { PIcon("newfolder", Color.White, 22.dp) }
                com.umutk.pulse.extras.watchdog.BatteryExemptionButton()
                if (!granted) Box(Modifier.size(40.dp).clickable { askPermission.launch(MusicLibrary.permission) }, contentAlignment = Alignment.Center) { PIcon("search", Color.White, 22.dp) }
                Spacer(Modifier.weight(1f))
                Text(tr("Art background", "Kapak arka planı"), color = Color.White, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                com.umutk.pulse.PSwitch(artBg, { artBg = it; prefs(ctx).edit().putBoolean("artBg", it).apply() })
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    lyrics -> LyricsPanel(st.current, rememberPosition(engine, st).value.first, Modifier.fillMaxSize(), onSeek = { engine.seekTo(it) })
                    look == MusicLook.MATERIAL -> MaterialPlayer(engine, library, Modifier.fillMaxSize(), artBackground = artBg)
                    look == MusicLook.POWERAMP -> PowerampPage(engine, library, artBg, ctx)
                    look == MusicLook.WINAMP -> com.umutk.pulse.extras.music.winamp.WinampScreen(engine, st, onAdd = { pick.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxSize(), onGetSkins = { openTool("extrastore") })
                    else -> WmpPage(engine, st, library, onOpen = { pick.launch(arrayOf("audio/*")) }, onPlaylists = { playlistsOpen = true })
                }
            }
        }
        if (playlistsOpen) PlaylistsDialog(engine, st.queue) { playlistsOpen = false }
    }
}

@Composable
private fun PowerampPage(engine: MusicPlayerEngine, library: List<Track>, artBg: Boolean, ctx: Context) {
    // skins carry their own backdrop (Album art, Glass...), the page-level art switch does not apply here
    com.umutk.pulse.extras.music.poweramp.PowerampPlayer(engine, library, art = { trackArt(ctx, it) }, modifier = Modifier.fillMaxSize())
}

/** Position and duration polled from the engine, which has no position flow. */
@Composable
private fun rememberPosition(engine: MusicPlayerEngine, st: PlaybackState): State<Pair<Int, Int>> {
    val s = remember { mutableStateOf(0 to 0) }
    LaunchedEffect(st.current, st.isPlaying) {
        while (true) {
            s.value = engine.positionMs to engine.durationMs
            delay(if (st.isPlaying) 250L else 1000L)
        }
    }
    return s
}

/** Winamp main window with its playlist and EQ windows stacked under it. */
@Composable
private fun WinampPage(engine: MusicPlayerEngine, st: PlaybackState, onEject: () -> Unit) {
    val (pos, dur) = rememberPosition(engine, st).value
    var showPl by remember { mutableStateOf(true) }
    var showEq by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(engine.player.volume) }
    val eq = engine.equalizer
    var eqOn by remember { mutableStateOf(eq.enabled) }
    var levels by remember { mutableStateOf(eq.bandLevels) }
    Column(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        WinampClassic(
            skin = DefaultWinampSkin,
            track = st.current,
            playing = st.isPlaying,
            positionMs = pos,
            durationMs = dur,
            levels = List(19) { 0f }, // no visualiser source is wired
            volume = volume,
            onPrev = engine::previous,
            onPlay = { engine.play() },
            onPause = engine::pause,
            onStop = { engine.pause(); engine.seekTo(0) },
            onNext = engine::next,
            onEject = onEject,
            onSeek = { engine.seekTo(it) },
            onVolume = { volume = it; engine.player.volume = it },
            onTogglePlaylist = { showPl = !showPl },
            onToggleEq = { showEq = !showEq; levels = eq.bandLevels },
        )
        if (showEq) WinampEqWindow(
            skin = DefaultWinampSkin,
            enabled = eqOn,
            bandHz = eq.centerFrequenciesHz,
            range = eq.levelRange,
            levels = levels,
            presets = eq.presetNames,
            onEnabled = { eqOn = it; eq.setEnabled(it) },
            onBand = { b, mb -> eq.setBandLevel(b, mb); levels = eq.bandLevels },
            onPreset = { eq.usePreset(it); levels = eq.bandLevels },
            onClose = { showEq = false },
        )
        if (showPl) WinampPlaylistWindow(DefaultWinampSkin, st.queue, st.index, onPick = engine::playAt, onClose = { showPl = false })
    }
}

/** Windows Media Player 7: the nav tree switches between the visualisation, the library list and the playlists dialog. */
@Composable
private fun WmpPage(engine: MusicPlayerEngine, st: PlaybackState, library: List<Track>, onOpen: () -> Unit, onPlaylists: () -> Unit) {
    val (pos, dur) = rememberPosition(engine, st).value
    var mode by remember { mutableStateOf(Wmp7Mode.FULL) }
    var nav by remember { mutableIntStateOf(0) }
    val navLabels = listOf(tr("Now Playing", "Çalıyor"), tr("Media Library", "Ortam Kitaplığı"), tr("Playlists", "Çalma listeleri"))
    val skin = Wmp7Skin.Default
    Box(Modifier.fillMaxSize()) {
        Wmp7Player(
            mode = mode,
            skin = skin,
            now = Wmp7Now(st.current?.displayName().orEmpty(), st.current?.artist.orEmpty(), st.isPlaying, pos, dur, emptyList()),
            menus = listOf(tr("File", "Dosya"), tr("View", "Görünüm"), tr("Play", "Yürüt")),
            nav = navLabels,
            navSelected = nav,
            actions = Wmp7Actions(
                onPrev = engine::previous, onPlayPause = engine::togglePlayPause,
                onStop = { engine.pause(); engine.seekTo(0) }, onNext = engine::next, onSeek = { engine.seekTo(it) },
                onMode = { mode = it },
                onMenu = { i -> when (i) { 0 -> onOpen(); 1 -> mode = if (mode == Wmp7Mode.FULL) Wmp7Mode.COMPACT else Wmp7Mode.FULL; else -> engine.togglePlayPause() } },
                onNav = { i -> nav = i; if (i == 2) onPlaylists() },
            ),
            modifier = Modifier.fillMaxSize(),
        )
        if (nav == 1 && mode == Wmp7Mode.FULL) {
            LazyColumn(Modifier.padding(start = 160.dp).fillMaxSize().background(skin.treeBg)) {
                itemsIndexed(library) { i, t ->
                    Text(t.displayName(), color = skin.treeText, fontSize = 13.sp, maxLines = 1,
                        modifier = Modifier.fillMaxWidth().clickable { engine.playQueue(library, i); nav = 0 }.padding(8.dp))
                }
            }
        }
    }
}

/** Named playlists: save the queue, load or delete one, import and export M3U8 through the picker. */
@Composable
private fun PlaylistsDialog(engine: MusicPlayerEngine, queue: List<Track>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var names by remember { mutableStateOf(Playlists.names(ctx)) }
    var name by remember { mutableStateOf("") }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Playlists.import(ctx, uri).takeIf { it.isNotEmpty() }?.let { engine.playQueue(it); onClose() }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        if (uri != null) runCatching { Playlists.export(ctx, uri, queue) }
    }
    WinAlert(
        onDismissRequest = onClose,
        title = { Text(tr("Playlists", "Çalma listeleri"), color = T.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row {
                    PButton(tr("Save queue", "Kuyruğu kaydet"), compact = true) {
                        if (name.isNotBlank() && queue.isNotEmpty()) { Playlists.save(ctx, name, queue); names = Playlists.names(ctx); name = "" }
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(36.dp).clickable { import.launch(arrayOf("*/*")) }, contentAlignment = Alignment.Center) { PIcon("newfolder", T.text, 20.dp) }
                    Box(Modifier.size(36.dp).clickable { if (queue.isNotEmpty()) export.launch("queue.m3u8") }, contentAlignment = Alignment.Center) { PIcon("download", T.text, 20.dp) }
                }
                names.forEach { n ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(n, color = T.text, fontSize = 14.sp, maxLines = 1,
                            modifier = Modifier.weight(1f).clickable { Playlists.load(ctx, n).takeIf { it.isNotEmpty() }?.let { engine.playQueue(it); onClose() } }.padding(vertical = 8.dp))
                        Box(Modifier.size(36.dp).clickable { Playlists.delete(ctx, n); names = Playlists.names(ctx) }, contentAlignment = Alignment.Center) { PIcon("delete", T.text, 20.dp) }
                    }
                }
            }
        },
        confirmButton = { PButton("OK", onClick = onClose) },
    )
}
