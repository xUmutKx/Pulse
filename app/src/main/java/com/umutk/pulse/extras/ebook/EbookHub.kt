package com.umutk.pulse.extras.ebook

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.umutk.pulse.PButton
import com.umutk.pulse.PCheck
import com.umutk.pulse.PDialog
import com.umutk.pulse.PField
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.onAccent
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Books: a shelf of the books (covers drawn from the title, nothing stored), a drawer with the shelf's lists and an options page,
 * and the reader. Tap the left or right third to turn the page, the middle to show or hide the bars. The ⋮ menu of the reader
 * opens the options as a list, like the classic readers; each option has its own page.
 */

/** [deco]: the picture behind the text, 0 none, 1 night sky with stars, 2 sakura, 3 sakura at night. */
private class Theme(val bg: Color, val ink: Color, val sub: Color, val deco: Int = 0)

private val READER_THEMES = listOf(
    Theme(Color(0xFFF5F2EA), Color(0xFF1E1E1E), Color(0xFF6B6B6B)),   // day
    Theme(Color(0xFFFFFFFF), Color(0xFF111111), Color(0xFF777777)),   // pure white
    Theme(Color(0xFFF3E6C8), Color(0xFF3B2F1E), Color(0xFF7A6A4E)),   // sand
    Theme(Color(0xFFE9E7E2), Color(0xFF2A2A2A), Color(0xFF6A6A6A)),   // paper grey
    Theme(Color(0xFF000000), Color(0xFFD8D8D8), Color(0xFF8E8E93)),   // night (AMOLED black)
    Theme(Color(0xFF000000), Color(0xFFC8C8C8), Color(0xFF7A7A7A)),   // true black
    Theme(Color(0xFF2B3340), Color(0xFFD6DCE5), Color(0xFF8F9AAA)),   // slate
    Theme(Color(0xFF262626), Color(0xFFCFCFCF), Color(0xFF8A8A8A)),   // charcoal
    Theme(Color(0xFF05070F), Color(0xFFE6E9F2), Color(0xFF9AA3B8), 1),   // space
    Theme(Color(0xFFFBF0F3), Color(0xFF3A2A30), Color(0xFF8C6E78), 2),   // sakura
    Theme(Color(0xFF1E1620), Color(0xFFF0DCE4), Color(0xFFB89AA8), 3),   // sakura night
)
private val THEME_NAMES = listOf(
    tr("Day", "Gündüz"), tr("Pure white", "Saf beyaz"), tr("Sand", "Kum"), tr("Paper grey", "Kâğıt gri"),
    tr("Night", "Gece"), tr("True black", "Tam siyah"), tr("Slate", "Arduvaz"), tr("Charcoal", "Kömür"),
    tr("Space", "Uzay"), tr("Sakura", "Sakura"), tr("Sakura night", "Sakura gece"),
)
/** The day and the night theme of the automatic switch. */
private const val DAY_THEME = 0
private const val NIGHT_THEME = 4

private fun rp(c: Context) = c.getSharedPreferences("ebook_reader", Context.MODE_PRIVATE)

private fun autoIndex(): Int = if (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) in 7..18) DAY_THEME else NIGHT_THEME

/** The shelf: one line per book (uri, title, author, pdf flag) in the order they were added. */
private fun shelfLoad(c: Context): List<Book> = c.getSharedPreferences("ebook_state", Context.MODE_PRIVATE).getString("shelf", "").orEmpty()
    .split('\n').mapNotNull { line ->
        val f = line.split('\t')
        if (f.size < 4) null else Book(f[0], f[1], f[2], f[3] == "1", emptyList())
    }

private fun shelfSave(c: Context, list: List<Book>) {
    c.getSharedPreferences("ebook_state", Context.MODE_PRIVATE).edit()
        .putString("shelf", list.joinToString("\n") { listOf(it.uri, it.title, it.author, if (it.pdf) "1" else "0").joinToString("\t") }).apply()
}

private fun coverColor(title: String): Color {
    val hue = (title.hashCode() and 0x7fffffff) % 360
    return Color.hsv(hue.toFloat(), .45f, .55f)
}

/** Lays the paragraphs out in pages of the given size (pixels): a paragraph is cut between two lines when a page is full. */
fun paginate(measurer: androidx.compose.ui.text.TextMeasurer, paras: List<Para>, w: Int, h: Int, body: TextStyle, head: TextStyle, gap: Float): List<List<Para>> {
    val pages = ArrayList<List<Para>>()
    var cur = ArrayList<Para>()
    var used = 0f
    for (p in paras) {
        var rest = p.text
        val style = if (p.heading) head else body
        while (rest.isNotEmpty()) {
            val layout = measurer.measure(AnnotatedString(rest), style = style, constraints = Constraints(maxWidth = w))
            val left = h - used
            if (layout.size.height <= left) {
                cur.add(Para(rest, p.heading)); used += layout.size.height + gap; rest = ""
                continue
            }
            var k = 0
            while (k < layout.lineCount && layout.getLineBottom(k) <= left) k++
            if (k == 0) {
                if (cur.isEmpty()) k = 1 else { pages += cur; cur = ArrayList(); used = 0f; continue }
            }
            val end = layout.getLineEnd(k - 1, visibleEnd = true)
            cur.add(Para(rest.substring(0, end).trim(), p.heading))
            rest = rest.substring(end).trimStart()
            pages += cur; cur = ArrayList(); used = 0f
        }
    }
    if (cur.isNotEmpty()) pages += cur
    return pages
}

@Composable
fun EbookHub(onBack: () -> Unit) {
    var open by remember { mutableStateOf<Book?>(null) }
    if (open != null) {
        ReaderScreen(open!!) { open = null }
    } else {
        Shelf(onBack = onBack, onOpen = { open = it })
    }
}

/** The colours of the shelf's own pages: the look of Pulse. */
private fun shelfTheme(day: Boolean, amoled: Boolean) = when {
    day -> Theme(Color(0xFFF5F2EA), Color(0xFF1E1E1E), Color(0xFF6B6B6B))
    amoled -> Theme(Color.Black, T.text, T.sub)
    else -> Theme(T.panel, T.text, T.sub)
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Shelf(onBack: () -> Unit, onOpen: (Book) -> Unit) {
    val ctx = LocalContext.current
    var shelf by remember { mutableStateOf(shelfLoad(ctx)) }
    var note by remember { mutableStateOf("") }
    // books whose file was deleted outside Pulse leave the shelf
    LaunchedEffect(Unit) {
        val gone = withContext(Dispatchers.IO) { shelf.filter { !bookExists(ctx, it) } }
        if (gone.isNotEmpty()) {
            shelf = shelf.filter { b -> gone.none { it.uri == b.uri } }.also { shelfSave(ctx, it) }
            note = tr("Books deleted outside Pulse were removed from the shelf", "Pulse dışında silinen kitaplar raftan kaldırıldı")
        }
    }
    // "" the shelf, "options" the options page
    var screen by remember { mutableStateOf("") }
    var drawer by remember { mutableStateOf(false) }
    // a long press on a book asks whether it leaves the shelf (the file itself stays on the phone)
    var askRemove by remember { mutableStateOf<Book?>(null) }
    // a long press on a book: its favourite and removal (favourites are set here, on the shelf)
    var askPick by remember { mutableStateOf<Book?>(null) }
    var about by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showProgress by remember { mutableStateOf(EbookPrefs.showProgress(ctx)) }
    var cover by remember { mutableStateOf(EbookPrefs.cover(ctx)) }
    var wood by remember { mutableStateOf(EbookPrefs.wood(ctx)) }
    var perRow by remember { mutableStateOf(EbookPrefs.perRow(ctx)) }
    var amoled by remember { mutableStateOf(EbookPrefs.amoled(ctx)) }
    var animStyle by remember { mutableStateOf(EbookPrefs.animStyle(ctx)) }
    var eink by remember { mutableStateOf(EbookPrefs.eink(ctx)) }
    var dayNight by remember { mutableStateOf(rp(ctx).getInt("theme", DAY_THEME)) }
    // the lists of the drawer: the whole shelf, the books opened lately, the favourites
    var tab by remember { mutableStateOf(0) }
    val listed = when (tab) {
        1 -> shelf.filter { Ebook.opened(ctx, it.uri) > 0L }.sortedByDescending { Ebook.opened(ctx, it.uri) }
        2 -> shelf.filter { Ebook.isFavorite(ctx, it.uri) }
        else -> shelf
    }.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    val title = when (tab) { 1 -> tr("Recent", "Son okunanlar"); 2 -> tr("Favorites", "Favoriler"); else -> tr("My shelf", "Rafım") }
    BackHandler {
        when {
            drawer -> drawer = false
            screen.isNotEmpty() -> screen = ""
            searching -> { searching = false; query = "" }
            else -> onBack()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val book = Ebook.open(ctx, uri)
        if (book == null) note = tr("This file cannot be read", "Bu dosya okunamıyor")
        else {
            shelf = (shelf.filter { it.uri != book.uri } + book.copy(chapters = emptyList())).also { shelfSave(ctx, it) }
            note = ""
        }
    }
    val th = shelfTheme(dayNight == DAY_THEME, amoled)
    Box(Modifier.fillMaxSize()) {
        // AMOLED is a pure black shelf; otherwise the wooden background, or the look's panel colour
        val shelfWood = wood && !amoled
        if (shelfWood) WoodBackground(Modifier.matchParentSize())
        Column(Modifier.fillMaxSize().then(if (shelfWood) Modifier else Modifier.background(th.bg)).padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (screen.isEmpty()) {
                    Box(Modifier.size(40.dp).clickable { drawer = true }, contentAlignment = Alignment.Center) { PIcon("menu", th.ink, 24.dp) }
                } else {
                    Box(Modifier.size(40.dp).clickable { screen = "" }, contentAlignment = Alignment.Center) { PIcon("back", th.ink, 24.dp) }
                }
                Text(if (screen == "options") tr("Options", "Seçenekler") else if (screen == "find") tr("Find free books", "Ücretsiz kitap bul") else title, color = th.ink, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                if (screen.isEmpty()) {
                    Box(Modifier.size(40.dp).clickable { searching = !searching; if (!searching) query = "" }, contentAlignment = Alignment.Center) { PIcon("search", th.ink, 22.dp) }
                    Box(Modifier.size(40.dp).clickable { pick.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf")) }, contentAlignment = Alignment.Center) { PIcon("add", th.ink, 24.dp) }
                }
            }
            if (note.isNotEmpty()) Text(note, color = th.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))

            if (screen == "find") FindPage(th) { book ->
                shelf = (shelf.filter { it.uri != book.uri } + book.copy(chapters = emptyList())).also { shelfSave(ctx, it) }
                screen = ""; tab = 0
            }
            else if (screen == "options") {
                LazyColumn(Modifier.weight(1f)) {
                    item { SectionTitle(tr("My Shelf", "Rafım"), th) }
                    item { SettingRow(tr("Use woody background", "Ahşap arka plan"), th) { PSwitch(wood, { wood = it; EbookPrefs.setWood(ctx, it) }) } }
                    item {
                        val label = if (perRow == 0) tr("auto", "otomatik") else "$perRow"
                        SettingRow(tr("Books in a row: $label", "Satırda kitap: $label"), th) {
                            StepButton("−", th) { perRow = if (perRow <= 2) 0 else perRow - 1; EbookPrefs.setPerRow(ctx, perRow) }
                            StepButton("+", th) { perRow = if (perRow == 0) 2 else (perRow + 1).coerceAtMost(6); EbookPrefs.setPerRow(ctx, perRow) }
                        }
                    }
                    item { SettingRow(tr("Show reading progress on book cover", "Kapakta okuma ilerlemesi"), th) { PSwitch(showProgress, { showProgress = it; EbookPrefs.setShowProgress(ctx, it) }) } }
                    item { SettingRow(tr("Main folder", "Ana klasör"), th) { Text("/sdcard/Books", color = th.sub, fontSize = 13.sp) } }
                    item {
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PButton(tr("Import books", "Kitapları içe aktar"), compact = true) {
                                // the books of the Books and Download folders join the shelf; nothing is removed
                                val found = scanBooks().filter { f -> shelf.none { it.uri == f.uri } }
                                shelf = (shelf + found).also { shelfSave(ctx, it) }
                                note = if (found.isEmpty()) tr("No new books in Books or Download", "Books ve Download'da yeni kitap yok") else ""
                            }
                            PButton(tr("Empty shelf", "Rafı boşalt"), compact = true) { shelf = emptyList(); shelfSave(ctx, emptyList()); note = "" }
                        }
                    }
                    item { SectionTitle(tr("Color", "Renk"), th) }
                    item { SettingRow(tr("Use AMOLED dark mode", "AMOLED koyu mod"), th) { PSwitch(amoled, { amoled = it; EbookPrefs.setAmoled(ctx, it) }) } }
                    item { SectionTitle(tr("App", "Uygulama"), th) }
                    item { ReaderRow(tr("Page animation", "Sayfa animasyonu"), th, animName(animStyle)) { animStyle = (animStyle + 1) % 4; EbookPrefs.setAnimStyle(ctx, animStyle) } }
                    item { SettingRow(tr("E-ink screen mode", "E-mürekkep ekran"), th) { PSwitch(eink, { eink = it; EbookPrefs.setEink(ctx, it) }) } }
                }
            } else {
                if (searching) PField(query, { query = it }, tr("Search the shelf", "Rafta ara"), Modifier.fillMaxWidth().padding(top = 6.dp))
                if (shelf.isEmpty()) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr("No books yet", "Henüz kitap yok"), color = th.sub, fontSize = 14.sp)
                        Spacer(Modifier.height(12.dp))
                        PButton(tr("Find books on this phone", "Telefondaki kitapları bul"), compact = true) {
                            // the folders books usually live in: Books and Download on the phone's storage
                            val found = scanBooks()
                            shelf = found.also { shelfSave(ctx, it) }
                            note = if (found.isEmpty()) tr("No EPUB, TXT or PDF in Books or Download", "Books ve Download klasöründe EPUB, TXT veya PDF yok") else ""
                        }
                        Spacer(Modifier.height(8.dp))
                        PButton(tr("Add a book", "Kitap ekle"), compact = true) { pick.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf")) }
                    }
                } else if (listed.isEmpty()) {
                    Text(tr("Nothing here", "Burada boş"), color = th.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp))
                } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    // two or three books in a row; the covers grow to fill the width
                    // as many covers as the width takes (a landscape screen gets more), unless a number is chosen
                    val cols = if (perRow > 0) perRow else (maxWidth / 130.dp).toInt().coerceAtLeast(2)
                    val cw = ((maxWidth - 10.dp * (cols - 1)) / cols).coerceAtMost(200.dp)
                    LazyVerticalGrid(columns = GridCells.Fixed(cols), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                        items(listed) { b ->
                            Column(Modifier.combinedClickable(onClick = { onOpen(b) }, onLongClick = { askPick = b }), horizontalAlignment = Alignment.CenterHorizontally) {
                                // the cover is the first page of a PDF or the cover picture of an EPUB; a book with neither is a card with its first letter
                                val pic by produceState<ImageBitmap?>(null, b.uri) {
                                    value = withContext(Dispatchers.IO) { Ebook.cover(ctx, b)?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
                                }
                                Box(Modifier.size(cw, cw * 1.4f).clip(RoundedCornerShape(6.dp)).background(coverColor(b.title)), contentAlignment = Alignment.Center) {
                                    val shown = pic
                                    // the whole cover is shown (no cropping into a zoomed part); the card colour fills the rest
                                    if (shown != null) Image(shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                                    else Text(b.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 5, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(8.dp))
                                    if (showProgress) Text("${Ebook.pct(ctx, b.uri)}%", color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp))
                                }
                                Text(b.title, color = if (shelfWood) Color(0xFFF6E7CC) else th.ink, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(cw).padding(top = 4.dp))
                            }
                        }
                    }
                }
            }
        }
        if (drawer) ShelfDrawer(
            tab = tab,
            amoled = amoled,
            dayNight = dayNight,
            onTab = { tab = it; screen = ""; drawer = false },
            onFiles = { drawer = false; pick.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf")) },
            onFind = { drawer = false; screen = "find" },
            onOptions = { drawer = false; screen = "options" },
            onAbout = { drawer = false; about = true },
            onDayNight = {
                dayNight = if (dayNight == NIGHT_THEME) DAY_THEME else NIGHT_THEME
                rp(ctx).edit().putInt("theme", dayNight).apply()
            },
            onExit = onBack,
            onClose = { drawer = false },
        )
        askPick?.let { bk ->
            com.umutk.pulse.extras.video.PickDialog(bk.title, listOf(tr("Favorite", "Favori") to Ebook.isFavorite(ctx, bk.uri), tr("Remove from shelf", "Raftan kaldır") to false), { askPick = null }) { i ->
                if (i == 0) Ebook.toggleFavorite(ctx, bk.uri) else askRemove = bk
                askPick = null
            }
        }
        askRemove?.let { bk ->
            PDialog(
                title = bk.title,
                text = tr("Remove this book from the shelf? The file stays on the phone.", "Bu kitap raftan kaldırılsın mı? Dosya telefonda kalır."),
                confirm = tr("Remove", "Kaldır"),
                onConfirm = { shelf = shelf.filter { it.uri != bk.uri }.also { shelfSave(ctx, it) }; askRemove = null },
                onDismiss = { askRemove = null },
            )
        }
        if (about) PDialog(
            title = tr("Books", "Kitaplar"),
            text = tr("Your EPUB, TXT and PDF books, read on this phone. Nothing is uploaded.", "EPUB, TXT ve PDF kitaplarınız, bu telefonda okunur. Hiçbir şey yüklenmez."),
            confirm = tr("OK", "Tamam"),
            onConfirm = { about = false },
            onDismiss = { about = false },
            info = true,
        )
    }
}

/** The drawer of the shelf: the lists, the options, the day or night theme and the exit. */
@Composable
private fun ShelfDrawer(
    tab: Int, dayNight: Int, amoled: Boolean,
    onTab: (Int) -> Unit, onFiles: () -> Unit, onFind: () -> Unit, onOptions: () -> Unit, onAbout: () -> Unit,
    onDayNight: () -> Unit, onExit: () -> Unit, onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).clickable { onClose() })
    val th = shelfTheme(dayNight == DAY_THEME, amoled)
    Column(Modifier.fillMaxHeight().width(280.dp).background(th.bg)) {
        Text(tr("Book reader", "Kitap okuyucu"), color = th.ink, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp))
        DrawerRow(th, "sort", tr("Recent list", "Son okunanlar"), tab == 1, false) { onTab(1) }
        DrawerRow(th, "book", tr("My Shelf", "Rafım"), tab == 0, false) { onTab(0) }
        DrawerRow(th, "heart", tr("My Favorites", "Favorilerim"), tab == 2, false) { onTab(2) }
        DrawerRow(th, "add", tr("My Files", "Dosyalarım"), false, false, onFiles)
        DrawerRow(th, "search", tr("Find free books", "Ücretsiz kitap bul"), false, false, onFind)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(th.sub.copy(alpha = .3f)))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            DrawerBottom(th, "settings", tr("Options", "Seçenekler"), onOptions)
            DrawerBottom(th, "info", tr("About", "Hakkında"), onAbout)
            DrawerBottom(th, "palette", if (dayNight == NIGHT_THEME) tr("Night Theme", "Gece teması") else tr("Day Theme", "Gündüz teması"), onDayNight)
            DrawerBottom(th, "power", tr("Exit", "Çıkış"), onExit)
        }
    }
}

@Composable
private fun DrawerRow(th: Theme, icon: String?, label: String, on: Boolean, indent: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(start = if (indent) 60.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) PIcon(icon, if (on) T.accent else th.ink, 22.dp)
        Text(label, color = if (on) T.accent else th.ink, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = if (icon != null) 16.dp else 0.dp))
    }
}

@Composable
private fun DrawerBottom(th: Theme, icon: String, label: String, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        PIcon(icon, th.sub, 22.dp)
        Text(label, color = th.sub, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

/** Whether a book's file is still there: it may have been deleted outside Pulse. */
private fun bookExists(c: Context, b: Book): Boolean = runCatching {
    val u = Uri.parse(b.uri)
    if (u.scheme == "file") File(u.path.orEmpty()).exists()
    else c.contentResolver.openInputStream(u)?.use { true } ?: false
}.getOrDefault(false)

/** The books found in the phone's Books and Download folders (by name; a book is read when it is opened). */
private fun scanBooks(): List<Book> {
    val root = android.os.Environment.getExternalStorageDirectory()
    return listOf(File(root, "Books"), File(root, "Download")).flatMap { dir ->
        dir.listFiles()?.filter { it.isFile && Ebook.isBook(it.name) }?.sortedBy { it.name.lowercase() }?.map { f ->
            Book(Uri.fromFile(f).toString(), f.name.substringBeforeLast('.'), "", f.name.endsWith(".pdf", true), emptyList())
        }.orEmpty()
    }
}

/** The panels of the reader's ⋮ menu that open as a full page; each goes back to the menu. */

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReaderScreen(start: Book, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val uri = Uri.parse(start.uri)
    var book by remember { mutableStateOf<Book?>(null) }
    LaunchedEffect(start.uri) {
        val opened = withContext(Dispatchers.IO) { runCatching { Ebook.open(ctx, uri) }.getOrNull() }
        if (opened == null) {
            // the file is gone (deleted outside Pulse): the book leaves the shelf and the reader closes
            shelfSave(ctx, shelfLoad(ctx).filter { it.uri != start.uri })
            onClose()
        } else { book = opened; Ebook.touch(ctx, start.uri) }
    }
    val b = book

    var amoled by remember { mutableStateOf(EbookPrefs.amoled(ctx)) }
    var autoTheme by remember { mutableStateOf(EbookPrefs.autoTheme(ctx)) }
    var themeIx by remember { mutableStateOf(if (EbookPrefs.autoTheme(ctx)) autoIndex() else rp(ctx).getInt("theme", DAY_THEME)) }
    var textSize by remember { mutableStateOf(rp(ctx).getInt("size", 15)) }
    var eink by remember { mutableStateOf(EbookPrefs.eink(ctx)) }
    var animStyle by remember { mutableStateOf(EbookPrefs.animStyle(ctx)) }
    var dbl by remember { mutableStateOf(EbookPrefs.double(ctx)) }
    var fontIx by remember { mutableStateOf(EbookPrefs.font(ctx)) }
    var autoOn by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    val base = READER_THEMES[themeIx]
    val th = if (eink) Theme(Color.White, Color.Black, Color(0xFF555555))
        else if (amoled && base.bg.red + base.bg.green + base.bg.blue < .3f) Theme(Color.Black, base.ink, base.sub, base.deco)
        else base
    val family = when (fontIx) { 1 -> FontFamily.SansSerif; 2 -> FontFamily.Monospace; else -> FontFamily.Serif }
    var chapter by remember { mutableStateOf(Ebook.place(ctx, start.uri).first) }
    var page by remember { mutableStateOf(Ebook.place(ctx, start.uri).second) }
    var forward by remember { mutableStateOf(true) }
    var bars by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    // the chapters page: 0 chapters, 1 bookmarks
    var listTab by remember { mutableStateOf(0) }
    var speechRate by remember { mutableStateOf(EbookPrefs.speechRate(ctx)) }
    var ttsPkg by remember { mutableStateOf(EbookPrefs.ttsEngine(ctx)) }
    val ttsList = remember { ttsEngines(ctx) }
    var bookmarked by remember { mutableStateOf(Ebook.bookmarks(ctx, start.uri).contains("$chapter,$page")) }
    var query by remember { mutableStateOf("") }
    var brightness by remember { mutableStateOf(-1f) }
    var barIds by remember { mutableStateOf(EbookPrefs.bar(ctx)) }
    var autoSec by remember { mutableStateOf(EbookPrefs.autoSeconds(ctx)) }
    // the screen orientation of the reader: 0 follows the phone, 1 portrait, 2 landscape
    var orientation by remember { mutableStateOf(rp(ctx).getInt("orient", 0)) }
    LaunchedEffect(orientation) { setOrientation(ctx, orientation) }
    // the page's text, read aloud; a page read to its end turns to the next one
    val curText = remember { arrayOf("") }
    val pageTurner = remember { mutableStateOf<(() -> Unit)?>(null) }
    val speaker = remember { Speaker(ctx) { if (speaking) pageTurner.value?.invoke() } }
    // the words being read, as a range of the page's text: they are highlighted
    var spoken by remember { mutableStateOf(IntRange.EMPTY) }
    var pageCount by remember { mutableStateOf(0) }
    speaker.onRange = { s, e -> spoken = s until e }
    DisposableEffect(Unit) { onDispose { speaker.shutdown(); setBrightness(ctx, -1f); setOrientation(ctx, 0) } }
    BackHandler {
        when {
            // back from any page of the menu (or the menu itself) goes straight to the book
            panel.isNotEmpty() -> panel = ""
            else -> { Ebook.savePlace(ctx, start.uri, chapter, page); onClose() }
        }
    }
    fun save() {
        Ebook.savePlace(ctx, start.uri, chapter, page)
        b?.let { bk -> if (bk.chapters.isNotEmpty()) Ebook.savePct(ctx, start.uri, (chapter + 1) * 100 / bk.chapters.size) }
    }
    fun pickTheme(i: Int) {
        eink = false; EbookPrefs.setEink(ctx, false)
        themeIx = i; rp(ctx).edit().putInt("theme", i).apply()
    }

    // one place for what a bar button (or a row of the ⋮ menu) does
    fun act(id: String) {
        when (id) {
            "orient" -> { orientation = (orientation + 1) % 3; rp(ctx).edit().putInt("orient", orientation).apply() }
            "daynight" -> pickTheme(if (themeIx == NIGHT_THEME) DAY_THEME else NIGHT_THEME)
            "speak" -> speaking = !speaking
            "auto" -> autoOn = !autoOn
            "chapters" -> { listTab = 0; panel = "chapters" }
            "bookmarks" -> { listTab = 1; panel = "chapters" }
            "brightness" -> panel = "control"
            "text" -> panel = "sizepop"
            "search" -> panel = "search"
            "visual" -> panel = "visual"
            "control" -> panel = "control"
            "theme" -> panel = "themepop"
            "options" -> panel = "quick"
            "custom" -> panel = "bar"
            "info" -> panel = "info"
            "share" -> b?.let { bk ->
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, bk.title + if (bk.author.isNotEmpty()) " - " + bk.author else "")
                ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            "eink" -> { eink = !eink; EbookPrefs.setEink(ctx, eink) }
        }
    }

    Box(Modifier.fillMaxSize().background(th.bg)) {
        if (th.deco != 0) ThemeDecor(th.deco, Modifier.fillMaxSize())
        if (b == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr("Opening…", "Açılıyor…"), color = th.sub, fontSize = 14.sp) }
        } else if (b.pdf) {
            PdfPages(ctx, uri, page, th, forward,
                onTurn = { d -> forward = d > 0; page = (page + d).coerceAtLeast(0); save() },
                onTap = { bars = !bars })
        } else {
            val chapters = b.chapters
            if (chapter !in chapters.indices) chapter = 0
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wPx = with(density) { maxWidth.roundToPx() }
                val hPx = with(density) { maxHeight.roundToPx() }
                val bodyStyle = TextStyle(fontFamily = family, fontSize = textSize.sp, lineHeight = (textSize * 1.6f).sp, color = th.ink)
                val headStyle = TextStyle(fontFamily = family, fontSize = (textSize + 4).sp, lineHeight = ((textSize + 4) * 1.4f).sp, color = th.ink, fontWeight = FontWeight.Bold)
                val pages by produceState<List<List<Para>>?>(null, chapter, textSize, fontIx, wPx, hPx, themeIx) {
                    value = null
                    value = withContext(Dispatchers.Default) {
                        paginate(measurer, chapters.getOrNull(chapter)?.paras.orEmpty(), wPx - with(density) { 40.dp.roundToPx() }, hPx - with(density) { 96.dp.roundToPx() }, bodyStyle, headStyle, with(density) { 14.dp.toPx() })
                    }
                }
                val list = pages
                // a page past the end (or -1: the last page of the chapter before) is set once the chapter is laid out
                LaunchedEffect(list) { if (list != null && list.isNotEmpty() && (page < 0 || page >= list.size)) page = list.size - 1 }
                val shown = if (list == null) 0 else page.coerceIn(0, (list.size - 1).coerceAtLeast(0))
                val cur = list?.getOrNull(shown).orEmpty()
                curText[0] = cur.joinToString(" ") { it.text }
                val key = chapter.toLong() * 100000L + shown
                // the next page, or the next chapter; used by the tap zones, the autoscroll and the speech
                fun next() {
                    forward = true
                    if (list != null && page < list.size - 1) page++ else if (chapter < chapters.size - 1) { chapter++; page = 0 }
                    bookmarked = Ebook.bookmarks(ctx, start.uri).contains("$chapter,$page")
                    save()
                }
                fun prev() {
                    forward = false
                    if (page > 0) page-- else if (chapter > 0) { chapter--; page = -1 }
                    bookmarked = Ebook.bookmarks(ctx, start.uri).contains("$chapter,$page")
                    save()
                }
                pageTurner.value = { next() }
                LaunchedEffect(autoOn, key) { if (autoOn) { kotlinx.coroutines.delay(autoSec * 1000L); if (autoOn) next() } }
                LaunchedEffect(speaking, key) { spoken = IntRange.EMPTY; if (speaking) speaker.speak(curText[0]) else speaker.stop() }
                LaunchedEffect(list) { pageCount = list?.size ?: 0 }
                AnimatedContent(
                    targetState = key,
                    transitionSpec = {
                        when (if (eink) 0 else animStyle) {
                            1 -> if (forward) (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(260)))
                                 else (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(260)))
                            // the next page feeds in from the top and the old one slides down out of the screen
                            2 -> (slideInVertically(tween(320)) { -it } + fadeIn(tween(320))) togetherWith (slideOutVertically(tween(320)) { it } + fadeOut(tween(320)))
                            3 -> (scaleIn(tween(220), initialScale = .9f) + fadeIn(tween(220))) togetherWith (scaleOut(tween(220), targetScale = .9f) + fadeOut(tween(220)))
                            else -> (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                        }
                    },
                    label = "page",
                ) { _ ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 44.dp), verticalArrangement = Arrangement.Top) {
                        // each paragraph; the words being read are highlighted
                        var off = 0
                        cur.forEach { p ->
                            val start = off
                            off += p.text.length + 1
                            Text(highlighted(p.text, start, spoken, T.accent.copy(alpha = .35f)), style = if (p.heading) headStyle else bodyStyle, modifier = Modifier.padding(bottom = 14.dp))
                        }
                    }
                }
                // the three zones of the page: the left third goes back, the right third forward, the middle shows the bars
                Box(Modifier.fillMaxSize().pointerInput(chapter, shown, list?.size) {
                    detectTapGestures(onTap = { o ->
                        val w = size.width
                        when {
                            o.x < w / 3f -> prev()
                            o.x > w * 2f / 3f -> next()
                            else -> bars = !bars
                        }
                    })
                })
                Text("${shown + 1}/${(list?.size ?: 1).coerceAtLeast(1)}", color = th.sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
                if (autoOn) Text(tr("Autoscroll", "Otomatik"), color = th.sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
        }

        if (bars && b != null) {
            Row(Modifier.fillMaxWidth().background(th.bg).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { save(); onClose() }, contentAlignment = Alignment.Center) { PIcon("back", th.ink, 22.dp) }
                Text(b.title, color = th.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                Box(Modifier.size(40.dp).clickable { bookmarked = Ebook.toggleBookmark(ctx, start.uri, chapter, page) }, contentAlignment = Alignment.Center) {
                    PIcon("bookmark", if (bookmarked) th.ink else th.sub, 22.dp)
                }
                Box(Modifier.size(40.dp).clickable { panel = "menu" }, contentAlignment = Alignment.Center) { Text("⋮", color = th.ink, fontSize = 22.sp) }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(th.bg).padding(horizontal = 8.dp, vertical = 6.dp)) {
                val count = if (b.pdf) 0 else b.chapters.size
                // the reading slider: the chapters, with the share read in per cent
                if (count > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("‹", color = th.sub, fontSize = 22.sp, modifier = Modifier.clickable { if (chapter > 0) { chapter = chapter - 1; page = 0; forward = false; save() } }.padding(horizontal = 8.dp))
                    // the slider moves through the pages of the chapter
                    val last = (pageCount - 1).coerceAtLeast(1).toFloat()
                    PSlider(page.coerceIn(0, (pageCount - 1).coerceAtLeast(0)).toFloat(), { page = it.roundToInt().coerceIn(0, (pageCount - 1).coerceAtLeast(0)) }, 0f..last,
                        modifier = Modifier.weight(1f), onDone = { save() })
                    Text("${(page + 1).coerceAtMost(pageCount.coerceAtLeast(1))}/${pageCount.coerceAtLeast(1)}", color = th.ink, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
                    Text("›", color = th.sub, fontSize = 22.sp, modifier = Modifier.clickable { if (chapter < count - 1) { chapter = chapter + 1; page = 0; forward = true; save() } }.padding(horizontal = 8.dp))
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, maxItemsInEachRow = if (dbl) (barIds.size + 1) / 2 else Int.MAX_VALUE) {
                    barIds.forEach { id ->
                        val t = BAR_TOOLS.firstOrNull { it.third == id } ?: return@forEach
                        val on = when (id) { "speak" -> speaking; "auto" -> autoOn; else -> false }
                        // holding the speak button opens the phone's speech settings (engine, voice, speed)
                        Box(Modifier.size(44.dp).combinedClickable(onClick = { act(id) }, onLongClick = { if (id == "speak") openTtsSettings(ctx) }), contentAlignment = Alignment.Center) {
                            if (id == "text") Text("Aa", color = if (on) th.bg else th.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            else PIcon(if (id == "speak" && speaking) "pause" else t.first, if (on) th.ink else th.ink.copy(alpha = .8f), 22.dp)
                        }
                    }
                }
                Text(tr("Page ${page + 1} of ${pageCount.coerceAtLeast(1)} · Chapter ${chapter + 1}", "Sayfa ${page + 1} / ${pageCount.coerceAtLeast(1)} · Bölüm ${chapter + 1}"), color = th.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
        }

        // the quick choices open as a small popup above the bar; the full options stay in the menu
        // the text size and the font, above the bar
        if (panel == "sizepop" && b != null) {
            Box(Modifier.fillMaxSize().clickable { panel = "" })
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).fillMaxWidth(.9f).clip(RoundedCornerShape(14.dp)).background(th.bg)
                .border(1.dp, th.sub.copy(alpha = .3f), RoundedCornerShape(14.dp)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("A", color = th.ink, fontSize = 13.sp)
                    PSlider(textSize.toFloat(), { textSize = it.roundToInt(); rp(ctx).edit().putInt("size", textSize).apply() }, 12f..34f,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp), onDone = {})
                    Text("A", color = th.ink, fontSize = 22.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(tr("Serif", "Serif"), tr("Sans", "Sans"), tr("Mono", "Mono")).forEachIndexed { i, name ->
                        Text(name, color = if (fontIx == i) th.ink else th.sub, fontSize = 14.sp, fontFamily = fontOf(i),
                            fontWeight = if (fontIx == i) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clickable { fontIx = i; EbookPrefs.setFont(ctx, i) }.padding(8.dp))
                    }
                }
            }
        }
        // the themes, above the bar: a swatch per theme, then the AMOLED and e-ink switches
        if (panel == "themepop" && b != null) {
            Box(Modifier.fillMaxSize().clickable { panel = "" })
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).fillMaxWidth(.94f).clip(RoundedCornerShape(14.dp)).background(th.bg)
                .border(1.dp, th.sub.copy(alpha = .3f), RoundedCornerShape(14.dp)).padding(14.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    READER_THEMES.forEachIndexed { i, t ->
                        val picked = !eink && themeIx == i
                        Column(Modifier.width(64.dp).clickable { pickTheme(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(56.dp, 40.dp).clip(RoundedCornerShape(8.dp)).background(t.bg)
                                .border(if (picked) 2.dp else 1.dp, if (picked) Color(0xFF3D8BFF) else t.sub.copy(alpha = .4f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center) { Text("Aa", color = t.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
                            Text(THEME_NAMES[i], color = th.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SettingRow(tr("Use AMOLED dark mode", "AMOLED koyu mod"), th) { PSwitch(amoled, { amoled = it; EbookPrefs.setAmoled(ctx, it) }) }
                SettingRow(tr("E-ink look", "E-mürekkep görünüm"), th) { PSwitch(eink, { eink = it; EbookPrefs.setEink(ctx, it) }) }
            }
        }
        if (panel == "quick" && b != null) {
            Box(Modifier.fillMaxSize().clickable { panel = "" })
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp).fillMaxWidth(.72f).clip(RoundedCornerShape(12.dp)).background(th.bg).border(1.dp, th.sub.copy(alpha = .3f), RoundedCornerShape(12.dp)).padding(vertical = 6.dp)) {
                listOf(tr("Serif", "Serif"), tr("Sans", "Sans"), tr("Mono", "Mono")).forEachIndexed { i, name ->
                    PopRow(name, fontIx == i, th, fontOf(i)) { fontIx = i; EbookPrefs.setFont(ctx, i) }
                }
                PopRow(tr("Larger text", "Büyük yazı"), false, th) { textSize = (textSize + 2).coerceAtMost(34); rp(ctx).edit().putInt("size", textSize).apply() }
                PopRow(tr("Smaller text", "Küçük yazı"), false, th) { textSize = (textSize - 2).coerceAtLeast(12); rp(ctx).edit().putInt("size", textSize).apply() }
                THEME_NAMES.forEachIndexed { i, name ->
                    PopRow(name, !eink && themeIx == i, th) { pickTheme(i) }
                }
                PopRow(tr("E-ink", "E-mürekkep"), eink, th) { eink = !eink; EbookPrefs.setEink(ctx, eink) }
            }
        }

        // the ⋮ menu: a list that drops from the top right, like the classic readers
        if (panel == "menu" && b != null) {
            Box(Modifier.fillMaxSize().clickable { panel = "" })
            Column(Modifier.align(Alignment.TopEnd).padding(top = 52.dp, end = 8.dp).width(260.dp).heightIn(max = 560.dp).verticalScroll(rememberScrollState()).clip(RoundedCornerShape(8.dp)).background(th.bg).border(1.dp, th.sub.copy(alpha = .3f), RoundedCornerShape(8.dp)).padding(vertical = 6.dp)) {
                // the buttons that are not on the bottom bar wait here; a button on the bar is not repeated in the menu
                // (Visual Options, Options and Brightness are not in the menu: Brightness is in Control Options; they can still be put on the bar)
                val hidden = BAR_TOOLS.filter { it.third !in barIds && it.third !in setOf("visual", "options", "brightness") }
                hidden.forEach { t -> ReaderRow(t.second, th, icon = t.first) { panel = ""; act(t.third) } }
                if (hidden.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(th.sub.copy(alpha = .3f)))
            }
        }

        // the pages of the menu and the lists: each fills the reader, with its title and a back arrow
        if (panel.isNotEmpty() && panel != "quick" && panel != "menu" && panel != "sizepop" && panel != "themepop" && b != null) {
            val titleOf = when (panel) {
                "visual" -> tr("Visual Options", "Görünüm seçenekleri"); "control" -> tr("Control Options", "Denetim seçenekleri")
                "themes" -> tr("Themes", "Temalar"); "ops" -> tr("More Operations", "Diğer işlemler")
                "chapters" -> tr("Chapters and bookmarks", "Bölümler ve yer imleri"); "search" -> tr("Search", "Ara")
                "bar" -> tr("Customize reader bar buttons", "Okuma çubuğu düğmeleri"); "info" -> tr("Book information", "Kitap bilgisi"); else -> ""
            }
            // the customise page is a popup over the reader (a tap outside closes it); the other pages fill the reader
            val popup = panel == "bar"
            if (popup) Box(Modifier.fillMaxSize().clickable { panel = "" })
            Column(
                (if (popup) Modifier.align(Alignment.Center).fillMaxWidth(.92f).heightIn(max = 600.dp).clip(RoundedCornerShape(14.dp)).background(th.bg)
                    .border(1.dp, th.sub.copy(alpha = .3f), RoundedCornerShape(14.dp))
                else Modifier.fillMaxSize().background(th.bg)).padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clickable { panel = "" }, contentAlignment = Alignment.Center) { PIcon("back", th.ink, 22.dp) }
                    Text(titleOf, color = th.ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                when (panel) {
                    "visual" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SectionTitle(tr("Font", "Yazı tipi"), th)
                        listOf(tr("Serif", "Serif"), tr("Sans", "Sans"), tr("Mono", "Mono")).forEachIndexed { i, name ->
                            PopRow(name, fontIx == i, th, fontOf(i)) { fontIx = i; EbookPrefs.setFont(ctx, i) }
                        }
                        SectionTitle(tr("Text", "Metin"), th)
                        SettingRow(tr("Text size: $textSize", "Yazı boyutu: $textSize"), th) {
                            StepButton("−", th) { textSize = (textSize - 2).coerceAtLeast(12); rp(ctx).edit().putInt("size", textSize).apply() }
                            StepButton("+", th) { textSize = (textSize + 2).coerceAtMost(34); rp(ctx).edit().putInt("size", textSize).apply() }
                        }
                        SettingRow(tr("E-ink look", "E-mürekkep görünüm"), th) { PSwitch(eink, { eink = it; EbookPrefs.setEink(ctx, it) }) }
                        ReaderRow(tr("Page animation", "Sayfa animasyonu"), th, animName(animStyle)) { animStyle = (animStyle + 1) % 4; EbookPrefs.setAnimStyle(ctx, animStyle) }
                    }
                    "control" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        SettingRow(tr("Autoscroll", "Otomatik kaydırma"), th) { PSwitch(autoOn, { autoOn = it }) }
                        SettingRow(tr("Reading speed of the voice: ${"%.2f".format(speechRate)}", "Sesli okuma hızı: ${"%.2f".format(speechRate)}"), th) {
                            StepButton("−", th) { speechRate = (speechRate - .05f).coerceAtLeast(.5f); EbookPrefs.setSpeechRate(ctx, speechRate) }
                            StepButton("+", th) { speechRate = (speechRate + .05f).coerceAtMost(1.6f); EbookPrefs.setSpeechRate(ctx, speechRate) }
                        }
                        ReaderRow(tr("Speech engine", "Konuşma motoru"), th, ttsList.firstOrNull { it.first == ttsPkg }?.second ?: tr("Automatic", "Otomatik")) {
                            // the engines the phone has, one after another; "automatic" is the first step
                            val all = listOf("" to "") + ttsList
                            val next = all[(all.indexOfFirst { it.first == ttsPkg }.coerceAtLeast(0) + 1) % all.size]
                            ttsPkg = next.first; EbookPrefs.setTtsEngine(ctx, next.first)
                        }
                        ReaderRow(tr("Voice and speech engine", "Ses ve konuşma motoru"), th, "›") { openTtsSettings(ctx) }
                        SettingRow(tr("Seconds per page: $autoSec", "Sayfa başına saniye: $autoSec"), th) {
                            StepButton("−", th) { autoSec = (autoSec - 2).coerceAtLeast(3); EbookPrefs.setAutoSeconds(ctx, autoSec) }
                            StepButton("+", th) { autoSec = (autoSec + 2).coerceAtMost(120); EbookPrefs.setAutoSeconds(ctx, autoSec) }
                        }
                        SectionTitle(tr("Brightness", "Parlaklık"), th)
                        PSlider(if (brightness < 0f) .5f else brightness, { brightness = it; setBrightness(ctx, it) }, 0f..1f, onDone = {})
                        SectionTitle(tr("Screen", "Ekran"), th)
                        // the screen in three parts, as the reader uses them
                        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(th.ink.copy(alpha = .06f)).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Text(tr("Back", "Geri"), color = th.ink, fontSize = 13.sp)
                            Text(tr("Bars", "Çubuklar"), color = th.ink, fontSize = 13.sp)
                            Text(tr("Forward", "İleri"), color = th.ink, fontSize = 13.sp)
                        }
                    }
                    "themes" -> LazyColumn(Modifier.fillMaxSize()) {
                        // one card per theme, drawn in its own colours; the e-ink card is the black-on-white look
                        val cards = READER_THEMES.mapIndexed { i, t -> Triple(t, THEME_NAMES[i], i) }
                        itemsIndexed(cards.chunked(2)) { _, pair ->
                            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                pair.forEach { (t, name, i) ->
                                    ThemeCard(t, name, themeIx == i && !eink, Modifier.weight(1f)) { pickTheme(i) }
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        item {
                            ThemeCard(Theme(Color.White, Color.Black, Color(0xFF555555)), tr("E-ink", "E-mürekkep"), eink, Modifier.fillMaxWidth()) { eink = true; EbookPrefs.setEink(ctx, true) }
                        }
                        item { SectionTitle(tr("Night", "Gece"), th) }
                        item {
                            SettingRow(tr("Use AMOLED dark mode", "AMOLED koyu mod"), th) { PSwitch(amoled, { amoled = it; EbookPrefs.setAmoled(ctx, it) }) }
                        }
                        item {
                            SettingRow(tr("Automatic day night switch", "Otomatik gündüz gece"), th) {
                                PSwitch(autoTheme, { autoTheme = it; EbookPrefs.setAutoTheme(ctx, it); if (it) pickTheme(autoIndex()) })
                            }
                        }
                    }
                    "ops" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        ReaderRow(tr("Chapters", "Bölümler"), th, "›") { panel = "chapters" }
                        ReaderRow(tr("Bookmarks", "Yer imleri"), th, "›") { listTab = 1; panel = "chapters" }
                        ReaderRow(tr("Search", "Ara"), th, "›") { panel = "search" }
                    }
                    // chapters and bookmarks in one list, switched by the two tabs on top
                    "chapters" -> Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(tr("Chapters", "Bölümler"), tr("Bookmarks", "Yer imleri")).forEachIndexed { i, name ->
                                Text(name, color = if (listTab == i) th.bg else th.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (listTab == i) th.ink else th.ink.copy(alpha = .06f))
                                        .clickable { listTab = i }.padding(vertical = 10.dp), textAlign = TextAlign.Center)
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            if (listTab == 0) ReaderList(b.chapters.mapIndexed { i, c -> "${i + 1}. ${c.title}" }, th) { i -> chapter = i; page = 0; forward = true; panel = ""; bars = false; save() }
                            else {
                                val marks = Ebook.bookmarks(ctx, start.uri)
                                if (marks.isEmpty()) Text(tr("No bookmarks", "Yer imi yok"), color = th.sub, fontSize = 13.sp)
                                else ReaderList(marks.map { m -> val c = m.substringBefore(',').toInt(); "${c + 1}. ${b.chapters.getOrNull(c)?.title.orEmpty()}" }, th) { i ->
                                    val m = marks[i].split(','); chapter = m[0].toInt(); page = m[1].toInt(); forward = true; panel = ""; bars = false; save()
                                }
                            }
                        }
                    }
                    "search" -> {
                        OutlinedField(query, th, tr("Word or phrase", "Kelime veya cümle")) { query = it }
                        val hits = remember(query) { searchBook(b.chapters, query) }
                        if (query.isNotBlank() && hits.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = th.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        ReaderList(hits.map { "${it.first + 1}. ${it.second}" }, th) { i -> chapter = hits[i].first; page = 0; forward = true; panel = ""; bars = false; save() }
                    }
                    "bar" -> LazyColumn(Modifier.fillMaxSize()) {
                        item {
                            // the bar as it is now: the chosen buttons in their order
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(10.dp)).background(th.ink.copy(alpha = .06f))
                                .horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                barIds.forEach { id ->
                                    val t = BAR_TOOLS.firstOrNull { it.third == id } ?: return@forEach
                                    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                        if (id == "text") Text("Aa", color = th.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                        else PIcon(t.first, th.ink, 22.dp)
                                    }
                                }
                            }
                        }
                        item {
                            SettingRow(tr("Double-line layout", "Çift satır düzeni"), th) { PSwitch(dbl, { dbl = it; EbookPrefs.setDouble(ctx, it) }) }
                        }
                        itemsIndexed(BAR_TOOLS) { _, t ->
                            val on = t.third in barIds
                            // the whole row ticks the button (a tap on the name must not reach the reader under the panel)
                            // the customise button always stays on, last in the bar (EbookPrefs.pinned)
                            val toggle = { if (t.third != "custom") { barIds = EbookPrefs.pinned(if (on) barIds - t.third else barIds + t.third); EbookPrefs.setBar(ctx, barIds) } }
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                // the box ticks the button for the bar; a tap on the name opens that button's panel and closes the popup
                                PCheck(on, { toggle() })
                                Row(Modifier.weight(1f).clickable { if (t.third != "custom") { panel = ""; act(t.third) } }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.padding(start = 10.dp).size(20.dp), contentAlignment = Alignment.Center) {
                                        if (t.third == "text") Text("Aa", color = th.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        else PIcon(t.first, th.ink, 20.dp)
                                    }
                                    Text(t.second, color = th.ink, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                }
                                if (on && t.third != "custom") {
                                    val at = barIds.indexOf(t.third)
                                    StepButton("▲", th) { if (at > 0) { barIds = EbookPrefs.pinned(barIds.toMutableList().also { it.add(at - 1, it.removeAt(at)) }); EbookPrefs.setBar(ctx, barIds) } }
                                    StepButton("▼", th) { if (at < barIds.size - 2) { barIds = EbookPrefs.pinned(barIds.toMutableList().also { it.add(at + 1, it.removeAt(at)) }); EbookPrefs.setBar(ctx, barIds) } }
                                }
                            }
                        }
                    }
                    "info" -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.size(84.dp, 118.dp).clip(RoundedCornerShape(6.dp)).background(coverColor(b.title)), contentAlignment = Alignment.Center) {
                                Text(b.title.take(1).uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(b.title, color = th.ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                if (b.author.isNotEmpty()) Text(b.author, color = th.sub, fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        InfoLine(tr("Format", "Biçim"), if (b.pdf) "PDF" else if (start.uri.endsWith(".txt", true)) "TXT" else "EPUB", th)
                        InfoLine(tr("Chapters", "Bölümler"), if (b.pdf) "-" else "${b.chapters.size}", th)
                        InfoLine(tr("Current chapter", "Şu anki bölüm"), b.chapters.getOrNull(chapter)?.title ?: "-", th)
                        InfoLine(tr("Progress", "İlerleme"), "${Ebook.pct(ctx, start.uri)}%", th)
                        InfoLine(tr("Bookmarks", "Yer imleri"), "${Ebook.bookmarks(ctx, start.uri).size}", th)
                        // share lives with the book's information, not in the ⋮ menu
                        ReaderRow(tr("Share…", "Paylaş…"), th, icon = "share") {
                            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, b.title + if (b.author.isNotEmpty()) " - " + b.author else "")
                            ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

/** Sets the orientation of the reader's window: 0 the phone decides, 1 portrait, 2 landscape. */
private fun setOrientation(c: Context, o: Int) {
    var ctx: Context? = c
    while (ctx != null && ctx !is android.app.Activity) ctx = (ctx as? android.content.ContextWrapper)?.baseContext
    (ctx as? android.app.Activity)?.requestedOrientation = when (o) {
        1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

/** One line of the reader's menu, with an optional mark at its end. */
@Composable
/** The phone's speech settings (the engine, its voices and speed): no root needed, the voices are installed there. */
private fun openTtsSettings(c: Context) {
    runCatching { c.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { c.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** The name of a page turn, as the reader's row shows it. */
private fun animName(style: Int): String = when (style) {
    1 -> tr("Slide", "Kayma")
    2 -> tr("Scroll down", "Aşağı kayma")
    3 -> tr("Flip", "Çevirme")
    else -> tr("Instant", "Anında")
}

private fun ReaderRow(label: String, th: Theme, mark: String = "", icon: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon == "text") Text("Aa", color = th.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(20.dp).padding(end = 14.dp))
        else if (icon.isNotEmpty()) PIcon(icon, th.ink, 20.dp, Modifier.padding(end = 14.dp))
        Text(label, color = th.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (mark.isNotEmpty()) Text(mark, color = th.sub, fontSize = 18.sp)
    }
}

@Composable
private fun ThemeCard(t: Theme, name: String, picked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(118.dp).clip(RoundedCornerShape(10.dp)).background(t.bg)
        .border(if (picked) 2.dp else 1.dp, if (picked) Color(0xFF3D8BFF) else t.sub.copy(alpha = .4f), RoundedCornerShape(10.dp))
        .clickable { onClick() }) {
        // the paper or the cloth of the theme, drawn as fine grain (the same every time); the picture themes draw their picture
        if (t.deco != 0) ThemeDecor(t.deco, Modifier.fillMaxSize())
        else Canvas(Modifier.fillMaxSize()) {
            var seed = name.hashCode()
            fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
            repeat(40) {
                val y = rnd() * size.height
                drawLine(t.ink.copy(alpha = .03f + rnd() * .05f), Offset(rnd() * size.width * .2f, y), Offset(size.width - rnd() * size.width * .2f, y + (rnd() - .5f) * 6f), strokeWidth = 1f + rnd())
            }
        }
        Text("Aa", color = t.ink, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(12.dp))
        Text(name, color = t.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(10.dp))
    }
}

@Composable
private fun SectionTitle(text: String, th: Theme) {
    Text(text, color = th.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun SettingRow(label: String, th: Theme, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = th.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

/**
 * Find free books: a search of the public-domain catalogue (Turkish first), and a tap on a result downloads it into the
 * phone's Books folder and puts it on the shelf through [onAdded].
 */
@Composable
private fun FindPage(th: Theme, onAdded: (Book) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<BookFind.Found>?>(null) }
    var busy by remember { mutableStateOf("") }
    val booksDir = File(android.os.Environment.getExternalStorageDirectory(), "Books")
    fun search() {
        if (query.isBlank()) return
        scope.launch {
            busy = tr("Searching...", "Aranıyor...")
            results = withContext(Dispatchers.IO) { runCatching { BookFind.search(query) }.getOrNull() }
            busy = if (results == null) tr("No connection", "Bağlantı yok") else ""
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedField(query, th, tr("Title or author", "Başlık veya yazar")) { query = it }
            Box(Modifier.size(40.dp).clickable { search() }, contentAlignment = Alignment.Center) { PIcon("search", th.ink, 22.dp) }
        }
        Text(tr("Only books whose copyright has ended, from Project Gutenberg. Turkish first.",
            "Yalnızca telif süresi dolmuş kitaplar, Project Gutenberg'den. Önce Türkçe."), color = th.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        if (busy.isNotEmpty()) Text(busy, color = th.sub, fontSize = 13.sp)
        if (results != null && results.isNullOrEmpty() && busy.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = th.sub, fontSize = 13.sp)
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(results.orEmpty()) { _, f ->
                Column(Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        busy = tr("Downloading...", "İndiriliyor...")
                        val file = withContext(Dispatchers.IO) { runCatching { BookFind.download(booksDir, f) }.getOrNull() }
                        val book = file?.let { withContext(Dispatchers.IO) { Ebook.open(ctx, Uri.fromFile(it)) } }
                        busy = ""
                        if (book != null) onAdded(book) else busy = tr("This book cannot be downloaded", "Bu kitap indirilemiyor")
                    }
                }.padding(vertical = 10.dp)) {
                    Text(f.title, color = th.ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (f.author.isNotEmpty()) Text(f.author, color = th.sub, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StepButton(text: String, th: Theme, onClick: () -> Unit) {
    Text(text, color = th.ink, fontSize = 18.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(th.ink.copy(alpha = .08f)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 4.dp))
}

@Composable
private fun InfoLine(label: String, value: String, th: Theme) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = th.sub, fontSize = 13.sp, modifier = Modifier.weight(.45f))
        Text(value, color = th.ink, fontSize = 13.sp, modifier = Modifier.weight(.55f))
    }
}

@Composable
private fun ReaderList(items: List<String>, th: Theme, onPick: (Int) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(items) { i, t ->
            Text(t, color = th.ink, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().clickable { onPick(i) }.padding(vertical = 10.dp))
        }
    }
}

@Composable
private fun OutlinedField(value: String, th: Theme, hint: String, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onChange, singleLine = true,
        textStyle = TextStyle(color = th.ink, fontSize = 15.sp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(th.ink.copy(alpha = .08f)).padding(10.dp),
        decorationBox = { inner -> if (value.isEmpty()) Text(hint, color = th.sub, fontSize = 15.sp); inner() },
    )
}

/** A PDF: each page is drawn by the system's renderer at the width of the screen, one at a time. */
@Composable
private fun PdfPages(ctx: Context, uri: Uri, page: Int, th: Theme, forward: Boolean, onTurn: (Int) -> Unit, onTap: () -> Unit) {
    val renderer by produceState<PdfRenderer?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.contentResolver.openFileDescriptor(uri, "r")?.let { PdfRenderer(it) } }.getOrNull()
        }
    }
    DisposableEffect(renderer) {
        onDispose { runCatching { renderer?.close() } }
    }
    val r = renderer
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val wPx = with(density) { maxWidth.roundToPx() }
        val count = r?.pageCount ?: 0
        val idx = page.coerceIn(0, (count - 1).coerceAtLeast(0))
        val bmp by produceState<Bitmap?>(null, idx, wPx, r) {
            value = null
            value = if (r == null || count == 0) null else withContext(Dispatchers.IO) {
                synchronized(r) {
                    val pg = r.openPage(idx)
                    val h = (wPx * pg.height / pg.width.toFloat()).toInt().coerceAtLeast(1)
                    val out = Bitmap.createBitmap(wPx, h, Bitmap.Config.ARGB_8888)
                    out.eraseColor(android.graphics.Color.WHITE)
                    pg.render(out, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pg.close()
                    out
                }
            }
        }
        AnimatedContent(targetState = idx, transitionSpec = {
            if (forward) (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(260)))
            else (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(260)))
        }, label = "pdf") { _ ->
            val b = bmp
            if (b != null) Canvas(Modifier.fillMaxSize()) { drawImage(b.asImageBitmap()) }
        }
        Box(Modifier.fillMaxSize().pointerInput(idx, count) {
            detectTapGestures(onTap = { o ->
                val w = size.width
                when {
                    o.x < w / 3f -> onTurn(-1)
                    o.x > w * 2f / 3f -> if (idx < count - 1) onTurn(1)
                    else -> onTap()
                }
            })
        })
        Text("${idx + 1}/$count", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
    }
}

/** The face of a font choice: 0 serif, 1 sans-serif, 2 monospace. */
private fun fontOf(i: Int): FontFamily = when (i) { 1 -> FontFamily.SansSerif; 2 -> FontFamily.Monospace; else -> FontFamily.Serif }

@Composable
private fun PopRow(label: String, on: Boolean, th: Theme, family: FontFamily = FontFamily.Default, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (on) th.ink else th.sub, fontSize = 14.sp, fontFamily = family, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        if (on) Text("✓", color = th.ink, fontSize = 14.sp)
    }
}

/**
 * The picture of a reader theme, drawn in the top part of the page (no picture is stored, so it is the same every time):
 * 1 the night sky with stars, 2 and 3 cherry branches with their blossoms (light, then night).
 */
@Composable
private fun ThemeDecor(deco: Int, modifier: Modifier) {
    Canvas(modifier) {
        var seed = 11 + deco * 97
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        val top = size.height * .42f
        if (deco == 1) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFF2A2F6B).copy(alpha = .35f), Color.Transparent), 0f, top))
            repeat(170) {
                val r = (.5f + rnd() * 1.6f).dp.toPx()
                drawCircle(Color.White.copy(alpha = .25f + rnd() * .7f), r, Offset(rnd() * size.width, rnd() * top))
            }
            return@Canvas
        }
        val petal = if (deco == 2) Color(0xFFF2A3BA) else Color(0xFFC95F82)
        val stem = if (deco == 2) Color(0xFF8A5A4E) else Color(0xFF6E4A4A)
        fun flower(c: Offset, r: Float) {
            repeat(5) { k ->
                val a = k * 1.2566f + .3f
                drawCircle(petal.copy(alpha = .85f), r, Offset(c.x + cos(a) * r * .9f, c.y + sin(a) * r * .9f))
            }
            drawCircle(Color(0xFFFFE08A).copy(alpha = .9f), r * .4f, c)
        }
        // two branches from the top corners, the blossoms set along them
        val branches = listOf(Offset(0f, 0f) to Offset(size.width * .55f, top * .9f), Offset(size.width, 0f) to Offset(size.width * .4f, top * .6f))
        branches.forEach { (from, to) ->
            val path = Path().apply {
                moveTo(from.x, from.y)
                cubicTo(from.x + (to.x - from.x) * .2f, from.y + top * .3f, to.x, to.y - top * .2f, to.x, to.y)
            }
            drawPath(path, stem, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            repeat(9) {
                val t = (it + rnd() * .6f) / 9f
                val c = Offset(from.x + (to.x - from.x) * t + (rnd() - .5f) * 30.dp.toPx(), from.y + (to.y - from.y) * t + (rnd() - .5f) * 24.dp.toPx())
                flower(c, (5f + rnd() * 4f).dp.toPx())
            }
        }
        // a few petals drifting down
        repeat(12) { flower(Offset(rnd() * size.width, rnd() * top), (2.5f + rnd() * 2f).dp.toPx()) }
    }
}

/** A wooden shelf background, drawn (no picture is stored): the grain of planks, lighter at the top. */
@Composable
private fun WoodBackground(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF9C6B3C), Color(0xFF7A4E2A), Color(0xFF8A5C32))))
        // the grain: long soft lines of different strength, drawn the same way every time
        var seed = 7
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        repeat(60) {
            val y = rnd() * size.height
            val a = .05f + rnd() * .12f
            drawLine(Color.Black.copy(alpha = a), Offset(0f, y), Offset(size.width, y + (rnd() - .5f) * 14f), strokeWidth = 1f + rnd() * 2.5f)
        }
        // the shelf boards: a darker line every 160 dp
        val step = 160.dp.toPx()
        var yb = step
        while (yb < size.height) { drawRect(Color.Black.copy(alpha = .35f), Offset(0f, yb), Size(size.width, 4.dp.toPx())); yb += step }
    }
}

/** The text of a paragraph, its part that is read now (the range of the page's text, [start] being where the paragraph begins) set in a background. */
private fun highlighted(text: String, start: Int, r: IntRange, color: Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        append(text)
        if (!r.isEmpty()) {
            val a = (r.first - start).coerceIn(0, text.length)
            val b = (r.last + 1 - start).coerceIn(0, text.length)
            if (b > a) addStyle(androidx.compose.ui.text.SpanStyle(background = color), a, b)
        }
    }
