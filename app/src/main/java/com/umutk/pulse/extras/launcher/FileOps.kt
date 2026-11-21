package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.umutk.pulse.tr
import java.io.File

/** What was copied or cut in any file window of the desktop: another window can paste it, so files move between windows. */
object FileClip {
    var files by mutableStateOf<List<File>>(emptyList())
    var cut by mutableStateOf(false)
}

private fun freeName(dir: File, name: String): File {
    var n = File(dir, name); var i = 2
    val base = name.substringBeforeLast('.', name); val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
    while (n.exists()) { n = File(dir, "$base ($i)$ext"); i++ }
    return n
}

/** Pastes the clipboard into [dir]; a cut moves, a copy copies. Returns the last name put there. */
fun pasteInto(dir: File): String? {
    var last: String? = null
    FileClip.files.forEach { s ->
        if (!s.exists() || (s.isDirectory && dir.path.startsWith(s.path))) return@forEach
        val d = if (FileClip.cut && s.parentFile?.path == dir.path) s else freeName(dir, s.name)
        if (d.path == s.path) return@forEach
        runCatching {
            if (FileClip.cut) { if (!s.renameTo(d)) { s.copyRecursively(d, false); s.deleteRecursively() } } else s.copyRecursively(d, false)
            last = d.path
        }
    }
    if (FileClip.cut) FileClip.files = emptyList()
    return last
}

/** A right click of a mouse (or the secondary button of a trackpad) does what a long press does: [onRight] runs with the position kept for the menu. */
internal fun Modifier.rightClick(origin: () -> Offset, onRight: () -> Unit): Modifier = pointerInput(Unit) {
    androidx.compose.foundation.gestures.awaitEachGesture {
        val d = awaitFirstDown(requireUnconsumed = false)
        if (d.type == androidx.compose.ui.input.pointer.PointerType.Mouse && currentEvent.buttons.isSecondaryPressed) {
            d.consume(); LauncherSession.pressAt = origin() + d.position; onRight()
            androidx.compose.foundation.gestures.waitForUpOrCancellation()
        }
    }
}

/** Long press on a row (position kept for the menu), a tap, and (when [onDouble] is given) a double tap, as a file window of Windows: one tap picks, two open. */
fun Modifier.fileRow(onTap: () -> Unit, onLong: () -> Unit, onDouble: (() -> Unit)? = null): Modifier = composed {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    val long by androidx.compose.runtime.rememberUpdatedState(onLong)
    val dbl by androidx.compose.runtime.rememberUpdatedState(onDouble)
    onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(onDouble != null) {
        detectTapGestures(onTap = { tap() }, onDoubleTap = if (onDouble != null) ({ _ -> dbl?.invoke() }) else null, onLongPress = { LauncherSession.pressAt = origin + it; long() })
    }.rightClick({ origin }) { long() }
}

/** A physical keyboard in a file window: Ctrl+C / X / V, Delete, F2, Enter, Backspace and Alt+Left. */
fun Modifier.fileKeys(copy: () -> Unit, cut: () -> Unit, paste: () -> Unit, delete: () -> Unit, rename: () -> Unit, open: () -> Unit, back: () -> Unit): Modifier =
    onPreviewKeyEvent { e ->
        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when {
            e.isCtrlPressed && e.key == Key.C -> { copy(); true }
            e.isCtrlPressed && e.key == Key.X -> { cut(); true }
            e.isCtrlPressed && e.key == Key.V -> { paste(); true }
            e.key == Key.Delete -> { delete(); true }
            e.key == Key.F2 -> { rename(); true }
            e.key == Key.Enter || e.key == Key.NumPadEnter -> { open(); true }
            e.key == Key.Backspace || (e.isAltPressed && e.key == Key.DirectionLeft) -> { back(); true }
            else -> false
        }
    }

/** The long-press menu of a file or folder, and the rename and delete questions. [changed] gets the path to select (or null) after a change. */
@Composable
fun FileMenu(target: File?, dir: File, onClose: () -> Unit, open: (File) -> Unit, changed: (String?) -> Unit) {
    val ctx = LocalContext.current
    var rename by remember { mutableStateOf<File?>(null) }
    var ask by remember { mutableStateOf<File?>(null) }
    var props by remember { mutableStateOf<File?>(null) }
    var text by remember { mutableStateOf("") }
    if (target != null) ContextPopup(220.dp, 370.dp, onClose) {
        val ink = LocalMenuSkin.current?.text ?: Color(0xFF1E1E1E)
        ActionRow("open", tr("Open", "Aç"), ink) { onClose(); open(target) }
        ActionRow("copy", tr("Copy", "Kopyala"), ink) { FileClip.files = listOf(target); FileClip.cut = false; onClose() }
        ActionRow("cut", tr("Cut", "Kes"), ink) { FileClip.files = listOf(target); FileClip.cut = true; onClose() }
        if (FileClip.files.isNotEmpty()) ActionRow("paste", tr("Paste", "Yapıştır"), ink) {
            onClose(); changed(pasteInto(if (target.isDirectory) target else dir))
        }
        ActionRow("pin", tr("Add to desktop", "Masaüstüne ekle"), ink) {
            val l = "file:" + target.path
            if (l !in LauncherPrefs.links) { LauncherPrefs.links = LauncherPrefs.links + l; LauncherPrefs.save(ctx) }
            onClose()
        }
        ActionRow("edit", tr("Rename", "Yeniden adlandır"), ink) { text = target.name; rename = target; onClose() }
        ActionRow("delete", tr("Delete", "Sil"), ink) { ask = target; onClose() }
        ActionRow("info", tr("Properties", "Özellikler"), ink) { props = target; onClose() }
    }
    props?.let { f -> FilePropertiesDialog(f, { props = null }) { changed(it) } }
    rename?.let { f ->
        AlertDialog(onDismissRequest = { rename = null }, title = { Text(tr("Rename", "Yeniden adlandır")) },
            text = { BasicTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton({
                val n = File(f.parentFile, text.trim()); rename = null
                if (text.isNotBlank() && !n.exists() && f.renameTo(n)) changed(n.path)
            }) { Text("OK") } },
            dismissButton = { TextButton({ rename = null }) { Text(tr("Cancel", "İptal")) } })
    }
    ask?.let { f ->
        AlertDialog(onDismissRequest = { ask = null }, title = { Text(tr("Delete", "Sil")) }, text = { Text(f.name) },
            confirmButton = { TextButton({ ask = null; f.deleteRecursively(); changed(null) }) { Text(tr("Delete", "Sil")) } },
            dismissButton = { TextButton({ ask = null }) { Text(tr("Cancel", "İptal")) } })
    }
}


/**
 * Search of a file window: every file and folder under [top], at any depth, whose name has [q] in it (breadth first, so the near ones come first; it stops at [limit] hits or after
 * looking into 20 000 folders). Folders first, then by name. Not on the main thread.
 */
fun searchTree(top: File, q: String, hidden: Boolean, limit: Int = 400): List<File> {
    val out = ArrayList<File>()
    val todo = ArrayDeque<File>().apply { add(top) }
    var looked = 0
    while (todo.isNotEmpty() && out.size < limit && looked < 20_000) {
        val d = todo.removeFirst(); looked++
        d.listFiles()?.forEach { f ->
            if (!hidden && f.name.startsWith(".")) return@forEach
            if (f.name.contains(q, ignoreCase = true)) out += f
            if (f.isDirectory) todo.addLast(f)
        }
    }
    return out.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
}

/** The search of a window: null while it is still looking (or when nothing is asked), else what it found. The query is waited on a moment, so typing does not start a search for every letter. */
@Composable
fun rememberTreeSearch(top: File, query: String, hidden: Boolean): State<List<File>?> {
    val q = query.trim()
    return androidx.compose.runtime.produceState<List<File>?>(null, top.path, q, hidden) {
        value = null
        if (q.length < 2) return@produceState
        kotlinx.coroutines.delay(350)
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { searchTree(top, q, hidden) }.getOrDefault(emptyList()) }
    }
}
