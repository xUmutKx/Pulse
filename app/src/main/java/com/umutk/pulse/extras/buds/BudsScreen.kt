package com.umutk.pulse.extras.buds

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.extras.ui.SettingsSection
import com.umutk.pulse.tr

/** The Galaxy Buds manager: pick the earbuds, see the battery and which bud is in the ear, change the noise control, and let them stop the music when taken out. */
@Composable
fun BudsScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { back() }
    val s by BudsLink.state.collectAsState()
    var address by remember { mutableStateOf(BudsPrefs.address(ctx)) }
    var granted by remember { mutableStateOf(Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r -> granted = r[Manifest.permission.BLUETOOTH_CONNECT] != false }
    var pause by remember { mutableStateOf(BudsPrefs.pauseOnRemove(ctx)) }
    var noiseOff by remember { mutableStateOf(BudsPrefs.noiseOffOnRemove(ctx)) }
    var resume by remember { mutableStateOf(BudsPrefs.resumeOnWear(ctx)) }
    var keep by remember { mutableStateOf(BudsPrefs.keepOn(ctx)) }
    // every paired earphone / headset, the Galaxy Buds first (a renamed pair has no "Buds" in its name)
    val devices = remember(granted) {
        if (!granted) emptyList() else runCatching {
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices.orEmpty().filter { d ->
                val n = runCatching { d.name }.getOrNull() ?: ""
                val major = runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()
                n.contains("Buds", true) || n.contains("Galaxy", true) || major == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
            }.sortedByDescending { (runCatching { it.name }.getOrNull() ?: "").contains("Buds", true) }
                .map { it.address to (runCatching { it.name }.getOrNull() ?: it.address) }
        }.getOrDefault(emptyList())
    }
    // the only pair is picked at once, and the link is opened when the screen opens
    LaunchedEffect(devices) { if (address == null && devices.size == 1) { address = devices[0].first; BudsPrefs.setAddress(ctx, address) } }
    LaunchedEffect(address, granted) { if (address != null && granted && !BudsLink.state.value.connected) BudsService.start(ctx) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Buds manager", "Buds yöneticisi"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        if (!granted) {
            PCard {
                Text(tr("Bluetooth permission is needed to talk to the earbuds.", "Kulaklıkla konuşmak için Bluetooth izni gerekir."), color = T.text, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                PButton(tr("Allow", "İzin ver")) { ask.launch(if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(Manifest.permission.BLUETOOTH_CONNECT)) }
            }
            return@Column
        }
        // the earbuds
        PCard {
            Text(tr("Earbuds", "Kulaklık"), color = T.sub, fontSize = 12.sp)
            if (devices.isEmpty()) Text(tr("No paired Galaxy Buds. Pair them in the phone's Bluetooth settings first.", "Eşleşmiş Galaxy Buds yok. Önce telefonun Bluetooth ayarlarından eşleştir."), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            devices.forEach { (a, n) ->
                Row(Modifier.fillMaxWidth().clickable { address = a; BudsPrefs.setAddress(ctx, a) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("sensors", if (address == a) T.accent else T.sub, 22.dp)
                    Text(n, color = T.text, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
                    if (address == a) PIcon("check", T.accent, 20.dp)
                }
            }
            if (address != null) {
                Spacer(Modifier.height(6.dp))
                if (s.connected) PButton(tr("Disconnect", "Bağlantıyı kes")) { BudsService.stop(ctx) }
                else PButton(tr("Connect", "Bağlan")) { BudsService.start(ctx) }
            }
        }
        // the state
        if (s.connected) {
            PCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Bud("L", s.batteryL, s.wornL)
                    if (s.batteryCase in 0..100) Bud(tr("Case", "Kutu"), s.batteryCase, null)
                    Bud("R", s.batteryR, s.wornR)
                }
            }
            if (s.canNoise) PCard {
                Text(tr("Noise control", "Gürültü denetimi"), color = T.sub, fontSize = 12.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to tr("Off", "Kapalı"), 1 to "ANC", 2 to tr("Ambient", "Ortam")).forEach { (m, n) -> PChip(s.noise == m, n) { BudsLink.setNoise(m) } }
                }
                Text(when (s.noise) {
                    1 -> tr("Active noise cancelling: the earbuds listen to the outside and play the opposite sound, so engines and crowds get quieter.", "Aktif gürültü engelleme: kulaklık dışarıyı dinler ve ters sesi çalar, motor ve kalabalık sesi azalır.")
                    2 -> tr("Ambient sound: the outside is played into your ears, so you can hear people and traffic with the earbuds in.", "Ortam sesi: dışarıdaki ses kulağına verilir, kulaklık takılıyken insanları ve trafiği duyarsın.")
                    0 -> tr("Off: the earbuds only play the music, nothing is cancelled and nothing is let in.", "Kapalı: kulaklık sadece müziği çalar, ses engellenmez ve dışarıdan ses verilmez.")
                    else -> ""
                }, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else if (address != null) Text(tr("Not connected. Put the earbuds in or open their case.", "Bağlı değil. Kulaklığı tak ya da kutusunu aç."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        SettingsSection(tr("When the earbuds come out", "Kulaklıklar çıkınca"), startOpen = true) {
            BRow(tr("Stop the music", "Müziği durdur"), tr("When both earbuds are taken out of your ears, the music or video that is playing is paused.", "İki kulaklık da kulaktan çıkınca çalan müzik ya da video duraklatılır."), pause) { pause = it; BudsPrefs.setPauseOnRemove(ctx, it) }
            BRow(tr("Turn noise control off", "Gürültü denetimini kapat"), tr("Noise cancelling and ambient sound are switched off while the earbuds are out, which saves their battery.", "Kulaklıklar çıkıkken gürültü engelleme ve ortam sesi kapatılır, pil tasarrufu sağlar."), noiseOff) { noiseOff = it; BudsPrefs.setNoiseOffOnRemove(ctx, it) }
        }
        SettingsSection(tr("When you put them back", "Tekrar takınca"), startOpen = true) {
            BRow(tr("Put back as it was", "Eski haline getir"), tr("When one earbud goes back in, the music carries on and the noise control returns to the mode it had before.", "Bir kulaklık tekrar takılınca müzik devam eder ve gürültü denetimi önceki moduna döner."), resume) { resume = it; BudsPrefs.setResumeOnWear(ctx, it) }
        }
        SettingsSection(tr("Connection", "Bağlantı")) {
            BRow(tr("Stay connected in the background", "Arka planda bağlı kal"), tr("Keeps the link to the earbuds open with a small notification, so the actions above work even when this screen is closed.", "Kulaklıkla bağlantıyı küçük bir bildirimle açık tutar, böylece yukarıdaki işlemler bu ekran kapalıyken de çalışır."), keep) { keep = it; BudsPrefs.setKeepOn(ctx, it); if (it && address != null) BudsService.start(ctx) else if (!it) BudsService.stop(ctx) }
        }
        Text(tr("Galaxy Buds Live, Pro, 2, 2 Pro, FE and 3. The protocol notes are from the GalaxyBudsClient project.", "Galaxy Buds Live, Pro, 2, 2 Pro, FE ve 3. Protokol bilgisi GalaxyBudsClient projesinden."), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
private fun Bud(label: String, battery: Int, worn: Boolean?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(if (worn == true) T.accent.copy(alpha = .25f) else T.text.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
            Text(label, color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(if (battery in 0..100) "$battery%" else "—", color = if (battery in 0..20) Color(0xFFC42B1C) else T.text, fontSize = 16.sp, modifier = Modifier.padding(top = 4.dp))
        if (worn != null) Text(if (worn) tr("in ear", "kulakta") else tr("out", "çıkarılmış"), color = T.sub, fontSize = 11.sp)
    }
}

@Composable
private fun BRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
            PSwitch(checked, onChange)
        }
        Text(desc, color = T.sub, fontSize = 12.sp)
    }
}
