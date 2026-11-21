package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The Windows 11 flyouts that have a button of their own on the taskbar: the quick settings (the connection / sound / battery button: tiles, brightness, volume, battery)
 * and the search (the search button: a search box, the kinds to search, the results). The Start button opens the Start menu and the clock the notifications and calendar,
 * so every button opens its own panel.
 */
@Composable
fun Win11Flyouts(taskbar: Dp, state: LauncherState?) {
    if (LauncherSession.quickOpen) QuickFlyout(taskbar)
    if (LauncherSession.w11SearchOpen) SearchFlyout(taskbar, state)
    if (LauncherSession.runOpen) RunBox(taskbar)
    if (LauncherSession.winXOpen) WinXMenu(taskbar)
}

private val FLY_BG = Color(0x7A1B2232)
private val FLY_EDGE = Color(0x40FFFFFF)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QuickFlyout(taskbar: Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val none = remember { MutableInteractionSource() }
    val hi = launcherAccent()
    val shape = RoundedCornerShape(8.dp)
    var rev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // the tiles on show (kept in a list the user edits with the pencil): nine to a page
    val qp = remember { ctx.getSharedPreferences("pulse_quick_tiles", Context.MODE_PRIVATE) }
    val defaults = remember { listOf("Wi-Fi", "Bluetooth", "Airplane mode", "Battery saver", "Rotation lock", "Flashlight", "Location", "Cellular", "Screen recorder", "Motion cues", "Floating timer", "Auto clicker", "Screenshot", "Private DNS", "Dark mode", "NFC", "Sync", "Quiet hours") }
    var shown by remember { mutableStateOf(qp.getString("shown", null)?.split("|")?.filter { n -> QUICK.any { it.en == n } } ?: defaults) }
    var editing by remember { mutableStateOf(false) }
    val names = if (editing) QUICK.map { it.en }.filter { it != "All settings" } else shown
    val tiles = remember(names) { names.mapNotNull { n -> QUICK.firstOrNull { it.en == n } } }
    val ons = remember(rev, tiles) { tiles.map { runCatching { it.state?.invoke(ctx) == true }.getOrDefault(false) } }
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    var bright by remember { mutableFloatStateOf(runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(.5f)) }
    val bat = remember { ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val pct = remember { bat?.let { (it.getIntExtra("level", 0) * 100 / it.getIntExtra("scale", 100).coerceAtLeast(1)) } ?: 0 }
    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { LauncherSession.quickOpen = false }) {
        val place = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 6.dp, bottom = taskbar + 6.dp).statusBarsPadding().widthIn(max = 360.dp).fillMaxWidth(.94f).wrapContentHeight(align = Alignment.Bottom)
        AcrylicBox("win11", FLY_BG, place.border(1.dp, FLY_EDGE, shape).clickable(interactionSource = none, indication = null) {}, shape, 30.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // the toggles: nine to a page (three by three), pages one under the other with dots at the right; the pencil edits which tiles show
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val rowH = (maxWidth - 14.dp - 16.dp) / 3 / 1.5f
                    val pages = tiles.chunked(9)
                    val pager = androidx.compose.foundation.pager.rememberPagerState { pages.size.coerceAtLeast(1) }
                    androidx.compose.foundation.pager.VerticalPager(pager, Modifier.fillMaxWidth().padding(end = 14.dp).height(rowH * 3 + 16.dp), pageSpacing = 8.dp) { pg ->
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            pages.getOrNull(pg).orEmpty().chunked(3).forEachIndexed { r, row ->
                                Row(Modifier.fillMaxWidth().height(rowH), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEachIndexed { c, t ->
                                        val on = ons.getOrNull(pg * 9 + r * 3 + c) == true
                                        val kept = t.en in shown
                            Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(if (editing) (if (kept) hi else Color(0x22FFFFFF)) else if (on) hi else Color(0x33FFFFFF))
                                // a tap works the tile; held, the tile's own page of Pulse's Settings opens in the launcher (the phone's page only for a tile Pulse has no page for)
                                .combinedClickable(onClick = { if (editing) { shown = if (kept) shown - t.en else shown + t.en; qp.edit().putString("shown", shown.joinToString("|")).apply() } else t.run(ctx); scope.launch { delay(500); rev++; delay(1200); rev++ } }, onLongClick = {
                                    val page = when (t.en) { "Wi-Fi" -> "Wi-Fi"; "Bluetooth" -> "Bluetooth"; "Airplane mode" -> "Airplane mode"; "Cellular" -> "Mobile network"; "Mobile hotspot" -> "Mobile hotspot"; "VPN" -> "VPN"; "Battery saver" -> "Power & battery"; "Quiet hours" -> "Focus"; "Rotation lock", "Dark mode" -> "Display"; else -> null }
                                    val phoneAct = when (t.en) { "NFC" -> Settings.ACTION_NFC_SETTINGS; "Sync" -> Settings.ACTION_SYNC_SETTINGS; "Private DNS" -> Settings.ACTION_WIRELESS_SETTINGS; "Location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS; "Flashlight" -> Settings.ACTION_DISPLAY_SETTINGS; else -> null }
                                    if (page != null) { SettingsJump.item = page; LauncherSession.quickOpen = false; openDesktopItem(ctx, "pulse:winsettings", "win11") }
                                    else if (phoneAct != null) { LauncherSession.quickOpen = false; runCatching { ctx.startActivity(Intent(phoneAct).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                                    else t.run(ctx)
                                }).padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(tr(t.en, t.trName), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (pages.size > 1) Column(Modifier.align(Alignment.CenterEnd), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(pages.size) { i -> Box(Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (pager.currentPage == i) Color.White else Color(0x66FFFFFF))) }
                    }
                }
                // the sliders: brightness (written to the system when it may be) and volume
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("light", Color.White, 18.dp)
                    Slider(bright, { bright = it; runCatching { if (Settings.System.canWrite(ctx)) Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (it * 255).toInt().coerceIn(1, 255)) } },
                        modifier = Modifier.weight(1f).padding(start = 8.dp), colors = SliderDefaults.colors(thumbColor = hi, activeTrackColor = hi, inactiveTrackColor = Color(0x44FFFFFF)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("volume", Color.White, 18.dp)
                    Slider(vol, { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0) },
                        modifier = Modifier.weight(1f).padding(start = 8.dp), colors = SliderDefaults.colors(thumbColor = hi, activeTrackColor = hi, inactiveTrackColor = Color(0x44FFFFFF)))
                }
                // the foot: the battery and the way to the settings
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("battery", Color.White, 16.dp)
                    Text("$pct%", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(if (editing) hi else Color.Transparent).clickable { editing = !editing }, contentAlignment = Alignment.Center) { Glyph(if (editing) "check" else "edit", Color.White, 18.dp) }
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).clickable { LauncherSession.quickOpen = false; runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }, contentAlignment = Alignment.Center) {
                        Glyph("settings", Color.White, 18.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchFlyout(taskbar: Dp, state: LauncherState?) {
    val ctx = LocalContext.current
    val none = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)
    var q by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val kinds = listOf(tr("All", "Tümü"), tr("Apps", "Uygulamalar"), tr("Settings", "Ayarlar"), tr("Web", "Web"))
    val apps = remember(q, state) { if (q.isBlank()) state?.apps.orEmpty().take(8) else state?.apps.orEmpty().filter { it.label.contains(q.trim(), true) }.take(8) }
    // the settings are Pulse's own (Windows 11 Settings opens in a window on the matching page), not the phone's pages
    val setList = remember { WIN11_SETTINGS.flatMap { it.items } }
    val sets = remember(q) { if (q.isBlank()) setList.take(5) else setList.filter { it.en.contains(q.trim(), true) || it.tr.contains(q.trim(), true) }.take(8) }
    // YTXTube is our YouTube: found by "you", "tube", "ytx" and opens its own screen
    val ytx = remember(q) { q.isNotBlank() && listOf("youtube", "ytxtube", "ytx", "tube", "yt music").any { it.contains(q.trim(), true) } && q.trim().length >= 2 }
    val imeUp = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { LauncherSession.w11SearchOpen = false }) {
        val place = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().statusBarsPadding().imePadding().padding(top = 28.dp, bottom = if (imeUp) 8.dp else taskbar + 8.dp).widthIn(max = 560.dp).fillMaxWidth(.94f).wrapContentHeight(align = Alignment.Bottom)
        AcrylicBox("win11", FLY_BG, place.border(1.dp, FLY_EDGE, shape).clickable(interactionSource = none, indication = null) {}, shape, 30.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // the search pill with the accent line under it
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x26FFFFFF))) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph("search", Color.White, 18.dp)
                        BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(Color.White),
                            modifier = Modifier.weight(1f).padding(start = 10.dp).focusRequester(focus),
                            decorationBox = { inner -> if (q.isEmpty()) Text(tr("Search apps, settings and the web", "Uygulama, ayar ve web ara"), color = Color(0xAAFFFFFF), fontSize = 15.sp); inner() })
                    }
                    Box(Modifier.fillMaxWidth().height(2.dp).background(launcherAccent()))
                }
                // the kinds to search
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    kinds.forEachIndexed { i, n ->
                        Text(n, color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (kind == i) launcherAccent() else Color(0x26FFFFFF)).clickable { kind = i }.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                if (kind == 0 || kind == 1) {
                    Text(tr("Apps", "Uygulamalar"), color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    apps.forEach { a ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { LauncherSession.w11SearchOpen = false; state?.open(a) }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            LaunchAppPicture(a, 28.dp, Color(0x33FFFFFF), Color.White)
                            Text(a.label, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                if (ytx && (kind == 0 || kind == 1)) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable {
                    LauncherSession.w11SearchOpen = false
                    runCatching { ctx.startActivity(Intent().setClassName(ctx.packageName, "com.umutk.pulse.extras.ytx.YtxActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) { Glyph("video", Color.White, 18.dp) }
                    Text("YTXTube", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                }
                if (kind == 0 || kind == 2) {
                    Text(tr("Settings", "Ayarlar"), color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    sets.forEach { it ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { LauncherSession.w11SearchOpen = false; SettingsJump.item = it.en; openDesktopItem(ctx, "pulse:winsettings", "win11") }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph("settings", Color.White, 20.dp)
                            Text(tr(it.en, it.tr), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }
                if (q.isNotBlank() && (kind == 0 || kind == 3)) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable {
                    LauncherSession.w11SearchOpen = false
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.trim()))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph("search", Color.White, 20.dp)
                    Text(tr("Search the web for \"", "Webde ara: \"") + q.trim() + "\"", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
                }
                if (q.isNotBlank()) ContactResults(q, Color.White, Color(0xAAFFFFFF))
            }
        }
    }
}

/**
 * The phone companion of the Start menu (its second layout): the phone and its battery, Wi-Fi and Bluetooth, Messages, Calls, Send files and a place for the recent phone activity,
 * docked on the right of the menu like Phone Link in Windows 11.
 */
@Composable
internal fun PhoneCompanionPanel(fg: Color, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(8.dp)
    val bat = remember { ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val pct = remember { bat?.let { (it.getIntExtra("level", 0) * 100 / it.getIntExtra("scale", 100).coerceAtLeast(1)) } ?: 0 }
    Column(Modifier.width(190.dp).fillMaxHeight().background(Color(0x1FFFFFFF)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 52.dp, height = 92.dp).clip(RoundedCornerShape(10.dp)).background(launcherAccent().copy(alpha = .85f)))
        Text(android.os.Build.MODEL, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph("battery", fg, 16.dp); Text("$pct%", color = fg, fontSize = 12.sp)
            QUICK.firstOrNull { it.en == "Wi-Fi" }?.let { t -> Icon(t.icon, null, tint = fg, modifier = Modifier.size(18.dp).clickable { t.run(ctx) }) }
            QUICK.firstOrNull { it.en == "Bluetooth" }?.let { t -> Icon(t.icon, null, tint = fg, modifier = Modifier.size(18.dp).clickable { t.run(ctx) }) }
        }
        Spacer(Modifier.height(10.dp))
        listOf(
            // Pulse's own Messages (SMS) opens as a window on the desktop, not in another app
            Triple("chat", tr("Messages", "Mesajlar")) { openDesktopItem(ctx, "pulse:x:messages", "win11") },
            Triple("phone", tr("Calls", "Aramalar")) { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
            Triple("share", tr("Send files", "Dosyaları gönder")) { openDesktopItem(ctx, "pulse:x:localsend", "win11") },
        ).forEach { (ic, label, act) ->
            Row(Modifier.fillMaxWidth().clip(shape).clickable { onClose(); act() }.padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(ic, fg, 18.dp)
                Text(label, color = fg, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text(tr("Recent", "En son"), color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("Recent mobile activity shows here.", "Son mobil etkinlikler burada görünür."), color = fg.copy(alpha = .6f), fontSize = 12.sp)
    }
}


/** The Run box: a command typed in it opens the matching Windows tool of Pulse (cmd and terminal, regedit, taskmgr, control, settings, notepad, calc, explorer, paint ...). */
@Composable
private fun RunBox(taskbar: Dp) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    fun close() { LauncherSession.runOpen = false }
    fun go() {
        val c = text.trim().lowercase().removeSuffix(".exe").removeSuffix(".msc")
        val id = when (c) {
            "cmd", "powershell", "pwsh", "terminal", "wt", "command", "conhost" -> "pulse:cmd"
            "taskmgr", "task manager", "perfmon", "resmon" -> "pulse:taskmgr"
            "control", "control panel", "appwiz.cpl", "main.cpl" -> "pulse:settings"
            "settings", "ms-settings:", "ms-settings", "winver", "msinfo32" -> "pulse:winsettings"
            "explorer", "files", ".", "this pc", "shell:mycomputer" -> "pulse:files"
            "notepad", "wordpad" -> "pulse:x:win:notepad"
            "calc", "calculator" -> "pulse:x:calculator"
            "mspaint", "paint" -> "pulse:paint"
            else -> null
        }
        when {
            c == "regedit" || c == "regedt32" -> { close(); DesktopWindows.open("win:regedit", tr("Registry Editor", "Kayıt Defteri Düzenleyicisi")) }
            id != null -> { close(); openDesktopItem(ctx, id, "win11") }
            else -> error = true
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { close() }, contentAlignment = Alignment.BottomStart) {
        Column(Modifier.padding(start = 12.dp, bottom = taskbar + 10.dp).widthIn(max = 380.dp).fillMaxWidth(.94f).clip(shape).background(Color(0xF2202530)).border(1.dp, Color(0x33FFFFFF), shape)
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {}.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Glyph("run", Color.White, 20.dp); Text("  " + tr("Run", "Çalıştır"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            Text(tr("Type the name of a program, folder or tool, and it will open.", "Bir programın, klasörün veya aracın adını yaz, açılsın."), color = Color(0xB3FFFFFF), fontSize = 12.sp)
            androidx.compose.foundation.text.BasicTextField(text, { text = it; error = false }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go, autoCorrect = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go() }),
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(Color(0x26FFFFFF)).padding(horizontal = 10.dp, vertical = 9.dp))
            if (error) Text(tr("Windows cannot find '$text'. Make sure you typed the name correctly.", "'$text' bulunamadı. Adı doğru yazdığından emin ol."), color = Color(0xFFFFB4AB), fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0x33FFFFFF)).clickable { close() }.padding(horizontal = 16.dp, vertical = 7.dp)) { Text(tr("Cancel", "İptal"), color = Color.White, fontSize = 13.sp) }
                Box(Modifier.clip(RoundedCornerShape(4.dp)).background(launcherAccent()).clickable { go() }.padding(horizontal = 20.dp, vertical = 7.dp)) { Text("OK", color = Color.White, fontSize = 13.sp) }
            }
        }
    }
}


/** The Win+X menu (the Start button held): the system tools in a short list, in the look of the launcher (acrylic and rounded in Windows 11, flat and square in Windows 10). */
@Composable
private fun WinXMenu(taskbar: Dp) {
    val ctx = LocalContext.current
    val w11 = LauncherPrefs.look == "win11"
    val shape = RoundedCornerShape(if (w11) 8.dp else 0.dp)
    fun close() { LauncherSession.winXOpen = false }
    fun page(id: String) { close(); openDesktopItem(ctx, id, if (w11) "win11" else "win10") }
    val none = remember { MutableInteractionSource() }
    fun xi(icon: String, name: String, act: () -> Unit) = Triple(icon, name, act)
    val items = listOf(
        xi("apps", tr("Apps and Features", "Uygulamalar ve özellikler")) { page("pulse:apps") },
        xi("battery", tr("Power Options", "Güç seçenekleri")) { close(); SettingsJump.item = "Power & battery"; openDesktopItem(ctx, "pulse:winsettings", if (w11) "win11" else "win10") },
        xi("cpu", tr("System", "Sistem")) { page("pulse:pc") },
        xi("processes", tr("Task Manager", "Görev Yöneticisi")) { page("pulse:taskmgr") },
        xi("settings", tr("Settings", "Ayarlar")) { page("pulse:winsettings") },
        xi("settings", tr("Control Panel", "Denetim Masası")) { page("pulse:settings") },
        xi("run", tr("Terminal", "Terminal")) { page("pulse:cmd") },
        xi("services", tr("Registry Editor", "Kayıt Defteri Düzenleyicisi")) { close(); DesktopWindows.open("win:regedit", tr("Registry Editor", "Kayıt Defteri Düzenleyicisi")) },
        xi("folder", tr("File Explorer", "Dosya Gezgini")) { page("pulse:files") },
        xi("search", tr("Search", "Ara")) { close(); LauncherSession.w11SearchOpen = true },
        xi("run", tr("Run", "Çalıştır")) { close(); LauncherSession.runOpen = true },
    )
    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { close() }) {
        val place = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 8.dp, bottom = taskbar + 6.dp).widthIn(max = 280.dp).fillMaxWidth(.7f)
        val body: @Composable () -> Unit = {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                items.forEach { (icon, name, act) ->
                    Row(Modifier.fillMaxWidth().clickable { act() }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Glyph(icon, Color.White, 18.dp)
                        Text(name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 14.dp))
                    }
                }
            }
        }
        if (w11) AcrylicBox("win11", FLY_BG, place.border(1.dp, FLY_EDGE, shape).clickable(interactionSource = none, indication = null) {}, shape, 30.dp) { body() }
        else Box(place.background(Color(0xFF1F1F1F)).border(1.dp, Color(0xFF3A3A3A)).clickable(interactionSource = none, indication = null) {}) { body() }
    }
}
