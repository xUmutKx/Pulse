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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
    // the shared walk of the storage (see StorageScan): the same numbers as the folder tree, and no second scan
    val snap by StorageScan.snap.collectAsState()
    LaunchedEffect(root) { StorageScan.ensure(root) }
    val summaries = snap?.takeIf { it.root == root.path }?.kinds

    Column(Modifier.fillMaxSize().background(T.bg).padding(horizontal = 16.dp)) {
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Look.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Scanning...", color = T.sub, fontSize = 13.sp)
                }
            }
            return@Column
        }

        val totalBytes = data.sumOf { it.bytes }
        val totalCount = data.sumOf { it.count }
        val open = selected

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
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
                        .padding(vertical = 3.dp)
                        .background(T.panel, RoundedCornerShape(T.radius))
                        .clickable { selected = if (isOpen) null else s.kind }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.kind.label, color = T.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("${s.count} files", color = T.sub, fontSize = 12.sp)
                    }
                    Text(tbFormatBytes(s.bytes), color = T.text, fontSize = 14.sp)
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
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                f.file.name,
                                color = T.text,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tbFormatBytes(f.size), color = T.sub, fontSize = 13.sp)
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
}

private fun tbFormatBytes(b: Long): String = when {
    b >= 1L shl 30 -> "%.1f GB".format(b / 1073741824.0)
    b >= 1L shl 20 -> "%.1f MB".format(b / 1048576.0)
    b >= 1L shl 10 -> "%.0f KB".format(b / 1024.0)
    else -> "$b B"
}
