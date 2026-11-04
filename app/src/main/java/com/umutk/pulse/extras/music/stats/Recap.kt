package com.umutk.pulse.extras.music.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar

/**
 * Every real listening the player does is noted here (when, how long, which song): the recap is made only from these notes, so nothing that did not happen
 * is ever shown. Listening before the notes began is not in it; the pages say from when they count.
 */
object PlayLog {
    class Entry(val t: Long, val ms: Long, val path: String, val title: String, val artist: String, val counted: Boolean)
    private fun file(c: Context) = File(c.filesDir, "music_playlog.tsv")

    @Synchronized fun add(c: Context, t: Long, ms: Long, path: String, title: String, artist: String, counted: Boolean) {
        runCatching {
            val f = file(c)
            f.appendText("$t\t$ms\t${path.replace('\t', ' ')}\t${title.replace('\t', ' ').replace('\n', ' ')}\t${artist.replace('\t', ' ').replace('\n', ' ')}\t${if (counted) 1 else 0}\n")
            // a year of heavy listening is about 40 000 lines; the oldest go beyond 60 000
            if (f.length() > 9_000_000L) f.writeText(f.readLines().takeLast(45_000).joinToString("\n", postfix = "\n"))
        }
    }

    fun all(c: Context): List<Entry> = runCatching {
        file(c).readLines().mapNotNull { l -> l.split('\t').takeIf { it.size >= 6 }?.let { p -> Entry(p[0].toLong(), p[1].toLong(), p[2], p[3], p[4], p[5] == "1") } }
    }.getOrDefault(emptyList())

    fun since(c: Context): Long = runCatching { file(c).useLines { it.firstOrNull()?.substringBefore('\t')?.toLong() } }.getOrNull() ?: 0L
}

// white text on black; the labels and the bars are greys, so nothing is pink or violet any more
private val DEEP = Color.Black; private val VIOLET = Color(0xFF8C8C8C); private val PINK = Color(0xFFB5B5B5); private val LIME = Color.White; private val SOFT = Color.White

class Recap(val from: Long, val minutes: Long, val plays: Int, val artists: List<Pair<String, Long>>, val songs: List<Triple<String, String, Int>>, val byPart: List<Long>, val byDay: List<Long>, val busiestHour: Int, val firstNote: Long, val moods: List<Pair<String, Long>> = emptyList(), val genres: List<Pair<String, Long>> = emptyList(), val songPaths: List<String> = emptyList(), val latest: List<Triple<String, String, String>> = emptyList(),
    /** the figures of the summary page: how many different songs and artists, on how many days, the busiest day, every hour, the longer top lists */
    val distinctSongs: Int = 0, val distinctArtists: Int = 0, val activeDays: Int = 0, val bestDayMs: Long = 0L, val bestDayAt: Long = 0L,
    val byHour: List<Long> = emptyList(), val artists10: List<Pair<String, Long>> = emptyList(), val songs10: List<Triple<String, String, Int>> = emptyList())

private fun makeRecap(c: Context, days: Int): Recap {
    val from = System.currentTimeMillis() - days * 86_400_000L
    val e = PlayLog.all(c).filter { it.t >= from }
    val minutes = e.sumOf { it.ms } / 60_000L
    val artists = e.filter { it.artist.isNotBlank() }.groupBy { it.artist }.mapValues { it.value.sumOf { x -> x.ms } }.toList().sortedByDescending { it.second }.take(5)
    val songs = e.filter { it.counted }.groupBy { it.path }.map { (_, l) -> Triple(l.first().title, l.first().artist, l.size) }.sortedByDescending { it.third }.take(5)
    val cal = Calendar.getInstance()
    val part = LongArray(4); val day = LongArray(7); val hour = LongArray(24)
    e.forEach { x ->
        cal.timeInMillis = x.t
        val h = cal.get(Calendar.HOUR_OF_DAY)
        hour[h] += x.ms; part[when { h in 6..11 -> 0; h in 12..17 -> 1; h in 18..22 -> 2; else -> 3 }] += x.ms
        day[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7] += x.ms
    }
    // mood and genre come from the tags of the songs that are in the library now; a song that is gone from it is left out
    val byPath = com.umutk.pulse.extras.music.MusicLibrary.tracks.value.associateBy { it.path }
    val mood = HashMap<String, Long>(); val genre = HashMap<String, Long>()
    e.forEach { x ->
        val t = byPath[x.path] ?: return@forEach
        com.umutk.pulse.extras.music.Recommendations.moodsOf(t).forEach { m -> mood[m] = (mood[m] ?: 0L) + x.ms }
        t.genre?.trim()?.takeIf { it.isNotEmpty() }?.let { g -> genre[g] = (genre[g] ?: 0L) + x.ms }
    }
    // the file path of each top song (same order as songs), for its cover
    val songPaths = e.filter { it.counted }.groupBy { it.path }.entries.sortedByDescending { it.value.size }.take(5).map { it.key }
    // the summary page: different songs and artists, the days with listening, the busiest day
    val byCalDay = HashMap<Int, Long>(); val dayStart = HashMap<Int, Long>()
    e.forEach { x ->
        cal.timeInMillis = x.t
        val k = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
        byCalDay[k] = (byCalDay[k] ?: 0L) + x.ms; if (k !in dayStart) dayStart[k] = x.t
    }
    val bestKey = byCalDay.maxByOrNull { it.value }?.key
    val artists10 = e.filter { it.artist.isNotBlank() }.groupBy { it.artist }.mapValues { it.value.sumOf { x -> x.ms } }.toList().sortedByDescending { it.second }.take(10)
    val songs10 = e.filter { it.counted }.groupBy { it.path }.map { (_, l) -> Triple(l.first().title, l.first().artist, l.size) }.sortedByDescending { it.third }.take(10)
    return Recap(from, minutes, e.count { it.counted }, artists, songs, part.toList(), day.toList(), hour.indices.maxByOrNull { hour[it] } ?: 0, PlayLog.since(c),
        mood.toList().sortedByDescending { it.second }.take(5), genre.toList().sortedByDescending { it.second }.take(5), songPaths,
        // the last songs played in the range, newest first, each once
        e.sortedByDescending { it.t }.distinctBy { it.path }.take(6).map { Triple(it.title, it.artist, it.path) },
        distinctSongs = e.filter { it.counted }.map { it.path }.distinct().size, distinctArtists = e.filter { it.artist.isNotBlank() }.map { it.artist }.distinct().size,
        activeDays = byCalDay.size, bestDayMs = bestKey?.let { byCalDay[it] } ?: 0L, bestDayAt = bestKey?.let { dayStart[it] } ?: 0L,
        byHour = hour.toList(), artists10 = artists10, songs10 = songs10)
}

/** A song's cover, small, found by its path in the library; an empty square when it has none. */
@Composable
private fun RecapCover(path: String?, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val track = remember(path) { path?.let { p -> com.umutk.pulse.extras.music.MusicLibrary.tracks.value.firstOrNull { it.path == p } } }
    val img by produceState<ImageBitmap?>(null, track) { value = track?.let { t -> withContext(Dispatchers.IO) { com.umutk.pulse.extras.music.trackArt(ctx, t) } } }
    Box(Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

/** The card as a picture (1080 x 1920): the figures of the recap on a dark violet ground. Saved as a PNG in Download/Pulse. */
private fun cardBitmap(r: Recap, label: String): Bitmap {
    val b = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val cv = Canvas(b)
    cv.drawColor(0xFF000000.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF1A1A1A.toInt(); cv.drawCircle(900f, 180f, 380f, p)
    p.color = 0xFF111111.toInt(); cv.drawCircle(120f, 1750f, 300f, p)
    p.color = 0xFFFFFFFF.toInt(); p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.textSize = 64f
    cv.drawText("PULSE", 80f, 150f, p)
    p.typeface = Typeface.DEFAULT; p.textSize = 40f; p.color = 0xCCFFFFFF.toInt(); cv.drawText(label, 80f, 215f, p)
    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.color = 0xFFFFFFFF.toInt(); p.textSize = 220f
    cv.drawText("${r.minutes}", 80f, 560f, p)
    p.typeface = Typeface.DEFAULT; p.color = 0xFFFFFFFF.toInt(); p.textSize = 54f; cv.drawText(tr("minutes of music", "dakika müzik"), 80f, 640f, p)
    var y = 800f
    p.textSize = 40f; p.color = 0xAAFFFFFF.toInt(); cv.drawText(tr("TOP ARTISTS", "EN ÇOK DİNLENEN SANATÇILAR"), 80f, y, p); y += 70f
    p.textSize = 56f; p.color = 0xFFFFFFFF.toInt()
    r.artists.take(3).forEachIndexed { i, a -> cv.drawText("${i + 1}  " + a.first.take(26), 80f, y, p); y += 80f }
    y += 60f
    p.textSize = 40f; p.color = 0xAAFFFFFF.toInt(); cv.drawText(tr("TOP SONGS", "EN ÇOK DİNLENEN ŞARKILAR"), 80f, y, p); y += 70f
    p.textSize = 56f; p.color = 0xFFFFFFFF.toInt()
    r.songs.take(3).forEachIndexed { i, s -> cv.drawText("${i + 1}  " + s.first.take(26), 80f, y, p); y += 80f }
    p.textSize = 44f; p.color = 0xFFB5B5B5.toInt()
    cv.drawText(tr("${r.plays} plays  ·  most at ${"%02d".format(r.busiestHour)}:00", "${r.plays} dinleme  ·  en çok ${"%02d".format(r.busiestHour)}:00'da"), 80f, 1780f, p)
    return b
}

/** One line of the summary: the name of a figure and its value. */
@Composable
private fun SummaryLine(name: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A heading inside the summary. */
@Composable
private fun SummaryHead(text: String) {
    Text(text.uppercase(), color = Color.White.copy(alpha = .6f), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 26.dp, bottom = 2.dp))
}

/** A bar row: a name, a bar as long as the value is next to the largest one, and the value. */
@Composable
private fun SummaryBar(name: String, value: Long, top: Long, text: String, nameWidth: androidx.compose.ui.unit.Dp = 120.dp) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.size(width = nameWidth, height = 20.dp))
        Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth((value / top.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = .85f)))
        }
        Text(" $text", color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
    }
}

/**
 * The last page: everything the recap counted, on one page you scroll. The figures, the ten best artists and songs, the time of the day, the days of the week,
 * every hour, mood and genre, and the button that saves the card as a picture.
 */
@Composable
private fun RecapSummary(r: Recap, days: Int, label: String, since: String?, cover: ImageBitmap?, saved: String, onSave: () -> Unit) {
    val dn = listOf(tr("Mon", "Pzt"), tr("Tue", "Sal"), tr("Wed", "Çar"), tr("Thu", "Per"), tr("Fri", "Cum"), tr("Sat", "Cmt"), tr("Sun", "Paz"))
    Text(label.uppercase(), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
    Text(tr("Summary", "Özet"), color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Black)
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        cover?.let { Image(it, null, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop) }
        Column(Modifier.padding(start = if (cover != null) 14.dp else 0.dp)) {
            Text("${r.minutes} " + tr("minutes", "dakika"), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
            if (r.minutes >= 60) Text("%.1f ".format(r.minutes / 60f) + tr("hours", "saat"), color = Color.White.copy(alpha = .7f), fontSize = 14.sp)
        }
    }
    SummaryHead(tr("In numbers", "Sayılarla"))
    SummaryLine(tr("Songs played (30 s or more)", "Dinlenen şarkı (30 sn ve üzeri)"), "${r.plays}")
    SummaryLine(tr("Different songs", "Farklı şarkı"), "${r.distinctSongs}")
    SummaryLine(tr("Different artists", "Farklı sanatçı"), "${r.distinctArtists}")
    SummaryLine(tr("Days with music", "Müzik dinlenen gün"), "${r.activeDays} / $days")
    SummaryLine(tr("Average per day", "Günlük ortalama"), "${r.minutes / days.coerceAtLeast(1)} " + tr("min", "dk"))
    if (r.activeDays > 0) SummaryLine(tr("Average on a day with music", "Dinlenen günde ortalama"), "${r.minutes / r.activeDays} " + tr("min", "dk"))
    if (r.bestDayMs > 0) SummaryLine(tr("Busiest day", "En yoğun gün"), java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(r.bestDayAt)) + "  ·  ${r.bestDayMs / 60_000L} " + tr("min", "dk"))
    if (r.minutes > 0) SummaryLine(tr("Busiest hour", "En yoğun saat"), "%02d:00".format(r.busiestHour))
    val topDay = r.byDay.indices.maxByOrNull { r.byDay[it] } ?: 0
    if (r.minutes > 0) SummaryLine(tr("Favourite day of the week", "En çok dinlenen gün"), dn[topDay])
    if (r.artists10.isNotEmpty()) {
        SummaryHead(tr("Top artists", "En çok dinlenen sanatçılar"))
        val top = r.artists10.first().second
        r.artists10.forEachIndexed { i, a -> SummaryBar("${i + 1}  " + a.first, a.second, top, "${a.second / 60_000L} " + tr("min", "dk"), 150.dp) }
    }
    if (r.songs10.isNotEmpty()) {
        SummaryHead(tr("Top songs", "En çok dinlenen şarkılar"))
        val top = r.songs10.first().third.toLong()
        r.songs10.forEachIndexed { i, s -> SummaryBar("${i + 1}  " + s.first, s.third.toLong(), top, "${s.third} " + tr("plays", "dinleme"), 150.dp) }
    }
    val names = listOf(tr("Morning", "Sabah"), tr("Afternoon", "Öğleden sonra"), tr("Evening", "Akşam"), tr("Night", "Gece"))
    val tot = r.byPart.sum().coerceAtLeast(1L)
    SummaryHead(tr("Time of day", "Günün saati"))
    names.forEachIndexed { i, n -> SummaryBar(n, r.byPart[i], tot, "%d%%".format(r.byPart[i] * 100 / tot)) }
    SummaryHead(tr("Days of the week", "Haftanın günleri"))
    val dmx = (r.byDay.maxOrNull() ?: 1L).coerceAtLeast(1L)
    r.byDay.forEachIndexed { i, v -> SummaryBar(dn[i], v, dmx, "${v / 60_000L} " + tr("min", "dk"), 60.dp) }
    if (r.byHour.size == 24) {
        SummaryHead(tr("Every hour", "Her saat"))
        val hmx = (r.byHour.maxOrNull() ?: 1L).coerceAtLeast(1L)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
            r.byHour.forEachIndexed { h, v -> Box(Modifier.weight(1f).height((4 + 60 * v / hmx.toFloat()).dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = if (v == hmx && v > 0) 1f else .5f))) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("00", "06", "12", "18", "23").forEach { Text(it, color = Color.White.copy(alpha = .55f), fontSize = 10.sp, modifier = Modifier.weight(1f)) }
        }
    }
    listOf(r.moods to tr("Mood", "Ruh hâli"), r.genres to tr("Genre", "Tür")).forEach { (rows, title) ->
        if (rows.isNotEmpty()) {
            SummaryHead(title)
            val top = rows.first().second
            rows.forEach { (n, ms) -> SummaryBar(n.replaceFirstChar { it.uppercase() }, ms, top, "${ms / 60_000L} " + tr("min", "dk")) }
        }
    }
    if (since != null) Text(tr("Counted from $since, when Pulse began to note the listening.", "$since tarihinden beri sayılır: Pulse dinlemeyi o gün yazmaya başladı."), color = Color.White.copy(alpha = .5f), fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp))
    Spacer(Modifier.height(22.dp))
    Text(tr("Save as picture", "Resim olarak kaydet"), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable { onSave() }.padding(horizontal = 22.dp, vertical = 12.dp))
    if (saved.isNotEmpty()) Text(saved, color = Color.White.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(24.dp))
}

/** The music recap: pages you swipe through (minutes, artists, songs, when you listen, the card to save), for the last 30 days, 3 months or a year. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecapScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    var days by remember { mutableIntStateOf(90) }
    // the play log can be thousands of lines: it is read and counted off the main thread, so the screen never stops; the last figures stay until the new ones are ready
    var recap by remember { mutableStateOf<Recap?>(null) }
    LaunchedEffect(days) { recap = withContext(Dispatchers.Default) { makeRecap(ctx, days) } }
    val r = recap ?: run { Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { Text("…", color = Color.White, fontSize = 28.sp) }; return }
    val pager = rememberPagerState { 7 }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf("") }
    // the top song's cover: its track is looked up in the library by path
    val coverTrack = remember(r) { r.songPaths.firstOrNull()?.let { p -> com.umutk.pulse.extras.music.MusicLibrary.tracks.value.firstOrNull { it.path == p } } }
    val cover by produceState<ImageBitmap?>(null, coverTrack) { value = coverTrack?.let { t -> withContext(Dispatchers.IO) { com.umutk.pulse.extras.music.trackArt(ctx, t) } } }
    val label = when (days) { 30 -> tr("Last 30 days", "Son 30 gün"); 365 -> tr("Last year", "Son bir yıl"); else -> tr("Last 3 months", "Son 3 ay") }
    val since = if (r.firstNote > 0) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(r.firstNote)) else null
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", SOFT, 22.dp) }
                Spacer(Modifier.weight(1f))
                listOf(30 to "30d", 90 to "3m", 365 to "1y").forEach { (d, n) ->
                    Text(n, color = if (days == d) DEEP else SOFT, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(if (days == d) LIME else Color.White.copy(alpha = .12f)).clickable { days = d }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            // the dots of the pages
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
                repeat(7) { i -> Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 18.dp else 7.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(if (pager.currentPage == i) LIME else Color.White.copy(alpha = .25f))) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                // each page fades and rises in when it is turned to; the numbers count up with it
                val enter = remember { Animatable(0f) }
                LaunchedEffect(pager.currentPage == page) { if (pager.currentPage == page) enter.animateTo(1f, tween(700)) else enter.snapTo(0f) }
                Column(Modifier.fillMaxSize().then(if (page == 6) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(28.dp).graphicsLayer { alpha = enter.value; translationY = (1f - enter.value) * 48.dp.toPx() }, verticalArrangement = if (page == 6) Arrangement.Top else Arrangement.Center) {
                    when (page) {
                        0 -> {
                            Text(label.uppercase(), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(tr("Your music recap", "Müzik özetin"), color = SOFT, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 44.sp)
                            Spacer(Modifier.height(14.dp))
                            Text(if (r.minutes == 0L) tr("Nothing noted yet. From now on every song you listen to is written down, and this page fills in.", "Henüz kayıt yok. Bundan sonra dinlediğin her şarkı yazılır ve bu sayfa dolar.")
                                else tr("Swipe to see what you listened to.", "Dinlediklerini görmek için kaydır."), color = SOFT.copy(alpha = .8f), fontSize = 16.sp)
                            if (r.latest.isNotEmpty()) {
                                Text(tr("Last played", "Son çalınanlar"), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, modifier = Modifier.padding(top = 24.dp))
                                r.latest.forEach { (title, artist, path) ->
                                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        RecapCover(path, 40.dp)
                                        Text(title + (if (artist.isNotBlank()) "  ·  $artist" else ""), color = SOFT.copy(alpha = .85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp).weight(1f))
                                    }
                                }
                            }
                            if (since != null) Text(tr("Counted from $since, when Pulse began to note the listening.", "$since tarihinden beri sayılır: Pulse dinlemeyi o gün yazmaya başladı."), color = SOFT.copy(alpha = .55f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        1 -> {
                            Text(tr("You listened for", "Şu kadar dinledin"), color = SOFT.copy(alpha = .8f), fontSize = 16.sp)
                            Text("${(r.minutes * enter.value).toLong()}", color = LIME, fontSize = 110.sp, fontWeight = FontWeight.Black, lineHeight = 112.sp)
                            Text(tr("minutes", "dakika") + (if (r.minutes >= 60) "  ·  %.1f ".format(r.minutes / 60f) + tr("hours", "saat") else ""), color = SOFT, fontSize = 22.sp)
                            Text(tr("${r.plays} songs played to the end of their 30 seconds or more", "${r.plays} şarkı en az 30 saniye dinlendi"), color = SOFT.copy(alpha = .6f), fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        2 -> {
                            Text(tr("Top artists", "En çok dinlenen sanatçılar"), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            if (r.artists.isEmpty()) Text("—", color = SOFT, fontSize = 30.sp)
                            val top = r.artists.firstOrNull()?.second?.coerceAtLeast(1L) ?: 1L
                            r.artists.forEachIndexed { i, a ->
                                Column(Modifier.padding(top = 12.dp)) {
                                    Text("${i + 1}  " + a.first, color = SOFT, fontSize = if (i == 0) 30.sp else 20.sp, fontWeight = if (i == 0) FontWeight.Black else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Box(Modifier.padding(top = 4.dp).fillMaxWidth(a.second / top.toFloat()).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i == 0) LIME else VIOLET))
                                    Text("${a.second / 60_000L} " + tr("min", "dk"), color = SOFT.copy(alpha = .55f), fontSize = 12.sp)
                                }
                            }
                        }
                        3 -> {
                            Text(tr("Top songs", "En çok dinlenen şarkılar"), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            cover?.let { Image(it, null, Modifier.padding(top = 14.dp).size(150.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
                            if (r.songs.isEmpty()) Text("—", color = SOFT, fontSize = 30.sp)
                            r.songs.forEachIndexed { i, s ->
                                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RecapCover(r.songPaths.getOrNull(i), if (i == 0) 64.dp else 48.dp)
                                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                        Text("${i + 1}  " + s.first, color = SOFT, fontSize = if (i == 0) 28.sp else 19.sp, fontWeight = if (i == 0) FontWeight.Black else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text(s.second + (if (s.second.isNotBlank()) "  ·  " else "") + "${s.third} " + tr("plays", "dinleme"), color = SOFT.copy(alpha = .55f), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        4 -> {
                            Text(tr("When you listen", "Ne zaman dinliyorsun"), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(if (r.minutes == 0L) "—" else "%02d:00".format(r.busiestHour), color = LIME, fontSize = 80.sp, fontWeight = FontWeight.Black)
                            Text(tr("the hour you listen most", "en çok dinlediğin saat"), color = SOFT.copy(alpha = .7f), fontSize = 14.sp)
                            Spacer(Modifier.height(16.dp))
                            val names = listOf(tr("Morning", "Sabah"), tr("Afternoon", "Öğleden sonra"), tr("Evening", "Akşam"), tr("Night", "Gece"))
                            val tot = r.byPart.sum().coerceAtLeast(1L)
                            names.forEachIndexed { i, n ->
                                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(n, color = SOFT, fontSize = 14.sp, modifier = Modifier.size(width = 100.dp, height = 20.dp))
                                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .1f))) { Box(Modifier.fillMaxWidth(r.byPart[i] / tot.toFloat()).fillMaxHeight().background(VIOLET)) }
                                    Text(" %d%%".format(r.byPart[i] * 100 / tot), color = SOFT.copy(alpha = .7f), fontSize = 12.sp)
                                }
                            }
                            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                                val dn = listOf("M", "T", "W", "T", "F", "S", "S"); val mx = (r.byDay.maxOrNull() ?: 1L).coerceAtLeast(1L)
                                r.byDay.forEachIndexed { i, v -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.fillMaxWidth().height((8 + 56 * v / mx.toFloat()).dp).clip(RoundedCornerShape(4.dp)).background(if (v == mx) LIME else PINK.copy(alpha = .7f)))
                                    Text(dn[i], color = SOFT.copy(alpha = .6f), fontSize = 11.sp) } }
                            }
                        }
                        5 -> {
                            Text(tr("Mood and genre", "Ruh hâli ve tür"), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            if (r.moods.isEmpty() && r.genres.isEmpty()) Text(tr("The tags of the songs say nothing yet.", "Şarkıların etiketleri henüz bir şey söylemiyor."), color = SOFT.copy(alpha = .7f), fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
                            listOf(r.moods to true, r.genres to false).forEach { (rows, isMood) ->
                                if (rows.isNotEmpty()) {
                                    val top = rows.first().second.coerceAtLeast(1L)
                                    Text(if (isMood) tr("Mood", "Ruh hâli") else tr("Genre", "Tür"), color = SOFT.copy(alpha = .6f), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
                                    rows.forEachIndexed { i, (n, ms) ->
                                        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(n.replaceFirstChar { it.uppercase() }, color = SOFT, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.size(width = 110.dp, height = 20.dp))
                                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .1f))) { Box(Modifier.fillMaxWidth(ms / top.toFloat()).fillMaxHeight().background(if (i == 0) LIME else VIOLET)) }
                                            Text(" ${ms / 60_000L} " + tr("min", "dk"), color = SOFT.copy(alpha = .7f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                        else -> RecapSummary(r, days, label, since, cover, saved) {
                            scope.launch {
                                val bytes = withContext(Dispatchers.Default) { ByteArrayOutputStream().also { cardBitmap(r, label).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray() }
                                val n = "pulse-recap-" + System.currentTimeMillis() / 1000 + ".png"
                                saved = runCatching { withContext(Dispatchers.IO) { com.umutk.pulse.docScanSave(ctx, n, "image/png") { it.write(bytes) } }; tr("Saved $n in Download/Pulse", "$n kaydedildi, Download/Pulse içinde") }.getOrElse { tr("Could not save", "Kaydedilemedi") }
                            }
                        }
                    }
                }
            }
        }
    }
}
