@file:OptIn(ExperimentalMaterial3Api::class)

package com.umutk.pulse

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

/** Editor state. All-day events: [end] is the last day (inclusive) in the UI. */
class EditState(val id: Long, calId: Long, title: String, loc: String, desc: String, allDay: Boolean, start: LocalDateTime, end: LocalDateTime, var rrule: String?, rem: List<Int>) {
    var title by mutableStateOf(title); var loc by mutableStateOf(loc); var desc by mutableStateOf(desc); var allDay by mutableStateOf(allDay)
    var start by mutableStateOf(start); var end by mutableStateOf(end); var reminders by mutableStateOf(rem)
    var calIdS by mutableStateOf(calId)
}

private val REPEATS = listOf("" to "No repeat", "FREQ=DAILY" to "Daily", "FREQ=WEEKLY" to "Weekly", "FREQ=MONTHLY" to "Monthly", "FREQ=YEARLY" to "Yearly")
private val REMS = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880)
private fun durText(m: Int) = if (m % 1440 == 0) "${m / 1440} ${ct("d")}" else if (m % 60 == 0) "${m / 60} ${ct("h")}" else "$m ${ct("min")}"
private fun remText(m: Int) = when { m == 0 -> ct("At start"); m % 1440 == 0 -> "${m / 1440} ${ct("d")} ${ct("before")}"; m % 60 == 0 -> "${m / 60} ${ct("h")} ${ct("before")}"; else -> "$m ${ct("min")} ${ct("before")}" }

@Composable
private fun PickDate(init: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val st = rememberDatePickerState(initialSelectedDateMillis = init.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { IconButton({ st.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; onDismiss() }) { CalIc("check", Icons.Outlined.Check, ct("OK")) } },
        dismissButton = { IconButton(onDismiss) { CalIc("close", Icons.Outlined.Close, ct("Cancel")) } }) { DatePicker(st) }
}

@Composable
private fun PickTime(init: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(init.hour, init.minute, CalPrefs.h24)
    AlertDialog(onDismissRequest = onDismiss, text = { TimePicker(st) },
        confirmButton = { IconButton({ onPick(LocalTime.of(st.hour, st.minute)); onDismiss() }) { CalIc("check", Icons.Outlined.Check, ct("OK")) } },
        dismissButton = { IconButton(onDismiss) { CalIc("close", Icons.Outlined.Close, ct("Cancel")) } })
}

@Composable
private fun Row2(key: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(40.dp)) { CalIc(key, icon, tint = MaterialTheme.colorScheme.onSurfaceVariant) }; content()
    }
}

@Composable
fun EventEditor(st: EditState, cals: List<CalCal>, onSave: (EditState) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var pick by remember { mutableStateOf("") } // sd, st, ed, et
    var repMenu by remember { mutableStateOf(false) }; var remMenu by remember { mutableStateOf(false) }; var calMenu by remember { mutableStateOf(false) }
    val writable = cals.filter { it.writable }
    val cal = cals.firstOrNull { it.id == st.calIdS }
    fun setStart(n: LocalDateTime) { val dur = java.time.Duration.between(st.start, st.end); st.start = n; st.end = n.plus(dur) }
    fun setEnd(n: LocalDateTime) { st.end = if (n.isBefore(st.start)) st.start.plusHours(if (st.allDay) 0 else 1) else n }
    Scaffold(topBar = {
        CalBar(title = { Text(ct(if (st.id == 0L) "New event" else "Edit event")) },
            nav = { IconButton(onClose) { CalIc("close", Icons.Outlined.Close, ct("Cancel")) } },
            actions = { IconButton({ onSave(st) }, enabled = cal != null) { CalIc("check", Icons.Outlined.Check, ct("Save")) } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(st.title, { st.title = it }, Modifier.fillMaxWidth(), label = { Text(ct("Title")) }, singleLine = true)
            Row2("clock", Icons.Outlined.Schedule) { Text(ct("All day"), Modifier.weight(1f)); Switch(st.allDay, { st.allDay = it }) }
            Row2("calendar", Icons.Outlined.Event) {
                TextButton({ pick = "sd" }) { Text(fmtDate(st.start.toLocalDate())) }
                if (!st.allDay) TextButton({ pick = "st" }) { Text(fmtTime(st.start.toLocalTime())) }
            }
            Row2("calendar", Icons.Outlined.EventAvailable) {
                TextButton({ pick = "ed" }) { Text(fmtDate(st.end.toLocalDate())) }
                if (!st.allDay) TextButton({ pick = "et" }) { Text(fmtTime(st.end.toLocalTime())) }
            }
            Row2("refresh", Icons.Outlined.Repeat) {
                Box {
                    val cur = REPEATS.firstOrNull { it.first == (st.rrule ?: "") }?.second ?: "Custom"
                    TextButton({ repMenu = true }) { Text(ct(cur)) }
                    DropdownMenu(repMenu, { repMenu = false }) { REPEATS.forEach { (k, n) -> DropdownMenuItem({ Text(ct(n)) }, { st.rrule = k.ifEmpty { null }; repMenu = false }) } }
                }
            }
            Row2("bell", Icons.Outlined.Notifications) {
                Column {
                    st.reminders.sorted().forEach { m -> InputChip(false, { st.reminders = st.reminders - m }, { Text(remText(m)) }, Modifier.padding(end = 4.dp), trailingIcon = { CalIc("close", Icons.Outlined.Close, null, size = 16.dp) }) }
                    Box {
                        IconButton({ remMenu = true }) { CalIc("add", Icons.Outlined.Add, ct("Reminders")) }
                        DropdownMenu(remMenu, { remMenu = false }) { REMS.filter { it !in st.reminders }.forEach { m -> DropdownMenuItem({ Text(remText(m)) }, { st.reminders = st.reminders + m; remMenu = false }) } }
                    }
                }
            }
            Row2("compass", Icons.Outlined.Place) { OutlinedTextField(st.loc, { st.loc = it }, Modifier.fillMaxWidth(), label = { Text(ct("Location")) }, singleLine = true) }
            Row2("notes", Icons.Outlined.Notes) { OutlinedTextField(st.desc, { st.desc = it }, Modifier.fillMaxWidth(), label = { Text(ct("Description")) }, minLines = 3) }
            Row2("calendar", Icons.Outlined.CalendarMonth) {
                Box {
                    TextButton({ calMenu = true }) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal?.color ?: 0xFF888888.toInt()))); Spacer(Modifier.width(8.dp)); Text(cal?.name ?: ct("No writable calendar"))
                    }
                    DropdownMenu(calMenu, { calMenu = false }) { writable.forEach { c -> DropdownMenuItem({ Text(c.name) }, { st.calIdS = c.id; calMenu = false }, leadingIcon = { Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.color))) }) } }
                }
            }
            Spacer(Modifier.height(72.dp).navigationBarsPadding().imePadding())
        }
    }
    when (pick) {
        "sd" -> PickDate(st.start.toLocalDate(), { setStart(it.atTime(st.start.toLocalTime())) }) { pick = "" }
        "st" -> PickTime(st.start.toLocalTime(), { setStart(st.start.toLocalDate().atTime(it)) }) { pick = "" }
        "ed" -> PickDate(st.end.toLocalDate(), { setEnd(it.atTime(st.end.toLocalTime())) }) { pick = "" }
        "et" -> PickTime(st.end.toLocalTime(), { setEnd(st.end.toLocalDate().atTime(it)) }) { pick = "" }
    }
}

@Composable
fun CalendarsDialog(cals: List<CalCal>, onChange: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(onDismissRequest = onClose, title = { Text(ct("Calendars")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                cals.forEach { c ->
                    val shown = c.id.toString() !in CalPrefs.hidden
                    Row(Modifier.fillMaxWidth().clickable { CalPrefs.hidden = if (shown) CalPrefs.hidden + c.id.toString() else CalPrefs.hidden - c.id.toString(); CalPrefs.save(); onChange() }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(shown, null, colors = CheckboxDefaults.colors(checkedColor = Color(c.color)), modifier = Modifier.padding(8.dp))
                        Column(Modifier.weight(1f)) { Text(c.name, maxLines = 1); if (c.account.isNotEmpty()) Text(c.account, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
                        if (c.writable) IconButton({ CalPrefs.defCal = c.id; CalPrefs.save(); onChange() }) { CalIc("heart", if (CalPrefs.defCal == c.id) Icons.Outlined.Star else Icons.Outlined.StarBorder, ct("Default calendar")) }
                        else CalIc("lock", Icons.Outlined.Lock, ct("Read-only calendar"), Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        },
        confirmButton = { IconButton(onClose) { CalIc("check", Icons.Outlined.Check, ct("Done")) } },
        dismissButton = { IconButton({ if (CalData.addLocal(ctx, ct("Local calendar")) > 0) onChange() else Toast.makeText(ctx, ct("Failed"), Toast.LENGTH_SHORT).show() }) { CalIc("add", Icons.Outlined.Add, ct("Add calendar")) } })
}

private val ACCENTS = listOf(0xFF2E6FD8, 0xFF0E7C86, 0xFF2E9E5B, 0xFFE5484D, 0xFFE8833A, 0xFF8E4EC6, 0xFFD6409F, 0xFF6B7280).map { it.toInt() }

@Composable
fun CalSettings(cals: List<CalCal>, onClose: () -> Unit, onChange: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    fun target() = cals.firstOrNull { it.id == CalPrefs.defCal && it.writable } ?: cals.firstOrNull { it.writable }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { val t = target(); val n = if (t == null) -1 else CalData.importIcs(ctx, u, t.id)
            Toast.makeText(ctx, if (n >= 0) "${ct("Imported")}: $n" else ct("Failed"), Toast.LENGTH_SHORT).show(); onChange() }
    }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { u ->
        if (u != null) Toast.makeText(ctx, ct(if (CalData.exportIcs(ctx, u)) "Exported" else "Failed"), Toast.LENGTH_SHORT).show()
    }
    fun p(f: () -> Unit) { f(); CalPrefs.save() }
    var dlg by remember { mutableStateOf("") }
    val lookNames = mapOf("material" to "Material", "oneui" to "Samsung", "coloros" to "ColorOS", "win11" to "Windows 11", "win10" to "Windows 10", "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95")
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    fun ic(key: String, v: androidx.compose.ui.graphics.vector.ImageVector): @Composable () -> Unit = { CalIc(key, v, tint = dim) }
    val themeOpts = listOf("system" to ct("System"), "light" to ct("Light"), "dark" to ct("Dark"))
    val weekOpts = listOf(1 to ct("Mon"), 7 to ct("Sun"), 6 to ct("Sat"))
    val viewOpts = listOf("month" to ct("Month"), "week" to ct("Week"), "day" to ct("Day"), "agenda" to ct("Agenda"))
    val visOpts = listOf(0 to ct("Default"), 1 to ct("Confidential"), 2 to ct("Private"), 3 to ct("Public"))
    val availOpts = listOf(0 to ct("Busy"), 1 to ct("Free"))
    val colOpts = listOf("event" to ct("Event"), "calendar" to ct("Calendar"))
    val zoomOpts = listOf(36 to ct("Small"), 48 to ct("Medium"), 64 to ct("Large"))
    val countryOpts = listOf("" to ct("Device country") + " (" + CalHolidays.country() + ")") + CalHolidays.countries.map { it to CalHolidays.countryName(it) }
    Scaffold(topBar = { CalBar(title = { Text(ct("Settings")) }, nav = { IconButton(onClose) { CalIc("back", Icons.AutoMirrored.Outlined.ArrowBack, ct("Cancel")) } }) }) { pad ->
        val sc = rememberScrollState()
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(sc), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            @Composable fun Sec(t: String, key: String, v: androidx.compose.ui.graphics.vector.ImageVector, open: Boolean = false, c: @Composable ColumnScope.() -> Unit) =
                SetSection(ct(t), { CalIc(key, v, tint = MaterialTheme.colorScheme.primary) }, open, c)
            Sec("Look", "palette", Icons.Outlined.Palette, true) {
                SetRow(ic("palette", Icons.Outlined.Palette), ct("Look"), lookNames[CalPrefs.look], { dlg = "look" })
            }
            if (CalPrefs.look == "material") Sec("Colours", "palette", Icons.Outlined.ColorLens, true) {
                SetRow(ic("light", Icons.Outlined.DarkMode), ct("Theme"), themeOpts.first { it.first == CalPrefs.dark }.second, { dlg = "theme" })
                SetSwitchRow(ic("palette", Icons.Outlined.Palette), ct("Material You"), CalPrefs.dynamic) { v -> p { CalPrefs.dynamic = v } }
                if (!CalPrefs.dynamic) SetRow(ic("palette", Icons.Outlined.ColorLens), ct("Accent"), null, { dlg = "accent" }) { ColorDot(Color(CalPrefs.accent)) }
                SetSwitchRow(ic("light", Icons.Outlined.DarkMode), ct("AMOLED black"), CalPrefs.amoled) { v -> p { CalPrefs.amoled = v } }
            }
            Sec("Language", "globe", Icons.Outlined.Language) {
                SetRow(ic("globe", Icons.Outlined.Language), ct("Language"), if (CalPrefs.lang == "tr") "Türkçe" else "English", { dlg = "lang" })
            }
            Sec("Calendar", "calendar", Icons.Outlined.CalendarMonth) {
                SetRow(ic("calendar", Icons.Outlined.CalendarMonth), ct("Week starts on"), weekOpts.first { it.first == CalPrefs.weekStart }.second, { dlg = "week" })
                SetSwitchRow(ic("history", Icons.Outlined.Schedule), ct("24-hour clock"), CalPrefs.h24) { v -> p { CalPrefs.h24 = v } }
                SetRow(ic("gridView", Icons.Outlined.ViewAgenda), ct("Start view"), viewOpts.first { it.first == CalPrefs.startView }.second, { dlg = "view" })
            }
            Sec("Events", "bell", Icons.Outlined.Event) {
                SetRow(ic("calendar", Icons.Outlined.CalendarMonth), ct("Default calendar"), cals.firstOrNull { it.id == CalPrefs.defCal && it.writable }?.name ?: target()?.name, { dlg = "defcal" })
                SetRow(ic("clock", Icons.Outlined.Timer), ct("Default duration"), durText(CalPrefs.defDur), { dlg = "dur" })
                SetRow(ic("bell", Icons.Outlined.Notifications), ct("Default reminder"), if (CalPrefs.defRem < 0) ct("None") else remText(CalPrefs.defRem), { dlg = "rem" })
                SetRow(ic("lock", Icons.Outlined.Lock), ct("Visibility"), visOpts.first { it.first == CalPrefs.defVis }.second, { dlg = "vis" })
                SetRow(ic("clock", Icons.Outlined.EventAvailable), ct("Availability"), availOpts.first { it.first == CalPrefs.defAvail }.second, { dlg = "avail" })
                SetRow(ic("palette", Icons.Outlined.Palette), ct("Event colour"), colOpts.first { it.first == CalPrefs.colorSrc }.second, { dlg = "col" })
                SetSwitchRow(ic("close", Icons.Outlined.EventBusy), ct("Show declined events"), CalPrefs.showDeclined) { v -> p { CalPrefs.showDeclined = v }; onChange() }
            }
            Sec("Views", "table", Icons.Outlined.CalendarViewMonth) {
                SetSwitchRow(ic("calendar", Icons.Outlined.Today), ct("Open on today"), CalPrefs.goToday) { v -> p { CalPrefs.goToday = v } }
                SetSwitchRow(ic("table", Icons.Outlined.Tag), ct("Week numbers"), CalPrefs.weekNums) { v -> p { CalPrefs.weekNums = v } }
                SetSwitchRow(ic("calendar", Icons.Outlined.CalendarViewWeek), ct("Highlight weekends"), CalPrefs.hlWeekend) { v -> p { CalPrefs.hlWeekend = v } }
                SetSwitchRow(ic("details", Icons.Outlined.ViewAgenda), ct("Month titles"), CalPrefs.monthTitles) { v -> p { CalPrefs.monthTitles = v } }
                SetRow(ic("clock", Icons.Outlined.Schedule), ct("Week starts at"), fmtTime(java.time.LocalTime.of(CalPrefs.weekHour, 0)), { dlg = "hour" })
                SetRow(ic("search", Icons.Outlined.ZoomIn), ct("Zoom"), zoomOpts.first { it.first == CalPrefs.weekZoom }.second, { dlg = "zoom" })
            }
            Sec("Holidays", "calendar", Icons.Outlined.Flag) {
                SetSwitchRow(ic("calendar", Icons.Outlined.Flag), ct("Show holidays"), CalPrefs.holShow) { v -> p { CalPrefs.holShow = v }; onChange() }
                if (CalPrefs.holShow) {
                    SetRow(ic("globe", Icons.Outlined.Public), ct("Country"), countryOpts.firstOrNull { it.first == CalPrefs.holCountry }?.second, { dlg = "country" })
                    SetSwitchRow(ic("calendar", Icons.Outlined.Event), ct("Public holidays"), CalPrefs.holPublic) { v -> p { CalPrefs.holPublic = v }; onChange() }
                    SetSwitchRow(ic("heart", Icons.Outlined.Star), ct("Observances"), CalPrefs.holObs) { v -> p { CalPrefs.holObs = v }; onChange() }
                }
            }
            Sec("Data", "download", Icons.Outlined.FileDownload) {
                SetRow(ic("download", Icons.Outlined.FileDownload), ct("Import .ics"), null, { imp.launch(arrayOf("text/calendar", "application/octet-stream", "*/*")) })
                SetRow(ic("upload", Icons.Outlined.FileUpload), ct("Export .ics"), null, { exp.launch("pulse-calendar.ics") })
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
    when (dlg) {
        "look" -> SetLookDialog(ct("Look"), CalPrefs.looks, CalPrefs.look, { lookNames[it] ?: it }, { id -> p { CalPrefs.look = id } }) { dlg = "" }
        "theme" -> SetPickDialog(ct("Theme"), themeOpts, CalPrefs.dark, { v -> p { CalPrefs.dark = v } }, { dlg = "" })
        "accent" -> SetColorDialog(ct("Accent"), ACCENTS.map { Color(it) }, ACCENTS.indexOf(CalPrefs.accent), { i -> p { CalPrefs.accent = ACCENTS[i] } }) { dlg = "" }
        "lang" -> SetPickDialog(ct("Language"), listOf("en" to "English", "tr" to "Türkçe"), CalPrefs.lang, { v -> p { CalPrefs.lang = v } }, { dlg = "" })
        "week" -> SetPickDialog(ct("Week starts on"), weekOpts, CalPrefs.weekStart, { v -> p { CalPrefs.weekStart = v } }, { dlg = "" })
        "view" -> SetPickDialog(ct("Start view"), viewOpts, CalPrefs.startView, { v -> p { CalPrefs.startView = v } }, { dlg = "" })
        "defcal" -> SetPickDialog(ct("Default calendar"), cals.filter { it.writable }.map { it.id to it.name }, CalPrefs.defCal, { v -> p { CalPrefs.defCal = v } }, { dlg = "" })
        "dur" -> SetPickDialog(ct("Default duration"), listOf(15, 30, 45, 60, 90, 120, 180, 1440).map { it to durText(it) }, CalPrefs.defDur, { v -> p { CalPrefs.defDur = v } }, { dlg = "" })
        "rem" -> SetPickDialog(ct("Default reminder"), listOf(-1 to ct("None")) + REMS.map { it to remText(it) }, CalPrefs.defRem, { v -> p { CalPrefs.defRem = v } }, { dlg = "" })
        "vis" -> SetPickDialog(ct("Visibility"), visOpts, CalPrefs.defVis, { v -> p { CalPrefs.defVis = v } }, { dlg = "" })
        "avail" -> SetPickDialog(ct("Availability"), availOpts, CalPrefs.defAvail, { v -> p { CalPrefs.defAvail = v } }, { dlg = "" })
        "col" -> SetPickDialog(ct("Event colour"), colOpts, CalPrefs.colorSrc, { v -> p { CalPrefs.colorSrc = v }; onChange() }, { dlg = "" })
        "hour" -> SetPickDialog(ct("Week starts at"), (0..12).map { it to fmtTime(java.time.LocalTime.of(it, 0)) }, CalPrefs.weekHour, { v -> p { CalPrefs.weekHour = v } }, { dlg = "" })
        "zoom" -> SetPickDialog(ct("Zoom"), zoomOpts, CalPrefs.weekZoom, { v -> p { CalPrefs.weekZoom = v } }, { dlg = "" })
        "country" -> SetPickDialog(ct("Country"), countryOpts, CalPrefs.holCountry, { v -> p { CalPrefs.holCountry = v }; onChange() }, { dlg = "" })
    }
}
