package com.umutk.pulse

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import com.umutk.pulse.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import android.widget.VideoView
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Extensions the Pulse file manager hands to the photo viewer. */
val IMG_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")

/** Camera RAW files: the picture is developed by the RAW editor (CalibRaw), not by the photo viewer. */
val RAW_EXTS = setOf("dng", "cr2", "cr3", "nef", "nrw", "arw", "raf", "orf", "rw2", "pef", "srw")
private const val RAW_EDITOR = "de.duecki.calibraw"
private const val RAW_EDITOR_PAGE = "https://github.com/Duecki1/CalibRaw/releases/latest"

/** Opens a RAW file in the RAW editor when it is installed (it takes the file from Pulse); otherwise its release page. */
fun rawOpen(ctx: Context, f: File) {
    val installed = runCatching { ctx.packageManager.getPackageInfo(RAW_EDITOR, 0); true }.getOrDefault(false)
    val i = if (installed) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*").setPackage(RAW_EDITOR)
            .setClipData(android.content.ClipData.newRawUri("", uri))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    } else Intent(Intent.ACTION_VIEW, Uri.parse(RAW_EDITOR_PAGE))
    if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (e: Exception) { Toast.makeText(ctx, tr("No app can open this file", "Bu dosyayı açacak uygulama yok"), Toast.LENGTH_SHORT).show() }
}

/** One picture of the viewer: a local file (editable, deletable) or just a content / file URI from another app. */
internal class ImgSrc(val uri: Uri, val file: File?, val name: String)

/** Opens [f] in the Pulse photo viewer; the other pictures of its folder can be swiped to. */
fun imgOpen(ctx: Context, f: File) {
    val i = Intent(ctx, ImageViewActivity::class.java).setData(Uri.fromFile(f)).putExtra("path", f.absolutePath)
    if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (e: Exception) { Toast.makeText(ctx, "Could not open: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
}

/** The pictures of [folder], sorted by name. */
internal fun imgSiblingsOf(folder: File): List<File> =
    runCatching {
        folder.listFiles()?.filter { it.isFile && !it.name.startsWith(".") && it.extension.lowercase() in IMG_EXTS }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }.getOrDefault(emptyList())

class ImageViewActivity : ComponentActivity() {
    private val items = mutableStateListOf<ImgSrc>()
    private var startIdx = 0
    private val favAsk = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { PhotosPrefs.tick++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this); PhotosPrefs.load(this); PhotosPrefs.apply()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0); paintPhotos()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val uri = intent.data
        val fromPath = intent.getStringExtra("path")?.let { File(it) }?.takeIf { it.isFile }
        val f = fromPath ?: if (uri != null && uri.scheme == "file") uri.path?.let { File(it) }?.takeIf { it.isFile } else null
        if (f != null) {
            val sib = f.parentFile?.let { imgSiblingsOf(it) } ?: emptyList()
            val list = if (sib.any { it.absolutePath == f.absolutePath }) sib else listOf(f)
            list.forEach { items.add(ImgSrc(Uri.fromFile(it), it, it.name)) }
            startIdx = list.indexOfFirst { it.absolutePath == f.absolutePath }.coerceAtLeast(0)
        } else if (uri != null) {
            items.add(ImgSrc(uri, null, nameOf(uri)))
        }
        // started from the launcher (no picture): the gallery of the device, a tap opens the viewer and back returns to the gallery
        val gallery = items.isEmpty()
        PhotosPrefs.launchSender = { sender -> favAsk.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build()) }
        setContent { LookHost(transparent = false, lookId = PhotosPrefs.look) { th ->
            if (PhotosPrefs.amoled && th.material) Look.resolved = PhotosPrefs.black(th)
            var viewing by remember { mutableStateOf(!gallery) }
            if (viewing) ImgViewerScreen(items, startIdx) { if (gallery) { viewing = false; items.clear() } else finish() }
            else PhotosGalleryScreen { l, i -> items.clear(); items.addAll(l); startIdx = i; viewing = true }
        } }
    }

    // the Photos look / language are process-wide singletons: give the other Pulse screens their own back
    private fun paintPhotos() = paintWindow(Look.mode == "dark" || (Look.mode != "light" && (resources.configuration.uiMode and 0x30) == 0x20), PhotosPrefs.amoled, Look.theme)
    private fun enter() = AppLookGuard.enter({ PhotosPrefs.apply() }, { paintPhotos() })
    override fun onStart() { enter(); super.onStart() }
    override fun onResume() { enter(); super.onResume() }
    override fun onStop() { super.onStop(); AppLookGuard.leave(this) }
    override fun onDestroy() { super.onDestroy(); PhotosPrefs.launchSender = null; AppLookGuard.leave(this) }

    private fun nameOf(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "image"
        return runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "image"
    }
}

// ---------------------------------------------------------------------------------------------------------------- decoding

internal fun imgStream(ctx: Context, s: ImgSrc): InputStream? =
    runCatching { if (s.file != null) FileInputStream(s.file) else ctx.contentResolver.openInputStream(s.uri) }.getOrNull()

private fun imgExifMatrix(ctx: Context, s: ImgSrc): android.graphics.Matrix? = runCatching {
    imgStream(ctx, s)?.use { ins ->
        val o = ExifInterface(ins).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val m = android.graphics.Matrix()
        when (o) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
            else -> return@use null
        }
        m
    }
}.getOrNull()

/** Decodes [s] sub-sampled so the long edge stays around [maxDim] pixels, with the EXIF rotation applied. Null when it cannot be read. */
internal fun imgDecode(ctx: Context, s: ImgSrc, maxDim: Int): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    imgStream(ctx, s)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
    val raw = imgStream(ctx, s)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return@runCatching null
    val m = imgExifMatrix(ctx, s)
    if (m == null) raw else (Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() })
}.getOrNull()

/** Frame of a video (API 29+); null for anything else. */
private fun videoThumb(ctx: Context, s: ImgSrc, px: Int): Bitmap? = runCatching {
    if (android.os.Build.VERSION.SDK_INT < 29) return@runCatching null
    val sz = android.util.Size(px, px)
    if (s.file != null) android.media.ThumbnailUtils.createVideoThumbnail(s.file, sz, null) else ctx.contentResolver.loadThumbnail(s.uri, sz, null)
}.getOrNull()

/** Small thumbnails shared by the grid and the filmstrip. */
internal object ImgThumbs {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 12).toInt().coerceAtLeast(2 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    fun get(k: String): Bitmap? = cache.get(k)
    fun put(k: String, b: Bitmap) { cache.put(k, b) }
}

@Composable
internal fun ImgThumb(src: ImgSrc, modifier: Modifier = Modifier, px: Int = 220) {
    val ctx = LocalContext.current
    val key = src.uri.toString() + "|" + (src.file?.lastModified() ?: 0L)
    val bmp by produceState<Bitmap?>(ImgThumbs.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { (imgDecode(ctx, src, px) ?: videoThumb(ctx, src, px))?.also { ImgThumbs.put(key, it) } }
    }
    val b = bmp
    val fade by androidx.compose.animation.core.animateFloatAsState(if (b != null) 1f else 0f, androidx.compose.animation.core.tween(180), label = "thumb")
    Box(modifier.background(T.line.copy(alpha = .35f))) {
        if (b != null) Image(remember(b) { b.asImageBitmap() }, null, Modifier.fillMaxSize().graphicsLayer { alpha = fade }, contentScale = ContentScale.Crop)
    }
}

/** Thumbnail grid of the pictures of [folder]; [onOpen] gets the tapped file. */
@Composable
fun ImageGalleryGrid(folder: File, onOpen: (File) -> Unit) {
    val files = remember(folder) { imgSiblingsOf(folder) }
    if (files.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(48.dp), T.sub) }
        return
    }
    val r = if (T.material) 10.dp else 0.dp
    LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items(files, key = { it.absolutePath }) { f ->
            ImgThumb(ImgSrc(Uri.fromFile(f), f, f.name), Modifier.aspectRatio(1f).clip(RoundedCornerShape(r)).clickable { onOpen(f) })
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------- live photos

/** A live photo's video: [pair] is a same-name .mov / .mp4 beside the picture, otherwise the MP4 sits in the last [tail] bytes of the JPEG (0 = find it by its "ftyp" box). */
internal class LiveInfo(val pair: File?, val tail: Long)

/** Null when [s] is a plain picture. Reads only the first 256 KB (where the XMP packet lives). */
internal fun imgLiveInfo(ctx: Context, s: ImgSrc): LiveInfo? = runCatching {
    s.file?.let { f ->
        val base = f.nameWithoutExtension
        f.parentFile?.listFiles()?.firstOrNull { it.isFile && it.length() in 1..25_000_000L && it.nameWithoutExtension.equals(base, true) && it.extension.lowercase() in setOf("mov", "mp4") }?.takeIf { f.extension.lowercase() in setOf("jpg", "jpeg", "heic", "heif") }?.let { return@runCatching LiveInfo(it, 0) }
    }
    val buf = ByteArray(256 * 1024)
    val n = imgStream(ctx, s)?.use { ins -> var o = 0; while (o < buf.size) { val r = ins.read(buf, o, buf.size - o); if (r <= 0) break; o += r }; o } ?: 0
    if (n < 4 || buf[0] != 0xFF.toByte() || buf[1] != 0xD8.toByte()) return@runCatching null
    val h = String(buf, 0, n, Charsets.ISO_8859_1)
    Regex("MicroVideoOffset\\s*=\\s*\"(\\d+)\"").find(h)?.let { m -> m.groupValues[1].toLong().takeIf { it > 0 }?.let { return@runCatching LiveInfo(null, it) } }
    // ordinary photos also carry MotionPhoto="0": only a flag of 1 plus a real video item counts
    if (!Regex("MotionPhoto\\s*=\\s*\"1\"").containsMatchIn(h)) return@runCatching null
    val len = Regex("<Container:Item[^>]*>").findAll(h).map { it.value }.firstOrNull { it.contains("video/mp4") }
        ?.let { Regex("Item:Length\\s*=\\s*\"(\\d+)\"").find(it)?.groupValues?.get(1)?.toLong() } ?: 0L
    if (len <= 0L) return@runCatching null
    LiveInfo(null, len)
}.getOrNull()

/** The playable video file of a live photo (extracted into the cache for embedded ones), or null. */
internal fun imgLiveFile(ctx: Context, s: ImgSrc, li: LiveInfo): File? = runCatching {
    li.pair?.let { return@runCatching it }
    val out = File(File(ctx.cacheDir, "live").apply { mkdirs() }, "${s.uri.toString().hashCode()}_${s.file?.lastModified() ?: 0L}.mp4")
    if (out.length() > 0) return@runCatching out
    val all = imgStream(ctx, s)?.use { it.readBytes() } ?: return@runCatching null
    var start = if (li.tail in 1 until all.size.toLong()) (all.size - li.tail).toInt() else -1
    if (start < 0) { // shortcut: first "ftyp" after the JPEG header, wrong only if the EXIF thumbnail were an MP4
        val pat = "ftyp".toByteArray(Charsets.ISO_8859_1)
        var i = 8
        while (i < all.size - 4 && start < 0) { if (all[i] == pat[0] && all[i + 1] == pat[1] && all[i + 2] == pat[2] && all[i + 3] == pat[3]) start = i - 4; i++ }
    }
    if (start < 0) return@runCatching null
    out.outputStream().use { it.write(all, start, all.size - start) }
    out
}.getOrNull()

/** Plays [f] in place; [onDone] when it ends or fails. */
@Composable
private fun ImgLivePlayer(f: File, onDone: () -> Unit) {
    AndroidView(factory = { c ->
        VideoView(c).apply {
            setVideoPath(f.absolutePath)
            setOnPreparedListener { it.isLooping = PhotosPrefs.loopVideos; start() }
            setOnCompletionListener { onDone() }
            setOnErrorListener { _, _, _ -> onDone(); true }
        }
    }, modifier = Modifier.fillMaxSize().background(Color.Black), onRelease = { it.stopPlayback() })
}

// ---------------------------------------------------------------------------------------------------------------- actions

internal fun imgShare(ctx: Context, s: ImgSrc) {
    runCatching {
        val uri = if (s.file != null) FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", s.file) else s.uri
        val i = Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun imgSetAs(ctx: Context, s: ImgSrc) {
    runCatching {
        val uri = if (s.file != null) FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", s.file) else s.uri
        val i = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun imgInfoRows(ctx: Context, s: ImgSrc): List<Pair<String, String>> {
    val rows = ArrayList<Pair<String, String>>()
    rows.add(tr("Name", "Ad") to s.name)
    runCatching {
        val len = s.file?.length() ?: ctx.contentResolver.query(s.uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else -1L } ?: -1L
        if (len >= 0) rows.add(tr("Size", "Boyut") to Formatter.formatFileSize(ctx, len))
    }
    runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        imgStream(ctx, s)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth > 0) rows.add(tr("Resolution", "Çözünürlük") to "${o.outWidth} × ${o.outHeight}  (${"%.1f".format(Locale.US, o.outWidth * o.outHeight / 1_000_000.0)} MP)")
    }
    s.file?.let { rows.add(tr("Date", "Tarih") to SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it.lastModified()))) }
    rows.add(tr("Path", "Yol") to (s.file?.absolutePath ?: s.uri.toString()))
    return rows
}

// ---------------------------------------------------------------------------------------------------------------- screen

@Composable
internal fun ImgViewerScreen(items: SnapshotStateList<ImgSrc>, start: Int, onFinish: () -> Unit) {
    val ctx = LocalContext.current
    if (items.isEmpty()) { LaunchedEffect(Unit) { onFinish() }; return }
    val pager = rememberPagerState(start.coerceIn(0, items.lastIndex)) { items.size }
    val scope = rememberCoroutineScope()
    var bars by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var zoomTick by remember { mutableIntStateOf(0) }
    var liveTick by remember { mutableIntStateOf(0) }
    var hasLive by remember { mutableStateOf(false) }
    var favs by remember { mutableStateOf(PhotosPrefs.favs) }
    var rev by remember { mutableIntStateOf(0) }
    var info by remember { mutableStateOf(PhotosPrefs.exifDefault) }
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(PhotosPrefs.keepOn) { view.keepScreenOn = PhotosPrefs.keepOn; onDispose { view.keepScreenOn = false } }
    var del by remember { mutableStateOf(false) }
    var grid by remember { mutableStateOf(false) }
    var slide by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ImgSrc?>(null) }
    val cur = items.getOrNull(pager.currentPage)

    LaunchedEffect(pager.currentPage) { zoomed = false }
    if (slide) LaunchedEffect(slide) {
        while (slide && items.size > 1) { delay(PhotosPrefs.slideSec * 1000L); runCatching { pager.animateScrollToPage((pager.currentPage + 1) % items.size) } }
    }
    BackHandler(enabled = grid) { grid = false }
    BackHandler(enabled = info) { info = false }

    fun doDelete(s: ImgSrc) {
        val ok = runCatching { if (s.file != null) s.file.delete() else ctx.contentResolver.delete(s.uri, null, null) > 0 }.getOrDefault(false)
        if (ok) {
            s.file?.let { MediaScannerConnection.scanFile(ctx, arrayOf(it.absolutePath), null, null) }
            items.remove(s)
        } else Toast.makeText(ctx, tr("Could not delete", "Silinemedi"), Toast.LENGTH_SHORT).show()
    }

    val acts = ImgActs(
        onBack = onFinish,
        onShare = { cur?.let { imgShare(ctx, it) } },
        onEdit = { if (cur != null) editing = cur },
        onInfo = { info = true },
        onDelete = { if (cur != null) { if (PhotosPrefs.confirmDelete) del = true else doDelete(cur) } },
        onGrid = if (cur?.file != null) ({ grid = true }) else null,
        onZoom = { zoomTick++ },
        onSlide = { slide = !slide },
        slideOn = slide,
        onSetAs = { cur?.let { imgSetAs(ctx, it) } },
        onLive = if (hasLive) ({ liveTick++ }) else null,
        fav = cur != null && cur.uri.toString() in favs,
        onFav = { cur?.let { favs = PhotosPrefs.toggleFav(ctx, it.uri.toString()) } },
    )
    val rows = remember(info, cur, rev) { if (info && cur != null) imgInfoRows(ctx, cur) else emptyList() }

    Box(Modifier.fillMaxSize()) {
        val count = "${pager.currentPage + 1} / ${items.size}"
        val gal = imgKind().let { it == ImgKind.MATERIAL || it == ImgKind.ONEUI || it == ImgKind.COLOROS }
        val stamp = cur?.file?.takeIf { gal }?.lastModified()?.let { Date(it) }
        ImgChrome(if (stamp != null) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(stamp) else cur?.name ?: "",
            if (stamp != null) SimpleDateFormat("HH:mm", Locale.getDefault()).format(stamp) + "  ·  " + count else count, bars, acts, items, pager.currentPage, { i -> scope.launch { runCatching { pager.scrollToPage(i) } } }) {
            HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 8.dp, userScrollEnabled = !zoomed, key = { items.getOrNull(it)?.uri?.toString() ?: it }) { p ->
                val s = items.getOrNull(p)
                if (s != null) ImgPage(s, rev, p == pager.currentPage, zoomTick, liveTick, { l -> if (p == pager.currentPage) hasLive = l }, { z -> if (p == pager.currentPage) zoomed = z }) { bars = !bars }
            }
        }
        if (info) ImgInfoSheet(rows, { info = false }, Modifier.align(Alignment.BottomCenter))
        if (del && cur != null) {
            WinAlert(onDismissRequest = { del = false },
                title = { Text(tr("Delete this picture?", "Bu resim silinsin mi?")) },
                text = { Text(cur.name, color = T.sub, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                confirmButton = { TextButton({ del = false; doDelete(cur) }) { Text(tr("Delete", "Sil")) } },
                dismissButton = { TextButton({ del = false }) { Text(tr("Cancel", "İptal")) } })
        }
        val gridFolder = cur?.file?.parentFile
        if (grid && gridFolder != null) {
            val folder = gridFolder
            Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ImgIcBtn({ grid = false }) { ImgIc("back", Icons.Outlined.ArrowBack, T.text) }
                    Text(folder.name, color = T.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                Box(Modifier.weight(1f)) {
                    ImageGalleryGrid(folder) { f ->
                        val i = items.indexOfFirst { it.file?.absolutePath == f.absolutePath }
                        grid = false
                        if (i >= 0) scope.launch { runCatching { pager.scrollToPage(i) } }
                    }
                }
            }
        }
        editing?.let { e ->
            ImageEditor(e, { editing = null }) { saved, overwritten ->
                editing = null
                if (overwritten) rev++
                else {
                    val at = (items.indexOf(e) + 1).coerceIn(0, items.size)
                    items.add(at, ImgSrc(Uri.fromFile(saved), saved, saved.name))
                    scope.launch { runCatching { pager.scrollToPage(at) } }
                }
            }
        }
    }
}

/** One page of the pager: pinch zoom, pan, double tap zoom, single tap hides / shows the bars. */
@Composable
private fun ImgPage(s: ImgSrc, rev: Int, active: Boolean, zoomTick: Int, liveTick: Int, onLive: (Boolean) -> Unit, onZoomed: (Boolean) -> Unit, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val live by produceState<LiveInfo?>(null, s, rev) { value = withContext(Dispatchers.IO) { imgLiveInfo(ctx, s) } }
    var vid by remember { mutableStateOf<File?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) vid = null }
    fun playLive() {
        val li = live ?: return
        if (vid != null) { vid = null; return }
        if (busy) return
        busy = true
        scope.launch { vid = withContext(Dispatchers.IO) { imgLiveFile(ctx, s, li) }; busy = false }
    }
    val ld by produceState<Pair<Boolean, Bitmap?>>(false to null, s, rev) { value = true to withContext(Dispatchers.IO) { imgDecode(ctx, s, 2560) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val b = ld.second

    fun maxOff(sc: Float): Offset {
        if (b == null || box.width == 0 || box.height == 0) return Offset.Zero
        val fit = min(box.width.toFloat() / b.width, box.height.toFloat() / b.height)
        return Offset(max(0f, (b.width * fit * sc - box.width) / 2f), max(0f, (b.height * fit * sc - box.height) / 2f))
    }
    fun toggle(p: Offset) {
        var ns = 1f; var tx = 0f; var ty = 0f
        if (scale <= 1.01f) {
            ns = PhotosPrefs.zoomLevel; val mo = maxOff(ns)
            val c = p - Offset(box.width / 2f, box.height / 2f)
            tx = (c.x * (1 - ns)).coerceIn(-mo.x, mo.x); ty = (c.y * (1 - ns)).coerceIn(-mo.y, mo.y)
        }
        val s0 = scale; val x0 = ox; val y0 = oy
        scope.launch { animate(0f, 1f) { v, _ -> scale = s0 + (ns - s0) * v; ox = x0 + (tx - x0) * v; oy = y0 + (ty - y0) * v } }
    }
    var seenLive by remember { mutableIntStateOf(liveTick) }
    LaunchedEffect(live != null, active) { if (active) { onLive(live != null); if (live != null && PhotosPrefs.liveAuto) playLive() } }
    LaunchedEffect(liveTick) { if (liveTick != seenLive) { seenLive = liveTick; if (active) playLive() } }
    var seen by remember { mutableIntStateOf(zoomTick) }
    LaunchedEffect(zoomTick) { if (zoomTick != seen) { seen = zoomTick; if (active) toggle(Offset(box.width / 2f, box.height / 2f)) } }
    LaunchedEffect(scale > 1.01f, active) { if (active) onZoomed(scale > 1.01f) }

    Box(Modifier.fillMaxSize().onSizeChanged { box = it }
        .pointerInput(b, live) { detectTapGestures(onTap = { if (vid != null) vid = null else onTap() }, onDoubleTap = { p -> toggle(p) }, onLongPress = { if (live != null) playLive() }) }
        .pointerInput(b) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val ev = awaitPointerEvent()
                    val n = ev.changes.count { it.pressed }
                    if (n >= 2 || scale > 1.01f) {
                        val z = ev.calculateZoom(); val pan = ev.calculatePan()
                        val c = ev.calculateCentroid() - Offset(box.width / 2f, box.height / 2f)
                        val ns = (scale * z).coerceIn(1f, 8f); val k = ns / scale
                        val mo = maxOff(ns)
                        ox = (ox * k + c.x * (1 - k) + pan.x).coerceIn(-mo.x, mo.x)
                        oy = (oy * k + c.y * (1 - k) + pan.y).coerceIn(-mo.y, mo.y)
                        scale = ns
                        if (ns <= 1.001f) { ox = 0f; oy = 0f }
                        ev.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (ev.changes.any { it.pressed })
            }
        }, contentAlignment = Alignment.Center) {
        when {
            !ld.first -> CircularProgressIndicator(Modifier.size(28.dp), color = Look.accent, strokeWidth = 2.dp)
            b == null -> Icon(Icons.Outlined.BrokenImage, null, Modifier.size(48.dp), T.sub)
            else -> Image(remember(b) { b.asImageBitmap() }, null,
                Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = ox; translationY = oy }, contentScale = ContentScale.Fit)
        }
        vid?.let { f -> ImgLivePlayer(f) { vid = null } }
        if (live != null && active && imgKind() != ImgKind.MATERIAL) Row(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 64.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = .55f))
            .clickable { playLive() }.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (vid != null) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, null, Modifier.size(14.dp), Color.White)
            Spacer(Modifier.width(3.dp))
            Text("LIVE", color = Color.White, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }
}
