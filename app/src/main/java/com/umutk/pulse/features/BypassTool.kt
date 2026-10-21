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

private val DNS_HOSTS = listOf("Cloudflare" to "one.one.one.one", "Google" to "dns.google", "AdGuard" to "dns.adguard-dns.com", "Quad9" to "dns.quad9.net")

/** Toolkit tool: encrypted DNS in one tap (it beats DNS blocks only) and the TTL of outgoing packets. */
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
        Text(tr("Beats blocks that work on the site name (DNS). Blocks that read the traffic (DPI) are not beaten by it.", "Site adı (DNS) üzerinden yapılan engelleri aşar. Trafiği okuyan engelleri (DPI) aşamaz."), color = T.sub, fontSize = 12.sp)
    }
    TtlCard()
}

/** The TTL of outgoing packets (IPv4 default TTL) through root: 65 makes tethered traffic look like it comes from the phone itself. */
@Composable
private fun TtlCard() {
    val scope = rememberCoroutineScope()
    var cur by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf("") }
    fun sh(c: String) = if (com.umutk.pulse.Shell.root == true) com.umutk.pulse.Shell.runRoot(c) else com.umutk.pulse.Shell.run(c)
    fun read() { scope.launch { cur = withContext(Dispatchers.IO) { sh("cat /proc/sys/net/ipv4/ip_default_ttl").trim().takeIf { it.toIntOrNull() != null } } } }
    androidx.compose.runtime.LaunchedEffect(Unit) { read() }
    fun set(v: Int) {
        scope.launch {
            note = tr("Switching…", "Değiştiriliyor…")
            val out = withContext(Dispatchers.IO) {
                // the default for new packets, plus a mangle rule for the traffic that is routed through the phone (tethering)
                sh("sysctl -w net.ipv4.ip_default_ttl=$v; echo $v > /proc/sys/net/ipv4/ip_default_ttl; " +
                    "iptables -t mangle -D POSTROUTING -j TTL --ttl-set $v 2>/dev/null; iptables -t mangle -A POSTROUTING -j TTL --ttl-set $v 2>/dev/null; " +
                    "cat /proc/sys/net/ipv4/ip_default_ttl")
            }
            read()
            note = if (out.trim().lines().lastOrNull() == v.toString()) tr("TTL is $v", "TTL $v oldu") else tr("The phone did not take it. Root is needed.", "Telefon kabul etmedi. Root gerekir.")
        }
    }
    PCard {
        Text("TTL", color = T.text, fontSize = 15.sp)
        Text(tr("Now", "Şimdi") + ": " + (cur ?: "?"), color = T.sub, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            listOf(64, 65, 128).forEach { v -> PChip(cur == v.toString(), "$v") { set(v) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            com.umutk.pulse.PField(custom, { custom = it.filter(Char::isDigit).take(3) }, tr("Other value", "Başka değer"), Modifier.weight(1f))
            com.umutk.pulse.PButton(tr("Set", "Uygula")) { custom.toIntOrNull()?.takeIf { it in 1..255 }?.let { set(it) } }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        Text(tr("Resets when the phone restarts.", "Telefon yeniden başlayınca sıfırlanır."), color = T.sub, fontSize = 12.sp)
    }
}
