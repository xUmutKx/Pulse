package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

fun weekStartOf(d: LocalDate): LocalDate = d.minusDays(((d.dayOfWeek.value - CalPrefs.weekStart + 7) % 7).toLong())
fun gridStartOf(d: LocalDate): LocalDate = weekStartOf(d.withDayOfMonth(1))
fun dayName(d: DayOfWeek, long: Boolean = false): String = d.getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, calLocale())
fun fmtTime(ms: Long): String = msToLdt(ms).toLocalTime().let { fmtTime(it) }
fun fmtTime(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern(if (CalPrefs.h24) "HH:mm" else "h:mm a", calLocale()))
fun fmtDate(d: LocalDate, p: String = "EEE, d MMM yyyy"): String = d.format(DateTimeFormatter.ofPattern(p, calLocale()))
fun isWeekend(d: LocalDate) = CalPrefs.hlWeekend && d.dayOfWeek.value >= 6
fun weekNo(d: LocalDate): Int = d.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR)
fun calSearchShape(): Shape = RoundedCornerShape(if (T.material || lookId == "oneui" || lookId == "coloros") 28.dp else calR())
fun onColor(c: Color) = if (c.luminance() > 0.55f) Color.Black else Color.White
fun evColor(e: CalEv): Color = if (e.color != 0) Color(e.color) else if (T.material) Color(CalPrefs.accent) else T.accent.copy(alpha = 1f)

// ---- era chrome: every non-Material look draws its own icons, bars, corners and event blocks
private val outlook get() = !T.material && (T.id == "win7" || T.id == "xp")
private val lookId get() = if (T.material) "material" else T.id
fun calR() = when (lookId) { "win95", "win10" -> 0.dp; "xp", "win7" -> 2.dp; "win11" -> 5.dp; "oneui", "coloros" -> 8.dp; else -> 3.dp }
fun calDayShape(): Shape = when (lookId) { "material", "oneui", "coloros" -> CircleShape; "win11" -> RoundedCornerShape(6.dp); "win10", "win95" -> RectangleShape; else -> RoundedCornerShape(2.dp) }
fun calFabShape(): Shape? = when (lookId) { "material" -> null; "win95", "win10" -> RectangleShape; "xp", "win7" -> RoundedCornerShape(3.dp); "win11" -> RoundedCornerShape(8.dp); "oneui" -> CircleShape; else -> RoundedCornerShape(20.dp) }
fun evFill(c: Color) = if (outlook) lerp(c, Color.White, .78f) else c
fun evText(c: Color) = if (outlook) Color(0xFF1B1B1B) else onColor(c)
fun Modifier.evFrame(c: Color): Modifier = if (outlook) border(1.dp, c, RoundedCornerShape(calR())) else if (!T.material && T.bevel) border(1.dp, Color.Black) else this
@Composable fun calLine(): Color = if (T.material) MaterialTheme.colorScheme.outlineVariant else T.line
/** Weekday header band: grey bevel (95), Outlook blue (XP), Aero (7), flat for the rest. */
fun Modifier.calHeader(): Modifier = when (lookId) {
    "win95" -> background(T.bg).border(1.dp, T.line)
    "xp" -> background(Brush.verticalGradient(listOf(Color(0xFFE3EDFB), Color(0xFFC3D5F0))))
    "win7" -> background(Brush.verticalGradient(listOf(Color(0xFFFDFEFF), Color(0xFFDCE7F5))))
    "win10" -> background(T.accent.copy(alpha = .12f))
    else -> this
}

/** Icon through the look's own pack (PIcon) for every look but Material, which keeps its vector. */
@Composable
fun CalIc(key: String, vec: ImageVector, desc: String? = null, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp? = null) {
    if (T.material) Icon(vec, desc, if (size != null) modifier.size(size) else modifier, tint) else PIcon(key, tint, size ?: 24.dp, modifier)
}

/** App bar per look; Material keeps TopAppBar. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CalBar(title: @Composable () -> Unit, nav: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    if (T.material) { TopAppBar(title = title, navigationIcon = { nav?.invoke() }, actions = actions); return }
    val id = T.id
    val bar: Modifier = when (id) {
        "win95" -> Modifier.background(T.bg).drawBehind {
            drawLine(Color.White, Offset(0f, 1f), Offset(size.width, 1f), 2f)
            drawLine(Color(0xFF808080), Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), 2f)
            drawLine(Color.Black, Offset(0f, size.height - .5f), Offset(size.width, size.height - .5f), 1f) }
        "xp" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFE3EDFB), Color(0xFFB9CFF0))))
        "win7" -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFFDFEFF), Color(0xFFD5E2F3)))).drawBehind { drawLine(Color(0xFF9DB2CF), Offset(0f, size.height), Offset(size.width, size.height), 1f) }
        "win10" -> Modifier.background(T.accent.copy(alpha = 1f))
        "win11" -> Modifier.padding(8.dp, 6.dp).clip(RoundedCornerShape(8.dp)).background(T.panel).border(1.dp, T.line, RoundedCornerShape(8.dp))
        "coloros" -> Modifier.padding(8.dp, 6.dp).clip(RoundedCornerShape(24.dp)).background(T.panel)
        else -> Modifier.padding(vertical = 8.dp)
    }
    val fg = if (id == "win10") Color.White else T.text
    val tb = T.title
    CompositionLocalProvider(LocalContentColor provides fg) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            if (tb != null) Row(Modifier.fillMaxWidth().background(tb).padding(8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
                CalIc("calendar", Icons.Outlined.CalendarMonth, tint = T.titleText, size = 16.dp)
                Spacer(Modifier.width(6.dp)); Text(ct("Calendar"), color = T.titleText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth().then(bar).heightIn(min = 48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                nav?.invoke()
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(fontSize = if (id == "oneui") 24.sp else 17.sp, fontWeight = if (id == "oneui" || id == "coloros") FontWeight.Bold else FontWeight.Medium)) { title() }
                }
                actions()
            }
        }
    }
}

fun eventDays(evs: List<CalEv>): Set<LocalDate> {
    val out = HashSet<LocalDate>()
    for (e in evs) {
        var d = msToLdt(e.start).toLocalDate(); val last = msToLdt(maxOf(e.end - 1, e.start)).toLocalDate(); var n = 0
        while (!d.isAfter(last) && n++ < 400) { out.add(d); d = d.plusDays(1) }
    }
    return out
}

fun evTimeText(e: CalEv): String {
    if (e.allDay) return ct("All day")
    val a = msToLdt(e.start); val b = msToLdt(e.end)
    return if (a.toLocalDate() == b.toLocalDate() || e.end == e.start) fmtTime(e.start) + (if (e.end > e.start) " - " + fmtTime(e.end) else "")
    else fmtDate(a.toLocalDate(), "d MMM") + " " + fmtTime(e.start) + " - " + fmtDate(b.toLocalDate(), "d MMM") + " " + fmtTime(e.end)
}

@Composable
fun EventChip(e: CalEv, modifier: Modifier = Modifier, small: Boolean = true, onClick: () -> Unit) {
    val c = evColor(e)
    Text(e.title.ifEmpty { ct("Untitled") }, modifier = modifier.fillMaxWidth().padding(vertical = 0.5.dp).clip(RoundedCornerShape(calR())).background(evFill(c)).evFrame(c).clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 1.dp),
        color = evText(c), fontSize = if (small) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, lineHeight = 12.sp)
}

@Composable
fun MonthView(anchor: LocalDate, evs: List<CalEv>, onDay: (LocalDate) -> Unit, onEv: (CalEv) -> Unit) {
    val start = gridStartOf(anchor); val today = LocalDate.now()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().calHeader().padding(vertical = 4.dp)) {
            if (CalPrefs.weekNums) Spacer(Modifier.width(22.dp))
            for (i in 0..6) Text(dayName(start.plusDays(i.toLong()).dayOfWeek), Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (w in 0..5) Row(Modifier.fillMaxWidth().weight(1f)) {
            if (CalPrefs.weekNums) Text("${weekNo(start.plusDays((w * 7).toLong()))}", Modifier.width(22.dp).padding(top = 4.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            for (i in 0..6) {
                val d = start.plusDays((w * 7 + i).toLong()); val inMonth = d.month == anchor.month
                if (CalPrefs.monthStyle != "grid") {
                    // no frames: a day is its number, the day's events are dots under it; "clean" has a thin line between the weeks, "round" puts every day in a soft circle
                    val round = CalPrefs.monthStyle == "round"
                    val cs = MaterialTheme.colorScheme
                    val day = evs.filter { it.on(d) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
                    Column(Modifier.weight(1f).fillMaxHeight().then(if (!round) Modifier.drawBehind { drawLine(Color.Gray.copy(alpha = .25f), Offset(0f, 0f), Offset(size.width, 0f), 1f) } else Modifier)
                        .clickable { onDay(d) }.padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(if (round) 38.dp else 34.dp).clip(CircleShape)
                            .background(if (d == today) cs.primary else if (round) cs.onSurface.copy(alpha = if (inMonth) .07f else .03f) else Color.Transparent),
                            contentAlignment = Alignment.Center) {
                            Text("${d.dayOfMonth}", fontSize = 17.sp, fontWeight = if (d == today) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (d == today) cs.onPrimary else if (inMonth) (if (isWeekend(d)) cs.primary else cs.onSurface) else cs.onSurface.copy(alpha = .38f))
                        }
                        Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            day.take(3).forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(evColor(it))) }
                        }
                    }
                    continue
                }
                Column(Modifier.weight(1f).fillMaxHeight().then(if (isWeekend(d)) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .07f)) else Modifier).border(0.5.dp, calLine()).clickable { onDay(d) }.padding(1.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).size(22.dp).clip(calDayShape()).background(if (d == today) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}", fontSize = 11.sp, color = if (d == today) MaterialTheme.colorScheme.onPrimary else if (inMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
                    }
                    val day = evs.filter { it.on(d) }.sortedWith(compareBy({ !it.allDay }, { it.start }))
                    if (!CalPrefs.monthTitles) Row(Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        day.take(4).forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(evColor(it))) }
                    } else {
                    day.take(3).forEach { EventChip(it) { onEv(it) } }
                    if (day.size > 3) Text("+${day.size - 3}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp))
                    }
                }
            }
        }
    }
}

private fun lanes(list: List<CalEv>): List<Triple<CalEv, Int, Int>> {
    val res = ArrayList<Triple<CalEv, Int, Int>>(); var cluster = ArrayList<Pair<CalEv, Int>>(); val ends = ArrayList<Long>()
    fun flush() { val n = ends.size; cluster.forEach { res.add(Triple(it.first, it.second, n)) }; cluster = ArrayList(); ends.clear() }
    for (e in list.sortedBy { it.start }) {
        if (ends.isNotEmpty() && ends.all { it <= e.start }) flush()
        var l = ends.indexOfFirst { it <= e.start }; val end = maxOf(e.end, e.start + 15 * 60000L)
        if (l < 0) { ends.add(end); l = ends.lastIndex } else ends[l] = end
        cluster.add(e to l)
    }
    flush(); return res
}

/** Week (7 days) and day (1 day) share this hourly grid. */
@Composable
fun TimeGrid(days: List<LocalDate>, evs: List<CalEv>, onEv: (CalEv) -> Unit, onAdd: (LocalDateTime) -> Unit, onDay: (LocalDate) -> Unit) {
    val hh = CalPrefs.weekZoom.dp; val today = LocalDate.now(); val lw = 40.dp
    val px = with(LocalDensity.current) { (hh * CalPrefs.weekHour).roundToPx() }
    val scroll = rememberScrollState(px)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().calHeader().padding(vertical = 4.dp)) {
            Spacer(Modifier.width(lw))
            days.forEach { d ->
                Column(Modifier.weight(1f).then(if (isWeekend(d)) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .07f)) else Modifier).clickable { onDay(d) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dayName(d.dayOfWeek), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(26.dp).clip(calDayShape()).background(if (d == today) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}", fontSize = 13.sp, color = if (d == today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (evs.any { it.allDay }) Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(lw))
            days.forEach { d -> Column(Modifier.weight(1f).padding(horizontal = 1.dp)) { evs.filter { it.allDay && it.on(d) }.forEach { EventChip(it, small = days.size > 1) { onEv(it) } } } }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Column(Modifier.width(lw)) { for (h in 0..23) Box(Modifier.height(hh)) { Text(if (h == 0) "" else fmtTime(LocalTime.of(h, 0)).replace(" ", "\n"), fontSize = 9.sp, lineHeight = 10.sp, modifier = Modifier.padding(start = 2.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }; Spacer(Modifier.height(88.dp)) }
            days.forEach { d ->
                val ds = d.startMs(); val de = d.plusDays(1).startMs()
                val timed = remember(evs, d) { lanes(evs.filter { !it.allDay && it.on(d) }) }
                BoxWithConstraints(Modifier.weight(1f).height(hh * 24).then(if (outlook && d == today) Modifier.background(Color(0xFFFFF4C4).copy(alpha = .6f)) else if (isWeekend(d)) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = .07f)) else Modifier).border(0.5.dp, calLine().copy(alpha = .5f))) {
                    Column { for (h in 0..23) Box(Modifier.fillMaxWidth().height(hh).border(0.25.dp, calLine().copy(alpha = .4f)).clickable { onAdd(d.atTime(h, 0)) }) }
                    for ((e, l, n) in timed) {
                        val s = maxOf(e.start, ds); val en = minOf(maxOf(e.end, e.start + 1), de)
                        val c = evColor(e); val w: Dp = maxWidth * (1f / n)
                        Column(Modifier.offset(x = w * l, y = hh * ((s - ds) / 3600000f)).width(w - 1.dp).height(maxOf(hh * ((en - s) / 3600000f), 20.dp))
                            .clip(RoundedCornerShape(calR())).background(evFill(c)).evFrame(c).clickable { onEv(e) }.padding(2.dp)) {
                            Text(e.title.ifEmpty { ct("Untitled") }, color = evText(c), fontSize = 10.sp, lineHeight = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (d == today) {
                        val nowH = LocalTime.now().let { it.hour + it.minute / 60f }
                        Box(Modifier.offset(y = hh * nowH).fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.error))
                    }
                }
            }
        }
    }
}

@Composable
fun YearView(year: Int, evs: List<CalEv>, onMonth: (LocalDate) -> Unit, onDay: (LocalDate) -> Unit) {
    val marked = remember(evs) { eventDays(evs) }; val today = LocalDate.now()
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize().padding(horizontal = 6.dp)) {
        items(12) { m ->
            val first = LocalDate.of(year, m + 1, 1); val start = gridStartOf(first)
            Column(Modifier.padding(4.dp)) {
                Text(first.month.getDisplayName(TextStyle.SHORT, calLocale()), Modifier.clickable { onMonth(first) }.padding(vertical = 2.dp), fontWeight = FontWeight.Medium, color = if (first.month == today.month && year == today.year) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                for (w in 0..5) Row {
                    for (i in 0..6) {
                        val d = start.plusDays((w * 7 + i).toLong())
                        Box(Modifier.weight(1f).height(18.dp).clip(calDayShape()).background(if (d == today) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { onDay(d) }, contentAlignment = Alignment.Center) {
                            if (d.month == first.month) Text("${d.dayOfMonth}", fontSize = 9.sp, fontWeight = if (d in marked) FontWeight.Bold else FontWeight.Normal,
                                color = if (d == today) MaterialTheme.colorScheme.onPrimary else if (d in marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaView(evs: List<CalEv>, onEv: (CalEv) -> Unit) {
    val groups = remember(evs) { eventDays(evs).sorted().map { d -> d to evs.filter { it.on(d) }.sortedWith(compareBy({ !it.allDay }, { it.start })) } }
    if (groups.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CalIc("calendar", Icons.Outlined.EventBusy, ct("No events"), tint = MaterialTheme.colorScheme.outline, size = 56.dp) }; return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
        groups.forEach { (d, list) ->
            item(key = "h$d") { Text(fmtDate(d, "EEEE, d MMMM yyyy"), Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 4.dp), fontWeight = FontWeight.Medium,
                color = if (d == LocalDate.now()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            items(list, key = { "e$d${it.id}${it.start}" }) { e ->
                Row(Modifier.fillMaxWidth().clickable { onEv(e) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(if (T.material) 2.dp else calR())).background(evColor(e)))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.title.ifEmpty { ct("Untitled") }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text(evTimeText(e) + if (e.loc.isNotEmpty()) "  ·  " + e.loc else "", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Full results page: events (and holidays) grouped by month. */
@Composable
fun SearchResults(evs: List<CalEv>, onEv: (CalEv) -> Unit) {
    val groups = remember(evs) { evs.sortedBy { it.start }.groupBy { java.time.YearMonth.from(msToLdt(it.start)) }.toList() }
    if (groups.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CalIc("search", Icons.Outlined.SearchOff, ct("No events"), tint = MaterialTheme.colorScheme.outline, size = 56.dp) }; return }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
        groups.forEach { (ym, list) ->
            item(key = "m$ym") { Text(ym.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", calLocale())), Modifier.fillMaxWidth().padding(16.dp, 14.dp, 16.dp, 4.dp), fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
            items(list, key = { "r${it.calId}_${it.id}_${it.start}" }) { e ->
                Row(Modifier.fillMaxWidth().clickable { onEv(e) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(if (T.material) 2.dp else calR())).background(evColor(e)))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.title.ifEmpty { ct("Untitled") }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                        Text(fmtDate(msToLdt(e.start).toLocalDate(), "EEE, d MMM") + "  ·  " + evTimeText(e), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
