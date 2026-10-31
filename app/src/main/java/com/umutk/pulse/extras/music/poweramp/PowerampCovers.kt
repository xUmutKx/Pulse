package com.umutk.pulse.extras.music.poweramp

import androidx.compose.runtime.mutableIntStateOf
import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.extras.music.forgetArt
import com.umutk.pulse.tr
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Album art the user picked online. Saved per track in the files dir and read before the embedded cover. */
internal object PowerampCovers {
    /** One search result: a 100 px thumbnail and the 600 px artwork, with the artist and the album or song name it belongs to (to check a match). */
    class Hit(val thumb: String, val big: String, val artist: String = "", val name: String = "")

    private fun fileFor(c: Context, path: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(c.filesDir, "covers"), "$hash.jpg")
    }

    /** The saved cover picture of [path] as a file, or null when there is none. */
    fun jpgOf(c: Context, path: String): File? = fileFor(c, path).takeIf { it.isFile }

    /** The saved cover of [path], or null when none was picked (or the file no longer decodes). */
    fun saved(c: Context, path: String): ImageBitmap? =
        fileFor(c, path).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

    /** The saved cover at most about 512 px, for the song lists: a full-size picture takes megabytes for each row on screen. */
    fun savedSmall(c: Context, path: String): ImageBitmap? {
        val f = fileFor(c, path).takeIf { it.isFile } ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 512) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
    }

    fun save(c: Context, path: String, jpg: ByteArray) {
        val f = fileFor(c, path)
        f.parentFile?.mkdirs()
        f.writeBytes(jpg)
        forgetArt(path)
    }

    /** Deletes every saved cover (the ones picked and the ones downloaded) and the "found nothing" marks; the embedded pictures stay. Returns how many files went. */
    fun clearAll(c: Context): Int {
        val dir = File(c.filesDir, "covers")
        val n = dir.listFiles()?.size ?: 0
        dir.deleteRecursively()
        com.umutk.pulse.extras.music.forgetAllArt()
        return n
    }

    /** Marker that an online lookup for [path] found nothing; [CoverFetch] retries after a week. */
    internal fun markMiss(c: Context, path: String) {
        val f = fileFor(c, path).let { File(it.parentFile, it.nameWithoutExtension + ".miss") }
        f.parentFile?.mkdirs()
        f.writeText("")
    }

    internal fun missTime(c: Context, path: String): Long =
        fileFor(c, path).let { File(it.parentFile, it.nameWithoutExtension + ".miss") }.takeIf { it.isFile }?.lastModified() ?: 0L

    fun fetch(url: String): ByteArray {
        val con = URL(url).openConnection() as HttpURLConnection
        try {
            con.connectTimeout = 10_000
            con.readTimeout = 10_000
            // MusicBrainz and the Cover Art Archive ask every program to say who it is
            con.setRequestProperty("User-Agent", "Pulse/1.0 (github.com/xUmutKx/Pulse)")
            return con.inputStream.use { it.readBytes() }
        } finally {
            con.disconnect()
        }
    }

    fun image(url: String): ImageBitmap? = runCatching {
        val b = fetch(url)
        BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
    }.getOrNull()

    /** Every service that has covers, one after the other (iTunes, Deezer, MusicBrainz with the Cover Art Archive; none needs a key); the same record from two of them is listed once. */
    fun search(term: String, entity: String): List<Hit> {
        val all = searchItunes(term, entity) + searchDeezer(term, entity) + (if (entity == "album") searchBrainz(term) else emptyList())
        fun key(h: Hit) = (h.artist + h.name).lowercase().filter { it.isLetterOrDigit() }
        return all.distinctBy { key(it) }.take(24)
    }

    /** Deezer search (no key): albums, or songs with the cover of their album. */
    private fun searchDeezer(term: String, entity: String): List<Hit> = runCatching {
        val q = URLEncoder.encode(term, "UTF-8")
        val data = JSONObject(String(fetch(if (entity == "album") "https://api.deezer.com/search/album?q=$q&limit=10" else "https://api.deezer.com/search?q=$q&limit=10"))).getJSONArray("data")
        (0 until data.length()).mapNotNull { i ->
            val o = data.getJSONObject(i)
            val alb = if (entity == "album") o else o.optJSONObject("album") ?: return@mapNotNull null
            val big = alb.optString("cover_xl").ifBlank { alb.optString("cover_big") }
            if (big.isBlank() || big.contains("/images/cover//")) return@mapNotNull null
            Hit(alb.optString("cover_medium").ifBlank { big }, big, o.optJSONObject("artist")?.optString("name").orEmpty(), if (entity == "album") o.optString("title") else o.optString("title"))
        }
    }.getOrDefault(emptyList())

    /** MusicBrainz release groups with the Cover Art Archive's front picture (no key). A group without a picture shows as a failed download, so these come last. */
    private fun searchBrainz(term: String): List<Hit> = runCatching {
        val res = JSONObject(String(fetch("https://musicbrainz.org/ws/2/release-group/?query=${URLEncoder.encode(term, "UTF-8")}&fmt=json&limit=5"))).getJSONArray("release-groups")
        (0 until res.length()).mapNotNull { i ->
            val o = res.getJSONObject(i)
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artist = o.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
            Hit("https://coverartarchive.org/release-group/$id/front-250", "https://coverartarchive.org/release-group/$id/front-500", artist, o.optString("title"))
        }
    }.getOrDefault(emptyList())

    /** iTunes Search API (no key). Empty on any failure. */
    private fun searchItunes(term: String, entity: String): List<Hit> = runCatching {
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=10"
        val res = JSONObject(String(fetch(url))).getJSONArray("results")
        (0 until res.length()).mapNotNull { i ->
            val o = res.getJSONObject(i)
            o.optString("artworkUrl100").takeIf { it.isNotBlank() }
                ?.let { Hit(it, it.replace("100x100bb", "600x600bb"), o.optString("artistName"), if (entity == "song") o.optString("trackName") else o.optString("collectionName")) }
        }
    }.getOrDefault(emptyList())
}

/** Grid of online album art for [track]; tapping one saves it as the track's cover. */
@Composable
internal fun CoverArtDialog(track: Track, style: PowerampStyle, onSaved: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val album = track.album?.takeIf { it.isNotBlank() }
    // the names the search uses; the fields under "No results" change them for another try
    var qArtist by remember { mutableStateOf(track.artist.orEmpty()) }
    var qName by remember { mutableStateOf(album ?: track.displayName()) }
    var attempt by remember { mutableIntStateOf(0) }
    var hits by remember { mutableStateOf<List<PowerampCovers.Hit>?>(null) }
    var saving by remember { mutableStateOf(false) }
    // a tap on a picture opens it large first; Apply puts it on the song
    var chosen by remember { mutableStateOf<PowerampCovers.Hit?>(null) }
    LaunchedEffect(track.path, attempt) {
        hits = null
        hits = withContext(Dispatchers.IO) {
            val entity = if (album != null && attempt == 0) "album" else "song"
            // narrowest first, then wider: artist and name, the name alone, the artist and the song title, the song title alone
            listOf(listOf(qArtist, qName), listOf(qName), listOf(qArtist, track.displayName()), listOf(track.displayName()))
                .map { it.filter { w -> w.isNotBlank() }.joinToString(" ") }.filter { it.isNotBlank() }.distinct()
                .firstNotNullOfOrNull { t -> PowerampCovers.search(t, entity).takeIf { it.isNotEmpty() } ?: PowerampCovers.search(t, if (entity == "album") "song" else "album").takeIf { it.isNotEmpty() } }
                .orEmpty()
        }
    }
    Dialog(onDismissRequest = { if (!saving) onClose() }) {
        Column(
            Modifier.fillMaxWidth(.92f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF161616)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr("Album art", "Albüm kapağı"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            // the names can always be spelled another way: artist and song (or album), then search again
            if (hits?.isNotEmpty() == true) Column(Modifier.padding(bottom = 10.dp)) {
                @Composable fun f2(v: String, hint: String, on: (String) -> Unit) = androidx.compose.foundation.text.BasicTextField(
                    v, on, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 8.dp),
                    decorationBox = { inner -> if (v.isEmpty()) Text(hint, color = Color.White.copy(alpha = .4f), fontSize = 14.sp); inner() })
                f2(qArtist, tr("Artist", "Sanatçı")) { qArtist = it }
                f2(qName, tr("Album or song", "Albüm ya da şarkı")) { qName = it }
                Text(tr("Search again", "Tekrar ara"), color = Color.Black, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp).align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { attempt++ }.padding(horizontal = 18.dp, vertical = 7.dp))
            }
            val list = hits
            val pick = chosen
            when {
                pick != null && !saving -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val big by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, pick.big) { value = withContext(Dispatchers.IO) { PowerampCovers.image(pick.big) } }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .08f)), Alignment.Center) {
                        big?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                            ?: CircularProgressIndicator(Modifier.size(28.dp), color = style.accent, strokeWidth = 2.dp)
                    }
                    Text(listOf(pick.name, pick.artist).filter { it.isNotBlank() }.joinToString(" · "), color = style.textDim, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("Back", "Geri"), color = Color.White, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .14f)).clickable { chosen = null }.padding(horizontal = 22.dp, vertical = 9.dp))
                        Text(tr("Apply", "Uygula"), color = Color.Black, fontSize = 14.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).clickable(enabled = big != null) {
                            saving = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) { runCatching { PowerampCovers.save(ctx, track.path, PowerampCovers.fetch(pick.big)) }.isSuccess }
                                saving = false
                                if (ok) { onSaved(); onClose() } else Toast.makeText(ctx, tr("Could not download", "İndirilemedi"), Toast.LENGTH_SHORT).show()
                            }
                        }.padding(horizontal = 22.dp, vertical = 9.dp))
                    }
                }
                list == null || saving -> Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = style.accent, strokeWidth = 2.dp)
                }
                list.isEmpty() -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(tr("No results", "Sonuç yok"), color = style.textDim, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                    @Composable fun field(v: String, hint: String, on: (String) -> Unit) = androidx.compose.foundation.text.BasicTextField(
                        v, on, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner -> if (v.isEmpty()) Text(hint, color = Color.White.copy(alpha = .4f), fontSize = 15.sp); inner() })
                    field(qArtist, tr("Artist", "Sanatçı")) { qArtist = it }
                    field(qName, tr("Album or song", "Albüm ya da şarkı")) { qName = it }
                    Text(tr("Search again", "Tekrar ara"), color = Color.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color.White).clickable { attempt++ }.padding(horizontal = 20.dp, vertical = 9.dp))
                }
                else -> Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { h ->
                                CoverThumb(h.thumb, Modifier.weight(1f)) { chosen = h }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverThumb(url: String, modifier: Modifier, onClick: () -> Unit) {
    val img by produceState<ImageBitmap?>(null, url) { value = withContext(Dispatchers.IO) { PowerampCovers.image(url) } }
    Box(
        modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF2A2A2A)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}
