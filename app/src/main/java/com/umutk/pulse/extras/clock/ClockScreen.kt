package com.umutk.pulse.extras.clock

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The Clock: alarms (real system alarms), world clocks, and the stopwatch and the timer of the Toolkit in one place.
 * The layout of the idea (alarms, world clock, stopwatch, timer in one app) follows Lucid Alarm (GPL-3.0, DarkAspect1); the code is Pulse's own.
 */
@Composable
fun ClockScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    var tab by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { AlarmEngine.rescheduleAll(ctx) }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Clock", "Saat"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(tr("Alarms", "Alarmlar"), tr("World", "Dünya"), tr("Stopwatch", "Kronometre"), tr("Timer", "Zamanlayıcı")).forEachIndexed { i, n -> PChip(tab == i, n) { tab = i } }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> AlarmsTab()
                1 -> WorldTab()
                2 -> com.umutk.pulse.features.StopwatchTool()
                else -> com.umutk.pulse.features.TimerTool()
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private val DAY_ORDER = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
private fun dayName(d: Int) = java.text.DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays[d]

private fun repeatText(days: Set<Int>) = when {
    days.isEmpty() -> tr("Once", "Bir kez")
    days.size == 7 -> tr("Every day", "Her gün")
    days == setOf(Calendar.SATURDAY, Calendar.SUNDAY) -> tr("Weekends", "Hafta sonu")
    days == setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY) -> tr("Weekdays", "Hafta içi")
    else -> DAY_ORDER.filter { it in days }.joinToString(" ") { dayName(it) }
}

@Composable
private fun AlarmsTab() {
    val ctx = LocalContext.current
    var rev by remember { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<AlarmItem?>(null) }
    val list = remember(rev) { AlarmStore.all(ctx) }
    var exact by remember { mutableStateOf(AlarmEngine.canExact(ctx)) }
    val askNotes = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) askNotes.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        while (true) { exact = AlarmEngine.canExact(ctx); delay(2000) }
    }
    if (!exact) PCard {
        Text(tr("Android needs your permission for alarms at an exact time.", "Android, tam saatinde çalacak alarmlar için iznini ister."), color = T.text, fontSize = 14.sp)
        PButton(tr("Allow", "İzin ver")) { ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (android.os.Build.VERSION.SDK_INT >= 34 && !ctx.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()) PCard {
        Text(tr("To open the alarm screen over the lock screen, allow full-screen notifications.", "Alarm ekranının kilit ekranında açılması için tam ekran bildirimlere izin ver."), color = T.text, fontSize = 14.sp)
        PButton(tr("Allow", "İzin ver")) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (list.isEmpty()) Text(tr("No alarm yet.", "Henüz alarm yok."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(vertical = 14.dp))
    list.forEach { a ->
        PCard {
            Row(Modifier.fillMaxWidth().clickable { edit = a }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(String.format("%02d:%02d", a.hour, a.minute), color = if (a.enabled) T.text else T.sub, fontSize = 38.sp, fontWeight = FontWeight.Light)
                    Text(listOf(a.label, repeatText(a.days)).filter { it.isNotBlank() }.joinToString("  ·  "), color = T.sub, fontSize = 13.sp)
                    if (a.enabled) Text(tr("Next: ", "Sıradaki: ") + SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(AlarmEngine.nextTrigger(a))), color = Look.accent, fontSize = 12.sp)
                }
                PSwitch(a.enabled, { on -> AlarmStore.save(ctx, a.copy(enabled = on)); rev++ })
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    PButton(tr("Add an alarm", "Alarm ekle"), Modifier.fillMaxWidth()) { val c = Calendar.getInstance(); edit = AlarmItem(AlarmStore.nextId(ctx), c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)) }
    edit?.let { a -> AlarmEditor(a, isNew = list.none { it.id == a.id }, onDismiss = { edit = null }, onDone = { rev++; edit = null }) }
}

@Composable
private fun AlarmEditor(start: AlarmItem, isNew: Boolean, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var a by remember { mutableStateOf(start) }
    WinAlert(onDismissRequest = onDismiss, title = { Text(if (isNew) tr("New alarm", "Yeni alarm") else tr("Alarm", "Alarm")) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // the time: the system's own clock dialog, so the wheel and the keyboard entry are the phone's
                Text(String.format("%02d:%02d", a.hour, a.minute), color = T.text, fontSize = 52.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        TimePickerDialog(ctx, { _, h, m -> a = a.copy(hour = h, minute = m) }, a.hour, a.minute, android.text.format.DateFormat.is24HourFormat(ctx)).show()
                    }.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                PField(a.label, { a = a.copy(label = it) }, tr("Label", "Etiket"), Modifier.fillMaxWidth())
                Text(tr("Repeat", "Tekrar"), color = T.sub, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_ORDER.forEach { d -> PChip(d in a.days, dayName(d)) { a = a.copy(days = if (d in a.days) a.days - d else a.days + d) } }
                }
                Text(tr("Snooze", "Erteleme"), color = T.sub, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5, 10, 15, 20).forEach { m -> PChip(a.snoozeMin == m, "$m") { a = a.copy(snoozeMin = m) } } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Vibrate", "Titreşim"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    PSwitch(a.vibrate, { a = a.copy(vibrate = it) })
                }
            }
        },
        confirmButton = { Text(tr("Save", "Kaydet"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable { AlarmStore.save(ctx, a.copy(enabled = true)); onDone() }.padding(horizontal = 14.dp, vertical = 8.dp)) },
        dismissButton = { Row {
            if (!isNew) Text(tr("Delete", "Sil"), color = androidx.compose.ui.graphics.Color(0xFFE5484D), fontSize = 15.sp, modifier = Modifier.clickable { AlarmStore.delete(ctx, a.id); onDone() }.padding(horizontal = 14.dp, vertical = 8.dp))
            Text(tr("Cancel", "Vazgeç"), color = T.text, fontSize = 15.sp, modifier = Modifier.clickable { onDismiss() }.padding(horizontal = 14.dp, vertical = 8.dp))
        } })
}

private fun worldPrefs(c: android.content.Context) = c.getSharedPreferences("clock_world", android.content.Context.MODE_PRIVATE)

@Composable
private fun WorldTab() {
    val ctx = LocalContext.current
    var zones by remember { mutableStateOf(worldPrefs(ctx).getString("zones", "").orEmpty().split(',').filter { it.isNotBlank() }) }
    var add by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(10_000); tick++ } }
    fun save(l: List<String>) { zones = l; worldPrefs(ctx).edit().putString("zones", l.joinToString(",")).apply() }
    val here = TimeZone.getDefault()
    val rows = listOf(here.id) + zones.filter { it != here.id }
    rows.forEach { id ->
        val tz = TimeZone.getTimeZone(id)
        val now = Date()
        val hm = SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = tz }.format(now)
        val dd = SimpleDateFormat("yyyyMMdd", Locale.US)
        val mine = dd.apply { timeZone = here }.format(now).toInt()
        val there = dd.apply { timeZone = tz }.format(now).toInt()
        val diff = (tz.getOffset(now.time) - here.getOffset(now.time)) / 3_600_000f
        val dayText = when { there > mine -> tr("Tomorrow", "Yarın"); there < mine -> tr("Yesterday", "Dün"); else -> tr("Today", "Bugün") }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(id.substringAfterLast('/').replace('_', ' '), color = T.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(dayText + (if (id == here.id) "  ·  " + tr("here", "burası") else "  ·  " + (if (diff >= 0) "+" else "") + (if (diff == diff.toInt().toFloat()) diff.toInt().toString() else "%.1f".format(diff)) + " " + tr("h", "sa")), color = T.sub, fontSize = 12.sp)
                }
                Text(hm, color = T.text, fontSize = 34.sp, fontWeight = FontWeight.Light)
                if (id != here.id) Box(Modifier.size(36.dp).clickable { save(zones - id) }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 18.dp) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    PButton(tr("Add a city", "Şehir ekle"), Modifier.fillMaxWidth()) { add = true }
    if (add) {
        var q by remember { mutableStateOf("") }
        val all = remember { TimeZone.getAvailableIDs().filter { '/' in it && !it.startsWith("Etc/") && !it.startsWith("SystemV/") } }
        val hits = remember(q) { all.filter { q.isBlank() || it.replace('_', ' ').contains(q.trim(), true) }.take(40) }
        WinAlert(onDismissRequest = { add = false }, title = { Text(tr("Add a city", "Şehir ekle")) },
            text = { Column {
                PField(q, { q = it }, tr("Search", "Ara"), Modifier.fillMaxWidth())
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    hits.forEach { id -> Text(id.replace('_', ' '), color = T.text, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().clickable { if (id !in zones) save(zones + id); add = false }.padding(vertical = 10.dp)) }
                }
            } },
            confirmButton = {}, dismissButton = { Text(tr("Close", "Kapat"), color = T.text, fontSize = 15.sp, modifier = Modifier.clickable { add = false }.padding(horizontal = 14.dp, vertical = 8.dp)) })
    }
}
