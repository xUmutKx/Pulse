package com.umutk.pulse.extras.netlab

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.features.NetPolicy
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Network lab: small tools for the Wi-Fi the phone is on, written for Pulse: what the network says about itself, who is on it, ping, which ports of a device are open,
 * DNS, waking a computer, how the signal is, and whether a public Wi-Fi keeps its guests apart. Everything runs on the phone; nothing is sent anywhere but to the
 * address that is asked about.
 */
object NetLab {
    class Info(val ssidLine: String, val addresses: String, val gateway: String, val dns: String, val rssi: Int, val speed: Int, val mhz: Int)

    @Suppress("DEPRECATION")
    fun info(c: Context): Info {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork)
        val addr = lp?.linkAddresses?.joinToString(", ") { it.address.hostAddress.orEmpty() }.orEmpty()
        val gw = lp?.routes?.firstOrNull { it.isDefaultRoute && it.gateway != null }?.gateway?.hostAddress.orEmpty()
        val dns = lp?.dnsServers?.joinToString(", ") { it.hostAddress.orEmpty() }.orEmpty()
        val wm = c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ci = runCatching { wm.connectionInfo }.getOrNull()
        val ssid = ci?.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        return Info(ssid ?: tr("Wi-Fi", "Wi-Fi"), addr, gw, dns, ci?.rssi ?: -127, ci?.linkSpeed ?: 0, ci?.frequency ?: 0)
    }

    /** Four echoes to [host]: the time of each, or that nobody answered. */
    suspend fun ping(host: String): List<String> = withContext(Dispatchers.IO) {
        val addr = runCatching { InetAddress.getByName(host.trim()) }.getOrNull() ?: return@withContext listOf(tr("Cannot find ${host.trim()}", "${host.trim()} bulunamadı"))
        val out = ArrayList<String>()
        repeat(4) {
            val t0 = System.nanoTime()
            val ok = runCatching { addr.isReachable(1500) }.getOrDefault(false)
            val ms = (System.nanoTime() - t0) / 1_000_000
            out += if (ok) "${addr.hostAddress}  ·  $ms ms" else tr("No answer", "Cevap yok")
            delay(250)
        }
        out
    }

    private val ports = listOf(21 to "FTP", 22 to "SSH", 23 to "Telnet", 53 to "DNS", 80 to "Web", 139 to "Windows files", 443 to "Secure web", 445 to "Windows files",
        554 to "Camera stream", 631 to "Printer", 1900 to "UPnP", 3389 to "Remote desktop", 5000 to "Web or UPnP", 8080 to "Web", 8443 to "Secure web", 9100 to "Printer")

    /** The common ports of [host] that accept a connection. */
    suspend fun portCheck(host: String): List<String> = withContext(Dispatchers.IO) {
        val h = host.trim()
        if (runCatching { InetAddress.getByName(h) }.isFailure) return@withContext listOf(tr("Cannot find $h", "$h bulunamadı"))
        val open = coroutineScope {
            ports.map { (p, name) -> async { val ok = runCatching { Socket().use { s -> s.connect(InetSocketAddress(h, p), 700); true } }.getOrDefault(false); Triple(p, name, ok) } }.awaitAll()
        }.filter { it.third }.map { "${it.first}  ·  ${it.second}" }
        open.ifEmpty { listOf(tr("None of the common ports is open", "Yaygın portların hiçbiri açık değil")) }
    }

    /** Every address a name has. */
    suspend fun dns(host: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { InetAddress.getAllByName(host.trim()).map { (if (it is java.net.Inet6Address) "IPv6  " else "IPv4  ") + it.hostAddress } }
            .getOrElse { listOf(tr("No answer for ${host.trim()}", "${host.trim()} için cevap yok")) }
    }

    /** The magic packet that switches on a computer whose network card listens for it (aa:bb:cc:dd:ee:ff). */
    suspend fun wake(mac: String): String = withContext(Dispatchers.IO) {
        val b = mac.trim().split(':', '-').mapNotNull { it.toIntOrNull(16)?.toByte() }
        if (b.size != 6) return@withContext tr("The address must be six pairs, like aa:bb:cc:dd:ee:ff", "Adres altı çift olmalı: aa:bb:cc:dd:ee:ff")
        val data = ByteArray(6) { 0xFF.toByte() } + (1..16).flatMap { b }.toByteArray()
        runCatching {
            DatagramSocket().use { s -> s.broadcast = true; s.send(DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), 9)) }
            tr("Sent. A computer set to wake on the network turns on in a moment.", "Gönderildi. Ağdan uyanacak şekilde ayarlı bilgisayar birazdan açılır.")
        }.getOrElse { tr("Could not send: ${it.message}", "Gönderilemedi: ${it.message}") }
    }
}

@Composable
fun NetLabScreen(back: () -> Unit) {
    var page by remember { mutableStateOf("home") }
    BackHandler { if (page == "home") back() else page = "home" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { if (page == "home") back() else page = "home" }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Network lab", "Ağ laboratuvarı"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
        when (page) {
            "info" -> InfoPage()
            "devices" -> DevicesPage()
            "ping" -> HostPage(tr("Ping", "Ping"), tr("Name or address", "Ad veya adres"), "ping") { NetLab.ping(it) }
            "ports" -> HostPage(tr("Port check", "Port kontrolü"), tr("Address of one device", "Bir cihazın adresi"), "ports") { NetLab.portCheck(it) }
            "dns" -> HostPage(tr("DNS lookup", "DNS sorgusu"), tr("Name, like example.com", "Ad, örneğin example.com"), "dns") { NetLab.dns(it) }
            "wake" -> WakePage()
            "signal" -> SignalPage()
            "public" -> PublicPage()
            else -> Home { page = it }
        }
    }
}

@Composable
private fun Home(open: (String) -> Unit) {
    val ctx = LocalContext.current
    val info = remember { NetLab.info(ctx) }
    PCard {
        Text(info.ssidLine, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(listOf(info.addresses, info.gateway.takeIf { it.isNotEmpty() }?.let { tr("router $it", "yönlendirici $it") }).filterNotNull().filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { tr("Not on a Wi-Fi network", "Wi-Fi ağında değil") },
            color = T.sub, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
    val tiles = listOf(
        Triple("devices", tr("Who is here", "Kimler burada"), tr("Devices on this Wi-Fi", "Bu Wi-Fi'daki cihazlar")) to "sensors",
        Triple("public", tr("Public Wi-Fi check", "Halka açık Wi-Fi kontrolü"), tr("Does it keep guests apart?", "Misafirleri birbirinden ayırıyor mu?")) to "shield",
        Triple("signal", tr("Signal meter", "Sinyal ölçer"), tr("Live strength, walk around", "Canlı güç, dolaşarak bak")) to "wifi",
        Triple("info", tr("Network info", "Ağ bilgisi"), tr("Address, router, DNS", "Adres, yönlendirici, DNS")) to "info",
        Triple("ping", tr("Ping", "Ping"), tr("Is it up, how fast", "Açık mı, ne kadar hızlı")) to "bolt",
        Triple("ports", tr("Port check", "Port kontrolü"), tr("Open ports of one device", "Bir cihazın açık portları")) to "search",
        Triple("dns", tr("DNS lookup", "DNS sorgusu"), tr("Every address of a name", "Bir adın tüm adresleri")) to "globe",
        Triple("wake", tr("Wake on LAN", "Ağdan uyandır"), tr("Turn on a computer", "Bir bilgisayarı aç")) to "power",
    )
    tiles.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (t, icon) ->
                PCard(Modifier.weight(1f).clickable { open(t.first) }) {
                    PIcon(icon, Look.accent, 24.dp)
                    Text(t.second, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(t.third, color = T.sub, fontSize = 12.sp)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = T.sub, fontSize = 13.sp, modifier = Modifier.weight(.4f))
        Text(value.ifEmpty { "—" }, color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(.6f))
    }
}

@Composable
private fun InfoPage() {
    val ctx = LocalContext.current
    val i = remember { NetLab.info(ctx) }
    PCard {
        Line(tr("Network", "Ağ"), i.ssidLine)
        Line(tr("Address", "Adres"), i.addresses)
        Line(tr("Router", "Yönlendirici"), i.gateway)
        Line("DNS", i.dns)
        Line(tr("Signal", "Sinyal"), if (i.rssi > -127) "${i.rssi} dBm" else "")
        Line(tr("Link speed", "Bağlantı hızı"), if (i.speed > 0) "${i.speed} Mbit/s" else "")
        Line(tr("Band", "Bant"), if (i.mhz > 0) (if (i.mhz > 4000) "5 GHz" else "2.4 GHz") + " (${i.mhz} MHz)" else "")
    }
}

@Composable
private fun HostPage(title: String, hint: String, key: String, run: suspend (String) -> List<String>) {
    val scope = rememberCoroutineScope()
    var host by remember(key) { mutableStateOf("") }
    var out by remember(key) { mutableStateOf<List<String>>(emptyList()) }
    var busy by remember(key) { mutableStateOf(false) }
    PCard {
        Text(title, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        PField(host, { host = it }, hint, Modifier.fillMaxWidth().padding(top = 8.dp))
        PButton(if (busy) tr("Working…", "Çalışıyor…") else tr("Start", "Başlat"), Modifier.padding(top = 8.dp)) {
            if (!busy && host.isNotBlank()) { busy = true; scope.launch { out = run(host); busy = false } }
        }
    }
    if (out.isNotEmpty()) PCard { out.forEach { Text(it, color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 2.dp)) } }
}

@Composable
private fun WakePage() {
    val scope = rememberCoroutineScope()
    var mac by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    PCard {
        Text(tr("Wake on LAN", "Ağdan uyandır"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("The computer's hardware address, like aa:bb:cc:dd:ee:ff. Its network card has to be set to wake on the network.", "Bilgisayarın donanım adresi, örneğin aa:bb:cc:dd:ee:ff. Ağ kartının ağdan uyanmaya ayarlı olması gerekir."), color = T.sub, fontSize = 12.sp)
        PField(mac, { mac = it }, "aa:bb:cc:dd:ee:ff", Modifier.fillMaxWidth().padding(top = 8.dp))
        PButton(tr("Send", "Gönder"), Modifier.padding(top = 8.dp)) { scope.launch { out = NetLab.wake(mac) } }
    }
    if (out.isNotEmpty()) PCard { Text(out, color = T.text, fontSize = 13.sp) }
}

@Composable
private fun SignalPage() {
    val ctx = LocalContext.current
    var i by remember { mutableStateOf(NetLab.info(ctx)) }
    // looked at once a second: walk around and watch it
    LaunchedEffect(Unit) { while (true) { i = withContext(Dispatchers.IO) { NetLab.info(ctx) }; delay(1000) } }
    val pct = ((i.rssi + 100) * 2).coerceIn(0, 100)
    val verdict = when { i.rssi <= -127 -> tr("Not on Wi-Fi", "Wi-Fi'da değil"); i.rssi >= -55 -> tr("Strong", "Güçlü"); i.rssi >= -67 -> tr("Good", "İyi"); i.rssi >= -78 -> tr("Weak", "Zayıf"); else -> tr("Very weak", "Çok zayıf") }
    PCard {
        Text(if (i.rssi > -127) "${i.rssi} dBm" else "—", color = T.text, fontSize = 40.sp, fontWeight = FontWeight.Light)
        Text(verdict + if (i.speed > 0) " · ${i.speed} Mbit/s" else "", color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(10.dp).androidClip().androidBg(T.text.copy(alpha = .12f))) {
            Box(Modifier.fillMaxWidth(pct / 100f).height(10.dp).androidClip().androidBg(Look.accent))
        }
    }
}

@Composable
private fun DevicesPage() {
    val ctx = LocalContext.current
    var list by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    fun scan() { list = null; scope.launch { list = withContext(Dispatchers.IO) { NetPolicy.scanList(ctx) } } }
    LaunchedEffect(Unit) { scan() }
    PCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Who is here", "Kimler burada"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PButton(tr("Scan again", "Yeniden tara"), compact = true) { scan() }
        }
        Text(list?.let { tr("${it.size} devices answered", "${it.size} cihaz cevap verdi") } ?: tr("Asking every address…", "Her adres soruluyor…"), color = T.sub, fontSize = 13.sp)
    }
    list?.forEach { ip -> PCard { Text(ip, color = T.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace) } }
}

@Composable
private fun PublicPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var count by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    PCard {
        Text(tr("Public Wi-Fi check", "Halka açık Wi-Fi kontrolü"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("A good public Wi-Fi keeps the guests apart (client isolation), so nobody can reach your phone and you cannot reach theirs. This asks every address of the network once.",
            "İyi bir halka açık Wi-Fi misafirleri birbirinden ayırır (istemci yalıtımı): kimse telefonuna ulaşamaz, sen de onlara ulaşamazsın. Ağın her adresine bir kez sorar."), color = T.sub, fontSize = 12.sp)
        PButton(if (busy) tr("Checking…", "Kontrol ediliyor…") else tr("Check this Wi-Fi", "Bu Wi-Fi'yı kontrol et"), Modifier.padding(top = 8.dp)) {
            if (!busy) { busy = true; scope.launch { count = withContext(Dispatchers.IO) { NetPolicy.scanList(ctx).size }; busy = false } }
        }
    }
    count?.let { n ->
        // the router is one of the answers: one or none means that nobody else could be reached
        PCard {
            Text(if (n <= 1) tr("Guests look kept apart", "Misafirler ayrı görünüyor") else tr("Other devices can be reached", "Diğer cihazlara ulaşılabiliyor"),
                color = if (n <= 1) Look.accent else T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(if (n <= 1) tr("Only $n device answered: the Wi-Fi probably isolates its guests. Good for a public place.", "Yalnızca $n cihaz cevap verdi: Wi-Fi büyük ihtimalle misafirleri yalıtıyor. Halka açık yerde iyidir.")
                else tr("$n devices answered, so this Wi-Fi does not keep its guests apart. On a public network, anyone on it can try to reach your phone: avoid private logins here.",
                    "$n cihaz cevap verdi, yani bu Wi-Fi misafirleri ayırmıyor. Halka açık bir ağda bunun içindeki herkes telefonuna ulaşmayı deneyebilir: burada özel girişlerden kaçın."),
                color = T.sub, fontSize = 13.sp)
        }
    }
}

private fun Modifier.androidClip(): Modifier = this.then(Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp)))
private fun Modifier.androidBg(c: androidx.compose.ui.graphics.Color): Modifier = this.then(Modifier.background(c))
