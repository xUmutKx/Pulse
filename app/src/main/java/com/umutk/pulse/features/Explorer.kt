package com.umutk.pulse.features

import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.text.font.FontWeight
import com.umutk.pulse.fabFill
import com.umutk.pulse.fabMark
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.umutk.pulse.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.gestures.detectDragGestures
import kotlinx.coroutines.flow.first
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.umutk.pulse.BarMeter
import com.umutk.pulse.bevel
import com.umutk.pulse.xpCaption
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.umutk.pulse.FluentPaths
import com.umutk.pulse.PRadio
import com.umutk.pulse.PSwitch
import androidx.compose.foundation.shape.CircleShape
import com.umutk.pulse.PButton
import com.umutk.pulse.WinAlert
import com.umutk.pulse.ThemedMenu
import com.umutk.pulse.ThemedMenuItem
import com.umutk.pulse.files.recent.RecentScreen
import com.umutk.pulse.files.recent.RecentStore
import com.umutk.pulse.LocalWindowLook
import com.umutk.pulse.Guard
import com.umutk.pulse.InfoRows
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
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
import com.umutk.pulse.files.root.RootFs
import com.umutk.pulse.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

/** Files cut or copied with the copy and move icons, waiting for the paste icon. */
private class Clip(val files: List<File>, val move: Boolean, val once: Boolean = false)

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
private const val EX_VIEW_DIRS = "ex_view_dirs"

/** Set by an intent that opens a folder (Pulse chosen as the file manager): Files then opens full screen. */
object FilesLaunch {
    var full by mutableStateOf(false)
    /** An archive another app asked Pulse to open (Open with Pulse on a zip): the explorer browses it like a folder. */
    var archive by mutableStateOf<File?>(null)
    /** Set the moment such an archive is on its way in (a content:// copy can take a second): the Home page switches to Browse at once instead of flashing first. */
    var opening by mutableStateOf(false)
}

/** When set, tapping a file hands it to this callback instead of opening it (the Office "Open file" picker). */
val LocalFilePick = androidx.compose.runtime.staticCompositionLocalOf<((File) -> Unit)?> { null }

/** The side page for files and the tools that read them: the explorer, the folder tree, file types, cache cleaning and root. Full screen (Settings > Functions) puts it in its own window with the look's caption buttons. */
@Composable
fun FilesPage() {
    // the Files look override belongs to the standalone Files app; inside Pulse the page follows the global look
    val fl = if (LocalContext.current is com.umutk.pulse.FilesActivity) filesLook() else null
    FilesPrefs.load(LocalContext.current)
    // The file manager's own look: T reads Look.current (= Look.resolved ?: Look.theme, a global), so the chosen look is swapped into Look.resolved
    // while this page is on screen (the same trick LookHost uses for the Material look) and the old value is put back when it leaves.
    // Look.theme is read so a change of the app look re-runs this after LookHost has reset resolved. Pulse's own chrome may briefly share the look.
    @Suppress("UNUSED_EXPRESSION") Look.theme
    val saved = remember { Look.resolved }
    Look.resolved = fl ?: saved
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { Look.resolved = saved } }
    if (fl == null) FilesPageInner() else androidx.compose.runtime.CompositionLocalProvider(LocalWindowLook provides fl) { FilesPageInner() }
}

@Composable
private fun FilesPageInner() {
    val ctx = LocalContext.current
    val standalone = ctx is com.umutk.pulse.FilesActivity
    // the standalone Files app starts on its home page (storage, recent, types); inside Pulse the explorer opens at once
    // an archive is already on its way in (set the moment the intent arrives, before the content:// copy that can take a second): skip the home flash
    var tab by rememberSaveable { mutableStateOf(if (standalone && !FilesPrefs.startBrowse && !FilesLaunch.opening) "home" else "files") }
    val top = remember { Environment.getExternalStorageDirectory() }
    // Folder sizes / File types / Clean cache are only ever opened from the Browse tab's own drawer, so back always returns there, never to Home
    val back: () -> Unit = { tab = "files" }
    // Back from any other tab of the standalone app goes to the home page first
    androidx.activity.compose.BackHandler(enabled = standalone && tab != "home") { tab = "home" }
    // the full-screen window is the standalone app's; embedded in Pulse the page stays a page
    var full by rememberSaveable { mutableStateOf(standalone && (Opt.filesFull || FilesLaunch.full)) }
    LaunchedEffect(FilesLaunch.full) { if (FilesLaunch.full && standalone) { full = true; FilesLaunch.full = false } }
    // an archive opened from another app shows in the browser at once, not on the home page
    // it goes the way a file tapped on the home page goes (that way opens the archive inside the browser): the browser tab, and the file handed over as the pending one
    LaunchedEffect(FilesLaunch.archive) {
        val a = FilesLaunch.archive
        if (a != null && standalone) { FilesLaunch.archive = null; FilesLaunch.opening = false; com.umutk.pulse.files.home.FilesHomeNav.pendingFile = a; ExNav.lastHere = a.parentFile?.path ?: ExNav.lastHere; tab = "files" }
    }
    var windowed by rememberSaveable { mutableStateOf(false) }
    var settings by rememberSaveable { mutableStateOf(false) }
    val openTool = com.umutk.pulse.LocalOpenTool.current
    val wl = LocalWindowLook.current
    val glassId = wl.id == "win11" && FilesPrefs.glass
    if (standalone) androidx.compose.runtime.SideEffect { (ctx as com.umutk.pulse.FilesActivity).setGlass(glassId) }
    val body: @Composable () -> Unit = { com.umutk.pulse.LookFade(FilesLook.theme) {
        // full screen is a Dialog that covers the app, so the settings screen opens inside it (the app's sub screen would sit behind)
        androidx.compose.runtime.CompositionLocalProvider(com.umutk.pulse.LocalOpenTool provides { id -> if (full && id == "files_settings") settings = true else openTool(id) }) {
        if (settings && full) {
            androidx.activity.compose.BackHandler { settings = false }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp)) {
                FilesSettings()
            }
        } else
        Column(Modifier.fillMaxSize().then(if (standalone && glassId) Modifier.background(wl.bg.copy(alpha = wl.glassA)) else if (standalone) Modifier.background(wl.bg) else Modifier)) {
            com.umutk.pulse.StorageAccessCard()
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    "home" -> {
                        val ot = com.umutk.pulse.LocalOpenTool.current
                        com.umutk.pulse.files.home.FilesHome(onTab = { tab = it },
                            onOpenDir = { d -> ExNav.lastHere = d; tab = "files" },
                            onOpenFile = { f -> com.umutk.pulse.files.recent.RecentStore.add(ctx, f.path); if (isArchiveFile(f) || f.extension.equals("apk", true)) { ExNav.lastHere = f.parentFile?.path ?: ExNav.lastHere; com.umutk.pulse.files.home.FilesHomeNav.pendingFile = f; tab = "files" } else com.umutk.pulse.files.home.openFileWithSystem(ctx, f) },
                            onSettings = { ot("files_settings") })
                    }
                    "files" -> if (FilesLaunch.opening && FilesLaunch.archive == null) com.umutk.pulse.Center(tr("Opening…", "Açılıyor…"))
                        else ExplorerTab(full || !standalone, { tab = it }) { full = !full }
                    "more" -> com.umutk.pulse.files.home.FilesMorePage(onTab = { tab = it })
                    "tree" -> FolderTreeScreen(top, back)
                    "types" -> TypeBreakdownScreen(top, back)
                    "cache" -> CacheCleanerScreen(back)
                    else -> ExplorerTab(full || !standalone, { tab = it }) { full = !full }
                }
            }
            if (standalone) {
                val ot = com.umutk.pulse.LocalOpenTool.current
                com.umutk.pulse.files.home.FilesNavBar(tab, { tab = it }) { ot("localsend") }
            }
        }
        }
    } }
    if (!full) { ExOpenAnim { body() }; return }
    // close leaves the page (Pulse Files: finishes the activity), minimise sends the task to the back (embedded: gives the window back to the app),
    // maximise toggles between the full-screen window and an inset, rounded one
    val close: () -> Unit = { if (standalone) (ctx as android.app.Activity).finish() else { full = false; com.umutk.pulse.widgets.WidgetOpen.page = Opt.startPage } }
    val minimise: () -> Unit = { if (standalone) (ctx as android.app.Activity).moveTaskToBack(true) else { full = false } }
    val w = LocalWindowLook.current
    val classic = w.tabs && w.title != null
    val ink = if (classic) w.titleText else w.text
    // Windows 11, 10 and 7: the surfaces are see-through and the window blurs what is behind it (the backdrop / wallpaper), like Mica / Aero
    val glass = glassId
    Dialog(close, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        androidx.compose.runtime.SideEffect {
            val win = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: return@SideEffect
            win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            if (android.os.Build.VERSION.SDK_INT >= 31) win.setBackgroundBlurRadius(if (glass && ctx.getSystemService(android.view.WindowManager::class.java).isCrossWindowBlurEnabled) 40 else 0)
        }
        val shape = if (windowed) RoundedCornerShape(if (w.tabs) w.radius else if (w.material) 20.dp else 12.dp) else RoundedCornerShape(0.dp)
        ExOpenAnim {
        Column(Modifier.fillMaxSize().then(if (windowed) Modifier.padding(horizontal = 14.dp, vertical = 30.dp) else Modifier).clip(shape)
            .background(if (glass) w.bg.copy(alpha = w.glassA) else w.bg).statusBarsPadding().navigationBarsPadding()
            .then(if (w.id == "win95") Modifier.bevel(true) else Modifier)) {
            Row(Modifier.fillMaxWidth().height(if (w.id == "win95") 22.dp else 34.dp)
                .then(if (classic) Modifier.background(w.title!!) else if (w.material) Modifier.background(if (glass) w.panel.copy(alpha = .4f) else w.panel) else Modifier)
                .padding(start = 10.dp, end = if (classic) 3.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                if (standalone) Spacer(Modifier.weight(1f)) else Text("Files", Modifier.weight(1f), color = ink, fontSize = 12.sp, fontWeight = if (classic) androidx.compose.ui.text.font.FontWeight.Bold else null)
                ExCaption(w, ink, minimise, { windowed = !windowed }, close, showMin = !standalone)
            }
            Box(Modifier.weight(1f).fillMaxWidth()) { body() }
        }
        }
    }
}

/** Opening animation: grows from the centre while fading in (95 and XP only zoom, quicker; Opt.authentic off keeps the one soft zoom for every look). */
@Composable
private fun ExOpenAnim(content: @Composable () -> Unit) {
    // the standalone Files app (launcher entry) opens without the zoom; only the window inside Pulse animates
    if (LocalContext.current is com.umutk.pulse.FilesActivity) { content(); return }
    val id = T.id
    val retro = Opt.authentic && (id == "win95" || id == "xp")
    val p = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { p.animateTo(1f, androidx.compose.animation.core.tween(if (retro) 140 else 260, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
    Box(Modifier.fillMaxSize().graphicsLayer {
        val sc = (if (retro) .85f else .92f) + (if (retro) .15f else .08f) * p.value
        scaleX = sc; scaleY = sc; alpha = if (retro) 1f else p.value
    }) { content() }
}

/** Minimise, maximise and close in the look's style: 95 bevelled, XP glossy squares, 7 glass, 10 and 11 flat, Material round. */
@Composable
private fun ExCaption(w: com.umutk.pulse.PTheme, ink: Color, onMin: () -> Unit, onMax: () -> Unit, onClose: () -> Unit, showMin: Boolean = true) {
    val id = w.id
    val ks = if (showMin) 0..2 else 1..2
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
        "win95" -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { ks.forEach { k ->
            if (k == 2) Spacer(Modifier.width(2.dp))
            cap(k, androidx.compose.ui.unit.DpSize(16.dp, 14.dp), Modifier.background(Color(0xFFC0C0C0)).bevel(true), Color.Black, acts[k]) } }
        "xp" -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { ks.forEach { k ->
            cap(k, androidx.compose.ui.unit.DpSize(21.dp, 21.dp), Modifier.drawBehind { xpCaption(k == 2, k == 1) }, Color.Transparent, acts[k]) } }
        "win7" -> Row(Modifier.offset(y = (-3).dp)) { ks.forEach { k ->
            val shape = RoundedCornerShape(bottomStart = if (k == 0) 4.dp else 0.dp, bottomEnd = if (k == 2) 4.dp else 0.dp)
            cap(k, androidx.compose.ui.unit.DpSize(if (k == 2) 44.dp else 27.dp, 19.dp),
                Modifier.clip(shape).background(if (k == 2) Brush.verticalGradient(listOf(Color(0xFFE9A394), Color(0xFFC9442B))) else Brush.verticalGradient(listOf(Color(0xFFDCE8F6), Color(0xFF9DBBDD)))).border(1.dp, Color(0xFF3A4A5E), shape),
                if (k == 2) Color.White else Color(0xFF1B2B45), acts[k]) } }
        "win10", "win11" -> Row { ks.forEach { k ->
            cap(k, androidx.compose.ui.unit.DpSize(46.dp, 34.dp), Modifier, ink, acts[k]) } }
        // Material, AMOLED, One UI, ColorOS: one minimise button (two arrows pointing at each other); every other look gets the three flat buttons
        else -> if (!w.material) Row { ks.forEach { k -> cap(k, androidx.compose.ui.unit.DpSize(46.dp, 34.dp), Modifier, ink, acts[k]) } }
        else if (showMin) Box(Modifier.padding(end = 4.dp).size(36.dp, 34.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable(onClick = onMin), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CloseFullscreen, null, Modifier.size(20.dp), ink)
        }
    }
}

/** Result of a cut out of an archive: the top-level files now in the clipboard folder (null when nothing was cut) and the reason it failed ("" on success). */
private class CutResult(val tops: List<File>?, val reason: String)

/** The file manager on shared storage: browse, search, select, sort, open, share, zip, bookmarks, properties, finders and undo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable

private fun ExplorerTab(full: Boolean, onTab: (String) -> Unit, onToggleFull: () -> Unit) {
    val ctx = LocalContext.current
    val standalone = ctx is com.umutk.pulse.FilesActivity
    val scope = rememberCoroutineScope()
    val top = remember { Environment.getExternalStorageDirectory() }
    FilesPrefs.load(ctx)
    var here by rememberSaveable { mutableStateOf(ExNav.lastHere?.takeIf { File(it).isDirectory && it.startsWith(top.path) } ?: FilesPrefs.start.takeIf { it.isNotEmpty() && File(it).isDirectory } ?: top.path) }
    LaunchedEffect(here) { ExNav.lastHere = here; if (standalone && here != top.path) com.umutk.pulse.files.home.RecentDirs.add(ctx, here) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<Pair<String, File>?>(null) }
    var apkAsk by remember { mutableStateOf<File?>(null) }
    // a file whose type nobody knows: asked how to open it
    var openAsAsk by remember { mutableStateOf<File?>(null) }
    var saveAsFile by remember { mutableStateOf<File?>(null) }
    var name by remember { mutableStateOf("") }
    var newExt by remember { mutableStateOf("docx") }
    val filePick = LocalFilePick.current
    var note by remember { mutableStateOf("") }
    var viewing by remember { mutableStateOf<File?>(null) }
    var propsOf by remember { mutableStateOf<File?>(null) }
    var finder by remember { mutableStateOf<String?>(null) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var query by rememberSaveable { mutableStateOf("") }
    // sort: one order for every folder, unless a folder has its own (folderSort); both are kept on the phone
    var sortAll by remember { mutableStateOf(exReadInt(ctx, EX_SORT_ALL, 0)) }  // not saveable: the settings screen can change it
    // type filter: remembered for all folders or just this one ("all" = none; "ext:a,b" = custom extensions)
    var filtAll by remember { mutableStateOf(com.umutk.pulse.filesPrefs(ctx).getString("ex_filt_all", "all") ?: "all") }
    var filtDir by remember { mutableStateOf(exRead(ctx, "ex_filt_dir").associate { it.substringBeforeLast('|') to it.substringAfterLast('|') }) }
    var folderSort by remember { mutableStateOf(exReadSortMap(ctx)) }
    // the view: a folder's own choice when it has one, else the global list / grid, and the detailed list when that is picked
    var viewDir by remember { mutableStateOf(exReadViewMap(ctx)) }
    var viewMenu by remember { mutableStateOf(false) }
    var detailAll by remember { mutableStateOf(exReadInt(ctx, "ex_detail", 0) == 1) }
    val viewOwn = viewDir[here]
    val viewNow = viewOwn ?: if (FilesPrefs.grid) 1 else if (detailAll) 2 else 0
    val gridNow = viewNow == 1
    val detailNow = viewNow == 2
    var drawer by remember { mutableStateOf(false) }
    // not saved: leaving the Files page for another and coming back finds the search closed
    var searchOpen by remember { mutableStateOf(false) }
    // the bottom bar's Search opens the search field
    LaunchedEffect(com.umutk.pulse.files.home.FilesHomeNav.openSearch) { if (com.umutk.pulse.files.home.FilesHomeNav.openSearch) { searchOpen = true; com.umutk.pulse.files.home.FilesHomeNav.openSearch = false } }
    var freq by remember { mutableStateOf(exRead(ctx, EX_FREQ)) }
    var sortMenu by remember { mutableStateOf(false) }
    var menuOf by remember { mutableStateOf<File?>(null) }
    var about by remember { mutableStateOf(false) }
    val openToolFn = com.umutk.pulse.LocalOpenTool.current
    var archiveOf by remember { mutableStateOf<File?>(null) }
    var arc by remember { mutableStateOf<ArcReq?>(null) }
    var archiveList by remember { mutableStateOf<List<Pair<String, Long>>?>(null) }
    // an archive opened like a folder: the file and the folder inside it ("" = its root, "a/b/")
    var arcFile by remember { mutableStateOf<File?>(null) }
    var arcCwd by remember { mutableStateOf("") }
    // entries picked inside an open archive (their paths); copy or extract takes them out
    var arcSel by remember { mutableStateOf(emptySet<String>()) }
    // the row of the open archive whose three-dot menu is showing
    var arcMenu by remember { mutableStateOf<String?>(null) }
    var arcInfo by remember { mutableStateOf<String?>(null) }
    // an HTML page shown in the app
    var htmlView by remember { mutableStateOf<File?>(null) }
    // bumped after a cut rewrote the archive, so its entries are read again
    var arcRev by remember { mutableStateOf(0) }
    val wantedArc = FilesLaunch.archive
    LaunchedEffect(wantedArc) { if (wantedArc != null) { arcFile = wantedArc; arcCwd = ""; arcSel = emptySet(); FilesLaunch.archive = null } }
    val arcAll by produceState<List<AEntry>?>(null, arcFile, arcRev) {
        value = arcFile?.let { f -> withContext(Dispatchers.IO) { runCatching { archiveEntries(f) }.getOrDefault(emptyList()) } }
    }
    var showHidden by remember { mutableStateOf(FilesPrefs.hidden) }
    var showSizes by rememberSaveable { mutableStateOf(true) }
    var recentView by rememberSaveable { mutableStateOf(FilesPrefs.openRecent) }
    var marks by remember { mutableStateOf(exRead(ctx, EX_MARKS)) }
    var recents by remember { mutableStateOf(exRead(ctx, EX_RECENT)) }
    var undo by remember { mutableStateOf<ExUndo?>(null) }
    var found by remember { mutableStateOf<List<File>?>(null) }
    // files waiting for the delete confirm box; the box calls deleteNow
    var bulk by remember { mutableStateOf<List<File>?>(null) }
    // files waiting for the compress box; the box starts a background zip (ExplorerZip.kt)
    var zipAsk by remember { mutableStateOf<List<File>?>(null) }
    // the share sheet of the selection: the files themselves, their paths or their names
    var shareSheet by remember { mutableStateOf<List<File>?>(null) }
    // the bin (when on) keeps the files and Undo brings them back
    val deleteNow: (List<File>) -> Unit = { files ->
        sel = emptySet()
        scope.launch {
            val u = withContext(Dispatchers.IO) { if (Opt.recycle) exTrash(top, files) else { FileJobs.deleteTracked(ctx, files); null } }
            undo = u
            note = if (u == null && Opt.recycle) "Could not delete" else ""
            refresh++
        }
    }
    val askDelete: (List<File>) -> Unit = { if (FilesPrefs.confirm) bulk = it else deleteNow(it) }
    val dir = File(here)
    val sort = folderSort[here] ?: sortAll
    val (total, free) = remember(refresh) { exStorage(top) }
    // folders the app cannot read (/data, ...) are listed through su or Shizuku when either is there
    // a state, not a constant: the root check below may finish after the first frame, and the drawer's root rows must appear then
    var canRoot by remember { mutableStateOf(Shell.root == true || Shell.shizuku == 2) }
    // the drawer's root entries (/, /data, /data/data) need the root state even when the Files app opens on a normal folder
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { RootFs.ensureChecked() }; canRoot = Shell.root == true || Shell.shizuku == 2 }
    // why a root listing came back empty (root cannot see the folder, su refused), shown where "No files here" would be
    var rootErr by remember { mutableStateOf("") }
    val viaRoot by produceState<List<File>?>(null, here, refresh, canRoot) {
        rootErr = ""
        // under /data and Android/data, Android/obb the other apps' folders only list through root: Android returns just Pulse's own folder there, so a partial normal listing must not win
        val restricted = dir.path.startsWith("/data") || dir.path.contains("/Android/data") || dir.path.contains("/Android/obb")
        // /data/data lists every installed app from PackageManager (root's ls shows only a few of them there)
        value = when {
            canRoot && dir.path == "/data/data" -> withContext(Dispatchers.IO) { AppDataFolders.list(ctx).map { RootFile(it.path, true) } }
            canRoot && (dir.listFiles() == null || restricted) -> withContext(Dispatchers.IO) { exRootList(dir) { rootErr = it } }
            else -> null
        }
    }
    // the size of each folder here, measured one after the other in the background (shown under the name; the size sort uses it)
    val dirSizes by produceState(emptyMap<String, Long>(), here, refresh, viaRoot) {
        val dirs = (viaRoot ?: dir.listFiles()?.toList()).orEmpty().filter { it.isDirectory && !it.path.startsWith("/data") && !it.path.contains("/Android/data") && !it.path.contains("/Android/obb") }
        val acc = HashMap<String, Long>()
        dirs.forEach { d -> ExDirSizes.cached(d)?.let { acc[d.path] = it } }
        value = acc.toMap()
        for (d in dirs) {
            if (d.path in acc) continue
            acc[d.path] = withContext(Dispatchers.IO) { ExDirSizes.measure(d) }
            value = acc.toMap()
        }
    }
    val listing = remember(here, refresh, showHidden, sort, viaRoot, if (sort / 2 == 1) dirSizes else null) {
        exSort((viaRoot ?: dir.listFiles()?.toList()).orEmpty().filter { showHidden || !it.name.startsWith(".") }, sort, dirSizes)
    }
    var resultsFor by remember { mutableStateOf<String?>(null) }
    // the search looks under this folder; the button beside the field widens it to the whole storage
    var searchAll by remember { mutableStateOf(false) }
    LaunchedEffect(resultsFor, here, showHidden, searchAll) {
        found = null
        found = resultsFor?.let { q -> withContext(Dispatchers.IO) { if (searchAll) exFind(top, q, showHidden, 300_000) else exFind(dir, q, showHidden) } }
    }
    // typing searches this folder and everything under it by itself, a moment after the last key
    LaunchedEffect(query, searchOpen) {
        if (!searchOpen) return@LaunchedEffect
        if (query.trim().length < 2) { resultsFor = null; return@LaunchedEffect }
        delay(350); resultsFor = query.trim()
    }
    val shown: List<File> = when {
        arcFile != null -> emptyList()
        recentView -> recents.map { File(it) }.filter { it.exists() }
        query.isNotBlank() -> listing.filter { it.name.contains(query.trim(), ignoreCase = true) }.filter { exFilterOk(it, filtDir[here] ?: filtAll) }
        else -> listing.filter { exFilterOk(it, filtDir[here] ?: filtAll) }
    }
    // a long folder (an app data folder holds hundreds of entries) is composed in slices of 60, one slice every 50 ms, so it opens at once and the rest fills in
    val sliceFor by produceState(60, here, shown.size) { value = 60; while (value < shown.size) { delay(50); value += 60 } }
    val shownNow = if (shown.size <= 80) shown else shown.take(sliceFor)
    val filt = filtDir[here] ?: filtAll
    val bookmarks = marks.filter { File(it).isDirectory }
    val picked = sel.map { File(it) }

    fun go(p: String) { ExScroll.live?.let { ExScroll.memo[here] = it.value }; freq = exBump(ctx, p); arcFile = null; arcCwd = ""; arcSel = emptySet(); here = p; sel = emptySet(); query = ""; recentView = false; searchOpen = false }
    // Browse pressed again in the bottom bar: back to the top folder from wherever we went into
    LaunchedEffect(com.umutk.pulse.files.home.FilesHomeNav.toRoot) { if (com.umutk.pulse.files.home.FilesHomeNav.toRoot) { com.umutk.pulse.files.home.FilesHomeNav.toRoot = false; resultsFor = null; go(top.path) } }
    // Back steps out of the current view first (selection, search, recents), then up one folder; only at the top does it leave the page
    androidx.activity.compose.BackHandler(enabled = resultsFor != null || searchOpen || arcFile != null || sel.isNotEmpty() || query.isNotBlank() || recentView || dir != top) {
        when {
            resultsFor != null -> resultsFor = null
            searchOpen -> { searchOpen = false; query = "" }
            arcFile != null -> if (arcCwd.isEmpty()) arcFile = null else arcCwd = arcCwd.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            sel.isNotEmpty() -> sel = emptySet()
            query.isNotBlank() -> query = ""
            recentView -> recentView = false
            else -> go((dir.parentFile ?: top).path)
        }
    }
    // an APK asks first: view its contents, or install it (a tap on other files opens them as before)
    fun openFile(f: File) {
        if (filePick != null) { if (f.extension.lowercase() in com.umutk.pulse.OF_EXTS || com.umutk.pulse.ofIsText(f)) filePick(f); return }
        if (f.extension.equals("apk", true) || (standalone && f.extension.lowercase() in setOf("xapk", "apks", "apkm"))) { apkAsk = f; sel = emptySet(); return }
        if (isArchiveFile(f)) { arcFile = f; arcCwd = ""; sel = emptySet(); return }
        // a video plays in Pulse's own player straight away; "Open as" holds the gallery and the other apps
        if (!FilesPrefs.openAsk && f.extension.lowercase() in EX_VIDEO) { recents = exAddRecent(ctx, f); if (exOpenInPulseVideo(ctx, f)) return }
        if (FilesPrefs.openAsk) { recents = exAddRecent(ctx, f); if (exHandOver(ctx, f, false)) return }
        // web pages show in the app (a file with no extension that holds a page too)
        if (f.extension.lowercase() in setOf("html", "htm", "xhtml") || (f.extension.isEmpty() && exLooksHtml(f))) { recents = exAddRecent(ctx, f); htmlView = f; return }
        // music files play right here, in a thin player at the bottom, not in another app
        if (f.extension.lowercase() in EX_AUDIO) { recents = exAddRecent(ctx, f); ExAudio.play(f); return }
        if (f.extension.lowercase() in com.umutk.pulse.OF_EXTS) { recents = exAddRecent(ctx, f); com.umutk.pulse.ofOpen(ctx, f, if (ctx is com.umutk.pulse.FilesActivity) "material" else null); return }
        if (f.extension.lowercase() in com.umutk.pulse.IMG_EXTS || f.extension.lowercase() in setOf("heic", "heif", "avif")) {
            recents = exAddRecent(ctx, f)
            if (!exOpenInGallery(ctx, f)) com.umutk.pulse.imgOpen(ctx, f)
            return
        }
        if (com.umutk.pulse.ofIsText(f)) { recents = exAddRecent(ctx, f); com.umutk.pulse.ofOpenText(ctx, f); return }
        // a file of no known type always asks how to open it (with or without an extension)
        if (f.extension.isEmpty() || fileMime(f) == "*/*") { openAsAsk = f; return }
        recents = exAddRecent(ctx, f)
        if (FilesPrefs.openAsk && exHandOver(ctx, f, false)) return
        Downloads.openFile(ctx, f)
    }
    // a file tapped on the home page: this tab is already in its folder, the archive opens inside it
    LaunchedEffect(com.umutk.pulse.files.home.FilesHomeNav.pendingFile) {
        com.umutk.pulse.files.home.FilesHomeNav.pendingFile?.let { f -> com.umutk.pulse.files.home.FilesHomeNav.pendingFile = null; openFile(f) }
    }
    fun toggle(p: String) { sel = if (p in sel) sel - p else sel + p }
    fun mark(p: String) { marks = if (p in marks) marks - p else marks + p; exWrite(ctx, EX_MARKS, marks) }
    // paste = into the folder on screen; cut moves (and the clipboard empties), copy copies (the clipboard stays); name clashes get a "1-" prefix (see paste)
    val canPaste = clip?.let { c -> !(c.move && c.files.all { it.parentFile == dir }) } == true
    val doPaste: () -> Unit = {
        if (!canPaste) note = tr("Already in this folder", "Zaten bu klasörde")
        else clip?.let { c ->
            note = tr("Pasting…", "Yapıştırılıyor…")
            scope.launch {
                val moved = mutableListOf<Pair<File, File>>()
                val err = withContext(Dispatchers.IO) { paste(ctx, c, dir, moved) }
                val n = c.files.size
                // status line: a failure note wins, else say what happened (a copy keeps the clipboard, a cut empties it)
                note = err.ifEmpty { if (c.move) tr("Moved $n items", "$n öğe taşındı") else tr("Pasted $n items", "$n öğe yapıştırıldı") }
                if (moved.isNotEmpty()) undo = ExUndo(moved)
                // what came out of an archive is a one-time copy: after it lands the paste offer is gone
                if (clip === c) clip = null
                refresh++
            }
        }
    }
    val sortUi: @Composable () -> Unit = {
        ExSortMenu(sortMenu, sort, here in folderSort, filt, FilesPrefs.ext, { FilesPrefs.ext = !FilesPrefs.ext; FilesPrefs.save(ctx) }, showHidden, { showHidden = !showHidden }, { sortMenu = false }, { mode ->
            if (here in folderSort) { folderSort = folderSort + (here to mode); exWriteSortMap(ctx, folderSort) }
            else { sortAll = mode; exWriteInt(ctx, EX_SORT_ALL, mode) }
            sortMenu = false
        }, { type ->
            sortMenu = false
            if (here in filtDir) { filtDir = filtDir + (here to type); exWrite(ctx, "ex_filt_dir", filtDir.map { "${it.key}|${it.value}" }) }
            else { filtAll = type; com.umutk.pulse.filesPrefs(ctx).edit().putString("ex_filt_all", type).apply() }
        }, { on ->
            // one switch for sort and filter in this folder; the menu stays open
            folderSort = if (on) folderSort + (here to sort) else folderSort - here
            exWriteSortMap(ctx, folderSort)
            filtDir = if (on) filtDir + (here to filt) else filtDir - here
            exWrite(ctx, "ex_filt_dir", filtDir.map { "${it.key}|${it.value}" })
        })
    }
    var ovf by remember { mutableStateOf(false) }
    var selMore by remember { mutableStateOf(false) }

    var arcPasteNow: () -> Unit = {}
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // the header stays on screen while the list scrolls under it
        Column(Modifier.padding(horizontal = 12.dp)) {
        if (standalone) Row(verticalAlignment = Alignment.CenterVertically) {
            // declutter: menu, title, search and one overflow menu
            Box(Modifier.size(40.dp).clickable { drawer = true }, contentAlignment = Alignment.Center) { ExGlyph(Icons.Outlined.Menu, "menu", T.text, 24.dp) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                // the standalone app has no "Files" title at the top: the status bar clips it
                if (arcFile != null || dir.path != top.path) Text(if (arcFile != null) arcFile!!.name else if (dir.path == top.path) "Files" else dir.name.ifEmpty { "/" }, color = T.text, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (arcFile == null) {
                    val nd = shown.count { it.isDirectory }
                    Text("$nd ${tr("folders", "klasör")}, ${shown.size - nd} ${tr("files", "dosya")}", color = T.sub, fontSize = 12.sp, maxLines = 1)
                }
            }
            Box(Modifier.size(40.dp).clickable { searchOpen = true }, contentAlignment = Alignment.Center) { ExGlyph(Icons.Outlined.Search, "search", T.text, 22.dp) }
            Box {
                Box(Modifier.size(40.dp).clickable { ovf = true }, contentAlignment = Alignment.Center) { ExGlyph(Icons.Outlined.MoreVert, "more", T.text, 22.dp) }
                val openTool = com.umutk.pulse.LocalOpenTool.current
                val sep: @Composable () -> Unit = { Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(T.line)) }
                ThemedMenu(ovf, { ovf = false }) {
                    @Composable fun row(label: String, ic: androidx.compose.ui.graphics.vector.ImageVector, checked: Boolean? = null, act: () -> Unit) =
                        ThemedMenuItem(label, { ovf = false; act() }, icon = ic, checked = checked)
                    row(if (FilesPrefs.grid) tr("List", "Liste") else tr("Grid", "Izgara"), if (FilesPrefs.grid) Icons.Outlined.ViewList else Icons.Outlined.GridView) { FilesPrefs.grid = !FilesPrefs.grid; FilesPrefs.save(ctx) }
                    row(tr("Sort", "Sırala"), Icons.Outlined.Sort) { sortMenu = true }
                    row(tr("Hidden files", "Gizli dosyalar"), Icons.Outlined.Visibility, showHidden) { showHidden = !showHidden }
                    row(tr("Sizes and dates", "Boyut ve tarih"), Icons.Outlined.Straighten, showSizes) { showSizes = !showSizes }
                    row(tr("Recent", "Son"), Icons.Outlined.History, recentView) { recentView = !recentView; query = "" }
                    row(tr("Empty folders", "Boş klasörler"), Icons.Outlined.FolderOff) { finder = "empty" }
                    row(tr("Large files", "Büyük dosyalar"), Icons.Outlined.SdStorage) { finder = "large" }
                    row(tr("Same names", "Aynı adlılar"), Icons.Outlined.ContentCopy) { finder = "dupes" }
                    sep()
                    row(tr("Select all", "Tümünü seç"), Icons.Outlined.SelectAll) { sel = shown.map { it.path }.toSet() }
                    row(tr("Invert selection", "Seçimi ters çevir"), Icons.Outlined.Deselect) { sel = shown.map { it.path }.toSet() - sel }
                    sep()
                    row(if (dir.path in marks) tr("Remove bookmark", "Yer imini kaldır") else tr("Add bookmark", "Yer imi ekle"), if (dir.path in marks) Icons.Outlined.Star else Icons.Outlined.StarBorder) { mark(dir.path) }
                    row(tr("Home", "Ana klasör"), Icons.Outlined.Home) { go(top.path) }
                    row(tr("Refresh", "Yenile"), Icons.Outlined.Refresh) { AppDataFolders.forget(); refresh++ }
                    row(tr("Properties", "Özellikler"), Icons.Outlined.Info) { propsOf = dir }
                    undo?.let { u -> row(tr("Undo", "Geri al"), Icons.Outlined.Undo) { scope.launch { note = withContext(Dispatchers.IO) { exUndo(u) }; undo = null; refresh++ } } }
                    sep()
                    row(tr("Folder sizes", "Klasör boyutları"), Icons.Outlined.Straighten) { onTab("tree") }
                    row(tr("File types", "Dosya türleri"), Icons.Outlined.Category) { onTab("types") }
                    row(tr("Clean cache", "Önbelleği temizle"), Icons.Outlined.CleaningServices) { onTab("cache") }
                    row(tr("Empty folders", "Boş klasörler"), Icons.Outlined.FolderOff) { finder = "empty" }
                    row(tr("Large files", "Büyük dosyalar"), Icons.Outlined.SdStorage) { finder = "large" }
                    row(tr("Duplicates", "Kopyalar"), Icons.Outlined.FileCopy) { finder = "dupes" }
                    row(tr("Settings", "Ayarlar"), Icons.Outlined.Settings) { openTool("files_settings") }
                }
                sortUi()
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clickable { drawer = true }, contentAlignment = Alignment.Center) { ExGlyph(Icons.Outlined.Menu, "menu", T.text, 24.dp) }
            Spacer(Modifier.weight(1f))
            // tap: the folder sizes of the whole storage; hold: the same tree, opened in the folder you are in
            if (FilesPrefs.analysis) ExToolbar({ onTab(it) }) { k -> if (k == "tree") openTreeAt(dir); onTab(k) }
            val openTool = com.umutk.pulse.LocalOpenTool.current
            if (!full) Box(Modifier.size(36.dp).clickable(onClick = onToggleFull), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.OpenInFull, null, Modifier.size(20.dp), T.sub) }
        }
        // clipboard cue: stays while navigating between folders
        clip?.let { c ->
            Row(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(20.dp)).background(Look.accent.copy(alpha = .16f)).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (c.move) Icons.Outlined.ContentCut else Icons.Outlined.ContentCopy, null, Modifier.size(16.dp), Look.accent)
                val n = c.files.size
                Text(if (c.move) tr("Cut $n", "$n kesildi") else tr("Copied $n", "$n kopyalandı"), Modifier.padding(horizontal = 8.dp), color = T.text, fontSize = 13.sp)
                if (canPaste) Box(Modifier.size(36.dp).clickable { doPaste() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(20.dp), Look.accent) }
                Box(Modifier.size(36.dp).clickable { clip = null }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Close, null, Modifier.size(18.dp), T.sub) }
            }
        }
        }
        val exScroll = rememberScrollState()
        // pull the list down from its top to reload it: the drag is counted here, a release past the line refreshes
        var pull by remember { mutableFloatStateOf(0f) }
        val pullLine = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
        val pullConn = remember {
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (pull > 0f && available.y < 0f) { val d = maxOf(available.y, -pull); pull += d; return androidx.compose.ui.geometry.Offset(0f, d) }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    if (source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput && available.y > 0f) { pull = (pull + available.y * .5f).coerceAtMost(pullLine * 1.5f); return androidx.compose.ui.geometry.Offset(0f, available.y) }
                    return androidx.compose.ui.geometry.Offset.Zero
                }
                override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                    if (pull >= pullLine) { AppDataFolders.forget(); refresh++ }
                    pull = 0f
                    return androidx.compose.ui.unit.Velocity.Zero
                }
            }
        }
        if (pull > 0f) Box(Modifier.fillMaxWidth().height(with(androidx.compose.ui.platform.LocalDensity.current) { pull.toDp() }), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Refresh, null, Modifier.size(22.dp).graphicsLayer { rotationZ = pull / pullLine * 270f; alpha = (pull / pullLine).coerceIn(0f, 1f) }, Look.accent)
        }
        // the folder you left keeps its place: coming back lands where you were
        SideEffect { ExScroll.live = exScroll }
        LaunchedEffect(Unit) { withContext(Dispatchers.IO) { TempCopies.sweep(ctx) } }
        LaunchedEffect(here) {
            val want = ExScroll.memo[here] ?: 0
            if (want > 0) { kotlinx.coroutines.withTimeoutOrNull(1500) { snapshotFlow { exScroll.maxValue }.first { it >= want } }; exScroll.scrollTo(want.coerceAtMost(exScroll.maxValue)) } else exScroll.scrollTo(0)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        Column(Modifier.fillMaxSize().nestedScroll(pullConn).verticalScroll(exScroll).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (bookmarks.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bookmarks.forEach { p -> PChip(here == p, if (p == top.path) "Storage" else File(p).name) { go(p) } }
        }
        val selBar: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DenseSelSquare(T.sub, { sel = emptySet() }) { ExIcon("close", T.sub, 24.dp) }
                Text("${sel.size}", Modifier.weight(1f).padding(start = 4.dp), color = T.text, fontSize = 11.sp, maxLines = 1)
                DenseSelSquare(T.text, { sel = shown.map { it.path }.toSet() }) { ExIcon("selectAll", T.text, 24.dp) }
                // several APKs picked: one button (left of cut) installs them all in one go, in the installer
                if (picked.size >= 2 && picked.all { it.isFile && it.extension.lowercase() in setOf("apk", "xapk", "apks", "apkm") }) DenseSelSquare(Look.accent, {
                    val i = Intent().setClassName(ctx, "com.umutk.pulse.ExtraToolActivity").putExtra("route", "installer").putStringArrayListExtra("apks", ArrayList(picked.map { it.path })).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(i) }; sel = emptySet()
                }, big = true) { ExGlyph(Icons.Outlined.Android, "installer", Look.accent, 24.dp) }
                DenseSelSquare(T.text, { clip = Clip(picked, true); sel = emptySet(); note = tr("${picked.size} cut", "${picked.size} öğe kesildi") }, big = true) { ExIcon("cut", T.text, 24.dp) }
                DenseSelSquare(T.text, { clip = Clip(picked, false); sel = emptySet(); note = tr("${picked.size} copied", "${picked.size} öğe kopyalandı") }, big = true) { ExIcon("copy", T.text, 24.dp) }
                if (canPaste) DenseSelSquare(Look.accent, { sel = emptySet(); doPaste() }, big = true) { ExIcon("paste", Look.accent, 24.dp) }
                // the share button opens the share sheet (files, paths or names); folders in the pick are left out of files and paths
                if (picked.any { it.isFile }) DenseSelSquare(T.text, { shareSheet = picked }) { ExIcon("share", T.text, 24.dp) }
                DenseSelSquare(T.text, { askDelete(picked) }) { ExIcon("delete", T.text, 24.dp) }
                Box {
                    DenseSelSquare(T.text, { selMore = true }) { ExGlyph(Icons.Outlined.MoreVert, "more", T.text, 24.dp) }
                    ThemedMenu(selMore, { selMore = false }) {
                        ThemedMenuItem(tr("Compress", "Sıkıştır"), { selMore = false; zipAsk = picked }, icon = Icons.Outlined.FolderZip)
                        // Share is in this menu too, for one file or for several (folders in the pick are left out)
                        if (picked.any { it.isFile }) ThemedMenuItem(tr("Share", "Paylaş"), { selMore = false; shareSheet = picked }, icon = Icons.Outlined.Share)
                        picked.singleOrNull()?.let { f -> ThemedMenuItem(tr("Rename", "Yeniden adlandır"), { selMore = false; name = f.name; dialog = "rename" to f }, icon = Icons.Outlined.DriveFileRenameOutline) }
                        picked.singleOrNull()?.let { f -> ThemedMenuItem(tr("Properties", "Özellikler"), { selMore = false; propsOf = f }, icon = Icons.Outlined.Info) }
                        picked.singleOrNull()?.takeIf { isArchiveFile(it) }?.let { f -> archiveMenu(f).forEach { (l, k, kind) -> ThemedMenuItem(l, { selMore = false; if (kind == "open") { arcFile = f; arcCwd = ""; arcSel = emptySet(); sel = emptySet() } else arc = ArcReq(kind, f) }, leading = { ExIcon(k, com.umutk.pulse.LocalMenuStyle.current.sub, 18.dp) }) } }
                    }
                }
            }
        }
        // hold anywhere on the card and slide: a loading-style bar along its bottom sets the item size (left = smaller, right = larger);
        // the path and the buttons stay on screen while it runs
        var sizing by remember { mutableStateOf(false) }
        // copy (to the clipboard, then paste into any folder) or extract here, for the entries picked inside an archive
        fun arcCopy(here: Boolean) {
            val af = arcFile ?: return
            val picks = arcSel
            val entries = arcAll.orEmpty()
            scope.launch {
                val dest = if (here) dir else File(ctx.cacheDir, "arcsel/${System.nanoTime()}")
                val files = withContext(Dispatchers.IO) { archiveExtractSel(af, picks, entries, dest) }
                arcSel = emptySet()
                if (here) { note = tr("Extracted ${files.size}", "${files.size} öğe çıkarıldı"); refresh++ }
                else {
                    // a picked folder is copied as the folder, not as the files inside it: the clipboard holds the top-level picks only
                    val tops = picks.map { p -> File(dest, p.removePrefix(p.substringBeforeLast('/', "")).trimStart('/')) }.filter { it.exists() }.distinct()
                    clip = Clip(tops.ifEmpty { files }, false, once = true); note = tr("Copied ${tops.size.coerceAtLeast(1)}: paste in a folder", "${tops.size.coerceAtLeast(1)} öğe kopyalandı: bir klasörde yapıştır")
                }
            }
        }
        // cut: the picks go to the clipboard as a move (paste them in a folder or an archive), and leave the archive
        // so its space comes back (zip-family only)
        fun arcCut() {
            val af = arcFile ?: return
            val picks = arcSel
            val entries = arcAll.orEmpty()
            val expected = archivePicked(picks, entries).size
            scope.launch {
                val dest = File(ctx.filesDir, "arcclip/${System.nanoTime()}")
                // shortcut: the clipboard keeps the top-level picks only; same-named picks from two folders collide in dest
                val done = withContext(Dispatchers.IO) {
                    val files = archiveExtractSel(af, picks, entries, dest)
                    // only what was written out is removed from the archive: a partial extract removes nothing
                    val r = if (files.size != expected) "partial:${files.size}/$expected" else archiveRemove(af, picks)
                    var tops: List<File>? = null
                    if (r.isEmpty()) tops = picks.map { p -> File(dest, p.removePrefix(p.substringBeforeLast('/', "")).trimStart('/')) }.filter { it.exists() }.distinct()
                    else dest.deleteRecursively()
                    CutResult(tops, r)
                }
                val tops = done.tops
                val res = done.reason
                // the archive cannot lose entries (apk, 7z, rar, a failed rewrite): the picks are taken out into this folder instead,
                // and the compress box opens with them so they can go back into a new archive
                if (res.isNotEmpty() && !res.startsWith("partial")) {
                    val out = withContext(Dispatchers.IO) { archiveExtractSel(af, picks, entries, dir) }
                    arcSel = emptySet()
                    val topFiles = picks.map { p -> File(dir, p.removePrefix(p.substringBeforeLast('/', "")).trimStart('/')) }.filter { it.exists() }.distinct()
                    note = tr("Cannot cut from this archive: ${out.size} extracted here instead", "Bu arşivden kesilemiyor: ${out.size} öğe bunun yerine buraya çıkarıldı")
                    if (topFiles.isNotEmpty()) zipAsk = topFiles
                    refresh++
                    return@launch
                }
                arcSel = emptySet()
                arcRev++
                note = when {
                    res.isEmpty() -> { clip = Clip(tops.orEmpty(), true); tr("Cut $expected: paste in a folder or archive", "$expected öğe kesildi: klasöre ya da arşive yapıştır") }
                    res.startsWith("partial") -> tr("Only some could be extracted, nothing removed (${res.substringAfter(':')})", "Bazı öğeler çıkarılamadı, hiçbiri silinmedi (${res.substringAfter(':')})")
                    res == "apk" -> tr("APK files cannot be cut: removing entries breaks the signature", "APK'lerden kesilemez: imza bozulur")
                    res == "unsupported" -> tr("Cut works on zip files only", "Kesme yalnızca zip dosyalarında çalışır")
                    else -> tr("Could not rewrite the archive: $res", "Arşiv yeniden yazılamadı: $res")
                }
                refresh++
            }
        }
        // paste the clipboard into the archive folder on screen (a cut from a folder takes the originals out after it lands)
        fun arcPaste() {
            val af = arcFile ?: return
            val c = clip ?: return
            val entries = c.files.flatMap { f ->
                val base = f.parentFile
                if (base == null) emptyList() else f.walkTopDown().map { e -> (arcCwd + e.relativeTo(base).path.replace(File.separatorChar, '/')) to e }.toList()
            }
            scope.launch {
                val res = withContext(Dispatchers.IO) { archiveAddZip(af, entries) }
                note = when (res) {
                    "" -> tr("Pasted ${c.files.size} items", "${c.files.size} öğe yapıştırıldı")
                    "unsupported" -> tr("Paste into an archive works on zip files only", "Arşive yapıştırma yalnızca zip dosyalarında çalışır")
                    else -> tr("Could not paste: $res", "Yapıştırılamadı: $res")
                }
                if (res.isEmpty()) {
                    if (c.move) { withContext(Dispatchers.IO) { c.files.forEach { it.deleteRecursively() } }; clip = null }
                    else if (clip === c) clip = null
                    arcRev++
                }
            }
        }
        // delete: the picks leave the archive for good (zip-family only)
        fun arcDelete() {
            val af = arcFile ?: return
            val picks = arcSel
            scope.launch {
                val r = withContext(Dispatchers.IO) { archiveRemove(af, picks) }
                arcSel = emptySet(); arcRev++
                note = when (r) {
                    "" -> tr("Deleted ${picks.size} from the archive", "${picks.size} öğe arşivden silindi")
                    "apk" -> tr("APK files cannot be edited: removing entries breaks the signature", "APK'ler düzenlenemez: imza bozulur")
                    "unsupported" -> tr("Deleting works on zip files only", "Silme yalnızca zip dosyalarında çalışır")
                    else -> tr("Could not rewrite the archive: $r", "Arşiv yeniden yazılamadı: $r")
                }
                refresh++
            }
        }
        arcPasteNow = { arcPaste() }
        val arcBar: @Composable () -> Unit = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelSquare(T.sub, { arcSel = emptySet() }) { ExIcon("close", T.sub, 24.dp) }
                Text("${arcSel.size}", Modifier.weight(1f).padding(start = 4.dp), color = T.text, fontSize = 14.sp, maxLines = 1)
                SelSquare(T.text, { arcSel = childrenOf(arcAll.orEmpty(), arcCwd).map { arcCwd + it.name }.toSet() }) { ExIcon("selectAll", T.text, 24.dp) }
                SelSquare(T.text, { arcCopy(false) }) { ExIcon("copy", T.text, 24.dp) }
                SelSquare(T.text, { arcCopy(true) }) { ExIcon("zip", T.text, 24.dp) }
                SelSquare(T.text, { arcCut() }) { ExIcon("cut", T.text, 24.dp) }
                if (clip != null) SelSquare(T.text, { arcPaste() }) { ExIcon("paste", T.text, 24.dp) }
                SelSquare(T.text, { arcDelete() }) { ExIcon("delete", T.text, 24.dp) }
            }
        }
        PCard(Modifier.then(if (sel.isNotEmpty() || arcSel.isNotEmpty()) Modifier.zIndex(1f).graphicsLayer { translationY = exScroll.value.toFloat() } else Modifier).sizeDrag(ctx) { sizing = it }.sizeOverlay(sizing, T.panel, Look.accent)) {
            if (arcFile != null && arcSel.isNotEmpty()) arcBar() else if (sel.isNotEmpty()) selBar() else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (dir != top || arcFile != null) Box(Modifier.clickable { if (arcFile != null) { if (arcCwd.isEmpty()) arcFile = null else arcCwd = arcCwd.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" } } else go((dir.parentFile ?: top).path) }) { PIcon("chevron", T.text, 22.dp, Modifier.rotate(180f)) }
                val cur = buildList<Triple<String, String, () -> Unit>> {
                    val inTop = dir.path == top.path || dir.path.startsWith(top.path + "/")
                    val base = if (inTop) top.path else ""
                    add(Triple("d:" + base, if (inTop) top.name else "/", { go(base.ifEmpty { "/" }) }))
                    var acc = base
                    dir.path.removePrefix(base).split('/').filter { it.isNotEmpty() }.forEach { n -> acc += "/$n"; val q = acc; add(Triple("d:$q", n, { go(q) })) }
                    arcFile?.let { af ->
                        // a forward crumb after going back reopens the archive too (arcFile is cleared by go)
                        add(Triple("a:" + af.path, af.name, { arcFile = af; arcCwd = "" }))
                        var c = ""
                        arcCwd.split('/').filter { it.isNotEmpty() }.forEach { n -> c += "$n/"; val q = c; add(Triple("c:$q", n, { arcFile = af; arcCwd = q })) }
                    }
                }
                // forward trail: stepping back keeps the deeper segments (dimmed); a different folder replaces it
                val hold = remember { arrayOf(listOf<Triple<String, String, () -> Unit>>()) }
                val trail = if (hold[0].size >= cur.size && cur.indices.all { hold[0][it].first == cur[it].first }) hold[0] else cur
                hold[0] = trail
                ExCrumbs(trail.map { it.second to it.third }, cur.lastIndex, Modifier.weight(1f).padding(horizontal = 4.dp))
                if (!standalone) {
                    Box {
                        ExAct(listOf("sortName", "sortSize", "sortDate")[sort / 2], if (here in folderSort) Look.accent else T.sub, if (sort % 2 == 1) 180f else 0f) { sortMenu = true }
                        if (filt != "all") Box(Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 3.dp).size(7.dp).background(Look.accent, CircleShape))
                        sortUi()
                    }
                    undo?.let { u ->
                        ExAct("undo", Look.accent) {
                            scope.launch {
                                note = withContext(Dispatchers.IO) { exUndo(u) }
                                undo = null
                                refresh++
                            }
                        }
                    }
                    // list or grid, icon shows the one a tap switches to (same convention as the standalone app's overflow row)
                    Box {
                        ExAct("view", if (viewOwn != null) Look.accent else T.sub) { viewMenu = true }
                        ThemedMenu(viewMenu, { viewMenu = false }) {
                            val cur = viewNow
                            fun pick(m: Int) {
                                viewMenu = false
                                if (viewOwn != null) { viewDir = viewDir + (here to m); exWriteViewMap(ctx, viewDir) }
                                else { FilesPrefs.grid = m == 1; detailAll = m == 2; FilesPrefs.save(ctx); exWriteInt(ctx, "ex_detail", if (m == 2) 1 else 0) }
                            }
                            ThemedMenuItem(tr("List", "Liste"), { pick(0) }, checked = cur == 0)
                            ThemedMenuItem(tr("Grid", "Izgara"), { pick(1) }, checked = cur == 1)
                            ThemedMenuItem(tr("Detailed list", "Detaylı liste"), { pick(2) }, checked = cur == 2)
                            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(com.umutk.pulse.LocalMenuStyle.current.sub.copy(alpha = .3f)))
                            ThemedMenuItem(tr("Only for this folder", "Yalnızca bu klasör"), {
                                viewMenu = false
                                viewDir = if (viewOwn != null) viewDir - here else viewDir + (here to cur)
                                exWriteViewMap(ctx, viewDir)
                            }, checked = viewOwn != null)
                        }
                    }
                    ExAct(if (dir.path in marks) "starOn" else "star", if (dir.path in marks) Look.accent else T.sub) { mark(dir.path) }
                    ExAct("home", T.text) { go(top.path) }
                }
                // paste sits outside the standalone check, so the Files app shows it too; inside an archive it writes into the archive
                if (arcFile != null) { if (clip != null) ExAct("paste", Look.accent) { arcPaste() } }
                else if (canPaste) ExAct("paste", Look.accent) { doPaste() }
            }
        }
        // compressions still running (they go on when this screen closes) and the result of the last one
        val zipJobs by ZipJobs.jobs.collectAsState()
        val zipEnd by ZipJobs.last.collectAsState()
        LaunchedEffect(zipEnd) { if (zipEnd.isNotEmpty()) { note = zipEnd; ZipJobs.clearLast(); refresh++ } }
        zipJobs.forEach { ZipProgressLine(it) }
        FileJobLines()
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp)
        if (shown.isEmpty() && arcFile == null) Text(if (recentView) "No recent files" else if (RootFs.needsRoot(here) && (!canRoot || Shell.root != true)) tr("Root is needed to list this folder", "Bu klasörü listelemek için root gerekir") else if (rootErr.isNotEmpty()) rootErr else "No files here", color = T.sub, fontSize = 13.sp)
        val item: @Composable (File) -> Unit = { f ->
            val grid = gridNow
            val isz = (24 + FilesPrefs.icon.coerceIn(0, 9) * 5).let { if (grid) it * 3 / 2 else it }
            // the names grow with the icons (list rows, and the tiles of the grid)
            val ts = (isz / 40f).coerceIn(1f, 1.5f)
            val tsg = (isz / 60f).coerceIn(1f, 1.6f)
            val shownName = if (!FilesPrefs.ext && !f.isDirectory) f.nameWithoutExtension.ifEmpty { f.name } else f.name
            val isMark = f.path in marks
            val isPicked = f.path in sel
            val cutA = if (clip?.let { it.move && f in it.files } == true) .5f else 1f
            val copied = clip?.let { !it.move && f in it.files } == true
            // folders keep their card; files are plain rows in a list
            val rowBody: @Composable () -> Unit = {
                // one row: the name on the left, the three dots (menu) on the right of the same line
                val more: @Composable () -> Unit = {
                Box {
                    Box(Modifier.size(36.dp).clickable { menuOf = f }, contentAlignment = Alignment.Center) { MoreIcon(T.sub, 20.dp) }
                    val actions = buildList<Triple<String, String, () -> Unit>> {
                        add(Triple("Open", "view", { if (f.isDirectory) go(f.path) else openFile(f) }))
                        if (!f.isDirectory) add(Triple("Open with", "openWith", { if (exHandOver(ctx, f, false)) recents = exAddRecent(ctx, f) else note = "Could not open ${f.name}" }))
                        add(Triple("Cut", "move", { clip = Clip(listOf(f), true) }))
                        add(Triple("Copy", "copy", { clip = Clip(listOf(f), false) }))
                        if (canPaste) add(Triple("Paste", "paste", { doPaste() }))
                        add(Triple("Delete", "delete", { askDelete(listOf(f)) }))
                        add(Triple("Rename", "rename", { name = f.name; dialog = "rename" to f }))
                        add(Triple("Compress", "zip", { zipAsk = listOf(f) }))
                        if (!f.isDirectory) add(Triple("Share", "share", { if (!exHandOver(ctx, f, true)) note = "Could not share ${f.name}" }))
                        if (!f.isDirectory) add(Triple("Save as", "copy", { saveAsFile = f }))
                        add(Triple("Copy path", "document", { exCopy(ctx, f.path); note = "Path copied" }))
                        if (f.isDirectory) {
                            add(Triple(if (f.path in marks) "Remove bookmark" else "Add bookmark", "bookmark", { mark(f.path) }))
                            add(Triple("Create shortcut", "link", { mark(f.path) }))
                        }
                        if (isArchiveFile(f)) archiveMenu(f).forEach { (l, k, kind) -> add(Triple(l, k, { if (kind == "open") { arcFile = f; arcCwd = ""; arcSel = emptySet(); sel = emptySet() } else arc = ArcReq(kind, f, if (kind == "add" && f.path in sel) picked else listOf(f)) })) }
                        if (isFirstPart(f)) add(Triple(tr("Join parts", "Parçaları birleştir"), "zip", { scope.launch { note = withContext(Dispatchers.IO) { joinParts(f) }; refresh++ } }))
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
                if (grid) Column(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(SEL_SHAPE).background(if (isPicked) Look.accent.copy(alpha = .18f) else Color.Transparent), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.fillMaxWidth().combinedClickable(
                    onClick = { if (sel.isNotEmpty()) toggle(f.path) else if (f.isDirectory) go(f.path) else openFile(f) },
                    onLongClick = { toggle(f.path) }), horizontalAlignment = Alignment.CenterHorizontally) {
                    // an app's data folder shows that app's icon; other folders keep the folder icon
                    val thumb = if (f.isDirectory) AppDataFolders.icon(ctx, f.path) else (exThumb(f) ?: exPreview(f))
                    ExBadged(f, isz.dp) {
                        // one rounded square for every file: the picture when there is one, the type icon on the panel colour otherwise
                        val square = RoundedCornerShape(percent = 22)
                        if (thumb != null) Image(thumb, null, Modifier.size(isz.dp).clip(square), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                        else Box(Modifier.size(isz.dp).clip(square).background(T.panel), contentAlignment = Alignment.Center) {
                            ExIcon(if (f.isDirectory) "folder" else "filetypes", Look.accent, (isz * 11 / 20).dp)
                        }
                    }
                    Text(shownName, Modifier.fillMaxWidth().padding(top = 4.dp), color = T.text, fontSize = (12 * tsg).sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Box(Modifier.align(Alignment.TopEnd)) { more() }
                }
                // the pick pill is thicker than the row's content (4 dp above and below it); the gaps between rows stay small
                else Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 1.dp).clip(SEL_SHAPE).background(if (isPicked) Look.accent.copy(alpha = .18f) else Color.Transparent).padding(vertical = 2.dp).heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 44.dp).combinedClickable(
                    onClick = { if (sel.isNotEmpty()) toggle(f.path) else if (f.isDirectory) go(f.path) else openFile(f) },
                    onLongClick = { toggle(f.path) }), verticalAlignment = Alignment.CenterVertically) {
                    // an app's data folder shows that app's icon; other folders keep the folder icon
                    val thumb = if (f.isDirectory) AppDataFolders.icon(ctx, f.path) else (exThumb(f) ?: exPreview(f))
                    ExBadged(f, maxOf(isz, 40).dp) {
                        if (thumb != null) Image(thumb, null, Modifier.size(maxOf(isz, 40).dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.High)
                        else ExIcon(if (f.isDirectory) "folder" else "filetypes", Look.accent, (isz * 11 / 20).dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Text(shownName, color = T.text, fontSize = (16 * ts).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = when {
                            found != null -> exRel(f, top)
                            !f.isDirectory && (showSizes || detailNow) -> exSize(f.length()) + "  ·  " + exDate(f.lastModified(), FilesPrefs.dateFmt)
                            // the folder's own total size was already measured in the background either way (dirSizes above); it just was not shown
                            // outside the standalone app — now the embedded Files tab gets it too
                            f.isDirectory && (showSizes || detailNow) && !f.path.startsWith("/data") -> "${f.list()?.size ?: 0} ${tr("items", "öğe")}  ·  " + (dirSizes[f.path]?.let { exSize(it) + "  ·  " } ?: "") + exDate(f.lastModified(), FilesPrefs.dateFmt)
                            else -> ""
                        }
                        if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = (11 * ts).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                more()
                }
            }
            if (standalone) {
                // same row for folders and files; optional card look (own background, rounded, 2dp gaps)
                if (FilesPrefs.cards) {
                    val base = if (FilesLook.theme == "amoled") Color(0xFF0A0A0A) else T.panel
                    val bgc = if (FilesPrefs.alt && shown.indexOf(f) % 2 == 1) T.text.copy(alpha = .05f).compositeOver(base) else base
                    Box(Modifier.fillMaxWidth().padding(vertical = 1.dp).clip(RoundedCornerShape(listOf(8, 14, 22)[FilesPrefs.radius.coerceIn(0, 2)].dp)).background(bgc).padding(horizontal = 10.dp, vertical = 2.dp)) { Box(Modifier.alpha(cutA)) { rowBody(); if (copied) Icon(Icons.Outlined.ContentCopy, null, Modifier.align(Alignment.TopEnd).padding(end = 40.dp).size(12.dp), Look.accent) } }
                } else Box(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { Box(Modifier.alpha(cutA)) { rowBody(); if (copied) Icon(Icons.Outlined.ContentCopy, null, Modifier.align(Alignment.TopEnd).padding(end = 40.dp).size(12.dp), Look.accent) } }
            } else if (f.isDirectory && (FilesPrefs.folderCards || !T.material)) PCard(Modifier.fillMaxWidth()) { Box(Modifier.alpha(cutA)) { rowBody(); if (copied) Icon(Icons.Outlined.ContentCopy, null, Modifier.align(Alignment.TopEnd).padding(end = 40.dp).size(12.dp), Look.accent) } } else Box(Modifier.fillMaxWidth().padding(vertical = 1.dp)) { Box(Modifier.alpha(cutA)) { rowBody(); if (copied) Icon(Icons.Outlined.ContentCopy, null, Modifier.align(Alignment.TopEnd).padding(end = 40.dp).size(12.dp), Look.accent) } }
        }
        arcFile?.let { af ->
            val all = arcAll
            val rows = remember(all, arcCwd) { childrenOf(all.orEmpty(), arcCwd) }
            val fmt = remember { java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT) }
            if (all == null) Text(tr("Reading…", "Okunuyor…"), color = T.sub, fontSize = 13.sp)
            else if (rows.isEmpty()) Text(tr("Empty", "Boş"), color = T.sub, fontSize = 13.sp)
            // shortcut: only the first 400 rows of an archive folder are drawn, a lazy list is the upgrade
            rows.take(400).forEach { r ->
                val p = arcCwd + r.name
                val picked = p in arcSel
                // tap opens (or enters a folder); once something is picked, tap toggles, and a long press picks
                Row(Modifier.fillMaxWidth().clip(SEL_SHAPE).background(if (picked) Look.accent.copy(alpha = .18f) else Color.Transparent).combinedClickable(
                    onClick = {
                        if (arcSel.isNotEmpty()) arcSel = if (picked) arcSel - p else arcSel + p
                        else if (r.dir) { arcCwd = "$p/" } else scope.launch {
                            val out = File(ctx.cacheDir, "arc/${System.nanoTime()}/${r.name}")
                            val ok = withContext(Dispatchers.IO) { runCatching { archiveExtractOne(af, p, out) }.getOrDefault(false) }
                            if (ok) openFile(out) else note = tr("Could not read the entry", "Öğe okunamadı")
                        }
                    },
                    onLongClick = { arcSel = if (picked) arcSel - p else arcSel + p },
                ).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    ExIcon(if (r.dir) "folder" else "filetypes", Look.accent, 22.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = ((if (r.size > 0 || !r.dir) exSize(r.size) + "  " else "") + (if (r.time > 0) exDate(r.time, FilesPrefs.dateFmt) else "")).trim()
                        if (sub.isNotEmpty()) Text(sub, color = T.sub, fontSize = 11.sp)
                    }
                    // the three dots: delete and info for this row (open, cut, copy and paste are a tap and the selection bar)
                    Box {
                        Box(Modifier.size(36.dp).clickable { arcMenu = p }, contentAlignment = Alignment.Center) { MoreIcon(T.sub, 20.dp) }
                        ThemedMenu(arcMenu == p, { arcMenu = null }) {
                            val items = buildList<Triple<String, String, () -> Unit>> {
                                add(Triple(tr("Delete", "Sil"), "delete", { arcSel = setOf(p); arcDelete() }))
                                add(Triple(tr("Info", "Bilgi"), "info", { arcInfo = p }))
                            }
                            items.forEach { (label, key, act) -> ThemedMenuItem(label, { arcMenu = null; act() }, leading = { ExIcon(key, com.umutk.pulse.LocalMenuStyle.current.sub, 18.dp) }) }
                        }
                    }
                }
            }
        }
        // the call recordings folder has a page of its own: the people called, and the calls by day
        if (arcFile == null && isCallRecordings(dir)) CallPane(shownNow) { f -> recents = exAddRecent(ctx, f); ExAudio.play(f) }
        else if (gridNow) BoxWithConstraints(Modifier.fillMaxWidth()) {
            // a tile is its icon plus a little room for the name; the gap between tiles is the same small one at every size,
            // so a bigger icon only means fewer columns, and the cells share the width evenly
            val gap = 4.dp
            val tile = ((24 + FilesPrefs.icon.coerceIn(0, 9) * 5) * 3 / 2 + 8).dp
            val cols = ((maxWidth + gap) / (tile + gap)).toInt().coerceAtLeast(1)
            val cell = (maxWidth - gap * (cols - 1)) / cols
            shownNow.chunked(cols).forEach { r -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                r.forEach { f -> Box(Modifier.width(cell).clipToBounds(), contentAlignment = Alignment.TopCenter) { item(f) } }
            } }
        } else shownNow.forEachIndexed { i, f -> item(f); if (FilesPrefs.rowSep && i < shownNow.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(T.line)) }
        Spacer(Modifier.height(88.dp))
        }
        // a thin handle on the right edge: drag it to run through a long list quickly
        val fsScope = rememberCoroutineScope()
        BoxWithConstraints(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(28.dp)) {
            val track = constraints.maxHeight.toFloat()
            if (exScroll.maxValue > track * 2) {
                val thumbH = 56.dp
                val thumbPx = with(androidx.compose.ui.platform.LocalDensity.current) { thumbH.toPx() }
                val frac = exScroll.value.toFloat() / exScroll.maxValue
                Box(Modifier.offset { androidx.compose.ui.unit.IntOffset(0, ((track - thumbPx) * frac).toInt()) }.align(Alignment.TopEnd).width(28.dp).height(thumbH)
                    .pointerInput(Unit) {
                        detectDragGestures { c, drag ->
                            c.consume()
                            val room = (track - thumbPx).coerceAtLeast(1f)
                            val f2 = (exScroll.value.toFloat() / exScroll.maxValue + drag.y / room).coerceIn(0f, 1f)
                            fsScope.launch { exScroll.scrollTo((f2 * exScroll.maxValue).toInt()) }
                        }
                    }, contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.padding(end = 3.dp).width(5.dp).height(thumbH).clip(androidx.compose.foundation.shape.RoundedCornerShape(3.dp)).background(Look.accent.copy(alpha = .65f)))
                }
            }
        }
        }
    }
    resultsFor?.let { q ->
        Column(Modifier.fillMaxSize().background(T.bg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clickable { resultsFor = null; searchOpen = false; query = "" }, contentAlignment = Alignment.Center) { ExIcon("back", T.text, 22.dp) }
                Text(q, Modifier.weight(1f), color = T.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val hits = found
            if (hits == null) Text(tr("Searching…", "Aranıyor…"), Modifier.padding(16.dp), color = T.sub, fontSize = 13.sp)
            else if (hits.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), Modifier.padding(16.dp), color = T.sub, fontSize = 13.sp)
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(hits.size) { i ->
                    val f = hits[i]
                    Row(Modifier.fillMaxWidth().clickable { if (f.isDirectory) { resultsFor = null; searchOpen = false; go(f.path) } else openFile(f) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ExIcon(if (f.isDirectory) "folder" else "filetypes", Look.accent, 22.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(exRel(f, top), color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.size(40.dp).clickable { resultsFor = null; searchOpen = false; go(if (f.isDirectory) f.path else f.parentFile?.path ?: top.path) }, contentAlignment = Alignment.Center) { ExIcon("openFolder", T.sub, 20.dp) }
                    }
                }
            }
        }
    }
    if (arcFile == null) ExSearchBar(standalone, searchOpen, { searchOpen = true }, query, { query = it }, { if (query.isNotBlank()) resultsFor = query.trim() }, { searchOpen = false; query = ""; resultsFor = null; searchAll = false }, searchAll) { searchAll = !searchAll; if (query.trim().length >= 2) resultsFor = query.trim() }
    if (recentView) RecentScreen({ openFile(it) }) { recentView = false }
    ExMiniPlayer()
    htmlView?.let { h -> ExHtmlViewer(h, { htmlView = null }) { htmlView = null; com.umutk.pulse.ofOpenText(ctx, h) } }
    arcInfo?.let { p ->
        val af = arcFile
        val all = arcAll.orEmpty()
        val e = all.firstOrNull { it.path == p }
        val under = all.filter { it.path.startsWith("$p/") && !it.dir }
        com.umutk.pulse.WinAlert({ arcInfo = null }, title = { Text(p.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(tr("Path", "Yol") + ": " + p, fontSize = 13.sp)
                    if (af != null) Text(tr("In", "İçinde") + ": " + af.name, fontSize = 13.sp)
                    if (e != null && !e.dir) {
                        Text(tr("Size", "Boyut") + ": " + exSize(e.size), fontSize = 13.sp)
                        Text(tr("Packed", "Sıkıştırılmış") + ": " + exSize(e.packed), fontSize = 13.sp)
                    } else {
                        Text(tr("Files", "Dosya") + ": ${under.size}", fontSize = 13.sp)
                        Text(tr("Size", "Boyut") + ": " + exSize(under.sumOf { it.size }), fontSize = 13.sp)
                    }
                    if (e != null && e.time > 0) Text(tr("Modified", "Değiştirilme") + ": " + exDate(e.time, FilesPrefs.dateFmt), fontSize = 13.sp)
                }
            },
            confirmButton = { androidx.compose.material3.TextButton({ arcInfo = null }) { Text(tr("Close", "Kapat")) } })
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { ExAudio.stop() } }
    if (arcFile == null && resultsFor == null) clip?.let { c -> ExPasteFab(c.files.size, c.move, { doPaste() }) { clip = null } }
    // inside an archive the same button writes into the archive (the bar's paste can be pushed out of view by its other buttons)
    if (arcFile != null && resultsFor == null) clip?.let { c -> ExPasteFab(c.files.size, c.move, { arcPasteNow() }) { clip = null } }
    if (arcFile == null && resultsFor == null) ExNewFab { k -> name = ""; if (k == "folder") dialog = "newfolder" to dir else { newExt = k; dialog = "newfile" to dir } }
    if (drawer) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f)).clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { drawer = false })
        Column(Modifier.fillMaxHeight().width(280.dp).background(T.panel).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            val pick: (String) -> Unit = { go(it); drawer = false }
            var rm by remember { mutableStateOf<String?>(null) }
            ExDrawerRow("disk", tr("Internal storage", "Dahili depolama"), here == top.path, sub = if (FilesPrefs.storageInfo) tr("${Guard.gb(free)} free of ${Guard.gb(total)}", "${Guard.gb(total)} içinde ${Guard.gb(free)} boş") else null,
                bar = if (FilesPrefs.storageInfo && total > 0) (total - free).toFloat() / total else null) { pick(top.path) }
            // an inserted SD card or USB drive is a slot of its own: the other app-specific dirs sit on the removable volumes
            ctx.getExternalFilesDirs(null).drop(1).filterNotNull().map { it.path.substringBefore("/Android") }.filter { File(it).canRead() }.forEach { v ->
                ExDrawerRow("disk", tr("SD card", "SD kart") + " · " + File(v).name, here.startsWith(v)) { pick(v) }
            }
            if (canRoot) ExDrawerRow("folder", tr("Root (/)", "Kök (/)"), here == "/") { pick("/") }
            if (canRoot) ExDrawerRow("folder", tr("App data (/data/data)", "Uygulama verisi (/data/data)"), here == "/data/data") { pick("/data/data") }
            else ExDrawerRow("folder", tr("Root: not available", "Kök: yok"), false, dim = true) { }
            // the Library: the usual folders of internal storage; each one can be switched off in Files settings (Library)
            val lib = FilesPrefs.libFolders.filter { it !in FilesPrefs.libHidden }.map { File(top, it).path }.filter { File(it).isDirectory }
            if (FilesPrefs.stdFolders && lib.isNotEmpty()) { ExDrawerDivider(); ExDrawerHead(tr("Library", "Kütüphane")) }
            if (FilesPrefs.stdFolders) lib.forEach { p ->
                ExDrawerRow("folderY", File(p).name, here == p, glyph = when (File(p).name) {
                    "DCIM" -> Icons.Outlined.PhotoCamera; "Download" -> Icons.Outlined.Download; "Pictures" -> Icons.Outlined.Image
                    "Screenshots" -> Icons.Outlined.Image; "Movies" -> Icons.Outlined.Movie; "Music" -> Icons.Outlined.MusicNote; else -> Icons.Outlined.Description }) { pick(p) }
            }
            if (FilesPrefs.bookmarks && marks.isNotEmpty()) {
                ExDrawerDivider(); ExDrawerHead(tr("Bookmarks", "Yer imleri"))
                marks.forEach { p -> Box {
                    ExDrawerRow("bookmark", File(p).name.ifEmpty { "/" }, here == p, onLong = { rm = p }) { pick(p) }
                    ThemedMenu(rm == p, { rm = null }) { ThemedMenuItem(tr("Remove", "Kaldır"), { rm = null; mark(p) }, leading = { ExIcon("delete", com.umutk.pulse.LocalMenuStyle.current.sub, 18.dp) }) }
                } }
            }
            ExDrawerRow("recent", tr("Recent", "Son"), false) { drawer = false; sel = emptySet(); recentView = true }
            // the most opened folders, apart from the fixed ones above
            val defaults = listOf(File(top, "DCIM").path, File(top, "Download").path)
            val often = freq.mapNotNull { l -> l.substringAfterLast('|').toIntOrNull()?.let { l.substringBeforeLast('|') to it } }
                .sortedByDescending { it.second }.map { it.first }.filter { it !in defaults }.take(6)
            if (often.isNotEmpty()) ExDrawerHead(tr("Frequent", "Sık kullanılanlar"))
            often.forEach { p -> ExDrawerRow("history", File(p).name.ifEmpty { "/" }, here == p) { pick(p) } }
            ExDrawerDivider()
            if (Opt.recycle) ExDrawerRow("delete", tr("Trash", "Çöp kutusu"), false) { drawer = false; openToolFn("recycle") }
            ExDrawerRow("settings", tr("Settings", "Ayarlar"), false) { drawer = false; openToolFn("files_settings") }
            ExDrawerRow("info", tr("About", "Hakkında"), false) { drawer = false; about = true }
        }
    }
    }

    if (about) WinAlert(onDismissRequest = { about = false }, title = { Text("Pulse Files") },
        text = { Text(tr("Files for Pulse. Browse, search, archives, bookmarks.", "Pulse için dosyalar. Gezin, arayın, arşivler, yer imleri."), color = T.sub, fontSize = 13.sp) },
        confirmButton = { TextButton({ about = false }) { Text(tr("Close", "Kapat")) } })
    viewing?.let { f -> FileViewer(f, { openFile(it) }) { viewing = null } }

    arc?.let { r ->
        ArchiveHost(r, { arc = null }, { note = it }, { refresh++ }) { v ->
            if (v.extension.lowercase() in TEXT_EXT || v.extension.lowercase() in IMAGE_EXT) viewing = v else Downloads.openFile(ctx, v)
        }
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
        val title = if (files.size > 1) "Delete Items" else if (files[0].isDirectory) "Delete Folder" else "Delete File"
        val msg = if (Opt.recycle) (if (files.size == 1) tr("Move this item to the Recycle Bin?", "Bu öğe Çöp Kutusuna taşınsın mı?") else tr("Move these ${files.size} items to the Recycle Bin?", "Bu ${files.size} öğe Çöp Kutusuna taşınsın mı?"))
            else (if (files.size == 1) tr("Are you sure you want to permanently delete this item?", "Bu öğeyi kalıcı olarak silmek istediğinizden emin misiniz?") else tr("Are you sure you want to permanently delete these ${files.size} items?", "Bu ${files.size} öğeyi kalıcı olarak silmek istediğinizden emin misiniz?"))
        if (T.material) MaterialDeleteDialog(title, msg, { bulk = null }) { bulk = null; deleteNow(files) }
        else {
        WinAlert(onDismissRequest = { bulk = null },
            title = { Text(if (files.size > 1) "Delete Items" else if (files[0].isDirectory) "Delete Folder" else "Delete File") },
            text = { Text(if (Opt.recycle) (if (files.size == 1) tr("Move this item to the Recycle Bin?", "Bu öğe Çöp Kutusuna taşınsın mı?") else tr("Move these ${files.size} items to the Recycle Bin?", "Bu ${files.size} öğe Çöp Kutusuna taşınsın mı?"))
                else (if (files.size == 1) tr("Are you sure you want to permanently delete this item?", "Bu öğeyi kalıcı olarak silmek istediğinizden emin misiniz?") else tr("Are you sure you want to permanently delete these ${files.size} items?", "Bu ${files.size} öğeyi kalıcı olarak silmek istediğinizden emin misiniz?")), fontSize = 13.sp) },
            // the Windows looks draw their own button, so the red text stays on Material only
            confirmButton = { TextButton({ bulk = null; deleteNow(files) }) { Text("Delete", color = if (com.umutk.pulse.LocalWindowLook.current.id in com.umutk.pulse.winButtonLooks) Color.Unspecified else Color(0xFFC42B1C)) } },
            dismissButton = { TextButton({ bulk = null }) { Text(tr("Cancel", "İptal")) } }, iconKey = if (Opt.recycle) "recycle" else "close")
        }
    }

    zipAsk?.let { files ->
        ZipAskDialog(files, { zipAsk = null }) { name, kind, bytes -> zipAsk = null; ZipJobs.start(ctx, files, name, dir, kind, bytes) }
    }
    shareSheet?.let { picks ->
        ExShareSheet(picks, { shareSheet = null }, { note = it })
    }

    openAsAsk?.let { f ->
        val mat = T.material
        // Material: quiet tonal rows with soft corners and a plain Cancel text button at the right; the Windows looks keep their own buttons
        @Composable fun item(label: String, go: () -> Unit) {
            if (mat) Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(T.text.copy(alpha = .07f)).clickable(onClick = go).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(label, color = T.text, fontSize = 15.sp)
            } else PButton(label, Modifier.fillMaxWidth(), onClick = go)
        }
        WinAlert(onDismissRequest = { openAsAsk = null }, iconKey = "info",
            title = { Text(tr("Open as", "Şu olarak aç")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(f.name, fontSize = 12.sp, color = T.sub, maxLines = 2, modifier = Modifier.padding(bottom = 4.dp))
                item(tr("Archive", "Arşiv")) { openAsAsk = null; ArcForce.paths += f.path; arcFile = f; arcCwd = ""; sel = emptySet() }
                item("HTML") { openAsAsk = null; recents = exAddRecent(ctx, f); htmlView = f }
                item(tr("Text", "Metin")) { openAsAsk = null; recents = exAddRecent(ctx, f); com.umutk.pulse.ofOpenText(ctx, f) }
                item(tr("Image", "Resim")) { openAsAsk = null; recents = exAddRecent(ctx, f); com.umutk.pulse.imgOpen(ctx, f) }
                if (f.extension.lowercase() in EX_VIDEO) {
                    item(tr("Video player", "Video oynatıcı")) { openAsAsk = null; recents = exAddRecent(ctx, f); if (!exOpenInPulseVideo(ctx, f)) note = "Could not open ${f.name}" }
                    item(tr("Gallery", "Galeri")) { openAsAsk = null; recents = exAddRecent(ctx, f); if (!exOpenInGallery(ctx, f)) note = "Could not open ${f.name}" }
                }
                item(tr("Save as…", "Farklı kaydet…")) { openAsAsk = null; saveAsFile = f }
                item(tr("Another app…", "Başka uygulama…")) { openAsAsk = null; if (exHandOver(ctx, f, false)) recents = exAddRecent(ctx, f) else note = "Could not open ${f.name}" }
            } },
            confirmButton = { TextButton({ openAsAsk = null }) { Text(tr("Cancel", "İptal")) } })
    }
    // Save as: a copy under a name and in a folder the user picks (the file may have no known type to open it with)
    saveAsFile?.let { f ->
        var newName by remember(f) { mutableStateOf(f.name) }
        val roots = listOf(dir, File(top, "Download"), File(top, "Documents"), File(top, "DCIM"), File(top, "Pictures"), File(top, "Music")).filter { it.isDirectory }.distinctBy { it.path }
        var target by remember(f) { mutableStateOf(roots.first()) }
        WinAlert(onDismissRequest = { saveAsFile = null }, title = { Text(tr("Save as", "Farklı kaydet")) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                com.umutk.pulse.PNameField(newName, { newName = it }, tr("Name", "Ad"), Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roots.forEach { d -> PChip(target.path == d.path, if (d.path == dir.path) tr("Here", "Burası") else d.name) { target = d } }
                }
            } },
            confirmButton = { TextButton({
                val clean = newName.trim().ifEmpty { f.name }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                var out = File(target, clean); var n = 2
                while (out.exists()) out = File(target, clean.substringBeforeLast('.', clean) + " ($n)" + clean.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }.also { n++ })
                saveAsFile = null
                scope.launch { note = withContext(Dispatchers.IO) { runCatching { f.copyTo(out); tr("Saved ${out.name}", "${out.name} kaydedildi") }.getOrElse { tr("Could not save", "Kaydedilemedi") } }; refresh++ }
            }) { Text(tr("Save", "Kaydet")) } },
            dismissButton = { TextButton({ saveAsFile = null }) { Text(tr("Cancel", "İptal")) } })
    }
    apkAsk?.let { a -> ApkInfo(a, { apkAsk = null }) { arcFile = a; arcCwd = ""; apkAsk = null } }
    dialog?.let { (kind, f) ->
        WinAlert(onDismissRequest = { dialog = null },
            title = { Text(if (kind == "rename") "Rename" else if (kind == "newfile") tr("New file", "Yeni dosya") + " (." + newFileSplit(name, newExt).second + ")" else "New folder") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (kind == "rename") {
                    // the name is already selected (without its extension) and the keyboard is up: type to replace it
                    var tv by remember(f) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(name, androidx.compose.ui.text.TextRange(0, name.lastIndexOf('.').let { if (it > 0) it else name.length }))) }
                    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
                    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() }; kb?.show() }
                    androidx.compose.material3.OutlinedTextField(tv, { tv = it; name = it.text }, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(fr))
                } else
                PField(name, { name = it }, "Name", Modifier.fillMaxWidth())
                if (kind == "newfile") Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // a chip sets the extension; one already typed in the name is replaced by it
                    (listOf("docx", "xlsx", "pptx", "pdf", "txt") + tr("Other…", "Diğer…")).forEachIndexed { i, e ->
                        val other = i == 5
                        PChip(if (other) newExt !in listOf("docx", "xlsx", "pptx", "pdf", "txt") else newExt == e, if (other) e else ".$e") {
                            val (b, x) = newFileSplit(name, newExt)
                            if (other) { newExt = "other" } else { newExt = e; if (name.trim().let { it.lastIndexOf('.') in 1 until it.length - 1 }) name = "$b.$e" }
                        }
                    }
                }
            } },
            confirmButton = { TextButton({
                if (kind == "newfile") {
                    val (b, x) = newFileSplit(name, newExt)
                    val made = runCatching { com.umutk.pulse.ofCreateIn(f, b.ifBlank { "New" }, x) }.getOrNull()
                    note = if (made != null) "" else "Could not use that name"
                    dialog = null; refresh++
                    return@TextButton
                }
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

/** "name.ext" typed in the new-file box -> (name, extension); without a usable extension the chosen one counts ("other" = txt). */
private fun newFileSplit(typed: String, chosen: String): Pair<String, String> {
    val t = typed.trim()
    val i = t.lastIndexOf('.')
    val typedExt = if (i in 1 until t.length - 1) t.substring(i + 1).filter { it.isLetterOrDigit() || it in "_+-" } else ""
    val ext = typedExt.ifEmpty { if (chosen == "other") "txt" else chosen }.let { if (it.lowercase() in setOf("docx", "xlsx", "pptx", "pdf")) it.lowercase() else it }
    return (if (typedExt.isNotEmpty()) t.substring(0, i) else t) to ext
}

private const val SIZE_PAD_DP = 16

/** The outline of a selected item, in the list, the grid and inside archives: rounded corners, never a half circle. */
private val SEL_SHAPE = RoundedCornerShape(16.dp)

/** Press and hold the row, then slide: the finger position sets the item size live (0 at the left edge, 9 at the right); saved on release. */
private fun Modifier.sizeDrag(ctx: android.content.Context, active: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    fun setAt(x: Float) {
        val pad = SIZE_PAD_DP.dp.toPx()
        val w = (size.width - 2 * pad).coerceAtLeast(1f)
        FilesPrefs.icon = (((x - pad) / w).coerceIn(0f, 1f) * 9f).roundToInt()
    }
    detectDragGesturesAfterLongPress(
        onDragStart = { o -> active(true); setAt(o.x) },
        onDragEnd = { active(false); FilesPrefs.save(ctx) },
        onDragCancel = { active(false); FilesPrefs.save(ctx) },
        onDrag = { change, _ -> change.consume(); setAt(change.position.x) },
    )
}

/** While [on], the whole card becomes the size bar: the part up to the item size is filled, the rest is dimmed, and a handle marks the edge. */
private fun Modifier.sizeOverlay(on: Boolean, bg: Color, fill: Color): Modifier = drawWithContent {
    drawContent()
    if (!on) return@drawWithContent
    val frac = FilesPrefs.icon.coerceIn(0, 9) / 9f
    // the same x as sizeDrag reads: the usable width sits inside the side padding
    val pad = SIZE_PAD_DP.dp.toPx()
    val x = pad + (size.width - 2 * pad) * frac
    drawRect(bg.copy(alpha = .75f))
    drawRect(fill.copy(alpha = .3f), size = androidx.compose.ui.geometry.Size(x, size.height))
    drawRect(fill, androidx.compose.ui.geometry.Offset(x - 2.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
}

/** The path as clickable segments with chevrons between; it scrolls sideways and stays at the end. */
@Composable
private fun ExCrumbs(segs: List<Pair<String, () -> Unit>>, curIdx: Int, modifier: Modifier) {
    val sc = rememberScrollState()
    LaunchedEffect(segs.map { it.first }, curIdx) { snapshotFlow { sc.maxValue }.collect { sc.scrollTo(it) } }
    // the folder on screen is light grey, the other segments dark grey; never white (dark looks: both greys sit on the light side, AMOLED included)
    val darkLook = T.bg.luminance() < .5f
    val curC = if (darkLook) Color(0xFFC8C8C8) else Color(0xFF9A9A9A)
    val othC = if (darkLook) Color(0xFF8A8A8A) else Color(0xFF4A4A4A)
    // scrolls by hand only when the path is longer than the room; otherwise the free space belongs to the size drag of the row
    Row(modifier.horizontalScroll(sc, enabled = sc.maxValue > 0), verticalAlignment = Alignment.CenterVertically) {
        segs.forEachIndexed { i, (n, act) ->
            // chevron pointing right between two segments (the down chevron turned a quarter back)
            if (i > 0) PIcon("chevron", othC, 14.dp, Modifier.rotate(-90f))
            Box(Modifier.padding(end = 2.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = act).padding(horizontal = 6.dp, vertical = 5.dp)) {
                Text(n, color = if (i == curIdx) curC else othC, fontSize = 14.sp, fontWeight = if (i == curIdx) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ExDrawerHead(t: String) = Text(t, Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp), color = T.sub, fontSize = 12.sp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExDrawerRow(icon: String, label: String, on: Boolean, dim: Boolean = false, glyph: ImageVector? = null, sub: String? = null, bar: Float? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    // Material: a rounded pill marks the open entry and the outline icons keep the theme colours; the other looks keep the flat bar and yellow folders
    // the separate-rows (cards) setting gives the drawer the same pill rows as the file list
    val mat = LocalWindowLook.current.material || FilesPrefs.cards
    val pill = if (mat) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp)
    Row((if (mat) Modifier.padding(horizontal = 12.dp) else Modifier).fillMaxWidth().clip(pill).background(if (on) Look.accent.copy(alpha = .16f) else Color.Transparent)
        .combinedClickable(enabled = !dim, onLongClick = onLong, onClick = onClick).padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        // Windows 11 takes the Fluent picture for the key (never the Material glyph in a fixed orange)
        if (glyph != null && T.id != "win11") Icon(glyph, null, Modifier.size(20.dp), if (mat) (if (on) Look.accent else T.sub) else Color(0xFFE8B84A))
        else if (icon == "folderY") ExIcon("folder", Color(0xFFE8B84A), 20.dp)
        else ExIcon(icon, if (dim) T.sub else if (on) Look.accent else T.text, 20.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(label, color = if (dim) T.sub else T.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = T.sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (bar != null) BarMeter(bar, Look.accent, h = 4.dp)
        }
    }
}

@Composable
private fun ExDrawerDivider() = if (FilesPrefs.cards) Spacer(Modifier.height(8.dp))
    else Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(1.dp).background(T.line.copy(alpha = .6f)))

/** An entry listed through su: the app itself cannot stat it, so the folder flag comes from the "/" that ls -p adds. */
private class RootFile(path: String, private val dir: Boolean) : File(path) {
    override fun isDirectory() = dir
    override fun isFile() = !dir
    override fun exists() = true
    override fun length() = 0L
    override fun lastModified() = 0L
}

private fun exRootList(dir: File, onError: (String) -> Unit = {}): List<File> = when (val r = RootFs.list(dir)) {
    is RootFs.Result.Ok -> r.entries.map { RootFile(File(dir, it.name).path, it.dir) }
    is RootFs.Result.Failed -> { onError(r.msg); emptyList() }
    RootFs.Result.NoRoot -> emptyList()
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
private fun ExAct(k: String, tint: Color, rot: Float = 0f, size: Dp = 23.dp, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 5.dp).clickable(onClick = onClick)) { ExIcon(k, tint, size, rot) }
}

/** One button of the selection bar: every action sits in the same rounded square, so all of them are the same size. */
@Composable
private fun SelSquare(tint: Color, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = 2.dp).size(40.dp).clip(RoundedCornerShape(10.dp)).background(T.panel).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** Selection bar button: a little smaller than [SelSquare] and with no gap between neighbours. */
@Composable
private fun DenseSelSquare(tint: Color, onClick: () -> Unit, big: Boolean = false, content: @Composable () -> Unit) {
    // every button of the selection bar is the same size: 80% of the first cut / copy / paste size
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(T.panel).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

/** Header glyph: Fluent on Windows 11 (the app's own icon set), the Material vector everywhere else. */
@Composable
private fun ExGlyph(material: ImageVector, fluent: String, tint: Color, size: Dp) {
    if (T.id == "win11") PIcon(fluent, tint, size) else Icon(material, null, Modifier.size(size), tint)
}

/** Material outline icons in the Material looks; the Windows looks use the nearest Fluent glyph they already have. */
@Composable
private fun ExIcon(k: String, tint: Color, size: Dp = 22.dp, rot: Float = 0f) = androidx.compose.runtime.key(T.id) {
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
    "grid" to Icons.Outlined.GridView, "list" to Icons.Outlined.ViewList,
    "ext" to Icons.Filled.TextFields, "star" to Icons.Filled.StarBorder, "bookmark" to Icons.Filled.BookmarkBorder, "starOn" to Icons.Filled.Star, "check" to Icons.Filled.CheckCircle,
    "sortName" to Icons.Filled.SortByAlpha, "sortSize" to Icons.Filled.DataUsage, "sortDate" to Icons.Filled.Schedule,
    "eyeOn" to Icons.Filled.Visibility, "eyeOff" to Icons.Filled.VisibilityOff, "sizes" to Icons.Filled.Straighten,
    "zoomIn" to Icons.Filled.ZoomIn, "zoomOut" to Icons.Filled.ZoomOut, "selectAll" to Icons.Outlined.SelectAll,
    "recent" to Icons.Filled.History, "emptyFolder" to Icons.Filled.FolderOff, "bigFile" to Icons.Filled.SdStorage,
    "dupes" to Icons.Filled.FileCopy, "undo" to Icons.Filled.Undo, "home" to Icons.Filled.Home,
    "share" to Icons.Filled.Share, "openWith" to Icons.Filled.OpenWith, "zip" to Icons.Filled.FolderZip,
    "link" to Icons.Filled.Link, "save" to Icons.Filled.Save, "edit" to Icons.Filled.Edit,
    "delete" to Icons.Filled.Delete, "document" to Icons.Filled.Description, "cut" to Icons.Outlined.ContentCut,
    "search" to Icons.Filled.Search, "filter" to Icons.Filled.FilterList, "back" to Icons.Filled.ArrowBack, "close" to Icons.Filled.Close, "openFolder" to Icons.Filled.FolderOpen,
)

/** Win11 file menu: one distinct Fluent glyph per action. */
private val W11_ACTION = mapOf("Open" to "open", "Cut" to "cut", "Properties" to "properties", "View archive" to "openFolder", "Copy path" to "link", "Create shortcut" to "folderLink")

private val EX_FLUENT = mapOf(
    "grid" to "gridView", "list" to "details",
    "ext" to "document", "star" to "heart", "starOn" to "heart", "bookmark" to "bookmark", "check" to "info", "sortName" to "details", "sortSize" to "disk", "sortDate" to "history",
    "eyeOn" to "view", "eyeOff" to "view", "sizes" to "disk", "recent" to "history", "emptyFolder" to "folder", "bigFile" to "disk",
    "dupes" to "copy", "undo" to "paste", "home" to "folder", "share" to "download", "openWith" to "run", "zip" to "extract",
    "filter" to "details", "link" to "copy", "save" to "rename", "edit" to "rename",
)

/** Recent files and bookmarks: one path per line under [key]. */
private fun exRead(ctx: Context, key: String): List<String> =
    com.umutk.pulse.filesPrefs(ctx).getString(key, "").orEmpty().lines().filter { it.isNotBlank() }

private fun exWrite(ctx: Context, key: String, list: List<String>) {
    com.umutk.pulse.filesPrefs(ctx).edit().putString(key, list.joinToString("\n")).apply()
}

/** Puts [f] at the top of the recent list (50 at most) and returns the new list. */
private fun exAddRecent(ctx: Context, f: File): List<String> {
    val list = RecentStore.add(ctx, f.path)
    return list
}

private fun exReadInt(ctx: Context, key: String, def: Int): Int = com.umutk.pulse.filesPrefs(ctx).getInt(key, def)

private fun exWriteInt(ctx: Context, key: String, v: Int) {
    com.umutk.pulse.filesPrefs(ctx).edit().putInt(key, v).apply()
}

/** Folder path -> sort mode, one "path|mode" line each (the path may hold '|', the mode is after the last one). */
private fun exReadSortMap(ctx: Context): Map<String, Int> = exRead(ctx, EX_SORT_DIRS).mapNotNull { line ->
    val path = line.substringBeforeLast('|', "")
    val mode = line.substringAfterLast('|').toIntOrNull()
    if (path.isEmpty() || mode == null) null else path to mode
}.toMap()

private fun exWriteSortMap(ctx: Context, map: Map<String, Int>) = exWrite(ctx, EX_SORT_DIRS, map.map { "${it.key}|${it.value}" })

/** The view each folder has of its own (0 list, 1 grid, 2 detailed list); a folder not here follows the global view. */
private fun exReadViewMap(ctx: Context): Map<String, Int> = exRead(ctx, EX_VIEW_DIRS).mapNotNull { line ->
    val i = line.lastIndexOf('|'); if (i < 0) null else line.substring(i + 1).toIntOrNull()?.let { line.substring(0, i) to it }
}.toMap()

private fun exWriteViewMap(ctx: Context, map: Map<String, Int>) = exWrite(ctx, EX_VIEW_DIRS, map.map { "${it.key}|${it.value}" })

private val EX_FILTER_ICONS = mapOf("all" to Icons.Outlined.FilterList, "images" to Icons.Outlined.Image, "videos" to Icons.Outlined.Movie, "audio" to Icons.Outlined.MusicNote,
    "docs" to Icons.Outlined.Description, "archives" to Icons.Outlined.FolderZip, "apks" to Icons.Outlined.Apps, "folders" to Icons.Outlined.Folder)
private val EX_FILTERS = listOf("all" to "All", "images" to "Images", "videos" to "Videos", "audio" to "Audio", "docs" to "Documents", "archives" to "Archives", "apks" to "APKs", "folders" to "Folders only")

private fun exFilterOk(f: File, type: String): Boolean {
    if (type == "all") return true
    if (type == "folders") return f.isDirectory
    if (f.isDirectory) return true // folders stay so the list can still be walked
    val e = f.extension.lowercase()
    return when {
        type.startsWith("ext:") -> e in type.removePrefix("ext:").split(',').map { it.trim().trimStart('.').lowercase() }.filter { it.isNotEmpty() }
        type == "images" -> e in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "avif", "svg")
        type == "videos" -> e in setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
        type == "audio" -> e in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "wma", "lrc")
        type == "docs" -> e in setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "csv", "md", "json")
        type == "archives" -> e in setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "jar")
        type == "apks" -> e in setOf("apk", "apks", "xapk", "apkm")
        else -> true
    }
}

/** The filter dialog in the Files look (WinAlert follows LocalWindowLook): big type rows, custom extensions, "Only this folder" and Clear. */
/** The sort popup: six orders, the filter types, then one switch that gives this folder its own sort and filter. */
@Composable
private fun ExSortMenu(expanded: Boolean, current: Int, perFolder: Boolean, filt: String, extOn: Boolean, onExt: () -> Unit, hiddenOn: Boolean, onHidden: () -> Unit, onDismiss: () -> Unit, onPick: (Int) -> Unit, onFilter: (String) -> Unit, onPerFolder: (Boolean) -> Unit) {
    // one row per key; the arrow shows the direction, tapping the same key again flips it
    val keys = listOf(tr("Name", "Ad"), tr("Size", "Boyut"), tr("Date", "Tarih"))
    ThemedMenu(expanded, onDismiss) {
        keys.forEachIndexed { key, label ->
            val on = current / 2 == key
            val arrow = if (!on) "" else if (current % 2 == 0) "  ↑" else "  ↓"
            ThemedMenuItem(label + arrow, { onPick(if (on) key * 2 + (1 - current % 2) else key * 2) }, checked = on)
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(com.umutk.pulse.LocalMenuStyle.current.sub.copy(alpha = .3f)))
        // type filters as small icons, four to a row; the label is only read out by screen readers
        EX_FILTERS.chunked(4).forEach { rw -> Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { rw.forEach { (id, label) ->
            val on = filt == id
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) Look.accent.copy(alpha = .2f) else Color.Transparent).clickable { onFilter(id) }, contentAlignment = Alignment.Center) {
                Icon(EX_FILTER_ICONS[id] ?: Icons.Outlined.FilterList, label, Modifier.size(20.dp), if (on) Look.accent else com.umutk.pulse.LocalMenuStyle.current.sub)
            }
        } } }
        // the file extensions and the hidden files switches: icon buttons beside the type filters
        Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Triple(extOn, onExt, Icons.Outlined.Title to tr("File extensions", "Dosya uzantıları")), Triple(hiddenOn, onHidden, Icons.Outlined.Visibility to tr("Hidden files", "Gizli dosyalar"))).forEach { (on, act, pair) ->
                Box(Modifier.size(36.dp).clip(CircleShape).background(if (on) Look.accent.copy(alpha = .2f) else Color.Transparent).clickable { act() }, contentAlignment = Alignment.Center) {
                    Icon(pair.first, pair.second, Modifier.size(20.dp), if (on) Look.accent else com.umutk.pulse.LocalMenuStyle.current.sub)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(com.umutk.pulse.LocalMenuStyle.current.sub.copy(alpha = .3f)))
        ThemedMenuItem(tr("Only for this folder", "Yalnızca bu klasör"), { onPerFolder(!perFolder) }, checked = perFolder)
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
private fun exSort(list: List<File>, mode: Int, dirSizes: Map<String, Long> = emptyMap()): List<File> {
    val key: Comparator<File> = when (mode / 2) {
        0 -> compareBy<File> { it.name.lowercase() }
        // by size: a folder counts with everything inside it (its size is measured in the background; unmeasured ones come as the smallest for now)
        1 -> compareBy<File> { if (it.isDirectory) dirSizes[it.path] ?: -1L else it.length() }
        else -> compareBy<File> { it.lastModified() }
    }
    val by = if (mode % 2 == 1) key.reversed() else key
    return list.sortedWith(if (FilesPrefs.foldersFirst) compareBy<File> { !it.isDirectory }.then(by) else by)
}

/** Entries of a zip file as (name, size); empty when the file cannot be read as a zip. */
private fun exArchiveList(f: File): List<Pair<String, Long>> = runCatching {
    java.util.zip.ZipFile(f).use { z -> z.entries().toList().map { it.name to it.size } }
}.getOrDefault(emptyList())

/** Names containing [q] under [root]; stops after 20 000 entries or 200 hits. Hidden names only when [hidden] is on. */
private fun exFind(root: File, q: String, hidden: Boolean, limit: Int = 20_000): List<File> =
    root.walkTopDown().take(limit).filter { it != root && (hidden || !it.name.startsWith(".")) && it.name.contains(q, ignoreCase = true) }.take(200).toList()

/** The Material delete question: a rounded card with a thin edge, a quiet info mark, a bold title, grey text, a soft Cancel and a clear red Delete. */
@Composable
private fun MaterialDeleteDialog(title: String, msg: String, onCancel: () -> Unit, onDelete: () -> Unit) {
    val w = com.umutk.pulse.LocalWindowLook.current
    val edge = if (w.dark) Color(0xFF252525) else Color(0xFFD6D6D6)
    val grey = if (w.dark) Color(0xFF939393) else Color(0xFF6E6E6E)
    androidx.compose.ui.window.Dialog(onCancel) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(w.panel).border(1.dp, edge, RoundedCornerShape(28.dp)).padding(start = 28.dp, end = 20.dp, top = 28.dp, bottom = 20.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.size(24.dp).align(Alignment.CenterHorizontally), tint = grey)
            Text(title, Modifier.padding(top = 16.dp).align(Alignment.CenterHorizontally), color = w.text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(msg, Modifier.padding(top = 12.dp), color = grey, fontSize = 14.sp, lineHeight = 20.sp)
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Cancel", "İptal"), Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onCancel).padding(horizontal = 18.dp, vertical = 10.dp), color = w.text.copy(alpha = .75f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text(tr("Delete", "Sil"), Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onDelete).padding(horizontal = 18.dp, vertical = 10.dp), color = Color(0xFFFF453A), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** The results of the empty-folder, largest-file and same-name finders under [dir]. */
private fun exFinder(kind: String, dir: File): List<File> = when (kind) {
    "empty" -> dir.walkTopDown().take(50_000).filter { it != dir && it.isDirectory && it.listFiles()?.isEmpty() == true }.take(200).toList()
    "large" -> dir.walkTopDown().take(50_000).filter { it.isFile }.sortedByDescending { it.length() }.take(50).toList()
    else -> dir.walkTopDown().take(50_000).filter { it.isFile }.groupBy { it.name }.values.filter { it.size > 1 }.flatten().sortedBy { it.name }.take(200)
}

/** The folder of [f] as shown from the storage root, e.g. "/Download". */
private fun exRel(f: File, top: File): String = (f.parentFile?.path ?: top.path).removePrefix(top.path).ifEmpty { "/" }

/** KB under 1 MB, MB under 1 GB, GB above. */
internal fun exSize(b: Long): String {
    val bin = FilesPrefs.binary; val k = if (bin) 1024.0 else 1000.0; val u = if (bin) listOf("KiB", "MiB", "GiB") else listOf("KB", "MB", "GB")
    return when {
        b < k * k -> "%.0f %s".format(b / k, u[0])
        b < k * k * k -> "%.0f %s".format(b / (k * k), u[1])
        else -> "%.1f %s".format(b / (k * k * k), u[2])
    }
}

/** A modification time in the chosen format: 0 smart (drops the year, month or day when they are the current ones), 1 long, 2 ISO. */
internal fun exDate(ms: Long, fmt: Int): String = when (fmt) {
    1 -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ms))
    2 -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(Date(ms))
    else -> exSmartDate(ms)
}

/** Today: only the time. Otherwise: day, the month spelled out, year and time. */
private fun exSmartDate(ms: Long): String {
    val now = java.util.Calendar.getInstance(); val t = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val sameYear = now.get(java.util.Calendar.YEAR) == t.get(java.util.Calendar.YEAR)
    val sameMonth = sameYear && now.get(java.util.Calendar.MONTH) == t.get(java.util.Calendar.MONTH)
    val sameDay = sameMonth && now.get(java.util.Calendar.DAY_OF_MONTH) == t.get(java.util.Calendar.DAY_OF_MONTH)
    val pat = when { sameDay -> "HH:mm"; else -> "d MMMM yyyy HH:mm" }
    return java.text.SimpleDateFormat(pat, java.util.Locale.getDefault()).format(Date(ms))
}

private val EX_IMAGE_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")

/** A small decoded picture for an image file, or null for anything else or when the device cannot decode it. */
@Composable
internal fun exThumb(f: File): ImageBitmap? {
    if (!FilesPrefs.thumbs || f.extension.lowercase() !in EX_IMAGE_EXT) return null
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

/** Sends [f] to a chooser: Share sends it, Open with opens it. Returns false when the file cannot be handed over. */
private fun exHandOver(ctx: Context, f: File, share: Boolean): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val mime = fileMime(f)
    val i = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri) else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
    i.clipData = ClipData.newRawUri("", uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // Open with is the system's own "Just once / Always" picker (no chooser wrapper); Share keeps the chooser
    if (share) ctx.startActivity(Intent.createChooser(i, f.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    else ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Shares several files at once (folders are left out: they have no single file to send). False when none could be handed over. */
private fun exShareMany(ctx: Context, files: List<File>): Boolean = runCatching {
    val list = files.filter { it.isFile }
    val uris = ArrayList<android.net.Uri>(list.map { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it) })
    val mimes = list.map { fileMime(it) }.distinct()
    val mime = if (mimes.size == 1) mimes[0] else if (mimes.all { it.substringBefore('/') == mimes[0].substringBefore('/') }) mimes[0].substringBefore('/') + "/*" else "*/*"
    val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType(mime).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    i.clipData = ClipData.newRawUri("", uris[0]).also { cd -> uris.drop(1).forEach { cd.addItem(ClipData.Item(it)) } }
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "${list.size}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

/** Pictures (HEIC included) go straight to Samsung Gallery when it is there, else to whatever the phone opens them with; false when nothing took it. */
private fun exOpenInGallery(ctx: Context, f: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, fileMime(f).takeIf { it != "*/*" } ?: "image/*")
    i.clipData = ClipData.newRawUri("", uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    val samsung = "com.sec.android.gallery3d"
    val has = runCatching { ctx.packageManager.getPackageInfo(samsung, 0); true }.getOrDefault(false)
    ctx.startActivity(if (has) i.setPackage(samsung) else i)
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
private fun paste(ctx: Context, c: Clip, dir: File, moved: MutableList<Pair<File, File>>): String {
    var failed = 0
    // a real copy of 2 MB or more (also a move across storage) shows its progress in a notification that stays when Pulse goes to the background
    val total by lazy { c.files.sumOf { FileJobs.treeSize(it) } }
    var job: FileJob? = null
    for (src in c.files) {
        var target = File(dir, src.name)
        var n = 1
        while (target.exists()) target = File(dir, "$n-${src.name}").also { n++ }
        val ok = runCatching {
            if (c.move && src.renameTo(target)) { moved += (target to src); true }
            else {
                if (job == null && total >= 2L * 1024 * 1024) job = FileJobs.begin(ctx, if (c.move) tr("Moving", "Taşınıyor") else tr("Copying", "Kopyalanıyor"), total)
                FileJobs.copyTree(src, target, job)
                if (c.move && job?.cancelled != true) src.deleteRecursively()
                true
            }
        }.getOrDefault(false)
        if (!ok) failed++
    }
    job?.let { FileJobs.end(ctx, it, if (it.cancelled) tr("Cancelled", "İptal edildi") else if (failed == 0) tr("Done: ${c.files.size} items", "Bitti: ${c.files.size} öğe") else tr("$failed could not be copied", "$failed öğe kopyalanamadı")) }
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

/** The file tools in the header: Windows 95 gets a flat grey bar of raised buttons, Windows 7 a light glossy command bar; the other looks keep plain icons. */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ExToolbar(onTab: (String) -> Unit, onLong: (String) -> Unit) {
    val tools = listOf("tree" to "sizes", "types" to "filetypes", "cache" to "clean")
    val t = T
    val barMod = when (t.id) {
        "win95" -> Modifier.background(Color(0xFFC0C0C0)).bevel(true).padding(2.dp)
        "win7" -> Modifier.clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF0F4FA), Color(0xFFDCE5F2), Color(0xFFE9EFF8)))).border(1.dp, Color(0xFFB5C4D9), RoundedCornerShape(3.dp)).padding(horizontal = 2.dp, vertical = 1.dp)
        else -> Modifier
    }
    Row(barMod, verticalAlignment = Alignment.CenterVertically) {
        tools.forEachIndexed { i, (k, icon) ->
            when (t.id) {
                "win95" -> {
                    val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val down by src.collectIsPressedAsState()
                    Box(Modifier.padding(horizontal = 1.dp).size(26.dp).background(Color(0xFFC0C0C0)).bevel(!down).combinedClickable(src, null, onClick = { onTab(k) }, onLongClick = { onLong(k) }), contentAlignment = Alignment.Center) { FluentGlyph(icon, Color.Black, 16.dp) }
                }
                "win7" -> {
                    if (i > 0) Box(Modifier.width(1.dp).height(18.dp).background(Color(0xFFC9D5E6)))
                    Box(Modifier.size(width = 30.dp, height = 26.dp).combinedClickable(onClick = { onTab(k) }, onLongClick = { onLong(k) }), contentAlignment = Alignment.Center) { FluentGlyph(icon, Color(0xFF2B4F86), 16.dp) }
                }
                else -> Box(Modifier.size(36.dp).combinedClickable(onClick = { onTab(k) }, onLongClick = { onLong(k) }), contentAlignment = Alignment.Center) { FluentGlyph(icon, t.text, 22.dp) }
            }
        }
    }
}

/** A file of no known extension that starts like a web page. */
private fun exLooksHtml(f: File): Boolean = runCatching {
    f.inputStream().use { i -> val b = ByteArray(600); val n = i.read(b); n > 0 && String(b, 0, n, Charsets.UTF_8).trimStart().lowercase().let { it.startsWith("<!doctype html") || it.startsWith("<html") || it.contains("<head") || it.contains("<body") } }
}.getOrDefault(false)

/** A web page of the phone drawn inside the app: no scripts, only the page itself. */
@Composable
private fun ExHtmlViewer(f: File, onClose: () -> Unit, onSource: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(T.bg)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.name, Modifier.weight(1f), color = T.text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(tr("Source", "Kaynak"), Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSource() }.padding(horizontal = 10.dp, vertical = 8.dp), color = Look.accent, fontSize = 13.sp)
                Text("✕", Modifier.clickable { onClose() }.padding(horizontal = 12.dp, vertical = 8.dp), color = T.text, fontSize = 18.sp)
            }
            androidx.compose.ui.viewinterop.AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                android.webkit.WebView(c).apply {
                    settings.javaScriptEnabled = false; settings.allowFileAccess = true; settings.allowContentAccess = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    val text = runCatching { f.readText() }.getOrDefault("")
                    loadDataWithBaseURL("file://" + (f.parent ?: "") + "/", text, "text/html", "utf-8", null)
                }
            })
        }
    }
}

internal val EX_VIDEO = setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "m4v", "ts", "flv", "wmv")

/** Starts Pulse's video player on [f]. False when it cannot be started (the caller then falls back to the other ways). */
private fun exOpenInPulseVideo(ctx: Context, f: File): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(ctx, com.umutk.pulse.ExtraToolActivity::class.java).setAction(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").putExtra("route", "video")
    i.clipData = ClipData.newRawUri("", uri)
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(i)
}.isSuccess

internal val EX_AUDIO = setOf("mp3", "ogg", "oga", "opus", "wav", "flac", "m4a", "aac", "amr")

/** The player of the music files tapped in Files: one file at a time, played in place, with speed. */
internal object ExAudio {
    private var mp: android.media.MediaPlayer? = null
    var file by mutableStateOf<File?>(null)
    var playing by mutableStateOf(false)
    var pos by mutableStateOf(0)
    var dur by mutableStateOf(0)
    var speed by mutableStateOf(1f)

    fun play(f: File) {
        stop()
        val p = android.media.MediaPlayer()
        runCatching {
            p.setDataSource(f.path); p.prepare(); p.setOnCompletionListener { playing = false; pos = 0 }
            mp = p; file = f; dur = p.duration; pos = 0
            p.start(); applySpeed(); playing = true
        }.onFailure { runCatching { p.release() }; mp = null; file = null }
    }
    private fun applySpeed() {
        val p = mp ?: return
        // speed needs Android 6; it is set while playing (a paused player would start on setting it)
        if (android.os.Build.VERSION.SDK_INT >= 23 && p.isPlaying) runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
    }
    fun toggle() { val p = mp ?: return; if (p.isPlaying) { p.pause(); playing = false } else { p.start(); applySpeed(); playing = true } }
    fun seek(ms: Int) { mp?.seekTo(ms); pos = ms }
    fun nextSpeed() { speed = when { speed < .9f -> 1f; speed < 1.1f -> 1.25f; speed < 1.4f -> 1.5f; speed < 1.9f -> 2f; else -> .75f }; applySpeed() }
    fun tick() { val p = mp ?: return; if (p.isPlaying) pos = p.currentPosition }
    fun stop() { runCatching { mp?.stop() }; runCatching { mp?.release() }; mp = null; file = null; playing = false; pos = 0; dur = 0 }
}

@Composable
private fun androidx.compose.foundation.layout.BoxScope.ExMiniPlayer() {
    val f = ExAudio.file ?: return
    LaunchedEffect(f) { while (true) { ExAudio.tick(); kotlinx.coroutines.delay(250) } }
    fun mmss(ms: Int) = "%d:%02d".format(ms / 60000, ms / 1000 % 60)
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).clip(RoundedCornerShape(16.dp)).background(T.panel).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clickable { ExAudio.toggle() }, contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(if (ExAudio.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Look.accent, modifier = Modifier.size(26.dp))
            }
            Text(f.nameWithoutExtension, Modifier.weight(1f).padding(horizontal = 6.dp), color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(mmss(ExAudio.pos) + " / " + mmss(ExAudio.dur), color = T.sub, fontSize = 11.sp)
            Text(("%.2f".format(ExAudio.speed).trimEnd('0').trimEnd('.')) + "x", Modifier.clip(RoundedCornerShape(10.dp)).clickable { ExAudio.nextSpeed() }.padding(horizontal = 8.dp, vertical = 6.dp), color = Look.accent, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
            Box(Modifier.size(32.dp).clickable { ExAudio.stop() }, contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Rounded.Close, null, tint = T.sub, modifier = Modifier.size(18.dp)) }
        }
        com.umutk.pulse.PSlider(if (ExAudio.dur > 0) ExAudio.pos.toFloat() / ExAudio.dur else 0f, { ExAudio.seek((it * ExAudio.dur).toInt()) }, 0f..1f, onDone = {})
    }
}

/** Paste button above the "+" while the clipboard holds something: tap pastes here, long press clears the clipboard; the badge counts the items. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ExPasteFab(count: Int, move: Boolean, onPaste: () -> Unit, onClear: () -> Unit) {
    val win = Opt.authentic && LocalWindowLook.current.id in com.umutk.pulse.winButtonLooks
    val shape = RoundedCornerShape(if (win) (if (LocalWindowLook.current.id == "win95") 0.dp else 4.dp) else 14.dp)
    Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 16.dp + 56.dp + 12.dp)) {
        Box(Modifier.size(44.dp).shadow(4.dp, shape).clip(shape).background(T.panel).border(1.dp, Look.accent, shape).combinedClickable(onClick = onPaste, onLongClick = onClear), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.ContentPaste, null, Modifier.size(22.dp), Look.accent)
        }
        Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(18.dp).clip(CircleShape).background(Look.accent), contentAlignment = Alignment.Center) {
            Text("$count", color = com.umutk.pulse.onAccent(Look.accent), fontSize = 10.sp)
        }
    }
}

/** Floating search at the bottom-left: a round button that expands into a field (clear + close); Enter calls [onSearch]. Material = pill, the other looks = square. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ExSearchBar(top: Boolean, open: Boolean, onOpen: () -> Unit, query: String, onQuery: (String) -> Unit, onSearch: () -> Unit, onClose: () -> Unit, all: Boolean, onAll: () -> Unit) {
    val shape = if (T.material) RoundedCornerShape(28.dp) else RoundedCornerShape(0.dp)
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { fr.requestFocus() } }
    // the standalone Files app: the search button is the one at the top right; the field grows out of it to the left, over the header
    if (top) {
        androidx.compose.animation.AnimatedVisibility(open, Modifier.align(Alignment.TopEnd).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            enter = androidx.compose.animation.expandHorizontally(expandFrom = Alignment.End) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkHorizontally(shrinkTowards = Alignment.End) + androidx.compose.animation.fadeOut()) {
            Row(Modifier.fillMaxWidth().height(48.dp).clip(shape).background(T.panel), verticalAlignment = Alignment.CenterVertically) {
                PField(query, onQuery, tr("Search", "Ara"), Modifier.weight(1f).focusRequester(fr), onSearch)
                // search the whole storage instead of this folder
                Box(Modifier.size(44.dp).clickable(onClick = onAll), contentAlignment = Alignment.Center) { ExGlyph(Icons.Outlined.Public, "globe", if (all) Look.accent else T.sub, 22.dp) }
                // the cross at the right end clears the text first, then closes the field
                Box(Modifier.size(44.dp).clickable { if (query.isNotEmpty()) onQuery("") else onClose() }, contentAlignment = Alignment.Center) { ExIcon("close", T.text, 20.dp) }
            }
        }
        return
    }
    Row(Modifier.align(Alignment.BottomStart).navigationBarsPadding().imePadding().padding(start = 16.dp, bottom = 16.dp, end = if (open) 88.dp else 0.dp)
        .then(if (open) Modifier.fillMaxWidth() else Modifier)
        .shadow(6.dp, shape).clip(shape).background(T.panel).then(if (!open) Modifier.clickable(onClick = onOpen) else Modifier),
        verticalAlignment = Alignment.CenterVertically) {
        if (!open) Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { ExIcon("search", T.text, 24.dp) }
        else {
            Box(Modifier.size(44.dp).clickable(onClick = onClose), contentAlignment = Alignment.Center) { ExIcon("back", T.text, 20.dp) }
            PField(query, onQuery, tr("Search", "Ara"), Modifier.weight(1f).focusRequester(fr), onSearch)
            Box(Modifier.size(44.dp).clickable(onClick = onAll), contentAlignment = Alignment.Center) { ExGlyph(Icons.Outlined.Public, "globe", if (all) Look.accent else T.sub, 22.dp) }
            if (query.isNotEmpty()) Box(Modifier.size(44.dp).clickable { onQuery("") }, contentAlignment = Alignment.Center) { ExIcon("close", T.sub, 20.dp) }
        }
    }
}

/** Floating "+" at the bottom-right: Material = speed-dial, Windows looks (Opt.authentic) = a copy of Explorer's "New" menu. [pick] gets "folder" or the file extension. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ExNewFab(pick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val win = Opt.authentic && LocalWindowLook.current.id in com.umutk.pulse.winButtonLooks
    val go: (String) -> Unit = { open = false; pick(it) }
    if (open && !win) Box(Modifier.fillMaxSize().clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { open = false })
    // both children sit at the bottom-right of this box, so the speed dial grows upward from the button
    Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)) {
        if (open && !win) Column(Modifier.align(Alignment.BottomEnd).padding(bottom = 68.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("txt" to Icons.Outlined.NoteAdd, "folder" to Icons.Outlined.CreateNewFolder).forEach { (k, ic) ->
                Row(Modifier.clip(RoundedCornerShape(28.dp)).background(T.panel).clickable { go(k) }.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (k == "folder") tr("New folder", "Yeni klasör") else tr("New file", "Yeni dosya"), color = T.text, fontSize = 14.sp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(fabFill()), contentAlignment = Alignment.Center) { Icon(ic, null, Modifier.size(22.dp), fabMark()) }
                }
            }
        }
        val fabShape = RoundedCornerShape(if (win && LocalWindowLook.current.id == "win95") 0.dp else 16.dp)
        Box(Modifier.align(Alignment.BottomEnd).size(56.dp).shadow(6.dp, fabShape).clip(fabShape).background(fabFill()).clickable { open = !open }, contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, Modifier.size(28.dp).rotate(if (open) 45f else 0f), fabMark())
        if (win) ThemedMenu(open, { open = false }) {
            val sub = com.umutk.pulse.LocalMenuStyle.current.sub
            ThemedMenuItem(tr("Folder", "Klasör"), { go("folder") }, leading = { ExIcon("folder", Color(0xFFE8B84A), 18.dp) })
            ThemedMenuItem(tr("Text Document", "Metin Belgesi"), { go("txt") }, leading = { ExIcon("filetypes", sub, 18.dp) })
            listOf(
                Triple(com.umutk.pulse.OfKind.WORD, "docx", tr("Microsoft Word Document", "Microsoft Word Belgesi")),
                Triple(com.umutk.pulse.OfKind.EXCEL, "xlsx", tr("Microsoft Excel Worksheet", "Microsoft Excel Çalışma Sayfası")),
                Triple(com.umutk.pulse.OfKind.PPT, "pptx", tr("Microsoft PowerPoint Presentation", "Microsoft PowerPoint Sunusu")),
                Triple(com.umutk.pulse.OfKind.PDF, "pdf", tr("PDF Document", "PDF Belgesi")),
            ).forEach { (k, e, l) -> ThemedMenuItem(l, { go(e) }, leading = { com.umutk.pulse.OfLogo(k, 18.dp) }) }
        }
        }
    }
}


/** Where each folder was scrolled to when it was left, so going back (or opening it again) continues from there. */
object ExScroll {
    val memo = HashMap<String, Int>()
    var live: androidx.compose.foundation.ScrollState? = null
}


/** Where the Files browser was last (kept in memory, so a trip to another tab of the Files app and back lands in the same folder). */
object ExNav { var lastHere: String? = null }

/** Folder sizes: everything under a folder, measured once (up to 300 000 files) and kept until the folder changes. */
object ExDirSizes {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()   // path -> (bytes, last modified when measured)
    fun cached(d: File): Long? = cache[d.path]?.takeIf { it.second == d.lastModified() }?.first
    fun measure(d: File): Long {
        cached(d)?.let { return it }
        var total = 0L; var n = 0
        runCatching { d.walkTopDown().onEnter { true }.forEach { f -> if (f.isFile) total += f.length(); if (++n > 300_000) return@runCatching } }
        cache[d.path] = total to d.lastModified()
        return total
    }
}

/** The bottom bar of the Share button: send the files, copy their paths, or send their names as text. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExShareSheet(picks: List<File>, onDismiss: () -> Unit, note: (String) -> Unit) {
    val ctx = LocalContext.current
    val files = picks.filter { it.isFile }
    val folders = picks.any { it.isDirectory }
    fun done(msg: String) { note(msg); onDismiss() }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = T.panel) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            val rows = listOf(
                Triple(Icons.Outlined.Share, tr("Share the files", "Dosyaları paylaş")) {
                    val ok = if (files.size == 1) exHandOver(ctx, files[0], true) else exShareMany(ctx, files)
                    done(if (ok) (if (folders) tr("Folders were left out", "Klasörler paylaşıma alınmadı") else "") else tr("Could not share", "Paylaşılamadı"))
                },
                Triple(Icons.Outlined.ContentCopy, tr("Copy the paths", "Yolları kopyala")) {
                    val text = picks.joinToString("\n") { it.path }
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("paths", text))
                    done(tr("Paths copied", "Yollar kopyalandı"))
                },
                Triple(Icons.Outlined.TextSnippet, tr("Share the names as text", "Adları metin olarak paylaş")) {
                    val text = picks.joinToString("\n") { it.name }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    done("")
                },
            )
            rows.forEach { (ic, label, act) ->
                Row(Modifier.fillMaxWidth().clickable { act() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(ic, null, Modifier.size(22.dp), T.text)
                    Spacer(Modifier.width(16.dp))
                    Text(label, color = T.text, fontSize = 16.sp)
                }
            }
        }
    }
}
