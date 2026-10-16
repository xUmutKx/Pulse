package com.umutk.pulse

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MenuEntry(val label: String, val checked: Boolean? = null, val icon: ImageVector? = null, val key: String? = null, val onClick: () -> Unit)
class MenuTop(val label: String, val items: List<MenuEntry>)

/** The menus and the dialogs they open. */
class MenuHost(val tops: List<MenuTop>, val dialogs: @Composable () -> Unit)

private fun activityOf(c: Context): Activity? { var x = c; while (x is ContextWrapper) { if (x is Activity) return x; x = x.baseContext }; return null }

/** File / Options / View / Help like Task Manager's, and they work: the switches are the same ones Settings has. */
@Composable
fun rememberMenus(go: (String) -> Unit): MenuHost {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val openTool = LocalOpenTool.current
    var run by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    var font by remember { mutableStateOf(false) }
    val version = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (e: Exception) { "" } }
    val tops = listOf(
        MenuTop("File", listOf(
            MenuEntry("Run new task…", icon = Icons.Outlined.PlayArrow, key = "run") { run = true },
            MenuEntry("Exit", icon = Icons.Outlined.ExitToApp, key = "power") { activityOf(ctx)?.finish() },
        )),
        MenuTop("View", listOf(
            MenuEntry("Refresh now", icon = Icons.Outlined.Refresh, key = "history") { scope.launch(Dispatchers.IO) { Sampler.sample(ctx) } },
            MenuEntry("Widgets", icon = Icons.Outlined.Widgets, key = "apps") { go("widgets") },
            MenuEntry("System font", icon = Icons.Outlined.FormatSize, key = "details") { openTool("fonts") },
        )),
        MenuTop("Tools", listOf(
            MenuEntry("Toolkit", icon = Icons.Outlined.Build, key = "settings") { openTool("toolkit") },
            MenuEntry("App lock", icon = Icons.Outlined.Lock, key = "applock") { go("applock") },
            MenuEntry("APK installer", icon = Icons.Outlined.Download, key = "download") { openTool("installer") },
            MenuEntry("Device troubleshoot", icon = Icons.Outlined.HealthAndSafety, key = "info") { openTool("troubleshoot") },
            MenuEntry("Authenticator", icon = Icons.Outlined.Key, key = "users") { openTool("authenticator") },
            MenuEntry("Network usage", icon = Icons.Outlined.DataUsage, key = "speed") { openTool("netusage") },
            MenuEntry("Guide", icon = Icons.Outlined.MenuBook, key = "book") { openTool("guide") },
        )),
        // keys are the per-look icon names (Win11 Fluency PNG, 95/XP/7 packs or Fluent vectors); Material uses the ImageVector
        MenuTop(EXTRA_APPS, listOf(
            MenuEntry(tr("Files", "Dosyalar"), icon = Icons.Outlined.Folder, key = "folder") { launchExtra(ctx, "com.umutk.pulse.FilesActivity") },
            MenuEntry(tr("Photos", "Fotoğraflar"), icon = Icons.Outlined.Image, key = "image") { launchExtra(ctx, "com.umutk.pulse.ImageViewActivity") },
            MenuEntry(tr("Calendar", "Takvim"), icon = Icons.Outlined.CalendarMonth, key = "calendar") { launchExtra(ctx, "com.umutk.pulse.CalendarActivity") },
            MenuEntry(tr("Contacts", "Kişiler"), icon = Icons.Outlined.Contacts, key = "users") { launchExtra(ctx, "com.umutk.pulse.ContactsActivity") },
        )),
    )
    return MenuHost(tops) {
        if (run) RunTaskDialog { run = false }
        if (font) WinAlert(
            onDismissRequest = { font = false },
            title = { Text("Font size ${(Opt.fontScale * 100).toInt()}%", color = T.text) },
            text = { PSlider(Opt.fontScale, { Opt.fontScale = it }, .8f..1.4f, { Opt.save(ctx) }) },
            confirmButton = { PButton("OK") { font = false } },
        )
        if (about) PDialog("Pulse $version", "A task manager for Android that looks like Windows, with a file explorer, downloads, an app installer, network usage, a 20-tool toolkit, widgets and a terminal.", "OK", { about = false }, { about = false }, info = true)
    }
}

private val EXTRA_APPS get() = tr("Extra apps", "Ek uygulamalar")

/** Class-name launch so the menu compiles before the Calendar/Contacts activities exist. */
private fun launchExtra(ctx: Context, cls: String) {
    try { ctx.startActivity(Intent().setClassName(ctx, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

@Composable
private fun Item(e: MenuEntry, close: () -> Unit) {
    ThemedMenuItem(e.label, { close(); e.onClick() }, iconKey = if (T.id == "win11" && !T.material) WIN11_MENU_KEY[e.label] ?: e.key else e.key, icon = e.icon, checked = e.checked)
}

/** Win11 look: one Fluent glyph per menu entry (FluentPaths), so no icon repeats in the overflow menu. */
private val WIN11_MENU_KEY = mapOf(
    "Run new task…" to "open", "Exit" to "power", "Refresh now" to "refresh", "Widgets" to "gridView", "System font" to "edit",
    "Toolkit" to "wrench", "App lock" to "applock", "APK installer" to "download", "Settings" to "settings", "DNS" to "globe", "Device troubleshoot" to "shieldOk",
    "Authenticator" to "key", "Network usage" to "compare", "Guide" to "book",
)

/** The menu bar of the classic looks: each word opens its menu. */
@Composable
fun MenuBar(host: MenuHost, color: Color, size: TextUnit, gap: Dp = 14.dp) {
    var open by remember { mutableStateOf<String?>(null) }
    val w = LocalWindowLook.current
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        host.tops.forEach { m ->
            Box {
                Text(m.label, color = color, fontSize = size, modifier = Modifier.clickable { open = m.label }.padding(vertical = 2.dp))
                ThemedMenu(open == m.label, { open = null }) { m.items.forEach { Item(it) { open = null } } }
            }
        }
    }
}

/** The modern looks have no menu bar: the same menus sit behind one "⋮". */
@Composable
fun MenuOverflow(host: MenuHost, color: Color) {
    var open by remember { mutableStateOf(false) }
    var extra by rememberSaveable { mutableStateOf(false) }
    val w = LocalWindowLook.current
    Box {
        MoreIcon(color, 22.dp, Modifier.clickable { open = true }.padding(horizontal = 8.dp, vertical = 2.dp))
        // the menu takes the look: its panel (the Material container colour), corners and, on the Windows looks, the edge
        ThemedMenu(open, { open = false }) {
            host.tops.forEachIndexed { i, m ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(w.line.copy(alpha = .5f)))
                val fold = m.label == EXTRA_APPS
                Text((if (fold) (if (extra) "▾ " else "▸ ") else "") + m.label.uppercase(), color = Look.accent, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().then(if (fold) Modifier.clickable { extra = !extra } else Modifier).padding(start = 16.dp, top = 8.dp, bottom = 2.dp))
                if (!fold || extra) m.items.forEach { Item(it) { open = false } }
            }
        }
    }
}

/** File > Run new task: a command to run (as root when Pulse has root), or an app's package name to open. */
@Composable
private fun RunTaskDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val t = LocalWindowLook.current
    var cmd by remember { mutableStateOf("") }
    var out by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    WinAlert(
        onDismissRequest = onDismiss,
        title = { Text("Create new task", color = t.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Type a command to run (as root when Pulse has root), or an app's package name to open it.", color = t.sub, fontSize = 13.sp)
                PField(cmd, { cmd = it }, "Command or package name", Modifier.fillMaxWidth())
                if (out.isNotEmpty()) Text(out, color = t.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
            }
        },
        confirmButton = {
            PButton(if (busy) "Running…" else "Run") {
                val c = cmd.trim()
                if (c.isEmpty() || busy) return@PButton
                val launch = if (' ' !in c && '.' in c) ctx.packageManager.getLaunchIntentForPackage(c) else null
                if (launch != null) { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); onDismiss() }
                else { busy = true; scope.launch { val r = withContext(Dispatchers.IO) { Shell.run(c) }; out = r.trim().ifEmpty { "(no output)" }.take(4000); busy = false } }
            }
        },
        dismissButton = { PButton("Close", onClick = onDismiss) },
    )
}

/** The menus of the classic looks, listed down the navigation pane: a letter when the pane is narrow, the name when wide. */
@Composable
fun PaneMenus(host: MenuHost, color: Color, wide: Boolean) {
    val w = LocalWindowLook.current
    host.tops.forEach { top ->
        var open by remember { mutableStateOf(false) }
        Box {
            Row(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (wide) top.label else top.label.take(1), color = color, fontSize = 13.sp, maxLines = 1)
            }
            ThemedMenu(open, { open = false }) {
                top.items.forEach { Item(it) { open = false } }
            }
        }
    }
}
