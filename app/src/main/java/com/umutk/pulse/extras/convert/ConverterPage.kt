package com.umutk.pulse.extras.convert

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The converter: a file picked from the phone's own folders (this app's list, not the system's picker), then the format it becomes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConverterPage() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<File?>(null) }
    var target by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Convert a file", "Dosya dönüştür"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        val file = picked
        if (file == null) {
            Text(tr("Pick a file. Only the types that can be converted are shown in colour.", "Bir dosya seç. Dönüştürülebilen türler renkli görünür."), color = T.sub, fontSize = 13.sp)
            FileBrowser { f ->
                picked = f
                target = Converter.targetsFor(f).firstOrNull().orEmpty()
                note = ""
            }
        } else {
            Text(file.name, color = T.text, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(tr("Convert to", "Şuna dönüştür"), color = T.sub, fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Converter.targetsFor(file).forEach { t ->
                    PChip(target == t, t.uppercase()) { target = t; note = "" }
                }
            }
            PButton(if (busy) tr("Converting…", "Dönüştürülüyor…") else tr("Convert", "Dönüştür"), Modifier.fillMaxWidth()) {
                if (!busy && target.isNotEmpty()) {
                    busy = true
                    note = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { Converter.convert(ctx, file, target) } }
                        busy = false
                        note = result.fold(
                            { out -> tr("Saved next to the file: ${out.name}", "Dosyanın yanına kaydedildi: ${out.name}") },
                            { tr("This file could not be converted", "Bu dosya dönüştürülemedi") }
                        )
                    }
                }
            }
            if (note.isNotEmpty()) Text(note, color = T.text, fontSize = 14.sp)
            PButton(tr("Pick another file", "Başka dosya seç"), Modifier.fillMaxWidth()) { picked = null; note = "" }
        }
    }
}

/** The phone's folders, from the storage root: folders open on a tap, files that can be converted are picked. */
@Composable
private fun FileBrowser(onPick: (File) -> Unit) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    val entries = remember(dir) {
        (dir.listFiles()?.toList() ?: emptyList())
            .filter { !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (dir != root) {
                Row(Modifier.clickable { dir.parentFile?.let { dir = it } }.padding(vertical = 6.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("back", T.text, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Up", "Üst klasör"), color = T.text, fontSize = 14.sp)
                }
            }
            Text(dir.absolutePath, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp).weight(1f))
        }
        if (entries.isEmpty()) Text(tr("Nothing here", "Burada bir şey yok"), color = T.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries) { f ->
                val ok = f.isDirectory || Converter.targetsFor(f).isNotEmpty()
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = ok) { if (f.isDirectory) dir = f else onPick(f) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PIcon(if (f.isDirectory) "folder" else "filetypes", if (ok) T.text else T.sub, 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(f.name, color = if (ok) T.text else T.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
