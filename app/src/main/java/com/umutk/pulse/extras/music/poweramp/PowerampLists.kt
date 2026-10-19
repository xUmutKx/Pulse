package com.umutk.pulse.extras.music.poweramp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import com.umutk.pulse.extras.ui.ThemeGrid
import com.umutk.pulse.extras.ui.ThemeItem
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.umutk.pulse.tr
import kotlin.math.roundToInt

private sealed interface Lib {
    object Root : Lib
    object Songs : Lib
    object Folders : Lib
    object Settings : Lib
    object Stats : Lib
    object Favs : Lib
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
 * Tapping a song starts the whole library from it. The skin chooser opens from the palette icon when [onSkin] is set.
 */
@Composable
fun LibraryScreen(
    tracks: List<Track>,
    current: Track?,
    style: PowerampStyle,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    skinId: String? = null,
    onSkin: ((String) -> Unit)? = null,
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
        Lib.Settings -> tr("Skin", "Görünüm")
        Lib.Stats -> tr("Statistics", "İstatistikler")
        Lib.Favs -> tr("Favourites", "Favoriler")
        is Lib.Quick -> quickNames[n.kind]
        Lib.Playlists -> tr("Playlists", "Çalma listeleri")
        is Lib.Playlist -> n.name
        is Lib.Cat -> catNames[n.kind]
        is Lib.Group -> n.key
        is Lib.Folder -> n.name
        is Lib.Tree -> n.dir.substringAfterLast('/').ifEmpty { tr("Folders", "Klasörler") }
    }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (node != Lib.Root) {
                IconButton(onClick = { node = Lib.Root }) { Icon(Icons.Rounded.ArrowBack, tr("Back", "Geri"), tint = fg) }
            }
            Text(title, color = fg, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (node == Lib.Songs) {
                IconButton(onClick = { sortBy = (sortBy + 1) % 3 }) { Icon(Icons.Rounded.SortByAlpha, tr("Sort", "Sırala"), tint = if (sortBy == 2) style.textDim else style.accent) }
            }
            if (node == Lib.Root && onSkin != null) {
                IconButton(onClick = { node = Lib.Settings }) { Icon(Icons.Rounded.Palette, tr("Skin", "Görünüm"), tint = style.textDim) }
            }
        }
        when (val n = node) {
            Lib.Root -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                val cats = listOf(
                    Triple(Icons.Rounded.TrendingUp, Color(0xFFD9695B), Lib.Quick(0) to quickNames[0]),
                    Triple(Icons.Rounded.History, Color(0xFF5BA0D9), Lib.Quick(1) to quickNames[1]),
                    Triple(Icons.Rounded.NewReleases, Color(0xFF6BC08A), Lib.Quick(2) to quickNames[2]),
                    Triple(Icons.Rounded.LibraryMusic, Color(0xFF6E8BE8), Lib.Songs to tr("All songs", "Tüm şarkılar")),
                    Triple(Icons.Rounded.Album, Color(0xFFD96B6B), Lib.Cat(0) to catNames[0]),
                    Triple(Icons.Rounded.Person, Color(0xFFD98A5B), Lib.Cat(1) to catNames[1]),
                    Triple(Icons.Rounded.Piano, Color(0xFF9B6BD9), Lib.Cat(2) to catNames[2]),
                    Triple(Icons.Rounded.Event, Color(0xFF5BA0D9), Lib.Cat(3) to catNames[3]),
                    Triple(Icons.Rounded.Edit, Color(0xFF6BC08A), Lib.Cat(4) to catNames[4]),
                    Triple(Icons.Rounded.Folder, Color(0xFF4A7BD6), Lib.Folders to tr("Folders", "Klasörler")),
                    Triple(Icons.Rounded.AccountTree, Color(0xFF5B63D9), Lib.Tree(root) to tr("Folder tree", "Klasör ağacı")),
                    Triple(Icons.Rounded.QueueMusic, Color(0xFF8E5BD9), Lib.Playlists to tr("Playlists", "Çalma listeleri")),
                    Triple(Icons.Rounded.Star, Color(0xFFD9A05B), Lib.Favs to tr("Favourites", "Favoriler")),
                    Triple(Icons.Rounded.BarChart, Color(0xFF5BB5A8), Lib.Stats to tr("Statistics", "İstatistikler")),
                )
                itemsIndexed(cats) { _, (ic, col, p) -> CatRow(ic, col, p.second, style) { node = p.first } }
            }
            Lib.Settings -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
                ThemeGrid(powerampSkins.map { sk -> ThemeItem(sk.id, sk.name) { PowerampSkinTile(sk) } }, skinId ?: "", { onSkin?.invoke(it) })
            }
            is Lib.Cat -> {
                val groups = remember(tracks, n.kind) {
                    tracks.mapNotNull { t -> groupKey(t, n.kind)?.let { it to t } }.groupBy({ it.first }, { it.second })
                        .toList().let { if (n.kind == 3) it.sortedByDescending { g -> g.first } else it.sortedBy { g -> g.first.lowercase() } }
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    itemsIndexed(groups) { _, (name, list) -> CatRow(catIcons[n.kind], Color(0xFF6E8BE8), name, style, count = list.size) { node = Lib.Group(n.kind, name) } }
                }
            }
            is Lib.Group -> SongList(tracks.withIndex().filter { groupKey(it.value, n.kind) == n.key }, current, style, onPlay)
            Lib.Songs -> {
                val items = remember(tracks, sortBy) {
                    val l = tracks.withIndex().toList()
                    when (sortBy) { 0 -> l.sortedBy { it.value.displayName().lowercase() }; 1 -> l.sortedBy { it.value.folderName().lowercase() }; else -> l }
                }
                SongList(items, current, style, onPlay)
            }
            Lib.Stats -> StatsPage(style)
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

@Composable
private fun SongList(items: List<IndexedValue<Track>>, current: Track?, style: PowerampStyle, onPlay: (Int) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
        itemsIndexed(items) { _, (i, t) -> TrackRow(t, t == current, style, onClick = { onPlay(i) }) }
    }
}

@Composable
private fun CatRow(icon: ImageVector, color: Color, label: String, style: PowerampStyle, count: Int = -1, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(color.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White.copy(alpha = .92f), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, color = style.onBackdrop, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (count >= 0) Text("$count", color = style.textDim, fontSize = 13.sp)
    }
}

/** Search over the library by title and path. Results keep their library index when played. */
@Composable
fun SearchScreen(library: List<Track>, current: Track?, style: PowerampStyle, onPlay: (Int) -> Unit, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val fg = style.onBackdrop
    val hits = remember(query, library) {
        library.withIndex().filter { (_, t) ->
            query.isBlank() || t.displayName().contains(query, true) || t.path.contains(query, true)
        }
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
                Icon(Icons.Rounded.Close, tr("Clear", "Temizle"), tint = style.textDim, modifier = Modifier.size(22.dp).clickable { query = "" })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(hits) { _, (i, t) -> TrackRow(t, t == current, style, onClick = { onPlay(i) }) }
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
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(style.chip), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = if (selected) style.accent else style.textDim, modifier = Modifier.size(22.dp))
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
