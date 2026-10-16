package com.umutk.pulse

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Choices of the standalone Photos app (shared "p" preferences, ph_* keys). They are applied over the app-wide look while the Photos activity is open. */
internal object PhotosPrefs {
    var look by mutableStateOf("material")   // Material is the default; oneui = Samsung Gallery style
    var amoled by mutableStateOf(false)
    var lang by mutableStateOf("en")
    var dynamic by mutableStateOf(true)
    var accent by mutableIntStateOf(-1)
    var cols by mutableIntStateOf(4)
    var favs by mutableStateOf(setOf<String>())
    var startTab by mutableIntStateOf(0)
    var showVideos by mutableStateOf(false)
    var showHidden by mutableStateOf(false)
    var hiddenAlbums by mutableStateOf(setOf<String>())
    var sortBy by mutableStateOf("taken")      // taken | modified | name
    var sortAsc by mutableStateOf(false)
    var group by mutableStateOf("day")         // day | month | none
    var slideSec by mutableIntStateOf(3)
    var filmstrip by mutableStateOf(true)
    var loopVideos by mutableStateOf(false)
    var liveAuto by mutableStateOf(false)
    var zoomLevel by mutableFloatStateOf(2.5f)
    var confirmDelete by mutableStateOf(true)
    var keepOn by mutableStateOf(false)
    var exifDefault by mutableStateOf(false)
    private fun sp(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    fun load(c: Context) {
        val p = sp(c)
        look = p.getString("ph_look", "material") ?: "material"; amoled = p.getBoolean("ph_amoled", false); lang = p.getString("ph_lang", "en") ?: "en"
        dynamic = p.getBoolean("ph_dynamic", true); accent = p.getInt("ph_accent", -1); cols = p.getInt("ph_cols", 4).coerceIn(2, 7)
        favs = p.getStringSet("ph_favs", emptySet()) ?: emptySet()
        startTab = p.getInt("ph_tab", 0).coerceIn(0, 2); showVideos = p.getBoolean("ph_videos", false); showHidden = p.getBoolean("ph_hidden", false)
        hiddenAlbums = p.getStringSet("ph_hideAlbums", emptySet()) ?: emptySet()
        sortBy = p.getString("ph_sort", "taken") ?: "taken"; sortAsc = p.getBoolean("ph_asc", false); group = p.getString("ph_group", "day") ?: "day"
        slideSec = p.getInt("ph_slide", 3); filmstrip = p.getBoolean("ph_film", true); loopVideos = p.getBoolean("ph_loop", false)
        liveAuto = p.getBoolean("ph_liveauto", false); zoomLevel = p.getFloat("ph_zoom", 2.5f); confirmDelete = p.getBoolean("ph_confirm", true)
        keepOn = p.getBoolean("ph_keepon", false); exifDefault = p.getBoolean("ph_exif", false)
    }
    fun save(c: Context) {
        sp(c).edit().putString("ph_look", look).putBoolean("ph_amoled", amoled).putString("ph_lang", lang).putBoolean("ph_dynamic", dynamic)
            .putInt("ph_accent", accent).putInt("ph_cols", cols).putStringSet("ph_favs", favs)
            .putInt("ph_tab", startTab).putBoolean("ph_videos", showVideos).putBoolean("ph_hidden", showHidden).putStringSet("ph_hideAlbums", hiddenAlbums)
            .putString("ph_sort", sortBy).putBoolean("ph_asc", sortAsc).putString("ph_group", group).putInt("ph_slide", slideSec).putBoolean("ph_film", filmstrip)
            .putBoolean("ph_loop", loopVideos).putBoolean("ph_liveauto", liveAuto).putFloat("ph_zoom", zoomLevel).putBoolean("ph_confirm", confirmDelete)
            .putBoolean("ph_keepon", keepOn).putBoolean("ph_exif", exifDefault).apply()
    }
    /** Pushes the choices into the app-wide look; called at start and after every change. */
    fun apply() {
        Look.theme = Themes.firstOrNull { it.id == look } ?: Themes.first { it.id == "material" }
        Look.mode = if (amoled) "dark" else "system"
        Look.windowTheme = if (amoled) "amoled" else "win"
        Look.dynamic = dynamic; Look.accentIdx = accent; Opt.lang = lang
    }
    /** Bumped after the system favourite dialog returns and on resume: the gallery queries again. */
    var tick by mutableIntStateOf(0)
    /** Set by ImageViewActivity: runs the system's "favourite these pictures" dialog (no all-files access). */
    var launchSender: ((android.content.IntentSender) -> Unit)? = null
    private val localOnly = HashSet<String>() // hearts MediaStore refused: kept in the app's own set only
    fun toggleFav(c: Context, u: String): Set<String> {
        val on = u !in favs
        favs = if (on) favs + u else favs - u; save(c)
        if (!setSystemFav(c, Uri.parse(u), on)) { if (on) localOnly.add(u) else localOnly.remove(u) }
        return favs
    }
    /** Writes IS_FAVORITE on the MediaStore row (API 30+). Some OEM galleries (Samsung) keep favourites in their own database and ignore it. */
    fun setSystemFav(c: Context, u: Uri, on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        return try {
            val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val id = if (u.scheme == "file") c.contentResolver.query(base, arrayOf(MediaStore.Images.Media._ID), "${MediaStore.Images.Media.DATA}=?", arrayOf(u.path ?: ""), null)?.use { if (it.moveToFirst()) it.getLong(0) else null }
                else ContentUris.parseId(u)
            if (id == null) return false
            val mu = ContentUris.withAppendedId(base, id)
            if (Environment.isExternalStorageManager()) {
                c.contentResolver.update(mu, android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_FAVORITE, if (on) 1 else 0) }, null, null) > 0
            } else {
                val l = launchSender ?: return false
                l(MediaStore.createFavoriteRequest(c.contentResolver, listOf(mu), on).intentSender); true
            }
        } catch (_: Exception) { false }
    }
    /** System favourites win for pictures that are in [l]; hearts the system refused stay. */
    fun sync(c: Context, l: List<Ph>) {
        if (Build.VERSION.SDK_INT < 30) return
        val uris = l.map { it.src.uri.toString() }.toSet()
        val n = (favs - uris) + l.filter { it.fav }.map { it.src.uri.toString() } + (favs intersect localOnly)
        if (n != favs) { favs = n; save(c) }
    }
    /** True black twin of a Material look for the AMOLED switch. */
    fun black(t: PTheme) = PTheme(t.id, t.name, true, Color.Black, Color(0xFF111111), Color.White, Color(0xFF9E9E9E), t.accent, Color(0xFF2A2A2A), t.radius, material = true, glassA = t.glassA)
}

internal class Ph(val src: ImgSrc, val date: Long, val album: String, val fav: Boolean = false, val mod: Long = 0, val video: Boolean = false)

/** Device pictures, newest first. With all-files access each item is a File (editable in the viewer), otherwise just a content URI. */
internal fun queryPhotos(ctx: Context): List<Ph> {
    val all = Environment.isExternalStorageManager()
    val out = ArrayList<Ph>()
    fun read(base: Uri, video: Boolean) = runCatching {
        ctx.contentResolver.query(base, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) + (if (Build.VERSION.SDK_INT >= 30) arrayOf(MediaStore.MediaColumns.IS_FAVORITE) else emptyArray()), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: "image"
                val f = if (all) c.getString(2)?.let { File(it) }?.takeIf { it.isFile } else null
                val uri = if (f != null) Uri.fromFile(f) else ContentUris.withAppendedId(base, c.getLong(0))
                val d = c.getLong(3).takeIf { it > 0 } ?: (c.getLong(4) * 1000)
                out.add(Ph(ImgSrc(uri, f, name), d, c.getString(5) ?: "?", Build.VERSION.SDK_INT >= 30 && c.getInt(6) == 1, c.getLong(4) * 1000, video))
            }
        }
    }
    read(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
    if (PhotosPrefs.showVideos) read(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
    return out
}

/** Applies hidden-folder / hidden-album filters and the chosen sort. */
internal fun arrangePhotos(l: List<Ph>): List<Ph> {
    val f = l.filter { (PhotosPrefs.showHidden || !it.album.startsWith(".")) && it.album !in PhotosPrefs.hiddenAlbums }
    val c: Comparator<Ph> = when (PhotosPrefs.sortBy) { "name" -> compareBy { it.src.name.lowercase() }; "modified" -> compareBy { it.mod }; else -> compareBy { it.date } }
    return f.sortedWith(if (PhotosPrefs.sortAsc) c else c.reversed())
}

private fun shareMany(ctx: Context, l: List<ImgSrc>) {
    if (l.size == 1) { imgShare(ctx, l[0]); return }
    runCatching {
        val uris = ArrayList(l.map { if (it.file != null) FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", it.file) else it.uri })
        val i = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun TopBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, tint: Color = T.text) =
    ImgIcBtn(onClick, 44.dp) { LIc(icon, tint, 24.dp) }

/** The Pulse Photos start screen: pictures (timeline), albums and favourites; [onOpen] gets the shown list and the tapped index. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PhotosGalleryScreen(onOpen: (List<ImgSrc>, Int) -> Unit) = LookFade { PhotosGalleryInner(onOpen) }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotosGalleryInner(onOpen: (List<ImgSrc>, Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted() = Environment.isExternalStorageManager() || ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    var ok by remember { mutableStateOf(granted()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok = granted() }
    LaunchedEffect(Unit) { if (!ok) ask.launch(perm) }
    var all by remember { mutableStateOf<List<Ph>?>(null) }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val ob = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) PhotosPrefs.tick++ }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    LaunchedEffect(ok, PhotosPrefs.tick, PhotosPrefs.showVideos) { if (ok) { val l = withContext(Dispatchers.IO) { queryPhotos(ctx) }; PhotosPrefs.sync(ctx, l); all = l } }

    var tab by rememberSaveable { mutableIntStateOf(PhotosPrefs.startTab) }
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var results by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val favs = PhotosPrefs.favs
    val cols = PhotosPrefs.cols

    val src = all ?: emptyList()
    val base = remember(all, PhotosPrefs.sortBy, PhotosPrefs.sortAsc, PhotosPrefs.showHidden, PhotosPrefs.hiddenAlbums) { arrangePhotos(src) }
    val loc = if (Opt.lang == "tr" || (Opt.lang == "auto" && Locale.getDefault().language == "tr")) Locale("tr") else Locale.ENGLISH
    val fmtDay = remember(loc) { SimpleDateFormat("EEE, d MMM yyyy", loc) }
    val fmtMon = remember(loc) { SimpleDateFormat("MMMM yyyy", loc) }
    val fmtAll = remember(loc) { SimpleDateFormat("d MMMM MMM yyyy EEEE", loc) }
    val toks = remember(q) { q.trim().lowercase(loc).split(' ').filter { it.isNotEmpty() } }
    fun hit(text: String) = toks.all { text.contains(it) }
    val visible = remember(base, tab, album, toks, favs) {
        when {
            toks.isNotEmpty() -> base.filter { hit((it.src.name + " " + it.album + " " + fmtAll.format(Date(it.date))).lowercase(loc)) }
            tab == 2 -> base.filter { it.src.uri.toString() in favs }
            album != null -> base.filter { it.album == album }
            else -> base
        }
    }
    val albumHits = remember(base, toks, results) { if (results && toks.isNotEmpty()) base.groupBy { it.album }.entries.filter { hit(it.key.lowercase(loc)) } else emptyList() }
    val showAlbums = tab == 1 && album == null && toks.isEmpty() && !results
    val pics = remember(visible) { visible.filter { !it.video } }
    val srcs = remember(pics) { pics.map { it.src } }
    val index = remember(pics) { pics.withIndex().associate { it.value.src.uri.toString() to it.index } }
    val grp = if (PhotosPrefs.sortBy == "name") "none" else PhotosPrefs.group
    val entries = remember(visible, loc, grp, albumHits) {
        val out = ArrayList<Any>(); var last = ""
        if (albumHits.isNotEmpty()) out.add(albumHits)
        for (p in visible) {
            val d = when (grp) { "day" -> fmtDay.format(Date(p.date)); "month" -> fmtMon.format(Date(p.date)); else -> null }
            if (d != null && d != last) { out.add(d); last = d }
            out.add(p)
        }
        out
    }

    fun closeSearch() { results = false; searchOpen = false; q = "" }
    BackHandler(album != null) { album = null }
    BackHandler(results || searchOpen) { closeSearch() }
    BackHandler(sel.isNotEmpty()) { sel = emptySet() }
    BackHandler(settings) { settings = false }

    fun runDelete() {
        val gone = src.filter { it.src.uri.toString() in sel }
        sel = emptySet()
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                gone.filter { p -> runCatching {
                    if (p.src.file != null) { val d = p.src.file.delete(); MediaScannerConnection.scanFile(ctx, arrayOf(p.src.file.absolutePath), null, null); d }
                    else ctx.contentResolver.delete(p.src.uri, null, null) > 0
                }.getOrDefault(false) }
            }
            all = all?.filter { a -> done.none { it === a } }
        }
    }
    fun toggle(u: String) { sel = if (u in sel) sel - u else sel + u }
    val r = if (T.material) 6.dp else 0.dp

    Box(Modifier.fillMaxSize().background(T.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // top bar: selection actions, the search field, or the title with search / settings
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    sel.isNotEmpty() -> {
                        TopBtn(Icons.Outlined.Close, { sel = emptySet() })
                        Text(sel.size.toString(), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
                        TopBtn(Icons.Outlined.Share, { shareMany(ctx, src.filter { it.src.uri.toString() in sel }.map { it.src }) })
                        TopBtn(Icons.Outlined.Delete, { if (PhotosPrefs.confirmDelete) confirmDel = true else runDelete() })
                    }
                    results -> {
                        TopBtn(Icons.Outlined.ArrowBack, { closeSearch() })
                        Text(q, color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 6.dp))
                    }
                    else -> {
                        if (album != null) TopBtn(Icons.Outlined.ArrowBack, { album = null })
                        Text(album ?: when (tab) { 1 -> tr("Albums", "Albümler"); 2 -> tr("Favorites", "Favoriler"); else -> tr("Photos", "Fotoğraflar") },
                            color = T.text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(start = if (album != null) 4.dp else 10.dp))
                        TopBtn(Icons.Outlined.Settings, { settings = true })
                    }
                }
            }
            val l = all
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    !ok -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        TextButton({ ask.launch(perm) }) { Text(tr("Allow access to photos", "Fotoğraflara erişime izin ver"), color = Look.accent) }
                    }
                    l == null -> Box(Modifier.fillMaxSize())
                    showAlbums -> {
                        val groups = remember(base) { base.groupBy { it.album }.entries.toList() }
                        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 4.dp, 12.dp, 170.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(groups.size, key = { groups[it].key }) { i ->
                                val g = groups[i]
                                Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable { album = g.key }) {
                                    ImgThumb(g.value[0].src, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(18.dp)), 360)
                                    Text(g.key, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                                    Text(g.value.size.toString(), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                        }
                    }
                    visible.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LIc(if (tab == 2) Icons.Outlined.FavoriteBorder else Icons.Outlined.PhotoLibrary, T.sub, 56.dp)
                    }
                    else -> LazyVerticalGrid(GridCells.Fixed(cols), Modifier.fillMaxSize().pointerInput(Unit) {
                        // pinch: spread = fewer, bigger tiles; pinch in = more columns
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var acc = 1f
                            do {
                                val ev = awaitPointerEvent(PointerEventPass.Initial)
                                if (ev.changes.count { it.pressed } >= 2) {
                                    acc *= ev.calculateZoom()
                                    ev.changes.forEach { it.consume() }
                                    if (acc > 1.3f) { PhotosPrefs.cols = (PhotosPrefs.cols - 1).coerceAtLeast(2); acc = 1f; PhotosPrefs.save(ctx) }
                                    else if (acc < .77f) { PhotosPrefs.cols = (PhotosPrefs.cols + 1).coerceAtMost(7); acc = 1f; PhotosPrefs.save(ctx) }
                                }
                            } while (ev.changes.any { it.pressed })
                        }
                    }, contentPadding = PaddingValues(2.dp, 0.dp, 2.dp, 170.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(entries.size, key = { i -> entries[i].let { if (it is Ph) it.src.uri.toString() else if (it is String) "h$i" else "albums" } },
                            span = { i -> if (entries[i] is Ph) GridItemSpan(1) else GridItemSpan(maxLineSpan) },
                            contentType = { i -> entries[i] is Ph }) { i ->
                            val e = entries[i]
                            if (e is Ph) {
                                val u = e.src.uri.toString()
                                val on = u in sel
                                val k by animateFloatAsState(if (on) .86f else 1f, label = "sel")
                                // the live check reads the file head, so only tiles that are on screen pay for it
                                val live by produceState(false, e) { value = !e.video && withContext(Dispatchers.IO) { imgLiveInfo(ctx, e.src) != null } }
                                Box(Modifier.aspectRatio(1f).scale(k).clip(RoundedCornerShape(if (on) 12.dp else r))
                                    .combinedClickable(onClick = { if (sel.isNotEmpty()) toggle(u) else if (e.video) openVideo(ctx, e.src) else onOpen(srcs, index[u] ?: 0) }, onLongClick = { toggle(u) })) {
                                    ImgThumb(e.src, Modifier.fillMaxSize(), if (cols <= 3) 420 else 240)
                                    if (e.video) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                                        LIc(Icons.Outlined.PlayArrow, Color.White, 12.dp)
                                    }
                                    if (live) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)), contentAlignment = Alignment.Center) {
                                        LIc(Icons.Outlined.SlowMotionVideo, Color.White, 12.dp)
                                    }
                                    if (u in favs && sel.isEmpty()) LIc(Icons.Filled.Favorite, Color.White, 14.dp, Modifier.align(Alignment.BottomStart).padding(5.dp))
                                    if (sel.isNotEmpty()) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                                        .background(if (on) Look.accent else Color.Black.copy(alpha = .35f)).border(1.5.dp, Color.White, CircleShape), contentAlignment = Alignment.Center) {
                                        if (on) LIc(Icons.Outlined.Check, onAccent(Look.accent), 14.dp)
                                    }
                                }
                            } else if (e is List<*>) LazyRow(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(e.size) { k ->
                                    @Suppress("UNCHECKED_CAST") val g = e[k] as Map.Entry<String, List<Ph>>
                                    Column(Modifier.width(110.dp).clip(RoundedCornerShape(14.dp)).clickable { closeSearch(); tab = 1; album = g.key }) {
                                        ImgThumb(g.value[0].src, Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)), 240)
                                        Text(g.key, color = T.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
                                        Text(g.value.size.toString(), color = T.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                                    }
                                }
                            } else Text(e as String, color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(10.dp, 16.dp, 10.dp, 6.dp))
                        }
                    }
                }
            }
        }
        // floating bottom tabs (icons only)
        if (sel.isEmpty() && !results && !settings) Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp).height(60.dp).clip(RoundedCornerShape(30.dp))
            .background(T.panel).border(1.dp, T.line, RoundedCornerShape(30.dp)).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(Icons.Outlined.PhotoLibrary, Icons.Outlined.Collections, Icons.Outlined.FavoriteBorder).forEachIndexed { i, ic ->
                val on = tab == i
                Box(Modifier.padding(horizontal = 4.dp).size(width = 64.dp, height = 44.dp).clip(CircleShape).background(if (on) Look.accent.copy(alpha = .22f) else Color.Transparent)
                    .clickable { tab = i; album = null }, contentAlignment = Alignment.Center) {
                    LIc(ic, if (on) T.text else T.sub, 24.dp)
                }
            }
        }
        if (sel.isEmpty() && !results && !settings) PhotoSearchBar(searchOpen, q, { q = it }, { searchOpen = true }, { closeSearch() }, { if (q.isNotBlank()) results = true }, Modifier.align(Alignment.BottomCenter))
        if (settings) PhotosSettings { settings = false }
        if (confirmDel) WinAlert(onDismissRequest = { confirmDel = false },
            title = { Text(tr("Delete ${sel.size} pictures?", "${sel.size} resim silinsin mi?")) },
            confirmButton = {
                TextButton({ confirmDel = false; runDelete() }) { Text(tr("Delete", "Sil")) }
            },
            dismissButton = { TextButton({ confirmDel = false }) { Text(tr("Cancel", "İptal")) } })
    }
}

private fun openVideo(ctx: Context, s: ImgSrc) {
    runCatching {
        val u = if (s.file != null) FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", s.file) else s.uri
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(u, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Floating search: a round button above the tab pill that expands to a bar; typing filters live, the search key opens the results page. */
@Composable
private fun PhotoSearchBar(open: Boolean, q: String, onQ: (String) -> Unit, onOpen: () -> Unit, onClose: () -> Unit, onSubmit: () -> Unit, modifier: Modifier) {
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val fm = LocalFocusManager.current
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(open) { if (open) runCatching { fr.requestFocus() } }
    val shape = RoundedCornerShape(if (T.material) 28.dp else 4.dp)
    Box(modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = if (ime) 10.dp else 88.dp), contentAlignment = Alignment.BottomEnd) {
        if (!open) Box(Modifier.size(52.dp).clip(shape).background(T.panel).border(1.dp, T.line, shape).clickable(onClick = onOpen), contentAlignment = Alignment.Center) { LIc(Icons.Outlined.Search, T.text, 24.dp) }
        else Row(Modifier.fillMaxWidth().height(52.dp).clip(shape).background(T.panel).border(1.dp, T.line, shape).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (q.isEmpty()) Text(tr("Search", "Ara"), color = T.sub, fontSize = 16.sp)
                BasicTextField(q, onQ, Modifier.fillMaxWidth().focusRequester(fr), singleLine = true, textStyle = TextStyle(color = T.text, fontSize = 16.sp), cursorBrush = SolidColor(Look.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { fm.clearFocus(); onSubmit() }))
            }
            ImgIcBtn({ if (q.isNotEmpty()) onQ("") else onClose() }, 48.dp) { LIc(Icons.Outlined.Close, T.sub, 22.dp) }
        }
    }
}

/** Photos settings: rows open radio / colour dialogs; switches use the look's own PSwitch. */
@Composable
private fun PhotosSettings(onClose: () -> Unit) {
    val ctx = LocalContext.current
    fun change(f: () -> Unit) { f(); PhotosPrefs.save(ctx); PhotosPrefs.apply() }
    val looks = listOf("material" to "Material", "oneui" to "Samsung", "coloros" to "ColorOS", "win11" to "Windows 11", "win10" to "Windows 10", "win7" to "Windows 7", "xp" to "Windows XP", "win95" to "Windows 95")
    var dlg by remember { mutableStateOf("") }
    val tabs = listOf(0 to tr("Timeline", "Zaman çizelgesi"), 1 to tr("Albums", "Albümler"), 2 to tr("Favorites", "Favoriler"))
    val sorts = listOf("taken" to tr("Date taken", "Çekim tarihi"), "modified" to tr("Date modified", "Değiştirme tarihi"), "name" to tr("Name", "Ad"))
    val groups = listOf("day" to tr("Day", "Gün"), "month" to tr("Month", "Ay"), "none" to tr("None", "Yok"))
    var albums by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(dlg) { if (dlg == "hidden") albums = withContext(Dispatchers.IO) { queryPhotos(ctx).map { it.album } }.distinct().sorted() }
    val ic: (androidx.compose.ui.graphics.vector.ImageVector) -> (@Composable () -> Unit) = { v -> { LIc(v, T.text) } }
    Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopBtn(Icons.Outlined.ArrowBack, onClose)
            Text(tr("Settings", "Ayarlar"), color = T.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
        }
        val sc = rememberScrollState()
        Column(Modifier.weight(1f).verticalScroll(sc).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SetSection(tr("Look", "Görünüm"), ic(Icons.Outlined.Palette), true) {
                SetRow(ic(Icons.Outlined.Palette), tr("Look", "Görünüm"), looks.first { it.first == PhotosPrefs.look }.second, { dlg = "look" })
            }
            SetSection(tr("Colours", "Renkler"), ic(Icons.Outlined.ColorLens), true) {
                SetSwitchRow(ic(Icons.Outlined.DarkMode), "AMOLED", PhotosPrefs.amoled) { v -> change { PhotosPrefs.amoled = v } }
                if (T.material && !PhotosPrefs.amoled) {
                    if (Build.VERSION.SDK_INT >= 31) SetSwitchRow(ic(Icons.Outlined.Palette), "Material You", PhotosPrefs.dynamic) { v -> change { PhotosPrefs.dynamic = v } }
                    if (!PhotosPrefs.dynamic || Build.VERSION.SDK_INT < 31) SetRow(ic(Icons.Outlined.ColorLens), tr("Accent", "Vurgu"), null, { dlg = "accent" }) { MaterialAccents.getOrNull(PhotosPrefs.accent)?.let { ColorDot(it) } }
                }
            }
            SetSection(tr("Language", "Dil"), ic(Icons.Outlined.Language)) {
                SetRow(ic(Icons.Outlined.Language), tr("Language", "Dil"), if (PhotosPrefs.lang == "tr") "Türkçe" else "English", { dlg = "lang" })
            }
            SetSection(tr("Library", "Kitaplık"), ic(Icons.Outlined.PhotoLibrary)) {
                SetRow(ic(Icons.Outlined.Collections), tr("Start tab", "Açılış sekmesi"), tabs.first { it.first == PhotosPrefs.startTab }.second, { dlg = "tab" })
                SetRow(ic(Icons.Outlined.GridView), tr("Columns", "Sütun"), PhotosPrefs.cols.toString(), { dlg = "cols" })
                SetSwitchRow(ic(Icons.Outlined.Videocam), tr("Videos", "Videolar"), PhotosPrefs.showVideos) { v -> change { PhotosPrefs.showVideos = v } }
                SetSwitchRow(ic(Icons.Outlined.VisibilityOff), tr("Hidden folders", "Gizli klasörler"), PhotosPrefs.showHidden) { v -> change { PhotosPrefs.showHidden = v } }
                SetRow(ic(Icons.Outlined.Close), tr("Hidden albums", "Gizli albümler"), PhotosPrefs.hiddenAlbums.size.toString(), { dlg = "hidden" })
                SetRow(ic(Icons.Outlined.Sort), tr("Sort by", "Sırala"), sorts.first { it.first == PhotosPrefs.sortBy }.second, { dlg = "sort" })
                SetSwitchRow(ic(Icons.Outlined.Sort), tr("Oldest first", "Eskiden yeniye"), PhotosPrefs.sortAsc) { v -> change { PhotosPrefs.sortAsc = v } }
                SetRow(ic(Icons.Outlined.ViewAgenda), tr("Group by", "Grupla"), groups.first { it.first == PhotosPrefs.group }.second, { dlg = "group" })
            }
            SetSection(tr("Viewer", "Görüntüleyici"), ic(Icons.Outlined.Image)) {
                SetRow(ic(Icons.Outlined.Slideshow), tr("Slideshow", "Slayt"), "${PhotosPrefs.slideSec} s", { dlg = "slide" })
                SetSwitchRow(ic(Icons.Outlined.Collections), tr("Film strip", "Film şeridi"), PhotosPrefs.filmstrip) { v -> change { PhotosPrefs.filmstrip = v } }
                SetSwitchRow(ic(Icons.Outlined.Repeat), tr("Loop live video", "Canlı videoyu döngüle"), PhotosPrefs.loopVideos) { v -> change { PhotosPrefs.loopVideos = v } }
                SetSwitchRow(ic(Icons.Outlined.SlowMotionVideo), tr("Live autoplay", "Canlı otomatik oynat"), PhotosPrefs.liveAuto) { v -> change { PhotosPrefs.liveAuto = v } }
                SetRow(ic(Icons.Outlined.ZoomIn), tr("Double-tap zoom", "Çift dokunma yakınlaştırma"), "×" + PhotosPrefs.zoomLevel.toString().removeSuffix(".0"), { dlg = "zoom" })
                SetSwitchRow(ic(Icons.Outlined.Delete), tr("Confirm delete", "Silmeyi onayla"), PhotosPrefs.confirmDelete) { v -> change { PhotosPrefs.confirmDelete = v } }
                SetSwitchRow(ic(Icons.Outlined.Visibility), tr("Keep screen on", "Ekranı açık tut"), PhotosPrefs.keepOn) { v -> change { PhotosPrefs.keepOn = v } }
                SetSwitchRow(ic(Icons.Outlined.Info), tr("Show details", "Ayrıntıları göster"), PhotosPrefs.exifDefault) { v -> change { PhotosPrefs.exifDefault = v } }
            }
        }
    }
    when (dlg) {
        "look" -> SetLookDialog(tr("Look", "Görünüm"), looks.map { it.first }, PhotosPrefs.look, { id -> looks.first { it.first == id }.second }, { id -> change { PhotosPrefs.look = id } }) { dlg = "" }
        "accent" -> SetColorDialog(tr("Accent", "Vurgu"), MaterialAccents, PhotosPrefs.accent, { i -> change { PhotosPrefs.accent = i } }) { dlg = "" }
        "tab" -> SetPickDialog(tr("Start tab", "Açılış sekmesi"), tabs, PhotosPrefs.startTab, { v -> change { PhotosPrefs.startTab = v } }, { dlg = "" })
        "cols" -> SetPickDialog(tr("Columns", "Sütun"), (2..7).map { it to it.toString() }, PhotosPrefs.cols, { v -> change { PhotosPrefs.cols = v } }, { dlg = "" })
        "sort" -> SetPickDialog(tr("Sort by", "Sırala"), sorts, PhotosPrefs.sortBy, { v -> change { PhotosPrefs.sortBy = v } }, { dlg = "" })
        "group" -> SetPickDialog(tr("Group by", "Grupla"), groups, PhotosPrefs.group, { v -> change { PhotosPrefs.group = v } }, { dlg = "" })
        "slide" -> SetPickDialog(tr("Slideshow", "Slayt"), listOf(2, 3, 5, 8, 12).map { it to "$it s" }, PhotosPrefs.slideSec, { v -> change { PhotosPrefs.slideSec = v } }, { dlg = "" })
        "zoom" -> SetPickDialog(tr("Double-tap zoom", "Çift dokunma yakınlaştırma"), listOf(1.5f, 2.5f, 4f).map { it to "×" + it.toString().removeSuffix(".0") }, PhotosPrefs.zoomLevel, { v -> change { PhotosPrefs.zoomLevel = v } }, { dlg = "" })
        "hidden" -> WinAlert(onDismissRequest = { dlg = "" }, title = { Text(tr("Hidden albums", "Gizli albümler")) },
            text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                (albums + PhotosPrefs.hiddenAlbums).distinct().sorted().forEach { a ->
                    val on = a in PhotosPrefs.hiddenAlbums
                    SetSwitchRow(ic(Icons.Outlined.Collections), a, on) { v -> change { PhotosPrefs.hiddenAlbums = if (v) PhotosPrefs.hiddenAlbums + a else PhotosPrefs.hiddenAlbums - a } }
                }
            } },
            confirmButton = { TextButton({ dlg = "" }) { LIc(Icons.Outlined.Check, Look.accent) } })
        "lang" -> SetPickDialog(tr("Language", "Dil"), listOf("en" to "English", "tr" to "Türkçe"), PhotosPrefs.lang, { v -> change { PhotosPrefs.lang = v } }, { dlg = "" })
    }
}

/** Fades the content in again whenever [key] (the look) changes; the first composition stays still. Shared by the standalone Files, Photos, Calendar and Contacts apps. */
@Composable
internal fun LookFade(key: Any = Look.theme.id, content: @Composable () -> Unit) {
    val a = remember { androidx.compose.animation.core.Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) { if (first) first = false else { a.snapTo(.25f); a.animateTo(1f, androidx.compose.animation.core.tween(220)) } }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a.value }) { content() }
}

/** Collapsible settings section: icon + title + rotating chevron (plain header, no pill); the body expands and collapses with animation. */
@Composable
internal fun SetSection(title: String, icon: @Composable () -> Unit, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    val rot by animateFloatAsState(if (open) 90f else 0f, androidx.compose.animation.core.tween(200), label = "chev")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.runtime.key(T.id) { icon() }
            Text(title, Modifier.weight(1f).padding(start = 14.dp), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            LIc(Icons.Outlined.KeyboardArrowRight, T.sub, 22.dp, Modifier.graphicsLayer { rotationZ = rot })
        }
        androidx.compose.animation.AnimatedVisibility(open) {
            Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = content)
        }
    }
}

/** Material vector -> the look's own icon key (PIcon). */
internal fun iconKeyOf(v: androidx.compose.ui.graphics.vector.ImageVector): String = when (v) {
    Icons.Outlined.PhotoLibrary, Icons.Outlined.Image, Icons.Outlined.PhotoCamera, Icons.Outlined.Wallpaper -> "image"
    Icons.Outlined.Collections, Icons.Outlined.ViewAgenda -> "gridView"
    Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite -> "heart"
    Icons.Outlined.ArrowBack, Icons.AutoMirrored.Outlined.ArrowBack -> "back"
    Icons.Outlined.Close -> "close"; Icons.Outlined.Share -> "share"; Icons.Outlined.Delete -> "delete"; Icons.Outlined.Search -> "search"
    Icons.Outlined.Settings -> "settings"; Icons.Outlined.Check -> "check"; Icons.Outlined.ColorLens, Icons.Outlined.Palette -> "palette"
    Icons.Outlined.DarkMode -> "light"; Icons.Outlined.Language, Icons.Outlined.Public -> "globe"; Icons.Outlined.SortByAlpha -> "sortName"
    Icons.Outlined.CalendarMonth, Icons.Outlined.Event -> "calendar"; Icons.Outlined.Schedule -> "history"; Icons.Outlined.Add -> "add"
    Icons.Outlined.Person -> "person"; Icons.Outlined.Star, Icons.Outlined.StarBorder -> "star"; Icons.Outlined.Edit -> "edit"
    Icons.Outlined.PlayArrow, Icons.Outlined.SlowMotionVideo, Icons.Outlined.Videocam, Icons.Outlined.Slideshow -> "play"; Icons.Outlined.Sort -> "sortName"; Icons.Outlined.ZoomIn -> "zoomIn"; Icons.Outlined.Timer -> "history"
    Icons.Outlined.FileDownload -> "download"; Icons.Outlined.FileUpload -> "upload"; Icons.Outlined.Info -> "info"
    else -> "info"
}

/** Icon that follows the look: the Material vector in Material looks, the look's own icon pack otherwise; re-resolved whenever the look changes. */
@Composable
internal fun LIc(v: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = T.text, size: androidx.compose.ui.unit.Dp = 24.dp, modifier: Modifier = Modifier) =
    androidx.compose.runtime.key(T.id) { if (T.material) Icon(v, null, modifier.size(size), tint) else PIcon(iconKeyOf(v), tint, size, modifier) }

/** Soft fade at the start / end of a scroll area, only on the side where more content can scroll. Use [fadingEdges] with a ScrollState or LazyListState. */
internal fun Modifier.fadingEdges(vertical: Boolean, back: () -> Boolean, fwd: () -> Boolean, len: androidx.compose.ui.unit.Dp = 24.dp): Modifier =
    this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        val l = len.toPx(); val total = if (vertical) size.height else size.width
        fun edge(from: Float, to: Float, a: Float, b: Float) {
            val br = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.Black.copy(alpha = a), Color.Black.copy(alpha = b)),
                if (vertical) Offset(0f, from) else Offset(from, 0f), if (vertical) Offset(0f, to) else Offset(to, 0f))
            val lo = minOf(from, to)
            drawRect(br, if (vertical) Offset(0f, lo) else Offset(lo, 0f), if (vertical) androidx.compose.ui.geometry.Size(size.width, l) else androidx.compose.ui.geometry.Size(l, size.height), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
        }
        if (back()) edge(0f, l, 0f, 1f)
        if (fwd()) edge(total - l, total, 1f, 0f)
    }
internal fun Modifier.fadingEdges(s: androidx.compose.foundation.ScrollState, vertical: Boolean = false) = fadingEdges(vertical, { s.canScrollBackward }, { s.canScrollForward })
internal fun Modifier.fadingEdges(s: androidx.compose.foundation.lazy.LazyListState, vertical: Boolean = false) = fadingEdges(vertical, { s.canScrollBackward }, { s.canScrollForward })

/** Full-width settings row: icon, title, current value as secondary text; [trailing] for a control. */
@Composable
internal fun SetRow(icon: @Composable () -> Unit, title: String, value: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 48.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.runtime.key(T.id) { icon() }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, color = T.text, fontSize = 16.sp)
            if (!value.isNullOrEmpty()) Text(value, color = T.sub, fontSize = 13.sp)
        }
        if (trailing != null) androidx.compose.runtime.key(T.id) { trailing() }
    }
}

/** Settings row with the look's own switch / checkbox (PSwitch). */
@Composable
internal fun SetSwitchRow(icon: @Composable () -> Unit, title: String, on: Boolean, onChange: (Boolean) -> Unit) =
    SetRow(icon, title, null, { onChange(!on) }) { PSwitch(on, onChange) }

/** Dialog with a radio list (PRadio, the look's own); [lead] draws a small preview before each option. Tap picks and closes. */
@Composable
internal fun <K> SetPickDialog(title: String, options: List<Pair<K, String>>, cur: K, onPick: (K) -> Unit, onDismiss: () -> Unit, lead: (@Composable (K) -> Unit)? = null) =
    PickDialogImpl(title, options, cur, onPick, onDismiss, lead, false)

@Composable
private fun <K> PickDialogImpl(title: String, options: List<Pair<K, String>>, cur: K, onPick: (K) -> Unit, onDismiss: () -> Unit, lead: (@Composable (K) -> Unit)?, fade: Boolean) {
    WinAlert(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            val sc = rememberScrollState()
            Column(Modifier.heightIn(max = 420.dp).then(if (fade) Modifier.fadingEdges(sc, true) else Modifier).verticalScroll(sc)) {
                options.forEach { (k, n) ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(k); onDismiss() }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        PRadio(k == cur) { onPick(k); onDismiss() }
                        if (lead != null) { lead(k); Spacer(Modifier.width(10.dp)) }
                        Text(n, Modifier.weight(1f), color = T.text, fontSize = 15.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { LIc(Icons.Outlined.Check, Look.accent) } })
}

/** Small look preview used inside the look dialog: the look's own background, panel, bar and accent. */
@Composable
internal fun LookThumb(th: PTheme) {
    val sh = RoundedCornerShape(if (th.material) 8.dp else 2.dp)
    Box(Modifier.size(34.dp, 48.dp).clip(sh).background(th.bg).border(1.dp, th.line, sh)) {
        Box(Modifier.fillMaxWidth().height(9.dp).then(th.title?.let { Modifier.background(it) } ?: Modifier.background(th.panel)))
        Box(Modifier.align(Alignment.Center).size(14.dp).background(th.accent.copy(alpha = 1f), if (th.material) CircleShape else androidx.compose.ui.graphics.RectangleShape))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(24.dp, 6.dp).background(th.panel))
    }
}

/** Look chooser dialog: radio list with a small preview thumbnail per look. [ids] are Theme ids. */
@Composable
internal fun SetLookDialog(title: String, ids: List<String>, cur: String, name: (String) -> String, onPick: (String) -> Unit, onDismiss: () -> Unit) =
    PickDialogImpl(title, ids.map { it to name(it) }, cur, onPick, onDismiss, { id -> Themes.firstOrNull { it.id == id }?.let { LookThumb(it) } }, true)

/** Dialog grid of round colour dots; [cur] is the selected index. */
@Composable
internal fun SetColorDialog(title: String, colors: List<Color>, cur: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    WinAlert(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                colors.withIndex().chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { (i, c) ->
                            Box(Modifier.size(44.dp).background(c, CircleShape).border(if (i == cur) 3.dp else 1.dp, if (i == cur) T.text else T.line, CircleShape)
                                .clickable { onPick(i); onDismiss() }, contentAlignment = Alignment.Center) { if (i == cur) LIc(Icons.Outlined.Check, onAccent(c), 20.dp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { LIc(Icons.Outlined.Check, Look.accent) } })
}

/** Round colour dot shown as the value of an accent row. */
@Composable
internal fun ColorDot(c: Color) = Box(Modifier.size(24.dp).background(c, CircleShape).border(1.dp, T.line, CircleShape))
