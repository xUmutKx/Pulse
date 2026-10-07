package com.umutk.pulse

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = T.text, fontSize = 15.sp); if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 12.sp) }
        Switch(on, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Look.accent))
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Text(title, color = T.text, fontSize = 14.sp)
    Slider(value, onChange, valueRange = range, onValueChangeFinished = onDone, colors = SliderDefaults.colors(thumbColor = Look.accent, activeTrackColor = Look.accent))
}

/** Settings > General: start page, which pages show, temperature unit, screen on, text size. */
@Composable
fun GeneralSettings() {
    val ctx = LocalContext.current
    PCard {
        Text("Start page", color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pages.forEach { p -> FilterChip(Opt.startPage == p.id, { Opt.startPage = p.id; Opt.save(ctx) }, { Text(p.label) }) }
        }
    }
    PCard {
        SwitchRow("Temperature: Fahrenheit", "Celsius when off", Opt.tempF) { Opt.tempF = it; Opt.save(ctx) }
        SwitchRow("Keep screen on", "The screen stays on while Pulse is open", Opt.keepOn) { Opt.keepOn = it; Opt.save(ctx) }
        SliderRow("Text size ${(Opt.fontScale * 100).toInt()}%", Opt.fontScale, .8f..1.4f, { Opt.fontScale = it }) { Opt.save(ctx) }
    }
    PCard {
        Text("Pages shown", color = T.sub, fontSize = 12.sp)
        Pages.forEach { p ->
            Row(Modifier.fillMaxWidth().clickable { Opt.hidden = if (p.id in Opt.hidden) Opt.hidden - p.id else Opt.hidden + p.id; Opt.save(ctx) }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(p.id !in Opt.hidden, { Opt.hidden = if (it) Opt.hidden - p.id else Opt.hidden + p.id; Opt.save(ctx) }, colors = CheckboxDefaults.colors(checkedColor = Look.accent))
                PIcon(p.icon, T.text, 18.dp); Spacer(Modifier.width(8.dp)); Text(p.label, color = T.text, fontSize = 14.sp)
            }
        }
    }
}

/** Settings > Monitor: floating numbers, notification and alerts. */
@Composable
fun MonitorSettings() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val canOverlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    fun apply() { Opt.save(ctx); MonitorService.sync(ctx) }
    PCard {
        SwitchRow("Floating monitor", "Live numbers over other apps; drag to move", Opt.ovOn) { Opt.ovOn = it; apply() }
        if (!canOverlay) {
            Text("Needs the 'display over other apps' permission.", color = T.sub, fontSize = 12.sp)
            PButton("Allow") { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); tick++ }
        }
        SwitchRow("Show in notification", "Numbers in a notification line", Opt.notifOn) { Opt.notifOn = it; apply() }
        Text("Show", color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("cpu" to "Processor", "ram" to "Memory", "gpu" to "Graphics", "temp" to "Temperature", "bat" to "Battery", "net" to "Network").forEach { (k, n) ->
                FilterChip(k in Opt.ovItems, { Opt.ovItems = if (k in Opt.ovItems) Opt.ovItems - k else Opt.ovItems + k; apply() }, { Text(n) })
            }
        }
        SliderRow("Opacity ${(Opt.ovAlpha * 100).toInt()}%", Opt.ovAlpha, .1f..1f, { Opt.ovAlpha = it }) { Opt.save(ctx) }
        SliderRow("Text size ${Opt.ovSize.toInt()}", Opt.ovSize, 8f..22f, { Opt.ovSize = it }) { Opt.save(ctx) }
        Text("Add the Pulse tile to Quick Settings to switch it on and off with one tap.", color = T.sub, fontSize = 11.sp)
    }
    PCard {
        Text("Alerts", color = Look.accent, fontSize = 15.sp)
        Text("Works while the app is closed; the same alert repeats at most every 5 minutes. 0 = off.", color = T.sub, fontSize = 12.sp)
        SliderRow(if (Opt.alertTemp == 0) "Temperature alert: off" else "Temperature ${Opt.alertTemp}°C or higher", Opt.alertTemp.toFloat(), 0f..70f, { Opt.alertTemp = it.toInt() }) { apply() }
        SliderRow(if (Opt.alertBat == 0) "Low battery alert: off" else "Battery ${Opt.alertBat}% or lower", Opt.alertBat.toFloat(), 0f..50f, { Opt.alertBat = it.toInt() }) { apply() }
        SliderRow(if (Opt.alertRam == 0) "High memory alert: off" else "Memory ${Opt.alertRam}% or higher", Opt.alertRam.toFloat(), 0f..100f, { Opt.alertRam = it.toInt() }) { apply() }
        if (android.os.Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Text("Needs the notification permission.", color = T.sub, fontSize = 12.sp)
            PButton("Notification settings") { ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** Settings > Report: everything in one text, to share or copy. */
@Composable
fun ReportSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    PCard {
        Text("Share everything (system, battery, memory, storage, display, network, thermal, camera, codecs) as one text.", color = T.text, fontSize = 14.sp)
        PButton(if (busy) "Preparing…" else "Share report") {
            if (!busy) { busy = true; scope.launch {
                val txt = withContext(Dispatchers.IO) { Hw.report(ctx) }
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt).putExtra(Intent.EXTRA_SUBJECT, "Pulse report"), "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                busy = false
            } }
        }
        PButton("Copy to clipboard") {
            scope.launch {
                val txt = withContext(Dispatchers.IO) { Hw.report(ctx) }
                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("Pulse", txt))
            }
        }
    }
}
