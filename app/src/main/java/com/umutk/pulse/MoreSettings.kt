package com.umutk.pulse

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import android.widget.Toast
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.foundation.layout.*
import com.umutk.pulse.features.DnsSettings
import com.umutk.pulse.features.GlassPreview
import com.umutk.pulse.features.GlassPrefs
import com.umutk.pulse.features.InstallShow
import com.umutk.pulse.features.Downloads
import com.umutk.pulse.features.QUICK_DIRS
import android.os.Environment
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

/** An installer option: a Material 3 switch, whatever the look of the page. */
@Composable
private fun MatSwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = T.text, fontSize = 15.sp); if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 12.sp) }
        androidx.compose.material3.Switch(on, onChange)
    }
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Text(title, color = T.text, fontSize = 14.sp)
    PSlider(value, onChange, range, onDone = onDone)
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
            Pages.filter { it.id !in SecurityOnly }.forEach { p -> PChip(Opt.startPage == p.id, p.label) { Opt.startPage = p.id; Opt.save(ctx) } }
        }
    }
    PCard {
        SwitchRow("Temperature: Fahrenheit", "Celsius when off", Opt.tempF) { Opt.tempF = it; Opt.save(ctx) }
        SwitchRow("Keep screen on", "The screen stays on while Pulse is open", Opt.keepOn) { Opt.keepOn = it; Opt.save(ctx) }
        SwitchRow(tr("Old monitor filter", "Eski monitör filtresi"), tr("Scan lines and dots, like a CRT", "CRT gibi tarama çizgileri ve noktalar"), Opt.crt) { Opt.crt = it; Opt.save(ctx) }
        SwitchRow(tr("Sign-in screen at start", "Açılışta giriş ekranı"), tr("Windows 95, XP, 7, 10 and 11 looks", "Windows 95, XP, 7, 10 ve 11 görünümleri"), Opt.signIn) { Opt.signIn = it; Opt.save(ctx) }
        SwitchRow(tr("Start-up sound", "Açılış sesi"), tr("The Windows sound of the look when Pulse opens (off by default)", "Pulse açılırken görünümün Windows sesi (varsayılan kapalı)"), Opt.startSound) { Opt.startSound = it; Opt.save(ctx) }
        SwitchRow(tr("Opening animation", "Açılış animasyonu"), tr("The Pulse splash at start", "Başlangıçtaki Pulse açılış animasyonu"), Opt.splash) { Opt.splash = it; Opt.save(ctx) }
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
    PermissionKeepCard()
    DataBackupCard()
    ExtraAppsCard()
}

/** Export and import of settings and files (DataBackup). Import replaces the same files, then Pulse restarts. */
@Composable
private fun DataBackupCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    var askImport by remember { mutableStateOf<android.net.Uri?>(null) }
    var info by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    var restored by remember { mutableStateOf<DataBackup.Restored?>(null) }
    LaunchedEffect(Unit) { info = withContext(Dispatchers.IO) { DataBackup.summary(ctx) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            status = tr("Saving...", "Kaydediliyor...")
            val n = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { DataBackup.export(ctx, it) } ?: -1 }.getOrDefault(-1) }
            status = if (n >= 0) tr("Saved $n files", "$n dosya kaydedildi") else tr("Could not save", "Kaydedilemedi")
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) askImport = uri }
    PCard {
        Text(tr("Data", "Veri"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("Settings and files in one zip file.", "Ayarlar ve dosyalar tek bir zip dosyasında."), color = T.sub, fontSize = 12.sp)
        info?.let { (n, bytes) ->
            val size = android.text.format.Formatter.formatShortFileSize(ctx, bytes)
            Text(tr("$n files, $size", "$n dosya, $size"), color = T.sub, fontSize = 12.sp)
        }
        var auto by remember { mutableStateOf(AutoBackup.enabled(ctx)) }
        var lastAuto by remember { mutableStateOf(AutoBackup.lastRun(ctx)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Back up every day", "Her gün yedekle"), color = T.text, fontSize = 14.sp)
                Text(tr("Pulse and keyboard zips in Download/Pulse backup, the last 7 of each",
                    "Pulse ve klavye zip'leri Download/Pulse backup içinde, her birinin son 7'si"), color = T.sub, fontSize = 12.sp)
                if (lastAuto > 0) Text(tr("Last: ", "Son: ") + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(lastAuto)), color = T.sub, fontSize = 12.sp)
            }
            PSwitch(auto, { auto = it; AutoBackup.setEnabled(ctx, it) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Back up now", "Şimdi yedekle")) {
                scope.launch {
                    status = tr("Saving...", "Kaydediliyor...")
                    val ok = withContext(Dispatchers.IO) { AutoBackup.run(ctx) }
                    lastAuto = AutoBackup.lastRun(ctx)
                    status = if (ok) tr("Saved to Download/Pulse backup", "Download/Pulse backup içine kaydedildi") else tr("Could not save", "Kaydedilemedi")
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Export", "Dışa aktar")) { save.launch("pulse-backup.zip") }
            PButton(tr("Import", "İçe aktar")) { open.launch(arrayOf("application/zip", "application/octet-stream")) }
        }
        if (status.isNotEmpty()) Text(status, color = T.sub, fontSize = 12.sp)
        // the saved backups: how big each is, and a way to delete the old ones
        var saved by remember { mutableStateOf<List<AutoBackup.Saved>>(emptyList()) }
        var showSaved by remember { mutableStateOf(false) }
        LaunchedEffect(lastAuto, showSaved) { saved = withContext(Dispatchers.IO) { AutoBackup.list(ctx) } }
        val total = saved.sumOf { it.bytes }
        Row(Modifier.fillMaxWidth().clickable { showSaved = !showSaved }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Saved backups", "Kayıtlı yedekler") + " · ${saved.size} · " + android.text.format.Formatter.formatShortFileSize(ctx, total), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 20.dp, Modifier.rotate(if (showSaved) 180f else 0f))
        }
        if (showSaved) {
            saved.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name, color = T.text, fontSize = 13.sp, maxLines = 1)
                        Text(android.text.format.Formatter.formatShortFileSize(ctx, b.bytes) + "  ·  " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(b.time)), color = T.sub, fontSize = 11.sp)
                    }
                    Box(Modifier.size(38.dp).clickable { scope.launch { withContext(Dispatchers.IO) { AutoBackup.delete(ctx, b) }; saved = withContext(Dispatchers.IO) { AutoBackup.list(ctx) } } }, contentAlignment = Alignment.Center) { PIcon("delete", T.sub, 20.dp) }
                }
            }
            if (saved.isEmpty()) Text(tr("No backup saved yet.", "Henüz kayıtlı yedek yok."), color = T.sub, fontSize = 12.sp)
            if (saved.size > 1) PButton(tr("Delete all but the newest two", "En yeni ikisi hariç hepsini sil"), compact = true) {
                scope.launch { withContext(Dispatchers.IO) { saved.drop(2).forEach { AutoBackup.delete(ctx, it) } }; saved = withContext(Dispatchers.IO) { AutoBackup.list(ctx) } }
            }
        }
    }
    askImport?.let { uri ->
        PDialog(
            title = tr("Import data", "Veriyi içe aktar"),
            text = tr("Settings and files in the backup replace the same ones here. Pulse restarts after the import.",
                "Yedekteki ayarlar ve dosyalar, buradakilerin yerine geçer. İçe aktarmadan sonra Pulse yeniden başlar."),
            confirm = tr("Import", "İçe aktar"),
            onConfirm = {
                askImport = null
                scope.launch {
                    status = tr("Importing...", "İçe aktarılıyor...")
                    val r = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)?.use { DataBackup.restore(ctx, it) } }.getOrNull() }
                    if (r == null || r.files == 0) { status = tr("No Pulse data in this file", "Bu dosyada Pulse verisi yok"); return@launch }
                    restored = r
                }
            },
            onDismiss = { askImport = null },
        )
    }
    // shown after the import; the restart happens only when the user has read the list
    restored?.let { r ->
        fun restart() {
            // the running process keeps old settings in memory: start a fresh one and end this one
            ctx.startActivity(android.content.Intent(ctx, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
            android.os.Process.killProcess(android.os.Process.myPid())
        }
        PDialog(
            title = tr("Data restored", "Veri geri yüklendi"),
            text = buildString {
                append(tr("${r.files} files restored.", "${r.files} dosya geri yüklendi."))
                if (r.prefs.isNotEmpty()) append("\n\n" + tr("Settings", "Ayarlar") + " (${r.prefs.size}): " + r.prefs.joinToString(", "))
                if (r.folders.isNotEmpty()) append("\n\n" + tr("Files", "Dosyalar") + " (${r.folders.size}): " + r.folders.joinToString(", "))
                append("\n\n" + tr("Pulse restarts now.", "Pulse şimdi yeniden başlar."))
            },
            confirm = tr("Restart", "Yeniden başlat"),
            onConfirm = { restart() },
            onDismiss = { restart() },
            info = true,
        )
    }
}

/** Settings > Monitor: floating numbers, notification and alerts. */
@Composable
fun MonitorSettings() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val canOverlay = remember(tick) { com.umutk.pulse.Compat.canOverlay(ctx) }
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
        // the bundled pictures: the wide ones (1-16) and the phone ones (17-34); small previews, decoded off the main thread
        Text(tr("Wallpapers", "Duvar kağıtları"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        (1..17).chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // the 17th wide picture (the blue bloom) is pack35, after the phone ones
                row.forEach { i -> val pk = if (i == 17) 35 else i; BackdropThumb("win11_wall_$i", 1.6f, bd.mode == "pack$pk", Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { bd.set(ctx, "pack$pk") } } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Text(tr("Phone pictures", "Telefon resimleri"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        (1..18).chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { n -> BackdropThumb("mw11_$n", .55f, bd.mode == "pack${16 + n}", Modifier.weight(1f)) { scope.launch(Dispatchers.IO) { bd.set(ctx, "pack${16 + n}") } } }
            }
        }
    }
}

/** A low-quality preview of a bundled picture: decoded at 1/16 size on a background thread, so the page scrolls smoothly. */
@Composable
private fun BackdropThumb(name: String, aspect: Float, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val img by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            val res = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (res == 0) null else runCatching { android.graphics.BitmapFactory.decodeResource(ctx.resources, res, android.graphics.BitmapFactory.Options().apply { inSampleSize = 16; inPreferredConfig = android.graphics.Bitmap.Config.RGB_565 })?.asImageBitmap() }.getOrNull()
        }
    }
    Box(modifier.aspectRatio(aspect).clip(RoundedCornerShape(8.dp)).background(T.panel)
        .border(if (picked) 3.dp else 1.dp, if (picked) Look.accent else T.line, RoundedCornerShape(8.dp)).clickable(onClick = onClick)) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
    }
}

/** Settings > Installer: what the install window shows, how it looks and how it loads. */
@Composable
fun InstallWindowSettings() {
    val ctx = LocalContext.current
    InstallShow.load(ctx)
    PCard {
        Text("Install window", color = Look.accent, fontSize = 15.sp)
        MatSwitchRow("App name", "Show the name and its badge", InstallShow.name) { InstallShow.set(ctx, "inst_name", it) }
        MatSwitchRow("Version", "Show the version and code", InstallShow.version) { InstallShow.set(ctx, "inst_ver", it) }
        MatSwitchRow("Package name", "Show the package name", InstallShow.pkg) { InstallShow.set(ctx, "inst_pkg", it) }
        MatSwitchRow("Status", "Show New, Identical, Upgrade, Downgrade", InstallShow.chip) { InstallShow.set(ctx, "inst_chip", it) }
        MatSwitchRow("SDK versions", "Min and target SDK of the APK. Off by default", InstallShow.sdk) { InstallShow.set(ctx, "inst_sdk", it) }
        MatSwitchRow("Certificate", "The signing certificate fingerprint. Off by default", InstallShow.cert) { InstallShow.set(ctx, "inst_cert", it) }
        MatSwitchRow(tr("Other CPU files", "Diğer işlemci dosyaları"), tr("Leave out the splits of CPUs this phone does not use (v7, x86 on an arm64 phone): less space", "Bu telefonun kullanmadığı işlemcilerin parçalarını (arm64'te v7, x86) dışarıda bırak: daha az yer"), InstallShow.libs) { InstallShow.set(ctx, "inst_libs", it) }
        MatSwitchRow(tr("Shrink new installs", "Yeni kurulumları küçült"), tr("A single APK loses the CPU libraries this phone does not use (arm64 phone: v7 and x86 go). It is signed again with Pulse's key, so it only applies to apps that are not installed yet", "Tek APK, bu telefonun kullanmadığı işlemci kütüphanelerini (arm64'te v7 ve x86) kaybeder. Pulse anahtarıyla yeniden imzalanır; sadece henüz kurulu olmayan uygulamalara uygulanır"), InstallShow.shrink) { InstallShow.set(ctx, "inst_shrink", it) }
    }
    Downloads.load(ctx)
    PCard {
        Text(tr("Save to", "Kayıt konumu"), color = Look.accent, fontSize = 15.sp)
        var folder by remember { mutableStateOf(Downloads.dirPath) }
        val sd = Environment.getExternalStorageDirectory().path
        Text(Downloads.dirPath.removePrefix(sd).ifEmpty { "/" }, color = T.sub, fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            QUICK_DIRS.forEach { name ->
                val p = "$sd/$name"
                PChip(Downloads.dirPath == p, name) { Downloads.setDir(ctx, p); folder = p }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PField(folder, { folder = it }, "$sd/...", Modifier.weight(1f))
            Box(Modifier.padding(start = 8.dp).clickable {
                val d = java.io.File(folder.trim())
                if (d.isDirectory || d.mkdirs()) Downloads.setDir(ctx, d.path) else android.widget.Toast.makeText(ctx, tr("Cannot use this folder", "Bu klasör kullanılamaz"), android.widget.Toast.LENGTH_SHORT).show()
            }) { PIcon("folder", T.text, 24.dp) }
        }
    }
    PCard {
        Text(tr("Speed", "Hız"), color = Look.accent, fontSize = 15.sp)
        listOf(Triple("speed", "Unlimited", "Sınırsız"), Triple("bolt", "Steady", "Dengeli"), Triple("clock", "Slow", "Yavaş"), Triple("snail", "Snail mode", "Salyangoz modu")).forEachIndexed { i, (ic, en, trk) ->
            Row(Modifier.fillMaxWidth().clickable { Downloads.setSpeed(ctx, i) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PIcon(ic, if (Downloads.speed == i) Look.accent else T.sub, 22.dp)
                Text(tr(en, trk), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (Downloads.speed == i) PIcon("check", Look.accent, 20.dp)
            }
        }
    }
    PCard {
        Text("Default app", color = Look.accent, fontSize = 15.sp)
        PButton("Make Pulse the default for APK files") { com.umutk.pulse.features.openDefaultApkSettings(ctx) }
    }
    PCard {
        Text("Window look", color = Look.accent, fontSize = 15.sp)
        // one preview per look, three to a row, the name under each; "App theme" shows the look of the app right now
        val installLooks = listOf("material" to "Material You", "app" to tr("App theme", "Uygulama teması"), "amoled" to "AMOLED black", "coloros" to "ColorOS", "win11" to "Windows 11", "win10" to "Windows 10",
            "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95", "oneui" to "One UI")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        installLooks.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (id, n) ->
                    val sel = InstallShow.theme == id
                    Column(Modifier.weight(1f).clickable { InstallShow.setTheme(ctx, id) }
                        .semantics { contentDescription = n }.border(if (sel) 2.dp else 1.dp, if (sel) Look.accent else T.line, RoundedCornerShape(10.dp)).padding(6.dp)) {
                        com.umutk.pulse.features.InstallPreview(id)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
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
        Text(tr("Install source", "Kurulum kaynağı"), color = Look.accent, fontSize = 15.sp)
        androidx.compose.runtime.CompositionLocalProvider(LocalWindowLook provides com.umutk.pulse.features.installLook()) { com.umutk.pulse.features.InstallSourcePicker() }
    }
    PCard {
        Text("Security", color = Look.accent, fontSize = 15.sp)
        MatSwitchRow("Require biometric to install", "Fingerprint or face before each install. Off: never asked", InstallShow.biometric) { InstallShow.set(ctx, "inst_bio", it) }
        MatSwitchRow("Cancel installation on dismiss", "Closing the window stops a waiting biometric check", InstallShow.cancelOnDismiss) { InstallShow.set(ctx, "inst_cancel", it) }
    }
    PCard {
        Text("Details", color = Look.accent, fontSize = 15.sp)
        MatSwitchRow("Show version compare in single line", "Installed and new version on one line. Off: two lines", InstallShow.cmpSingle) { InstallShow.set(ctx, "inst_cmp1", it) }
        MatSwitchRow("Show SDK compare in multi lines", "Min and target SDK on their own lines. Off: one line", InstallShow.sdkMulti) { InstallShow.set(ctx, "inst_sdkm", it) }
        MatSwitchRow("Show identical data as a single value", "When the old and new values match, show one value", InstallShow.sameSingle) { InstallShow.set(ctx, "inst_same1", it) }
        MatSwitchRow("Show extended menu", "More options in the install dialog", InstallShow.extended) { InstallShow.set(ctx, "inst_ext", it) }
        MatSwitchRow("Show Smart Suggestions", "Next steps shown when an install fails", InstallShow.suggest) { InstallShow.set(ctx, "inst_sugg", it) }
        MatSwitchRow("Expand panel by default", "Install options open when the dialog opens", InstallShow.expand) { InstallShow.set(ctx, "inst_exp", it) }
        MatSwitchRow("Rounded corners", "Off: square corners in the install window", InstallShow.round) { InstallShow.set(ctx, "inst_round", it) }
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
    if (walls.isNotEmpty() && lookId != "win11") {
        val sel = com.umutk.pulse.features.wallSel(lookId)
        Box(Modifier.padding(vertical = 6.dp)) {
            com.umutk.pulse.features.WallGrid(lookId, if (uri == null) sel else -1) { i ->
                com.umutk.pulse.features.setWallSel(ctx, lookId, i); setThemeWallpaper(ctx, lookId, null); uri = null
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

/** Settings > Functions: what Pulse does, not how it looks: notification history, how often data is read, the report and the root / Shizuku access. */
@Composable
fun FunctionsSettings() {
    val ctx = LocalContext.current
    val openTool = LocalOpenTool.current
    PCard {
        // the same switch as the notification history page (NotifHist, prefs "notifhist"); the label opens the page while it is on
        var notif by remember { mutableStateOf(com.umutk.pulse.extras.notifhist.NotifHist.enabled(ctx)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon("notifications", T.text, 22.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp).clickable { if (notif) openTool("notifhist") }) {
                Text(tr("Notification history", "Bildirim geçmişi"), color = T.text, fontSize = 15.sp)
                Text(tr("Keeps the notifications you get; off keeps none", "Aldığın bildirimleri saklar; kapalıyken hiçbiri saklanmaz"), color = T.sub, fontSize = 12.sp)
            }
            PSwitch(notif, { notif = it; com.umutk.pulse.extras.notifhist.NotifHist.setEnabled(ctx, it) })
        }
    }
    PCard {
        SwitchRow(tr("Files: full screen", "Dosyalar: tam ekran"), "", Opt.filesFull) { Opt.filesFull = it; Opt.save(ctx) }
    }
    // where App manager's Extract APK writes (default: Downloads/Pulse/extract)
    PCard {
        Text(tr("APK extract folder", "APK çıkarma klasörü"), color = Look.accent, fontSize = 15.sp)
        var apkDir by remember { mutableStateOf(com.umutk.pulse.features.ApkExtract.path(ctx)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PField(apkDir, { apkDir = it }, "/storage/emulated/0/Download/Pulse/extract", Modifier.weight(1f))
            Box(Modifier.padding(start = 8.dp).clickable {
                val d = java.io.File(apkDir.trim())
                if (d.isDirectory || d.mkdirs()) com.umutk.pulse.features.ApkExtract.set(ctx, d.path)
                else android.widget.Toast.makeText(ctx, tr("Cannot use this folder", "Bu klasör kullanılamaz"), android.widget.Toast.LENGTH_SHORT).show()
            }) { PIcon("folder", T.text, 24.dp) }
        }
        Text(tr("App manager's Extract APK saves here", "App manager'ın APK çıkarma işlemi buraya kaydeder"), color = T.sub, fontSize = 12.sp)
    }
    // menus and message boxes: the current look, or Material You for every look
    PCard {
        Text(tr("Pop-up windows", "Açılır pencereler"), color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(Look.windowTheme != "material", tr("Current theme", "Mevcut tema")) { Cfg.setWindowTheme(ctx, "win") }
            PChip(Look.windowTheme == "material", "Material You") { Cfg.setWindowTheme(ctx, "material") }
        }
    }
    Text(tr("Refresh rate", "Yenileme hızı"), color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    RefreshSettings()
    Text(tr("Report", "Rapor"), color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    ReportSettings()
    Text(tr("Permissions", "İzinler"), color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    RootSettings()
}

/** Fresh installs only, once: the extra-app launcher aliases start switched off. Existing installs keep their state. */
internal fun disableExtraAppsOnFreshInstall(ctx: android.content.Context) {
    val pm = ctx.packageManager
    val pi = pm.getPackageInfo(ctx.packageName, 0)
    val prefs = ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE)
    if (pi.firstInstallTime != pi.lastUpdateTime || prefs.getBoolean("extraAppsOff", false)) return
    listOf("FilesLauncher", "PhotosLauncher", "CalendarLauncher", "ContactsLauncher", "PaintLauncher", "CalculatorLauncher", "ArtCraftLauncher", "MusicLauncher", "VideoLauncher", "WhiteNoiseLauncher", "VoiceLauncher", "NotesLauncher", "WeatherLauncher", "MessagesLauncher", "ScreenRecLauncher", "NotifHistLauncher", "LocalSendLauncher", "KeyboardLauncher", "ExtraStoreLauncher", "ClockLauncher").forEach {
        // a missing alias throws on startup, so one bad name must not stop the rest (or the app)
        runCatching { pm.setComponentEnabledSetting(android.content.ComponentName(ctx, "com.umutk.pulse." + it), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP) }
    }
    prefs.edit().putBoolean("extraAppsOff", true).apply()
}

/** Settings > General: which of the extra apps (Files, Photos, Calendar, Contacts) show in the launcher; each is a launcher activity-alias. */
@Composable
private fun ExtraAppsCard() {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val openTool = LocalOpenTool.current
    val apps = listOf(
        Triple("FilesLauncher", R.drawable.ic_files, tr("Files", "Dosyalar")),
        Triple("PhotosLauncher", R.drawable.ic_photos, tr("Photos", "Fotoğraflar")),
        Triple("CalendarLauncher", R.drawable.ic_calendar, tr("Calendar", "Takvim")),
        Triple("ContactsLauncher", R.drawable.ic_contacts, tr("Contacts", "Kişiler")),
        Triple("PaintLauncher", R.drawable.mat_palette, "Paint"),
        Triple("CalculatorLauncher", R.drawable.ic_qs_calc, tr("Calculator", "Hesap makinesi")),
        Triple("ClockLauncher", R.drawable.ic_ad_clock_fg, tr("Clock", "Saat")),
        Triple("ArtCraftLauncher", R.drawable.mat_palette, "ArtCraft"),
        Triple("MusicLauncher", R.drawable.ic_music, tr("Music player", "Müzik çalar")),
        Triple("VideoLauncher", R.drawable.ic_video, tr("Video", "Video")),
        Triple("WhiteNoiseLauncher", R.drawable.ic_whitenoise, tr("White noise", "Beyaz gürültü")),
        Triple("VoiceLauncher", R.drawable.mat_voice, tr("Voice recorder", "Ses kaydedici")),
        Triple("NotesLauncher", R.drawable.mat_notes, tr("Notes", "Notlar")),
        Triple("WeatherLauncher", R.drawable.mat_weather, tr("Weather", "Hava durumu")),
        Triple("MessagesLauncher", R.drawable.mat_messages, tr("Messages", "Mesajlar")),
        Triple("ScreenRecLauncher", R.drawable.mat_record, tr("Screen recorder", "Ekran kaydı")),
        Triple("NotifHistLauncher", R.drawable.mat_notifications, tr("Notification history", "Bildirim geçmişi")),
        Triple("LocalSendLauncher", R.drawable.mat_wifi, "LocalSend"),
        Triple("KeyboardLauncher", R.drawable.mat_keyboard, tr("Keyboard", "Klavye")),
        Triple("PaletteLauncher", R.drawable.mat_palette, "Palette"),
        Triple("BlackOutLauncher", R.drawable.ic_ad_blackout_fg, "BlackOut"),
        Triple("IslandLauncher", R.drawable.ic_ad_island_fg, "Island"),
    )
    // the apps in catalogs: a heading before each group
    val catalog = listOf(
        (tr("Files and documents", "Dosyalar ve belgeler")) to listOf("FilesLauncher", "PhotosLauncher", "NotesLauncher", "VoiceLauncher"),
        (tr("Media", "Medya")) to listOf("MusicLauncher", "VideoLauncher", "WhiteNoiseLauncher", "ScreenRecLauncher", "PaintLauncher", "ArtCraftLauncher"),
        (tr("Tools and life", "Araçlar ve günlük")) to listOf("CalculatorLauncher", "ClockLauncher", "WeatherLauncher", "CalendarLauncher", "ContactsLauncher"),
        (tr("Communication and system", "İletişim ve sistem")) to listOf("MessagesLauncher", "NotifHistLauncher", "LocalSendLauncher", "KeyboardLauncher", "PaletteLauncher", "BlackOutLauncher", "IslandLauncher"),
    )
    val routeOf = mapOf("MusicLauncher" to "music", "VideoLauncher" to "video", "WhiteNoiseLauncher" to "whitenoise", "VoiceLauncher" to "voice", "NotesLauncher" to "notes", "PaintLauncher" to "paint",
        "CalculatorLauncher" to "calculator", "ClockLauncher" to "clock", "WeatherLauncher" to "weather", "MessagesLauncher" to "messages", "ScreenRecLauncher" to "screenrec", "NotifHistLauncher" to "notifhist", "LocalSendLauncher" to "localsend", "KeyboardLauncher" to "keyboard", "PaletteLauncher" to "palette", "BlackOutLauncher" to "blackout", "IslandLauncher" to "island")
    val ownActivity = mapOf("FilesLauncher" to "FilesActivity", "CalendarLauncher" to "CalendarActivity", "ContactsLauncher" to "ContactsActivity", "ArtCraftLauncher" to "ArtCraftActivity")
    /** Asks the launcher to put this app on the home screen; the shortcut opens the app directly, whether or not its app-drawer icon is switched on. */
    fun pinToHome(alias: String, label: String, icon: Int) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val sm = ctx.getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
        if (!sm.isRequestPinShortcutSupported) return
        val i = when {
            alias in ownActivity -> Intent(Intent.ACTION_MAIN).setClassName(ctx, "com.umutk.pulse." + ownActivity.getValue(alias))
            alias in routeOf -> Intent(Intent.ACTION_MAIN).setClassName(ctx, "com.umutk.pulse.ExtraToolActivity").putExtra("route", routeOf.getValue(alias))
            else -> Intent(Intent.ACTION_MAIN).setComponent(android.content.ComponentName(ctx, "com.umutk.pulse." + alias)).addCategory(Intent.CATEGORY_LAUNCHER)
        }
        // the app's own launcher picture (the adaptive icon), so every shortcut on the home screen looks like its app, not like Pulse
        val mip = mapOf("FilesLauncher" to "ic_files", "PhotosLauncher" to "ic_photos", "CalendarLauncher" to "ic_calendar", "ContactsLauncher" to "ic_contacts", "MusicLauncher" to "ic_music", "VideoLauncher" to "ic_video",
            "PaintLauncher" to "ic_ad_paint", "ClockLauncher" to "ic_ad_clock", "CalculatorLauncher" to "ic_ad_calc", "ArtCraftLauncher" to "ic_ad_artcraft", "WhiteNoiseLauncher" to "ic_ad_noise", "VoiceLauncher" to "ic_ad_voice", "NotesLauncher" to "ic_ad_notes",
            "WeatherLauncher" to "ic_ad_weather", "MessagesLauncher" to "ic_ad_messages", "ScreenRecLauncher" to "ic_ad_record", "NotifHistLauncher" to "ic_ad_notif", "LocalSendLauncher" to "ic_ad_localsend", "KeyboardLauncher" to "ic_ad_keyboard", "ExtraStoreLauncher" to "ic_ad_extras")
        val res = mip[alias]?.let { ctx.resources.getIdentifier(it, "mipmap", ctx.packageName) } ?: 0
        val ic = if (res != 0) android.graphics.drawable.Icon.createWithResource(ctx, res) else android.graphics.drawable.Icon.createWithResource(ctx, icon)
        sm.requestPinShortcut(android.content.pm.ShortcutInfo.Builder(ctx, "app_$alias").setShortLabel(label).setIcon(ic).setIntent(i).build(), null)
    }
    // Windows 11 glyph per app (FluentPaths keys; Fluent UI System Icons, MIT License, Microsoft)
    val fluent = mapOf("FilesLauncher" to "extraFiles", "PhotosLauncher" to "extraPhotos", "CalendarLauncher" to "extraCalendar", "ContactsLauncher" to "extraContacts",
        "PaintLauncher" to "extraPaint", "ClockLauncher" to "extraClock", "CalculatorLauncher" to "extraCalculator", "ArtCraftLauncher" to "extraArtCraft", "MusicLauncher" to "extraMusic", "VideoLauncher" to "extraVideo", "WhiteNoiseLauncher" to "extraWhiteNoise",
        "VoiceLauncher" to "extraVoice", "NotesLauncher" to "extraNotes", "WeatherLauncher" to "extraWeather", "MessagesLauncher" to "extraMessages",
        "ScreenRecLauncher" to "extraScreenRec", "NotifHistLauncher" to "extraNotifHist", "LocalSendLauncher" to "extraLocalSend", "KeyboardLauncher" to "extraKeyboard", "PaletteLauncher" to "extraPalette", "BlackOutLauncher" to "extraBlackOut", "IslandLauncher" to "extraIsland", "ExtraStoreLauncher" to "extraStore")
    fun cn(a: String) = android.content.ComponentName(ctx, "com.umutk.pulse." + a)
    fun shown(a: String) = pm.getComponentEnabledSetting(cn(a)) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    val state = remember { mutableStateMapOf<String, Boolean>().apply { apps.forEach { put(it.first, shown(it.first)) } } }
    var rev by remember { mutableIntStateOf(0) }
    var placeOpen by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ctx) {
        val lo = ctx as? androidx.lifecycle.LifecycleOwner
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) rev++ }
        lo?.lifecycle?.addObserver(ob)
        onDispose { lo?.lifecycle?.removeObserver(ob) }
    }
    PCard {
        Text(tr("Apps", "Uygulamalar"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        catalog.forEach { (heading, members) ->
        Text(heading, color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        apps.filter { it.first in members }.forEach { (alias, icon, label) ->
            // every row of this card has the same height and the same 36 dp icon slot
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                // Windows 11: the app's own Fluent glyph (the real Fluent System Icons path); the other looks keep the drawable tile
                // the colour Fluent picture of the app (Fluent UI System Icons, colour set); Windows 11 with the Line icon style keeps the thin glyph
                val colorIcon = if (T.id == "win11" && IconStyle.of(IconCat.APPS) != "line") fluentColorRes(ctx, FC_APP[alias] ?: "") else 0
                if (T.id == "win11" && colorIcon == 0) Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { FluentGlyph(fluent.getValue(alias), FluentFilled.color(fluent.getValue(alias)), 24.dp) }
                else if (colorIcon != 0) Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Image(painterResource(colorIcon), null, Modifier.size(30.dp)) }
                else Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(androidx.compose.ui.graphics.Color.Black)) {
                    Image(painterResource(icon), null, Modifier.fillMaxSize().graphicsLayer { scaleX = 1.5f; scaleY = 1.5f })
                }
                Spacer(Modifier.width(12.dp))
                // a tap on the name opens the app
                Text(label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).clickable {
                    if (alias == "ExtraStoreLauncher") openTool("extrastore") else AppPlace.byAlias(alias)?.run(ctx, openTool)
                }.padding(vertical = 14.dp))
                // the pin puts the app on the home screen of the phone
                PIcon("pin", T.sub, 20.dp, Modifier.clickable { pinToHome(alias, label, icon) }.padding(horizontal = 6.dp))
                // the chevron folds where the app is listed inside Pulse (top-right menu, left menu, Settings only); the switch is its home-screen icon
                PIcon("chevron", T.sub, 20.dp, Modifier.clickable { placeOpen = if (placeOpen == alias) null else alias }.padding(horizontal = 6.dp).graphicsLayer { rotationZ = if (placeOpen == alias) 180f else 0f })
                PSwitch(state[alias] == true, { on ->
                    pm.setComponentEnabledSetting(cn(alias), if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP)
                    state[alias] = on
                    rev++
                })
            }
            if (placeOpen == alias) PlaceChoice(AppPlace.where(alias), listOf("menu" to "more", "side" to "menu", "hidden" to "eyeOff")) { AppPlace.set(ctx, alias, it); rev++ }
        }
        }
        // the optional tools: the switch shows one in this card; the chevron folds the four places (menu, this card, left menu, Settings only)
        ToolPlace.all.forEach { t ->
            var open by remember(t.id) { mutableStateOf(false) }
            val cur = ToolPlace.where(ctx, t)
            val shownHere = cur == "extra"
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Windows 11: the Fluent vector of the tool, one glyph per tool (the PNG pack is bypassed, it has black glyphs)
                    // Windows 11: the Fluent colour picture of the tool (fc_<id>), else the Fluent glyph in the text colour
                    val toolColor = if (T.id == "win11") fluentColorRes(ctx, if (t.id == "extrastore") "store" else t.id) else 0
                    if (toolColor != 0) Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Image(painterResource(toolColor), null, Modifier.size(30.dp)) }
                    else if (T.id == "win11") Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { FluentGlyph("tool_${t.id}", T.text, 24.dp) } else {
                        // Office and Download manager get the black launcher tile with a white outline glyph (dark: accent glyph)
                        val tile = when (t.id) { "office" -> R.drawable.ic_tile_office; "download" -> R.drawable.ic_tile_download; else -> 0 }
                        if (tile != 0) Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (T.dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White), contentAlignment = Alignment.Center) {
                            Image(painterResource(tile), null, Modifier.fillMaxSize().padding(6.dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (T.dark) Look.accent else androidx.compose.ui.graphics.Color.Black))
                        } else Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { PIcon(t.key, T.text, 22.dp) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(tr(t.en, t.trName), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).clickable(enabled = shownHere) {
                        // a tool screen opens on top; a page-based one opens the main app on its page
                        if (t.isTool) openTool(t.target)
                        else ctx.startActivity(Intent(ctx, MainActivity::class.java).putExtra("start", t.target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    })
                    PIcon("chevron", T.sub, 20.dp, Modifier.clickable { open = !open }.padding(horizontal = 6.dp).graphicsLayer { rotationZ = if (open) 180f else 0f })
                    // on: the tool is listed in Extra apps (and the Apps group of the ⋮ menu); off: it is hidden from both
                    PSwitch(shownHere, { on -> ToolPlace.set(ctx, t, if (on) "extra" else "hidden"); rev++ })
                }
                // where it shows, one radio per place with the place's icon: the overflow menu, the Apps group, the left menu, nowhere (Settings only)
                if (open) PlaceChoice(cur, buildList {
                    add("menu" to "more"); add("extra" to "apps")
                    // a tool with a page of its own has its switch with the pages below, so the left menu is not offered twice
                    if (!ToolPlace.hasPage(t)) add("side" to "menu")
                    add("hidden" to "eyeOff")
                }) { ToolPlace.set(ctx, t, it); rev++ }
            }
        }
        // the app's own pages: shown in the left menu or hidden
        Pages.filter { it.id !in SecurityOnly }.forEach { p ->
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                val pageColor = if (T.id == "win11" && IconStyle.of(IconCat.MENU) != "line") fluentColorRes(ctx, FC_PAGE[p.id] ?: p.id) else 0
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { if (pageColor != 0) Image(painterResource(pageColor), null, Modifier.size(30.dp)) else if (T.id == "win11" && FluentFilled.map.containsKey("page_${p.icon}")) FluentGlyph("page_${p.icon}", FluentFilled.color("page_${p.icon}"), 24.dp) else PIcon(p.icon, T.text, 22.dp) }
                Spacer(Modifier.width(12.dp))
                // a tap on the name opens that page; the switch only decides whether it is in the left menu
                Text(p.label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).clickable { com.umutk.pulse.widgets.WidgetOpen.page = p.id })
                PSwitch(p.id !in Opt.hidden, { on -> Opt.hidden = if (on) Opt.hidden - p.id else Opt.hidden + p.id; Opt.save(ctx) })
            }
        }
    }
}

/** One radio per place, each under that place's icon; [options] are (value, icon key). */
@Composable
private fun PlaceChoice(selected: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 48.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEach { (value, icon) ->
            Column(Modifier.clickable { onPick(value) }, horizontalAlignment = Alignment.CenterHorizontally) {
                PIcon(icon, if (selected == value) Look.accent else T.sub, 22.dp)
                Spacer(Modifier.height(4.dp))
                PRadio(selected == value) { onPick(value) }
            }
        }
    }
}

/** A Fluent vector glyph straight from FluentPaths (Windows 11 tool icons; the PNG pack is not used here). */
@Composable
private fun FluentGlyph(k: String, tint: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp) {
    // the Line icon style (Settings > Look) keeps the thin glyphs in the text colour; Fluent colour draws the filled glyph in its colour
    val line = IconStyle.of(IconCat.APPS) == "line" || IconStyle.of(IconCat.TOOLS) == "line"
    val paths = remember(k, line) { ((if (line) null else FluentFilled.map[k]) ?: FluentPaths.map[k] ?: FluentPaths.map["info"]!!).map { PathParser().parsePathString(it).toPath() } }
    val ink = if (line) T.text else tint
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, pivot = Offset.Zero) { paths.forEach { drawPath(it, ink) } }
    }
}

/** Intent a launcher alias should win as the default handler (alias name -> intent, activity classes that count as Pulse's). */
private fun extraProbe(alias: String): Pair<Intent, List<String>> = when (alias) {
    "FilesLauncher" -> Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.externalstorage.documents/document/primary%3A"), "vnd.android.document/directory") to listOf("FilesActivity", "MainActivity")
    "PhotosLauncher" -> Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://media/external/images/media/1"), "image/*") to listOf("ImageViewActivity")
    "CalendarLauncher" -> Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.calendar/events/1"), "vnd.android.cursor.item/event") to listOf("CalendarActivity")
    else -> Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.contacts/contacts/1"), "vnd.android.cursor.item/contact") to listOf("ContactsActivity")
}

internal fun extraIsDefault(ctx: android.content.Context, alias: String): Boolean = try {
    val (i, names) = extraProbe(alias)
    val ai = ctx.packageManager.resolveActivity(i, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
    ai != null && ai.packageName == ctx.packageName && names.any { ai.name.endsWith(it) }
} catch (_: Throwable) { false }

/** Roles the platform has for an app; Files has none (chooser only). */
private fun extraRole(alias: String) = when (alias) {
    "PhotosLauncher" -> "android.app.role.GALLERY"; "CalendarLauncher" -> "android.app.role.CALENDAR"; "ContactsLauncher" -> "android.app.role.CONTACTS"; else -> null
}

/** With root/Shizuku: add Pulse as the role holder. Otherwise (or when that did not stick) the system "Open with" sheet is shown, where "Always" makes the choice. */
internal fun extraSetDefault(ctx: android.content.Context, alias: String, done: () -> Unit) {
    Thread {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        try {
            val role = extraRole(alias)
            if (role != null && (Shell.root == true || Shell.shizuku == 2)) {
                val has = Shell.run("cmd role get-role-holders --user 0 $role")
                if (!has.contains("Exception") && !has.contains("Unknown") && !has.contains("not found", true))
                    Shell.run("cmd role add-role-holder --user 0 $role ${ctx.packageName}")
            }
            if (extraIsDefault(ctx, alias)) { h.post { Toast.makeText(ctx, tr("Default", "Varsayılan"), Toast.LENGTH_SHORT).show(); done() }; return@Thread }
            val dir = java.io.File(ctx.cacheDir, "installer").apply { mkdirs() }
            val (name, mime, body) = when (alias) {
                "PhotosLauncher" -> Triple("sample.png", "image/png", android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==", 0))
                "CalendarLauncher" -> Triple("sample.ics", "text/calendar", "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n".toByteArray())
                "ContactsLauncher" -> Triple("sample.vcf", "text/x-vcard", "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Sample\r\nEND:VCARD\r\n".toByteArray())
                else -> Triple("", "", ByteArray(0))
            }
            val i = if (name.isEmpty()) extraProbe(alias).first else {
                val f = java.io.File(dir, name).apply { writeBytes(body) }
                Intent(Intent.ACTION_VIEW).setDataAndType(androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            h.post { try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {} }
        } catch (_: Throwable) {}
    }.start()
}

/** "Default app" row for a settings page of one of the four apps that handle a system link (files, pictures, events, contacts). */
@Composable
internal fun ExtraDefaultRow(alias: String) {
    val ctx = LocalContext.current
    var rev by remember { mutableStateOf(0) }
    val isDef = remember(rev) { extraIsDefault(ctx, alias) }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tr("Default app", "Varsayılan uygulama"), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(if (isDef) tr("Yes", "Evet") else tr("No", "Hayır"), color = T.sub, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        PButton(tr("Make default", "Varsayılan yap")) { extraSetDefault(ctx, alias) { rev++ } }
    }
}

/** Colour Fluent picture res/drawable-nodpi/fc_<key>.png (Fluent UI System Icons colour set, MIT, rendered at 144 px); 0 when there is none. */
internal fun fluentColorRes(ctx: android.content.Context, key: String): Int =
    if (key.isEmpty()) 0 else ctx.resources.getIdentifier("fc_${key.lowercase()}", "drawable", ctx.packageName)

private val FC_APP = mapOf("CalculatorLauncher" to "calculator", "ClockLauncher" to "clock", "FilesLauncher" to "files", "PhotosLauncher" to "photos", "CalendarLauncher" to "calendar", "ContactsLauncher" to "contacts", "PaintLauncher" to "paint",
    "LocalSendLauncher" to "localsend", "KeyboardLauncher" to "keyboard", "PaletteLauncher" to "palette", "BlackOutLauncher" to "blackout", "IslandLauncher" to "island", "ArtCraftLauncher" to "artcraft", "MusicLauncher" to "music", "VideoLauncher" to "video", "WhiteNoiseLauncher" to "whitenoise", "VoiceLauncher" to "voice", "NotesLauncher" to "notes",
    "WeatherLauncher" to "weather", "MessagesLauncher" to "messages", "ScreenRecLauncher" to "screenrec", "NotifHistLauncher" to "notifhist", "ExtraStoreLauncher" to "store")
private val FC_PAGE = mapOf("downloads" to "download", "files" to "files")


/**
 * Android 11+ takes the permissions of an app that was not opened for months ("Pause app activity if unused" / permission auto-reset): the accessibility,
 * notification, usage and overlay grants then vanish. This card shows whether Pulse is exempt and sets it: with root the exemption is written directly (the
 * same appop the system screen writes), without root the system screen of Pulse opens, where the switch is turned off by hand.
 */
@Composable
private fun PermissionKeepCard() {
    if (android.os.Build.VERSION.SDK_INT < 30) return
    val ctx = LocalContext.current
    fun exempt() = runCatching { ctx.packageManager.isAutoRevokeWhitelisted }.getOrDefault(false)
    var on by remember { mutableStateOf(exempt()) }
    var note by remember { mutableStateOf("") }
    // coming back from the system screen: read the state again
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) on = exempt() }
        lifecycle.addObserver(ob); onDispose { lifecycle.removeObserver(ob) }
    }
    PCard {
        SwitchRow(tr("Keep permissions", "İzinleri koru"),
            tr("Stops Android from taking Pulse's permissions back when it is not opened for a while", "Pulse bir süre açılmazsa Android'in izinlerini geri almasını engeller"), on) { want ->
            if (Shell.root == true) {
                Thread {
                    val r = Shell.runRoot("cmd appops set ${ctx.packageName} AUTO_REVOKE_PERMISSIONS_IF_UNUSED " + (if (want) "ignore" else "allow") + "; echo __OK")
                    android.os.Handler(android.os.Looper.getMainLooper()).post { on = exempt(); note = if (r.contains("__OK") && on == want) "" else tr("The system did not take it.", "Sistem kabul etmedi.") }
                }.start()
            } else {
                note = tr("Turn off \"Pause app activity if unused\" on the screen that opens.", "Açılan ekranda \"Kullanılmıyorsa uygulama etkinliğini duraklat\" seçeneğini kapat.")
                runCatching { ctx.startActivity(Intent(Intent.ACTION_AUTO_REVOKE_PERMISSIONS, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
    }
}
