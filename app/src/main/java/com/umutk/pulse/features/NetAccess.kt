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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
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

    /**
     * The devices that answer on this Wi-Fi, as addresses. Every address of the phone's own /24 network is asked: an echo, then a connection to a few ports (a refused
     * connection also shows a device is there: phones, TVs and routers answer that way when they do not answer the echo), and two multicast calls (SSDP and mDNS) are
     * listened to for a moment. The phone itself is not in the list; empty when the phone is not on a Wi-Fi network.
     */
    fun scanList(c: Context): List<String> = runCatching {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val net = cm.activeNetwork ?: return emptyList()
        val caps = cm.getNetworkCapabilities(net)
        if (caps == null || !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) return emptyList()
        val la = cm.getLinkProperties(net)?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address } ?: return emptyList()
        val own = la.address.address
        val ownText = la.address.hostAddress.orEmpty()
        val found = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        val hosts = (1..254).map { i -> own.copyOf().also { it[3] = i.toByte() } }.filter { !it.contentEquals(own) }
        // the multicast calls run beside the sweep
        val listeners = listOf(
            Thread { multicast("239.255.255.250", 1900, "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n".toByteArray(), found, ownText) },
            Thread { multicast("224.0.0.251", 5353, byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 9, 95, 115, 101, 114, 118, 105, 99, 101, 115, 7, 95, 100, 110, 115, 45, 115, 100, 4, 95, 117, 100, 112, 5, 108, 111, 99, 97, 108, 0, 0, 12, 0, 1), found, ownText) },
        ).onEach { it.start() }
        val pool = java.util.concurrent.Executors.newFixedThreadPool(64)
        try {
            val futures = hosts.map { h ->
                pool.submit<Boolean> {
                    val addr = java.net.InetAddress.getByAddress(h)
                    var up = runCatching { addr.isReachable(250) }.getOrDefault(false)
                    if (!up) for (port in intArrayOf(80, 443, 53, 62078, 8009, 7000, 22, 445)) {
                        val s = java.net.Socket()
                        try { s.connect(java.net.InetSocketAddress(addr, port), 200); up = true }
                        catch (e: java.net.ConnectException) { if ((e.message ?: "").contains("refused", true)) up = true }
                        catch (e: Exception) { }
                        finally { runCatching { s.close() } }
                        if (up) break
                    }
                    if (up) found.add(addr.hostAddress.orEmpty())
                    up
                }
            }
            futures.forEach { runCatching { it.get(12, java.util.concurrent.TimeUnit.SECONDS) } }
        } finally { pool.shutdownNow() }
        listeners.forEach { runCatching { it.join(2500) } }
        found.filter { it != ownText }.sortedBy { ip -> ip.substringAfterLast('.').toIntOrNull() ?: 0 }
    }.getOrDefault(emptyList())

    /** One multicast call and the addresses of whoever answers within about two seconds. */
    private fun multicast(group: String, port: Int, query: ByteArray, found: MutableSet<String>, own: String) {
        runCatching {
            val sock = java.net.DatagramSocket()
            sock.soTimeout = 400
            sock.send(java.net.DatagramPacket(query, query.size, java.net.InetAddress.getByName(group), port))
            val end = System.currentTimeMillis() + 2200
            val buf = ByteArray(1500)
            while (System.currentTimeMillis() < end) {
                try { val p = java.net.DatagramPacket(buf, buf.size); sock.receive(p); p.address.hostAddress?.let { if (it != own) found.add(it) } } catch (e: java.net.SocketTimeoutException) { }
            }
            sock.close()
        }
    }

    fun scan(c: Context): Int = scanList(c).size

    /** Sets the two cuts of one app. Returns false when the rule could not be set (no root or Shizuku). */
    fun setRule(c: Context, pkg: String, wifi: Boolean, mobile: Boolean): Boolean {
        val uid = runCatching { c.packageManager.getApplicationInfo(pkg, 0).uid }.getOrElse { return false }
        val script = buildString {
            // the old rules of this app go first, so they never stack up
            // (a delete of a rule that is not there is not an error: without "|| true" the last one made lifting every cut look like a failure)
            append("iptables -D OUTPUT -m owner --uid-owner $uid -o wlan+ -j DROP 2>/dev/null || true; ")
            append("iptables -D OUTPUT -m owner --uid-owner $uid -o rmnet+ -j DROP 2>/dev/null || true; ")
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
    var found by remember { mutableStateOf<List<String>>(emptyList()) }
    var open by remember { mutableStateOf(false) }
    // the scan runs again each time the button is pressed (and once when the card opens)
    var scan by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(true) }
    LaunchedEffect(scan) {
        scanning = true
        withContext(Dispatchers.IO) { NetPolicy.reapply(ctx); val l = NetPolicy.scanList(ctx); found = l; devices = maxOf(NetPolicy.devices(), l.size) }
        scanning = false
    }
    PCard(Modifier.fillMaxWidth()) {
        Text(tr("Devices on this Wi-Fi", "Bu Wi-Fi'deki cihazlar"), color = T.sub, fontSize = 12.sp)
        Text(if (scanning) "…" else "${devices + 1}", color = T.text, fontSize = 26.sp)
        Text(tr("this phone and the others it sees (the router too)", "bu telefon ve gördüğü diğerleri (yönlendirici dahil)"), color = T.sub, fontSize = 11.sp)
        // the addresses that answered
        if (!scanning && found.isNotEmpty()) Text(found.take(40).joinToString("  ·  "), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.padding(top = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PButton(tr("Access per app", "Uygulama başına erişim")) { open = true }
            Spacer(Modifier.padding(start = 8.dp))
            PButton(tr("Scan again", "Tekrar tara"), compact = true) { if (!scanning) scan++ }
        }
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
                        Text(ctx.packageManager.getApplicationLabel(ai).toString(), color = if (wifi || mobile) T.text.copy(alpha = .55f) else T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        // the two icons are the buttons: lit when the app may use that network, grey when it is cut off
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).clickable { val was = wifi; wifi = !wifi; scope.launch(Dispatchers.IO) { if (!NetPolicy.setRule(ctx, pkg, wifi, mobile)) { wifi = was; netFail(ctx) } } }.semantics { contentDescription = "Wi-Fi" }, contentAlignment = Alignment.Center) {
                            PIcon("wifi", if (!wifi) T.accent else T.sub.copy(alpha = .35f), 26.dp)
                            if (wifi) BlockSlash()
                        }
                        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).clickable { val was = mobile; mobile = !mobile; scope.launch(Dispatchers.IO) { if (!NetPolicy.setRule(ctx, pkg, wifi, mobile)) { mobile = was; netFail(ctx) } } }.semantics { contentDescription = tr("Mobile", "Mobil") }, contentAlignment = Alignment.Center) {
                            PIcon("phone", if (!mobile) T.accent else T.sub.copy(alpha = .35f), 26.dp)
                            if (mobile) BlockSlash()
                        }
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat"), onClick = onDismiss) },
    )
}

/** A red slash over an icon: the app is cut off from this network. */
@Composable
private fun BlockSlash() {
    Box(Modifier.size(30.dp).drawBehind {
        drawLine(androidx.compose.ui.graphics.Color(0xFFE53935), androidx.compose.ui.geometry.Offset(size.width * .12f, size.height * .88f), androidx.compose.ui.geometry.Offset(size.width * .88f, size.height * .12f), 3.5f, androidx.compose.ui.graphics.StrokeCap.Round)
    })
}

private fun netFail(ctx: Context) {
    val msg = if (Privilege.mode == Privilege.Mode.None) tr("Needs root or Shizuku to cut an app off.", "Bir uygulamayı kesmek için root ya da Shizuku gerekir.")
    else tr("The cut could not be set (iptables refused it).", "Kesme uygulanamadı (iptables reddetti).")
    android.os.Handler(android.os.Looper.getMainLooper()).post { android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show() }
}
