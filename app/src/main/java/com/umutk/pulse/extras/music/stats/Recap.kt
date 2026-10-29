package com.umutk.pulse.extras.music.stats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlinx.coroutines.launch
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

private val DEEP = Color(0xFF14102B); private val VIOLET = Color(0xFF7C4DFF); private val PINK = Color(0xFFFF4F9A); private val LIME = Color(0xFFC6FF3D); private val SOFT = Color(0xFFEDE7FF)

class Recap(val from: Long, val minutes: Long, val plays: Int, val artists: List<Pair<String, Long>>, val songs: List<Triple<String, String, Int>>, val byPart: List<Long>, val byDay: List<Long>, val busiestHour: Int, val firstNote: Long)

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
    return Recap(from, minutes, e.count { it.counted }, artists, songs, part.toList(), day.toList(), hour.indices.maxByOrNull { hour[it] } ?: 0, PlayLog.since(c))
}

/** The card as a picture (1080 x 1920): the figures of the recap on a dark violet ground. Saved as a PNG in Download/Pulse. */
private fun cardBitmap(r: Recap, label: String): Bitmap {
    val b = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
    val cv = Canvas(b)
    cv.drawColor(0xFF14102B.toInt())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    p.color = 0xFF7C4DFF.toInt(); cv.drawCircle(900f, 180f, 380f, p)
    p.color = 0xFFFF4F9A.toInt(); cv.drawCircle(120f, 1750f, 300f, p)
    p.color = 0xFFEDE7FF.toInt(); p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.textSize = 64f
    cv.drawText("PULSE", 80f, 150f, p)
    p.typeface = Typeface.DEFAULT; p.textSize = 40f; p.color = 0xCCEDE7FF.toInt(); cv.drawText(label, 80f, 215f, p)
    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); p.color = 0xFFC6FF3D.toInt(); p.textSize = 220f
    cv.drawText("${r.minutes}", 80f, 560f, p)
    p.typeface = Typeface.DEFAULT; p.color = 0xFFEDE7FF.toInt(); p.textSize = 54f; cv.drawText(tr("minutes of music", "dakika müzik"), 80f, 640f, p)
    var y = 800f
    p.textSize = 40f; p.color = 0xAAEDE7FF.toInt(); cv.drawText(tr("TOP ARTISTS", "EN ÇOK DİNLENEN SANATÇILAR"), 80f, y, p); y += 70f
    p.textSize = 56f; p.color = 0xFFEDE7FF.toInt()
    r.artists.take(3).forEachIndexed { i, a -> cv.drawText("${i + 1}  " + a.first.take(26), 80f, y, p); y += 80f }
    y += 60f
    p.textSize = 40f; p.color = 0xAAEDE7FF.toInt(); cv.drawText(tr("TOP SONGS", "EN ÇOK DİNLENEN ŞARKILAR"), 80f, y, p); y += 70f
    p.textSize = 56f; p.color = 0xFFEDE7FF.toInt()
    r.songs.take(3).forEachIndexed { i, s -> cv.drawText("${i + 1}  " + s.first.take(26), 80f, y, p); y += 80f }
    p.textSize = 44f; p.color = 0xFFFF4F9A.toInt()
    cv.drawText(tr("${r.plays} plays  ·  most at ${"%02d".format(r.busiestHour)}:00", "${r.plays} dinleme  ·  en çok ${"%02d".format(r.busiestHour)}:00'da"), 80f, 1780f, p)
    return b
}

/** The music recap: pages you swipe through (minutes, artists, songs, when you listen, the card to save), for the last 30 days, 3 months or a year. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecapScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    var days by remember { mutableIntStateOf(90) }
    val r = remember(days) { makeRecap(ctx, days) }
    val pager = rememberPagerState { 6 }
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf("") }
    val label = when (days) { 30 -> tr("Last 30 days", "Son 30 gün"); 365 -> tr("Last year", "Son bir yıl"); else -> tr("Last 3 months", "Son 3 ay") }
    val since = if (r.firstNote > 0) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(r.firstNote)) else null
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(DEEP, Color(0xFF241A4D))))) {
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
                repeat(6) { i -> Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 18.dp else 7.dp, 7.dp).clip(RoundedCornerShape(4.dp)).background(if (pager.currentPage == i) LIME else Color.White.copy(alpha = .25f))) }
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
                    when (page) {
                        0 -> {
                            Text(label.uppercase(), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text(tr("Your music recap", "Müzik özetin"), color = SOFT, fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 44.sp)
                            Spacer(Modifier.height(14.dp))
                            Text(if (r.minutes == 0L) tr("Nothing noted yet. From now on every song you listen to is written down, and this page fills in.", "Henüz kayıt yok. Bundan sonra dinlediğin her şarkı yazılır ve bu sayfa dolar.")
                                else tr("Swipe to see what you listened to.", "Dinlediklerini görmek için kaydır."), color = SOFT.copy(alpha = .8f), fontSize = 16.sp)
                            if (since != null) Text(tr("Counted from $since, when Pulse began to note the listening.", "$since tarihinden beri sayılır: Pulse dinlemeyi o gün yazmaya başladı."), color = SOFT.copy(alpha = .55f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        }
                        1 -> {
                            Text(tr("You listened for", "Şu kadar dinledin"), color = SOFT.copy(alpha = .8f), fontSize = 16.sp)
                            Text("${r.minutes}", color = LIME, fontSize = 110.sp, fontWeight = FontWeight.Black, lineHeight = 112.sp)
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
                            if (r.songs.isEmpty()) Text("—", color = SOFT, fontSize = 30.sp)
                            r.songs.forEachIndexed { i, s ->
                                Column(Modifier.padding(top = 12.dp)) {
                                    Text("${i + 1}  " + s.first, color = SOFT, fontSize = if (i == 0) 28.sp else 19.sp, fontWeight = if (i == 0) FontWeight.Black else FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(s.second + (if (s.second.isNotBlank()) "  ·  " else "") + "${s.third} " + tr("plays", "dinleme"), color = SOFT.copy(alpha = .55f), fontSize = 12.sp)
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
                        else -> {
                            Text(tr("Your card", "Kartın"), color = PINK, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("${r.minutes} " + tr("minutes", "dakika"), color = LIME, fontSize = 44.sp, fontWeight = FontWeight.Black)
                            Text(r.artists.take(3).joinToString("  ·  ") { it.first }, color = SOFT, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                            Spacer(Modifier.height(20.dp))
                            Text(tr("Save as picture", "Resim olarak kaydet"), color = DEEP, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(LIME)
                                .clickable { scope.launch {
                                    val bytes = ByteArrayOutputStream().also { cardBitmap(r, label).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                                    val n = "pulse-recap-" + System.currentTimeMillis() / 1000 + ".png"
                                    saved = runCatching { com.umutk.pulse.docScanSave(ctx, n, "image/png") { it.write(bytes) }; tr("Saved $n in Download/Pulse", "$n kaydedildi, Download/Pulse içinde") }.getOrElse { tr("Could not save", "Kaydedilemedi") }
                                } }.padding(horizontal = 22.dp, vertical = 12.dp))
                            if (saved.isNotEmpty()) Text(saved, color = SOFT.copy(alpha = .7f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
