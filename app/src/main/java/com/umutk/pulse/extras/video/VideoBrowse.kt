package com.umutk.pulse.extras.video

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PIcon
import com.umutk.pulse.PRadio
import com.umutk.pulse.WinAlert
import com.umutk.pulse.tr
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class Play(val items: List<VideoItem>, val index: Int)

private const val RECENT = "\u0000recent"

private class Folder(val key: String, val name: String, val vids: List<VideoItem>)

internal fun fk(v: VideoItem) = v.path.ifBlank { v.folder }

private fun parentOf(cur: String): String { val t = cur.trimEnd('/'); return if ('/' !in t) "" else t.substringBeforeLast('/') + "/" }

private fun videoOrder(p: VideoPrefs): Comparator<VideoItem> {
    val c: Comparator<VideoItem> = when (p.sortBy) {
        0 -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        2 -> compareBy { it.durMs }
        3 -> compareBy { it.size }
        4 -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.path + it.name }
        5 -> compareBy { it.w.toLong() * it.h }
        6 -> compareBy { it.modified }
        else -> compareBy { it.added }
    }
    return if (p.sortDesc) c.reversed() else c
}

private fun folderOrder(p: VideoPrefs): Comparator<Folder> {
    val c: Comparator<Folder> = when (p.sortBy) {
        1, 6 -> compareBy { f -> f.vids.maxOf { it.added } }
        2 -> compareBy { f -> f.vids.sumOf { it.durMs } }
        3 -> compareBy { f -> f.vids.sumOf { it.size } }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    return if (p.sortDesc) c.reversed() else c
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun displayName(ctx: Context, u: Uri): String =
    runCatching {
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull() ?: u.lastPathSegment.orEmpty()

private fun urlItem(u: Uri) = VideoItem(u, u.lastPathSegment?.takeIf { it.isNotBlank() } ?: u.toString(), 0, "")

/** Video app: folders, all files or a folder tree of the device, a file picker and a network address box; a tap plays. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { VideoPrefs(ctx) }
    var play by remember { mutableStateOf<Play?>(null) }
    var at by remember { mutableStateOf<String?>(null) }
    var videos by remember { mutableStateOf<List<VideoItem>?>(null) }
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED) }
    var urlDialog by remember { mutableStateOf(false) }
    var optDialog by remember { mutableStateOf(false) }
    var rev by remember { mutableIntStateOf(0) }
    var skinId by remember { mutableStateOf(prefs.skin) }
    var settings by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(!prefs.lockOn) }
    var search by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<VideoItem?>(null) }
    var infoFor by remember { mutableStateOf<VideoItem?>(null) }
    val netScope = rememberCoroutineScope()
    var renameFor by remember { mutableStateOf<VideoItem?>(null) }
    var pendingRename by remember { mutableStateOf<Pair<VideoItem, String>?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { us ->
        if (us.isNotEmpty()) play = Play(us.map { VideoItem(it, displayName(ctx, it), 0, "") }, 0)
    }
    val sender = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        pendingRename?.let { (v, n) -> if (r.resultCode == Activity.RESULT_OK) renameVideo(ctx, v, n) }
        pendingRename = null; rev++
    }
    // the video widget's Resume button starts this page with the video to open
    LaunchedEffect(Unit) {
        val act = ctx as? Activity
        // "open with" from other apps: ACTION_VIEW video/* (needs the manifest filter, see needs-video-deep.md)
        if (act?.intent?.action == android.content.Intent.ACTION_VIEW && act.intent.data != null) act.intent.putExtra("resume_video", act.intent.data.toString())
        act?.intent?.getStringExtra("resume_video")?.let { s ->
            act.intent.removeExtra("resume_video")
            play = Play(listOf(urlItem(Uri.parse(s)).let { it.copy(name = displayName(ctx, it.uri)) }), 0)
        }
    }
    LaunchedEffect(granted, play == null, rev) {
        if (granted && play == null) videos = withContext(Dispatchers.IO) { queryVideos(ctx) }
    }
    BackHandler(enabled = play == null && !settings) {
        when {
            search != null -> search = null
            at != null && prefs.browse == 2 && at != "" -> at = parentOf(at!!).takeIf { it.isNotEmpty() } ?: ""
            at != null && prefs.browse != 2 -> at = null
            else -> onBack()
        }
    }

    if (!unlocked) { VideoLockGate(onBack) { unlocked = true }; return }
    play?.let { p ->
        VideoPlayerScreen(p.items[p.index].uri, onBack = { play = null }, queue = p.items, index = p.index)
        return
    }
    if (settings) { VideoSettings(prefs, { settings = false }) { skinId = prefs.skin; rev++ }; return }

    val sk = skinOf(skinId)
    val browse = prefs.browse
    val tree = browse == 2
    val hidden = prefs.hidden
    val incl = prefs.include
    val all = videos.orEmpty().filter { (prefs.showHidden || fk(it) !in hidden) && it.durMs >= prefs.minDur * 1000L && (incl.isEmpty() || fk(it) in incl) }
    val vOrder = videoOrder(prefs)
    val q = search?.trim().orEmpty()
    val cur = at ?: if (tree) "" else null

    // what the page shows: folder entries and video entries
    var folders: List<Folder> = emptyList()
    var list: List<VideoItem> = emptyList()
    when {
        q.isNotEmpty() -> list = all.filter { it.name.contains(q, true) }.sortedWith(vOrder)
        browse == 1 -> list = all.sortedWith(vOrder)
        cur == RECENT -> list = prefs.recent().map { r -> all.firstOrNull { it.key == r.key } ?: r }
        tree -> {
            val here = cur.orEmpty()
            list = all.filter { it.path == here }.sortedWith(vOrder)
            folders = all.filter { it.path.length > here.length && it.path.startsWith(here) }
                .groupBy { here + it.path.substring(here.length).substringBefore('/') + "/" }
                .map { (k, v) -> Folder(k, k.trimEnd('/').substringAfterLast('/'), v) }.sortedWith(folderOrder(prefs))
        }
        cur == null -> folders = all.groupBy { fk(it) }.map { (k, v) -> Folder(k, v.first().folder.ifBlank { k.ifBlank { "/" } }, v) }.sortedWith(folderOrder(prefs))
        else -> list = all.filter { fk(it) == cur }.sortedWith(vOrder)
    }
    val title = when {
        cur == RECENT -> tr("Recent", "Son oynatılanlar")
        tree && cur != "" -> cur.orEmpty().trimEnd('/').substringAfterLast('/')
        !tree && cur != null && browse == 0 -> all.firstOrNull { fk(it) == cur }?.folder ?: cur
        else -> tr("Video", "Video")
    }

    Column(Modifier.fillMaxSize().background(sk.bg)) {
        Row(Modifier.fillMaxWidth().background(sk.top).padding(horizontal = 8.dp, vertical = if (sk.bevel) 2.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            SkinBtn(sk, { if (search != null) search = null else if (at != null && !(tree && at == "")) at = if (tree) parentOf(at!!) else null else onBack() }) { PIcon("back", sk.topFg, 24.dp) }
            if (search != null) {
                BasicTextField(
                    search.orEmpty(), { search = it }, singleLine = true,
                    textStyle = TextStyle(color = sk.topFg, fontSize = 16.sp), cursorBrush = SolidColor(sk.topFg),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp),
                )
            } else Text(title, Modifier.weight(1f), color = sk.topFg, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SkinBtn(sk, { search = if (search == null) "" else null }) { PIcon("search", sk.topFg, 24.dp) }
            SkinBtn(sk, { optDialog = true }) { PIcon("sort", sk.topFg, 24.dp) }
            SkinBtn(sk, { pick.launch(arrayOf("video/*")) }) { PIcon("folder", sk.topFg, 24.dp) }
            SkinBtn(sk, { urlDialog = true }) { PIcon("wifi", sk.topFg, 24.dp) }
            SkinBtn(sk, { settings = true }) { PIcon("settings", sk.topFg, 24.dp) }
        }
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                PButton(tr("Allow video access", "Video erişimine izin ver"), icon = Icons.Outlined.FolderOpen) { ask.launch(Manifest.permission.READ_MEDIA_VIDEO) }
            }
        } else {
            val recentOn = browse == 0 && cur == null && q.isEmpty() && prefs.showRecent && prefs.recent().isNotEmpty()
            val entries = buildList<Any> {
                if (recentOn) add(Folder(RECENT, tr("Recent", "Son oynatılanlar"), emptyList()))
                addAll(folders); addAll(list)
            }
            val shape = RoundedCornerShape(sk.round.coerceAtMost(12.dp))
            val onOpen: (Any) -> Unit = { e ->
                if (e is Folder) { at = e.key; search = null }
                else if (e is VideoItem) play = Play(list, list.indexOf(e).coerceAtLeast(0))
            }
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { PIcon("folder", sk.sub, 48.dp) }
            } else if (prefs.grid) {
                LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries) { e ->
                        Entry(e, sk, prefs, shape, true, Modifier.combinedClickable(onClick = { onOpen(e) }, onLongClick = { (e as? VideoItem)?.let { menuFor = it } })) { (e as? VideoItem)?.let { menuFor = it } }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(entries) { e ->
                        Entry(e, sk, prefs, shape, false, Modifier.combinedClickable(onClick = { onOpen(e) }, onLongClick = { (e as? VideoItem)?.let { menuFor = it } })) { (e as? VideoItem)?.let { menuFor = it } }
                    }
                }
            }
        }
    }

    if (urlDialog) UrlDialog(prefs, onDismiss = { urlDialog = false }) { u ->
        urlDialog = false; prefs.addUrl(u.toString())
        if (u.path?.lowercase()?.let { it.endsWith(".m3u") || it.endsWith(".pls") } == true)
            netScope.launch { val l = withContext(Dispatchers.IO) { readPlaylist(ctx, u) }; if (l.isNotEmpty()) play = Play(l.map { urlItem(it) }, 0) }
        else play = Play(listOf(urlItem(u)), 0)
    }
    if (optDialog) ViewOptions(prefs, { optDialog = false }) { at = if (prefs.browse == 2) "" else null; rev++ }
    menuFor?.let { v ->
        val acts = listOf(tr("Play", "Oynat"), tr("Info", "Bilgi"), tr("Share", "Paylaş"), tr("Rename", "Yeniden adlandır"), tr("Delete", "Sil"), tr("Hide folder", "Klasörü gizle"), tr("Mark unplayed", "Oynanmadı say"))
        PickDialog(v.name, acts.map { it to false }, { menuFor = null }) { i ->
            menuFor = null
            when (i) {
                0 -> play = Play(listOf(v), 0)
                1 -> infoFor = v
                2 -> shareVideo(ctx, v)
                3 -> renameFor = v
                4 -> if (v.local) { if (android.os.Build.VERSION.SDK_INT >= 30) sender.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(v.uri)).intentSender).build()) else { runCatching { ctx.contentResolver.delete(v.uri, null, null) }; rev++ } }
                5 -> { prefs.hidden = prefs.hidden + fk(v); rev++ }
                6 -> { prefs.forget(v.key); rev++ }
            }
        }
    }
    infoFor?.let { FileInfoDialog(it, emptyList()) { infoFor = null } }
    renameFor?.let { v ->
        TextDialog(tr("Rename", "Yeniden adlandır"), v.name, { renameFor = null }) { n ->
            renameFor = null
            if (n.isNotBlank() && n != v.name) {
                if (renameVideo(ctx, v, n)) rev++
                else if (v.local && android.os.Build.VERSION.SDK_INT >= 30) { pendingRename = v to n; sender.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(ctx.contentResolver, listOf(v.uri)).intentSender).build()) }
            }
        }
    }
}

/** One folder or video cell, as a list row or a grid cell. */
@Composable
private fun Entry(e: Any, sk: VSkin, p: VideoPrefs, shape: androidx.compose.ui.graphics.Shape, grid: Boolean, modifier: Modifier, onMore: () -> Unit) {
    val boxMod = if (grid) Modifier.fillMaxWidth().aspectRatio(16f / 9f) else Modifier.size(96.dp, 54.dp)
    val first = (e as? Folder)?.vids?.firstOrNull() ?: e as? VideoItem
    val thumb: @Composable () -> Unit = {
        Box(boxMod.background(sk.fg.copy(alpha = .12f), shape).clip(shape)) {
            if (p.showThumb && first != null) Thumb(first, Modifier.fillMaxSize(), shape)
            if (e is Folder) {
                PIcon(if (e.key == RECENT) "history" else "folder", Color.White, 20.dp, Modifier.align(Alignment.BottomStart).padding(4.dp))
            } else if (e is VideoItem) {
                if (p.showDur && e.durMs > 0) Text(clock(e.durMs), Modifier.align(Alignment.BottomEnd).background(Color(0x99000000)).padding(horizontal = 4.dp), color = Color.White, fontSize = 11.sp)
                val prog = if (p.showProgress) p.progress(e.key, e.durMs) else 0f
                if (prog > 0f) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(prog).height(3.dp).background(sk.accent))
                if (p.markNew && e.added > System.currentTimeMillis() / 1000 - 7 * 86400 && !p.played(e.key))
                    Text("NEW", Modifier.align(Alignment.TopStart).background(sk.accent).padding(horizontal = 4.dp), color = if (sk.accent.luminance() > .5f) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            when (e) {
                is Folder -> {
                    Text(e.name, color = sk.fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.key != RECENT) Text(e.vids.size.toString(), color = sk.sub, fontSize = 12.sp)
                }
                is VideoItem -> {
                    Text(e.name, color = sk.fg, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        e.ext.takeIf { p.showExt && it.isNotBlank() }, fmtSize(e.size).takeIf { p.showSize && e.size > 0 },
                        "${e.w}x${e.h}".takeIf { p.showRes && e.w > 0 }, e.path.trimEnd('/').takeIf { p.showPath && it.isNotBlank() },
                    ).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, color = sk.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (grid) {
        Column(modifier) { thumb(); Row(verticalAlignment = Alignment.Top) { text(Modifier.weight(1f).padding(top = 4.dp)); if (e is VideoItem) SkinBtn(sk, onMore, 28.dp) { com.umutk.pulse.extras.ui.TIcon("more", sk.sub, 18.dp, sk.icons) } } }
    } else {
        Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            thumb(); Spacer(Modifier.width(12.dp)); text(Modifier.weight(1f))
            if (e is VideoItem) SkinBtn(sk, onMore, 36.dp) { com.umutk.pulse.extras.ui.TIcon("more", sk.sub, 22.dp, sk.icons) }
        }
    }
}

private fun Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
private fun Thumb(v: VideoItem, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape) {
    val bmp = videoThumb(v)
    if (bmp != null) Image(bmp, null, modifier.clip(shape), contentScale = ContentScale.Crop)
}

/** View mode, sort field, order and layout in one window; [onMode] runs when the view mode changed. */
@Composable
private fun ViewOptions(p: VideoPrefs, onDismiss: () -> Unit, onMode: () -> Unit) {
    val w = LocalWindowLook.current
    var rev by remember { mutableIntStateOf(0) }
    rev.let { }
    val modes = listOf(tr("Folders", "Klasörler"), tr("Files", "Dosyalar"), tr("Tree", "Ağaç"))
    val sorts = listOf(tr("Title", "Ad"), tr("Date added", "Eklenme"), tr("Length", "Süre"), tr("Size", "Boyut"), tr("Path", "Yol"), tr("Resolution", "Çözünürlük"), tr("Modified", "Değişme"))
    @Composable fun group(head: String, names: List<String>, cur: Int, set: (Int) -> Unit) {
        Text(head, color = Look.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        names.forEachIndexed { i, n ->
            Row(Modifier.fillMaxWidth().clickable { set(i); rev++ }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                PRadio(cur == i) { set(i); rev++ }; Spacer(Modifier.width(8.dp)); Text(n, color = w.text, fontSize = 14.sp)
            }
        }
    }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("View", "Görünüm"), color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                group(tr("View", "Görünüm"), modes, p.browse) { p.browse = it; onMode() }
                group(tr("Sort", "Sırala"), sorts, p.sortBy) { p.sortBy = it }
                group(tr("Order", "Sıra"), listOf(tr("Ascending", "Artan"), tr("Descending", "Azalan")), if (p.sortDesc) 1 else 0) { p.sortDesc = it == 1 }
                group(tr("Layout", "Düzen"), listOf(tr("List", "Liste"), tr("Grid", "Izgara")), if (p.grid) 1 else 0) { p.grid = it == 1 }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = { onMode(); onDismiss() }) },
    )
}

@Composable
private fun UrlDialog(prefs: VideoPrefs, onDismiss: () -> Unit, onOk: (Uri) -> Unit) {
    var text by remember { mutableStateOf("https://") }
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text("URL", color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                BasicTextField(
                    text, { text = it }, singleLine = true,
                    textStyle = TextStyle(color = w.text, fontSize = 14.sp), cursorBrush = SolidColor(Look.accent),
                    modifier = Modifier.fillMaxWidth().border(1.dp, w.sub.copy(alpha = .5f)).padding(8.dp),
                )
                prefs.urls().forEach { u ->
                    Row(Modifier.fillMaxWidth().clickable { text = u }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PIcon("history", w.sub, 18.dp); Spacer(Modifier.width(8.dp))
                        Text(u, color = w.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("Play", "Oynat")) { if (text.trim().let { it.startsWith("http", true) || it.startsWith("rtsp", true) }) onOk(Uri.parse(text.trim())) } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç"), onClick = onDismiss) },
    )
}

@Composable
internal fun TextDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val w = LocalWindowLook.current
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(title, color = w.text) },
        text = {
            BasicTextField(
                text, { text = it }, singleLine = true,
                textStyle = TextStyle(color = w.text, fontSize = 14.sp), cursorBrush = SolidColor(Look.accent),
                modifier = Modifier.fillMaxWidth().border(1.dp, w.sub.copy(alpha = .5f)).padding(8.dp),
            )
        },
        confirmButton = { PButton(tr("OK", "Tamam")) { onOk(text.trim()) } },
        dismissButton = { PButton(tr("Cancel", "Vazgeç"), onClick = onDismiss) },
    )
}

/** File facts; [extra] adds live rows such as the codecs while playing. */
@Composable
internal fun FileInfoDialog(v: VideoItem, extra: List<Pair<String, String>>, onDismiss: () -> Unit) {
    val w = LocalWindowLook.current
    val df = remember { java.text.DateFormat.getDateTimeInstance() }
    val rows = listOfNotNull(
        tr("Name", "Ad") to v.name,
        v.path.takeIf { it.isNotBlank() }?.let { tr("Path", "Yol") to it },
        (if (v.local) null else tr("Address", "Adres") to v.key),
        v.size.takeIf { it > 0 }?.let { tr("Size", "Boyut") to fmtSize(it) },
        v.durMs.takeIf { it > 0 }?.let { tr("Length", "Süre") to clock(it) },
        if (v.w > 0) tr("Resolution", "Çözünürlük") to "${v.w}x${v.h}" else null,
        v.mime.takeIf { it.isNotBlank() }?.let { "MIME" to it },
        v.added.takeIf { it > 0 }?.let { tr("Added", "Eklenme") to df.format(java.util.Date(it * 1000)) },
        v.modified.takeIf { it > 0 }?.let { tr("Modified", "Değişme") to df.format(java.util.Date(it * 1000)) },
    ) + extra
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text(tr("Info", "Bilgi"), color = w.text) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                rows.forEach { (k, x) ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(k, color = w.sub, fontSize = 12.sp)
                        Text(x, color = w.text, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { PButton(tr("OK", "Tamam"), onClick = onDismiss) },
    )
}
