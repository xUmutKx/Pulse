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
    "kbd15" to (tr("Windows 11: Start, Search, quick settings and the clock each open their own panel. Files: a file's menu is two rows of five icons, Secure Folder copies first so nothing is lost. YTXTube: tappable links, Open as Music, an easier mini player. The keyboard backup holds the theme, size and key rows.",
        "Windows 11: Başlat, Arama, hızlı ayarlar ve saat kendi panelini açıyor. Dosyalar: dosya menüsü 5'er ikonluk iki satır, Güvenli Klasör önce kopyalıyor, hiçbir şey kaybolmuyor. YTXTube: tıklanan bağlantılar, Müzik olarak aç, daha kolay mini oynatıcı. Klavye yedeği tema, boyut ve tuş satırlarını tutuyor. Pulse ikonu siyah, işlem listesinde sütun çizgileri.")),
    "kbd14" to (tr("Code is an app of its own (AMOLED, Run for pages), Files can move a file to Secure Folder, the keyboard's calculator answers on the bar and its Dictionary reads TDK, online songs have covers in the notification and go on with a mix, tablets get bigger icons and right click.",
        "Kod artık ayrı bir uygulama (AMOLED, sayfalar için Çalıştır), Dosyalar Güvenli Klasör'e taşıyabiliyor, klavyenin hesap makinesi sonucu çubukta veriyor ve Sözlük TDK'yı okuyor, çevrimiçi şarkıların bildiriminde kapak var ve bir mix ile devam ediyor, tabletlerde daha büyük ikonlar ve sağ tık.")),
    "kbd13" to (tr("Keyboard: backspace takes only the space after a dot, file names like test.py stay joined, command words only in terminals, a waiting file shows its name. Shuffle beside Play, square online covers, ruled process list.",
        "Klavye: silme noktadan sonraki boşluğu siler, test.py gibi adlar bitişik kalır, komut önerileri yalnız terminalde, bekleyen dosyanın adı görünür. Karıştır Çal'ın yanında, kare çevrimiçi kapaklar, çizgili işlem listesi.")),
    "kbd12" to (tr("Covers for YouTube Music and Spotify songs, Spotify login in the music page, Game Hub's Look setting reachable again, the YTXTube opening back, and no stray key when the keyboard comes up.",
        "YouTube Music ve Spotify şarkılarında kapaklar, müzik sayfasında Spotify girişi, Game Hub görünüm ayarı yine erişilebilir, YTXTube açılışı geri, klavye açılırken yanlışlıkla tuş yazılmıyor.")),
    "kbd11" to (tr("Lyrics notification only for a song that was played, keyboard tools (Dictionary, Calculator, Convert) back in the menu, Pulse AI finds models already on the phone, a crash fix in the keyboard's suggestions.",
        "Söz bildirimi yalnız çalınmış şarkıda, klavye araçları (Sözlük, Hesap makinesi, Çevirici) menüde geri, Pulse AI telefondaki hazır modelleri buluyor, klavye önerilerinde çökme düzeltmesi.")),
    "kbd10" to (tr("Keyboard: a very long text no longer freezes it, a Special signs family with 25 groups in Symbols, a higher emoji bottom row with a space bar. The lyrics notification stays while the song is paused and goes when the player closes.",
        "Klavye: çok uzun metin artık takılmaya yol açmaz, Semboller'de 25 gruplu Özel işaretler ailesi, boşluk çubuklu daha yüksek emoji alt sırası. Söz bildirimi duraklatınca kalır, oynatıcı kapanınca gider.")),
    "kbd9" to (tr("Keyboard: Dictionary, Calculator, Convert, Stickers and Grammar tiles in the menu, a GIF button by the emoji search, and the hybrid correction on by default. YTXTube's Downloads tab is back and lists the Pulse app's downloads too. Toolkit tools open in the look's dialog box; DNS and TTL has its own DNS for Wi-Fi and mobile data and an app access list. The PDF viewer scrolls on its empty sides and zooms around your fingers; the emoji panel has a GIF page; YTXTube's small window moves elastically.",
        "Klavye: menüde Sözlük, Hesap makinesi, Çevirici, Çıkartmalar ve Dilbilgisi, emoji aramasının yanında GIF düğmesi, hibrit düzeltme varsayılan açık. YTXTube'un İndirilenler sekmesi geri geldi, Pulse'un indirmelerini de listeler. Toolkit araçları görünümün diyalog kutusunda açılır; DNS ve TTL'de Wi-Fi ve mobil veri için ayrı DNS ve uygulama erişim listesi var. PDF görüntüleyici boş kenarlarda da kayar ve parmakların etrafında büyür; emoji panelinde GIF sayfası var; YTXTube'un küçük penceresi esnek hareket eder.")),
    "kbd8" to (tr("Keyboard: six new themes, corner swipes on the number keys with presets (old phone letters, edit functions), an Old phone theme, the Passwords key in the menu, a calculator (12*5=), abbreviations and chemical names it no longer breaks (tbmm becomes TBMM), and a one-tap Whisper model download. YTXTube's cinema view is smoother. A crash when Game Hub opened while YTXTube ran is fixed. From WM Keyboard: settings search, a typing speed test, encrypted backups, GIF and smarter emoji search, codes from notifications, works before unlock. Music: settings on the blended cover, YouTube Music and Spotify cards in the library. Files has a code editor with colours for about 40 languages, folding and search, and Notes has a Code mode. Torrents choose Wi-Fi or mobile data by themselves, and a performance log (Report > Crash and freeze log > Performance) records what makes the phone lag.",
        "Klavye: altı yeni tema, rakam tuşlarında köşe kaydırması (eski telefon harfleri, düzenleme işlevleri), Eski telefon teması, menüde Parolalar tuşu, hesap makinesi (12*5=), bozmadığı kısaltma ve kimyasal adlar (tbmm → TBMM) ve tek dokunuşla Whisper modeli indirme. YTXTube sinema görünümü daha akıcı. Game Hub, YTXTube çalışırken açılınca olan çökme giderildi. WM Keyboard'dan: ayar arama, yazma hız testi, şifreli yedek, GIF ve daha akıllı emoji arama, bildirimden kod, kilit açılmadan çalışma. Müzik: ayarlar blended kapakta, kütüphanede YouTube Music ve Spotify kartları. Files'ta yaklaşık 40 dil için renkli, katlamalı, aramalı kod editörü, Notlar'da Kod modu var. Torrentler Wi-Fi ya da mobil veriyi kendisi seçer; performans kaydı (Rapor > Çökme ve donma kaydı > Performans) telefonu neyin kastığını kaydeder.")),
    "hybrid7" to (tr("Keyboard: Hybrid correction (Settings > Typing, off by default) runs WM Keyboard's correction engine beside Pulse's, with a confidence gate, typing rhythm and missed-space fixes. Music can show YouTube Music and Spotify songs in its search. YTXTube can keep a video playing until the next is ready, its settings sheets are darker and its playback settings have icon switches. Windows Phone launcher without the left dock, Everest redrawn, Files opens code with colours.",
        "Klavye: Hibrit düzeltme (Ayarlar > Yazım, varsayılan kapalı) WM Keyboard'un düzeltme motorunu Pulse'unkiyle birlikte çalıştırır; güven modeli, yazım ritmi ve unutulan boşluk düzeltmesi var. Müzik aramada YouTube Music ve Spotify şarkılarını gösterebilir. YTXTube sonraki hazır olana kadar videoyu çalmaya devam edebilir, ayar sayfaları daha koyu, oynatma ayarlarında ikon düğmeler var. Windows Phone launcher sol dock'suz, Everest yeniden çizildi, Files kodu renkli açar.")),
    "more6" to (tr("YTXTube: Watch now for series and movies, playlists with cover, Play all and Shuffle, Not interested and hiding a channel, the home feed learns the channels you watch, the black of the cinema view takes gestures, repeat list or one song. Keyboard: offline Whisper voice typing, a copied text stays while you type, a retyped word is learned. Files opens code and text with colours. Torrents can use mobile data on a blocking Wi-Fi.",
        "YTXTube: diziler ve filmler için Hemen izle, kapaklı oynatma listeleri, Hepsini oynat ve Karıştır, İlgilenmiyorum ve kanalı gizle, ana sayfa izlediğin kanalları öğrenir, sinema görünümünün siyahı dokunuşları alır, liste ya da tek şarkı tekrar. Klavye: çevrimdışı Whisper ile sesle yazma, kopyalanan metin yazarken durur, yeniden yazılan kelime öğrenilir. Files kod ve metni renkli açar. Torrentler engelleyen Wi-Fi'de mobil veri kullanabilir.")),
    "kbd6" to (tr("Keyboard: Gamepad, Emoji, AI fix and Key layout left the ⋮ menu; Quick auth is a top-bar icon again (or in the menu, your choice), and its password chips show only in number boxes. YTXTube's Saved tab is now Watch (Series and movies, no download manager). The weather notification shows emoji signs by default.",
        "Klavye: Gamepad, Emoji, AI fix ve Key layout ⋮ menüsünden kalktı; Hızlı kimlik yine üst çubukta (ya da menüde, sen seç) ve şifre önerileri yalnızca sayı kutularında çıkar. YTXTube'da Saved sekmesi İzle oldu (Diziler ve filmler, indirme yöneticisi yok). Hava durumu bildirimi varsayılan olarak emoji işaretleri gösterir.")),
    "tool" to (tr("Fixed a crash in the Toolkit windows (a tool with a scrolling page of its own).",
        "Araç kutusu pencerelerindeki bir çökme düzeltildi (kendi kaydırmalı sayfası olan bir araç).")),
    "game" to (tr("Game Hub opens on the normal (web) shelf again; the native shelf is a choice in its settings and now keeps only the games near the middle in memory. Pulse Watch has Apps, Files, Water, Tip, Reaction and Convert.",
        "Game Hub yine normal (web) rafla açılıyor; yerel raf ayarlarından seçilir ve artık yalnızca ortadaki oyunların çevresini bellekte tutuyor. Pulse Watch'ta Uygulamalar, Dosyalar, Su, Bahşiş, Tepki ve Çevirici var.")),
    "video" to (tr("YTXTube: the quality panel and the settings sheet are the same dark glass with the controls hidden behind them, the edge of the video is a dead zone for the system's swipes, and leaving full screen turns the page upright even when the rotation is locked. The first start no longer shows a blank window.",
        "YTXTube: kalite paneli ve ayar sayfası aynı koyu cam ve kontroller arkasında gizli, videonun kenarı sistemin kaydırmaları için ölü bölge, ve tam ekrandan çıkınca dönme kilitli olsa bile sayfa dikeye dönüyor. İlk açılışta boş pencere görünmüyor.")),
    "game" to (tr("Game Hub hides the games it knows from the phone by default (root or Shizuku) and lists the hidden ones in Select games; its native shelf has more animations. YTXTube: previous and next video buttons, fades between videos, a black centred player from the lower page, a start animation. The keyboard pastes pictures into more fields and a copied file waits in the bar. Files columns fit their names and show a text preview. The rain in Weather is real rain.",
        "Game Hub bildiği oyunları varsayılan olarak telefondan gizler (root ya da Shizuku) ve gizlileri Oyunları seç'te listeler; yerel rafında daha fazla animasyon var. YTXTube: önceki ve sonraki video düğmeleri, videolar arası geçiş, alt sayfadan siyah ortalanmış oynatıcı, açılış animasyonu. Klavye resimleri daha fazla alana yapıştırır ve kopyalanan dosya çubukta bekler. Dosya sütunları adlarına sığar ve metin önizlemesi gösterir. Hava durumundaki yağmur gerçek yağmur.")),
    "game" to (tr("Game Hub has a native shelf drawn by Rust (turn it on in its settings), about 70 MB less is kept for built-in game boxes, and hidden apps stay in its lists. The keyboard is faster (its language model also runs in Rust) and suggests the next word by the whole sentence. Series and movies can use Pulse's torrent engine or a local torrent server. Pulse keeps its folders under Download/Pulse.",
        "Game Hub'ın Rust ile çizilen yerel bir rafı var (ayarlarından açılır), yerleşik oyun kutuları için yaklaşık 70 MB daha az yer tutuluyor, gizli uygulamalar listelerinde kalıyor. Klavye daha hızlı (dil modeli de Rust'ta çalışır) ve sonraki kelimeyi tüm cümleye göre öneriyor. Diziler ve filmler Pulse'un torrent motorunu ya da yerel bir torrent sunucusunu kullanabiliyor. Pulse klasörlerini Download/Pulse altında tutuyor.")),
    "weather" to (tr("Weather scenery sits under a soft shade so towers and roofs no longer run through the cards. YTXTube's quality list is a clear glass panel at the right of the screen. The installer opens one APK in the same floating window as an APK opened from outside, and XAPK / APKS / APKM files open in Pulse from a file app. Game Hub lists games hidden outside Pulse (Clash Royale, Clash of Clans...) so they can be shown or hidden. The Auto clicker page is a small white note.",
        "Hava durumu manzarası yumuşak bir gölgenin altında, kuleler ve çatılar artık kartların içinden geçmiyor. YTXTube'da kalite listesi ekranın sağında şeffaf cam panel. Yükleyici tek APK'yı dışarıdan açılan APK ile aynı yüzen pencerede açıyor; XAPK / APKS / APKM dosyaları dosya uygulamasından Pulse'ta açılıyor. Game Hub, Pulse dışında gizlenen oyunları (Clash Royale, Clash of Clans...) listeliyor. Otomatik tıklayıcı sayfası küçük beyaz bir not.")),
    "palette" to (tr("Keyboard: colours or a picture for each key cap, ready colour sets, your own space bar figure that faces the way it walks, RGB ring round a pressed key. Launcher: windows blur all over, widgets drag after a hold, start menus keep their size in landscape.",
        "Klavye: her tuş kapağına renk ya da resim, hazır renk setleri, gittiği yöne bakan kendi boşluk tuşu figürün, basılan tuşta RGB halka. Launcher: pencere bulanıklığı her yerde, widget'lar basılı tutunca sürükleniyor, başlat menüleri yatayda kendi boyunda.")),
    "weather" to (tr("Weather now has drawn scenery: mountains, sea, city, forest, street or highland under the sky of the hour, with rain, snow and fog. A new Weather, scenery widget shows it on the home screen.",
        "Hava durumunda çizimli manzaralar var: saate göre gökyüzü altında dağ, deniz, şehir, orman, sokak ya da yayla; yağmur, kar ve sis ile. Yeni Hava durumu, manzara widget'ı bunu ana ekranda gösterir.")),
    "touch" to (tr("New Auto clicker in the Toolkit and as a Quick Settings tile: a floating bar and target marks you drag to the spots to tap, 1 to 10 spots, start / stop and speed.",
        "Araç kutusunda ve Hızlı Ayarlar karosu olarak yeni Otomatik tıklayıcı: yüzen çubuk ve dokunulacak noktalara sürüklediğin hedef işaretleri, 1 ile 10 nokta, başlat / durdur ve hız.")),
    "keyboard" to (tr("In Termux the bar offers commands and words you typed, nothing is autocorrected, and holding Delete works.",
        "Termux'ta çubuk komutları ve yazdığın kelimeleri öneriyor, otomatik düzeltme yok, Sil tuşunu basılı tutmak çalışıyor.")),
    "download" to (tr("Stremio's local links download as the torrent itself. Series and movies prefers streams with 70+ people sharing and sizes up to twice the one you set, and opens in YTXTube's black AMOLED look. The keyboard offers a file at the right of the bar after five letters of its name.",
        "Stremio'nun yerel bağlantıları torrentin kendisi olarak iniyor. Diziler ve filmler 70+ kişinin paylaştığı yayınları ve ayarladığın boyutun iki katına kadar olanları seçiyor, YTXTube'da siyah AMOLED görünümüyle açılıyor. Klavye bir dosyanın adının beş harfinden sonra dosyayı çubuğun sağında öneriyor.")),
    "trash" to (tr("Deleting a download asks first, with Delete from the device ticked; the emptied folder goes too. Series downloads keep their original file names.",
        "İndirmeyi silmek önce soruyor, Cihazdan sil işaretli geliyor; boşalan klasör de siliniyor. Dizi indirmeleri özgün dosya adını koruyor.")),
    "convert" to (tr("PowerPoint to PDF (and spreadsheets to PDF); Convert files is in Office tools too, with icons and descriptions. The Received files shortcut is gone from Files home.",
        "PowerPoint'ten PDF'e (tablolar da PDF'e); Dosya dönüştür Office araçlarında da, simgeler ve açıklamalarla. Alınan dosyalar kısayolu Dosyalar ana ekranından kalktı.")),
    "heart" to (tr("No more notification dots on the app icons for weather, playback and Buds cards. YTXTube's headphones button brings the video back when you return to the app.",
        "Hava durumu, oynatma ve Buds kartları için uygulama simgelerinde artık bildirim rozeti yok. YTXTube'un kulaklık düğmesi uygulamaya dönünce videoyu geri getiriyor.")),
    "music" to (tr("Fixed: the music player's media card no longer pops up when nothing plays, an old song no longer starts on a volume press after a headset was plugged in, and YTXTube's picture-in-picture Next never opens a song.",
        "Düzeltildi: müzik çaların medya kartı hiçbir şey çalmazken çıkmıyor, kulaklık takıldıktan sonra ses tuşuna basınca eski şarkı başlamıyor, YTXTube pencere-içi pencerenin İleri tuşu şarkı açmıyor.")),
    "palette" to (tr("Two new looks: Cloud and Cloud light, soft clouds behind frosted panels (Look > Other colors). Pulse Watch builds again.",
        "İki yeni görünüm: Cloud ve Cloud light, buzlu panellerin arkasında yumuşak bulutlar (Görünüm > Diğer renkler). Pulse Watch yeniden derleniyor.")),
    "keyboard" to (tr("Keyboard smooth mode: no animations, effects, RGB light or neural weighing, for a phone that lags (also in Toolkit > Performance).",
        "Klavye akıcı mod: animasyon, efekt, RGB ışık ve sinirsel tartım yok, kasan telefon için (Araç kutusu > Performans'ta da var).")),
    "keyboard" to (tr("Brands, apps and creators are written their own way: Starbucks, McDonald's, YouTube, iPhone, MrBeast, and Starbucks'ta in Turkish.",
        "Markalar, uygulamalar ve içerik üreticileri kendi yazılışıyla: Starbucks, McDonald's, YouTube, iPhone, MrBeast, Türkçede Starbucks'ta.")),
    "keyboard" to (tr("Personal data in one section, circle and round-trip gestures, a phone keypad, a picture and a walking figure on the space bar, no cursor jumps on space slides.",
        "Kişisel veriler tek bölümde, daire ve gidiş-dönüş hareketleri, telefon tuş takımı, boşluk tuşunda resim ve yürüyen figür, boşlukta kaydırınca imleç zıplamıyor.")),
    "download" to (tr("Quick paste: a file just downloaded or a video just recorded waits in the keyboard bar, like a screenshot.",
        "Hızlı yapıştırma: yeni inen dosya ya da yeni çekilen video da klavye çubuğunda bekliyor, ekran görüntüsü gibi.")),
    "speed" to (tr("Toolkit > Performance: Pulse's processes and the other running apps (root or Shizuku) with their memory, stopped one by one or all at once.",
        "Araç kutusu > Performans: Pulse'un işlemleri ve çalışan diğer uygulamalar (root ya da Shizuku) bellekleriyle, tek tek ya da hepsi birden durdurulur.")),
    "clock" to (tr("Toolkit > Watch: pair with a Wear OS watch over wireless debugging, install Pulse Watch or any APK, send files, manage its apps, see its screen.",
        "Araç kutusu > Saat: Wear OS saatle kablosuz hata ayıklama üzerinden eşleş, Pulse Watch ya da herhangi bir APK kur, dosya gönder, uygulamalarını yönet, ekranını gör.")),
    "video" to (tr("YTXTube channels: Playlists tab and Latest / Popular / Oldest; playlist search; opens on Home; the video follows the finger when full screen is pulled down.",
        "YTXTube kanalları: Oynatma listeleri sekmesi ve En yeni / Popüler / En eski; oynatma listesi arama; Ana sayfada açılır; tam ekran aşağı çekilince video parmağı izler.")),
    "video" to (tr("YTXTube: the channel's own videos under a video, and one slide at the right edge goes from silent to full volume.",
        "YTXTube: videonun altında kanalın kendi videoları da var, sağ kenarda tek kaydırma sesi sıfırdan tama çıkarır.")),
    "music" to (tr("YTXTube Music: Song / Video switch, a thumb down, a queue button; word-by-word lyrics on the lock screen, floating lyrics and the notification.",
        "YTXTube Müzik: Şarkı / Video geçişi, beğenmeme, sıra tuşu; kelime kelime sözler kilit ekranında, yüzen sözlerde ve bildirimde.")),
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
