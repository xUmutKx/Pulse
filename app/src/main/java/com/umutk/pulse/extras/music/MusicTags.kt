package com.umutk.pulse.extras.music

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umutk.pulse.PButton
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import org.json.JSONObject

/**
 * Basic tag editing: title, artist and album. The new values are kept in the app (shown everywhere in the players)
 * and also offered to the media database. Audio files themselves are never rewritten.
 */
object MusicTags {
    private fun sp(c: Context) = c.getSharedPreferences("music_tags", Context.MODE_PRIVATE)

    /** Stores the edit of one track; [genre] and [year] stay as they were when not given. */
    fun set(c: Context, path: String, title: String, artist: String, album: String, genre: String? = null, year: Int? = null) {
        val all = runCatching { JSONObject(sp(c).getString("t", "{}")!!) }.getOrDefault(JSONObject())
        val o = all.optJSONObject(path) ?: JSONObject()
        o.put("ti", title).put("ar", artist).put("al", album)
        genre?.let { o.put("ge", it) }; year?.let { o.put("yr", it) }
        all.put(path, o)
        sp(c).edit().putString("t", all.toString()).apply()
        // best effort: the media database may refuse files owned by other apps
        runCatching {
            c.contentResolver.update(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply { put(MediaStore.Audio.Media.TITLE, title); put(MediaStore.Audio.Media.ARTIST, artist); put(MediaStore.Audio.Media.ALBUM, album) },
                "${MediaStore.Audio.Media.DATA}=?", arrayOf(path),
            )
        }
    }

    /** Adds tags found online: only the fields given are written, the others (and the user's own edits) stay. */
    @Synchronized
    fun merge(c: Context, path: String, artist: String? = null, album: String? = null, genre: String? = null, year: Int? = null) {
        val all = runCatching { JSONObject(sp(c).getString("t", "{}")!!) }.getOrDefault(JSONObject())
        val o = all.optJSONObject(path) ?: JSONObject()
        artist?.let { o.put("ar", it) }; album?.let { o.put("al", it) }; genre?.let { o.put("ge", it) }; year?.takeIf { it > 0 }?.let { o.put("yr", it) }
        all.put(path, o)
        sp(c).edit().putString("t", all.toString()).apply()
    }

    fun apply(c: Context, list: List<Track>): List<Track> {
        val all = runCatching { JSONObject(sp(c).getString("t", "{}")!!) }.getOrDefault(JSONObject())
        if (all.length() == 0) return list
        return list.map { t ->
            val o = all.optJSONObject(t.path) ?: return@map t
            t.copy(title = o.optString("ti").ifBlank { t.title }, artist = o.optString("ar").ifBlank { t.artist }, album = o.optString("al").ifBlank { t.album },
                genre = o.optString("ge").ifBlank { t.genre }, year = o.optInt("yr").takeIf { it > 0 } ?: t.year)
        }
    }
}

@Composable
fun TagEditDialog(track: Track, onDone: (Track) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ti by remember { mutableStateOf(track.title.orEmpty()) }
    var ar by remember { mutableStateOf(track.artist.orEmpty()) }
    var al by remember { mutableStateOf(track.album.orEmpty()) }
    var ge by remember { mutableStateOf(track.genre.orEmpty()) }
    var yr by remember { mutableStateOf(track.year.takeIf { it > 0 }?.toString().orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    WinAlert(
        onDismissRequest = onClose,
        title = { Text(tr("Tags", "Etiketler"), color = T.text) },
        text = {
            Column {
                OutlinedTextField(ti, { ti = it }, singleLine = true, label = { Text(tr("Title", "Başlık")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ar, { ar = it }, singleLine = true, label = { Text(tr("Artist", "Sanatçı")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(al, { al = it }, singleLine = true, label = { Text(tr("Album", "Albüm")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(ge, { ge = it }, singleLine = true, label = { Text(tr("Genre", "Tür")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(yr, { yr = it.filter { c -> c.isDigit() }.take(4) }, singleLine = true, label = { Text(tr("Year", "Yıl")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                // looks the song up online (iTunes catalogue, no account) and fills what it finds into the fields; nothing is saved until OK
                PButton(if (busy) "…" else tr("Find online", "İnternetten bul"), Modifier.padding(top = 8.dp)) {
                    if (busy) return@PButton
                    busy = true
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { OnlineTags.lookup(track.copy(title = ti.ifBlank { null }, artist = ar.ifBlank { null })) }
                        busy = false
                        if (f == null) Toast.makeText(ctx, tr("Nothing found", "Bulunamadı"), Toast.LENGTH_SHORT).show()
                        else {
                            f.artist?.let { ar = it }; f.album?.let { al = it }; f.genre?.let { ge = it }; if (f.year > 0) yr = f.year.toString()
                        }
                    }
                }
            }
        },
        confirmButton = {
            PButton("OK") {
                MusicTags.set(ctx, track.path, ti, ar, al, ge, yr.toIntOrNull() ?: 0)
                onDone(track.copy(title = ti.ifBlank { null }, artist = ar.ifBlank { null }, album = al.ifBlank { null }, genre = ge.ifBlank { null }, year = yr.toIntOrNull() ?: 0))
                onClose()
            }
        },
    )
}
