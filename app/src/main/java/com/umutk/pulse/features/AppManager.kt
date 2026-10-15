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
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.onAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class AmFilter(val label: String, val icon: String) {
    ALL("All", "folder"), USER("User", "filetypes"), SYSTEM("System", "disk"), DISABLED("Disabled", "delete"),
    RESTRICTED("Battery limit", "performance"), MODULES("Xposed modules", "table"),
}

private enum class AmSort(val label: String, val icon: String) {
    NAME("Name", "rename"), SIZE("Size", "disk"), INSTALLED("Install date", "download"), LAST_USED("Last used", "info"),
}

/** A filter or sort chip with its icon: the picked one takes the accent, the others stay quiet. */
@Composable
private fun AmChip(on: Boolean, label: String, icon: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(T.radius.coerceAtMost(12.dp))
    Row(
        Modifier.clip(shape)
            .background(if (on) Look.accent.copy(alpha = .16f) else Color.Transparent)
            .border(1.dp, if (on) Look.accent else T.line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIcon(icon, if (on) Look.accent else T.sub, 16.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (on) T.text else T.sub, fontSize = 12.sp)
    }
}

private class AmRow(
    val pkg: String,
    val label: String,
    val version: String,
    val sizeBytes: Long,
    val installed: Long,
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
        val ai = p.applicationInfo ?: return@mapNotNull null
        AmRow(
            pkg = p.packageName,
            label = ai.loadLabel(pm).toString(),
            version = p.versionName.orEmpty(),
            // APK size, same measure the existing Apps page uses.
            sizeBytes = File(ai.sourceDir).length(),
            installed = p.firstInstallTime,
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

private fun AmRow.matches(filter: AmFilter): Boolean = when (filter) {
    AmFilter.ALL -> true
    AmFilter.USER -> !system
    AmFilter.SYSTEM -> system
    AmFilter.DISABLED -> disabled
    AmFilter.RESTRICTED -> restricted
    AmFilter.MODULES -> module
}

private fun comparatorFor(sort: AmSort): Comparator<AmRow> = when (sort) {
    AmSort.NAME -> compareBy<AmRow> { it.label.lowercase() }
    AmSort.SIZE -> compareByDescending { it.sizeBytes }
    AmSort.INSTALLED -> compareByDescending { it.installed }
    AmSort.LAST_USED -> compareByDescending<AmRow> { it.lastUsed ?: -1L }
}

@Composable
fun AppManagerScreen(onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<AmRow>?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AmFilter.ALL) }
    var sort by remember { mutableStateOf(AmSort.NAME) }
    var debloat by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // the descriptions of what each app is live in Debloat, not here
    LaunchedEffect(Unit) { rows = withContext(Dispatchers.IO) { loadAmRows(ctx) } }

    val all = rows.orEmpty()
    val shown = remember(rows, query, filter, sort) {
        all.filter { a ->
            a.matches(filter) && (query.isBlank() || a.label.contains(query, true) || a.pkg.contains(query, true))
        }.sortedWith(comparatorFor(sort))
    }

    // the debloater opens over this list (its own screen, with Back)
    if (debloat) {
        DebloatScreen { debloat = false }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            PHead("App manager", if (rows == null) "Loading" else "${shown.size} / ${all.size}")
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                // the one button on the right opens the debloater (Recommended and Advanced system apps)
                Row(Modifier.border(1.dp, T.line, RoundedCornerShape(50)).clickable { debloat = true }.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("delete", T.text, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Debloat", color = T.text, fontSize = 13.sp)
                }
            }
            PField(query, { query = it }, "Search", Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AmFilter.entries.forEach { f -> AmChip(filter == f, f.label, f.icon) { filter = f } }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AmSort.entries.forEach { s -> AmChip(sort == s, s.label, s.icon) { sort = s } }
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
