package com.umutk.pulse.extras.music.looks

import com.umutk.pulse.extras.music.foldHas
import com.umutk.pulse.extras.music.shownArtist
import com.umutk.pulse.extras.music.MusicLevels

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntOffset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.umutk.pulse.Icon
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
private fun Track.artistName() = shownArtist()
private fun Track.folder() = path.substringBeforeLast('/', "")
private fun Track.albumName() = album?.takeIf { it.isNotBlank() } ?: folder().substringAfterLast('/').ifEmpty { tr("Unknown album", "Bilinmeyen albüm") }
private fun fmt(ms: Int): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

/** Cover colour: 4-means on a 24x24 copy, the biggest reasonably saturated, not too dark or bright cluster wins. */
internal fun coverColor(bm: ImageBitmap, fallback: Color): Color = runCatching {
    var src = bm.asAndroidBitmap()
    if (android.os.Build.VERSION.SDK_INT >= 26 && src.config == Bitmap.Config.HARDWARE) src = src.copy(Bitmap.Config.ARGB_8888, false)
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

/** Three bars that bounce while the current track plays and rest low when it is paused. */
@Composable
private fun PlayingBars(color: Color, playing: Boolean) {
    val inf = rememberInfiniteTransition(label = "bars")
    Row(Modifier.padding(horizontal = 6.dp).size(width = 18.dp, height = 16.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(380, 520, 450).forEachIndexed { i, ms ->
            val h by inf.animateFloat(.25f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Reverse), label = "bar$i")
            Box(Modifier.width(4.dp).fillMaxHeight(if (playing) h else .25f).background(color, RoundedCornerShape(1.dp)))
        }
    }
}

private class Lists(val ctx: android.content.Context) {
    private val sp = ctx.getSharedPreferences("pulse_mp_playlists", 0)
    fun all(): Map<String, List<String>> = sp.all.mapValues { (it.value as? String).orEmpty().split('\n').filter { p -> p.isNotEmpty() } }
    fun save(name: String, paths: List<String>) = sp.edit().putString(name, paths.joinToString("\n")).apply()
    fun delete(name: String) = sp.edit().remove(name).apply()
}

/** A round top-bar button; [label] shows beside it (minutes left on the sleep timer). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TopAction(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, accent: Color, label: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50)).background(if (on) accent.copy(alpha = .22f) else Color.Transparent).combinedClickable(onLongClick = onLong, onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (on) accent else TXT, modifier = Modifier.size(22.dp))
        if (label != null) { Spacer(Modifier.width(4.dp)); Text(label, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
    }
}

/** Live level bars from the audio that is playing; they fall flat when it pauses. */
@Composable
private fun LevelBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    var levels by remember { mutableStateOf(List(MusicLevels.BANDS) { 0f }) }
    // the bars jump up with the sound and slide back down by themselves: after the sound stops they sink for a moment (they do not fall flat at once)
    LaunchedEffect(playing) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                if (t - last >= 16_000_000L) {
                    last = t
                    val src = if (playing) MusicLevels.snapshot() else List(MusicLevels.BANDS) { 0f }
                    levels = List(MusicLevels.BANDS) { i -> val o = levels.getOrElse(i) { 0f }; val v = src[i]; if (v > o) o + (v - o) * .6f else o * .93f }
                }
            }
            if (!playing && levels.all { it < .01f }) { levels = List(MusicLevels.BANDS) { 0f }; break }
        }
    }
    androidx.compose.foundation.Canvas(modifier) {
        val n = levels.size
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        levels.forEachIndexed { i, v ->
            val h = (size.height * v.coerceIn(0f, 1f)).coerceAtLeast(3.dp.toPx())
            drawRoundRect(color.copy(alpha = .85f), androidx.compose.ui.geometry.Offset(i * (w + gap), size.height - h), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(w / 2))
        }
    }
}

@Composable
fun MaterialPlayer(engine: MusicPlayerEngine, library: List<Track>, modifier: Modifier = Modifier, artBackground: Boolean = false, onNowPlaying: (Boolean) -> Unit = {}) {
    val ctx = LocalContext.current
    val st by engine.state.collectAsState()
    val cur = st.current
    var openLyricsNow by remember { mutableStateOf(false) }
    var artRev by remember { mutableIntStateOf(0) }
    var artOpen by remember { mutableStateOf(false) }
    val art = rememberTrackArt(cur, artRev)
    val prevIdx = remember(st.index, st.queue, engine.player.shuffleModeEnabled, engine.player.repeatMode) { engine.player.previousMediaItemIndex }
    val nextIdx = remember(st.index, st.queue, engine.player.shuffleModeEnabled, engine.player.repeatMode) { engine.player.nextMediaItemIndex }
    val (artPrev, artNext) = neighbourCovers(st.queue, prevIdx, nextIdx) { trackArt(ctx, it) }
    // AMOLED is pure black; the other Material looks keep the deep blue-grey
    val pbg = if (T.id == "amoled") Color.Black else PBG
    val base = Look.accent.let { if (it.luminance() < .35f) Color(0xFFB9C3FF) else it }
    // always a dark player, whatever the look or light mode; the cover colour is the accent
    val accent = remember(art, base) { art?.let { coverColor(it, base) }?.let { c -> if (c.luminance() < .3f) androidx.compose.ui.graphics.lerp(c, Color.White, .45f) else c } ?: base }
    // the cover colour flows into the next one instead of jumping
    val dyn by androidx.compose.animation.animateColorAsState(accent, tween(700), label = "dyn")
    val tabs = listOf(tr("All songs", "Tüm şarkılar"),tr("Albums", "Albümler"), tr("Artists", "Sanatçılar"), tr("Playlists", "Listeler"), tr("Folders", "Klasörler"))
    var tab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<String?>(null) }
    var showNow by remember { mutableStateOf(false) }
    // a tap on the media notification opens the full player, a tap on the lyrics notification the full player with the lyrics
    androidx.compose.runtime.LaunchedEffect(com.umutk.pulse.extras.music.MusicNav.request) {
        val r = com.umutk.pulse.extras.music.MusicNav.request
        if (r == "player" || r == "lyrics") { showNow = true; if (r == "lyrics") openLyricsNow = true; com.umutk.pulse.extras.music.MusicNav.request = null }
    }
    var showQueue by remember { mutableStateOf(false) }
    val lists = remember { Lists(ctx) }
    var playlists by remember { mutableStateOf(lists.all()) }

    var pos by remember { mutableIntStateOf(0) }
    var dur by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val modesPrefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("music_modes", android.content.Context.MODE_PRIVATE)
    // "shuffled" also covers a queue that was shuffled once into a fixed order, which the player itself does not know
    var shuffle by remember { mutableStateOf(engine.player.shuffleModeEnabled || modesPrefs.getBoolean("queue_shuffled", false)) }
    var repeat by remember { mutableIntStateOf(engine.player.repeatMode) }
    LaunchedEffect(st.isPlaying, st.current) {
        while (true) { if (!dragging) pos = engine.positionMs; dur = engine.durationMs; if (!st.isPlaying) break; delay(100) }
    }

    // sleep timer: minutes left, 0 = off
    var sleepLeft by remember { mutableIntStateOf(0) }
    LaunchedEffect(sleepLeft > 0) {
        while (sleepLeft > 0) { delay(60_000); sleepLeft--; if (sleepLeft == 0) engine.pause() }
    }

    val scope = rememberCoroutineScope()
    val sheetY = remember { Animatable(0f) }
    var rootH by remember { mutableFloatStateOf(0f) }
    var sheetOn by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var askForget by remember { mutableStateOf(false) }
    if (askForget && cur != null) com.umutk.pulse.extras.lyrics.ForgetLyricsDialog(cur.path.takeIf { !it.contains("://") }, cur.name(), cur.artistName()) { askForget = false }
    androidx.compose.runtime.LaunchedEffect(openLyricsNow, cur?.path) { if (openLyricsNow && cur != null) { showLyrics = true; openLyricsNow = false } }
    if (artOpen && cur != null) com.umutk.pulse.extras.music.poweramp.CoverArtDialog(cur, com.umutk.pulse.extras.music.poweramp.PowerampStyle(), onSaved = { com.umutk.pulse.extras.music.dropArt(cur.path); artRev++ }, onClose = { artOpen = false })
    LaunchedEffect(cur?.path) { showLyrics = false }
    // a drag down moves the whole player and closes it past a fifth of the screen; a drag up opens the lyrics instead (the player stays put)
    var upAcc by remember { mutableFloatStateOf(0f) }
    var lyricsPull by remember { mutableFloatStateOf(0f) }
    fun pageDrag(dy: Float) {
        if (dy < 0f) upAcc += -dy else upAcc = (upAcc - dy).coerceAtLeast(0f)
        scope.launch { sheetY.snapTo((sheetY.value + dy).coerceAtLeast(0f)) }
    }
    fun pageRelease() {
        val up = upAcc; upAcc = 0f
        scope.launch {
            if (sheetY.value > rootH * .22f) {
                sheetY.animateTo(rootH, tween(330, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, 0f, 0f, 1f)))
                showQueue = false; showNow = false
            } else {
                if (up > rootH * .06f && sheetY.value < 1f) { showQueue = false; showLyrics = true }
                sheetY.animateTo(0f, spring(dampingRatio = .8f, stiffness = Spring.StiffnessLow))
            }
        }
    }
    // the lyrics of this song and the next two are looked up in the background, so the sheet opens with them in place
    val prefetchCtx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(cur?.path, st.queue.size) {
        val i = st.index
        listOfNotNull(cur, st.queue.getOrNull(i + 1), st.queue.getOrNull(i + 2)).forEach { t ->
            runCatching {
                com.umutk.pulse.extras.lyrics.Lyrics.forTrack(prefetchCtx, t.name(), t.artistName(), t.album, t.durationSec * 1000L, t.path.takeIf { !it.contains("://") })
                    ?: com.umutk.pulse.extras.lyrics.Lyrics.forTrack(prefetchCtx, t.name().substringAfter(" - "), "", t.album, t.durationSec * 1000L, t.path.takeIf { !it.contains("://") })
            }
        }
    }

    BackHandler(showNow || group != null) { if (showQueue) showQueue = false else if (showNow) showNow = false else group = null }

    val q = query.trim().lowercase()
    val songs = remember(library, q) { if (q.isEmpty()) library else library.filter { it.name().foldHas(q) || it.folder().foldHas(q) || it.artist.foldHas(q) || it.album.foldHas(q) } }

    DisposableEffect(showNow) { onNowPlaying(showNow); onDispose { onNowPlaying(false) } }

    Box(modifier.fillMaxSize().background(pbg).onSizeChanged { rootH = it.height.toFloat() }) {
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
                            Text(if (tab == 4) g.substringAfterLast('/') else g, color = TXT, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Icon(Icons.Rounded.Shuffle, null, tint = accent, modifier = Modifier.clickable { if (shown.isNotEmpty()) engine.playQueue(shown.shuffled()) })
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (tab == 0 && g == null && q.isEmpty()) item { RecommendationHome(library, cur, TXT, SUB, PANEL, accent, round = false, onPlay = { engine.playQueue(library, it) }, onQueue = { l, i -> engine.playQueue(l, i) }) }
                            itemsIndexed(shown, key = { i, t -> "$i${t.path}" }) { i, t ->
                                val on = cur?.path == t.path
                                Row(Modifier.fillMaxWidth().clickable { engine.playQueue(shown, i); showNow = true }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Art(t, 44.dp, accent)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(t.name(), color = if (on) accent else TXT, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(t.albumName(), color = SUB, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (on) PlayingBars(accent, st.isPlaying)
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
                // shuffle and repeat with their state (a lit icon, "1" for repeat one), previous, a round play button on the cover colour, next
                ShuffleButton(engine, shuffle, dyn, SUB, 34.dp, 7.dp) { shuffle = it; modesPrefs.edit().putBoolean("queue_shuffled", it).apply() }
                Icon(Icons.Rounded.SkipPrevious, null, tint = TXT, modifier = Modifier.size(36.dp).clickable { engine.previous() }.padding(5.dp))
                Box(Modifier.padding(horizontal = 2.dp).size(42.dp).clip(CircleShape).background(dyn).clickable { engine.togglePlayPause() }, contentAlignment = Alignment.Center) {
                    Crossfade(st.isPlaying, animationSpec = tween(260), label = "miniPlay") { p -> Icon(if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = onAccent(dyn), modifier = Modifier.size(26.dp)) }
                }
                Icon(Icons.Rounded.SkipNext, null, tint = TXT, modifier = Modifier.size(36.dp).clickable { engine.next() }.padding(5.dp))
                RepeatButton(engine, repeat, dyn, SUB, 34.dp, 7.dp) { repeat = it }
            }
        }

        // the player is a sheet over the library: it rises from the bottom, follows the finger on any vertical drag and the library shows underneath
        val visible = showNow && cur != null
        LaunchedEffect(visible) {
            if (visible) { sheetY.snapTo(if (rootH > 0f) rootH else 3000f); sheetOn = true; sheetY.animateTo(0f, tween(520, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, 0f, 0f, 1f))) }
            else if (sheetOn) { sheetY.animateTo(rootH.coerceAtLeast(1f), tween(380, easing = FastOutSlowInEasing)); sheetOn = false }
        }
        if (sheetOn) {
            val cur = cur ?: return@Box
            // the cover breathes out while playing and settles smaller when paused
            val coverScale by animateFloatAsState(if (st.isPlaying) 1f else .88f, spring(dampingRatio = .55f, stiffness = Spring.StiffnessLow), label = "cover")
            Box(Modifier.fillMaxSize().offset { IntOffset(0, sheetY.value.roundToInt()) }.background(pbg)
                .pointerInput(Unit) { detectVerticalDragGestures(
                    onDragEnd = { if (showLyrics) { if (lyricsPull > 100f) showLyrics = false; lyricsPull = 0f } else pageRelease() },
                    onDragCancel = { if (showLyrics) lyricsPull = 0f else pageRelease() },
                    // with the lyrics open a pull down that the lyrics list did not take (it is at its top) closes the lyrics only; the player stays
                    onVerticalDrag = { c, d -> if (showLyrics) { c.consume(); lyricsPull += d } else { c.consume(); pageDrag(d) } }) }) {
                // one layer: the blurred cover is the background when the art switch is on, nothing extra is drawn otherwise
                if (artBackground) Crossfade(art, animationSpec = tween(600), label = "bg") { a -> if (a != null) Image(a, null, Modifier.fillMaxSize().blur(48.dp).alpha(.55f), contentScale = ContentScale.Crop) }
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = TXT, modifier = Modifier.size(36.dp).clickable { showQueue = false; showNow = false })
                        Spacer(Modifier.weight(1f))
                        TopAction(Icons.Rounded.Image, artOpen, dyn) { artOpen = true }
                        TopAction(Icons.Rounded.Subject, showLyrics, dyn, null, onLong = { askForget = true }) { showQueue = false; showLyrics = !showLyrics }
                        TopAction(Icons.Rounded.Bedtime, sleepLeft > 0, dyn, if (sleepLeft > 0) "$sleepLeft" else null) { sleepLeft = when (sleepLeft) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 } }
                        TopAction(Icons.Rounded.QueueMusic, showQueue, dyn) { showLyrics = false; showQueue = !showQueue }
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
                        // a tenth bigger than the column: the cover reaches out evenly on both sides, into the page margin
                        Box(Modifier.layout { m, c ->
                            val w = (c.maxWidth * 1.1f).toInt()
                            val p = m.measure(c.copy(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity))
                            layout(c.maxWidth, p.height) { p.place(-(w - c.maxWidth) / 2, 0) }
                        }.fillMaxWidth().aspectRatio(1f).graphicsLayer { scaleX = coverScale; scaleY = coverScale }) {
                        CoverCarousel(art, artPrev, artNext, prevIdx >= 0, nextIdx >= 0, { engine.previous() }, { engine.next() },
                            Modifier.fillMaxSize(),
                            // a vertical drag on the cover moves the whole player, like anywhere else on it
                            onPageDrag = { pageDrag(it) }, onPageRelease = { pageRelease() }) { a ->
                            if (a != null) Image(a, null, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
                            else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(dyn.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MusicNote, null, tint = dyn, modifier = Modifier.fillMaxSize(.45f)) }
                        }
                        // the song's canvas (a short video of the user's choice) plays over the cover
                        com.umutk.pulse.extras.music.CanvasVideo(cur.path, st.isPlaying, Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)))
                        // with a canvas the video is the cover; one small play button sits at its foot, nothing else
                        val canvasCtx = androidx.compose.ui.platform.LocalContext.current
                        val canvasOn = com.umutk.pulse.extras.music.TrackCanvas.version.intValue.let { com.umutk.pulse.extras.music.TrackCanvas.get(canvasCtx, cur.path) != null }
                        if (canvasOn && !showLyrics) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).size(46.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .38f))
                            .clickable { engine.togglePlayPause() }, contentAlignment = Alignment.Center) {
                            Icon(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
                        }
                        // the lyrics sit on top of the cover, in its place
                        com.umutk.pulse.extras.lyrics.PlayerLyrics(visible = showLyrics, title = cur.name(), artist = cur.artistName(), album = cur.album, durationMs = dur.toLong(),
                            localPath = cur.path.takeIf { !it.contains("://") }, positionMs = pos.toLong(), onDismiss = { showLyrics = false },
                            onSeek = { ms -> engine.seekTo(ms.toInt()); pos = ms.toInt() }, art = art, modifier = Modifier.clip(RoundedCornerShape(24.dp)))
                        }
                        Spacer(Modifier.weight(1f))
                        LevelBars(st.isPlaying, dyn, Modifier.fillMaxWidth().height(28.dp).padding(bottom = 6.dp))
                    }
                    // title and artist slide in from the side the track came from (next from the right, previous from the left), like Poweramp
                    var lastIndex by remember { mutableIntStateOf(st.index) }
                    val forward = st.index >= lastIndex
                    SideEffect { lastIndex = st.index }
                    androidx.compose.animation.AnimatedContent(cur, transitionSpec = {
                        val d = if (forward) 1 else -1
                        (slideInHorizontally(tween(520, easing = androidx.compose.animation.core.CubicBezierEasing(.2f, 0f, 0f, 1f))) { it / 3 * d } + fadeIn(tween(420))) togetherWith
                            (slideOutHorizontally(tween(380, easing = FastOutSlowInEasing)) { -it / 3 * d } + fadeOut(tween(280)))
                    }, contentKey = { it.path }, label = "title") { t ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t.name(), color = TXT, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(t.artistName(), color = SUB, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                    com.umutk.pulse.extras.lyrics.LiveLyricsLine(cur, pos, Modifier.fillMaxWidth().padding(top = 8.dp))
                    val d = dur.coerceAtLeast(1)
                    // the position arrives every 400 ms: glide between the ticks instead of jumping
                    val glide by androidx.compose.animation.core.animateFloatAsState(pos.toFloat(), tween(420, easing = androidx.compose.animation.core.LinearEasing), label = "seekGlide")
                    // the thumb is pulled along by the finger on a spring: it lags a little, overshoots and settles, so the bar feels elastic and slippery
                    val elastic = remember { androidx.compose.animation.core.Animatable(0f) }
                    PSlider((if (dragging) elastic.value else glide).coerceIn(0f, d.toFloat()), {
                        val first = !dragging
                        dragging = true; pos = it.toInt()
                        scope.launch { if (first) elastic.snapTo(glide); elastic.animateTo(it, spring(dampingRatio = .5f, stiffness = Spring.StiffnessMediumLow)) }
                    }, 0f..d.toFloat()) { engine.seekTo(pos); scope.launch { elastic.animateTo(pos.toFloat(), spring(dampingRatio = .6f, stiffness = Spring.StiffnessLow)); dragging = false } }
                    Row(Modifier.fillMaxWidth()) { Text(fmt(pos), color = SUB, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text(fmt(dur), color = SUB, fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        ShuffleButton(engine, shuffle, dyn, SUB, 44.dp, 10.dp) { shuffle = it; modesPrefs.edit().putBoolean("queue_shuffled", it).apply() }
                        Icon(Icons.Rounded.SkipPrevious, null, tint = TXT, modifier = Modifier.size(48.dp).clickable { engine.previous() }.padding(8.dp))
                        // the play button squeezes under the finger and springs back
                        val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val pressed by press.collectIsPressedAsState()
                        val squeeze by animateFloatAsState(if (pressed) .88f else 1f, spring(dampingRatio = .45f, stiffness = Spring.StiffnessMedium), label = "squeeze")
                        Box(Modifier.size(68.dp).graphicsLayer { scaleX = squeeze; scaleY = squeeze }.clip(CircleShape).background(dyn)
                            .clickable(press, androidx.compose.material3.ripple()) { engine.togglePlayPause() }, contentAlignment = Alignment.Center) {
                            Crossfade(st.isPlaying, animationSpec = tween(300), label = "playPause") { playing ->
                                Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = onAccent(dyn), modifier = Modifier.size(36.dp))
                            }
                        }
                        Icon(Icons.Rounded.SkipNext, null, tint = TXT, modifier = Modifier.size(48.dp).clickable { engine.next() }.padding(8.dp))
                        RepeatButton(engine, repeat, dyn, SUB, 44.dp, 10.dp) { repeat = it }
                    }
                }
            }
        }
    }
}
