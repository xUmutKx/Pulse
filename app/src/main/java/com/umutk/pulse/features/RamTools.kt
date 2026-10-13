package com.umutk.pulse.features

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.BarMeter
import com.umutk.pulse.Graph
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.Sampler
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.mb
import com.umutk.pulse.resColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Background apps a "free memory" may stop: (package, pid). Not Pulse, not the launcher, not system apps. */
private fun background(ctx: Context): List<Pair<String, Int>> {
    val pm = ctx.packageManager
    val home = pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    val procs = ctx.getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty()
        .filter { it.importance > RunningAppProcessInfo.IMPORTANCE_FOREGROUND }
    return procs.asSequence()
        .map { it.processName.substringBefore(':') to it.pid }
        .filter { (pkg, _) -> pkg.matches(Regex("[A-Za-z0-9_.]+")) && pkg != ctx.packageName && pkg != home }
        .filter { (pkg, _) -> runCatching { (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) == 0 }.getOrDefault(false) }
        .distinctBy { it.first }
        .toList()
}

/** RAM: total, available and used memory, a 60 s graph, and a "free memory" action (stops background apps when root is available). */
@Composable
fun RamPage() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(ActivityManager::class.java) }
    val h by Sampler.history.collectAsState()
    val scope = rememberCoroutineScope()
    var mi by remember { mutableStateOf<ActivityManager.MemoryInfo?>(null) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) { mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }; delay(2000) }
    }
    val m = mi
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("RAM", if (Shell.root == true) "root" else "")
        PCard {
            if (m == null) Text("Reading...", color = T.sub, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            else {
                val used = m.totalMem - m.availMem
                Text("${used * 100 / m.totalMem}% used", Modifier.fillMaxWidth(), color = T.text, fontSize = 28.sp, textAlign = TextAlign.Center)
                BarMeter(used.toFloat() / m.totalMem, Look.accent)
                InfoRows(listOf("Total" to mb((m.totalMem shr 20).toInt()), "Used" to mb((used shr 20).toInt()), "Available" to mb((m.availMem shr 20).toInt())))
            }
        }
        PCard {
            Text("Memory history (60 s)", color = T.sub, fontSize = 12.sp)
            Graph(listOf(h["ram"].orEmpty()), listOf(resColor("memory")), 100f, height = 70.dp)
        }
        PCard {
            Text("Free memory", color = Look.accent, fontSize = 15.sp)
            PButton(if (busy) "Freeing…" else "Free memory") {
                if (!busy) scope.launch {
                    busy = true
                    val apps = withContext(Dispatchers.IO) { background(ctx) }
                    if (Shell.root == true) {
                        val before = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
                        if (apps.isNotEmpty()) withContext(Dispatchers.IO) { Shell.run(apps.joinToString("; ") { "am force-stop ${it.first}" }) }
                        delay(1000)
                        val after = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
                        note = "Stopped ${apps.size} apps, freed ${mb(((after - before).coerceAtLeast(0L) shr 20).toInt())}"
                    } else {
                        // without root nothing is stopped: only the amount that could be freed is shown
                        val pss = withContext(Dispatchers.IO) { am.getProcessMemoryInfo(apps.map { it.second }.toIntArray()).sumOf { it.totalPss.toLong() } }
                        note = "${apps.size} background apps could free about ${mb((pss shr 10).toInt())}. Root is needed to stop them."
                    }
                    busy = false
                }
            }
            if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        }
        Spacer(Modifier.height(12.dp))
    }
}
