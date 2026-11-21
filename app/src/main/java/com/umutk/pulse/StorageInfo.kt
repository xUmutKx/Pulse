package com.umutk.pulse

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.asImageBitmap
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
import androidx.compose.ui.graphics.graphicsLayer
import com.umutk.pulse.PIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** One thing Pulse keeps on the phone: where it is, its size, what it is for. */
private class StoreRow(val name: String, val path: String, val bytes: Long, val what: String, val file: File, val count: Int = 1, val parts: List<String> = emptyList(), val entries: List<Pair<File, Long>> = emptyList(), val members: List<File>? = null)

/** Everything Pulse keeps in its data (files) and its cache, largest first. */
private fun storeRows(ctx: Context): List<StoreRow> {
    val base = ctx.applicationInfo.dataDir
    val out = ArrayList<StoreRow>()
    listOf("files" to ctx.filesDir, "cache" to ctx.cacheDir).forEach { (group, dir) ->
        dir.listFiles()?.forEach { f ->
            val files = runCatching { f.walkTopDown().filter { it.isFile }.map { it.relativeTo(f).path.replace('\\', '/') to it.length() }.toList() }.getOrDefault(emptyList())
            val bytes = files.sumOf { it.second }
            // what the folder holds, by kind: how many files of each and how much they take (the biggest kinds first)
            val parts = if (f.isDirectory) files.groupBy { kindOf(f.name, it.first) }.map { (k, l) -> Triple(k, l.size, l.sumOf { it.second }) }
                .sortedByDescending { it.third }.take(6).map { (k, n, b) -> "$k  ·  $n  ·  ${sizeText(b)}" } else emptyList()
            val abs = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.toList() else listOf(f)
            val entries = abs.map { it to it.length() }.sortedByDescending { it.second }.take(300)
            if (f.name == "covers") {
                // the folder is shared: book covers are named by a 32-letter MD5, the music covers by a 40-letter SHA-1; the "found nothing" marks belong to whichever made them
                val book = abs.filter { it.nameWithoutExtension.length == 32 }
                val music = abs.filter { it.nameWithoutExtension.length != 32 }
                listOf(Triple("covers (books)", tr("Book covers: the first page of a PDF or the cover of an EPUB, kept small", "Kitap kapakları: PDF'in ilk sayfası ya da EPUB'ın kapağı, küçük tutulur"), book),
                    Triple("covers (music)", tr("Album covers of your songs that you picked or that were found online", "Şarkılarının albüm kapakları: seçtiklerin ya da internette bulunanlar"), music)).forEach { (n, w, l) ->
                    if (l.isNotEmpty()) out += StoreRow(n, "$base/$group/covers", l.sumOf { it.length() }, w, f, l.size, emptyList(), l.map { it to it.length() }.sortedByDescending { it.second }.take(300), l)
                }
            } else out += StoreRow(f.name, "$base/$group/${f.name}", bytes, whatIs(group, f.name), f, files.size, parts, entries)
        }
    }
    return out.sortedByDescending { it.bytes }
}

/** The group a folder of Pulse's data belongs to, for the summary at the top. */
private fun catOf(r: StoreRow): String = when {
    r.path.contains("/cache/") -> tr("Temporary (cache)", "Geçici (önbellek)")
    r.name == "gamehub" || r.name == "htmlgames" -> "Game Hub"
    r.name == "extras" -> tr("Downloaded packs", "İndirilen paketler")
    r.name.startsWith("torrent") -> tr("Torrent engine", "Torrent motoru")
    r.name.startsWith("kbd") || r.name == "clips" -> tr("Keyboard", "Klavye")
    r.name.startsWith("lyrics") || r.name.startsWith("music") || r.name.startsWith("covers") || r.name in setOf( "skins", "thumbs", "video_net", "artist_art") -> tr("Music, lyrics and books", "Müzik, sözler ve kitaplar")
    else -> tr("Other", "Diğer")
}

/** The kind of a file inside a folder of Pulse's data, for the breakdown under the folder (by the name's start, the sub-folder or the extension). */
private fun kindOf(dir: String, rel: String): String {
    val leaf = rel.substringAfterLast('/')
    if (dir == "gamehub") return when {
        leaf.startsWith("cover_") -> tr("Covers you chose", "Seçtiğin kapaklar")
        leaf.startsWith("steam_") -> tr("Steam covers", "Steam kapakları")
        leaf.startsWith("hero_") -> tr("Wide pictures (shelf backgrounds)", "Geniş resimler (raf arka planları)")
        leaf.startsWith("logo_") -> tr("Logos", "Logolar")
        leaf.startsWith("shot_") -> tr("Screenshots (back of the box)", "Ekran görüntüleri (kutunun arkası)")
        leaf.startsWith("case_") -> tr("Boxes (the case styles)", "Kutular (kılıf stilleri)")
        leaf.startsWith("play_") -> tr("Google Play pictures", "Google Play resimleri")
        leaf.startsWith("guide_") -> tr("Game guides", "Oyun rehberleri")
        leaf.startsWith("icon_") -> tr("Game icons", "Oyun simgeleri")
        else -> tr("Other", "Diğer")
    }
    if (dir == "torrent" && leaf.endsWith(".so")) return tr("The engine itself (libtorrent4j.so), downloaded once", "Motorun kendisi (libtorrent4j.so), bir kez indirilir")
    if (rel.contains('/')) return rel.substringBefore('/')
    return leaf.substringAfterLast('.', "").uppercase().ifEmpty { tr("Files", "Dosyalar") }
}

private fun whatIs(group: String, name: String): String = when {
    name == "gamehub" -> tr("Game Hub: pictures of your games (covers, wide backgrounds, logos, screenshots), their boxes in the case styles, guides and icons. Fetched from the net and made again when needed.",
        "Game Hub: oyunlarının resimleri (kapaklar, geniş arka planlar, logolar, ekran görüntüleri), kılıf stillerindeki kutuları, rehberleri ve simgeleri. Ağdan indirilir, gerektiğinde yeniden yapılır.")
    name == "torrent" -> tr("The torrent engine (libtorrent, about 16 MB), fetched once when you first download a torrent or a series. It is not part of the app.",
        "Torrent motoru (libtorrent, yaklaşık 16 MB): ilk torrent ya da dizi indirmende bir kez indirilir. Uygulamanın içinde değildir.")
    name == "torrent-work" -> tr("Half-downloaded torrent pieces; they go when the download is finished or removed.", "Yarım inen torrent parçaları; indirme bitince ya da silinince giderler.")
    name == "extras" -> tr("Packs you downloaded from the extras store (skins, wallpapers, icons, mascots, gym photos, …), each in its own folder.",
        "Ekstra mağazasından indirdiğin paketler (kaplamalar, duvar kağıtları, simgeler, maskotlar, spor fotoğrafları, …), her biri kendi klasöründe.")
    name == "kbd" -> tr("The keyboard's language model and what it learned from you (words, pairs, typos). Deleting it makes the keyboard forget.",
        "Klavyenin dil modeli ve senden öğrendikleri (kelimeler, çiftler, yazım hataları). Silersen klavye unutur.")
    name == "clips" -> tr("The keyboard's clipboard history.", "Klavyenin pano geçmişi.")
    name == "htmlgames" -> tr("The web games you added to Game Hub, with their covers.", "Game Hub'a eklediğin web oyunları ve kapakları.")
    name == "skins" -> tr("Skins of the music player (Winamp style) you added.", "Eklediğin müzik çalar kaplamaları (Winamp tarzı).")
    name == "relay" -> tr("Relay: the pairing and the transfer state.", "Relay: eşleşme ve aktarım durumu.")
    name == "standby_font" -> tr("The clock font you chose for StandBy.", "StandBy için seçtiğin saat yazı tipi.")
    name == "weather_tiles" -> tr("Map tiles of the weather map, kept for a while.", "Hava haritası döşemeleri, bir süre saklanır.")
    name == "updates" -> tr("An update of YTXTube that was downloaded, before it is installed.", "İndirilen bir YTXTube güncellemesi (kurulmadan önce).")
    name == "media" -> tr("Video and audio pieces YTXTube keeps for a quick replay.", "YTXTube'un hızlı tekrar için sakladığı video ve ses parçaları.")
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
    // collapsed at first, like the Privacy section: the heading opens and closes the list
    var open by remember { mutableStateOf(false) }
    PCard {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Storage", "Depolama"), color = Look.accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            PIcon("chevron", T.sub, 20.dp, Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f })
        }
    }
    if (open) PCard {
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
        // the summary: the folders grouped by what they are for, biggest first, each with its share as a bar
        val total = list.sumOf { it.bytes }.coerceAtLeast(1L)
        Text(tr("By kind", "Türe göre"), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        list.groupBy { catOf(it) }.map { (k, l) -> k to l.sumOf { it.bytes } }.sortedByDescending { it.second }.forEach { (k, b) ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(k, color = T.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(sizeText(b), color = T.sub, fontSize = 12.sp)
            }
            androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp).background(T.line)) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth((b.toFloat() / total).coerceIn(0.01f, 1f)).height(4.dp).background(Look.accent))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("Folders", "Klasörler"), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        list.forEach { row ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                Text(sizeText(row.bytes), color = T.sub, fontSize = 13.sp)
            }
            Text(row.path, color = T.sub, fontSize = 11.sp)
            Text(row.what, color = T.text, fontSize = 12.sp)
            if (row.parts.isNotEmpty()) {
                Text(tr("${row.count} files", "${row.count} dosya"), color = T.sub, fontSize = 11.sp)
                row.parts.forEach { Text(it, color = T.sub, fontSize = 11.sp) }
            }
            var shown by remember(row.name) { mutableStateOf(false) }
            if (row.entries.isNotEmpty()) Text((if (shown) "▾ " else "▸ ") + tr("What is inside", "İçindekiler"), color = Look.accent, fontSize = 12.sp, modifier = Modifier.clickable { shown = !shown }.padding(vertical = 6.dp))
            if (shown) {
                row.entries.take(60).forEach { (file, size) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        val ext = file.extension.lowercase()
                        val thumb = if (ext == "jpg" || ext == "png" || ext == "jpeg" || ext == "webp") remember(file.path) { runCatching {
                            android.graphics.BitmapFactory.decodeFile(file.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() } else null
                        if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.size(36.dp), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                        else Spacer(Modifier.size(36.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(file.name, color = T.text, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        Text(sizeText(size), color = T.sub, fontSize = 11.sp)
                    }
                }
                if (row.entries.size > 60) Text(tr("… and ${row.count - 60} more", "… ve ${row.count - 60} tane daha"), color = T.sub, fontSize = 11.sp)
            }
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
                runCatching { if (row.members != null) row.members.forEach { it.delete() } else if (row.file.isDirectory) row.file.deleteRecursively() else row.file.delete() }
                askDelete = null
                reload++
            },
            onDismiss = { askDelete = null },
        )
    }
}
