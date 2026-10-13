package com.umutk.pulse.features.doctor

import java.util.Locale

// Device troubleshoot catalog, ported from AndroDoc (Troubleshoot.kt). Each entry: a read-only check and an optional fix.
// The screen shows a fix as text only; it is never run from here.

/** Turkish text when the phone locale is Turkish, English otherwise (Pulse has no language setting). */
internal val turkish = Locale.getDefault().language == "tr"
internal fun tx(en: String, tr: String) = if (turkish) tr else en

// Sections of the catalog, in display order.
internal enum class TGroup(val en: String, val tr: String) {
    CAMERA("Camera & screenshots", "Kamera ve ekran görüntüsü"),
    AUDIO("Sound & microphone", "Ses ve mikrofon"),
    PHONE("Phone & SIM", "Telefon ve SIM"),
    NETWORK("Wi-Fi & Bluetooth", "Wi-Fi ve Bluetooth"),
    LOCATION("Time & location", "Saat ve konum"),
    INPUT("Keyboard, touch & sensors", "Klavye, dokunma ve sensörler"),
    DISPLAY("Display", "Ekran"),
    NOTIFY("Notifications", "Bildirimler"),
    BATTERY("Battery & background", "Pil ve arka plan"),
    MEMORY("Memory, CPU & crashes", "Bellek, işlemci ve çökmeler"),
    STORAGE("Storage & apps", "Depolama ve uygulamalar"),
    SECURITY("Security & developer", "Güvenlik ve geliştirici"),
    SYSTEM("System", "Sistem"),
}

/** One common problem. [check] is a read-only command that shows the state; [fix] is optional and is never run here. */
internal data class Trouble(
    val id: String,
    val titleEn: String, val titleTr: String,
    val keywords: String,
    val whyEn: String, val whyTr: String,
    val check: String,
    val fix: String? = null, val fixEn: String = "", val fixTr: String = "",
)

internal val troubles: List<Trouble> = listOf(
    Trouble("cam", "Camera not working", "Kamera çalışmıyor", "camera kamera kameram bozuldu fotoğraf açılmıyor selfie", "The camera service may be stuck, or an app holds the camera.", "Kamera servisi takılmış olabilir ya da bir uygulama kamerayı tutuyor olabilir.", "dumpsys media.camera | grep -m5 -i 'Camera'"),
    Trouble("cam-flash", "Camera flash stays on", "Kamera flaşı açık kalıyor", "flash fener el feneri flaş kamera", "A torch app or a camera app can keep the flash on.", "Bir fener uygulaması ya da kamera uygulaması flaşı açık tutabilir.", "dumpsys media.camera | grep -m3 -i 'torch\\|flash'"),
    Trouble("mic", "Microphone not recording", "Mikrofon kayıt yapmıyor", "mikrofon mic ses kaydı sesim gitmiyor", "An app may hold the microphone, or the mic is muted.", "Bir uygulama mikrofonu tutuyor olabilir ya da mikrofon sessize alınmış olabilir.", "dumpsys audio | grep -m5 -i 'record'"),
    Trouble("speaker", "No sound from speaker", "Hoparlörden ses gelmiyor", "hoparlör ses yok sessiz speaker müzik çalmıyor", "Media volume is zero, or audio is routed to another device.", "Medya sesi sıfır olabilir ya da ses başka bir cihaza yönlendirilmiş olabilir.", "dumpsys audio | grep -m5 -i 'STREAM_MUSIC'"),
    Trouble("vol-safe", "Volume stuck low", "Ses seviyesi düşük kalıyor", "ses düşük kısık volume azaldı", "Safe volume limit can cap the volume on some phones.", "Bazı telefonlarda güvenli ses sınırı sesi kısıtlayabilir.", "settings get system volume_music_speaker"),
    Trouble("wifi", "Wi-Fi keeps dropping", "Wi-Fi sürekli kopuyor", "wifi wi-fi bağlantı kopuyor internet yok", "Power saving or a weak signal can drop the link.", "Güç tasarrufu ya da zayıf sinyal bağlantıyı düşürebilir.", "dumpsys wifi | grep -m3 -i 'Wi-Fi is'"),
    Trouble("wifi-hot", "Hotspot does not start", "Hotspot açılmıyor", "hotspot taşınabilir erişim noktası modem açılmıyor", "Hotspot can be blocked by a limit or a missing setting.", "Hotspot bir sınır ya da eksik bir ayar yüzünden engellenmiş olabilir.", "dumpsys wifi | grep -m3 -i 'softap'"),
    Trouble("bt", "Bluetooth not connecting", "Bluetooth bağlanmıyor", "bluetooth bt kulaklık eşleşmiyor bağlanmıyor", "Bluetooth may be off or stuck; a reset often helps.", "Bluetooth kapalı ya da takılmış olabilir; yeniden başlatmak genelde yardımcı olur.", "settings get global bluetooth_on"),
    Trouble("mobile", "No mobile data", "Mobil veri yok", "mobil veri internet yok 4g 5g data çekmiyor", "Mobile data may be off, or the network mode is limited.", "Mobil veri kapalı olabilir ya da ağ modu sınırlı olabilir.", "settings get global mobile_data"),
    Trouble("airplane", "Airplane mode stuck on", "Uçak modu açık kalıyor", "uçak modu açılmıyor kapanmıyor", "A setting may keep airplane mode on.", "Bir ayar uçak modunu açık tutuyor olabilir.", "settings get global airplane_mode_on", "settings put global airplane_mode_on 0", "Turn airplane mode off", "Uçak modunu kapat"),
    Trouble("sim", "SIM card not detected", "SIM kart algılanmıyor", "sim kart yok sim tanınmıyor", "The SIM may be unseated or the state is not ready.", "SIM yerinden çıkmış olabilir ya da durum hazır değil.", "getprop gsm.sim.state"),
    Trouble("calls", "Calls drop or fail", "Aramalar düşüyor", "arama kesiliyor arama yapamıyorum telefon çalışmıyor", "Signal, network mode or a call app can break calls.", "Sinyal, ağ modu ya da bir arama uygulaması aramaları bozabilir.", "dumpsys telephony.registry | grep -m3 -i 'mCallState'"),
    Trouble("gps", "GPS location wrong", "Konum yanlış", "gps konum yanlış harita konum bulamıyor", "Location mode may be off or limited to Wi-Fi only.", "Konum modu kapalı olabilir ya da yalnızca Wi-Fi'ye sınırlı olabilir.", "settings get secure location_mode"),
    Trouble("time", "Clock shows wrong time", "Saat yanlış", "saat yanlış zaman tarih yanlış", "Automatic time may be turned off.", "Otomatik saat kapalı olabilir.", "settings get global auto_time", "settings put global auto_time 1", "Turn automatic time on", "Otomatik saati aç"),
    Trouble("tz", "Wrong time zone", "Saat dilimi yanlış", "saat dilimi zaman dilimi yanlış", "The time zone is set manually or wrongly.", "Saat dilimi elle ya da yanlış ayarlanmış.", "getprop persist.sys.timezone"),
    Trouble("keyboard", "Keyboard not showing", "Klavye görünmüyor", "klavye yazı yazamıyorum klavye açılmıyor", "Another keyboard may be the default, or the input method is missing.", "Başka bir klavye varsayılan olabilir ya da giriş yöntemi eksik olabilir.", "settings get secure default_input_method"),
    Trouble("font", "Text too small or big", "Yazılar çok küçük ya da büyük", "yazı boyutu yazılar küçük büyük font", "The font scale setting changed.", "Yazı boyutu ayarı değişmiş olabilir.", "settings get system font_scale", "settings put system font_scale 1.0", "Reset the font size", "Yazı boyutunu sıfırla"),
    Trouble("rotate", "Screen does not rotate", "Ekran dönmüyor", "ekran dönmüyor otomatik döndürme yatay dikey", "Auto-rotate is off.", "Otomatik döndürme kapalı.", "settings get system accelerometer_rotation", "settings put system accelerometer_rotation 1", "Turn auto-rotate on", "Otomatik döndürmeyi aç"),
    Trouble("bright", "Screen too dim", "Ekran çok karanlık", "ekran karanlık parlaklık kısık", "Brightness or adaptive brightness is low.", "Parlaklık ya da uyarlanabilir parlaklık düşük.", "settings get system screen_brightness"),
    Trouble("timeout", "Screen turns off too fast", "Ekran çok çabuk kapanıyor", "ekran kapanıyor ekran uyuyor ekran süresi", "The screen timeout is short.", "Ekran kapanma süresi kısa.", "settings get system screen_off_timeout"),
    Trouble("dark", "Dark mode wrong", "Karanlık mod yanlış", "karanlık mod koyu tema beyaz ekran siyah ekran", "The system night mode is on or off unexpectedly.", "Sistemin gece modu beklenmedik şekilde açık ya da kapalı.", "cmd uimode night", "cmd uimode night yes", "Turn dark mode on", "Karanlık modu aç"),
    Trouble("notif", "Notifications not showing", "Bildirimler gelmiyor", "bildirim gelmiyor bildirimler yok", "Do not disturb or notification access can block them.", "Rahatsız etme ya da bildirim izni bunları engelleyebilir.", "settings get global zen_mode"),
    Trouble("dnd", "Do not disturb stuck", "Rahatsız etme açık kalıyor", "rahatsız etme açık kalıyor sessiz mod", "A schedule or a rule keeps Do not disturb on.", "Bir zamanlama ya da kural Rahatsız Etme'yi açık tutuyor.", "settings get global zen_mode"),
    Trouble("battery", "Battery drains fast", "Pil çabuk bitiyor", "pil bitiyor batarya hızlı boşalıyor", "A background app or a bad battery can drain it.", "Arka plandaki bir uygulama ya da zayıf pil boşaltıyor olabilir.", "dumpsys battery | grep -m5 -i 'level\\|status'"),
    Trouble("charge", "Charging is slow", "Şarj yavaş", "şarj yavaş doluyor şarj olmuyor", "The charger or cable may be weak, or the phone is in use.", "Şarj aleti ya da kablo zayıf olabilir ya da telefon kullanılıyor.", "cat /sys/class/power_supply/battery/current_now"),
    Trouble("health", "Battery health", "Pil sağlığı", "pil sağlığı pil yaşlı pil şişmiş", "The battery capacity shows its wear.", "Pil kapasitesi aşınmayı gösterir.", "dumpsys batterystats --charged | head -5"),
    Trouble("heat", "Phone gets hot", "Telefon ısınıyor", "telefon ısınıyor sıcak kızıyor ısı", "A busy app or a hot environment raises the temperature.", "Yoğun bir uygulama ya da sıcak ortam sıcaklığı artırır.", "dumpsys thermalservice | head -10"),
    Trouble("slow", "Phone is slow", "Telefon yavaş", "telefon yavaş donuyor takılıyor", "Memory pressure or many background apps slow it down.", "Bellek baskısı ya da çok sayıda arka plan uygulaması yavaşlatır.", "cat /proc/loadavg"),
    Trouble("ram", "Memory full", "Bellek dolu", "ram bellek dolu hafıza yetmiyor", "Too many apps are kept in memory.", "Bellekte çok fazla uygulama tutuluyor.", "cat /proc/meminfo | head -3"),
    Trouble("storage", "Storage almost full", "Depolama neredeyse dolu", "depolama dolu yer kalmadı hafıza doldu", "Files, caches or apps take up the space.", "Dosyalar, önbellekler ya da uygulamalar yer kaplıyor.", "df -h /storage/emulated"),
    Trouble("crash", "Apps keep crashing", "Uygulamalar çöküyor", "uygulama çöküyor kapanıyor hata veriyor durdu", "A crash log shows which app and why.", "Çökme kaydı hangi uygulamanın ve neden çöktüğünü gösterir.", "logcat -d -b crash | tail -20"),
    Trouble("install", "App will not install", "Uygulama kurulmuyor", "uygulama kurulmuyor apk yüklenmiyor kurulum hatası", "Unknown sources may be blocked, or the storage is too small.", "Bilinmeyen kaynaklar engellenmiş olabilir ya da depolama küçük.", "settings get secure install_non_market_apps"),
    Trouble("uninstall", "Cannot uninstall an app", "Uygulama silinmiyor", "uygulama silinmiyor kaldıramıyorum", "A device admin or a system app can block removal.", "Bir cihaz yöneticisi ya da sistem uygulaması kaldırmayı engelleyebilir.", "pm list packages -3 | head -20"),
    Trouble("autostart", "Apps start by themselves", "Uygulamalar kendiliğinden açılıyor", "uygulama kendi kendine açılıyor otomatik başlıyor", "Apps with boot or background rights start on their own.", "Açılış ya da arka plan izni olan uygulamalar kendiliğinden başlar.", "dumpsys activity processes | grep -c ProcessRecord"),
    Trouble("doze", "Background apps stop", "Arka plan uygulamaları duruyor", "arka plan bildirim gecikiyor uygulama duruyor doze", "The phone's doze mode pauses background work.", "Telefonun doze modu arka plan işlerini durdurur.", "dumpsys deviceidle | grep -m3 'mState'"),
    Trouble("sensor", "Sensors not working", "Sensörler çalışmıyor", "sensör gyro ivme ışık sensörü çalışmıyor", "A sensor may be blocked or the service is stuck.", "Bir sensör engellenmiş ya da servis takılmış olabilir.", "dumpsys sensorservice | head -10"),
    Trouble("touch", "Touch unresponsive", "Dokunmatik tepki vermiyor", "dokunmatik ekran dokunmuyor ekran tepki vermiyor", "Touch input or a screen protector can cause it.", "Dokunma girişi ya da ekran koruyucu bunu yapabilir.", "dumpsys input | grep -m5 -i 'touch'"),
    Trouble("fingerprint", "Fingerprint not working", "Parmak izi çalışmıyor", "parmak izi okuyucu açılmıyor kilit açılmıyor", "The fingerprint service may be stuck or no print is enrolled.", "Parmak izi servisi takılmış olabilir ya da kayıtlı parmak izi yok.", "dumpsys fingerprint | head -8"),
    Trouble("lock", "Lock screen problem", "Kilit ekranı sorunu", "kilit ekranı kilit açılmıyor şifre", "A lock setting or the lock service can cause it.", "Bir kilit ayarı ya da kilit servisi bunu yapabilir.", "settings get secure lock_screen_show_notifications"),
    Trouble("screenshot", "Screenshot not working", "Ekran görüntüsü alınmıyor", "ekran görüntüsü alınmıyor screenshot kaydedilmiyor", "An app can block screenshots, or storage is full.", "Bir uygulama ekran görüntüsünü engelleyebilir ya da depolama dolu.", "dumpsys window | grep -m3 -i 'secure'"),
    Trouble("refresh", "Screen flickers or jumps", "Ekran titriyor ya da zıplıyor", "ekran titriyor yanıp sönüyor ekran zıplıyor", "The refresh rate or a display setting can cause it.", "Yenileme hızı ya da bir ekran ayarı bunu yapabilir.", "settings get system peak_refresh_rate"),
    Trouble("display", "Display colors wrong", "Ekran renkleri yanlış", "renkler yanlış ekran renk filtre", "A colour filter or invert colours may be on.", "Bir renk filtresi ya da renkleri ters çevirme açık olabilir.", "settings get secure accessibility_display_inversion_enabled", "settings put secure accessibility_display_inversion_enabled 0", "Turn invert colours off", "Renkleri ters çevirmeyi kapat"),
    Trouble("devopt", "Developer options turned on", "Geliştirici seçenekleri açık", "geliştirici seçenekleri açık developer options", "A developer setting can change how apps behave.", "Bir geliştirici ayarı uygulamaların davranışını değiştirebilir.", "settings get global development_settings_enabled"),
    Trouble("adb", "USB debugging on", "USB hata ayıklama açık", "usb hata ayıklama adb açık", "USB debugging can stay on after use.", "USB hata ayıklama kullanımdan sonra da açık kalabilir.", "settings get global adb_enabled"),
    Trouble("nfc", "NFC not working", "NFC çalışmıyor", "nfc ödeme temas okuma çalışmıyor", "NFC may be off or the reader app is blocked.", "NFC kapalı olabilir ya da okuyucu uygulaması engelli.", "dumpsys nfc | head -5"),
    Trouble("vpn", "VPN keeps dropping", "VPN sürekli kopuyor", "vpn bağlantı kopuyor internet gitmiyor", "A VPN app or the network may drop the tunnel.", "Bir VPN uygulaması ya da ağ tüneli düşürüyor olabilir.", "dumpsys connectivity | grep -m3 -i 'vpn'"),
    Trouble("restart", "Phone reboots itself", "Telefon kendi kendine yeniden başlıyor", "telefon kendi kendine kapanıyor yeniden başlıyor", "A crash in the system or an overheating phone can cause reboots.", "Sistemde bir çökme ya da aşırı ısınma yeniden başlatmaya yol açabilir.", "logcat -b kernel -d | grep -m5 -i 'reboot'"),
    Trouble("update", "Updates fail", "Güncelleme başarısız", "güncelleme yüklenmiyor sistem güncellemesi hata", "Storage or a bad network can break the update.", "Depolama ya da kötü ağ güncellemeyi bozabilir.", "getprop ro.build.version.release"),
    Trouble("gallery", "Photos missing from gallery", "Fotoğraflar galeride yok", "fotoğraf galeride yok resimler görünmüyor medya taraması", "The media index may be out of date.", "Medya dizini güncel olmayabilir.", "ls /sdcard/DCIM | head -5"),
    Trouble("sdcard", "SD card not found", "SD kart bulunamıyor", "sd kart görünmüyor hafıza kartı takılmıyor", "The card may be unmounted or not formatted for the phone.", "Kart takılı değil ya da telefon için biçimlendirilmemiş olabilir.", "ls /storage"),
    Trouble("sleep", "Music stops when screen off", "Ekran kapanınca müzik duruyor", "ekran kapanınca müzik durur arka planda ses kesiliyor", "Background audio can be limited by the system (see the Audio tab).", "Arka plan sesi sistem tarafından sınırlanmış olabilir (Ses sekmesine bak).", "dumpsys audio | grep -m5 -i 'hardening'"),
)

internal val troubleGroup: Map<String, TGroup> = mapOf(
    "cam" to TGroup.CAMERA, "cam-flash" to TGroup.CAMERA, "mic" to TGroup.AUDIO, "speaker" to TGroup.AUDIO,
    "vol-safe" to TGroup.AUDIO, "wifi" to TGroup.NETWORK, "wifi-hot" to TGroup.NETWORK, "bt" to
    TGroup.NETWORK, "mobile" to TGroup.PHONE, "airplane" to TGroup.PHONE, "sim" to TGroup.PHONE, "calls" to
    TGroup.PHONE, "gps" to TGroup.LOCATION, "time" to TGroup.LOCATION, "tz" to TGroup.LOCATION, "keyboard" to
    TGroup.INPUT, "font" to TGroup.DISPLAY, "rotate" to TGroup.DISPLAY, "bright" to TGroup.DISPLAY, "timeout"
    to TGroup.DISPLAY, "dark" to TGroup.DISPLAY, "notif" to TGroup.NOTIFY, "dnd" to TGroup.NOTIFY, "battery"
    to TGroup.BATTERY, "charge" to TGroup.BATTERY, "health" to TGroup.BATTERY, "heat" to TGroup.BATTERY,
    "slow" to TGroup.MEMORY, "ram" to TGroup.MEMORY, "storage" to TGroup.STORAGE, "crash" to TGroup.MEMORY,
    "install" to TGroup.STORAGE, "uninstall" to TGroup.STORAGE, "autostart" to TGroup.BATTERY, "doze" to
    TGroup.BATTERY, "sensor" to TGroup.INPUT, "touch" to TGroup.INPUT, "fingerprint" to TGroup.SECURITY,
    "lock" to TGroup.SECURITY, "screenshot" to TGroup.CAMERA, "refresh" to TGroup.DISPLAY, "display" to
    TGroup.DISPLAY, "devopt" to TGroup.SECURITY, "adb" to TGroup.SECURITY, "nfc" to TGroup.NETWORK, "vpn" to
    TGroup.NETWORK, "restart" to TGroup.BATTERY, "update" to TGroup.SYSTEM, "gallery" to TGroup.STORAGE,
    "sdcard" to TGroup.STORAGE, "sleep" to TGroup.AUDIO
)
