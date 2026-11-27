package com.umutk.pulse.extras.code

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.launch
import java.io.File

/** Where the project of the code editor is kept (a folder picked, or a zip opened into Documents/Pulse/Code/projects). */
object CodeProject {
    fun get(c: android.content.Context): File? = c.getSharedPreferences("pulse_code", 0).getString("project", null)?.let { File(it) }?.takeIf { it.isDirectory }
    fun set(c: android.content.Context, f: File) { c.getSharedPreferences("pulse_code", 0).edit().putString("project", f.path).apply() }

    /** A folder picked with the system's folder picker, as a path (the phone's own storage and SD cards). */
    fun treePath(u: android.net.Uri): File? = runCatching {
        val id = android.provider.DocumentsContract.getTreeDocumentId(u)
        val vol = id.substringBefore(':'); val rel = id.substringAfter(':', "")
        val base = if (vol.equals("primary", true)) android.os.Environment.getExternalStorageDirectory().path else "/storage/$vol"
        File(if (rel.isEmpty()) base else "$base/$rel")
    }.getOrNull()?.takeIf { it.isDirectory }

    /** Opens a zip into a project folder of its own (its name; a number added when the name is taken) and returns it. */
    fun unzip(c: android.content.Context, u: android.net.Uri): File? = runCatching {
        val name = (c.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { k -> if (k.moveToFirst()) k.getString(0) else null } ?: "project.zip").substringBeforeLast('.')
        val base = File(android.os.Environment.getExternalStorageDirectory(), "Documents/Pulse/Code/projects").apply { mkdirs() }
        var out = File(base, name); var n = 2
        while (out.exists()) { out = File(base, "$name $n"); n++ }
        out.mkdirs()
        val root = out.canonicalPath
        c.contentResolver.openInputStream(u)!!.use { ins ->
            java.util.zip.ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val f = File(out, e.name)
                    // nothing is written outside the project folder (a zip with ../ in its names)
                    if (f.canonicalPath.startsWith(root)) { if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { z.copyTo(it) } } }
                    e = z.nextEntry
                }
            }
        }
        // a zip with one folder inside: that folder is the project
        out.listFiles()?.singleOrNull()?.takeIf { it.isDirectory } ?: out
    }.getOrNull()
}

/**
 * The project tree (as an editor on a computer): the project folder, its folders open and close in place, a tap on a file opens it; a folder or a zip
 * can be opened as the project. Hidden files and build folders are left out.
 */
@Composable
fun ProjectTree(root: File?, current: String, onPick: (File) -> Unit, onRoot: (File) -> Unit, onClose: (() -> Unit)?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val expanded = remember(root) { androidx.compose.runtime.mutableStateListOf<String>() }
    // the open file's folders are open at first, so it is in sight
    androidx.compose.runtime.LaunchedEffect(root, current) {
        val r = root?.path ?: return@LaunchedEffect
        var p = File(current).parentFile
        while (p != null && p.path.startsWith(r) && p.path != r) { if (p.path !in expanded) expanded.add(p.path); p = p.parentFile }
    }
    val pickDir = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { u -> u?.let { CodeProject.treePath(it) }?.let { onRoot(it) } }
    val pickZip = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) scope.launch { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CodeProject.unzip(ctx, u) }?.let { onRoot(it) } }
    }
    Column(modifier.background(Color.Black).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PIcon("folder", Look.accent, 22.dp)
            Text(root?.name ?: tr("Project", "Proje"), color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
            // a folder as the project, a zip opened as the project, one folder up
            Box(Modifier.size(38.dp).clickable { runCatching { pickDir.launch(null) } }, contentAlignment = Alignment.Center) { PIcon("newfolder", T.text, 20.dp) }
            Box(Modifier.size(38.dp).clickable { runCatching { pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) } }, contentAlignment = Alignment.Center) { PIcon("archive", T.text, 20.dp) }
            root?.parentFile?.takeIf { it.canRead() }?.let { up -> Box(Modifier.size(38.dp).clickable { onRoot(up) }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.graphicsLayer { rotationZ = -90f }) } }
            if (onClose != null) Box(Modifier.size(38.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 20.dp) }
        }
        if (root == null) Text(tr("No folder", "Klasör yok"), color = T.sub, modifier = Modifier.padding(16.dp))
        else {
            val rows = remember(root, expanded.toList()) { tree(root, expanded.toSet()) }
            LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                items(rows, key = { it.second.path }) { (depth, f) ->
                    val dir = f.isDirectory
                    val on = f.path == current
                    Row(Modifier.width(560.dp).background(if (on) Look.accent.copy(alpha = .18f) else Color.Transparent).clickable {
                        if (dir) { if (f.path in expanded) expanded.remove(f.path) else expanded.add(f.path) } else onPick(f)
                    }.padding(start = (10 + depth * 14).dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (dir) PIcon("chevron", T.sub, 14.dp, Modifier.graphicsLayer { rotationZ = if (f.path in expanded) 90f else 0f }) else Spacer(Modifier.width(14.dp))
                        Spacer(Modifier.width(6.dp))
                        PIcon(if (dir) "folder" else "filetypes", if (dir) Look.accent else T.sub, 18.dp)
                        Text(f.name, color = if (on) Look.accent else T.text, fontSize = 14.sp, fontFamily = if (dir) null else FontFamily.Monospace, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** The project tree over the editor on the whole left side (upright); sideways it stays beside the editor instead (see CodeEditorHost). */
@Composable
fun ProjectPanel(open: Boolean, root: File?, current: String, onPick: (File) -> Unit, onRoot: (File) -> Unit, onClose: () -> Unit) {
    if (open) BackHandler { onClose() }
    Box(Modifier.fillMaxSize()) {
        if (open) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).pointerInput(Unit) { detectTapGestures { onClose() } })
        AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            ProjectTree(root, current, onPick, onRoot, onClose, Modifier.fillMaxHeight().fillMaxWidth(.84f))
        }
    }
}

private val SKIP = setOf("build", "node_modules", ".git", ".gradle", ".idea", "__pycache__", "target", ".dart_tool")

/** The rows of the tree: each entry with its depth, the open folders' entries under them; folders first. */
private fun tree(root: File, open: Set<String>, depth: Int = 0, out: MutableList<Pair<Int, File>> = ArrayList()): List<Pair<Int, File>> {
    val list = (root.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") && it.name !in SKIP }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    for (f in list) {
        if (out.size > 3000) break
        out += depth to f
        if (f.isDirectory && f.path in open) tree(f, open, depth + 1, out)
    }
    return out
}

/**
 * Pulse AI (the model on the phone) in the code editor: say what to change; it gets the selection (or the whole file) and its answer replaces it once
 * Apply is pressed (Undo takes it back).
 */
@Composable
fun CodeAiBar(h: CodeHandle, langName: String?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var ask by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf("") }
    // what the answer replaces: the selection when there is one, else the whole file
    var range by remember { mutableStateOf<IntArray?>(null) }
    // a long file: the model sees the part the request is about and answers with search / replace blocks, applied where they match
    var blocks by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    fun run() {
        val ed = h.ed ?: return
        if (ask.isBlank() || busy) return
        val c = ed.cursor
        val sel = c.isSelected
        val r = if (sel) intArrayOf(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn) else intArrayOf(0, 0, ed.text.lineCount - 1, ed.text.getColumnCount(ed.text.lineCount - 1))
        val code = if (sel) ed.text.subContent(r[0], r[1], r[2], r[3]).toString() else ed.text.toString()
        val long = code.length > 7000
        range = r; busy = true; err = ""; answer = null; blocks = emptyList()
        scope.launch {
            val lang = langName?.let { "$it " } ?: ""
            val out = if (!long) {
                val sys = "You are a code editor. You get ${lang}code and an instruction. Apply the instruction to the code. Reply with the complete changed code only, no explanation, no markdown fences."
                runCatching { com.umutk.pulse.extras.ai.AiEngine.once(ctx, sys, "Instruction: $ask\n\nCode:\n$code", (code.length / 2 + 512).coerceAtMost(4096)) }.getOrNull()
            } else {
                val part = CodeAi.focus(code, ask, 6000)
                val sys = "You are a code editor. You get part of a ${lang}file and an instruction. Reply only with edit blocks, each exactly:\n<<<<<<< SEARCH\n(lines copied exactly from the code)\n=======\n(the new lines)\n>>>>>>> REPLACE\nNo other text."
                runCatching { com.umutk.pulse.extras.ai.AiEngine.once(ctx, sys, "Instruction: $ask\n\nCode:\n$part", 2048) }.getOrNull()
            }
            busy = false
            if (out == null) { err = tr("Pulse AI is not ready: pick a model in Pulse AI > Models", "Pulse AI hazır değil: Pulse AI > Modeller'den bir model seç"); return@launch }
            if (long) {
                val b = CodeAi.blocks(out).filter { (f, _) -> f.isNotBlank() && code.contains(f) }
                if (b.isEmpty()) err = tr("Pulse AI's answer did not match the code; try saying it another way", "Pulse AI'ın cevabı koda uymadı; başka türlü söylemeyi dene")
                else { blocks = b; answer = b.joinToString("\n\n") { (f, t) -> "- " + f.lines().joinToString("\n- ") + "\n+ " + t.lines().joinToString("\n+ ") } }
            } else answer = CodeAi.unfence(out)
        }
    }
    Column(Modifier.fillMaxWidth().background(Color(0xFF0B0B0B)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Icon(Icons.Outlined.AutoAwesome, null, tint = Look.accent, modifier = Modifier.size(20.dp))
            PField(ask, { ask = it }, tr("Ask Pulse AI to change the code", "Pulse AI'dan kodu değiştirmesini iste"), Modifier.weight(1f).padding(horizontal = 8.dp))
            Box(Modifier.size(40.dp).clickable(enabled = !busy) { run() }, contentAlignment = Alignment.Center) { PIcon(if (busy) "snail" else "send", if (busy) T.sub else Look.accent, 20.dp) }
            Box(Modifier.size(36.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("close", T.sub, 18.dp) }
        }
        if (busy) Text(tr("Thinking…", "Düşünüyor…"), color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(start = 30.dp, top = 2.dp))
        if (err.isNotEmpty()) Text(err, color = Color(0xFFFF6B6B), fontSize = 12.sp, modifier = Modifier.padding(start = 30.dp, top = 2.dp))
        answer?.let { a ->
            Column(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF141414)).padding(8.dp)) {
                Text(a, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Apply", "Uygula"), compact = true) {
                        val ed = h.ed; val r = range
                        if (ed != null && r != null) runCatching {
                            if (blocks.isEmpty()) ed.text.replace(r[0], r[1], r[2], r[3], a)
                            else {
                                // each block where its lines are in the file (the cursor of the text finds the line and column of the place)
                                for ((f, t) in blocks) {
                                    val all = ed.text.toString(); val at = all.indexOf(f); if (at < 0) continue
                                    val s0 = ed.text.indexer.getCharPosition(at); val e0 = ed.text.indexer.getCharPosition(at + f.length)
                                    ed.text.replace(s0.line, s0.column, e0.line, e0.column, t)
                                }
                            }
                        }
                        blocks = emptyList()
                        answer = null; ask = ""
                    }
                    PButton(tr("Discard", "At"), compact = true) { answer = null }
                }
            }
        }
    }
}

/** The helpers of Pulse AI's code edits. */
object CodeAi {
    /** The part of a long [code] the request is about: the lines with the most words of [ask] and those around them, [max] characters at most. */
    fun focus(code: String, ask: String, max: Int): String {
        val lines = code.lines()
        val words = ask.lowercase().split(Regex("[^\\p{L}\\p{N}_]+")).filter { it.length >= 3 }.toSet()
        val score = lines.map { l -> val low = l.lowercase(); words.count { low.contains(it) } }
        val best = score.indices.maxByOrNull { score[it] } ?: 0
        var a = best; var b = best; var len = lines[best].length
        while (len < max && (a > 0 || b < lines.lastIndex)) {
            if (a > 0) { a--; len += lines[a].length + 1 }
            if (b < lines.lastIndex && len < max) { b++; len += lines[b].length + 1 }
        }
        return lines.subList(a, b + 1).joinToString("\n")
    }

    /** The SEARCH / REPLACE blocks of an answer. */
    fun blocks(out: String): List<Pair<String, String>> =
        Regex("<<<<<<<\\s*SEARCH\\s*\\n([\\s\\S]*?)\\n=======\\s*\\n([\\s\\S]*?)\\n?>>>>>>>\\s*REPLACE").findAll(out).map { it.groupValues[1] to it.groupValues[2] }.toList()

    /** The code of an answer without markdown fences around it. */
    fun unfence(out: String): String {
        val t = out.trim()
        if (!t.startsWith("```")) return t.trimEnd()
        return t.substringAfter('\n', "").substringBeforeLast("```").trimEnd()
    }
}
