package com.umutk.pulse.extras.store

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.LocalOpenTool
import com.umutk.pulse.launchExtra
import com.umutk.pulse.PIcon
import com.umutk.pulse.PProgress
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.flow.update

private val RED = androidx.compose.ui.graphics.Color(0xFFC42B1C)

private enum class StoreTab { STORE, INSTALLED }

/** One row of the store: a downloadable module or a FOSS app the store only links to. */
private sealed interface StoreEntry {
    val key: String
    val kategori: String
    val name: String
    val subtitle: String
}

private class ModuleEntry(val m: ExtraModule) : StoreEntry {
    override val key get() = m.id
    override val kategori get() = if (m.kategori == "kus") "ses" else m.kategori   // bird packs sit with the sounds
    override val name get() = m.name()
    override val subtitle get() = "${m.lisans} · ${formatMb(m.boyut)}"
}

private class ExternalEntry(val a: ExternalApp) : StoreEntry {
    override val key get() = a.pkg
    override val kategori get() = a.kategori
    override val name get() = a.name
    override val subtitle get() = "${a.license} · ${a.sizeText}"
}

private fun categoryName(k: String) = when (k) {
    "ses" -> tr("Sounds", "Ses"); "skin" -> tr("Player skins", "Çalar skinleri")
    "gorsel" -> tr("Visuals", "Görseller"); "video" -> tr("Video", "Video"); "playlist" -> tr("Playlists", "Listeler")
    "lyrics" -> tr("Lyrics", "Şarkı sözleri"); "tools" -> tr("Tools", "Araçlar"); else -> k
}

private fun categoryIcon(k: String) = when (k) {
    "ses" -> "volume"; "skin" -> "palette"; "gorsel" -> "apps"
    "video" -> "play"; "playlist" -> "folder"; "lyrics" -> "details"; "tools" -> "settings"; else -> "download"
}

private fun inList(s: ModuleState) = s == ModuleState.INSTALLED || s == ModuleState.UPDATE

/** Extra Apps store: search, categories, featured, details, installed list and updates. */
@Composable
fun ExtraStoreScreen(onBack: () -> Unit = {}) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val rev by StoreInstaller.rev.collectAsState()
    val progress by StoreInstaller.progress.collectAsState()
    val errors by StoreInstaller.errors.collectAsState()
    var modules by remember { mutableStateOf(StoreCatalog.load(ctx)) }
    var offline by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(StoreTab.STORE) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var askRemove by remember { mutableStateOf<ExtraModule?>(null) }

    LaunchedEffect(Unit) {
        StoreCatalog.refresh(ctx)
        modules = StoreCatalog.load(ctx)
        offline = StoreCatalog.offline
    }
    // back from the system uninstall screen or the settings page: re-read the states
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) StoreInstaller.rev.update { it + 1 } }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    val entries = remember(modules, rev) {
        modules.map { ModuleEntry(it) as StoreEntry } +
            EXTERNAL_APPS.filter { it.showWhenMissing || StoreInstaller.externalReady(ctx, it) }.map { ExternalEntry(it) }
    }
    val states = remember(entries, rev, progress, errors) {
        entries.associate { e ->
            e.key to when (e) {
                is ModuleEntry -> StoreInstaller.state(ctx, e.m)
                is ExternalEntry -> if (StoreInstaller.externalReady(ctx, e.a)) ModuleState.INSTALLED else ModuleState.NOT_INSTALLED
            }
        }
    }
    val installed = entries.filter { inList(states[it.key] ?: ModuleState.NOT_INSTALLED) }
    val installedBytes = remember(installed, rev) {
        installed.filterIsInstance<ModuleEntry>().sumOf { StoreInstaller.installedBytes(ctx, it.m) }
    }

    BackHandler(enabled = openKey != null) { openKey = null }
    val detail = entries.firstOrNull { it.key == openKey }
    if (detail != null) {
        StoreDetail(detail, states[detail.key] ?: ModuleState.NOT_INSTALLED, progress[detail.key], errors[detail.key],
            installedBytes = (detail as? ModuleEntry)?.let { StoreInstaller.installedBytes(ctx, it.m) } ?: 0L,
            onBack = { openKey = null }, onRemove = { askRemove = it })
    } else {
        val style = storeStyle()
        val totals = tr("Total", "Toplam") + " " + formatMb(StoreInstaller.totalBytes(modules)) + "  ·  " + tr("Installed", "Kurulu") + " " + formatMb(installedBytes)
        Column(Modifier.fillMaxSize()) {
            StoreHeader(tr("Extra apps", "Ek uygulamalar"), onBack,
                onSearch = if (style == StoreStyle.PLAY) null else ({ searching = !searching; if (!searching) query = "" }))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (searching || style == StoreStyle.PLAY) {
                PField(query, { query = it }, hint = tr("Search modules", "Modül ara"), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
            }
            if (style != StoreStyle.CLASSIC) Text(totals, color = T.sub, fontSize = 13.sp)
            if (offline) Text(tr("Offline, saved list", "Çevrimdışı, son liste"), color = T.sub, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            val upd = states.values.count { it == ModuleState.UPDATE }
            StoreTabBar(
                listOf(tr("Store", "Mağaza"), tr("Installed", "Kurulu") + " (${installed.size})" + if (upd > 0) " · $upd↑" else ""),
                if (tab == StoreTab.STORE) 0 else 1,
            ) { tab = if (it == 0) StoreTab.STORE else StoreTab.INSTALLED }
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tab == StoreTab.STORE) {
                    val q = query.trim().lowercase()
                    if (q.isNotEmpty()) {
                        val hits = entries.filter { it.name.lowercase().contains(q) || categoryName(it.kategori).lowercase().contains(q) }
                        items(hits, key = { it.key }) { e ->
                            StoreRow(e, states[e.key] ?: ModuleState.NOT_INSTALLED, progress[e.key], errors[e.key],
                                onOpen = { openKey = e.key }, onRemove = { askRemove = it })
                        }
                        if (hits.isEmpty()) item { Text(tr("Nothing found", "Bulunamadı"), color = T.sub, fontSize = 14.sp) }
                    } else {
                        item { BuiltinRows() }
                        val featured = entries.filterIsInstance<ModuleEntry>().sortedByDescending { it.m.guncellendi }.take(3)
                        if (featured.isNotEmpty()) {
                            item { SectionTitle(tr("Featured", "Öne çıkanlar")) }
                            if (style == StoreStyle.CLASSIC) {
                                items(featured, key = { "f-" + it.key }) { e ->
                                    StoreRow(e, states[e.key] ?: ModuleState.NOT_INSTALLED, progress[e.key], errors[e.key],
                                        onOpen = { openKey = e.key }, onRemove = { askRemove = it })
                                }
                            } else item {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(featured, key = { it.key }) { e ->
                                        FeaturedCard(e, states[e.key] ?: ModuleState.NOT_INSTALLED, progress[e.key], errors[e.key],
                                            onOpen = { openKey = e.key }, onRemove = { askRemove = it })
                                    }
                                }
                            }
                        }
                        val cats = entries.map { it.kategori }.distinct()
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                items(cats, key = { it }) { k ->
                                    val n = entries.count { it.kategori == k }
                                    if (style == StoreStyle.CLASSIC) PChip(category == k, categoryName(k) + " ($n)") { category = if (category == k) null else k }
                                    else CategoryTile(categoryName(k), categoryIcon(k), n, selected = category == k) {
                                        category = if (category == k) null else k
                                    }
                                }
                            }
                        }
                        val groups = entries.filter { category == null || it.kategori == category }.groupBy { it.kategori }
                        groups.forEach { (k, list) ->
                            item(key = "h-$k") {
                                val mods = list.filterIsInstance<ModuleEntry>()
                                SectionTitle(categoryName(k) + "  ·  " + list.size + "  ·  " + formatMb(mods.sumOf { it.m.boyut }))
                            }
                            items(list, key = { it.key }) { e ->
                                StoreRow(e, states[e.key] ?: ModuleState.NOT_INSTALLED, progress[e.key], errors[e.key],
                                    onOpen = { openKey = e.key }, onRemove = { askRemove = it })
                            }
                        }
                        if (groups.isEmpty()) item { Text(tr("No modules yet", "Henüz modül yok"), color = T.sub, fontSize = 14.sp) }
                    }
                } else {
                    val updates = installed.filter { states[it.key] == ModuleState.UPDATE }
                    if (updates.isNotEmpty()) {
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f)) { SectionTitle(tr("Updates", "Güncellemeler") + " (${updates.size})") }
                                PButton(tr("Update all", "Hepsini güncelle"), compact = true) {
                                    StoreInstaller.updateAll(ctx, updates.filterIsInstance<ModuleEntry>().map { it.m })
                                }
                            }
                        }
                        items(updates, key = { "u-" + it.key }) { e ->
                            StoreRow(e, ModuleState.UPDATE, progress[e.key], errors[e.key],
                                onOpen = { openKey = e.key }, onRemove = { askRemove = it })
                        }
                    }
                    item {
                        StoreRowShell {
                            InfoRows(listOf(tr("Installed modules", "Kurulu modüller") to formatMb(installedBytes)))
                        }
                    }
                    if (installed.isEmpty()) item { Text(tr("No installed modules", "Kurulu modül yok"), color = T.sub, fontSize = 14.sp) }
                    items(installed, key = { "i-" + it.key }) { e ->
                        StoreRow(e, states[e.key] ?: ModuleState.NOT_INSTALLED, progress[e.key], errors[e.key],
                            onOpen = { openKey = e.key }, onRemove = { askRemove = it })
                    }
                }
            }
            }
            if (style == StoreStyle.CLASSIC) StoreStatusBar(totals)
        }
    }

    askRemove?.let { m ->
        PDialog(
            title = m.name(),
            text = tr("Files of this module are deleted (${formatMb(StoreInstaller.installedBytes(ctx, m))}).",
                "Bu modülün dosyaları silinir (${formatMb(StoreInstaller.installedBytes(ctx, m))})."),
            confirm = tr("Remove", "Kaldır"),
            onConfirm = { StoreInstaller.remove(ctx, m); askRemove = null },
            onDismiss = { askRemove = null },
        )
    }
}

/** The action button for one row, by state. */
@Composable
private fun EntryAction(e: StoreEntry, st: ModuleState, onRemove: (ExtraModule) -> Unit) {
    val ctx = LocalContext.current
    when (e) {
        is ModuleEntry -> when (st) {
            ModuleState.DOWNLOADING -> PButton(tr("Cancel", "İptal"), compact = true) { StoreInstaller.cancel(e.m.id) }
            ModuleState.INSTALLED -> PButton(tr("Remove", "Kaldır"), danger = true, compact = true) { onRemove(e.m) }
            ModuleState.UPDATE -> PButton(tr("Update", "Güncelle"), compact = true) { StoreInstaller.download(ctx, e.m) }
            else -> PButton(tr("Get", "İndir") + " · " + formatMb(e.m.boyut), compact = true) { StoreInstaller.download(ctx, e.m) }
        }
        is ExternalEntry -> {
            if (st == ModuleState.INSTALLED) PButton(tr("Open", "Aç"), compact = true) { StoreInstaller.launch(ctx, e.a.pkg) }
            else e.a.fdroid?.let { url -> PButton(tr("Open in F-Droid", "F-Droid'de aç"), compact = true) { StoreInstaller.open(ctx, url) } }
        }
    }
}

@Composable
private fun StoreRow(e: StoreEntry, st: ModuleState, pct: Float?, error: String?, onOpen: () -> Unit, onRemove: (ExtraModule) -> Unit) {
    StoreRowShell {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                StoreArt(categoryIcon(e.kategori), 28.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(e.subtitle, color = T.sub, fontSize = 12.sp)
                }
                EntryAction(e, st, onRemove)
            }
            if (st == ModuleState.DOWNLOADING) ProgressLine(pct, Modifier.padding(top = 6.dp))
            if (error != null && st == ModuleState.FAILED) Text(error, color = RED, fontSize = 12.sp)
        }
    }
}

/** Progress bar with the percent beside it. */
@Composable
private fun ProgressLine(pct: Float?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PProgress(pct ?: 0f, Modifier.weight(1f))
        Text(((pct ?: 0f) * 100).toInt().toString() + "%", color = T.sub, fontSize = 12.sp)
    }
}

/** A section title in the page style: bold in the classic looks, large in Win10 / 11, medium in the Play style. */
@Composable
private fun SectionTitle(text: String) {
    val big = storeStyle() != StoreStyle.CLASSIC
    Text(text, color = T.text, fontSize = if (big) 17.sp else 14.sp, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun FeaturedCard(e: StoreEntry, st: ModuleState, pct: Float?, error: String?, onOpen: () -> Unit, onRemove: (ExtraModule) -> Unit) {
    StoreTile(Modifier.width(260.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically) {
                StoreArt(categoryIcon(e.kategori), 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(e.subtitle, color = T.sub, fontSize = 12.sp, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { EntryAction(e, st, onRemove) }
            if (st == ModuleState.DOWNLOADING) ProgressLine(pct)
            if (error != null && st == ModuleState.FAILED) Text(error, color = RED, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CategoryTile(name: String, icon: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(96.dp)
            .background(if (selected) Look.accent.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PIcon(icon, if (selected) Look.accent else T.text, 26.dp)
        Text(name, color = T.text, fontSize = 13.sp, maxLines = 1)
        Text(count.toString(), color = T.sub, fontSize = 12.sp)
    }
}

/** The details page: size block, description, details, source and the source button. */
@Composable
private fun StoreDetail(
    e: StoreEntry, st: ModuleState, pct: Float?, error: String?, installedBytes: Long, onBack: () -> Unit, onRemove: (ExtraModule) -> Unit,
) {
    val ctx = LocalContext.current
    val style = storeStyle()
    Column(Modifier.fillMaxSize()) {
    StoreHeader(e.name, onBack)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        val head: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoreArt(categoryIcon(e.kategori), if (style == StoreStyle.CLASSIC) 40.dp else 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.name, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(categoryName(e.kategori), color = T.sub, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                EntryAction(e, st, onRemove)
                if (st == ModuleState.UPDATE && e is ModuleEntry) PButton(tr("Remove", "Kaldır"), danger = true, compact = true) { onRemove(e.m) }
                if (e is ModuleEntry && style != StoreStyle.PLAY) Text(tr("Version", "Sürüm") + " " + e.m.surum, color = T.sub, fontSize = 13.sp)
            }
            if (st == ModuleState.DOWNLOADING) ProgressLine(pct, Modifier.padding(top = 8.dp))
            if (error != null && st == ModuleState.FAILED) Text(error, color = RED, fontSize = 13.sp)
        }
        if (style == StoreStyle.PLAY) Column { head() } else StoreTile { Column { head() } }
        if (style == StoreStyle.PLAY && e is ModuleEntry) {
            // Play-Store-like stat strip: size, version, licence
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf(formatMb(e.m.boyut) to tr("Size", "Boyut"), e.m.surum to tr("Version", "Sürüm"), e.m.lisans to tr("License", "Lisans")).forEach { (v, k) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(k, color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        when (e) {
            is ModuleEntry -> {
                val m = e.m
                StoreRowShell {
                    InfoRows(buildList<Pair<String, String>> {
                        add(tr("Download", "İndirme") to formatMb(m.boyut))
                        if (inList(st)) add(tr("Installed", "Kurulu") to formatMb(installedBytes))
                    })
                }
                if (m.description().isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(m.description(), color = T.text, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
                StoreRowShell {
                    InfoRows(listOf(
                        tr("Version", "Sürüm") to m.surum,
                        tr("Updated", "Güncellendi") to m.guncellendi,
                        tr("License", "Lisans") to m.lisans,
                        tr("Source", "Kaynak") to (m.upstream ?: "—"),
                    ))
                }
                m.atif?.let { Spacer(Modifier.height(8.dp)); Text(it, color = T.sub, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (m.kaynakUrl ?: m.upstream)?.let { url -> PButton(tr("Source", "Kaynak"), compact = true) { StoreInstaller.open(ctx, url) } }
                    m.lisansMetni?.let { url -> PButton(tr("License", "Lisans metni"), compact = true) { StoreInstaller.open(ctx, url) } }
                }
            }
            is ExternalEntry -> {
                val a = e.a
                StoreRowShell {
                    InfoRows(listOf(
                        tr("Size", "Boyut") to a.sizeText,
                        tr("License", "Lisans") to a.license,
                        tr("Source", "Kaynak") to a.upstream,
                    ))
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Source", "Kaynak"), compact = true) { StoreInstaller.open(ctx, a.upstream) }
                }
            }
        }
    }
    }
}

/** Built into Pulse: nothing is downloaded, each row opens the page in this app. Routes are in Tools.kt; apps are Pulse's own standalone activities. */
private class BuiltinRow(val name: String, val sub: String, val icon: String, val onClick: () -> Unit)

@Composable
private fun BuiltinRows() {
    val open = LocalOpenTool.current
    val ctx = LocalContext.current
    val rows = listOf(
        BuiltinRow(tr("Music player", "Müzik çalar"), tr("Poweramp and Winamp looks, lyrics inside", "Poweramp ve Winamp görünümleri, içinde şarkı sözleri"), "play") { open("music") },
        BuiltinRow(tr("Video", "Video"), tr("Plays video files", "Video dosyalarını oynatır"), "image") { open("video") },
        BuiltinRow(tr("Online playlists", "Çevrimiçi çalma listeleri"), tr("YouTube and Spotify playlists, streamed", "YouTube ve Spotify listeleri, akış"), "play") { open("playlists") },
        BuiltinRow(tr("White noise", "Beyaz gürültü"), tr("Steady background sounds", "Sabit arka plan sesleri"), "volume") { open("whitenoise") },
        BuiltinRow(tr("Files", "Dosyalar"), tr("File explorer", "Dosya gezgini"), "folder") { launchExtra(ctx, "com.umutk.pulse.FilesActivity") },
        BuiltinRow(tr("Photos", "Fotoğraflar"), tr("Pictures and videos", "Resimler ve videolar"), "image") { launchExtra(ctx, "com.umutk.pulse.ImageViewActivity") },
        BuiltinRow(tr("Calendar", "Takvim"), tr("Events and reminders", "Etkinlikler ve hatırlatıcılar"), "calendar") { launchExtra(ctx, "com.umutk.pulse.CalendarActivity") },
        BuiltinRow(tr("Contacts", "Kişiler"), tr("Contacts and groups", "Kişiler ve gruplar"), "users") { launchExtra(ctx, "com.umutk.pulse.ContactsActivity") },
        BuiltinRow("ArtCraft", tr("Drawing", "Çizim"), "menu") { launchExtra(ctx, "com.umutk.pulse.ArtCraftActivity") },
    )
    SectionTitle(tr("Built in", "Yerleşik"))
    rows.forEach { r ->
        Row(Modifier.fillMaxWidth().clickable { r.onClick() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(r.icon, T.text, 26.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(r.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(r.sub, color = T.sub, fontSize = 12.sp)
            }
        }
    }
}
