package com.umutk.pulse.extras.music

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
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

    fun set(c: Context, path: String, title: String, artist: String, album: String) {
        val all = runCatching { JSONObject(sp(c).getString("t", "{}")!!) }.getOrDefault(JSONObject())
        all.put(path, JSONObject().put("ti", title).put("ar", artist).put("al", album))
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

    fun apply(c: Context, list: List<Track>): List<Track> {
        val all = runCatching { JSONObject(sp(c).getString("t", "{}")!!) }.getOrDefault(JSONObject())
        if (all.length() == 0) return list
        return list.map { t ->
            val o = all.optJSONObject(t.path) ?: return@map t
            t.copy(title = o.optString("ti").ifBlank { t.title }, artist = o.optString("ar").ifBlank { t.artist }, album = o.optString("al").ifBlank { t.album })
        }
    }
}

@Composable
fun TagEditDialog(track: Track, onDone: (Track) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var ti by remember { mutableStateOf(track.title.orEmpty()) }
    var ar by remember { mutableStateOf(track.artist.orEmpty()) }
    var al by remember { mutableStateOf(track.album.orEmpty()) }
    WinAlert(
        onDismissRequest = onClose,
        title = { Text(tr("Tags", "Etiketler"), color = T.text) },
        text = {
            Column {
                OutlinedTextField(ti, { ti = it }, singleLine = true, label = { Text(tr("Title", "Başlık")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(ar, { ar = it }, singleLine = true, label = { Text(tr("Artist", "Sanatçı")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                OutlinedTextField(al, { al = it }, singleLine = true, label = { Text(tr("Album", "Albüm")) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
        },
        confirmButton = { PButton("OK") { MusicTags.set(ctx, track.path, ti, ar, al); onDone(track.copy(title = ti.ifBlank { null }, artist = ar.ifBlank { null }, album = al.ifBlank { null })); onClose() } },
    )
}
