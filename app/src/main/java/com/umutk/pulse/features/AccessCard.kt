package com.umutk.pulse.features

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.Proc
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

/** True while neither root nor a granted Shizuku is available. Reads the Shizuku state so callers redraw when it changes. */
fun accessMissing(): Boolean { Shell.shizuku; return Shell.root != true && !Shell.shizukuOk() }

/** Shows root and Shizuku status with actions; draws nothing once either one works. [limited] says what the page shows without them. */
@Composable
fun AccessNeededCard(feature: String, limited: String = "", modifier: Modifier = Modifier) {
    if (!accessMissing()) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rootTried by remember { mutableStateOf(false) }
    val installed = remember(Shell.shizuku) { Shell.shizukuInstalled(ctx) }
    val shizukuState = when {
        Shell.shizuku == 1 -> tr("Shizuku: permission needed", "Shizuku: izin gerekli")
        Shell.shizuku == 2 -> tr("Shizuku: ready", "Shizuku: hazır")
        installed -> tr("Shizuku not started", "Shizuku başlatılmamış")
        else -> tr("Shizuku: not installed", "Shizuku: yüklü değil")
    }
    PCard(modifier) {
        Text(feature, color = T.text, fontSize = 15.sp)
        if (limited.isNotBlank()) Text(limited, color = T.sub, fontSize = 13.sp)
        Text(if (rootTried) tr("Root: not available", "Root: yok") else tr("Root: not detected", "Root: algılanmadı"), color = T.sub, fontSize = 13.sp)
        Text(shizukuState, color = T.sub, fontSize = 13.sp)
        if (Shell.shizukuMsg.isNotBlank()) Text(Shell.shizukuMsg, color = T.sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Check root", "Root'u dene")) { scope.launch { withContext(Dispatchers.IO) { Shell.detectRoot() }; rootTried = true } }
            when {
                Shell.shizuku == 1 -> PButton(tr("Allow Shizuku", "Shizuku'ya izin ver")) { Shell.askShizuku(ctx) }
                installed -> PButton(tr("Open Shizuku", "Shizuku'yu aç")) { (listOf(SHIZUKU_PKG, "rikka.shizuku", "dev.rikka.shizuku").firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) })?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                else -> PButton(tr("Get Shizuku", "Shizuku'yu indir")) { Shell.askShizuku(ctx) }
            }
        }
    }
}

/** What an app can see of processes without root or Shizuku: its own, from ActivityManager (memory is PSS, CPU is not available). */
fun ownProcesses(ctx: Context): List<Proc> {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val list = am.runningAppProcesses.orEmpty()
    if (list.isEmpty()) return emptyList()
    val mem = runCatching { am.getProcessMemoryInfo(list.map { it.pid }.toIntArray()) }.getOrNull()
    return list.mapIndexed { i, p -> Proc(p.pid, p.processName, (mem?.getOrNull(i)?.totalPss ?: 0) / 1024, 0f, "app", "S") }
}


/**
 * Without root or Shizuku the other apps' processes cannot be read. The page then lists the apps the phone does let it see (the ones used in the last week,
 * or else every app with a launcher icon): their size and network speed are read for real, and the figures that need root (memory, CPU) are left blank, not invented.
 */
fun visibleApps(ctx: Context): List<Proc> {
    val pm = ctx.packageManager
    val launch = pm.queryIntentActivities(android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER), 0).map { it.activityInfo.packageName }.toSet()
    val recent = runCatching {
        if (!com.umutk.pulse.Extra.usageGranted(ctx)) return@runCatching emptyList<String>()
        val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        um.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_BEST, now - 7 * 86_400_000L, now)
            .filter { it.lastTimeUsed > 0 && it.packageName in launch }.sortedByDescending { it.lastTimeUsed }.map { it.packageName }
    }.getOrDefault(emptyList())
    val names = (recent + launch.sorted()).distinct().take(80)
    return names.map { Proc(0, it, 0, 0f, "", "") }
}

/** The last process list that was read, kept so the page has something to show when a read fails. */
object ProcCache {
    private fun sp(c: Context) = c.getSharedPreferences("proc_cache", Context.MODE_PRIVATE)
    fun save(c: Context, l: List<Proc>) {
        val a = org.json.JSONArray()
        l.take(250).forEach { p -> a.put(org.json.JSONArray(listOf(p.pid, p.name, p.rssMb, p.cpu.toDouble(), p.user, p.state, p.vszMb, p.nice, p.ppid))) }
        sp(c).edit().putString("list", a.toString()).apply()
    }
    fun load(c: Context): List<Proc> = runCatching {
        val a = org.json.JSONArray(sp(c).getString("list", "[]"))
        (0 until a.length()).map { i -> a.getJSONArray(i).let { Proc(it.getInt(0), it.getString(1), it.getInt(2), it.getDouble(3).toFloat(), it.getString(4), it.getString(5), it.getInt(6), it.getInt(7), it.getInt(8)) } }
    }.getOrDefault(emptyList())
}
