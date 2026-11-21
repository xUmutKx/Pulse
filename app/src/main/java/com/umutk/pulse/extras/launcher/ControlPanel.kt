package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
internal class CpItem(val icon: String, val en: String, val trName: String, val enDesc: String, val trDesc: String, val route: String)
internal class CpGroup(val en: String, val trName: String, val items: List<CpItem>)

internal val CONTROL_PANEL = listOf(
    CpGroup("System and Security", "Sistem ve Güvenlik", listOf(
        CpItem("phone", "System", "Sistem", "Device, processor, memory, Android version", "Aygıt, işlemci, bellek, Android sürümü", "page:device"),
        CpItem("heart", "Security and Maintenance", "Güvenlik ve Bakım", "Device health and battery wear", "Aygıt sağlığı ve pil aşınması", "health"),
        CpItem("bug", "Troubleshooting", "Sorun giderme", "Find and fix common problems", "Yaygın sorunları bul ve düzelt", "troubleshoot"),
        CpItem("services", "Administrative Tools: Services", "Yönetimsel Araçlar: Hizmetler", "Services that run in the background", "Arka planda çalışan hizmetler", "page:services"),
        CpItem("startup", "Startup", "Başlangıç", "Apps that start with the phone", "Telefonla başlayan uygulamalar", "page:startup"),
    )),
    CpGroup("Programs", "Programlar", listOf(
        CpItem("apps", "Programs and Features", "Programlar ve Özellikler", "Uninstall or change an app", "Bir uygulamayı kaldır ya da değiştir", "page:apps"),
        CpItem("processes", "Task Manager", "Görev Yöneticisi", "Running apps and what they use", "Çalışan uygulamalar ve kullandıkları", "page:taskmgr"),
        CpItem("performance", "Performance Monitor", "Performans İzleyicisi", "CPU, memory, storage and network graphs", "İşlemci, bellek, depolama ve ağ grafikleri", "page:performance"),
        CpItem("notifications", "Notification Area Icons", "Bildirim Alanı Simgeleri", "Which icons show on the taskbar", "Görev çubuğunda hangi simgeler görünsün", "trayicons"),
    )),
    CpGroup("Storage", "Depolama", listOf(
        CpItem("folder", "Folder sizes", "Klasör boyutları", "Which folders take the room", "Hangi klasörler yer kaplıyor", "folders"),
        CpItem("details", "Disk Cleanup", "Disk Temizleme", "Free space by clearing caches", "Önbellekleri silip yer aç", "cache"),
        CpItem("download", "Files by type", "Türe göre dosyalar", "Pictures, video, music, documents", "Resimler, videolar, müzik, belgeler", "typebreak"),
    )),
    CpGroup("Network and Internet", "Ağ ve İnternet", listOf(
        CpItem("globe", "DNS and TTL", "DNS ve TTL", "Private DNS and packet TTL", "Özel DNS ve paket TTL", "dns"),
    )),
    CpGroup("Appearance and Personalization", "Görünüm ve Kişiselleştirme", listOf(
        CpItem("settings", "Personalization", "Kişiselleştirme", "Look of the desktop, wallpaper, icons, dock", "Masaüstünün görünümü, duvar kağıdı, simgeler, dok", "win:lsettings"),
        CpItem("menu", "Context menu", "Sağ tık menüsü", "Choose and order the actions of an app's menu", "Uygulama menüsündeki eylemleri seç ve sırala", "win:lsettings:menu"),
        CpItem("details", "Fonts", "Yazı tipleri", "Change the system font", "Sistem yazı tipini değiştir", "fonts"),
        CpItem("run", "Command Prompt", "Komut İstemi", "Run commands", "Komut çalıştır", "page:cmd"),
    )),
)

/**
 * The Control Panel as a window opens it. It is the classic one whatever the look: a white body in Category view (the eight categories with their first tasks as links), a View by switch
 * for large or small icons, an address bar and a search box. A tap opens the page of the phone or of Pulse that does the job.
 */
@Composable
fun ControlPanelHome() {
    // the Windows 7 desktop has the Control Panel in its Category view: the big category names, each with its first tasks under it as links
    if (T.id == "win7") { Win7ControlPanel(); return }
    val open = LocalOpenTool.current
    var wDetail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<WItem?>(null) }
    val pickW: (WItem) -> Unit = { if (it.route.startsWith("intent:")) wDetail = it else open(it.route) }
    wDetail?.let { d -> Box(Modifier.fillMaxSize().background(T.bg).padding(14.dp)) { Win11Detail(d) { wDetail = null } }; return }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var view by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    var cat by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<WGroup?>(null) }
    var q by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    // Windows 11: dark and see-through (the window's body lets the desktop show), as its Settings; the other looks keep the classic white page
    val w11 = T.id == "win11"
    val deskWin = LocalDesktopWin.current
    androidx.compose.runtime.DisposableEffect(deskWin, w11) { if (w11) deskWin?.glass = true; onDispose { if (w11) deskWin?.glass = false } }
    val white = if (w11) T.bg.copy(alpha = .3f) else androidx.compose.ui.graphics.Color.White
    val ink = if (w11) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1A1A1A)
    val link = if (w11) androidx.compose.ui.graphics.Color(0xFF60CDFF) else androidx.compose.ui.graphics.Color(0xFF0066CC)
    val title = if (w11) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1E3287)
    val bar = if (w11) androidx.compose.ui.graphics.Color(0x22FFFFFF) else androidx.compose.ui.graphics.Color(0xFFF1F1F1)
    val tile = if (w11) androidx.compose.ui.graphics.Color(0x22FFFFFF) else androidx.compose.ui.graphics.Color(0xFFE8F0FA)
    val tileEdge = if (w11) androidx.compose.ui.graphics.Color(0x33FFFFFF) else androidx.compose.ui.graphics.Color(0xFFA9C0DC)
    val field = if (w11) androidx.compose.ui.graphics.Color(0x22FFFFFF) else white
    Column(Modifier.fillMaxSize().background(white)) {
        // the address bar and the search box
        Row(Modifier.fillMaxWidth().background(bar).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (cat == null) "Control Panel ›" else "Control Panel › " + tr(cat!!.en, cat!!.tr) + " ›", color = ink, fontSize = 12.sp, modifier = Modifier.weight(1f).clickable { cat = null })
            Box(Modifier.width(140.dp).background(field).border(1.dp, if (w11) tileEdge else androidx.compose.ui.graphics.Color(0xFFAAAAAA)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                androidx.compose.foundation.text.BasicTextField(q, { q = it }, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = ink, fontSize = 12.sp))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (cat == null) tr("Adjust your computer's settings", "Bilgisayar ayarlarınızı düzenleyin") else tr(cat!!.en, cat!!.tr), color = title, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(tr("View by: ", "Görüntüleme: ") + listOf(tr("Category", "Kategori"), tr("Large icons", "Büyük simgeler"), tr("Small icons", "Küçük simgeler"))[view] + " ▾", color = link, fontSize = 12.sp, modifier = Modifier.clickable { view = (view + 1) % 3 })
            }
            Spacer(Modifier.height(10.dp))
            val flat = CONTROL_PANEL_8.flatMap { it.items }
            val hits = if (q.isBlank()) emptyList() else flat.filter { tr(it.en, it.tr).contains(q, true) || tr(it.enDesc, it.trDesc).contains(q, true) }
            when {
                q.isNotBlank() -> hits.forEach { it -> CpApplet(it, 1, ink, link) { pickW(it) } }
                cat != null -> cat!!.items.forEach { it -> CpApplet(it, 1, ink, link) { pickW(it) } }
                view == 0 -> androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // room enough (a turned phone, a wide window): the categories stand in two columns
                    val cols = if (maxWidth >= 560.dp) 2 else 1
                    Column(Modifier.fillMaxWidth()) {
                        CONTROL_PANEL_8.chunked(cols).forEach { rowGroups ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                rowGroups.forEach { g ->
                                    Row(Modifier.weight(1f).padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                        Box(Modifier.size(52.dp).background(tile).border(1.dp, tileEdge), contentAlignment = Alignment.Center) { PIcon(g.icon, androidx.compose.ui.graphics.Color(0xFF2F6DB5).let { if (w11) link else it }, 32.dp) }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(tr(g.en, g.tr), color = link, fontSize = 15.sp, modifier = Modifier.clickable { cat = g })
                                            g.items.take(3).forEach { it -> Text(tr(it.en, it.tr), color = link, fontSize = 12.sp, modifier = Modifier.clickable { pickW(it) }.padding(top = 3.dp)) }
                                        }
                                    }
                                }
                                repeat(cols - rowGroups.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                else -> flat.chunked(if (view == 1) 3 else 2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { it -> Box(Modifier.weight(1f)) { CpApplet(it, view, ink, link) { pickW(it) } } }
                        repeat((if (view == 1) 3 else 2) - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CpApplet(it: WItem, mode: Int, ink: androidx.compose.ui.graphics.Color, link: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val dark = T.id == "win11"
    val iconTint = if (dark) link else androidx.compose.ui.graphics.Color(0xFF2F6DB5)
    val subTint = if (dark) androidx.compose.ui.graphics.Color(0xFFC9C9C9) else androidx.compose.ui.graphics.Color(0xFF555555)
    if (mode == 1 || mode == 0) Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(it.icon, iconTint, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(it.en, it.tr), color = link, fontSize = 14.sp)
            Text(tr(it.enDesc, it.trDesc), color = subTint, fontSize = 12.sp)
        }
    } else Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        PIcon(it.icon, iconTint, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(tr(it.en, it.tr), color = ink, fontSize = 12.sp)
    }
}

/** The Windows 10 / 11 Settings app: a title, a search box and the settings as big tiles (two across) with a line of what is inside. A tap opens the page in the same window. */
@Composable
fun WindowsSettingsHome() {
    val open = LocalOpenTool.current
    var wDetail by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<WItem?>(null) }
    val pickW: (WItem) -> Unit = { if (it.route.startsWith("intent:")) wDetail = it else open(it.route) }
    wDetail?.let { d -> Box(Modifier.fillMaxSize().background(T.bg).padding(14.dp)) { Win11Detail(d) { wDetail = null } }; return }
    // Windows 11 has its own Settings: a sidebar, a search box and rounded rows
    if (T.id == "win11") { Win11SettingsHome(open); return }
    var q by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val all = WIN11_SETTINGS.flatMap { g -> g.items.map { g to it } }
    val shown = all.filter { (_, it) -> q.isBlank() || tr(it.en, it.tr).contains(q, true) || tr(it.enDesc, it.trDesc).contains(q, true) }
    // Windows 10: one flat palette for the whole window (dark: near black with grey tiles; light: pale grey with white tiles), so the window never shows a white page behind dark tiles
    val w10 = T.id == "win10" || T.id == "win10black"
    val dark = T.dark || T.id == "win10black"
    val pageBg = if (!w10) androidx.compose.ui.graphics.Color.Transparent else if (dark) androidx.compose.ui.graphics.Color(0xFF1A1A1A) else androidx.compose.ui.graphics.Color(0xFFF0F0F0)
    val tileBg = if (!w10) T.panel else if (dark) androidx.compose.ui.graphics.Color(0xFF2B2B2B) else androidx.compose.ui.graphics.Color.White
    val ink = if (!w10) T.text else if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1B1B1B)
    val sub = if (!w10) T.sub else if (dark) androidx.compose.ui.graphics.Color(0xFFB0B0B0) else androidx.compose.ui.graphics.Color(0xFF5E5E5E)
    Column(Modifier.fillMaxSize().background(pageBg).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(tr("Settings", "Ayarlar"), color = ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        com.umutk.pulse.PField(q, { q = it }, tr("Find a setting", "Ayar bul"), Modifier.fillMaxWidth().padding(vertical = 10.dp))
        shown.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (_, it) ->
                    Row(Modifier.weight(1f).background(tileBg).clickable { pickW(it) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon(it.icon, T.accent, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(tr(it.en, it.tr), color = ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                            Text(tr(it.enDesc, it.trDesc), color = sub, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private val W7_CP_TITLE = androidx.compose.ui.graphics.Color(0xFF1E3287)
private val W7_CP_LINK = androidx.compose.ui.graphics.Color(0xFF0066CC)

/** The Control Panel of Windows 7, Category view: "Adjust your computer's settings", View by Category, the categories in two columns on a wide window. */
@Composable
private fun Win7ControlPanel() {
    val open = LocalOpenTool.current
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(if (LocalDesktopWin.current != null) listOf(androidx.compose.ui.graphics.Color(0xB8D8E6F6), androidx.compose.ui.graphics.Color(0xB8FFFFFF)) else listOf(androidx.compose.ui.graphics.Color(0xFFD8E6F6), androidx.compose.ui.graphics.Color.White), 0f, 300f)),
    ) {
        val two = maxWidth >= 560.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Adjust your computer's settings", "Bilgisayar ayarlarınızı düzenleyin"), color = W7_CP_TITLE, fontSize = 19.sp, modifier = Modifier.weight(1f))
                Text(tr("View by: Category \u25BE", "Görüntüleme: Kategori \u25BE"), color = W7_CP_LINK, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            CONTROL_PANEL.chunked(if (two) 2 else 1).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { g -> W7Category(g, Modifier.weight(1f)) { open(it) } }
                    if (row.size == 1 && two) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun W7Category(g: CpGroup, modifier: Modifier, open: (String) -> Unit) {
    Row(modifier.padding(4.dp), verticalAlignment = Alignment.Top) {
        val tile = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        Box(
            Modifier.size(52.dp).background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFF4F8FD), androidx.compose.ui.graphics.Color(0xFFCFE0F3))), tile)
                .border(1.dp, androidx.compose.ui.graphics.Color(0xFFA9C0DC), tile),
            contentAlignment = Alignment.Center,
        ) { PIcon(g.items.first().icon, androidx.compose.ui.graphics.Color(0xFF2F6DB5), 32.dp) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(tr(g.en, g.trName), color = W7_CP_TITLE, fontSize = 15.sp, modifier = Modifier.clickable { open(g.items.first().route) })
            g.items.take(3).forEach { it -> Text(tr(it.en, it.trName), color = W7_CP_LINK, fontSize = 12.sp, modifier = Modifier.clickable { open(it.route) }.padding(top = 3.dp)) }
        }
    }
}
