package com.umutk.pulse.features

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A free tool for reaching blocked sites: [what] says in a few words what it does. */
private class Bypass(val name: String, val enName: String, val trName: String, val url: String)

private val BYPASS = listOf(
    Bypass("ByeDPI", "DPI bypass on the phone (a local VPN, no server)", "Telefonda DPI aşma (yerel VPN, sunucu yok)", "https://github.com/dovecoteescapee/ByeDPIAndroid/releases"),
    Bypass("Cloudflare WARP (1.1.1.1)", "Free VPN, also reaches many DPI-blocked sites", "Ücretsiz VPN, DPI ile engellenen çoğu siteye de ulaşır", "https://play.google.com/store/apps/details?id=com.cloudflare.onedotonedotonedotone"),
    Bypass("Proton VPN", "Free plan, no data limit, no ads", "Ücretsiz plan, veri sınırı ve reklam yok", "https://protonvpn.com/download-android"),
    Bypass("Psiphon", "Free, built to get past censorship", "Ücretsiz, sansürü aşmak için yapıldı", "https://psiphon.ca/en/download.html"),
    Bypass("Orbot (Tor)", "Tor network for the whole phone", "Tüm telefon için Tor ağı", "https://guardianproject.info/apps/org.torproject.android/"),
    Bypass("Intra", "Blocks DNS tampering with encrypted DNS", "Şifreli DNS ile DNS engelini aşar", "https://getintra.org/"),
    Bypass("Windscribe", "Free plan with a monthly data allowance", "Aylık veri hakkı olan ücretsiz plan", "https://windscribe.com/download"),
)

private val DNS_HOSTS = listOf("Cloudflare" to "one.one.one.one", "Google" to "dns.google", "AdGuard" to "dns.adguard-dns.com", "Quad9" to "dns.quad9.net")

/** Toolkit tool: encrypted DNS in one tap (it beats DNS blocks only), and the free VPN / DPI-bypass apps worth installing. */
@Composable
fun BypassTool() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf(Dns.current(ctx)) }
    var note by remember { mutableStateOf("") }
    PCard {
        Text(tr("Encrypted DNS", "Şifreli DNS"), color = T.text, fontSize = 15.sp)
        Text(tr("Now", "Şimdi") + ": " + (if (cur.first == "hostname") cur.second else cur.first), color = T.sub, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            DNS_HOSTS.forEach { (n, h) ->
                PChip(cur.first == "hostname" && cur.second == h, n) {
                    scope.launch {
                        note = tr("Switching…", "Değiştiriliyor…")
                        val r = withContext(Dispatchers.IO) { Dns.write(ctx, "hostname", h) }
                        cur = Dns.current(ctx); note = r
                    }
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        Text(tr("Beats blocks that work on the site name (DNS). A block that reads the traffic (DPI) needs one of the apps below.", "Site adı (DNS) üzerinden yapılan engelleri aşar. Trafiği okuyan engeller (DPI) için aşağıdaki uygulamalardan biri gerekir."), color = T.sub, fontSize = 12.sp)
    }
    PCard {
        Text(tr("Free VPN and DPI-bypass apps", "Ücretsiz VPN ve DPI aşma uygulamaları"), color = T.text, fontSize = 15.sp)
        Column {
            BYPASS.forEach { b ->
                Row(Modifier.fillMaxWidth().clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(b.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("globe", T.text, 20.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(b.name, color = T.text, fontSize = 14.sp)
                        Text(tr(b.enName, b.trName), color = T.sub, fontSize = 12.sp)
                    }
                    PIcon("share", T.sub, 16.dp)
                }
            }
        }
    }
}
