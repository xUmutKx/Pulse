package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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

/** Long press on a row (position kept for the menu) and tap. */
fun Modifier.fileRow(onTap: () -> Unit, onLong: () -> Unit): Modifier = composed2 { 
    var origin by remember { mutableStateOf(Offset.Zero) }
    this.onGloballyPositioned { origin = it.positionInRoot() }.pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() }, onLongPress = { LauncherSession.pressAt = origin + it; onLong() })
    }
}

private fun Modifier.composed2(f: @Composable Modifier.() -> Modifier): Modifier = androidx.compose.ui.composed { f() }

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
    var text by remember { mutableStateOf("") }
    if (target != null) ContextPopup(220.dp, 330.dp, onClose) {
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
    }
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
