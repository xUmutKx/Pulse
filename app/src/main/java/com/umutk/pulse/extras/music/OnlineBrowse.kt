package com.umutk.pulse.extras.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * YouTube Music and Spotify browsed from inside the music player, as one client (in the way of Spotube): YouTube Music's popular songs and a search,
 * the Spotify playlists and liked songs of the signed-in account. Every song plays through YouTube Music in YTXTube (Spotify only lets its own app play).
 */
@Composable
fun OnlineBrowse(onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }           // 0 YouTube Music, 1 Spotify
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<OnlineMusic.Hit>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var spLists by remember { mutableStateOf<List<com.umutk.pulse.extras.spotify.SpPlaylist>>(emptyList()) }
    fun show(t: String, load: suspend () -> List<OnlineMusic.Hit>) { title = t; busy = true; scope.launch { list = runCatching { load() }.getOrDefault(emptyList()); busy = false } }
    LaunchedEffect(tab) {
        list = emptyList()
        if (tab == 0) show(tr("Popular now", "Şu an popüler")) { OnlineMusic.youtube(ctx, "top hits this week") }
        else {
            spLists = runCatching { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).playlists() }.getOrDefault(emptyList())
            show(tr("Liked songs", "Beğenilen şarkılar")) { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).liked().map { OnlineMusic.Hit(it.title, it.artist, it.image, "sp", null) } }
        }
    }
    LaunchedEffect(query, tab) {
        if (query.trim().length < 2) return@LaunchedEffect
        delay(600)
        show("“$query”") { if (tab == 0) OnlineMusic.youtube(ctx, query) else OnlineMusic.spotify(ctx, query) }
    }
    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            listOf("YouTube Music", "Spotify").forEachIndexed { i, n ->
                Text(n, color = if (tab == i) Look.accent else T.sub, fontSize = 16.sp, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { tab = i }.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 16.sp), cursorBrush = SolidColor(Look.accent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(T.panel).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner -> if (query.isEmpty()) Text(tr("Search songs", "Şarkı ara"), color = T.sub, fontSize = 16.sp); inner() })
        if (tab == 1 && spLists.isNotEmpty() && query.isBlank()) androidx.compose.foundation.lazy.LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(spLists) { pl ->
                Column(Modifier.width(96.dp).clickable { show(pl.name) { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).playlistTracks(pl.id).map { OnlineMusic.Hit(it.title, it.artist, it.image, "sp", null) } } }) {
                    coil.compose.AsyncImage(pl.image, null, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(T.panel), contentScale = ContentScale.Crop)
                    Text(pl.name, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (tab == 1 && spLists.isEmpty() && list.isEmpty() && !busy) Text(tr("Sign in to Spotify in the music settings to see your playlists and to search Spotify.", "Çalma listelerin ve Spotify araması için müzik ayarlarından Spotify’a giriş yap."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Text(if (busy) tr("Loading…", "Yükleniyor…") else title, color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(list) { h ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { scope.launch { OnlineMusic.play(ctx, h) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    coil.compose.AsyncImage(h.thumb, null, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(T.panel), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(h.title, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.artist, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PIcon("play", T.sub, 22.dp)
                }
            }
        }
    }
}
