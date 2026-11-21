package com.umutk.pulse.files.recent

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.features.FilesPrefs
import com.umutk.pulse.features.exDate
import com.umutk.pulse.tr
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Full-page Recent view: files opened in Files, then the newest files on the device. No selection here. */
@Composable
fun RecentScreen(onOpen: (File) -> Unit, onClose: () -> Unit, onLong: (File) -> Unit = {}) {
    val ctx = LocalContext.current
    FilesPrefs.load(ctx)
    val recent = remember { RecentStore.list(ctx).map { File(it) }.filter { it.isFile } }
    // the new files come in pages as the list is scrolled: no limit, and no wait for the whole phone to be read
    var arrivals by remember { mutableStateOf<List<RecentStore.Arrival>?>(null) }
    var offset by remember { mutableStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    suspend fun more() {
        if (loading || ended) return
        loading = true
        val (page, rows) = withContext(Dispatchers.IO) { RecentStore.arrivalsPage(ctx, offset, 60) }
        offset += rows
        if (rows == 0) ended = true
        arrivals = ((arrivals ?: emptyList()) + page).distinctBy { it.path }
        loading = false
    }
    LaunchedEffect(Unit) { more() }
    // when a file picker waits for one kind of file, the others are shown dim and cannot be chosen
    val pickMime = com.umutk.pulse.features.LocalPickMime.current

    Column(Modifier.fillMaxSize().background(T.panel)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 24.dp) }
            Text(tr("Recent", "Son"), Modifier.padding(start = 4.dp), color = T.text, fontSize = 18.sp)
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Head(tr("Recently opened", "Son açılanlar")) }
            if (recent.isEmpty()) item { Empty(tr("Nothing yet", "Henüz yok")) }
            items(recent) { f -> Entry(f, f.parentFile?.name.orEmpty(), exDate(f.lastModified(), FilesPrefs.dateFmt), { onOpen(f) }, { onLong(f) }, dimmed = !com.umutk.pulse.features.pickMatches(pickMime, f)) }

            item { Head(tr("New files", "Yeni gelenler")) }
            val list = arrivals
            when {
                list == null -> Unit
                list.isEmpty() && !RecentStore.canListAll(ctx) -> item { Empty(tr("No access", "Erişim yok")) }
                list.isEmpty() -> item { Empty(tr("Nothing yet", "Henüz yok")) }
                else -> {
                    items(list, key = { it.path }) { a -> Entry(File(a.path), File(a.path).parentFile?.name.orEmpty(), exDate(a.addedMs, FilesPrefs.dateFmt), { onOpen(File(a.path)) }, { onLong(File(a.path)) }, dimmed = !com.umutk.pulse.features.pickMatches(pickMime, File(a.path))) }
                    // the end of what is loaded: reaching it reads the next page
                    if (!ended) item { LaunchedEffect(list.size) { more() }; Box(Modifier.fillMaxWidth().height(40.dp)) }
                }
            }
        }
    }
}

@Composable
private fun Head(t: String) = Text(t, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), color = T.sub, fontSize = 12.sp)

@Composable
private fun Empty(t: String) = Text(t, Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp), color = T.sub, fontSize = 13.sp)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Entry(file: File, folder: String, date: String, onClick: () -> Unit, onLong: () -> Unit = {}, dimmed: Boolean = false) {
    val name = file.name
    Row(Modifier.fillMaxWidth().alpha(if (dimmed) .32f else 1f).combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            val th = com.umutk.pulse.features.exThumb(file)
            if (th != null) {
                androidx.compose.foundation.Image(th, null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                com.umutk.pulse.features.FileTypeBadge(file, Modifier.align(Alignment.BottomEnd).padding(2.dp), 18.dp)
            } else com.umutk.pulse.features.FileTypeBadge(file, size = 40.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOf(folder, date).filter { it.isNotEmpty() }.joinToString("  ·  "), color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
