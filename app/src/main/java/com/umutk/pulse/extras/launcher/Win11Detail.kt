package com.umutk.pulse.extras.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** One line of a settings page: a label and its value. */
private class DRow(val label: String, val value: String)

/** The page of one Windows 11 setting inside Pulse: its title, what the phone says about it right now, the controls Pulse can work itself, and the controls that really work from inside Pulse. */
@Composable
internal fun Win11Detail(item: WItem, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rows = remember(item) { rowsFor(ctx, item) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().clickable { onBack() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("←", color = T.text, fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text(tr("Settings", "Ayarlar"), color = T.text, fontSize = 16.sp)
        }
        Row(Modifier.padding(top = 6.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(item.icon, T.accent, 30.dp)
            Spacer(Modifier.width(14.dp))
            Text(tr(item.en, item.tr), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(tr(item.enDesc, item.trDesc), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp))
        val shape = RoundedCornerShape(8.dp)
        if (rows.isNotEmpty()) Column(Modifier.fillMaxWidth().clip(shape).background(T.panel.copy(alpha = .26f)).border(1.dp, Color.White.copy(alpha = .08f), shape)) {
            rows.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.label, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(r.value, color = T.sub, fontSize = 14.sp)
                }
                if (i < rows.lastIndex) Box1()
            }
        }
        Spacer(Modifier.height(12.dp))
        when (item.en) {
            "Sound" -> VolumeSliders(ctx)
            "Display" -> BrightnessSlider(ctx)
        }
        Spacer(Modifier.height(12.dp))
        Win11Controls(ctx, item.route.removePrefix("intent:"))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Box1() { Spacer(Modifier.fillMaxWidth().height(1.dp).padding(start = 14.dp).background(T.sub.copy(alpha = .12f))) }

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(T.panel.copy(alpha = .26f)).border(1.dp, Color.White.copy(alpha = .08f), shape).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun VolumeSliders(ctx: Context) {
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    Section(tr("Volume", "Ses düzeyi")) {
        listOf(AudioManager.STREAM_MUSIC to tr("Media", "Medya"), AudioManager.STREAM_RING to tr("Ringtone", "Zil sesi"), AudioManager.STREAM_NOTIFICATION to tr("Notifications", "Bildirimler"), AudioManager.STREAM_ALARM to tr("Alarm", "Alarm")).forEach { (s, label) ->
            val max = am.getStreamMaxVolume(s).coerceAtLeast(1)
            var v by remember { mutableStateOf(am.getStreamVolume(s).toFloat()) }
            Text("$label  ·  ${(v * 100 / max).toInt()}%", color = T.sub, fontSize = 12.sp)
            Slider(v, { v = it; runCatching { am.setStreamVolume(s, it.toInt(), 0) } }, valueRange = 0f..max.toFloat(), steps = (max - 1).coerceAtLeast(0), colors = SliderDefaults.colors(thumbColor = T.accent, activeTrackColor = T.accent))
        }
    }
}

@Composable
private fun BrightnessSlider(ctx: Context) {
    val can = remember { Settings.System.canWrite(ctx) }
    Section(tr("Brightness", "Parlaklık")) {
        if (!can) {
            Text(tr("Pulse may change the brightness once it is allowed to modify system settings.", "Pulse, sistem ayarlarını değiştirme izni verilince parlaklığı değiştirebilir."), color = T.sub, fontSize = 12.sp)
            PButton(tr("Allow", "İzin ver")) { runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).setData(android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        } else {
            var v by remember { mutableStateOf(runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrDefault(128).toFloat()) }
            Text("${(v * 100 / 255).toInt()}%", color = T.sub, fontSize = 12.sp)
            Slider(v, { v = it; runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL); Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, it.toInt().coerceIn(1, 255)) } }, valueRange = 1f..255f, colors = SliderDefaults.colors(thumbColor = T.accent, activeTrackColor = T.accent))
        }
    }
}

@SuppressLint("MissingPermission")
private fun rowsFor(ctx: Context, item: WItem): List<DRow> {
    val on = tr("On", "Açık"); val off = tr("Off", "Kapalı")
    fun gi(name: String) = runCatching { Settings.Global.getInt(ctx.contentResolver, name) }.getOrDefault(0)
    return runCatching {
        when (item.en) {
            "Wi-Fi", "Mobile network", "Mobile hotspot", "VPN" -> {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
                val caps = cm.getNetworkCapabilities(cm.activeNetwork)
                val wifi = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                val cell = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true
                val vpn = caps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                when (item.en) {
                    "Wi-Fi" -> listOf(DRow(tr("Connection", "Bağlantı"), if (wifi) tr("Connected", "Bağlı") else tr("Not connected", "Bağlı değil")),
                        DRow(tr("Speed down", "İndirme hızı"), if (wifi) "${(caps?.linkDownstreamBandwidthKbps ?: 0) / 1000} Mbps" else "-"))
                    "Mobile network" -> listOf(DRow(tr("Mobile data", "Mobil veri"), if (cell) on else off), DRow(tr("Roaming", "Dolaşım"), if (gi(Settings.Global.DATA_ROAMING) == 1) on else off))
                    "VPN" -> listOf(DRow("VPN", if (vpn) tr("Connected", "Bağlı") else tr("Not connected", "Bağlı değil")))
                    else -> emptyList()
                }
            }
            "Airplane mode" -> listOf(DRow(tr("Airplane mode", "Uçak modu"), if (gi(Settings.Global.AIRPLANE_MODE_ON) == 1) on else off))
            "Bluetooth" -> {
                val ad = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                val list = mutableListOf(DRow("Bluetooth", if (ad?.isEnabled == true) on else off))
                ad?.bondedDevices?.take(8)?.forEach { list += DRow(it.name ?: it.address, tr("Paired", "Eşleşmiş")) }
                list
            }
            "Power & battery" -> {
                val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val lvl = (b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / (b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
                val st = b?.getIntExtra(BatteryManager.EXTRA_STATUS, 0) ?: 0
                val ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
                listOf(DRow(tr("Battery level", "Pil düzeyi"), "$lvl%"), DRow(tr("Charging", "Şarj"), if (ch) on else off),
                    DRow(tr("Temperature", "Sıcaklık"), "%.1f °C".format((b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f)))
            }
            "Storage" -> {
                val s = StatFs(Environment.getDataDirectory().path)
                val gb = 1073741824.0
                listOf(DRow(tr("Total", "Toplam"), "%.1f GB".format(s.totalBytes / gb)), DRow(tr("Free", "Boş"), "%.1f GB".format(s.availableBytes / gb)))
            }
            "Display" -> {
                val m = ctx.resources.displayMetrics
                listOf(DRow(tr("Resolution", "Çözünürlük"), "${m.widthPixels} × ${m.heightPixels}"), DRow(tr("Density", "Yoğunluk"), "${m.densityDpi} dpi"))
            }
            "Focus" -> listOf(DRow(tr("Do not disturb", "Rahatsız etme"), when (gi("zen_mode")) { 0 -> off; else -> on }))
            "Cameras" -> emptyList()
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

/** The top of Network & internet: whether the phone is connected, as the card at the top of that page in Windows. */
@Composable
internal fun Win11NetCard() {
    val ctx = LocalContext.current
    val (title, sub) = remember {
        runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            when {
                caps == null -> tr("Not connected", "Bağlı değil") to tr("You are not connected to any network", "Hiçbir ağa bağlı değilsiniz")
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi" to tr("Connected", "Bağlı")
                caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> tr("Mobile network", "Mobil ağ") to tr("Connected", "Bağlı")
                else -> tr("Connected", "Bağlı") to ""
            }
        }.getOrDefault(tr("Not connected", "Bağlı değil") to "")
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon("globe", T.accent, 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 13.sp)
        }
    }
}

/** The top of Bluetooth & devices: the paired devices as cards and a card to add one. */
@SuppressLint("MissingPermission")
@Composable
internal fun Win11BtCards() {
    val ctx = LocalContext.current
    val devices = remember { runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.bondedDevices?.map { (it.name ?: it.address) to it.bluetoothClass?.majorDeviceClass }.orEmpty() }.getOrDefault(emptyList()) }
    val shape = RoundedCornerShape(8.dp)
    val cards: List<Pair<String, Int?>> = devices.map { it.first to it.second } + listOf("" to -1)
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, cls) ->
                    val add = cls == -1
                    Column(Modifier.weight(1f).height(104.dp).clip(shape).background(T.panel.copy(alpha = .26f)).border(1.dp, Color.White.copy(alpha = .08f), shape)
                        .clickable { runCatching { ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        // the kind of device picks the picture: audio (headphones), phone, computer, peripheral (controller / keyboard), else a plain device
                        val icon = when {
                            add -> "add"
                            cls == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> "voice"
                            cls == android.bluetooth.BluetoothClass.Device.Major.PHONE -> "phone"
                            cls == android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> "cpu"
                            cls == android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> "pointer"
                            else -> "sensors"
                        }
                        PIcon(icon, T.accent, 30.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(if (add) tr("Add device", "Cihaz ekle") else name, color = T.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (!add) Text(tr("Paired", "Eşleşmiş"), color = T.sub, fontSize = 11.sp)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
