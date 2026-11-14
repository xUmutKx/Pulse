package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.tr

private val T_GREEN = Color(0xFF33FF33)
private val T_DIM = Color(0xFF1F9E1F)
private val T_BG = Color.Black
private val T_MONO = FontFamily.Monospace

/** The launcher as a terminal: black ground, green monospace text, the apps as lines of a shell, and the apps list as a prompt that launches what is typed. */
class TerminalLauncherLook : LauncherLook {
    override val id = "terminal"
    override val nameEn = "Terminal"
    override val nameTr = "Terminal"
    override val taskbarHeight: Dp = 46.dp
    override val taskbarTop = false
    override val ownSearch = true

    @Composable
    override fun Background(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier.fillMaxSize().background(T_BG)) { content() }
    }

    /** The prompt line at the foot: the dock apps as words, and the prompt that opens the shell. */
    @Composable
    override fun Taskbar(state: LauncherState, modifier: Modifier) {
        val edge = LocalLauncherEdge.current
        Row(modifier.background(T_BG).border(1.dp, T_DIM).padding(horizontal = maxOf(10.dp, edge)), verticalAlignment = Alignment.CenterVertically) {
            Text("pulse@phone:~$", color = T_GREEN, fontFamily = T_MONO, fontSize = 13.sp, modifier = Modifier.clickable { LauncherSession.startOpen = true }.padding(end = 8.dp))
            state.dock.take(4).forEach { app ->
                Text("[" + slug(app.label) + "]", color = T_DIM, fontFamily = T_MONO, fontSize = 12.sp, maxLines = 1, modifier = Modifier.appTap(state, app) { state.open(app) }.padding(end = 8.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("_", color = T_GREEN, fontFamily = T_MONO, fontSize = 14.sp, modifier = Modifier.clickable { LauncherSession.startOpen = true })
        }
    }

    /** A shell: the apps matching what is typed are listed as numbered lines, a tap or Enter runs the first; a few commands (help, ls, clear, exit, neofetch, settings) answer too. */
    @Composable
    override fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var q by remember { mutableStateOf("") }
        var out by remember { mutableStateOf(listOf(tr("Pulse shell 1.0. Type a name to launch an app. help for commands.", "Pulse kabuğu 1.0. Uygulama başlatmak için bir ad yaz. Komutlar için help."))) }
        val fr = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        val matches = remember(q, state.apps) { if (q.isBlank()) state.apps else state.apps.filter { it.label.contains(q.trim(), true) || slug(it.label).contains(q.trim().lowercase()) } }
        fun run(cmd: String) {
            val c = cmd.trim()
            when {
                c.isEmpty() -> {}
                c == "help" -> out = out + "$ help" + listOf("help      this list", "ls        every app", "clear     clear the screen", "neofetch  about this phone", "settings  launcher settings", "exit      close the shell", "<name>    launch an app")
                c == "ls" -> { q = ""; return }
                c == "clear" -> out = emptyList()
                c == "exit" || c == "q" -> { onClose(); return }
                c == "settings" -> { onClose(); runCatching { ctx.startActivity(android.content.Intent(ctx, LauncherSettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                c == "neofetch" -> out = out + "$ neofetch" + com.umutk.pulse.features.NeofetchText.lines(ctx).map { it.joinToString("") { s -> s.text } }
                else -> {
                    val a = state.apps.firstOrNull { slug(it.label) == c.lowercase() || it.label.equals(c, true) } ?: matches.firstOrNull()
                    if (a != null) { state.open(a); onClose() } else out = out + "$ $c" + "pulse: command not found: $c"
                }
            }
            q = ""
        }
        Column(modifier.background(T_BG).statusBarsPadding().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), reverseLayout = false) {
                items(out.size) { i -> Text(out[i], color = if (out[i].startsWith("$")) T_GREEN else T_DIM, fontFamily = T_MONO, fontSize = 12.sp) }
                items(matches.size) { i ->
                    val a = matches[i]
                    Text("[%2d] %s".format(i + 1, slug(a.label)), color = T_GREEN, fontFamily = T_MONO, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().appTap(state, a) { state.open(a); onClose() }.padding(vertical = 3.dp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("pulse@phone:~$ ", color = T_GREEN, fontFamily = T_MONO, fontSize = 14.sp)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = TextStyle(color = T_GREEN, fontFamily = T_MONO, fontSize = 14.sp), cursorBrush = SolidColor(T_GREEN),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrect = false), keyboardActions = KeyboardActions(onGo = { run(q) }), modifier = Modifier.weight(1f).focusRequester(fr))
            }
        }
    }

    /** An app on the home: its name as a shell word, with a small prompt. */
    @Composable
    override fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier) {
        Row(modifier.padding(vertical = 4.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("> ", color = T_DIM, fontFamily = T_MONO, fontSize = 13.sp)
            Text(slug(app.label), color = T_GREEN, fontFamily = T_MONO, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    private fun slug(label: String) = label.lowercase().replace(Regex("[^a-z0-9çğıöşü]+"), "_").trim('_')
}
