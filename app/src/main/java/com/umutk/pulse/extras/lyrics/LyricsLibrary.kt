package com.umutk.pulse.extras.lyrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PProgress
import com.umutk.pulse.T
import com.umutk.pulse.extras.ai.Transcriber
import com.umutk.pulse.extras.music.MusicLibrary
import com.umutk.pulse.extras.music.MusicPlayerEngine
import com.umutk.pulse.launchExtra
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The songs and their lyrics: the songs without lyrics first, the ones with lyrics below them, dimmed. "Make lyrics" makes the missing ones in the
 * background (it goes on when the page is left); a tap plays a song, and its lyrics show on the player's volume page.
 */
@Composable
fun LyricsLibrary() {
    val ctx = LocalContext.current
    val engine = MusicPlayerEngine.get(ctx)
    val tracks by MusicLibrary.tracks.collectAsState()
    val busy = LyricsBatchJob.busy
    val done = LyricsBatchJob.done
    val total = LyricsBatchJob.total
    // the status of every song, read off the main thread; it is read again when the batch moves on
    val status by produceState<Map<String, Boolean>?>(null, tracks, done, busy) {
        value = withContext(Dispatchers.IO) { tracks.associate { it.path to LyricsStatus.has(ctx, it.path, it.artist.orEmpty(), it.title.orEmpty()) } }
    }
    val st = status
    val sorted = if (st == null) tracks else tracks.sortedBy { if (st[it.path] == true) 1 else 0 }
    val missing = if (st == null) 0 else tracks.count { st[it.path] == false }
    val firstHave = if (st == null) -1 else sorted.indexOfFirst { st[it.path] == true }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Song lyrics", "Şarkı sözleri"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(tr("$missing songs have no lyrics", "$missing şarkının sözü yok"), color = T.sub, fontSize = 13.sp)
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$done / $total · ${LyricsBatchJob.now}", color = T.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.size(36.dp).clickable { LyricsBatchJob.cancel(ctx) }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 18.dp) }
            }
            PProgress(if (total > 0) done.toFloat() / total else 0f, Modifier.fillMaxWidth())
            Text(tr("It goes on in the background, also when the app is closed. Long songs take a few minutes each.",
                "Arka planda sürer, uygulama kapalıyken de. Uzun şarkılar her biri birkaç dakika sürer."), color = T.sub, fontSize = 12.sp)
        } else if (missing > 0) {
            PButton(tr("Make lyrics for all", "Hepsi için söz yap"), Modifier.fillMaxWidth()) {
                if (!LyricsBatchJob.start(ctx, tracks.filter { st?.get(it.path) == false }) && !Transcriber.ready(ctx)) launchExtra(ctx, "com.umutk.pulse.ExtraToolActivity", "ai")
            }
            if (!Transcriber.ready(ctx)) Text(tr("First get a speech model in Pulse AI.", "Önce Pulse AI'da bir ses modeli indir."), color = T.sub, fontSize = 12.sp)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            itemsIndexed(sorted) { i, t ->
                if (i == firstHave && firstHave > 0) Text(tr("With lyrics", "Sözü olanlar"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                val has = st?.get(t.path) == true
                val name = t.title ?: File(t.path).nameWithoutExtension
                Row(Modifier.fillMaxWidth().alpha(if (has) .4f else 1f).clickable { engine.playQueue(sorted, i) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = T.text, fontSize = 15.sp, maxLines = 1)
                        Text(t.artist.orEmpty(), color = T.sub, fontSize = 12.sp, maxLines = 1)
                    }
                    if (has) PIcon("check", T.sub, 16.dp)
                }
            }
        }
    }
}
