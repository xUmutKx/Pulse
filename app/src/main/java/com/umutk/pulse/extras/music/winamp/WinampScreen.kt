package com.umutk.pulse.extras.music.winamp

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PField
import com.umutk.pulse.extras.music.skin.InstalledSkin
import com.umutk.pulse.extras.music.skin.loadSkinPreview
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.PlaybackState
import com.umutk.pulse.extras.music.skin.DefaultWinampSkin
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.extras.music.skin.installedSkins
import com.umutk.pulse.extras.music.skin.loadWinampSkin
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The whole Winamp look for a phone: main window (or its shade strip), equalizer and playlist stacked, every window
 * scaled to the screen width. A thumbnail strip (real main.bmp of each skin, with search) picks the skin from the bundled skins plus any
 * the user put in the skin folders; the painted classic one is always first. [onGetSkins] is unused now (skins ship in the APK).
 * The spectrum shows the real audio levels (MusicLevels).
 */
@Composable
fun WinampScreen(
    engine: MusicPlayerEngine,
    st: PlaybackState,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    onGetSkins: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("winamp_look", android.content.Context.MODE_PRIVATE) }
    val turkish = tr("en", "tr") == "tr"
    var skins by remember { mutableStateOf(emptyList<com.umutk.pulse.extras.music.skin.InstalledSkin>()) }
    var chosen by remember { mutableStateOf(prefs.getString("skin", "")?.ifEmpty { "base-2.91.wsz" } ?: "base-2.91.wsz") }
    var skin by remember { mutableStateOf<WinampSkin>(DefaultWinampSkin) }
    LaunchedEffect(Unit) { skins = withContext(Dispatchers.IO) { installedSkins(ctx, turkish) } }
    LaunchedEffect(chosen, skins) {
        val f = skins.firstOrNull { it.file.name == chosen }?.file
        skin = if (f == null) DefaultWinampSkin else withContext(Dispatchers.IO) { loadWinampSkin(f) } ?: DefaultWinampSkin
    }

    var pos by remember { mutableStateOf(0) }
    var dur by remember { mutableStateOf(0) }
    LaunchedEffect(st.current, st.isPlaying) {
        while (true) {
            pos = engine.positionMs; dur = engine.durationMs
            delay(if (st.isPlaying) 250L else 1000L)
        }
    }
    val levels by com.umutk.pulse.extras.music.rememberLevels(com.umutk.pulse.extras.music.visual.VIS_BARS)
    val wave by com.umutk.pulse.extras.music.rememberWave()
    var visScope by remember { mutableStateOf(prefs.getBoolean("vis_scope", false)) }

    var lyricsOn by remember { mutableStateOf(false) }
    var showPl by remember { mutableStateOf(true) }
    var showEq by remember { mutableStateOf(false) }
    var shade by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(engine.player.volume) }
    var shuffle by remember { mutableStateOf(engine.player.shuffleModeEnabled) }
    var repeat by remember { mutableStateOf(engine.player.repeatMode != Player.REPEAT_MODE_OFF) }
    val eq = engine.equalizer
    var eqEnabled by remember { mutableStateOf(eq.enabled) }
    var bands by remember { mutableStateOf(eq.bandLevels) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        // windows fill the width inside an 8 dp gutter on each side
        val s = ((maxWidth.value - 16f) / WINAMP_W).coerceIn(1f, 3f)
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp).verticalScroll(rememberScrollState())) {
            WinampClassic(
                skin = skin, track = st.current, playing = st.isPlaying, positionMs = pos, durationMs = dur,
                levels = levels, volume = volume, wave = wave, scope = visScope,
                onVisToggle = { visScope = !visScope; prefs.edit().putBoolean("vis_scope", visScope).apply() },
                onPrev = engine::previous, onPlay = { engine.play() }, onPause = engine::pause,
                onStop = { engine.pause(); engine.seekTo(0) }, onNext = engine::next, onEject = onAdd,
                onSeek = { engine.seekTo(it) },
                onVolume = { volume = it; engine.player.volume = it },
                onTogglePlaylist = { showPl = !showPl },
                lyricsOn = lyricsOn, onLyrics = { lyricsOn = !lyricsOn },
                onToggleEq = { showEq = !showEq; bands = eq.bandLevels },
                scaleDp = s, shuffle = shuffle, repeat = repeat, eqOn = showEq, plOn = showPl, shade = shade,
                onShuffle = { shuffle = !shuffle; engine.player.shuffleModeEnabled = shuffle },
                onRepeat = {
                    repeat = !repeat
                    engine.player.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                },
                onShade = { shade = !shade },
            )
            if (lyricsOn) WinampLyricsPane(skin, st.current, pos, { engine.seekTo(it) }, s)
            if (showEq) WinampEqWindow(
                skin = skin, enabled = eqEnabled, bandHz = eq.centerFrequenciesHz, range = eq.levelRange, levels = bands,
                presets = eq.presetNames,
                onEnabled = { eqEnabled = it; eq.setEnabled(it) },
                onBand = { b, mb -> eq.setBandLevel(b, mb); bands = eq.bandLevels },
                onPreset = { eq.usePreset(it); bands = eq.bandLevels },
                onClose = { showEq = false }, scaleDp = s,
            )
            if (showPl) WinampPlaylistWindow(
                skin, st.queue, st.index, onPick = engine::playAt, onClose = { showPl = false }, scaleDp = s,
                onAdd = onAdd, onRemove = { engine.removeFromQueue(it) },
            )
        }
    }
}

private val SKIN_THUMB_W = 210.dp
// three thumbnails (each with its name) stacked: 3 x ~108dp
private val SKIN_STRIP_H = 332.dp

/** Search box over a scrolling strip of skin thumbnails; "" is the painted classic skin, else the skin file name. */
@Composable
internal fun SkinStrip(skins: List<InstalledSkin>, chosen: String, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val shown = remember(skins, q) { if (q.isBlank()) skins else skins.filter { it.name.contains(q.trim(), ignoreCase = true) } }
    val state = rememberLazyGridState()
    LaunchedEffect(skins) {
        val i = skins.indexOfFirst { it.file.name == chosen }
        if (i >= 0) state.scrollToItem(i + 1)
    }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        if (skins.size > 8) PField(q, { q = it }, tr("Search", "Ara"), Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
        // three rows scroll sideways together; each preview is the skin's own main window, drawn at a readable size
        LazyHorizontalGrid(GridCells.Fixed(3), Modifier.fillMaxWidth().height(SKIN_STRIP_H), state = state, contentPadding = PaddingValues(horizontal = 6.dp)) {
            if (q.isBlank()) item(key = "classic") { SkinThumb(tr("Classic", "Klasik"), null, chosen.isEmpty()) { onPick("") } }
            items(shown, key = { it.file.path }) { sk -> SkinThumb(sk.name, sk.file, chosen == sk.file.name) { onPick(sk.file.name) } }
        }
    }
}

@Composable
private fun SkinThumb(label: String, file: java.io.File?, on: Boolean, onClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(if (file == null) DefaultWinampSkin.main else null, file) {
        if (file != null) value = withContext(Dispatchers.IO) { loadSkinPreview(file) }
    }
    Column(Modifier.width(SKIN_THUMB_W).clickable(onClick = onClick).padding(4.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(WINAMP_W / 116f).border(if (on) 2.dp else 1.dp, if (on) Look.accent.copy(alpha = 1f) else T.text.copy(alpha = .25f))) {
            img?.let { Image(it, label, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None) }
        }
        Text(label, color = T.text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The skin chooser for the settings page: one horizontal strip, same preference the player reads. */
@Composable
fun WinampSkinSettings() {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("winamp_look", android.content.Context.MODE_PRIVATE) }
    val turkish = tr("en", "tr") == "tr"
    var skins by remember { mutableStateOf(emptyList<InstalledSkin>()) }
    var chosen by remember { mutableStateOf(prefs.getString("skin", "")?.ifEmpty { "base-2.91.wsz" } ?: "base-2.91.wsz") }
    LaunchedEffect(Unit) { skins = withContext(Dispatchers.IO) { installedSkins(ctx, turkish) } }
    // the built-in Classic skin (grey-blue body, green LCD, bevelled buttons) is always the first tile
    SkinStrip(skins, chosen) { chosen = it; prefs.edit().putString("skin", it).apply() }
}
