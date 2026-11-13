package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.weight
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

/**
 * Settings in the look of Windows 11: a sidebar of the groups on a wide screen (one page at a time on a phone), a search box, and each
 * group's settings as rounded rows with an icon, a title, a line of description and a chevron.
 */
@Composable
internal fun Win11SettingsHome(open: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    var sel by remember { mutableStateOf(0) }
    var inGroup by remember { mutableStateOf(false) }
    val groups = CONTROL_PANEL
    val accent = T.accent
    BoxWithConstraints(Modifier.fillMaxSize().background(T.bg)) {
        val wide = maxWidth >= 600.dp
        val pageOf: @Composable (CpGroup) -> Unit = { g -> Win11Page(g, open) }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(260.dp).fillMaxHeight().padding(start = 12.dp, top = 14.dp, end = 8.dp, bottom = 10.dp)) {
                    Win11Profile()
                    Spacer(Modifier.height(12.dp))
                    Win11Search(q) { q = it }
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        groups.forEachIndexed { i, g ->
                            Win11NavRow(g, i == sel && q.isBlank(), accent) { sel = i; q = "" }
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp, top = 18.dp, end = 18.dp)) {
                    if (q.isNotBlank()) Win11Results(q, open) else pageOf(groups[sel])
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (inGroup && q.isBlank()) {
                    Row(Modifier.fillMaxWidth().clickable { inGroup = false }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("←", color = T.text, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        Text(tr("Settings", "Ayarlar"), color = T.text, fontSize = 16.sp)
                    }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { pageOf(groups[sel]) }
                } else {
                    Win11Profile()
                    Spacer(Modifier.height(12.dp))
                    Win11Search(q) { q = it }
                    Spacer(Modifier.height(10.dp))
                    if (q.isNotBlank()) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { Win11Results(q, open) }
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel).padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon("search", T.sub, 16.dp)
        Spacer(Modifier.width(8.dp))
        com.umutk.pulse.PField(q, onChange, tr("Find a setting", "Ayar bul"), Modifier.fillMaxWidth())
    }
}

@Composable
private fun Win11NavRow(g: CpGroup, selected: Boolean, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) T.panel else androidx.compose.ui.graphics.Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        if (selected) Spacer(Modifier.width(9.dp))
        PIcon(g.items.firstOrNull()?.icon ?: "settings", if (selected) accent else T.sub, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(tr(g.en, g.trName), color = T.text, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

@Composable
private fun Win11Page(g: CpGroup, open: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(tr(g.en, g.trName), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel)) {
            g.items.forEachIndexed { i, it ->
                Win11Row(it, open)
                if (i < g.items.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(T.sub.copy(alpha = .12f)))
            }
        }
    }
}

@Composable
private fun Win11Results(q: String, open: (String) -> Unit) {
    val hits = CONTROL_PANEL.flatMap { it.items }.filter { tr(it.en, it.trName).contains(q, true) || tr(it.enDesc, it.trDesc).contains(q, true) }
    Column(Modifier.fillMaxWidth()) {
        Text(tr("Results", "Sonuçlar"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(T.panel)) {
            if (hits.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            hits.forEachIndexed { i, it ->
                Win11Row(it, open)
                if (i < hits.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(T.sub.copy(alpha = .12f)))
            }
        }
    }
}

@Composable
private fun Win11Row(it: CpItem, open: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { open(it.route) }.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(it.icon, T.accent, 22.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(it.en, it.trName), color = T.text, fontSize = 15.sp, maxLines = 1)
            Text(tr(it.enDesc, it.trDesc), color = T.sub, fontSize = 12.sp, maxLines = 1)
        }
        Text("›", color = T.sub, fontSize = 20.sp)
    }
}
