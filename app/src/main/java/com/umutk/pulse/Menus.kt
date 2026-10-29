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
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Equalizer
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Message
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.umutk.pulse.Icon
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
        *(ToolPlace.menuEntries(ctx, { go(it) }, { openTool(it) }).takeIf { it.isNotEmpty() }?.let { listOf(MenuTop("Tools", it)) } ?: emptyList()).toTypedArray(),
        // the tools placed under Extra apps (Settings > Extra apps: download manager, Office, App lock, ...); the store ends the group, the music player, video, white noise and the standalone apps are listed inside it
        MenuTop(EXTRA_APPS, listOf(
            *ToolPlace.extraEntries(ctx, { go(it) }, { openTool(it) }).toTypedArray(),
            // the standalone apps that are switched on in Settings > Apps open from here too
            *builtinApps(ctx) { openTool(it) }.toTypedArray(),
            // every extra app, the ones switched off included, in one list
            *(if (com.umutk.pulse.extras.store.ExtraRowsHidden.menuOn(ctx)) listOf(MenuEntry(tr("Extra apps", "Ekstra uygulamalar"), icon = Icons.Outlined.Apps, key = "extraStore") { openTool("extrastore") }) else emptyList()).toTypedArray(),
        )),
    )
    return MenuHost(tops) {
        if (run) RunTaskDialog { run = false }
        if (font) WinAlert(
            onDismissRequest = { font = false },
            title = { Text("Font size ${(Opt.fontScale * 100).toInt()}%", color = T.text) },
            text = { PSlider(Opt.fontScale, { Opt.fontScale = it }, .8f..1.4f) { Opt.save(ctx) } },
            confirmButton = { PButton("OK") { font = false } },
        )
        if (about) PDialog("Pulse $version", "A task manager for Android that looks like Windows, with a file explorer, downloads, an app installer, network usage, a 20-tool toolkit, widgets and a terminal.", "OK", { about = false }, { about = false }, info = true)
    }
}

/** Where each optional tool is listed: the overflow menu's Tools group ("menu"), the Extra apps card in Settings ("extra"), or only in Settings ("hidden").
 *  Only the tools with a screen of their own (isTool) can sit in the Extra apps card; the page-based ones are menu or hidden. */
object ToolPlace {
    class Tool(val id: String, val en: String, val trName: String, val target: String, val isTool: Boolean, val key: String, val icon: ImageVector, val default: String)
    val all = listOf(
        Tool("toolkit", "Toolkit", "Araç kutusu", "toolkit", true, "settings", Icons.Outlined.Build, "extra"),
        Tool("office", "Office", "Office", "office", false, "document", Icons.Outlined.Description, "extra"),
        Tool("download", "Download manager", "İndirme yöneticisi", "downloads", false, "download", Icons.Outlined.Download, "extra"),
        Tool("applock", "App lock", "Uygulama kilidi", "applock", false, "applock", Icons.Outlined.Lock, "extra"),
        Tool("health", "Health", "Sağlık", "health", false, "heart", Icons.Outlined.Favorite, "extra"),
        Tool("installer", "APK installer", "APK yükleyici", "installer", true, "download", Icons.Outlined.SystemUpdate, "extra"),
        Tool("troubleshoot", "Device troubleshoot", "Cihaz sorun giderme", "troubleshoot", true, "shieldOk", Icons.Outlined.HealthAndSafety, "hidden"),
        Tool("authenticator", "Authenticator", "Kimlik doğrulayıcı", "authenticator", true, "users", Icons.Outlined.Key, "extra"),
        Tool("clock", "Clock", "Saat", "clock", true, "timer", Icons.Outlined.AccessTime, "extra"),
        Tool("radio", "Radio", "Radyo", "radio", true, "volume", Icons.Outlined.Radio, "extra"),
        Tool("relay", "Relay", "Relay", "relay", true, "send", Icons.Outlined.SyncAlt, "extra"),
        Tool("recap", "Music recap", "Müzik özeti", "recap", true, "heart", Icons.Outlined.Equalizer, "extra"),
        Tool("speedtest", "Internet speed test", "İnternet hız testi", "speedtest", true, "speed", Icons.Outlined.Speed, "extra"),
        Tool("chargewatts", "Charging power", "Şarj gücü", "chargewatts", true, "power", Icons.Outlined.BatteryChargingFull, "extra"),
        Tool("goodlock", "Good Lock", "Good Lock", "goodlock", true, "settings", Icons.Outlined.Widgets, "extra"),
        Tool("buds", "Buds manager", "Buds yöneticisi", "buds", true, "sensors", Icons.Outlined.Headphones, "extra"),
        Tool("netusage", "Network usage", "Ağ kullanımı", "netusage", true, "speed", Icons.Outlined.DataUsage, "extra"),
        // the launcher's settings: its Extra apps row (Settings card and the ⋮ Apps group) left with the old "HomeLauncher" alias in 0.10.134 and is back, on by default
        Tool("launcher", "Launcher", "Ana ekran", "launcher", true, "apps", Icons.Outlined.Apps, "extra"),
    )
    private val placed = mutableStateMapOf<String, String>()
    private var loaded = false
    private fun prefs(c: Context) = c.getSharedPreferences("p", Context.MODE_PRIVATE)
    private fun load(c: Context) { if (loaded) return; loaded = true; all.forEach { t -> prefs(c).getString("tp_${t.id}", null)?.let { placed[t.id] = it } } }
    fun where(c: Context, t: Tool): String { load(c); return placed[t.id] ?: t.default }
    fun set(c: Context, t: Tool, v: String) { load(c); placed[t.id] = v; prefs(c).edit().putString("tp_${t.id}", v).apply() }
    /** The entries of the overflow menu's Tools group for the tools placed there. */
    fun menuEntries(c: Context, go: (String) -> Unit, open: (String) -> Unit): List<MenuEntry> =
        all.filter { where(c, it) == "menu" }.map { t -> MenuEntry(tr(t.en, t.trName), icon = t.icon, key = t.key) { if (t.isTool) open(t.target) else go(t.target) } }
    /** The tools placed in the Extra apps card. */
    fun extraTools(c: Context): List<Tool> = all.filter { where(c, it) == "extra" }
    /** The entries of the overflow menu's Extra apps group: the same tools as the Extra apps card. */
    fun extraEntries(c: Context, go: (String) -> Unit, open: (String) -> Unit): List<MenuEntry> =
        extraTools(c).filter { "tool:${it.id}" !in com.umutk.pulse.extras.store.ExtraRowsHidden.load(c) }.map { t -> MenuEntry(tr(t.en, t.trName), icon = t.icon, key = t.key) { if (t.isTool) open(t.target) else go(t.target) } }
    /** The tools that already have a left-menu page (their own switch in Settings > Pages): they are not added to the side twice. */
    fun hasPage(t: Tool) = t.target in setOf("office", "downloads", "health")
    fun byId(id: String): Tool? = all.firstOrNull { it.id == id }
    /** Left-menu entries for the tools placed on the side (the page-based ones use their own switch, so they are left out here). */
    fun sidePages(): List<Pg> = all.filter { !hasPage(it) && placed[it.id] == "side" }.map { Pg("tool:${it.id}", tr(it.en, it.trName), it.key) }
    /** Reads the saved places once, at start (MainActivity calls it), so the left menu can use them while composing. */
    fun attach(c: Context) { load(c); AppPlace.load(c) }
}

/** The standalone apps of Settings > Apps, and where each one is listed: the top-right Apps menu, the left menu, or nowhere (Settings only). */
object AppPlace {
    class App(val alias: String, val en: String, val trName: String, val key: String, val icon: ImageVector, val run: (Context, (String) -> Unit) -> Unit)
    private const val TOOL = "com.umutk.pulse.ExtraToolActivity"
    val all = listOf(
        App("FilesLauncher", "Files", "Dosyalar", "folder", Icons.Outlined.Folder) { c, _ -> launchExtra(c, "com.umutk.pulse.FilesActivity") },
        App("PhotosLauncher", "Photos", "Fotoğraflar", "image", Icons.Outlined.Image) { c, _ -> launchExtra(c, "com.umutk.pulse.ImageViewActivity") },
        App("CalendarLauncher", "Calendar", "Takvim", "calendar", Icons.Outlined.CalendarMonth) { c, _ -> launchExtra(c, "com.umutk.pulse.CalendarActivity") },
        App("ContactsLauncher", "Contacts", "Kişiler", "person", Icons.Outlined.Person) { c, _ -> launchExtra(c, "com.umutk.pulse.ContactsActivity") },
        App("PaintLauncher", "Paint", "Paint", "palette", Icons.Outlined.Brush) { c, _ -> launchExtra(c, TOOL, "paint") },
        App("CalculatorLauncher", "Calculator", "Hesap makinesi", "calc", Icons.Outlined.Calculate) { c, _ -> launchExtra(c, TOOL, "calculator") },
        App("ArtCraftLauncher", "ArtCraft", "ArtCraft", "palette", Icons.Outlined.Palette) { c, _ -> launchExtra(c, "com.umutk.pulse.ArtCraftActivity") },
        App("BrowserLauncher", "Browser", "Tarayıcı", "globe", Icons.Outlined.Public) { c, _ -> launchExtra(c, TOOL, "browser") },
        App("OfficeLauncher", "Office", "Office", "document", Icons.Outlined.Description) { c, _ -> launchExtra(c, TOOL, "office") },
        App("MusicLauncher", "Music player", "Müzik çalar", "music", Icons.Outlined.MusicNote) { c, _ -> launchExtra(c, TOOL, "music") },
        App("VideoLauncher", "Video", "Video", "video", Icons.Outlined.Videocam) { c, _ -> launchExtra(c, TOOL, "video") },
        App("WhiteNoiseLauncher", "White noise", "Beyaz gürültü", "volume", Icons.Outlined.Waves) { c, _ -> launchExtra(c, TOOL, "whitenoise") },
        App("VoiceLauncher", "Voice recorder", "Ses kaydedici", "voice", Icons.Outlined.Mic) { c, _ -> launchExtra(c, TOOL, "voice") },
        App("NotesLauncher", "Notes", "Notlar", "notes", Icons.Outlined.Notes) { c, _ -> launchExtra(c, TOOL, "notes") },
        App("WeatherLauncher", "Weather", "Hava durumu", "weather", Icons.Outlined.WbSunny) { c, _ -> launchExtra(c, TOOL, "weather") },
        App("MessagesLauncher", "Messages", "Mesajlar", "chat", Icons.Outlined.Message) { _, o -> o("messages") },
        App("ScreenRecLauncher", "Screen recorder", "Ekran kaydı", "record", Icons.Outlined.FiberManualRecord) { _, o -> o("screenrec") },
        App("KeyboardLauncher", "Keyboard", "Klavye", "keyboard", Icons.Outlined.Keyboard) { c, _ -> launchExtra(c, TOOL, "keyboard") },
        App("LocalSendLauncher", "LocalSend", "LocalSend", "wifi", Icons.Outlined.Wifi) { _, o -> o("localsend") },
        App("PaletteLauncher", "Palette", "Palette", "image", Icons.Outlined.Palette) { _, o -> o("palette") },
        App("BlackOutLauncher", "BlackOut", "BlackOut", "shield", Icons.Outlined.DarkMode) { _, o -> o("blackout") },
        App("IslandLauncher", "Island", "Ada", "phone", Icons.Outlined.PhoneAndroid) { _, o -> o("island") },
        App("NotifHistLauncher", "Notification history", "Bildirim geçmişi", "history", Icons.Outlined.History) { _, o -> o("notifhist") },
    )
    private val placed = androidx.compose.runtime.mutableStateMapOf<String, String>()
    private fun sp(c: Context) = c.getSharedPreferences("app_place", Context.MODE_PRIVATE)
    // an app that is switched on in Settings > Apps is listed in the top-right Apps menu; the chevron there can move it to the left menu or hide it
    /** Every app from the calculator on only lives in the Extra apps list at first; Settings > Apps puts them in the ⋮ menu. */
    private val quiet = setOf("CalculatorLauncher", "ArtCraftLauncher", "MusicLauncher", "VideoLauncher", "WhiteNoiseLauncher", "VoiceLauncher", "NotesLauncher", "WeatherLauncher",
        "MessagesLauncher", "ScreenRecLauncher", "KeyboardLauncher", "LocalSendLauncher", "NotifHistLauncher", "BrowserLauncher", "OfficeLauncher")
    fun load(c: Context) { all.forEach { placed[it.alias] = sp(c).getString(it.alias, if (it.alias in quiet) "hidden" else "menu") ?: "menu" } }
    fun where(alias: String): String = placed[alias] ?: if (alias in quiet) "hidden" else "menu"
    fun set(c: Context, alias: String, place: String) { placed[alias] = place; sp(c).edit().putString(alias, place).apply() }
    fun byAlias(alias: String) = all.firstOrNull { it.alias == alias }
    /** The key of the app's Windows 11 picture (FluentFilled / fc_*). */
    fun win11Key(alias: String) = "extra" + alias.removeSuffix("Launcher")
    /** Left-menu entries of the apps placed there; "open:<alias>" starts the app instead of switching the page. */
    fun sidePages(): List<Pg> = all.filter { where(it.alias) == "side" }.map { Pg("open:${it.alias}", tr(it.en, it.trName), it.key) }
}

/** The standalone apps placed in the top-right Apps menu, as menu entries: each opens its own window. */
private fun builtinApps(ctx: Context, openTool: (String) -> Unit): List<MenuEntry> =
    AppPlace.all.filter { a -> "app:${a.alias}" !in com.umutk.pulse.extras.store.ExtraRowsHidden.load(ctx) && AppPlace.where(a.alias) == "menu" && (a.alias == "CalculatorLauncher" || ctx.packageManager.getComponentEnabledSetting(android.content.ComponentName(ctx, "com.umutk.pulse.${a.alias}")) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) }.map { a -> MenuEntry(tr(a.en, a.trName), icon = a.icon, key = a.key) { a.run(ctx, openTool) } }

private val EXTRA_APPS get() = tr("Apps", "Uygulamalar")

/** Class-name launch so the menu compiles before the Calendar/Contacts activities exist. */
// launches the target activity, not a launcher alias: a disabled alias cannot be started; [route] picks the page of ExtraToolActivity
internal fun launchExtra(ctx: Context, cls: String, route: String? = null) {
    try { ctx.startActivity(Intent().setClassName(ctx, cls).also { i -> route?.let { i.putExtra("route", it) } }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

@Composable
private fun Item(e: MenuEntry, close: () -> Unit) {
    ThemedMenuItem(e.label, { close(); e.onClick() }, iconKey = if (T.id == "win11" && !T.material) WIN11_MENU_KEY[e.label] ?: win11KeyOf(e) else e.key, icon = e.icon, checked = e.checked)
}

/** The Windows 11 icon key of a tool or app entry, found by its (English or Turkish) name. */
private fun win11KeyOf(e: MenuEntry): String =
    ToolPlace.all.firstOrNull { tr(it.en, it.trName) == e.label }?.let { "tool_${it.id}" }
        ?: AppPlace.all.firstOrNull { tr(it.en, it.trName) == e.label }?.let { AppPlace.win11Key(it.alias) } ?: e.key.orEmpty()

/** Win11 look: one Fluent glyph per menu entry (FluentPaths), so no icon repeats in the overflow menu. */
private val WIN11_MENU_KEY = mapOf(
    "Run new task…" to "open", "Exit" to "power", "Refresh now" to "refresh", "Widgets" to "gridView", "System font" to "edit",
    "Toolkit" to "tool_toolkit", "Office" to "tool_office", "Download manager" to "tool_download", "App lock" to "tool_applock", "Health" to "tool_health",
    "APK installer" to "tool_installer", "Device troubleshoot" to "tool_troubleshoot", "Authenticator" to "tool_authenticator", "Network usage" to "tool_netusage",
    "Screen cover" to "tool_cover", "Store" to "extraStore", "Settings" to "settings", "DNS" to "globe",
    // these two fell through to a "tool_goodlock"/"tool_buds" key with no glyph behind it and drew nothing in Win11
    "Good Lock" to "gridView", "Buds manager" to "sensors", "Launcher" to "gridView", "Ana ekran" to "gridView",
)

/**
 * The volume keys of the main screens, where no sound plays: MainActivity counts a press of volume up / down here (and swallows it, so the
 * system volume bar never shows), and the shell's left panel and the ⋮ menu each watch their own counter and flip when it moves.
 */
object ChromeKeys {
    var leftTick by mutableIntStateOf(0)
    var moreTick by mutableIntStateOf(0)
}

/** The menu bar of the classic looks: each word opens its menu. */
@Composable
fun MenuBar(host: MenuHost, color: Color, size: TextUnit, gap: Dp = 14.dp) {
    var open by remember { mutableStateOf<String?>(null) }
    var seenMore by remember { mutableIntStateOf(ChromeKeys.moreTick) }
    LaunchedEffect(ChromeKeys.moreTick) { if (ChromeKeys.moreTick != seenMore) { seenMore = ChromeKeys.moreTick; open = if (open == null) host.tops.firstOrNull()?.label else null } }
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
    var seenMore by remember { mutableIntStateOf(ChromeKeys.moreTick) }
    LaunchedEffect(ChromeKeys.moreTick) { if (ChromeKeys.moreTick != seenMore) { seenMore = ChromeKeys.moreTick; open = !open } }
    var extra by rememberSaveable { mutableStateOf(false) }
    val w = LocalWindowLook.current
    Box {
        MoreIcon(color, 22.dp, Modifier.clickable { open = true }.padding(horizontal = 8.dp, vertical = 2.dp))
        // the menu takes the look: its panel (the Material container colour), corners and, on the Windows looks, the edge
        ThemedMenu(open, { open = false }) {
          CompositionLocalProvider(LocalNoColourIcons provides true) {
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
