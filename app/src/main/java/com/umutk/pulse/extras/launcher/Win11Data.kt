package com.umutk.pulse.extras.launcher

import android.provider.Settings

/** One setting of the Windows 11 Settings app or one applet of the Control Panel: the route is a Pulse page, or "intent:<action>" for a page of the phone's own settings. */
internal class WItem(val icon: String, val en: String, val tr: String, val enDesc: String, val trDesc: String, val route: String)
internal class WGroup(val id: String, val icon: String, val en: String, val tr: String, val items: List<WItem>)

private fun a(action: String) = "intent:$action"
private fun w(icon: String, en: String, tr: String, ed: String, td: String, route: String) = WItem(icon, en, tr, ed, td, route)

/** The eleven groups of Windows 11 Settings, each setting leading to the page of the phone (or of Pulse) that does the same job. */
internal val WIN11_SETTINGS: List<WGroup> = listOf(
    WGroup("system", "phone", "System", "Sistem", listOf(
        w("phone", "Display", "Ekran", "Brightness, night light, scale, refresh rate", "Parlaklık, gece ışığı, ölçek, yenileme hızı", a(Settings.ACTION_DISPLAY_SETTINGS)),
        w("volume", "Sound", "Ses", "Volume levels, output, input, sound devices", "Ses düzeyleri, çıkış, giriş, ses aygıtları", a(Settings.ACTION_SOUND_SETTINGS)),
        w("notifications", "Notifications", "Bildirimler", "Alerts from apps and system", "Uygulamalardan ve sistemden gelen uyarılar", a(Settings.ACTION_APP_NOTIFICATION_SETTINGS)),
        w("light", "Focus", "Odaklanma", "Reduce distractions, do not disturb", "Dikkat dağıtan şeyleri azalt, rahatsız etme", a("android.settings.ZEN_MODE_SETTINGS")),
        w("battery", "Power & battery", "Güç ve pil", "Screen and sleep, battery usage, battery saver", "Ekran ve uyku, pil kullanımı, pil tasarrufu", a(Intent_ACTION_POWER_USAGE_SUMMARY)),
        w("disk", "Storage", "Depolama", "Storage space, drives, cleanup", "Depolama alanı, sürücüler, temizlik", a(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        w("share", "Nearby sharing", "Yakındaki paylaşım", "Discoverability, received files location", "Bulunabilirlik, alınan dosyaların konumu", "page:localsend"),
        w("processes", "Multitasking", "Çoklu görev", "Running apps, switching between them", "Çalışan uygulamalar, aralarında geçiş", "page:taskmgr"),
        w("bug", "Troubleshoot", "Sorun gider", "Find and fix common problems", "Yaygın sorunları bul ve düzelt", "troubleshoot"),
        w("history", "Recovery", "Kurtarma", "Reset, backup and restore options", "Sıfırlama, yedekleme ve geri yükleme", a("android.settings.PRIVACY_SETTINGS")),
        w("info", "Clipboard", "Pano", "Clipboard history and sync", "Pano geçmişi ve eşitleme", a(Settings.ACTION_INPUT_METHOD_SETTINGS)),
        w("cpu", "About", "Hakkında", "Device name, processor, memory, Android version", "Aygıt adı, işlemci, bellek, Android sürümü", "page:device"),
    )),
    WGroup("bluetooth", "wifi", "Bluetooth & devices", "Bluetooth ve cihazlar", listOf(
        w("wifi", "Bluetooth", "Bluetooth", "Paired devices, connect new ones", "Eşleşmiş cihazlar, yenilerini bağla", a(Settings.ACTION_BLUETOOTH_SETTINGS)),
        w("phone", "Printers & scanners", "Yazıcılar ve tarayıcılar", "Printing services and queues", "Yazdırma hizmetleri ve kuyrukları", a(Settings.ACTION_PRINT_SETTINGS)),
        w("keyboard", "Typing and keyboard", "Yazma ve klavye", "Input methods and layouts", "Giriş yöntemleri ve düzenler", a(Settings.ACTION_INPUT_METHOD_SETTINGS)),
        w("details", "Cameras", "Kameralar", "Permissions and default settings", "İzinler ve varsayılan ayarlar", a(Settings.ACTION_APPLICATION_SETTINGS)),
        w("share", "NFC", "NFC", "Tap to pay and share", "Dokunarak öde ve paylaş", a(Settings.ACTION_NFC_SETTINGS)),
        w("download", "USB", "USB", "USB connection, file transfer", "USB bağlantısı, dosya aktarımı", a(Settings.ACTION_SETTINGS)),
    )),
    WGroup("network", "globe", "Network & internet", "Ağ ve internet", listOf(
        w("wifi", "Wi-Fi", "Wi-Fi", "Connect, known networks, metered", "Bağlan, bilinen ağlar, tarifeli", a(Settings.ACTION_WIFI_SETTINGS)),
        w("globe", "Mobile network", "Mobil ağ", "Data usage, SIM, roaming", "Veri kullanımı, SIM, dolaşım", a(Settings.ACTION_DATA_ROAMING_SETTINGS)),
        w("globe", "VPN", "VPN", "Add, connect, manage", "Ekle, bağlan, yönet", a(Settings.ACTION_VPN_SETTINGS)),
        w("wifi", "Mobile hotspot", "Mobil etkin nokta", "Share your internet connection", "İnternet bağlantını paylaş", a("com.android.settings.TetherSettings")),
        w("phone", "Airplane mode", "Uçak modu", "Stop wireless communication", "Kablosuz iletişimi durdur", a(Settings.ACTION_AIRPLANE_MODE_SETTINGS)),
        w("globe", "Private DNS and TTL", "Özel DNS ve TTL", "DNS server of each network, packet TTL", "Her ağın DNS sunucusu, paket TTL", "dns"),
        w("globe", "Advanced network settings", "Gelişmiş ağ ayarları", "All network adapters, network reset", "Tüm ağ bağdaştırıcıları, ağ sıfırlama", a(Settings.ACTION_WIRELESS_SETTINGS)),
    )),
    WGroup("personalization", "palette", "Personalization", "Kişiselleştirme", listOf(
        w("settings", "Background", "Arka plan", "Wallpaper, picture, slideshow", "Duvar kâğıdı, resim, slayt gösterisi", "win:lsettings:look"),
        w("palette", "Colors", "Renkler", "Accent color, transparency effects, theme", "Vurgu rengi, saydamlık efektleri, tema", "win:lsettings:feel"),
        w("palette", "Themes", "Temalar", "Windows looks: 95, XP, 7, 10, 11, Material", "Windows görünümleri: 95, XP, 7, 10, 11, Material", "win:lsettings:look"),
        w("menu", "Start", "Başlat", "Layout, recently added apps, folders", "Düzen, son eklenen uygulamalar, klasörler", "win:lsettings:apps"),
        w("menu", "Taskbar", "Görev çubuğu", "Taskbar items, alignment, auto-hide", "Görev çubuğu öğeleri, hizalama, otomatik gizleme", "win:lsettings:dock"),
        w("notifications", "Taskbar corner icons", "Görev çubuğu köşe simgeleri", "Which icons show in the tray", "Tepsi'de hangi simgeler görünsün", "trayicons"),
        w("details", "Fonts", "Yazı tipleri", "Change the system font", "Sistem yazı tipini değiştir", "fonts"),
        w("apps", "Home screen", "Ana ekran", "Grid, icons, pages, windows, orientation", "Izgara, simgeler, sayfalar, pencereler, yön", "win:lsettings:home"),
        w("folder", "Folders", "Klasörler", "How a folder looks", "Klasörün görünümü", "win:lsettings:folders"),
        w("gridView", "Gadgets", "Gadget'lar", "Add and remove gadgets", "Gadget ekle ve kaldır", "win:lsettings:widgets"),
        w("folder", "Desktop icons", "Masaüstü simgeleri", "Which shortcuts sit on the Windows desktops", "Windows masaüstlerindeki kısayollar", "win:lsettings:desktop"),
        w("palette", "Look and feel", "Görünüm ve his", "Icon shape, wallpaper effects, sounds, boot screens", "Simge şekli, duvar kağıdı efektleri, sesler, açılış ekranları", "win:lsettings:feel"),
        w("pointer", "Gestures and inputs", "Hareketler ve girişler", "Double tap, swipes, vibration", "Çift dokunma, kaydırma, titreşim", "win:lsettings:gestures"),
        w("notifications", "Notification badges", "Bildirim rozetleri", "Numbers or dots on the apps", "Uygulamalarda sayı veya nokta", "win:lsettings:badges"),
        w("clock", "Clock and screen", "Saat ve ekran", "Clock formats, screen on, layout lock", "Saat biçimleri, ekran açık, düzen kilidi", "win:lsettings:screen"),
        w("save", "Backup and reset", "Yedek ve sıfırlama", "Export, import, reset", "Dışa aktar, içe aktar, sıfırla", "win:lsettings:backup"),
        w("home", "Default home app", "Varsayılan ana ekran", "Make Pulse the home screen", "Pulse'ı ana ekran yap", "win:lsettings:system"),
        w("menu", "Context menu", "Sağ tık menüsü", "Choose and order the actions", "Eylemleri seç ve sırala", "win:lsettings:menu"),
        w("lock", "Lock screen", "Kilit ekranı", "Lock screen and lyrics on it", "Kilit ekranı ve üstündeki sözler", a(Settings.ACTION_SECURITY_SETTINGS)),
        w("keyboard", "Text input", "Metin girişi", "Touch keyboard, suggestions", "Dokunmatik klavye, öneriler", a(Settings.ACTION_INPUT_METHOD_SETTINGS)),
    )),
    WGroup("apps", "apps", "Apps", "Uygulamalar", listOf(
        w("apps", "Installed apps", "Yüklü uygulamalar", "Manage and uninstall apps", "Uygulamaları yönet ve kaldır", "page:apps"),
        w("apkPackage", "Advanced app settings", "Gelişmiş uygulama ayarları", "Where apps come from, special access", "Uygulamaların kaynağı, özel erişim", a(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        w("apps", "Default apps", "Varsayılan uygulamalar", "Browser, phone, files and links", "Tarayıcı, telefon, dosyalar ve bağlantılar", a(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        w("globe", "Apps for websites", "Web siteleri için uygulamalar", "Links that open in an app", "Bir uygulamada açılan bağlantılar", a("android.settings.MANAGE_DOMAIN_URLS")),
        w("startup", "Startup", "Başlangıç", "Apps that start with the phone", "Telefonla başlayan uygulamalar", "page:startup"),
        w("services", "Services", "Hizmetler", "Services that run in the background", "Arka planda çalışan hizmetler", "page:services"),
    )),
    WGroup("accounts", "users", "Accounts", "Hesaplar", listOf(
        w("users", "Your info", "Bilgileriniz", "Your accounts and profile", "Hesaplarınız ve profiliniz", a(Settings.ACTION_SYNC_SETTINGS)),
        w("lock", "Sign-in options", "Oturum açma seçenekleri", "Screen lock, fingerprint, face", "Ekran kilidi, parmak izi, yüz", a(Settings.ACTION_SECURITY_SETTINGS)),
        w("users", "Email & accounts", "E-posta ve hesaplar", "Accounts used by apps", "Uygulamaların kullandığı hesaplar", a(Settings.ACTION_SYNC_SETTINGS)),
        w("download", "Backup", "Yedekleme", "Back up and restore", "Yedekle ve geri yükle", a("android.settings.PRIVACY_SETTINGS")),
    )),
    WGroup("time", "history", "Time & language", "Zaman ve dil", listOf(
        w("history", "Date & time", "Tarih ve saat", "Time zone, automatic time", "Saat dilimi, otomatik saat", a(Settings.ACTION_DATE_SETTINGS)),
        w("globe", "Language & region", "Dil ve bölge", "Display language, region", "Görüntüleme dili, bölge", a(Settings.ACTION_LOCALE_SETTINGS)),
        w("keyboard", "Typing", "Yazma", "Keyboard, autocorrect, suggestions", "Klavye, otomatik düzeltme, öneriler", a(Settings.ACTION_INPUT_METHOD_SETTINGS)),
        w("volume", "Speech", "Konuşma", "Voice input and text to speech", "Sesli giriş ve metinden konuşmaya", a("com.android.settings.TTS_SETTINGS")),
    )),
    WGroup("gaming", "play", "Gaming", "Oyun", listOf(
        w("play", "Game Hub", "Game Hub", "Your games, the shelf, the in-game panel", "Oyunların, raf, oyun içi panel", "page:gamehub"),
        w("details", "Captures", "Yakalamalar", "Screenshots and screen recording", "Ekran görüntüleri ve ekran kaydı", "page:screenrec"),
        w("speed", "Game Mode", "Oyun Modu", "Performance and do not disturb while playing", "Oynarken performans ve rahatsız etme", "page:gamehub"),
    )),
    WGroup("accessibility", "info", "Accessibility", "Erişilebilirlik", listOf(
        w("info", "Text size", "Metin boyutu", "Font size and display size", "Yazı boyutu ve ekran boyutu", a(Settings.ACTION_DISPLAY_SETTINGS)),
        w("light", "Visual effects", "Görsel efektler", "Animations, transparency", "Animasyonlar, saydamlık", a(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        w("details", "Magnifier", "Büyüteç", "Magnification", "Büyütme", a(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        w("palette", "Color filters", "Renk filtreleri", "Color correction, grayscale, inversion", "Renk düzeltme, gri tonlama, ters çevirme", a(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        w("volume", "Hearing", "İşitme", "Captions, mono audio, hearing devices", "Altyazı, mono ses, işitme cihazları", a(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        w("users", "Screen reader", "Ekran okuyucu", "TalkBack", "TalkBack", a(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
    )),
    WGroup("privacy", "lock", "Privacy & security", "Gizlilik ve güvenlik", listOf(
        w("lock", "Security", "Güvenlik", "Screen lock, Play Protect, encryption", "Ekran kilidi, Play Protect, şifreleme", a(Settings.ACTION_SECURITY_SETTINGS)),
        w("search", "Find my device", "Cihazımı bul", "Locate a lost phone", "Kayıp telefonu bul", a(Settings.ACTION_SECURITY_SETTINGS)),
        w("info", "Permissions", "İzinler", "What apps may use: camera, microphone, location", "Uygulamaların kullanabilecekleri: kamera, mikrofon, konum", a("android.settings.PRIVACY_SETTINGS")),
        w("globe", "Location", "Konum", "Location services", "Konum hizmetleri", a(Settings.ACTION_LOCATION_SOURCE_SETTINGS)),
        w("services", "For developers", "Geliştiriciler için", "Developer options, registry editor, root and Shizuku", "Geliştirici seçenekleri, kayıt defteri, root ve Shizuku", "win:regedit"),
        w("heart", "Device health", "Aygıt sağlığı", "Battery wear and health checks", "Pil aşınması ve sağlık kontrolleri", "health"),
    )),
    WGroup("update", "download", "Windows Update", "Windows Update", listOf(
        w("download", "Check for updates", "Güncellemeleri denetle", "System update and Pulse update", "Sistem güncellemesi ve Pulse güncellemesi", a("android.settings.SYSTEM_UPDATE_SETTINGS")),
        w("history", "Update history", "Güncelleştirme geçmişi", "What was installed", "Neler yüklendi", a(Settings.ACTION_DEVICE_INFO_SETTINGS)),
        w("apps", "Pulse extras", "Pulse ek uygulamalar", "Store of extra apps and their updates", "Ek uygulamaların mağazası ve güncellemeleri", "page:extrastore"),
    )),
)

private const val Intent_ACTION_POWER_USAGE_SUMMARY = "android.intent.action.POWER_USAGE_SUMMARY"

/** The eight categories of the Control Panel, each with its applets (the phone's pages or Pulse's). */
internal val CONTROL_PANEL_8: List<WGroup> = listOf(
    WGroup("syssec", "heart", "System and Security", "Sistem ve Güvenlik", listOf(
        w("heart", "Security and Maintenance", "Güvenlik ve Bakım", "Device health, Play Protect, battery wear", "Aygıt sağlığı, Play Protect, pil aşınması", "health"),
        w("lock", "Windows Defender Firewall", "Windows Defender Güvenlik Duvarı", "Network protection and private DNS", "Ağ koruması ve özel DNS", "dns"),
        w("phone", "System", "Sistem", "Device, processor, memory, Android version", "Aygıt, işlemci, bellek, Android sürümü", "page:device"),
        w("battery", "Power Options", "Güç Seçenekleri", "Battery saver, screen timeout, usage", "Pil tasarrufu, ekran zaman aşımı, kullanım", a("android.intent.action.POWER_USAGE_SUMMARY")),
        w("download", "File History", "Dosya Geçmişi", "Back up files", "Dosyaları yedekle", a("android.settings.PRIVACY_SETTINGS")),
        w("lock", "BitLocker Drive Encryption", "BitLocker Sürücü Şifreleme", "Encryption and screen lock", "Şifreleme ve ekran kilidi", a(Settings.ACTION_SECURITY_SETTINGS)),
        w("services", "Windows Tools", "Windows Araçları", "Services, task manager, startup, registry editor", "Hizmetler, görev yöneticisi, başlangıç, kayıt defteri", "page:services"),
    )),
    WGroup("net", "globe", "Network and Internet", "Ağ ve İnternet", listOf(
        w("globe", "Network and Sharing Center", "Ağ ve Paylaşım Merkezi", "Wi-Fi, mobile data, sharing", "Wi-Fi, mobil veri, paylaşım", a(Settings.ACTION_WIRELESS_SETTINGS)),
        w("wifi", "Change adapter settings", "Bağdaştırıcı ayarlarını değiştir", "Wi-Fi networks and their DNS", "Wi-Fi ağları ve DNS'leri", a(Settings.ACTION_WIFI_SETTINGS)),
        w("globe", "Internet Options", "İnternet Seçenekleri", "Proxy, private DNS, TTL", "Proxy, özel DNS, TTL", "dns"),
    )),
    WGroup("hw", "volume", "Hardware and Sound", "Donanım ve Ses", listOf(
        w("phone", "Devices and Printers", "Aygıtlar ve Yazıcılar", "Bluetooth, printers, NFC", "Bluetooth, yazıcılar, NFC", a(Settings.ACTION_BLUETOOTH_SETTINGS)),
        w("download", "AutoPlay", "Otomatik Kullan", "What happens when a drive is connected", "Bir sürücü bağlanınca ne olur", a(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)),
        w("volume", "Sound", "Ses", "Volume, ringtone, sound devices", "Ses, zil sesi, ses aygıtları", a(Settings.ACTION_SOUND_SETTINGS)),
        w("battery", "Power Options and Mobility Center", "Güç Seçenekleri ve Mobilite Merkezi", "Brightness, volume and power at a glance", "Parlaklık, ses ve güç bir bakışta", a(Settings.ACTION_DISPLAY_SETTINGS)),
    )),
    WGroup("prog", "apps", "Programs", "Programlar", listOf(
        w("apps", "Programs and Features", "Programlar ve Özellikler", "Uninstall or change an app", "Bir uygulamayı kaldır ya da değiştir", "page:apps"),
        w("apps", "Default Programs", "Varsayılan Programlar", "Default apps and file types", "Varsayılan uygulamalar ve dosya türleri", a(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)),
        w("startup", "Startup", "Başlangıç", "Apps that start with the phone", "Telefonla başlayan uygulamalar", "page:startup"),
    )),
    WGroup("users", "users", "User Accounts", "Kullanıcı Hesapları", listOf(
        w("users", "User Accounts", "Kullanıcı Hesapları", "Accounts and sync", "Hesaplar ve eşitleme", a(Settings.ACTION_SYNC_SETTINGS)),
        w("lock", "Credential Manager", "Kimlik Bilgileri Yöneticisi", "Passwords saved in Pulse's keyboard and the phone", "Pulse klavyesinde ve telefonda kayıtlı parolalar", a(Settings.ACTION_SECURITY_SETTINGS)),
        w("details", "Mail", "Posta", "E-mail accounts", "E-posta hesapları", a(Settings.ACTION_SYNC_SETTINGS)),
    )),
    WGroup("look", "palette", "Appearance and Personalization", "Görünüm ve Kişiselleştirme", listOf(
        w("settings", "Personalization", "Kişiselleştirme", "Look of the desktop, wallpaper, icons, dock", "Masaüstünün görünümü, duvar kâğıdı, simgeler, dok", "win:lsettings"),
        w("menu", "Taskbar and Navigation", "Görev Çubuğu ve Gezinti", "Taskbar items and alignment", "Görev çubuğu öğeleri ve hizalama", "win:lsettings"),
        w("folder", "File Explorer Options", "Dosya Gezgini Seçenekleri", "Hidden files, extensions, view", "Gizli dosyalar, uzantılar, görünüm", "page:filestabs"),
        w("details", "Fonts", "Yazı Tipleri", "Change the system font", "Sistem yazı tipini değiştir", "fonts"),
    )),
    WGroup("clock", "history", "Clock and Region", "Saat ve Bölge", listOf(
        w("history", "Date and Time", "Tarih ve Saat", "Time zone, automatic time", "Saat dilimi, otomatik saat", a(Settings.ACTION_DATE_SETTINGS)),
        w("globe", "Region", "Bölge", "Language, number and date formats", "Dil, sayı ve tarih biçimleri", a(Settings.ACTION_LOCALE_SETTINGS)),
    )),
    WGroup("ease", "info", "Ease of Access", "Erişim Kolaylığı", listOf(
        w("info", "Ease of Access Center", "Erişim Kolaylığı Merkezi", "Magnifier, screen reader, high contrast", "Büyüteç, ekran okuyucu, yüksek karşıtlık", a(Settings.ACTION_ACCESSIBILITY_SETTINGS)),
        w("volume", "Speech Recognition", "Konuşma Tanıma", "Voice input and text to speech", "Sesli giriş ve metinden konuşmaya", a("com.android.settings.TTS_SETTINGS")),
    )),
)

/** Opens an item: a Pulse page through [open]. The phone's own Settings app is never opened; the pages with an `intent:` route are drawn by Win11Detail with controls that work inside Pulse. */
internal fun openWItem(@Suppress("UNUSED_PARAMETER") ctx: android.content.Context, route: String, open: (String) -> Unit) {
    if (!route.startsWith("intent:")) open(route)
}
