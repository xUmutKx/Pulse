package com.umutk.pulse.extras.buds

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr

/** Device manager: the Galaxy Buds manager and the watch manager (install and manage apps on a watch over Wi-Fi) as the two pages of one tool. */
@Composable
fun DeviceManagerScreen(back: () -> Unit) {
    BackHandler { back() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = pageGutter, end = pageGutter, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Device manager", "Cihaz yöneticisi"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(horizontal = pageGutter, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(tab == 0, tr("Buds", "Buds")) { tab = 0 }
            PChip(tab == 1, tr("Watch", "Saat")) { tab = 1 }
        }
        Box(Modifier.fillMaxSize()) {
            if (tab == 0) BudsScreen(back, embedded = true)
            else Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) { com.umutk.pulse.features.WatchManagerTool() }
        }
    }
}
