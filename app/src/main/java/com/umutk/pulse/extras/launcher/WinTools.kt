package com.umutk.pulse.extras.launcher

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PChip
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Windows administration tools drawn over what the phone really reports: Device Manager, Event Viewer, Disk Management and Windows Security. Each one is a window of the desktop
 * (win:devmgmt, win:eventvwr, win:diskmgmt, win:security), opened from the Run box (devmgmt.msc, eventvwr, diskmgmt.msc, windowsdefender), the Win+X menu and the tools search.
 */

private class Dev(val name: String, val props: List<Pair<String, String>>)
private class DevGroup(val title: String, val devices: List<Dev>)

private fun gb(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / (1L shl 30).toDouble()) else "%.0f MB".format(b / (1L shl 20).toDouble())

@SuppressLint("MissingPermission")
private fun deviceGroups(ctx: Context): List<DevGroup> {
    val out = ArrayList<DevGroup>()
    fun grp(title: String, f: () -> List<Dev>) { val l = runCatching(f).getOrDefault(emptyList()); if (l.isNotEmpty()) out += DevGroup(title, l) }
    grp(tr("Processors", "İşlemciler")) {
        val soc = if (Build.VERSION.SDK_INT >= 31) listOf(Build.SOC_MANUFACTURER, Build.SOC_MODEL).filter { it.isNotBlank() && it != "unknown" }.joinToString(" ") else Build.HARDWARE
        (0 until Runtime.getRuntime().availableProcessors()).map { i ->
            val maxKhz = runCatching { java.io.File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
            Dev("$soc · core $i", listOfNotNull("ABI" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "-"), maxKhz?.let { tr("Max speed", "En yüksek hız") to "%.2f GHz".format(it / 1e6) }))
        }
    }
    grp(tr("Display adapters", "Görüntü bağdaştırıcıları")) {
        val dm = ctx.resources.displayMetrics
        val d = (ctx.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager).getDisplay(0)
        val gl = (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).deviceConfigurationInfo.glEsVersion
        listOf(Dev(Build.HARDWARE.ifBlank { "GPU" }, listOf("OpenGL ES" to gl, tr("Resolution", "Çözünürlük") to "${dm.widthPixels} × ${dm.heightPixels}", tr("Density", "Yoğunluk") to "${dm.densityDpi} dpi",
            tr("Refresh rate", "Yenileme hızı") to "%.0f Hz".format(d.refreshRate), "HDR" to (if (d.isHdr) tr("Yes", "Evet") else tr("No", "Hayır")))))
    }
    grp(tr("Batteries", "Piller")) {
        val b = ctx.registerReceiver(null, IntentFilter(Intent_BATTERY))
        listOf(Dev(b?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Battery", listOf(tr("Level", "Düzey") to "${b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)}%",
            tr("Voltage", "Voltaj") to "%.2f V".format((b?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f), tr("Temperature", "Sıcaklık") to "%.1f °C".format((b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f),
            tr("Health", "Sağlık") to "${b?.getIntExtra(BatteryManager.EXTRA_HEALTH, 0)}")))
    }
    grp(tr("Sensors", "Sensörler")) {
        (ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getSensorList(Sensor.TYPE_ALL).map { s ->
            Dev(s.name, listOf(tr("Vendor", "Üretici") to s.vendor, tr("Type", "Tür") to s.stringType.substringAfterLast('.'), tr("Range", "Aralık") to "%.2f".format(s.maximumRange), tr("Power", "Güç") to "%.2f mA".format(s.power),
                tr("Resolution", "Çözünürlük") to "%.4f".format(s.resolution), tr("Fastest", "En hızlı") to (if (s.minDelay > 0) "%.0f Hz".format(1e6 / s.minDelay) else "-")))
        }
    }
    grp(tr("Cameras", "Kameralar")) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.map { id ->
            val c = cm.getCameraCharacteristics(id)
            val sz = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val face = when (c.get(CameraCharacteristics.LENS_FACING)) { CameraCharacteristics.LENS_FACING_FRONT -> tr("Front", "Ön"); CameraCharacteristics.LENS_FACING_BACK -> tr("Back", "Arka"); else -> tr("External", "Harici") }
            Dev("$face camera ($id)", listOfNotNull(sz?.let { tr("Sensor", "Sensör") to "%.1f MP".format(it.width * it.height / 1e6) },
                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull()?.let { tr("Focal length", "Odak uzaklığı") to "%.1f mm".format(it) },
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)?.let { tr("Flash", "Flaş") to (if (it) tr("Yes", "Evet") else tr("No", "Hayır")) }))
        }
    }
    grp(tr("Audio inputs and outputs", "Ses girişleri ve çıkışları")) {
        (ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_ALL).map { d ->
            Dev(d.productName.toString().ifBlank { "Audio" }, listOf(tr("Direction", "Yön") to (if (d.isSink) tr("Output", "Çıkış") else tr("Input", "Giriş")), tr("Type", "Tür") to audioType(d.type),
                "ID" to d.id.toString()))
        }
    }
    grp("Bluetooth") {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter?.bondedDevices?.map { Dev(it.name ?: it.address, listOf(tr("Address", "Adres") to it.address, tr("Paired", "Eşleşmiş") to tr("Yes", "Evet"))) } ?: emptyList()
    }
    grp(tr("Network adapters", "Ağ bağdaştırıcıları")) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.allNetworks.mapNotNull { n ->
            val c = cm.getNetworkCapabilities(n) ?: return@mapNotNull null
            val kind = when { c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"; c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> tr("Mobile network", "Mobil ağ"); c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "VPN"; c.hasTransport(android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"; else -> tr("Network", "Ağ") }
            Dev(kind, listOf(tr("Down", "İndirme") to "${c.linkDownstreamBandwidthKbps / 1000} Mbps", tr("Up", "Yükleme") to "${c.linkUpstreamBandwidthKbps / 1000} Mbps", tr("Internet", "İnternet") to (if (c.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)) tr("Yes", "Evet") else tr("No", "Hayır"))))
        }
    }
    grp("USB") {
        (ctx.getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager).deviceList.values.map { Dev(it.productName ?: it.deviceName, listOf(tr("Vendor ID", "Üretici kimliği") to "%04x".format(it.vendorId), tr("Product ID", "Ürün kimliği") to "%04x".format(it.productId))) }
    }
    return out
}

private const val Intent_BATTERY = "android.intent.action.BATTERY_CHANGED"

private fun audioType(t: Int) = when (t) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"; AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"; AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Microphone"
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headset"; AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"; AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"; AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"; else -> "Type $t"
}

/** Device Manager: the phone's hardware in groups; a device opens to show its properties. */
@Composable
fun DeviceManagerWindow() {
    WinGlass()
    val ctx = LocalContext.current
    val groups by produceState<List<DevGroup>?>(null) { value = withContext(Dispatchers.Default) { deviceGroups(ctx) } }
    var openGroup by remember { mutableStateOf(setOf<String>()) }
    var openDev by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(winPageBg()).verticalScroll(rememberScrollState()).padding(10.dp)) {
        Text(Build.MODEL, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        val g = groups
        if (g == null) Text(tr("Looking at the hardware…", "Donanım taranıyor…"), color = T.sub, fontSize = 13.sp)
        g?.forEach { grp ->
            val open = grp.title in openGroup
            Row(Modifier.fillMaxWidth().clickable { openGroup = if (open) openGroup - grp.title else openGroup + grp.title }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (open) "▾" else "▸", color = T.sub, fontSize = 13.sp, modifier = Modifier.width(18.dp))
                Text(grp.title, color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("${grp.devices.size}", color = T.sub, fontSize = 12.sp)
            }
            if (open) grp.devices.forEachIndexed { i, d ->
                val key = grp.title + i
                Column(Modifier.fillMaxWidth().padding(start = 22.dp).clickable { openDev = if (openDev == key) "" else key }.padding(vertical = 4.dp)) {
                    Text(d.name, color = T.text, fontSize = 13.sp)
                    if (openDev == key) Column(Modifier.padding(top = 4.dp, start = 8.dp)) {
                        d.props.forEach { (k, v) -> Row { Text(k, color = T.sub, fontSize = 12.sp, modifier = Modifier.width(120.dp)); Text(v, color = T.text, fontSize = 12.sp) } }
                    }
                }
            }
        }
    }
}

// ---- Event Viewer ---------------------------------------------------------------------------------------------------------

private class Ev(val time: Long, val level: Int, val source: String, val text: String)   // level: 0 information, 1 warning, 2 error

/** What the system tells about Pulse: how its processes ended (crash, not responding, memory, ...) and, with Shizuku or root, the system log's warnings and errors. */
private fun readEvents(ctx: Context): List<Ev> {
    val out = ArrayList<Ev>()
    runCatching {
        if (Build.VERSION.SDK_INT >= 30) {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getHistoricalProcessExitReasons(ctx.packageName, 0, 40).forEach { e ->
                val name = when (e.reason) {
                    android.app.ApplicationExitInfo.REASON_CRASH, android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "Crash"
                    android.app.ApplicationExitInfo.REASON_ANR -> "Not responding"
                    android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "Low memory"
                    android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "Closed by the user"
                    android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "Force stopped"
                    android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "Exited"
                    android.app.ApplicationExitInfo.REASON_SIGNALED -> "Killed (signal)"
                    android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "Dependency died"
                    else -> "Ended (${e.reason})"
                }
                val lvl = if (e.reason == android.app.ApplicationExitInfo.REASON_CRASH || e.reason == android.app.ApplicationExitInfo.REASON_CRASH_NATIVE || e.reason == android.app.ApplicationExitInfo.REASON_ANR) 2
                    else if (e.reason == android.app.ApplicationExitInfo.REASON_LOW_MEMORY || e.reason == android.app.ApplicationExitInfo.REASON_SIGNALED) 1 else 0
                out += Ev(e.timestamp, lvl, e.processName, name + (e.description?.let { " — $it" } ?: "") + " (pid ${e.pid}, status ${e.status})")
            }
        }
    }
    if (Shell.shizukuOk() || Shell.root == true) runCatching {
        val fmt = SimpleDateFormat("yyyy", Locale.US).format(Date())
        Shell.run("logcat -d -v threadtime -t 400 *:W").lines().forEach { l ->
            val m = Regex("""^(\d\d-\d\d)\s+(\d\d:\d\d:\d\d)\.\d+\s+\d+\s+\d+\s+([WEF])\s+(\S+)\s*:\s*(.*)$""").find(l) ?: return@forEach
            val t = runCatching { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse("$fmt-${m.groupValues[1]} ${m.groupValues[2]}")?.time }.getOrNull() ?: 0L
            out += Ev(t, if (m.groupValues[3] == "W") 1 else 2, m.groupValues[4], m.groupValues[5])
        }
    }
    return out.sortedByDescending { it.time }
}

/** Event Viewer: the events as a list with Information / Warning / Error; a row opens to show the whole text. */
@Composable
fun EventViewerWindow() {
    WinGlass()
    val ctx = LocalContext.current
    var reload by remember { mutableStateOf(0) }
    val events by produceState<List<Ev>?>(null, reload) { value = withContext(Dispatchers.IO) { readEvents(ctx) } }
    var filter by remember { mutableStateOf(-1) }
    var open by remember { mutableStateOf(-1L) }
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()) }
    Column(Modifier.fillMaxSize().background(winPageBg()).padding(10.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PChip(filter == -1, tr("All", "Hepsi")) { filter = -1 }
            PChip(filter == 2, tr("Error", "Hata")) { filter = 2 }
            PChip(filter == 1, tr("Warning", "Uyarı")) { filter = 1 }
            PChip(filter == 0, tr("Information", "Bilgi")) { filter = 0 }
            PChip(false, tr("Refresh", "Yenile")) { reload++ }
        }
        Spacer(Modifier.height(8.dp))
        val list = events
        if (list == null) Text(tr("Reading the log…", "Günlük okunuyor…"), color = T.sub, fontSize = 13.sp)
        else {
            val shown = list.filter { filter == -1 || it.level == filter }
            if (shown.isEmpty()) Text(tr("No events", "Olay yok"), color = T.sub, fontSize = 13.sp)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (!(Shell.shizukuOk() || Shell.root == true)) Text(tr("With Shizuku or root the system log shows here too.", "Shizuku veya root ile sistem günlüğü de burada görünür."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
                shown.take(300).forEach { e ->
                    Column(Modifier.fillMaxWidth().clickable { open = if (open == e.time) -1L else e.time }.padding(vertical = 5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(when (e.level) { 2 -> Color(0xFFD13438); 1 -> Color(0xFFFFB900); else -> Color(0xFF0078D4) }))
                            Text(fmt.format(Date(e.time)), color = T.sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 8.dp, end = 8.dp))
                            Text(e.source, color = T.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                        Text(e.text, color = T.text, fontSize = 12.sp, maxLines = if (open == e.time) 20 else 2, modifier = Modifier.padding(start = 18.dp, top = 2.dp))
                    }
                }
            }
        }
    }
}

// ---- Disk Management ------------------------------------------------------------------------------------------------------

/** Disk Management: the storage volumes (internal storage, SD card, USB drives) with how full each one is. */
@Composable
fun DiskManagementWindow() {
    WinGlass()
    val ctx = LocalContext.current
    val vols = remember {
        runCatching {
            val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            sm.storageVolumes.map { v ->
                val dir = if (Build.VERSION.SDK_INT >= 30) v.directory else null
                val st = dir?.takeIf { it.exists() }?.let { runCatching { StatFs(it.path) }.getOrNull() }
                listOf(v.getDescription(ctx), if (v.isPrimary) tr("Primary", "Birincil") else if (v.isRemovable) tr("Removable", "Çıkarılabilir") else tr("Fixed", "Sabit"), v.state, (st?.totalBytes ?: 0L).toString(), (st?.availableBytes ?: 0L).toString(), dir?.path ?: "")
            }
        }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize().background(winPageBg()).verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        vols.forEachIndexed { i, v ->
            val total = v[3].toLong(); val free = v[4].toLong()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(T.panel.copy(alpha = .3f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Disk $i · ${v[0]}", color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${v[1]} · ${v[2]}" + if (v[5].isNotEmpty()) " · ${v[5]}" else "", color = T.sub, fontSize = 11.sp)
                if (total > 0) {
                    Box(Modifier.fillMaxWidth().height(14.dp).background(T.sub.copy(alpha = .2f))) { Box(Modifier.fillMaxWidth(((total - free).toFloat() / total).coerceIn(.01f, 1f)).height(14.dp).background(Color(0xFF0078D4))) }
                    Text(tr("Used ", "Kullanılan ") + gb(total - free) + "  ·  " + tr("Free ", "Boş ") + gb(free) + "  ·  " + tr("Total ", "Toplam ") + gb(total), color = T.text, fontSize = 12.sp)
                }
            }
        }
        if (vols.isEmpty()) Text(tr("No volumes found", "Birim bulunamadı"), color = T.sub, fontSize = 13.sp)
    }
}

// ---- Windows Security -----------------------------------------------------------------------------------------------------

private class SecRow(val title: String, val value: String, val ok: Boolean)

/** Windows Security: what the phone says about its own protection, each line with a green tick or a warning. */
@Composable
fun WindowsSecurityWindow() {
    WinGlass()
    val ctx = LocalContext.current
    val rows = remember {
        val l = ArrayList<SecRow>()
        runCatching { l += (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure.let { SecRow(tr("Screen lock", "Ekran kilidi"), if (it) tr("A PIN, pattern or password is set", "PIN, desen veya parola kurulu") else tr("No screen lock", "Ekran kilidi yok"), it) } }
        runCatching { l += (ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager).storageEncryptionStatus.let { SecRow(tr("Device encryption", "Cihaz şifrelemesi"), if (it == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE || it == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER) tr("On", "Açık") else tr("Off or unknown", "Kapalı ya da bilinmiyor"), it >= DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE) } }
        runCatching {
            val patch = Build.VERSION.SECURITY_PATCH
            val days = ((System.currentTimeMillis() - (SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch)?.time ?: 0L)) / 86_400_000L)
            l += SecRow(tr("Security update", "Güvenlik güncellemesi"), "$patch ($days " + tr("days ago", "gün önce") + ")", days < 120)
        }
        runCatching { l += androidx.biometric.BiometricManager.from(ctx).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK).let { SecRow(tr("Fingerprint or face", "Parmak izi veya yüz"), if (it == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) tr("Ready", "Hazır") else tr("Not set up", "Kurulu değil"), it == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) } }
        runCatching { l += (Shell.root == true).let { SecRow(tr("Root access", "Root erişimi"), if (it) tr("This phone is rooted", "Bu telefon root'lu") else tr("Not rooted", "Root yok"), !it) } }
        l += SecRow(tr("Install unknown apps (Pulse)", "Bilinmeyen uygulama yükleme (Pulse)"), if (Build.VERSION.SDK_INT >= 26 && ctx.packageManager.canRequestPackageInstalls()) tr("Allowed", "İzinli") else tr("Not allowed", "İzinli değil"), Build.VERSION.SDK_INT < 26 || !ctx.packageManager.canRequestPackageInstalls())
        l
    }
    val good = rows.all { it.ok }
    Column(Modifier.fillMaxSize().background(winPageBg()).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(if (good) Color(0xFF107C10) else Color(0xFFFFB900)), contentAlignment = Alignment.Center) { Text(if (good) "✓" else "!", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Column(Modifier.padding(start = 12.dp)) {
                Text(if (good) tr("Your device is protected", "Cihazınız korunuyor") else tr("Action is recommended", "Önlem alınması önerilir"), color = T.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(tr("Security at a glance", "Bir bakışta güvenlik"), color = T.sub, fontSize = 12.sp)
            }
        }
        rows.forEach { r ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(T.panel.copy(alpha = .3f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (r.ok) "✓" else "!", color = if (r.ok) Color(0xFF107C10) else Color(0xFFFFB900), fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) { Text(r.title, color = T.text, fontSize = 14.sp); Text(r.value, color = T.sub, fontSize = 12.sp) }
            }
        }
    }
}
