package com.umutk.pulse.extras.music.poweramp

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
    /** One search result: a 100 px thumbnail and the 600 px artwork. */
    class Hit(val thumb: String, val big: String)

    private fun fileFor(c: Context, path: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(c.filesDir, "covers"), "$hash.jpg")
    }

    /** The saved cover of [path], or null when none was picked (or the file no longer decodes). */
    fun saved(c: Context, path: String): ImageBitmap? =
        fileFor(c, path).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }

    fun save(c: Context, path: String, jpg: ByteArray) {
        val f = fileFor(c, path)
        f.parentFile?.mkdirs()
        f.writeBytes(jpg)
        forgetArt(path)
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
            return con.inputStream.use { it.readBytes() }
        } finally {
            con.disconnect()
        }
    }

    fun image(url: String): ImageBitmap? = runCatching {
        val b = fetch(url)
        BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap()
    }.getOrNull()

    /** iTunes Search API (no key). Empty on any failure. */
    fun search(term: String, entity: String): List<Hit> = runCatching {
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(term, "UTF-8")}&entity=$entity&limit=10"
        val res = JSONObject(String(fetch(url))).getJSONArray("results")
        (0 until res.length()).mapNotNull { i ->
            res.getJSONObject(i).optString("artworkUrl100").takeIf { it.isNotBlank() }
                ?.let { Hit(it, it.replace("100x100bb", "600x600bb")) }
        }
    }.getOrDefault(emptyList())
}

/** Grid of online album art for [track]; tapping one saves it as the track's cover. */
@Composable
internal fun CoverArtDialog(track: Track, style: PowerampStyle, onSaved: () -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val album = track.album?.takeIf { it.isNotBlank() }
    val term = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, album ?: track.displayName()).joinToString(" ")
    var hits by remember { mutableStateOf<List<PowerampCovers.Hit>?>(null) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(track.path) {
        hits = withContext(Dispatchers.IO) { PowerampCovers.search(term, if (album != null) "album" else "song") }
    }
    Dialog(onDismissRequest = { if (!saving) onClose() }) {
        Column(
            Modifier.fillMaxWidth(.92f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF161616)).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(tr("Album art", "Albüm kapağı"), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            val list = hits
            when {
                list == null || saving -> Box(Modifier.fillMaxWidth().height(160.dp), Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = style.accent, strokeWidth = 2.dp)
                }
                list.isEmpty() -> Box(Modifier.fillMaxWidth().height(120.dp), Alignment.Center) {
                    Text(tr("No results", "Sonuç yok"), color = style.textDim, fontSize = 14.sp)
                }
                else -> Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    list.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { h ->
                                CoverThumb(h.thumb, Modifier.weight(1f)) {
                                    saving = true
                                    scope.launch {
                                        val ok = withContext(Dispatchers.IO) {
                                            runCatching { PowerampCovers.save(ctx, track.path, PowerampCovers.fetch(h.big)) }.isSuccess
                                        }
                                        saving = false
                                        if (ok) { onSaved(); onClose() }
                                        else Toast.makeText(ctx, tr("Could not download", "İndirilemedi"), Toast.LENGTH_SHORT).show()
                                    }
                                }
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
