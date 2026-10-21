package com.umutk.pulse

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/** Text-based files the Office page can create and edit. */
val OF_TEXT_EXTS = listOf("txt", "json", "lrc", "md", "csv", "xml", "html", "srt", "log", "ini", "yml")

fun ofIsText(f: File) = f.extension.lowercase() in OF_TEXT_EXTS || f.extension.lowercase() in setOf("kt", "java", "py", "sh", "properties", "yaml", "cfg", "gradle", "htm")

fun ofCreateText(ext: String, base: String): File {
    val dir = OfficePrefs.folder.apply { mkdirs() }
    val clean = base.trim().ifEmpty { "Note" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    var f = File(dir, "$clean.$ext"); var n = 2
    while (f.exists()) f = File(dir, "$clean ${n++}.$ext")
    f.writeText(when (ext) { "json" -> "{\n  \n}\n"; "lrc" -> "[ti:]\n[ar:]\n[00:00.00]"; "xml" -> "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"; "html" -> "<!DOCTYPE html>\n<html>\n<body>\n\n</body>\n</html>\n"; else -> "" })
    return f
}

fun ofOpenText(ctx: Context, f: File) {
    val i = Intent(ctx, OfficeTextActivity::class.java).putExtra("path", f.path)
    if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (e: Exception) { Toast.makeText(ctx, "Could not open: ${e.message ?: e.javaClass.simpleName}", Toast.LENGTH_LONG).show() }
}

private val NOTEPAD_BASES = setOf("95", "2003", "2013", "2017", "365")

/** A plain text editor for txt / json / lrc / md and other text-based files. The Windows looks draw it as Notepad. */
class OfficeTextActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Cfg.load(this); Opt.load(this); OfficePrefs.load(this)
        val f = intent.getStringExtra("path")?.let { File(it) }
        if (f == null || !f.exists()) { finish(); return }
        OfficePrefs.addRecent(this, Uri.fromFile(f).toString(), f.name, OfKind.WORD)
        val first = runCatching { f.readText() }.getOrDefault("")
        setContent {
            LookHost(transparent = false) {
                var text by remember { mutableStateOf(first) }
                var saved by remember { mutableStateOf(first) }
                var saveAs by remember { mutableStateOf(false) }
                var txtSave by remember { mutableStateOf(false) }
                fun save() { runCatching { f.writeText(text) }.onSuccess { saved = text; Toast.makeText(this, tr("Saved", "Kaydedildi"), Toast.LENGTH_SHORT).show() }.onFailure { Toast.makeText(this, "Could not save: ${it.message}", Toast.LENGTH_LONG).show() } }
                BackHandler { if (text != saved) save(); finish() }
                val base = ofResolveId("app").removeSuffix("d")
                if (base in NOTEPAD_BASES) NotepadWindow(
                    f.name, base, text, { text = it }, text != saved, { save() }, { saveAs = true },
                    if (!f.extension.equals("txt", true)) ({ txtSave = true }) else null, { if (text != saved) save(); finish() },
                ) else Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clickable { if (text != saved) save(); finish() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(22.dp), T.text) }
                        Text(f.name + if (text != saved) " •" else "", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        // Save first (it writes the open file), then Save as; "as TXT" only for files that are not .txt already, so no two buttons do the same
                        Box(Modifier.size(40.dp).clickable { save() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Save, null, Modifier.size(22.dp), Look.accent) }
                        Box(Modifier.size(40.dp).clickable { saveAs = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SaveAs, null, Modifier.size(22.dp), T.text) }
                        if (!f.extension.equals("txt", true)) Box(Modifier.size(40.dp).clickable { txtSave = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TextSnippet, null, Modifier.size(22.dp), T.text) }
                    }
                    BasicTextField(text, { text = it }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                        textStyle = TextStyle(color = T.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace), cursorBrush = SolidColor(Look.accent))
                }
                if (saveAs) OfSaveAsDialog(f.name, (listOf(f.extension.lowercase()) + OF_TEXT_EXTS + "tsv").filter { it.isNotEmpty() }.distinct(), f.parentFile, { saveAs = false }) { d ->
                    saveAs = false
                    runCatching { d.writeText(text) }.onSuccess { saved = text; ofOpenText(this@OfficeTextActivity, d); finish() }
                        .onFailure { Toast.makeText(this@OfficeTextActivity, "Could not save: ${it.message}", Toast.LENGTH_LONG).show() }
                }
                if (txtSave) OfSaveAsDialog(f.name, listOf("txt"), f.parentFile, { txtSave = false }, overwrite = true) { d ->
                    txtSave = false
                    runCatching { d.writeText(text) }.onSuccess { if (d == f) saved = text; Toast.makeText(this@OfficeTextActivity, tr("Saved as TXT: ${d.name}", "TXT olarak kaydedildi: ${d.name}"), Toast.LENGTH_SHORT).show() }
                        .onFailure { Toast.makeText(this@OfficeTextActivity, "Could not save: ${it.message}", Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
}

/** A menu bar word (File, Edit, ...) with its drop-down. */
@Composable
private fun NpMenu(label: String, ink: androidx.compose.ui.graphics.Color, items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(label, color = ink, fontSize = 13.sp, modifier = Modifier.clickable { open = true }.padding(horizontal = 9.dp, vertical = 5.dp))
        ThemedMenu(open, { open = false }) { items.forEach { (l, f) -> ThemedMenuItem(l, { open = false; f() }) } }
    }
}

/**
 * Notepad, in the look of the Windows theme: the 95 and XP window has a title bar and a menu bar and no status bar in 95;
 * 7, 10 and 11 have the flat title, the menu bar and the status bar with the line and column. File, Edit, Format, View and Help work.
 */
@Composable
private fun NotepadWindow(
    name: String, base: String, text: String, onText: (String) -> Unit, modified: Boolean,
    save: () -> Unit, saveAs: () -> Unit, saveTxt: (() -> Unit)?, exit: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pal = ofPal("app", OfKind.WORD)
    val classic = base == "95" || base == "2003"
    var tf by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(text)) }
    var wrap by remember { mutableStateOf(true) }
    var statusOn by remember { mutableStateOf(base != "95") }
    var size by remember { mutableIntStateOf(14) }
    val undo = remember { java.util.ArrayDeque<String>() }
    val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
    fun change(v: androidx.compose.ui.text.input.TextFieldValue) {
        if (v.text != tf.text) { undo.addLast(tf.text); if (undo.size > 100) undo.removeFirst() }
        tf = v; onText(v.text)
    }
    fun edit(newText: String, caret: Int) = change(androidx.compose.ui.text.input.TextFieldValue(newText, androidx.compose.ui.text.TextRange(caret.coerceIn(0, newText.length))))
    fun selected() = tf.text.substring(tf.selection.min, tf.selection.max)
    fun copy() { if (!tf.selection.collapsed) cm?.setPrimaryClip(android.content.ClipData.newPlainText("", selected())) }
    fun delete() { val a = tf.selection.min; edit(tf.text.removeRange(a, tf.selection.max), a) }
    val fileItems = buildList {
        add(tr("Save", "Kaydet") to save); add(tr("Save As…", "Farklı Kaydet…") to saveAs)
        saveTxt?.let { add(tr("Save as TXT", "TXT olarak kaydet") to it) }
        add(tr("Exit", "Çıkış") to exit)
    }
    val editItems = listOf(
        tr("Undo", "Geri Al") to { if (undo.isNotEmpty()) { val t = undo.removeLast(); tf = androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(t.length)); onText(t) } },
        tr("Cut", "Kes") to { copy(); if (!tf.selection.collapsed) delete() },
        tr("Copy", "Kopyala") to { copy() },
        tr("Paste", "Yapıştır") to {
            val t = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
            if (t.isNotEmpty()) edit(tf.text.replaceRange(tf.selection.min, tf.selection.max, t), tf.selection.min + t.length)
        },
        tr("Delete", "Sil") to { if (!tf.selection.collapsed) delete() },
        tr("Select All", "Tümünü Seç") to { tf = tf.copy(selection = androidx.compose.ui.text.TextRange(0, tf.text.length)) },
        tr("Time/Date", "Saat/Tarih") to {
            val t = java.text.SimpleDateFormat("HH:mm dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date())
            edit(tf.text.replaceRange(tf.selection.min, tf.selection.max, t), tf.selection.min + t.length)
        },
    )
    val formatItems = listOf(
        (if (wrap) "✓ " else "") + tr("Word Wrap", "Sözcük Kaydır") to { wrap = !wrap },
        tr("Larger font", "Yazıyı büyüt") to { size = (size + 2).coerceAtMost(32) },
        tr("Smaller font", "Yazıyı küçült") to { size = (size - 2).coerceAtLeast(8) },
    )
    val viewItems = listOf((if (statusOn) "✓ " else "") + tr("Status Bar", "Durum Çubuğu") to { statusOn = !statusOn })
    val helpItems = listOf(tr("About Notepad", "Not Defteri Hakkında") to { Toast.makeText(ctx, "Notepad — Pulse", Toast.LENGTH_SHORT).show() })
    val band = when (base) {
        "95" -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF000080), androidx.compose.ui.graphics.Color(0xFF1084D0)))
        "2003" -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0058EE), androidx.compose.ui.graphics.Color(0xFF3A93FF)))
        else -> androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(pal.titleBg, pal.titleBg))
    }
    val paperLight = pal.paper.luminance() > .5f
    // Windows 11's Notepad is the system grey, not the white Office paper
    val win11 = T.id == "win11"
    val paper = if (win11) (if (paperLight) androidx.compose.ui.graphics.Color(0xFFF3F3F3) else androidx.compose.ui.graphics.Color(0xFF202020)) else pal.paper
    val titleInk = if (win11) pal.ink else pal.titleInk
    Column(Modifier.fillMaxSize().background(if (classic) pal.ribbonBg else paper).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(if (classic) 28.dp else 34.dp).background(if (win11) androidx.compose.ui.graphics.SolidColor(paper) else band).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon(if (T.id == "win11") "notepad" else "document", titleInk, 18.dp)
            Spacer(Modifier.width(6.dp))
            Text((if (modified) "*" else "") + name + " - Notepad", color = titleInk, fontSize = 13.sp, fontWeight = if (classic) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f), maxLines = 1)
            Box(
                Modifier.size(22.dp).then(if (base == "95") Modifier.background(androidx.compose.ui.graphics.Color(0xFFC0C0C0)).frame(T, raised = true) else if (base == "2003") Modifier.background(androidx.compose.ui.graphics.Color(0xFFD9543C)) else Modifier)
                    .clickable { exit() },
                contentAlignment = Alignment.Center,
            ) { PIcon("close", if (base == "95") androidx.compose.ui.graphics.Color.Black else titleInk, 14.dp) }
        }
        Row(Modifier.fillMaxWidth().background(if (classic) pal.ribbonBg else paper).padding(horizontal = 2.dp)) {
            NpMenu(tr("File", "Dosya"), pal.ink, fileItems)
            NpMenu(tr("Edit", "Düzen"), pal.ink, editItems)
            NpMenu(tr("Format", "Biçim"), pal.ink, formatItems)
            NpMenu(tr("View", "Görünüm"), pal.ink, viewItems)
            NpMenu(tr("Help", "Yardım"), pal.ink, helpItems)
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(paper).then(if (classic) Modifier.frame(T) else Modifier)) {
            BasicTextField(
                tf, { change(it) },
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState())).padding(4.dp),
                textStyle = TextStyle(color = if (paperLight) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White, fontSize = size.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(if (paperLight) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White),
            )
        }
        if (statusOn) {
            val before = tf.text.substring(0, tf.selection.start.coerceIn(0, tf.text.length))
            val ln = before.count { it == '\n' } + 1
            val col = before.length - (before.lastIndexOf('\n') + 1) + 1
            Row(Modifier.fillMaxWidth().background(if (win11) paper else pal.statusBg).padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Ln $ln, Col $col", "Satır $ln, Sütun $col"), color = if (win11) pal.ink else pal.statusInk, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("${tf.text.length} " + tr("chars", "karakter") + "   100%   UTF-8", color = if (win11) pal.ink else pal.statusInk, fontSize = 12.sp)
            }
        }
    }
}
