package com.umutk.pulse

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One thing Pulse keeps on the phone: where it is, its size, what it is for. */
private class StoreRow(val name: String, val path: String, val bytes: Long, val what: String, val file: File)

/** Everything Pulse keeps in its data (files) and its cache, largest first. */
private fun storeRows(ctx: Context): List<StoreRow> {
    val base = ctx.applicationInfo.dataDir
    val out = ArrayList<StoreRow>()
    listOf("files" to ctx.filesDir, "cache" to ctx.cacheDir).forEach { (group, dir) ->
        dir.listFiles()?.forEach { f ->
            val bytes = runCatching { f.walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
            out += StoreRow(f.name, "$base/$group/${f.name}", bytes, whatIs(group, f.name), f)
        }
    }
    return out.sortedByDescending { it.bytes }
}

private fun whatIs(group: String, name: String): String = when {
    name.startsWith("kbd_") -> tr("Word lists of the keyboard", "Klavye kelime listeleri")
    name == "lyrics_index.gz" -> tr("Index of the lyrics beside your songs, for search", "Şarkıların yanındaki sözlerin dizini (arama için)")
    name == "lyrics-saved" -> tr("Lyrics that could not be written beside a song", "Şarkının yanına yazılamayan sözler")
    name == "lyrics-cache" || name == "lyrics-hits" || name == "lyrics" || name == "lyrics-search" ->
        tr("Old lyrics copies, now kept beside the songs", "Eski söz kopyaları, artık şarkıların yanında")
    name == "covers" -> tr("Book covers", "Kitap kapakları")
    name == "video_net" -> tr("Videos streamed from the net, for a quick replay", "Ağdan oynatılan videolar (hızlı tekrar için)")
    name == "thumbs" -> tr("Small pictures of files and music", "Dosya ve müzik küçük resimleri")
    name == "mediatools" -> tr("Temporary files of the media tools", "Medya araçlarının geçici dosyaları")
    name == "installer" || name == "open" -> tr("APK files for installing", "Kurulum için APK dosyaları")
    name == "pulse_bench.bin" -> tr("Speed test file", "Hız testi dosyası")
    group == "cache" -> tr("Temporary files of Pulse, safe to delete", "Pulse'ın geçici dosyaları, silmek güvenli")
    else -> tr("Data Pulse keeps for its features", "Özellikleri için Pulse'ın tuttuğu veri")
}

private fun sizeText(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> "%.1f MB".format(b / (1L shl 20).toDouble())
    b >= 1L shl 10 -> "%.0f KB".format(b / (1L shl 10).toDouble())
    else -> "$b B"
}

/** Settings > About > Storage: what Pulse keeps on the phone, where, what for, and a button to delete each one. */
@Composable
fun StorageCard() {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val rows by produceState<List<StoreRow>?>(null, reload) { value = withContext(Dispatchers.IO) { storeRows(ctx) } }
    var askDelete by remember { mutableStateOf<StoreRow?>(null) }
    PCard {
        Text(tr("Storage", "Depolama"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            tr("Where Pulse keeps its data and cache, and what each part is for. The path is inside the app's own folder.",
                "Pulse'ın veri ve önbelleğinin yeri ve her parçanın ne için olduğu. Yol uygulamanın kendi klasörü içindedir."),
            color = T.sub, fontSize = 12.sp,
        )
        val list = rows
        if (list == null) {
            Text(tr("Counting...", "Sayılıyor..."), color = T.sub, fontSize = 13.sp)
            return@PCard
        }
        if (list.isEmpty()) Text(tr("Nothing kept yet", "Henüz bir şey tutulmuyor"), color = T.sub, fontSize = 13.sp)
        list.forEach { row ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(sizeText(row.bytes), color = T.sub, fontSize = 13.sp)
            }
            Text(row.path, color = T.sub, fontSize = 11.sp)
            Text(row.what, color = T.text, fontSize = 12.sp)
            PButton(tr("Delete", "Sil"), Modifier.fillMaxWidth(), danger = true, compact = true) { askDelete = row }
        }
        Spacer(Modifier.height(6.dp))
        Text(tr("Total: ${sizeText(list.sumOf { it.bytes })}", "Toplam: ${sizeText(list.sumOf { it.bytes })}"), color = T.sub, fontSize = 13.sp)
    }
    askDelete?.let { row ->
        PDialog(
            title = tr("Delete ${row.name}?", "${row.name} silinsin mi?"),
            text = tr("${row.what}. It is removed from the phone; Pulse makes it again when it needs it.",
                "${row.what}. Telefondan silinir; Pulse gerektiğinde yeniden oluşturur."),
            confirm = tr("Delete", "Sil"),
            onConfirm = {
                runCatching { if (row.file.isDirectory) row.file.deleteRecursively() else row.file.delete() }
                askDelete = null
                reload++
            },
            onDismiss = { askDelete = null },
        )
    }
}
