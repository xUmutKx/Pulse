package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** A setting to open at once when the Settings window comes up (the quick settings tiles, held): the English name of its row. */
object SettingsJump { var item by mutableStateOf<String?>(null) }

/**
 * Settings in the look of Windows 11: a sidebar of the groups on a wide screen (one page at a time on a phone), a search box, and each
 * group's settings as rounded rows with an icon, a title, a line of description and a chevron.
 */
@Composable
internal fun Win11SettingsHome(open: (String) -> Unit, startGroup: String = "") {
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(WIN11_SETTINGS.indexOfFirst { it.id == startGroup }.coerceAtLeast(0)) }
    var inGroup by remember { mutableStateOf(startGroup.isNotEmpty()) }
    // a setting that is a page of the phone's own settings gets a page of its own here first (what it says now, the controls Pulse can work)
    var detail by remember { mutableStateOf<WItem?>(null) }
    val jump = SettingsJump.item
    androidx.compose.runtime.LaunchedEffect(jump) { if (jump != null) { WIN11_SETTINGS.flatMap { it.items }.firstOrNull { it.en == jump }?.let { detail = it }; SettingsJump.item = null } }
    val groups = WIN11_SETTINGS
    val accent = T.accent
    // Mica: the window's body is see-through for Settings and the page is tinted, so the desktop shows through it
    val deskWin = LocalDesktopWin.current
    androidx.compose.runtime.DisposableEffect(deskWin) { deskWin?.glass = true; onDispose { deskWin?.glass = false } }
    BoxWithConstraints(Modifier.fillMaxSize().background(T.bg.copy(alpha = if (deskWin != null) .36f else 1f))) {
        val wide = maxWidth >= 600.dp
        val pageOf: @Composable (WGroup) -> Unit = { g -> Win11Page(g, open) { detail = it } }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(260.dp).fillMaxHeight().padding(start = 12.dp, top = 14.dp, end = 8.dp, bottom = 10.dp)) {
                    Win11Profile()
                    Spacer(Modifier.height(12.dp))
                    Win11Search(q) { q = it }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        groups.forEachIndexed { i, g ->
                            Win11NavRow(g, i == sel && q.isBlank(), accent) { sel = i; q = ""; detail = null }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, top = 18.dp, end = 18.dp)) {
                    if (detail != null) Win11Detail(detail!!) { detail = null } else if (q.isNotBlank()) Win11Results(q, open) { detail = it } else pageOf(groups[sel])
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (detail != null) Win11Detail(detail!!) { detail = null }
                else if (inGroup && q.isBlank()) {
                    Row(Modifier.fillMaxWidth().clickable { inGroup = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("←", color = T.text, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Settings", "Ayarlar"), color = T.text, fontSize = 16.sp)
                    }
                    Column(Modifier.fillMaxSize()) { pageOf(groups[sel]) }
                } else {
                    Win11Profile()
                    Spacer(Modifier.height(12.dp))
                    Win11Search(q) { q = it }
                    Spacer(Modifier.height(10.dp))
                    if (q.isNotBlank()) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Win11Results(q, open) { detail = it } }
                    else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        groups.forEachIndexed { i, g -> Win11NavRow(g, false, accent) { sel = i; inGroup = true } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Win11Profile() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(T.accent.copy(alpha = .25f)), contentAlignment = Alignment.Center) {
            Text("P", color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Pulse", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(tr("This phone", "Bu telefon"), color = T.sub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Win11Search(q: String, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel.copy(alpha = .5f)).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        // the field draws its own magnifier (a second one beside it was a double icon)
        com.umutk.pulse.PField(q, onChange, tr("Find a setting", "Ayar bul"), Modifier.fillMaxWidth())
    }
}

@Composable
private fun Win11NavRow(g: WGroup, selected: Boolean, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) T.panel.copy(alpha = .5f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        if (selected) Spacer(Modifier.width(9.dp))
        PIcon(g.icon, if (selected) accent else T.sub, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(tr(g.en, g.tr), color = T.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun Win11Page(g: WGroup, open: (String) -> Unit, onDetail: (WItem) -> Unit) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(tr(g.en, g.tr), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
        if (g.id == "personalization") Win11Colors()
        if (g.id == "system") Win11DeviceCard()
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel.copy(alpha = .5f)).border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = .08f), RoundedCornerShape(8.dp))) {
            g.items.forEachIndexed { i, it ->
                Win11Row(it, open, onDetail)
                if (i < g.items.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(T.sub.copy(alpha = .12f)))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** The device card on top of System: what the phone is, as Windows shows the PC (name, model, Android version). */
@Composable
private fun Win11DeviceCard() {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)).background(T.panel.copy(alpha = .5f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(6.dp)).background(T.accent.copy(alpha = .2f)), contentAlignment = Alignment.Center) { PIcon("phone", T.accent, 32.dp) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(android.os.Build.MODEL, color = T.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " · Android " + android.os.Build.VERSION.RELEASE, color = T.sub, fontSize = 12.sp)
        }
    }
}

private val SWATCHES = listOf(0xFFFFB900, 0xFFFF8C00, 0xFFF7630C, 0xFFCA5010, 0xFFDA3B01, 0xFFEF6950, 0xFFD13438, 0xFFFF4343, 0xFFE74856, 0xFFE81123, 0xFFEA005E, 0xFFC30052,
    0xFFE3008C, 0xFFBF0077, 0xFFC239B3, 0xFF9A0089, 0xFF0078D4, 0xFF0063B1, 0xFF8E8CD8, 0xFF6B69D6, 0xFF8764B8, 0xFF744DA9, 0xFFB146C2, 0xFF881798,
    0xFF0099BC, 0xFF2D7D9A, 0xFF00B7C3, 0xFF038387, 0xFF00B294, 0xFF018574, 0xFF00CC6A, 0xFF10893E, 0xFF52D017, 0xFF4CC417, 0xFF498205, 0xFF107C10)

/** Colors, as in Windows 11: the preview of the look, the transparency effects switch and the accent colors (the one picked is ringed). It is the accent of the whole desktop. */
@Composable
private fun Win11Colors() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(8.dp)).background(T.panel.copy(alpha = .5f)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // the preview: a small window in the accent colour on the desktop picture
        Row(Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF101820), androidx.compose.ui.graphics.Color(0xFF1B2735)))).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(140.dp).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(androidx.compose.ui.graphics.Color(0xFF2B2B2B)).padding(8.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { Box(Modifier.fillMaxWidth(if (it == 2) .5f else 1f).height(5.dp).background(androidx.compose.ui.graphics.Color(0x55FFFFFF))) }
                    Box(Modifier.width(36.dp).height(8.dp).clip(RoundedCornerShape(2.dp)).background(launcherAccent()))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Transparency effects", "Saydamlık efektleri"), color = T.text, fontSize = 14.sp)
                Text(tr("Windows and surfaces appear translucent", "Pencereler ve yüzeyler yarı saydam görünür"), color = T.sub, fontSize = 12.sp)
            }
            com.umutk.pulse.PSwitch(LauncherPrefs.blurAll, { LauncherPrefs.blurAll = it; LauncherPrefs.save(ctx) })
        }
        Text(tr("Accent color", "Vurgu rengi"), color = T.text, fontSize = 14.sp)
        SWATCHES.chunked(12).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    val on = LauncherPrefs.accentOn && LauncherPrefs.accentArgb == c.toInt()
                    Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Color(c))
                        .border(if (on) 2.dp else 0.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(4.dp))
                        .clickable { LauncherPrefs.accentOn = true; LauncherPrefs.accentArgb = c.toInt(); LauncherPrefs.save(ctx) })
                }
            }
        }
    }
}

@Composable
private fun Win11Results(q: String, open: (String) -> Unit, onDetail: (WItem) -> Unit) {
    val hits = WIN11_SETTINGS.flatMap { it.items }.filter { tr(it.en, it.tr).contains(q, true) || tr(it.enDesc, it.trDesc).contains(q, true) }
    Column(Modifier.fillMaxWidth()) {
        Text(tr("Results", "Sonuçlar"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel.copy(alpha = .5f))) {
            if (hits.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            hits.forEachIndexed { i, it ->
                Win11Row(it, open, onDetail)
                if (i < hits.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(T.sub.copy(alpha = .12f)))
            }
        }
    }
}

@Composable
private fun Win11Row(it: WItem, open: (String) -> Unit, onDetail: (WItem) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Row(Modifier.fillMaxWidth().clickable { if (it.route.startsWith("intent:")) onDetail(it) else openWItem(ctx, it.route, open) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(it.icon, T.accent, 22.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(it.en, it.tr), color = T.text, fontSize = 15.sp, maxLines = 1)
            Text(tr(it.enDesc, it.trDesc), color = T.sub, fontSize = 12.sp, maxLines = 1)
        }
        Text("›", color = T.sub, fontSize = 20.sp)
    }
}
