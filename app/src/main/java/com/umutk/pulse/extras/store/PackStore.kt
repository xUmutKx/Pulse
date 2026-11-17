package com.umutk.pulse.extras.store

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PDialog
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** The name of a pack's kind, for its list. */
fun packKind(k: String): String = when (k) {
    "ses", "kus" -> tr("Sounds", "Sesler"); "skin" -> tr("Player skins", "Çalar kaplamaları")
    "gorsel" -> tr("Pictures", "Resimler"); "video" -> tr("Video", "Video"); "playlist" -> tr("Playlists", "Listeler")
    "lyrics" -> tr("Lyrics", "Şarkı sözleri"); "tools" -> tr("Tools", "Araçlar"); else -> tr("Packs", "Paketler")
}

/** The icon of a pack's kind (the line icons every look has). */
fun packIcon(k: String): String = when (k) {
    "ses", "kus" -> "volume"; "skin" -> "palette"; "gorsel" -> "image"; "video" -> "play"
    "playlist" -> "music"; "lyrics" -> "details"; "tools" -> "settings"; else -> "download"
}

/** The packs of the store, read again when the page opens (the saved list shows first; offline it stays as it was). */
@Composable
fun rememberPacks(): List<ExtraModule> {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf(StoreCatalog.load(ctx)) }
    LaunchedEffect(Unit) { StoreCatalog.refresh(ctx); list = StoreCatalog.load(ctx) }
    return list
}

/** What a pack is doing now: its state, and how far a download has come (0..1). Changes redraw the caller. */
@Composable
fun packState(m: ExtraModule): Pair<ModuleState, Float?> {
    val ctx = LocalContext.current
    val progress by StoreInstaller.progress.collectAsState()
    val rev by StoreInstaller.rev.collectAsState()
    val st = remember(m, progress, rev) { StoreInstaller.state(ctx, m) }
    return st to progress[m.id]
}

/** One button for a pack, by its state: Get, the percent (a tap cancels), Update, Remove (asks first). */
@Composable
fun PackButton(m: ExtraModule, state: ModuleState, pct: Float?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var ask by remember { mutableStateOf(false) }
    when (state) {
        ModuleState.DOWNLOADING -> PButton("${((pct ?: 0f) * 100).toInt()}%", modifier, compact = true) { StoreInstaller.cancel(m.id) }
        ModuleState.INSTALLED -> PButton(tr("Remove", "Kaldır"), modifier, danger = true, compact = true) { ask = true }
        ModuleState.UPDATE -> PButton(tr("Update", "Güncelle"), modifier, compact = true) { StoreInstaller.download(ctx, m) }
        ModuleState.FAILED -> PButton(tr("Try again", "Tekrar dene"), modifier, compact = true) { StoreInstaller.download(ctx, m) }
        else -> PButton(tr("Get", "Al"), modifier, compact = true) { StoreInstaller.download(ctx, m) }
    }
    if (ask) PDialog(
        title = m.name(),
        text = tr("Files of this pack are deleted (${formatMb(StoreInstaller.installedBytes(ctx, m))}). It can be downloaded again.", "Bu paketin dosyaları silinir (${formatMb(StoreInstaller.installedBytes(ctx, m))}). Yeniden indirilebilir."),
        confirm = tr("Remove", "Kaldır"),
        onConfirm = { StoreInstaller.remove(ctx, m); ask = false },
        onDismiss = { ask = false },
    )
}

/** One pack as a line: its kind's icon, name, size and licence, and its button. */
@Composable
fun PackLine(m: ExtraModule) {
    val (state, pct) = packState(m)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PIcon(packIcon(m.kategori), T.text, 24.dp)
        Column(Modifier.weight(1f)) {
            Text(m.name(), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(packKind(m.kategori) + "  ·  " + formatMb(m.boyut) + "  ·  " + m.lisans, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PackButton(m, state, pct)
    }
}

/** All the packs of the store, the ones on the phone first. [filter] keeps the ones whose name has it. */
@Composable
fun PackList(filter: String = "") {
    val packs = rememberPacks().filter { !it.isApk && (filter.isBlank() || it.name().contains(filter, ignoreCase = true)) }
    val ctx = LocalContext.current
    val rev by StoreInstaller.rev.collectAsState()
    val here = remember(packs, rev) { packs.filter { StoreInstaller.state(ctx, it) in setOf(ModuleState.INSTALLED, ModuleState.UPDATE) } }
    Column(Modifier.fillMaxWidth()) {
        if (packs.isEmpty()) Text(tr("No packs to show (offline?)", "Gösterilecek paket yok (çevrimdışı mı?)"), color = T.sub, fontSize = 13.sp)
        if (here.isNotEmpty()) {
            Text(tr("On this phone", "Bu telefonda"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            here.forEach { PackLine(it) }
            Spacer(Modifier.padding(top = 6.dp))
            Text(tr("More to get", "Alınabilecekler"), color = T.sub, fontSize = 12.sp)
        }
        packs.filter { it !in here }.forEach { PackLine(it) }
    }
}

/** Settings > About: the packs that can be downloaded and the ones on the phone, each with Get / Remove. Closed at first, like Storage. */
@Composable
fun PacksCard() {
    var open by remember { mutableStateOf(false) }
    PCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Downloaded packs", "İndirilen paketler"), color = com.umutk.pulse.Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
        }
    }
    if (open) PCard { PackList() }
}
