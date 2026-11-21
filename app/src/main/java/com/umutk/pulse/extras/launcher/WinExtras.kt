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
