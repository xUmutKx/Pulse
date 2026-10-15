package com.umutk.pulse.features

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.FilterList
import com.umutk.pulse.PCheck
import com.umutk.pulse.onAccent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PHead
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

// The recommendation list of the Universal Android Debloater project: package name -> removal level and what the app does.
// Downloaded on demand and kept in the app's own storage; the licence of that list does not limit how Pulse uses it here.
private const val LIST_URL = "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
private const val PREF_REMOVED = "debloat_removed"

/** One package from the list: how safe it is to remove, and a short text about it. */
class DebloatInfo(
    val removal: String,
    val description: String,
    val list: String = "",
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
)

private fun JSONObject.strings(name: String): List<String> =
    optJSONArray(name)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } }.orEmpty()

/** Canta's badge colours (green, yellow, red, magenta). */
private fun badgeColor(level: String): Color = when (level) {
    "Recommended" -> Color(0xFF4CAF50); "Advanced" -> Color(0xFFFFEB3B); "Expert" -> Color(0xFFE53935); "Unsafe" -> Color(0xFFE040FB); else -> T.sub
}

@Composable
private fun LevelBadge(level: String) {
    val badge = badgeColor(level)
    Text(level, color = badge, fontSize = 9.sp, maxLines = 1, softWrap = false, modifier = Modifier.background(badge.copy(alpha = .14f), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp))
}

private fun listFile(ctx: Context) = File(ctx.filesDir, "uad_lists.json")

/** The downloaded list, or nothing yet. */
fun debloatList(ctx: Context): Map<String, DebloatInfo> = runCatching {
    // the copy downloaded from the internet wins; the one bundled with the app is the offline fallback
    val text = if (listFile(ctx).exists()) listFile(ctx).readText() else ctx.assets.open("debloat/uad_lists.json").bufferedReader().use { it.readText() }
    val o = JSONObject(text)
    o.keys().asSequence().associateWith { k ->
        o.getJSONObject(k).let {
            DebloatInfo(it.optString("removal", "Unknown"), it.optString("description", ""), it.optString("list", ""),
                it.strings("dependencies"), it.strings("neededBy"), it.strings("labels"))
        }
    }
}.getOrDefault(emptyMap())

/** Fetches the list and keeps it. Returns a line for the screen. Blocking: call it off the main thread. */
fun downloadDebloatList(ctx: Context): String = runCatching {
    val text = URL(LIST_URL).openStream().bufferedReader().use { it.readText() }
    val n = JSONObject(text).length()
    listFile(ctx).writeText(text)
    "Downloaded $n entries"
}.getOrElse { "Download failed: ${it.message}" }

/** The list's removal words, as Canta shows them: Recommended, Advanced, Expert or Unsafe. */
fun safetyOf(removal: String): String = when (removal) {
    "Recommended" -> "Recommended"
    "Advanced" -> "Advanced"
    "Expert" -> "Expert"
    "Unsafe" -> "Unsafe"
    else -> "Unknown"
}

private fun removedOf(ctx: Context): List<String> =
    ctx.getSharedPreferences("p", Context.MODE_PRIVATE).getString(PREF_REMOVED, "").orEmpty().split('\n').filter { it.isNotBlank() }

private fun saveRemoved(ctx: Context, list: List<String>) {
    ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString(PREF_REMOVED, list.joinToString("\n")).apply()
}

/** Installed apps by package name and label. Packages already removed for this user are not listed here. */
private fun installedLabels(ctx: Context): Map<String, String> =
    ctx.packageManager.getInstalledApplications(0).associate { it.packageName to ctx.packageManager.getApplicationLabel(it).toString() }

/** Packages installed for this user, read with pm as root. Anything removed by Canta or another tool is missing from it. Null when root is not there. */
private fun installedPkgs(): Set<String>? = runCatching {
    Shell.run("pm list packages --user 0").lines().filter { it.startsWith("package:") }.map { it.removePrefix("package:").trim() }.toSet()
}.getOrNull()?.takeIf { it.isNotEmpty() }

/** Packages that ship with the phone (FLAG_SYSTEM), so the User filter can hide them. */
private fun systemPkgs(ctx: Context): Set<String> =
    ctx.packageManager.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }.map { it.packageName }.toSet()

private val SAFETY_ORDER = listOf("Recommended", "Advanced", "Expert", "Unsafe", "Unknown")

/** The bag with a crossed-out bin: the debloat icon. Drawn in the theme's colour, like the other icons. */
@Composable
fun DebloatIcon(tint: Color, size: Dp = 22.dp) {
    val path = remember { PathParser().parsePathString(DEBLOAT_PATH).toPath().apply { fillType = PathFillType.EvenOdd } }
    Canvas(Modifier.size(size)) { scale(this.size.width / 24f, pivot = Offset.Zero) { drawPath(path, tint) } }
}

// bag outline with a handle; the cross inside is a hole (even-odd fill)
private const val DEBLOAT_PATH = "M8,8V6a4,4 0 0 1 8,0v2h2a2,2 0 0 1 2,2v10a2,2 0 0 1 -2,2H6a2,2 0 0 1 -2,-2V10a2,2 0 0 1 2,-2z " +
    "M10,13L11,12L12,13L13,12L14,13L13,14L14,15L13,16L12,15L11,16L10,15L11,14Z"

/** Debloater: remove the system apps the list marks, see what is removed, and bring them back. Uses root (pm / cmd package). */
@Composable
fun DebloatScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(debloatList(ctx)) }
    val labels = remember { installedLabels(ctx) }
    var removed by remember { mutableStateOf(removedOf(ctx)) }
    var tab by remember { mutableStateOf("uninstall") }
    var ask by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var installed by remember { mutableStateOf<Set<String>?>(null) }
    // Canta's filters: User is the default, the others narrow by system/level/unclassified
    var scope0 by remember { mutableStateOf("User") }
    // empty sets mean "all": the colour code is the removal level, the flag is the list the entry comes from (Aosp, Oem, Google...)
    var levels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var flags by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filterOpen by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var askBatch by remember { mutableStateOf(false) }
    // search: by app name or package, on both the uninstall and the reinstall lists
    var query by remember { mutableStateOf("") }
    var sys by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        // no list yet (first open, or never downloaded): fetch it once, so each app shows why it can go
        if (list.isEmpty()) {
            note = withContext(Dispatchers.IO) { downloadDebloatList(ctx) }
            list = debloatList(ctx)
        }
        installed = withContext(Dispatchers.IO) { installedPkgs() }
        sys = withContext(Dispatchers.IO) { systemPkgs(ctx) }
    }

    // runs pm / cmd package as root; the result is checked for "Success" (remove) or "installed for user" (restore)
    fun act(pkg: String, restore: Boolean) {
        busy = true
        scope.launch {
            val cmd = if (restore) "cmd package install-existing --user 0 $pkg" else "pm uninstall -k --user 0 $pkg"
            val out = withContext(Dispatchers.IO) { Shell.run(cmd) }
            val ok = out.contains("Success") || out.contains("installed for user")
            val name = labels[pkg] ?: pkg
            note = if (ok) "${if (restore) "Reinstalled" else "Removed"} $name" else "Failed: ${out.trim().take(120)}"
            if (ok) {
                removed = if (restore) removed - pkg else (removed - pkg) + pkg
                saveRemoved(ctx, removed)
            }
            busy = false
        }
    }

    // the selection goes one by one; the line at the top says how many went
    fun removeAll(pkgs: List<String>) {
        busy = true
        scope.launch {
            var done = 0
            var last = ""
            pkgs.forEach { pkg ->
                val out = withContext(Dispatchers.IO) { Shell.run("pm uninstall -k --user 0 $pkg") }
                if (out.contains("Success")) { done++; removed = (removed - pkg) + pkg; saveRemoved(ctx, removed) } else last = out.trim().take(80)
            }
            note = "Removed $done of ${pkgs.size}" + if (last.isNotEmpty()) " · $last" else ""
            picked = emptySet()
            busy = false
        }
    }

    // every app pm says is installed for this user; the chips pick which ones show
    val candidates = remember(list, removed, installed, sys, scope0, levels, flags, query) {
        (installed ?: labels.keys).filter { pkg ->
            val level = list[pkg]?.removal?.let { safetyOf(it) }?.takeIf { it != "Unknown" } ?: "Unclassified"
            pkg !in removed && matchesQuery(pkg, labels, query) && when (scope0) {
                "User" -> pkg !in sys
                "System" -> pkg in sys
                else -> true
            } && (levels.isEmpty() || level in levels) && (flags.isEmpty() || list[pkg]?.list.orEmpty() in flags)
        }
            .sortedWith(compareBy({ SAFETY_ORDER.indexOf(safetyOf(list[it]?.removal.orEmpty())) }, { labels[it]?.lowercase() ?: it }))
    }

    detail?.let { pkg ->
        val info = list[pkg]
        val level = safetyOf(info?.removal.orEmpty())
        // other packages by their app name when installed, else the bare package
        fun names(pkgs: List<String>) = pkgs.joinToString("\n") { p -> labels[p]?.let { "$it ($p)" } ?: p }
        WinAlert(
            onDismissRequest = { detail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(pkg, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(labels[pkg] ?: pkg, color = T.text, fontSize = 16.sp)
                        Text(pkg, color = T.sub, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        LevelBadge(level)
                        if (!info?.list.isNullOrBlank()) Text(info!!.list, color = T.sub, fontSize = 11.sp)
                    }
                    Text(info?.description?.ifBlank { null } ?: "Not in the list.", color = T.text, fontSize = 13.sp)
                    if (!info?.dependencies.isNullOrEmpty()) {
                        Text("Needs", color = T.sub, fontSize = 11.sp)
                        Text(names(info!!.dependencies), color = T.text, fontSize = 12.sp)
                    }
                    if (!info?.neededBy.isNullOrEmpty()) {
                        Text("Needed by", color = T.sub, fontSize = 11.sp)
                        Text(names(info!!.neededBy), color = T.text, fontSize = 12.sp)
                    }
                    if (!info?.labels.isNullOrEmpty()) Text(info!!.labels.joinToString(" · "), color = T.sub, fontSize = 11.sp)
                }
            },
            confirmButton = { PButton("Uninstall", danger = true) { detail = null; ask = pkg } },
            dismissButton = { PButton("Close") { detail = null } },
        )
    }

    if (askBatch) WinAlert(
        onDismissRequest = { askBatch = false },
        title = { Text("Remove ${picked.size} app${if (picked.size == 1) "" else "s"}?", color = T.text) },
        text = { Text(picked.joinToString("\n") { labels[it] ?: it }.take(600) + "\n\nThey are removed for this user only; Reinstall brings them back.", color = T.sub, fontSize = 13.sp) },
        confirmButton = { PButton("Remove", danger = true) { askBatch = false; removeAll(picked.toList()) } },
        dismissButton = { PButton("Cancel") { askBatch = false } },
        warn = true,
    )

    if (filterOpen) WinAlert(
        onDismissRequest = { filterOpen = false },
        title = { Text("Filter", color = T.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("User", "System", "Any").forEach { r -> PChip(scope0 == r, r) { scope0 = r } }
                }
                // colour code: the removal level, in Canta's colours
                Text("Colour", color = T.sub, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SAFETY_ORDER.map { if (it == "Unknown") "Unclassified" else it }.distinct().forEach { l ->
                        FilterPill(l, badgeColor(l), l in levels) { levels = if (l in levels) levels - l else levels + l }
                    }
                }
                val kinds = list.values.map { it.list }.filter { it.isNotBlank() }.distinct().sorted()
                if (kinds.isNotEmpty()) {
                    Text("Flag", color = T.sub, fontSize = 11.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        kinds.forEach { k -> FilterPill(k, Look.accent, k in flags) { flags = if (k in flags) flags - k else flags + k } }
                    }
                }
            }
        },
        confirmButton = { PButton("OK") { filterOpen = false } },
        dismissButton = { PButton("Reset") { scope0 = "User"; levels = emptySet(); flags = emptySet() } },
    )

    ask?.let { pkg ->
        val info = list[pkg]
        WinAlert(
            onDismissRequest = { ask = null },
            title = { Text("Remove ${labels[pkg] ?: pkg}?", color = T.text) },
            text = { Text("${safetyOf(info?.removal.orEmpty())}. ${info?.description.orEmpty()}\n\nIt is removed for this user only; its data stays, and Reinstall brings it back.", color = T.sub, fontSize = 13.sp) },
            confirmButton = { PButton("Remove", danger = true) { ask = null; act(pkg, false) } },
            dismissButton = { PButton("Cancel") { ask = null } },
            warn = true,
        )
    }

    val filtered = scope0 != "User" || levels.isNotEmpty() || flags.isNotEmpty()
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = T.text) }
                Box(Modifier.weight(1f)) { PHead("Debloat", "${candidates.size} to remove · ${removed.size} removed", guide = "debloat") }
                // the filter: a long line over a short one
                if (tab == "uninstall") IconButton(onClick = { filterOpen = true }) { Icon(Icons.Filled.FilterList, "Filter", tint = if (filtered) Look.accent else T.text) }
            }
        }
        if (note.isNotEmpty()) item { Text(note, color = Look.accent, fontSize = 12.sp) }
        item {
            // the undo sits at the top: it brings back the last app removed
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PButton("Undo last", danger = false) { removed.lastOrNull()?.let { act(it, true) } }
                PButton(if (busy) "Working…" else "Update list") {
                    if (!busy) scope.launch {
                        busy = true
                        note = withContext(Dispatchers.IO) { downloadDebloatList(ctx) }
                        list = debloatList(ctx)
                        busy = false
                    }
                }
            }
        }
        item {
            // left: uninstall, right: reinstall (the removed apps)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PChip(tab == "uninstall", "Uninstall") { tab = "uninstall" }
                PChip(tab == "reinstall", "Reinstall") { tab = "reinstall" }
            }
        }
        item {
            // search field: name or package, filters the list below
            Row(Modifier.fillMaxWidth().border(1.dp, T.line, RoundedCornerShape(50)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("search", T.sub, 18.dp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Search apps", color = T.sub, fontSize = 14.sp)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (list.isEmpty()) item { Text("No list yet. Press Update list to download it.", color = T.sub, fontSize = 13.sp) }

        if (tab == "uninstall") {
            items(candidates, key = { it }) { pkg ->
                val info = list[pkg]
                val level = safetyOf(info?.removal.orEmpty())
                val sel = pkg in picked
                PCard(Modifier.fillMaxWidth().clickable { detail = pkg }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            // the name on its own line, then the tags under it, then what the app is: a long name never pushes the tags out
                            Text(labels[pkg] ?: pkg, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                LevelBadge(level)
                                if (!info?.list.isNullOrBlank()) Text(info!!.list, color = T.sub, fontSize = 9.sp, maxLines = 1, softWrap = false,
                                    modifier = Modifier.border(1.dp, T.line, RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                            // what the app is, from the Universal Debloater list (the text Canta shows)
                            if (!info?.description.isNullOrBlank()) Text(info!!.description, color = T.sub, fontSize = 12.sp, maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                        // the box to tick; the delete button for all ticked apps shows at the bottom right
                        PCheck(sel, { picked = if (sel) picked - pkg else picked + pkg })
                    }
                }
            }
        } else {
            if (removed.isEmpty()) item { Text("Nothing removed yet.", color = T.sub, fontSize = 13.sp) }
            items(removed.reversed().filter { matchesQuery(it, labels, query) }, key = { "r_$it" }) { pkg ->
                PCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(pkg, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(labels[pkg] ?: pkg, color = T.text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        PButton("Reinstall") { act(pkg, true) }
                    }
                }
            }
        }
        item { Spacer(Modifier.size(88.dp)) }
    }
    if (picked.isNotEmpty() && tab == "uninstall") {
        val fab = Look.accent
        Row(Modifier.align(Alignment.BottomEnd).padding(16.dp).background(fab, RoundedCornerShape(50)).clickable { askBatch = true }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("delete", onAccent(fab), 22.dp)
            Spacer(Modifier.width(8.dp))
            Text("${picked.size}", color = onAccent(fab), fontSize = 15.sp)
        }
    }
    }
}

/** A filter choice with its colour dot; picked ones get an edge in that colour. */
@Composable
private fun FilterPill(label: String, dot: Color, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.border(if (on) 2.dp else 1.dp, if (on) dot else T.line, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(dot, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, color = T.text, fontSize = 13.sp, maxLines = 1)
    }
}

/** The search box: an empty query shows everything, otherwise the name or the package must contain it. */
private fun matchesQuery(pkg: String, labels: Map<String, String>, query: String): Boolean =
    query.isBlank() || (labels[pkg] ?: pkg).contains(query, ignoreCase = true) || pkg.contains(query, ignoreCase = true)
