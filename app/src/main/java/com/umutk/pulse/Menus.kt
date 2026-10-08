package com.umutk.pulse

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuEntry(val label: String, val checked: Boolean? = null, val onClick: () -> Unit)
class MenuTop(val label: String, val items: List<MenuEntry>)

/** The menus and the dialogs they open. */
class MenuHost(val tops: List<MenuTop>, val dialogs: @Composable () -> Unit)

private fun activityOf(c: Context): Activity? { var x = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }

/** File / Options / View / Help like Task Manager's, and they work: the switches are the same ones Settings has. */
@Composable
fun rememberMenus(go: (String) -> Unit): MenuHost {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var run by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    val tops = listOf(
        MenuTop("File", listOf(
            MenuEntry("Run new task…") { run = true },
            MenuEntry("Share a report") {
                scope.launch {
                    val txt = withContext(Dispatchers.IO) { try { Hw.report(ctx) } catch (e: Throwable) { "Could not build the report: ${e.message}" } }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, txt), "Pulse report"))
                }
            },
            MenuEntry("Settings") { go("settings") },
            MenuEntry("Exit") { activityOf(ctx)?.finish() },
        )),
        MenuTop("Options", listOf(
            MenuEntry("Keep the screen on", Opt.keepOn) { Opt.keepOn = !Opt.keepOn; Opt.save(ctx) },
            MenuEntry("Ask before ending a task", Cfg.confirmKill) { Cfg.setConfirm(ctx, !Cfg.confirmKill) },
            MenuEntry("Floating monitor", Opt.ovOn) { Opt.ovOn = !Opt.ovOn; Opt.save(ctx); MonitorService.sync(ctx) },
            MenuEntry("Temperatures in °F", Opt.tempF) { Opt.tempF = !Opt.tempF; Opt.save(ctx) },
        )),
        MenuTop("View", listOf(
            MenuEntry("Refresh now") { scope.launch(Dispatchers.IO) { Sampler.sample(ctx) } },
            MenuEntry("Update speed: High (0.5 s)", Cfg.interval == 500L) { Cfg.setInterval(ctx, 500L) },
            MenuEntry("Update speed: Normal (1 s)", Cfg.interval == 1000L) { Cfg.setInterval(ctx, 1000L) },
            MenuEntry("Update speed: Low (2 s)", Cfg.interval == 2000L) { Cfg.setInterval(ctx, 2000L) },
            MenuEntry("Guardians") { go("guardians") },
        )),
        MenuTop("Help", listOf(MenuEntry("About Pulse") { about = true })),
    )
    return MenuHost(tops) {
        if (run) RunTaskDialog { run = false }
        if (about) PDialog("Pulse $version", "A task manager for Android that looks like the Windows one, with a full browser for everything your phone knows about itself.", "OK", { about = false }, { about = false })
    }
}

@Composable
private fun Item(e: MenuEntry, close: () -> Unit) {
    DropdownMenuItem(text = { Text(e.label) }, leadingIcon = e.checked?.let { c -> { Text(if (c) "✓" else "   ") } }, onClick = { close(); e.onClick() })
}

/** The menu bar of the classic looks: each word opens its menu. */
@Composable
fun MenuBar(host: MenuHost, color: Color, size: TextUnit, gap: Dp = 14.dp) {
    var open by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        host.tops.forEach { m ->
            Box {
                Text(m.label, color = color, fontSize = size, modifier = Modifier.clickable { open = m.label }.padding(vertical = 2.dp))
                DropdownMenu(open == m.label, { open = null }) { m.items.forEach { Item(it) { open = null } } }
            }
        }
    }
}

/** The modern looks have no menu bar: the same menus sit behind one "⋮". */
@Composable
fun MenuOverflow(host: MenuHost, color: Color) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text("⋮", color = color, fontSize = 22.sp, modifier = Modifier.clickable { open = true }.padding(horizontal = 12.dp, vertical = 2.dp))
        DropdownMenu(open, { open = false }) {
            host.tops.forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(T.line.copy(alpha = .5f)))
                Text(m.label.uppercase(), color = Look.accent, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
                m.items.forEach { Item(it) { open = false } }
            }
        }
    }
}

/** File > Run new task: a command to run (as root when Pulse has root), or an app's package name to open. */
@Composable
private fun RunTaskDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = T
    var cmd by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.ui.window.Dialog(onDismiss) {
        Column(Modifier.fillMaxWidth().background(t.bg, RoundedCornerShape(t.radius.coerceAtMost(12.dp)))
            .then(if (t.bevel) Modifier.bevel(true) else Modifier.border(1.dp, t.line, RoundedCornerShape(t.radius.coerceAtMost(12.dp)))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create new task", color = t.text, fontSize = 18.sp)
            Text("Type a command to run (as root when Pulse has root), or an app's package name to open it.", color = t.sub, fontSize = 13.sp)
            PField(cmd, { cmd = it }, "Command or package name", Modifier.fillMaxWidth())
            if (out.isNotEmpty()) Text(out, color = t.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                PButton("Close", onClick = onDismiss); Spacer(Modifier.width(8.dp))
                PButton(if (busy) "Running…" else "Run") {
                    val c = cmd.trim()
                    if (c.isEmpty() || busy) return@PButton
                    val launch = if (' ' !in c && '.' in c) ctx.packageManager.getLaunchIntentForPackage(c) else null
                    if (launch != null) { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onDismiss() }
                    else { busy = true; scope.launch { val r = withContext(Dispatchers.IO) { Shell.run(c) }; out = r.trim().ifEmpty { "(no output)" }.take(4000); busy = false } }
                }
            }
        }
    }
}
