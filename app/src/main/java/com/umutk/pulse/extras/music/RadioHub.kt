package com.umutk.pulse.extras.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Internet radio from Radio Browser, a free and open catalogue of stations (several servers; the first that answers is used). */
object RadioBrowser {
    private val servers = listOf("de1.api.radio-browser.info", "nl1.api.radio-browser.info", "at1.api.radio-browser.info")

    class Station(val name: String, val url: String, val country: String, val kbps: Int)

    /** The most voted stations of Turkey. Throws when no server answers. */
    fun turkey(): List<Station> = get("/json/stations/bycountrycodeexact/TR?hidebroken=true&order=votes&reverse=true&limit=150")

    /** The stations whose name holds [q], most voted first. */
    fun search(q: String): List<Station> =
        get("/json/stations/search?name=" + URLEncoder.encode(q.trim(), "UTF-8") + "&hidebroken=true&order=votes&reverse=true&limit=100")

    private fun get(path: String): List<Station> {
        var last: Throwable? = null
        for (host in servers) {
            try {
                val c = URL("https://$host$path").openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "Pulse/1.0 (Android)")
                c.connectTimeout = 10_000
                c.readTimeout = 20_000
                val arr = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
                return List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    Station(o.optString("name").trim(), o.optString("url_resolved").ifEmpty { o.optString("url") }, o.optString("country"), o.optInt("bitrate"))
                }.filter { it.url.isNotEmpty() }
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no server")
    }
}

/** The radio page of the library: Turkish stations first, a search by name; a tap plays the station's stream. */
@Composable
fun RadioHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var stations by remember { mutableStateOf<List<RadioBrowser.Station>>(emptyList()) }
    var busy by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }
    fun load(q: String) {
        scope.launch {
            busy = true; note = ""
            val result = withContext(Dispatchers.IO) { runCatching { if (q.isBlank()) RadioBrowser.turkey() else RadioBrowser.search(q) } }
            result.onSuccess { stations = it; if (it.isEmpty()) note = tr("No stations found", "İstasyon bulunamadı") }
                .onFailure { note = tr("No connection", "Bağlantı yok") }
            busy = false
        }
    }
    LaunchedEffect(Unit) { load("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, tr("Back", "Geri"), tint = T.text) }
            Text(tr("Radio", "Radyo"), color = T.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
        PField(query, { query = it }, tr("Station name", "İstasyon adı"), onDone = { load(query) })
        if (busy) Text(tr("Loading...", "Yükleniyor..."), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        else if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(stations) { _, s ->
                Row(Modifier.fillMaxWidth().clickable {
                    // the stream plays like a track: the station's address is its path
                    MusicPlayerEngine.get(ctx).playQueue(listOf(Track(path = s.url, title = s.name, artist = s.country.ifEmpty { tr("Radio", "Radyo") })), 0)
                }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Radio, null, tint = T.accent, modifier = Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(listOf(s.country, if (s.kbps > 0) "${s.kbps} kbps" else "").filter { it.isNotEmpty() }.joinToString(" · "), color = T.sub, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}
