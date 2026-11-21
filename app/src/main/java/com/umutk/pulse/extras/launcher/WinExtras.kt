package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PTheme
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Win+L / Lock of the Windows desktops: the lock screen of that Windows (picture, big clock), then its sign-in screen. Only the launcher is locked, not the phone. */
@Composable
internal fun WinLockHost(th: PTheme) {
    var stage by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        if (stage == 0) WinLockScreen(th.id) { stage = 1 } else com.umutk.pulse.LoginScreen(th) { LauncherSession.lockOpen = false }
    }
}

@Composable
private fun WinLockScreen(lookId: String, onOpen: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val pic = remember(lookId, land) { wallpaperRes(lookId, land) }
    val locale = Locale.getDefault()
    val time = SimpleDateFormat("HH:mm", locale).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now))
    var dragged by remember { mutableLongStateOf(0L) }
    Box(Modifier.fillMaxSize().background(Color(0xFF0B1C34)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onOpen() }
        .pointerInput(Unit) { detectVerticalDragGestures(onDragEnd = { onOpen() }) { _, _ -> dragged++ } }) {
        if (pic != null) Image(painterResource(pic), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f)))
        if (lookId == "win10") Column(Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 64.dp)) {
            Text(time, color = Color.White, fontSize = 84.sp, fontWeight = FontWeight.Light)
            Text(date, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light)
        } else Column(Modifier.align(Alignment.TopCenter).padding(top = 96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, color = Color.White, fontSize = if (lookId == "win11") 22.sp else 20.sp, fontWeight = FontWeight.Normal)
            Text(time, color = Color.White, fontSize = if (lookId == "win11") 110.sp else 90.sp, fontWeight = if (lookId == "win11") FontWeight.SemiBold else FontWeight.Light)
        }
    }
}

/** Shut down and Restart of the Windows desktops: the screen of that Windows ("Shutting down", a spinner; now and then "Working on updates") and then the launcher closes or starts again. */
@Composable
internal fun WinPowerScreen(lookId: String, mode: String) {
    val activity = LocalContext.current as? android.app.Activity
    // Windows 10 and 11 sometimes install updates on the way out
    val updates = remember { (lookId == "win10" || lookId == "win11") && Math.random() < .25 }
    var pct by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        if (updates) { while (pct < 100) { delay(45); pct += 1 } } else delay(if (lookId == "win95") 2500L else 2200L)
        delay(300)
        LauncherSession.powerScreen = ""
        if (mode == "off") activity?.finishAndRemoveTask()
        else if (activity != null) { LauncherSession.forceBoot = 1; LauncherSession.booted = false; activity.recreate() }
    }
    val bg = when (lookId) { "win11" -> Color(0xFF0A1B2E); "win10" -> Color(0xFF0063B1); "xp" -> Color(0xFF5A7EDC); "win7" -> Color(0xFF0E2A4D); else -> Color.Black }
    val word = if (mode == "off") tr("Shutting down", "Kapatılıyor") else tr("Restarting", "Yeniden başlatılıyor")
    Box(Modifier.fillMaxSize().background(bg).pointerInput(Unit) { detectTapGestures { } }, contentAlignment = Alignment.Center) {
        if (lookId == "win95") {
            Text(if (mode == "off") tr("It's now safe to turn off your computer.", "Bilgisayarınızı şimdi güvenle kapatabilirsiniz.") else tr("Windows is restarting...", "Windows yeniden başlatılıyor..."), color = Color(0xFFFF8C00), fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.width(40.dp).height(40.dp))
            if (updates) {
                Text(tr("Working on updates", "Güncelleştirmeler üzerinde çalışılıyor"), color = Color.White, fontSize = 22.sp)
                Text("$pct% " + tr("complete", "tamamlandı"), color = Color.White, fontSize = 18.sp)
                Text(tr("Don't turn off your computer", "Bilgisayarınızı kapatmayın"), color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
            } else Text(word, color = Color.White, fontSize = 24.sp)
        }
    }
}

/** Win+V: the clipboard history (the texts the keyboard kept) as a small panel; a tap puts one back on the clipboard. */
@Composable
internal fun WinClipPanel() {
    val ctx = LocalContext.current
    val items by produceState(emptyList<com.umutk.pulse.extras.keyboard.ClipItem>()) {
        value = withContext(Dispatchers.IO) { runCatching { com.umutk.pulse.extras.keyboard.ClipStore.load(ctx).filter { it.image == null && it.text.isNotBlank() }.take(30) }.getOrDefault(emptyList()) }
    }
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { LauncherSession.clipOpen = false }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(340.dp).heightIn(max = 460.dp).clip(shape).background(Color(0xF2202020)).border(1.dp, Color.White.copy(alpha = .15f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Clipboard history", "Pano geçmişi"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (items.isEmpty()) Text(tr("Nothing copied yet", "Henüz bir şey kopyalanmadı"), color = Color.White.copy(alpha = .6f), fontSize = 13.sp)
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEach { c ->
                    Text(c.text.trim().take(160), color = Color.White, fontSize = 13.sp, maxLines = 3, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .08f))
                        .clickable {
                            runCatching { (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("clip", c.text)) }
                            LauncherSession.clipOpen = false
                        }.padding(10.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

/** Win+/ or Win+F1: the keyboard shortcuts of the launcher, in a small panel. */
@Composable
internal fun WinKeysGuide() {
    val shape = RoundedCornerShape(8.dp)
    val rows = listOf("Win" to tr("Start menu", "Başlat menüsü"), "Win + X" to tr("Quick menu", "Hızlı menü"), "Win + R" to tr("Run", "Çalıştır"), "Win + E" to tr("File Explorer", "Dosya Gezgini"),
        "Win + I" to tr("Settings", "Ayarlar"), "Win + S / Q" to tr("Search", "Ara"), "Win + D" to tr("Show the desktop", "Masaüstünü göster"), "Win + M" to tr("Minimise all", "Tümünü küçült"),
        "Win + L" to tr("Lock", "Kilitle"), "Win + A" to tr("Quick settings", "Hızlı ayarlar"), "Win + V" to tr("Clipboard history", "Pano geçmişi"), "Win + Tab" to tr("Task view", "Görev görünümü"),
        "Win + ← / →" to tr("Snap the window", "Pencereyi yapıştır"), "Win + ↑ / ↓" to tr("Maximise / restore / minimise", "Büyüt / geri al / küçült"), "Win + Shift + S" to tr("Screenshot", "Ekran görüntüsü"),
        "Alt + Tab" to tr("Switch window", "Pencere değiştir"), "Alt + F4" to tr("Close the window", "Pencereyi kapat"), "Ctrl + Esc" to tr("Start menu", "Başlat menüsü"), "Esc" to tr("Close a menu", "Menüyü kapat"))
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { LauncherSession.keysOpen = false }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(360.dp).heightIn(max = 520.dp).clip(shape).background(Color(0xF2202020)).border(1.dp, Color.White.copy(alpha = .15f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }.padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Keyboard shortcuts", "Klavye kısayolları"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            rows.forEach { (k, v) ->
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
                    Text(k, color = Color(0xFF60CDFF), fontSize = 13.sp, modifier = Modifier.width(130.dp))
                    Text(v, color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}

/** A Windows style error box: used when something on the desktop cannot be opened. */
@Composable
internal fun WinErrorDialog(lookId: String, what: String) {
    val classic = lookId == "win95" || lookId == "xp" || lookId == "win7"
    val title = when (lookId) { "win95" -> Color(0xFF000080); "xp" -> Color(0xFF0A246A); "win7" -> Color(0xFF4F7CB5); else -> Color(0xFF202020) }
    val body = if (classic) (if (lookId == "win95") Color(0xFFC0C0C0) else Color(0xFFECE9D8)) else Color(0xFF2B2B2B)
    val ink = if (classic) Color.Black else Color.White
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }, contentAlignment = Alignment.Center) {
        Column(Modifier.width(300.dp).clip(RoundedCornerShape(if (classic) 3.dp else 8.dp)).background(body).border(1.dp, if (classic) Color(0xFF404040) else Color.White.copy(alpha = .2f), RoundedCornerShape(if (classic) 3.dp else 8.dp))) {
            Text(what.ifBlank { tr("Error", "Hata") }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.fillMaxWidth().background(title).padding(horizontal = 10.dp, vertical = 6.dp))
            androidx.compose.foundation.layout.Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(32.dp).height(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFD13438)), contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 18.sp) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(what, color = ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr("This app cannot be opened. It may have been removed or turned off.", "Bu uygulama açılamıyor. Kaldırılmış ya da kapatılmış olabilir."), color = ink.copy(alpha = .8f), fontSize = 13.sp)
                }
            }
            Box(Modifier.align(Alignment.End).padding(end = 14.dp, bottom = 12.dp).clip(RoundedCornerShape(if (classic) 2.dp else 4.dp)).background(if (classic) Color(0xFFE0E0E0) else Color(0xFF0067C0))
                .clickable { LauncherSession.errorText = "" }.padding(horizontal = 28.dp, vertical = 6.dp)) { Text(tr("OK", "Tamam"), color = if (classic) Color.Black else Color.White, fontSize = 13.sp) }
        }
    }
}

/** The window of a tool is see-through on Windows 11 (the desktop shows behind it), as Settings and the Terminal are. */
@Composable
internal fun WinGlass() {
    val deskWin = LocalDesktopWin.current
    val on = com.umutk.pulse.T.id == "win11"
    androidx.compose.runtime.DisposableEffect(deskWin, on) { if (on) deskWin?.glass = true; onDispose { if (on) deskWin?.glass = false } }
}

/** The page colour of a tool window: dark and see-through on Windows 11, the look's own colour elsewhere. */
internal fun winPageBg(): Color = if (com.umutk.pulse.T.id == "win11") Color.Black.copy(alpha = .5f) else com.umutk.pulse.T.bg

/** A small window inside the tool's own window (not a screen wide dialog): a title bar that moves it, a cross, and the content. Used for the edit boxes of the tools. */
@Composable
internal fun WinPopup(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val shape = RoundedCornerShape(8.dp)
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(16.dp).offset { androidx.compose.ui.unit.IntOffset(off.x.toInt(), off.y.toInt()) }.fillMaxWidth().clip(shape).background(Color(0xF0202020)).border(1.dp, Color.White.copy(alpha = .2f), shape)) {
            Row(Modifier.fillMaxWidth().background(Color.White.copy(alpha = .06f)).pointerInput(Unit) { androidx.compose.foundation.gestures.detectDragGestures { c, d -> c.consume(); off += d } }.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.width(40.dp).height(32.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 14.sp) }
            }
            Column(Modifier.padding(14.dp)) { content() }
        }
    }
}

/** The level shown when the volume or brightness keys of a keyboard are pressed: a small rounded bar at the bottom of the screen that goes away by itself. */
@Composable
internal fun WinOsd() {
    var now by remember { mutableLongStateOf(android.os.SystemClock.uptimeMillis()) }
    LaunchedEffect(LauncherSession.osdAt) { if (LauncherSession.osdAt != 0L) { delay(1600); now = android.os.SystemClock.uptimeMillis() } }
    if (LauncherSession.osdAt == 0L || now - LauncherSession.osdAt > 1500L && now >= LauncherSession.osdAt + 1500L) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.foundation.layout.Row(Modifier.padding(bottom = 90.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xE6202020)).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(LauncherSession.osdText, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(80.dp))
            Box(Modifier.width(140.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .25f))) {
                Box(Modifier.fillMaxWidth(LauncherSession.osdLevel.coerceIn(.02f, 1f)).height(6.dp).background(launcherAccent()))
            }
            Text("${(LauncherSession.osdLevel * 100).toInt()}", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

/** The box that renames the phone as the Start menu shows it (empty goes back to the phone's model). */
@Composable
internal fun RenameDeviceDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf(LauncherPrefs.deviceName) }
    com.umutk.pulse.WinAlert(onDismissRequest = onClose, title = { Text(tr("Device name", "Cihaz adı")) },
        text = { com.umutk.pulse.PField(text, { text = it.take(24) }, android.os.Build.MODEL, Modifier.fillMaxWidth()) },
        confirmButton = { com.umutk.pulse.PButton(tr("OK", "Tamam")) { LauncherPrefs.deviceName = text.trim(); LauncherPrefs.save(ctx); onClose() } })
}
