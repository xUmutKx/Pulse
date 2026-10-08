package com.umutk.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class Sub(val id: String, val label: String)

private val Subs = listOf(
    Sub("overview", "Overview"), Sub("system", "System"), Sub("cpu", "CPU"), Sub("memory", "Memory"), Sub("storage", "Storage"),
    Sub("battery", "Battery"), Sub("network", "Network"), Sub("thermal", "Thermal"), Sub("display", "Display"), Sub("camera", "Camera"),
    Sub("sensors", "Sensors"), Sub("media", "Media & DRM"), Sub("features", "Features"), Sub("props", "Properties"), Sub("bench", "Benchmark"),
)

/** Everything about the phone itself in one module, grouped like the parts of a computer: tabs along the top instead of a long menu. */
@Composable
fun DeviceModule() {
    var sub by rememberSaveable { mutableStateOf("overview") }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Subs.forEach { p ->
                val on = sub == p.id
                Column(Modifier.clickable { sub = p.id }.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    FitText(p.label, if (on) Look.accent else T.sub, 13.sp, weight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    Box(Modifier.padding(top = 4.dp).width(24.dp).height(2.dp).background(if (on) Look.accent else androidx.compose.ui.graphics.Color.Transparent))
                }
            }
        }
        Box(Modifier.weight(1f)) {
            when (sub) {
                "overview" -> DashboardPage()
                "system" -> LiveSections("System", 5000) { Hw.system(it) }
                "cpu" -> CoresPage()
                "memory" -> LiveSections("Memory", 2000) { Hw.memory(it) }
                "storage" -> StoragePage()
                "battery" -> BatteryPage()
                "network" -> NetworkPage()
                "thermal" -> ThermalPage()
                "display" -> DisplayPage()
                "camera" -> LiveSections("Camera", 0) { Hw.camera(it) }
                "sensors" -> SensorsPage()
                "media" -> LiveSections("Media & DRM", 0) { Hw.media() }
                "features" -> FeaturesPage()
                "props" -> PropsPage()
                else -> BenchPage()
            }
        }
    }
}
