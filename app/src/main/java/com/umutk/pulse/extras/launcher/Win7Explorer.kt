package com.umutk.pulse.extras.launcher

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
    // the places already seen, Computer included (null): Back from a folder opened out of Computer goes back to Computer, not to the storage root
    val back = remember { mutableStateListOf<File?>() }
    val fwd = remember { mutableStateListOf<File?>() }
    var sel by remember { mutableStateOf<String?>(null) }
    var hidden by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<File?>(null) }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    // the page opens on Computer, the drives and partitions; a folder chosen leaves it
    var computer by remember { mutableStateOf(true) }
    fun here(): File? = if (computer) null else dir
    fun go(d: File) { if (computer || d.path != dir.path) { back.add(here()); fwd.clear(); dir = d; computer = false; sel = null; query = "" } }
    fun goComputer() { if (!computer) { back.add(here()); fwd.clear(); computer = true; sel = null; query = "" } }
    fun stepBack() { if (back.isNotEmpty()) { val p = back.removeAt(back.lastIndex); fwd.add(here()); if (p == null) computer = true else { dir = p; computer = false }; sel = null; query = "" } }
    fun stepFwd() { if (fwd.isNotEmpty()) { val n = fwd.removeAt(fwd.lastIndex); back.add(here()); if (n == null) computer = true else { dir = n; computer = false }; sel = null; query = "" } }
    // a picture or a text file opens in a window of the desktop; the other kinds go to another app
    fun open(f: File) {
        if (f.isDirectory) go(f)
        else if (opensInWindow(f)) DesktopWindows.open("view:" + f.path, f.name)
        else com.umutk.pulse.features.exHandOver(ctx, f, false)
    }
    var refused by remember { mutableStateOf(false) }
    val files by produceState(emptyList<File>(), dir, hidden, rev) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val raw = dir.listFiles()
                refused = raw == null
                (raw?.toList() ?: emptyList()).filter { hidden || !it.name.startsWith(".") }
                    .map { it to it.isDirectory }.sortedWith(compareBy({ !it.second }, { it.first.name.lowercase() })).map { it.first }
            }.getOrDefault(emptyList())
        }
    }
    val shown = if (query.isBlank()) files else files.filter { it.name.contains(query.trim(), ignoreCase = true) }
    fun selFile() = files.firstOrNull { it.path == sel }
    val keys = Modifier.focusRequester(fr).focusable().fileKeys(
        copy = { selFile()?.let { FileClip.files = listOf(it); FileClip.cut = false } },
        cut = { selFile()?.let { FileClip.files = listOf(it); FileClip.cut = true } },
        paste = { pasteInto(dir); rev++ },
        delete = { selFile()?.let { menuFor = it } },
        rename = { selFile()?.let { menuFor = it } },
        open = { selFile()?.let { open(it) } },
        back = { stepBack() },
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    FileMenu(menuFor, dir, { menuFor = null }, { open(it) }) { rev++; if (it != null) sel = it }
    // the window's body is see-through for this tool: only the file area below is white
    val deskWin = LocalDesktopWin.current
    androidx.compose.runtime.DisposableEffect(deskWin) { deskWin?.glass = true; onDispose { deskWin?.glass = false } }
    Column(Modifier.fillMaxSize().then(keys)) {
        // the back and forward buttons, the address bar and the search box
        Row(Modifier.fillMaxWidth().background(W7_GLASS).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            W7Round(back.isNotEmpty(), false) { stepBack() }
            Spacer(Modifier.width(2.dp))
            W7Round(fwd.isNotEmpty(), true) { stepFwd() }
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
                W7Pic(if (computer) "l_pc" else "dv_internal", 16.dp)
                if (computer) Text(" " + tr("Computer", "Bilgisayar"), color = W7_INK, fontSize = 12.sp, maxLines = 1)
                if (!computer) crumbs.forEachIndexed { i, d ->
                    Text(" › ", color = Color(0xFF7A8AA3), fontSize = 12.sp)
                    Text(if (i == 0) tr("Internal storage", "Dahili depolama") else d.name, color = W7_INK, fontSize = 12.sp, maxLines = 1, modifier = Modifier.clickable { go(d) })
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.width(88.dp).height(28.dp).background(Color.White, RoundedCornerShape(2.dp)).border(1.dp, W7_EDGE, RoundedCornerShape(2.dp)).padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) {
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
                    listOf(Triple(tr("Downloads", "İndirilenler"), Environment.DIRECTORY_DOWNLOADS, "fd_downloads"), Triple("DCIM", Environment.DIRECTORY_DCIM, "fd_camera")).forEach { (l, n, ic) ->
                        val f = File(root, n); if (f.exists()) W7Nav(l, !computer && dir.path == f.path, ic) { go(f) }
                    }
                    W7NavTitle(tr("Libraries", "Kütüphaneler"))
                    listOf(
                        Triple(tr("Documents", "Belgeler"), Environment.DIRECTORY_DOCUMENTS, "fd_docs"), Triple(tr("Music", "Müzik"), Environment.DIRECTORY_MUSIC, "fd_music"),
                        Triple(tr("Pictures", "Resimler"), Environment.DIRECTORY_PICTURES, "fd_pictures"), Triple(tr("Videos", "Videolar"), Environment.DIRECTORY_MOVIES, "fd_videos"),
                    ).forEach { (l, n, ic) -> val f = File(root, n); if (f.exists()) W7Nav(l, !computer && dir.path == f.path, ic) { go(f) } }
                    W7NavTitle(tr("Computer", "Bilgisayar"))
                    W7Nav(tr("This PC", "Bu bilgisayar"), computer, "l_pc") { goComputer() }
                    W7Nav(tr("Internal storage", "Dahili depolama"), !computer && dir.path == root.path, "dv_internal") { go(root) }
                }
                if (wide) Box(Modifier.width(1.dp).fillMaxHeight().background(W7_EDGE))
                if (computer) W7Computer(root, Modifier.weight(1f).fillMaxHeight()) { go(it) }
                // the details list
                else Column(Modifier.weight(1f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().background(W7_HEAD).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr("Name", "Ad"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(tr("Date modified", "Değiştirme tarihi"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.width(118.dp))
                        if (cols) Text(tr("Type", "Tür"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.width(110.dp))
                        if (cols) Text(tr("Size", "Boyut"), color = Color(0xFF4C607A), fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFD5DDE8)))
                    // an empty folder says so; one Android keeps from the apps (Android/data and others) says that, instead of looking empty by mistake
                    if (shown.isEmpty() && query.isBlank()) Text(if (refused) tr("Android does not let apps list this folder", "Android bu klasörün içeriğini uygulamalara göstermez") else tr("This folder is empty.", "Bu klasör boş."), color = Color(0xFF6B7B93), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(shown, key = { it.path }) { f ->
                            val isDir = f.isDirectory
                            val on = f.path == sel
                            Row(
                                Modifier.fillMaxWidth().then(if (on) Modifier.background(W7_SEL).border(1.dp, Color(0xFF7DA2CE)) else Modifier)
                                    .fileRow({ fr.requestFocus(); sel = f.path }, { sel = f.path; menuFor = f }, onDouble = { open(f) }).padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    // a picture, a video or a song shows its own picture (the preview), the other files the picture of their kind
                                    val thumb = if (isDir) null else (com.umutk.pulse.features.exThumb(f) ?: com.umutk.pulse.features.exPreview(f))
                                    if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.size(26.dp).clip(RoundedCornerShape(3.dp)).border(1.dp, Color(0x33000000), RoundedCornerShape(3.dp)), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                                    else W7Pic(if (isDir) w7FolderKey(f, root) else w7FileKey(f), 22.dp, if (isDir) "folder" else com.umutk.pulse.features.fileTypeKey(f))
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

/** The round back and forward button: the blue orb of the Windows 7 pack at full size, the pressed one while it is held, faded when it cannot be used. */
@Composable
private fun W7Round(enabled: Boolean, forward: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val down by src.collectIsPressedAsState()
    val base = if (forward) "forward" else "back"
    val pic = com.umutk.pulse.features.ThemeIcons.bitmap(ctx, "win7", base)
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        if (pic != null) androidx.compose.foundation.Image(pic, null, Modifier.fillMaxSize().padding(if (down) 3.dp else 1.dp).graphicsLayer { alpha = if (enabled) 1f else .38f })
        else PIcon("back", Color(0xFF5F86B6), 22.dp)
    }
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
private fun W7Nav(label: String, on: Boolean, icon: String = "folder", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (on) Modifier.background(W7_SEL) else Modifier).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        W7Pic(icon, 18.dp)
        Text(label, color = Color(0xFF1A1A1A), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp))
    }
}


/** A Windows 7 picture of the icon pack by its key (the plain folder when the key has none). */
@Composable
private fun W7Pic(key: String, size: androidx.compose.ui.unit.Dp, fallback: String = "folder") {
    val ctx = LocalContext.current
    val b = com.umutk.pulse.features.ThemeIcons.bitmap(ctx, "win7", key)
    if (b != null) androidx.compose.foundation.Image(b, null, Modifier.size(size)) else PIcon(fallback, W7_FOLDER, size)
}

/** The folders of the phone's main places have the picture of their library; the others are plain folders. */
private fun w7FolderKey(f: File, root: File): String = if (f.parentFile?.path != root.path) "folder" else when (f.name) {
    "Download", "Downloads" -> "fd_downloads"; "Documents" -> "fd_docs"; "Music" -> "fd_music"; "Pictures" -> "fd_pictures"
    "Movies", "Videos" -> "fd_videos"; "DCIM" -> "fd_camera"; else -> "folder"
}

private fun w7FileKey(f: File): String = when (f.extension.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif" -> "ft_image"
    "mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts" -> "ft_video"
    "mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "wma" -> "ft_audio"
    "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> "ft_archive"
    "xls", "xlsx", "ods", "csv" -> "ft_table"
    "apk", "xapk", "apks" -> "ft_apk"
    "txt", "log", "md", "json", "xml" -> "ft_text"
    else -> "ft_doc"
}

/** One drive or partition of Computer. */
private class W7Drive(val name: String, val dir: File, val icon: String)

/** The Computer page: the hard disk drives (the shared storage, the data and the system partitions) and the removable storage, each with its bar of used space. */
@Composable
private fun W7Computer(root: File, modifier: Modifier, open: (File) -> Unit) {
    val ctx = LocalContext.current
    val (disks, removable) = remember {
        val hd = listOf(W7Drive(tr("Internal storage", "Dahili depolama"), root, "dv_internal"),
            W7Drive(tr("Data (apps)", "Veri (uygulamalar)"), Environment.getDataDirectory(), "dv_data"),
            W7Drive(tr("System", "Sistem"), Environment.getRootDirectory(), "dv_system"))
        val rm = runCatching {
            val sm = ctx.getSystemService(android.os.storage.StorageManager::class.java)
            sm.storageVolumes.filter { it.isRemovable && it.state == android.os.Environment.MEDIA_MOUNTED }.mapNotNull { v ->
                val d = if (android.os.Build.VERSION.SDK_INT >= 30) v.directory else null
                d?.let { W7Drive(v.getDescription(ctx) ?: tr("SD card", "SD kart"), it, if (v.getDescription(ctx)?.contains("USB", true) == true) "dv_usb" else "dv_sd") }
            }
        }.getOrDefault(emptyList())
        hd to rm
    }
    Column(modifier.background(Color.White).verticalScroll(rememberScrollState()).padding(8.dp)) {
        val picked = remember { mutableStateOf<String?>(null) }
        W7DriveGroup(tr("Hard Disk Drives (${disks.size})", "Sabit Disk Sürücüleri (${disks.size})"), disks, picked, open)
        if (removable.isNotEmpty()) W7DriveGroup(tr("Devices with Removable Storage (${removable.size})", "Çıkarılabilir Depolama Aygıtları (${removable.size})"), removable, picked, open)
    }
}

@Composable
private fun W7DriveGroup(title: String, drives: List<W7Drive>, picked: androidx.compose.runtime.MutableState<String?>, open: (File) -> Unit) {
    Text(title, color = W7_INK, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFD5DDE8)))
    drives.forEach { d ->
        val (total, free) = remember(d.dir.path) { runCatching { val st = android.os.StatFs(d.dir.path); st.blockCountLong * st.blockSizeLong to st.availableBlocksLong * st.blockSizeLong }.getOrDefault(0L to 0L) }
        val used = if (total > 0) 1f - free.toFloat() / total else 0f
        Row(Modifier.fillMaxWidth().then(if (picked.value == d.dir.path) Modifier.background(W7_SEL).border(1.dp, Color(0xFF7DA2CE)) else Modifier)
            .fileRow({ picked.value = d.dir.path }, { picked.value = d.dir.path }, onDouble = { open(d.dir) }).padding(vertical = 8.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            W7Pic(d.icon, 44.dp)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(d.name, color = Color(0xFF1A1A1A), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(top = 4.dp, bottom = 3.dp).fillMaxWidth().height(13.dp).background(Color(0xFFE6E6E6), RoundedCornerShape(2.dp)).border(1.dp, Color(0xFFBCBCBC), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(used.coerceIn(0f, 1f)).background(
                        Brush.verticalGradient(if (used > .9f) listOf(Color(0xFFE8786F), Color(0xFFC13A30)) else listOf(Color(0xFF6FB4F2), Color(0xFF2A78D6))), RoundedCornerShape(2.dp)))
                }
                if (total > 0) Text(tr("${com.umutk.pulse.features.exSize(free)} free of ${com.umutk.pulse.features.exSize(total)}", "${com.umutk.pulse.features.exSize(total)} içinden ${com.umutk.pulse.features.exSize(free)} boş"), color = Color(0xFF6B7B93), fontSize = 11.sp)
            }
        }
    }
}
