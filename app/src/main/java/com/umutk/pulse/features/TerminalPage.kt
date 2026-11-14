package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.Look
import com.umutk.pulse.PCard
import com.umutk.pulse.PButton
import com.umutk.pulse.PField
import androidx.compose.ui.platform.LocalContext
import com.umutk.pulse.PHead
import com.umutk.pulse.PIcon
import com.umutk.pulse.Shell
import com.umutk.pulse.T

/** The last 20 commands, newest first. Kept in memory: survives moving between pages, not a restart. */
private val termHistory = mutableStateListOf<String>()

/** Persistent shell child process owned by Pulse (no Termux). Output is live; the working directory is read back after every command. */
private object TermSession {
    var text by mutableStateOf("")
    var cwd by mutableStateOf("")
    var asRoot by mutableStateOf(false)
    private var proc: Process? = null
    private var pid = -1L
    private val mark = Regex("\u0001([^\u0001]*)\u0001\n?")

    @Synchronized fun start(home: String, root: Boolean) {
        stop(); asRoot = root; cwd = home
        // shortcut: no pty, so full-screen programs (vim, top) and Ctrl-C of the shell itself are unsupported
        val p = try { ProcessBuilder(if (root) listOf("su") else listOf("/system/bin/sh")).directory(java.io.File(home)).redirectErrorStream(true)
            .apply { environment()["HOME"] = home }.start() } catch (e: Exception) { text += "Cannot start shell: ${e.message}\n"; return }
        proc = p
        pid = runCatching { p.javaClass.getDeclaredField("pid").apply { isAccessible = true }.getLong(p) }.getOrDefault(-1L)
        Thread {
            val r = p.inputStream.bufferedReader(); val buf = CharArray(4096)
            while (true) { val n = try { r.read(buf) } catch (e: Exception) { -1 }; if (n < 0) break; append(String(buf, 0, n)) }
            append("\n[shell closed]\n")
        }.start()
        send("cd \"$home\"", echo = false)
    }

    private fun append(s: String) {
        var t = text + s
        while (true) { val m = mark.find(t) ?: break; cwd = m.groupValues[1]; t = t.removeRange(m.range) }
        if (t.length > 60000) t = t.takeLast(40000)
        text = t
    }
    /** Text shown: anything after an unfinished cwd marker is held back. */
    val shown get() = text.substringBefore('\u0001')

    @Synchronized fun send(c: String, echo: Boolean = true) {
        val p = proc ?: return
        if (echo) text += "$ $c\n"
        Thread { runCatching { p.outputStream.apply { write("$c\nprintf '\\001%s\\001\\n' \"\$PWD\"\n".toByteArray()); flush() } } }.start()
    }
    /** Ctrl-C: interrupt the shell's running children. */
    fun interrupt() {
        if (pid <= 0) return
        Thread { runCatching { ProcessBuilder(if (asRoot) listOf("su", "-c", "pkill -2 -P $pid") else listOf("sh", "-c", "pkill -2 -P $pid")).start().waitFor() } }.start()
        text += "^C\n"
    }
    fun stop() { proc?.destroy(); proc = null }
    val alive get() = proc?.let { runCatching { it.exitValue(); false }.getOrDefault(true) } == true
}

/** Colours of the 16 ANSI codes (the Ubuntu-like palette of a terminal). */
private val ANSI16 = listOf(0xFF2E3436, 0xFFCC0000, 0xFF4E9A06, 0xFFC4A000, 0xFF3465A4, 0xFF75507B, 0xFF06989A, 0xFFD3D7CF,
    0xFF555753, 0xFFEF2929, 0xFF8AE234, 0xFFFCE94F, 0xFF729FCF, 0xFFAD7FA8, 0xFF34E2E2, 0xFFEEEEEC).map { Color(it) }

/** The output with its colour codes (ESC [ ... m: the 16 colours, bold, reset, and 38;2;r;g;b) drawn as colours; any other escape code is dropped. */
private fun ansi(src: String, base: Color): androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    var color = base; var bold = false
    var i = 0
    val esc = Regex("\u001B\\[([0-9;]*)([A-Za-z])")
    while (i < src.length) {
        val m = esc.find(src, i)
        val end = m?.range?.first ?: src.length
        if (end > i) {
            val st = androidx.compose.ui.text.SpanStyle(color = color, fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null)
            pushStyle(st); append(src.substring(i, end)); pop()
        }
        if (m == null) break
        if (m.groupValues[2] == "m") {
            val ps = m.groupValues[1].split(';').mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
            var k = 0
            while (k < ps.size) {
                val c = ps[k]
                when {
                    c == 0 -> { color = base; bold = false }
                    c == 1 -> bold = true
                    c in 30..37 -> color = ANSI16[c - 30]
                    c in 90..97 -> color = ANSI16[c - 90 + 8]
                    c == 39 -> color = base
                    c == 38 && ps.getOrNull(k + 1) == 2 && k + 4 < ps.size + 0 -> { color = Color(ps[k + 2], ps[k + 3].coerceIn(0, 255), ps[k + 4].coerceIn(0, 255)); k += 4 }
                }
                k++
            }
        }
        i = m.range.last + 1
    }
}

/** The neofetch card as the terminal prints it: ANSI colour codes around each run. */
private fun neofetchOut(ctx: android.content.Context): String = NeofetchText.lines(ctx).joinToString("\n") { line ->
    line.joinToString("") { s ->
        val c = s.color
        if (c == null && !s.bold) s.text
        else "\u001B[" + (if (s.bold) "1;" else "") + (if (c != null) "38;2;${(c.red * 255).toInt()};${(c.green * 255).toInt()};${(c.blue * 255).toInt()}" else "0") + "m" + s.text + "\u001B[0m"
    }
} + "\n"

/**
 * Terminal: an embedded shell session (sh, or su when root is on) running inside Pulse with live output, drawn as a terminal window: black, monospace, a prompt line at the foot.
 * `neofetch` is built in, so the phone answers it as if it were installed; the colour codes of any program are drawn.
 */
@Composable
fun TerminalPage() {
    val ctx = LocalContext.current
    val home = remember { ctx.filesDir.absolutePath }
    var cmd by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { if (!TermSession.alive) TermSession.start(home, false) }
    LaunchedEffect(TermSession.text.length) { scroll.animateScrollTo(scroll.maxValue) }
    val fg = Color(0xFFE6E6E6); val green = Color(0xFF3DDC84)
    var hi by remember { mutableStateOf(-1) }
    fun go() {
        val c = cmd.trim()
        if (c.isEmpty()) return
        termHistory.remove(c); termHistory.add(0, c); hi = -1
        while (termHistory.size > 20) termHistory.removeAt(termHistory.lastIndex)
        if (!TermSession.alive) TermSession.start(TermSession.cwd.ifEmpty { home }, TermSession.asRoot)
        // neofetch is part of the terminal, as if it were installed
        if (c == "neofetch" || c.startsWith("neofetch ")) { TermSession.text += "$ $c\n" + neofetchOut(ctx); cmd = ""; return }
        if (c == "clear") { TermSession.text = ""; cmd = ""; return }
        TermSession.send(c); cmd = ""
    }
    Column(Modifier.fillMaxSize().background(Color.Black).padding(horizontal = 10.dp, vertical = 8.dp)) {
        // the window's title line and its few buttons
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("pulse@phone" + if (TermSession.asRoot) " (root)" else "", color = green, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1)
            Text("^C", color = fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { TermSession.interrupt() }.padding(4.dp))
            Text("clear", color = fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { TermSession.text = "" }.padding(4.dp))
            if (Shell.root == true) Text(if (TermSession.asRoot) "sh" else "su", color = fg, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.clickable { TermSession.start(TermSession.cwd.ifEmpty { home }, !TermSession.asRoot) }.padding(4.dp))
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(top = 6.dp)) {
            // the colour codes are worked out once per output, not on every frame (up to 60,000 characters)
            val shownText = TermSession.shown
            val coloured = remember(shownText) { ansi(shownText, fg) }
            Text(coloured, color = fg, fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp)
        }
        Text(TermSession.cwd, color = Color(0xFF729FCF), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$ ", color = green, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            androidx.compose.foundation.text.BasicTextField(cmd, { cmd = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = fg, fontSize = 14.sp, fontFamily = FontFamily.Monospace), cursorBrush = androidx.compose.ui.graphics.SolidColor(green),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go, autoCorrect = false),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go() }), modifier = Modifier.weight(1f))
            // the last commands, one press back each
            Text("\u2191", color = fg, fontSize = 18.sp, modifier = Modifier.clickable { if (termHistory.isNotEmpty()) { hi = (hi + 1).coerceAtMost(termHistory.lastIndex); cmd = termHistory[hi] } }.padding(8.dp))
        }
    }
}
