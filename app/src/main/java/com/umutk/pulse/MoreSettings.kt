package com.umutk.pulse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import com.umutk.pulse.features.DnsSettings
import com.umutk.pulse.features.InstallShow
import com.umutk.pulse.features.setThemeWallpaper
import com.umutk.pulse.features.themeWallpaper
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
        PSwitch(on, onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Text(title, color = T.text, fontSize = 14.sp)
    PSlider(value, onChange, range, onDone)
}

/** Settings > General: start page, which pages show, temperature unit, screen on, text size. */
@Composable
fun GeneralSettings() {
    val ctx = LocalContext.current
    WindowThemeSettings()
    InstallWindowSettings()
    WatchdogSettings()
    DnsSettings()
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            SplashPic.set(ctx, uri.toString())
        }
    }
    PCard {
        Text("Open files", color = Look.accent, fontSize = 15.sp)
        Text("Ask which app each time, or open directly with the app that handles the file.", color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(!com.umutk.pulse.features.Downloads.directOpen, "Ask") { com.umutk.pulse.features.Downloads.setDirectOpen(ctx, false) }
            PChip(com.umutk.pulse.features.Downloads.directOpen, "Open directly") { com.umutk.pulse.features.Downloads.setDirectOpen(ctx, true) }
        }
    }
    PCard {
        Text("Opening picture", color = Look.accent, fontSize = 15.sp)
        Text("Your own picture behind the opening screen. The logo and the loading bar sit on their own card.", color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton("Pick picture") { pick.launch(arrayOf("image/*")) }
            if (SplashPic.uri != null) PButton("Default") { SplashPic.set(ctx, null) }
        }
    }
    PCard {
        Text("Start page", color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pages.forEach { p -> PChip(Opt.startPage == p.id, p.label) { Opt.startPage = p.id; Opt.save(ctx) } }
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
                PCheck(p.id !in Opt.hidden, { Opt.hidden = if (it) Opt.hidden - p.id else Opt.hidden + p.id; Opt.save(ctx) })
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
        SwitchRow("Floating monitor", "Live numbers over other apps", Opt.ovOn) { Opt.ovOn = it; apply() }
        if (!canOverlay) {
            Text("Needs the 'display over other apps' permission.", color = T.sub, fontSize = 12.sp)
            PButton("Allow") { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); tick++ }
        }
        SwitchRow("Show in notification", "Numbers in a notification line", Opt.notifOn) { Opt.notifOn = it; apply() }
        Text("Show", color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("cpu" to "Processor", "ram" to "Memory", "gpu" to "Graphics", "temp" to "Temperature", "bat" to "Battery", "net" to "Network").forEach { (k, n) ->
                PChip(k in Opt.ovItems, n) { Opt.ovItems = if (k in Opt.ovItems) Opt.ovItems - k else Opt.ovItems + k; apply() }
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

/** Settings > General, top: the look of pop-up windows, dialogs and menus. */
@Composable
fun WindowThemeSettings() {
    val ctx = LocalContext.current
    PCard {
        Text("Window theme", color = Look.accent, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("win" to "Windows", "material" to "Material", "amoled" to "AMOLED").forEach { (id, n) ->
                PChip(Look.windowTheme == id, n) { Cfg.setWindowTheme(ctx, id) }
            }
        }
    }
    BackdropSettings()
}

/** Windows 11 windows: a blurred picture behind the panels (phone wallpaper, opening picture, your own, or off). */
@Composable
fun BackdropSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { com.umutk.pulse.features.Backdrop.set(ctx, "custom", uri) }
    }
    PCard {
        Text("Background behind windows", color = Look.accent, fontSize = 15.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to "Off", "phone" to "Phone wallpaper").forEach { (id, n) ->
                PChip(com.umutk.pulse.features.Backdrop.mode == id, n) { scope.launch(Dispatchers.IO) { com.umutk.pulse.features.Backdrop.set(ctx, id) } }
            }
            PChip(com.umutk.pulse.features.Backdrop.mode == "custom", "Own picture") { pick.launch(arrayOf("image/*")) }
        }
        // the 16 bundled pictures, in the four themes of the pack: A (1-4), B (5-8), C (9-12), D (13-16)
        // tapping a theme title sets its accent colour and its first picture; the pictures below still pick one by one
        listOf("A" to 1, "B" to 5, "C" to 9, "D" to 13).forEachIndexed { g, (theme, first) ->
            Text("Theme $theme", color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp).clickable {
                Cfg.setAccent(ctx, 7 + g)
                scope.launch(Dispatchers.IO) { com.umutk.pulse.features.Backdrop.set(ctx, "pack$first") }
            })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (first until first + 4).forEach { i ->
                    PChip(com.umutk.pulse.features.Backdrop.mode == "pack$i", "$theme$i") { scope.launch(Dispatchers.IO) { com.umutk.pulse.features.Backdrop.set(ctx, "pack$i") } }
                }
            }
        }
    }
}

/** Switches for what the install window shows. */
@Composable
fun InstallWindowSettings() {
    val ctx = LocalContext.current
    val open = LocalOpenTool.current
    PCard { PButton("Installer") { open("installer") } }
    InstallShow.load(ctx)
    PCard {
        Text("Install window", color = Look.accent, fontSize = 15.sp)
        SwitchRow("App name", "Show the name and its badge", InstallShow.name) { InstallShow.set(ctx, "inst_name", it) }
        SwitchRow("Version", "Show the version and code", InstallShow.version) { InstallShow.set(ctx, "inst_ver", it) }
        SwitchRow("Package name", "Show the package name", InstallShow.pkg) { InstallShow.set(ctx, "inst_pkg", it) }
        SwitchRow("Status", "Show New, Identical, Upgrade, Downgrade", InstallShow.chip) { InstallShow.set(ctx, "inst_chip", it) }
    }
    PCard {
        Text("Default app", color = Look.accent, fontSize = 15.sp)
        PButton("Make Pulse the default for APK files") { com.umutk.pulse.features.openDefaultApkSettings(ctx) }
    }
    PCard {
        Text("Window look", color = Look.accent, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("win11" to "Windows 11", "material" to "Material", "amoled" to "AMOLED black").forEach { (id, n) ->
                PChip(InstallShow.theme == id, n) { InstallShow.setTheme(ctx, id) }
            }
        }
    }
    PCard {
        Text("Security", color = Look.accent, fontSize = 15.sp)
        SwitchRow("Require biometric to install", "Fingerprint or face before each install. Off: never asked", InstallShow.biometric) { InstallShow.set(ctx, "inst_bio", it) }
        SwitchRow("Cancel installation on dismiss", "Closing the window stops a waiting biometric check", InstallShow.cancelOnDismiss) { InstallShow.set(ctx, "inst_cancel", it) }
    }
    PCard {
        Text("Details", color = Look.accent, fontSize = 15.sp)
        SwitchRow("Show version compare in single line", "Installed and new version on one line. Off: two lines", InstallShow.cmpSingle) { InstallShow.set(ctx, "inst_cmp1", it) }
        SwitchRow("Show SDK compare in multi lines", "Min and target SDK on their own lines. Off: one line", InstallShow.sdkMulti) { InstallShow.set(ctx, "inst_sdkm", it) }
        SwitchRow("Show identical data as a single value", "When the old and new values match, show one value", InstallShow.sameSingle) { InstallShow.set(ctx, "inst_same1", it) }
        SwitchRow("Show extended menu", "More options in the install dialog", InstallShow.extended) { InstallShow.set(ctx, "inst_ext", it) }
        SwitchRow("Show Smart Suggestions", "Next steps shown when an install fails", InstallShow.suggest) { InstallShow.set(ctx, "inst_sugg", it) }
        SwitchRow("Expand panel by default", "Install options open when the dialog opens", InstallShow.expand) { InstallShow.set(ctx, "inst_exp", it) }
        SwitchRow("Rounded corners", "Off: square corners in the install window", InstallShow.round) { InstallShow.set(ctx, "inst_round", it) }
    }
}

/** Settings > General: restarts the monitor and running downloads when Android stops them. */
@Composable
fun WatchdogSettings() {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(Watchdog.isOn(ctx)) }
    PCard {
        SwitchRow("Watchdog", "Checks every 15 minutes and restarts the monitor and running downloads", on) { on = it; Watchdog.set(ctx, it) }
    }
}

/** A look's own wallpaper: pick one, or go back to the built-in one. */
@Composable
fun ThemeWallpaperRow(lookId: String) {
    val ctx = LocalContext.current
    var uri by remember(lookId) { mutableStateOf(themeWallpaper(lookId)) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            setThemeWallpaper(ctx, lookId, u.toString()); uri = u.toString()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PButton("Wallpaper") { pick.launch(arrayOf("image/*")) }
        if (uri != null) PButton("Default") { setThemeWallpaper(ctx, lookId, null); uri = null }
    }
}
