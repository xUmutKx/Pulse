package com.umutk.pulse.features

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import com.umutk.pulse.pageGutter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AppEntry(val pkg: String, val name: String, val bytes: Long, val hidden: Boolean, val system: Boolean = false)

/**
 * Every app of the phone with its size. With the usage access the size includes each app's data. Apps hidden with `pm hide`
 * (or removed for this user with their data kept) are not in the normal list, so they are read with the "uninstalled too" flag; their size, which the
 * system will not give, comes from their folders through root or Shizuku (program, data, OBB and media).
 */
fun measureApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    @Suppress("DEPRECATION")
    val all = runCatching { pm.getInstalledApplications(android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES) }.getOrDefault(emptyList())
    val apps = all.ifEmpty { runCatching { pm.getInstalledApplications(0) }.getOrDefault(emptyList()) }
    val visible = runCatching { pm.getInstalledApplications(0).map { it.packageName }.toSet() }.getOrDefault(emptySet())
    val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager
    @Suppress("DEPRECATION")
    val usage = ops?.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) == android.app.AppOpsManager.MODE_ALLOWED
    val ssm = if (android.os.Build.VERSION.SDK_INT >= 26) ctx.getSystemService(Context.STORAGE_STATS_SERVICE) as? android.app.usage.StorageStatsManager else null
    fun apkBytes(a: android.content.pm.ApplicationInfo): Long {
        var n = a.sourceDir?.let { File(it).length() } ?: 0L
        a.splitSourceDirs?.forEach { n += File(it).length() }
        return n
    }
    val out = ArrayList<AppEntry>()
    fun label(a: android.content.pm.ApplicationInfo) = runCatching { pm.getApplicationLabel(a).toString() }.getOrDefault(a.packageName)
    val hidden = ArrayList<android.content.pm.ApplicationInfo>()
    for (a in apps) {
        val st = if (usage && ssm != null && a.packageName in visible) runCatching {
            ssm.queryStatsForPackage(android.os.storage.StorageManager.UUID_DEFAULT, a.packageName, android.os.Process.myUserHandle())
        }.getOrNull() else null
        if (a.packageName !in visible) {
            // only apps of the user count as hidden (a system app removed for this user is not a game hidden by hand), and at most 30 are measured by the shell
            if ((a.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && hidden.size < 30) hidden += a
            continue
        }
        out += AppEntry(a.packageName, label(a), if (st != null) st.appBytes + st.dataBytes + st.cacheBytes else apkBytes(a), false, (a.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0)
    }
    // the hidden ones: their folders measured with root or Shizuku (one run for all of them), else only their program files
    if (hidden.isNotEmpty()) {
        val P = com.umutk.pulse.extras.blackout.Privilege
        if (P.mode == P.Mode.None) runCatching { P.detect() }
        val sized = if (P.mode != P.Mode.None) runCatching {
            val script = hidden.joinToString("; ") { a ->
                val k = a.packageName
                "echo \"$k \$(du -sk /data/app/*/$k-* /data/app/$k-* /data/data/$k /data/user_de/0/$k /storage/emulated/0/Android/data/$k /storage/emulated/0/Android/obb/$k /storage/emulated/0/Android/media/$k 2>/dev/null | awk '{ if (!seen[\$2]++) s += \$1 } END { print s * 1024 }')\""
            }
            P.run("renice -n 19 -p \$\$ >/dev/null 2>&1; " + script).text.lines().mapNotNull { l -> l.trim().split(' ').takeIf { it.size == 2 }?.let { it[0] to (it[1].toDoubleOrNull()?.toLong() ?: 0L) } }.toMap()
        }.getOrDefault(emptyMap()) else emptyMap()
        for (a in hidden) out += AppEntry(a.packageName, label(a), maxOf(sized[a.packageName] ?: 0L, apkBytes(a)), true)
    }
    return out
}


/**
 * The apps of the phone, biggest first: the installed ones and the hidden ones (`pm hide`, or removed with their data kept), each with its size. A tap opens its details;
 * a long press starts a pick, and the bin uninstalls the picked apps (system apps cannot be picked). With root or Shizuku the apps go at once, hidden ones too
 * (they are shown again first, as a hidden app cannot be uninstalled); without them the system asks about each app in turn.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun InstalledAppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by remember { mutableStateOf<String?>(null) }
    var reload by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var ask by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    // without root or Shizuku: the apps wait here and the system's own question is asked for each, one after the other
    var queue by remember { mutableStateOf(listOf<String>()) }
    val apps by produceState<List<AppEntry>?>(null, reload) { value = withContext(Dispatchers.IO) { measureApps(ctx).sortedByDescending { it.bytes } } }
    val next = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { _ ->
        queue = queue.drop(1)
        if (queue.isEmpty()) reload++
    }
    androidx.compose.runtime.LaunchedEffect(queue) {
        queue.firstOrNull()?.let { pkg -> runCatching { next.launch(android.content.Intent(android.content.Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg"))) }.onFailure { queue = queue.drop(1) } }
    }
    BackHandler { if (open != null) open = null else if (picked.isNotEmpty()) picked = emptySet() else onBack() }
    open?.let { AppDetailsScreen(it) { open = null; reload++ }; return }
    val selecting = picked.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(Modifier.size(44.dp).clickable { picked = emptySet() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 22.dp) }
                val total = apps.orEmpty().filter { it.pkg in picked }.sumOf { it.bytes }
                Text("${picked.size} · " + humanSize(total), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box(Modifier.size(44.dp).clickable { ask = true }, contentAlignment = Alignment.Center) { PIcon("delete", T.text, 24.dp) }
            } else {
                Box(Modifier.size(44.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 22.dp, Modifier.rotate(180f)) }
                Text(tr("Apps", "Uygulamalar") + (apps?.let { " · ${it.size}" } ?: ""), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                apps?.let { l -> Text(humanSize(l.sumOf { it.bytes }), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(end = 10.dp)) }
            }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp))
        val list = apps
        if (list == null) com.umutk.pulse.Center(tr("Measuring…", "Ölçülüyor…"))
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = pageGutter, vertical = 4.dp)) {
            items(list, key = { it.pkg }) { a ->
                val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, a.pkg) {
                    value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(a.pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                }
                val on = a.pkg in picked
                Row(
                    Modifier.fillMaxWidth().background(if (on) T.accent.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent)
                        .combinedClickable(
                            onClick = { if (selecting) { if (!a.system) picked = if (on) picked - a.pkg else picked + a.pkg } else open = a.pkg },
                            onLongClick = { if (!a.system) picked = if (on) picked - a.pkg else picked + a.pkg },
                        ).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon?.let { androidx.compose.foundation.Image(it, null, Modifier.size(40.dp)) } ?: Spacer(Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(a.name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(a.pkg + if (a.system) " · " + tr("system", "sistem") else "", color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (a.hidden) Box(Modifier.padding(end = 8.dp)) { PIcon("lock", T.accent, 16.dp) }
                    Text(humanSize(a.bytes), color = T.text, fontSize = 14.sp)
                    if (selecting && !a.system) Box(Modifier.padding(start = 8.dp)) { com.umutk.pulse.PCheck(on, { picked = if (on) picked - a.pkg else picked + a.pkg }) }
                }
            }
        }
    }
    if (ask) {
        val names = apps.orEmpty().filter { it.pkg in picked }
        com.umutk.pulse.WinAlert(
            onDismissRequest = { ask = false },
            title = { Text(tr("Uninstall ${names.size} apps?", "${names.size} uygulama kaldırılsın mı?"), fontSize = 16.sp) },
            text = { Text(names.take(8).joinToString("\n") { it.name } + if (names.size > 8) "\n…" else "", fontSize = 13.sp) },
            confirmButton = {
                com.umutk.pulse.PButton(tr("Uninstall", "Kaldır")) {
                    ask = false
                    val pkgs = names.map { it.pkg }
                    val hiddenSet = names.filter { it.hidden }.map { it.pkg }.toSet()
                    picked = emptySet()
                    scope.launch {
                        val P = com.umutk.pulse.extras.blackout.Privilege
                        val power = withContext(Dispatchers.IO) { if (P.mode == P.Mode.None) runCatching { P.detect() }; P.mode != P.Mode.None }
                        if (power) {
                            note = tr("Uninstalling…", "Kaldırılıyor…")
                            val done = withContext(Dispatchers.IO) {
                                pkgs.count { pkg ->
                                    if (pkg in hiddenSet) P.run("pm unhide $pkg")
                                    P.run("pm uninstall $pkg").text.contains("Success", true)
                                }
                            }
                            note = tr("$done of ${pkgs.size} uninstalled", "${pkgs.size} uygulamadan $done tanesi kaldırıldı")
                            reload++
                        } else { note = ""; queue = pkgs }
                    }
                }
            },
            dismissButton = { com.umutk.pulse.PButton(tr("Cancel", "İptal")) { ask = false } },
        )
    }
}

private fun humanSize(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.0f MB".format(b / 1048576.0)
    else -> "%d KB".format(b / 1024)
}
