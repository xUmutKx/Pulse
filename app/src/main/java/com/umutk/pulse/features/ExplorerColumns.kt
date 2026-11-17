package com.umutk.pulse.features

import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files on a wide screen (a tablet turned sideways): the page is not one list with an empty right side but columns, as in a file manager of a computer. At the left the usual places; then one column
 * for every folder on the way (the folder that was opened is marked in the column before it), the last column is the folder you are in; at the right the file that was picked, with its picture and
 * its details, or, with none picked, the folder's. A tap on a folder opens it (a new column), a tap on a file picks it, "Open" opens it. The list view (the old one) is one tap away.
 */
@Composable
internal fun ExColumnsPane(
    dir: File, top: File, showHidden: Boolean,
    onGo: (String) -> Unit, onOpen: (File) -> Unit, onList: () -> Unit, onMenu: () -> Unit,
) {
    val ctx = LocalContext.current
    var picked by remember(dir.path) { mutableStateOf<File?>(null) }
    // the folders from the top to here, one column each
    val chain = remember(dir.path, top.path) {
        val out = ArrayList<File>()
        var d: File? = dir
        while (d != null) { out.add(0, d); if (d.path == top.path) break; d = d.parentFile }
        if (out.firstOrNull()?.path != top.path) out.add(0, top)
        out
    }
    val hs = rememberScrollState()
    LaunchedEffect(dir.path) { kotlinx.coroutines.delay(60); hs.animateScrollTo(hs.maxValue) }
    Column(Modifier.fillMaxSize()) {
        // the way to here, as one pill; each part opens that folder
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onMenu() }, contentAlignment = Alignment.Center) { PIcon("menu", T.text, 22.dp) }
            Row(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(T.panel).horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                chain.forEachIndexed { i, f ->
                    val last = i == chain.lastIndex
                    Text(if (i == 0) tr("Internal storage", "Dahili depolama") else f.name, color = if (last) Look.accent else T.sub, fontSize = 13.sp, fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, modifier = Modifier.clickable { onGo(f.path) })
                    if (!last) Text("  ›  ", color = T.sub, fontSize = 13.sp)
                }
            }
            Box(Modifier.size(40.dp).clickable { onList() }, contentAlignment = Alignment.Center) { PIcon("listView", T.text, 22.dp) }
        }
        Row(Modifier.fillMaxSize()) {
            // the usual places
            Column(Modifier.width(190.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 12.dp, end = 4.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val places = remember {
                    listOf(
                        Triple(tr("Internal storage", "Dahili depolama"), "storage", top),
                        Triple(tr("Downloads", "İndirilenler"), "download", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)),
                        Triple(tr("Documents", "Belgeler"), "document", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)),
                        Triple(tr("Pictures", "Resimler"), "image", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)),
                        Triple(tr("Music", "Müzik"), "music", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)),
                        Triple(tr("Videos", "Videolar"), "video", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)),
                        Triple("DCIM", "image", Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)),
                    )
                }
                places.forEach { (label, ic, f) ->
                    val on = dir.path == f.path || dir.path.startsWith(f.path + "/") && f.path != top.path
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) Look.accent.copy(alpha = .16f) else androidx.compose.ui.graphics.Color.Transparent).clickable { onGo(f.path) }.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon(ic, if (on) Look.accent else T.sub, 20.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(label, color = if (on) Look.accent else T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // the columns
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(hs)) {
                chain.forEachIndexed { i, folder ->
                    val next = chain.getOrNull(i + 1)
                    ExColumn(folder, showHidden, mark = next?.path ?: picked?.path, last = i == chain.lastIndex,
                        onFolder = { onGo(it.path) }, onFile = { picked = it })
                    Box(Modifier.width(1.dp).fillMaxHeight().background(T.line.copy(alpha = .6f)))
                }
            }
            // the picked file, or the folder
            Column(Modifier.width(300.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ExDetails(ctx, picked ?: dir, onOpen)
            }
        }
    }
}

/** One column: the contents of [folder], folders first, then files; the row of [mark] is marked. */
@Composable
private fun ExColumn(folder: File, showHidden: Boolean, mark: String?, last: Boolean, onFolder: (File) -> Unit, onFile: (File) -> Unit) {
    val list by produceState<List<File>?>(null, folder.path, showHidden) {
        value = withContext(Dispatchers.IO) {
            runCatching { folder.listFiles()?.filter { showHidden || !it.name.startsWith(".") }?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) }.getOrNull() ?: emptyList()
        }
    }
    Column(Modifier.width(270.dp).fillMaxHeight()) {
        val l = list
        if (l == null) Box(Modifier.fillMaxSize())
        else if (l.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Empty", "Boş"), color = T.sub, fontSize = 13.sp) }
        else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            items(l, key = { it.path }) { f ->
                val on = f.path == mark
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) Look.accent.copy(alpha = if (last) .22f else .14f) else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { if (f.isDirectory) onFolder(f) else onFile(f) }.padding(horizontal = 8.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(if (f.isDirectory) "folder" else "document", if (f.isDirectory) Look.accent else T.sub, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (f.isDirectory) (f.list()?.size ?: 0).let { tr("$it items", "$it öğe") } else exSize(f.length()) + "  " + exDate(f.lastModified(), 0), color = T.sub, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The right column: a picture of the picked file (when it is one), its title, size, dates and place, and the buttons. */
@Composable
private fun ExDetails(ctx: Context, f: File, onOpen: (File) -> Unit) {
    val thumb by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, f.path) {
        value = withContext(Dispatchers.IO) {
            val ext = f.extension.lowercase()
            if (f.isFile && ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp"))
                runCatching { android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull()
            else null
        }
    }
    if (thumb != null) androidx.compose.foundation.Image(thumb!!, f.name, Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
    else Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(12.dp)).background(T.panel), contentAlignment = Alignment.Center) { PIcon(if (f.isDirectory) "folder" else "document", Look.accent, 44.dp) }
    Text(f.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
    if (f.isFile) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Open", "Aç"), compact = true) { onOpen(f) }
            PButton(tr("Share", "Paylaş"), compact = true) { exShareMany(ctx, listOf(f)) }
        }
    }
    @Composable fun line(k: String, v: String) { if (v.isNotBlank()) Column { Text(k, color = T.sub, fontSize = 11.sp); Text(v, color = T.text, fontSize = 13.sp) } }
    line(tr("Size", "Boyut"), if (f.isFile) exSize(f.length()) else (f.list()?.size ?: 0).let { tr("$it items", "$it öğe") })
    line(tr("Last modified", "Son değişiklik"), exDate(f.lastModified(), 0))
    line(tr("Path", "Konum"), f.path)
}
