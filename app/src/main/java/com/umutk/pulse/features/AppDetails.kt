package com.umutk.pulse.features

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.AppIcon
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

private class CompRow(val name: String, val exported: Boolean)
private class ActionRow(val action: String, val component: String)
private class Details(
    val label: String,
    val version: String,
    val isSystem: Boolean,
    val activities: List<CompRow>,
    val services: List<CompRow>,
    val receivers: List<CompRow>,
    val actions: List<ActionRow>,
    val actionsNote: String?,
    val permissions: List<String>,
)

private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

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

    return Details(
        label = ai.loadLabel(pm).toString(),
        version = pi.versionName ?: "",
        isSystem = ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
        activities = pi.activities.orEmpty().map { CompRow(it.name, it.exported) },
        services = pi.services.orEmpty().map { CompRow(it.name, it.exported) },
        receivers = pi.receivers.orEmpty().map { CompRow(it.name, it.exported) },
        actions = manifest.first.sortedWith(compareBy({ it.action }, { it.component })),
        actionsNote = manifest.second,
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
                    "activity", "service", "receiver" -> component = qualify(pkg, parser.getAttributeValue(ANDROID_NS, "name"))
                    "action" -> component?.let { c ->
                        parser.getAttributeValue(ANDROID_NS, "name")?.let { out += ActionRow(it, c) }
                    }
                }
            } else if (event == XmlPullParser.END_TAG && parser.name in setOf("activity", "service", "receiver")) {
                component = null
            }
            event = parser.next()
        }
    } finally {
        parser.close()
    }
    return out.distinctBy { it.action to it.component }
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
            PButton("Back") { onBack() }
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
                if (d != null && !d.isSystem) {
                    PButton("Uninstall", danger = true) { confirmUninstall = true }
                }
            }
            if (note.isNotEmpty()) Text(note, color = Look.accent, fontSize = 12.sp)
            error?.let { Text(it, color = T.sub, fontSize = 13.sp) }
        }

        if (d != null) {
            section("Activities (${d.activities.size})")
            components(d.activities)
            section("Services (${d.services.size})")
            components(d.services)
            section("Receivers (${d.receivers.size})")
            components(d.receivers)

            section("Intent actions (${d.actions.size})")
            if (d.actionsNote != null) item { Text(d.actionsNote, color = T.sub, fontSize = 12.sp) }
            else if (d.actions.isEmpty()) item { Text("None declared", color = T.sub, fontSize = 12.sp) }
            items(d.actions, key = { it.action + "|" + it.component }) { a ->
                PCard {
                    Text(a.action, color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(a.component, color = T.sub, fontSize = 11.sp)
                }
            }

            section("Permissions (${d.permissions.size})")
            if (d.permissions.isEmpty()) item { Text("None requested", color = T.sub, fontSize = 12.sp) }
            items(d.permissions, key = { "perm|$it" }) { p ->
                PCard {
                    Text(p.substringAfterLast('.'), color = T.text, fontSize = 13.sp)
                    Text(p, color = T.sub, fontSize = 11.sp)
                }
            }
        }
    }

    if (confirmUninstall && d != null) {
        AlertDialog(
            onDismissRequest = { confirmUninstall = false },
            title = { Text("Uninstall ${d.label}?", color = T.text) },
            text = { Text("The system will ask you to confirm the removal.", color = T.sub) },
            confirmButton = {
                PButton("Uninstall", danger = true) {
                    confirmUninstall = false
                    ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            dismissButton = { PButton("Cancel") { confirmUninstall = false } },
        )
    }
}

private fun LazyListScope.section(title: String) {
    item {
        Text(
            title.uppercase(),
            color = Look.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

private fun LazyListScope.components(list: List<CompRow>) {
    if (list.isEmpty()) item { Text("None declared", color = T.sub, fontSize = 12.sp) }
    items(list, key = { "c|" + it.name }) { c ->
        PCard {
            Text(c.name.substringAfterLast('.'), color = T.text, fontSize = 13.sp)
            Text(
                c.name + if (c.exported) " · exported" else " · not exported",
                color = T.sub,
                fontSize = 11.sp,
            )
        }
    }
}
