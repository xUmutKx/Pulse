package com.umutk.pulse.extras.launcher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalOpenTool
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

private fun Context.activity(): Activity? { var c = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

/** A "Microsoft Store"-style window for the launcher: the looks, the stock pictures and the widgets that can be had, all from this phone (nothing is downloaded). */
@Composable
fun LauncherStoreWindow() {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var pickWall by remember { mutableStateOf(false) }
    var pickWidget by remember { mutableStateOf(false) }
    val addWidget = rememberWidgetAdd { }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PChip(tab == 0, tr("Looks", "Görünümler")) { tab = 0 }
            PChip(tab == 1, tr("Pictures", "Resimler")) { tab = 1 }
            PChip(tab == 2, tr("Widgets", "Widget'lar")) { tab = 2 }
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
            when (tab) {
                0 -> LauncherLooks.all.forEach { l ->
                    val on = LauncherPrefs.look == l.id
                    Row(Modifier.fillMaxWidth().clickable { LauncherPrefs.look = l.id; LauncherPrefs.save(ctx) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        LookPicture(l.id, Modifier.width(72.dp).height(44.dp).clip(RoundedCornerShape(6.dp)))
                        Text(tr(l.nameEn, l.nameTr), color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        if (on) PIcon("check", T.accent, 22.dp)
                    }
                }
                1 -> {
                    Text(tr("Pictures of ${LauncherLooks.byId(LauncherPrefs.look).nameEn}", "${LauncherLooks.byId(LauncherPrefs.look).nameTr} resimleri"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    PButton(tr("Browse pictures", "Resimlere göz at")) { pickWall = true }
                }
                else -> {
                    Text(tr("Widgets on this phone", "Bu telefondaki widget'lar"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp))
                    PButton(tr("Browse widgets", "Widget'lara göz at")) { pickWidget = true }
                }
            }
        }
    }
    if (pickWall) com.umutk.pulse.features.WallPickDialog(LauncherPrefs.look, LauncherPrefs.wallIndex(LauncherPrefs.look), onDismiss = { pickWall = false }) { i ->
        LauncherPrefs.setWallIndex(LauncherPrefs.look, i); LauncherPrefs.save(ctx); pickWall = false
    }
    if (pickWidget) WidgetPickDialog(onPick = { pickWidget = false; addWidget(it) }, onDismiss = { pickWidget = false })
}

/** File Explorer with tabs: each tab keeps its own folder, only the open one is shown. */
@Composable
fun ExplorerTabsWindow() {
    var tabs by remember { mutableIntStateOf(1) }
    var cur by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until tabs) {
                Row(
                    Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(if (i == cur) T.panel else T.bg).clickable { cur = i }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PIcon("folder", if (i == cur) T.accent else T.sub, 16.dp)
                    Text(tr("Tab ${i + 1}", "Sekme ${i + 1}"), color = if (i == cur) T.text else T.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                    if (tabs > 1) Box(Modifier.padding(start = 6.dp).size(18.dp).clickable { tabs--; cur = cur.coerceAtMost(tabs - 1) }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 12.dp) }
                }
                Spacer(Modifier.width(2.dp))
            }
            if (tabs < 5) Box(Modifier.size(32.dp).clickable { tabs++; cur = tabs - 1 }, contentAlignment = Alignment.Center) { PIcon("add", T.text, 18.dp) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // every tab stays composed (its folder is kept); the hidden ones take no room and no touches
            for (i in 0 until tabs) Box(if (i == cur) Modifier.fillMaxSize() else Modifier.size(0.dp).alpha(0f)) { com.umutk.pulse.features.FilesPage() }
        }
    }
}

/** The Control Center: the quick tiles of the action center with a brightness and a volume slider on top. */
@Composable
fun ControlCenterWindow() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    var vol by remember { mutableFloatStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max) }
    var bright by remember { mutableFloatStateOf((ctx.activity()?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }) ?: .6f) }
    val sc = SliderDefaults.colors(thumbColor = T.accent, activeTrackColor = T.accent)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon("light", T.text, 22.dp)
            Slider(bright, {
                bright = it
                ctx.activity()?.window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = it.coerceIn(.02f, 1f) } }
            }, Modifier.weight(1f).padding(start = 8.dp), colors = sc)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PIcon("volume", T.text, 22.dp)
            Slider(vol, { vol = it; am.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0) }, Modifier.weight(1f).padding(start = 8.dp), colors = sc)
        }
        QUICK.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { t ->
                    val on = t.en == "Flashlight" && ActionCenter.torch
                    Column(
                        Modifier.weight(1f).aspectRatio(1.3f).clip(RoundedCornerShape(10.dp)).background(if (on) T.accent else T.panel).clickable { t.run(ctx) }.padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Icon(t.icon, null, tint = if (on) Color.White else T.text, modifier = Modifier.size(24.dp))
                        Text(tr(t.en, t.trName), color = if (on) Color.White else T.text, fontSize = 12.sp, maxLines = 1)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Windows 10 Mobile's Settings: a big lowercase title, the groups as a pivot you swipe, and the items as plain lines under it. */
@Composable
fun PhoneSettingsHome() {
    val open = LocalOpenTool.current
    var g by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text(tr("SETTINGS", "AYARLAR"), color = T.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            CONTROL_PANEL.forEachIndexed { i, grp ->
                Text(tr(grp.en, grp.trName).lowercase(), color = if (i == g) T.text else T.sub.copy(alpha = .6f), fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1,
                    modifier = Modifier.clickable { g = i })
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            CONTROL_PANEL[g].items.forEach { it ->
                Column(Modifier.fillMaxWidth().clickable { open(it.route) }.padding(vertical = 10.dp)) {
                    Text(tr(it.en, it.trName), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.Light)
                    Text(tr(it.enDesc, it.trDesc), color = T.sub, fontSize = 13.sp)
                }
            }
        }
    }
}
