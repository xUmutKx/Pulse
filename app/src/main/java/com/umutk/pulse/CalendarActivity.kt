@file:OptIn(ExperimentalMaterial3Api::class)

package com.umutk.pulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

/** "Pulse Calendar": standalone launcher app on the system CalendarContract provider (modelled on Fossify Calendar). */
class CalendarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this)
        CalPrefs.load(this); CalPrefs.apply()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0) // standalone start: no open animation
        paintCal()
        setContent { LookHost(transparent = false, lookId = if (CalPrefs.amoled && CalPrefs.look == "material") "amoled" else CalPrefs.look) { CalTheme { LookFade { CalRoot() } } } }
    }

    // warm start / returning from another Pulse activity: put this app's look back before the first frame
    private fun paintCal() = paintWindow((CalPrefs.amoled && CalPrefs.look == "material") || CalPrefs.dark == "dark" || (CalPrefs.dark != "light" && (resources.configuration.uiMode and 0x30) == 0x20), CalPrefs.amoled && CalPrefs.look == "material", if (CalPrefs.look != "material") Look.theme else null)
    private fun enter() = AppLookGuard.enter({ CalPrefs.apply() }, { paintCal() })
    override fun onStart() { enter(); super.onStart() }
    override fun onResume() { enter(); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); AppLookGuard.leave(this) }
}

@Composable
fun CalTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    if (CalPrefs.look != "material") { // the real look (colours from LookHost), only the corners follow it
        val r = RoundedCornerShape(T.radius)
        MaterialTheme(colorScheme = MaterialTheme.colorScheme, shapes = Shapes(r, r, r, r, r)) { Surface(color = MaterialTheme.colorScheme.background, content = content) }
        return
    }
    val dark = if (CalPrefs.amoled) true else when (CalPrefs.dark) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val acc = Color(CalPrefs.accent)
    var cs = if (CalPrefs.dynamic && android.os.Build.VERSION.SDK_INT >= 31) (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx))
    else {
        val pc = if (dark) lerp(acc, Color.Black, .55f) else lerp(acc, Color.White, .8f)
        (if (dark) darkColorScheme() else lightColorScheme()).copy(primary = if (dark) lerp(acc, Color.White, .35f) else acc, onPrimary = if (dark) Color.Black else Color.White,
            primaryContainer = pc, secondaryContainer = pc, onPrimaryContainer = if (dark) Color.White else Color.Black, onSecondaryContainer = if (dark) Color.White else Color.Black)
    }
    if (dark && CalPrefs.amoled) cs = cs.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color.Black, surfaceContainerLowest = Color.Black, surfaceTint = Color.Black,
        surfaceContainerLow = Color.Black, surfaceContainer = Color.Black, surfaceContainerHigh = Color.Black, surfaceContainerHighest = Color.Black,
        onSurface = Color(0xFFEDEDED), onBackground = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFFC4C4C4), outlineVariant = Color(0xFF3A3A3A))
    MaterialTheme(colorScheme = cs) { Surface(color = MaterialTheme.colorScheme.background, content = content) }
}

private val PERMS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

@Composable
fun CalRoot() {
    val ctx = LocalContext.current
    fun has() = PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember { mutableStateOf(has()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = has() }
    LaunchedEffect(Unit) { if (!granted) ask.launch(PERMS) }
    if (!granted) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CalIc("calendar", Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            FilledTonalButton({ ask.launch(PERMS) }) { CalIc("lock", Icons.Outlined.Lock); Spacer(Modifier.width(8.dp)); Text(ct("Allow calendar access")) }
        }
        return
    }
    CalMain()
}

@Composable
fun CalMain() {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(CalPrefs.startView) }
    var anchor by remember { mutableStateOf(if (!CalPrefs.goToday && CalPrefs.lastDay != 0L) LocalDate.ofEpochDay(CalPrefs.lastDay) else LocalDate.now()) }
    DisposableEffect(Unit) { onDispose { CalPrefs.lastDay = anchor.toEpochDay(); CalPrefs.save() } }
    var refresh by remember { mutableIntStateOf(0) }
    var cals by remember { mutableStateOf(emptyList<CalCal>()) }
    var evs by remember { mutableStateOf(emptyList<CalEv>()) }
    var query by remember { mutableStateOf("") }
    var sOpen by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    var found by remember { mutableStateOf(emptyList<CalEv>()) }
    var detail by remember { mutableStateOf<CalEv?>(null) }
    var edit by remember { mutableStateOf<EditState?>(null) }
    var screen by remember { mutableStateOf("") } // settings / cals
    var viewMenu by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        var l = withContext(Dispatchers.IO) { CalData.calendars(ctx) }
        if (l.none { it.writable }) { withContext(Dispatchers.IO) { CalData.addLocal(ctx, ct("Local calendar")) }; l = withContext(Dispatchers.IO) { CalData.calendars(ctx) } }
        cals = l
    }
    val (from, to) = when (mode) {
        "month" -> gridStartOf(anchor).let { it to it.plusDays(42) }
        "week" -> weekStartOf(anchor).let { it to it.plusDays(7) }
        "day" -> anchor to anchor.plusDays(1)
        "year" -> LocalDate.of(anchor.year, 1, 1).let { it to it.plusYears(1) }
        else -> anchor to anchor.plusDays(365)
    }
    LaunchedEffect(from, to, refresh, CalPrefs.hidden) { evs = withContext(Dispatchers.IO) { CalData.events(ctx, from.startMs(), to.startMs()) } }
    var hol by remember { mutableStateOf(emptyList<CalEv>()) }
    LaunchedEffect(from, to, refresh, CalPrefs.holShow, CalPrefs.holCountry, CalPrefs.holPublic, CalPrefs.holObs) { hol = withContext(Dispatchers.IO) { CalHolidays.events(ctx, from.startMs(), to.startMs()) } }
    val shown = remember(evs, hol) { if (hol.isEmpty()) evs else (evs + hol).sortedBy { it.start } }
    LaunchedEffect(query, refresh, CalPrefs.hidden, CalPrefs.showDeclined, CalPrefs.colorSrc, CalPrefs.holShow, CalPrefs.holCountry, CalPrefs.holPublic, CalPrefs.holObs) {
        val q = query.trim()
        found = if (q.isEmpty()) emptyList() else withContext(Dispatchers.IO) {
            val a = LocalDate.now().minusYears(1).startMs(); val b = LocalDate.now().plusYears(2).startMs()
            (CalData.events(ctx, a, b) + CalHolidays.events(ctx, a, b))
                .filter { it.title.contains(q, true) || it.loc.contains(q, true) || it.desc.contains(q, true) }
        }
    }

    fun nav(n: Int) { anchor = when (mode) { "week" -> anchor.plusWeeks(n.toLong()); "day" -> anchor.plusDays(n.toLong()); "year" -> anchor.plusYears(n.toLong()); else -> anchor.plusMonths(n.toLong()) } }
    fun newEvent(at: LocalDateTime) {
        val c = cals.firstOrNull { it.id == CalPrefs.defCal && it.writable } ?: cals.firstOrNull { it.writable }
        edit = EditState(0, c?.id ?: -1, "", "", "", false, at, at.plusMinutes(CalPrefs.defDur.toLong()), null, if (CalPrefs.defRem < 0) emptyList() else listOf(CalPrefs.defRem))
    }
    fun editEvent(e: CalEv) {
        val s = CalData.seriesStart(ctx, e.id) ?: e.start; val en = s + (e.end - e.start)
        edit = EditState(e.id, e.calId, e.title, e.loc, e.desc, e.allDay, msToLdt(s), msToLdt(if (e.allDay) en - 1 else en), e.rrule, CalData.reminders(ctx, e.id))
    }

    val e0 = edit
    if (e0 != null) {
        EventEditor(e0, cals, onSave = { s ->
            val all = s.allDay
            val st = if (all) s.start.toLocalDate().startMs() else ldtToMs(s.start)
            val en = if (all) s.end.toLocalDate().plusDays(1).startMs() else ldtToMs(s.end)
            if (CalData.save(ctx, s.id, s.calIdS, s.title.trim(), s.loc.trim(), s.desc.trim(), all, st, en, s.rrule, s.reminders)) { edit = null; refresh++ }
            else android.widget.Toast.makeText(ctx, ct("Failed"), android.widget.Toast.LENGTH_SHORT).show()
        }, onClose = { edit = null })
        return
    }
    if (screen == "settings") { CalSettings(cals, { screen = "" }, { refresh++ }); return }

    BackHandler(sOpen) { sOpen = false; query = ""; kb?.hide() }
    BackHandler(results) { results = false }
    val swipe = Modifier.pointerInput(mode) {
        var dx = 0f
        detectHorizontalDragGestures(onDragStart = { dx = 0f }, onDragEnd = { if (dx > 140) nav(-1) else if (dx < -140) nav(1) }) { _, d -> dx += d }
    }
    Scaffold(
        topBar = {
            if (results) CalBar(title = { Text(ct("Results") + ": " + query.trim(), maxLines = 1) },
                nav = { IconButton({ results = false }) { CalIc("back", Icons.AutoMirrored.Outlined.ArrowBack, ct("Cancel")) } })
            else CalBar(title = {
                Text(when (mode) {
                    "week" -> fmtDate(weekStartOf(anchor), "d MMM") + " - " + fmtDate(weekStartOf(anchor).plusDays(6), "d MMM")
                    "day" -> fmtDate(anchor, "EEE, d MMM yyyy")
                    "year" -> "${anchor.year}"
                    else -> fmtDate(anchor, "MMMM yyyy")
                }, maxLines = 1)
            }, actions = {
                IconButton({ nav(-1) }) { CalIc("back", Icons.Outlined.ChevronLeft) }
                IconButton({ nav(1) }) { CalIc("forward", Icons.Outlined.ChevronRight) }
                IconButton({ anchor = LocalDate.now() }) { CalIc("calendar", Icons.Outlined.Today, ct("Today")) }
                IconButton({ sOpen = true }) { CalIc("search", Icons.Outlined.Search, ct("Search")) }
                Box {
                    IconButton({ viewMenu = true }) { CalIc("more", Icons.Outlined.MoreVert) }
                    DropdownMenu(viewMenu, { viewMenu = false }) {
                        listOf("month" to ("Month" to Icons.Outlined.CalendarViewMonth), "week" to ("Week" to Icons.Outlined.CalendarViewWeek), "day" to ("Day" to Icons.Outlined.CalendarViewDay),
                            "year" to ("Year" to Icons.Outlined.CalendarToday), "agenda" to ("Agenda" to Icons.Outlined.ViewAgenda)).forEach { (k, v) ->
                            val ik = when (k) { "month", "year" -> "calendar"; "week" -> "table"; "day" -> "clock"; else -> "details" }
                            DropdownMenuItem({ Text(ct(v.first), fontWeight = if (k == mode) FontWeight.Bold else FontWeight.Normal) }, { mode = k; viewMenu = false }, leadingIcon = { CalIc(ik, v.second) })
                        }
                        HorizontalDivider()
                        DropdownMenuItem({ Text(ct("Calendars")) }, { screen = "cals"; viewMenu = false }, leadingIcon = { CalIc("users", Icons.Outlined.Palette) })
                        DropdownMenuItem({ Text(ct("Settings")) }, { screen = "settings"; viewMenu = false }, leadingIcon = { CalIc("settings", Icons.Outlined.Settings) })
                    }
                }
            })
        },
        floatingActionButton = {
            if (!sOpen && !results) FloatingActionButton({ newEvent(if (anchor == LocalDate.now()) LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) else anchor.atTime(9, 0)) }, shape = calFabShape() ?: FloatingActionButtonDefaults.shape,
                containerColor = if (T.id == "amoled") fabFill() else FloatingActionButtonDefaults.containerColor,
                contentColor = if (T.id == "amoled") fabMark() else contentColorFor(FloatingActionButtonDefaults.containerColor)) { CalIc("add", Icons.Outlined.Add, ct("New event")) }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize()) {
        Box(Modifier.padding(pad).padding(bottom = if (sOpen || results) 88.dp else 16.dp).fillMaxSize().then(if (!results) swipe else Modifier)) {
            if (results) SearchResults(found) { detail = it }
            else when (mode) {
                "month" -> MonthView(anchor, shown, { anchor = it; mode = "day" }, { detail = it })
                "week" -> TimeGrid((0..6).map { weekStartOf(anchor).plusDays(it.toLong()) }, shown, { detail = it }, { newEvent(it) }, { anchor = it; mode = "day" })
                "day" -> TimeGrid(listOf(anchor), shown, { detail = it }, { newEvent(it) }, {})
                "year" -> YearView(anchor.year, shown, { anchor = it; mode = "month" }, { anchor = it; mode = "day" })
                else -> AgendaView(shown) { detail = it }
            }
        }
        if (!results) Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(pad).consumeWindowInsets(pad).imePadding().padding(start = 16.dp, end = if (sOpen) 16.dp else 88.dp, bottom = 16.dp)) {
            if (sOpen && query.isNotBlank()) Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
                Column { found.take(6).forEach { e ->
                    Row(Modifier.fillMaxWidth().clickable { detail = e }.padding(14.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(evColor(e))); Spacer(Modifier.width(12.dp))
                        Column { Text(e.title.ifEmpty { ct("Untitled") }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(fmtDate(msToLdt(e.start).toLocalDate(), "EEE, d MMM") + "  ·  " + evTimeText(e), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } }
                    if (found.isEmpty()) Text(ct("No events"), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(Modifier.fillMaxWidth().height(56.dp), shape = calSearchShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp) {
                if (!sOpen) Row(Modifier.fillMaxSize().clickable { sOpen = true }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    CalIc("search", Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(12.dp))
                    Text(ct("Search events"), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                } else {
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        CalIc("search", Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextField(query, { query = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true, placeholder = { Text(ct("Search events")) },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) { kb?.hide(); results = true } }),
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
                        IconButton({ if (query.isNotEmpty()) query = "" else { sOpen = false; kb?.hide() } }) { CalIc("close", Icons.Outlined.Close, ct("Clear")) }
                    }
                }
            }
        }
        }
    }

    if (screen == "cals") CalendarsDialog(cals, { refresh++ }) { screen = "" }

    detail?.let { e ->
        val cal = cals.firstOrNull { it.id == e.calId }
        AlertDialog(onDismissRequest = { detail = null },
            icon = { Box(Modifier.size(14.dp).clip(CircleShape).background(evColor(e))) },
            title = { Text(e.title.ifEmpty { ct("Untitled") }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(fmtDate(msToLdt(e.start).toLocalDate()) + "\n" + evTimeText(e))
                    if (e.loc.isNotEmpty()) Row { CalIc("compass", Icons.Outlined.Place, null, size = 18.dp); Spacer(Modifier.width(6.dp)); Text(e.loc) }
                    if (e.desc.isNotEmpty()) Text(e.desc, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.calId == CalHolidays.CAL_ID) Text(ct("Holidays"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cal != null) Text(cal.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { if (cal?.writable == true) IconButton({ editEvent(e); detail = null }) { CalIc("edit", Icons.Outlined.Edit, ct("Edit event")) } else IconButton({ detail = null }) { CalIc("check", Icons.Outlined.Check, ct("OK")) } },
            dismissButton = { if (cal?.writable == true) IconButton({ confirmDel = true }) { CalIc("delete", Icons.Outlined.Delete, ct("Delete"), tint = MaterialTheme.colorScheme.error) } })
        if (confirmDel) com.umutk.pulse.WinAlert(onDismissRequest = { confirmDel = false }, title = { Text(ct("Delete event?")) },
            confirmButton = { IconButton({ CalData.delete(ctx, e.id); confirmDel = false; detail = null; refresh++ }) { CalIc("delete", Icons.Outlined.Delete, ct("Delete"), tint = MaterialTheme.colorScheme.error) } },
            dismissButton = { IconButton({ confirmDel = false }) { CalIc("close", Icons.Outlined.Close, ct("Cancel")) } })
    }
}
