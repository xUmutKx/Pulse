package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.T
import com.umutk.pulse.tr

/**
 * One icon of the Windows 7 notification area: [key] names it (and its picture, assets/icons/win7/<pic>.webp), [always] ones stand in the bar and are always there, the others
 * live in the arrow's flyout and are there only while the thing they stand for is really going on ([active]).
 */
internal class TrayItem(val key: String, val pic: String, val en: String, val trName: String, val always: Boolean, val active: (Context) -> Boolean, val act: (Context) -> Unit)

private fun settings(c: Context, action: String) { runCatching { c.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

internal val TRAY_ITEMS = listOf(
    TrayItem("action", "tray_flag", "Action Center", "Eylem Merkezi", true, { true }) { openDesktopItem(it, "pulse:x:notifhist", "win7") },
    TrayItem("network", "tray_net", "Network", "Ağ", true, { true }) { settings(it, android.provider.Settings.ACTION_WIFI_SETTINGS) },
    TrayItem("volume", "tray_vol", "Volume", "Ses", true, { true }) { settings(it, "android.settings.panel.action.VOLUME") },
    // MSN stays in the bar once it was opened, as if it ran in the background
    TrayItem("msn", "tray_msn", "MSN Messenger", "MSN Messenger", true, { LauncherSession.msnTray }) { openDesktopItem(it, "pulse:x:messages", "win7") },
    // the hidden icons: each is there only while it is true
    TrayItem("music", "l_music", "Windows Media Player (playing)", "Windows Media Player (çalıyor)", false, { runCatching { (it.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager).isMusicActive }.getOrDefault(false) }) { openDesktopItem(it, "pulse:x:music", "win7") },
    TrayItem("downloads", "tray_update", "Downloads (running)", "İndirmeler (sürüyor)", false, { com.umutk.pulse.features.Downloads.tasks.value.any { t -> t.state.value == com.umutk.pulse.features.DlState.RUNNING } }) { c -> runCatching { c.startActivity(com.umutk.pulse.features.Downloads.openPage(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } },
    TrayItem("firewall", "tray_fw", "Network protection (VPN on)", "Ağ koruması (VPN açık)", false, { c ->
        runCatching { val cm = c.getSystemService(android.net.ConnectivityManager::class.java); cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true }.getOrDefault(false)
    }) { settings(it, android.provider.Settings.ACTION_VPN_SETTINGS) },
    TrayItem("bluetooth", "tray_bt", "Bluetooth (on)", "Bluetooth (açık)", false, { c -> runCatching { android.provider.Settings.Global.getInt(c.contentResolver, "bluetooth_on", 0) == 1 }.getOrDefault(false) }) { settings(it, android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) },
    TrayItem("storage", "tray_usb", "Removable storage", "Çıkarılabilir depolama", false, { c ->
        runCatching { c.getSystemService(android.os.storage.StorageManager::class.java).storageVolumes.any { it.isRemovable && it.state == android.os.Environment.MEDIA_MOUNTED } }.getOrDefault(false)
    }) { openDesktopItem(it, "pulse:files", "win7") },
)

/** Control Panel > Notification Area Icons: every icon with a switch for showing it (the hidden ones show only while what they stand for is going on). */
@Composable
fun TrayIconsPage() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
        Text(tr("Select which icons appear on the taskbar", "Görev çubuğunda hangi simgelerin görüneceğini seç"), color = T.text, fontSize = 16.sp)
        Text(tr("The icons in the arrow's flyout show only while what they stand for is really going on: music playing, a download running, a VPN, Bluetooth, removable storage.", "Okun açtığı kutudaki simgeler yalnızca temsil ettikleri şey gerçekten sürerken görünür: çalan müzik, süren indirme, VPN, Bluetooth, çıkarılabilir depolama."), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        TRAY_ITEMS.forEach { it2 ->
            val on = it2.key !in LauncherPrefs.trayHidden
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TrayPic(it2.pic, 24.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(it2.en, it2.trName), color = T.text, fontSize = 14.sp)
                    Text(if (it2.always) tr("In the bar", "Çubukta") else tr("In the flyout while it is going on", "Sürerken kutuda"), color = T.sub, fontSize = 11.sp)
                }
                com.umutk.pulse.PSwitch(on, { v -> LauncherPrefs.trayHidden = if (v) LauncherPrefs.trayHidden - it2.key else LauncherPrefs.trayHidden + it2.key; LauncherPrefs.save(ctx) })
            }
        }
    }
}

@Composable
internal fun TrayPic(key: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val b = com.umutk.pulse.features.ThemeIcons.bitmap(ctx, "win7", key)
    if (b != null) androidx.compose.foundation.Image(b, null, Modifier.size(size))
}
