package com.umutk.pulse.extras.launcher

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private class EventHit(val id: Long, val title: String, val start: Long)

/** The events of the calendar whose title has [q] (from a month back on, the soonest first), a few of them; empty without the calendar permission. */
private fun findEvents(c: Context, q: String): List<EventHit> {
    if (q.trim().length < 2 || c.checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
    return runCatching {
        val out = ArrayList<EventHit>()
        c.contentResolver.query(CalendarContract.Events.CONTENT_URI, arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART),
            "${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DELETED} = 0",
            arrayOf("%${q.trim()}%", (System.currentTimeMillis() - 30L * 86_400_000L).toString()), "${CalendarContract.Events.DTSTART} ASC")?.use { cur ->
            while (cur.moveToNext() && out.size < 5) out += EventHit(cur.getLong(0), cur.getString(1).orEmpty(), cur.getLong(2))
        }
        out
    }.getOrDefault(emptyList())
}

/** The calendar events that match a search of the launcher: a title and its date; a tap opens the event in the calendar. */
@Composable
fun CalendarResults(query: String, fg: Color, sub: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val hits by produceState(emptyList<EventHit>(), query) {
        delay(200)
        value = withContext(Dispatchers.IO) { findEvents(ctx, query) }
    }
    if (hits.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        val fmt = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        hits.forEach { h ->
            Column(Modifier.fillMaxWidth().clickable {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, h.id)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }.padding(vertical = 8.dp)) {
                Text(h.title.ifBlank { "-" }, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmt.format(Date(h.start)), color = sub, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}
