package com.umutk.pulse.extras.music.looks

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PSlider
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.CoverCarousel
import com.umutk.pulse.extras.music.RecommendationHome
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.neighbourCovers
import com.umutk.pulse.extras.music.rememberTrackArt
import com.umutk.pulse.extras.music.trackArt
import com.umutk.pulse.onAccent
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

private val PBG = Color(0xFF0B0B0F)
private val PANEL = Color(0xFF1A1A22)
private val TXT = Color(0xFFF2F2F7)
private val SUB = Color(0xFFA0A0B0)
private fun Track.name() = title?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').substringBeforeLast('.')
private fun Track.artistName() = artist?.takeIf { it.isNotBlank() } ?: tr("Unknown artist", "Bilinmeyen sanatçı")
private fun Track.folder() = path.substringBeforeLast('/', "")
private fun Track.albumName() = album?.takeIf { it.isNotBlank() } ?: folder().substringAfterLast('/').ifEmpty { tr("Unknown album", "Bilinmeyen albüm") }
private fun fmt(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Cover colour: 4-means on a 24x24 copy, the biggest reasonably saturated, not too dark or bright cluster wins. */
internal fun coverColor(bm: ImageBitmap, fallback: Color): Color = runCatching {
    var src = bm.asAndroidBitmap()
    if (src.config == Bitmap.Config.HARDWARE) src = src.copy(Bitmap.Config.ARGB_8888, false)
    val px = IntArray(24 * 24).also { Bitmap.createScaledBitmap(src, 24, 24, true).getPixels(it, 0, 24, 0, 0, 24, 24) }
    val k = 4
    val cen = Array(k) { px[it * px.size / k].let { c -> floatArrayOf(android.graphics.Color.red(c).toFloat(), android.graphics.Color.green(c).toFloat(), android.graphics.Color.blue(c).toFloat()) } }
    val owner = IntArray(px.size)
    repeat(6) {
        px.forEachIndexed { i, c ->
            owner[i] = (0 until k).minByOrNull { j ->
                val dr = android.graphics.Color.red(c) - cen[j][0]; val dg = android.graphics.Color.green(c) - cen[j][1]; val db = android.graphics.Color.blue(c) - cen[j][2]
                dr * dr + dg * dg + db * db
            }!!
        }
        for (j in 0 until k) {
            val m = px.indices.filter { owner[it] == j }; if (m.isEmpty()) continue
            cen[j][0] = m.map { android.graphics.Color.red(px[it]) }.average().toFloat()
            cen[j][1] = m.map { android.graphics.Color.green(px[it]) }.average().toFloat()
            cen[j][2] = m.map { android.graphics.Color.blue(px[it]) }.average().toFloat()
        }
    }
    val hsv = FloatArray(3)
    val best = (0 until k).maxByOrNull { j ->
        val n = owner.count { it == j }
        android.graphics.Color.RGBToHSV(cen[j][0].toInt(), cen[j][1].toInt(), cen[j][2].toInt(), hsv)
        if (hsv[2] < .2f || hsv[2] > .95f) 0f else n * (.25f + hsv[1])
    }!!
    Color(cen[best][0].toInt(), cen[best][1].toInt(), cen[best][2].toInt())
}.getOrDefault(fallback)

@Composable
private fun Art(t: Track?, size: androidx.compose.ui.unit.Dp?, tint: Color, modifier: Modifier = Modifier) {
    val bm = rememberTrackArt(t)
    val m = (if (size != null) modifier.size(size) else modifier).clip(RoundedCornerShape(if (size != null && size < 80.dp) 12.dp else 32.dp))
    if (bm != null) Image(bm, null, m, contentScale = ContentScale.Crop)
    else Box(m.background(tint.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = tint, modifier = Modifier.fillMaxSize(.45f)) }
}

private class Lists(val ctx: android.content.Context) {
    private val sp = ctx.getSharedPreferences("pulse_mp_playlists", 0)
    fun all(): Map<String, List<String>> = sp.all.mapValues { (it.value as? String).orEmpty().split('\n').filter { p -> p.isNotEmpty() } }
    fun save(name: String, paths: List<String>) = sp.edit().putString(name, paths.joinToString("\n")).apply()
    fun delete(name: String) = sp.edit().remove(name).apply()
}

@Composable
fun MaterialPlayer(engine: MusicPlayerEngine, library: List<Track>, modifier: Modifier = Modifier, artBackground: Boolean = false, onNowPlaying: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    val st by engine.state.collectAsState()
    val cur = st.current
    val art = rememberTrackArt(cur)
    val (artPrev, artNext) = neighbourCovers(st.queue, st.index) { trackArt(ctx, it) }
    // AMOLED is pure black; the other Material looks keep the deep blue-grey
    val pbg = if (T.id == "amoled") Color.Black else PBG
    val base = Look.accent.let { if (it.luminance() < .35f) Color(0xFFB9C3FF) else it }
    // always a dark player, whatever the look or light mode; the cover colour is the accent
    val accent = remember(art, base) { art?.let { coverColor(it, base) }?.let { c -> if (c.luminance() < .3f) androidx.compose.ui.graphics.lerp(c, Color.White, .45f) else c } ?: base }
    val dyn = accent
    val tabs = listOf(tr("All songs", "Tüm şarkılar"),tr("Albums", "Albümler"), tr("Artists", "Sanatçılar"), tr("Playlists", "Listeler"), tr("Folders", "Klasörler"))
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var showNow by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val lists = remember { Lists(ctx) }
    var playlists by remember { mutableStateOf(lists.all()) }

    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(engine.player.shuffleModeEnabled) }
    var repeat by remember { mutableIntStateOf(engine.player.repeatMode) }
    LaunchedEffect(st.isPlaying, st.current) {
        while (true) { if (!dragging) pos = engine.positionMs; dur = engine.durationMs; if (!st.isPlaying) break; delay(400) }
    }

    // sleep timer: minutes left, 0 = off
    var sleepLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(sleepLeft > 0) {
        while (sleepLeft > 0) { delay(60_000); sleepLeft--; if (sleepLeft == 0) engine.pause() }
    }

    BackHandler(showNow || group != null) { if (showQueue) showQueue = false else if (showNow) showNow = false else group = null }

    val q = query.trim().lowercase()
    val songs = remember(library, q) { if (q.isEmpty()) library else library.filter { it.name().lowercase().contains(q) || it.folder().lowercase().contains(q) } }

    DisposableEffect(showNow) { onNowPlaying(showNow); onDispose { onNowPlaying(false) } }

    Box(modifier.fillMaxSize().background(pbg)) {
        Column(Modifier.fillMaxSize()) {
            // search
            Row(Modifier.fillMaxWidth().padding(12.dp, 8.dp).clip(RoundedCornerShape(28.dp)).background(PANEL).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = SUB, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(tr("Search", "Ara"), color = SUB, fontSize = 15.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = TXT, fontSize = 15.sp), cursorBrush = SolidColor(accent), modifier = Modifier.fillMaxWidth())
                }
                if (query.isNotEmpty()) Icon(Icons.Rounded.Close, null, tint = SUB, modifier = Modifier.size(20.dp).clickable { query = "" })
            }
            // tabs
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                tabs.forEachIndexed { i, s ->
                    Text(s, color = if (tab == i) onAccent(accent) else TXT, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(50)).background(if (tab == i) accent else Color.Transparent)
                            .clickable { tab = i; group = null }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
            // content
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val groups: Map<String, List<Track>> = when (tab) {
                    1 -> songs.groupBy { it.albumName() }
                    2 -> songs.groupBy { it.artistName() }
                    4 -> songs.groupBy { it.folder() }
                    3 -> { val byPath = library.associateBy { it.path }; playlists.mapValues { (_, ps) -> ps.mapNotNull { byPath[it] } } }
                    else -> emptyMap()
                }
                val g = group
                if (tab == 0 || g != null) {
                    val shown = if (tab == 0) songs else groups[g ?: ""].orEmpty()
                    Column {
                        if (g != null) Row(Modifier.fillMaxWidth().clickable { group = null }.padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ArrowBack, null, tint = TXT); Spacer(Modifier.width(12.dp))
                            Text(if (tab == 4) g.substringAfterLast('/') else g, color = TXT, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Shuffle, null, tint = accent, modifier = Modifier.clickable { if (shown.isNotEmpty()) engine.playQueue(shown.shuffled()) })
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (tab == 0 && g == null && q.isEmpty()) item { RecommendationHome(library, cur, TXT, SUB, PANEL, accent, round = false, onPlay = { engine.playQueue(library, it) }) }
                            itemsIndexed(shown, key = { i, t -> "$i${t.path}" }) { i, t ->
                                val on = cur?.path == t.path
                                Row(Modifier.fillMaxWidth().clickable { engine.playQueue(shown, i) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Art(t, 44.dp, accent)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(t.name(), color = if (on) accent else TXT, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(t.albumName(), color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Rounded.PlaylistAdd, null, tint = SUB, modifier = Modifier.clickable { engine.addToQueue(t) }.padding(6.dp))
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (tab == 3) item {
                            Row(Modifier.fillMaxWidth().clickable {
                                val qs = engine.queue(); if (qs.isNotEmpty()) { lists.save("Mix ${playlists.size + 1}", qs.map { it.path }); playlists = lists.all() }
                            }.padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Add, null, tint = accent); Spacer(Modifier.width(12.dp))
                                Text(tr("Save queue", "Kuyruğu kaydet"), color = accent, fontSize = 15.sp)
                            }
                        }
                        itemsIndexed(groups.keys.toList().sorted()) { _, k ->
                            val ts = groups[k].orEmpty()
                            Row(Modifier.fillMaxWidth().clickable { group = k }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Art(ts.firstOrNull(), 52.dp, accent)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(if (tab == 4) k.substringAfterLast('/') else k, color = TXT, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${ts.size}", color = SUB, fontSize = 12.sp)
                                }
                                if (tab == 3) Icon(Icons.Rounded.Delete, null, tint = SUB, modifier = Modifier.clickable { lists.delete(k); playlists = lists.all() }.padding(6.dp))
                            }
                        }
                    }
                }
            }
            // mini player
            if (cur != null) Row(Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(24.dp)).background(dyn.copy(alpha = .28f)).clickable { showNow = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Art(cur, 44.dp, dyn)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(cur.name(), color = TXT, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cur.artistName(), color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = TXT, modifier = Modifier.size(40.dp).clickable { engine.togglePlayPause() }.padding(6.dp))
                Icon(Icons.Rounded.SkipNext, null, tint = TXT, modifier = Modifier.size(40.dp).clickable { engine.next() }.padding(6.dp))
            }
        }

        if (showNow && cur != null) {
            Box(Modifier.fillMaxSize().background(pbg)) {
                // one layer: the blurred cover is the background when the art switch is on, nothing extra is drawn otherwise
                if (artBackground && art != null) Image(art, null, Modifier.fillMaxSize().blur(48.dp).alpha(.55f), contentScale = ContentScale.Crop)
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = TXT, modifier = Modifier.size(36.dp).clickable { showQueue = false; showNow = false })
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.Bedtime, null, tint = if (sleepLeft > 0) dyn else TXT, modifier = Modifier.clickable { sleepLeft = when (sleepLeft) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 } }.padding(6.dp))
                        if (sleepLeft > 0) Text("$sleepLeft", color = dyn, fontSize = 13.sp)
                        Icon(Icons.Rounded.QueueMusic, null, tint = if (showQueue) dyn else TXT, modifier = Modifier.clickable { showQueue = !showQueue }.padding(6.dp))
                    }
                    if (showQueue) {
                        val qs = st.queue
                        val ls = rememberLazyListState()
                        LazyColumn(Modifier.weight(1f).fillMaxWidth(), ls) {
                            itemsIndexed(qs, key = { i, t -> "$i${t.path}" }) { i, t ->
                                Row(Modifier.fillMaxWidth().clickable { engine.playAt(i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name(), color = if (i == st.index) dyn else TXT, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (i > 0) Icon(Icons.Rounded.KeyboardArrowUp, null, tint = SUB, modifier = Modifier.clickable { engine.moveInQueue(i, i - 1) }.padding(6.dp))
                                    if (i < qs.lastIndex) Icon(Icons.Rounded.KeyboardArrowDown, null, tint = SUB, modifier = Modifier.clickable { engine.moveInQueue(i, i + 1) }.padding(6.dp))
                                    Icon(Icons.Rounded.Close, null, tint = SUB, modifier = Modifier.clickable { engine.removeFromQueue(i) }.padding(6.dp))
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                        CoverCarousel(art, artPrev, artNext, st.index > 0, st.index < st.queue.lastIndex, { engine.previous() }, { engine.next() },
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(32.dp))) { a ->
                            if (a != null) Image(a, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Box(Modifier.fillMaxSize().background(dyn.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = dyn, modifier = Modifier.fillMaxSize(.45f)) }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Text(cur.name(), color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(cur.artistName(), color = SUB, fontSize = 15.sp, maxLines = 1)
                    com.umutk.pulse.extras.lyrics.LiveLyricsLine(cur, pos, Modifier.fillMaxWidth().padding(top = 8.dp))
                    val d = dur.coerceAtLeast(1)
                    PSlider(pos.coerceIn(0, d).toFloat(), { dragging = true; pos = it.toInt() }, 0f..d.toFloat()) { engine.seekTo(pos); dragging = false }
                    Row(Modifier.fillMaxWidth()) { Text(fmt(pos), color = SUB, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(fmt(dur), color = SUB, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Shuffle, null, tint = if (shuffle) dyn else SUB, modifier = Modifier.size(44.dp).clickable { shuffle = !shuffle; engine.player.shuffleModeEnabled = shuffle }.padding(10.dp))
                        Icon(Icons.Rounded.SkipPrevious, null, tint = TXT, modifier = Modifier.size(48.dp).clickable { engine.previous() }.padding(8.dp))
                        Box(Modifier.size(68.dp).clip(CircleShape).background(dyn).clickable { engine.togglePlayPause() }, contentAlignment = Alignment.Center) {
                            Icon(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = onAccent(dyn), modifier = Modifier.size(36.dp))
                        }
                        Icon(Icons.Rounded.SkipNext, null, tint = TXT, modifier = Modifier.size(48.dp).clickable { engine.next() }.padding(8.dp))
                        Icon(if (repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, null, tint = if (repeat != 0) dyn else SUB,
                            modifier = Modifier.size(44.dp).clickable { repeat = when (repeat) { 0 -> 2; 2 -> 1; else -> 0 }; engine.player.repeatMode = repeat }.padding(10.dp))
                    }
                }
            }
        }
    }
}
