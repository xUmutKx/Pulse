package com.umutk.pulse.extras.launcher

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// The File Explorer of the Windows 7 desktop, drawn the way Windows 7 draws it: the round back and forward buttons, the address bar with its path, the search box,
// the command bar (Organize, Open, New folder), the navigation pane on the left (Favorites, Libraries) and the folder's files in a details list.

private val W7_INK = Color(0xFF1E395B)
private val W7_EDGE = Color(0xFF9EB1CC)
private val W7_BAR = Brush.verticalGradient(listOf(Color(0xFFFAFBFD), Color(0xFFE2E8F0)))
/** The address and command bars lie on the window's glass: light, but the desktop shows through them. */
private val W7_GLASS = Brush.verticalGradient(listOf(Color(0xAAFFFFFF), Color(0x77DCE6F2)))
private val W7_HEAD = Brush.verticalGradient(listOf(Color.White, Color(0xFFF0F3F8)))
private val W7_SEL = Brush.verticalGradient(listOf(Color(0xFFEBF4FD), Color(0xFFD2E5FA)))
private val W7_FOLDER = Color(0xFFE9C24D)

@Composable
fun Win7Explorer() {
    val ctx = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    val back = remember { mutableStateListOf<File>() }
    val fwd = remember { mutableStateListOf<File>() }
    var sel by remember { mutableStateOf<String?>(null) }
    var hidden by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    fun go(d: File) { if (d.path != dir.path) { back.add(dir); fwd.clear(); dir = d; sel = null; query = "" } }
    fun open(f: File) { if (f.isDirectory) go(f) else com.umutk.pulse.features.exHandOver(ctx, f, false) }
    val files by produceState(emptyList<File>(), dir, hidden, rev) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                (dir.listFiles()?.toList() ?: emptyList()).filter { hidden || !it.name.startsWith(".") }
                    .map { it to it.isDirectory }.sortedWith(compareBy({ !it.second }, { it.first.name.lowercase() })).map { it.first }
            }.getOrDefault(emptyList())
        }
    }
    val shown = if (query.isBlank()) files else files.filter { it.name.contains(query.trim(), ignoreCase = true) }
    // the window's body is see-through for this tool: only the file area below is white
    val deskWin = LocalDesktopWin.current
    androidx.compose.runtime.DisposableEffect(deskWin) { deskWin?.glass = true; onDispose { deskWin?.glass = false } }
    Column(Modifier.fillMaxSize()) {
        // the back and forward buttons, the address bar and the search box
        Row(Modifier.fillMaxWidth().background(W7_GLASS).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            W7Round(back.isNotEmpty(), false) { val p = back.removeAt(back.lastIndex); fwd.add(dir); dir = p; sel = null; query = "" }
            Spacer(Modifier.width(4.dp))
            W7Round(fwd.isNotEmpty(), true) { val n = fwd.removeAt(fwd.lastIndex); back.add(dir); dir = n; sel = null; query = "" }
            Spacer(Modifier.width(6.dp))
            val crumbs = remember(dir) {
                val l = ArrayList<File>(); var d: File? = dir
                while (d != null && d.path.length >= root.path.length) { l.add(0, d); if (d.path == root.path) break; d = d.parentFile }
                l
            }
            Row(
                Modifier.weight(1f).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)).border(1.dp, W7_EDGE, RoundedCornerShape(2.dp))
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PIcon("folder", W7_FOLDER, 16.dp)
                crumbs.forEachIndexed { i, d ->
                    Text(" › ", color = Color(0xFF7A8AA3), fontSize = 12.sp)
                    Text(if (i == 0) tr("Internal storage", "Dahili depolama") else d.name, color = W7_INK, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clickable { go(d) })
                }
            }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(96.dp).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)).border(1.dp, W7_EDGE, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = W7_INK, fontSize = 12.sp), cursorBrush = SolidColor(W7_INK), modifier = Modifier.fillMaxWidth())
                if (query.isEmpty()) Text(tr("Search", "Ara"), color = Color(0xFF8A97AB), fontSize = 12.sp)
            }
        }
        // the command bar
        Row(Modifier.fillMaxWidth().background(W7_GLASS).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                W7Cmd(tr("Organize ▾", "Düzenle ▾")) { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(if (hidden) tr("Hide hidden files", "Gizli dosyaları gizle") else tr("Show hidden files", "Gizli dosyaları göster")) }, { hidden = !hidden; menu = false })
                    DropdownMenuItem({ Text(tr("Refresh", "Yenile")) }, { rev++; menu = false })
                }
            }
            W7Cmd(tr("Open", "Aç"), enabled = sel != null) { files.firstOrNull { it.path == sel }?.let { open(it) } }
            W7Cmd(tr("New folder", "Yeni klasör")) {
                var n = File(dir, "New folder"); var i = 2
                while (n.exists()) { n = File(dir, "New folder ($i)"); i++ }
                if (n.mkdir()) { rev++; sel = n.path }
            }
            Box(Modifier.weight(1f))
            Text("${shown.size}", color = Color(0xFF6B7B93), fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(W7_EDGE))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().background(Color.White)) {
            val wide = maxWidth >= 380.dp
            val cols = maxWidth >= 520.dp
            Row(Modifier.fillMaxSize()) {
                // the navigation pane
                if (wide) Column(Modifier.width(112.dp).fillMaxHeight().background(Color(0xFFF3F6FB)).verticalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    W7NavTitle(tr("Favorites", "Sık kullanılanlar"))
                    listOf(tr("Downloads", "İndirilenler") to Environment.DIRECTORY_DOWNLOADS, "DCIM" to Environment.DIRECTORY_DCIM).forEach { (l, n) ->
                        val f = File(root, n); if (f.exists()) W7Nav(l, dir.path == f.path) { go(f) }
                    }
                    W7NavTitle(tr("Libraries", "Kütüphaneler"))
                    listOf(
                        tr("Documents", "Belgeler") to Environment.DIRECTORY_DOCUMENTS, tr("Music", "Müzik") to Environment.DIRECTORY_MUSIC,
                        tr("Pictures", "Resimler") to Environment.DIRECTORY_PICTURES, tr("Videos", "Videolar") to Environment.DIRECTORY_MOVIES,
                    ).forEach { (l, n) -> val f = File(root, n); if (f.exists()) W7Nav(l, dir.path == f.path) { go(f) } }
                    W7NavTitle(tr("Computer", "Bilgisayar"))
                    W7Nav(tr("Internal storage", "Dahili depolama"), dir.path == root.path) { go(root) }
                }
                if (wide) Box(Modifier.width(1.dp).fillMaxHeight().background(W7_EDGE))
                // the details list
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().background(W7_HEAD).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Name", "Ad"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(tr("Date modified", "Değiştirme tarihi"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.width(118.dp))
                        if (cols) Text(tr("Type", "Tür"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.width(110.dp))
                        if (cols) Text(tr("Size", "Boyut"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFD5DDE8)))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.path }) { f ->
                            val isDir = f.isDirectory
                            val on = f.path == sel
                            Row(
                                Modifier.fillMaxWidth().then(if (on) Modifier.background(W7_SEL).border(1.dp, Color(0xFF7DA2CE)) else Modifier)
                                    .clickable { if (on) open(f) else sel = f.path }.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    PIcon(if (isDir) "folder" else com.umutk.pulse.features.fileTypeKey(f), if (isDir) W7_FOLDER else Color(0xFF5B7BA8), 18.dp)
                                    Text(f.name, color = Color(0xFF1A1A1A), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
                                }
                                Text(com.umutk.pulse.features.exDate(f.lastModified(), com.umutk.pulse.features.FilesPrefs.dateFmt), color = Color(0xFF555555), fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(118.dp))
                                if (cols) Text(if (isDir) tr("File folder", "Dosya klasörü") else (f.extension.uppercase().ifEmpty { "File" } + " " + tr("File", "Dosyası")), color = Color(0xFF555555), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                                if (cols) Text(if (isDir) "" else com.umutk.pulse.features.exSize(f.length()), color = Color(0xFF555555), fontSize = 12.sp, maxLines = 1, modifier = Modifier.width(70.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The round back and forward button: glassy blue when it can be used, grey when it cannot. */
@Composable
private fun W7Round(enabled: Boolean, forward: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(CircleShape)
            .background(if (enabled) Brush.verticalGradient(listOf(Color(0xFFC9DDF2), Color(0xFF86AEDA))) else Brush.verticalGradient(listOf(Color(0xFFE9EDF2), Color(0xFFD3DAE4))))
            .border(1.dp, if (enabled) Color(0xFF5F86B6) else Color(0xFFB4BFCF), CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.graphicsLayer { rotationZ = if (forward) 180f else 0f }) { PIcon("back", if (enabled) Color.White else Color(0xFF9AA7B8), 16.dp) } }
}

@Composable
private fun W7Cmd(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = if (enabled) W7_INK else Color(0xFF9AA7B8), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick).padding(horizontal = 9.dp, vertical = 8.dp))
}

@Composable
private fun W7NavTitle(label: String) {
    Text(label, color = W7_INK, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 3.dp))
}

@Composable
private fun W7Nav(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (on) Modifier.background(W7_SEL) else Modifier).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIcon("folder", W7_FOLDER, 15.dp)
        Text(label, color = Color(0xFF1A1A1A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}
