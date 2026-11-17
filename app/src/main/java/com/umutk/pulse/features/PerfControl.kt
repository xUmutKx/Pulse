package com.umutk.pulse.features

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The performance controller: Pulse's own processes (each extra app runs in one of its own) with their memory and a Stop, the switches that make
 * Pulse lighter, and, with root or Shizuku, the other apps running in the background with their memory, each stopped by itself or all at once.
 */
object PerfControl {
    class Proc(val pid: Int, val name: String, val kb: Long)
    class AppRun(val pkg: String, val label: String, val kb: Long)

    /** Pulse's processes now running, with their memory (PSS, kB). */
    fun pulseProcs(ctx: Context): List<Proc> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mine = am.runningAppProcesses.orEmpty().filter { it.uid == android.os.Process.myUid() }
        val mem = runCatching { am.getProcessMemoryInfo(mine.map { it.pid }.toIntArray()) }.getOrNull()
        return mine.mapIndexed { i, p -> Proc(p.pid, p.processName, mem?.getOrNull(i)?.totalPss?.toLong() ?: 0L) }.sortedByDescending { it.kb }
    }

    /** Stops one of Pulse's processes (never the one this page runs in). */
    fun stopProc(p: Proc) { if (p.pid != android.os.Process.myPid()) android.os.Process.killProcess(p.pid) }

    /** The packages that must never be stopped from here: Pulse, the keyboard and the home screen in use, and the phone's core. */
    private fun keep(ctx: Context): Set<String> {
        val ime = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty().substringBefore('/')
        val home = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName.orEmpty()
        return setOf(ctx.packageName, ime, home, "android", "com.android.systemui", "com.android.phone", "moe.shizuku.privileged.api", "com.topjohnwu.magisk")
    }

    /** The installed (not system) apps running now, the biggest first (needs root or Shizuku: `ps` shows only Pulse's own otherwise). */
    fun otherApps(ctx: Context): List<AppRun> {
        val pm = ctx.packageManager
        val user = pm.getInstalledApplications(0).filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 }.associateBy { it.packageName }
        val skip = keep(ctx)
        val sums = HashMap<String, Long>()
        Shell.run("ps -A -o RSS,NAME").lineSequence().drop(1).forEach { line ->
            val parts = line.trim().split(Regex("\\s+"), 2)
            if (parts.size == 2) {
                val pkg = parts[1].substringBefore(':')
                val kb = parts[0].toLongOrNull() ?: 0L
                if (pkg in user && pkg !in skip) sums[pkg] = (sums[pkg] ?: 0L) + kb
            }
        }
        return sums.map { (p, kb) -> AppRun(p, runCatching { pm.getApplicationLabel(user.getValue(p)).toString() }.getOrDefault(p), kb) }.sortedByDescending { it.kb }
    }

    fun forceStop(pkg: String) { Shell.run("am force-stop $pkg") }

    /** Every background process the system may kill without harm (what the system does under low memory). */
    fun killBackground() { Shell.run("am kill-all") }

    fun mb(kb: Long): String = if (kb >= 1024 * 1024) "%.1f GB".format(kb / 1048576f) else "${kb / 1024} MB"
}

@Composable
fun PerfControlTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var procs by remember { mutableStateOf(emptyList<PerfControl.Proc>()) }
    var apps by remember { mutableStateOf<List<PerfControl.AppRun>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val power = remember { Shell.shizukuOk() || Shell.root == true }
    fun reload() = scope.launch {
        busy = true
        procs = withContext(Dispatchers.IO) { PerfControl.pulseProcs(ctx) }
        if (power) apps = withContext(Dispatchers.IO) { runCatching { PerfControl.otherApps(ctx) }.getOrDefault(emptyList()) }
        busy = false
    }
    LaunchedEffect(Unit) { reload() }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Pulse's own processes
        PCard {
            Text("Pulse", color = com.umutk.pulse.Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            procs.forEach { p ->
                val main = p.pid == android.os.Process.myPid()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PIcon("cpu", T.sub, 20.dp)
                    Column(Modifier.weight(1f)) {
                        Text(p.name.substringAfter(':', tr("main", "ana")), color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PerfControl.mb(p.kb), color = T.sub, fontSize = 12.sp)
                    }
                    if (!main) PButton(tr("Stop", "Durdur"), compact = true, danger = true) { PerfControl.stopProc(p); reload() }
                }
            }
            if (procs.count { it.pid != android.os.Process.myPid() } > 1) PButton(tr("Stop all extra apps", "Tüm ek uygulamaları durdur")) { procs.forEach { PerfControl.stopProc(it) }; reload() }
        }
        // the switches that make Pulse lighter
        PCard {
            Text(tr("Lighter", "Daha hafif"), color = com.umutk.pulse.Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            var smooth by remember { mutableStateOf(com.umutk.pulse.extras.keyboard.KeyboardPrefs.smooth) }
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Keyboard smooth mode", "Klavye akıcı mod"), color = T.text, fontSize = 14.sp)
                    Text(tr("No animations, effects, RGB light or neural weighing", "Animasyon, efekt, RGB ışık ve sinirsel tartım yok"), color = T.sub, fontSize = 12.sp)
                }
                PSwitch(smooth, { smooth = it; com.umutk.pulse.extras.keyboard.KeyboardPrefs.smooth = it; com.umutk.pulse.extras.keyboard.KeyboardPrefs.save(ctx) })
            }
        }
        // other apps (root or Shizuku)
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Other apps", "Diğer uygulamalar"), color = com.umutk.pulse.Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (power) PButton(tr("Refresh", "Yenile"), compact = true) { reload() }
            }
            if (!power) {
                Text(tr("Needs root or Shizuku", "Root ya da Shizuku gerekir"), color = T.sub, fontSize = 13.sp)
                PButton("Shizuku", compact = true) { Shell.askShizuku(ctx) }
                if (Shell.shizukuMsg.isNotEmpty()) Text(Shell.shizukuMsg, color = T.sub, fontSize = 12.sp)
            } else {
                val list = apps
                if (list == null || busy && list.isEmpty()) Text("…", color = T.sub)
                else {
                    Text(tr("${list.size} running · ${PerfControl.mb(list.sumOf { it.kb })}", "${list.size} çalışıyor · ${PerfControl.mb(list.sumOf { it.kb })}"), color = T.sub, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PButton(tr("Stop all", "Hepsini durdur"), compact = true, danger = true) { scope.launch { withContext(Dispatchers.IO) { list.forEach { PerfControl.forceStop(it.pkg) } }; reload() } }
                        PButton(tr("Free memory", "Belleği boşalt"), compact = true) { scope.launch { withContext(Dispatchers.IO) { PerfControl.killBackground() }; reload() } }
                    }
                    list.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(a.label, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(PerfControl.mb(a.kb), color = T.sub, fontSize = 12.sp)
                            }
                            PButton(tr("Stop", "Durdur"), compact = true) { scope.launch { withContext(Dispatchers.IO) { PerfControl.forceStop(a.pkg) }; reload() } }
                        }
                    }
                }
            }
        }
    }
}
