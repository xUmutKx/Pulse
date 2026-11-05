package com.umutk.pulse.extras.video

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSwitch
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

/**
 * The subtitle window of the player in three short pages instead of one long list: SOURCE (off, the video's own tracks, the loaded file, pick a file,
 * search online), LOOK (size, colour, bold / outline / background, height) and TIMING (delay, text encoding). Every change is saved at once.
 */
@Composable
internal fun SubMenu(
    prefs: VideoPrefs, tracks: List<Pair<String, Boolean>>, off: Boolean, extName: String?, delayMs: Long,
    onChoose: (Int) -> Unit, onOff: () -> Unit, onFile: () -> Unit, onSearch: () -> Unit,
    onDelay: (Long) -> Unit, onStyle: () -> Unit, onEnc: () -> Unit, onDismiss: () -> Unit,
) {
    val w = LocalWindowLook.current
    var tab by remember { mutableIntStateOf(0) }
    var rev by remember { mutableIntStateOf(0) }
    rev.let { }
    val bump = { rev++; onStyle() }
    @Composable fun chip(label: String, on: Boolean, click: () -> Unit) {
        Text(
            label, color = if (on) Color.White else w.text, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) Look.accent else w.sub.copy(alpha = .18f)).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
    @Composable fun label(t: String) = Text(t, color = w.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
    @Composable fun stepper(l: String, value: String, minus: () -> Unit, plus: () -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(l, Modifier.weight(1f), color = w.text, fontSize = 14.sp)
            PButton("-", compact = true, onClick = minus)
            Text(value, Modifier.width(76.dp), color = w.text, fontSize = 14.sp, textAlign = TextAlign.Center)
            PButton("+", compact = true, onClick = plus)
        }
    }
    @Composable fun source(n: String, on: Boolean, click: () -> Unit) {
        Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            PRadio(on, click); Spacer(Modifier.width(8.dp)); Text(n, color = w.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Subtitles", "Altyazı"), color = w.text) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(tr("Source", "Kaynak"), tr("Look", "Görünüm"), tr("Timing", "Zamanlama")).forEachIndexed { i, n -> chip(n, tab == i) { tab = i } }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp)) {
                    when (tab) {
                        0 -> {
                            source(tr("Off", "Kapalı"), off) { onOff() }
                            if (extName != null) source(extName, true) { }
                            tracks.forEachIndexed { i, (n, on) -> source(n, on && extName == null) { onChoose(i) } }
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PButton(tr("File", "Dosya"), Modifier.weight(1f), icon = Icons.Outlined.FolderOpen, onClick = onFile)
                                PButton(tr("Search", "Ara"), Modifier.weight(1f), icon = Icons.Outlined.Search, onClick = onSearch)
                            }
                        }
                        1 -> {
                            label(tr("Size", "Boyut"))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(28, 34, 38, 44, 52).forEach { s -> chip("$s", prefs.subSize == s) { prefs.subSize = s; bump() } } }
                            label(tr("Size in the small window", "Küçük pencerede boyut"))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(14, 16, 18, 20, 24).forEach { s -> chip("$s", prefs.subSizePip == s) { prefs.subSizePip = s; bump() } } }
                            label(tr("Colour", "Renk"))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                subColors.forEachIndexed { i, c ->
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(Color(c)).border(if (prefs.subColor == i) 3.dp else 1.dp, if (prefs.subColor == i) Look.accent else w.sub, CircleShape)
                                        .clickable { prefs.subColor = i; bump() })
                                }
                            }
                            label(tr("Style", "Biçim"))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                chip(tr("Bold", "Kalın"), prefs.subBold) { prefs.subBold = !prefs.subBold; bump() }
                                chip(tr("Outline", "Kenar"), prefs.subEdge) { prefs.subEdge = !prefs.subEdge; bump() }
                                chip(tr("Background", "Arka plan"), prefs.subBg) { prefs.subBg = !prefs.subBg; bump() }
                            }
                            stepper(tr("Height", "Yükseklik"), "${prefs.subPos}%", { prefs.subPos = (prefs.subPos - 2).coerceAtLeast(0); bump() }, { prefs.subPos = (prefs.subPos + 2).coerceAtMost(40); bump() })
                        }
                        else -> {
                            stepper(tr("Delay", "Gecikme"), "%+.1f s".format(delayMs / 1000f), { onDelay(delayMs - 100); rev++ }, { onDelay(delayMs + 100); rev++ })
                            stepper(tr("Delay (big)", "Gecikme (büyük)"), "%+d s".format(delayMs / 1000), { onDelay(delayMs - 1000); rev++ }, { onDelay(delayMs + 1000); rev++ })
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) { PButton(tr("Reset delay", "Gecikmeyi sıfırla"), compact = true) { onDelay(0L); rev++ } }
                            Row(Modifier.fillMaxWidth().clickable(onClick = onEnc).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tr("Encoding", "Kodlama"), Modifier.weight(1f), color = w.text, fontSize = 14.sp); Text(prefs.subEnc, color = w.sub, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}
