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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
                // a pair whose name is empty or only a space is not one to pick; the same name twice is one pair
                val n = (runCatching { d.name }.getOrNull() ?: "").trim()
                if (n.isEmpty()) return@filter false
                val major = runCatching { d.bluetoothClass?.majorDeviceClass }.getOrNull()
                n.contains("Buds", true) || n.contains("Galaxy", true) || major == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO
            }.sortedByDescending { (runCatching { it.name }.getOrNull() ?: "").contains("Buds", true) }
                .distinctBy { (runCatching { it.name }.getOrNull() ?: "").trim().lowercase() }
                .map { it.address to (runCatching { it.name }.getOrNull() ?: it.address).trim() }
        }.getOrDefault(emptyList())
    }
    // the only pair is picked at once, and the link is opened when the screen opens
    LaunchedEffect(devices) { if (address == null && devices.size == 1) { address = devices[0].first; BudsPrefs.setAddress(ctx, address) } }
    // without "stay connected" the link (and its notification) only lives while this screen is open
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { if (!BudsPrefs.keepOn(ctx)) BudsService.stop(ctx) } }
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
        BudsHero(s, devices.firstOrNull { it.first == address }?.second.orEmpty(), address != null)
        // the earbuds
        PCard {
            Text(tr("Earbuds", "Kulaklık"), color = T.sub, fontSize = 12.sp)
            if (devices.isEmpty()) Text(tr("No paired Galaxy Buds. Pair them in the phone's Bluetooth settings first.", "Eşleşmiş Galaxy Buds yok. Önce telefonun Bluetooth ayarlarından eşleştir."), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            devices.forEach { (a, n) ->
                Row(Modifier.fillMaxWidth().clickable { address = a; BudsPrefs.setAddress(ctx, a) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("tool_buds", if (address == a) T.accent else T.sub, 22.dp)
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
            if (s.canNoise) PCard {
                Text(tr("Noise control", "Gürültü denetimi"), color = T.sub, fontSize = 12.sp)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf(0 to tr("Off", "Kapalı"), 1 to "ANC", 2 to tr("Ambient", "Ortam")) + (if (s.name.contains("Buds3")) listOf(3 to tr("Adaptive", "Uyarlanır")) else emptyList())).forEach { (m, n) -> PChip(s.noise == m, n) { BudsLink.setNoise(m) } }
                }
                Text(when (s.noise) {
                    1 -> tr("Active noise cancelling: the earbuds listen to the outside and play the opposite sound, so engines and crowds get quieter.", "Aktif gürültü engelleme: kulaklık dışarıyı dinler ve ters sesi çalar, motor ve kalabalık sesi azalır.")
                    2 -> tr("Ambient sound: the outside is played into your ears, so you can hear people and traffic with the earbuds in.", "Ortam sesi: dışarıdaki ses kulağına verilir, kulaklık takılıyken insanları ve trafiği duyarsın.")
                    0 -> tr("Off: the earbuds only play the music, nothing is cancelled and nothing is let in.", "Kapalı: kulaklık sadece müziği çalar, ses engellenmez ve dışarıdan ses verilmez.")
                    else -> ""
                }, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
        } else if (address != null) Text(tr("Not connected. Put the earbuds in or open their case.", "Bağlı değil. Kulaklığı tak ya da kutusunu aç."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        if (s.connected) BudsControls(s)
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


/** The settings of the earbuds themselves, sent over the serial link (message ids and values from the GalaxyBudsClient notes). */
@Composable
private fun BudsControls(s: BudsState) {
    val ctx = LocalContext.current
    var touchL by remember(s.touchL) { mutableStateOf(s.touchL.takeIf { it in 1..4 } ?: BudsPrefs.saved(ctx, "touchL", 2)) }
    var touchR by remember(s.touchR) { mutableStateOf(s.touchR.takeIf { it in 1..4 } ?: BudsPrefs.saved(ctx, "touchR", 2)) }
    var cycle by remember(s.cycle) { mutableStateOf(s.cycle.takeIf { it in 0..2 } ?: BudsPrefs.saved(ctx, "cycle", 0)) }
    var lock by remember(s.lock) { mutableStateOf(s.lock ?: (BudsPrefs.saved(ctx, "lock", 0) == 1)) }
    var eq by remember(s.eq) { mutableStateOf(s.eq.takeIf { it in 0..5 } ?: BudsPrefs.saved(ctx, "eq", 0)) }
    var ancHigh by remember(s.ancHigh) { mutableStateOf(s.ancHigh ?: (BudsPrefs.saved(ctx, "ancHigh", 0) == 1)) }
    var ambVol by remember(s.ambientVolume) { mutableStateOf(s.ambientVolume.takeIf { it in 0..3 } ?: BudsPrefs.saved(ctx, "ambVol", 1)) }
    var seamless by remember(s.seamless) { mutableStateOf(s.seamless ?: (BudsPrefs.saved(ctx, "seamless", 1) == 1)) }
    var voice by remember(s.voiceDetect) { mutableStateOf(s.voiceDetect ?: (BudsPrefs.saved(ctx, "voice", 0) == 1)) }
    var oneBud by remember(s.oneBud) { mutableStateOf(s.oneBud ?: (BudsPrefs.saved(ctx, "oneBud", 0) == 1)) }
    var game by remember { mutableStateOf(BudsPrefs.saved(ctx, "game", 0) == 1) }
    var spatial by remember(s.spatial) { mutableStateOf(s.spatial ?: (BudsPrefs.saved(ctx, "spatial", 0) == 1)) }
    var find by remember { mutableStateOf(false) }
    fun save(k: String, v: Int) = BudsPrefs.save(ctx, k, v)
    val touchNames = listOf(1 to tr("Voice assistant", "Sesli asistan"), 2 to tr("Noise control", "Gürültü denetimi"), 3 to tr("Volume", "Ses"))
    SettingsSection(tr("Touch controls", "Dokunma denetimi"), startOpen = true) {
        Text(tr("What a touch and hold on each earbud does.", "Her kulaklıkta basılı tutmanın ne yapacağı."), color = T.sub, fontSize = 12.sp)
        Text(tr("Left", "Sol"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { touchNames.forEach { (v, n) -> PChip(touchL == v, n) { touchL = v; save("touchL", v); BudsLink.send(BudsProto.SET_TOUCHPAD_OPTION, touchL, touchR) } } }
        Text(tr("Right", "Sağ"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { touchNames.forEach { (v, n) -> PChip(touchR == v, n) { touchR = v; save("touchR", v); BudsLink.send(BudsProto.SET_TOUCHPAD_OPTION, touchL, touchR) } } }
        Text(tr("When it is set to Noise control, a hold cycles through:", "Gürültü denetimi seçiliyken basılı tutmak şunlar arasında geçer:"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        val cycles = listOf(tr("ANC and off", "ANC ve kapalı"), tr("Ambient and off", "Ortam ve kapalı"), tr("ANC and ambient", "ANC ve ortam"))
        val bytes = listOf(intArrayOf(1, 0, 1), intArrayOf(0, 1, 1), intArrayOf(1, 1, 0))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { cycles.forEachIndexed { i, n -> PChip(cycle == i, n) {
            cycle = i; save("cycle", i); val b = bytes[i]; BudsLink.send(BudsProto.SET_TOUCH_AND_HOLD_NOISE, *(b + b))
        } } }
        BRow(tr("Lock the touch panels", "Dokunma yüzeylerini kilitle"), tr("Taps and holds on the earbuds do nothing while locked, so a touch in your sleep or hair changes nothing.", "Kilitliyken kulaklıklara dokunmak hiçbir şey yapmaz; uyurken ya da saça değince bir şey değişmez."), lock) {
            lock = it; save("lock", if (it) 1 else 0); BudsLink.send(BudsProto.LOCK_TOUCHPAD, if (it) 0 else 1, 1, 1, 1, 1, 1, 1)
        }
    }
    SettingsSection(tr("Sound", "Ses"), startOpen = true) {
        Text(tr("Equalizer", "Ekolayzır"), color = T.text, fontSize = 14.sp)
        val eqs = listOf(tr("Off", "Kapalı"), tr("Bass boost", "Bas"), tr("Soft", "Yumuşak"), tr("Dynamic", "Dinamik"), tr("Clear", "Net"), tr("Treble boost", "Tiz"))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { eqs.forEachIndexed { i, n -> PChip(eq == i, n) { eq = i; save("eq", i); BudsLink.send(BudsProto.EQUALIZER, i) } } }
        Text(tr("Noise cancelling strength", "Gürültü engelleme gücü"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(false to tr("Low", "Düşük"), true to tr("High", "Yüksek")).forEach { (v, n) -> PChip(ancHigh == v, n) { ancHigh = v; save("ancHigh", if (v) 1 else 0); BudsLink.send(BudsProto.NOISE_REDUCTION_LEVEL, if (v) 1 else 0) } } }
        Text(tr("Ambient sound volume", "Ortam sesi seviyesi"), color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(tr("Low", "Düşük"), tr("Medium", "Orta"), tr("High", "Yüksek")).forEachIndexed { i, n -> PChip(ambVol == i, n) { ambVol = i; save("ambVol", i); BudsLink.send(BudsProto.AMBIENT_VOLUME, i) } } }
        BRow(tr("Noise control with one earbud", "Tek kulaklıkla gürültü denetimi"), tr("Noise cancelling and ambient sound also work when only one earbud is in.", "Gürültü engelleme ve ortam sesi tek kulaklık takılıyken de çalışır."), oneBud) { oneBud = it; save("oneBud", if (it) 1 else 0); BudsLink.send(BudsProto.SET_ANC_WITH_ONE_EARBUD, if (it) 1 else 0) }
        BRow(tr("Voice detect", "Konuşma algılama"), tr("When you start talking, the music gets quieter and the ambient sound comes in.", "Konuşmaya başlayınca müzik kısılır ve ortam sesi açılır."), voice) { voice = it; save("voice", if (it) 1 else 0); BudsLink.send(BudsProto.SET_DETECT_CONVERSATIONS, if (it) 1 else 0) }
        BRow(tr("360 audio", "360 ses"), tr("The sound stays where it is when you turn your head.", "Başını çevirince ses olduğu yerde kalır."), spatial) { spatial = it; save("spatial", if (it) 1 else 0); BudsLink.send(BudsProto.SET_SPATIAL_AUDIO, if (it) 1 else 0) }
        BRow(tr("Game mode", "Oyun modu"), tr("Lowers the delay between the picture and the sound.", "Görüntü ile ses arasındaki gecikmeyi azaltır."), game) { game = it; save("game", if (it) 1 else 0); BudsLink.send(BudsProto.GAME_MODE, if (it) 1 else 0) }
    }
    SettingsSection(tr("More", "Diğer")) {
        BRow(tr("Seamless connection", "Kesintisiz bağlantı"), tr("The earbuds move to your other Galaxy devices by themselves.", "Kulaklık diğer Galaxy cihazlarına kendiliğinden geçer."), seamless) { seamless = it; save("seamless", if (it) 1 else 0); BudsLink.send(BudsProto.SET_SEAMLESS_CONNECTION, if (it) 1 else 0) }
        BRow(tr("Find my earbuds", "Kulaklığımı bul"), tr("Makes the earbuds beep. Turn it off when you have found them.", "Kulaklıkların bip sesi çıkarmasını sağlar. Bulunca kapat."), find) { find = it; BudsLink.send(if (it) BudsProto.FIND_START else BudsProto.FIND_STOP) }
    }
}


/** The top card: the case and the two earbuds drawn, which one is in the ear, the batteries and whether the link is up. */
@Composable
private fun BudsHero(s: BudsState, name: String, picked: Boolean) {
    PCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.name.ifBlank { name }.ifBlank { "Galaxy Buds" }, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(when { s.connected -> tr("Connected", "Bağlı"); picked -> tr("Connecting…", "Bağlanıyor…") ; else -> tr("Pick the earbuds below", "Aşağıdan kulaklığı seç") },
                    color = if (s.connected) T.accent else T.sub, fontSize = 13.sp)
            }
        }
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 8.dp)) {
            val w = size.width; val h = size.height
            val body = if (s.connected) Color(0xFFE9E9EE) else T.text.copy(alpha = .25f)
            fun bud(cx: Float, worn: Boolean, flip: Float) {
                val ring = if (worn) T.accent else T.text.copy(alpha = .18f)
                drawOval(body, androidx.compose.ui.geometry.Offset(cx - 34f, h * .12f), androidx.compose.ui.geometry.Size(68f, 64f))
                drawOval(ring, androidx.compose.ui.geometry.Offset(cx - 34f, h * .12f), androidx.compose.ui.geometry.Size(68f, 64f), style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                drawRoundRect(body, androidx.compose.ui.geometry.Offset(cx - 9f + flip * 14f, h * .12f + 48f), androidx.compose.ui.geometry.Size(18f, 54f), androidx.compose.ui.geometry.CornerRadius(9f))
                drawCircle(Color(0xFF3A3A44), 11f, androidx.compose.ui.geometry.Offset(cx + flip * -8f, h * .12f + 30f))
            }
            bud(w * .22f, s.wornL, -1f)
            // the case in the middle: a rounded block with a lid line and a small light
            val cw = 110f
            drawRoundRect(T.text.copy(alpha = .14f), androidx.compose.ui.geometry.Offset(w * .5f - cw / 2, h * .18f), androidx.compose.ui.geometry.Size(cw, h * .62f), androidx.compose.ui.geometry.CornerRadius(34f))
            drawLine(T.text.copy(alpha = .3f), androidx.compose.ui.geometry.Offset(w * .5f - cw / 2 + 8f, h * .18f + h * .2f), androidx.compose.ui.geometry.Offset(w * .5f + cw / 2 - 8f, h * .18f + h * .2f), 3f)
            drawCircle(if (s.connected) T.accent else T.text.copy(alpha = .3f), 5f, androidx.compose.ui.geometry.Offset(w * .5f, h * .18f + h * .42f))
            bud(w * .78f, s.wornR, 1f)
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            BudBattery("L", s.batteryL, s.wornL)
            if (s.batteryCase in 0..100) BudBattery(tr("Case", "Kutu"), s.batteryCase, null)
            BudBattery("R", s.batteryR, s.wornR)
        }
    }
}

@Composable
private fun BudBattery(label: String, level: Int, worn: Boolean?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (level in 0..100) "$level%" else "—", color = if (level in 0..20) Color(0xFFC42B1C) else T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.padding(top = 4.dp).size(width = 56.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(T.text.copy(alpha = .12f))) {
            if (level in 0..100) Box(Modifier.fillMaxWidth(level / 100f).fillMaxHeight().background(if (level <= 20) Color(0xFFC42B1C) else T.accent))
        }
        Text(label + if (worn == true) " · " + tr("in ear", "kulakta") else "", color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
