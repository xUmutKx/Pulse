package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalOpenTool
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr

/** One Control Panel entry: what it is called in Windows, what it shows, and the Pulse page behind it. */
private class CpItem(val icon: String, val en: String, val trName: String, val enDesc: String, val trDesc: String, val route: String)
private class CpGroup(val en: String, val trName: String, val items: List<CpItem>)

private val CONTROL_PANEL = listOf(
    CpGroup("System and Security", "Sistem ve Güvenlik", listOf(
        CpItem("phone", "System", "Sistem", "Device, processor, memory, Android version", "Aygıt, işlemci, bellek, Android sürümü", "page:device"),
        CpItem("heart", "Security and Maintenance", "Güvenlik ve Bakım", "Device health and battery wear", "Aygıt sağlığı ve pil aşınması", "health"),
        CpItem("bug", "Troubleshooting", "Sorun giderme", "Find and fix common problems", "Yaygın sorunları bul ve düzelt", "troubleshoot"),
        CpItem("services", "Administrative Tools: Services", "Yönetimsel Araçlar: Hizmetler", "Services that run in the background", "Arka planda çalışan hizmetler", "page:services"),
        CpItem("startup", "Startup", "Başlangıç", "Apps that start with the phone", "Telefonla başlayan uygulamalar", "page:startup"),
    )),
    CpGroup("Programs", "Programlar", listOf(
        CpItem("apps", "Programs and Features", "Programlar ve Özellikler", "Uninstall or change an app", "Bir uygulamayı kaldır ya da değiştir", "page:apps"),
        CpItem("processes", "Task Manager", "Görev Yöneticisi", "Running apps and what they use", "Çalışan uygulamalar ve kullandıkları", "page:processes"),
        CpItem("performance", "Performance Monitor", "Performans İzleyicisi", "CPU, memory, storage and network graphs", "İşlemci, bellek, depolama ve ağ grafikleri", "page:performance"),
    )),
    CpGroup("Storage", "Depolama", listOf(
        CpItem("folder", "Folder sizes", "Klasör boyutları", "Which folders take the room", "Hangi klasörler yer kaplıyor", "folders"),
        CpItem("details", "Disk Cleanup", "Disk Temizleme", "Free space by clearing caches", "Önbellekleri silip yer aç", "cache"),
        CpItem("download", "Files by type", "Türe göre dosyalar", "Pictures, video, music, documents", "Resimler, videolar, müzik, belgeler", "typebreak"),
    )),
    CpGroup("Network and Internet", "Ağ ve İnternet", listOf(
        CpItem("globe", "Network usage", "Ağ kullanımı", "Data used by each app", "Her uygulamanın kullandığı veri", "netusage"),
        CpItem("globe", "DNS and TTL", "DNS ve TTL", "Private DNS and packet TTL", "Özel DNS ve paket TTL", "dns"),
    )),
    CpGroup("Appearance and Personalization", "Görünüm ve Kişiselleştirme", listOf(
        CpItem("settings", "Personalization", "Kişiselleştirme", "Theme, accent, icons and pages of Pulse", "Pulse'un teması, vurgusu, ikonları ve sayfaları", "page:settings"),
        CpItem("details", "Fonts", "Yazı tipleri", "Change the system font", "Sistem yazı tipini değiştir", "fonts"),
        CpItem("run", "Command Prompt", "Komut İstemi", "Run commands", "Komut çalıştır", "page:terminal"),
    )),
)

/** The Control Panel as a window opens it: the groups with their items and a line under each saying what it holds. A tap opens the page in the same window. */
@Composable
fun ControlPanelHome() {
    val open = LocalOpenTool.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text(tr("Adjust your computer's settings", "Bilgisayar ayarlarınızı düzenleyin"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("View by: Category", "Görüntüleme: Kategori"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
        CONTROL_PANEL.forEach { g ->
            Text(tr(g.en, g.trName), color = T.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
            g.items.forEach { it ->
                Row(Modifier.fillMaxWidth().clickable { open(it.route) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(it.icon, T.accent, 30.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tr(it.en, it.trName), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(tr(it.enDesc, it.trDesc), color = T.sub, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
