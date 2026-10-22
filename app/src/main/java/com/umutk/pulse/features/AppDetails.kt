package com.umutk.pulse.features

import com.umutk.pulse.Shell
import com.umutk.pulse.tr
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.umutk.pulse.WinAlert
import com.umutk.pulse.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private class CompRow(val name: String, val exported: Boolean)
private class ActionRow(val action: String, val component: String)

/** A plain-language function the app can perform, with the icon that stands for it. */
private data class Fn(val label: String, val icon: ImageVector)

private val FN_OTHER = Fn("Other actions", Icons.Filled.Extension)

// Matched against the action string in order, first hit wins. Keep "SENDTO" above "SEND".
private val FN_RULES: List<Pair<String, Fn>> = listOf(
    "SENDTO" to Fn("Messages", Icons.Filled.Message),
    "SMS" to Fn("Messages", Icons.Filled.Message),
    "VIEW" to Fn("Opens links or files", Icons.Filled.OpenInNew),
    "SEND" to Fn("Shares content", Icons.Filled.Share),
    "CAPTURE" to Fn("Camera", Icons.Filled.CameraAlt),
    "CALL" to Fn("Phone calls", Icons.Filled.Call),
    "DIAL" to Fn("Phone calls", Icons.Filled.Call),
    "PICK" to Fn("Picks files", Icons.Filled.FolderOpen),
    "GET_CONTENT" to Fn("Picks files", Icons.Filled.FolderOpen),
    "OPEN_DOCUMENT" to Fn("Picks files", Icons.Filled.FolderOpen),
    "BOOT" to Fn("Starts at boot", Icons.Filled.RestartAlt),
    "ALARM" to Fn("Alarms", Icons.Filled.Alarm),
    "BLUETOOTH" to Fn("Bluetooth", Icons.Filled.Bluetooth),
    "LOCATION" to Fn("Location", Icons.Filled.LocationOn),
    "WIFI" to Fn("Network", Icons.Filled.Wifi),
    "CONNECTIVITY" to Fn("Network", Icons.Filled.Wifi),
    "PACKAGE" to Fn("App install and removal", Icons.Filled.Apps),
    "SCREEN" to Fn("Screen events", Icons.Filled.PhoneAndroid),
    "USER_PRESENT" to Fn("Screen events", Icons.Filled.PhoneAndroid),
    "BATTERY" to Fn("Battery events", Icons.Filled.BatteryFull),
    "VOICE" to Fn("Voice", Icons.Filled.Mic),
    "ASSIST" to Fn("Voice", Icons.Filled.Mic),
    "NOTIFICATION" to Fn("Notifications", Icons.Filled.Notifications),
    "SEARCH" to Fn("Search", Icons.Filled.Search),
)

private fun fnFor(action: String): Fn =
    FN_RULES.firstOrNull { action.contains(it.first, ignoreCase = true) }?.second ?: FN_OTHER

private class Details(
    val label: String,
    val version: String,
    val isSystem: Boolean,
    val activities: List<CompRow>,
    val services: List<CompRow>,
    val receivers: List<CompRow>,
    val actions: List<ActionRow>,
    val actionsNote: String?,
    val functions: List<Pair<Fn, Int>>,
    val permissions: List<String>,
)

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
private val COMPONENT_TAGS = setOf("activity", "activity-alias", "service", "receiver")

/** Loads everything the details screen shows. Runs on IO. */
private fun loadDetails(ctx: Context, pkg: String): Details {
    val pm = ctx.packageManager
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS
    val pi = pm.getPackageInfo(pkg, flags)
    val ai = pi.applicationInfo ?: throw IllegalStateException("No application info for $pkg")

    // PackageInfo carries no intent filters, so the action strings come from the manifest itself.
    val manifest = try {
        manifestActions(ctx, pkg) to null
    } catch (e: Exception) {
        emptyList<ActionRow>() to "Could not read the manifest (${e.javaClass.simpleName})"
    }
    val actions = manifest.first.sortedWith(compareBy({ it.action }, { it.component }))

    return Details(
        label = ai.loadLabel(pm).toString(),
        version = pi.versionName ?: "",
        isSystem = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
        activities = pi.activities.orEmpty().map { CompRow(it.name, it.exported) },
        services = pi.services.orEmpty().map { CompRow(it.name, it.exported) },
        receivers = pi.receivers.orEmpty().map { CompRow(it.name, it.exported) },
        actions = actions,
        actionsNote = manifest.second,
        functions = actions.groupBy { fnFor(it.action) }.map { (fn, list) -> fn to list.size }
            .sortedByDescending { it.second },
        permissions = pi.requestedPermissions.orEmpty().sorted(),
    )
}

/** Walks AndroidManifest.xml of the target APK and collects action strings under each component's intent filters. */
private fun manifestActions(ctx: Context, pkg: String): List<ActionRow> {
    val out = mutableListOf<ActionRow>()
    val parser = ctx.createPackageContext(pkg, 0).assets.openXmlResourceParser("AndroidManifest.xml")
    try {
        var component: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    in COMPONENT_TAGS -> component = qualify(pkg, parser.getAttributeValue(ANDROID_NS, "name"))
                    "action" -> component?.let { c ->
                        parser.getAttributeValue(ANDROID_NS, "name")?.let { out += ActionRow(it, c) }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name in COMPONENT_TAGS) {
                component = null
            }
            event = parser.next()
        }
    } finally {
        parser.close()
    }
    return out.distinctBy { it.action to it.component }
}

/**
 * Copies the installed APK to Downloads/Pulse/extract. A split install (app bundle) goes into one .apks zip with
 * base.apk and the splits. Returns the note to show: the saved path or the reason it failed. Runs on IO.
 */
/** Where Extract APK writes: a folder chosen in Settings, by default Downloads/Pulse/extract. */
object ApkExtract {
    private fun prefs(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun path(c: Context): String = prefs(c).getString("apkExtractDir", null) ?: File(Downloads.defaultDir(), "Pulse/extract").path
    fun dir(c: Context): File = File(path(c))
    fun set(c: Context, p: String) { prefs(c).edit().putString("apkExtractDir", p).apply() }
}

internal fun extractApp(ctx: Context, pkg: String): String {
    val pm = ctx.packageManager
    val pi = pm.getPackageInfo(pkg, 0)
    val ai = pi.applicationInfo ?: return "Could not read"
    val splits = ai.splitSourceDirs.orEmpty().toList()
    val files = listOf(File(ai.sourceDir)) + splits.map { File(it) }
    if (files.any { !it.canRead() }) return "Could not read"
    val name = listOf(ai.loadLabel(pm).toString(), pi.versionName.orEmpty())
        .filter { it.isNotBlank() }.joinToString("-").replace(Regex("[^\\p{L}\\p{N} .-]"), "_")
    val dir = ApkExtract.dir(ctx).apply { mkdirs() }
    return try {
        val out = if (splits.isEmpty()) {
            File(dir, "$name.apk").also { files[0].copyTo(it, overwrite = true) }
        } else {
            File(dir, "$name.apks").also { target ->
                ZipOutputStream(target.outputStream().buffered()).use { zos ->
                    files.forEachIndexed { i, f ->
                        zos.putNextEntry(ZipEntry(if (i == 0) "base.apk" else f.name))
                        f.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        "Saved to ${out.path}"
    } catch (e: Exception) {
        "Extract failed: ${e.message ?: e.javaClass.simpleName}"
    }
}

private fun qualify(pkg: String, name: String?): String? = when {
    name == null -> null
    name.startsWith(".") -> pkg + name
    !name.contains('.') -> "$pkg.$name"
    else -> name
}

@Composable
fun AppDetailsScreen(pkg: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var details by remember(pkg) { mutableStateOf<Details?>(null) }
    var error by remember(pkg) { mutableStateOf<String?>(null) }
    var note by remember(pkg) { mutableStateOf("") }
    var confirmUninstall by remember(pkg) { mutableStateOf(false) }

    LaunchedEffect(pkg) {
        withContext(Dispatchers.IO) { runCatching { loadDetails(ctx, pkg) } }
            .onSuccess { details = it }
            .onFailure { error = it.message ?: "Package not found" }
    }

    BackHandler { onBack() }

    val d = details
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("back", com.umutk.pulse.T.text, 22.dp) }
            PHead(d?.label ?: pkg, d?.version ?: "")
            Text(pkg, color = T.sub, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Open") {
                    val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
                    if (launch != null) ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    else note = "${d?.label ?: pkg} has no screen to open"
                }
                PButton("App info") {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                PButton("Force stop") {
                    Shell.run("am force-stop $pkg")
                    note = "${d?.label ?: pkg} was stopped"
                }
                PButton("Extract APK") { scope.launch { note = tr("Extracting…", "Çıkarılıyor…"); note = withContext(Dispatchers.IO) { extractApp(ctx, pkg) } } }
                if (d != null && !d.isSystem) {
                    PButton("Uninstall", danger = true) { confirmUninstall = true }
                }
            }
            if (note.isNotEmpty()) Text(note, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp)
            error?.let { Text(it, color = T.sub, fontSize = 13.sp) }
        }

        if (d != null) {
            section("Functions (${d.functions.size})", Icons.Filled.Bolt)
            if (d.functions.isEmpty() && d.actionsNote == null) item { Text("None declared", color = T.sub, fontSize = 12.sp) }
            items(d.functions, key = { "fn|" + it.first.label }) { (fn, n) ->
                IconCard(fn.icon, fn.label, "$n intent action" + if (n == 1) "" else "s", bold = true)
            }

            section("Activities (${d.activities.size})", Icons.Filled.Web)
            components(d.activities, Icons.Filled.Web)
            section("Services (${d.services.size})", Icons.Filled.Build)
            components(d.services, Icons.Filled.Build)
            section("Receivers (${d.receivers.size})", Icons.Filled.Sensors)
            components(d.receivers, Icons.Filled.Sensors)

            section("Intent actions (${d.actions.size})", Icons.Filled.Share)
            if (d.actionsNote != null) item { Text(d.actionsNote, color = T.sub, fontSize = 12.sp) }
            else if (d.actions.isEmpty()) item { Text("None declared", color = T.sub, fontSize = 12.sp) }
            items(d.actions, key = { it.action + "|" + it.component }) { a ->
                IconCard(fnFor(a.action).icon, a.action, a.component)
            }

            section("Permissions (${d.permissions.size})", Icons.Filled.Lock)
            if (d.permissions.isEmpty()) item { Text("None requested", color = T.sub, fontSize = 12.sp) }
            items(d.permissions, key = { "perm|$it" }) { p ->
                IconCard(Icons.Filled.Lock, p.substringAfterLast('.'), p)
            }
        }
    }

    if (confirmUninstall && d != null) {
        val uninstall = {
            confirmUninstall = false
            ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        // the Windows looks frame the button themselves, so they get a plain TextButton inside it
        val winBtn = com.umutk.pulse.LocalWindowLook.current.id in com.umutk.pulse.winButtonLooks
        WinAlert(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Uninstall") },
            text = { Text("Uninstall ${d.label}? The system will ask you to confirm the removal.", color = T.sub) },
            confirmButton = { if (winBtn) androidx.compose.material3.TextButton(uninstall) { Text("Uninstall") } else PButton("Uninstall", danger = true, onClick = uninstall) },
            dismissButton = { if (winBtn) androidx.compose.material3.TextButton({ confirmUninstall = false }) { Text("Cancel") } else PButton("Cancel") { confirmUninstall = false } },
            warn = true,
        )
    }
}

@Composable
private fun IconCard(icon: ImageVector, title: String, subtitle: String, bold: Boolean = false) {
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), Look.accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = T.text, fontSize = 13.sp, fontWeight = if (bold) FontWeight.SemiBold else null)
                Text(subtitle, color = T.sub, fontSize = 11.sp)
            }
        }
    }
}

private fun LazyListScope.section(title: String, icon: ImageVector) {
    item {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp), Look.accent)
            Spacer(Modifier.width(6.dp))
            Text(title.uppercase(), color = Look.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun LazyListScope.components(list: List<CompRow>, icon: ImageVector) {
    if (list.isEmpty()) item { Text("None declared", color = T.sub, fontSize = 12.sp) }
    items(list, key = { "c|" + it.name }) { c ->
        IconCard(icon, c.name.substringAfterLast('.'), c.name + (if (c.exported) " · exported" else " · not exported"))
    }
}
