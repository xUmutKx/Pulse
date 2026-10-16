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
    val alive get() = proc?.isAlive == true
}

/** ADB-style shell commands for quick use: name, icon and command. Run on the phone's shell (root when available). */
private val presets = listOf(
    Triple("Model", "phone", "getprop ro.product.model"),
    Triple("Android", "info", "getprop ro.build.version.release"),
    Triple("Battery", "battery", "dumpsys battery"),
    Triple("Packages", "users", "pm list packages -3"),
    Triple("Top app", "processes", "dumpsys activity activities | grep -m 3 mResumedActivity"),
    Triple("Screen", "palette", "wm size; wm density"),
    Triple("Settings", "settings", "settings list global | head -n 40"),
    Triple("Log", "table", "logcat -d -t 100"),
)

/** Terminal: an embedded shell session (sh, or su when root is on) running inside Pulse with live output. */
@Composable
fun TerminalPage() {
    val ctx = LocalContext.current
    val home = remember { ctx.filesDir.absolutePath }
    var cmd by remember { mutableStateOf("") }
    var presetsOpen by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) { if (!TermSession.alive) TermSession.start(home, false) }
    LaunchedEffect(TermSession.text.length) { scroll.animateScrollTo(scroll.maxValue) }
    fun go() {
        val c = cmd.trim()
        if (c.isEmpty()) return
        termHistory.remove(c); termHistory.add(0, c)
        while (termHistory.size > 20) termHistory.removeAt(termHistory.lastIndex)
        if (!TermSession.alive) TermSession.start(TermSession.cwd.ifEmpty { home }, TermSession.asRoot)
        TermSession.send(c); cmd = ""
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PHead("Terminal", if (TermSession.asRoot) "root" else "", guide = "terminal")
        PCard {
            Column(Modifier.fillMaxWidth().height(300.dp)
                .background(if (T.dark) Color(0xFF0C0C0C) else Color(0xFFF4F4F4), RoundedCornerShape(T.radius))
                .verticalScroll(scroll).padding(8.dp)) {
                Text(TermSession.shown, color = T.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            }
            Text(TermSession.cwd, color = T.sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PField(cmd, { cmd = it }, "Command", Modifier.weight(1f), onDone = { go() })
                Box(Modifier.size(40.dp).clickable { go() }, contentAlignment = Alignment.Center) { PIcon("run", Look.accent, 24.dp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PButton("Ctrl-C", Modifier.weight(1f), compact = true) { TermSession.interrupt() }
                PButton("Clear", Modifier.weight(1f), compact = true) { TermSession.text = "" }
                if (Shell.root == true) PButton(if (TermSession.asRoot) "sh" else "su", Modifier.weight(1f), compact = true) {
                    TermSession.start(TermSession.cwd.ifEmpty { home }, !TermSession.asRoot)
                }
            }
            Row(Modifier.fillMaxWidth().clickable { presetsOpen = !presetsOpen }, verticalAlignment = Alignment.CenterVertically) {
                PIcon("chevron", T.sub, 16.dp, Modifier.rotate(if (presetsOpen) 90f else 0f))
                Spacer(Modifier.width(8.dp))
                Text("ADB commands", color = T.text, fontSize = 14.sp)
            }
            if (presetsOpen) presets.forEach { (name, icon, c) ->
                Row(Modifier.fillMaxWidth().clickable { cmd = c }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    PIcon(icon, Look.accent, 18.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(name, color = T.text, fontSize = 14.sp)
                        Text(c, color = T.sub, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
        PCard {
            Text("History", color = T.sub, fontSize = 12.sp)
            termHistory.forEach { c ->
                Text(c, Modifier.fillMaxWidth().clickable { cmd = c }.padding(vertical = 4.dp), color = T.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
