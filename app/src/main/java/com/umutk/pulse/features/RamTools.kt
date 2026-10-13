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

/** RAM: total, available and used memory and a 60 s graph. */
@Composable
fun RamPage() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(ActivityManager::class.java) }
    val h by Sampler.history.collectAsState()
    var mi by remember { mutableStateOf<ActivityManager.MemoryInfo?>(null) }
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
        Spacer(Modifier.height(12.dp))
    }
}
