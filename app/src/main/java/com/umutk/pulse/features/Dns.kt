package com.umutk.pulse.features

import com.umutk.pulse.subtitleCompat

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.openPulse
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

/** Private DNS in Settings.Global: mode "off", "opportunistic" (Auto) or "hostname" (Manual), plus the host name. */
object Dns {
    private val HOST = Regex("[A-Za-z0-9][A-Za-z0-9.-]{0,252}") // the host goes into a shell command, so only these characters

    /** Mode and host as the system reports them now. */
    fun current(c: Context): Pair<String, String> {
        val cr = c.contentResolver
        return (Settings.Global.getString(cr, "private_dns_mode") ?: "opportunistic") to Settings.Global.getString(cr, "private_dns_specifier").orEmpty()
    }

    fun savedHost(c: Context): String = c.getSharedPreferences("p", Context.MODE_PRIVATE).getString("dns_host", "").orEmpty()

    fun saveHost(c: Context, host: String) { c.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString("dns_host", host).apply() }

    fun validHost(h: String): Boolean = HOST.matches(h)

    /** Blocking. Returns "" on success, otherwise the note to show ("Needs root or Shizuku" or the command output).
     *  Writes the setting directly when WRITE_SECURE_SETTINGS is granted (fast, works from the tile); otherwise through
     *  Shizuku or root, and then grants that permission so the next change is direct. */
    fun write(c: Context, mode: String, host: String = ""): String {
        if (mode == "hostname" && !validHost(host)) return "Enter a valid host name"
        val cr = c.contentResolver
        try {
            if (mode == "hostname") Settings.Global.putString(cr, "private_dns_specifier", host)
            Settings.Global.putString(cr, "private_dns_mode", mode)
            return ""
        } catch (e: SecurityException) { }
        // a tile wakes Pulse in a fresh process: give the Shizuku binder a moment to arrive
        repeat(15) { if (!Shell.shizukuOk() && Shell.root != true) Thread.sleep(100) }
        if (!Shell.shizukuOk() && Shell.root != true) Shell.detectRoot()
        if (!Shell.shizukuOk() && Shell.root != true) return "Needs root or Shizuku"
        val cmd = if (mode == "hostname") "settings put global private_dns_specifier $host; settings put global private_dns_mode hostname"
        else "settings put global private_dns_mode $mode"
        val out = Shell.run("$cmd; pm grant ${c.packageName} android.permission.WRITE_SECURE_SETTINGS").trim()
        return if (current(c).first == mode) "" else out.ifEmpty { "Could not change Private DNS" }
    }
}

/**
 * A Private DNS of its own for Wi-Fi and for mobile data (a setting, off at first): when the phone's network changes between the two, the choice made for the new one is written
 * to Private DNS. A choice is "keep" (the DNS stays as it is), "auto", "off" or a host name. Writing it needs WRITE_SECURE_SETTINGS (granted once through root or Shizuku, as the
 * DNS tile does); without it nothing is changed. It runs in the main Pulse process while the setting is on.
 */
object DnsPerNet {
    private fun p(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun on(c: Context) = p(c).getBoolean("dnsPerNet", false)
    fun setOn(c: Context, v: Boolean) { p(c).edit().putBoolean("dnsPerNet", v).apply(); if (v) start(c) else stop(c) }
    // Each of the two networks has its own settings: the mode, the host when it is Manual, and whether a sign-in page makes a Manual one Auto for a while.
    // Wi-Fi starts as Auto, mobile data as Manual (the host saved in Pulse, else dns.google).
    private fun k(wifi: Boolean, what: String) = (if (wifi) "dnsWifi" else "dnsCell") + what
    /** "keep" (leave Private DNS alone), "auto", "off" or "manual". */
    fun mode(c: Context, wifi: Boolean): String {
        p(c).getString(k(wifi, "Mode"), null)?.let { return it }
        // what an older version saved: smart, keep, auto, off or a host name
        val old = p(c).getString(if (wifi) "dnsWifi" else "dnsCell", null)
        return when (old) { null, "smart" -> if (wifi) "auto" else "manual"; "keep", "auto", "off" -> old; else -> "manual" }
    }
    fun host(c: Context, wifi: Boolean): String {
        p(c).getString(k(wifi, "Host"), null)?.takeIf { Dns.validHost(it) }?.let { return it }
        val old = p(c).getString(if (wifi) "dnsWifi" else "dnsCell", null)
        if (old != null && old !in listOf("smart", "keep", "auto", "off") && Dns.validHost(old)) return old
        return Dns.savedHost(c).takeIf { Dns.validHost(it) } ?: "dns.google"
    }
    /** While the network asks for a sign-in page (or has no working internet) a Manual one is Auto, so the page can open. On at first. */
    fun portalAuto(c: Context, wifi: Boolean) = p(c).getBoolean(k(wifi, "Portal"), true)
    fun setMode(c: Context, wifi: Boolean, v: String) { p(c).edit().putString(k(wifi, "Mode"), v).apply(); applyNow(c) }
    fun setHost(c: Context, wifi: Boolean, v: String) { if (Dns.validHost(v)) { p(c).edit().putString(k(wifi, "Host"), v).apply(); applyNow(c) } }
    fun setPortalAuto(c: Context, wifi: Boolean, v: Boolean) { p(c).edit().putBoolean(k(wifi, "Portal"), v).apply(); applyNow(c) }

    private var cb: android.net.ConnectivityManager.NetworkCallback? = null
    private var last = ""

    /** The network asks for a sign-in page, or has no working internet (a Private DNS would stop the sign-in page from opening). */
    private fun portal(c: Context): Boolean = runCatching {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork) ?: return@runCatching false
        nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) || !nc.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(false)

    /** The kind of the default network now: "wifi", "cell" or "" (none or another kind). */
    private fun kind(c: Context): String = runCatching {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val nc = cm.getNetworkCapabilities(cm.activeNetwork)
        when { nc == null -> ""; nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"; nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cell"; else -> "" }
    }.getOrDefault("")

    /** Writes the choice made for the network the phone is on now (once for each change of network). */
    fun applyNow(c: Context, force: Boolean = true) {
        if (!on(c)) return
        val k = kind(c)
        val port = portal(c)
        // a change of the network, or of whether it asks for a sign-in, is a new case
        if (k.isEmpty() || (!force && (k + port) == last)) return
        last = k + port
        val wifi = k == "wifi"
        val m = mode(c, wifi)
        if (m == "keep") return
        Thread {
            val (mode, host) = when {
                m == "off" -> "off" to ""
                m == "auto" || (m == "manual" && port && portalAuto(c, wifi)) -> "opportunistic" to ""
                else -> "hostname" to host(c, wifi)
            }
            val now = Dns.current(c)
            if (now.first == mode && (mode != "hostname" || now.second == host)) return@Thread
            Dns.write(c, mode, host)
        }.start()
    }

    fun start(c: Context) {
        if (!on(c) || cb != null) return
        val app = c.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val n = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: android.net.Network, caps: android.net.NetworkCapabilities) { applyNow(app, force = false) }
            override fun onLost(network: android.net.Network) { last = "" }
        }
        cb = n
        runCatching { cm.registerDefaultNetworkCallback(n) }
        applyNow(app)
    }

    fun stop(c: Context) {
        val n = cb ?: return
        cb = null; last = ""
        runCatching { (c.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(n) }
    }
}

/** Settings > General: Private DNS as Auto, Off or Manual (host name + Apply). Shows the current setting when opened. */
@Composable
fun DnsSettings() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pick by remember { mutableStateOf("auto") }
    var now by remember { mutableStateOf("") }
    var field by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    fun reload() {
        val (m, h) = Dns.current(ctx)
        pick = when (m) { "off" -> "off"; "hostname" -> "manual"; else -> "auto" }
        now = when (m) { "off" -> "Off"; "hostname" -> "Manual, $h"; else -> "Auto" }
        field = h.ifEmpty { Dns.savedHost(ctx) }
    }
    LaunchedEffect(Unit) { reload() }
    fun apply(mode: String, host: String = "") {
        scope.launch {
            val r = withContext(Dispatchers.IO) { Dns.write(ctx, mode, host) }
            note = r
            if (r.isEmpty() && mode == "hostname") Dns.saveHost(ctx, host)
            reload()
        }
    }
    PCard {
        Text("Private DNS", color = Look.accent, fontSize = 15.sp)
        Text("Now: $now", color = T.sub, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(pick == "auto", "Auto") { pick = "auto"; apply("opportunistic") }
            PChip(pick == "off", "Off") { pick = "off"; apply("off") }
            PChip(pick == "manual", "Manual") { pick = "manual" }
        }
        if (pick == "manual") {
            PField(field, { field = it.trim() }, "dns.example.com")
            PButton("Apply") { apply("hostname", field) }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
    }
    // a DNS of its own for Wi-Fi and for mobile data
    var perNet by remember { mutableStateOf(DnsPerNet.on(ctx)) }
    PCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("Own DNS for Wi-Fi and mobile data", "Wi-Fi ve mobil veri için ayrı DNS"), color = Look.accent, fontSize = 15.sp)
                Text(tr("Changes Private DNS by itself when the phone moves between them: Wi-Fi starts as Auto, mobile data as Manual (needs root or Shizuku once)", "Telefon ikisi arasında geçince Özel DNS'i kendisi değiştirir: Wi-Fi Auto, mobil veri Manuel başlar (bir kez root ya da Shizuku gerekir)"), color = T.sub, fontSize = 12.sp)
            }
            com.umutk.pulse.PSwitch(perNet, { perNet = it; DnsPerNet.setOn(ctx, it) })
        }
        if (perNet) {
            NetDnsBlock(true, tr("Wi-Fi", "Wi-Fi"))
            NetDnsBlock(false, tr("Mobile data", "Mobil veri"))
        }
    }
}

/** The settings of one network: its mode (Keep, Auto, Off, Manual), the host of a Manual one (a preset or your own), and whether a sign-in page makes it Auto for a while. */
@Composable
private fun NetDnsBlock(wifi: Boolean, label: String) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(DnsPerNet.mode(ctx, wifi)) }
    var host by remember { mutableStateOf(DnsPerNet.host(ctx, wifi)) }
    var field by remember { mutableStateOf(DnsPerNet.host(ctx, wifi)) }
    var portal by remember { mutableStateOf(DnsPerNet.portalAuto(ctx, wifi)) }
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = T.text, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("keep" to tr("Keep", "Dokunma"), "auto" to "Auto", "off" to tr("Off", "Kapalı"), "manual" to tr("Manual", "Manuel")).forEach { (v, n) ->
                PChip(mode == v, n) { mode = v; DnsPerNet.setMode(ctx, wifi, v) }
            }
        }
        if (mode == "manual") {
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Cloudflare" to "one.one.one.one", "Google" to "dns.google", "AdGuard" to "dns.adguard-dns.com", "Quad9" to "dns.quad9.net").forEach { (n, h) ->
                    PChip(host == h, n) { host = h; field = h; DnsPerNet.setHost(ctx, wifi, h) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PField(field, { field = it.trim() }, "dns.example.com", Modifier.weight(1f))
                PButton(tr("Apply", "Uygula"), compact = true) { if (Dns.validHost(field)) { host = field; DnsPerNet.setHost(ctx, wifi, field) } }
            }
            if (field.isNotEmpty() && !Dns.validHost(field)) Text(tr("Not a host name", "Geçerli bir ad değil"), color = T.sub, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().clickable { portal = !portal; DnsPerNet.setPortalAuto(ctx, wifi, portal) }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Auto while a sign-in page is asked", "Giriş sayfası istenirken Auto"), color = T.text, fontSize = 14.sp)
                    Text(tr("A Manual DNS would stop the sign-in page of a hotel or school Wi-Fi from opening", "Manuel DNS otel ya da okul Wi-Fi'sinin giriş sayfasını açmaz"), color = T.sub, fontSize = 12.sp)
                }
                com.umutk.pulse.PSwitch(portal, { portal = it; DnsPerNet.setPortalAuto(ctx, wifi, it) })
            }
        }
    }
}

/** Quick Settings tile: Private DNS Auto, Manual (the host saved in Pulse, else dns.google) and Off, one tap moves to the next.
 *  A failed change shows its reason as the subtitle; without root or Shizuku the tap opens Settings. */
@androidx.annotation.RequiresApi(24)
class DnsTile : TileService() {
    private var note: String? = null
    private var busy = false
    override fun onStartListening() { com.umutk.pulse.Opt.load(this); note = null; paint() }
    private fun paint() {
        qsTile?.apply {
            val (m, h) = Dns.current(this@DnsTile)
            state = if (m != "off") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = tr("Private DNS", "Özel DNS")
            subtitleCompat = if (busy) "…" else note ?: when (m) { "off" -> tr("Off", "Kapalı"); "hostname" -> tr("Manual", "Manuel") + h.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty(); else -> tr("Auto", "Otomatik") }
            updateTile()
        }
    }
    // no tap animation here: the picture frames of the pop left the DNS tile with a wrong background, so the tap only switches
    override fun onClick() { click0() }
    private fun click0() {
        if (busy) return
        val (m, h) = Dns.current(this)
        // a host set in the system Settings is kept, so switching back on returns to it
        if (m == "hostname" && Dns.savedHost(this).isEmpty() && Dns.validHost(h)) Dns.saveHost(this, h)
        // Manual with no host saved in Pulse falls back to Google's resolver, so the tap always has the three steps
        val host = Dns.savedHost(this).ifEmpty { "dns.google" }
        // Auto -> Manual (the host saved in Pulse, else dns.google) -> Off -> Auto
        val next = when (m) { "off" -> "opportunistic"; "hostname" -> "off"; else -> if (Dns.validHost(host)) "hostname" else "off" }
        busy = true; note = null; paint()
        Thread {
            val r = Dns.write(this, next, host)
            Handler(Looper.getMainLooper()).post {
                busy = false
                // the reason stays under the tile until the panel is opened again
                note = r.takeIf { it.isNotEmpty() }?.lineSequence()?.first()?.take(40)?.let { tr("Failed: ", "Olmadı: ") + it }
                paint()
                if (r == "Needs root or Shizuku") openPulse("settings")
            }
        }.start()
    }
}
