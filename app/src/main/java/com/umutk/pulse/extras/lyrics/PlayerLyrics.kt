package com.umutk.pulse.extras.lyrics

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.gestures.detectTapGestures as tapGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch

/**
 * The lyrics of the player, laid over the cover square. One rule keeps it from ever staying invisible: it is a plain AnimatedVisibility
 * (the slide is the library's own), with no size measuring or hand-driven offset to get stuck; the content is the lines in white on a dark sheet,
 * or the searching / not-found note with the fields to spell the song another way. Lines found on the phone come first, the net after.
 */
@Composable
fun PlayerLyrics(
    visible: Boolean, title: String, artist: String, album: String?, durationMs: Long, localPath: String?, positionMs: Long,
    onDismiss: () -> Unit, onSeek: ((Long) -> Unit)?, modifier: Modifier = Modifier,
    // the whole-screen form: the cover, title and artist across the top, the lyrics under them
    full: Boolean = false, art: androidx.compose.ui.graphics.ImageBitmap? = null,
) {
    AnimatedVisibility(
        visible, modifier,
        enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(220)),
        exit = slideOutVertically(tween(300, easing = FastOutLinearInEasing)) { it } + fadeOut(tween(220)),
    ) {
        val ctx = LocalContext.current
        var attempt by remember { mutableIntStateOf(0) }
        // a find the person chose: the lyrics are looked up again, now from beside the song
        var pickRev by remember { mutableIntStateOf(0) }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        var qTitle by remember(title) { mutableStateOf(title) }
        var qArtist by remember(artist) { mutableStateOf(artist) }
        // (still looking, what was found)
        val state by produceState(true to (null as LyricsResult?), title, artist, localPath, attempt, pickRev) {
            value = true to null
            val r = runCatching {
                if (attempt == 0 || pickRev > 0) Lyrics.forTrack(ctx, title, artist, album, durationMs, localPath) ?: Lyrics.forTrack(ctx, title.substringAfter(" - "), "", album, durationMs, localPath)
                else Lyrics.forTrack(ctx, qTitle, qArtist, album, durationMs, null)
            }.getOrNull()
            value = false to r
            if (r != null && localPath != null && r.source in setOf("lrclib", "ovh", "cache")) withContext(Dispatchers.IO) {
                Lyrics.keep(localPath, r)
            }
        }
        // nothing found (or the first find is not taken on its own): the finds are listed, to try against the song and choose from
        val noneFound = !state.first && state.second == null
        val cands by produceState(emptyList<LyricsRecord>(), noneFound, qTitle, qArtist, attempt) {
            value = if (noneFound) runCatching { Lyrics.candidates(ctx, qTitle, qArtist, album, durationMs) }.getOrDefault(emptyList()) else emptyList()
        }
        var preview by remember(title, artist) { mutableStateOf<LyricsRecord?>(null) }
        // sharing the lyrics card is an ad-gated feature (play build): the ad opens it for 30 minutes
        val shareGate = com.umutk.pulse.ads.rememberAdGate(tr("Share lyrics", "Şarkı sözünü paylaş"), tr("Watch a short ad to share the lyrics.", "Şarkı sözünü paylaşmak için kısa bir reklam izle."), key = "lyrics_share", minutes = 30)
        // 0 reading, 1 picking lines to share, 2 the card to share
        var mode by remember(title, artist) { mutableIntStateOf(0) }
        // lyrics the person edited or made with Pulse AI: shown at once, before the song's file is read again
        var edited by remember(title, artist) { mutableStateOf<LyricsResult?>(null) }
        var aiRun by remember(title, artist) { mutableStateOf(false) }
        val aiOk = localPath != null && com.umutk.pulse.extras.ai.aiSupported && com.umutk.pulse.extras.music.MusicSettings.aiLyrics(ctx)
        var picked by remember(title, artist) { mutableStateOf(emptySet<Int>()) }
        var style by remember { mutableIntStateOf(0) }
        var pull by remember { mutableFloatStateOf(0f) }
        val only = remember { LyricsShare.consecutive(ctx) }
        // a pull down that the lines did not take (they are at their top) closes the lyrics
        val closer = remember(onDismiss) {
            object : NestedScrollConnection {
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source == NestedScrollSource.UserInput) pull = if (consumed.y != 0f || available.y < 0f) 0f else pull + available.y
                    return Offset.Zero
                }
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity { if (pull > 180f) onDismiss(); pull = 0f; return Velocity.Zero }
            }
        }
        fun toggle(i: Int) {
            val cur = picked
            if (i in cur) {
                picked = if (!only || i == cur.min() || i == cur.max()) cur - i else setOf(i)
            } else if (only && cur.isNotEmpty() && i != cur.min() - 1 && i != cur.max() + 1) picked = setOf(i)
            else if (cur.size >= LyricsShare.MAX_LINES) android.widget.Toast.makeText(ctx, tr("At most ${LyricsShare.MAX_LINES} lines", "En fazla ${LyricsShare.MAX_LINES} satır"), android.widget.Toast.LENGTH_SHORT).show()
            else picked = cur + i
        }
        Box(Modifier.fillMaxSize().background(Color.Black).nestedScroll(closer).pointerInput(Unit) { tapGestures { } }) {
            val (busy, res0) = state
            val res = edited ?: res0
            when {
                res != null && mode == 3 -> LyricsEditor(res, positionMs, { r -> edited = r; scope.launch(Dispatchers.IO) { Lyrics.saveEdited(localPath, r) }; mode = 0 }, { mode = 0 }, Modifier.fillMaxSize().padding(top = 36.dp))
                res != null && mode == 1 -> LyricsSelect(res, picked, ::toggle, { mode = 2 }, Modifier.fillMaxSize().padding(top = 36.dp))
                res != null && mode == 2 -> LyricsSharePage(title, artist, art, picked.sorted().map { res.lines[it].text }, style, { style = it }, { bmp -> shareGate { LyricsShare.share(ctx, bmp) } }, Modifier.fillMaxSize().padding(top = 40.dp))
                res != null && full -> Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = .12f))) {
                            art?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                            // the share button sits in the cover's top-left corner
                            // the share button, and beside it the AI button: it listens to the song and writes its lyrics with their times
                            Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                Box(Modifier.size(26.dp).background(Color.Black.copy(alpha = .55f), androidx.compose.foundation.shape.RoundedCornerShape(bottomEnd = 10.dp)).clickable { mode = 1; picked = emptySet() }, contentAlignment = Alignment.Center) { PIcon("share", Color.White, 15.dp) }
                                if (aiOk) Box(Modifier.size(26.dp).background(Color.Black.copy(alpha = .55f), androidx.compose.foundation.shape.RoundedCornerShape(bottomEnd = 10.dp)).clickable {
                                    if (com.umutk.pulse.extras.ai.Transcriber.ready(ctx)) aiRun = true else com.umutk.pulse.launchExtra(ctx, "com.umutk.pulse.ExtraToolActivity", "ai")
                                }, contentAlignment = Alignment.Center) { PIcon("star", Color.White, 15.dp) }
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp, end = 40.dp)) {
                            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(artist, color = Color.White.copy(alpha = .7f), fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    LyricsView(res, positionMs, onSeek, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), onDark = true)
                }
                res != null -> LyricsView(res, positionMs, onSeek, Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 48.dp), onDark = true)
                // a find tried against the song that plays: its lines follow the position; "Use these" keeps it beside the song
                preview != null && res == null -> Column(Modifier.fillMaxSize()) {
                    val rec = preview!!
                    Text("${rec.artistName} - ${rec.trackName}", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(start = 16.dp, end = 56.dp, top = 12.dp))
                    Lyrics.toResult(rec, "lrclib")?.let { pr -> LyricsView(pr, positionMs, onSeek, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), onDark = true) }
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("Use these", "Bunu kullan"), color = Color.Black, fontSize = 14.sp, modifier = Modifier.background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .clickable { scope.launch { Lyrics.choose(ctx, rec, localPath); preview = null; pickRev++ } }.padding(horizontal = 20.dp, vertical = 9.dp))
                        Text(tr("Back", "Geri"), color = Color.White, fontSize = 14.sp, modifier = Modifier.background(Color.White.copy(alpha = .14f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .clickable { preview = null }.padding(horizontal = 20.dp, vertical = 9.dp))
                    }
                }
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
                    if (aiOk) Row(Modifier.padding(top = 10.dp).background(Color.White.copy(alpha = .14f), androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .clickable { if (com.umutk.pulse.extras.ai.Transcriber.ready(ctx)) aiRun = true else com.umutk.pulse.launchExtra(ctx, "com.umutk.pulse.ExtraToolActivity", "ai") }
                        .padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("star", Color.White, 16.dp)
                        Text(tr("Make timed lyrics", "Zamanlı söz oluştur"), color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (cands.isNotEmpty()) {
                        Text(tr("Found", "Bulunanlar"), color = Color.White.copy(alpha = .6f), fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                        Column(Modifier.heightIn(max = 220.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                            cands.forEach { c ->
                                Column(Modifier.fillMaxWidth().clickable { preview = c }.padding(vertical = 7.dp)) {
                                    Text("${c.artistName} - ${c.trackName}", color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(
                                        when { c.syncedLyrics?.contains('<') == true -> tr("Word by word", "Kelime kelime"); c.syncedLyrics != null -> tr("Synced", "Senkron"); else -> tr("Plain text", "Düz metin") },
                                        color = Color.White.copy(alpha = .55f), fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // the AI lyrics run here, over the lyrics (the dialog shows the progress and writes the lines when it is done)
            if (aiRun && localPath != null) com.umutk.pulse.extras.ai.AiLyricsDialog(localPath, title, artist) { lrc ->
                aiRun = false
                if (lrc != null) Lyrics.fromText(lrc, "ai")?.let { r -> edited = r; scope.launch(Dispatchers.IO) { Lyrics.saveEdited(localPath, r) } }
            }
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                // the share button sits right here, no menu: Share opens the line picker; the edit button opens the editor
                if (res != null && mode == 0) IconButton(onClick = { mode = 1; picked = emptySet() }) { PIcon("share", Color.White, 22.dp) }
                if (res != null && mode == 0) IconButton(onClick = { mode = 3 }) { PIcon("edit", Color.White, 22.dp) }
                IconButton(onClick = { if (mode == 0) onDismiss() else mode = if (mode == 3) 0 else mode - 1 }) { PIcon(if (mode == 0) "close" else "back", Color.White, 22.dp) }
            }
        }
    }
}
