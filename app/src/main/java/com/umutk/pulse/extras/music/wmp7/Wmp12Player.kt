package com.umutk.pulse.extras.music.wmp7

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.poweramp.displayName
import com.umutk.pulse.extras.music.shownArtist
import com.umutk.pulse.tr

private val W_TEXT = Color(0xFFE8F3FA)
private val W_SUB = Color(0xFFB5CFDF)
private val W_LINK = Color(0xFF9CD3F5)
private val W_LINE = Color(0x33FFFFFF)

/**
 * The player of Windows Media Player 12 (Windows 7), made after the real one. Two pages:
 * - Now Playing: the song and its artist at the top left, the cover large in the middle, "Show list" at the top right (when the window is wide or maximized the play
 *   list stands at the right side for good); at the foot the progress line and the glossy pill (shuffle, repeat, stop, back, play, forward, volume).
 * - Library: the back arrow and the path ("Library > Music > Album"), a search box, the tree at the left (Playlists, and Music by All music, Album, Artist, Genre, Year, Composer
 *   and Folder, as the categories of the Poweramp library), and the albums as the real library draws them: the cover, the name, and the songs with number, title, length and artist.
 */
@Composable
fun Wmp12Player(
    title: String, artist: String, album: String?, cover: ImageBitmap?, playing: Boolean, pos: Int, dur: Int,
    onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onStop: () -> Unit, onSeek: (Int) -> Unit, modifier: Modifier = Modifier,
    queue: List<Track> = emptyList(), current: Int = -1, onPick: (Int) -> Unit = {},
    library: List<Track> = emptyList(), albumCover: suspend (String) -> ImageBitmap? = { null }, onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
    playlists: List<String> = emptyList(), loadPlaylist: (String) -> List<Track> = { emptyList() },
    shuffleOn: Boolean = false, repeat: Int = 0, onShuffle: () -> Unit = {}, onRepeat: () -> Unit = {},
    maximized: Boolean = false,
) {
    var lib by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    val glow = Brush.radialGradient(listOf(Color(0xFF14677F), Color(0xFF0A2C3E), Color(0xFF040C14)), center = androidx.compose.ui.geometry.Offset(900f, 500f), radius = 1400f)
    BoxWithConstraints(modifier.fillMaxSize().background(glow)) {
        // the list stands beside the cover when there is room, or when the window is maximized
        val side = maxWidth >= 520.dp || (maximized && maxWidth >= 340.dp)
        val roomy = maxWidth >= 520.dp
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (lib) WmpLibrary(library, queue, current, albumCover, onPlayTracks, playlists, loadPlaylist, roomy, onBack = { lib = false })
                else NowPlaying(title, artist, album, cover, queue, current, onPick, side, showList, { showList = !showList })
            }
            Transport(pos, dur, playing, onPrev, onPlayPause, onNext, onStop, onSeek, shuffleOn, repeat, onShuffle, onRepeat, lib, { lib = !lib }, library.isNotEmpty())
        }
    }
}

@Composable
private fun NowPlaying(
    title: String, artist: String, album: String?, cover: ImageBitmap?, queue: List<Track>, current: Int, onPick: (Int) -> Unit,
    side: Boolean, showList: Boolean, toggleList: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, end = if (side) 10.dp else 18.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(artist.takeIf { it.isNotBlank() }, album?.takeIf { it.isNotBlank() }).joinToString(" - ")
                    if (sub.isNotEmpty()) Text(sub, color = W_TEXT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                // "Show list" with its little list mark, as at the top right of the real window
                if (queue.isNotEmpty() && !side) Row(Modifier.padding(start = 10.dp, top = 4.dp).clickable { toggleList() }, verticalAlignment = Alignment.CenterVertically) {
                    ListMark()
                    Text(if (showList) tr("Hide list", "Listeyi gizle") else tr("Show list", "Listeyi göster"), color = W_TEXT, fontSize = 12.sp, modifier = Modifier.padding(start = 5.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (showList && !side) QueueList(queue, current, onPick, Modifier.fillMaxSize())
                else {
                    // the cover is as large as the room allows, square, in a thin frame
                    val s = minOf(maxWidth, maxHeight).coerceAtLeast(60.dp)
                    Box(Modifier.size(s).clip(RoundedCornerShape(2.dp)).background(Color(0x22FFFFFF)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                        if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        // no picture of the song: the glossy note the real player shows in its place
                        else com.umutk.pulse.PIcon("music", Color(0xFFBFE3FA), s * .55f)
                    }
                }
            }
        }
        if (side) Column(Modifier.width(250.dp).fillMaxHeight().padding(end = 12.dp, top = 12.dp, bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Unsaved list", "Kaydedilmemiş liste"), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(W_LINE))
            QueueList(queue, current, onPick, Modifier.weight(1f).fillMaxWidth())
            val mins = queue.sumOf { it.durationSec.coerceAtLeast(0) } / 60
            Text(tr("${queue.size} items, $mins min", "${queue.size} öğe, $mins dk"), color = W_SUB, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun QueueList(queue: List<Track>, current: Int, onPick: (Int) -> Unit, modifier: Modifier) {
    val st = rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(current) { if (current in queue.indices) st.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(modifier, state = st) {
        itemsIndexed(queue) { i, t ->
            val on = i == current
            Row(Modifier.fillMaxWidth().background(if (on) Color(0x2E4DA6FF) else Color.Transparent).clickable { onPick(i) }.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t.displayName(), color = if (on) Color(0xFF7CC4FF) else W_TEXT, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (t.durationSec > 0) Text(len(t.durationSec), color = W_SUB, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** The page of the library: the path and the search at the top, the tree at the left, the albums (or songs) in the middle. */
@Composable
private fun WmpLibrary(
    all: List<Track>, queue: List<Track>, current: Int, albumCover: suspend (String) -> ImageBitmap?, onPlay: (List<Track>, Int) -> Unit,
    playlists: List<String>, loadPlaylist: (String) -> List<Track>, roomy: Boolean, onBack: () -> Unit,
) {
    var cat by remember { mutableStateOf("album") }
    var query by remember { mutableStateOf("") }
    val cats = listOf("songs" to tr("All music", "Tüm müzik"), "album" to tr("Album", "Albüm"), "artist" to tr("Artist", "Sanatçı"), "genre" to tr("Genre", "Tür"),
        "year" to tr("Year", "Yıl"), "composer" to tr("Composer", "Besteci"), "folder" to tr("Folder", "Klasör"))
    val groups = remember(all, cat, query) {
        val rows = if (query.isBlank()) all else all.filter { t -> t.displayName().contains(query, true) || t.shownArtist().contains(query, true) || (t.album ?: "").contains(query, true) }
        when {
            cat.startsWith("pl:") -> listOf("" to loadPlaylist(cat.removePrefix("pl:")))
            cat == "songs" -> listOf("" to rows.sortedBy { it.displayName().lowercase() })
            else -> rows.groupBy { t ->
                when (cat) {
                    "album" -> t.album?.trim().orEmpty().ifBlank { tr("Unknown album", "Bilinmeyen albüm") }
                    "artist" -> t.shownArtist()
                    "genre" -> t.genre?.trim().orEmpty().ifBlank { tr("Unknown genre", "Bilinmeyen tür") }
                    "year" -> if (t.year > 0) t.year.toString() else tr("No year", "Yıl yok")
                    "composer" -> t.composer?.trim().orEmpty().ifBlank { tr("No composer", "Besteci yok") }
                    else -> t.path.substringBeforeLast('/', "").substringAfterLast('/').ifBlank { "/" }
                }
            }.toList().sortedBy { it.first.lowercase() }
        }
    }
    val flat = remember(groups) { groups.flatMap { it.second } }
    val curPath = queue.getOrNull(current)?.path
    val catName = if (cat.startsWith("pl:")) cat.removePrefix("pl:") else cats.firstOrNull { it.first == cat }?.second.orEmpty()
    Column(Modifier.fillMaxSize()) {
        // the back arrow, the path and the search
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0x33FFFFFF)).border(1.dp, Color(0x66FFFFFF), CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("‹", color = Color.White, fontSize = 18.sp)
            }
            Text(if (cat.startsWith("pl:")) "Library › Playlists › $catName" else "Library › Music › $catName", color = W_TEXT, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Row(Modifier.width(if (roomy) 160.dp else 110.dp).height(24.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x33FFFFFF)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text(tr("Search", "Ara"), color = W_SUB, fontSize = 11.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 12.sp), cursorBrush = SolidColor(Color.White), modifier = Modifier.fillMaxWidth())
                }
                com.umutk.pulse.PIcon("search", W_SUB, 13.dp)
            }
        }
        // the toolbar: Play all, Shuffle all, and how many songs there are
        Row(Modifier.fillMaxWidth().background(Color(0x22FFFFFF)).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("Play all", "Tümünü çal") + " ▾", color = W_TEXT, fontSize = 12.sp, modifier = Modifier.clickable { if (flat.isNotEmpty()) onPlay(flat, 0) })
            Text(tr("Shuffle all", "Tümünü karıştır") + " ▾", color = W_TEXT, fontSize = 12.sp, modifier = Modifier.clickable { if (flat.isNotEmpty()) onPlay(flat.shuffled(), 0) })
            Spacer(Modifier.weight(1f))
            Text(tr("${flat.size} songs", "${flat.size} şarkı"), color = W_SUB, fontSize = 11.sp)
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // the tree: Library, Playlists, Music with its categories
            val treeMod = if (roomy) Modifier.width(150.dp).fillMaxHeight().verticalScroll(rememberScrollState()) else Modifier
            if (roomy) Column(treeMod.padding(top = 6.dp, start = 6.dp, end = 4.dp)) {
                TreeRow(tr("Library", "Kitaplık"), false, 0, bold = true) {}
                TreeRow(tr("Playlists", "Çalma listeleri"), false, 1) {}
                playlists.forEach { n -> TreeRow(n, cat == "pl:$n", 2) { cat = "pl:$n" } }
                TreeRow(tr("Music", "Müzik"), false, 1) {}
                cats.forEach { (k, n) -> TreeRow(n, cat == k, 2) { cat = k } }
                Spacer(Modifier.height(10.dp))
            }
            if (roomy) Box(Modifier.width(1.dp).fillMaxHeight().background(W_LINE))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                // narrow window: the tree is a row of the categories at the top
                if (!roomy) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (cats + playlists.map { "pl:$it" to it }).forEach { (k, n) ->
                        Text(n, color = if (cat == k) Color.White else W_SUB, fontSize = 12.sp, fontWeight = if (cat == k) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(if (cat == k) Color(0x554DA6FF) else Color(0x22FFFFFF)).clickable { cat = k }.padding(horizontal = 9.dp, vertical = 4.dp))
                    }
                }
                if (flat.isEmpty()) Text(tr("Nothing here", "Burada bir şey yok"), color = W_SUB, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                LazyColumn(Modifier.fillMaxSize()) {
                    var offset = 0
                    groups.forEach { (name, tracks) ->
                        val start = offset; offset += tracks.size
                        if (name.isNotEmpty()) item(key = "g_$name") {
                            Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp)) {
                                Text(name, color = Color(0xFF8CC9F2), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(Color(0x558CC9F2)))
                            }
                        }
                        item(key = "b_$name") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                                if (cat != "songs" && !cat.startsWith("pl:")) {
                                    val c by produceState<ImageBitmap?>(null, tracks.first().path) { value = albumCover(tracks.first().path) }
                                    Box(Modifier.padding(end = 8.dp, top = 2.dp).size(if (roomy) 76.dp else 56.dp).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                                        val b = c
                                        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else com.umutk.pulse.PIcon("music", Color(0xFF9CD3F5), 26.dp)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    tracks.forEachIndexed { i, t ->
                                        val on = t.path == curPath
                                        Row(Modifier.fillMaxWidth().background(if (on) Color(0x2E4DA6FF) else Color.Transparent).clickable { onPlay(flat, start + i) }.padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${i + 1}", color = W_SUB, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                                            Text(t.displayName(), color = if (on) Color(0xFF7CC4FF) else Color.White, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                            if (roomy) Text(t.shownArtist(), color = W_SUB, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(100.dp).padding(horizontal = 4.dp))
                                            if (t.durationSec > 0) Text(len(t.durationSec), color = W_SUB, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeRow(text: String, selected: Boolean, depth: Int, bold: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)).background(if (selected) Color(0x554DA6FF) else Color.Transparent).clickable(enabled = depth == 2) { onClick() }.padding(start = (depth * 12).dp, top = 3.dp, bottom = 3.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = if (selected) Color.White else if (depth == 2) W_TEXT else W_SUB, fontSize = 12.sp, fontWeight = if (bold || selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The thin progress line and the pill of controls; the same at the foot of both pages. */
@Composable
private fun Transport(
    pos: Int, dur: Int, playing: Boolean, onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onStop: () -> Unit, onSeek: (Int) -> Unit,
    shuffleOn: Boolean, repeat: Int, onShuffle: () -> Unit, onRepeat: () -> Unit, lib: Boolean, toggleLib: () -> Unit, hasLib: Boolean,
) {
    val vctx = androidx.compose.ui.platform.LocalContext.current
    val am = remember { vctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val vmax = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / vmax) }
    var drag by remember { mutableFloatStateOf(-1f) }
    val frac = if (drag >= 0f) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 8.dp)) {
        // the progress line: a tap or a slide seeks
        Box(
            Modifier.fillMaxWidth().height(16.dp)
                .pointerInput(dur) { detectTapGestures { o -> if (dur > 0) onSeek((o.x / size.width * dur).toInt().coerceIn(0, dur)) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { if (dur > 0 && drag >= 0f) onSeek((drag * dur).toInt()); drag = -1f }, onDragCancel = { drag = -1f }) { c, _ -> drag = (c.position.x / size.width).coerceIn(0f, 1f) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF)))
            Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.001f)).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF2B7FD6), Color(0xFF8CD0FF)))))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(mmss(pos), color = W_TEXT, fontSize = 11.sp, modifier = Modifier.width(36.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color(0x77000000)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(28.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundMark(Icons.Rounded.Shuffle, shuffleOn, onShuffle)
                RoundMark(if (repeat == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, repeat != 0, onRepeat)
                WmpFlat(Icons.Rounded.Stop, onStop)
                WmpFlat(Icons.Rounded.SkipPrevious, onPrev)
                // the glossy blue button: a light top that sinks into deep blue, a white ring and a glow
                Box(
                    Modifier.size(50.dp).shadow(10.dp, CircleShape, ambientColor = Color(0xFF2B8CFF), spotColor = Color(0xFF2B8CFF))
                        .clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF7FC4FF), Color(0xFF1E6FD0), Color(0xFF0B3C8A))))
                        .border(2.dp, Color(0xCCFFFFFF), CircleShape).clickable { onPlayPause() },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(width = 32.dp, height = 16.dp).clip(RoundedCornerShape(50)).background(Color(0x55FFFFFF)))
                    Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
                WmpFlat(Icons.Rounded.SkipNext, onNext)
                Icon(Icons.Rounded.VolumeUp, null, tint = Color(0xFF8CC9F2), modifier = Modifier.size(18.dp))
                // the volume: a thin slider with a round thumb, a tap or a slide sets the phone's media volume
                Box(
                    Modifier.width(48.dp).height(24.dp)
                        .pointerInput(Unit) { detectTapGestures { o -> vol = (o.x / size.width).coerceIn(0f, 1f); am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (vol * vmax).toInt(), 0) } }
                        .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> vol = (c.position.x / size.width).coerceIn(0f, 1f); am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (vol * vmax).toInt(), 0) } },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF)))
                    Box(Modifier.fillMaxWidth(vol.coerceAtLeast(0.001f)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF8CD0FF)))
                    BoxWithConstraints(Modifier.fillMaxWidth()) { Box(Modifier.padding(start = (maxWidth - 9.dp) * vol).size(9.dp).clip(CircleShape).background(Color(0xFFBFE3FA))) }
                }
            }
            Text(mmss(dur), color = W_TEXT, fontSize = 11.sp, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
            // the switch between the two pages, at the right of the pill as in the real window
            if (hasLib) Box(Modifier.padding(start = 4.dp).size(26.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)).clickable { toggleLib() }, contentAlignment = Alignment.Center) {
                if (lib) com.umutk.pulse.PIcon("play", W_TEXT, 15.dp) else com.umutk.pulse.PIcon("music", W_TEXT, 15.dp)
            }
        }
    }
}

/** The small round buttons at the left of the pill (shuffle, repeat); lit when on. */
@Composable
private fun RoundMark(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(24.dp).clip(CircleShape).background(if (on) Color(0x664DA6FF) else Color(0x22FFFFFF)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on) Color.White else Color(0xFF9CC4DE), modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun WmpFlat(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(width = 36.dp, height = 26.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color(0xFFDCEBF5), modifier = Modifier.size(18.dp))
    }
}

/** The little list mark (three lines in a frame) beside "Show list". */
@Composable
private fun ListMark() {
    Column(Modifier.size(width = 15.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3B8FE0)).padding(horizontal = 3.dp, vertical = 3.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        repeat(3) { Box(Modifier.fillMaxWidth().height(1.5.dp).background(Color.White)) }
    }
}

private fun mmss(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
private fun len(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)
