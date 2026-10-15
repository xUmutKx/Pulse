package com.umutk.pulse.features

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.ImageDecoder
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.umutk.pulse.BarMeter
import com.umutk.pulse.bevel
import com.umutk.pulse.xpCaption
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.umutk.pulse.FluentPaths
import com.umutk.pulse.PButton
import com.umutk.pulse.WinAlert
import com.umutk.pulse.ThemedMenu
import com.umutk.pulse.ThemedMenuItem
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.Guard
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.WinAlert
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PHead
import com.umutk.pulse.tr
import com.umutk.pulse.Opt
import com.umutk.pulse.PIcon
import com.umutk.pulse.BinIcon
import com.umutk.pulse.MoreIcon
import androidx.compose.material.icons.outlined.Description
import com.umutk.pulse.Shell
import com.umutk.pulse.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Files cut or copied with the copy and move icons, waiting for the paste icon. */
private class Clip(val files: List<File>, val move: Boolean)

/** The last delete or move, kept in memory: each pair is (where the file is now, where it came from). */
private class ExUndo(val moves: List<Pair<File, File>>)

/** What the properties dialog shows; the hashes are "-" for folders and unreadable files. */
private class ExProps(val size: Long, val modified: String, val count: Int, val md5: String, val sha: String)

/** Bookmarks and recent files live in the shared "p" preferences, one path per line. */
private const val EX_MARKS = "ex_marks"
private const val EX_RECENT = "ex_recent"
private const val EX_FREQ = "ex_freq"
private const val EX_SORT_ALL = "ex_sort_all"
private const val EX_SORT_DIRS = "ex_sort_dirs"

/** Set by an intent that opens a folder (Pulse chosen as the file manager): Files then opens full screen. */
object FilesLaunch { var full by mutableStateOf(false) }

/** The side page for files and the tools that read them: the explorer, the folder tree, file types, cache cleaning and root. Full screen (Settings > Functions) puts it in its own window with the look's caption buttons. */
@Composable
fun FilesPage() {
    var tab by rememberSaveable { mutableStateOf("files") }
    val top = remember { Environment.getExternalStorageDirectory() }
    val back: () -> Unit = { tab = "files" }
    var full by rememberSaveable { mutableStateOf(Opt.filesFull || FilesLaunch.full) }
    LaunchedEffect(FilesLaunch.full) { if (FilesLaunch.full) { full = true; FilesLaunch.full = false } }
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            com.umutk.pulse.StorageAccessCard()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "tree" -> FolderTreeScreen(top, back)
                    "types" -> TypeBreakdownScreen(top, back)
                    "cache" -> CacheCleanerScreen(back)
                    else -> ExplorerTab(full, { tab = it }) { full = !full }
                }
            }
        }
    }
    if (!full) { body(); return }
    // closing leaves the page for the start page; minimise and maximise both give the window back to the app
    val close = { full = false; com.umutk.pulse.widgets.WidgetOpen.page = Opt.startPage }
    val w = LocalWindowLook.current
    val classic = w.tabs && w.title != null
    val ink = if (classic) w.titleText else w.text
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(w.bg).statusBarsPadding().navigationBarsPadding()
            .then(if (w.id == "win95") Modifier.bevel(true) else Modifier)) {
            Row(Modifier.fillMaxWidth().height(if (w.id == "win95") 22.dp else 34.dp)
                .then(if (classic) Modifier.background(w.title!!) else Modifier.background(w.panel))
                .padding(start = 10.dp, end = if (classic) 3.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Files", Modifier.weight(1f), color = ink, fontSize = 12.sp, fontWeight = if (classic) androidx.compose.ui.text.font.FontWeight.Bold else null)
                ExCaption(w, ink, { full = false }, { full = false }, close)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { body() }
        }
    }
}

/** Minimise, maximise and close in the look's style: 95 bevelled, XP glossy squares, 7 glass, 10 and 11 flat, Material round. */
@Composable
private fun ExCaption(w: com.umutk.pulse.PTheme, ink: Color, onMin: () -> Unit, onMax: () -> Unit, onClose: () -> Unit) {
    val id = w.id
    // kind 0 minimise, 1 maximise, 2 close
    @Composable
    fun cap(kind: Int, size: androidx.compose.ui.unit.DpSize, deco: Modifier, glyph: Color, onClick: () -> Unit) {
        Box(Modifier.size(size).then(deco).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            if (id == "xp") return@Box
            Canvas(Modifier.size(if (id == "win95") 8.dp else 10.dp)) {
                val st = if (id == "win95") 1.5.dp.toPx() else 1.dp.toPx(); val a = this.size.width
                when (kind) {
                    0 -> drawLine(glyph, Offset(0f, a * .85f), Offset(a, a * .85f), st)
                    1 -> drawRect(glyph, Offset(st / 2, st / 2), androidx.compose.ui.geometry.Size(a - st, a - st), style = androidx.compose.ui.graphics.drawscope.Stroke(st))
                    else -> { drawLine(glyph, Offset(0f, 0f), Offset(a, a), st); drawLine(glyph, Offset(a, 0f), Offset(0f, a), st) }
                }
            }
        }
    }
    val acts = listOf(onMin, onMax, onClose)
    when (id) {
        "win95" -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { (0..2).forEach { k ->
            if (k == 2) Spacer(Modifier.width(2.dp))
            cap(k, androidx.compose.ui.unit.DpSize(16.dp, 14.dp), Modifier.background(Color(0xFFC0C0C0)).bevel(true), Color.Black, acts[k]) } }
        "xp" -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { (0..2).forEach { k ->
            cap(k, androidx.compose.ui.unit.DpSize(21.dp, 21.dp), Modifier.drawBehind { xpCaption(k == 2, k == 1) }, Color.Transparent, acts[k]) } }
        "win7" -> Row(Modifier.offset(y = (-3).dp)) { (0..2).forEach { k ->
            val shape = RoundedCornerShape(bottomStart = if (k == 0) 4.dp else 0.dp, bottomEnd = if (k == 2) 4.dp else 0.dp)
            cap(k, androidx.compose.ui.unit.DpSize(if (k == 2) 44.dp else 27.dp, 19.dp),
                Modifier.clip(shape).background(if (k == 2) Brush.verticalGradient(listOf(Color(0xFFE9A394), Color(0xFFC9442B))) else Brush.verticalGradient(listOf(Color(0xFFDCE8F6), Color(0xFF9DBBDD)))).border(1.dp, Color(0xFF3A4A5E), shape),
                if (k == 2) Color.White else Color(0xFF1B2B45), acts[k]) } }
        "win10", "win11" -> Row { (0..2).forEach { k ->
            cap(k, androidx.compose.ui.unit.DpSize(46.dp, 34.dp), Modifier, ink, acts[k]) } }
        else -> Row(Modifier.padding(end = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) { (0..2).forEach { k ->
            cap(k, androidx.compose.ui.unit.DpSize(36.dp, 34.dp), Modifier.clip(androidx.compose.foundation.shape.CircleShape), ink, acts[k]) } }
    }
}

/** The file manager on shared storage: browse, search, select, sort, open, share, zip, bookmarks, properties, finders and undo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerTab(full: Boolean, onTab: (String) -> Unit, onToggleFull: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val top = remember { Environment.getExternalStorageDirectory() }
    var here by rememberSaveable { mutableStateOf(top.path) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Pair<String, File>?>(null) }
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<File?>(null) }
    var propsOf by remember { mutableStateOf<File?>(null) }
    var finder by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    // sort: one order for every folder, unless a folder has its own (folderSort); both are kept on the phone
    var sortAll by rememberSaveable { mutableStateOf(exReadInt(ctx, EX_SORT_ALL, 0)) }
    var folderSort by remember { mutableStateOf(exReadSortMap(ctx)) }
    var drawer by remember { mutableStateOf(false) }
    var freq by remember { mutableStateOf(exRead(ctx, EX_FREQ)) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuOf by remember { mutableStateOf<File?>(null) }
    var archiveOf by remember { mutableStateOf<File?>(null) }
    var apkChoice by remember { mutableStateOf<File?>(null) }
    var archiveList by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var showSizes by rememberSaveable { mutableStateOf(true) }
    var recentView by rememberSaveable { mutableStateOf(false) }
    var marks by remember { mutableStateOf(exRead(ctx, EX_MARKS)) }
    var recents by remember { mutableStateOf(exRead(ctx, EX_RECENT)) }
    var undo by remember { mutableStateOf<ExUndo?>(null) }
    var found by remember { mutableStateOf<List<File>?>(null) }
    // files waiting for the delete confirm box; the box calls deleteNow
    var bulk by remember { mutableStateOf<List<File>?>(null) }
    // the bin (when on) keeps the files and Undo brings them back
    val deleteNow: (List<File>) -> Unit = { files ->
        sel = emptySet()
        scope.launch {
            val u = withContext(Dispatchers.IO) { if (Opt.recycle) exTrash(top, files) else { files.forEach { it.deleteRecursively() }; null } }
            undo = u
            note = if (u == null && Opt.recycle) "Could not delete" else ""
            refresh++
        }
    }
    val dir = File(here)
    val sort = folderSort[here] ?: sortAll
    val (total, free) = remember(refresh) { exStorage(top) }
    // folders the app cannot read (/data, ...) are listed through su or Shizuku when either is there
    val canRoot = Shell.root == true || Shell.shizuku == 2
    val viaRoot by produceState<List<File>?>(null, here, refresh, canRoot) {
        value = if (canRoot && dir.listFiles() == null) withContext(Dispatchers.IO) { exRootList(dir) } else null
    }
    val listing = remember(here, refresh, showHidden, sort, viaRoot) {
        exSort((dir.listFiles()?.toList() ?: viaRoot).orEmpty().filter { showHidden || !it.name.startsWith(".") }, sort)
    }
    LaunchedEffect(query, here, refresh, showHidden) {
        found = if (query.isBlank()) null else withContext(Dispatchers.IO) { exFind(dir, query.trim(), showHidden) }
    }
    val shown: List<File> = when {
        recentView -> recents.map { File(it) }.filter { it.exists() }
        found != null -> found.orEmpty()
        else -> listing
    }
    val bookmarks = marks.filter { File(it).isDirectory }
    val picked = sel.map { File(it) }

    fun go(p: String) { freq = exBump(ctx, p); here = p; sel = emptySet(); query = ""; recentView = false }
    // Back steps out of the current view first (selection, search, recents), then up one folder; only at the top does it leave the page
    androidx.activity.compose.BackHandler(enabled = sel.isNotEmpty() || query.isNotBlank() || recentView || dir != top) {
        when {
            sel.isNotEmpty() -> sel = emptySet()
            query.isNotBlank() -> query = ""
            recentView -> recentView = false
            else -> go((dir.parentFile ?: top).path)
        }
    }
    // an APK asks first: view its contents, or install it (a tap on other files opens them as before)
    fun openFile(f: File) {
        if (f.extension.equals("apk", true)) { apkChoice = f; return }
        recents = exAddRecent(ctx, f); Downloads.openFile(ctx, f)
    }
    fun toggle(p: String) { sel = if (p in sel) sel - p else sel + p }
    fun mark(p: String) { marks = if (p in marks) marks - p else marks + p; exWrite(ctx, EX_MARKS, marks) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clickable { drawer = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Menu, null, Modifier.size(24.dp), T.text) }
            Spacer(Modifier.weight(1f))
            // the other file tools sit in the header as icons
            listOf("tree" to "sizes", "types" to "filetypes", "cache" to "clean").forEach { (k, icon) ->
                Box(Modifier.size(36.dp).clickable { onTab(k) }, contentAlignment = Alignment.Center) { FluentGlyph(icon, T.text, 22.dp) }
            }
            if (!full) Box(Modifier.size(36.dp).clickable(onClick = onToggleFull), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.OpenInFull, null, Modifier.size(20.dp), T.sub) }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PIcon("disk", T.sub, 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("${Guard.gb(total - free)} used, ${Guard.gb(free)} free", color = T.text, fontSize = 13.sp)
            }
            BarMeter(if (total > 0) (total - free).toFloat() / total else 0f, Look.accent, h = 8.dp)
        }
        if (bookmarks.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bookmarks.forEach { p -> PChip(here == p, if (p == top.path) "Storage" else File(p).name) { go(p) } }
        }
        PField(query, { query = it }, "Search", Modifier.fillMaxWidth())
        PCard {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ExAct(listOf("sortName", "sortSize", "sortDate")[sort / 2], if (here in folderSort) Look.accent else T.sub, if (sort % 2 == 1) 180f else 0f) { sortMenu = true }
                    ExSortMenu(sortMenu, sort, here in folderSort, { sortMenu = false }, { mode ->
                        if (here in folderSort) { folderSort = folderSort + (here to mode); exWriteSortMap(ctx, folderSort) }
                        else { sortAll = mode; exWriteInt(ctx, EX_SORT_ALL, mode) }
                        sortMenu = false
                    }, { on ->
                        folderSort = if (on) folderSort + (here to sort) else folderSort - here
                        exWriteSortMap(ctx, folderSort)
                        sortMenu = false
                    })
                }
                ExAct(if (showHidden) "eyeOn" else "eyeOff", if (showHidden) Look.accent else T.sub) { showHidden = !showHidden }
                ExAct("sizes", if (showSizes) Look.accent else T.sub) { showSizes = !showSizes }
                ExAct("recent", if (recentView) Look.accent else T.sub) { recentView = !recentView; query = "" }
                ExAct("emptyFolder", T.sub) { finder = "empty" }
                ExAct("bigFile", T.sub) { finder = "large" }
                ExAct("dupes", T.sub) { finder = "dupes" }
                undo?.let { u ->
                    ExAct("undo", Look.accent) {
                        scope.launch {
                            note = withContext(Dispatchers.IO) { exUndo(u) }
                            undo = null
                            refresh++
                        }
                    }
                }
            }
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dir != top) Box(Modifier.clickable { go((dir.parentFile ?: top).path) }) { PIcon("chevron", T.text, 22.dp, Modifier.rotate(180f)) }
                Text(dir.path.removePrefix(top.path).ifEmpty { "/" }, Modifier.weight(1f).padding(horizontal = 8.dp), color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ExAct(if (dir.path in marks) "starOn" else "star", if (dir.path in marks) Look.accent else T.sub) { mark(dir.path) }
                ExAct("home", T.text) { go(top.path) }
                PButton("New folder") { name = ""; dialog = "newfolder" to dir }
                clip?.let { c ->
                    ExAct("paste", Look.accent) {
                        scope.launch {
                            val moved = mutableListOf<Pair<File, File>>()
                            note = withContext(Dispatchers.IO) { paste(c, dir, moved) }
                            if (moved.isNotEmpty()) undo = ExUndo(moved)
                            if (c.move) clip = null
                            refresh++
                        }
                    }
                }
            }
        }
        if (sel.isNotEmpty()) PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExAct("close", T.sub) { sel = emptySet() }
                Text("${sel.size} selected", Modifier.weight(1f).padding(start = 4.dp), color = T.text, fontSize = 14.sp)
                ExAct("copy", T.text) { clip = Clip(picked, false); sel = emptySet() }
                ExAct("move", T.text) { clip = Clip(picked, true); sel = emptySet() }
                ExAct("zip", Look.accent) {
                    scope.launch {
                        note = withContext(Dispatchers.IO) { exZip(picked, dir) }
                        sel = emptySet()
                        refresh++
                    }
                }
                ExAct("delete", T.text) { bulk = picked }
            }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        if (shown.isEmpty()) Text(if (recentView) "No recent files" else "No files here", color = T.sub, fontSize = 13.sp)
        shown.forEach { f ->
            val isMark = f.path in marks
            val isPicked = f.path in sel
            // folders keep their card; files are plain rows in a list
            val rowBody: @Composable () -> Unit = {
                // one row: the name on the left, the three dots (menu) on the right of the same line
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).combinedClickable(
                    onClick = { if (sel.isNotEmpty()) toggle(f.path) else if (f.isDirectory) go(f.path) else openFile(f) },
                    onLongClick = { toggle(f.path) }), verticalAlignment = Alignment.CenterVertically) {
                    val thumb = if (f.isDirectory || isPicked) null else exThumb(f)
                    if (thumb != null) Image(thumb, null, Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                    else ExIcon(if (isPicked) "check" else if (f.isDirectory) "folder" else "filetypes", Look.accent, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when {
                            found != null -> exRel(f, top)
                            !f.isDirectory && showSizes -> exSize(f.length())
                            else -> ""
                        }
                        if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Box {
                    Box(Modifier.size(36.dp).clickable { menuOf = f }, contentAlignment = Alignment.Center) { MoreIcon(T.sub, 20.dp) }
                    val actions = buildList<Triple<String, String, () -> Unit>> {
                        add(Triple("Open", "view", { if (f.isDirectory) go(f.path) else openFile(f) }))
                        if (!f.isDirectory) add(Triple("Open with", "openWith", { if (exHandOver(ctx, f, false)) recents = exAddRecent(ctx, f) else note = "Could not open ${f.name}" }))
                        add(Triple("Cut", "move", { clip = Clip(listOf(f), true) }))
                        add(Triple("Copy", "copy", { clip = Clip(listOf(f), false) }))
                        add(Triple("Delete", "delete", { bulk = listOf(f) }))
                        add(Triple("Rename", "rename", { name = f.name; dialog = "rename" to f }))
                        add(Triple("Compress", "zip", { scope.launch { note = withContext(Dispatchers.IO) { exZip(listOf(f), dir) }; refresh++ } }))
                        if (!f.isDirectory) add(Triple("Share", "share", { if (!exHandOver(ctx, f, true)) note = "Could not share ${f.name}" }))
                        add(Triple("Copy path", "document", { exCopy(ctx, f.path); note = "Path copied" }))
                        if (f.isDirectory) {
                            add(Triple(if (f.path in marks) "Remove bookmark" else "Add bookmark", "bookmark", { mark(f.path) }))
                            add(Triple("Create shortcut", "link", { mark(f.path) }))
                        }
                        if (f.extension.equals("zip", true)) add(Triple("View archive", "zip", { archiveOf = f }))
                        if (f.extension.equals("zip", true)) add(Triple("Extract", "extract", { scope.launch { note = withContext(Dispatchers.IO) { unzip(f, dir) }; refresh++ } }))
                        add(Triple("Properties", "info", { propsOf = f }))
                    }
                    val w = LocalWindowLook.current
                    ThemedMenu(menuOf == f, { menuOf = null }) {
                        actions.forEach { (label, key, act) ->
                            ThemedMenuItem(label, { menuOf = null; act() }, leading = { ExIcon(if (T.id == "win11") W11_ACTION[label] ?: key else key, com.umutk.pulse.LocalMenuStyle.current.sub, 18.dp) })
                        }
                    }
                }
                }
            }
            if (f.isDirectory) PCard(Modifier.fillMaxWidth()) { rowBody() } else Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { rowBody() }
        }
    }
    if (drawer) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { drawer = false })
        Column(Modifier.fillMaxHeight().width(280.dp).background(T.panel).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            val pick: (String) -> Unit = { go(it); drawer = false }
            ExDrawerRow("disk", tr("Internal storage", "Dahili depolama"), here == top.path) { pick(top.path) }
            if (canRoot) ExDrawerRow("folder", tr("Root (/)", "Kök (/)"), here == "/") { pick("/") }
            else ExDrawerRow("folder", tr("Root: not available", "Kök: yok"), false, dim = true) { }
            if (marks.isNotEmpty()) {
                ExDrawerHead(tr("Bookmarks", "Yer imleri"))
                marks.forEach { p -> ExDrawerRow("bookmark", File(p).name.ifEmpty { "/" }, here == p) { pick(p) } }
            }
            // DCIM and Download are always there; the folders opened most often follow
            val defaults = listOf(File(top, "DCIM").path, File(top, "Download").path)
            val often = defaults + freq.mapNotNull { l -> l.substringAfterLast('|').toIntOrNull()?.let { l.substringBeforeLast('|') to it } }
                .sortedByDescending { it.second }.map { it.first }.filter { it !in defaults }.take(6)
            ExDrawerHead(tr("Frequent", "Sık kullanılanlar"))
            often.forEach { p -> ExDrawerRow("history", File(p).name.ifEmpty { "/" }, here == p) { pick(p) } }
        }
    }
    }

    viewing?.let { f -> FileViewer(f, { openFile(it) }) { viewing = null } }

    apkChoice?.let { a ->
        WinAlert(onDismissRequest = { apkChoice = null }, title = { Text(a.name) },
            text = { Text("View the contents, or install this app?", color = T.sub, fontSize = 13.sp) },
            confirmButton = { TextButton({ archiveOf = a; apkChoice = null }) { Text("View") } },
            dismissButton = { TextButton({
                apkChoice = null; recents = exAddRecent(ctx, a)
                if (!exHandOver(ctx, a, false)) note = "Could not install ${a.name}"
            }) { Text("Install") } })
    }

    archiveOf?.let { z ->
        LaunchedEffect(z) { archiveList = null; archiveList = withContext(Dispatchers.IO) { exArchiveList(z) } }
        val list = archiveList
        WinAlert(onDismissRequest = { archiveOf = null }, title = { Text("${z.name}: ${list?.size ?: 0} items") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    when {
                        list == null -> Text("Reading...", color = T.sub, fontSize = 13.sp)
                        list.isEmpty() -> Text("Empty, or not a zip file", color = T.sub, fontSize = 13.sp)
                        else -> list.forEach { (n, size) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(n, Modifier.weight(1f), color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (size >= 0) Text(Guard.gb(size), color = T.sub, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ archiveOf = null }) { Text("Close") } }, dismissButton = null)
    }

    propsOf?.let { f ->
        var p by remember(f) { mutableStateOf<ExProps?>(null) }
        LaunchedEffect(f) { p = withContext(Dispatchers.IO) { exProps(f) } }
        WinAlert(onDismissRequest = { propsOf = null },
            title = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                val d = p
                if (d == null) Text("Measuring…", color = T.sub, fontSize = 13.sp)
                else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val rows = buildList<Pair<String, String>> {
                        add("Path" to f.path)
                        add("Size" to Guard.gb(d.size))
                        add("Modified" to d.modified)
                        if (f.isDirectory) add("Items" to d.count.toString())
                        else { add("MD5" to d.md5); add("SHA-256" to d.sha) }
                    }
                    InfoRows(rows)
                }
            },
            confirmButton = { TextButton({ propsOf = null }) { Text("Close") } })
    }

    finder?.let { kind ->
        var hits by remember(kind, here) { mutableStateOf<List<File>?>(null) }
        LaunchedEffect(kind, here) { hits = withContext(Dispatchers.IO) { exFinder(kind, dir) } }
        WinAlert(onDismissRequest = { finder = null },
            title = { Text(when (kind) { "empty" -> "Empty folders"; "large" -> "Largest files"; else -> "Same names" }) },
            text = {
                val h = hits
                when {
                    h == null -> Text("Searching…", color = T.sub, fontSize = 13.sp)
                    h.isEmpty() -> Text("Nothing found", color = T.sub, fontSize = 13.sp)
                    else -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        h.forEach { f ->
                            Column(Modifier.fillMaxWidth().clickable { go(f.parentFile?.path ?: top.path); finder = null }.padding(vertical = 6.dp)) {
                                Text(f.name, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(exRel(f, top) + (if (f.isDirectory) "" else "  ·  " + Guard.gb(f.length())), color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ finder = null }) { Text("Close") } })
    }


    bulk?.let { files ->
        WinAlert(onDismissRequest = { bulk = null },
            title = { Text(if (files.size > 1) "Delete Items" else if (files[0].isDirectory) "Delete Folder" else "Delete File") },
            text = { Text(if (Opt.recycle) (if (files.size == 1) tr("Move this item to the Recycle Bin?", "Bu öğe Çöp Kutusuna taşınsın mı?") else tr("Move these ${files.size} items to the Recycle Bin?", "Bu ${files.size} öğe Çöp Kutusuna taşınsın mı?"))
                else (if (files.size == 1) tr("Are you sure you want to permanently delete this item?", "Bu öğeyi kalıcı olarak silmek istediğinizden emin misiniz?") else tr("Are you sure you want to permanently delete these ${files.size} items?", "Bu ${files.size} öğeyi kalıcı olarak silmek istediğinizden emin misiniz?")), fontSize = 13.sp) },
            // the Windows looks draw their own button, so the red text stays on Material only
            confirmButton = { TextButton({ bulk = null; deleteNow(files) }) { Text("Delete", color = if (com.umutk.pulse.LocalWindowLook.current.id in com.umutk.pulse.winButtonLooks) Color.Unspecified else Color(0xFFC42B1C)) } },
            dismissButton = { TextButton({ bulk = null }) { Text(tr("Cancel", "İptal")) } }, iconKey = if (Opt.recycle) "recycle" else "close")
    }

    dialog?.let { (kind, f) ->
        WinAlert(onDismissRequest = { dialog = null },
            title = { Text(if (kind == "rename") "Rename" else "New folder") },
            text = { PField(name, { name = it }, "Name", Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({
                val target = if (kind == "rename") File(f.parentFile, name.trim()) else File(f, name.trim())
                val ok = name.isNotBlank() && !target.exists() && runCatching {
                    if (kind == "rename") f.renameTo(target) else target.mkdirs()
                }.getOrDefault(false)
                note = if (ok) "" else "Could not use that name"
                dialog = null
                refresh++
            }) { Text("OK") } },
            dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } })
    }
}

@Composable
private fun ExDrawerHead(t: String) = Text(t, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), color = T.sub, fontSize = 12.sp)

@Composable
private fun ExDrawerRow(icon: String, label: String, on: Boolean, dim: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(if (on) Look.accent.copy(alpha = .16f) else Color.Transparent).clickable(enabled = !dim, onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        ExIcon(icon, if (dim) T.sub else if (on) Look.accent else T.text, 20.dp)
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (dim) T.sub else T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An entry listed through su: the app itself cannot stat it, so the folder flag comes from the "/" that ls -p adds. */
private class RootFile(path: String, private val dir: Boolean) : File(path) {
    override fun isDirectory() = dir
    override fun isFile() = !dir
    override fun exists() = true
    override fun length() = 0L
    override fun lastModified() = 0L
}

private fun exRootList(dir: File): List<File> {
    val q = dir.path.replace("'", "'\\''")
    return Shell.run("ls -1Ap '$q'").lines().filter { it.isNotBlank() && !it.startsWith("ls:") }.map { RootFile(File(dir, it.trimEnd('/')).path, it.endsWith("/")) }
}

/** Counts one more visit to [p] ("path|count" lines) and returns the list. */
private fun exBump(ctx: Context, p: String): List<String> {
    val m = exRead(ctx, EX_FREQ).associate { it.substringBeforeLast('|') to (it.substringAfterLast('|').toIntOrNull() ?: 0) }.toMutableMap()
    m[p] = (m[p] ?: 0) + 1
    val l = m.entries.sortedByDescending { it.value }.take(40).map { "${it.key}|${it.value}" }
    exWrite(ctx, EX_FREQ, l)
    return l
}

/** One tappable icon in a row or bar; [rot] turns it (the sort icon turns for descending). */
@Composable
private fun ExAct(k: String, tint: Color, rot: Float = 0f, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 5.dp).clickable(onClick = onClick)) { ExIcon(k, tint, 20.dp, rot) }
}

/** Material outline icons in the Material looks; the Windows looks use the nearest Fluent glyph they already have. */
@Composable
private fun ExIcon(k: String, tint: Color, size: Dp = 22.dp, rot: Float = 0f) {
    val m = Modifier.rotate(rot)
    val v = EX_MATERIAL[k]
    val ctx = LocalContext.current
    // a pack that has the exact key (share, link, zip...) shows its own icon before the nearest-Fluent stand-in
    val own = !T.material && T.id != "win10" && k != "delete" && (ThemeIcons.bitmap(ctx, T.id, k) != null || ((T.id == "win11" || T.id == "win10") && FluentPaths.map[k] != null))
    if (k == "delete" && T.id != "win11") BinIcon(tint, size, m)
    else if (T.material && v != null) Icon(v, null, m.size(size), tint)
    else if (T.id == "win11") PIcon(k, tint, size, m)
    else PIcon(if (own) k else EX_FLUENT[k] ?: k, tint, size, m)
}

private val EX_MATERIAL: Map<String, ImageVector> = mapOf(
    "star" to Icons.Outlined.StarBorder, "bookmark" to Icons.Outlined.BookmarkBorder, "starOn" to Icons.Filled.Star, "check" to Icons.Outlined.CheckCircle,
    "sortName" to Icons.Outlined.SortByAlpha, "sortSize" to Icons.Outlined.DataUsage, "sortDate" to Icons.Outlined.Schedule,
    "eyeOn" to Icons.Outlined.Visibility, "eyeOff" to Icons.Outlined.VisibilityOff, "sizes" to Icons.Outlined.Straighten,
    "recent" to Icons.Outlined.History, "emptyFolder" to Icons.Outlined.FolderOff, "bigFile" to Icons.Outlined.SdStorage,
    "dupes" to Icons.Outlined.FileCopy, "undo" to Icons.Outlined.Undo, "home" to Icons.Outlined.Home,
    "share" to Icons.Outlined.Share, "openWith" to Icons.Outlined.OpenWith, "zip" to Icons.Outlined.FolderZip,
    "link" to Icons.Outlined.Link, "save" to Icons.Outlined.Save, "edit" to Icons.Outlined.Edit,
    "delete" to Icons.Outlined.Delete, "document" to Icons.Outlined.Description,
)

/** Win11 file menu: one distinct Fluent glyph per action. */
private val W11_ACTION = mapOf("Open" to "open", "Cut" to "cut", "Properties" to "properties", "View archive" to "openFolder", "Copy path" to "link", "Create shortcut" to "folderLink")

private val EX_FLUENT = mapOf(
    "star" to "heart", "starOn" to "heart", "bookmark" to "bookmark", "check" to "info", "sortName" to "details", "sortSize" to "disk", "sortDate" to "history",
    "eyeOn" to "view", "eyeOff" to "view", "sizes" to "disk", "recent" to "history", "emptyFolder" to "folder", "bigFile" to "disk",
    "dupes" to "copy", "undo" to "paste", "home" to "folder", "share" to "download", "openWith" to "run", "zip" to "extract",
    "link" to "copy", "save" to "rename", "edit" to "rename",
)

/** Recent files and bookmarks: one path per line under [key]. */
private fun exRead(ctx: Context, key: String): List<String> =
    ctx.getSharedPreferences("p", Context.MODE_PRIVATE).getString(key, "").orEmpty().lines().filter { it.isNotBlank() }

private fun exWrite(ctx: Context, key: String, list: List<String>) {
    ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putString(key, list.joinToString("\n")).apply()
}

/** Puts [f] at the top of the recent list (ten at most) and returns the new list. */
private fun exAddRecent(ctx: Context, f: File): List<String> {
    val list = (listOf(f.path) + exRead(ctx, EX_RECENT).filter { it != f.path }).take(10)
    exWrite(ctx, EX_RECENT, list)
    return list
}

private fun exReadInt(ctx: Context, key: String, def: Int): Int = ctx.getSharedPreferences("p", Context.MODE_PRIVATE).getInt(key, def)

private fun exWriteInt(ctx: Context, key: String, v: Int) {
    ctx.getSharedPreferences("p", Context.MODE_PRIVATE).edit().putInt(key, v).apply()
}

/** Folder path -> sort mode, one "path|mode" line each (the path may hold '|', the mode is after the last one). */
private fun exReadSortMap(ctx: Context): Map<String, Int> = exRead(ctx, EX_SORT_DIRS).mapNotNull { line ->
    val path = line.substringBeforeLast('|', "")
    val mode = line.substringAfterLast('|').toIntOrNull()
    if (path.isEmpty() || mode == null) null else path to mode
}.toMap()

private fun exWriteSortMap(ctx: Context, map: Map<String, Int>) = exWrite(ctx, EX_SORT_DIRS, map.map { "${it.key}|${it.value}" })

/** The sort popup: six orders, then the switch that gives this folder its own order. */
@Composable
private fun ExSortMenu(expanded: Boolean, current: Int, perFolder: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit, onPerFolder: (Boolean) -> Unit) {
    val w = LocalWindowLook.current
    val labels = listOf("Name A-Z", "Name Z-A", "Size smallest first", "Size largest first", "Date oldest first", "Date newest first")
    ThemedMenu(expanded, onDismiss) {
        labels.forEachIndexed { mode, label -> ThemedMenuItem(label, { onPick(mode) }, checked = current == mode) }
        ThemedMenuItem("Only for this folder", { onPerFolder(!perFolder) }, checked = perFolder)
    }
}

/** Three dots in a row: the file menu's button. */
@Composable
private fun ExDots(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        val r = size.minDimension * .08f
        // three dots stacked vertically, the usual "more" button
        for (i in 0..2) drawCircle(tint, r, Offset(size.width / 2, size.height * (.2f + .3f * i)))
    }
}

/** Folders first, then the order picked by the sort icon: name, size or date, each ascending then descending. */
private fun exSort(list: List<File>, mode: Int): List<File> {
    val key: Comparator<File> = when (mode / 2) {
        0 -> compareBy<File> { it.name.lowercase() }
        1 -> compareBy<File> { it.length() }
        else -> compareBy<File> { it.lastModified() }
    }
    val by = if (mode % 2 == 1) key.reversed() else key
    return list.sortedWith(compareBy<File> { !it.isDirectory }.then(by))
}

/** Entries of a zip file as (name, size); empty when the file cannot be read as a zip. */
private fun exArchiveList(f: File): List<Pair<String, Long>> = runCatching {
    java.util.zip.ZipFile(f).use { z -> z.entries().toList().map { it.name to it.size } }
}.getOrDefault(emptyList())

/** Names containing [q] under [root]; stops after 20 000 entries or 200 hits. Hidden names only when [hidden] is on. */
private fun exFind(root: File, q: String, hidden: Boolean): List<File> =
    root.walkTopDown().take(20_000).filter { it != root && (hidden || !it.name.startsWith(".")) && it.name.contains(q, ignoreCase = true) }.take(200).toList()

/** The results of the empty-folder, largest-file and same-name finders under [dir]. */
private fun exFinder(kind: String, dir: File): List<File> = when (kind) {
    "empty" -> dir.walkTopDown().take(50_000).filter { it != dir && it.isDirectory && it.listFiles()?.isEmpty() == true }.take(200).toList()
    "large" -> dir.walkTopDown().take(50_000).filter { it.isFile }.sortedByDescending { it.length() }.take(50).toList()
    else -> dir.walkTopDown().take(50_000).filter { it.isFile }.groupBy { it.name }.values.filter { it.size > 1 }.flatten().sortedBy { it.name }.take(200)
}

/** The folder of [f] as shown from the storage root, e.g. "/Download". */
private fun exRel(f: File, top: File): String = (f.parentFile?.path ?: top.path).removePrefix(top.path).ifEmpty { "/" }

/** KB under 1 MB, MB under 1 GB, GB above. */
private fun exSize(b: Long): String = when {
    b < 1_048_576L -> "%.0f KB".format(b / 1024.0)
    b < 1_073_741_824L -> "%.0f MB".format(b / 1_048_576.0)
    else -> "%.1f GB".format(b / 1_073_741_824.0)
}

private val EX_IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

/** A small decoded picture for an image file, or null for anything else or when the device cannot decode it. */
@Composable
private fun exThumb(f: File): ImageBitmap? {
    if (f.extension.lowercase() !in EX_IMAGE_EXT) return null
    val ctx = LocalContext.current
    val first = remember(f.path) { Thumbs.cached(f) }
    return produceState(first, f.path) { if (value == null) value = Thumbs.load(ctx, f) }.value
}

/** Total and free bytes of the shared storage; zeros when the system will not say. */
private fun exStorage(top: File): Pair<Long, Long> = runCatching {
    val s = StatFs(top.path)
    s.totalBytes to s.availableBytes
}.getOrDefault(0L to 0L)

/** Moves [files] into a trash folder at the storage root, replacing the previous trash, so Undo can bring them back. Null when nothing moved. */
private fun exTrash(top: File, files: List<File>): ExUndo? {
    val trash = File(top, ".pulse_trash").apply { mkdirs() }
    val stamp = System.currentTimeMillis()
    val moves = files.mapIndexedNotNull { i, f ->
        val t = File(trash, "$stamp-$i-${f.name}")
        if (f.renameTo(t)) { RecycleBin.remember(t, f); (t to f) } else { f.deleteRecursively(); null }
    }
    return if (moves.isEmpty()) null else ExUndo(moves)
}

/** Moves each file back to where it came from; a spot taken since then is skipped. Returns a note when some stayed put. */
private fun exUndo(u: ExUndo): String {
    var failed = 0
    for ((now, was) in u.moves) if (was.exists() || !now.renameTo(was)) failed++
    return if (failed == 0) "" else "$failed could not be put back"
}

/** Zips [files] into [dir]: one item gives its own name, several give "Archive". Returns a note on failure. */
private fun exZip(files: List<File>, dir: File): String {
    val base = if (files.size == 1) files[0].name else "Archive"
    var target = File(dir, "$base.zip")
    var n = 1
    while (target.exists()) target = File(dir, "$base ($n).zip").also { n++ }
    return runCatching {
        ZipOutputStream(target.outputStream().buffered()).use { zos ->
            for (f in files) {
                val root = f.parentFile ?: dir
                f.walkTopDown().forEach { e ->
                    val rel = e.relativeTo(root).path.replace(File.separatorChar, '/')
                    if (e.isDirectory) zos.putNextEntry(ZipEntry("$rel/")) else zos.putNextEntry(ZipEntry(rel))
                    if (e.isFile) e.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        ""
    }.getOrElse { target.delete(); "Could not zip" }
}

/** Sends [f] to a chooser: Share sends it, Open with opens it. Returns false when the file cannot be handed over. */
private fun exHandOver(ctx: Context, f: File, share: Boolean): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val mime = fileMime(f)
    val i = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    i.clipData = ClipData.newRawUri("", uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

private fun exCopy(ctx: Context, text: String) {
    ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("path", text))
}

/** Size, modified date and item count, and the MD5 and SHA-256 of a file. A folder's size counts everything inside it. */
private fun exProps(f: File): ExProps {
    val kids = if (f.isDirectory) f.walkTopDown().drop(1).toList() else emptyList()
    val size = if (f.isDirectory) kids.filter { it.isFile }.sumOf { it.length() } else f.length()
    val (md5, sha) = if (f.isFile) runCatching { exHash(f) }.getOrDefault("-" to "-") else ("-" to "-")
    val modified = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(f.lastModified()))
    return ExProps(size, modified, kids.size, md5, sha)
}

/** MD5 and SHA-256 in one pass over the file. */
private fun exHash(f: File): Pair<String, String> {
    val md5 = MessageDigest.getInstance("MD5")
    val sha = MessageDigest.getInstance("SHA-256")
    f.inputStream().buffered().use { ins ->
        val buf = ByteArray(64 * 1024)
        while (true) {
            val n = ins.read(buf)
            if (n < 0) break
            md5.update(buf, 0, n)
            sha.update(buf, 0, n)
        }
    }
    return md5.digest().hex() to sha.digest().hex()
}

private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/** Copies or moves the clipped files into [dir]; a name already there gets a number. Each rename goes into [moved] for Undo. Returns a note when something failed. */
private fun paste(c: Clip, dir: File, moved: MutableList<Pair<File, File>>): String {
    var failed = 0
    for (src in c.files) {
        var target = File(dir, src.name)
        var n = 1
        while (target.exists()) target = File(dir, "$n-${src.name}").also { n++ }
        val ok = runCatching {
            if (c.move && src.renameTo(target)) { moved += (target to src); true }
            else { src.copyRecursively(target, overwrite = false); if (c.move) src.deleteRecursively(); true }
        }.getOrDefault(false)
        if (!ok) failed++
    }
    return if (failed == 0) "" else "$failed could not be pasted"
}


/** Root: whether the phone grants it, the one root action (trim app caches), and what each app holds on disk. */
@Composable
fun RootPage() {
    val has = Shell.root == true
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    var done by remember { mutableStateOf(false) }
    LaunchedEffect(has) { if (has) apps = withContext(Dispatchers.IO) { Guard.appData() } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PIcon("info", if (has) Look.accent else T.sub, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (has) "Root is granted" else "No root on this phone", color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                if (has) Box(Modifier.clickable {
                    scope.launch {
                        withContext(Dispatchers.IO) { Shell.run("pm trim-caches 999G") }
                        done = true
                    }
                }) { PIcon("clean", Look.accent, 22.dp) }
            }
        }
        if (has) PCard {
            Text("Data per app", color = Look.accent, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            val a = apps
            if (a == null) Text("Measuring…", color = T.sub, fontSize = 13.sp)
            else a.take(25).forEach { (pkg, sz) ->
                Row { Text(pkg, color = T.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text(Guard.gb(sz), color = T.sub, fontSize = 12.sp) }
            }
        }
        if (done) Text("Caches trimmed", color = Look.accent, fontSize = 12.sp)
    }
}

private val TEXT_EXT = setOf("txt", "md", "json", "csv", "log", "xml", "html", "htm", "kt", "java", "py", "sh", "gradle", "properties", "yml", "yaml", "ini", "cfg")
private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")

/** A picture that zooms with pinch (1x to 5x) and pans while zoomed. */
@Composable
private fun ZoomPic(img: ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxWidth().height(380.dp).clipToBounds().pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale > 1f) offset + pan else Offset.Zero
        }
    }) {
        Image(img, null, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }, contentScale = ContentScale.Fit)
    }
}

/** Shows a picture (zoomable) or the start of a text file. A text file that fits the 200 KB read can be edited and saved. Anything else says there is no preview. */
@Composable
private fun FileViewer(f: File, onOpen: (File) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val ext = f.extension.lowercase()
    val image = remember(f) {
        if (ext in IMAGE_EXT) runCatching { android.graphics.BitmapFactory.decodeFile(f.path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull()?.asImageBitmap() else null
    }
    val text = remember(f) {
        if (image == null && ext in TEXT_EXT) runCatching { f.inputStream().use { String(it.readNBytes(200_000)) } }.getOrNull() else null
    }
    val editable = text != null && f.length() <= 200_000L
    var editing by remember(f) { mutableStateOf(false) }
    var body by remember(f) { mutableStateOf(text.orEmpty()) }
    var note by remember(f) { mutableStateOf("") }
    WinAlert(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (editable && !editing) { ExAct("edit", T.sub) { editing = true } }
                if (editing) {
                    ExAct("save", Look.accent) {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { f.writeText(body) }.isSuccess }
                            note = if (ok) "Saved" else "Could not save"
                            if (ok) editing = false
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    image != null -> ZoomPic(image)
                    editing -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        BasicTextField(body, { body = it }, Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                            cursorBrush = SolidColor(Look.accent))
                    }
                    text != null -> Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(text, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    else -> Text("No preview for this file", color = T.sub, fontSize = 13.sp)
                }
                if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton({ onOpen(f) }) { Text("Open") } },
        dismissButton = { TextButton({ onDismiss() }) { Text("Close") } },
    )
}


/** Unpacks a zip into a folder beside it (named after the zip); entries that would leave the folder are refused. Returns a note on failure. */
private fun unzip(z: File, dir: File): String {
    val base = z.nameWithoutExtension
    var target = File(dir, base)
    var n = 1
    while (target.exists()) target = File(dir, "$base ($n)").also { n++ }
    return runCatching {
        target.mkdirs()
        val root = target.canonicalPath + File.separator
        java.util.zip.ZipInputStream(z.inputStream().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(target, e.name)
                if (!out.canonicalPath.startsWith(root)) throw SecurityException("unsafe entry")
                if (e.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { zin.copyTo(it) } }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        ""
    }.getOrElse { "Could not extract ${z.name}" }
}

/** A Fluent outline from FluentPaths in every look (the header tool icons stay the same whatever the design). */
@Composable
private fun FluentGlyph(k: String, tint: Color, size: androidx.compose.ui.unit.Dp) {
    val paths = remember(k) { (com.umutk.pulse.FluentPaths.map[k] ?: com.umutk.pulse.FluentPaths.map["info"]!!).map { androidx.compose.ui.graphics.vector.PathParser().parsePathString(it).toPath() } }
    androidx.compose.foundation.Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, pivot = androidx.compose.ui.geometry.Offset.Zero) { paths.forEach { drawPath(it, tint) } }
    }
}
