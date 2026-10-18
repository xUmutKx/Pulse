package com.umutk.pulse.extras.music.winamp

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
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
import kotlin.random.Random

/**
 * The whole Winamp look for a phone: main window (or its shade strip), equalizer and playlist stacked, every window
 * scaled to the screen width. A chip row picks the skin from the installed "skins" pack (the painted classic one is
 * always there). [onGetSkins], when set, adds a download chip while no skin is installed.
 * The spectrum is a decorative animation: the engine exposes no audio data.
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
    var chosen by remember { mutableStateOf(prefs.getString("skin", "") ?: "") }
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
    var levels by remember { mutableStateOf(List(19) { 0f }) }
    LaunchedEffect(st.isPlaying) {
        while (true) {
            levels = if (st.isPlaying) levels.map { maxOf(it * .82f, Random.nextFloat() * (.35f + .65f * Random.nextFloat())) }
            else levels.map { it * .8f }
            delay(70)
        }
    }

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
        val s = (maxWidth.value / WINAMP_W).coerceIn(1f, 3f)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (skins.isNotEmpty() || onGetSkins != null) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PIcon("palette", T.text, 20.dp, Modifier.padding(horizontal = 8.dp))
                    PChip(chosen.isEmpty(), tr("Classic", "Klasik")) { chosen = ""; prefs.edit().putString("skin", "").apply() }
                    skins.forEach { sk ->
                        Spacer(Modifier.width(6.dp))
                        PChip(chosen == sk.file.name, sk.name) { chosen = sk.file.name; prefs.edit().putString("skin", sk.file.name).apply() }
                    }
                    if (skins.isEmpty() && onGetSkins != null) {
                        Spacer(Modifier.width(6.dp))
                        PIcon("download", T.text, 22.dp, Modifier.heightIn(min = 40.dp).clickable { onGetSkins() }.padding(8.dp))
                    }
                }
            }
            WinampClassic(
                skin = skin, track = st.current, playing = st.isPlaying, positionMs = pos, durationMs = dur,
                levels = levels, volume = volume,
                onPrev = engine::previous, onPlay = { engine.play() }, onPause = engine::pause,
                onStop = { engine.pause(); engine.seekTo(0) }, onNext = engine::next, onEject = onAdd,
                onSeek = { engine.seekTo(it) },
                onVolume = { volume = it; engine.player.volume = it },
                onTogglePlaylist = { showPl = !showPl },
                onToggleEq = { showEq = !showEq; bands = eq.bandLevels },
                scaleDp = s, shuffle = shuffle, repeat = repeat, eqOn = showEq, plOn = showPl, shade = shade,
                onShuffle = { shuffle = !shuffle; engine.player.shuffleModeEnabled = shuffle },
                onRepeat = {
                    repeat = !repeat
                    engine.player.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                },
                onShade = { shade = !shade },
            )
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
