package com.umutk.pulse.features

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Cfg
import com.umutk.pulse.Center
import com.umutk.pulse.PButton
import com.umutk.pulse.PDialog
import com.umutk.pulse.Proc
import com.umutk.pulse.Sampler
import com.umutk.pulse.T
import com.umutk.pulse.labelOf
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class ProcRow(val proc: Proc, val label: String, val stoppable: Boolean)

/** Ps state letter to a word. */
private fun stateName(s: String): String = when (s.firstOrNull()) {
    'R' -> "Running"
    'S' -> "Sleeping"
    'D' -> "Waiting"
    'T', 't' -> "Stopped"
    'Z' -> "Zombie"
    'I' -> "Idle"
    null -> "-"
    else -> s.take(1)
}

@Composable
fun ProcessListScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<ProcRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<ProcRow?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            rows = withContext(Dispatchers.IO) {
                Sampler.processes().ifEmpty { ownProcesses(ctx) }.sortedByDescending { it.rssMb }.map { p ->
                    // never offer to stop this app's own processes
                    ProcRow(p, labelOf(ctx, p.name), stoppable = p.name.substringBefore(':') != ctx.packageName)
                }
            }
            loaded = true
            delay(3000)
        }
    }

    fun stop(r: ProcRow) {
        ask = null
        scope.launch {
            withContext(Dispatchers.IO) { Sampler.kill(r.proc) }
            rows = rows.filter { it.proc.pid != r.proc.pid }
        }
    }

    ask?.let { r ->
        PDialog("Stop ${r.label}?", "Unsaved data in this process will be lost. (PID ${r.proc.pid})", "Stop", { stop(r) }, { ask = null })
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PButton("Back") { onBack() }
            Spacer(Modifier.width(12.dp))
            Text("Processes", color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Name", color = T.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("Memory", color = T.sub, fontSize = 12.sp, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
            Text("State", color = T.sub, fontSize = 12.sp, modifier = Modifier.width(84.dp).padding(start = 8.dp))
            Spacer(Modifier.width(72.dp))
        }
        AccessNeededCard(tr("Processes", "İşlemler"), tr("Showing this app's own processes only. Other apps' processes need root or Shizuku.", "Yalnızca bu uygulamanın kendi işlemleri gösteriliyor. Diğer uygulamaların işlemleri için root veya Shizuku gerekir."), Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        if (loaded && rows.isEmpty()) {
            Center(tr("No processes could be read", "İşlemler okunamadı"))
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.proc.pid }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.proc.name, color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${r.proc.rssMb} MB", color = T.text, fontSize = 13.sp, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
                        Text(stateName(r.proc.state), color = T.sub, fontSize = 13.sp, modifier = Modifier.width(84.dp).padding(start = 8.dp))
                        if (r.stoppable) {
                            PButton("Stop", danger = true) { if (Cfg.confirmKill) ask = r else stop(r) }
                        } else {
                            Spacer(Modifier.width(72.dp))
                        }
                    }
                }
            }
        }
    }
}
