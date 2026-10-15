package com.umutk.pulse.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr

/** One manual entry: [id] is what GuideDialog opens, [key] the icon the feature uses elsewhere, [en] and [tr] the plain description. */
class GuideEntry(val id: String, val key: String, val title: String, val en: String, val tr: String, val titleTr: String = title)

/** The manual: add a feature by adding a line here. Descriptions say what a feature is and does, never how to operate it. */
val GUIDE = listOf(
    GuideEntry("processes", "processes", "Processes",
        "A task-manager table of the apps and processes that are running, with CPU, memory, disk and network columns and a heat map behind the numbers. Use it to find what is using the phone's resources. Listing works without root; ending or controlling processes needs root.",
        "Çalışan uygulama ve işlemlerin CPU, bellek, disk ve ağ sütunlarıyla görev yöneticisi tablosu; sayıların arkasında ısı haritası vardır. Telefonun kaynaklarını neyin yediğini bulmak için kullanılır. Listeleme root istemez; işlemleri sonlandırmak root ister.",
        "İşlemler"),
    GuideEntry("performance", "performance", "Performance",
        "Live graphs of processor, memory, storage, network and battery, with the numbers behind them (load, per-core speed, temperatures, charging power, disk traffic, where the space goes). Use it to see how the phone is doing right now. Some readings are hidden by Android on certain phones, and then the page says so.",
        "İşlemci, bellek, depolama, ağ ve pil için canlı grafikler ve arkasındaki sayılar (yük, çekirdek hızları, sıcaklıklar, şarj gücü, disk trafiği, alanın nereye gittiği). Telefonun şu an nasıl çalıştığını görmek için kullanılır. Bazı telefonlarda Android bazı değerleri gizler; sayfa bunu belirtir.",
        "Performans"),
    GuideEntry("history", "history", "App history",
        "How long each app was used and how many times the phone was unlocked, read from Android's usage statistics. Notification counts can be switched on too. It needs the Usage access permission, which you grant in Android's settings.",
        "Her uygulamanın ne kadar kullanıldığı ve telefonun kaç kez açıldığı, Android'in kullanım istatistiklerinden okunur. Bildirim sayıları da açılabilir. Android ayarlarından verilen Kullanım erişimi izni gerekir.",
        "Uygulama geçmişi"),
    GuideEntry("startup", "startup", "Startup apps",
        "The apps that start themselves when the phone boots. The list is always readable; switching an app's boot start on or off needs root. Use it to keep rarely used apps quiet after a restart.",
        "Telefon açılırken kendiliğinden başlayan uygulamalar. Liste her zaman okunur; bir uygulamanın açılışta başlamasını açıp kapatmak root ister. Az kullanılan uygulamaları yeniden başlatmadan sonra sessiz tutmak için kullanılır.",
        "Başlangıç uygulamaları"),
    GuideEntry("details", "details", "Details",
        "A plain table of every process with its PID, user, CPU, memory, virtual size, priority and parent, sortable and filterable by name, PID or user. It is the detailed view behind Processes.",
        "Her işlemin PID, kullanıcı, CPU, bellek, sanal boyut, öncelik ve üst işlem bilgisiyle düz tablosu; ada, PID'ye veya kullanıcıya göre sıralanır ve süzülür. İşlemler sayfasının ayrıntılı halidir.",
        "Ayrıntılar"),
    GuideEntry("services", "services", "Services",
        "Android services: the ones running right now (root needed to read them) and every service the installed apps declare, including disabled ones. Use it to see which apps keep background services alive.",
        "Android servisleri: şu an çalışanlar (okumak için root gerekir) ve yüklü uygulamaların tanımladığı tüm servisler, kapalı olanlar dahil. Hangi uygulamaların arka plan servisi çalıştırdığını görmek için kullanılır.",
        "Servisler"),
    GuideEntry("apps", "apps", "Apps / App manager",
        "Every installed app with its size, version and install date, with filters and sorting. An app opens its details, permissions and actions. Debloat sits here too. Some actions on system apps need root.",
        "Yüklü tüm uygulamalar boyut, sürüm ve kurulum tarihiyle, süzme ve sıralamayla listelenir. Bir uygulama ayrıntılarını, izinlerini ve işlemlerini açar. Debloat de buradadır. Sistem uygulamalarındaki bazı işlemler root ister.",
        "Uygulamalar"),
    GuideEntry("debloat", "delete", "Debloat",
        "A list of system apps that phones ship with, each marked Recommended, Advanced, Expert or Unsafe by the list's authors (the Canta list). It can remove them for your user and bring them back later. It needs root, and removing an Unsafe item can break the phone.",
        "Telefonla gelen sistem uygulamalarının listesi; her biri liste yazarlarınca Önerilen, Gelişmiş, Uzman veya Güvensiz olarak işaretlenir (Canta listesi). Bunları kullanıcınız için kaldırabilir ve sonra geri getirebilir. Root gerekir; Güvensiz bir öğeyi kaldırmak telefonu bozabilir.",
        "Debloat"),
    GuideEntry("permissions", "shield", "Permission audit",
        "Scans the installed non-system apps for the sensitive permissions they hold (camera, location, microphone, contacts and so on) and groups the apps by permission. Read only: it shows, it does not change anything.",
        "Yüklü sistem dışı uygulamaların sahip olduğu hassas izinleri (kamera, konum, mikrofon, kişiler vb.) tarar ve uygulamaları izne göre gruplar. Salt okunur: gösterir, hiçbir şeyi değiştirmez.",
        "İzin denetimi"),
    GuideEntry("downloads", "download", "Downloads",
        "A download manager that can split a file into several connections when the server allows it, with a speed limit, pause and resume, and notifications. Files go to your storage. A watchdog restarts the service if Android stops it.",
        "Sunucu izin verdiğinde dosyayı birden çok bağlantıya bölebilen indirme yöneticisi; hız sınırı, duraklatma, devam ettirme ve bildirimleri vardır. Dosyalar depolamanıza iner. Android servisi durdurursa bir bekçi yeniden başlatır.",
        "İndirmeler"),
    GuideEntry("files", "folder", "Files",
        "A file manager for shared storage: browse, search, sort, copy, move, rename, zip, share, bookmarks, recent files and file properties with hashes, plus undo for the last delete or move. Android 11 and later needs the All files access permission.",
        "Paylaşılan depolama için dosya yöneticisi: gezinme, arama, sıralama, kopyalama, taşıma, yeniden adlandırma, zip, paylaşma, yer imleri, son dosyalar ve karma değerli dosya özellikleri; son silme veya taşıma geri alınabilir. Android 11 ve sonrası Tüm dosyalara erişim izni ister.",
        "Dosyalar"),
    GuideEntry("folders", "sizes", "Folder tree",
        "The size of every folder on shared storage, biggest first, so you can see where the space went. Read only.",
        "Paylaşılan depolamadaki her klasörün boyutu, büyükten küçüğe; alanın nereye gittiğini gösterir. Salt okunur.",
        "Klasör ağacı"),
    GuideEntry("typebreak", "filetypes", "File types",
        "A donut chart of storage split by kind of file (pictures, video, audio, documents, archives, apps and others) with a legend. Read only.",
        "Depolamanın dosya türüne göre (resim, video, ses, belge, arşiv, uygulama ve diğer) bölünmüş halka grafiği ve açıklaması. Salt okunur.",
        "Dosya türleri"),
    GuideEntry("cache", "clean", "Cache cleaner",
        "Finds app caches, thumbnails, temp files, empty folders, duplicates and big files, and removes a group only after you confirm it. Nothing is deleted by scanning alone. Clearing other apps' caches can need root or special access.",
        "Uygulama önbellekleri, küçük resimler, geçici dosyalar, boş klasörler, kopya ve büyük dosyaları bulur; bir grubu yalnız onayladıktan sonra siler. Yalnız taramak hiçbir şeyi silmez. Diğer uygulamaların önbelleğini temizlemek root veya özel erişim isteyebilir.",
        "Önbellek temizleyici"),
    GuideEntry("recycle", "recycle", "Recycle bin",
        "When the Recycle bin option in Settings is on, files you delete are moved here instead of being erased, with their original place remembered. You can restore them, delete them for good or empty the bin. With the option off, deleting is permanent.",
        "Ayarlar'daki Çöp kutusu seçeneği açıkken sildiğiniz dosyalar silinmek yerine buraya taşınır ve özgün yerleri hatırlanır. Geri yükleyebilir, kalıcı silebilir veya kutuyu boşaltabilirsiniz. Seçenek kapalıyken silme kalıcıdır.",
        "Geri dönüşüm kutusu"),
    GuideEntry("device", "phone", "Device",
        "Everything about the phone in one place, grouped like the parts of a computer: processor, memory, storage, display, battery, sensors, network and system facts.",
        "Telefonla ilgili her şey tek yerde, bilgisayarın parçaları gibi gruplanmış: işlemci, bellek, depolama, ekran, pil, sensörler, ağ ve sistem bilgileri.",
        "Cihaz"),
    GuideEntry("health", "heart", "Health",
        "Steps, distance and calories from the phone's step counter, a heart rate read through the camera and flash with a fingertip over the lens, an activity timer and location. These are estimates, not medical measurements.",
        "Telefonun adım sayacından adım, mesafe ve kalori; mercek üzerine konan parmakla kamera ve flaşla okunan nabız, bir etkinlik zamanlayıcısı ve konum. Bunlar tahmindir, tıbbi ölçüm değildir.",
        "Sağlık"),
    GuideEntry("terminal", "run", "Terminal",
        "Runs shell commands on the phone, as root when root is available and as the normal shell otherwise, with a list of ready commands, a history of the last 20 and a button that hands the command to Termux.",
        "Telefonda kabuk komutları çalıştırır; root varsa root olarak, yoksa normal kabukta. Hazır komut listesi, son 20 komutun geçmişi ve komutu Termux'a gönderen bir düğme vardır.",
        "Terminal"),
    GuideEntry("widgets", "gridView", "Widgets",
        "Home-screen widgets in the designs of the app's looks (Windows, Material, glass, iOS style and more) showing CPU, memory, battery, storage and clocks, with a shared accent and Windows 11 tile opacity and blur.",
        "Uygulamanın görünümlerinin tasarımlarında ana ekran widget'ları (Windows, Material, cam, iOS tarzı ve diğerleri); CPU, bellek, pil, depolama ve saatleri gösterir, ortak vurgu rengi ve Windows 11 kutu saydamlığı ile bulanıklığı vardır.",
        "Widget'lar"),
    GuideEntry("toolkit", "wrench", "Toolkit",
        "A grid of small utilities: Compass, Level, Flashlight, Lux meter, Dice, Coin flip, Clipboard, Screenshot, Sensors off, Battery, Memory, Temperature, Volume, Sound mode, Stopwatch, Timer, Calculator, Screen scale, Finder, Power menu and the Dink game. Screenshot and Sensors off need root.",
        "Küçük araçlardan oluşan bir ızgara: Pusula, Su terazisi, Fener, Lux ölçer, Zar, Yazı tura, Pano, Ekran görüntüsü, Sensörleri kapat, Pil, Bellek, Sıcaklık, Ses, Ses modu, Kronometre, Zamanlayıcı, Hesap makinesi, Ekran ölçeği, Bulucu, Güç menüsü ve Dink oyunu. Ekran görüntüsü ve Sensörleri kapat root ister.",
        "Araç kutusu"),
    GuideEntry("sensors", "sensors", "Sensors",
        "Every sensor the phone reports, with vendor, live reading and power draw. A limited number can stream live values at once.",
        "Telefonun bildirdiği her sensör; üretici, canlı değer ve güç tüketimiyle. Aynı anda yalnız sınırlı sayıda sensör canlı değer akıtabilir.",
        "Sensörler"),
    GuideEntry("battery", "battery", "Battery health",
        "The battery's condition, charge cycles and capacity as far as the phone reports them. Some phones do not expose all of these.",
        "Pilin durumu, şarj döngüsü ve kapasitesi; telefonun bildirdiği kadarıyla. Bazı telefonlar bunların hepsini açmaz.",
        "Pil sağlığı"),
    GuideEntry("tempgraph", "thermal", "Temperature graph",
        "Sensor and battery temperature drawn over time, so heating while charging or gaming is visible.",
        "Sensör ve pil sıcaklığının zamana göre çizimi; şarj veya oyun sırasındaki ısınmayı gösterir.",
        "Sıcaklık grafiği"),
    GuideEntry("monitor", "bell", "Monitor and alerts",
        "Keeps sampling when the app is closed: floating numbers over other apps (needs the overlay permission), a live notification, and alerts when temperature, battery or memory pass a limit you set. Quick Settings tiles switch it on and off.",
        "Uygulama kapalıyken de ölçmeyi sürdürür: diğer uygulamaların üstünde yüzen sayılar (bindirme izni gerekir), canlı bildirim ve sıcaklık, pil veya bellek belirlediğiniz sınırı aşınca uyarılar. Hızlı Ayarlar karoları bunu açıp kapatır.",
        "İzleme ve uyarılar"),
    GuideEntry("watchdog", "clock", "Watchdog",
        "A repeating check every 15 minutes that restarts the monitor and any running downloads if Android has stopped them. It has no screen of its own.",
        "Android durdurmuşsa izleyiciyi ve çalışan indirmeleri yeniden başlatan, 15 dakikada bir yinelenen bir denetim. Kendi ekranı yoktur.",
        "Bekçi"),
    GuideEntry("applock", "lock", "App lock",
        "Locks chosen apps behind a PIN, password or fingerprint. It watches which app is in front through the Accessibility service and draws the lock screen over it, so it needs Accessibility, Usage access and Display over other apps. The lock screen can follow Material You, the app's look or a blurred Windows 11 style.",
        "Seçilen uygulamaları PIN, parola veya parmak iziyle kilitler. Hangi uygulamanın önde olduğunu Erişilebilirlik servisiyle izler ve kilit ekranını üstüne çizer; bu yüzden Erişilebilirlik, Kullanım erişimi ve Diğer uygulamaların üzerinde gösterme izinleri gerekir. Kilit ekranı Material You'yu, uygulamanın görünümünü veya bulanık Windows 11 stilini izleyebilir.",
        "Uygulama kilidi"),
    GuideEntry("installer", "add", "Installer",
        "Installs APK files and bundles (APKS, APKM, XAPK, zip with splits). It installs through Shizuku, through Android's own installer screen, or in the background, using saved profiles. Root or Shizuku is only needed for the silent modes.",
        "APK dosyalarını ve paketleri (APKS, APKM, XAPK, bölünmüş zip) kurar. Root, Shizuku, Android'in kendi yükleyici ekranı veya arka plan yoluyla, kayıtlı profillerle kurar. Root veya Shizuku yalnız sessiz modlar için gerekir.",
        "Yükleyici"),
    GuideEntry("dns", "globe", "DNS",
        "Sets Android's Private DNS to Auto, Off or a host name you give, and shows the current setting. A Quick Settings tile toggles it. Writing the setting needs the WRITE_SECURE_SETTINGS permission, granted once with ADB or root.",
        "Android'in Özel DNS'ini Otomatik, Kapalı veya verdiğiniz bir ana makine adına ayarlar ve geçerli ayarı gösterir. Hızlı Ayarlar karosu bunu değiştirir. Ayarı yazmak için bir kez ADB veya root ile verilen WRITE_SECURE_SETTINGS izni gerekir.",
        "DNS"),
    GuideEntry("netusage", "wifi", "Network usage",
        "How much mobile and Wi-Fi data each app used over the last 24 hours and 30 days, with hourly and daily charts. It needs the Usage access permission and shows nothing when no data was recorded.",
        "Her uygulamanın son 24 saatte ve 30 günde kullandığı mobil ve Wi-Fi veri miktarı, saatlik ve günlük grafiklerle. Kullanım erişimi izni gerekir; veri kaydedilmemişse hiçbir şey göstermez.",
        "Ağ kullanımı"),
    GuideEntry("troubleshoot", "shieldOk", "Device troubleshoot",
        "A searchable catalogue of common Android problems (camera, Wi-Fi, battery and more). Each item says what to check, can run a read-only command that shows the current state, and describes the fix as text. Pulse never runs the fixes for you.",
        "Sık görülen Android sorunlarının (kamera, Wi-Fi, pil vb.) aranabilir kataloğu. Her öğe neyin kontrol edileceğini söyler, geçerli durumu gösteren salt okunur bir komut çalıştırabilir ve çözümü metin olarak anlatır. Pulse çözümleri sizin yerinize çalıştırmaz.",
        "Cihaz sorun giderme"),
    GuideEntry("authenticator", "key", "Authenticator",
        "Two-factor codes (TOTP, 6 digits, 30 seconds) like a standalone authenticator app. Accounts are added from a base32 secret or an otpauth link and stored encrypted with a key in the Android Keystore. The secrets never leave the phone.",
        "Ayrı bir doğrulayıcı uygulama gibi iki adımlı doğrulama kodları (TOTP, 6 hane, 30 saniye). Hesaplar base32 anahtar veya otpauth bağlantısından eklenir ve Android Keystore'daki bir anahtarla şifreli saklanır. Anahtarlar telefondan çıkmaz.",
        "Doğrulayıcı"),
    GuideEntry("fonts", "edit", "System font",
        "Changes the system font without root by building a small signed font package and installing it through the Installer; you then pick it in Android's own font settings. Nothing in /system is touched. Some phone makers' font lists may not show third-party packs.",
        "Küçük imzalı bir yazı tipi paketi üretip Yükleyici ile kurarak sistem yazı tipini root olmadan değiştirir; sonra Android'in kendi yazı tipi ayarlarından seçersiniz. /system'e dokunulmaz. Bazı telefon üreticilerinin yazı tipi listesi üçüncü taraf paketleri göstermeyebilir.",
        "Sistem yazı tipi"),
    GuideEntry("settings", "settings", "Settings",
        "The look of the app (Windows 95, XP, 7, 10, 11, Material and more, with accent, wallpaper and window styles), language, which pages are shown, refresh rate, the Recycle bin option, alert limits, the floating monitor and root and permission status.",
        "Uygulamanın görünümü (Windows 95, XP, 7, 10, 11, Material ve daha fazlası; vurgu rengi, duvar kağıdı ve pencere stilleri), dil, hangi sayfaların gösterileceği, yenileme hızı, Çöp kutusu seçeneği, uyarı sınırları, yüzen izleyici ve root ile izin durumu.",
        "Ayarlar"),
)

/** The icon and the text of one entry, in the app's language. */
@Composable
private fun GuideBody(e: GuideEntry) = Text(tr(e.en, e.tr), color = T.text, fontSize = 13.sp)

/** The Guide page: every feature as a card that folds open. */
@Composable
fun GuidePage() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PHead(tr("Guide", "Kılavuz"), "${GUIDE.size}")
        GUIDE.forEach { e ->
            var open by rememberSaveable(e.id) { mutableStateOf(false) }
            PCard(Modifier.fillMaxWidth().clickable { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PIcon(e.key, Look.accent, 22.dp); Spacer(Modifier.width(12.dp))
                    Text(tr(e.title, e.titleTr), color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    PIcon("chevron", T.sub, 20.dp, Modifier.rotate(if (open) 180f else 0f))
                }
                if (open) { Spacer(Modifier.height(6.dp)); GuideBody(e) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** A themed pop-up window with one guide entry (the "i" in a page header opens this). */
@Composable
fun GuideDialog(id: String, onClose: () -> Unit) {
    val e = GUIDE.firstOrNull { it.id == id } ?: run { onClose(); return }
    WinAlert(
        onDismissRequest = onClose,
        title = { Row(verticalAlignment = Alignment.CenterVertically) { PIcon(e.key, Look.accent, 22.dp); Spacer(Modifier.width(10.dp)); Text(tr(e.title, e.titleTr), color = T.text) } },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { GuideBody(e) } },
        confirmButton = { PButton("OK") { onClose() } },
    )
}
