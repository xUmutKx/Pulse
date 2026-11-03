package com.umutk.pulse.features

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.blackout.Privilege
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import com.umutk.pulse.PIcon

/**
 * Per-app network access: an app can be cut off from Wi-Fi and from mobile data. The cut is an iptables rule per app (owner match on its
 * UID), so it needs root or Shizuku; without either the switches stay off. The rules are kept on the phone and set again when the page opens.
 */
object NetPolicy {
    private fun prefs(c: Context) = c.getSharedPreferences("netpolicy", Context.MODE_PRIVATE)
    fun wifiBlocked(c: Context, pkg: String) = prefs(c).getBoolean("wifi_$pkg", false)
    fun mobileBlocked(c: Context, pkg: String) = prefs(c).getBoolean("mobile_$pkg", false)

    /** Devices in the phone's neighbour table (the router included); 0 when the table cannot be read. */
    fun devices(): Int = runCatching {
        java.io.File("/proc/net/arp").readLines().drop(1).count { l ->
            val p = l.trim().split(Regex("\\s+"))
            p.size >= 4 && p[2] != "0x0" && p[3] != "00:00:00:00:00:00"
        }
    }.getOrDefault(0)

    /** Sets the two cuts of one app. Returns false when the rule could not be set (no root or Shizuku). */
    fun setRule(c: Context, pkg: String, wifi: Boolean, mobile: Boolean): Boolean {
        val uid = runCatching { c.packageManager.getApplicationInfo(pkg, 0).uid }.getOrElse { return false }
        val script = buildString {
            // the old rules of this app go first, so they never stack up
            append("iptables -D OUTPUT -m owner --uid-owner $uid -o wlan+ -j DROP 2>/dev/null; ")
            append("iptables -D OUTPUT -m owner --uid-owner $uid -o rmnet+ -j DROP 2>/dev/null; ")
            if (wifi) append("iptables -A OUTPUT -m owner --uid-owner $uid -o wlan+ -j DROP; ")
            if (mobile) append("iptables -A OUTPUT -m owner --uid-owner $uid -o rmnet+ -j DROP; ")
        }
        val ok = Privilege.run(script).code == 0
        if (ok) {
            val names = prefs(c).getStringSet("pkgs", emptySet()).orEmpty().toMutableSet()
            if (wifi || mobile) names += pkg else names -= pkg
            prefs(c).edit().putBoolean("wifi_$pkg", wifi).putBoolean("mobile_$pkg", mobile).putStringSet("pkgs", names).apply()
        }
        return ok
    }

    /** The saved cuts are set again (the rules do not survive a restart). */
    fun reapply(c: Context) {
        if (Privilege.mode == Privilege.Mode.None) return
        prefs(c).getStringSet("pkgs", emptySet()).orEmpty().forEach { pkg -> setRule(c, pkg, wifiBlocked(c, pkg), mobileBlocked(c, pkg)) }
    }
}

/** The network page's card: the number of devices on this Wi-Fi, and the per-app access list. */
@Composable
fun NetAccessCard() {
    val ctx = LocalContext.current
    var devices by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { NetPolicy.reapply(ctx); devices = NetPolicy.devices() }
    }
    PCard(Modifier.fillMaxWidth()) {
        Text(tr("Devices on this Wi-Fi", "Bu Wi-Fi'deki cihazlar"), color = T.sub, fontSize = 12.sp)
        Text("${devices + 1}", color = T.text, fontSize = 26.sp)
        Text(tr("this phone and the others it sees (the router too)", "bu telefon ve gördüğü diğerleri (yönlendirici dahil)"), color = T.sub, fontSize = 11.sp)
        Spacer(Modifier.padding(top = 6.dp))
        PButton(tr("Access per app", "Uygulama başına erişim")) { open = true }
    }
    if (open) NetAppsDialog { open = false }
}

@Composable
private fun NetAppsDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val apps = remember {
        val pm = ctx.packageManager
        pm.getInstalledApplications(0).filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Access per app", "Uygulama başına erişim"), color = T.text) },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
                if (Privilege.mode == Privilege.Mode.None) Text(tr("Needs root or Shizuku.", "Root ya da Shizuku gerekir."), color = T.sub, fontSize = 12.sp)
                apps.forEach { ai ->
                    val pkg = ai.packageName
                    var wifi by remember(pkg) { mutableStateOf(NetPolicy.wifiBlocked(ctx, pkg)) }
                    var mobile by remember(pkg) { mutableStateOf(NetPolicy.mobileBlocked(ctx, pkg)) }
                    // the app's own icon, then its name, then a switch for Wi-Fi and one for mobile data (each with its icon); a change is saved at once
                    val icon by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pkg) {
                        value = withContext(Dispatchers.IO) { runCatching { ctx.packageManager.getApplicationIcon(ai).toBitmap(96, 96).asImageBitmap() }.getOrNull() }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                            icon?.let { Image(it, null, Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)), contentScale = ContentScale.Fit) }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(ctx.packageManager.getApplicationLabel(ai).toString(), color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        PIcon("wifi", if (!wifi) T.accent else T.sub, 18.dp, Modifier.semantics { contentDescription = "Wi-Fi" })
                        PSwitch(!wifi, { on -> val was = wifi; wifi = !on; scope.launch(Dispatchers.IO) { if (!NetPolicy.setRule(ctx, pkg, !on, mobile)) wifi = was } })
                        Spacer(Modifier.width(10.dp))
                        PIcon("phone", if (!mobile) T.accent else T.sub, 18.dp, Modifier.semantics { contentDescription = tr("Mobile", "Mobil") })
                        PSwitch(!mobile, { on -> val was = mobile; mobile = !on; scope.launch(Dispatchers.IO) { if (!NetPolicy.setRule(ctx, pkg, wifi, !on)) mobile = was } })
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat"), onClick = onDismiss) },
    )
}
