package com.umutk.pulse

import com.umutk.pulse.files.editor.SaveTo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import com.umutk.pulse.Icon
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Extensions the Pulse file manager hands to the Office viewer. */
val OF_EXTS = setOf("docx", "xlsx", "pptx", "pdf", "csv", "rtf", "doc", "xls", "ppt", "xlt", "pps", "pot")

/** Opens [f] in the Office viewer (editable where the app allows it). */
fun ofOpen(ctx: Context, f: File, lookDefault: String? = null) = ofOpenUri(ctx, Uri.fromFile(f), lookDefault)

fun ofOpenUri(ctx: Context, uri: Uri, lookDefault: String? = null) {
    // a text file always opens in the text editor (Notepad in the Windows looks), whichever way it was reached
    uri.takeIf { it.scheme == "file" }?.path?.let { File(it) }?.takeIf { ofIsText(it) }?.let { ofOpenText(ctx, it); return }
    val i = Intent(ctx, OfficeViewActivity::class.java).setData(uri).putExtra("edit", true).putExtra("recent", true)
    if (lookDefault != null) i.putExtra("of_look_default", lookDefault)
    if (ctx !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (e: Exception) { Toast.makeText(ctx, "Could not open: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
}

/**
 * The Office viewer window: opens docx / xlsx / pptx / pdf (and best-effort csv, rtf, doc) from the file manager or from other apps.
 * Pulse's own opens carry edit=true; a VIEW from another app is view-only. The file is copied to the cache first, the screens read that copy.
 */
class OfficeViewActivity : ComponentActivity() {
    private var doc by mutableStateOf<OfDoc?>(null)
    private var kind by mutableStateOf<OfKind?>(null)
    private var err by mutableStateOf<String?>(null)
    private var splashDone by mutableStateOf(false)
    private var confirm by mutableStateOf(false)
    private var saveAsTrigger by mutableStateOf<(() -> Unit)?>(null)
    private var saveAsDest: File? = null
    /** a plain Save on a file that cannot be rewritten in its own format (.doc, .rtf, .odt): the writer waits for a Save as target */
    private var pendingSave by mutableStateOf<Pair<(File) -> Unit, (String) -> Unit>?>(null)
    /** "TXT olarak kaydet" asks for a .txt target; the text is the screen's current content */
    private var txtAsk by mutableStateOf(false)
    private val convExt get() = name.substringAfterLast('.', "").lowercase().let { if (it in setOf("doc", "rtf", "odt", "dotx")) "docx" else if (it in setOf("xls", "xlt")) "xlsx" else if (it in setOf("ppt", "pps", "pot")) "pptx" else null }
    private var name = ""
    private var srcFile: File? = null
    private var srcUri: Uri? = null
    private val pickOther = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            runCatching { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            ofOpenUri(this, u)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        saveLook()
        Cfg.load(this); Opt.load(this)
        OfficePrefs.defLook = intent.getStringExtra("of_look_default") ?: if (intent.action != null) "material" else "365"
        OfficePrefs.load(this)
        val uri = intent.data
        if (uri == null) { finish(); return }
        srcUri = uri
        if (uri.scheme == "file") srcFile = uri.path?.let { File(it) }
        name = nameOf(uri)
        kind = ofKindOf(name, intent.type ?: runCatching { contentResolver.getType(uri) }.getOrNull())
        val k = kind
        if (k == null) { err = "Unsupported file type: $name"; kind = OfKind.WORD }
        setContent { LookHost(transparent = true) { Screen() } }
        if (k != null) Thread { load(uri, k) }.start()
    }

    private fun nameOf(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "document"
        return runCatching { contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/') ?: "document"
    }

    private fun load(uri: Uri, k: OfKind) {
        try {
            val tmp = File(cacheDir, "office/${System.nanoTime()}_${name.replace('/', '_')}").also { it.parentFile?.mkdirs() }
            val ins = if (uri.scheme == "file") FileInputStream(File(uri.path ?: throw IOException("no path"))) else contentResolver.openInputStream(uri) ?: throw IOException("cannot read")
            var total = 0L
            ins.use { i -> tmp.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); total += n; if (total > 400L * 1024 * 1024) throw IOException("file too large") }
            } }
            val canEdit = total > 0 // every supported format opens editable; originals that cannot be rewritten go through Save as
            val d = OfDoc(tmp, name, canEdit,
                { w, done -> if (convExt != null) pendingSave = w to done else persist(false, w, done) }, { w, done -> persist(true, w, done) }, { requestClose() })
            runOnUiThread {
                doc = d
                if (intent.getBooleanExtra("recent", false)) OfficePrefs.addRecent(this, uri.toString(), name, k)
            }
        } catch (e: Throwable) { runOnUiThread { err = e.message ?: e.javaClass.simpleName } }
    }

    private fun fileOps(d: OfDoc, k: OfKind) = OfFileOps(d.name, { d.dirty }, { share(d) }, { pickOther.launch(arrayOf("*/*")) },
        { try { ofOpen(this, ofCreate(k, "${k.app} ${tr("document", "belge")}")) } catch (e: Exception) { Toast.makeText(this, "Could not create: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() } },
        if (k == OfKind.PDF) ({ print(d) }) else null, { info(d, k) }, { trig -> saveAsTrigger = trig },
        saveTxt = if (k == OfKind.WORD && !name.endsWith(".txt", true)) ({ txtAsk = true }) else null)

    /** writes the screen's plain text to [f] on a worker thread; the result is a toast */
    private fun saveTxt(f: File) {
        val text = doc?.txt?.invoke() ?: return
        Thread {
            val msg = try { f.writeText(text); tr("Saved as TXT: ${f.name}", "TXT olarak kaydedildi: ${f.name}") }
                catch (e: Throwable) { "Save failed: ${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
        }.start()
    }

    private fun share(d: OfDoc) {
        try {
            val ext = name.substringAfterLast('.', "").lowercase()
            val mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
            // shared storage files go straight through FileProvider; anything else is a copy in the provider's cache folder
            val uri = srcFile?.takeIf { it.exists() }?.let { runCatching { androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", it) }.getOrNull() }
                ?: androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", File(cacheDir, "installer/$name").also { it.parentFile?.mkdirs(); d.file.copyTo(it, true) })
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
        } catch (e: Exception) { Toast.makeText(this, "Could not share: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
    }

    private fun print(d: OfDoc) {
        val pm = getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
        pm.print(name, object : android.print.PrintDocumentAdapter() {
            override fun onLayout(o: android.print.PrintAttributes?, n: android.print.PrintAttributes?, c: android.os.CancellationSignal?, cb: LayoutResultCallback, x: Bundle?) {
                if (c?.isCanceled == true) cb.onLayoutCancelled()
                else cb.onLayoutFinished(android.print.PrintDocumentInfo.Builder(name).setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(android.print.PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
            }
            override fun onWrite(pages: Array<out android.print.PageRange>?, dest: android.os.ParcelFileDescriptor, c: android.os.CancellationSignal?, cb: WriteResultCallback) {
                try { FileInputStream(d.file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }; cb.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES)) }
                catch (e: Exception) { cb.onWriteFailed(e.message) }
            }
        }, null)
    }

    private fun info(d: OfDoc, k: OfKind): List<Pair<String, String>> {
        val src = srcFile
        val size = (src?.takeIf { it.exists() } ?: d.file).length()
        val l = mutableListOf(tr("Name", "Ad") to name, tr("Type", "Tür") to "${k.app} (.${name.substringAfterLast('.', "").lowercase()})",
            tr("Size", "Boyut") to android.text.format.Formatter.formatShortFileSize(this, size),
            tr("Location", "Konum") to (src?.parent ?: srcUri.toString()),
            tr("Modified", "Değişiklik") to java.text.DateFormat.getDateTimeInstance().format(java.util.Date((src?.takeIf { it.exists() } ?: d.file).lastModified())))
        if (k == OfKind.PDF) runCatching {
            val pfd = android.os.ParcelFileDescriptor.open(d.file, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            val r = android.graphics.pdf.PdfRenderer(pfd)
            try { l.add(tr("Pages", "Sayfa") to r.pageCount.toString()) } finally { r.close(); pfd.close() }
        }
        return l
    }

    private fun requestClose() { if (doc?.dirty == true) confirm = true else finish() }

    /** [writer] writes the new file into a temp file; it then replaces the original (or becomes a "(copy)" in the Office folder). */
    private fun persist(copy: Boolean, writer: (File) -> Unit, done: (String) -> Unit) {
        Thread {
            val msg = try {
                val tmp = File(cacheDir, "office/save_${System.nanoTime()}.tmp").also { it.parentFile?.mkdirs() }
                writer(tmp)
                fun asCopy(): String {
                    val dir = OfficePrefs.folder.apply { mkdirs() }
                    val base = name.substringBeforeLast('.'); val ext = convExt ?: name.substringAfterLast('.', "")
                    saveAsDest?.let { tmp.copyTo(it, true); return it.name }
                    var f = File(dir, "$base (copy).$ext"); var n = 2
                    while (f.exists()) f = File(dir, "$base (copy $n).$ext").also { n++ }
                    tmp.copyTo(f, true); return f.name
                }
                var ok = false
                // plain Save writes the same path; a failure throws (shown as "Save failed") and never turns into a "(copy)" file
                if (!copy) { SaveTo.overwrite(tmp, srcFile, srcUri, contentResolver); ok = true }
                val r = if (ok) tr("Saved", "Kaydedildi") else asCopy().let { tr("Saved copy: $it", "Kopya kaydedildi: $it") }
                tmp.delete(); r
            } catch (e: Throwable) { "Save failed: ${e.message ?: e.javaClass.simpleName}" }
            runOnUiThread {
                done(msg)
                val dest = saveAsDest; saveAsDest = null
                // Save as: the document continues in the new file
                if (dest != null && dest.exists() && !msg.startsWith("Save failed")) { ofOpen(this, dest); finish() }
            }
        }.start()
    }

    @Composable
    private fun Screen() {
        LaunchedEffect(Unit) { delay(800); splashDone = true }
        val k = kind ?: OfKind.WORD
        val t = ofTheme(k)
        val d = doc; val e = err
        BackHandler(true) { requestClose() }
        // the window is see-through (Theme.Pulse.Office) so the 365 / 2017 splash floats over wherever the file was opened from: everything after it paints its own canvas
        // the Microsoft 365 window draws its icons as Fluent glyphs, the other Office looks keep their own
        CompositionLocalProvider(LocalFluentIcons provides (t.base == "365")) {
            if (!splashDone || (d == null && e == null)) OfSplash(t, name)
            else Box(Modifier.fillMaxSize().background(t.pal.canvas)) {
                when {
                    e != null -> OfChromeBasic(t, name, emptyList(), "", { finish() }) { OfErrorCard(t, e) }
                    d != null -> CompositionLocalProvider(LocalOfFile provides remember(d, k) { fileOps(d, k) }) { when (k) {
                        OfKind.WORD -> OfWordScreen(d, t); OfKind.EXCEL -> OfExcelScreen(d, t); OfKind.PPT -> OfPptScreen(d, t); OfKind.PDF -> OfPdfScreen(d, t)
                    } }
                }
            }
        }
        // system status / navigation bars wear the chrome colours of the current look (Window APIs; edge-to-edge devices also get the drawn strips)
        val (barTop, barBot) = ofBars(t)
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopStart).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(barTop))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(barBot))
        }
        val sbView = androidx.compose.ui.platform.LocalView.current
        SideEffect {
            val win = (sbView.context as android.app.Activity).window
            win.statusBarColor = barTop.toArgb(); win.navigationBarColor = barBot.toArgb()
            if (android.os.Build.VERSION.SDK_INT >= 29) { win.isStatusBarContrastEnforced = false; win.isNavigationBarContrastEnforced = false }
            androidx.core.view.WindowCompat.getInsetsController(win, sbView).apply { isAppearanceLightStatusBars = barTop.luminance() > .5f; isAppearanceLightNavigationBars = barBot.luminance() > .5f }
        }
        saveAsTrigger?.let { trig ->
            val ext = name.substringAfterLast('.', "").lowercase()
            OfSaveAsDialog(if (convExt != null) name.substringBeforeLast('.') + "_edited.${convExt}" else name, if (ext == "csv" || ext == "tsv") listOf("csv", "tsv") else listOf(convExt ?: ext.ifEmpty { "docx" }), srcFile?.parentFile, { saveAsTrigger = null }) { f ->
                saveAsTrigger = null; saveAsDest = f; trig()
            }
        }
        pendingSave?.let { (w, done) ->
            OfOldFormatDialog(name, convExt ?: "docx", srcFile?.parentFile,
                onDismiss = { pendingSave = null; done("Save failed (cancelled)") },
                onOverwrite = { f -> pendingSave = null; saveAsDest = f; persist(true, w, done) },
                onPick = { f -> pendingSave = null; saveAsDest = f; persist(true, w, done) },
                onTxt = { pendingSave = null; txtAsk = true })
        }
        if (txtAsk) OfSaveAsDialog(name, listOf("txt"), srcFile?.parentFile, { txtAsk = false }, overwrite = true) { f -> txtAsk = false; saveTxt(f) }
        if (confirm) WinAlert(onDismissRequest = { confirm = false }, title = { Text(tr("Close without saving?", "Kaydetmeden kapatılsın mı?")) },
            confirmButton = { TextButton({ confirm = false; finish() }) { Text(tr("Close", "Kapat")) } },
            dismissButton = { TextButton({ confirm = false }) { Text(tr("Cancel", "İptal")) } })
    }

    // the Look is process-wide and Cfg.load above resets it: hand the calling app its own back
    private var lookSaved: (() -> Unit)? = null
    private fun saveLook() {
        val t = Look.theme; val a = Look.accentIdx; val m = Look.mode; val e = Look.effect; val w = Look.windowTheme; val bd = Look.blurDp; val bt = Look.blurTint; val d = Look.dynamic; val c = Look.corner
        lookSaved = { Look.theme = t; Look.accentIdx = a; Look.mode = m; Look.effect = e; Look.windowTheme = w; Look.blurDp = bd; Look.blurTint = bt; Look.dynamic = d; Look.corner = c }
    }
    override fun onStop() { super.onStop(); if (isFinishing) lookSaved?.invoke() }
    override fun onDestroy() { super.onDestroy(); lookSaved?.invoke(); runCatching { File(cacheDir, "office").listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3600_000L }?.forEach { it.delete() } } }
}

/** One "create new" tile: a white sample page with realistic miniature content and a ribbon with the type on top. */
@Composable
private fun OfExtTile(ext: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Color(when (ext) { "docx" -> 0xFF2B579A; "xlsx" -> 0xFF217346; "pptx" -> 0xFFC43E1C; "pdf" -> 0xFFB30B00; else -> 0xFF8A6A3F })
    val shape = RoundedCornerShape(6.dp)
    Column(modifier.aspectRatio(.74f).shadow(3.dp, shape).clip(shape).background(if (ext == "txt") Color(0xFFFFF8E1) else Color.White).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().height(20.dp).background(c), contentAlignment = Alignment.Center) {
            Text(ext.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val w = size.width; val h = size.height; val px = w * .1f
            val gray = Color(0xFFB8BCC2); val dark = Color(0xFF3B3F45)
            fun bar(x: Float, y: Float, x2: Float, col: Color = gray, th: Float = h * .032f) = drawRoundRect(col, Offset(x, y), Size(x2 - x, th), CornerRadius(th / 2))
            when (ext) {
                "docx" -> {
                    bar(px, h * .08f, w * .68f, dark, h * .06f)
                    bar(px, h * .18f, w * .45f, c.copy(alpha = .6f), h * .03f)
                    for (i in 0..3) bar(px, h * (.28f + i * .06f), if (i == 3) w * .6f else w - px)
                    bar(px, h * .54f, w * .42f, c, h * .042f)
                    for (i in 0..4) bar(px, h * (.63f + i * .06f), if (i == 4) w * .5f else w - px)
                }
                "xlsx" -> {
                    val x0 = w * .06f; val y0 = h * .06f; val rn = w * .1f; val cols = 3; val rows = 8
                    val cw = (w - x0 * 2 - rn) / cols; val rh = (h - y0 * 2) / rows
                    drawRect(Color(0xFFE2F0E8), Offset(x0, y0), Size(w - x0 * 2, rh))
                    drawRect(Color(0xFFF1F2F4), Offset(x0, y0), Size(rn, rh * rows))
                    for (r in 0..rows) drawLine(Color(0xFFD0D4D9), Offset(x0, y0 + r * rh), Offset(w - x0, y0 + r * rh), w * .01f)
                    for (cc in 0..cols) drawLine(Color(0xFFD0D4D9), Offset(x0 + rn + (cc - 0f) * cw, y0), Offset(x0 + rn + cc * cw, y0 + rh * rows), w * .01f)
                    drawLine(Color(0xFFD0D4D9), Offset(x0, y0), Offset(x0, y0 + rh * rows), w * .01f)
                    for (cc in 0 until cols) bar(x0 + rn + cc * cw + cw * .3f, y0 + rh * .38f, x0 + rn + cc * cw + cw * .7f, c, rh * .24f)
                    for (r in 1 until rows) {
                        bar(x0 + rn * .3f, y0 + r * rh + rh * .4f, x0 + rn * .7f, gray.copy(alpha = .7f), rh * .2f)
                        if (r < 6) for (cc in 0 until cols) bar(x0 + rn + cc * cw + cw * .15f, y0 + r * rh + rh * .4f, x0 + rn + cc * cw + cw * (if ((r + cc) % 2 == 0) .85f else .6f), dark.copy(alpha = .55f), rh * .2f)
                    }
                    drawRect(c, Offset(x0 + rn + cw, y0 + 3 * rh), Size(cw, rh), style = Stroke(w * .025f))
                }
                "pptx" -> {
                    val sw = w - px * 2; val sh = sw * 9f / 16f; val sy = h * .1f
                    drawRect(Color(0xFFD9DCE0), Offset(px, sy), Size(sw, sh), style = Stroke(w * .012f))
                    drawRect(c.copy(alpha = .1f), Offset(px, sy), Size(sw, sh))
                    drawRect(c, Offset(px, sy), Size(sw * .06f, sh))
                    bar(px + sw * .14f, sy + sh * .16f, px + sw * .7f, dark, sh * .13f)
                    for (i in 0..2) { drawCircle(c, sw * .015f, Offset(px + sw * .16f, sy + sh * (.46f + i * .17f))); bar(px + sw * .2f, sy + sh * (.43f + i * .17f), px + sw * (.62f - i * .08f), gray, sh * .07f) }
                    drawCircle(c, sw * .1f, Offset(px + sw * .82f, sy + sh * .68f))
                    val ty = sy + sh + h * .07f
                    for (i in 0..1) { drawRect(Color(0xFFD9DCE0), Offset(px, ty + i * h * .2f), Size(sw * .42f, sw * .42f * 9f / 16f), style = Stroke(w * .01f)); bar(px + sw * .5f, ty + i * h * .2f + h * .03f, w - px, gray, h * .026f); bar(px + sw * .5f, ty + i * h * .2f + h * .09f, px + sw * .85f, gray, h * .026f) }
                }
                "pdf" -> {
                    drawRect(c, Offset(0f, 0f), Size(w, h * .03f))
                    bar(px, h * .1f, w * .6f, c, h * .06f)
                    bar(px, h * .2f, w * .4f, gray, h * .028f)
                    for (i in 0..3) bar(px, h * (.3f + i * .055f), w - px)
                    drawRect(Color(0xFFF3D4D1), Offset(px, h * .54f), Size(w * .36f, h * .18f)); drawRect(c.copy(alpha = .7f), Offset(px, h * .66f), Size(w * .36f, h * .06f))
                    for (i in 0..3) bar(w * .54f, h * (.55f + i * .045f), w - px)
                    for (i in 0..2) bar(px, h * (.78f + i * .055f), if (i == 2) w * .5f else w - px)
                }
                else -> {
                    for (i in 0..9) drawLine(Color(0xFFB7CBE3), Offset(0f, h * (.1f + i * .09f)), Offset(w, h * (.1f + i * .09f)), w * .008f)
                    drawLine(Color(0xFFE57373), Offset(w * .16f, 0f), Offset(w * .16f, h), w * .012f)
                    for (i in 0..7) bar(w * .22f, h * (.075f + i * .09f), w * listOf(.88f, .8f, .86f, .55f, .84f, .7f, .85f, .4f)[i], dark.copy(alpha = .5f), h * .026f)
                }
            }
        }
    }
}

/** The Office page of the left menu: compact "new" row, recent documents; settings behind the top-right icon. */
@Composable
fun OfficePage() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { OfficePrefs.load(ctx) }
    var settings by remember { mutableStateOf(false) }
    var browse by remember { mutableStateOf(false) }
    var newKind by remember { mutableStateOf<OfKind?>(null) }
    var nameText by remember { mutableStateOf("") }
    var scan by remember { mutableStateOf(false) }
    BackHandler(settings) { settings = false }
    BackHandler(browse) { browse = false }
    BackHandler(scan) { scan = false }
    if (settings) { OfficeSettingsPage { settings = false }; return }
    if (scan) { DocScanPage { scan = false }; return }
    if (browse) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.size(40.dp).clickable { browse = false }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            CompositionLocalProvider(com.umutk.pulse.features.LocalFilePick provides { f: File -> browse = false; if (f.extension.lowercase() in OF_EXTS) ofOpen(ctx, f) else ofOpenText(ctx, f) }) { com.umutk.pulse.features.FilesPage() }
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.size(40.dp).clickable { settings = true }, contentAlignment = Alignment.Center) { PIcon("settings", T.text, 22.dp) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OfKind.values().forEach { k -> OfExtTile(k.ext, Modifier.weight(1f)) { nameText = ""; newKind = k } }
            OfExtTile("txt", Modifier.weight(1f)) {
                try { ofOpenText(ctx, ofCreateText("txt", tr("Note", "Not"))) }
                catch (x: Exception) { Toast.makeText(ctx, "Could not create: ${x.message ?: x.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PButton(tr("Open file", "Dosya aç"), Modifier.weight(1f), icon = Icons.Outlined.FolderOpen) { browse = true }
            PButton(tr("Scan document", "Belge tara"), Modifier.weight(1f)) { scan = true }
        }
        val recents = OfficePrefs.recent.mapNotNull { e -> e.split('\u0001').takeIf { it.size == 3 } }
        if (recents.isNotEmpty()) PCard {
            Text(tr("Recent", "Son kullanılanlar"), color = T.sub, fontSize = 12.sp)
            recents.forEach { (u, n, kn) ->
                val k = OfKind.values().firstOrNull { it.name == kn } ?: OfKind.WORD
                Row(Modifier.fillMaxWidth().clickable {
                    val uri = Uri.parse(u)
                    if (uri.scheme == "file" && uri.path?.let { File(it).exists() } != true) { OfficePrefs.forget(ctx, u); Toast.makeText(ctx, tr("File not found", "Dosya bulunamadı"), Toast.LENGTH_SHORT).show() }
                    else if (uri.scheme == "file" && ofIsText(File(uri.path!!))) ofOpenText(ctx, File(uri.path!!))
                    else ofOpenUri(ctx, uri)
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OfFileIcon(k, 28.dp, ext = n.substringAfterLast('.', k.ext)); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n, color = T.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (u.startsWith("file://")) Uri.parse(u).path ?: u else u, color = T.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    newKind?.let { k ->
        WinAlert(onDismissRequest = { newKind = null }, title = { Text("${tr("New", "Yeni")} ${k.app}") },
            text = { Column { Text("${OfficePrefs.dir}/", color = T.sub, fontSize = 12.sp); PField(nameText, { nameText = it }, tr("Name", "Ad")) } },
            confirmButton = { TextButton({
                newKind = null
                try { ofOpen(ctx, ofCreate(k, nameText.ifBlank { "${k.app} ${tr("document", "belge")}" })) }
                catch (e: Exception) { Toast.makeText(ctx, "Could not create: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
            }) { Text(tr("Create", "Oluştur")) } },
            dismissButton = { TextButton({ newKind = null }) { Text(tr("Cancel", "İptal")) } })
    }
}

/** Office settings: where new documents are saved, plus the Office looks and dark mode. */
@Composable
private fun OfficeSettingsPage(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 22.dp) }
            Text(tr("Office settings", "Office ayarları"), color = T.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        PCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.FolderOpen, null, tint = T.sub, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(tr("File location", "Dosya konumu"), color = T.sub, fontSize = 12.sp) }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Documents", "Documents/Pulse", "Download").forEach { d -> PChip(OfficePrefs.dir == d, d) { OfficePrefs.setDir(ctx, d) } }
            }
        }
        OfficeSettings()
    }
}
