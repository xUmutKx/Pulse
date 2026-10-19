package com.umutk.pulse.extras.music.poweramp

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.Playlists
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.stats.MusicStats
import com.umutk.pulse.tr

/** Totals, most played, recently played, top artists and albums, from [MusicStats]. */
@Composable
internal fun StatsPage(style: PowerampStyle) {
    val c = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    val fg = style.onBackdrop
    val d = remember(rev) {
        listOf(MusicStats.totalPlays(c), MusicStats.totalMs(c))
    }
    val most = remember(rev) { MusicStats.mostPlayed(c, 10) }
    val recent = remember(rev) { MusicStats.recent(c, 10) }
    val artists = remember(rev) { MusicStats.top(c, true, 5) }
    val albums = remember(rev) { MusicStats.top(c, false, 5) }
    @Composable fun head(t: String) = Text(t, color = style.textDim, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    @Composable fun line(l: String, r: String) = Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(r, color = style.textDim, fontSize = 14.sp)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Big("${d[0]}", tr("plays", "dinleme"), style)
            val m = (d[1] as Long) / 60_000
            Big(if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m", tr("listened", "süre"), style)
        }
        head(tr("Most played", "En çok çalınan"))
        most.forEach { line(it.title, "${it.plays}") }
        head(tr("Recently played", "Son çalınan"))
        recent.forEach { line(it.title, it.artist) }
        head(tr("Top artists", "Sanatçılar"))
        artists.forEach { line(it.first, "${it.second}") }
        head(tr("Top albums", "Albümler"))
        albums.forEach { line(it.first, "${it.second}") }
        Spacer(Modifier.height(12.dp))
        if (d[0] as Int > 0) IconButton(onClick = { MusicStats.clear(c); rev++ }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Rounded.Delete, tr("Clear", "Temizle"), tint = style.textDim)
        }
    }
}

@Composable
private fun Big(v: String, label: String, style: PowerampStyle) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = style.onBackdrop, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = style.textDim, fontSize = 13.sp)
    }
}

/**
 * Saved playlists: save the current queue as a new one, rename, delete; a tap opens it, [onOpen] shows its tracks.
 */
@Composable
internal fun PlaylistsPage(queue: List<Track>, style: PowerampStyle, onOpen: (String) -> Unit) {
    val c = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<String?>(null) } // "" = new, else the name being renamed
    var text by remember { mutableStateOf("") }
    val names = remember(rev) { Playlists.names(c) }
    val fg = style.onBackdrop
    Column(Modifier.fillMaxSize()) {
        if (edit == null) {
            IconButton(onClick = { edit = ""; text = "" }, modifier = Modifier.padding(start = 8.dp)) {
                Icon(Icons.Rounded.Add, tr("New from queue", "Kuyruktan yeni"), tint = fg)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(CircleShape).background(style.chip).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(text, { text = it }, singleLine = true, textStyle = TextStyle(color = fg, fontSize = 17.sp), cursorBrush = SolidColor(style.accent), modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val old = edit.orEmpty()
                    if (text.isNotBlank()) {
                        if (old.isEmpty()) Playlists.save(c, text, queue)
                        else if (old != text) { Playlists.save(c, text, Playlists.load(c, old)); Playlists.delete(c, old) }
                    }
                    edit = null; rev++
                }) { Icon(Icons.Rounded.Check, tr("Save", "Kaydet"), tint = style.accent) }
                IconButton(onClick = { edit = null }) { Icon(Icons.Rounded.Close, tr("Cancel", "Vazgeç"), tint = style.textDim) }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
            items(names) { n ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(n) }.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { edit = n; text = n }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Edit, tr("Rename", "Yeniden adlandır"), tint = style.textDim) }
                    IconButton(onClick = { Playlists.delete(c, n); rev++ }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.Delete, tr("Delete", "Sil"), tint = style.textDim) }
                }
            }
        }
    }
}
