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
    "music" to (tr("YTXTube's music player has a Song / Video switch at the top like YouTube Music, and a thumb down: the song is not suggested again and the next one plays. Series and movies is a page of shelves (popular series and movies) in the Library tab. Queue without messages: a queue button in the music player and Add to queue in the track menu. Pulse's old launcher icons are in Extra apps > icon settings.",
        "YTXTube'un müzik çalarında YouTube Music gibi üstte Şarkı / Video geçişi var, bir de başparmak aşağı: şarkı bir daha önerilmez ve sıradaki çalar. Diziler ve filmler Library sekmesinde raflı bir sayfa (popüler diziler ve filmler). Mesajsız kuyruk: müzik çalarda kuyruk düğmesi, parça menüsünde Kuyruğa ekle. Pulse'ın eski başlatıcı ikonları Extra apps > ikon ayarlarında.")),
    "video" to (tr("Series and movies is a screen of its own from YTXTube. Type the size in GB (it looks from a quarter to nearly twice, bigger first), tap an episode to see who shares each stream, and a torrent nobody shares gives way after 10 seconds. More trackers, also over HTTPS.",
        "Diziler ve filmler YTXTube'dan kendi ekranında açılıyor. Boyutu GB olarak yaz (dörtte birinden neredeyse iki katına kadar bakar, büyükler önce), bir bölüme dokununca her yayını kaç kişinin paylaştığını gör; kimse paylaşmıyorsa 10 saniye sonra diğerine geçer. Daha çok tracker, HTTPS dahil.")),
    "download" to (tr("Packs moved: Settings > About (Downloaded packs) and the launcher's Microsoft Store, drawn like Windows 11's Store. The Microsoft Store is in the Windows 10 look too. Storage opens with a summary by kind, says what each folder holds, and Game Hub no longer piles up old boxes.",
        "Paketler taşındı: Ayarlar > Hakkında (İndirilen paketler) ve launcher'daki Microsoft Store (Windows 11 Mağazası gibi çizili). Microsoft Store Windows 10 görünümünde de var. Depolama türe göre özetle açılıyor, her klasörün ne tuttuğunu söylüyor, Game Hub artık eski kutuları biriktirmiyor.")),
    "storage" to (tr("Files on a wide tablet shows columns like a computer: places, a column per folder, and the picked file's picture and details at the right. The Columns switch in the menu brings the list back.",
        "Geniş tablette Dosyalar bilgisayardaki gibi sütunlarla açılıyor: konumlar, her klasör için bir sütun, sağda seçilen dosyanın resmi ve ayrıntıları. Menüdeki Sütunlar anahtarı listeyi geri getirir.")),
    "video" to (tr("Series and movies has a back button and follows the look (see-through on Windows 11), and YTXTube's Saved tab opens it. Launcher glass has a new Windows Material style with darker, lighter and transparency settings. Game Hub shows all 380 games, and the action buttons open around your finger. More Windows XP and 10 wallpapers.",
        "Diziler ve filmler geri tuşuna sahip ve görünümü izliyor (Windows 11'de şeffaf), YTXTube'un Kaydedilenler sekmesi de onu açıyor. Başlatıcı camında yeni Windows Material stili: daha koyu, daha açık ve şeffaflık ayarı. Game Hub 380 oyunun hepsini gösteriyor, aksiyon tuşları parmağının etrafında açılıyor. Daha fazla Windows XP ve 10 duvar kağıdı.")),
    "heart" to (tr("Less battery use: the loops of the screen stop while the app is in the background, the sampler reads less often, widgets are redrawn less, Game Hub's idle halo is still. Gym exercises show a two-photo loop. Icon packs, bird sounds, the gym data and better wallpapers are downloads that come by themselves where they are used.",
        "Daha az pil: ekranın döngüleri uygulama arka plandayken duruyor, örnekleyici daha seyrek okuyor, widget'lar daha seyrek çiziliyor, Game Hub'ın boştaki halesi durgun. Spor egzersizleri iki fotoğraflı döngü gösteriyor. Simge paketleri, kuş sesleri, spor verisi ve daha iyi duvar kağıtları kullanıldığı yerde kendiliğinden iniyor.")),
    "palette" to (tr("Downloads where you use them: more Winamp skins in the skin picker, and higher-quality versions of the Windows 11 (4K), Windows 10 and Windows 7 wallpapers in the wallpaper pickers of Pulse and the launcher.",
        "İndirmeler kullandığın yerde: tema seçicide daha fazla Winamp teması, Pulse ve başlatıcının duvar kağıdı seçicilerinde Windows 11 (4K), Windows 10 ve Windows 7 duvar kağıtlarının yüksek kaliteli halleri.")),
    "music" to (tr("The lyrics already on your phone get their word times once, by themselves, in the background. Settings > Lyrics has the switch and an Add now button.",
        "Telefondaki sözler bir kez, kendiliğinden, arka planda kelime zamanı alıyor. Ayarlar > Sözler'de anahtar ve Şimdi ekle düğmesi var.")),
    "music" to (tr("Lyrics word by word, even syllable by syllable, when the song has them: LRCLIB's word-timed records and NetEase as a second source. Each word fills as it is sung; without word times the lines follow, then plain text. A song with line-timed lyrics gets its words in the background.",
        "Sözler kelime kelime, varsa hece hece: LRCLIB'in kelime zamanlı kayıtları ve ikinci kaynak olarak NetEase. Her kelime söylenirken doluyor; kelime zamanı yoksa satırlar, o da yoksa düz metin. Satır zamanlı sözü olan şarkı kelimelerini arka planda alıyor.")),
    "palette" to (tr("Smaller app: 64-bit by default (a separate 32-bit APK for old phones), most Winamp skins are a download in the Extra store, Chinese, Japanese and Korean PDF character maps left out, launcher icons as WebP.",
        "Daha küçük uygulama: varsayılan 64 bit (eski telefonlar için ayrı 32 bit APK), Winamp temalarının çoğu Ekstra mağazadan indirme, Çince, Japonca ve Korece PDF karakter haritaları çıkarıldı, başlatıcı ikonları WebP.")),
    "palette" to (tr("Notes looks like Samsung Notes: one tool strip under the title (pen, marker, eraser, lasso, undo, redo), colours and widths in a slim row under it. Palm rejection (off by default) in Notes settings. A linked folder shows at once.",
        "Notlar Samsung Notes gibi: başlığın altında tek araç şeridi (kalem, fosforlu, silgi, kement, geri al, yinele), altında ince renk ve kalınlık satırı. Avuç reddi (varsayılan kapalı) Notlar ayarlarında. Bağlanan klasör hemen görünüyor.")),
    "palette" to (tr("The Message button other apps show for Pulse (Nova search, share lists) follows the glass look of the Messages icon.",
        "Diğer uygulamaların Pulse için gösterdiği Mesaj butonu (Nova arama, paylaşım listeleri) Mesajlar ikonunun cam görünümünü izliyor.")),
    "video" to (tr("YTXTube music: the songs after a searched song are the same artist's other songs, not its remixes. Playlist covers have a play button in the corner, outside and inside. YTXTube's icon is a white play button on black.",
        "YTXTube müzik: aranan şarkıdan sonra gelenler aynı sanatçının diğer şarkıları, remiksler değil. Liste kapaklarının köşesinde oynat tuşu var, dışarıda ve içeride. YTXTube simgesi siyah üstüne beyaz oynat tuşu.")),
    "palette" to (tr("Task Manager without root lists Pulse's own parts: the extra apps and the services that run. Network lab and Appearance groups from the last version are in too.",
        "Root'suz Görev Yöneticisi Pulse'un kendi parçalarını listeliyor: ekstra uygulamalar ve çalışan servisler. Önceki sürümün Ağ laboratuvarı ve Görünüm grupları da içinde.")),
    "share" to (tr("One Pulse folder in Downloads for everything. Share lists what you received, with pictures, and a transfer is a notification with progress, Pause and Cancel.",
        "İndirilenler'de her şey için tek Pulse klasörü. Share, alınanları resimleriyle listeliyor, aktarım ilerleme çubuğu, Duraklat ve İptal'li bir bildirim.")),
    "palette" to (tr("Glass icons in the colours of Glass Material You, a screenshot stays in the bar for a minute, blocked words are gone for good.",
        "Cam simgeler Glass Material You renklerinde, ekran görüntüsü bar'da bir dakika kalıyor, engellenen kelimeler tamamen gidiyor.")),
    "video" to (tr("YTXTube is much lighter: its own process, video straight to the screen, hardware codecs only. It stops when the music player starts, so the media controls show the music.",
        "YTXTube çok daha hafif: kendi süreci, video doğrudan ekrana, yalnızca donanım kodekleri. Müzik çalar başlayınca duruyor, medya kontrolleri müziği gösteriyor.")),
    "palette" to (tr("Keyboard: correct place when the screen is upside down, free RGB effect colour, an RGB effect behind the whole keyboard.",
        "Klavye: ekran ters çevrilince doğru yerde, serbest RGB efekt rengi, tüm klavyenin arkasında RGB efekti.")),
    "palette" to (tr("Fixed a crash in the Neofetch card (the ASCII logo).",
        "Neofetch kartındaki (ASCII logo) çökme düzeltildi.")),
    "palette" to (tr("Keyboard: suggestions work again in Game Hub's search and in YTXTube's sign-in, without the freezes.",
        "Klavye: Game Hub aramasında ve YTXTube girişinde öneriler yine çalışıyor, donma olmadan.")),
    "music" to (tr("Long song, artist and album names scroll once and stand still, instead of ending in dots. Lyrics are smaller: lines with times 4 sizes, without times 8.",
        "Uzun şarkı, sanatçı ve albüm adları noktalarla bitmek yerine bir kez kayıp duruyor. Sözler daha küçük: zamanlı satırlar 4, zamansızlar 8 boy.")),
    "video" to (tr("YTXTube's icon is a white line mark on AMOLED black.",
        "YTXTube simgesi AMOLED siyah üstüne beyaz çizgi.")),
    "palette" to (tr("Windows XP start menu as XP drew it, the desktop games removed, YTXTube's own icon.",
        "Windows XP başlat menüsü XP'deki gibi, masaüstündeki oyunlar kaldırıldı, YTXTube'un kendi simgesi.")),
    "palette" to (tr("Downloads: Series and movies opens full screen. Keyboard: a picture behind the keys, darkened by a slider in Effects.",
        "İndirmeler: Diziler ve filmler tam ekran açılıyor. Klavye: Efektler'de karartma kaydırıcısıyla tuşların arkasında resim.")),
    "palette" to (tr("Keyboard: no more freezes in web pages (YTXTube sign-in, Game Hub search), hold 3 on the number pad for passwords, four RGB press effects with colour and length, lighter effects. The floating keyboard's edge keys are whole again.",
        "Klavye: web sayfalarında (YTXTube girişi, Game Hub araması) donma yok, rakam tuşlarında 3'e basılı tut: şifreler, dört RGB tuş efekti, renk ve süre ayarı, daha hafif efektler. Yüzen klavyenin kenar tuşları yine tam.")),
    "video" to (tr("YTXTube opens again. Downloads: Series and movies has its own page. Windows 11 launcher: choose the taskbar glass. Music: the library shows as the player is pulled down.",
        "YTXTube yine açılıyor. İndirmeler: Diziler ve filmler kendi sayfasında. Windows 11 başlatıcı: görev çubuğu camını seç. Müzik: oynatıcı çekilirken kütüphane görünür.")),
    "video" to (tr("YTXTube: YouTube and YouTube Music without ads as an extra app, with SponsorBlock, downloads and background play. AMOLED and light by default, a headphones button on the mini player, songs saved to Music with their cover and tags.",
        "YTXTube: reklamsız YouTube ve YouTube Music ekstra uygulama olarak, SponsorBlock, indirme ve arka planda oynatmayla. Varsayılan AMOLED ve hafif, mini oynatıcıda kulaklık düğmesi, şarkılar kapağı ve etiketleriyle Müzik'e kaydedilir.")),
    "music" to (tr("Music: search with icons for songs, albums, artists and lyrics, artists in a round row on top, album and artist pages that return to the search. The Blended cover is on by default behind every page.",
        "Müzik: şarkı, albüm, sanatçı ve sözler için ikonlu arama, üstte yuvarlak sanatçı sırası, aramaya geri dönen albüm ve sanatçı sayfaları. Blended kapak varsayılan açık ve her sayfanın arkasında.")),
    "palette" to (tr("Screen cover: a frameless AMOLED dashboard, pages grouped by kind, two full-screen wave charging animations and the real neofetch logo. Files, Game Hub search and the launcher settings colours are fixed and polished.",
        "Ekran kapağı: çerçevesiz AMOLED pano, türe göre gruplanmış sayfalar, iki tam ekran dalga şarj animasyonu ve gerçek neofetch logosu. Files, Game Hub araması ve launcher ayar renkleri düzeltildi.")),
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
