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
            else Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) { WatchLinkRow(); com.umutk.pulse.features.WatchManagerTool() }
        }
    }
}

/** Pulse Watch link: the Bluetooth server the Pulse Watch app talks to (now playing, find my phone, the phone's files). */
@Composable
private fun WatchLinkRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var on by remember { androidx.compose.runtime.mutableStateOf(com.umutk.pulse.features.watch.WatchLink.enabled(ctx)) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { on = true; com.umutk.pulse.features.watch.WatchLink.setEnabled(ctx, true) }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { PIcon("phone", T.text, 22.dp) }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(tr("Pulse Watch link", "Pulse Watch bağlantısı"), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(tr("Music, find phone, files on the watch", "Saatte müzik, telefonu bul, dosyalar"), color = T.sub, fontSize = 12.sp)
        }
        com.umutk.pulse.PSwitch(on, { v ->
            if (v && android.os.Build.VERSION.SDK_INT >= 31 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                ask.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
            else { on = v; com.umutk.pulse.features.watch.WatchLink.setEnabled(ctx, v) }
        })
    }
}
