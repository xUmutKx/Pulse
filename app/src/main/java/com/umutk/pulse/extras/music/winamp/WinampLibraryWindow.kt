package com.umutk.pulse.extras.music.winamp

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.skin.WINAMP_W
import com.umutk.pulse.extras.music.skin.WinampSkin
import com.umutk.pulse.tr

/** Internet radio stations the user added, kept on the phone as "name<TAB>url" lines. */
object RadioStations {
    private fun sp(c: Context) = c.getSharedPreferences("winamp_radio", Context.MODE_PRIVATE)
    data class Station(val name: String, val url: String)

    fun list(c: Context): List<Station> = sp(c).getString("list", "").orEmpty().lines().mapNotNull { line ->
        val f = line.split('\t')
        if (f.size == 2 && f[1].isNotBlank()) Station(f[0], f[1]) else null
    }

    fun add(c: Context, name: String, url: String) {
        val u = url.trim()
        if (u.isBlank()) return
        val all = list(c) + Station(name.trim().ifBlank { u }, u)
        save(c, all)
    }

    fun remove(c: Context, index: Int) {
        save(c, list(c).filterIndexed { i, _ -> i != index })
    }

    private fun save(c: Context, l: List<Station>) {
        sp(c).edit().putString("list", l.joinToString("\n") { it.name.replace('\t', ' ') + "\t" + it.url }).apply()
    }
}

/**
 * The Winamp media library: a window in the playlist's colours with two tabs. LIBRARY lists the music found on the phone; RADIO
 * lists the internet stations the user added (tap one to play it, the X on a row removes it).
 */
@Composable
fun WinampLibraryWindow(skin: WinampSkin, engine: MusicPlayerEngine, onClose: () -> Unit, scaleDp: Float) {
    val ctx = LocalContext.current
    val c = skin.playlist
    // the same pixel font as the playlist window
    val font = com.umutk.pulse.BundledFonts.first { it.first == "Press Start 2P" }.second
    val tracks by MusicLibrary.tracks.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var stations by remember { mutableStateOf(RadioStations.list(ctx)) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val fs = maxOf(9f * scaleDp, 11f).sp
    // the radio tab fills itself from the open station directory (most listened of the phone's country); the box searches it by name
    var found by remember { mutableStateOf<List<com.umutk.pulse.extras.radio.Station>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val country = remember { java.util.Locale.getDefault().country.ifBlank { "" } }
    androidx.compose.runtime.LaunchedEffect(tab, query) {
        if (tab != 1) return@LaunchedEffect
        loading = true; failed = false
        if (query.isNotBlank()) kotlinx.coroutines.delay(500)
        runCatching { com.umutk.pulse.extras.radio.RadioDirectory.search(query, if (query.isBlank()) country else "") }
            .onSuccess { found = it }.onFailure { failed = true }
        loading = false
    }

    Column(Modifier.width((WINAMP_W * scaleDp).dp).background(c.normalBg).padding((4 * scaleDp).dp)) {
        Row(Modifier.fillMaxWidth().height((20 * scaleDp).dp).background(c.selectedBg), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("MEDIA LIBRARY", "MEDYA KÜTÜPHANESİ"), color = c.current, fontFamily = font, fontSize = fs,
                modifier = Modifier.weight(1f).padding(start = (4 * scaleDp).dp))
            Text("X", color = c.current, fontFamily = font, fontSize = fs, modifier = Modifier.clickable { onClose() }.padding(horizontal = (4 * scaleDp).dp))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = (3 * scaleDp).dp)) {
            listOf(tr("LIBRARY", "KÜTÜPHANE"), tr("RADIO", "RADYO")).forEachIndexed { i, label ->
                Text(label, color = if (tab == i) c.current else c.normal, fontFamily = font, fontSize = fs,
                    modifier = Modifier.clickable { tab = i }.padding(horizontal = (6 * scaleDp).dp, vertical = (2 * scaleDp).dp))
            }
        }
        LazyColumn(Modifier.fillMaxWidth().height((190 * scaleDp).dp).background(c.normalBg)) {
            if (tab == 0) {
                if (tracks.isEmpty()) item { Text(tr("No music found yet", "Henüz müzik bulunamadı"), color = c.normal, fontFamily = font, fontSize = fs, modifier = Modifier.padding(4.dp)) }
                itemsIndexed(tracks) { i, t ->
                    Text(trackLabel(t), color = c.normal, fontFamily = font, fontSize = fs, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { engine.playQueue(tracks, i) }.padding(horizontal = 4.dp, vertical = 3.dp))
                }
            } else {
                itemsIndexed(stations) { i, s ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, color = c.normal, fontFamily = font, fontSize = fs, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).clickable { engine.playQueue(listOf(Track(s.url, s.name))) })
                        Text("X", color = c.normal, fontFamily = font, fontSize = fs,
                            modifier = Modifier.clickable { RadioStations.remove(ctx, i); stations = RadioStations.list(ctx) }.padding(horizontal = 4.dp))
                    }
                }
                item {
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.current, fontFamily = font, fontSize = fs),
                        cursorBrush = SolidColor(c.current),
                        modifier = Modifier.fillMaxWidth().background(c.selectedBg).padding(4.dp),
                        decorationBox = { inner -> if (query.isEmpty()) Text(tr("SEARCH STATIONS", "İSTASYON ARA"), color = c.normal, fontFamily = font, fontSize = fs); inner() })
                }
                if (loading) item { Text(tr("LOADING…", "YÜKLENİYOR…"), color = c.normal, fontFamily = font, fontSize = fs, modifier = Modifier.padding(4.dp)) }
                if (failed) item { Text(tr("NO CONNECTION", "BAĞLANTI YOK"), color = c.normal, fontFamily = font, fontSize = fs, modifier = Modifier.padding(4.dp)) }
                itemsIndexed(found) { _, s ->
                    Text(s.name + if (s.bitrate > 0) "  ${s.bitrate}k" else "", color = c.normal, fontFamily = font, fontSize = fs, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().clickable { engine.playQueue(listOf(Track(s.url, s.name))) }.padding(horizontal = 4.dp, vertical = 3.dp))
                }
            }
        }
    }

    if (adding) WinAlert(
        onDismissRequest = { adding = false },
        title = { Text(tr("Add a station", "İstasyon ekle")) },
        text = {
            Column {
                PField(name, { name = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                PField(url, { url = it }, tr("Stream address (http...)", "Yayın adresi (http...)"), Modifier.fillMaxWidth())
            }
        },
        confirmButton = { PButton(tr("Add", "Ekle")) { RadioStations.add(ctx, name, url); stations = RadioStations.list(ctx); name = ""; url = ""; adding = false } },
        dismissButton = { PButton(tr("Cancel", "İptal")) { adding = false } },
    )
}
