package com.umutk.pulse.extras.radio

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One station of the radio-browser.info directory (a free, open, community-kept list of internet radio stations; no account, no key). */
class Station(val id: String, val name: String, val url: String, val country: String, val tags: String, val bitrate: Int)

object RadioDirectory {
    private val servers = listOf("https://de1.api.radio-browser.info", "https://nl1.api.radio-browser.info", "https://at1.api.radio-browser.info")

    /** Searches by name, or the most listened stations of a country (two letters) when [name] is blank. Throws with the reason when no server answers. */
    suspend fun search(name: String, country: String): List<Station> = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (s in servers) {
            try {
                val q = "/json/stations/search?limit=60&hidebroken=true&order=clickcount&reverse=true" +
                    (if (name.isNotBlank()) "&name=" + URLEncoder.encode(name.trim(), "UTF-8") else "") + (if (country.isNotBlank()) "&countrycode=$country" else "")
                val cn = URL(s + q).openConnection() as HttpURLConnection
                cn.connectTimeout = 8000; cn.readTimeout = 12000; cn.setRequestProperty("User-Agent", "Pulse/1.0 (github.com/xUmutKx/Pulse)")
                if (cn.responseCode != 200) { last = java.io.IOException("HTTP ${cn.responseCode}"); cn.disconnect(); continue }
                val txt = try { cn.inputStream.bufferedReader().use { it.readText() } } finally { cn.disconnect() }
                val a = JSONArray(txt)
                return@withContext (0 until a.length()).mapNotNull { i ->
                    val o = a.getJSONObject(i)
                    val u = o.optString("url_resolved").ifBlank { o.optString("url") }
                    if (u.isBlank() || !(u.startsWith("http://") || u.startsWith("https://"))) null
                    else Station(o.optString("stationuuid"), o.optString("name").trim(), u, o.optString("country"), o.optString("tags").split(',').take(3).joinToString(", "), o.optInt("bitrate"))
                }.distinctBy { it.name.lowercase() }
            } catch (e: Exception) { last = e }
        }
        throw last ?: java.io.IOException("no server")
    }
}

private fun favs(c: Context) = c.getSharedPreferences("radio", Context.MODE_PRIVATE)
private fun loadFavs(c: Context): List<Station> = runCatching {
    val a = JSONArray(favs(c).getString("fav", "[]"))
    (0 until a.length()).map { i -> a.getJSONObject(i).let { o -> Station(o.getString("id"), o.getString("n"), o.getString("u"), o.optString("c"), o.optString("t"), o.optInt("b")) } }
}.getOrDefault(emptyList())
private fun saveFavs(c: Context, l: List<Station>) {
    val a = JSONArray(); l.forEach { a.put(org.json.JSONObject().put("id", it.id).put("n", it.name).put("u", it.url).put("c", it.country).put("t", it.tags).put("b", it.bitrate)) }
    favs(c).edit().putString("fav", a.toString()).apply()
}

/**
 * Internet radio: search the open station directory (radio-browser.info), pick a country, play a station through the music player (so it goes on in the background
 * with the media notification), and keep favourites. Nothing is shown when the directory cannot be reached, with the reason.
 */
@Composable
fun RadioScreen(back: () -> Unit, ink: Color? = null, sub: Color? = null, accent: Color? = null) {
    val ctx = LocalContext.current
    // the colours of the screen it is shown in (the music player's own look inside it), else the app's theme
    val tx = ink ?: T.text; val sb = sub ?: T.sub; val ac = accent ?: Look.accent
    BackHandler { back() }
    val engine = remember { MusicPlayerEngine.get(ctx) }
    val st by engine.state.collectAsState()
    var q by remember { mutableStateOf("") }
    var country by remember { mutableStateOf(java.util.Locale.getDefault().country.uppercase().ifBlank { "" }) }
    var showFav by remember { mutableStateOf(false) }
    var list by remember { mutableStateOf<List<Station>>(emptyList()) }
    var err by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var fav by remember { mutableStateOf(loadFavs(ctx)) }
    var go by remember { mutableStateOf(0) }
    LaunchedEffect(go, country) {
        busy = true; err = ""
        runCatching { RadioDirectory.search(q, country) }.onSuccess { list = it }.onFailure { err = tr("The station list could not be loaded: ", "İstasyon listesi yüklenemedi: ") + (it.message ?: it.javaClass.simpleName); list = emptyList() }
        busy = false
    }
    val playing = st.current?.path?.startsWith("http") == true
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", tx, 20.dp) }
            Text(tr("Radio", "Radyo"), color = tx, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            PField(q, { q = it }, tr("Search a station", "İstasyon ara"), Modifier.weight(1f), onDone = { showFav = false; go++ })
            Box(Modifier.size(44.dp).clickable { showFav = false; go++ }, contentAlignment = Alignment.Center) { PIcon("search", tx, 22.dp) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PChip(showFav, tr("Favourites", "Favoriler")) { showFav = !showFav }
            listOf("" to tr("World", "Dünya"), "TR" to "Türkiye", "US" to "USA", "GB" to "UK", "DE" to "Deutschland", "FR" to "France", "IT" to "Italia", "ES" to "España", "NL" to "Nederland", "JP" to "日本").forEach { (c, n) -> PChip(!showFav && country == c, n) { showFav = false; country = c } }
        }
        if (err.isNotEmpty() && !showFav) Text(err, color = androidx.compose.ui.graphics.Color(0xFFE5484D), fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (busy && !showFav) Text("…", color = sb, fontSize = 16.sp, modifier = Modifier.padding(8.dp))
        val shown = if (showFav) fav else list
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (showFav && fav.isEmpty()) item { Text(tr("No favourite yet.", "Henüz favori yok."), color = sb, fontSize = 14.sp, modifier = Modifier.padding(12.dp)) }
            items(shown, key = { it.id.ifBlank { it.url } }) { s ->
                val on = st.current?.path == s.url
                val isFav = fav.any { it.id == s.id }
                Row(Modifier.fillMaxWidth().clickable { engine.playQueue(listOf(Track(s.url, s.name, artist = listOf(s.country, s.tags).filter { it.isNotBlank() }.joinToString(" · "))), 0) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(if (on && st.isPlaying) "pause" else "play", if (on) ac else sb, 22.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(s.name, color = if (on) ac else tx, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(s.country, s.tags, if (s.bitrate > 0) "${s.bitrate} kbps" else "").filter { it.isNotBlank() }.joinToString(" · "), color = sb, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(Modifier.size(40.dp).clickable { fav = if (isFav) fav.filter { it.id != s.id } else fav + s; saveFavs(ctx, fav) }, contentAlignment = Alignment.Center) {
                        PIcon("heart", if (isFav) ac else sb, 20.dp)
                    }
                }
            }
        }
        if (playing) PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(st.current?.title ?: "", color = tx, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("Radio", "Radyo"), color = sb, fontSize = 12.sp)
                }
                Box(Modifier.size(44.dp).clickable { engine.togglePlayPause() }, contentAlignment = Alignment.Center) { PIcon(if (st.isPlaying) "pause" else "play", tx, 26.dp) }
            }
        }
    }
}
