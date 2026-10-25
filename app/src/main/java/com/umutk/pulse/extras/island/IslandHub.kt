package com.umutk.pulse.extras.island

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.umutk.pulse.Compat
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.blackout.BoBtn
import com.umutk.pulse.extras.blackout.BoButtons
import com.umutk.pulse.extras.blackout.BoCard
import com.umutk.pulse.extras.blackout.BoSlider
import com.umutk.pulse.extras.blackout.BoStatus
import com.umutk.pulse.extras.blackout.BoSwitchRow
import com.umutk.pulse.extras.blackout.BoText
import com.umutk.pulse.extras.blackout.MoonLogo
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr

/**
 * Island: a small pill at the top of the screen, over the camera. It widens for what is going on (the song, a notification, a charger, a headset, the volume, the
 * torch, a removed app, a screenshot) and opens into a card on a tap; a swipe up closes it. Here: the switch, what it needs, where it sits, what it shows and how it looks.
 */
@Composable
fun IslandHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffectOnce { IslandPrefs.load(ctx) }
    val tick = remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick.intValue++ }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    var calibrating by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { IslandService.calibrate(false) } }
    BackHandler { onBack() }
    fun save() = IslandPrefs.save(ctx)
    val overlay = tick.intValue >= 0 && Compat.canOverlay(ctx)
    val access = tick.intValue >= 0 && Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty().let { it.contains(ctx.packageName + "/com.umutk.pulse.extras.island.IslandListenerService") || it.contains("IslandListenerService") }
    val photos = tick.intValue >= 0 && ctx.checkSelfPermission(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val bt = tick.intValue >= 0 && (Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    val askPhotos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick.intValue++ }
    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick.intValue++ }

    fun setOn(v: Boolean) {
        if (v && !Compat.canOverlay(ctx)) { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
        IslandPrefs.on = v; save()
        if (v) IslandService.start(ctx) else { IslandService.stop(ctx); calibrating = false }
    }

    Column(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = pageGutter).padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("back", T.text, 22.dp, Modifier.clickable { onBack() })
            Text("Island", color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
            com.umutk.pulse.ExtraLookIcon(T.text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = pageGutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BoCard {
                BoSwitchRow(tr("Island", "Ada"), tr("A pill over the camera that shows what is going on and opens on a tap.", "Kameranın üstünde, olup biteni gösteren ve dokununca açılan bir hap."), IslandPrefs.on && overlay, bold = true) { setOn(it) }
                BoStatus(overlay, tr("Display over other apps is allowed", "Diğer uygulamaların üstünde gösterme izinli"), tr("Needs \"display over other apps\"", "\"Diğer uygulamaların üstünde göster\" izni gerekir"))
                if (!overlay) BoButtons { BoBtn(tr("Allow", "İzin ver")) { setOn(true) } }
                BoStatus(access, tr("Notification access is on (songs and notifications)", "Bildirim erişimi açık (şarkılar ve bildirimler)"), tr("Notification access is off: no songs, no notifications", "Bildirim erişimi kapalı: şarkı ve bildirim yok"), Modifier.padding(top = 6.dp))
                if (!access) BoButtons { BoBtn(tr("Open notification access", "Bildirim erişimini aç")) { ctx.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                BoStatus(photos, tr("Pictures are readable (screenshots)", "Resimler okunabilir (ekran görüntüleri)"), tr("Pictures are off: no screenshot card", "Resim izni kapalı: ekran görüntüsü kartı yok"), Modifier.padding(top = 6.dp))
                if (!photos) BoButtons { BoBtn(tr("Allow pictures", "Resimlere izin ver")) { askPhotos.launch(if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE) } }
                BoStatus(bt, tr("Bluetooth names are readable", "Bluetooth adları okunabilir"), tr("Bluetooth is off: devices show no name", "Bluetooth izni kapalı: cihazların adı görünmez"), Modifier.padding(top = 6.dp))
                if (!bt) BoButtons { BoBtn(tr("Allow Bluetooth", "Bluetooth'a izin ver")) { askBt.launch(Manifest.permission.BLUETOOTH_CONNECT) } }
            }

            BoCard {
                BoText(tr("Position and size", "Konum ve boyut"), 16, T.text, bold = true)
                BoText(tr("Place the pill over the camera of this phone: turn on the outline, then move the sliders until it fits.", "Hapı bu telefonun kamerasının üstüne yerleştirin: çerçeveyi açın, sonra oturana kadar çubukları oynatın."))
                BoSwitchRow(tr("Show the outline", "Çerçeveyi göster"), "", calibrating, IslandPrefs.on && overlay) { v ->
                    calibrating = v
                    if (v && IslandService.live == null) IslandService.start(ctx)
                    IslandService.calibrate(v)
                }
                Slide(tr("From the top", "Üstten uzaklık"), IslandPrefs.top, -20f..120f) { IslandPrefs.top = it; save(); IslandService.live?.view?.let { v -> v.post { v.requestLayout() } }; IslandService.calibrate(calibrating) }
                Slide(tr("Sideways", "Yana kayma"), IslandPrefs.x, -160f..160f) { IslandPrefs.x = it; save(); IslandService.calibrate(calibrating) }
                Slide(tr("Width", "Genişlik"), IslandPrefs.w, 50f..260f) { IslandPrefs.w = it; save(); IslandService.calibrate(calibrating) }
                Slide(tr("Height", "Yükseklik"), IslandPrefs.h, 18f..64f) { IslandPrefs.h = it; save(); IslandService.calibrate(calibrating) }
                BoSwitchRow(tr("Resting pill", "Dinlenme hapı"), tr("Draw the pill even when there is nothing to show (it hides behind the camera). Off: nothing until something happens.", "Gösterecek bir şey yokken de hapı çiz (kameranın arkasında gizlenir). Kapalı: bir şey olana kadar görünmez."), IslandPrefs.rest) { IslandPrefs.rest = it; save(); IslandService.calibrate(calibrating) }
                BoButtons { BoBtn(tr("Reset", "Sıfırla")) { IslandPrefs.top = 6f; IslandPrefs.x = 0f; IslandPrefs.w = 120f; IslandPrefs.h = 34f; save(); IslandService.calibrate(calibrating) } }
            }

            BoCard {
                BoText(tr("What it shows", "Ne gösterir"), 16, T.text, bold = true)
                BoSwitchRow(tr("Music", "Müzik"), tr("The song that is playing, with the controls", "Çalan şarkı ve denetimleri"), IslandPrefs.music) { IslandPrefs.music = it; save() }
                BoSwitchRow(tr("Notifications", "Bildirimler"), tr("A new notification, for a few seconds", "Yeni bir bildirim, birkaç saniyeliğine"), IslandPrefs.notif) { IslandPrefs.notif = it; save() }
                BoSwitchRow(tr("Charging", "Şarj"), tr("When a charger is connected", "Şarj aleti takılınca"), IslandPrefs.charge) { IslandPrefs.charge = it; save() }
                BoSwitchRow(tr("Headphones and Bluetooth", "Kulaklık ve Bluetooth"), tr("A headset or device that connects", "Bağlanan kulaklık ya da cihaz"), IslandPrefs.headset) { IslandPrefs.headset = it; save() }
                BoSwitchRow(tr("Volume", "Ses"), tr("The level while the volume keys are pressed", "Ses tuşlarına basılırken seviye"), IslandPrefs.volume) { IslandPrefs.volume = it; save() }
                BoSwitchRow(tr("Torch", "Fener"), tr("While the torch is on, with a button to turn it off", "Fener açıkken, kapatma düğmesiyle"), IslandPrefs.torch) { IslandPrefs.torch = it; save() }
                BoSwitchRow(tr("Removed apps", "Kaldırılan uygulamalar"), tr("Which app was just uninstalled", "Az önce hangi uygulama kaldırıldı"), IslandPrefs.uninstall) { IslandPrefs.uninstall = it; save() }
                BoSwitchRow(tr("Screenshots", "Ekran görüntüleri"), tr("The new picture, with Share and Copy", "Yeni resim, Paylaş ve Kopyala ile"), IslandPrefs.shot) { IslandPrefs.shot = it; save() }
            }

            BoCard {
                BoText(tr("Look and behaviour", "Görünüm ve davranış"), 16, T.text, bold = true)
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PChip(IslandPrefs.style == "black", tr("Black", "Siyah")) { IslandPrefs.style = "black"; save() }
                    PChip(IslandPrefs.style == "art", tr("Colour of the cover", "Kapağın rengi")) { IslandPrefs.style = "art"; save() }
                    PChip(IslandPrefs.style == "accent", tr("Dark grey", "Koyu gri")) { IslandPrefs.style = "accent"; save() }
                }
                BoText(tr("Closes by itself after", "Şu süre sonra kendiliğinden kapanır"), 13, T.text, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3 to "3 s", 6 to "6 s", 10 to "10 s", 0 to tr("Never", "Hiç")).forEach { (s, n) -> PChip(IslandPrefs.collapse == s, n) { IslandPrefs.collapse = s; save() } }
                }
                BoSwitchRow(tr("Vibrate", "Titreşim"), tr("A light tick when an alert comes and on a long press", "Bir uyarı gelince ve uzun basınca hafif titreşim"), IslandPrefs.haptic) { IslandPrefs.haptic = it; save() }
                BoText(tr("A tap opens the island, a swipe up closes it, a long press opens this page.", "Dokunmak adayı açar, yukarı kaydırmak kapatır, uzun basmak bu sayfayı açar."), modifier = Modifier.padding(top = 6.dp))
            }
            BoButtons { BoBtn(tr("Show a test alert", "Deneme uyarısı göster"), IslandPrefs.on && overlay) { IslandService.demo(ctx) } }
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Slide(label: String, v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BoText(label, 14, T.text, modifier = Modifier.weight(1f))
        BoText("${v.toInt()} dp", 13, T.accent)
    }
    BoSlider(v, range, onChange = onChange)
}

@Composable
private fun LaunchedEffectOnce(block: () -> Unit) { androidx.compose.runtime.LaunchedEffect(Unit) { block() } }
