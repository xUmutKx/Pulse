package com.umutk.pulse.extras.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

private class NewsItem(val title: String, val link: String, val source: String, val time: Long)

/** The news sources (RSS or Atom addresses), kept in the launcher's own preferences. */
object NewsSources {
    private val DEFAULT = listOf("https://feeds.bbci.co.uk/news/world/rss.xml", "https://hnrss.org/frontpage")
    private fun prefs(c: Context) = c.getSharedPreferences("pulse_news", Context.MODE_PRIVATE)
    fun list(c: Context): List<String> = prefs(c).getString("urls", null)?.split('\n')?.filter { it.isNotBlank() } ?: DEFAULT
    fun save(c: Context, l: List<String>) { prefs(c).edit().putString("urls", l.joinToString("\n")).apply() }
}

private fun readFeed(url: String): List<NewsItem> = runCatching {
    val con = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 8000; setRequestProperty("User-Agent", "Mozilla/5.0 (Android)") }
    val out = ArrayList<NewsItem>()
    con.inputStream.use { ins ->
        val p = XmlPullParserFactory.newInstance().newPullParser().apply { setInput(ins, null) }
        var channel = Uri.parse(url).host.orEmpty()
        var title = ""; var link = ""; var time = 0L; var inItem = false
        var e = p.eventType
        while (e != XmlPullParser.END_DOCUMENT && out.size < 15) {
            when (e) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "item", "entry" -> { inItem = true; title = ""; link = ""; time = 0L }
                    "title" -> { val t = p.nextText().trim(); if (inItem) title = t else if (t.isNotEmpty()) channel = t }
                    "link" -> if (inItem) { val h = p.getAttributeValue(null, "href"); link = if (!h.isNullOrBlank()) h else p.nextText().trim() }
                    "pubDate", "published", "updated" -> if (inItem) {
                        val t = p.nextText().trim()
                        time = listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "yyyy-MM-dd'T'HH:mm:ssXXX").firstNotNullOfOrNull { f -> runCatching { java.text.SimpleDateFormat(f, java.util.Locale.US).parse(t)?.time }.getOrNull() } ?: 0L
                    }
                }
                XmlPullParser.END_TAG -> if ((p.name == "item" || p.name == "entry") && inItem) { inItem = false; if (title.isNotEmpty()) out += NewsItem(title, link, channel, time) }
            }
            e = p.next()
        }
    }
    out
}.getOrDefault(emptyList())

/** The news page: the newest items of the chosen sources together, a tap opens the article in the browser; the plus adds a source, a held item's source is taken off. */
@Composable
fun NewsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var sources by remember { mutableStateOf(NewsSources.list(ctx)) }
    var items by remember { mutableStateOf<List<NewsItem>?>(null) }
    var adding by remember { mutableStateOf(false) }
    var newUrl by remember { mutableStateOf("") }
    LaunchedEffect(sources) {
        items = null
        items = withContext(Dispatchers.IO) { sources.flatMap { readFeed(it) }.sortedByDescending { it.time } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 22.dp) }
                Text(tr("News", "Haberler"), color = T.text, fontSize = 20.sp, modifier = Modifier.weight(1f).padding(start = 6.dp))
                Box(Modifier.size(40.dp).clickable { adding = !adding }, contentAlignment = Alignment.Center) { PIcon("add", T.accent, 22.dp) }
            }
            if (adding) Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(newUrl, { newUrl = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 14.sp), cursorBrush = SolidColor(T.accent),
                    modifier = Modifier.weight(1f).background(T.panel).padding(12.dp),
                    decorationBox = { inner -> if (newUrl.isEmpty()) Text(tr("RSS or Atom address", "RSS veya Atom adresi"), color = T.sub, fontSize = 14.sp); inner() })
                Box(Modifier.size(44.dp).clickable {
                    val u = newUrl.trim().let { if (it.startsWith("http")) it else "https://$it" }
                    if (u.length > 10) { sources = (sources + u).distinct(); NewsSources.save(ctx, sources); newUrl = ""; adding = false }
                }, contentAlignment = Alignment.Center) { PIcon("check", T.accent, 22.dp) }
            }
            val list = items
            if (list == null) Text(tr("Loading…", "Yükleniyor…"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            else if (list.isEmpty()) Text(tr("Nothing could be read (is there a connection?)", "Hiçbir şey okunamadı (bağlantı var mı?)"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(list.orEmpty()) { n ->
                    Column(Modifier.fillMaxWidth().clickable { if (n.link.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }.padding(vertical = 10.dp, horizontal = 4.dp)) {
                        Text(n.title, color = T.text, fontSize = 15.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(n.source, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
