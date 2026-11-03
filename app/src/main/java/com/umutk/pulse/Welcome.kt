package com.umutk.pulse

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The launcher icon as a bitmap. It is an adaptive-icon XML, which Compose's painterResource and imageResource
 * cannot load (that crashed the first start), so it is drawn through the Android Drawable instead.
 */
@Composable
fun rememberLauncherLogo(): ImageBitmap {
    val ctx = LocalContext.current
    return remember(ctx) { ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher)!!.toBitmap(288, 288).asImageBitmap() }
}

/** Shown once, after the splash, on the first start: what Pulse does, what it is built from, and its size. */
@Composable
/** The installed version name: the welcome (with what is new) shows again after every update. */
fun appVersionName(c: android.content.Context): String =
    runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() }.getOrDefault("")

fun Welcome(onDone: () -> Unit) {
    val ctx = LocalContext.current
    fun finish() {
        ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putBoolean("welcomed", true).putString("seen_version", appVersionName(ctx)).apply()
        // the one Shizuku question: only when it runs and Pulse has no root to fall back on; never on later starts
        Shell.refreshShizuku()
        if (Shell.root != true && Shell.shizuku == 1) Shell.askShizuku(ctx)
        onDone()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 40.dp, bottom = 24.dp)) {
        Image(rememberLauncherLogo(), null, Modifier.size(72.dp))
        Text(tr("Welcome to Pulse", "Pulse'a hoş geldin"), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        Text(tr("A task manager for Android that looks like the Windows one, with tools for the rest of the phone.",
            "Windows'a benzeyen bir Android görev yöneticisi, telefonun geri kalanı için araçlarla birlikte."),
            color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))

        WelcomeCard(tr("What's new", "Yenilikler")) {
            WelcomeLine(tr("Keyboard: a floating keyboard, a number pad with +, a rounded suggestion bar and a custom bottom key.",
                "Klavye: yüzen klavye, + tuşlu sayı paneli, yuvarlak öneri çubuğu ve özel alt tuş."))
            WelcomeLine(tr("Game Hub: your game library with covers, play time, hidden games and the Play button.",
                "Game Hub: kapaklı oyun kütüphanesi, oynama süresi, gizli oyunlar ve Oyna düğmesi."))
            WelcomeLine(tr("Weather from several sources combined, and the last forecast when nothing answers.",
                "Hava durumu: birden çok kaynak birleşik, hiçbiri yanıt vermezse son tahmin."))
            WelcomeLine(tr("Alarms: awake check, its own volume for the final ring, and a dismiss you hold and drag.",
                "Alarmlar: uyanıklık kontrolü, son çalmanın kendi sesi ve basılı tutup sürükleyerek kapatma."))
            WelcomeLine(tr("Video links from other apps open in Pulse, with a download or an outside player.",
                "Diğer uygulamalardan gelen video bağlantıları Pulse'ta açılır: indirme ya da dış oynatıcı."))
        }

        WelcomeCard(tr("Built from open source", "Açık kaynak parçalar")) {
            WelcomeLine(tr("Universal Dependencies treebanks (Turkish BOUN, Kenet and Penn, English EWT), CC BY-SA 4.0: the word pairs of the keyboard.",
                "Universal Dependencies ağaç bankaları (Türkçe BOUN, Kenet ve Penn, İngilizce EWT), CC BY-SA 4.0: klavyenin kelime çiftleri."))
            WelcomeLine(tr("FrequencyWords by hermitdave, CC BY-SA 4.0: the word lists of the keyboard.", "hermitdave'in FrequencyWords listesi, CC BY-SA 4.0: klavyenin kelime listeleri."))
            WelcomeLine(tr("Shizuku API, MIT licence (RikkaW).", "Shizuku API, MIT lisansı (RikkaW)."))
            WelcomeLine(tr("Microsoft Fluent System Icons, MIT licence: the outline icons of the Windows themes.",
                "Microsoft Fluent System Icons, MIT lisansı: Windows temalarındaki çizgi ikonlar."))
            WelcomeLine(tr("Windows XP icon pack by Matt Pettit, CC0 public domain dedication.",
                "Matt Pettit'in Windows XP ikon paketi, CC0 kamu malı adanmışlığı."))
            WelcomeLine(tr("Licence texts are in About, and in the repository's README.",
                "Lisans metinleri Hakkında bölümünde ve deponun README dosyasında."))
        }

        WelcomeCard(tr("Size and permissions", "Boyut ve izinler")) {
            WelcomeLine(tr("The app is about 11 MB, for arm64 phones with Android 10 or newer.",
                "Uygulama yaklaşık 11 MB, Android 10 veya üstü arm64 telefonlar için."))
            WelcomeLine(tr("Features that need a permission ask for it when you use them, and you can say no.",
                "İzin gerektiren özellikler, kullanırken izin ister; hayır diyebilirsin."))
            WelcomeLine(tr("Root and Shizuku are optional. Without them, Pulse shows less, and says so.",
                "Root ve Shizuku isteğe bağlı. Yoksa Pulse daha az bilgi gösterir ve bunu söyler."))
        }

        Spacer(Modifier.height(20.dp))
        PButton(tr("Start", "Başla"), Modifier.fillMaxWidth()) { finish() }
    }
}

@Composable
private fun WelcomeCard(title: String, content: @Composable () -> Unit) {
    PCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(title, color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun WelcomeLine(text: String) {
    Text("•  $text", color = T.text, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
}

/** The first screen: the terms and privacy note (the person accepts them once), and what root and Shizuku are for. Pulse works without either. */
@Composable
fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var accepted by remember { mutableStateOf(false) }
    fun finish() {
        ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit()
            .putBoolean("setup_done", true).putLong("terms_accepted_at", System.currentTimeMillis()).putString("terms_version", "1").apply()
        onDone()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 40.dp, bottom = 24.dp)) {
        Image(rememberLauncherLogo(), null, Modifier.size(72.dp))
        Text(tr("Set up Pulse", "Pulse'u kur"), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        Text(tr("Two things before you start.", "Başlamadan önce iki şey."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        WelcomeCard(tr("Terms and privacy", "Koşullar ve gizlilik")) {
            WelcomeLine(tr("Pulse keeps your files, music and settings on this phone. Nothing is sent to us.",
                "Pulse dosyalarını, müziğini ve ayarlarını bu telefonda tutar. Bize hiçbir şey gönderilmez."))
            WelcomeLine(tr("The Play version shows ads from Google AdMob. Google may use an advertising ID for them, and you can change that in your phone's settings.",
                "Play sürümü Google AdMob reklamları gösterir. Google bunlar için bir reklam kimliği kullanabilir; bunu telefonunuzun ayarlarından değiştirebilirsiniz."))
            WelcomeLine(tr("The terms of use and the privacy notice are in About.", "Kullanım koşulları ve gizlilik bildirimi Hakkında bölümünde."))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PCheck(accepted, { accepted = it })
                Text(tr("I have read and accept the terms and the privacy notice", "Koşulları ve gizlilik bildirimini okudum, kabul ediyorum"),
                    color = T.text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            }
        }
        PermissionsCard()
        WelcomeCard(tr("Root and Shizuku (optional)", "Root ve Shizuku (isteğe bağlı)")) {
            WelcomeLine(tr("Some tools need root or Shizuku: hiding games, Debloat, and keeping Pulse as the default keyboard.",
                "Bazı araçlar root veya Shizuku ister: oyunları gizlemek, Debloat ve Pulse'u varsayılan klavye olarak tutmak."))
            WelcomeLine(tr("Pulse works without either. Those tools just stay off until you give access in their settings.",
                "Pulse ikisi olmadan da çalışır. Bu araçlar, ayarlarından erişim verene kadar kapalı kalır."))
        }
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) {
            if (accepted) PButton(tr("Continue", "Devam"), onClick = { finish() })
            else Text(tr("Accept the terms to continue", "Devam etmek için koşulları kabul et"), color = T.sub, fontSize = 13.sp)
        }
    }
}

/** One permission group and the feature it serves. Nothing is asked until the box is ticked; a group left empty is skipped. */
private class PermItem(val title: String, val why: String, val perms: List<String>, val optional: Boolean)

private fun permItems(): List<PermItem> = listOf(
    PermItem(tr("Files and music", "Dosyalar ve müzik"), tr("Shows your music, photos and videos.", "Müziğini, fotoğraflarını ve videolarını gösterir."),
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE), optional = false),
    PermItem(tr("Notifications", "Bildirimler"), tr("Music controls and download progress.", "Müzik kontrolleri ve indirme durumu."),
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(), optional = true),
    PermItem(tr("Contacts", "Kişiler"), tr("The Pulse contacts list.", "Pulse kişi listesi."), listOf(Manifest.permission.READ_CONTACTS), optional = true),
    PermItem(tr("Calendar", "Takvim"), tr("The Pulse calendar.", "Pulse takvimi."), listOf(Manifest.permission.READ_CALENDAR), optional = true),
    PermItem(tr("Phone calls", "Telefon aramaları"), tr("Call from Pulse contacts and the call history.", "Pulse kişilerinden arama yapma ve arama geçmişi."),
        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG), optional = true),
    PermItem(tr("Messages", "Mesajlar"), tr("Read and send text messages from Pulse.", "Pulse'tan kısa mesaj okuma ve gönderme."),
        listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS), optional = true),
)

@Composable
private fun PermissionsCard() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    WelcomeCard(tr("Permissions", "İzinler")) {
        WelcomeLine(tr("Each one is for one feature. Tick the ones you want; the rest you can skip, and the feature asks again when you open it.",
            "Her biri tek bir özellik için. İstediklerini işaretle; kalanlarını atlayabilirsin, özellik açıldığında yeniden sorar."))
        permItems().forEach { item ->
            val granted = remember(tick, item.title) { item.perms.isEmpty() || item.perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PCheck(granted, { wanted -> if (wanted && !granted && item.perms.isNotEmpty()) launcher.launch(item.perms.toTypedArray()) })
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(item.title, color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.why + " " + (if (item.optional) tr("Optional.", "İsteğe bağlı.") else tr("Recommended.", "Önerilir.")), color = T.sub, fontSize = 12.sp)
                }
            }
        }
    }
}
