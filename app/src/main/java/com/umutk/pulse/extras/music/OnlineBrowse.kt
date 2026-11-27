package com.umutk.pulse.extras.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
fun OnlineBrowse(startTab: Int = 0, embedded: Boolean = false, onBack: () -> Unit) {
    BackHandler { onBack() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(startTab) }           // 0 YouTube Music, 1 Spotify
    var query by remember { mutableStateOf("") }
    var list by remember { mutableStateOf<List<OnlineMusic.Hit>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var ytIn by remember { mutableStateOf(false) }
    var ytLists by remember { mutableStateOf<List<com.umutk.pulse.extras.ytx.PlaylistRef>>(emptyList()) }
    var spLists by remember { mutableStateOf<List<com.umutk.pulse.extras.spotify.SpPlaylist>>(emptyList()) }
    // YouTube Music's home under the chips: a speed dial (what was played last) and suggestions (what the taste log and the radio offer)
    var dial by remember { mutableStateOf<List<OnlineMusic.Hit>>(emptyList()) }
    var suggest by remember { mutableStateOf<List<OnlineMusic.Hit>>(emptyList()) }
    LaunchedEffect(tab) {
        if (tab != 0) return@LaunchedEffect
        dial = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { OnlineMusic.history(ctx).take(10) }.getOrDefault(emptyList()) }
        suggest = runCatching { OnlineMusic.forYou(ctx) }.getOrDefault(emptyList()).take(14)
    }
    // "Played": everything that was listened to in the player (online and on the phone), searched and filtered here
    var played by remember { mutableStateOf(false) }
    var playedKind by remember { mutableIntStateOf(0) }   // 0 all, 1 online, 2 on the phone
    fun show(t: String, load: suspend () -> List<OnlineMusic.Hit>) { played = false; title = t; busy = true; scope.launch { list = runCatching { load() }.getOrDefault(emptyList()); busy = false } }
    LaunchedEffect(tab) {
        list = emptyList()
        if (tab == 0) {
            // signed in to YouTube Music (in YTXTube): your own liked songs first, then your playlists; else what is popular
            ytIn = OnlineMusic.ytSignedIn(ctx)
            ytLists = if (ytIn) OnlineMusic.ytPlaylists(ctx) else emptyList()
            // what was played here comes first: the "For you" list (the radio of the latest songs); with nothing played yet, your liked songs or what is popular
            if (OnlineMusic.history(ctx).isNotEmpty()) show(tr("For you", "Senin için")) { OnlineMusic.forYou(ctx) }
            else if (ytIn) show(tr("Your liked songs", "Beğendiğin şarkılar")) { OnlineMusic.ytLiked(ctx) } else show(tr("Popular now", "Şu an popüler")) { OnlineMusic.youtube(ctx, "top hits this week") }
        }
        else {
            spLists = runCatching { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).playlists() }.getOrDefault(emptyList())
            show(tr("Liked songs", "Beğenilen şarkılar")) { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).liked().map { OnlineMusic.Hit(it.title, it.artist, it.image, "sp", null) } }
        }
    }
    LaunchedEffect(query, tab, played) {
        if (played || query.trim().length < 2) return@LaunchedEffect
        delay(600)
        show("“$query”") { if (tab == 0) OnlineMusic.youtube(ctx, query) else OnlineMusic.spotify(ctx, query) }
    }
    // Spotify needs its sign-in first: the login page itself opens here (not a note pointing at the settings)
    LaunchedEffect(Unit) { com.umutk.pulse.extras.spotify.SpotifyAuth.restore(ctx) }
    if (tab == 1 && !com.umutk.pulse.extras.spotify.SpotifyAuth.loggedIn) { com.umutk.pulse.extras.spotify.LoginPage(onBack); return }
    // inside the library (embedded) the page takes its place and look: no window background or insets of its own
    Column(Modifier.fillMaxSize().then(if (embedded) Modifier else Modifier.background(T.bg).statusBarsPadding().navigationBarsPadding()).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
        }
        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 16.sp), cursorBrush = SolidColor(Look.accent),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(T.panel).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner -> if (query.isEmpty()) Text(tr("Search songs", "Şarkı ara"), color = T.sub, fontSize = 16.sp); inner() })
        // your YouTube Music: liked, recent, popular, then your playlists as covers
        if (tab == 0 && query.isBlank()) {
            Row(Modifier.padding(top = 10.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                @Composable fun chip(t: String, on: () -> Unit) = Text(t, color = T.text, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(T.panel).clickable { on() }.padding(horizontal = 12.dp, vertical = 7.dp))
                if (ytIn) { chip(tr("Liked", "Beğenilen")) { show(tr("Your liked songs", "Beğendiğin şarkılar")) { OnlineMusic.ytLiked(ctx) } }; chip(tr("Recent", "Son çalınan")) { show(tr("Played lately", "Son çalınanlar")) { OnlineMusic.ytHistory(ctx) } } }
                chip(tr("For you", "Senin için")) { show(tr("For you", "Senin için")) { OnlineMusic.forYou(ctx) } }
                // History is the whole page of everything played (online and on the phone), searched with the box above and filtered All / Online / On the phone
                chip(tr("History", "Geçmiş")) { played = true; title = tr("Everything you played", "Dinlediğin her şey") }
                chip(tr("Popular", "Popüler")) { show(tr("Popular now", "Şu an popüler")) { OnlineMusic.youtube(ctx, "top hits this week") } }
            }
            if (!ytIn) Text(tr("Sign in to YouTube in YTXTube to see your own songs here.", "Kendi şarkıların burada görünsün diye YTXTube'da YouTube'a giriş yap."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            if (ytLists.isNotEmpty()) androidx.compose.foundation.lazy.LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ytLists) { pl ->
                    // a speed dial: the square cover alone, no title under it
                    Box(Modifier.size(104.dp).clip(RoundedCornerShape(14.dp)).background(T.panel).clickable { show(pl.title) { OnlineMusic.ytPlaylist(ctx, pl.id) } }) {
                        coil.compose.AsyncImage(pl.thumbnail, pl.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        if (tab == 1 && spLists.isNotEmpty() && query.isBlank()) androidx.compose.foundation.lazy.LazyRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(spLists) { pl ->
                Column(Modifier.width(96.dp).clickable { show(pl.name) { com.umutk.pulse.extras.spotify.SpotifyApi(ctx).playlistTracks(pl.id).map { OnlineMusic.Hit(it.title, it.artist, it.image, "sp", null) } } }) {
                    coil.compose.AsyncImage(pl.image, null, Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(T.panel), contentScale = ContentScale.Crop)
                    Text(pl.name, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (tab == 1 && spLists.isEmpty() && list.isEmpty() && !busy) Text(com.umutk.pulse.extras.spotify.SpotifyApi.lastError.ifEmpty { tr("Nothing came back from Spotify. Open the log-in screen (the power icon in Spotify) and use Test the connection.", "Spotify’dan bir şey gelmedi. Giriş ekranını aç (Spotify’daki güç simgesi) ve Bağlantıyı dene’yi kullan.") }, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        Text(if (busy) tr("Loading…", "Yükleniyor…") else title, color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        // filtered as you type, at once; the online search then brings the rest
        val shown = remember(list, query) { val q = query.trim(); if (q.isEmpty()) list else list.filter { it.title.contains(q, true) || it.artist.contains(q, true) }.ifEmpty { list } }
        if (played) Box(Modifier.weight(1f)) { PlayedList(query, playedKind) { playedKind = it } } else LazyColumn(Modifier.weight(1f)) {
            if (tab == 0 && query.isBlank()) {
                // a row of covers that plays from the one tapped; the title under each is one line
                if (dial.isNotEmpty()) item(key = "dial") {
                    Column {
                        Text(tr("Speed dial", "Hızlı erişim"), color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(dial.size) { i ->
                                val h = dial[i]
                                Column(Modifier.width(88.dp).clickable { scope.launch { if (!OnlineMusic.playHere(ctx, dial, i)) OnlineMusic.play(ctx, h) } }) {
                                    coil.compose.AsyncImage(h.thumb, h.title, Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(T.panel), contentScale = ContentScale.Crop)
                                    Text(h.title, color = T.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                }
                            }
                        }
                    }
                }
                if (suggest.isNotEmpty()) item(key = "suggest") {
                    Column {
                        Text(tr("Suggestions", "Öneriler"), color = T.sub, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(suggest.size) { i ->
                                val h = suggest[i]
                                Column(Modifier.width(110.dp).clickable { scope.launch { if (!OnlineMusic.playHere(ctx, suggest, i)) OnlineMusic.play(ctx, h) } }) {
                                    coil.compose.AsyncImage(h.thumb, h.title, Modifier.size(110.dp).clip(RoundedCornerShape(16.dp)).background(T.panel), contentScale = ContentScale.Crop)
                                    Text(h.title, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                                    Text(h.artist, color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        Spacer(Modifier.size(10.dp))
                    }
                }
            }
            items(shown.size) { i ->
                val h = shown[i]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { scope.launch { if (!OnlineMusic.playHere(ctx, shown, i)) OnlineMusic.play(ctx, h) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
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


/** Everything played in the music player, newest first (the listening log): a filter for online songs or songs of the phone, and the search box above filters it as you type. */
@Composable
private fun PlayedList(query: String, kind: Int, onKind: (Int) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val all = remember { ListenLog.all(ctx) }
    val thumbs = remember { OnlineHistory.all(ctx).associate { it.id to it.thumb } }
    val q = query.trim()
    val shown = remember(all, q, kind) { all.filter { e -> (kind == 0 || (kind == 1) == e.online) && (q.isEmpty() || e.title.contains(q, true) || e.artist.contains(q, true)) } }
    val fmt = remember { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to tr("All", "Hepsi"), 1 to tr("Online", "Çevrimiçi"), 2 to tr("On the phone", "Telefonda")).forEach { (k, n) ->
                Text(n, color = if (kind == k) T.bg else T.text, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (kind == k) Look.accent else T.panel).clickable { onKind(k) }.padding(horizontal = 14.dp, vertical = 7.dp))
            }
        }
        if (shown.isEmpty()) Text(tr("Nothing here yet: what you play is saved by itself", "Henüz yok: çaldıkların kendiliğinden kaydedilir"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(shown.size) { i ->
                val e = shown[i]
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    if (e.online) {
                        val hits = shown.filter { it.online }.map { OnlineMusic.Hit(it.title, it.artist, thumbs[it.key], "yt", it.key) }
                        val at = hits.indexOfFirst { it.ytId == e.key }.coerceAtLeast(0)
                        scope.launch { if (!OnlineMusic.playHere(ctx, hits, at)) hits.getOrNull(at)?.let { OnlineMusic.play(ctx, it) } }
                    } else {
                        val local = shown.filter { !it.online }
                        MusicPlayerEngine.get(ctx).playQueue(local.map { Track(path = it.key, title = it.title, artist = it.artist) }, local.indexOfFirst { it.key == e.key }.coerceAtLeast(0))
                    }
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (e.online) coil.compose.AsyncImage(thumbs[e.key], null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(T.panel), contentScale = ContentScale.Crop)
                    else Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(T.panel), contentAlignment = Alignment.Center) { PIcon("play", T.sub, 22.dp) }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(e.title, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(e.artist, fmt.format(java.util.Date(e.at))).filter { it.isNotBlank() }.joinToString(" · "), color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
