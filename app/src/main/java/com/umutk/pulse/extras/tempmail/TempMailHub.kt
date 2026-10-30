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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A disposable mailbox from mail.tm (free, no sign-up): a random address, its inbox, and the text of each mail. */
private const val BASE = "https://api.mail.tm"

private class Msg(val id: String, val from: String, val subject: String, val intro: String)

private object Mail {
    private fun p(c: Context) = c.getSharedPreferences("tempmail", Context.MODE_PRIVATE)
    fun address(c: Context) = p(c).getString("address", "").orEmpty()
    fun token(c: Context) = p(c).getString("token", "").orEmpty()
    fun save(c: Context, addr: String, pw: String, tok: String) { p(c).edit().putString("address", addr).putString("password", pw).putString("token", tok).apply() }

    /** One request to mail.tm; no network, a refused request or a bad answer gives null instead of an exception that would close the app. */
    fun call(method: String, path: String, body: JSONObject?, token: String?): JSONObject? =
        try { request(method, path, body, token) } catch (e: Exception) { null }

    private fun request(method: String, path: String, body: JSONObject?, token: String?): JSONObject? {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/json")
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
            if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
            val code = c.responseCode
            if (code !in 200..299) return null
            val text = c.inputStream.bufferedReader().use { it.readText() }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally { c.disconnect() }
    }

    /** A new random address on an active domain; saves it with its password and login token. */
    fun create(c: Context): Boolean {
        val domain = call("GET", "/domains", null, null)?.optJSONArray("hydra:member")?.let { arr ->
            (0 until arr.length()).map { arr.getJSONObject(it) }.firstOrNull { it.optBoolean("isActive", true) }?.optString("domain")
        } ?: return false
        val local = (1..10).map { ('a'..'z').random() }.joinToString("")
        val addr = "$local@$domain"
        val pw = (1..16).map { ('a'..'z').random() }.joinToString("") + "9!"
        call("POST", "/accounts", JSONObject().put("address", addr).put("password", pw), null) ?: return false
        val tok = call("POST", "/token", JSONObject().put("address", addr).put("password", pw), null)?.optString("token").orEmpty()
        if (tok.isEmpty()) return false
        save(c, addr, pw, tok)
        return true
    }

    fun inbox(token: String): List<Msg>? {
        val arr = call("GET", "/messages", null, token)?.optJSONArray("hydra:member") ?: return null
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { m ->
            Msg(m.optString("id"), m.optJSONObject("from")?.optString("address").orEmpty(), m.optString("subject"), m.optString("intro"))
        }
    }

    fun body(token: String, id: String): String = call("GET", "/messages/$id", null, token)?.let { it.optString("text").ifBlank { it.optString("html") } }.orEmpty()
}

/** Temp mail: a new address with one tap, the inbox, and a tap on a mail to read it. Its own look: Material, AMOLED black by default. */
@Composable
fun TempMailHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var addr by remember { mutableStateOf(Mail.address(ctx)) }
    var msgs by remember { mutableStateOf<List<Msg>?>(null) }
    var open by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var reload by remember { mutableStateOf(0) }

    suspend fun refresh() {
        val tok = Mail.token(ctx)
        if (tok.isEmpty()) return
        busy = true
        msgs = withContext(Dispatchers.IO) { runCatching { Mail.inbox(tok) }.getOrNull() } ?: emptyList()
        busy = false
    }

    androidx.compose.runtime.LaunchedEffect(addr, reload) { refresh() }

    Column(Modifier.fillMaxSize().background(T.panel).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 24.dp) }
            Text("TempMail", color = T.text, fontSize = 18.sp, modifier = Modifier.padding(start = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (addr.isEmpty()) {
            Text(tr("Get a temporary address for sign-ups; it is thrown away when you no longer need it.", "Kayıt için geçici bir adres al; işin bitince at."), color = T.sub, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            PButton(tr("New address", "Yeni adres")) {
                busy = true
                // the network call runs off the main thread; the screen follows the saved address
                Thread {
                    val ok = Mail.create(ctx)
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        busy = false
                        if (ok) addr = Mail.address(ctx) else note = tr("Could not make an address (no network?)", "Adres oluşturulamadı (ağ yok mu?)")
                    }
                }.start()
            }
        } else {
            Text(addr, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PButton(tr("Copy", "Kopyala")) {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("", addr))
                    note = tr("Copied", "Kopyalandı")
                }
                Spacer(Modifier.width(8.dp))
                PButton(tr("Refresh", "Yenile")) { note = ""; reload++ }
                Spacer(Modifier.width(8.dp))
                PButton(tr("New address", "Yeni adres")) { addr = ""; msgs = null; open = null }
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
                            Thread {
                                val body = Mail.body(Mail.token(ctx), m.id)
                                android.os.Handler(android.os.Looper.getMainLooper()).post { text = body }
                            }.start()
                        }.padding(vertical = 10.dp)) {
                            Text(m.subject.ifBlank { tr("(no subject)", "(konu yok)") }, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.from, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.intro, color = T.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
