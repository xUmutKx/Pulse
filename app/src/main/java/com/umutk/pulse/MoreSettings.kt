package com.umutk.pulse

import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import com.umutk.pulse.features.DnsSettings
import com.umutk.pulse.features.GlassPreview
import com.umutk.pulse.features.GlassPrefs
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
import androidx.compose.ui.text.font.FontWeight
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
        SwitchRow(tr("Old monitor filter", "Eski monitör filtresi"), tr("Scan lines and dots, like a CRT", "CRT gibi tarama çizgileri ve noktalar"), Opt.crt) { Opt.crt = it; Opt.save(ctx) }
        SwitchRow(tr("Sign-in screen at start", "Açılışta giriş ekranı"), tr("Windows 95, XP, 7, 10 and 11 looks", "Windows 95, XP, 7, 10 ve 11 görünümleri"), Opt.signIn) { Opt.signIn = it; Opt.save(ctx) }
        SliderRow("Text size ${(Opt.fontScale * 100).toInt()}%", Opt.fontScale, .8f..1.4f, { Opt.fontScale = it }) { Opt.save(ctx) }
    }
    PCard {
        Text(tr("Language", "Dil"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("auto" to "Phone / Telefon", "en" to "English", "tr" to "Türkçe").forEach { (v, label) ->
                PChip(Opt.lang == v, label) { Opt.lang = v; Opt.save(ctx) }
            }
        }
    }
    PCard { AppIconChooser() }
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
    PCard { SwitchRow("Ask before ending", "Prevents ending a process by accident", Cfg.confirmKill) { Cfg.setConfirm(ctx, it) } }
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

/** Windows 11: a blurred picture behind the windows (phone wallpaper, the bundled A-D pictures, your own, or off). */
@Composable
fun BackdropSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bd = com.umutk.pulse.features.Backdrop
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) { bd.set(ctx, "custom", uri) }
    }
    PCard {
        Text("Background behind windows", color = Look.accent, fontSize = 15.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("off" to "Off", "phone" to "Phone wallpaper").forEach { (id, n) ->
                PChip(bd.mode == id, n) { scope.launch(Dispatchers.IO) { bd.set(ctx, id) } }
            }
            PChip(bd.mode == "custom", "Own picture") { pick.launch(arrayOf("image/*")) }
        }
        // linear 0-40 dp: small moves change little, 0 is sharp; the blur is rendered once on release
        val b = bd.blur
        SliderRow("Blur ${b.toInt()}", (b / 40f).coerceIn(0f, 1f), 0f..1f, { bd.blur = it * 40f }) { bd.saveBlur(ctx) }
        var editing by remember { mutableStateOf(false) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (bd.image != null) PButton(tr("Edit", "Düzenle"), icon = androidx.compose.material.icons.Icons.Outlined.Crop) { editing = true }
            listOf("top" to tr("Top", "Üst"), "center" to tr("Middle", "Orta"), "bottom" to tr("Bottom", "Alt")).forEach { (id, n) ->
                PChip(bd.align == id, n) { bd.setAlign(ctx, id) }
            }
        }
        if (editing) com.umutk.pulse.features.BackdropEditor { editing = false }
        // the 16 bundled pictures in four themes: A (1-4), B (5-8), C (9-12), D (13-16); a theme title sets its accent and first picture
        listOf("A" to 1, "B" to 5, "C" to 9, "D" to 13).forEachIndexed { g, (theme, first) ->
            Text("Theme $theme", color = T.sub, fontSize = 12.sp, modifier = Modifier.clickable {
                Cfg.setAccent(ctx, 7 + g)
                scope.launch(Dispatchers.IO) { bd.set(ctx, "pack$first") }
            }.padding(top = 6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (first until first + 4).forEach { i ->
                    val res = remember(i) { ctx.resources.getIdentifier("win11_wall_$i", "drawable", ctx.packageName) }
                    val picked = bd.mode == "pack$i"
                    Box(Modifier.weight(1f).aspectRatio(1.6f).clip(RoundedCornerShape(8.dp))
                        .border(if (picked) 3.dp else 1.dp, if (picked) Look.accent else T.line, RoundedCornerShape(8.dp))
                        .clickable { scope.launch(Dispatchers.IO) { bd.set(ctx, "pack$i") } }) {
                        if (res != 0) Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    }
                }
            }
        }
    }
}

/** Settings > Installer: what the install window shows, how it looks and how it loads. */
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
        SwitchRow("SDK versions", "Min and target SDK of the APK. Off by default", InstallShow.sdk) { InstallShow.set(ctx, "inst_sdk", it) }
        SwitchRow("Certificate", "The signing certificate fingerprint. Off by default", InstallShow.cert) { InstallShow.set(ctx, "inst_cert", it) }
    }
    PCard {
        Text("Default app", color = Look.accent, fontSize = 15.sp)
        PButton("Make Pulse the default for APK files") { com.umutk.pulse.features.openDefaultApkSettings(ctx) }
    }
    PCard {
        Text("Window look", color = Look.accent, fontSize = 15.sp)
        // one preview per look, three to a row, the name under each; "App theme" shows the look of the app right now
        val installLooks = listOf("material" to "Material You", "app" to tr("App theme", "Uygulama teması"), "amoled" to "AMOLED black", "win11" to "Windows 11", "win10" to "Windows 10",
            "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95")
        installLooks.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, n) ->
                    val sel = InstallShow.theme == id
                    Column(Modifier.weight(1f).clickable { InstallShow.setTheme(ctx, id) }
                        .border(if (sel) 2.dp else 1.dp, if (sel) Look.accent else T.line, RoundedCornerShape(10.dp)).padding(6.dp)) {
                        ThemePreview(if (id == "app") Look.theme else Themes.first { it.id == id })
                        Text(n, color = if (sel) Look.accent else T.sub, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    PCard {
        Text("Loading style", color = Look.accent, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("round" to "Round", "bar" to "Rounded bar", "wave" to "Wave").forEach { (id, n) ->
                PChip(InstallShow.loader == id, n) { InstallShow.setLoader(ctx, id) }
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
    val scope = rememberCoroutineScope()
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            setThemeWallpaper(ctx, lookId, u.toString()); uri = u.toString()
            // Windows 11: the picked wallpaper is the app background at once, blurred under Mica (no see-through blue)
            if (lookId == "win11") scope.launch(Dispatchers.IO) {
                com.umutk.pulse.features.Backdrop.set(ctx, "custom", u)
            }
        }
    }
    // the stock pictures of this look's era (Windows 11 has its own A-D packs under Background); the own picture wins while one is set
    val walls = com.umutk.pulse.features.wallsFor(lookId)
    if (walls.isNotEmpty()) {
        val sel = com.umutk.pulse.features.wallSel(lookId)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            walls.forEachIndexed { i, id ->
                val on = uri == null && sel == i
                Image(painterResource(id), null, Modifier.size(width = 96.dp, height = 60.dp).clip(RoundedCornerShape(6.dp))
                    .border(if (on) 3.dp else 1.dp, if (on) Look.accent else T.sub, RoundedCornerShape(6.dp))
                    .clickable { com.umutk.pulse.features.setWallSel(ctx, lookId, i); setThemeWallpaper(ctx, lookId, null); uri = null }, contentScale = ContentScale.Crop)
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PButton("Wallpaper") { pick.launch(arrayOf("image/*")) }
        if (uri != null) PButton("Default") {
            setThemeWallpaper(ctx, lookId, null); uri = null
            if (lookId == "win11") scope.launch(Dispatchers.IO) { com.umutk.pulse.features.Backdrop.set(ctx, "off") }
        }
    }
}

private class LauncherIcon(val alias: String, val fg: Int, val bg: Int)
private val launcherIcons = listOf(
    LauncherIcon("IconDefault", R.drawable.ic_ti_default, R.color.ti_bg_default),
    LauncherIcon("IconWin95", R.drawable.ic_ti_95, R.color.ti_bg_95),
    LauncherIcon("IconXp", R.drawable.ic_ti_xp, R.color.ti_bg_xp),
    LauncherIcon("IconWin7", R.drawable.ic_ti_7, R.color.ti_bg_7),
    LauncherIcon("IconWin10", R.drawable.ic_ti_10, R.color.ti_bg_10),
    LauncherIcon("IconWin11", R.drawable.ic_ti_11, R.color.ti_bg_11),
)

/** Launcher icon choice: exactly one activity-alias stays enabled; the new one is enabled before the others are switched off. */
@Composable
private fun AppIconChooser() {
    val ctx = LocalContext.current
    fun on(i: LauncherIcon): Boolean {
        val st = ctx.packageManager.getComponentEnabledSetting(android.content.ComponentName(ctx, "com.umutk.pulse." + i.alias))
        return st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            (st == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && i === launcherIcons[0])
    }
    var cur by remember { mutableStateOf((launcherIcons.firstOrNull { on(it) } ?: launcherIcons[0]).alias) }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        launcherIcons.forEach { i ->
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                    .background(androidx.compose.ui.res.colorResource(i.bg))
                    .border(if (cur == i.alias) 3.dp else 1.dp, if (cur == i.alias) Look.accent else T.sub, RoundedCornerShape(16.dp))
                    .clickable {
                        val pm = ctx.packageManager
                        fun set(a: LauncherIcon, e: Boolean) = pm.setComponentEnabledSetting(
                            android.content.ComponentName(ctx, "com.umutk.pulse." + a.alias),
                            if (e) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            android.content.pm.PackageManager.DONT_KILL_APP,
                        )
                        set(i, true)
                        launcherIcons.forEach { if (it !== i) set(it, false) }
                        cur = i.alias
                    },
            ) { Image(painterResource(i.fg), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f }) }
        }
    }
}

/** Settings > Functions: what Pulse does, not how it looks: the recycle bin, how often data is read, the report and the root / Shizuku access. */
@Composable
fun FunctionsSettings() {
    val ctx = LocalContext.current
    PCard {
        SwitchRow(tr("Recycle bin", "Çöp kutusu"), tr("Deleted files wait there; off deletes for good", "Silinen dosyalar orada bekler; kapalıyken kalıcı silinir"), Opt.recycle) { Opt.recycle = it; Opt.save(ctx) }
        // the recycle bin page of the file manager
        if (Opt.recycle)
        Row(Modifier.fillMaxWidth().clickable { com.umutk.pulse.widgets.WidgetOpen.page = "recycle" }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("delete", T.text, 22.dp)
            Spacer(Modifier.width(12.dp))
            Text(tr("Open Recycle bin", "Çöp kutusunu aç"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 18.dp)
        }
    }
    PCard {
        SwitchRow(tr("Old icons", "Eski ikonlar"), tr("Windows 11 left menu: the first icon set; off uses the new Fluency icons", "Windows 11 sol menüsü: ilk ikon seti; kapalıyken yeni Fluency ikonları"), Opt.oldNavIcons) { Opt.oldNavIcons = it; Opt.save(ctx) }
    }
    Text(tr("Refresh rate", "Yenileme hızı"), color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    RefreshSettings()
    Text(tr("Report", "Rapor"), color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    ReportSettings()
    Text(tr("Permissions", "İzinler"), color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    RootSettings()
}
