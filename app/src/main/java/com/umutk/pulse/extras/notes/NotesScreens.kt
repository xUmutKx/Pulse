package com.umutk.pulse.extras.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import com.umutk.pulse.PRadio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.ExtraLookIcon
import com.umutk.pulse.ExtraLookRow
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.PSwitch
import com.umutk.pulse.T
import com.umutk.pulse.pageGutter
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The list of notes: search, folder and tag filters, pinned and date groups, grid or list. */
@Composable
fun HomeView(
    notes: List<Note>, rev: Int, query: String, onQuery: (String) -> Unit,
    tag: String?, onTag: (String) -> Unit, folder: String?, onFolder: (String) -> Unit,
    selected: Set<String>, onSelect: (Set<String>) -> Unit, onOpen: (String) -> Unit, onNew: () -> Unit,
    onTrash: () -> Unit, onBack: () -> Unit, onOpenTrash: () -> Unit, onOpenSettings: () -> Unit,
    onDlg: (NDlg) -> Unit, onPinSelected: () -> Unit, onChanged: () -> Unit, onSwipe: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val fg = T.text
    val sub = T.sub
    val sort = NotesPrefs.sort(ctx)
    val view = NotesPrefs.view(ctx)
    val grid = view != "list"
    val showDates = NotesPrefs.dates(ctx)
    var drawer by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val unlocked = notes.filter { it.trashedAt == 0L && (!it.locked || it.id in NotesPrefs.unlocked) }
    // tags and folders come only from notes that are open, so a locked note leaks nothing
    val tags = unlocked.flatMap { NotesStore.tagsOf(it.body) }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(8).map { it.key }
    val folders = unlocked.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()
    val base: Comparator<Note> = when (sort) {
        NotesPrefs.Sort.UPDATED -> compareByDescending<Note> { it.updated }
        NotesPrefs.Sort.CREATED -> compareByDescending<Note> { it.created }
        NotesPrefs.Sort.TITLE -> compareBy<Note> { NotesStore.titleOf(it).lowercase() }
    }
    val order = compareByDescending<Note> { it.pinned }.then(if (NotesPrefs.reverse(ctx)) base.reversed() else base)
    val sorted = unlocked
        .filter { n -> (tag == null || tag in NotesStore.tagsOf(n.body)) && (folder == null || n.folder == folder) && NotesStore.matches(n, query) }
        .sortedWith(order)
    val sections: List<Pair<String, List<Note>>> = if (!NotesPrefs.groups(ctx) || sort == NotesPrefs.Sort.TITLE) listOf("" to sorted) else {
        val stamp: (Note) -> Long = { if (sort == NotesPrefs.Sort.CREATED) it.created else it.updated }
        buildList<Pair<String, List<Note>>> {
            val pinned = sorted.filter { it.pinned }
            if (pinned.isNotEmpty()) add(tr("Pinned", "Sabitli") to pinned)
            sorted.filter { !it.pinned }.groupBy { bucket(stamp(it)) }.forEach { (label, items) -> add(label to items) }
        }
    }

    if (drawer) NotesSidePanel(
        all = unlocked.size,
        pinned = unlocked.count { it.pinned },
        locked = notes.count { it.locked && it.trashedAt == 0L },
        trash = notes.count { it.trashedAt != 0L },
        folders = folders,
        counts = unlocked.groupingBy { it.folder }.eachCount(),
        current = folder,
        expanded = expanded,
        onToggle = { path -> expanded = if (path in expanded) expanded - path else expanded + path },
        onAll = { folder?.let { onFolder(it) }; drawer = false },
        onFolder = { onFolder(it); drawer = false },
        onPinned = { drawer = false },
        onLocked = { drawer = false },
        onTrash = { drawer = false; onOpenTrash() },
        onClose = { drawer = false },
    )
    Column(Modifier.fillMaxSize().padding(horizontal = pageGutter)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (selected.isEmpty()) {
                NIcon("back", fg, tr("Back", "Geri")) { onBack() }
                Text(tr("Notes", "Notlar"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                NIcon("menu", fg, tr("Folders", "Klasörler")) { drawer = true }
                NIcon("settings", fg, tr("Settings", "Ayarlar")) { onOpenSettings() }
                NIcon("add", fg, tr("New note", "Yeni not")) { onNew() }
            } else {
                NIcon("close", fg, tr("Cancel", "İptal")) { onSelect(emptySet()) }
                Text(tr("${selected.size} selected", "${selected.size} seçili"), color = fg, fontSize = 17.sp, modifier = Modifier.weight(1f))
                NIcon("check", fg, tr("Select all", "Hepsini seç")) { onSelect(sorted.map { it.id }.toSet()) }
                NIcon("pin", fg, tr("Pin", "Sabitle")) { onPinSelected() }
                NIcon("palette", fg, tr("Colour", "Renk")) { onDlg(NDlg.Swatch(selected)) }
                NIcon("folder", fg, tr("Folder", "Klasör")) { onDlg(NDlg.Folder(selected)) }
                NIcon("bell", fg, tr("Reminder", "Hatırlatma")) { onDlg(NDlg.Remind(selected)) }
                NIcon("delete", fg, tr("Move to trash", "Çöpe taşı")) { onTrash() }
            }
        }
        PField(query, onQuery, tr("Search", "Ara"), Modifier.fillMaxWidth())
        if (folders.isNotEmpty() || tags.isNotEmpty()) Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            folders.forEach { f -> PChip(folder == f, f) { onFolder(f) } }
            tags.forEach { t -> PChip(tag == t, "#$t") { onTag(t) } }
        }
        if (sorted.isEmpty()) Text(tr("No notes", "Not yok"), color = sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (grid) 2 else 1),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (grid) 8.dp else 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEach { (label, items) ->
                if (label.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(label, color = sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                }
                gridItems(items, key = { it.id }) { n ->
                    if (view == "book") NoteBookCard(n, n.id in selected,
                        onTap = { if (selected.isNotEmpty()) onSelect(if (n.id in selected) selected - n.id else selected + n.id) else onOpen(n.id) },
                        onLong = { onSelect(if (n.id in selected) selected - n.id else selected + n.id) })
                    else NoteCard(n, query, grid, showDates, n.id in selected,
                        onTap = { if (selected.isNotEmpty()) onSelect(if (n.id in selected) selected - n.id else selected + n.id) else onOpen(n.id) },
                        onLong = { onSelect(if (n.id in selected) selected - n.id else selected + n.id) },
                        onSwipe = { onSwipe(it) })
                }
            }
        }
    }
}

private fun bucket(t: Long): String {
    val day = 86_400_000L
    val now = java.util.Calendar.getInstance()
    now.set(java.util.Calendar.HOUR_OF_DAY, 0); now.set(java.util.Calendar.MINUTE, 0); now.set(java.util.Calendar.SECOND, 0); now.set(java.util.Calendar.MILLISECOND, 0)
    val start = now.timeInMillis
    return when {
        t >= start -> tr("Today", "Bugün")
        t >= start - day -> tr("Yesterday", "Dün")
        t >= start - 6 * day -> tr("This week", "Bu hafta")
        else -> tr("Earlier", "Daha önce")
    }
}

/** One note in the list or grid. Locked notes show only their name, and the search term is marked in bold. */
@Composable
private fun NoteCard(n: Note, query: String, grid: Boolean, showDates: Boolean, selected: Boolean, onTap: () -> Unit, onLong: () -> Unit, onSwipe: (String) -> Unit) {
    val ctx = LocalContext.current
    val fg = T.text
    val sub = T.sub
    val size = NotesPrefs.fontSize(ctx)
    val date = remember { SimpleDateFormat("d MMM HH:mm", Locale.getDefault()) }
    val hidden = n.locked
    val title = if (hidden) tr("Locked note", "Kilitli not") else NotesStore.titleOf(n).ifBlank { tr("Untitled", "Adsız") }
    // without a title, the first line is the title and the preview starts after it
    val lines = if (hidden) emptyList() else n.body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val preview = (if (n.title.isBlank()) lines.drop(1) else lines).take(2).joinToString("  ")
    val checks = if (hidden) null else Regex("\\[[ xX]]").findAll(n.body).toList().takeIf { it.isNotEmpty() }?.let { all -> all.count { it.value != "[ ]" } to all.size }
    val bg = when {
        selected -> T.accent.copy(alpha = .22f)
        grid -> T.panel
        else -> Color.Transparent
    }
    Row(
        Modifier.fillMaxWidth()
            .then(if (grid) Modifier.clip(RoundedCornerShape(12.dp)) else Modifier)
            .background(bg)
            .pointerInput(n, selected) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLong() }) }
            // a swipe to the left of more than 160 px moves the note to the trash
            .pointerInput(n.id) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -160f) onSwipe(n.id) },
                    onDragCancel = { total = 0f },
                    onHorizontalDrag = { _, dx -> total += dx },
                )
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        if (n.color != 0) Box(Modifier.width(4.dp).height(if (grid) 40.dp else 36.dp).clip(RoundedCornerShape(2.dp)).background(Color(n.color)))
        if (n.color != 0) Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(highlight(title, query, T.accent), color = fg, fontSize = (size + 1).sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (NotesPrefs.previews(ctx) && preview.isNotBlank()) Text(highlight(preview, query, T.accent), color = sub, fontSize = (size - 2).sp, maxLines = if (grid) 3 else 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (n.pinned) PIcon("pin", sub, 13.dp)
                if (n.remindAt > 0) PIcon("bell", sub, 13.dp)
                if (n.files.isNotEmpty()) PIcon("image", sub, 13.dp)
                checks?.let { (done, total) -> Text("$done/$total", color = sub, fontSize = 11.sp) }
                if (n.folder.isNotBlank()) Text(n.folder, color = sub, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showDates) Text(date.format(Date(n.updated)), color = sub, fontSize = 11.sp)
            }
        }
    }
}

/** The trash: restore a note, delete it for good, or empty the trash. Notes leave it by themselves after 30 days. */
@Composable
fun TrashView(notes: List<Note>, onBack: () -> Unit, onRestore: (String) -> Unit, onDrop: (String) -> Unit, onEmpty: () -> Unit) {
    val fg = T.text
    val sub = T.sub
    val now = System.currentTimeMillis()
    val trashed = notes.filter { it.trashedAt > 0 }.sortedByDescending { it.trashedAt }
    LazyColumn(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = pageGutter)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                NIcon("back", fg, tr("Back", "Geri")) { onBack() }
                Text(tr("Trash", "Çöp kutusu"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (trashed.isNotEmpty()) NIcon("recycle", fg, tr("Empty trash", "Çöpü boşalt")) { onEmpty() }
            }
        }
        if (trashed.isEmpty()) item { Text(tr("Trash is empty", "Çöp kutusu boş"), color = sub, fontSize = 14.sp, modifier = Modifier.padding(8.dp)) }
        items(trashed, key = { it.id }) { n ->
            val left = ((n.trashedAt + NotesStore.TRASH_DAYS * 86_400_000L - now) / 86_400_000L).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (n.locked) tr("Locked note", "Kilitli not") else NotesStore.titleOf(n).ifBlank { tr("Untitled", "Adsız") }, color = fg, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(tr("$left days left", "$left gün kaldı"), color = sub, fontSize = 12.sp)
                }
                NIcon("undo", fg, tr("Restore", "Geri yükle")) { onRestore(n.id) }
                NIcon("delete", fg, tr("Delete for good", "Kalıcı sil")) { onDrop(n.id) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
        }
    }
}

/** A titled block of settings that folds: the chevron turns down when open. */
@Composable
fun NSection(title: String, open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = T.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box(Modifier.graphicsLayer { rotationZ = if (open) 180f else 0f }) { PIcon("chevron", T.sub, 20.dp) }
        }
        if (open) content()
    }
}

/** One settings row: icon, title, a value or a control on the right. */
@Composable
fun NRow(icon: String, title: String, value: String = "", trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PIcon(icon, T.text, 22.dp)
        Spacer(Modifier.width(14.dp))
        Text(title, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = T.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

/** The settings screen: sorting and list, paper, security and data. Notes' own settings only. */
@Composable
fun SettingsView(
    notes: List<Note>, rev: Int, onBack: () -> Unit, onDlg: (NDlg) -> Unit, onChanged: () -> Unit, onStats: () -> Unit,
    onTrash: () -> Unit, onSetEncrypt: (Boolean) -> Unit, onExport: () -> Unit, onExportMd: () -> Unit, onImport: () -> Unit, onRemovePin: () -> Unit,
) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(setOf("list", "paper", "security", "data")) }
    fun toggle(k: String) { open = if (k in open) open - k else open + k }
    val paper = NotesPrefs.paper(ctx)
    val defColor = NotesPrefs.defaultColor(ctx)
    LazyColumn(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = pageGutter)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                NIcon("back", T.text, tr("Back", "Geri")) { onBack() }
                Text(tr("Settings", "Ayarlar"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
        item { ExtraLookRow() }
        item { NRow("view", tr("Transparent background", "Şeffaf arka plan"), trailing = { PSwitch(NotesPrefs.transparent(ctx), { NotesPrefs.setTransparent(ctx, it); com.umutk.pulse.PageBg.transparent = it; onChanged() }) }) }
        item {
            NSection(tr("List", "Liste"), "list" in open, { toggle("list") }) {
                NRow("sort", tr("Sort by", "Sırala"), tr(NotesPrefs.sort(ctx).en, NotesPrefs.sort(ctx).tr), onClick = { onDlg(NDlg.Sort) })
                // the three ways of showing the notes: a list, a grid of cards, or pages like a book
                listOf("list" to tr("List", "Liste"), "grid" to tr("Grid", "Izgara"), "book" to tr("Book", "Kitap")).forEach { (v, label) ->
                    NRow("view", label, trailing = { PRadio(NotesPrefs.view(ctx) == v, { NotesPrefs.setView(ctx, v); onChanged() }) })
                }
                NRow("add", tr("New from a template", "Şablondan yeni not"), onClick = { onDlg(NDlg.Template) })
                NRow("recycle", tr("Trash", "Çöp kutusu"), onClick = { onTrash() })
                NRow("sort", tr("Reverse order", "Ters sırala"), trailing = { PSwitch(NotesPrefs.reverse(ctx), { NotesPrefs.setReverse(ctx, it); onChanged() }) })
                NRow("calendar", tr("Group by date", "Tarihe göre grupla"), trailing = { PSwitch(NotesPrefs.groups(ctx), { NotesPrefs.setGroups(ctx, it); onChanged() }) })
                NRow("view", tr("Show previews", "Önizleme göster"), trailing = { PSwitch(NotesPrefs.previews(ctx), { NotesPrefs.setPreviews(ctx, it); onChanged() }) })
                NRow("calendar", tr("Show dates", "Tarihleri göster"), trailing = { PSwitch(NotesPrefs.dates(ctx), { NotesPrefs.setDates(ctx, it); onChanged() }) })
                NRow("edit", tr("Text size", "Yazı boyutu"), "${NotesPrefs.fontSize(ctx)} sp", onClick = { onDlg(NDlg.Font) })
                NRow("palette", tr("New note colour", "Yeni not rengi"), trailing = {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(if (defColor == 0) T.panel else Color(defColor)))
                }, onClick = { onDlg(NDlg.DefaultColor) })
            }
        }
        item {
            NSection(tr("Paper", "Kağıt"), "paper" in open, { toggle("paper") }) {
                NRow("palette", tr("Editor paper", "Düzenleyici kağıdı"), tr(paper.en, paper.tr), onClick = { onDlg(NDlg.Paper) })
            }
        }
        item {
            NSection(tr("Security", "Güvenlik"), "security" in open, { toggle("security") }) {
                NRow("lock", tr("Keep the screen private", "Ekranı gizli tut"), trailing = {
                    PSwitch(NotesPrefs.secure(ctx), { NotesPrefs.setSecure(ctx, it); onChanged() })
                })
                NRow("lock", tr("Encrypt the notes file", "Not dosyasını şifrele"), trailing = {
                    PSwitch(NotesPrefs.encrypted(ctx), { onSetEncrypt(it); onChanged() })
                })
                NRow("lock", tr("PIN for locked notes", "Kilitli notlar için PIN"), if (NotesPrefs.hasPin(ctx)) tr("Change", "Değiştir") else tr("Set", "Ayarla"), onClick = { onDlg(NDlg.SetPin(null)) })
                if (NotesPrefs.hasPin(ctx)) NRow("unlock", tr("Remove the PIN", "PIN'i kaldır"), onClick = { onRemovePin() })
            }
        }
        item {
            NSection(tr("Data", "Veri"), "data" in open, { toggle("data") }) {
                NRow("save", tr("Export as JSON", "JSON olarak dışa aktar"), onClick = { onExport() })
                NRow("save", tr("Export as Markdown", "Markdown olarak dışa aktar"), onClick = { onExportMd() })
                NRow("download", tr("Import notes", "Not içe aktar"), onClick = { onImport() })
                NRow("performance", tr("Statistics", "İstatistikler"), onClick = { onStats() })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Counts over all notes: how many, how big, and what they hold. */
@Composable
fun StatsView(notes: List<Note>, onBack: () -> Unit) {
    val live = notes.filter { it.trashedAt == 0L }
    val words = live.sumOf { n -> n.body.split(Regex("\\s+")).count { it.isNotBlank() } }
    val rows = listOf(
        tr("Notes", "Notlar") to live.size.toString(),
        tr("Pinned", "Sabitli") to live.count { it.pinned }.toString(),
        tr("Locked", "Kilitli") to live.count { it.locked }.toString(),
        tr("With a reminder", "Hatırlatmalı") to live.count { it.remindAt > 0 }.toString(),
        tr("Folders", "Klasörler") to live.map { it.folder }.filter { it.isNotBlank() }.distinct().size.toString(),
        tr("Tags", "Etiketler") to live.flatMap { NotesStore.tagsOf(it.body) }.distinct().size.toString(),
        tr("Words", "Kelimeler") to words.toString(),
        tr("Characters", "Karakterler") to live.sumOf { it.body.length }.toString(),
        tr("Photos", "Fotoğraflar") to live.sumOf { n -> n.files.count { it.startsWith("im_") } }.toString(),
        tr("Sketches", "Çizimler") to live.sumOf { n -> n.files.count { it.startsWith("sk_") } }.toString(),
        tr("Voice notes", "Ses notları") to live.sumOf { n -> n.files.count { it.startsWith("au_") } }.toString(),
        tr("In the trash", "Çöp kutusunda") to notes.count { it.trashedAt > 0 }.toString(),
    )
    LazyColumn(Modifier.fillMaxSize().background(com.umutk.pulse.pageBg).padding(horizontal = pageGutter)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                NIcon("back", T.text, tr("Back", "Geri")) { onBack() }
                Text(tr("Statistics", "İstatistikler"), color = T.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            }
        }
        items(rows) { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(label, color = T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(value, color = T.sub, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(T.line))
        }
    }
}

/**
 * The side panel of the notes: the groups of notes (all, favourites, locked, trash) with their icons, then the folders as a tree
 * (a folder named "Ders/Matematik" sits under "Ders"). Picking a row shows its notes; the panel closes.
 */
@Composable
private fun NotesSidePanel(
    all: Int, pinned: Int, locked: Int, trash: Int, folders: List<String>, counts: Map<String, Int>, current: String?,
    onAll: () -> Unit, onFolder: (String) -> Unit, onPinned: () -> Unit, onLocked: () -> Unit, onTrash: () -> Unit, onClose: () -> Unit,
    expanded: Set<String> = emptySet(), onToggle: (String) -> Unit = {},
) {
    val fg = T.text
    val sub = T.sub
    val bg = T.panel
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Row(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = .45f)).clickable(onClick = onClose)) {
            Column(
                Modifier.fillMaxHeight().width(290.dp).background(bg).padding(vertical = 16.dp).verticalScroll(rememberScrollState()).clickable(enabled = false) {},
            ) {
                Text(tr("Notes", "Notlar"), color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                NoteSideRow("document", tr("All notes", "Tüm notlar"), all, current == null, fg, sub, onAll)
                NoteSideRow("star", tr("Favourites", "Favoriler"), pinned, false, fg, sub, onPinned)
                NoteSideRow("lock", tr("Locked notes", "Kilitli notlar"), locked, false, fg, sub, onLocked)
                NoteSideRow("delete", tr("Trash", "Çöp kutusu"), trash, false, fg, sub, onTrash)
                Text(tr("Folders", "Klasörler"), color = sub, fontSize = 12.sp, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                // each folder with its depth as indentation, the sub folders right under their parent
                // a folder shows when all its parents are open; a folder with sub folders has an arrow that opens them (as in Windows)
                folders.sorted().filter { path -> path.split('/').dropLast(1).indices.all { k -> path.split('/').take(k + 1).joinToString("/") in expanded } }.forEach { path ->
                    val depth = path.count { it == '/' }
                    val hasKids = folders.any { it.startsWith("$path/") }
                    val open = path in expanded
                    NoteSideRow("folder", path.substringAfterLast('/'), counts[path] ?: 0, current == path, fg, sub,
                        onClick = { if (hasKids) onToggle(path) else onFolder(path) }, indent = depth * 14,
                        arrow = if (hasKids) open else null)
                }
                if (folders.isEmpty()) Text(tr("No folders yet. Move a note into one from its menu.", "Henüz klasör yok. Bir notu menüsünden klasöre taşıyın."), color = sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun NoteSideRow(icon: String, label: String, count: Int, selected: Boolean, fg: androidx.compose.ui.graphics.Color, sub: androidx.compose.ui.graphics.Color, onClick: () -> Unit, indent: Int = 0, arrow: Boolean? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp + indent.dp, end = 8.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) sub.copy(alpha = .18f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (arrow != null) PIcon("chevron", sub, 16.dp, Modifier.graphicsLayer { rotationZ = if (arrow) 0f else -90f })
        else Spacer(Modifier.width(16.dp))
        Spacer(Modifier.width(6.dp))
        PIcon(icon, if (selected) fg else sub, 20.dp)
        Spacer(Modifier.width(12.dp))
        Text(label, color = fg, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count.toString(), color = sub, fontSize = 13.sp)
    }
}

/** One note as a page: the title on top, the first lines of the text under it, the date at the foot. Locked notes show no text. */
@Composable
private fun NoteBookCard(n: Note, selected: Boolean, onTap: () -> Unit, onLong: () -> Unit) {
    val ctx = LocalContext.current
    val fg = T.text
    val sub = T.sub
    val date = remember { SimpleDateFormat("d MMM", Locale.getDefault()) }
    val hidden = n.locked
    val title = if (hidden) tr("Locked note", "Kilitli not") else NotesStore.titleOf(n).ifBlank { tr("Untitled", "Adsız") }
    val lines = if (hidden) emptyList() else n.body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    val text = (if (n.title.isBlank()) lines.drop(1) else lines).take(8).joinToString("\n")
    // a page in 3D: pressed, it leans towards the finger and lifts a little less; the spine on its left edge stays dark
    var pressed by remember(n.id) { mutableStateOf(false) }
    var tx by remember(n.id) { mutableFloatStateOf(0f) }
    var ty by remember(n.id) { mutableFloatStateOf(0f) }
    val rx by androidx.compose.animation.core.animateFloatAsState(if (pressed) ty * 9f else 0f, androidx.compose.animation.core.spring(dampingRatio = .6f, stiffness = 400f), label = "noteRx")
    val ry by androidx.compose.animation.core.animateFloatAsState(if (pressed) tx * -9f else 0f, androidx.compose.animation.core.spring(dampingRatio = .6f, stiffness = 400f), label = "noteRy")
    val lift by androidx.compose.animation.core.animateDpAsState(if (pressed) 2.dp else 8.dp, label = "noteLift")
    val dens = LocalDensity.current.density
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.72f)
            .graphicsLayer {
                rotationX = rx; rotationY = ry; cameraDistance = 14f * dens
                shadowElevation = lift.toPx(); shape = RoundedCornerShape(10.dp); clip = false
            }
            .background(if (selected) T.accent.copy(alpha = .22f) else T.panel, RoundedCornerShape(10.dp))
            .pointerInput(n, selected) {
                detectTapGestures(
                    onPress = { p ->
                        tx = (p.x / size.width) * 2f - 1f; ty = (p.y / size.height) * 2f - 1f
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onTap() }, onLongPress = { onLong() },
                )
            },
    ) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxSize().width(7.dp).background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Transparent))))
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
            Text(title, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Text(if (hidden) "" else text, color = sub, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 9, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(date.format(Date(n.updated)), color = sub, fontSize = 11.sp)
        }
    }
}
