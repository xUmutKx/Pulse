package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.DetailsPage
import com.umutk.pulse.HistoryPage
import com.umutk.pulse.Look
import com.umutk.pulse.PIcon
import com.umutk.pulse.PerformancePage
import com.umutk.pulse.ProcessesPage
import com.umutk.pulse.ServicesPage
import com.umutk.pulse.StartupPage
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** The pages of Task Manager, in the order Windows lists them. */
private val TM_PAGES = listOf("processes", "performance", "history", "startup", "details", "services")

private fun pageName(id: String) = when (id) {
    "processes" -> tr("Processes", "İşlemler"); "performance" -> tr("Performance", "Performans"); "history" -> tr("App history", "Uygulama geçmişi")
    "startup" -> tr("Startup apps", "Başlangıç uygulamaları"); "details" -> tr("Details", "Ayrıntılar"); else -> tr("Services", "Hizmetler")
}

/**
 * Task Manager as a window of the desktop, with its real pages: Processes, Performance (the CPU, memory, disk and network graphs), App history, Startup apps, Details and Services.
 * Windows 11 has its slim icon rail on the left; the other looks have the tabs along the top (raised and bordered in 95, XP and 7, a line under the open one in 10).
 */
@Composable
fun TaskManagerWindow() {
    var page by rememberSaveable { mutableStateOf("processes") }
    val body: @Composable () -> Unit = {
        when (page) {
            "performance" -> PerformancePage(); "history" -> HistoryPage(); "startup" -> StartupPage()
            "details" -> DetailsPage(); "services" -> ServicesPage(); else -> ProcessesPage()
        }
    }
    if (T.id == "win11" || T.material) {
        // the rail: an icon for each page, the open one marked with the accent bar at its edge, as in Windows 11
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(46.dp).fillMaxHeight().background(T.panel.copy(alpha = .35f)).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TM_PAGES.forEach { id ->
                    val on = page == id
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(if (on) Look.accent.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent).clickable { page = id }, contentAlignment = Alignment.Center) {
                        if (on) Box(Modifier.align(Alignment.CenterStart).width(3.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(Look.accent))
                        PIcon(id, if (on) Look.accent else T.sub, 22.dp)
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxHeight()) { body() }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        // the tabs along the top
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
            TM_PAGES.forEach { id ->
                val on = page == id
                val classic = T.id == "win95" || T.id == "xp" || T.id == "win7"
                Row(
                    Modifier.then(if (classic) Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(if (on) T.panel else T.bg).border(1.dp, T.line, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)) else Modifier)
                        .clickable { page = id }.padding(horizontal = 10.dp, vertical = if (on && classic) 8.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PIcon(id, if (on) Look.accent else T.sub, 16.dp)
                    Text(pageName(id), color = if (on) T.text else T.sub, fontSize = 12.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
        if (T.id == "win10") Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
        Box(Modifier.weight(1f).fillMaxWidth()) { body() }
    }
}
