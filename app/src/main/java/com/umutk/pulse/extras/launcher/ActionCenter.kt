package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.content.IntentFilter
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AirplanemodeActive
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.ScreenLockRotation
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraToolActivity
import com.umutk.pulse.Shell
import com.umutk.pulse.tr

/** The Windows 10 Mobile action center: the quick tiles, "Collapse" and the notification area. It opens with a pull down from the top edge. */
object ActionCenter {
    var open by mutableStateOf(false)
    var torch by mutableStateOf(false)
}

internal class QTile(val icon: ImageVector, val en: String, val trName: String, val run: (Context) -> Unit)

private fun settings(a: String): (Context) -> Unit = { c -> runCatching { c.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

/** Flips airplane mode in place when the phone gives Pulse root or Shizuku; without them the settings page opens, as Android does not let an app switch it. */
private fun airplane(c: Context) {
    if (Shell.root != true && Shell.shizuku != 2) return settings(Settings.ACTION_AIRPLANE_MODE_SETTINGS)(c)
    val on = Settings.Global.getInt(c.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    Thread { Shell.run("cmd connectivity airplane-mode ${if (on) "disable" else "enable"}") }.start()
}

internal val QUICK = listOf(
    QTile(Icons.Outlined.SignalCellularAlt, "Cellular", "Hücresel", settings(Settings.ACTION_WIRELESS_SETTINGS)),
    QTile(Icons.Outlined.WifiTethering, "Mobile hotspot", "Mobil erişim noktası", settings("android.settings.TETHER_SETTINGS")),
    QTile(Icons.Outlined.NightsStay, "Quiet hours", "Sessiz saatler", settings(Settings.ACTION_SOUND_SETTINGS)),
    QTile(Icons.Outlined.BrightnessAuto, "Automatic", "Otomatik", settings(Settings.ACTION_DISPLAY_SETTINGS)),
    QTile(Icons.Outlined.VpnKey, "VPN", "VPN", settings(Settings.ACTION_VPN_SETTINGS)),
    QTile(Icons.Outlined.LocationOn, "Location", "Konum", settings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
    QTile(Icons.Outlined.AirplanemodeActive, "Airplane mode", "Uçak modu", ::airplane),
    QTile(Icons.Outlined.CameraAlt, "Camera", "Kamera", { c -> runCatching { c.startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }),
    QTile(Icons.Outlined.Cast, "Connect", "Bağlan", settings(Settings.ACTION_CAST_SETTINGS)),
    QTile(Icons.Outlined.BatterySaver, "Battery saver", "Pil tasarrufu", settings(Settings.ACTION_BATTERY_SAVER_SETTINGS)),
    QTile(Icons.Outlined.FlashlightOn, "Flashlight", "El feneri", { c ->
        runCatching {
            val m = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = m.cameraIdList.first()
            ActionCenter.torch = !ActionCenter.torch
            if (android.os.Build.VERSION.SDK_INT >= 23) m.setTorchMode(id, ActionCenter.torch)
        }
    }),
    QTile(Icons.Outlined.StickyNote2, "Note", "Not", { c -> runCatching { c.startActivity(Intent(c, ExtraToolActivity::class.java).putExtra("route", "notes").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }),
    QTile(Icons.Outlined.Wifi, "Wi-Fi", "Wi-Fi", settings(Settings.ACTION_WIFI_SETTINGS)),
    QTile(Icons.Outlined.Bluetooth, "Bluetooth", "Bluetooth", settings(Settings.ACTION_BLUETOOTH_SETTINGS)),
    QTile(Icons.Outlined.ScreenLockRotation, "Rotation lock", "Döndürme kilidi", settings(Settings.ACTION_DISPLAY_SETTINGS)),
    QTile(Icons.Outlined.Settings, "All settings", "Tüm ayarlar", settings(Settings.ACTION_SETTINGS)),
)

/** A strip along the top edge that opens the action center when it is pulled down. */
@Composable
fun ActionCenterHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(28.dp).pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total > 60f) ActionCenter.open = true }) { _, d -> total += d }
    })
}

/** The panel itself, over the whole screen; black, the grey tiles four to a row. */
@Composable
fun ActionCenterPanel() {
    if (!ActionCenter.open) return
    val c = LocalContext.current
    val teal = Color(0xFF00B7C3)
    val battery = remember(ActionCenter.open) {
        c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { i -> i.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(onDragStart = { total = 0f }, onDragEnd = { if (total < -60f) ActionCenter.open = false }) { _, d -> total += d }
    }.padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.End) { Text("$battery%", color = Color(0xFFBBBBBB), fontSize = 14.sp) }
        QUICK.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                row.forEach { t ->
                    val on = t.en == "Flashlight" && ActionCenter.torch
                    Column(Modifier.weight(1f).aspectRatio(1.1f).background(if (on) teal else Color(0xFF2D2D2D)).clickable { t.run(c) }.padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
                        Text(tr(t.en, t.trName), color = Color.White, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.End) {
            Text(tr("Collapse", "Daralt") + " ⌃", color = teal, fontSize = 16.sp, modifier = Modifier.clickable { ActionCenter.open = false }.padding(8.dp))
        }
        WpNotifications(c)
        Box(Modifier.fillMaxWidth().height(24.dp).background(teal).clickable { ActionCenter.open = false }, contentAlignment = Alignment.Center) {
            Text("═", color = Color.White, fontSize = 14.sp)
        }
    }
}

/**
 * The notifications of the Windows Phone action centre: the newest first, the app's name small above the title, a tap opens the app. They come from the
 * notification history, which keeps them while it is switched on (Settings > Notification history).
 */
@Composable
private fun ColumnScope.WpNotifications(c: Context) {
    val entries by com.umutk.pulse.extras.notifhist.NotifHist.entries.collectAsState()
    val on = com.umutk.pulse.extras.notifhist.NotifHist.enabled(c)
    if (!on) Text(tr("Notifications are not kept yet. Turn on Notification history in Settings.", "Bildirimler henüz tutulmuyor. Ayarlar'dan Bildirim geçmişini aç."),
        color = Color(0xFFCCCCCC), fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp, top = 16.dp))
    else if (entries.isEmpty()) Text(tr("No notifications", "Bildirim yok"), color = Color(0xFFCCCCCC), fontSize = 18.sp, modifier = Modifier.padding(start = 10.dp, top = 24.dp))
    else androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
        items(entries.take(40)) { e ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 10.dp).clickable {
                c.packageManager.getLaunchIntentForPackage(e.pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { c.startActivity(it) }
                ActionCenter.open = false
            }) {
                Text(e.app, color = Color(0xFF00B7C3), fontSize = 12.sp, maxLines = 1)
                if (e.title.isNotBlank()) Text(e.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.text.isNotBlank()) Text(e.text, color = Color(0xFFBBBBBB), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        item {
            Text(tr("Clear all", "Tümünü temizle"), color = Color(0xFF00B7C3), fontSize = 15.sp, modifier = Modifier.clickable { com.umutk.pulse.extras.notifhist.NotifHist.clear(c) }.padding(10.dp))
        }
    }
}
