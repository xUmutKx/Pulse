package com.umutk.pulse.features

import com.umutk.pulse.WinAlert
import androidx.compose.material3.TextButton
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Extra
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PHead
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSwitch
import com.umutk.pulse.tr
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AmShow(val en: String, val tur: String, val icon: String) {
    ALL("All apps", "Tüm uygulamalar", "folder"), USER("User apps", "Kullanıcı uygulamaları", "filetypes"),
    SYSTEM("System apps", "Sistem uygulamaları", "disk"), DISABLED("Disabled", "Devre dışı", "delete"),
    FROZEN("Frozen / suspended", "Dondurulmuş", "pause"), RUNNING("Running", "Çalışan", "run"),
    UPDATED_SYS("Updated system apps", "Güncellenmiş sistem uygulamaları", "download"),
    RECENT_INSTALL("Recently installed", "Yeni yüklenen", "newfolder"), RECENT_UPDATE("Recently updated", "Yeni güncellenen", "refresh"),
    RESTRICTED("Battery limit", "Pil kısıtlı", "performance"), MODULES("Xposed modules", "Xposed modülleri", "table"),
}

private enum class AmSource(val en: String, val tur: String) {
    ANY("Any source", "Her kaynak"), PLAY("Play Store", "Play Store"), SIDELOAD("Sideloaded", "Harici yükleme"), PULSE("Installed by Pulse", "Pulse ile yüklenen"),
}

private enum class AmSort(val en: String, val tur: String, val icon: String) {
    NAME("Name", "Ad", "rename"), SIZE("Size", "Boyut", "disk"), INSTALLED("Install date", "Yükleme tarihi", "download"),
    UPDATED("Update date", "Güncelleme tarihi", "refresh"), LAST_USED("Last used", "Son kullanım", "info"), PKG("Package name", "Paket adı", "table"),
}

private const val RECENT_MS = 7L * 24 * 60 * 60 * 1000

private class AmRow(
    val pkg: String,
    val label: String,
    val version: String,
    val sizeBytes: Long,
    val installed: Long,
    val updated: Long,
    val frozen: Boolean,
    val running: Boolean,
    val updatedSystem: Boolean,
    val installer: String?,
    val lastUsed: Long?,
    val system: Boolean,
    val disabled: Boolean,
    val restricted: Boolean,
    val module: Boolean,
)

// Background-run app op: anything other than ALLOWED means the system restricts it.
private const val RUN_ANY_IN_BACKGROUND = "android:run_any_in_background"
private const val ONE_YEAR_MS = 365L * 24 * 60 * 60 * 1000

/** Loads every installed app with the fields the list needs. Runs on IO. */
private fun loadAmRows(ctx: Context): List<AmRow> {
    val pm = ctx.packageManager
    val lastUsed = if (Extra.usageGranted(ctx)) lastUsedByPackage(ctx) else emptyMap()
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    return pm.getInstalledPackages(PackageManager.GET_META_DATA).mapNotNull { p ->
        // extra modules from the Extra Apps store stay out of the app list
        if (com.umutk.pulse.extras.store.StoreInstaller.isStoreInstalled(ctx, p.packageName)) return@mapNotNull null
        val ai = p.applicationInfo ?: return@mapNotNull null
        AmRow(
            pkg = p.packageName,
            label = ai.loadLabel(pm).toString(),
            version = p.versionName.orEmpty(),
            // APK size, same measure the existing Apps page uses.
            sizeBytes = File(ai.sourceDir).length(),
            installed = p.firstInstallTime,
            updated = p.lastUpdateTime,
            frozen = ai.flags and ApplicationInfo.FLAG_SUSPENDED != 0,
            running = ai.flags and ApplicationInfo.FLAG_STOPPED == 0,
            updatedSystem = ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            installer = runCatching { if (android.os.Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(p.packageName).installingPackageName else @Suppress("DEPRECATION") pm.getInstallerPackageName(p.packageName) }.getOrNull(),
            lastUsed = lastUsed[p.packageName],
            system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
            disabled = !ai.enabled,
            restricted = isRestrictedInBackground(ops, ai),
            module = isXposedModule(ai),
        )
    }
}

private fun lastUsedByPackage(ctx: Context): Map<String, Long> {
    val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    return usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - ONE_YEAR_MS, now)
        .filter { it.lastTimeUsed > 0 }
        .groupBy { it.packageName }
        .mapValues { (_, stats) -> stats.maxOf { it.lastTimeUsed } }
}

private fun isRestrictedInBackground(ops: AppOpsManager, ai: ApplicationInfo): Boolean {
    val mode = runCatching {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(RUN_ANY_IN_BACKGROUND, ai.uid, ai.packageName)
    }.getOrDefault(AppOpsManager.MODE_ALLOWED)
    return mode != AppOpsManager.MODE_ALLOWED
}

private fun AmRow.matches(show: AmShow, now: Long): Boolean = when (show) {
    AmShow.ALL -> true
    AmShow.USER -> !system
    AmShow.SYSTEM -> system
    AmShow.DISABLED -> disabled
    AmShow.FROZEN -> frozen
    AmShow.RUNNING -> running
    AmShow.UPDATED_SYS -> updatedSystem
    AmShow.RECENT_INSTALL -> now - installed < RECENT_MS
    AmShow.RECENT_UPDATE -> now - updated < RECENT_MS && updated > installed
    AmShow.RESTRICTED -> restricted
    AmShow.MODULES -> module
}

private fun AmRow.fromSource(src: AmSource): Boolean = when (src) {
    AmSource.ANY -> true
    AmSource.PLAY -> installer == "com.android.vending"
    AmSource.SIDELOAD -> installer != "com.android.vending" && !system
    AmSource.PULSE -> installer == "com.umutk.pulse"
}

private fun comparatorFor(sort: AmSort): Comparator<AmRow> = when (sort) {
    AmSort.NAME -> compareBy<AmRow> { it.label.lowercase() }
    AmSort.SIZE -> compareBy { it.sizeBytes }
    AmSort.INSTALLED -> compareBy { it.installed }
    AmSort.UPDATED -> compareBy { it.updated }
    AmSort.LAST_USED -> compareBy<AmRow> { it.lastUsed ?: -1L }
    AmSort.PKG -> compareBy<AmRow> { it.pkg }
}

// survives leaving for the detail page; filter and sort also persist in prefs "p" (query never does)
private object AmKept {
    var rows: List<AmRow>? = null
    var query = ""
    var show = AmShow.ALL
    var source = AmSource.ANY
    var sort = AmSort.NAME
    var asc = true
    var showSystem = true
    var showDisabled = true
    var hidePulse = false
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = ctx.getSharedPreferences("p", Context.MODE_PRIVATE)
        show = runCatching { AmShow.valueOf(sp.getString("am_show", "")!!) }.getOrDefault(AmShow.ALL)
        source = runCatching { AmSource.valueOf(sp.getString("am_source", "")!!) }.getOrDefault(AmSource.ANY)
        sort = runCatching { AmSort.valueOf(sp.getString("am_sort", "")!!) }.getOrDefault(AmSort.NAME)
        asc = sp.getBoolean("am_asc", true)
        showSystem = sp.getBoolean("am_sys", true)
        showDisabled = sp.getBoolean("am_dis", true)
        hidePulse = sp.getBoolean("am_hide", false)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit()
            .putString("am_show", show.name).putString("am_source", source.name).putString("am_sort", sort.name)
            .putBoolean("am_asc", asc).putBoolean("am_sys", showSystem).putBoolean("am_dis", showDisabled).putBoolean("am_hide", hidePulse).apply()
    }
}

/** One big comfortable row of the filter panel. */
@Composable
private fun AmRowItem(icon: String, label: String, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(icon, T.sub, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun AmSection(title: String) {
    Text(title, color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
fun AppManagerScreen(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val listState = rememberKeptListState("appmanager")
    AmKept.load(ctx)
    var rows by remember { mutableStateOf(AmKept.rows) }
    var query by remember { mutableStateOf(AmKept.query) }
    var show by remember { mutableStateOf(AmKept.show) }
    var source by remember { mutableStateOf(AmKept.source) }
    var sort by remember { mutableStateOf(AmKept.sort) }
    var asc by remember { mutableStateOf(AmKept.asc) }
    var showSystem by remember { mutableStateOf(AmKept.showSystem) }
    var showDisabled by remember { mutableStateOf(AmKept.showDisabled) }
    var hidePulse by remember { mutableStateOf(AmKept.hidePulse) }
    SideEffect {
        AmKept.rows = rows; AmKept.query = query; AmKept.show = show; AmKept.source = source; AmKept.sort = sort
        AmKept.asc = asc; AmKept.showSystem = showSystem; AmKept.showDisabled = showDisabled; AmKept.hidePulse = hidePulse
    }
    LaunchedEffect(show, source, sort, asc, showSystem, showDisabled, hidePulse) { AmKept.save(ctx) }
    val active = show != AmShow.ALL || source != AmSource.ANY || sort != AmSort.NAME || !asc || !showSystem || !showDisabled || hidePulse
    fun reset() { show = AmShow.ALL; source = AmSource.ANY; sort = AmSort.NAME; asc = true; showSystem = true; showDisabled = true; hidePulse = false }
    var filterOpen by remember { mutableStateOf(false) }
    var debloat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // the descriptions of what each app is live in Debloat, not here
    LaunchedEffect(Unit) { rows = withContext(Dispatchers.IO) { loadAmRows(ctx) } }

    val all = rows.orEmpty()
    val shown = remember(rows, query, show, source, sort, asc, showSystem, showDisabled, hidePulse) {
        val now = System.currentTimeMillis()
        all.filter { a ->
            a.matches(show, now) && a.fromSource(source) &&
                (show == AmShow.SYSTEM || show == AmShow.UPDATED_SYS || showSystem || !a.system) &&
                (show == AmShow.DISABLED || showDisabled || !a.disabled) &&
                !(hidePulse && a.pkg == "com.umutk.pulse") &&
                (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }.sortedWith(comparatorFor(sort).let { if (asc) it else it.reversed() })
    }

    // the debloater opens over this list (its own screen, with Back)
    if (debloat) {
        DebloatScreen { debloat = false }
        return
    }

    if (filterOpen) WinAlert(
        onDismissRequest = { filterOpen = false },
        title = { Text(tr("Filter", "Filtre"), color = T.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                AmSection(tr("Show", "Göster"))
                AmShow.entries.forEach { m -> AmRowItem(m.icon, tr(m.en, m.tur), { show = m }) { PRadio(show == m) { show = m } } }
                AmSection(tr("Source", "Kaynak"))
                AmSource.entries.forEach { m -> AmRowItem("download", tr(m.en, m.tur), { source = m }) { PRadio(source == m) { source = m } } }
                AmSection(tr("Sort by", "Sırala"))
                AmSort.entries.forEach { m -> AmRowItem(m.icon, tr(m.en, m.tur), { sort = m }) { PRadio(sort == m) { sort = m } } }
                AmRowItem(if (asc) "up" else "down", if (asc) tr("Ascending", "Artan") else tr("Descending", "Azalan"), { asc = !asc }) { PSwitch(asc, { asc = it }) }
                AmSection(tr("Options", "Seçenekler"))
                AmRowItem("disk", tr("Show system apps", "Sistem uygulamalarını göster"), { showSystem = !showSystem }) { PSwitch(showSystem, { showSystem = it }) }
                AmRowItem("delete", tr("Show disabled", "Devre dışıları göster"), { showDisabled = !showDisabled }) { PSwitch(showDisabled, { showDisabled = it }) }
                AmRowItem("view", tr("Hide Pulse", "Pulse'u gizle"), { hidePulse = !hidePulse }) { PSwitch(hidePulse, { hidePulse = it }) }
            }
        },
        confirmButton = { PButton(tr("Apply", "Uygula")) { filterOpen = false } },
        dismissButton = { PButton(tr("Reset", "Sıfırla")) { reset() } },
    )

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PHead("App manager", if (rows == null) "Loading" else "${shown.size} / ${all.size}")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // one filter button: filter, sort and the order, all with icons, in a dialog
                Box(Modifier.size(40.dp).clickable { filterOpen = true }, contentAlignment = Alignment.Center) {
                    PIcon("filter", if (active) Look.accent else T.text, 22.dp)
                    if (active) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(8.dp).clip(RoundedCornerShape(50)).background(Look.accent))
                }
                if (active) {
                    val sum = listOfNotNull(
                        tr(show.en, show.tur).takeIf { show != AmShow.ALL },
                        tr(source.en, source.tur).takeIf { source != AmSource.ANY },
                        "${tr(sort.en, sort.tur)} ${if (asc) "↑" else "↓"}".takeIf { sort != AmSort.NAME || !asc },
                    ).joinToString(" · ").ifBlank { tr("Options", "Seçenekler") }
                    Row(Modifier.clickable { reset() }.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sum, color = T.sub, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.width(4.dp))
                        PIcon("close", T.sub, 14.dp)
                    }
                }
                Spacer(Modifier.weight(1f))
                // the one button on the right opens the debloater (Recommended and Advanced system apps)
                Row(Modifier.border(1.dp, T.line, RoundedCornerShape(50)).clickable { debloat = true }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("delete", T.text, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Debloat", color = T.text, fontSize = 13.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PField(query, { query = it }, "Search", Modifier.weight(1f))
                // one tap clears what was typed
                if (query.isNotEmpty()) Box(Modifier.padding(start = 6.dp).size(36.dp).clickable { query = "" }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 18.dp) }
            }
        }
        items(shown, key = { it.pkg }) { a ->
            // a tap opens the app's details; picking several apps lives in Debloat
            PCard(Modifier.clickable { onOpen(a.pkg) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(a.pkg, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, color = T.text, fontSize = 14.sp, maxLines = 1)
                            if (a.module) { Spacer(Modifier.width(6.dp)); ModuleBadge() }
                        }
                        val tags = listOfNotNull(
                            a.version.ifBlank { null },
                            "%.1f MB".format(a.sizeBytes / 1048576f),
                            "Disabled".takeIf { a.disabled },
                            "Restricted".takeIf { a.restricted },
                        )
                        Text(tags.joinToString(" · "), color = T.sub, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
