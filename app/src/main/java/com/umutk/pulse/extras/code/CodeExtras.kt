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

/**
 * The project panel of the code editor (as an editor on a computer): the whole left side, the folder of the open file as the project, its folders open and
 * close in place, a tap on a file opens it. Hidden files and build folders are left out.
 */
@Composable
fun ProjectPanel(open: Boolean, root: File?, current: String, onPick: (File) -> Unit, onClose: () -> Unit) {
    if (open) BackHandler { onClose() }
    var top by remember(root) { mutableStateOf(root) }
    val expanded = remember(top) { androidx.compose.runtime.mutableStateListOf<String>() }
    Box(Modifier.fillMaxSize()) {
        if (open) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)).pointerInput(Unit) { detectTapGestures { onClose() } })
        AnimatedVisibility(open, enter = slideInHorizontally { -it }, exit = slideOutHorizontally { -it }) {
            Column(Modifier.fillMaxHeight().fillMaxWidth(.84f).background(Color.Black).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon("folder", Look.accent, 22.dp)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(tr("Project", "Proje"), color = T.sub, fontSize = 11.sp)
                        Text(top?.name ?: "—", color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    // one folder up: the project becomes the folder above
                    top?.parentFile?.takeIf { it.canRead() }?.let { up -> Box(Modifier.size(40.dp).clickable { top = up }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.graphicsLayer { rotationZ = -90f }) } }
                    Box(Modifier.size(40.dp).clickable { onClose() }, contentAlignment = Alignment.Center) { PIcon("close", T.text, 20.dp) }
                }
                val t = top
                if (t == null) Text(tr("No folder", "Klasör yok"), color = T.sub, modifier = Modifier.padding(16.dp))
                else {
                    val rows = remember(t, expanded.toList()) { tree(t, expanded.toSet()) }
                    LazyColumn(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
                        items(rows, key = { it.second.path }) { (depth, f) ->
                            val dir = f.isDirectory
                            val on = f.path == current
                            Row(Modifier.width(560.dp).background(if (on) Look.accent.copy(alpha = .18f) else Color.Transparent).clickable {
                                if (dir) { if (f.path in expanded) expanded.remove(f.path) else expanded.add(f.path) } else onPick(f)
                            }.padding(start = (12 + depth * 16).dp, top = 7.dp, bottom = 7.dp), verticalAlignment = Alignment.CenterVertically) {
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
    fun run() {
        val ed = h.ed ?: return
        if (ask.isBlank() || busy) return
        val c = ed.cursor
        val sel = c.isSelected
        val r = if (sel) intArrayOf(c.leftLine, c.leftColumn, c.rightLine, c.rightColumn) else intArrayOf(0, 0, ed.text.lineCount - 1, ed.text.getColumnCount(ed.text.lineCount - 1))
        val code = if (sel) ed.text.subContent(r[0], r[1], r[2], r[3]).toString() else ed.text.toString()
        if (code.length > 12_000) { err = tr("Too long: select the part to change", "Çok uzun: değişecek kısmı seç"); return }
        range = r; busy = true; err = ""; answer = null
        scope.launch {
            val sys = "You are a code editor. You get " + (langName?.let { "$it " } ?: "") + "code and an instruction. Apply the instruction to the code. " +
                "Reply with the complete changed code only, no explanation, no markdown fences."
            val out = runCatching { com.umutk.pulse.extras.ai.AiEngine.once(ctx, sys, "Instruction: $ask\n\nCode:\n$code", (code.length / 2 + 512).coerceAtMost(4096)) }.getOrNull()
            busy = false
            if (out == null) err = tr("Pulse AI is not ready: pick a model in Pulse AI > Models", "Pulse AI hazır değil: Pulse AI > Modeller'den bir model seç")
            else answer = out.removePrefix("```").substringAfter('\n', out).let { if (out.startsWith("```")) it.substringBeforeLast("```") else out }.trimEnd()
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
                        if (ed != null && r != null) runCatching { ed.text.replace(r[0], r[1], r[2], r[3], a) }
                        answer = null; ask = ""
                    }
                    PButton(tr("Discard", "At"), compact = true) { answer = null }
                }
            }
        }
    }
}
