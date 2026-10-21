package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var selected by remember(root) { mutableStateOf<FileKind?>(null) }
    var pending by remember { mutableStateOf<LargeFile?>(null) }
    var note by remember { mutableStateOf("") }
    // the shared walk of the storage (see StorageScan): the same numbers as the folder tree, and no second scan
    val snap by StorageScan.snap.collectAsState()
    val scanned by StorageScan.scanned.collectAsState()
    LaunchedEffect(root) { StorageScan.ensure(root) }
    val summaries = snap?.takeIf { it.root == root.path }?.kinds

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = T.text)
            }
            Column(Modifier.weight(1f)) {
                Text("Storage by type", color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(root.path, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { StorageScan.rescan(root) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = T.text) }
        }

        val data = summaries
        if (data == null) {
            // the same loading bar and count as the folder tree
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                com.umutk.pulse.Busy()
                Text("Measuring… $scanned files", color = T.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            return@Column
        }

        val totalBytes = data.sumOf { it.bytes }
        val totalCount = data.sumOf { it.count }
        val open = selected

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (note.isNotEmpty()) item { Text(note, color = Color(0xFFC42B1C), fontSize = 12.sp) }
            item {
                DonutChart(
                    slices = data.map { it.kind.label to it.bytes },
                    modifier = Modifier.padding(vertical = 12.dp),
                    centerText = tbFormatBytes(totalBytes),
                )
                Text(
                    "$totalCount files",
                    color = T.sub,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }

            items(data, key = { it.kind.name }) { s ->
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
                        Text(s.kind.label, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("${s.count} files", color = T.sub, fontSize = 13.sp)
                    }
                    Text(tbFormatBytes(s.bytes), color = T.text, fontSize = 16.sp)
                }
            }

            if (open != null) {
                val s = data.first { it.kind == open }
                item {
                    Text(
                        "Largest ${open.label.lowercase()}",
                        color = T.sub,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                if (s.largest.isEmpty()) {
                    item { Text("No files of this type", color = T.sub, fontSize = 13.sp) }
                }
                items(s.largest, key = { it.file.path }) { f ->
                    // a tap opens the file in the viewer or app that handles it
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp).clickable { tbOpen(ctx, f.file) }.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                f.file.name,
                                color = T.text,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tbFormatBytes(f.size), color = T.sub, fontSize = 13.sp)
                            IconButton(onClick = { pending = f }) { Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFC42B1C)) }
                        }
                        Text(
                            f.file.parent.orEmpty().removePrefix(root.path).trimStart('/').ifEmpty { "/" },
                            color = T.sub,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
    pending?.let { f ->
        WinAlert(
            onDismissRequest = { pending = null },
            title = { Text("Delete this file?") },
            text = { Text("${f.file.name}, ${tbFormatBytes(f.size)}. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    note = if (f.file.delete()) { StorageScan.forget(f.file, f.size); "" } else "Could not delete ${f.file.name}"
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
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
