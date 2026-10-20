package com.umutk.pulse.extras.music.poweramp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import com.umutk.pulse.extras.playlists.OnlinePlaylistsHub
import com.umutk.pulse.extras.whitenoise.WhiteNoiseHub
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.lyrics.LyricHit
import com.umutk.pulse.extras.lyrics.Lyrics
import com.umutk.pulse.extras.lyrics.LyricsResult
import com.umutk.pulse.extras.lyrics.LyricsView
import com.umutk.pulse.extras.music.RecommendationHome
import com.umutk.pulse.extras.music.rememberTrackArt
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private sealed interface Lib {
    object Root : Lib
    object Songs : Lib
    object Folders : Lib
    object Stats : Lib
    object Favs : Lib
    /** Hidden categories (composers, folder tree), opened from the bottom row of the library list. */
    object More : Lib
    /** Full pages that replace the library (white noise hub, online playlists hub). */
    object WhiteNoise : Lib
    object AllSounds : Lib
    object OnlinePlaylists : Lib
    /** 0 most played, 1 recently played, 2 last added */
    class Quick(val kind: Int) : Lib
    object Playlists : Lib
    class Playlist(val name: String) : Lib
    class Folder(val name: String) : Lib
    class Tree(val dir: String) : Lib
    /** kind 0 albums, 1 artists, 2 genres, 3 years, 4 composers: the list of groups. */
    class Cat(val kind: Int) : Lib
    class Group(val kind: Int, val key: String) : Lib
}

private val quickNames get() = listOf(tr("Most played", "En çok çalınan"), tr("Recently played", "Son çalınan"), tr("Last added", "Son eklenen"))

private fun groupKey(t: Track, kind: Int): String? = when (kind) {
    0 -> t.album; 1 -> t.artist; 2 -> t.genre; 3 -> t.year.takeIf { it > 0 }?.toString(); else -> t.composer
}?.takeIf { it.isNotBlank() }

private val catNames get() = listOf(tr("Albums", "Albümler"), tr("Artists", "Sanatçılar"), tr("Genres", "Türler"), tr("Years", "Yıllar"), tr("Composers", "Besteciler"))
private val catIcons = listOf(Icons.Rounded.Album, Icons.Rounded.Person, Icons.Rounded.Piano, Icons.Rounded.Event, Icons.Rounded.Edit)

private fun dirOf(path: String) = path.substringBeforeLast('/', "")

private fun commonDir(a: String, b: String): String {
    val p = a.commonPrefixWith(b)
    return when {
        a == b -> a
        p == a && b.startsWith("$a/") -> a
        p == b && a.startsWith("$b/") -> b
        else -> p.substringBeforeLast('/', "")
    }
}

/**
 * Library: a category list (all songs, folders, folder tree) with coloured round icons, as in Poweramp.
 * Tapping a song starts the whole library from it.
 */
@Composable
fun LibraryScreen(
    tracks: List<Track>,
    current: Track?,
    style: PowerampStyle,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    queue: List<Track> = emptyList(),
    onPlayTracks: (List<Track>, Int) -> Unit = { _, _ -> },
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var sortBy by remember { mutableIntStateOf(0) } // 0 title, 1 folder, 2 library order
    var node by remember { mutableStateOf<Lib>(Lib.Root) }
    val fg = style.onBackdrop
    // the tree starts at the longest directory shared by every file
    val root = remember(tracks) {
        tracks.map { dirOf(it.path) }.reduceOrNull(::commonDir) ?: ""
    }
    BackHandler(enabled = node != Lib.Root) {
        node = when (val n = node) {
            is Lib.Tree -> if (n.dir.length > root.length) Lib.Tree(dirOf(n.dir)) else Lib.Root
            is Lib.Folder -> Lib.Folders
            is Lib.Playlist -> Lib.Playlists
            is Lib.Group -> Lib.Cat(n.kind)
            else -> Lib.Root
        }
    }
    val title = when (val n = node) {
        Lib.Root -> tr("Library", "Kütüphane")
        Lib.Songs -> tr("All songs", "Tüm şarkılar")
        Lib.Folders -> tr("Folders", "Klasörler")
        Lib.Stats -> tr("Statistics", "İstatistikler")
        Lib.Favs -> tr("Favourites", "Favoriler")
        Lib.More -> tr("More categories", "Daha fazla kategori")
        is Lib.Quick -> quickNames[n.kind]
        Lib.WhiteNoise -> tr("White noise", "Beyaz gürültü")
        Lib.AllSounds -> tr("All sounds", "Tüm sesler")
        Lib.OnlinePlaylists -> tr("Online playlists", "Çevrimiçi çalma listeleri")
        Lib.Playlists -> tr("Playlists", "Çalma listeleri")
        is Lib.Playlist -> n.name
        is Lib.Cat -> catNames[n.kind]
        is Lib.Group -> n.key
        is Lib.Folder -> n.name
        is Lib.Tree -> n.dir.substringAfterLast('/').ifEmpty { tr("Folders", "Klasörler") }
    }
    // these hubs have their own header and back button, so they replace the library page
    when (node) {
        Lib.WhiteNoise, Lib.AllSounds -> { Box(modifier.fillMaxSize()) { WhiteNoiseHub(onBack = { node = Lib.Root }) }; return@LibraryScreen }
        Lib.OnlinePlaylists -> { Box(modifier.fillMaxSize()) { OnlinePlaylistsHub(onBack = { node = Lib.Root }) }; return@LibraryScreen }
        else -> Unit
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (node != Lib.Root) {
                IconButton(onClick = { node = Lib.Root }) { Icon(Icons.Rounded.ArrowBack, tr("Back", "Geri"), tint = fg) }
            }
            Text(title, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (node == Lib.Songs) {
                IconButton(onClick = { sortBy = (sortBy + 1) % 4 }) { Icon(Icons.Rounded.SortByAlpha, tr("Sort", "Sırala"), tint = if (sortBy == 2) style.textDim else style.accent) }
            }
        }
        val catKey: Any? = when (val n = node) {
            is Lib.Cat -> n.kind; is Lib.Group -> n.kind; Lib.Folders, is Lib.Folder -> "folders"; is Lib.Tree -> "tree"; else -> null
        }
        if (node == Lib.Root || catKey != null) CatChips(catKey, root, style) { node = it }
        when (val n = node) {
            Lib.Root -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                val cats = listOf(
                    Triple(Icons.Rounded.TrendingUp, Color(0xFFD9695B), Lib.Quick(0) to quickNames[0]),
                    Triple(Icons.Rounded.History, Color(0xFF5BA0D9), Lib.Quick(1) to quickNames[1]),
                    Triple(Icons.Rounded.NewReleases, Color(0xFF6BC08A), Lib.Quick(2) to quickNames[2]),
                    Triple(Icons.Rounded.Air, Color(0xFF4FA3C7), Lib.WhiteNoise to tr("White noise", "Beyaz gürültü")),
                    Triple(Icons.Rounded.Cloud, Color(0xFF7A8FD9), Lib.OnlinePlaylists to tr("Online playlists", "Çevrimiçi çalma listeleri")),
                    Triple(Icons.Rounded.Album, Color(0xFFD96B6B), Lib.Cat(0) to catNames[0]),
                    Triple(Icons.Rounded.Person, Color(0xFFD98A5B), Lib.Cat(1) to catNames[1]),
                    Triple(Icons.Rounded.Piano, Color(0xFF9B6BD9), Lib.Cat(2) to catNames[2]),
                    Triple(Icons.Rounded.Event, Color(0xFF5BA0D9), Lib.Cat(3) to catNames[3]),
                    Triple(Icons.Rounded.Folder, Color(0xFF4A7BD6), Lib.Folders to tr("Folders", "Klasörler")),
                    Triple(Icons.Rounded.QueueMusic, Color(0xFF8E5BD9), Lib.Playlists to tr("Playlists", "Çalma listeleri")),
                    Triple(Icons.Rounded.Star, Color(0xFFD9A05B), Lib.Favs to tr("Favourites", "Favoriler")),
                    Triple(Icons.Rounded.BarChart, Color(0xFF5BB5A8), Lib.Stats to tr("Statistics", "İstatistikler")),
                )
                itemsIndexed(cats) { _, (ic, col, p) -> CatRow(ic, col, p.second, style) { node = p.first } }
                // the suggestions sit at the bottom of the library, under the categories
                item { RecommendationHome(tracks, current, style.onBackdrop, style.textDim, style.chip, style.accent, round = true, onPlay = onPlay) }
            }
            is Lib.Cat -> {
                val groups = remember(tracks, n.kind) {
                    tracks.mapNotNull { t -> groupKey(t, n.kind)?.let { it to t } }.groupBy({ it.first }, { it.second })
                        .toList().let { if (n.kind == 3) it.sortedByDescending { g -> g.first } else it.sortedBy { g -> g.first.lowercase() } }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(groups) { _, (name, list) -> CatRow(catIcons[n.kind], Color(0xFF6E8BE8), name, style, count = list.size, art = list.firstOrNull()) { node = Lib.Group(n.kind, name) } }
                }
            }
            is Lib.Group -> SongList(tracks.withIndex().filter { groupKey(it.value, n.kind) == n.key }, current, style, onPlay)
            Lib.Songs -> {
                val items = remember(tracks, sortBy) {
                    val l = tracks.withIndex().toList()
                    when (sortBy) {
                        0 -> l.sortedBy { it.value.displayName().lowercase() }
                        1 -> l.sortedBy { it.value.folderName().lowercase() }
                        // newest year first; tracks without a year tag go last
                        3 -> l.sortedWith(compareBy<IndexedValue<Track>> { if (it.value.year > 0) 0 else 1 }.thenByDescending { it.value.year })
                        else -> l
                    }
                }
                SongList(items, current, style, onPlay)
            }
            Lib.Stats -> StatsPage(style)
            Lib.More -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                val hidden = listOf(
                    Triple(Icons.Rounded.Edit, Color(0xFF6BC08A), Lib.Cat(4) to catNames[4]),
                    Triple(Icons.Rounded.AccountTree, Color(0xFF5B63D9), Lib.Tree(root) to tr("Folder tree", "Klasör ağacı")),
                )
                itemsIndexed(hidden) { _, (ic, col, p) -> CatRow(ic, col, p.second, style) { node = p.first } }
            }
            Lib.WhiteNoise, Lib.AllSounds, Lib.OnlinePlaylists -> Unit // shown above
            Lib.Playlists -> PlaylistsPage(queue, style) { node = Lib.Playlist(it) }
            is Lib.Playlist -> {
                val l = remember(n.name) { com.umutk.pulse.extras.music.Playlists.load(ctx, n.name) }
                SongList(l.withIndex().toList(), current, style) { onPlayTracks(l, it) }
            }
            is Lib.Quick -> {
                val ix = remember(n.kind, tracks) {
                    val st = com.umutk.pulse.extras.music.stats.MusicStats
                    val byPath = tracks.withIndex().associateBy { it.value.path }
                    when (n.kind) {
                        0 -> st.mostPlayed(ctx, 100).mapNotNull { byPath[it.path] }
                        1 -> st.recent(ctx, 100).mapNotNull { byPath[it.path] }
                        else -> tracks.withIndex().filter { it.value.dateAdded > 0 }.sortedByDescending { it.value.dateAdded }.take(100)
                    }
                }
                SongList(ix, current, style, onPlay)
            }
            Lib.Favs -> {
                val fav = remember { com.umutk.pulse.extras.music.stats.MusicStats.favourites(ctx) }
                SongList(tracks.withIndex().filter { it.value.path in fav }, current, style, onPlay)
            }
            Lib.Folders -> {
                val groups = remember(tracks) { tracks.groupBy { it.folderName() }.toList().sortedBy { it.first.lowercase() } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(groups) { _, (name, list) ->
                        CatRow(Icons.Rounded.Folder, Color(0xFF4A7BD6), name, style, count = list.size) { node = Lib.Folder(name) }
                    }
                }
            }
            is Lib.Folder -> SongList(tracks.withIndex().filter { it.value.folderName() == n.name }, current, style, onPlay)
            is Lib.Tree -> {
                val dirs = remember(tracks, n.dir) {
                    tracks.map { dirOf(it.path) }.filter { it.startsWith(n.dir + "/") }
                        .map { n.dir + "/" + it.removePrefix(n.dir + "/").substringBefore('/') }.distinct().sorted()
                }
                val here = remember(tracks, n.dir) { tracks.withIndex().filter { dirOf(it.value.path) == n.dir } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(dirs) { _, d -> CatRow(Icons.Rounded.Folder, Color(0xFF4A7BD6), d.substringAfterLast('/'), style) { node = Lib.Tree(d) } }
                    itemsIndexed(here) { _, (i, t) -> TrackRow(t, t == current, style, onClick = { onPlay(i) }) }
                }
            }
        }
    }
}

/** Category switcher: albums, artists, genres, years, composers, folders and the folder tree in one horizontal row. */
@Composable
private fun CatChips(selected: Any?, root: String, style: PowerampStyle, onNode: (Lib) -> Unit) {
    val chips = buildList<Pair<Any, Pair<String, Lib>>> {
        catNames.forEachIndexed { k, name -> add(k to (name to Lib.Cat(k))) }
        add("folders" to (tr("Folders", "Klasörler") to Lib.Folders))
        add("tree" to (tr("Folder tree", "Klasör ağacı") to Lib.Tree(root)))
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { (key, p) ->
            val on = key == selected
            Text(p.first, color = if (on) style.onAccent else style.onBackdrop, fontSize = 14.sp, maxLines = 1,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(CircleShape).background(if (on) style.accent else style.chip).clickable { onNode(p.second) }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun SongList(items: List<IndexedValue<Track>>, current: Track?, style: PowerampStyle, onPlay: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        itemsIndexed(items) { _, (i, t) -> TrackRow(t, t == current, style, onClick = { onPlay(i) }) }
    }
}

@Composable
private fun CatRow(icon: ImageVector, color: Color, label: String, style: PowerampStyle, count: Int = -1, art: Track? = null, open: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cover = rememberTrackArt(art)
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
            if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(icon, null, tint = Color.White.copy(alpha = .92f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, color = style.onBackdrop, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count >= 0) Text("$count", color = style.textDim, fontSize = 13.sp)
        if (open) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = style.textDim)
    }
}

/**
 * Search over the library by title and path. Results keep their library index when played.
 * The lyrics chip switches the results to an online lyrics search of the same text (the lyrics search screen's rule).
 */
@Composable
fun SearchScreen(library: List<Track>, current: Track?, style: PowerampStyle, onPlay: (Int) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var lyricsOn by remember { mutableStateOf(false) }
    var lyricHits by remember { mutableStateOf<List<LyricHit>>(emptyList()) }
    var lyricsBusy by remember { mutableStateOf(false) }
    var lyricsOpen by remember { mutableStateOf<LyricsResult?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fg = style.onBackdrop
    val hits = remember(query, library) {
        library.withIndex().filter { (_, t) ->
            query.isBlank() || t.displayName().contains(query, true) || t.path.contains(query, true)
        }
    }
    LaunchedEffect(query, lyricsOn) {
        lyricHits = emptyList()
        if (!lyricsOn || query.trim().length < 3) { lyricsBusy = false; return@LaunchedEffect }
        delay(600)
        lyricsBusy = true
        lyricHits = Lyrics.searchByText(ctx, query)
        lyricsBusy = false
    }
    Box(modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).clip(CircleShape).background(style.chip).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = style.textDim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = fg, fontSize = 17.sp),
                cursorBrush = SolidColor(style.accent),
                modifier = Modifier.weight(1f),
            )
            if (query.isNotEmpty()) {
                // a 40 dp touch target around the 22 dp glyph
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .semantics { contentDescription = tr("Clear", "Temizle"); role = Role.Button }
                        .clickable { query = "" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, null, tint = style.textDim, modifier = Modifier.size(22.dp)) }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Search by lyrics", "Sözlerde ara"), color = if (lyricsOn) style.onAccent else style.onBackdrop, fontSize = 14.sp, maxLines = 1,
                fontWeight = if (lyricsOn) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clip(CircleShape).background(if (lyricsOn) style.accent else style.chip).clickable { lyricsOn = !lyricsOn }.padding(horizontal = 14.dp, vertical = 7.dp))
            if (lyricsBusy) CircularProgressIndicator(Modifier.padding(start = 12.dp).size(18.dp), color = style.accent, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(8.dp))
        if (lyricsOn) LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(lyricHits) { _, h ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(style.chip)
                        .clickable { scope.launch { lyricsOpen = Lyrics.forHit(ctx, h) } }.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(listOf(h.artist, h.title).filter { it.isNotBlank() }.joinToString(" - "), color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(h.line, color = style.accent, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(hits) { _, (i, t) -> TrackRow(t, t == current, style, onClick = { onPlay(i) }) }
        }
    }
    // the opened lyrics fill the search page, close icon top right (same as the lyrics search)
    lyricsOpen?.let { r ->
        BackHandler { lyricsOpen = null }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            LyricsView(r, -1L, null, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 48.dp))
            Box(Modifier.align(Alignment.TopEnd).size(44.dp).clickable { lyricsOpen = null }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
    }
}

private val rowHeight = 64.dp

/**
 * Play queue. The current entry is highlighted, the handle drags an entry to a new place (applied on release),
 * the close icon removes it.
 */
@Composable
fun QueueScreen(
    tracks: List<Track>,
    currentIndex: Int,
    style: PowerampStyle,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onMove: (Int, Int) -> Unit = { _, _ -> },
) {
    var from by remember { mutableIntStateOf(-1) }
    var off by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    val count by rememberUpdatedState(tracks.size)
    val move by rememberUpdatedState(onMove)
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        itemsIndexed(tracks) { i, t ->
            val dragging = i == from
            val idx by rememberUpdatedState(i)
            Box(
                Modifier.zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) off else 0f }
                    .background(if (dragging) style.chip else Color.Transparent),
            ) {
                TrackRow(t, i == currentIndex, style, onClick = { onPlay(i) }) {
                    IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, tr("Remove", "Kaldır"), tint = style.textDim)
                    }
                    Box(
                        Modifier.size(44.dp).pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { from = idx; off = 0f },
                                onDragEnd = {
                                    val to = (from + (off / rowPx).roundToInt()).coerceIn(0, count - 1)
                                    if (from >= 0 && to != from) move(from, to)
                                    from = -1; off = 0f
                                },
                                onDragCancel = { from = -1; off = 0f },
                            ) { c, d -> c.consume(); off += d.y }
                        }.semantics { contentDescription = tr("Reorder", "Sırala") },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.DragHandle, null, tint = style.textDim) }
                }
            }
        }
    }
}

@Composable
internal fun TrackRow(
    track: Track,
    selected: Boolean,
    style: PowerampStyle,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(rowHeight).clickable(onClick = onClick).padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val art = rememberTrackArt(track)
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(style.chip), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.MusicNote, null, tint = if (selected) style.accent else style.textDim, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.displayName(),
                color = if (selected) style.accent else style.onBackdrop,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(track.folderName(), color = style.textDim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) { trailing() }
    }
}
