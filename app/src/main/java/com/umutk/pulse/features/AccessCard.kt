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
        installed -> tr("Shizuku: not running", "Shizuku: çalışmıyor")
        else -> tr("Shizuku: not installed", "Shizuku: yüklü değil")
    }
    PCard(modifier) {
        Text(feature, color = T.text, fontSize = 15.sp)
        if (limited.isNotBlank()) Text(limited, color = T.sub, fontSize = 13.sp)
        Text(if (rootTried) tr("Root: not available", "Root: yok") else tr("Root: not detected", "Root: algılanmadı"), color = T.sub, fontSize = 13.sp)
        Text(shizukuState, color = T.sub, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Check root", "Root'u dene")) { scope.launch { withContext(Dispatchers.IO) { Shell.detectRoot() }; rootTried = true } }
            when {
                Shell.shizuku == 1 -> PButton(tr("Allow Shizuku", "Shizuku'ya izin ver")) { Shell.askShizuku() }
                installed -> PButton(tr("Open Shizuku", "Shizuku'yu aç")) { (listOf(SHIZUKU_PKG, "rikka.shizuku", "dev.rikka.shizuku").firstNotNullOfOrNull { ctx.packageManager.getLaunchIntentForPackage(it) })?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                else -> PButton(tr("Get Shizuku", "Shizuku'yu indir")) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
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
