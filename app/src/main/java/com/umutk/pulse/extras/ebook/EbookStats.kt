package com.umutk.pulse.extras.ebook

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Reading statistics: the time spent reading (only while pages are being turned or words run past), the pages and the books finished, per day and per book.
 * A book keeps its numbers when it leaves the shelf or its file is deleted: the title and author are stored with them.
 */
object EbookStats {
    private fun sp(c: Context) = c.getSharedPreferences("ebook_stats", Context.MODE_PRIVATE)
    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private fun today() = dayFmt.format(Date())

    private fun remember(c: Context, key: String, value: String) {
        val p = sp(c)
        val set = p.getStringSet(key, emptySet()).orEmpty()
        if (value !in set) p.edit().putStringSet(key, set + value).apply()
    }

    /** One more [sec] seconds of reading of the book [uri]. */
    fun addSeconds(c: Context, uri: String, title: String, author: String, sec: Int) {
        val d = today(); val p = sp(c)
        remember(c, "days", d); remember(c, "books", uri)
        p.edit().putLong("ds_$d", p.getLong("ds_$d", 0L) + sec).putLong("bs_$uri", p.getLong("bs_$uri", 0L) + sec)
            .putString("bm_$uri", title + "\u0001" + author).putLong("bl_$uri", System.currentTimeMillis()).apply()
    }

    /** A page turned forward in [uri]. */
    fun addPage(c: Context, uri: String, title: String, author: String) {
        val d = today(); val p = sp(c)
        remember(c, "days", d); remember(c, "books", uri)
        p.edit().putInt("dp_$d", p.getInt("dp_$d", 0) + 1).putInt("bp_$uri", p.getInt("bp_$uri", 0) + 1)
            .putString("bm_$uri", title + "\u0001" + author).apply()
    }

    /** The last page of the book was reached. */
    fun finish(c: Context, uri: String) {
        val p = sp(c)
        if (p.getBoolean("bf_$uri", false)) return
        remember(c, "books", uri)
        p.edit().putBoolean("bf_$uri", true).putLong("bd_$uri", System.currentTimeMillis()).apply()
    }

    class Day(val date: String, val sec: Long, val pages: Int)
    class BookStat(val uri: String, val title: String, val author: String, val seconds: Long, val pages: Int, val last: Long, val finished: Boolean)

    /** The last [n] days, today last, with zeros for the days of no reading. */
    fun days(c: Context, n: Int): List<Day> {
        val p = sp(c)
        val cal = Calendar.getInstance()
        val out = ArrayList<Day>()
        for (i in n - 1 downTo 0) {
            val k = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -i) }
            val d = dayFmt.format(k.time)
            out.add(Day(d, p.getLong("ds_$d", 0L), p.getInt("dp_$d", 0)))
        }
        return out
    }

    fun books(c: Context): List<BookStat> {
        val p = sp(c)
        return p.getStringSet("books", emptySet()).orEmpty().map { u ->
            val meta = p.getString("bm_$u", "").orEmpty().split('\u0001')
            BookStat(u, meta.getOrElse(0) { "" }.ifBlank { u.substringAfterLast('/') }, meta.getOrElse(1) { "" }, p.getLong("bs_$u", 0L), p.getInt("bp_$u", 0), p.getLong("bl_$u", 0L), p.getBoolean("bf_$u", false))
        }.filter { it.seconds > 0 || it.pages > 0 || it.finished }.sortedByDescending { it.last }
    }

    fun totalSeconds(c: Context): Long = books(c).sumOf { it.seconds }

    /** Days in a row with reading, counted back from today (a day with no reading yet today does not break it). */
    fun streak(c: Context): Int {
        val p = sp(c)
        val cal = Calendar.getInstance()
        var n = 0
        var first = true
        while (true) {
            val d = dayFmt.format(cal.time)
            if (p.getLong("ds_$d", 0L) > 0L) n++ else if (!first) break
            first = false
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (n > 3650) break
        }
        return n
    }

    fun time(sec: Long): String {
        val m = sec / 60
        return if (m >= 60) tr("${m / 60} h ${m % 60} min", "${m / 60} sa ${m % 60} dk") else if (m >= 1) tr("$m min", "$m dk") else tr("${sec} s", "$sec sn")
    }
}

/** The statistics page of the book reader: today, the week, all the time, the pages, the books finished, the streak, a bar for each day and a line for each book. */
@Composable
internal fun StatsPage(ink: Color, sub: Color, accent: Color, onShelf: (String) -> Boolean) {
    val c = LocalContext.current
    val days = remember { EbookStats.days(c, 7) }
    val books = remember { EbookStats.books(c) }
    val total = books.sumOf { it.seconds }
    val week = days.sumOf { it.sec }
    val pages = books.sumOf { it.pages }
    val finished = books.count { it.finished }
    val streak = remember { EbookStats.streak(c) }
    @Composable
    fun Card(label: String, value: String, modifier: Modifier) {
        Column(modifier.clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .07f)).padding(12.dp)) {
            Text(value, color = ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = sub, fontSize = 12.sp, maxLines = 1)
        }
    }
    LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(tr("Today", "Bugün"), EbookStats.time(days.last().sec), Modifier.weight(1f))
                Card(tr("This week", "Bu hafta"), EbookStats.time(week), Modifier.weight(1f))
                Card(tr("All time", "Toplam"), EbookStats.time(total), Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(tr("Pages", "Sayfa"), "$pages", Modifier.weight(1f))
                Card(tr("Finished", "Biten"), "$finished", Modifier.weight(1f))
                Card(tr("Streak", "Seri"), tr("$streak days", "$streak gün"), Modifier.weight(1f))
            }
        }
        item {
            // a bar for each of the last seven days
            val max = (days.maxOfOrNull { it.sec } ?: 0L).coerceAtLeast(60L)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(ink.copy(alpha = .07f)).padding(12.dp)) {
                Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                    val bw = size.width / (days.size * 2f)
                    days.forEachIndexed { i, d ->
                        val h = (size.height * d.sec / max).coerceAtLeast(if (d.sec > 0) 4f else 2f)
                        drawRect(if (i == days.lastIndex) accent else ink.copy(alpha = .35f), Offset(bw * (i * 2 + .5f), size.height - h), Size(bw, h))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    days.forEach { d ->
                        val cal = Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(d.date) ?: Date() }
                        Text(SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time).take(3), color = sub, fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        item { Text(tr("Books", "Kitaplar"), color = ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
        if (books.isEmpty()) item { Text(tr("Nothing read yet. The time and the pages are counted while you read.", "Henüz okuma yok. Süre ve sayfalar okurken sayılır."), color = sub, fontSize = 13.sp) }
        items(books.size) { i ->
            val b = books[i]
            val here = onShelf(b.uri)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ink.copy(alpha = .05f)).padding(10.dp)) {
                Text(b.title, color = ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(b.author.ifBlank { null }, EbookStats.time(b.seconds), tr("${b.pages} pages", "${b.pages} sayfa"),
                        if (b.finished) tr("finished", "bitti") else null, if (!here) tr("not on the shelf", "rafta değil") else null).joinToString(" · "),
                    color = sub, fontSize = 12.sp, maxLines = 2,
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}
