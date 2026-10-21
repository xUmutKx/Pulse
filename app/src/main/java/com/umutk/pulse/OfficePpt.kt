package com.umutk.pulse

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorFill
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.TextDecrease
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TextIncrease
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FormatAlignCenter
import androidx.compose.material.icons.outlined.FormatAlignLeft
import androidx.compose.material.icons.outlined.FormatAlignRight
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Highlight
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import kotlin.math.roundToInt

// PowerPoint: slide strip + canvas + slide show on the shared themed chrome (OfChromeBasic draws the 365 / 2017 / 2013 / 2003 / 95 windows).
// Parsing, rendering model, edits and the zip writer live in OfficePptData.kt.

/** The PowerPoint window in the look chosen for it. */
@Composable
fun PptChrome(theme: OfTheme, title: String, acts: List<OAct>, status: String, onBack: () -> Unit, content: @Composable BoxScope.() -> Unit) =
    // the Home tab gets the Word-style ribbon instead of the plain button row
    CompositionLocalProvider(LocalOfRibbon provides mapOf("Home" to pptHome(theme, acts))) {
        OfChromeBasic(theme, title, acts, status, onBack, content)
    }

/** The PowerPoint Home tab in the Word style: one row of icon cells with a caption under each, scrolling sideways. */
fun pptHome(theme: OfTheme, acts: List<OAct>): @Composable () -> Unit = {
    val pal = theme.pal
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp)) {
        acts.filter { it.group == "Home" }.forEach { a ->
            Column(Modifier.width(56.dp).clip(RoundedCornerShape(3.dp))
                .background(if (a.on) pal.accent.copy(alpha = .22f) else Color.Transparent).alpha(if (a.enabled) 1f else .4f)
                .clickable(enabled = a.enabled, onClick = a.click).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(a.icon, a.label, Modifier.size(19.dp), pal.ink)
                Text(a.label, color = pal.ink, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun OfPptScreen(doc: OfDoc, theme: OfTheme) {
    val loaded = remember(doc) { runCatching { PptModel(OfPptBin.toPptx(doc.file)) } }
    DisposableEffect(loaded) { onDispose { loaded.getOrNull()?.close() } }
    val m = loaded.getOrNull()
    if (m == null) {
        PptChrome(theme, doc.name, listOf(OAct("Close", "File", Icons.Outlined.Close, png = "exit") { doc.close() }), "", doc.close) {
            OfErrorCard(theme, loaded.exceptionOrNull()?.message ?: "PowerPoint")
        }
        return
    }
    PptEditor(m, doc, theme)
}

private val PPT_COLORS = listOf(0xFFFFFF, 0x000000, 0xC00000, 0xFF0000, 0xFFC000, 0xFFFF00, 0x92D050, 0x00B050, 0x00B0F0, 0x0070C0, 0x002060, 0x7030A0,
    0x44546A, 0xE7E6E6, 0xED7D31, 0xA5A5A5, 0x4472C4, 0x70AD47)

@Composable
private fun PptEditor(m: PptModel, doc: OfDoc, theme: OfTheme) {
    var ver by remember { mutableIntStateOf(0) }
    var cur by remember { mutableIntStateOf(0) }
    var sel by remember { mutableStateOf<Element?>(null) }
    var show by remember { mutableStateOf(false) }
    var notesOn by remember { mutableStateOf(true) }
    var editText by remember { mutableStateOf<String?>(null) }
    var colorMode by remember { mutableIntStateOf(0) }   // 1 shape fill, 2 slide background, 3 text colour, 4 highlight
    var menu by remember { mutableStateOf(false) }
    var sizePick by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var msg by remember { mutableStateOf("") }
    val pickImg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            val ext = when (ctx.contentResolver.getType(uri)) { "image/jpeg" -> "jpg"; "image/gif" -> "gif"; "image/webp" -> "webp"; "image/bmp" -> "bmp"; else -> "png" }
            sel = m.addPicture(cur, bytes, ext); ver++; doc.dirty = true
        }.onFailure { msg = it.message ?: "error" }
    }
    val ed = doc.canEdit
    val v = ver
    cur = cur.coerceIn(0, m.slides.size - 1)
    val r = remember(v, cur) { m.render(cur) }
    val selShape = r.shapes.firstOrNull { it.el === sel }
    val hasText = selShape != null && selShape.kind == "sp"
    val first = selShape?.paras?.firstOrNull { it.runs.isNotEmpty() }?.runs?.first()
    fun changed() { ver++; doc.dirty = true }
    fun edit(f: () -> Unit) { runCatching(f).onFailure { msg = it.message ?: "error" }; changed() }
    fun bump(d: Float) { val s = selShape; val b = first; if (s != null && b != null) edit { m.eachRun(cur, s.el) { it.setAttribute("sz", (((b.sizePt + d).coerceIn(6f, 200f)) * 100).roundToInt().toString()) } } }
    fun runs(f: (Element) -> Unit) { val s = selShape; if (s != null) edit { m.eachRun(cur, s.el, f) } }
    fun align(a: String) { val s = selShape; if (s != null) edit { m.eachPara(cur, s.el) { it.setAttribute("algn", a) } } }
    fun toggleBold() { val on = first?.bold == true; runs { it.setAttribute("b", if (on) "0" else "1") } }
    fun toggleItalic() { val on = first?.italic == true; runs { it.setAttribute("i", if (on) "0" else "1") } }
    fun toggleUnder() { val on = first?.under == true; runs { it.setAttribute("u", if (on) "none" else "sng") } }
    fun toggleBullet() { val s = selShape; val on = s?.paras?.firstOrNull()?.bullet == true; if (s != null) edit { m.setBullet(cur, s.el, !on) } }
    fun copySel() { selShape?.let { m.copy(cur, it.el); ver++ } }
    fun cutSel() { selShape?.let { m.copy(cur, it.el); edit { m.remove(cur, it.el); sel = null } } }
    fun pasteSel() { edit { sel = m.paste(cur) } }
    fun dupSel() { selShape?.let { s -> m.copy(cur, s.el); pasteSel() } }
    fun delSel() { selShape?.let { s -> edit { m.remove(cur, s.el); sel = null } } }
    val any = ed && selShape != null

    val acts = listOf(
        OAct(tr("Save", "Kaydet"), "File", Icons.Outlined.Save, enabled = ed) { val w = m.saver(); doc.save(w) { msg = it; doc.dirty = false } },
        OAct(tr("Save a copy", "Kopya kaydet"), "File", Icons.Outlined.SaveAs, enabled = ed) { val w = m.saver(); doc.saveCopy(w) { msg = it } },
        OAct(tr("Close", "Kapat"), "File", Icons.Outlined.Close, png = "exit") { doc.close() },
        OAct(tr("New slide", "Yeni slayt"), "Home", Icons.Outlined.Add, enabled = ed) { edit { m.addSlide(cur); cur++; sel = null } },
        OAct(tr("Duplicate", "Çoğalt"), "Home", Icons.Outlined.ContentCopy, enabled = ed, png = "copy") { edit { m.duplicate(cur); cur++; sel = null } },
        OAct(tr("Delete", "Sil"), "Home", Icons.Outlined.Delete, enabled = ed && m.slides.size > 1, png = "delete") { edit { m.delete(cur); sel = null } },
        OAct(tr("Move up", "Yukarı"), "Home", Icons.Outlined.ArrowUpward, enabled = ed && cur > 0) { edit { m.move(cur, cur - 1); cur-- } },
        OAct(tr("Move down", "Aşağı"), "Home", Icons.Outlined.ArrowDownward, enabled = ed && cur < m.slides.size - 1) { edit { m.move(cur, cur + 1); cur++ } },
        OAct(tr("Edit text", "Metni düzenle"), "Home", Icons.Outlined.Edit, enabled = ed && hasText) { editText = selShape!!.paras.joinToString("\n") { p -> p.runs.joinToString("") { it.text } } },
        OAct(tr("Bold", "Kalın"), "Home", Icons.Outlined.FormatBold, on = first?.bold == true, enabled = ed && hasText) { toggleBold() },
        OAct(tr("Italic", "İtalik"), "Home", Icons.Outlined.FormatItalic, on = first?.italic == true, enabled = ed && hasText) { toggleItalic() },
        OAct(tr("Underline", "Altı çizili"), "Home", Icons.Outlined.FormatUnderlined, on = first?.under == true, enabled = ed && hasText) { toggleUnder() },
        OAct(first?.sizePt?.roundToInt()?.toString() ?: tr("Size", "Boyut"), "Home", Icons.Outlined.FormatSize, enabled = ed && first != null) { sizePick = true },
        OAct(tr("Larger", "Büyüt"), "Home", Icons.Outlined.TextIncrease, enabled = ed && first != null) { bump(2f) },
        OAct(tr("Smaller", "Küçült"), "Home", Icons.Outlined.TextDecrease, enabled = ed && first != null) { bump(-2f) },
        OAct(tr("Text colour", "Yazı rengi"), "Home", Icons.Outlined.FormatColorText, enabled = ed && first != null) { colorMode = 3 },
        OAct(tr("Highlight", "Vurgu"), "Home", Icons.Outlined.Highlight, enabled = ed && first != null) { colorMode = 4 },
        OAct(tr("Shape fill", "Dolgu"), "Home", Icons.Outlined.FormatColorFill, enabled = ed && selShape != null && selShape.kind != "tbl") { colorMode = 1 },
        OAct(tr("Left", "Sola"), "Home", Icons.Outlined.FormatAlignLeft, on = first != null && selShape?.paras?.firstOrNull()?.align == 0, enabled = ed && hasText) { align("l") },
        OAct(tr("Centre", "Ortala"), "Home", Icons.Outlined.FormatAlignCenter, on = selShape?.paras?.firstOrNull()?.align == 1, enabled = ed && hasText) { align("ctr") },
        OAct(tr("Right", "Sağa"), "Home", Icons.Outlined.FormatAlignRight, on = selShape?.paras?.firstOrNull()?.align == 2, enabled = ed && hasText) { align("r") },
        OAct(tr("Bullets", "Madde"), "Home", Icons.Outlined.FormatListBulleted, on = selShape?.paras?.firstOrNull()?.bullet == true, enabled = ed && hasText) { toggleBullet() },
        OAct(tr("Cut", "Kes"), "Home", Icons.Outlined.ContentCut, enabled = any) { cutSel() },
        OAct(tr("Copy", "Kopyala"), "Home", Icons.Outlined.ContentCopy, enabled = any) { copySel() },
        OAct(tr("Paste", "Yapıştır"), "Home", Icons.Outlined.ContentPaste, enabled = ed && m.clip != null) { pasteSel() },
        OAct(tr("New slide", "Yeni slayt"), "Insert", Icons.Outlined.Add, enabled = ed) { edit { m.addSlide(cur); cur++; sel = null } },
        OAct(tr("Picture", "Resim"), "Insert", Icons.Outlined.Image, enabled = ed) { pickImg.launch("image/*") },
        OAct(tr("Text box", "Metin kutusu"), "Insert", Icons.Outlined.TextFields, enabled = ed) { edit { sel = m.addShape(cur, true) } },
        OAct(tr("Shape", "Şekil"), "Insert", Icons.Outlined.CropSquare, enabled = ed) { edit { sel = m.addShape(cur, false) } },
        OAct(tr("Background", "Arka plan"), "Design", Icons.Outlined.Palette, enabled = ed) { colorMode = 2 },
        OAct(tr("From start", "Baştan"), "Slide Show", Icons.Outlined.Slideshow) { cur = 0; sel = null; show = true },
        OAct(tr("From current", "Bu slayttan"), "Slide Show", Icons.Outlined.PlayArrow, png = "run") { sel = null; show = true },
        OAct(tr("Notes", "Notlar"), "View", Icons.Outlined.Notes, on = notesOn) { notesOn = !notesOn },
    )
    val pal = theme.pal
    PptChrome(theme, doc.name, acts, "${tr("Slide", "Slayt")} ${cur + 1} / ${m.slides.size}   $msg", doc.close) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val land = maxWidth > maxHeight
            val main: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                        val w = minOf(maxWidth, maxHeight * (m.cx / m.cy))
                        Box(Modifier.shadow(if (theme.classic || theme.base == "365" || theme.base == "2017") 0.dp else 4.dp)) {
                            PptSlideView(m, r, w, sel, ed, onMove = { s, dx, dy -> edit { m.setXfrm(cur, s.el, s.x + dx, s.y + dy, s.w, s.h) } },
                                onSize = { s, dw, dh -> edit { m.setXfrm(cur, s.el, s.x, s.y, s.w + dw, s.h + dh) } },
                                onLong = { s -> sel = s.el; menu = true }) { s ->
                                if (ed && s != null && s.el === sel && s.kind == "sp") editText = s.paras.joinToString("\n") { p -> p.runs.joinToString("") { it.text } }
                                else sel = s?.el
                            }
                            if (menu && any && selShape != null) DropdownMenu(true, { menu = false }, offset = DpOffset(w * (selShape.x / m.cx), w * (selShape.y / m.cx))) {
                                if (hasText) {
                                    Row { PIc(Icons.Outlined.FormatBold, first?.bold == true) { toggleBold() }; PIc(Icons.Outlined.FormatItalic, first?.italic == true) { toggleItalic() }
                                        PIc(Icons.Outlined.FormatUnderlined, first?.under == true) { toggleUnder() }; PIc(Icons.Outlined.TextDecrease, false) { bump(-2f) }
                                        PIc(Icons.Outlined.TextIncrease, false) { bump(2f) } }
                                    Row { PIc(Icons.Outlined.FormatColorText, false) { menu = false; colorMode = 3 }; PIc(Icons.Outlined.Highlight, false) { menu = false; colorMode = 4 }
                                        PIc(Icons.Outlined.FormatAlignLeft, false) { align("l") }; PIc(Icons.Outlined.FormatAlignCenter, false) { align("ctr") }
                                        PIc(Icons.Outlined.FormatAlignRight, false) { align("r") } }
                                    Row { PIc(Icons.Outlined.FormatSize, false) { menu = false; sizePick = true }; PIc(Icons.Outlined.FormatListBulleted, false) { toggleBullet() }
                                        PIc(Icons.Outlined.FormatColorFill, false) { menu = false; colorMode = 1 } }
                                }
                                DropdownMenuItem({ Text(tr("Cut", "Kes")) }, { menu = false; cutSel() }, leadingIcon = { Icon(Icons.Outlined.ContentCut, null) })
                                DropdownMenuItem({ Text(tr("Copy", "Kopyala")) }, { menu = false; copySel() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                                DropdownMenuItem({ Text(tr("Paste", "Yapıştır")) }, { menu = false; pasteSel() }, enabled = m.clip != null, leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) })
                                DropdownMenuItem({ Text(tr("Duplicate", "Çoğalt")) }, { menu = false; dupSel() }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                                DropdownMenuItem({ Text(tr("Bring to front", "En öne getir")) }, { menu = false; edit { m.front(cur, selShape.el) } }, leadingIcon = { Icon(Icons.Outlined.FlipToFront, null) })
                                DropdownMenuItem({ Text(tr("Delete", "Sil")) }, { menu = false; delSel() }, leadingIcon = { Icon(Icons.Outlined.Delete, null) })
                            }
                        }
                    }
                    if (notesOn) Text(m.notes(cur).ifEmpty { tr("Click to add notes", "Not eklemek için tıklayın") }, color = pal.ink.copy(alpha = .7f), fontSize = 12.sp, maxLines = 2,
                        modifier = Modifier.fillMaxWidth().background(pal.ribbonBg).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            val thumb: @Composable (Int) -> Unit = { i ->
                val tm = remember(v, i) { m.render(i) }
                Row(Modifier.padding(4.dp).clickable { cur = i; sel = null }, verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", color = pal.ink, fontSize = 10.sp, modifier = Modifier.width(16.dp))
                    Box(Modifier.border(if (i == cur) 2.dp else 1.dp, if (i == cur) pal.accent else pal.line)) { PptSlideView(m, tm, 92.dp, null, false) { } }
                }
            }
            if (land) Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.width(124.dp).fillMaxHeight().background(Color.Black.copy(alpha = .08f))) { items(m.slides.size + 0 * v) { thumb(it) } }
                main(Modifier.weight(1f).fillMaxHeight())
            } else Column(Modifier.fillMaxSize()) {
                main(Modifier.weight(1f).fillMaxWidth())
                LazyRow(Modifier.fillMaxWidth().height(70.dp).background(Color.Black.copy(alpha = .08f))) { items(m.slides.size + 0 * v) { thumb(it) } }
            }
        }
    }
    if (show) PptShow(m, cur, { cur = it }) { show = false }
    editText?.let { t ->
        var txt by remember(t) { mutableStateOf(t) }
        WinAlert(onDismissRequest = { editText = null }, title = { Text(tr("Edit text", "Metni düzenle")) },
            text = { OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth()) },
            confirmButton = { TextButton({ editText = null; val s = selShape; if (s != null) edit { m.setText(cur, s.el, txt) } }) { Text("OK") } },
            dismissButton = { TextButton({ editText = null }) { Text(tr("Cancel", "İptal")) } })
    }
    if (colorMode != 0) WinAlert(onDismissRequest = { colorMode = 0 }, title = { Text(when (colorMode) { 1 -> tr("Shape fill", "Dolgu"); 3 -> tr("Text colour", "Yazı rengi"); 4 -> tr("Highlight", "Vurgu"); else -> tr("Background", "Arka plan") }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PPT_COLORS.chunked(6).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { c ->
                    Box(Modifier.size(34.dp).background(Color(0xFF000000 or c.toLong()), CircleShape).border(1.dp, Color.Gray, CircleShape).clickable {
                        val mode = colorMode; colorMode = 0; val s = selShape
                        if (mode == 1 && s != null) edit { m.setFill(cur, s.el, c) } else if (mode == 2) edit { m.setBackground(cur, c) }
                        else if (mode == 3) runs { pptRunColor(it, c) } else if (mode == 4) runs { pptRunHighlight(it, c) }
                    })
                } } }
            }
        }, confirmButton = {}, dismissButton = { TextButton({ colorMode = 0 }) { Text(tr("Cancel", "İptal")) } })
    if (sizePick) WinAlert(onDismissRequest = { sizePick = false }, title = { Text(tr("Font size", "Yazı boyutu")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(8, 10, 12, 14, 16, 18, 20, 24, 28, 32, 36, 44, 54, 72).chunked(5).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { row.forEach { n ->
                    TextButton({ sizePick = false; runs { it.setAttribute("sz", (n * 100).toString()) } }) { Text("$n") }
                } } }
            }
        }, confirmButton = {}, dismissButton = { TextButton({ sizePick = false }) { Text(tr("Cancel", "İptal")) } })
}

@Composable
private fun PIc(icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, click: () -> Unit) {
    Box(Modifier.size(40.dp).background(if (on) Color(0x332B88D8) else Color.Transparent).clickable(onClick = click), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp)) }
}

@Composable
private fun PptShow(m: PptModel, cur: Int, setCur: (Int) -> Unit, exit: () -> Unit) {
    val r = remember(cur) { m.render(cur) }
    var drag by remember { mutableFloatStateOf(0f) }
    fun go(d: Int) { val n = cur + d; if (n >= m.slides.size) exit() else if (n >= 0) setCur(n) }
    // own window: full screen, bars hidden, back closes it
    Dialog(onDismissRequest = exit, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.let { w ->
                WindowCompat.setDecorFitsSystemWindows(w, false)
                WindowCompat.getInsetsController(w, view).apply { systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; hide(WindowInsetsCompat.Type.systemBars()) }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(cur) { detectTapGestures { go(if (it.x > size.width / 2) 1 else -1) } }
            .pointerInput(cur) { detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = { if (drag < -80f) go(1) else if (drag > 80f) go(-1) }, onHorizontalDrag = { _, dx -> drag += dx }) },
            contentAlignment = Alignment.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PptSlideView(m, r, minOf(maxWidth, maxHeight * (m.cx / m.cy)), null, false) { }
            }
        }
    }
}

/** One slide drawn at [width]; [edit] makes shapes tappable (onTap(null) = empty area). */
@Composable
fun PptSlideView(m: PptModel, r: PRender, width: Dp, sel: Element?, edit: Boolean, onMove: (PShape, Float, Float) -> Unit = { _, _, _ -> },
    onSize: (PShape, Float, Float) -> Unit = { _, _, _ -> }, onLong: (PShape) -> Unit = { }, onTap: (PShape?) -> Unit) {
    val d = LocalDensity.current
    val k = with(d) { width.toPx() } / m.cx
    Box(Modifier.size(width, with(d) { (m.cy * k).toDp() }).clipToBounds().background(if (OfficePrefs.darkPages && r.bg == -1) OfficePrefs.pageBgC else Color(r.bg))
        .then(if (edit) Modifier.pointerInput(r) { detectTapGestures { onTap(null) } } else Modifier)) {
        r.shapes.forEach { PptShapeView(m, it, k, edit && it.el === sel, edit, onTap, { dx, dy -> onMove(it, dx, dy) }, { dw, dh -> onSize(it, dw, dh) }, { onLong(it) }) }
    }
}

@Composable
private fun PptShapeView(m: PptModel, s: PShape, k: Float, selected: Boolean, edit: Boolean, onTap: (PShape?) -> Unit, onMove: (Float, Float) -> Unit, onSize: (Float, Float) -> Unit, onLong: () -> Unit) {
    val d = LocalDensity.current
    val empty = s.paras.none { p -> p.runs.any { it.text.isNotEmpty() } }
    var ox by remember(s.el) { mutableFloatStateOf(0f) }; var oy by remember(s.el) { mutableFloatStateOf(0f) }
    var gw by remember(s.el) { mutableFloatStateOf(0f) }; var gh by remember(s.el) { mutableFloatStateOf(0f) }
    val movable = edit && selected && m.canMove(s.el)
    Box(Modifier.offset { IntOffset((s.x * k + ox).roundToInt(), (s.y * k + oy).roundToInt()) }
        .size(with(d) { maxOf(s.w * k + gw, 8f).toDp() }, with(d) { maxOf(s.h * k + gh, 8f).toDp() }).rotate(s.rot)
        .then(if (edit) Modifier.pointerInput(s.el) { detectTapGestures(onLongPress = { onLong() }, onTap = { onTap(s) }) } else Modifier)
        .then(if (movable) Modifier.pointerInput(s.el) {
            detectDragGestures(onDragEnd = { val x = ox / k; val y = oy / k; ox = 0f; oy = 0f; onMove(x, y) }, onDragCancel = { ox = 0f; oy = 0f }) { ch, a -> ch.consume(); ox += a.x; oy += a.y }
        } else Modifier)
        .drawBehind {
            val w = size.width; val h = size.height; val sw = maxOf(1f, s.lineW * k)
            if (s.geom == "line" || s.geom.contains("onnector")) { drawLine(Color(s.line ?: s.fill ?: 0xFF000000.toInt()), Offset.Zero, Offset(w, h), sw); return@drawBehind }
            if (s.kind != "sp") return@drawBehind
            val p = Path().apply { when (s.geom) {
                "ellipse" -> addOval(Rect(0f, 0f, w, h))
                "roundRect" -> addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(minOf(w, h) * .17f)))
                "triangle" -> { moveTo(w / 2, 0f); lineTo(w, h); lineTo(0f, h); close() }
                "diamond" -> { moveTo(w / 2, 0f); lineTo(w, h / 2); lineTo(w / 2, h); lineTo(0f, h / 2); close() }
                else -> addRect(Rect(0f, 0f, w, h))
            } }
            s.fill?.let { drawPath(p, Color(it)) }
            s.line?.let { drawPath(p, Color(it), style = Stroke(sw)) }
        }) {
        when (s.kind) {
            "pic" -> {
                val bmp by produceState<ImageBitmap?>(null, s.media) { value = s.media?.let { p -> withContext(Dispatchers.IO) { runCatching { m.bitmap(p) }.getOrNull() } } }
                bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) } ?: Box(Modifier.fillMaxSize().background(Color(0x22888888)))
            }
            "tbl" -> Column {
                s.table?.forEachIndexed { ri, row -> Row {
                    row.forEachIndexed { ci, cell ->
                        Box(Modifier.size(with(d) { ((s.colW.getOrNull(ci) ?: 0f) * k).toDp() }, with(d) { ((s.rowH.getOrNull(ri) ?: 0f) * k).toDp() })
                            .background(cell.fill?.let { Color(it) } ?: Color.Transparent).border(Dp.Hairline, Color(0xFF999999)).padding(with(d) { (45720f * k).toDp() })) {
                            PptParas(cell.paras, k, 0, Modifier.fillMaxSize())
                        }
                    }
                } }
            }
            else -> if (!empty) PptParas(s.paras, k, s.anchor, Modifier.fillMaxSize().padding(with(d) { (s.ins[0] * k).toDp() }, with(d) { (s.ins[1] * k).toDp() }, with(d) { (s.ins[2] * k).toDp() }, with(d) { (s.ins[3] * k).toDp() }))
            else if (edit && s.ph) Box(Modifier.fillMaxSize().border(1.dp, Color(0x88888888)), contentAlignment = Alignment.Center) {
                Text(tr("Click to add text", "Metin eklemek için tıklayın"), color = Color(0xFF888888), fontSize = with(d) { (18f * 12700f * k).toSp() }, maxLines = 1)
            }
        }
        if (selected) Box(Modifier.fillMaxSize().border(2.dp, Color(0xFF2B88D8)))
        if (movable) Box(Modifier.align(Alignment.BottomEnd).offset(7.dp, 7.dp).size(16.dp).background(Color.White, CircleShape).border(2.dp, Color(0xFF2B88D8), CircleShape)
            .pointerInput(s.el) {
                detectDragGestures(onDragEnd = { val w = gw / k; val h = gh / k; gw = 0f; gh = 0f; onSize(w, h) }, onDragCancel = { gw = 0f; gh = 0f }) { ch, a -> ch.consume(); gw += a.x; gh += a.y }
            })
    }
}

@Composable
private fun PptParas(paras: List<PPara>, k: Float, anchor: Int, modifier: Modifier) {
    val d = LocalDensity.current
    Column(modifier, verticalArrangement = when (anchor) { 1 -> Arrangement.Center; 2 -> Arrangement.Bottom; else -> Arrangement.Top }) {
        paras.forEach { p ->
            val base = p.runs.firstOrNull()?.sizePt ?: 18f
            val str = buildAnnotatedString {
                if (p.bullet) append("•  ")
                p.runs.forEach { r ->
                    withStyle(SpanStyle(color = if (OfficePrefs.darkPages && r.color == 0xFF000000.toInt()) OfficePrefs.pageFgC else Color(r.color), fontSize = with(d) { (r.sizePt * 12700f * k).toSp() },
                        background = r.hl?.let { Color(it) } ?: Color.Unspecified, fontWeight = if (r.bold) FontWeight.Bold else null, fontStyle = if (r.italic) FontStyle.Italic else null,
                        textDecoration = if (r.under) TextDecoration.Underline else null)) { append(r.text) }
                }
            }
            Text(if (p.runs.isEmpty()) androidx.compose.ui.text.AnnotatedString(" ") else str, fontSize = with(d) { (base * 12700f * k).toSp() },
                textAlign = when (p.align) { 1 -> TextAlign.Center; 2 -> TextAlign.End; else -> TextAlign.Start }, modifier = Modifier.fillMaxWidth())
        }
    }
}