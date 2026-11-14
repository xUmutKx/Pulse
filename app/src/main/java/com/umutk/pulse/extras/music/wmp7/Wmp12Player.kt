package com.umutk.pulse.extras.music.wmp7

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The player of Windows Media Player 12 (Windows 7): a dark teal glow, the song and its artist at the top left, the cover under them, a thin progress line
 * and a pill of controls with the big glossy blue play button. It fills its window (no menu bar, no second frame inside the window's own).
 */
@Composable
fun Wmp12Player(
    title: String, artist: String, album: String?, cover: ImageBitmap?, playing: Boolean, pos: Int, dur: Int,
    onPrev: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onStop: () -> Unit, onSeek: (Int) -> Unit, modifier: Modifier = Modifier,
    queue: List<String> = emptyList(), current: Int = -1, onPick: (Int) -> Unit = {},
    albums: List<Wmp12Album> = emptyList(), albumCover: suspend (String) -> ImageBitmap? = { null }, onAlbum: (String) -> Unit = {},
) {
    // "Library": the albums of the phone as a grid of covers (the Media Library page of the real player); a tap plays the album
    var showLib by remember { androidx.compose.runtime.mutableStateOf(false) }
    // "Show list" (top right, as in the real player) swaps the cover for the play queue; the volume slider is the phone's media volume
    var showList by remember { androidx.compose.runtime.mutableStateOf(false) }
    val vctx = androidx.compose.ui.platform.LocalContext.current
    val am = remember { vctx.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager }
    val vmax = remember { am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / vmax) }
    val glow = Brush.radialGradient(listOf(Color(0xFF14677F), Color(0xFF0A2C3E), Color(0xFF040C14)), center = androidx.compose.ui.geometry.Offset(900f, 500f), radius = 1400f)
    BoxWithConstraints(modifier.fillMaxSize().background(glow)) {
        val coverSize = (maxWidth * .44f).coerceIn(90.dp, 200.dp)
        Column(Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (albums.isNotEmpty()) Text(if (showLib) "Now playing" else "Library", color = Color(0xFF9CD3F5), fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, top = 6.dp).clickable { showLib = !showLib; showList = false })
                if (queue.isNotEmpty() && !showLib) Text(if (showList) "Hide list" else "Show list", color = Color(0xFF9CD3F5), fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp, top = 6.dp).clickable { showList = !showList })
            }
            val sub = listOfNotNull(artist.takeIf { it.isNotBlank() }, album?.takeIf { it.isNotBlank() }).joinToString(" - ")
            if (sub.isNotEmpty()) Text(sub, color = Color(0xFFC7DCE8), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
              if (showLib) androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Adaptive(92.dp), Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(albums.size) { i ->
                    val a = albums[i]
                    val cv by androidx.compose.runtime.produceState<ImageBitmap?>(null, a.firstPath) { value = albumCover(a.firstPath) }
                    Column(Modifier.clickable { onAlbum(a.name); showLib = false }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
                            val b = cv
                            if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else com.umutk.pulse.PIcon("music", Color(0xFF9CD3F5), 34.dp)
                        }
                        Text(a.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        Text(a.artist, color = Color(0xFFB5CFDF), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
              } else if (showList) LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(queue) { i, name ->
                    Text(name, color = if (i == current) Color.White else Color(0xFFB5CFDF), fontSize = 14.sp, fontWeight = if (i == current) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().background(if (i == current) Color(0x33FFFFFF) else Color.Transparent).clickable { onPick(i) }.padding(horizontal = 8.dp, vertical = 7.dp))
                }
              } else
                Box(Modifier.size(coverSize).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(2.dp)), contentAlignment = Alignment.Center) {
                    if (cover != null) Image(cover, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    // no picture of the song: the glossy note WMP shows in its place
                    else com.umutk.pulse.PIcon("music", Color(0xFF9CD3F5), coverSize * .55f)
                }
            }
            // the progress line: a tap or a slide seeks
            var drag by remember { mutableFloatStateOf(-1f) }
            val frac = if (drag >= 0f) drag else if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth().height(18.dp)
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
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(mmss(pos), color = Color(0xFFC7DCE8), fontSize = 12.sp, modifier = Modifier.width(44.dp))
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color(0x66000000)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(28.dp)).padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
                ) {
                    WmpFlat(Icons.Rounded.Stop, onStop)
                    WmpFlat(Icons.Rounded.SkipPrevious, onPrev)
                    // the glossy blue button: a light top that sinks into deep blue, a white ring and a glow
                    Box(
                        Modifier.size(54.dp).shadow(10.dp, CircleShape, ambientColor = Color(0xFF2B8CFF), spotColor = Color(0xFF2B8CFF))
                            .clip(CircleShape).background(Brush.verticalGradient(listOf(Color(0xFF7FC4FF), Color(0xFF1E6FD0), Color(0xFF0B3C8A))))
                            .border(2.dp, Color(0xCCFFFFFF), CircleShape).clickable { onPlayPause() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.align(Alignment.TopCenter).padding(top = 3.dp).size(width = 34.dp, height = 18.dp).clip(RoundedCornerShape(50)).background(Color(0x55FFFFFF)))
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    WmpFlat(Icons.Rounded.SkipNext, onNext)
                    Icon(Icons.Rounded.VolumeUp, null, tint = Color(0xFF8CC9F2), modifier = Modifier.size(20.dp))
                    // the volume: a thin slider, a tap or a slide sets it
                    Box(
                        Modifier.width(54.dp).height(24.dp)
                            .pointerInput(Unit) { detectTapGestures { o -> vol = (o.x / size.width).coerceIn(0f, 1f); am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (vol * vmax).toInt(), 0) } }
                            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> vol = (c.position.x / size.width).coerceIn(0f, 1f); am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, (vol * vmax).toInt(), 0) } },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF)))
                        Box(Modifier.fillMaxWidth(vol.coerceAtLeast(0.001f)).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF8CD0FF)))
                    }
                }
                Text(mmss(dur), color = Color(0xFFC7DCE8), fontSize = 12.sp, modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}

@Composable
private fun WmpFlat(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(Modifier.size(width = 40.dp, height = 28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color(0xFFDCEBF5), modifier = Modifier.size(20.dp))
    }
}

private fun mmss(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)

/** One album of the phone for the Library page: its name, its artist and the file of its first song (its cover is read from there). */
class Wmp12Album(val name: String, val artist: String, val firstPath: String)
