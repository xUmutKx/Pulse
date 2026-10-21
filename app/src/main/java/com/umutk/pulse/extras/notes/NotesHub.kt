package com.umutk.pulse.extras.notes

import android.Manifest
import android.os.Build
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import java.util.Calendar
import java.util.UUID

/** Which screen of Notes is shown. */
sealed class NScreen {
    object Home : NScreen()
    object Trash : NScreen()
    object Settings : NScreen()
    object Stats : NScreen()
    data class Edit(val id: String) : NScreen()
    data class Sketch(val id: String) : NScreen()
}

/** Dialogs the hub can show over its screens. */
sealed class NDlg {
    data class Folder(val ids: Set<String>) : NDlg()
    data class Remind(val ids: Set<String>) : NDlg()
    data class Swatch(val ids: Set<String>) : NDlg()
    data class SetPin(val then: (() -> Unit)?) : NDlg()
    data class DropForever(val id: String) : NDlg()
    object Sort : NDlg()
    object Font : NDlg()
    object Paper : NDlg()
    object DefaultColor : NDlg()
    object Template : NDlg()
    object EmptyTrash : NDlg()
    object RemovePin : NDlg()
}

/** Starter notes for the template picker: key, title and body, in the current language. */
private fun templates() = listOf(
    Triple("blank", tr("Blank note", "Boş not"), ""),
    Triple("shopping", tr("Shopping list", "Alışveriş listesi"), tr("[ ] Milk\n[ ] Bread\n[ ] Eggs", "[ ] Süt\n[ ] Ekmek\n[ ] Yumurta")),
    Triple("todo", tr("To do", "Yapılacaklar"), "[ ] "),
    Triple("meeting", tr("Meeting notes", "Toplantı notları"), tr("## Agenda\n- \n\n## Notes\n- \n\n## Actions\n[ ] ", "## Gündem\n- \n\n## Notlar\n- \n\n## Yapılacaklar\n[ ] ")),
    Triple("daily", tr("Daily log", "Günlük"), tr("## Today\n\n## Done\n[ ] ", "## Bugün\n\n## Tamamlanan\n[ ] ")),
)

private const val DAY = 86_400_000L

/** The reminder time of a preset key: one hour from now, this evening, or tomorrow morning. */
fun reminderAt(key: String, now: Long = System.currentTimeMillis()): Long {
    fun at(days: Int, hour: Int) = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, days); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return when (key) {
        "1h" -> now + 3_600_000L
        "evening" -> at(0, 20).let { if (it > now) it else at(1, 20) }
        "tomorrow" -> at(1, 9)
        else -> 0L
    }
}

/** Date bucket for the list headers. */
private fun bucket(t: Long): String {
    val start = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    return when {
        t >= start -> tr("Today", "Bugün")
        t >= start - DAY -> tr("Yesterday", "Dün")
        t >= start - 6 * DAY -> tr("This week", "Bu hafta")
        else -> tr("Earlier", "Daha önce")
    }
}

/** A square icon button of the notes toolbars: 40 dp touch area, the look's own icon, and a description for screen readers. */
@Composable
fun NIcon(key: String, tint: Color, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable { onClick() }.semantics { contentDescription = desc }, contentAlignment = Alignment.Center) { PIcon(key, tint, 22.dp) }
}

fun noteToast(c: Context, msg: String) = Toast.makeText(c, msg, Toast.LENGTH_SHORT).show()

fun shareNote(c: Context, text: String) {
    c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
}

fun copyNote(c: Context, text: String) {
    c.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("note", text))
    noteToast(c, tr("Copied", "Kopyalandı"))
}

/**
 * Notes: a searchable, filterable list (pinned, date groups, grid or list), the trash, the settings, statistics,
 * and the editor. Every change is saved at once.
 */
@Composable
fun NotesHub(onBack: () -> Unit) {
    val ctx = LocalContext.current
    // a settings change bumps this, so everything that reads NotesPrefs is drawn again
    var rev by remember { mutableStateOf(0) }
    val settingsRev = rev
    var notes by remember { mutableStateOf(loadNotes(ctx)) }
    var screen by remember { mutableStateOf<NScreen>(NScreen.Home) }
    var query by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    var folder by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dlg by remember { mutableStateOf<NDlg?>(null) }
    // a request from the widget, the notification or the shortcut: read once
    val entry = remember { NotesEntry.request.also { NotesEntry.request = null } }

    fun save(list: List<Note>) { notes = list; NotesStore.save(ctx, list) }
    fun edit(id: String, touch: Boolean = true, f: (Note) -> Note) {
        val old = notes.firstOrNull { it.id == id } ?: return
        val new = f(old).let { if (touch) it.copy(updated = System.currentTimeMillis()) else it }
        if (new.remindAt != old.remindAt) { if (new.remindAt > 0) NotesReminders.schedule(ctx, new) else NotesReminders.cancel(ctx, id) }
        save(notes.map { if (it.id == id) new else it })
    }
    fun newNote(title: String = "", body: String = ""): String {
        val now = System.currentTimeMillis()
        val n = Note(UUID.randomUUID().toString(), title, body, now, now, color = NotesPrefs.defaultColor(ctx))
        save(notes + n)
        screen = NScreen.Edit(n.id)
        return n.id
    }
    fun trash(ids: Set<String>) {
        val t = System.currentTimeMillis()
        ids.forEach { NotesReminders.cancel(ctx, it) }
        save(notes.map { if (it.id in ids) it.copy(trashedAt = t, pinned = false, remindAt = 0L) else it })
        selected = emptySet()
    }
    fun restore(ids: Set<String>) = save(notes.map { if (it.id in ids) it.copy(trashedAt = 0L) else it })
    fun dropForever(ids: Set<String>) {
        notes.filter { it.id in ids }.forEach { n -> n.files.forEach { NotesMedia.remove(ctx, it) }; NotesReminders.cancel(ctx, n.id) }
        save(notes.filterNot { it.id in ids })
    }
    fun closeEditor(id: String) {
        NotesPrefs.unlocked.remove(id)
        val n = notes.firstOrNull { it.id == id }
        // an empty new note is dropped on the way out
        if (n != null && n.title.isBlank() && n.body.isBlank() && n.files.isEmpty()) { NotesReminders.cancel(ctx, id); save(notes.filterNot { it.id == id }) }
        screen = NScreen.Home
    }
    fun openTitle(title: String) {
        val hit = notes.firstOrNull { it.trashedAt == 0L && NotesStore.titleOf(it).equals(title, true) }
        if (hit != null) screen = NScreen.Edit(hit.id) else newNote(title = title)
    }
    fun duplicate(id: String) {
        val n = notes.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        val copy = n.copy(id = UUID.randomUUID().toString(), created = now, updated = now, pinned = false, remindAt = 0L,
            files = n.files.mapNotNull { NotesMedia.copyOf(ctx, it) })
        save(notes + copy)
        screen = NScreen.Edit(copy.id)
    }

    // the launchers of the hub: export, import, notification permission
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) noteToast(ctx, runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(NotesStore.exportJson(notes.filter { n -> n.trashedAt == 0L }).toByteArray()) }
            tr("Exported", "Dışa aktarıldı")
        }.getOrElse { tr("Export failed", "Dışa aktarılamadı") })
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null) noteToast(ctx, runCatching {
            val text = notes.filter { it.trashedAt == 0L }.joinToString("\n\n---\n\n") { "# ${NotesStore.titleOf(it)}\n\n${it.body}" }
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            tr("Exported", "Dışa aktarıldı")
        }.getOrElse { tr("Export failed", "Dışa aktarılamadı") })
    }
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val added = runCatching { NotesStore.importJson(ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()) }.getOrNull()
            if (added == null) noteToast(ctx, tr("Not a notes file", "Not dosyası değil"))
            else { save(notes + added); noteToast(ctx, tr("${added.size} notes added", "${added.size} not eklendi")) }
        }
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // a request from outside: a new note from the widget, or a note from a notification or the shortcut
    androidx.compose.runtime.LaunchedEffect(entry) {
        if (entry != null) {
            if (entry.isEmpty()) {
                val now = System.currentTimeMillis()
                val n = Note(UUID.randomUUID().toString(), "", "", now, now, color = NotesPrefs.defaultColor(ctx))
                save(notes + n)
                screen = NScreen.Edit(n.id)
            } else if (notes.any { it.id == entry }) screen = NScreen.Edit(entry)
        }
    }

    // the secure flag keeps the screen out of screenshots and recents while the setting is on
    val secure = NotesPrefs.secure(ctx)
    DisposableEffect(secure) {
        val w = (ctx as? Activity)?.window
        if (secure) w?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) w?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    BackHandler {
        val s = screen
        when {
            selected.isNotEmpty() -> selected = emptySet()
            dlg != null -> dlg = null
            s is NScreen.Edit -> closeEditor(s.id)
            s is NScreen.Sketch -> screen = NScreen.Edit(s.id)
            s != NScreen.Home -> screen = NScreen.Home
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg)) {
        when (val s = screen) {
            is NScreen.Edit -> {
                val n = notes.firstOrNull { it.id == s.id }
                if (n == null) screen = NScreen.Home
                else if (n.locked && n.id !in NotesPrefs.unlocked) {
                    NLockedView(onUnlock = { NotesPrefs.unlocked.add(n.id); rev++ }, onBack = { closeEditor(n.id) })
                } else NoteEditor(
                    note = n, notes = notes,
                    onEdit = { f -> edit(n.id) { f(it) } },
                    onDlg = { dlg = it },
                    onDuplicate = { duplicate(n.id) },
                    onTrash = { trash(setOf(n.id)); screen = NScreen.Home },
                    onOpenTitle = { openTitle(it) },
                    onSketch = { screen = NScreen.Sketch(n.id) },
                    onBack = { closeEditor(n.id) },
                    rev = settingsRev,
                )
            }
            is NScreen.Sketch -> NSketch { name ->
                if (name != null) edit(s.id) { it.copy(files = it.files + name) }
                screen = NScreen.Edit(s.id)
            }
            NScreen.Trash -> TrashView(notes, onBack = { screen = NScreen.Home }, onRestore = { restore(setOf(it)) }, onDrop = { dlg = NDlg.DropForever(it) }, onEmpty = { dlg = NDlg.EmptyTrash })
            NScreen.Settings -> SettingsView(
                notes, settingsRev, onBack = { screen = NScreen.Home }, onDlg = { dlg = it }, onChanged = { rev++ },
                onStats = { screen = NScreen.Stats },
                onSetEncrypt = { on -> NotesPrefs.setEncrypted(ctx, on); save(notes) },
                onExport = { exportJson.launch("pulse-notes.json") }, onExportMd = { exportMd.launch("pulse-notes.md") },
                onImport = { importJson.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                onRemovePin = { dlg = NDlg.RemovePin },
            )
            NScreen.Stats -> StatsView(notes, onBack = { screen = NScreen.Settings })
            NScreen.Home -> HomeView(
                notes = notes, rev = settingsRev, query = query, onQuery = { query = it },
                tag = tag, onTag = { tag = if (tag == it) null else it },
                folder = folder, onFolder = { folder = if (folder == it) null else it },
                selected = selected, onSelect = { selected = it },
                onOpen = { screen = NScreen.Edit(it) },
                onNew = { dlg = NDlg.Template },
                onTrash = { trash(selected) },
                onBack = onBack,
                onChanged = { rev++ },
                onSwipe = { trash(setOf(it)) },
                onOpenTrash = { screen = NScreen.Trash },
                onOpenSettings = { screen = NScreen.Settings },
                onDlg = { dlg = it },
                onPinSelected = {
                    val all = selected.all { id -> notes.firstOrNull { it.id == id }?.pinned == true }
                    selected.forEach { id -> edit(id, touch = false) { it.copy(pinned = !all) } }
                    selected = emptySet()
                },
            )
        }
    }

    // dialogs
    when (val d = dlg) {
        is NDlg.Folder -> {
            val only = d.ids.singleOrNull()?.let { id -> notes.firstOrNull { it.id == id }?.folder }.orEmpty()
            val existing = notes.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
            NInput(tr("Folder", "Klasör"), tr("Folder name", "Klasör adı"), only, existing, { name ->
                d.ids.forEach { id -> edit(id, touch = false) { it.copy(folder = name) } }
                dlg = null
            }, { dlg = null })
        }
        is NDlg.Remind -> NPick(tr("Remind me", "Hatırlat"), listOf(
            "none" to tr("No reminder", "Hatırlatma yok"), "1h" to tr("In one hour", "Bir saat sonra"),
            "evening" to tr("This evening, 20:00", "Bu akşam, 20:00"), "tomorrow" to tr("Tomorrow, 09:00", "Yarın, 09:00"),
        ), "", { key ->
            val at = reminderAt(key)
            if (at > 0 && Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            d.ids.forEach { id -> edit(id, touch = false) { it.copy(remindAt = at) } }
            dlg = null
        }, { dlg = null })
        is NDlg.Swatch -> NSwatch(tr("Colour", "Renk"), if (d.ids.size == 1) notes.firstOrNull { it.id == d.ids.first() }?.color ?: 0 else 0, { v ->
            d.ids.forEach { id -> edit(id, touch = false) { it.copy(color = v) } }
            dlg = null
        }, { dlg = null })
        is NDlg.SetPin -> NPin(tr("New PIN", "Yeni PIN"), { pin ->
            NotesPrefs.setPin(ctx, pin)
            dlg = null
            d.then?.invoke()
            null
        }, { dlg = null })
        is NDlg.RemovePin -> com.umutk.pulse.PDialog(tr("Remove the PIN?", "PIN kaldırılsın mı?"), tr("Locked notes open without a PIN after this.", "Bundan sonra kilitli notlar PIN'siz açılır."), tr("Remove", "Kaldır"), {
            NotesPrefs.clearPin(ctx)
            NotesPrefs.unlocked.clear()
            save(notes.map { it.copy(locked = false) })
            rev++; dlg = null
        }, { dlg = null })
        is NDlg.DropForever -> com.umutk.pulse.PDialog(tr("Delete for good?", "Kalıcı olarak silinsin mi?"), notes.firstOrNull { it.id == d.id }?.let { NotesStore.titleOf(it) }.orEmpty(), tr("Delete", "Sil"), {
            dropForever(setOf(d.id)); dlg = null
        }, { dlg = null })
        is NDlg.EmptyTrash -> com.umutk.pulse.PDialog(tr("Empty the trash?", "Çöp kutusu boşaltılsın mı?"), tr("Nothing in it can be restored after this.", "Bundan sonra içindekiler geri alınamaz."), tr("Empty", "Boşalt"), {
            dropForever(notes.filter { it.trashedAt > 0 }.map { it.id }.toSet()); dlg = null
        }, { dlg = null })
        NDlg.Sort -> NPick(tr("Sort by", "Sırala"), NotesPrefs.Sort.values().map { it.name to tr(it.en, it.tr) }, NotesPrefs.sort(ctx).name, { key ->
            NotesPrefs.setSort(ctx, NotesPrefs.Sort.valueOf(key)); rev++; dlg = null
        }, { dlg = null })
        NDlg.Font -> NPick(tr("Text size", "Yazı boyutu"), listOf("14" to "14", "16" to "16", "18" to "18", "20" to "20"), NotesPrefs.fontSize(ctx).toString(), { k ->
            NotesPrefs.setFontSize(ctx, k.toInt()); rev++; dlg = null
        }, { dlg = null })
        NDlg.Paper -> NPick(tr("Paper", "Kağıt"), NotesPrefs.papers.map { it.id to tr(it.en, it.tr) }, NotesPrefs.paper(ctx).id, { k ->
            NotesPrefs.setPaper(ctx, k); rev++; dlg = null
        }, { dlg = null })
        NDlg.DefaultColor -> NSwatch(tr("New note colour", "Yeni not rengi"), NotesPrefs.defaultColor(ctx), { v ->
            NotesPrefs.setDefaultColor(ctx, v); rev++; dlg = null
        }, { dlg = null })
        NDlg.Template -> NPick(tr("New note", "Yeni not"), templates().map { it.first to it.second }, "blank", { key ->
            val t = templates().first { it.first == key }
            dlg = null
            newNote(title = if (key == "blank") "" else t.second, body = t.third)
        }, { dlg = null })
        null -> Unit
    }
}

private fun loadNotes(c: Context): List<Note> {
    val all = NotesStore.load(c)
    val kept = NotesStore.purge(c, all)
    if (kept.size != all.size) NotesStore.save(c, kept)
    return kept
}
