package com.umutk.pulse.extras.music

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.extras.music.poweramp.PowerampCovers
import com.umutk.pulse.extras.music.poweramp.displayName
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Search and apply missing covers": every song without a cover is looked up at once (the same careful match as the background lookup: no cover is better than a wrong one);
 * the ones that could not be matched by themselves are listed, and each opens a search of its own where the right cover is picked by hand.
 */
@Composable
fun CoverRepairSection() {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf(-1) }
    var left by remember { mutableStateOf(emptyList<Track>()) }
    var pick by remember { mutableStateOf<Track?>(null) }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PButton(if (busy) progress.ifBlank { "…" } else tr("Search and apply missing covers", "Eksik kapakları ara ve uygula"), compact = true) {
            if (busy) return@PButton
            scope.launch {
                busy = true; applied = 0
                val todo = withContext(Dispatchers.IO) { MusicLibrary.tracks.value.filter { !it.path.startsWith("http") && PowerampCovers.saved(c, it.path) == null && embeddedArt(c, it.path) == null } }
                val miss = ArrayList<Track>()
                todo.forEachIndexed { i, t ->
                    progress = "${i + 1} / ${todo.size}"
                    val got = withContext(Dispatchers.IO) { CoverFetch.find(c, t, force = true) }
                    if (got != null) { applied++; forgetArt(t.path) } else miss += t
                    delay(1200)
                }
                left = miss; busy = false; progress = ""
            }
        }
        if (applied >= 0 && !busy) Text(tr("$applied covers applied, ${left.size} could not be matched by themselves", "$applied kapak uygulandı, ${left.size} tanesi kendiliğinden eşleşmedi"), color = T.sub, fontSize = 12.sp)
        left.take(60).forEach { t ->
            Row(Modifier.fillMaxWidth().clickable { pick = t }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.displayName(), color = T.text, fontSize = 14.sp, maxLines = 1)
                    Text(listOfNotNull(t.artist?.takeIf { it != "<unknown>" }, t.album).joinToString(" · "), color = T.sub, fontSize = 11.sp, maxLines = 1)
                }
                Text(tr("Choose…", "Seç…"), color = T.accent, fontSize = 13.sp)
            }
        }
        if (left.size > 60) Text(tr("and ${left.size - 60} more", "ve ${left.size - 60} tane daha"), color = T.sub, fontSize = 11.sp)
    }
    pick?.let { t -> CoverPicker(t, onDone = { done -> pick = null; if (done) left = left - t }) }
}

/** One song, a search box (filled in from its tags), the covers the service offers, and a tap to apply one. */
@Composable
private fun CoverPicker(t: Track, onDone: (Boolean) -> Unit) {
    val c = LocalContext.current
    val w = LocalWindowLook.current
    val scope = rememberCoroutineScope()
    var q by remember(t) { mutableStateOf(listOfNotNull(t.artist?.takeIf { it != "<unknown>" }, t.displayName()).joinToString(" ")) }
    var hits by remember(t) { mutableStateOf(emptyList<PowerampCovers.Hit>()) }
    var busy by remember(t) { mutableStateOf(false) }
    var note by remember(t) { mutableStateOf("") }
    fun search() { scope.launch { busy = true; note = ""
        hits = withContext(Dispatchers.IO) { (PowerampCovers.search(q, "song") + PowerampCovers.search(q, "album")).distinctBy { it.big } }
        if (hits.isEmpty()) note = tr("Nothing found", "Bir şey bulunamadı"); busy = false } }
    androidx.compose.runtime.LaunchedEffect(t) { search() }
    WinAlert(onDismissRequest = { onDone(false) },
        title = { Text(t.displayName(), color = w.text, maxLines = 1) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PField(q, { q = it }, tr("Artist and song", "Sanatçı ve şarkı"), Modifier.fillMaxWidth(), onDone = { search() })
                PButton(if (busy) "…" else tr("Search", "Ara"), compact = true) { search() }
                if (note.isNotEmpty()) Text(note, color = w.sub, fontSize = 12.sp)
                hits.forEach { h ->
                    val thumb by produceState<ImageBitmap?>(null, h.thumb) { value = withContext(Dispatchers.IO) { PowerampCovers.image(h.thumb) } }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { PowerampCovers.save(c, t.path, PowerampCovers.fetch(h.big)); true }.getOrDefault(false) }
                            if (ok) { forgetArt(t.path); onDone(true) } else note = tr("Could not load that cover", "Bu kapak yüklenemedi")
                        }
                    }.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(0.dp))
                        thumb?.let { Image(it, null, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))) } ?: Spacer(Modifier.size(56.dp).background(w.line.copy(alpha = .4f)))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(h.name, color = w.text, fontSize = 14.sp, maxLines = 1)
                            Text(h.artist, color = w.sub, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat")) { onDone(false) } })
}
