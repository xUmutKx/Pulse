package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
}

private val FLY_BG = Color(0x7A1B2232)
private val FLY_EDGE = Color(0x40FFFFFF)

@Composable
private fun QuickFlyout(taskbar: Dp) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val none = remember { MutableInteractionSource() }
    val hi = launcherAccent()
    val shape = RoundedCornerShape(8.dp)
    var rev by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val names = remember { listOf("Wi-Fi", "Bluetooth", "Airplane mode", "Battery saver", "Rotation lock", "Flashlight", "Location", "Cellular") }
    val tiles = remember { names.mapNotNull { n -> QUICK.firstOrNull { it.en == n } } }
    val ons = remember(rev) { tiles.map { runCatching { it.state?.invoke(ctx) == true }.getOrDefault(false) } }
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
                // the toggles: three in a row, lit in the accent colour when on
                tiles.chunked(3).forEachIndexed { r, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, t ->
                            val on = ons.getOrNull(r * 3 + c) == true
                            Column(Modifier.weight(1f).aspectRatio(1.5f).clip(RoundedCornerShape(4.dp)).background(if (on) hi else Color(0x33FFFFFF))
                                .clickable { t.run(ctx); scope.launch { delay(500); rev++; delay(1200); rev++ } }.padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                Text(tr(t.en, t.trName), color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
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
    val setList = remember { listOf("Wi-Fi" to Settings.ACTION_WIFI_SETTINGS, "Bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "Display" to Settings.ACTION_DISPLAY_SETTINGS, "Sound" to Settings.ACTION_SOUND_SETTINGS,
        "Battery" to Intent.ACTION_POWER_USAGE_SUMMARY, "Storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "Apps" to Settings.ACTION_APPLICATION_SETTINGS, "Location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "Date & time" to Settings.ACTION_DATE_SETTINGS) }
    val sets = remember(q) { if (q.isBlank()) setList.take(5) else setList.filter { it.first.contains(q.trim(), true) } }
    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { LauncherSession.w11SearchOpen = false }) {
        val place = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().statusBarsPadding().padding(top = 28.dp, bottom = taskbar + 8.dp).widthIn(max = 560.dp).fillMaxWidth(.94f).wrapContentHeight(align = Alignment.Bottom)
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
                if (kind == 0 || kind == 2) {
                    Text(tr("Settings", "Ayarlar"), color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                    sets.forEach { (n, act) ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable { LauncherSession.w11SearchOpen = false; runCatching { ctx.startActivity(Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph("settings", Color.White, 20.dp)
                            Text(n, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
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
            Triple("chat", tr("Messages", "Mesajlar")) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("sms:")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
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
