package com.umutk.pulse.extras.notes

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    val surface = if (plain) T.panel else Color(paper.bg)
    val ink = if (plain) fg else Color(paper.ink)
    val inkSub = ink.copy(alpha = .6f)
    val shape = RoundedCornerShape(if (T.material) 16.dp else T.radius.coerceAtMost(8.dp))
    val edge = if (T.bevel) Modifier.bevel(false) else Modifier.border(1.dp, T.line, shape)
    val date = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }

    var preview by remember(note.id) { mutableStateOf(false) }
    var palette by remember(note.id) { mutableStateOf(false) }
    // the tools of the note (pin, colour, folder, reminder, lock, photo, sketch, voice, read aloud, checklist) sit behind one plus button
    var more by remember(note.id) { mutableStateOf(false) }
    var recording by remember(note.id) { mutableStateOf(false) }
    var speaking by remember(note.id) { mutableStateOf(false) }
    val undo = remember(note.id) { mutableStateListOf<String>() }
    var lastPush by remember(note.id) { mutableStateOf(0L) }
    val rec = remember(note.id) { NRecorder(ctx) }
    val speaker = remember(note.id) { NSpeaker(ctx) }
    DisposableEffect(note.id) { onDispose { rec.release(); speaker.release() } }
    val canDictate = remember { Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).resolveActivity(ctx.packageManager) != null }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
        }
        onEdit { it.copy(body = v) }
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

    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        // the actions of the note
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            NIcon("back", fg, tr("Back", "Geri")) { onBack() }
            Box(Modifier.weight(1f))
            NIcon(if (more) "close" else "add", if (more) T.accent else fg, tr("Add to the note", "Nota ekle")) { more = !more }
            NIcon("undo", fg, tr("Undo", "Geri al")) { undo.removeLastOrNull()?.let { prev -> onEdit { it.copy(body = prev) } } }
            NIcon(if (preview) "edit" else "view", fg, tr("Preview", "Önizleme")) { preview = !preview }
            NIcon("share", fg, tr("Share", "Paylaş")) { shareNote(ctx, text) }
            NIcon("copy", fg, tr("Copy", "Kopyala")) { copyNote(ctx, text) }
            NIcon("newfile", fg, tr("Duplicate", "Çoğalt")) { onDuplicate() }
            NIcon("delete", fg, tr("Move to trash", "Çöpe taşı")) { onTrash() }
        }
        if (more) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            NIcon("pin", if (note.pinned) T.accent else fg, tr("Pin", "Sabitle")) { onEdit { it.copy(pinned = !it.pinned) } }
            NIcon("palette", if (palette) T.accent else fg, tr("Colour", "Renk")) { palette = !palette }
            NIcon("folder", fg, tr("Folder", "Klasör")) { onDlg(NDlg.Folder(setOf(note.id))) }
            NIcon("bell", if (note.remindAt > 0) T.accent else fg, tr("Reminder", "Hatırlatma")) { onDlg(NDlg.Remind(setOf(note.id))) }
            NIcon(if (note.locked) "unlock" else "lock", fg, tr("Lock", "Kilitle")) { toggleLock() }
            NIcon("image", fg, tr("Add photo", "Fotoğraf ekle")) { pickImage.launch(arrayOf("image/*")) }
            NIcon("edit", fg, tr("Sketch", "Çizim")) { onSketch() }
            NIcon("record", if (recording) Color(0xFFE53935) else fg, tr("Record", "Ses kaydet")) { toggleRecord() }
            if (canDictate) NIcon("mic", fg, tr("Dictate", "Diktе et")) {
                dictate.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM))
            }
            NIcon("speak", if (speaking) T.accent else fg, tr("Read aloud", "Sesli oku")) { toggleSpeak() }
            NIcon("check", fg, tr("Checklist line", "Kontrol satırı")) {
                changeBody(if (note.body.isEmpty() || note.body.endsWith("\n")) note.body + "[ ] " else note.body + "\n[ ] ")
            }
        }
        if (palette) Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            NSwatchRow(note.color) { v -> onEdit { it.copy(color = v) }; palette = false }
        }

        // the paper: title, body, links, attachments and counts scroll together
        Column(
            Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp, bottom = 12.dp)
                .clip(shape).background(surface, shape).then(edge)
                .verticalScroll(rememberScrollState()).padding(14.dp),
        ) {
            NField(note.title, { v -> onEdit { it.copy(title = v) } }, tr("Title", "Başlık"), ink, inkSub, size + 4, true, true, Modifier.fillMaxWidth())
            if (note.remindAt > 0) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PIconDim("bell", inkSub)
                Spacer(Modifier.width(6.dp))
                Text(date.format(Date(note.remindAt)), color = inkSub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                NIcon("close", inkSub, tr("Remove reminder", "Hatırlatmayı kaldır")) { onEdit { it.copy(remindAt = 0L) } }
            }
            Spacer(Modifier.height(10.dp))
            if (preview) NoteBody(note.body, size, ink, T.accent) { changeBody(it) }
            else NField(note.body, { changeBody(it) }, tr("Write here", "Buraya yaz"), ink, inkSub, size, false, false, Modifier.fillMaxWidth().heightIn(min = 180.dp))

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
                    if (name.startsWith("au_")) AudioRow(ctx, name, ink) { onEdit { it.copy(files = it.files - name) }; NotesMedia.remove(ctx, name) }
                    else ImageRow(ctx, name, ink) { onEdit { it.copy(files = it.files - name) }; NotesMedia.remove(ctx, name) }
                }
            }
            Text(tr("$words words · ${note.body.length} characters", "$words kelime · ${note.body.length} karakter"), color = inkSub, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp))
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
