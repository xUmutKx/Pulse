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

/** A plain text editor for txt / json / lrc / md and other text-based files. */
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
                Column(Modifier.fillMaxSize().background(T.bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(40.dp).clickable { if (text != saved) save(); finish() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, null, Modifier.size(22.dp), T.text) }
                        Text(f.name + if (text != saved) " •" else "", color = T.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                        // Save first (it writes the open file), then Save as; "as TXT" only for files that are not .txt already, so no two buttons do the same
                        Box(Modifier.size(40.dp).clickable { save() }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Save, null, Modifier.size(22.dp), Look.accent) }
                        Box(Modifier.size(40.dp).clickable { saveAs = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.SaveAs, null, Modifier.size(22.dp), T.text) }
                        if (!f.extension.equals("txt", true)) Box(Modifier.size(40.dp).clickable { txtSave = true }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.TextSnippet, null, Modifier.size(22.dp), T.text) }
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
                    BasicTextField(text, { text = it }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
                        textStyle = TextStyle(color = T.text, fontSize = 14.sp, fontFamily = FontFamily.Monospace), cursorBrush = SolidColor(Look.accent))
                }
            }
        }
    }
}
