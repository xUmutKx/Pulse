package com.umutk.pulse.extras.code

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.umutk.pulse.Look
import com.umutk.pulse.PButton
import com.umutk.pulse.PChip
import com.umutk.pulse.PField
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the toolbar needs of an editor on screen: the view, the folds, and the state of undo, redo and unsaved changes. */
class CodeHandle {
    var ed: CodeEditor? = null
    val folds = PulseCode.Folds()
    var canUndo by mutableStateOf(false)
    var canRedo by mutableStateOf(false)
    var dirty by mutableStateOf(false)
    var lines by mutableStateOf(0)
    /** Our own edits (folding) are not the user's: they do not count as changes. */
    var suppress = false
    var sizeSet = 0f
    fun sync() { ed?.let { canUndo = it.canUndo(); canRedo = it.canRedo(); lines = it.text.lineCount } }
    fun quiet(block: () -> Unit) { suppress = true; try { block() } finally { suppress = false }; sync() }
}

/** The editor view itself: Sora Editor with the TextMate grammar of [lang], in the colours of the look. */
@Composable
fun CodePane(h: CodeHandle, text: String, lang: PulseCode.Lang?, editable: Boolean, wrap: Boolean, textSp: Float, modifier: Modifier = Modifier, bgOverride: androidx.compose.ui.graphics.Color? = null, onText: ((String) -> Unit)? = null) {
    val ground = bgOverride ?: T.bg
    val dark = ground.luminance() < .5f
    val bg = ground.toArgb()
    // the view is made once: what it calls on a change must be the latest
    val onTextNow by androidx.compose.runtime.rememberUpdatedState(onText)
    AndroidView(modifier = modifier, factory = { c ->
        PulseCode.init(c)
        CodeEditor(c).also { ed ->
            h.ed = ed
            ed.setTypefaceText(Typeface.MONOSPACE)
            ed.setLineNumberEnabled(true)
            ed.setBlockLineEnabled(true)
            ed.setScalable(true)
            ed.setTabWidth(4)
            ed.setText(text)
            ed.subscribeEvent(ContentChangeEvent::class.java) { e, _ ->
                if (!h.suppress && e.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) { h.dirty = true; onTextNow?.invoke(ed.text.toString()) }
                h.sync()
            }
            // a tap on a fold marker (in the viewer) opens it again
            ed.subscribeEvent(SelectionChangeEvent::class.java) { e, _ ->
                if (!ed.isEditable && h.folds.any && e.cause == SelectionChangeEvent.CAUSE_TAP) {
                    val line = e.left.line
                    ed.post { h.quiet { PulseCode.unfoldLine(ed, h.folds, line) } }
                }
            }
            h.sync()
        }
    }, update = { ed ->
        val key = (lang?.scope ?: "") + "|" + dark + "|" + bg
        if (ed.tag != key) {
            ed.tag = key
            val s = PulseCode.scheme(dark)
            s.setColor(EditorColorScheme.WHOLE_BACKGROUND, bg)
            s.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, bg)
            ed.setColorScheme(s)
            ed.setEditorLanguage(PulseCode.language(lang?.scope))
        }
        if (ed.isEditable != editable) ed.setEditable(editable)
        if (ed.isWordwrap != wrap) ed.setWordwrap(wrap)
        if (h.sizeSet != textSp) { h.sizeSet = textSp; ed.setTextSize(textSp) }
    }, onRelease = { it.release() })
}

/** The keys a phone keyboard hides, above the keyboard while editing. */
@Composable
private fun SymbolBar(h: CodeHandle) {
    val keys = listOf("Tab", "{", "}", "(", ")", "[", "]", "<", ">", ";", ":", "\"", "'", "=", "/", "\\", "#", "$", "_", "-", "+", "*", "&", "|", "!", "?", "@", "%", "~", "`")
    Row(Modifier.fillMaxWidth().background(T.panel).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp)) {
        keys.forEach { k ->
            Box(Modifier.size(width = if (k == "Tab") 52.dp else 38.dp, height = 40.dp).clickable {
                h.ed?.let { ed -> val t = if (k == "Tab") "    " else k; ed.insertText(t, t.length) }
            }, contentAlignment = Alignment.Center) {
                Text(k, color = T.text, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun TopIcon(name: String, on: Boolean = true, accent: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable(enabled = on) { onClick() }, contentAlignment = Alignment.Center) {
        PIcon(name, if (!on) T.sub.copy(alpha = .35f) else if (accent) Look.accent else T.text, 21.dp)
    }
}

/** Where the code of an editor comes from and goes to: a file, or a note. */
class CodeSource(val name: String, val read: suspend () -> String?, val write: suspend (String) -> Boolean, val path: String = "")

/** The code editor of Files for [file] (a tap on a code file, or "Code editor" in a file's menu). */
@Composable
fun CodeEditorDialog(file: File, startEditing: Boolean = false, onClose: () -> Unit) {
    val src = remember(file) {
        CodeSource(file.name, { if (file.length() > 6_000_000L) null else PulseCode.readText(file) }, { runCatching { file.writeText(it) }.isSuccess }, file.parent.orEmpty())
    }
    CodeEditorHost(src, startEditing, null, null, onClose)
}

/**
 * The full code editor: the language is found from the name and the first lines (and can be changed), the code is coloured by its TextMate grammar, blocks fold away
 * (while reading; editing puts them back), there is search and replace, undo and redo, a symbol bar, and it saves to its [src]. [forced] is a language chosen before
 * (a note's), [onLang] hears a change of it.
 */
@Composable
fun CodeEditorHost(src: CodeSource, startEditing: Boolean, forced: PulseCode.Lang?, onLang: ((PulseCode.Lang) -> Unit)?, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { ctx.getSharedPreferences("pulse_code", 0) }
    val h = remember(src) { CodeHandle() }
    var text by remember(src) { mutableStateOf<String?>(null) }
    var lang by remember(src) { mutableStateOf<PulseCode.Lang?>(forced) }
    var editing by remember(src) { mutableStateOf(startEditing) }
    var wrap by remember { mutableStateOf(prefs.getBoolean("wrap", false)) }
    var size by remember { mutableStateOf(prefs.getFloat("size", 13f)) }
    // the tools are always in view (no ⋮ to open them); the ground is AMOLED black by default, the look's colour on request
    val tools = true
    var amoled by remember { mutableStateOf(prefs.getBoolean("amoled", true)) }
    var runHtml by remember { mutableStateOf<String?>(null) }
    var find by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var matches by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(src) {
        val t = withContext(Dispatchers.IO) { src.read() }
        text = t ?: ""
        if (t == null) note = tr("Too large to open here", "Burada açılamayacak kadar büyük")
        // a big file is shown without colours (the grammar would take long); the rest by name, then by what the first lines say
        if (forced == null) lang = if (t != null && t.length < 1_500_000) PulseCode.detect(src.name, t.take(400)) else null
    }

    fun save(then: () -> Unit = {}) {
        val ed = h.ed ?: return
        scope.launch {
            val body = ed.text.toString()
            val ok = withContext(Dispatchers.IO) { runCatching { src.write(body) }.getOrDefault(false) }
            note = if (ok) tr("Saved", "Kaydedildi") else tr("Could not save", "Kaydedilemedi")
            if (ok) { h.dirty = false; then() }
        }
    }
    fun close() { if (h.dirty) confirm = true else onClose() }
    fun startFind() { find = !find; if (!find) h.ed?.searcher?.stopSearch() }
    fun runFind() {
        val ed = h.ed ?: return
        if (query.isEmpty()) { ed.searcher.stopSearch(); matches = ""; return }
        ed.searcher.search(query, EditorSearcher.SearchOptions(true, false))
        matches = ed.searcher.matchedPositionCount.let { n -> if (n > 0) "${ed.searcher.currentMatchedPositionIndex + 1}/$n" else "0" }
    }

    // inside a window of the desktop the editor is drawn in that window; anywhere else it takes the whole screen
    val inWin = com.umutk.pulse.extras.launcher.LocalDesktopWin.current != null
    var langMenu by remember { mutableStateOf(false) }
    val body: @Composable () -> Unit = {
        val ground = if (amoled) androidx.compose.ui.graphics.Color.Black else T.bg
        Column(Modifier.fillMaxSize().background(ground).then(if (inWin) Modifier else Modifier.statusBarsPadding().navigationBarsPadding().imePadding())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                TopIcon("back") { close() }
                Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                    Text(src.name, color = T.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // the project (the folder) and the path of the file, then the type found from the name and the first lines
                    if (src.path.isNotEmpty()) {
                        val parts = src.path.trimEnd('/').split('/').filter { it.isNotEmpty() }
                        Text(buildString { append(parts.lastOrNull().orEmpty()); if (parts.size > 1) append("  ·  ").append(parts.dropLast(1).takeLast(3).joinToString("/", prefix = ".../")) },
                            color = Look.accent, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(listOfNotNull(lang?.label, if (h.lines > 0) "${h.lines} " + tr("lines", "satır") else null, if (h.dirty) tr("edited", "değişti") else null).joinToString("  ·  "),
                        color = T.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (editing) { TopIcon("undo", h.canUndo) { h.ed?.undo() }; TopIcon("redo", h.canRedo) { h.ed?.redo() } }
                // the language: one icon that opens the list (the row of language names is gone)
                Box {
                    TopIcon("details") { langMenu = true }
                    androidx.compose.material3.DropdownMenu(langMenu, { langMenu = false }) {
                        PulseCode.common.forEach { l -> androidx.compose.material3.DropdownMenuItem(text = { Text(l.label, color = if (lang?.scope == l.scope) Look.accent else T.text) }, onClick = { langMenu = false; lang = l; onLang?.invoke(l) }) }
                    }
                }
                TopIcon("search", accent = find) { startFind() }
                if (editing) TopIcon("save", h.dirty, accent = h.dirty) { save() }
                else TopIcon("edit", text != null) {
                    // editing puts every fold back first, so what is saved is the whole file
                    h.ed?.let { ed -> h.quiet { PulseCode.unfoldAll(ed, h.folds) } }
                    editing = true
                }
                // a page (html, svg, markdown shown as html) runs: it opens in a window as a browser shows it
                if ((lang?.label ?: "").lowercase().let { it.contains("html") || it.contains("svg") } || src.name.substringAfterLast('.').lowercase() in setOf("html", "htm", "svg")) TopIcon("play") { runHtml = h.ed?.text?.toString() ?: text }
            }
            if (confirm) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Save the changes?", "Değişiklikler kaydedilsin mi?"), color = T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                PButton(tr("Save", "Kaydet"), compact = true) { confirm = false; save { onClose() } }
                PButton(tr("Discard", "At"), compact = true) { onClose() }
                PButton(tr("Cancel", "Vazgeç"), compact = true) { confirm = false }
            }
            if (tools) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PChip(wrap, tr("Wrap", "Satır kaydır")) { wrap = !wrap; prefs.edit().putBoolean("wrap", wrap).apply() }
                PChip(amoled, tr("AMOLED black", "AMOLED siyah")) { amoled = !amoled; prefs.edit().putBoolean("amoled", amoled).apply() }
                if (!editing) {
                    PChip(false, tr("Fold block", "Bloğu kapat")) { h.ed?.let { ed -> h.quiet { if (!PulseCode.foldAtCursor(ed, h.folds)) note = tr("No block here", "Burada blok yok") } } }
                    PChip(false, tr("Fold all", "Hepsini kapat")) { h.ed?.let { ed -> h.quiet { PulseCode.foldAll(ed, h.folds) } } }
                    PChip(false, tr("Unfold all", "Hepsini aç")) { h.ed?.let { ed -> h.quiet { PulseCode.unfoldAll(ed, h.folds) } } }
                }
                PChip(false, "A−") { size = (size - 1f).coerceAtLeast(8f); prefs.edit().putFloat("size", size).apply() }
                PChip(false, "A+") { size = (size + 1f).coerceAtMost(28f); prefs.edit().putFloat("size", size).apply() }
            }
            if (find) Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PField(query, { query = it; runFind() }, tr("Find", "Ara"), Modifier.weight(1f))
                    Text(matches, color = T.sub, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
                    Box(Modifier.size(36.dp).clickable { h.ed?.searcher?.gotoPrevious(); runFind() }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.graphicsLayer { rotationZ = -90f }) }
                    Box(Modifier.size(36.dp).clickable { h.ed?.searcher?.gotoNext(); runFind() }, contentAlignment = Alignment.Center) { PIcon("chevron", T.text, 20.dp, Modifier.graphicsLayer { rotationZ = 90f }) }
                }
                if (editing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PField(replace, { replace = it }, tr("Replace with", "Şununla değiştir"), Modifier.weight(1f))
                    PButton(tr("All", "Hepsi"), compact = true) { h.ed?.searcher?.replaceAll(replace); runFind() }
                }
            }
            if (note.isNotEmpty()) LaunchedEffect(note) { kotlinx.coroutines.delay(1800); note = "" }
            if (note.isNotEmpty()) Text(note, color = Look.accent, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val t = text
                if (t == null) Text(tr("Reading…", "Okunuyor…"), color = T.sub, modifier = Modifier.align(Alignment.Center))
                else CodePane(h, t, lang, editing, wrap, size, Modifier.fillMaxSize(), bgOverride = ground)
            }
            if (editing) SymbolBar(h)
        }
    }
    if (inWin) body() else Dialog(onDismissRequest = { close() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, decorFitsSystemWindows = false)) { body() }
    // the page run: shown as a browser shows it, with its scripts (it may load what it links to from the internet)
    runHtml?.let { html ->
        Dialog(onDismissRequest = { runHtml = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TopIcon("back") { runHtml = null }
                    Text(src.name, color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
                    android.webkit.WebView(c).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = android.webkit.WebViewClient()
                        loadDataWithBaseURL("https://localhost/", html, "text/html", "utf-8", null)
                    }
                })
            }
        }
    }
}
