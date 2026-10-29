package com.umutk.pulse.extras.music

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.extras.music.poweramp.displayName
import com.umutk.pulse.extras.music.stats.MusicStats
import com.umutk.pulse.tr

/**
 * Home of the music library: a 3x3 grid of suggestions, then the daily picks as horizontal rows.
 * Only this device's data is used. [round] gives dialer-style round tiles (Poweramp); the other looks get rounded squares.
 * [onPlay] gets the index in [library] of the tapped track, as the library rows do.
 */
@Composable
fun RecommendationHome(
    library: List<Track>,
    current: Track?,
    ink: Color,
    dim: Color,
    tile: Color,
    accent: Color,
    round: Boolean,
    onPlay: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // a tap on a suggestion makes that group the queue (the album-like row it sits in); without it the track plays from the library
    onQueue: ((List<Track>, Int) -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val today = remember(library) { Recommendations.today() }
    val stats = remember(library) { MusicStats.all(ctx) }
    val grid = remember(library, stats) {
        Recommendations.grid(library, stats, MusicStats.ratings(ctx), MusicStats.favourites(ctx), today)
    }
    val sections = remember(library, stats) {
        val lists = Playlists.names(ctx).associateWith { n -> Playlists.load(ctx, n).map { it.path } }
        Recommendations.picks(library, stats, lists, today, MusicStats.ratings(ctx), MusicStats.favourites(ctx))
    }
    fun play(t: Track) = onPlay(library.indexOfFirst { it.path == t.path })
    val tileShape = if (round) CircleShape else RoundedCornerShape(12.dp)

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (grid.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            grid.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        Tile(t, current?.path == t.path, ink, dim, tile, accent, tileShape, Modifier.weight(1f)) { play(t) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        sections.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(
                    rowTitle(s), color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                )
                // plays the whole row from its first song
                Box(Modifier.padding(start = 10.dp).size(32.dp).clip(CircleShape).background(tile)
                    .clickable { if (onQueue != null) onQueue(s.tracks, 0) else s.tracks.firstOrNull()?.let { play(it) } }, contentAlignment = Alignment.Center) {
                    PIcon("play", accent, 18.dp)
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(s.tracks) { i, t ->
                    Tile(t, current?.path == t.path, ink, dim, tile, accent, tileShape, Modifier.width(96.dp)) { if (onQueue != null) onQueue(s.tracks, i) else play(t) }
                }
            }
        }
        MixMaker(library, ink, dim, tile, accent) { l -> if (onQueue != null) onQueue(l, 0) else l.firstOrNull()?.let { play(it) } }
    }
}

/** A mood mix is shown by the album most of its songs come from, like an album suggestion; the other rows keep their names. */
private fun rowTitle(s: PickSection): String {
    if (s.pick !is Pick.Mood) return title(s.pick)
    val album = s.tracks.mapNotNull { it.album?.takeIf { a -> a.isNotBlank() } }.groupingBy { it }.eachCount().maxByOrNull { it.value }
    return if (album != null && album.value >= 2) album.key else title(s.pick)
}

private fun title(p: Pick): String = when (p) {
    is Pick.Artist -> tr("Top artist: ${p.name}", "En çok dinlenen: ${p.name}")
    is Pick.Playlist -> tr("Playlist: ${p.name}", "Çalma listesi: ${p.name}")
    is Pick.Time -> when (p.part) {
        0 -> tr("Morning picks", "Sabah seçimleri")
        1 -> tr("Afternoon picks", "Öğleden sonra seçimleri")
        2 -> tr("Evening picks", "Akşam seçimleri")
        else -> tr("Night picks", "Gece seçimleri")
    }
    is Pick.Energy -> if (p.high) tr("High energy", "Yüksek enerji") else tr("Calm", "Sakin")
    is Pick.Mood -> when (p.mood) {
        "energetic" -> tr("Energetic mix", "Enerjik mix")
        "chill" -> tr("Chill mix", "Sakin mix")
        "focus" -> tr("Focus mix", "Odak mix")
        "happy" -> tr("Happy mix", "Neşeli mix")
        "sad" -> tr("Sad mix", "Hüzünlü mix")
        "calm" -> tr("Calm mix", "Dingin mix")
        "nostalgic" -> tr("Nostalgic mix", "Nostaljik mix")
        "romantic" -> tr("Romantic mix", "Romantik mix")
        "angry" -> tr("Angry mix", "Öfkeli mix")
        "dreamy" -> tr("Dreamy mix", "Hayalci mix")
        else -> tr("Daily mix", "Günlük mix")
    }
}

@Composable
private fun Tile(
    t: Track,
    playing: Boolean,
    ink: Color,
    dim: Color,
    tile: Color,
    accent: Color,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val art = rememberTrackArt(t)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(shape).background(tile), contentAlignment = Alignment.Center) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else PIcon("note", if (playing) accent else dim, 28.dp)
        }
        androidx.compose.material3.Text(
            t.displayName(), color = if (playing) accent else ink, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}


/** The moods the mix maker offers; the keys are the ones [Recommendations.moodsOf] returns. */
private val MIX_MOODS = listOf("energetic" to ("Energetic" to "Enerjik"), "chill" to ("Chill" to "Sakin"), "focus" to ("Focus" to "Odak"), "happy" to ("Happy" to "Neşeli"), "sad" to ("Sad" to "Hüzünlü"), "calm" to ("Calm" to "Dingin"), "nostalgic" to ("Nostalgic" to "Nostaljik"), "romantic" to ("Romantic" to "Romantik"), "angry" to ("Angry" to "Öfkeli"), "dreamy" to ("Dreamy" to "Hayalci"))
private val MIX_SOURCES = listOf("all" to ("Everything" to "Hepsi"), "fav" to ("Favourites" to "Favoriler"), "rated" to ("Best rated" to "En yüksek puanlı"), "top" to ("Most played" to "En çok çalınan"), "fresh" to ("Not heard lately" to "Uzun süredir dinlenmeyen"))

/** The mix maker at the bottom of the home: pick moods, where the songs come from and how many, and a queue is made and played. */
@Composable
private fun MixMaker(library: List<Track>, ink: Color, dim: Color, tile: Color, accent: Color, onMake: (List<Track>) -> Unit) {
    val ctx = LocalContext.current
    var moods by remember { androidx.compose.runtime.mutableStateOf(setOf<String>()) }
    var source by remember { androidx.compose.runtime.mutableStateOf("all") }
    var count by remember { androidx.compose.runtime.mutableIntStateOf(30) }
    var note by remember { androidx.compose.runtime.mutableStateOf("") }
    @Composable fun chip(on: Boolean, label: String, click: () -> Unit) = androidx.compose.material3.Text(
        label, color = if (on) Color.Black else ink, fontSize = 13.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) accent else tile).clickable(onClick = click).padding(horizontal = 14.dp, vertical = 8.dp),
    )
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        androidx.compose.material3.Text(tr("Mix maker", "Mix yapıcı"), color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        androidx.compose.material3.Text(tr("Mood (none = any)", "Ruh hali (seçme = hepsi)"), color = dim, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MIX_MOODS) { (k, n) -> chip(k in moods, tr(n.first, n.second)) { moods = if (k in moods) moods - k else moods + k } }
        }
        androidx.compose.material3.Text(tr("Songs from", "Şarkılar"), color = dim, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(MIX_SOURCES) { (k, n) -> chip(source == k, tr(n.first, n.second)) { source = k } }
        }
        androidx.compose.material3.Text(tr("How many", "Kaç şarkı"), color = dim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(15, 30, 60).forEach { n -> chip(count == n, "$n") { count = n } } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(tr("Make mix", "Mix yap"), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(accent).clickable {
                    val stats = MusicStats.all(ctx).associateBy { it.path }
                    val fav = MusicStats.favourites(ctx)
                    val ratings = MusicStats.ratings(ctx)
                    var pool = library.filter { t -> moods.isEmpty() || Recommendations.moodsOf(t).any { it in moods } }
                    pool = when (source) {
                        "fav" -> pool.filter { it.path in fav }
                        "rated" -> pool.filter { (ratings[it.path] ?: 0) > 0 }.sortedByDescending { ratings[it.path] ?: 0 }.take(maxOf(count * 3, 40))
                        "top" -> pool.sortedByDescending { stats[it.path]?.plays ?: 0 }.take(maxOf(count * 3, 40))
                        "fresh" -> pool.sortedBy { stats[it.path]?.lastPlayed ?: 0L }.take(maxOf(count * 3, 40))
                        else -> pool
                    }
                    if (pool.size < 2) { note = tr("Too few songs match: loosen the mood or the source", "Çok az şarkı uyuyor: ruh halini ya da kaynağı gevşet") }
                    else {
                        // the queue follows the smart shuffle: songs that suit each other, not a dice roll
                        val order = SmartShuffle.order(ctx, pool, java.util.Random().nextInt(pool.size), .45f)
                        note = ""
                        onMake(order.map { pool[it] }.take(count))
                    }
                }.padding(horizontal = 22.dp, vertical = 10.dp))
            if (note.isNotEmpty()) androidx.compose.material3.Text(note, color = dim, fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }
    }
}
