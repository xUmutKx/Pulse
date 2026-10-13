package com.umutk.pulse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown once, after the splash, on the first start: what Pulse does, what it is built from, and its size. */
@Composable
fun Welcome(onDone: () -> Unit) {
    val ctx = LocalContext.current
    fun finish() {
        ctx.getSharedPreferences("p", android.content.Context.MODE_PRIVATE).edit().putBoolean("welcomed", true).apply()
        onDone()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = pageGutter).padding(top = 40.dp, bottom = 24.dp)) {
        Text(tr("Welcome to Pulse", "Pulse'a hoş geldin"), color = T.text, fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text(tr("A task manager for Android that looks like the Windows one, with tools for the rest of the phone.",
            "Windows'a benzeyen bir Android görev yöneticisi, telefonun geri kalanı için araçlarla birlikte."),
            color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))

        WelcomeCard(tr("What it does", "Neler yapabilir")) {
            WelcomeLine(tr("Live CPU, memory, storage, network, battery and temperature, with processes and startup apps.",
                "CPU, bellek, depolama, ağ, pil ve sıcaklığı canlı gösterir; işlemler ve başlangıç uygulamalarını yönetir."))
            WelcomeLine(tr("A floating monitor, a notification with live numbers, alerts and home-screen widgets.",
                "Üstte yüzen izleyici, canlı sayıları gösteren bildirim, uyarılar ve ana ekran widget'ları."))
            WelcomeLine(tr("A file explorer with archive viewing and compressing, an APK installer and a downloads manager.",
                "Arşiv görüntüleme ve sıkıştırma özellikli dosya yöneticisi, APK kurucu ve indirme yöneticisi."))
            WelcomeLine(tr("A toolkit with a terminal, DNS settings, network usage and a cleaner for caches and large files.",
                "Terminal, DNS ayarları, ağ kullanımı ve önbellek ile büyük dosyalar için temizleyicili bir araç takımı."))
        }

        WelcomeCard(tr("Built from open source", "Açık kaynak parçalar")) {
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
