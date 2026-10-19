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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import com.umutk.pulse.Look
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
import kotlinx.coroutines.launch

/** The four looks, in switcher order. Material is the default. */
private enum class MusicLook(val label: String) { MATERIAL("Material"), POWERAMP("Poweramp"), WINAMP("Winamp"), WMP("Windows Media Player"), LYRICS("Lyrics") }

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
    var settingsOpen by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var noiseOpen by remember { mutableStateOf(false) }
    var onlineOpen by remember { mutableStateOf(false) }
    var spotifyOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var floating by remember { mutableStateOf(false) }
    var hitResult by remember { mutableStateOf<com.umutk.pulse.extras.lyrics.LyricsResult?>(null) }
    val hubScope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) {
        when ((ctx as? android.app.Activity)?.intent?.getStringExtra(MusicPlaybackService.EXTRA_OPEN)) {
            "lyrics" -> lyrics = true
            "noise" -> noiseOpen = true
            "eq" -> settingsOpen = true
        }
    }
    var granted by remember { mutableStateOf(MusicLibrary.hasPermission(ctx)) }

    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (MusicSettings.resume(ctx)) Playlists.restore(ctx, engine)
        if (!granted && !prefs(ctx).getBoolean("asked", false)) {
            prefs(ctx).edit().putBoolean("asked", true).apply()
            askPermission.launch(MusicLibrary.permission)
        }
    }
    LaunchedEffect(granted) { MusicLibrary.refresh(ctx) }
    DisposableEffect(Unit) { onDispose { engine.saveResume(); engine.equalizer.save(ctx) } }
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(rev, settingsOpen) {
        hostView.keepScreenOn = MusicSettings.keepScreenOn(ctx)
        onDispose { hostView.keepScreenOn = false }
    }

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
                Spacer(Modifier.weight(1f))
                // the looks, art background, add music and playlists live on the settings page behind the gear
                // labelled, so the white noise page is easy to find from the player
                // Winamp has its own bar, so these two pills stay off that look
                if (look != MusicLook.WINAMP) {
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .14f)).clickable { noiseOpen = true }
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("volume", Color.White, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("White noise", "Beyaz gürültü"), color = Color.White, fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .14f)).clickable { onlineOpen = true }
                        .padding(start = 8.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("newfolder", Color.White, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(tr("Playlists", "Çalma listeleri"), color = Color.White, fontSize = 12.sp)
                    }
                }
                Box(Modifier.size(40.dp).clickable { settingsOpen = true }, contentAlignment = Alignment.Center) { PIcon("settings", Color.White, 22.dp) }
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
              androidx.compose.runtime.key(rev) {
                when {
                    lyrics -> LyricsPanel(st.current, rememberPosition(engine, st).value.first, Modifier.fillMaxSize(), onSeek = { engine.seekTo(it) })
                    look == MusicLook.MATERIAL -> MaterialPlayer(engine, library, Modifier.fillMaxSize(), artBackground = artBg)
                    look == MusicLook.POWERAMP -> PowerampPage(engine, library, artBg, ctx)
                    look == MusicLook.WINAMP -> com.umutk.pulse.extras.music.winamp.WinampScreen(engine, st, onAdd = { pick.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxSize(), onGetSkins = { openTool("extrastore") })
                    look == MusicLook.LYRICS -> com.umutk.pulse.extras.music.winamp.LyricsLookPage(engine, st, modifier = Modifier.fillMaxSize())
                    else -> WmpPage(engine, st, library, onOpen = { pick.launch(arrayOf("audio/*")) }, onPlaylists = { playlistsOpen = true })
                }
              }
                // Poweramp and Winamp carry their own lyrics button; the others get one on the right edge
                if (look != MusicLook.POWERAMP && look != MusicLook.WINAMP && look != MusicLook.LYRICS) Box(Modifier.align(Alignment.CenterEnd).clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                    .background(if (lyrics) Look.accent else Color.Black.copy(alpha = .6f)).clickable { lyrics = !lyrics }.padding(horizontal = 10.dp, vertical = 16.dp)) {
                    Text(tr("Lyrics", "Sözler"), color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (noiseOpen) Box(Modifier.fillMaxSize().background(T.bg)) { com.umutk.pulse.extras.whitenoise.WhiteNoiseHub { noiseOpen = false } }
        if (onlineOpen) Box(Modifier.fillMaxSize().background(T.bg)) { com.umutk.pulse.extras.playlists.OnlinePlaylistsHub { onlineOpen = false } }
        if (spotifyOpen) Box(Modifier.fillMaxSize().background(T.bg)) { com.umutk.pulse.extras.spotify.SpotifyHub.Screen { spotifyOpen = false } }
        if (searchOpen) Box(Modifier.fillMaxSize().background(T.bg)) {
            com.umutk.pulse.extras.lyrics.LyricsSearchScreen(
                onOpen = { hit -> hubScope.launch { hitResult = com.umutk.pulse.extras.lyrics.Lyrics.forHit(ctx, hit) } },
                onClose = { searchOpen = false },
            )
        }
        hitResult?.let { r ->
            BackHandler { hitResult = null }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                com.umutk.pulse.extras.lyrics.LyricsView(r, -1L, null, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 48.dp))
                Box(Modifier.align(Alignment.TopEnd).size(44.dp).clickable { hitResult = null }, contentAlignment = Alignment.Center) { PIcon("close", Color.White, 22.dp) }
            }
        }
        if (playlistsOpen) PlaylistsDialog(engine, st.queue) { playlistsOpen = false }
        if (settingsOpen) {
            BackHandler { settingsOpen = false; rev++ }
            val ink = T.text
            Column(Modifier.fillMaxSize().background(T.bg).padding(horizontal = 12.dp, vertical = 8.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clickable { settingsOpen = false; rev++ }, contentAlignment = Alignment.Center) { PIcon("back", ink, 22.dp) }
                    Text(tr("Player settings", "Oynatıcı ayarları"), color = ink, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(tr("Look", "Görünüm"), color = Look.accent, fontSize = 12.sp)
                MusicLook.values().forEach { l ->
                    Row(Modifier.fillMaxWidth().clickable { look = l; lyrics = false; prefs(ctx).edit().putString("look", l.name).apply() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        com.umutk.pulse.PRadio(look == l) { look = l; lyrics = false; prefs(ctx).edit().putString("look", l.name).apply() }
                        Text(l.label, color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                com.umutk.pulse.ExtraLookRow(ink)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Art background", "Kapak arka planı"), color = ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    com.umutk.pulse.PSwitch(artBg, { artBg = it; prefs(ctx).edit().putBoolean("artBg", it).apply() })
                }
                Text(tr("Music", "Müzik"), color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().clickable { settingsOpen = false; pick.launch(arrayOf("audio/*")) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("folder", ink, 22.dp); Text(tr("Add music", "Müzik ekle"), color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Row(Modifier.fillMaxWidth().clickable { settingsOpen = false; playlistsOpen = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("newfolder", ink, 22.dp); Text(tr("Playlists", "Çalma listeleri"), color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Row(Modifier.fillMaxWidth().clickable { settingsOpen = false; searchOpen = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("book", ink, 22.dp); Text(tr("Find lyrics by text", "Sözlerle şarkı bul"), color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("search", ink, 22.dp); Text(tr("Floating lyrics search", "Yüzen söz araması"), color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    com.umutk.pulse.PSwitch(floating, { floating = it; if (it) com.umutk.pulse.extras.lyrics.FloatingLyricsSearch.show(ctx) else com.umutk.pulse.extras.lyrics.FloatingLyricsSearch.hide(ctx) })
                }
                Row(Modifier.fillMaxWidth().clickable { settingsOpen = false; spotifyOpen = true }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("heart", ink, 22.dp); Text("Spotify", color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                if (!granted) Row(Modifier.fillMaxWidth().clickable { askPermission.launch(MusicLibrary.permission) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("search", ink, 22.dp); Text(tr("Scan the phone's music", "Telefondaki müziği tara"), color = ink, fontSize = 16.sp, modifier = Modifier.padding(start = 12.dp))
                }
                Text(tr("Background", "Arka plan"), color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                com.umutk.pulse.extras.watchdog.BatteryExemptionButton()
                Spacer(Modifier.height(8.dp))
                MusicSettingsSections(
                    engine, when (look) { MusicLook.MATERIAL -> SettingsFor.MATERIAL; MusicLook.POWERAMP -> SettingsFor.POWERAMP; MusicLook.WINAMP -> SettingsFor.WINAMP; else -> SettingsFor.WMP },
                    onRescan = { MusicLibrary.refresh(ctx) },
                ) {
                    if (look == MusicLook.WINAMP) com.umutk.pulse.extras.ui.SettingsSection("Winamp") { com.umutk.pulse.extras.music.winamp.WinampSkinSettings() }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
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
            now = Wmp7Now(st.current?.displayName().orEmpty(), st.current?.artist.orEmpty(), st.isPlaying, pos, dur, rememberLevels(16).value),
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
