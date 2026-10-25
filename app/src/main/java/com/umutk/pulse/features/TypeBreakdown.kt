@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import com.umutk.pulse.WinAlert
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.umutk.pulse.PLoader
import com.umutk.pulse.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.PriorityQueue

internal const val TOP_PER_KIND = 100

enum class FileKind(val label: String, val exts: Set<String>) {
    IMAGES("Images", setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "svg", "raw", "dng")),
    VIDEO("Video", setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "m4v", "ts")),
    AUDIO("Audio", setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma", "amr", "mid")),
    DOCUMENTS(
        "Documents",
        setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "ods", "odp", "rtf", "csv", "epub", "md"),
    ),
    ARCHIVES("Archives", setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz")),
    APPS("Apps", setOf("apk", "apks", "xapk", "aab")),
    OTHER("Other", emptySet()),
}

data class KindSummary(val kind: FileKind, val bytes: Long, val count: Int, val largest: List<LargeFile>)

internal val KindByExt: Map<String, FileKind> =
    FileKind.entries.flatMap { k -> k.exts.map { it to k } }.toMap()


internal class KindAcc {
    var bytes = 0L
    var count = 0
    val top = PriorityQueue<LargeFile>(TOP_PER_KIND, compareBy<LargeFile> { it.size })
}

@Composable
fun TypeBreakdownScreen(root: File, onBack: () -> Unit) {
    // a type tapped on the Files home page opens its list at once (one tap)
    var selected by remember(root) { mutableStateOf<FileKind?>(when (com.umutk.pulse.files.home.FilesHomeNav.kind) {
        "images" -> FileKind.IMAGES; "videos" -> FileKind.VIDEO; "audio" -> FileKind.AUDIO; "docs" -> FileKind.DOCUMENTS; "archives" -> FileKind.ARCHIVES; "apps" -> FileKind.APPS; else -> null
    }.also { com.umutk.pulse.files.home.FilesHomeNav.kind = null }) }
    // a list opened from the Files home goes straight back there; only a list opened from the overview goes back to the overview
    val openedDirect = remember(root) { selected != null }
    var pending by remember { mutableStateOf<LargeFile?>(null) }
    var note by remember { mutableStateOf("") }
    // a long press picks files; the bin at the top right deletes all the picked ones at once
    var sel by remember { mutableStateOf(setOf<String>()) }
    var askBulk by remember { mutableStateOf(false) }
    // what a swipe to the left and to the right does to a file (delete, open, pick or nothing)
    val prefs = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("pulse_types", 0)
    var swL by remember { mutableStateOf(prefs.getString("swL", "delete") ?: "delete") }
    var swR by remember { mutableStateOf(prefs.getString("swR", "delete") ?: "delete") }
    // the shared walk of the storage (see StorageScan): the same numbers as the folder tree, and no second scan
    val snap by StorageScan.snap.collectAsState()
    val scanned by StorageScan.scanned.collectAsState()
    LaunchedEffect(root) { StorageScan.ensure(root) }
    val summaries = snap?.takeIf { it.root == root.path }?.kinds

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (selected != null && !openedDirect) selected = null else onBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = T.text)
            }
            androidx.activity.compose.BackHandler(selected != null && !openedDirect) { selected = null }
            Column(Modifier.weight(1f)) {
                Text(selected?.let { kindLabel(it) } ?: com.umutk.pulse.tr("Storage by type", "Türe göre depolama"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(root.path, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (sel.isNotEmpty()) {
                Text("${sel.size}", color = T.sub, fontSize = 14.sp)
                IconButton(onClick = { askBulk = true }) { Icon(Icons.Filled.Delete, com.umutk.pulse.tr("Delete", "Sil"), tint = Color(0xFFC42B1C)) }
                IconButton(onClick = { sel = emptySet() }) { Icon(Icons.Filled.Close, com.umutk.pulse.tr("Cancel", "Vazgeç"), tint = T.text) }
            }
            IconButton(onClick = { StorageScan.rescan(root) }) { Icon(Icons.Filled.Refresh, contentDescription = com.umutk.pulse.tr("Refresh", "Yenile"), tint = T.text) }
        }

        val data = summaries
        if (data == null) {
            // the same loading bar and count as the folder tree
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                com.umutk.pulse.Busy()
                Text(if (scanned < 0) com.umutk.pulse.tr("Reading the saved scan…", "Kayıtlı tarama okunuyor…") else com.umutk.pulse.tr("Measuring… $scanned files", "Ölçülüyor… $scanned dosya"), color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            return@Column
        }

        val totalBytes = data.sumOf { it.bytes }
        val totalCount = data.sumOf { it.count }
        val open = selected

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (note.isNotEmpty()) item { Text(note, color = Color(0xFFC42B1C), fontSize = 12.sp) }
            if (open == null) item {
                DonutChart(
                    slices = data.map { kindLabel(it.kind) to it.bytes },
                    modifier = Modifier.padding(vertical = 12.dp),
                    centerText = tbFormatBytes(totalBytes),
                )
                Text(
                    "$totalCount " + com.umutk.pulse.tr("files", "dosya"),
                    color = T.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }

            // a kind opens its own list, biggest file first, instead of unfolding under the kinds
            if (open == null) items(data, key = { it.kind.name }) { s ->
                val isOpen = s.kind == open
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp)
                        .background(T.panel, RoundedCornerShape(T.radius))
                        .clickable { selected = if (isOpen) null else s.kind }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(kindLabel(s.kind), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("${s.count} " + com.umutk.pulse.tr("files", "dosya"), color = T.sub, fontSize = 13.sp)
                    }
                    Text(tbFormatBytes(s.bytes), color = T.text, fontSize = 16.sp)
                }
            }

            if (open != null) {
                val s = data.first { it.kind == open }
                item {
                    Text(
                        "${kindLabel(open)}: " + com.umutk.pulse.tr("largest first", "en büyük önce"),
                        color = T.sub,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                if (s.largest.isEmpty()) {
                    item { Text(com.umutk.pulse.tr("No files of this type", "Bu türde dosya yok"), color = T.sub, fontSize = 13.sp) }
                }
                items(s.largest, key = { it.file.path }) { f ->
                    // a tap opens the file in the viewer or app that handles it
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    val picked = f.file.path in sel
                    val sc = androidx.compose.runtime.rememberCoroutineScope()
                    val off = remember(f.file.path) { androidx.compose.animation.core.Animatable(0f) }
                    var boxW by remember { mutableStateOf(1f) }
                    fun doAct(a: String) {
                        when (a) {
                            "delete" -> note = if (f.file.delete()) { StorageScan.forget(f.file, f.size); "" } else com.umutk.pulse.tr("Could not delete ${f.file.name}", "${f.file.name} silinemedi")
                            "open" -> tbOpen(ctx, f.file)
                            "pick" -> sel = sel + f.file.path
                        }
                    }
                    Box(Modifier.fillMaxWidth().padding(vertical = 1.dp).onSizeChanged { boxW = it.width.toFloat() }) {
                        // what the swipe will do shows behind the row, as an icon and a label on a colour
                        val act = if (off.value > 0f) swR else swL
                        if (off.value != 0f && act != "none") Row(
                            Modifier.matchParentSize().background(swipeColor(act)).padding(horizontal = 20.dp),
                            horizontalArrangement = if (off.value > 0f) Arrangement.Start else Arrangement.End, verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(swipeIcon(act), null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(swipeLabel(act), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Row(
                            Modifier.fillMaxWidth().offset { androidx.compose.ui.unit.IntOffset(off.value.toInt(), 0) }
                                .background(if (picked) T.accent.copy(alpha = .14f).compositeOver(T.bg) else T.bg)
                                .pointerInput(f.file.path, swL, swR) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            val a = if (off.value > 0f) swR else swL
                                            val far = kotlin.math.abs(off.value) > boxW * .4f
                                            sc.launch { off.animateTo(0f, androidx.compose.animation.core.spring()) }
                                            if (far && a != "none") doAct(a)
                                        },
                                        onDragCancel = { sc.launch { off.animateTo(0f) } },
                                    ) { change, dx -> change.consume(); sc.launch { off.snapTo((off.value + dx).coerceIn(-boxW * .7f, boxW * .7f)) } }
                                }
                                .combinedClickable(
                                    onClick = { if (sel.isNotEmpty()) sel = if (picked) sel - f.file.path else sel + f.file.path else tbOpen(ctx, f.file) },
                                    onLongClick = { sel = if (picked) sel - f.file.path else sel + f.file.path },
                                ).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TbThumb(f.file, picked)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(f.file.name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    tbFormatBytes(f.size) + " · " + f.file.parent.orEmpty().removePrefix(root.path).trimStart('/').ifEmpty { "/" },
                                    color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (askBulk) {
        val files = (summaries?.flatMap { it.largest }.orEmpty()).filter { it.file.path in sel }
        WinAlert(
            onDismissRequest = { askBulk = false },
            title = { Text(com.umutk.pulse.tr("Delete ${files.size} files?", "${files.size} dosya silinsin mi?")) },
            text = { Text(tbFormatBytes(files.sumOf { it.size }) + ". " + com.umutk.pulse.tr("This cannot be undone.", "Bu geri alınamaz.")) },
            confirmButton = {
                TextButton(onClick = {
                    askBulk = false
                    var failed = 0
                    files.forEach { f -> if (f.file.delete()) StorageScan.forget(f.file, f.size) else failed++ }
                    note = if (failed > 0) com.umutk.pulse.tr("$failed could not be deleted", "$failed dosya silinemedi") else ""
                    sel = emptySet()
                }) { Text(com.umutk.pulse.tr("Delete", "Sil")) }
            },
            dismissButton = { TextButton(onClick = { askBulk = false }) { Text(com.umutk.pulse.tr("Cancel", "Vazgeç")) } },
        )
    }
    pending?.let { f ->
        WinAlert(
            onDismissRequest = { pending = null },
            title = { Text(com.umutk.pulse.tr("Delete this file?", "Bu dosya silinsin mi?")) },
            text = { Text("${f.file.name}, ${tbFormatBytes(f.size)}. " + com.umutk.pulse.tr("This cannot be undone.", "Bu geri alınamaz.")) },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    note = if (f.file.delete()) { StorageScan.forget(f.file, f.size); "" } else com.umutk.pulse.tr("Could not delete ${f.file.name}", "${f.file.name} silinemedi")
                }) { Text(com.umutk.pulse.tr("Delete", "Sil")) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(com.umutk.pulse.tr("Cancel", "Vazgeç")) } },
        )
    }
}

/** A small picture of a file: the picture itself, a frame of a video, else the sign of its kind; a tick when picked. */
internal fun swipeLabel(a: String) = when (a) { "delete" -> com.umutk.pulse.tr("Delete", "Sil"); "open" -> com.umutk.pulse.tr("Open", "Aç"); "pick" -> com.umutk.pulse.tr("Select", "Seç"); else -> com.umutk.pulse.tr("Nothing", "Hiçbir şey") }
private fun swipeIcon(a: String): androidx.compose.ui.graphics.vector.ImageVector = when (a) { "delete" -> Icons.Filled.Delete; "open" -> Icons.AutoMirrored.Filled.OpenInNew; else -> Icons.Filled.Check }
private fun swipeColor(a: String): Color = when (a) { "delete" -> Color(0xFFC42B1C); "open" -> Color(0xFF1E6FD9); else -> Color(0xFF2E7D32) }

@Composable
private fun TbThumb(f: File, picked: Boolean) {
    val ext = f.extension.lowercase()
    val isImage = ext in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    val isVideo = ext in setOf("mp4", "mkv", "webm", "3gp", "mov", "avi")
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, f.path) {
        value = if (!isImage && !isVideo) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                if (isImage) {
                    val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(f.path, o)
                    var s = 1; while (o.outWidth / (s * 2) >= 160 && o.outHeight / (s * 2) >= 160) s *= 2
                    android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = s })
                } else {
                    val r = android.media.MediaMetadataRetriever()
                    try { r.setDataSource(f.path); r.getFrameAtTime(1_000_000L) } finally { runCatching { r.release() } }
                }
            }.getOrNull()
        }
    }
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(T.panel), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        else com.umutk.pulse.PIcon(if (ext in setOf("mp3", "m4a", "flac", "ogg", "wav", "opus", "aac")) "music" else if (ext == "apk") "apps" else "document", T.sub, 24.dp)
        if (picked) Box(Modifier.fillMaxSize().background(T.accent.copy(alpha = .55f)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(26.dp)) }
    }
}

/** Opens a file from the list like Files does: pictures, Office files and text in Pulse's own viewers, anything else through the system. */
private fun tbOpen(ctx: android.content.Context, f: File) {
    val e = f.extension.lowercase()
    when {
        e in com.umutk.pulse.IMG_EXTS -> com.umutk.pulse.imgOpen(ctx, f)
        e in com.umutk.pulse.OF_EXTS -> com.umutk.pulse.ofOpen(ctx, f)
        com.umutk.pulse.ofIsText(f) -> com.umutk.pulse.ofOpenText(ctx, f)
        else -> com.umutk.pulse.features.Downloads.openFile(ctx, f)
    }
}

private fun tbFormatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}

/** The name of a file kind in the language of the app. */
internal fun kindLabel(k: FileKind): String = when (k) {
    FileKind.IMAGES -> com.umutk.pulse.tr("Images", "Resimler"); FileKind.VIDEO -> com.umutk.pulse.tr("Video", "Videolar"); FileKind.AUDIO -> com.umutk.pulse.tr("Audio", "Ses")
    FileKind.DOCUMENTS -> com.umutk.pulse.tr("Documents", "Belgeler"); FileKind.ARCHIVES -> com.umutk.pulse.tr("Archives", "Arşivler"); FileKind.APPS -> com.umutk.pulse.tr("Apps", "Uygulamalar")
    FileKind.OTHER -> com.umutk.pulse.tr("Other", "Diğer")
}
