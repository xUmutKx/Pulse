package com.umutk.pulse.features

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
}

/** Quick Settings tile: Private DNS Auto, Manual (the host saved in Pulse) and Off, one tap moves to the next.
 *  A failed change shows its reason as the subtitle; without root or Shizuku the tap opens Settings. */
class DnsTile : TileService() {
    private var note: String? = null
    private var busy = false
    override fun onStartListening() { com.umutk.pulse.Opt.load(this); note = null; paint() }
    private fun paint() {
        qsTile?.apply {
            val (m, h) = Dns.current(this@DnsTile)
            state = if (m != "off") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = tr("Private DNS", "Özel DNS")
            subtitle = if (busy) "…" else note ?: when (m) { "off" -> tr("Off", "Kapalı"); "hostname" -> tr("Manual", "Manuel") + h.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty(); else -> tr("Auto", "Otomatik") }
            updateTile()
        }
    }
    override fun onClick() {
        if (busy) return
        val (m, h) = Dns.current(this)
        // a host set in the system Settings is kept, so switching back on returns to it
        if (m == "hostname" && Dns.savedHost(this).isEmpty() && Dns.validHost(h)) Dns.saveHost(this, h)
        val host = Dns.savedHost(this)
        // Auto -> Manual (when a host is saved in Pulse) -> Off -> Auto
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
