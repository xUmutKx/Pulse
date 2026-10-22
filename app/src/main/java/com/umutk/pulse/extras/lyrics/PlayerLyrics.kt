package com.umutk.pulse.extras.lyrics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.PLoader
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The lyrics of the player, laid over the cover square. One rule keeps it from ever staying invisible: it is a plain AnimatedVisibility
 * (the slide is the library's own), with no size measuring or hand-driven offset to get stuck; the content is the lines in white on a dark sheet,
 * or the searching / not-found note with the fields to spell the song another way. Lines found on the phone come first, the net after.
 */
@Composable
fun PlayerLyrics(
    visible: Boolean, title: String, artist: String, album: String?, durationMs: Long, localPath: String?, positionMs: Long,
    onDismiss: () -> Unit, onSeek: ((Long) -> Unit)?, modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible, modifier,
        enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(300, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(220)),
    ) {
        val ctx = LocalContext.current
        var attempt by remember { mutableIntStateOf(0) }
        var qTitle by remember(title) { mutableStateOf(title) }
        var qArtist by remember(artist) { mutableStateOf(artist) }
        // (still looking, what was found)
        val state by produceState(true to (null as LyricsResult?), title, artist, localPath, attempt) {
            value = true to null
            val r = runCatching {
                if (attempt == 0) Lyrics.forTrack(ctx, title, artist, album, durationMs, localPath) ?: Lyrics.forTrack(ctx, title.substringAfter(" - "), "", album, durationMs, localPath)
                else Lyrics.forTrack(ctx, qTitle, qArtist, album, durationMs, null)
            }.getOrNull()
            value = false to r
            if (r != null && localPath != null && r.source in setOf("lrclib", "ovh", "cache")) withContext(Dispatchers.IO) {
                if (LiveLyrics.saveLrc(ctx)) Lyrics.saveSidecar(localPath, r)
                if (LiveLyrics.embed(ctx)) Lyrics.embedMp3(localPath, r)
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .94f)).pointerInput(Unit) { detectTapGestures { } }) {
            val (busy, res) = state
            when {
                res != null -> LyricsView(res, positionMs, onSeek, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 48.dp), onDark = true)
                busy -> Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
                    PLoader()
                    Text(tr("Searching for lyrics…", "Sözler aranıyor…"), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
                }
                else -> Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                    PIcon("book", Color.White.copy(alpha = .6f), 40.dp)
                    Text(tr("No lyrics found", "Söz bulunamadı"), color = Color.White.copy(alpha = .7f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    Lyrics.lastError?.let { Text(tr("Network: ", "Ağ: ") + it, color = Color.White.copy(alpha = .5f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                    @Composable fun field(v: String, hint: String, on: (String) -> Unit) = BasicTextField(
                        v, on, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).background(Color.White.copy(alpha = .1f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner -> if (v.isEmpty()) Text(hint, color = Color.White.copy(alpha = .4f), fontSize = 15.sp); inner() })
                    field(qArtist, tr("Artist", "Sanatçı")) { qArtist = it }
                    field(qTitle, tr("Song", "Şarkı")) { qTitle = it }
                    Text(tr("Search again", "Tekrar ara"), color = Color.Black, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)
                        .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).clickable { attempt++ }.padding(horizontal = 20.dp, vertical = 9.dp))
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) { PIcon("close", Color.White, 22.dp) }
        }
    }
}
