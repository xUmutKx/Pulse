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
    var pick by remember { mutableStateOf<Track?>(null) }
    // the work runs in CoverRepairService; what is shown here is its state, so the page can be left and opened again
    val busy = CoverRepairJob.busy
    val left = CoverRepairJob.left
    // when the run is over and some songs found nothing, the search opens for the first of them, and for the next one after each pick
    androidx.compose.runtime.LaunchedEffect(CoverRepairJob.finished) { if (CoverRepairJob.finished && !CoverRepairJob.busy && CoverRepairJob.left.isNotEmpty() && pick == null && !CoverRepairJob.embedding && !CoverRepairJob.offered) { CoverRepairJob.offered = true; pick = CoverRepairJob.left.first() } }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PButton(if (busy) tr("Searching in the background…", "Arka planda aranıyor…") else tr("Search and apply missing covers", "Eksik kapakları ara ve uygula"), compact = true) {
            if (busy) return@PButton
            CoverRepairJob.start(c)
        }
        PButton(tr("Write the saved covers into the songs (mp3)", "Kayıtlı kapakları şarkı dosyalarına yaz (mp3)"), compact = true) { if (!busy) CoverRepairJob.start(c, embed = true) }
        Text(tr("The first song is tried on a copy and every song is copied aside and checked; a bad result is undone.", "İlk şarkı önce kopyada denenir, her şarkı kenara kopyalanıp kontrol edilir; kötü sonuç geri alınır."), color = T.sub, fontSize = 11.sp)
        if (busy) {
            val p = if (CoverRepairJob.total > 0) CoverRepairJob.done.toFloat() / CoverRepairJob.total else 0f
            androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = com.umutk.pulse.Look.accent)
            Text("${CoverRepairJob.done} / ${CoverRepairJob.total} · " + tr("${CoverRepairJob.applied} found", "${CoverRepairJob.applied} bulundu"), color = T.sub, fontSize = 12.sp)
        }
        if (CoverRepairJob.embedding && CoverRepairJob.finished && !busy) Text(CoverRepairJob.note, color = T.sub, fontSize = 12.sp)
        else if (CoverRepairJob.finished && !busy) Text(tr("${CoverRepairJob.applied} covers applied, ${left.size} could not be matched by themselves", "${CoverRepairJob.applied} kapak uygulandı, ${left.size} tanesi kendiliğinden eşleşmedi"), color = T.sub, fontSize = 12.sp)
        // what the run found, to look through: the covers that were put on songs
        if (CoverRepairJob.found.isNotEmpty() && !busy) {
            var showFound by remember { mutableStateOf(false) }
            Text((if (showFound) "▾ " else "▸ ") + tr("Found (${CoverRepairJob.found.size})", "Bulunanlar (${CoverRepairJob.found.size})"), color = T.accent, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clickable { showFound = !showFound }.padding(vertical = 6.dp))
            if (showFound) CoverRepairJob.found.take(80).forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { pick = t }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.displayName(), color = T.text, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(tr("Change…", "Değiştir…"), color = T.accent, fontSize = 12.sp)
                }
            }
        }
        if (left.isNotEmpty() && !busy) Text(tr("Not found (${left.size})", "Bulunamayanlar (${left.size})"), color = T.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
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
    pick?.let { t -> CoverPicker(t, onDone = { done -> pick = null; if (done) { CoverRepairJob.left = CoverRepairJob.left - t; pick = CoverRepairJob.left.firstOrNull() } }) }
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
