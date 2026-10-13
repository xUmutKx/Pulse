package com.umutk.pulse.features

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
import com.umutk.pulse.MainActivity
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.Shell
import com.umutk.pulse.T
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

    /** Blocking. Returns "" on success, otherwise the note to show ("Needs root or Shizuku" or the command output). */
    fun write(mode: String, host: String = ""): String {
        if (Shell.root == null) Shell.detectRoot()
        if (!Shell.shizukuOk() && Shell.root != true) return "Needs root or Shizuku"
        if (mode == "hostname" && !validHost(host)) return "Enter a valid host name"
        val cmd = if (mode == "hostname") "settings put global private_dns_mode hostname; settings put global private_dns_specifier $host"
        else "settings put global private_dns_mode $mode"
        return Shell.run(cmd).trim()
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
            val r = withContext(Dispatchers.IO) { Dns.write(mode, host) }
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

/** Quick Settings tile: toggles Private DNS between Off and Manual with the saved host; without a host it opens Settings. */
class DnsTile : TileService() {
    override fun onStartListening() = paint()
    private fun paint() { qsTile?.apply { state = if (Dns.current(this@DnsTile).first == "hostname") Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; label = "DNS"; updateTile() } }
    override fun onClick() {
        val host = Dns.savedHost(this)
        if (!Dns.validHost(host)) { openSettings(); return }
        val manual = Dns.current(this).first == "hostname"
        Thread { Dns.write(if (manual) "off" else "hostname", host); Handler(Looper.getMainLooper()).post { paint() } }.start()
    }
    private fun openSettings() {
        val i = Intent(this, MainActivity::class.java).putExtra("start", "settings").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        else startActivityAndCollapse(i)
    }
}
