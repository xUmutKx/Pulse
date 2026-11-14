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
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
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
/** The installed version name: the welcome (with what is new) shows again after every update. */
fun appVersionName(c: android.content.Context): String =
    runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty() }.getOrDefault("")

@Composable
fun Welcome(onDone: () -> Unit) {
    val ctx = LocalContext.current
    fun finish() {
        ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putBoolean("welcomed", true).putString("seen_version", appVersionName(ctx)).apply()
        // the one Shizuku question: only when it runs and Pulse has no root to fall back on; never on later starts
        Shell.refreshShizuku()
        if (Shell.root != true && Shell.shizuku == 1) Shell.askShizuku(ctx)
        onDone()
    }
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 40.dp, bottom = 24.dp)) {
        Image(rememberLauncherLogo(), null, Modifier.size(72.dp))
        Text(tr("Welcome to Pulse", "Pulse'a hoş geldin"), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        Text(tr("A task manager for Android that looks like the Windows one, with tools for the rest of the phone.",
            "Windows'a benzeyen bir Android görev yöneticisi, telefonun geri kalanı için araçlarla birlikte."),
            color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))

        // prominent disclosure for the Accessibility API: shown on the first start of every version, before any of its services is switched on
        // one switch per Accessibility service: it shows whether the service is on; a tap opens Accessibility settings, where Android turns it on or off
        val accLifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
        var accTick by remember { mutableIntStateOf(0) }
        androidx.compose.runtime.DisposableEffect(accLifecycle) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) accTick++ }
            accLifecycle.addObserver(observer)
            onDispose { accLifecycle.removeObserver(observer) }
        }
        WelcomeCard(tr("Accessibility", "Erişilebilirlik")) {
            WelcomeLine(tr("Pulse uses the Accessibility service for four features. Each one is switched on in Accessibility settings:",
                "Pulse dört özellik için Erişilebilirlik servisini kullanır. Her biri Erişilebilirlik ayarlarında açılır:"))
            WelcomeLine(tr("None of them is required: you can press Start below and turn them on later.", "Hiçbiri şart değil: aşağıdaki Başla'ya basıp sonra da açabilirsin."))
            for (s in ACCESS_ROWS) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(tr(s.titleEn, s.titleTr), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(tr(s.descEn, s.descTr), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    PSwitch(accessOn(ctx, s.cls, accTick), { openAccessibilitySettings(ctx) })
                }
            }
            WelcomeLine(tr("Pulse reads only what it needs on this phone. Nothing from the screen is sent off the device.",
                "Pulse yalnızca bu telefonda ihtiyacı olanı okur. Ekrandaki hiçbir şey cihazdan dışarı gönderilmez."))
        }

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
            WelcomeLine(tr("The app is about 44 MB, for arm64 phones with Android 10 or newer.",
                "Uygulama yaklaşık 44 MB, Android 10 veya üstü arm64 telefonlar için."))
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

/** One Accessibility service of Pulse: its class, and the title and short description shown beside its switch. */
private class AccessRow(val cls: String, val titleEn: String, val titleTr: String, val descEn: String, val descTr: String)

private val ACCESS_ROWS = listOf(
    AccessRow("com.umutk.pulse.applock.LockService", "App Lock", "Uygulama kilidi",
        "Sees which app is in front, to lock it. Reads no content.", "Hangi uygulamanın önde olduğunu görür, kilitlemek için. İçerik okumaz."),
    AccessRow("com.umutk.pulse.features.CoverAccessibilityService", "Screen cover", "Ekran kapağı",
        "Draws over other apps. Reads no screen content.", "Diğer uygulamaların üstüne çizer. Ekran içeriğini okumaz."),
    AccessRow("com.umutk.pulse.extras.blackout.AppWatch", "BlackOut", "BlackOut",
        "Sees the app in front, to change its colors.", "Önde olan uygulamaya göre renkleri değiştirir."),
    AccessRow("com.umutk.pulse.extras.keyboard.AccessKeyboardService", "Floating keyboard", "Yüzen klavye",
        "Appears when a text field is focused and writes into that field.", "Bir metin alanı seçildiğinde açılır ve o alana yazar."),
)

/** True when the Accessibility service [cls] of this app is on (Android keeps the list in a secure setting; reading it needs no permission). */
private fun accessOn(c: android.content.Context, cls: String, @Suppress("UNUSED_PARAMETER") tick: Int): Boolean =
    runCatching {
        android.provider.Settings.Secure.getString(c.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any { it.equals("${c.packageName}/$cls", ignoreCase = true) }
    }.getOrDefault(false)

/** Opens Accessibility settings, where the user turns a Pulse service on or off. Android does not let an app switch them itself. */
private fun openAccessibilitySettings(c: android.content.Context) {
    c.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
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
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 40.dp, bottom = 24.dp)) {
        Image(rememberLauncherLogo(), null, Modifier.size(72.dp))
        Text(tr("Set up Pulse", "Pulse'u kur"), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        Text(tr("Two things before you start.", "Başlamadan önce iki şey."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        // the one thing that is required, said plainly: everything else can be skipped and given later
        Text(tr("Only accepting the terms is required. Every permission below is optional: skip any of them, Pulse asks again when a feature needs it.",
            "Sadece koşulları kabul etmek gereklidir. Aşağıdaki izinlerin hepsi isteğe bağlıdır: istediğini atla, bir özellik gerektirdiğinde Pulse yeniden sorar."),
            color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        WelcomeCard(tr("Terms and privacy", "Koşullar ve gizlilik")) {
            WelcomeLine(tr("Pulse keeps your files, music and settings on this phone. Nothing is sent to us.",
                "Pulse dosyalarını, müziğini ve ayarlarını bu telefonda tutar. Bize hiçbir şey gönderilmez."))
            WelcomeLine(tr("The Play version shows ads from Google AdMob. Google may use an advertising ID for them, and you can change that in your phone's settings.",
                "Play sürümü Google AdMob reklamları gösterir. Google bunlar için bir reklam kimliği kullanabilir; bunu telefonunuzun ayarlarından değiştirebilirsiniz."))
            WelcomeLine(tr("The terms of use and the privacy notice are in About.", "Kullanım koşulları ve gizlilik bildirimi Hakkında bölümünde."))
            // the whole line is the switch (a big target), not only the small box
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
            // always one button that works: it accepts the terms (when not ticked yet) and goes on; permissions are never needed to continue
            PButton(tr("Continue", "Devam"), onClick = { accepted = true; finish() })
        }
    }
}

/** One permission group and the feature it serves. Nothing is asked until the box is ticked; a group left empty is skipped. */
private class PermItem(val title: String, val why: String, val perms: List<String>, val optional: Boolean, val icon: String = "info")

private fun permItems(): List<PermItem> = listOf(
    PermItem(tr("Files and music", "Dosyalar ve müzik"), tr("Shows your music, photos and videos.", "Müziğini, fotoğraflarını ve videolarını gösterir."),
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE), optional = false, icon = "music"),
    PermItem(tr("Notifications", "Bildirimler"), tr("Music controls and download progress.", "Müzik kontrolleri ve indirme durumu."),
        if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList(), optional = true, icon = "bell"),
    PermItem(tr("Contacts", "Kişiler"), tr("The Pulse contacts list.", "Pulse kişi listesi."), listOf(Manifest.permission.READ_CONTACTS), optional = true, icon = "users"),
    PermItem(tr("Calendar", "Takvim"), tr("The Pulse calendar.", "Pulse takvimi."), listOf(Manifest.permission.READ_CALENDAR), optional = true, icon = "calendar"),
    PermItem(tr("Phone calls", "Telefon aramaları"), tr("Call from Pulse contacts and the call history.", "Pulse kişilerinden arama yapma ve arama geçmişi."),
        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CALL_LOG), optional = true, icon = "call"),
    PermItem(tr("Messages", "Mesajlar"), tr("Read and send text messages from Pulse.", "Pulse'tan kısa mesaj okuma ve gönderme."),
        listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS), optional = true, icon = "messages"),
)

@Composable
private fun PermissionsCard() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var asked by remember { mutableStateOf<PermItem?>(null) }
    val act = ctx as? android.app.Activity
    // after two refusals Android no longer shows its question: the app's own settings page opens instead, so the button never seems dead
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        tick++
        val it0 = asked; asked = null
        if (it0 != null && res.values.any { !it } && act != null && it0.perms.none { p -> androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(act, p) }) openAppSettings(ctx)
    }
    WelcomeCard(tr("Permissions", "İzinler")) {
        WelcomeLine(tr("All optional. Each one is for one feature: give the ones you want now, the rest when you need them.",
            "Hepsi isteğe bağlı. Her biri tek bir özellik için: istediklerini şimdi ver, kalanlarını gerektiğinde."))
        permItems().forEach { item ->
            val granted = remember(tick, item.title) { item.perms.isEmpty() || item.perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } }
            val ask = { if (!granted && item.perms.isNotEmpty()) { asked = item; launcher.launch(item.perms.toTypedArray()) } }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PCheck(granted, { wanted -> if (wanted) ask() })
                Box(Modifier.padding(start = 8.dp).size(28.dp), contentAlignment = Alignment.Center) { PIcon(item.icon, Look.accent, 22.dp) }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(item.title, color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(item.why + " " + (if (item.optional) tr("Optional.", "İsteğe bağlı.") else tr("Recommended, not required.", "Önerilir, şart değil.")), color = T.sub, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * The notes of the current version, shown once after an update. Newest first.
 * This list is written again for every release (it was left at an old version before, so an old note showed after an update).
 */
private val WHATS_NEW = listOf(
    "folder" to (tr("File windows: a long press on a file or folder opens a menu (copy, cut, paste, add to desktop, rename, delete); copy in one window and paste in another; a physical keyboard works (Ctrl+C/X/V, Delete, F2, Enter). Extra blur setting for the Windows flyouts, and the Windows 11 clock panel fits the screen; Desktop icons are spaced wider; the floating keyboard is wider, closes with a tap on its handle (X) and the app behind stays usable; Game Hub's open case lost its coloured band and Xbox cases are green inside.",
        "Dosya pencereleri: dosya veya klasöre basılı tutunca menü açılır (kopyala, kes, yapıştır, masaüstüne ekle, yeniden adlandır, sil); bir pencerede kopyala, ötekinde yapıştır; fiziksel klavye çalışır (Ctrl+C/X/V, Delete, F2, Enter). Ekstra bulanıklık ayarı, Windows 11 saat paneli ekrana sığar; Masaüstü simgeleri daha seyrek; yüzen klavye daha geniş, tutamağına (yanındaki X) basınca kapanır, arkadaki uygulama kullanılabilir; Game Hub açık kutusunda renkli bant yok, Xbox kutuları içeriden yeşil.")),
    "star" to (tr("Launcher: right-click menus for every Windows look (Windows 11 with an icon row), a Properties window, renamable apps and an editable menu; assignable gestures, an A-Z apps list with a letter strip and an \"At a glance\" line; a Terminal look.",
        "Launcher: her Windows görünümü için sağ tık menüleri (Windows 11'de simge satırıyla), Özellikler penceresi, adı değiştirilebilen uygulamalar ve düzenlenebilir menü; atanabilir hareketler, harf şeritli A-Z uygulama listesi ve \"Bir bakışta\" satırı; Terminal görünümü.")),
    "apps" to (tr("Desktop: icons are placed anywhere by default (spacing is a setting), the mouse arrow follows the finger, a long press on the taskbar opens the menu; a boot screen and sign-in screen with the look's sound, now and then a BIOS screen and, rarely, a fake blue screen.",
        "Masaüstü: simgeler varsayılan olarak istediğin yere konur (boşluk ayarlı), fare oku parmağı izler, görev çubuğuna basılı tutunca menü açılır; açılışta görünümün sesiyle önyükleme ve giriş ekranı, ara sıra BIOS ekranı, nadiren sahte mavi ekran.")),
    "music" to (tr("Music: a real Windows Media Player 12 look (Aero glass, big cover, the list beside it, a Library page, six looks to choose).",
        "Müzik: gerçek Windows Media Player 12 görünümü (Aero cam, büyük kapak, yanında liste, Kitaplık sayfası, seçilebilir altı görünüm).")),
    "star" to (tr("Game Hub: the in-game menu is made of icon tiles with a screen cover, and its handle can be dragged; Add a game works again.",
        "Game Hub: oyun içi menü simge kutularından oluşur ve ekran perdesi vardır, tutamağı sürüklenebilir; Oyun ekle yeniden çalışır.")),
    "apps" to (tr("Terminal: drawn as a terminal with colours and a built-in neofetch; Neofetch also plays while charging and as a screen cover page.",
        "Terminal: renkli ve yerleşik neofetch'li gerçek terminal görünümü; Neofetch şarj animasyonunda ve ekran perdesi sayfası olarak da var.")),
    "info" to (tr("Apps: root or Shizuku removes an app at once, and Play Store links can open in Pulse. Device: processor, temperature, GPU and memory also work without root.",
        "Uygulamalar: root veya Shizuku uygulamayı hemen kaldırır, Play Store linkleri Pulse'ta açılabilir. Cihaz: işlemci, sıcaklık, GPU ve bellek root olmadan da çalışır.")),
    "keyboard" to (tr("Keyboard: the bar is not empty for unknown words, names keep their capitals, earlier wrong words are mended in the background, and a stack overflow is fixed.",
        "Klavye: bilinmeyen kelimede çubuk boş kalmaz, isimler büyük harfle kalır, önceki yanlış kelimeler arkadan düzeltilir, bir yığın taşması hatası giderildi.")),
)

/** After an update: what is new in this version, nothing to set up again (permissions and terms were done on the first start). */
@Composable
fun WhatsNew(onDone: () -> Unit) {
    val ctx = LocalContext.current
    fun finish() { ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putBoolean("welcomed", true).putString("seen_version", appVersionName(ctx)).apply(); onDone() }
    Column(Modifier.fillMaxSize().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 40.dp, bottom = 24.dp)) {
        Image(rememberLauncherLogo(), null, Modifier.size(72.dp))
        Text(tr("Pulse is updated", "Pulse güncellendi"), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(top = 12.dp))
        Text(tr("Version ", "Sürüm ") + appVersionName(ctx), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        WelcomeCard(tr("What's new", "Yenilikler")) {
            WHATS_NEW.forEach { (ic, line) ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { PIcon(ic, Look.accent, 22.dp) }
                    Text(line, color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(start = 10.dp, top = 4.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 18.dp), contentAlignment = Alignment.Center) { PButton(tr("Continue", "Devam"), onClick = { finish() }) }
    }
}


/** The app's own page in Android settings (Permissions is one tap from there). */
private fun openAppSettings(c: android.content.Context) {
    runCatching {
        c.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + c.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        android.widget.Toast.makeText(c, tr("Open Permissions there and allow it", "Orada İzinler'e girip izin ver"), android.widget.Toast.LENGTH_LONG).show()
    }
}
