package com.umutk.pulse.extras.music.poweramp

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.AlertDialog
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.extras.music.Playlists
import com.umutk.pulse.extras.music.Track
import com.umutk.pulse.tr
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SheetBg = Color(0xFF222222)
private val SheetLine = Color.White.copy(alpha = .12f)
private val SheetSub = Color(0xFF9E9E9E)

/**
 * The song menu: a dark sheet over a blurred copy of the track's cover. Header (cover, title, artist or album, duration and
 * format), then Delete, Add to Playlist, Album Art, Info/Tags | Lyrics, Artist | Album, Folder | Genre. Every row acts on the
 * current track; the value rows list the library tracks that share that value and play from there.
 */
@Composable
internal fun TrackSheet(
    track: Track, art: ImageBitmap?, onDismiss: () -> Unit, onLyrics: (() -> Unit)?, onFindArt: (() -> Unit)?, onInfo: () -> Unit, onEditTags: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val library by MusicLibrary.tracks.collectAsState()
    // "delete", "playlist", or a value list: "artist", "album", "genre", "folder"
    var pane by remember { mutableStateOf<String?>(null) }
    val format = track.path.substringAfterLast('.', "").uppercase()
    val duration = track.durationSec.takeIf { it >= 0 }?.let { formatMs(it * 1000) }.orEmpty()
    val close = onDismiss

    Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth(.94f).clip(RoundedCornerShape(28.dp)).background(SheetBg)) {
            if (art != null) {
                Image(art, null, Modifier.matchParentSize().blur(40.dp).alpha(.45f), contentScale = ContentScale.Crop)
                Box(Modifier.matchParentSize().background(SheetBg.copy(alpha = .6f)))
            }
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                        if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Rounded.MusicNote, null, tint = SheetSub, modifier = Modifier.size(32.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(track.displayName(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(track.artistTag(), track.album?.takeIf { it.isNotBlank() }).joinToString(" · ").ifEmpty { track.folderName() },
                            color = SheetSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        val year = track.year.takeIf { it > 0 }?.toString().orEmpty()
                        Text(listOf(year, duration, format).filter { it.isNotEmpty() }.joinToString(" · "), color = SheetSub, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Line()
                SheetRow(Icons.Rounded.Delete, tr("Delete", "Sil")) { pane = "delete" }
                SheetRow(Icons.Rounded.Add, tr("Add to Playlist", "Çalma listesine ekle")) { pane = "playlist" }
                if (onFindArt != null) SheetRow(Icons.Rounded.Image, tr("Album Art", "Albüm kapağı")) { close(); onFindArt() }
                Line()
                Row(Modifier.fillMaxWidth()) {
                    SheetCell(Icons.Rounded.Info, tr("Info / Tags", "Bilgi / Etiket"), Modifier.weight(1f)) { close(); onInfo() }
                    SheetCell(Icons.Rounded.Subject, tr("Lyrics", "Şarkı sözleri"), Modifier.weight(1f), onLong = { pane = "forgetLyrics" }) { close(); onLyrics?.invoke() }
                }
                Line()
                Row(Modifier.fillMaxWidth()) {
                    SheetCell(Icons.Rounded.Mic, tr("Artist", "Sanatçı"), Modifier.weight(1f)) { pane = "artist" }
                    SheetCell(Icons.Rounded.Album, tr("Album", "Albüm"), Modifier.weight(1f)) { pane = "album" }
                }
                Row(Modifier.fillMaxWidth()) {
                    SheetCell(Icons.Rounded.Folder, tr("Folder", "Klasör"), Modifier.weight(1f)) { pane = "folder" }
                    SheetCell(Icons.Rounded.LibraryMusic, tr("Genre", "Tür"), Modifier.weight(1f)) { pane = "genre" }
                }
            }
        }
    }

    when (pane) {
        "forgetLyrics" -> com.umutk.pulse.WinAlert(
            onDismissRequest = { pane = null },
            title = { Text(tr("Delete these lyrics?", "Bu sözler silinsin mi?")) },
            text = { Text(tr("The downloaded and saved lyrics of this song (and the .lrc / .txt next to the file) are removed. They are not looked up again for a few days.", "Bu şarkının indirilen ve kaydedilen sözleri (ve dosyanın yanındaki .lrc / .txt) silinir. Birkaç gün yeniden aranmaz.")) },
            confirmButton = {
                TextButton(onClick = {
                    pane = null; close()
                    scope.launch {
                        withContext(Dispatchers.IO) { com.umutk.pulse.extras.lyrics.Lyrics.forget(ctx, track.path, track.title ?: track.path.substringAfterLast('/').substringBeforeLast('.'), track.artist.orEmpty()) }
                        Toast.makeText(ctx, tr("Lyrics deleted", "Sözler silindi"), Toast.LENGTH_SHORT).show()
                    }
                }) { Text(tr("Delete", "Sil")) }
            },
            dismissButton = { TextButton(onClick = { pane = null }) { Text(tr("Cancel", "Vazgeç")) } },
        )
        "delete" -> com.umutk.pulse.WinAlert(
            onDismissRequest = { pane = null },
            title = { Text(tr("Delete this track?", "Bu parça silinsin mi?")) },
            text = { Text(tr("The file is deleted from the phone. This cannot be undone.", "Dosya telefondan silinir. Bu geri alınamaz.")) },
            confirmButton = {
                TextButton(onClick = {
                    pane = null; close()
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { !track.path.contains("://") && File(track.path).delete() }
                        Toast.makeText(ctx, if (ok) tr("Deleted", "Silindi") else tr("Could not delete", "Silinemedi"), Toast.LENGTH_SHORT).show()
                        if (ok) MusicLibrary.refresh(ctx)
                    }
                }) { Text(tr("Delete", "Sil")) }
            },
            dismissButton = { TextButton(onClick = { pane = null }) { Text(tr("Cancel", "Vazgeç")) } },
        )
        "playlist" -> {
            val names = remember { Playlists.names(ctx) }
            com.umutk.pulse.WinAlert(
                onDismissRequest = { pane = null },
                title = { Text(tr("Add to Playlist", "Çalma listesine ekle")) },
                text = {
                    if (names.isEmpty()) Text(tr("No playlists yet. Make one in Playlists first.", "Henüz çalma listesi yok. Önce Çalma listeleri'nde bir tane oluştur."))
                    else Column {
                        names.forEach { name ->
                            TextButton(onClick = {
                                pane = null; close()
                                scope.launch(Dispatchers.IO) {
                                    Playlists.save(ctx, name, Playlists.load(ctx, name) + track)
                                    withContext(Dispatchers.Main) { Toast.makeText(ctx, tr("Added to $name", "$name listesine eklendi"), Toast.LENGTH_SHORT).show() }
                                }
                            }) { Text(name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { pane = null }) { Text(tr("Close", "Kapat")) } },
            )
        }
        "artist", "album", "genre", "folder" -> {
            val group = when (pane) {
                "artist" -> track.artistTag()?.let { v -> library.filter { it.artistTag() == v } }
                "album" -> track.album?.let { v -> library.filter { it.album == v } }
                "genre" -> track.genre?.let { v -> library.filter { it.genre == v } }
                else -> File(track.path).parent?.let { p -> library.filter { File(it.path).parent == p } }
            }.orEmpty()
            Dialog(onDismissRequest = { pane = null }) {
                Column(Modifier.fillMaxWidth(.94f).clip(RoundedCornerShape(28.dp)).background(SheetBg).padding(16.dp)) {
                    Text(
                        when (pane) { "artist" -> track.artistTag(); "album" -> track.album; "genre" -> track.genre; else -> track.folderName() }
                            ?.takeIf { it.isNotBlank() } ?: tr("Nothing to show", "Gösterilecek bir şey yok"),
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (group.isEmpty()) Text(tr("No other tracks here", "Burada başka parça yok"), color = SheetSub, fontSize = 14.sp)
                    else LazyColumn(Modifier.height(360.dp)) {
                        items(group.size) { i ->
                            val t = group[i]
                            Row(Modifier.fillMaxWidth().clickable {
                                pane = null; close()
                                MusicPlayerEngine.get(ctx).playQueue(group, i)
                            }.padding(vertical = 10.dp)) {
                                Text(t.displayName(), color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Line() = Box(Modifier.fillMaxWidth().height(1.dp).background(SheetLine))

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SheetCell(icon: ImageVector, label: String, modifier: Modifier, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(modifier.combinedClickable(onLongClick = onLong, onClick = onClick).padding(vertical = 13.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
