package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.WinAlert
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Files with the same content: [size] each, [files] oldest first. */
class DupGroup(val size: Long, val files: List<File>) { val wasted get() = size * (files.size - 1) }

private fun md5(f: File, limit: Long = Long.MAX_VALUE, tail: Boolean = false): String {
    val md = MessageDigest.getInstance("MD5")
    val buf = ByteArray(256 * 1024)
    java.io.RandomAccessFile(f, "r").use { r ->
        if (tail && f.length() > limit) r.seek(f.length() - limit)
        var left = limit
        while (left > 0) {
            val n = r.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            md.update(buf, 0, n); left -= n
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/**
 * Looks for files of the same content under [root]: first by size, then by the first and last 64 KB, then by the whole file, so only the files that really match are read to the end.
 * [onStep] says what it is doing. The biggest waste comes first; Android/data and Android/obb and hidden folders are not looked into.
 */
suspend fun scanDuplicates(root: File, minBytes: Long, onStep: (String) -> Unit): List<DupGroup> {
    val bySize = HashMap<Long, MutableList<File>>()
    var seen = 0
    val stack = ArrayDeque<File>(); stack.add(root)
    while (stack.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val d = stack.removeLast()
        if (isSkippedDir(d) || d.name.startsWith(".")) continue
        val kids = d.listFiles() ?: continue
        for (k in kids) {
            if (k.isDirectory) { if (!java.nio.file.Files.isSymbolicLink(k.toPath())) stack.add(k) }
            else if (k.length() >= minBytes) { bySize.getOrPut(k.length()) { ArrayList() }.add(k); if (++seen % 500 == 0) onStep(tr("Looking through files: $seen", "Dosyalar taranıyor: $seen")) }
        }
    }
    val out = ArrayList<DupGroup>()
    val sameSize = bySize.filter { it.value.size > 1 }
    var done = 0
    for ((size, list) in sameSize) {
        currentCoroutineContext().ensureActive()
        onStep(tr("Comparing: ${++done} / ${sameSize.size}", "Karşılaştırılıyor: ${done} / ${sameSize.size}"))
        val part = list.groupBy { runCatching { md5(it, 65536) + md5(it, 65536, tail = true) }.getOrNull() }.filterKeys { it != null }.values.filter { it.size > 1 }
        for (g in part) {
            val full = g.groupBy { runCatching { md5(it) }.getOrNull() }.filterKeys { it != null }.values.filter { it.size > 1 }
            for (m in full) out += DupGroup(size, m.sortedBy { it.lastModified() })
        }
    }
    return out.sortedByDescending { it.wasted }.take(300)
}

/** The duplicate finder: a scan of the storage, the groups of equal files, a tick on the ones to remove (the oldest of each group is kept), and the delete. */
@Composable
fun DuplicatesScreen(back: () -> Unit) {
    val scope = rememberCoroutineScope()
    val root = remember { android.os.Environment.getExternalStorageDirectory() }
    var minIx by remember { mutableIntStateOf(1) }
    val mins = listOf(100L * 1024, 1024L * 1024, 10L * 1024 * 1024)
    val names = listOf("100 KB", "1 MB", "10 MB")
    var step by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<DupGroup>?>(null) }
    var picked by remember { mutableStateOf<Set<String>>(emptySet()) }
    var ask by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    val pickedSize = groups.orEmpty().sumOf { g -> g.files.filter { it.path in picked }.sumOf { g.size } }
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { back() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 20.dp) }
            Text(tr("Duplicate files", "Yinelenen dosyalar"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            names.forEachIndexed { i, n -> PChip(minIx == i, n) { minIx = i } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PButton(if (step != null) tr("Looking…", "Aranıyor…") else tr("Search", "Ara"), Modifier.weight(1f)) {
                if (step == null) { step = ""; groups = null; picked = emptySet(); note = ""
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { scanDuplicates(root, mins[minIx]) { s -> step = s } }.getOrDefault(emptyList()) }
                        groups = r; step = null
                        // every file but the oldest of a group is ticked
                        picked = r.flatMap { g -> g.files.drop(1).map { it.path } }.toSet()
                    }
                }
            }
            if (!groups.isNullOrEmpty()) PButton(tr("Delete", "Sil") + if (picked.isNotEmpty()) " (${picked.size})" else "", Modifier.weight(1f), danger = true) { if (picked.isNotEmpty()) ask = true }
        }
        step?.takeIf { it.isNotEmpty() }?.let { Text(it, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        val g = groups
        if (g != null && g.isEmpty() && step == null) Text(tr("No duplicates found.", "Yinelenen dosya bulunamadı."), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        if (!g.isNullOrEmpty()) {
            Text(tr("${g.size} groups · ${exSize(g.sumOf { it.wasted })} can be freed", "${g.size} grup · ${exSize(g.sumOf { it.wasted })} boşaltılabilir"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(g) { grp ->
                    PCard {
                        Text(grp.files.first().name + "  ·  " + exSize(grp.size), color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        grp.files.forEachIndexed { i, f ->
                            val on = f.path in picked
                            Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - f.path else picked + f.path }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(20.dp).background(if (on) T.accent else T.sub.copy(alpha = .25f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { if (on) PIcon("check", T.bg, 14.dp) }
                                Spacer(Modifier.size(10.dp))
                                Text((if (i == 0) tr("oldest · ", "en eski · ") else "") + f.path.removePrefix(root.path + "/"), color = T.sub, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        } else Spacer(Modifier.weight(1f))
    }
    if (ask) WinAlert(onDismissRequest = { ask = false },
        title = { Text(tr("Delete the ticked files?", "İşaretli dosyalar silinsin mi?")) },
        text = { Text(tr("${picked.size} files, ${exSize(pickedSize)}. This cannot be undone.", "${picked.size} dosya, ${exSize(pickedSize)}. Bu geri alınamaz.")) },
        confirmButton = { TextButton({
            ask = false
            val del = picked
            scope.launch {
                val n = withContext(Dispatchers.IO) { del.count { File(it).delete() } }
                note = tr("$n files deleted", "$n dosya silindi")
                groups = groups?.map { gr -> DupGroup(gr.size, gr.files.filter { it.path !in del }) }?.filter { it.files.size > 1 }
                picked = emptySet()
            }
        }) { Text(tr("Delete", "Sil")) } },
        dismissButton = { TextButton({ ask = false }) { Text(tr("Cancel", "İptal")) } })
}
