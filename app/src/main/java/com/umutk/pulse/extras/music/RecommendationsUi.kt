package com.umutk.pulse.extras.music

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
) {
    val ctx = LocalContext.current
    val today = remember(library) { Recommendations.today() }
    val stats = remember(library) { MusicStats.all(ctx) }
    val grid = remember(library, stats) {
        Recommendations.grid(library, stats, MusicStats.ratings(ctx), MusicStats.favourites(ctx), today)
    }
    val sections = remember(library, stats) {
        val lists = Playlists.names(ctx).associateWith { n -> Playlists.load(ctx, n).map { it.path } }
        Recommendations.picks(library, stats, lists, today)
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
            androidx.compose.material3.Text(
                title(s.pick), color = ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.tracks) { t ->
                    Tile(t, current?.path == t.path, ink, dim, tile, accent, tileShape, Modifier.width(96.dp)) { play(t) }
                }
            }
        }
    }
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
