package com.umutk.pulse.features

import androidx.compose.foundation.layout.Box
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.umutk.pulse.AppIcon
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private class AuditApp(val pkg: String, val label: String)
private class AuditPerm(val permission: String, val label: String, val apps: List<AuditApp>)

/** Runtime ("dangerous") permissions and the label shown for each. */
private val DANGEROUS: Map<String, String> = linkedMapOf(
    "android.permission.ACCESS_FINE_LOCATION" to "Precise location",
    "android.permission.ACCESS_COARSE_LOCATION" to "Approximate location",
    "android.permission.ACCESS_BACKGROUND_LOCATION" to "Background location",
    "android.permission.CAMERA" to "Camera",
    "android.permission.RECORD_AUDIO" to "Microphone",
    "android.permission.READ_CONTACTS" to "Read contacts",
    "android.permission.WRITE_CONTACTS" to "Write contacts",
    "android.permission.GET_ACCOUNTS" to "Accounts",
    "android.permission.READ_CALENDAR" to "Read calendar",
    "android.permission.WRITE_CALENDAR" to "Write calendar",
    "android.permission.READ_PHONE_STATE" to "Phone state",
    "android.permission.READ_PHONE_NUMBERS" to "Phone numbers",
    "android.permission.CALL_PHONE" to "Make calls",
    "android.permission.ANSWER_PHONE_CALLS" to "Answer calls",
    "android.permission.READ_CALL_LOG" to "Read call log",
    "android.permission.WRITE_CALL_LOG" to "Write call log",
    "android.permission.ADD_VOICEMAIL" to "Voicemail",
    "android.permission.USE_SIP" to "SIP calls",
    "android.permission.PROCESS_OUTGOING_CALLS" to "Outgoing calls",
    "android.permission.SEND_SMS" to "Send SMS",
    "android.permission.RECEIVE_SMS" to "Receive SMS",
    "android.permission.READ_SMS" to "Read SMS",
    "android.permission.RECEIVE_WAP_PUSH" to "WAP push",
    "android.permission.RECEIVE_MMS" to "Receive MMS",
    "android.permission.READ_EXTERNAL_STORAGE" to "Storage (read)",
    "android.permission.WRITE_EXTERNAL_STORAGE" to "Storage (write)",
    "android.permission.READ_MEDIA_IMAGES" to "Photos",
    "android.permission.READ_MEDIA_VIDEO" to "Videos",
    "android.permission.READ_MEDIA_AUDIO" to "Audio files",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" to "Selected photos",
    "android.permission.ACCESS_MEDIA_LOCATION" to "Media location",
    "android.permission.BODY_SENSORS" to "Body sensors",
    "android.permission.BODY_SENSORS_BACKGROUND" to "Background body sensors",
    "android.permission.ACTIVITY_RECOGNITION" to "Activity recognition",
    "android.permission.POST_NOTIFICATIONS" to "Notifications",
    "android.permission.BLUETOOTH_SCAN" to "Bluetooth scan",
    "android.permission.BLUETOOTH_CONNECT" to "Bluetooth connect",
    "android.permission.BLUETOOTH_ADVERTISE" to "Bluetooth advertise",
    "android.permission.NEARBY_WIFI_DEVICES" to "Nearby Wi-Fi devices",
    "android.permission.UWB_RANGING" to "Ultra-wideband ranging",
)

/** Scans installed non-system apps for granted dangerous permissions. Runs on IO. */
private fun loadAudit(ctx: Context): List<AuditPerm> {
    val pm = ctx.packageManager
    val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= 33) {
        pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
    } else {
        @Suppress("DEPRECATION")
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
    }
    val byPerm = HashMap<String, MutableList<AuditApp>>()
    for (p in packages) {
        val ai = p.applicationInfo ?: continue
        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
            (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
        if (isSystem) continue
        val names = p.requestedPermissions ?: continue
        val flags = p.requestedPermissionsFlags ?: continue
        var label: String? = null
        for (i in names.indices) {
            val name = names[i]
            if (name !in DANGEROUS) continue
            if ((flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) continue
            if (label == null) label = pm.getApplicationLabel(ai).toString()
            byPerm.getOrPut(name) { mutableListOf() }.add(AuditApp(p.packageName, label))
        }
    }
    return byPerm
        .map { (perm, apps) -> AuditPerm(perm, DANGEROUS.getValue(perm), apps.sortedBy { it.label.lowercase() }) }
        .sortedWith(compareByDescending<AuditPerm> { it.apps.size }.thenBy { it.label })
}

@Composable
fun PermAuditScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var rows by remember { mutableStateOf<List<AuditPerm>?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        rows = withContext(Dispatchers.IO) { runCatching { loadAudit(ctx) }.getOrDefault(emptyList()) }
    }

    val data = rows
    val current = selected?.let { perm -> data?.firstOrNull { it.permission == perm } }

    BackHandler {
        if (selected != null) selected = null else onBack()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Box(Modifier.size(40.dp).clickable { if (selected != null) selected = null else onBack() }, contentAlignment = Alignment.Center) { com.umutk.pulse.PIcon("back", com.umutk.pulse.T.text, 22.dp) }
        Spacer(Modifier.size(12.dp))

        when {
            data == null -> {
                PHead("Permissions", "")
                Text("Scanning apps...", color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
            }
            current != null -> {
                PHead(current.label, "${current.apps.size} apps")
                Spacer(Modifier.size(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(current.apps, key = { it.pkg }) { app ->
                        PCard(Modifier.fillMaxWidth().clickable { onOpen(app.pkg) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(app.pkg, 32.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(app.pkg, color = T.sub, fontSize = 11.sp)
                                }
                                PButton("Settings") {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            data.isEmpty() -> {
                PHead("Permissions", "")
                Text(
                    "No user app holds a dangerous permission.",
                    color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
                )
            }
            else -> {
                PHead("Permissions", "${data.size} types")
                Spacer(Modifier.size(8.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(data, key = { it.permission }) { perm ->
                        PCard(Modifier.fillMaxWidth().clickable { selected = perm.permission }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(perm.label, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(perm.permission.removePrefix("android.permission."), color = T.sub, fontSize = 11.sp)
                                }
                                Text("${perm.apps.size}", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
