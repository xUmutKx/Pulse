package com.umutk.pulse.extras.launcher

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val W11_FOLDER = Color(0xFFF1C84B)

/**
 * File Explorer in the way Windows 11 draws it: a tab at the top, round-less icon buttons (back, forward, up, refresh), an address bar and a search box, a command bar
 * (New, Sort, View, hidden files), a navigation pane at the left, the "Quick access" cards on the first page and a status line at the foot. Its panels are tinted
 * and see-through, so the blurred desktop shows through the window.
 */
@Composable
fun Win11Explorer() {
    val ctx = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    val back = remember { mutableStateListOf<File>() }
    val fwd = remember { mutableStateListOf<File>() }
    var sel by remember { mutableStateOf<String?>(null) }
    var hidden by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    var viewMenu by remember { mutableStateOf(false) }
    var sort by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }
    fun go(d: File) { if (d.path != dir.path) { back.add(dir); fwd.clear(); dir = d; sel = null; query = "" } }
    fun open(f: File) {
        if (f.isDirectory) go(f)
        else if (opensInWindow(f)) DesktopWindows.open("view:" + f.path, f.name)
        else com.umutk.pulse.features.exHandOver(ctx, f, false)
    }
    val files by produceState(emptyList<File>(), dir, hidden, rev, sort) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val all = (dir.listFiles()?.toList() ?: emptyList()).filter { hidden || !it.name.startsWith(".") }
                val by: Comparator<File> = when (sort) { 1 -> compareByDescending { it.lastModified() }; 2 -> compareByDescending { if (it.isDirectory) -1L else it.length() }; else -> compareBy { it.name.lowercase() } }
                all.sortedWith(compareBy<File> { !it.isDirectory }.then(by))
            }.getOrDefault(emptyList())
        }
    }
    val shown = if (query.isBlank()) files else files.filter { it.name.contains(query.trim(), ignoreCase = true) }
    // the body of the window is see-through for this tool; its own panels are tinted
    val deskWin = LocalDesktopWin.current
    DisposableEffect(deskWin) { deskWin?.glass = true; onDispose { deskWin?.glass = false } }
    val pane = T.panel.copy(alpha = .55f)
    val line = T.sub.copy(alpha = .22f)
    Column(Modifier.fillMaxSize().background(T.bg.copy(alpha = .35f))) {
        // the tab
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)).background(pane).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("folder", W11_FOLDER, 16.dp)
                Text(if (dir.path == root.path) tr("Home", "Ana sayfa") else dir.name, color = T.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
            }
        }
        // back, forward, up, refresh, the address bar and the search
        Row(Modifier.fillMaxWidth().background(pane).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            W11Icon("back", back.isNotEmpty(), 0f) { val p = back.removeAt(back.lastIndex); fwd.add(dir); dir = p; sel = null; query = "" }
            W11Icon("back", fwd.isNotEmpty(), 180f) { val n = fwd.removeAt(fwd.lastIndex); back.add(dir); dir = n; sel = null; query = "" }
            W11Icon("back", dir.path != root.path, 90f) { dir.parentFile?.let { go(it) } }
            W11Icon("refresh", true, 0f) { rev++ }
            Spacer(Modifier.width(4.dp))
            val crumbs = remember(dir) {
                val l = ArrayList<File>(); var d: File? = dir
                while (d != null && d.path.length >= root.path.length) { l.add(0, d); if (d.path == root.path) break; d = d.parentFile }
                l
            }
            Row(Modifier.weight(1f).height(30.dp).clip(RoundedCornerShape(5.dp)).background(T.bg.copy(alpha = .5f)).border(1.dp, line, RoundedCornerShape(5.dp)).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PIcon("phone", T.sub, 15.dp)
                crumbs.forEachIndexed { i, d ->
                    Text("  ›  ", color = T.sub, fontSize = 12.sp)
                    Text(if (i == 0) tr("Internal storage", "Dahili depolama") else d.name, color = T.text, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clickable { go(d) })
                }
            }
            Spacer(Modifier.width(6.dp))
            Row(Modifier.width(110.dp).height(30.dp).clip(RoundedCornerShape(5.dp)).background(T.bg.copy(alpha = .5f)).border(1.dp, line, RoundedCornerShape(5.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 12.sp), cursorBrush = SolidColor(T.text), modifier = Modifier.fillMaxWidth())
                    if (query.isEmpty()) Text(tr("Search", "Ara"), color = T.sub, fontSize = 12.sp)
                }
                PIcon("search", T.sub, 14.dp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        // the command bar
        Row(Modifier.fillMaxWidth().background(pane).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            W11Cmd("+  " + tr("New", "Yeni")) {
                var n = File(dir, "New folder"); var i = 2
                while (n.exists()) { n = File(dir, "New folder ($i)"); i++ }
                if (n.mkdir()) { rev++; sel = n.path }
            }
            Box {
                W11Cmd(tr("Sort", "Sırala") + " ▾") { sortMenu = true }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    DropdownMenuItem({ Text(tr("Name", "Ad")) }, { sort = 0; sortMenu = false })
                    DropdownMenuItem({ Text(tr("Date modified", "Değiştirme tarihi")) }, { sort = 1; sortMenu = false })
                    DropdownMenuItem({ Text(tr("Size", "Boyut")) }, { sort = 2; sortMenu = false })
                }
            }
            Box {
                W11Cmd(tr("View", "Görünüm") + " ▾") { viewMenu = true }
                DropdownMenu(viewMenu, { viewMenu = false }) {
                    DropdownMenuItem({ Text(if (hidden) tr("Hide hidden files", "Gizli dosyaları gizle") else tr("Show hidden files", "Gizli dosyaları göster")) }, { hidden = !hidden; viewMenu = false })
                }
            }
            W11Cmd(tr("Open", "Aç"), enabled = sel != null) { files.firstOrNull { it.path == sel }?.let { open(it) } }
            Box(Modifier.weight(1f))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(line))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 380.dp
            Row(Modifier.fillMaxSize()) {
                // the navigation pane
                if (wide) Column(Modifier.width(128.dp).fillMaxHeight().background(pane).verticalScroll(rememberScrollState()).padding(vertical = 6.dp, horizontal = 4.dp)) {
                    W11Nav("home", tr("Home", "Ana sayfa"), dir.path == root.path) { go(root) }
                    listOf(
                        tr("Downloads", "İndirilenler") to Environment.DIRECTORY_DOWNLOADS, tr("Documents", "Belgeler") to Environment.DIRECTORY_DOCUMENTS,
                        tr("Pictures", "Resimler") to Environment.DIRECTORY_PICTURES, tr("Music", "Müzik") to Environment.DIRECTORY_MUSIC, tr("Videos", "Videolar") to Environment.DIRECTORY_MOVIES, "DCIM" to Environment.DIRECTORY_DCIM,
                    ).forEach { (l, n) -> val f = File(root, n); if (f.exists()) W11Nav("folder", l, dir.path == f.path) { go(f) } }
                    Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(line))
                    W11Nav("phone", tr("This PC", "Bu bilgisayar"), false) { go(root) }
                }
                if (wide) Box(Modifier.width(1.dp).fillMaxHeight().background(line))
                LazyColumn(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp)) {
                    // the first page: Quick access cards for the usual folders
                    if (dir.path == root.path && query.isBlank()) item {
                        Text(tr("Quick access", "Hızlı erişim"), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                        val quick = listOf(
                            tr("Downloads", "İndirilenler") to Environment.DIRECTORY_DOWNLOADS, tr("Documents", "Belgeler") to Environment.DIRECTORY_DOCUMENTS,
                            tr("Pictures", "Resimler") to Environment.DIRECTORY_PICTURES, tr("Music", "Müzik") to Environment.DIRECTORY_MUSIC, tr("Videos", "Videolar") to Environment.DIRECTORY_MOVIES, "DCIM" to Environment.DIRECTORY_DCIM,
                        ).filter { File(root, it.second).exists() }
                        quick.chunked(if (maxWidth >= 480.dp) 3 else 2).forEach { rowQ ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowQ.forEach { (l, n) ->
                                    Row(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(pane).clickable { go(File(root, n)) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        PIcon("folder", W11_FOLDER, 24.dp)
                                        Text(l, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                repeat(((if (maxWidth >= 480.dp) 3 else 2) - rowQ.size).coerceAtLeast(0)) { Box(Modifier.weight(1f)) }
                            }
                        }
                        Text(tr("All files", "Tüm dosyalar"), color = T.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    }
                    items(shown, key = { it.path }) { f ->
                        val isDir = f.isDirectory
                        val on = f.path == sel
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).then(if (on) Modifier.background(T.accent.copy(alpha = .22f)) else Modifier)
                                .clickable { if (on) open(f) else sel = f.path }.padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PIcon(if (isDir) "folder" else com.umutk.pulse.features.fileTypeKey(f), if (isDir) W11_FOLDER else T.accent, 18.dp)
                            Text(f.name, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                            Text(com.umutk.pulse.features.exDate(f.lastModified(), com.umutk.pulse.features.FilesPrefs.dateFmt), color = T.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        // the status line
        Row(Modifier.fillMaxWidth().background(pane).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${shown.size} " + tr("items", "öğe") + (if (sel != null) "   |   1 " + tr("item selected", "öğe seçildi") else ""), color = T.sub, fontSize = 11.sp)
        }
    }
}

/** A flat icon button of the toolbar: no ring, just the glyph, dim when it cannot be used. */
@Composable
private fun W11Icon(icon: String, enabled: Boolean, rotate: Float, onClick: () -> Unit) {
    Box(Modifier.size(32.dp).clip(RoundedCornerShape(5.dp)).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Box(Modifier.graphicsLayer { rotationZ = rotate }) { PIcon(icon, if (enabled) T.text else T.sub.copy(alpha = .4f), 16.dp) }
    }
}

@Composable
private fun W11Cmd(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(label, color = if (enabled) T.text else T.sub.copy(alpha = .5f), fontSize = 12.sp, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(5.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp))
}

@Composable
private fun W11Nav(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(5.dp)).then(if (on) Modifier.background(T.accent.copy(alpha = .22f)) else Modifier).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIcon(icon, if (icon == "folder") W11_FOLDER else T.accent, 15.dp)
        Text(label, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 7.dp))
    }
}
