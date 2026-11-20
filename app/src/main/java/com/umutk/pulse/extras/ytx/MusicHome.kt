package com.umutk.pulse.extras.ytx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.umutk.pulse.tr
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Scallop = GenericShape { size, _ ->
    val r0 = size.minDimension / 2f * .93f; val amp = size.minDimension / 2f * .07f
    for (i in 0..360) {
        val a = i * PI / 180.0
        val r = r0 + amp * cos(a * 9).toFloat()
        val x = size.width / 2f + (r * cos(a)).toFloat(); val y = size.height / 2f + (r * sin(a)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/**
 * The music home made from what the person plays: Speed dial (shuffle and the tracks to start with), Rediscover, Artists for you and Quick picks.
 * The order follows the taste engine ([Neuro]); only tracks the person already knows are used, so nothing needs a request.
 */
fun LazyListScope.musicForYou(
    recent: List<VideoItem>,
    liked: List<VideoItem>,
    onOpenQueue: (List<VideoItem>, Int) -> Unit,
    onOptions: (VideoItem) -> Unit,
) {
    val pool = (recent + liked).filter { Taste.ok(it) }.distinctBy { it.id }
    if (pool.size < 4) return
    val ranked = Neuro.rank(pool)
    val dial = ranked.take(8)

    item(key = "mh_speed") {
        val c = MaterialTheme.colorScheme
        Column {
            MhTitle(tr("Speed dial", "Hızlı erişim"))
            // three rows of tiles that scroll sideways; the first tile starts a shuffle
            val tiles = remember(dial) { listOf<VideoItem?>(null) + dial }
            val cols = tiles.chunked(3)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cols.size) { ci ->
                    Column(Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        cols[ci].forEach { v ->
                            Row(
                                Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(16.dp)).background(c.surfaceContainerHigh)
                                    .pressable(pressedScale = .97f) { if (v == null) onOpenQueue(pool.shuffled(), 0) else onOpenQueue(dial, dial.indexOf(v)) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (v == null) {
                                    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Shuffle, null, tint = c.onSurface, modifier = Modifier.size(30.dp)) }
                                    Text(tr("Shuffle play", "Karışık çal"), color = c.onSurface, fontSize = 17.sp, modifier = Modifier.padding(start = 12.dp))
                                } else {
                                    AsyncImage(highResMusicThumbnail(v.thumbnail) ?: v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).background(c.surfaceContainerHighest))
                                    Column(Modifier.padding(horizontal = 12.dp)) {
                                        Text(v.title, color = c.onSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(v.channel, color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val rest = pool.filter { it !in dial }.let { if (it.size >= 3) it else pool }.take(12)
    item(key = "mh_rediscover") {
        val c = MaterialTheme.colorScheme
        Column {
            MhTitle(tr("Rediscover", "Yeniden keşfet"))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rest.size) { i ->
                    val v = rest[i]
                    Column(Modifier.width(190.dp).pressable(pressedScale = .96f) { onOpenQueue(rest, i) }) {
                        AsyncImage(highResMusicThumbnail(v.thumbnail) ?: v.thumbnail, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(190.dp).clip(RoundedCornerShape(22.dp)).background(c.surfaceContainerHighest))
                        Text(v.title, color = c.onSurface, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(v.channel, color = c.onSurfaceVariant, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    val artists = pool.groupBy { it.channel }.entries.sortedByDescending { e -> e.value.size * 2f + Neuro.score(e.value.first()) }.take(12)
    if (artists.size >= 3) item(key = "mh_artists") {
        val c = MaterialTheme.colorScheme
        Column {
            MhTitle(tr("Artists for you", "Senin için sanatçılar"))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(artists.size) { i ->
                    val (name, tracks) = artists[i].let { it.key to it.value }
                    Column(Modifier.width(120.dp).pressable(pressedScale = .95f) { onOpenQueue(tracks, 0) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        val pic = tracks.firstNotNullOfOrNull { it.avatar } ?: tracks.first().thumbnail
                        AsyncImage(highResMusicThumbnail(pic) ?: pic, null, contentScale = ContentScale.Crop, modifier = Modifier.size(120.dp).clip(Scallop).background(c.surfaceContainerHighest))
                        Text(name, color = c.onSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }
        }
    }

    val picks = ranked.take(12)
    item(key = "mh_quick") {
        val c = MaterialTheme.colorScheme
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Quick picks", "Hızlı seçimler"), color = c.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Row(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceContainerHigh).pressable(pressedScale = .96f) { onOpenQueue(picks, 0) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = c.onSurface, modifier = Modifier.size(20.dp))
                    Text(tr("Play all", "Hepsini çal"), color = c.onSurface, fontSize = 16.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            val pages = picks.chunked(4)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(pages.size) { pi ->
                    Column(Modifier.width(330.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        pages[pi].forEach { v ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surfaceContainerLow).pressable(pressedScale = .98f) { onOpenQueue(picks, picks.indexOf(v)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(highResMusicThumbnail(v.thumbnail) ?: v.thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)).background(c.surfaceContainerHighest))
                                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(v.title, color = c.onSurface, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(v.channel + if (v.durationSec > 0) "  •  " + formatDuration(v.durationSec * 1000) else "", color = c.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun MhTitle(t: String) {
    Text(t, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp))
}
