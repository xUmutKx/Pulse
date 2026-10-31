package com.umutk.pulse.extras.tempmail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/*
 * Temp mail. The calls follow the Baridmail app (MIT licence, Copyright (c) 2025 Mhamad, github.com/mm6x),
 * which reads the driftz temp-mail API: an address is a random name on one of its domains, its mail is read by that address.
 */
private const val API = "https://api.driftz.net"
private val DOMAINS = listOf("driftz.net", "vwh.sh", "iusearch.lol", "lifetalk.us", "z44d.pro", "wael.fun", "tawbah.site", "kuruptd.ink", "oxno1.space")

private class Msg(val id: String, val from: String, val subject: String)

private object Mail {
    private fun p(c: Context) = c.getSharedPreferences("tempmail", Context.MODE_PRIVATE)
    fun address(c: Context) = p(c).getString("address", "").orEmpty()

    /** A new address: a random name on one of the domains. No network is needed to make it. */
    fun newAddress(c: Context): String {
        val addr = UUID.randomUUID().toString().substring(0, 8) + "@" + DOMAINS.random()
        p(c).edit().putString("address", addr).apply()
        return addr
    }

    /** One GET to the API; null when there is no network, the request is refused or the answer is not JSON. */
    fun get(path: String): JSONObject? = try {
        val c = URL(API + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "GET"
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            c.setRequestProperty("User-Agent", "Pulse/1.0 (temp mail)")
            if (c.responseCode !in 200..299) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally { c.disconnect() }
    } catch (e: Exception) { null }

    /** The mails of an address, newest first; null when the service could not be reached. */
    fun inbox(addr: String): List<Msg>? {
        val j = get("/emails/$addr?limit=20") ?: return null
        val items = j.optJSONObject("result")?.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { i ->
            val m = items.optJSONObject(i) ?: return@mapNotNull null
            val from = m.optString("fromAddress").ifBlank { m.optString("from_address") }
            Msg(m.optString("id"), from, m.optString("subject"))
        }
    }

    /** The text of one mail; the HTML with its tags taken out when there is no plain text. */
    fun body(id: String): String {
        val r = get("/inbox/$id")?.optJSONObject("result") ?: return ""
        return r.optString("textContent").ifBlank { r.optString("htmlContent").replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim() }
    }
}

/** Temp mail: a new address with one tap, the inbox, and a tap on a mail to read it. Follows the look of Pulse. */
@Composable
fun TempMailHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var addr by remember { mutableStateOf(Mail.address(ctx)) }
    var msgs by remember { mutableStateOf<List<Msg>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    LaunchedEffect(addr, reload) {
        if (addr.isEmpty()) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) { runCatching { Mail.inbox(addr) }.getOrNull() }
        msgs = list ?: emptyList()
        if (list == null) note = tr("Could not reach the mail service (no network?)", "Posta servisine ulaşılamadı (ağ yok mu?)")
    }

    Column(Modifier.fillMaxSize().background(T.panel).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 24.dp) }
            Text("TempMail", color = T.text, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (addr.isEmpty()) {
            Text(tr("Get a temporary address for sign-ups; it is thrown away when you no longer need it.", "Kayıt için geçici bir adres al; işin bitince at."), color = T.sub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            PButton(tr("New address", "Yeni adres")) { addr = Mail.newAddress(ctx); msgs = null; note = "" }
        } else {
            Text(addr, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PButton(tr("Copy", "Kopyala")) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", addr))
                    note = tr("Copied", "Kopyalandı")
                }
                Spacer(Modifier.width(8.dp))
                PButton(tr("Refresh", "Yenile")) { note = ""; msgs = null; reload++ }
                Spacer(Modifier.width(8.dp))
                PButton(tr("New address", "Yeni adres")) { addr = Mail.newAddress(ctx); msgs = null; open = null; note = "" }
            }
            if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            val list = msgs
            when {
                open != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    PButton(tr("Back to the inbox", "Gelen kutusuna dön")) { open = null }
                    Text(text.ifEmpty { tr("Loading…", "Yükleniyor…") }, color = T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                list == null -> Text(tr("Loading…", "Yükleniyor…"), color = T.sub, fontSize = 13.sp)
                list.isEmpty() -> Text(tr("No mail yet", "Henüz mail yok"), color = T.sub, fontSize = 13.sp)
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(list) { m ->
                        Column(Modifier.fillMaxWidth().clickable {
                            open = m.id; text = ""
                            scope.launch { text = withContext(Dispatchers.IO) { runCatching { Mail.body(m.id) }.getOrNull() }.orEmpty() }
                        }.padding(vertical = 10.dp)) {
                            Text(m.subject.ifBlank { tr("(no subject)", "(konu yok)") }, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.from.ifBlank { tr("Unknown", "Bilinmiyor") }, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
