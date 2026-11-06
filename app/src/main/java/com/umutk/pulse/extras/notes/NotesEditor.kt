package com.umutk.pulse.extras.notes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.umutk.pulse.PIcon
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.umutk.pulse.T
import com.umutk.pulse.bevel
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One note open for editing. The toolbar holds the actions, the paper holds the title, the body (raw text, or the
 * markdown-lite view), the links, the attachments and the counts. Each change is saved at once.
 */
@Composable
fun NoteEditor(
    note: Note, notes: List<Note>, onEdit: ((Note) -> Note) -> Unit, onDlg: (NDlg) -> Unit,
    onDuplicate: () -> Unit, onTrash: () -> Unit, onOpenTitle: (String) -> Unit, onSketch: () -> Unit,
    onBack: () -> Unit, rev: Int,
) {
    val ctx = LocalContext.current
    val fg = T.text
    val paper = NotesPrefs.paper(ctx)
    val size = NotesPrefs.fontSize(ctx)
    val plain = paper.id == "auto"
    val surface = if (plain) Color.Transparent else Color(paper.bg)
    val ink = if (plain) fg else Color(paper.ink)
    val inkSub = ink.copy(alpha = .6f)
    val shape = RoundedCornerShape(if (T.material) 16.dp else T.radius.coerceAtMost(8.dp))
    val edge = if (plain) Modifier else if (T.bevel) Modifier.bevel(false) else Modifier.border(1.dp, T.line, shape)
    val date = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

    var preview by remember(note.id) { mutableStateOf(false) }
    var palette by remember(note.id) { mutableStateOf(false) }
    // the pen: the tool that is on (keyboard: only the text; pen, marker, eraser, lasso: the S Pen draws and a finger scrolls), its settings kept between notes
    var mode by remember(note.id) { mutableStateOf(InkMode.KEYBOARD) }
    var lastDraw by remember { mutableStateOf(InkMode.PEN) }
    var panel by remember(note.id) { mutableStateOf(false) }
    var selected by remember(note.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var penStyle by remember { mutableStateOf(InkPrefs.penStyle(ctx)) }
    var penColor by remember { mutableStateOf(InkPrefs.penColor(ctx)) }
    var penSize by remember { mutableStateOf(InkPrefs.penSize(ctx)) }
    var markerColor by remember { mutableStateOf(InkPrefs.markerColor(ctx)) }
    var markerSize by remember { mutableStateOf(InkPrefs.markerSize(ctx)) }
    var eraserSize by remember { mutableStateOf(InkPrefs.eraserSize(ctx)) }
    var finger by remember { mutableStateOf(InkPrefs.finger(ctx)) }
    var snap by remember { mutableStateOf(InkPrefs.snap(ctx)) }
    val inkUndo = remember(note.id) { mutableStateListOf<String>() }
    val inkRedo = remember(note.id) { mutableStateListOf<String>() }
    val textRedo = remember(note.id) { mutableStateListOf<String>() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // the tools of the note (pin, colour, folder, reminder, lock, photo, sketch, voice, read aloud, checklist) sit behind one plus button
    var more by remember(note.id) { mutableStateOf(false) }
    // the text row: a heading, bold, a bullet or a checklist line, added at the end of the note
    var fmt by remember(note.id) { mutableStateOf(false) }
    var recording by remember(note.id) { mutableStateOf(false) }
    var speaking by remember(note.id) { mutableStateOf(false) }
    val undo = remember(note.id) { mutableStateListOf<String>() }
    var lastPush by remember(note.id) { mutableStateOf(0L) }
    val rec = remember(note.id) { NRecorder(ctx) }
    val speaker = remember(note.id) { NSpeaker(ctx) }
    DisposableEffect(note.id) { onDispose { rec.release(); speaker.release() } }
    val canDictate = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }

    // a file is linked, not copied: the note shows its name and opens it where it is
    val pickFile = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { uri ->
        if (uri != null) { val entry = NotesFileLink.make(ctx, uri); onEdit { it.copy(files = it.files + entry) } }
    }
    val pickImage = rememberLauncherForActivityResult(com.umutk.pulse.PulsePick.OpenDocument()) { uri ->
        val name = uri?.let { NotesMedia.copyIn(ctx, it) }
        if (name != null) onEdit { it.copy(files = it.files + name) }
    }
    val dictate = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!said.isNullOrBlank()) onEdit { it.copy(body = if (it.body.isBlank()) said else it.body.trimEnd() + "\n" + said) }
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording(ctx, rec) { recording = it }
    }

    // a body change joins the undo history at most once a second, so typing a word is one step
    fun changeBody(v: String) {
        val now = System.currentTimeMillis()
        if (v != note.body && now - lastPush > 800) {
            undo.add(note.body)
            if (undo.size > 40) undo.removeAt(0)
            lastPush = now
            textRedo.clear()
        }
        onEdit { it.copy(body = v) }
    }
    // the ink: every change keeps the earlier picture of the page for undo and redo
    fun editInk(f: (List<InkStroke>) -> List<InkStroke>) {
        val next = NotesInk.encode(f(NotesInk.decode(note.ink)))
        if (next == note.ink) return
        inkUndo.add(note.ink)
        if (inkUndo.size > 60) inkUndo.removeAt(0)
        inkRedo.clear()
        onEdit { it.copy(ink = next) }
    }
    fun undoInk() { val prev = inkUndo.removeLastOrNull() ?: return; inkRedo.add(note.ink); selected = emptySet(); onEdit { it.copy(ink = prev) } }
    fun redoInk() { val nxt = inkRedo.removeLastOrNull() ?: return; inkUndo.add(note.ink); selected = emptySet(); onEdit { it.copy(ink = nxt) } }
    fun undoText() { val prev = undo.removeLastOrNull() ?: return; textRedo.add(note.body); onEdit { it.copy(body = prev) } }
    fun redoText() { val nxt = textRedo.removeLastOrNull() ?: return; undo.add(note.body); onEdit { it.copy(body = nxt) } }
    // a tool of the bar: the tool that is already on opens its settings; a drawing tool puts the keyboard away
    fun pick(m: InkMode) {
        if (m == mode) { panel = m != InkMode.KEYBOARD && !panel; return }
        mode = m; panel = false; selected = emptySet()
        if (m == InkMode.PEN || m == InkMode.MARKER) lastDraw = m
        if (m != InkMode.KEYBOARD) { focus.clearFocus(); keyboard?.hide() }
    }
    val inkOn = mode != InkMode.KEYBOARD
    // what the pen draws now: with the keyboard on, the last pen tool (the S Pen still draws)
    val drawTool = if (mode == InkMode.KEYBOARD) lastDraw else mode
    val penType = PEN_STYLES[penStyle.coerceIn(0, PEN_STYLES.lastIndex)]
    val strokeColor = if (drawTool == InkMode.MARKER) (markerColor and 0x00FFFFFF) or (0x66 shl 24) else if (penColor == 0) ink.toArgb() else penColor
    val strokeWidth = (if (drawTool == InkMode.MARKER) 14f * markerSize else penType.base * penSize) * density
    val strokeKind = if (drawTool == InkMode.MARKER) 1 else 0
    val strokeGain = if (drawTool == InkMode.MARKER) 0f else penType.gain
    val inkList = remember(note.ink) { NotesInk.decode(note.ink) }
    val pageMin = maxOf(520f, (NotesInk.bounds(inkList)?.bottom ?: 0f) / density + 320f).dp
    // the body with its selection, so the text row formats the selected words (or the place of the cursor)
    var bodyField by remember(note.id) { mutableStateOf(TextFieldValue(note.body, TextRange(note.body.length))) }
    LaunchedEffect(note.body) { if (bodyField.text != note.body) bodyField = TextFieldValue(note.body, TextRange(note.body.length)) }
    fun applyFmt(kind: String) {
        val t = bodyField.text
        val lo = minOf(bodyField.selection.min, bodyField.selection.max).coerceIn(0, t.length)
        val hi = maxOf(bodyField.selection.min, bodyField.selection.max).coerceIn(0, t.length)
        val (out, cur) = if (kind == "bold") {
            if (lo == hi) { val ins = "**" + tr("bold", "kalın") + "**"; (t.substring(0, lo) + ins + t.substring(hi)) to lo + ins.length }
            else { val w = "**" + t.substring(lo, hi) + "**"; (t.substring(0, lo) + w + t.substring(hi)) to lo + w.length }
        } else {
            // a line marker goes at the start of the line where the selection starts
            val prefix = when (kind) { "heading" -> "## "; "bullet" -> "- "; else -> "[ ] " }
            val ls = t.lastIndexOf('\n', lo - 1) + 1
            (t.substring(0, ls) + prefix + t.substring(ls)) to hi + prefix.length
        }
        bodyField = TextFieldValue(out, TextRange(cur.coerceIn(0, out.length)))
        changeBody(out)
    }
    fun toggleRecord() {
        if (recording) {
            recording = false
            rec.stop()?.let { name -> onEdit { it.copy(files = it.files + name) } }
            return
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording(ctx, rec) { recording = it }
        else micPerm.launch(Manifest.permission.RECORD_AUDIO)
    }
    fun toggleLock() {
        when {
            note.locked -> onEdit { it.copy(locked = false) }
            !NotesPrefs.hasPin(ctx) -> onDlg(NDlg.SetPin(then = { onEdit { it.copy(locked = true) } }))
            else -> onEdit { it.copy(locked = true) }
        }
    }
    fun toggleSpeak() {
        if (speaking) { speaker.stop(); speaking = false }
        else { speaker.speak(listOf(note.title, note.body).filter { it.isNotBlank() }.joinToString(". ")); speaking = true }
    }

    val text = listOf(note.title, note.body).filter { it.isNotBlank() }.joinToString("\n\n")
    val words = note.body.split(Regex("\\s+")).count { it.isNotBlank() }
    val links = NotesStore.linksOf(note.body).distinct()
    val myTitle = NotesStore.titleOf(note)
    val backlinks = notes.filter { other ->
        other.id != note.id && other.trashedAt == 0L && (!other.locked || other.id in NotesPrefs.unlocked) &&
            NotesStore.linksOf(other.body).any { it.equals(myTitle, true) || (note.title.isNotBlank() && it.equals(note.title, true)) }
    }

    Box(Modifier.fillMaxSize().padding(horizontal = pageGutter), contentAlignment = Alignment.TopCenter) {
    Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
        // the note's own actions: four icons up here; the rest is in the "+" row below, each with its name
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MIcon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "Geri"), fg) { onBack() }
            Box(Modifier.weight(1f))
            NIcon("pin", if (note.pinned) T.accent else fg, tr("Pin", "Sabitle")) { onEdit { it.copy(pinned = !it.pinned) } }
            MIcon(Icons.Outlined.Share, tr("Share", "Paylaş"), fg) { shareNote(ctx, text) }
            MIcon(if (more) Icons.Outlined.Close else Icons.Outlined.Add, tr("More", "Diğer"), if (more) T.accent else fg) { more = !more }
        }
        if (more) Row(Modifier.fillMaxWidth().padding(top = 6.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            NChip(tr("Undo", "Geri al"), fg) { undo.removeLastOrNull()?.let { prev -> onEdit { it.copy(body = prev) } } }
            NChip(if (preview) tr("Edit", "Düzenle") else tr("Preview", "Önizleme"), fg) { preview = !preview }
            NChip(tr("Copy", "Kopyala"), fg) { copyNote(ctx, text) }
            NChip(tr("Duplicate", "Çoğalt"), fg) { onDuplicate() }
            NChip(tr("Colour", "Renk"), if (palette) T.accent else fg) { palette = !palette }
            NChip(tr("Folder", "Klasör"), fg) { onDlg(NDlg.Folder(setOf(note.id))) }
            NChip(if (note.remindAt > 0) tr("Reminder set", "Hatırlatma var") else tr("Reminder", "Hatırlatma"), if (note.remindAt > 0) T.accent else fg) { onDlg(NDlg.Remind(setOf(note.id))) }
            NChip(if (note.locked) tr("Unlock", "Kilidi aç") else tr("Lock", "Kilitle"), fg) { toggleLock() }
            NChip(tr("Add a file", "Dosya ekle"), fg) { pickFile.launch(arrayOf("*/*")) }
            NChip(if (recording) tr("Stop recording", "Kaydı durdur") else tr("Record", "Ses kaydet"), if (recording) Color(0xFFE53935) else fg) { toggleRecord() }
            if (canDictate) NChip(tr("Dictate", "Konuşarak yaz"), fg) {
                dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
            }
            NChip(tr("Read aloud", "Sesli oku"), if (speaking) T.accent else fg) { toggleSpeak() }
            NChip(tr("Move to trash", "Çöpe taşı"), fg) { onTrash() }
        }
        if (palette) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            NSwatchRow(note.color) { v -> onEdit { it.copy(color = v) }; palette = false }
        }

        // the settings of the tool that is on (the pen's types, its size and colours, the eraser's size, whether a finger draws) and what the lasso chose
        if (panel && inkOn) InkPanel(
            mode = mode, style = penStyle, onStyle = { penStyle = it; InkPrefs.setPenStyle(ctx, it) },
            color = if (mode == InkMode.MARKER) markerColor else penColor,
            onColor = { c -> if (mode == InkMode.MARKER) { markerColor = c; InkPrefs.setMarkerColor(ctx, c) } else { penColor = c; InkPrefs.setPenColor(ctx, c) } },
            size = when (mode) { InkMode.MARKER -> markerSize; InkMode.ERASER -> eraserSize; else -> penSize },
            onSize = { v -> when (mode) { InkMode.MARKER -> { markerSize = v; InkPrefs.setMarkerSize(ctx, v) }; InkMode.ERASER -> { eraserSize = v; InkPrefs.setEraserSize(ctx, v) }; else -> { penSize = v; InkPrefs.setPenSize(ctx, v) } } },
            finger = finger, onFinger = { finger = it; InkPrefs.setFinger(ctx, it) }, snap = snap, onSnap = { snap = it; InkPrefs.setSnap(ctx, it) },
            onClearAll = { editInk { emptyList() }; selected = emptySet() }, autoColor = ink,
        )
        if (mode == InkMode.LASSO && selected.isNotEmpty()) InkSelection(
            count = selected.size,
            onColor = { c ->
                val col = if (c == 0) ink.toArgb() else c
                editInk { l -> l.mapIndexed { i, s -> if (i in selected) s.copy(color = if (s.kind == 1) (col and 0x00FFFFFF) or (0x66 shl 24) else col) else s } }
            },
            onDelete = { val ids = selected; editInk { l -> l.filterIndexed { i, _ -> i !in ids } }; selected = emptySet() },
            onDone = { selected = emptySet() }, autoColor = ink,
        )

        // the paper: title, body, links, attachments and counts scroll together with the ink, which lies over them; the page grows as the pen goes down
        Box(Modifier.fillMaxWidth().weight(1f)) {
        Column(
            Modifier.fillMaxSize().padding(top = 6.dp, bottom = 12.dp)
                .clip(shape).background(surface, shape).then(edge)
                .verticalScroll(rememberScrollState()).padding(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().heightIn(min = pageMin)) {
            Column(Modifier.fillMaxWidth()) {
            NField(note.title, { v -> onEdit { it.copy(title = v) } }, tr("Title", "Başlık"), ink, inkSub, size + 4, true, true, Modifier.fillMaxWidth())
            if (note.remindAt > 0) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIconDim("bell", inkSub)
                Spacer(Modifier.width(6.dp))
                Text(date.format(Date(note.remindAt)), color = inkSub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                NIcon("close", inkSub, tr("Remove reminder", "Hatırlatmayı kaldır")) { onEdit { it.copy(remindAt = 0L) } }
            }
            Spacer(Modifier.height(10.dp))
            if (preview) NoteBody(note.body, size, ink, T.accent) { changeBody(it) }
            else NFieldTV(bodyField, { tv -> bodyField = tv; if (tv.text != note.body) changeBody(tv.text) }, tr("Write here", "Buraya yaz"), ink, inkSub, size, Modifier.fillMaxWidth().heightIn(min = 180.dp))

            if (links.isNotEmpty()) {
                Text(tr("Links", "Bağlantılar"), color = inkSub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    links.forEach { l -> Text(l, color = T.accent, fontSize = 14.sp, modifier = Modifier.clickable2 { onOpenTitle(l) }) }
                }
            }
            if (backlinks.isNotEmpty()) {
                Text(tr("Linked from", "Bağlantılı notlar"), color = inkSub, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    backlinks.forEach { b -> Text(NotesStore.titleOf(b).ifBlank { tr("Untitled", "Adsız") }, color = T.accent, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable2 { onOpenTitle(NotesStore.titleOf(b)) }) }
                }
            }
            note.files.forEach { name ->
                Box(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    if (name.startsWith(NotesFileLink.PREFIX)) FileLinkRow(ctx, name, ink) { onEdit { it.copy(files = it.files - name) } }
                    else if (name.startsWith("au_")) AudioRow(ctx, name, ink) { onEdit { it.copy(files = it.files - name) }; NotesMedia.remove(ctx, name) }
                    else ImageRow(ctx, name, ink) { onEdit { it.copy(files = it.files - name) }; NotesMedia.remove(ctx, name) }
                }
            }
            Text(tr("$words words · ${note.body.length} characters", "$words kelime · ${note.body.length} karakter"), color = inkSub, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
            }
            InkLayer(
                modifier = Modifier.matchParentSize(), strokes = inkList, tool = drawTool, penOnly = mode == InkMode.KEYBOARD, finger = finger,
                color = strokeColor, width = strokeWidth, kind = strokeKind, gain = strokeGain, eraser = 16f * eraserSize * density, snap = snap,
                accent = T.accent, selected = selected,
                onStroke = { st -> editInk { it + st } },
                onErase = { ids -> editInk { l -> l.filterIndexed { i, _ -> i !in ids } }; selected = emptySet() },
                onSelect = { selected = it },
                onMove = { ids, d -> editInk { l -> l.mapIndexed { i, s -> if (i in ids) NotesInk.moved(s, d) else s } } },
                onPenUsed = { if (mode == InkMode.KEYBOARD) pick(lastDraw) },
            )
            }
        }
        }
        if (fmt) Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(22.dp)).background(T.panel.copy(alpha = .9f)).padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                Triple("H", tr("Heading", "Başlık"), "heading"),
                Triple("B", tr("Bold", "Kalın"), "bold"),
                Triple("•", tr("Bullet", "Madde"), "bullet"),
                Triple("☐", tr("Checklist", "Kontrol"), "check"),
            ).forEach { (label, desc, kind) ->
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { applyFmt(kind) }.semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
                    Text(label, color = fg, fontSize = 17.sp, fontWeight = if (label == "B") FontWeight.Bold else FontWeight.SemiBold)
                }
            }
        }
        InkBar(
            mode = mode, onMode = { pick(it) },
            canUndo = if (inkOn) inkUndo.isNotEmpty() else undo.isNotEmpty(), canRedo = if (inkOn) inkRedo.isNotEmpty() else textRedo.isNotEmpty(),
            onUndo = { if (inkOn) undoInk() else undoText() }, onRedo = { if (inkOn) redoInk() else redoText() },
            textOn = fmt, onText = { fmt = !fmt }, onPhoto = { pickImage.launch(arrayOf("image/*")) }, moreOn = more, onMore = { more = !more },
        )
    }
    }
}

private fun startRecording(ctx: Context, rec: NRecorder, onState: (Boolean) -> Unit) {
    if (rec.start()) onState(true) else noteToast(ctx, tr("Recording failed", "Kayıt başarısız"))
}

/** A photo or sketch attachment, drawn at its own proportions; the close button removes it. */
@Composable
private fun ImageRow(ctx: Context, name: String, ink: Color, onRemove: () -> Unit) {
    val bmp = remember(name) { NotesMedia.image(ctx, name) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { NIcon("close", ink, tr("Remove", "Kaldır")) { onRemove() } }
        if (bmp != null) Image(bmp.asImageBitmap(), name, Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.Fit)
        else Text(tr("Missing file", "Dosya yok"), color = ink, fontSize = 13.sp)
    }
}

/** A voice note with play and stop. */
@Composable
private fun AudioRow(ctx: Context, name: String, ink: Color, onRemove: () -> Unit) {
    var playing by remember(name) { mutableStateOf(false) }
    val player = remember(name) { NPlayer(ctx, name) { playing = false } }
    DisposableEffect(name) { onDispose { player.stop() } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        NIcon(if (playing) "pause" else "play", ink, tr("Play", "Oynat")) { playing = player.toggle() }
        Text(tr("Voice note", "Ses notu"), color = ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        NIcon("close", ink, tr("Remove", "Kaldır")) { onRemove() }
    }
}

/** A named action in the "more" row: the words show what it does, so no icon has to be guessed. */
@Composable
private fun NChip(label: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.padding(end = 8.dp).clip(RoundedCornerShape(16.dp)).background(T.panel.copy(alpha = .9f)).clickable { onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(label, color = tint, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** A Material icon of the note editor: the same round touch target as the others. */
@Composable
private fun MIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).clickable { onClick() }.semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        androidx.compose.material3.Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** The body field: its text and its selection, so the text row can format what is selected. */
@Composable
private fun NFieldTV(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, hint: String, ink: Color, sub: Color, size: Int, modifier: Modifier) {
    BasicTextField(
        value, onChange, modifier,
        textStyle = TextStyle(color = ink, fontSize = size.sp),
        cursorBrush = SolidColor(T.accent),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.text.isEmpty()) Text(hint, color = sub, fontSize = size.sp)
                inner()
            }
        },
    )
}

/** A field in the paper's own ink; the hint shows while it is empty. */
@Composable
private fun NField(value: String, onChange: (String) -> Unit, hint: String, ink: Color, sub: Color, size: Int, single: Boolean, bold: Boolean, modifier: Modifier) {
    BasicTextField(
        value, onChange, modifier,
        singleLine = single,
        textStyle = TextStyle(color = ink, fontSize = size.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        cursorBrush = SolidColor(T.accent),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty()) Text(hint, color = sub, fontSize = size.sp)
                inner()
            }
        },
    )
}

@Composable
private fun PIconDim(key: String, tint: Color) = com.umutk.pulse.PIcon(key, tint, 16.dp)

/** Clickable without the ripple, for the link words. */
private fun Modifier.clickable2(onClick: () -> Unit): Modifier =
    this.then(Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) })

/** A linked file in the note: its name and where it lives; a tap opens it, the cross takes the link out (the file stays). */
@Composable
private fun FileLinkRow(ctx: Context, entry: String, ink: Color, onRemove: () -> Unit) {
    val uri = NotesFileLink.uriOf(entry)
    val place = uri?.let { u -> u.path?.takeIf { it.isNotBlank() } ?: u.toString() }.orEmpty()
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(T.panel)
            .clickable { NotesFileLink.open(ctx, entry) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIcon("document", T.accent, 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(NotesFileLink.nameOf(entry), color = ink, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(place, color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.size(36.dp).clickable { onRemove() }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 16.dp) }
    }
}
