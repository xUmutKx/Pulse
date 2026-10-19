package com.umutk.pulse.extras.store

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** One full-width settings row: icon, title, value; [content] sits below it (radio options). */
@Composable
private fun SRow(icon: String, title: String, value: String, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, below: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(icon, T.text, 24.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (value.isNotEmpty()) Text(value, color = T.sub, fontSize = 12.sp)
            }
            trailing?.invoke()
        }
        below?.invoke()
    }
}

@Composable
private fun Opt(label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 38.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PRadio(on, onClick)
        Spacer(Modifier.width(10.dp))
        Text(label, color = T.text, fontSize = 14.sp)
    }
}

/** Extra apps settings: where files come from, automatic updates, where data lives, cache, and a switch set per extra. */
@Composable
fun StoreSettingsScreen(modules: List<ExtraModule>, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }
    val src = remember(tick) { StoreInstaller.source(ctx) }
    val loc = remember(tick) { StoreInstaller.location(ctx) }
    val auto = remember(tick) { StoreInstaller.autoAll(ctx) }
    val cache = remember(tick) { StoreInstaller.cacheBytes(ctx) }
    Column(Modifier.fillMaxSize()) {
        StoreHeader(tr("Settings", "Ayarlar"), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
            SRow("download", tr("Download source", "İndirme kaynağı"), if (src == 0) "GitHub" else "jsDelivr", { open = if (open == "src") "" else "src" }) {
                if (open == "src") {
                    Opt("GitHub", src == 0) { StoreInstaller.setSource(ctx, 0); tick++ }
                    Opt("jsDelivr", src == 1) { StoreInstaller.setSource(ctx, 1); tick++ }
                }
            }
            SRow("settings", tr("Auto-update", "Otomatik güncelle"), "", { StoreInstaller.setAutoAll(ctx, !auto); tick++ }, { PSwitch(auto, { StoreInstaller.setAutoAll(ctx, it); tick++ }) })
            SRow("folder", tr("Install location", "Kurulum yeri"), if (loc == 0) tr("Shared storage", "Paylaşılan depolama") else tr("Private storage", "Özel depolama"), { open = if (open == "loc") "" else "loc" }) {
                if (open == "loc") {
                    Opt(tr("Shared storage", "Paylaşılan depolama"), loc == 0) { StoreInstaller.setLocation(ctx, 0); tick++ }
                    Opt(tr("Private storage", "Özel depolama"), loc == 1) { StoreInstaller.setLocation(ctx, 1); tick++ }
                }
            }
            SRow("delete", tr("Clear cache", "Önbelleği temizle"), formatMb(cache), {
                val n = StoreInstaller.clearCache(ctx)
                Toast.makeText(ctx, formatMb(n), Toast.LENGTH_SHORT).show(); tick++
            })
            Spacer(Modifier.padding(top = 8.dp))
            Text(tr("Extra apps", "Ek uygulamalar"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 4.dp))
            modules.forEach { m ->
                SRow(if (m.isApk) "download" else "details", m.name(), m.surum, { open = if (open == m.id) "" else m.id }) {
                    if (open == m.id) Column(Modifier.padding(start = 38.dp)) {
                        ModuleOptions(m) { tick++ }
                    }
                }
            }
        }
    }
}

/** The switches and actions of one extra; also shown on its detail page. */
@Composable
fun ModuleOptions(m: ExtraModule, changed: () -> Unit = {}) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val auto = remember(tick) { StoreInstaller.autoOne(ctx, m.id) }
    val keep = remember(tick) { StoreInstaller.keepFile(ctx, m.id) }
    SRow("settings", tr("Auto-update", "Otomatik güncelle"), "", { StoreInstaller.setAutoOne(ctx, m.id, !auto); tick++; changed() }, { PSwitch(auto, { StoreInstaller.setAutoOne(ctx, m.id, it); tick++; changed() }) })
    SRow("folder", tr("Keep downloaded file", "İndirilen dosyayı sakla"), "", { StoreInstaller.setKeepFile(ctx, m.id, !keep); tick++; changed() }, { PSwitch(keep, { StoreInstaller.setKeepFile(ctx, m.id, it); tick++; changed() }) })
    SRow("download", if (StoreInstaller.isBundled(ctx, m)) tr("Built-in", "Dahili") else tr("Download", "İndirme"), formatMb(m.boyut))
    SRow("details", tr("License", "Lisans"), m.lisans, m.lisansMetni?.let { u -> { StoreInstaller.open(ctx, u) } })
    m.kaynakUrl?.let { u -> SRow("folder", tr("Source", "Kaynak"), u.removePrefix("https://"), { StoreInstaller.open(ctx, u) }) }
    if (!m.isApk && StoreInstaller.installedVersion(ctx, m) != null)
        SRow("delete", tr("Remove data", "Veriyi sil"), formatMb(StoreInstaller.installedBytes(ctx, m)), { StoreInstaller.clearData(ctx, m); tick++; changed() })
}
