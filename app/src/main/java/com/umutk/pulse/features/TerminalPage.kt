package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The last 20 commands, newest first. Kept in memory: survives moving between pages, not a restart. */
private val termHistory = mutableStateListOf<String>()

/** ADB-style shell commands for quick use: name, icon and command. Run on the phone's shell (root when available). */
private val presets = listOf(
    Triple("Model", "phone", "getprop ro.product.model"),
    Triple("Android", "info", "getprop ro.build.version.release"),
    Triple("Battery", "battery", "dumpsys battery"),
    Triple("Packages", "users", "pm list packages -3"),
    Triple("Top app", "processes", "dumpsys activity activities | grep -m 3 mResumedActivity"),
    Triple("Screen", "palette", "wm size; wm density"),
    Triple("Settings", "settings", "settings list global | head -n 40"),
    Triple("Log", "table", "logcat -d -t 100"),
)

/** Terminal: runs a command through Shell (root when available, otherwise the normal shell); Termux button sends it to Termux. */
@Composable
fun TerminalPage() {
    val scope = rememberCoroutineScope()
    var cmd by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var presetsOpen by remember { mutableStateOf(false) }
    // the installed Termux app's own icon; the Pulse run icon when Termux is missing
    val ctx = LocalContext.current
    val termuxIcon = remember { runCatching { ctx.packageManager.getApplicationIcon("com.termux").toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    var pending by remember { mutableStateOf("") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        out = if (ok && pending.isNotEmpty()) TermuxRun.send(ctx, pending) else "Termux permission was not granted."
    }
    fun termux() {
        val c = cmd.trim()
        if (c.isEmpty()) return
        if (!TermuxRun.granted(ctx)) { pending = c; permLauncher.launch(TermuxRun.PERM); return }
        out = TermuxRun.send(ctx, c)
    }
    fun go() {
        val c = cmd.trim()
        if (c.isEmpty() || busy) return
        busy = true
        termHistory.remove(c); termHistory.add(0, c)
        while (termHistory.size > 20) termHistory.removeAt(termHistory.lastIndex)
        scope.launch {
            val res = withContext(Dispatchers.IO) { Shell.run(c) }
            out = res.ifEmpty { "(no output)" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("Terminal", if (Shell.root == true) "root" else "", guide = "terminal")
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PField(cmd, { cmd = it }, "Command", Modifier.weight(1f), onDone = { go() })
                Box(Modifier.size(40.dp).clickable { go() }, contentAlignment = Alignment.Center) {
                    PIcon("run", Look.accent, 24.dp)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { presetsOpen = !presetsOpen }, verticalAlignment = Alignment.CenterVertically) {
                PIcon("chevron", T.sub, 16.dp, Modifier.rotate(if (presetsOpen) 90f else 0f))
                Spacer(Modifier.width(8.dp))
                Text("ADB commands", color = T.text, fontSize = 14.sp)
            }
            if (presetsOpen) presets.forEach { (name, icon, c) ->
                Row(Modifier.fillMaxWidth().clickable { cmd = c }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(icon, Look.accent, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(name, color = T.text, fontSize = 14.sp)
                        Text(c, color = T.sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { termux() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val ti = termuxIcon
                if (ti != null) Image(ti, null, Modifier.size(24.dp)) else PIcon("run", Look.accent, 24.dp)
                Spacer(Modifier.width(10.dp))
                Text("Run in Termux", color = T.text, fontSize = 14.sp)
            }
            Column(Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 320.dp)
                .background(if (T.dark) Color(0xFF0C0C0C) else Color(0xFFF4F4F4), RoundedCornerShape(T.radius))
                .verticalScroll(rememberScrollState()).padding(8.dp)) {
                Text(out, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
        }
        PCard {
            Text("History", color = T.sub, fontSize = 12.sp)
            termHistory.forEach { c ->
                Text(c, Modifier.fillMaxWidth().clickable { cmd = c }.padding(vertical = 4.dp), color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
