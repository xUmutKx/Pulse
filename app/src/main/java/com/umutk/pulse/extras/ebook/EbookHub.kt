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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.umutk.pulse.PSlider
import com.umutk.pulse.PSwitch
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * Books: a shelf of the books opened so far (covers drawn from the title, nothing stored), and the reader.
 * Tap the left or right third to turn the page, the middle to show or hide the bars. Pages turn with a slide and a fade.
 * The reader has its own colour themes (day, sepia, night, AMOLED black); the shelf follows the look of Pulse.
 */

private class Theme(val bg: Color, val ink: Color, val sub: Color)

private val READER_THEMES = listOf(
    Theme(Color(0xFFF5F2EA), Color(0xFF1E1E1E), Color(0xFF6B6B6B)),   // day
    Theme(Color(0xFFEAD9B5), Color(0xFF3B2F1E), Color(0xFF7A6A4E)),   // sepia
    Theme(Color(0xFF1C1C1E), Color(0xFFD8D8D8), Color(0xFF8E8E93)),   // night
    Theme(Color(0xFF000000), Color(0xFFC8C8C8), Color(0xFF7A7A7A)),   // AMOLED black
)
private val THEME_NAMES = listOf(tr("Day", "Gündüz"), tr("Sepia", "Sepya"), tr("Night", "Gece"), tr("AMOLED black", "AMOLED siyah"))

private fun rp(c: Context) = c.getSharedPreferences("ebook_reader", Context.MODE_PRIVATE)

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

@Composable
private fun Shelf(onBack: () -> Unit, onOpen: (Book) -> Unit) {
    val ctx = LocalContext.current
    var shelf by remember { mutableStateOf(shelfLoad(ctx)) }
    var note by remember { mutableStateOf("") }
    BackHandler { onBack() }
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
    Column(Modifier.fillMaxSize().background(T.panel).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clickable { onBack() }, contentAlignment = Alignment.Center) { PIcon("back", T.text, 24.dp) }
            Text(tr("Books", "Kitaplar"), color = T.text, fontSize = 18.sp, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Box(Modifier.size(40.dp).clickable { pick.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf")) }, contentAlignment = Alignment.Center) { PIcon("add", T.text, 24.dp) }
        }
        if (note.isNotEmpty()) Text(note, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        if (shelf.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("No books yet", "Henüz kitap yok"), color = T.sub, fontSize = 14.sp)
            }
        } else {
            LazyVerticalGrid(columns = GridCells.Adaptive(96.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 10.dp)) {
                items(shelf) { b ->
                    Column(Modifier.clickable { onOpen(b) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        // the cover is drawn from the title: a card in a colour of its own, the first letter on it
                        Box(Modifier.size(84.dp, 118.dp).clip(RoundedCornerShape(6.dp)).background(coverColor(b.title)), contentAlignment = Alignment.Center) {
                            Text(b.title.take(1).uppercase(), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(b.title, color = T.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(92.dp).padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(start: Book, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val uri = Uri.parse(start.uri)
    var book by remember { mutableStateOf<Book?>(null) }
    LaunchedEffect(start.uri) { book = withContext(Dispatchers.IO) { Ebook.open(ctx, uri) } }
    val b = book

    var themeIx by remember { mutableStateOf(rp(ctx).getInt("theme", 0)) }
    var textSize by remember { mutableStateOf(rp(ctx).getInt("size", 18)) }
    var eink by remember { mutableStateOf(EbookPrefs.eink(ctx)) }
    var fontIx by remember { mutableStateOf(EbookPrefs.font(ctx)) }
    var autoOn by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    val th = if (eink) Theme(Color.White, Color.Black, Color(0xFF555555)) else READER_THEMES[themeIx]
    val family = when (fontIx) { 1 -> FontFamily.SansSerif; 2 -> FontFamily.Monospace; else -> FontFamily.Serif }
    var chapter by remember { mutableStateOf(Ebook.place(ctx, start.uri).first) }
    var page by remember { mutableStateOf(Ebook.place(ctx, start.uri).second) }
    var forward by remember { mutableStateOf(true) }
    var bars by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf("") }
    var bookmarked by remember { mutableStateOf(Ebook.bookmarks(ctx, start.uri).contains("$chapter,$page")) }
    var query by remember { mutableStateOf("") }
    var brightness by remember { mutableStateOf(-1f) }
    var barIds by remember { mutableStateOf(EbookPrefs.bar(ctx)) }
    var autoSec by remember { mutableStateOf(EbookPrefs.autoSeconds(ctx)) }
    // the page's text, read aloud; a page read to its end turns to the next one
    val curText = remember { arrayOf("") }
    val pageTurner = remember { mutableStateOf<(() -> Unit)?>(null) }
    val speaker = remember { Speaker(ctx) { if (speaking) pageTurner.value?.invoke() } }
    DisposableEffect(Unit) { onDispose { speaker.shutdown(); setBrightness(ctx, -1f) } }
    BackHandler {
        if (panel.isNotEmpty()) panel = ""
        else { Ebook.savePlace(ctx, start.uri, chapter, page); onClose() }
    }
    fun save() = Ebook.savePlace(ctx, start.uri, chapter, page)

    Box(Modifier.fillMaxSize().background(th.bg)) {
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
                val bodyStyle = TextStyle(fontFamily = family, fontSize = textSize.sp, lineHeight = (textSize * 1.5f).sp, color = th.ink)
                val headStyle = TextStyle(fontFamily = family, fontSize = (textSize + 4).sp, lineHeight = ((textSize + 4) * 1.4f).sp, color = th.ink, fontWeight = FontWeight.Bold)
                val pages by produceState<List<List<Para>>?>(null, chapter, textSize, fontIx, wPx, hPx, themeIx) {
                    value = null
                    value = withContext(Dispatchers.Default) {
                        paginate(measurer, chapters.getOrNull(chapter)?.paras.orEmpty(), wPx - with(density) { 40.dp.roundToPx() }, hPx - with(density) { 90.dp.roundToPx() }, bodyStyle, headStyle, with(density) { 10.dp.toPx() })
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
                LaunchedEffect(speaking, key) { if (speaking) speaker.speak(curText[0]) else speaker.stop() }
                AnimatedContent(
                    targetState = key,
                    transitionSpec = {
                        if (eink) (fadeIn(tween(0)) togetherWith fadeOut(tween(0)))
                        else if (forward) (slideInHorizontally(tween(260)) { it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { -it / 5 } + fadeOut(tween(260)))
                        else (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(260))) togetherWith (slideOutHorizontally(tween(260)) { it / 5 } + fadeOut(tween(260)))
                    },
                    label = "page",
                ) { _ ->
                    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 44.dp), verticalArrangement = Arrangement.Top) {
                        cur.forEach { p -> Text(p.text, style = if (p.heading) headStyle else bodyStyle, modifier = Modifier.padding(bottom = 10.dp)) }
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
                Text("${chapter + 1}/${chapters.size.coerceAtLeast(1)}", color = th.sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))
                if (autoOn) Text(tr("Autoscroll", "Otomatik"), color = th.sub, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomStart).padding(12.dp))
            }
        }

        if (bars && b != null) {
            Row(Modifier.fillMaxWidth().background(th.bg.copy(alpha = .94f)).padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clickable { save(); onClose() }, contentAlignment = Alignment.Center) { PIcon("back", th.ink, 22.dp) }
                Text(b.title, color = th.ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                Box(Modifier.size(40.dp).clickable { bookmarked = Ebook.toggleBookmark(ctx, start.uri, chapter, page) }, contentAlignment = Alignment.Center) {
                    PIcon("pin", if (bookmarked) th.ink else th.sub, 22.dp)
                }
            }
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(th.bg.copy(alpha = .94f)).padding(horizontal = 8.dp, vertical = 6.dp)) {
                val count = if (b.pdf) 0 else b.chapters.size
                Text(tr("Chapter ${chapter + 1} of ${count.coerceAtLeast(1)}", "Bölüm ${chapter + 1} / ${count.coerceAtLeast(1)}"), color = th.sub, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    barIds.forEach { id ->
                        val t = BAR_TOOLS.firstOrNull { it.third == id } ?: return@forEach
                        val on = when (id) { "speak" -> speaking; "auto" -> autoOn; else -> false }
                        Box(Modifier.size(44.dp).clickable {
                            when (id) {
                                "chapters" -> panel = "chapters"
                                "text" -> { textSize = (textSize + if (textSize >= 26) -8 else 2); rp(ctx).edit().putInt("size", textSize).apply() }
                                "theme" -> { themeIx = (themeIx + 1) % READER_THEMES.size; rp(ctx).edit().putInt("theme", themeIx).apply() }
                                "bookmarks" -> panel = "bookmarks"
                                "search" -> panel = "search"
                                "speak" -> speaking = !speaking
                                "auto" -> autoOn = !autoOn
                                "options" -> panel = "options"
                                "info" -> panel = "info"
                            }
                        }, contentAlignment = Alignment.Center) {
                            if (id == "text") Text("Aa", color = if (on) th.bg else th.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            else PIcon(if (id == "speak" && speaking) "pause" else t.first, if (on) th.ink else th.ink.copy(alpha = .8f), 22.dp)
                        }
                    }
                }
            }
        }

        if (panel.isNotEmpty() && b != null) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(.92f).fillMaxHeight(.8f).clip(RoundedCornerShape(12.dp)).background(th.bg).padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (panel) {
                        "chapters" -> tr("Chapters", "Bölümler"); "bookmarks" -> tr("Bookmarks", "Yer imleri"); "search" -> tr("Search", "Ara")
                        "options" -> tr("Options", "Seçenekler"); else -> tr("Book information", "Kitap bilgisi") },
                        color = th.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clickable { panel = "" }, contentAlignment = Alignment.Center) { PIcon("close", th.ink, 20.dp) }
                }
                Spacer(Modifier.height(8.dp))
                when (panel) {
                    "chapters" -> ReaderList(b.chapters.mapIndexed { i, c -> "${i + 1}. ${c.title}" }, th) { i -> chapter = i; page = 0; forward = true; panel = ""; bars = false; save() }
                    "bookmarks" -> {
                        val marks = Ebook.bookmarks(ctx, start.uri)
                        if (marks.isEmpty()) Text(tr("No bookmarks", "Yer imi yok"), color = th.sub, fontSize = 13.sp)
                        else ReaderList(marks.map { m -> val c = m.substringBefore(',').toInt(); "${c + 1}. ${b.chapters.getOrNull(c)?.title.orEmpty()}" }, th) { i ->
                            val m = marks[i].split(','); chapter = m[0].toInt(); page = m[1].toInt(); forward = true; panel = ""; bars = false; save()
                        }
                    }
                    "search" -> {
                        OutlinedField(query, th, tr("Word or phrase", "Kelime veya cümle")) { query = it }
                        val hits = remember(query) { searchBook(b.chapters, query) }
                        if (query.isNotBlank() && hits.isEmpty()) Text(tr("Nothing found", "Bulunamadı"), color = th.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        ReaderList(hits.map { "${it.first + 1}. ${it.second}" }, th) { i -> chapter = hits[i].first; page = 0; forward = true; panel = ""; bars = false; save() }
                    }
                    "options" -> {
                        Text(tr("Font", "Yazı tipi"), color = th.sub, fontSize = 12.sp)
                        Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(tr("Serif", "Serif"), tr("Sans", "Sans"), tr("Mono", "Mono")).forEachIndexed { i, name ->
                                Text(name, color = if (fontIx == i) th.bg else th.ink, fontSize = 13.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (fontIx == i) th.ink else th.ink.copy(alpha = .1f)).clickable { fontIx = i; EbookPrefs.setFont(ctx, i) }.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                        Text(tr("Autoscroll: $autoSec s per page", "Otomatik kaydırma: sayfa başına $autoSec sn"), color = th.ink, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("−", color = th.ink, fontSize = 20.sp, modifier = Modifier.clickable { autoSec = (autoSec - 2).coerceAtLeast(3); EbookPrefs.setAutoSeconds(ctx, autoSec) }.padding(horizontal = 10.dp))
                            Text("+", color = th.ink, fontSize = 20.sp, modifier = Modifier.clickable { autoSec = (autoSec + 2).coerceAtMost(120); EbookPrefs.setAutoSeconds(ctx, autoSec) }.padding(horizontal = 10.dp))
                        }
                        Text(tr("Brightness", "Parlaklık"), color = th.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                        PSlider(if (brightness < 0f) .5f else brightness, { brightness = it; setBrightness(ctx, it) }, 0f..1f)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                            Text(tr("E-ink look", "E-mürekkep görünüm"), color = th.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            PSwitch(eink, { eink = it; EbookPrefs.setEink(ctx, it) })
                        }
                        Text(tr("Show on the bar", "Alt çubukta"), color = th.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                        BAR_TOOLS.forEach { t ->
                            val on = t.third in barIds
                            Row(Modifier.fillMaxWidth().clickable {
                                barIds = if (on) barIds - t.third else barIds + t.third
                                EbookPrefs.setBar(ctx, barIds)
                            }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(t.second, color = th.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(if (on) "✓" else "", color = th.ink, fontSize = 15.sp)
                            }
                        }
                    }
                    else -> {
                        Text(b.title, color = th.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        if (b.author.isNotEmpty()) Text(b.author, color = th.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        Spacer(Modifier.height(10.dp))
                        Text(tr("Chapters: ${b.chapters.size}", "Bölüm: ${b.chapters.size}"), color = th.ink, fontSize = 13.sp)
                        Text(tr("Theme: ${THEME_NAMES[themeIx]}", "Tema: ${THEME_NAMES[themeIx]}"), color = th.ink, fontSize = 13.sp)
                        Text(tr("Text size: $textSize", "Yazı boyutu: $textSize"), color = th.ink, fontSize = 13.sp)
                    }
                }
            }
        }
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
