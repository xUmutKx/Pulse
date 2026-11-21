package com.umutk.pulse.extras.launcher

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.composed
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class LaunchApp(val label: String, val pkg: String, val icon: ImageBitmap?, val installed: Long = 0L, val system: Boolean = false,
    /** The app's own store category (ApplicationInfo.CATEGORY_*), -1 when it has none. */
    val category: Int = -1,
    /** A PIcon key for the entries that are not apps (the desktop shortcuts): drawn in the look's own icon set, so every look has them. */
    val iconKey: String? = null)

/** Start menu groups, in the order they are listed. */
enum class StartGroup(val en: String, val tr: String) {
    GAMES("Games", "Oyunlar"), MEDIA("Media", "Medya"), TALK("Communication", "İletişim"), PRODUCTIVITY("Productivity", "Üretkenlik"),
    MAPS("Maps and travel", "Harita ve seyahat"), SYSTEM("System", "Sistem"), OTHER("Other", "Diğer"),
    /** Pulse's joke apps: their own group, last in the list, so they do not mix with the real programs. */
    FUN("Fun apps", "Eğlence uygulamaları"),
}

/** The group of an app by its store category; a system app without one goes to System, anything else to Other. */
fun startGroup(app: LaunchApp): StartGroup = if (app.pkg.startsWith("pulse:x:win:fun:")) StartGroup.FUN else when (app.category) {
    ApplicationInfo.CATEGORY_GAME -> StartGroup.GAMES
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> StartGroup.MEDIA
    ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS -> StartGroup.TALK
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> StartGroup.PRODUCTIVITY
    ApplicationInfo.CATEGORY_MAPS -> StartGroup.MAPS
    else -> if (app.system) StartGroup.SYSTEM else StartGroup.OTHER
}

/** The apps split into their groups, in group order; empty groups are left out. */
fun groupedApps(all: List<LaunchApp>): List<Pair<StartGroup, List<LaunchApp>>> =
    // the joke apps are on the desktop only, not in the lists of the Start menus
    StartGroup.values().filter { it != StartGroup.FUN }.map { g -> g to all.filter { startGroup(it) == g } }.filter { it.second.isNotEmpty() }

/** [columns] is the drawer's column count; [longPress] opens the app's action sheet. */
class LauncherState(val apps: List<LaunchApp>, val vertical: Boolean, val columns: Int, val iconDp: Int, val dock: List<LaunchApp>,
    val open: (LaunchApp) -> Unit, val longPress: (LaunchApp) -> Unit)

/** A tap opens the app, a long press asks for its action sheet. Used by every look's icons, dock and drawer. */
fun Modifier.appTap(state: LauncherState, app: LaunchApp, onTap: () -> Unit): Modifier = composed {
    // where the finger is in the window, so the long-press menu opens right there like a context menu
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val view = androidx.compose.ui.platform.LocalView.current
    // the small show of a tapped icon (a setting): zoom, flip, rotate or bounce
    val prog = remember { androidx.compose.animation.core.Animatable(0f) }
    val anim = androidx.compose.runtime.rememberCoroutineScope()
    onGloballyPositioned { origin = it.positionInRoot() }
        .graphicsLayer {
            val t = prog.value
            when (if (LauncherPrefs.classicWin) "none" else LauncherPrefs.iconAnim) {
                "zoom" -> { val k = 1f + .18f * kotlin.math.sin(Math.PI.toFloat() * t); scaleX = k; scaleY = k }
                "flip" -> { rotationY = 360f * t; cameraDistance = 12f * density }
                "rotate" -> rotationZ = 360f * t
                "bounce" -> translationY = -22f * density * kotlin.math.sin(Math.PI.toFloat() * t)
            }
        }
        .pointerInput(app.pkg) { detectTapGestures(onTap = { if (LauncherPrefs.iconAnim != "none" && !LauncherPrefs.classicWin) anim.launch { prog.snapTo(0f); prog.animateTo(1f, androidx.compose.animation.core.tween(300)) }; onTap() }, onLongPress = { if (LauncherPrefs.haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); LauncherSession.pressAt = origin + it; state.longPress(app) }) }
}

/** A tap opens the app; a long press asks for its menu only when [menuOnLongPress] (the desktop moves an icon with the long press, and opens the menu when the finger lifts without having moved it). */
fun Modifier.tapOnly(onTap: () -> Unit, menuOnLongPress: (() -> Unit)? = null): Modifier = this.pointerInput(menuOnLongPress != null) {
    detectTapGestures(onTap = { onTap() }, onLongPress = menuOnLongPress?.let { m -> { m() } })
}

/** Dock = the chosen packages that are installed (max 5); none until the user pins some. */
fun dockApps(apps: List<LaunchApp>, pkgs: List<String>): List<LaunchApp> =
    pkgs.take(LauncherPrefs.dockCount).mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

/** Side inset of the screen (rounded corners or a side cutout) in dp. Bars and menus keep their content clear of it; set by LauncherActivity. */
val LocalLauncherEdge = compositionLocalOf { 0.dp }

/** Start menu data: the pinned apps (only while nothing is typed) and every app whose label matches the search. */
fun startLists(state: LauncherState, query: String): Pair<List<LaunchApp>, List<LaunchApp>> =
    (if (query.isBlank()) state.dock else emptyList()) to (state.apps.filter { !it.pkg.startsWith("pulse:x:win:fun:") && it.label.contains(query.trim(), ignoreCase = true) } + windowsTools(query))

/**
 * The tools of Windows 10 and 11 in the Start menu's list and search (Command Prompt, Control Panel, Registry Editor, Task Manager, Services, Run ...), as the real Start menu has them
 * under Windows Tools; the older looks keep their own lists. An app that has the same name is not listed twice.
 */
fun windowsTools(query: String): List<LaunchApp> {
    val modern = LauncherPrefs.look == "win11" || LauncherPrefs.look == "win10" || LauncherPrefs.look == "win10black"
    if (!modern) return emptyList()
    val tools = listOf(
        LaunchApp(tr("Command Prompt", "Komut İstemi"), "pulse:cmd", null, iconKey = "run"),
        LaunchApp(tr("Control Panel", "Denetim Masası"), "pulse:settings", null, iconKey = "settings"),
        LaunchApp(tr("Registry Editor", "Kayıt Defteri Düzenleyicisi"), "pulse:regedit", null, iconKey = "services"),
        LaunchApp(tr("Task Manager", "Görev Yöneticisi"), "pulse:taskmgr", null, iconKey = "processes"),
        LaunchApp(tr("Services", "Hizmetler"), "pulse:services", null, iconKey = "services"),
        LaunchApp(tr("Startup apps", "Başlangıç uygulamaları"), "pulse:startup", null, iconKey = "startup"),
    )
    return tools.filter { it.label.contains(query.trim(), ignoreCase = true) }
}

/** The start menu is opened from a look's taskbar and closed by the activity (the taskbar has no callback in the look API). */
object LauncherSession {
    /** The hidden taskbar is shown for a moment (a tap or a swipe on the screen edge); it hides again by itself. */
    var taskbarPeek by androidx.compose.runtime.mutableStateOf(false)
    /** Window position of the last long press on an app (px). */
    var pressAt = androidx.compose.ui.geometry.Offset.Zero
    /** The clock flyout (calendar and, in Windows 10 / 11, the quick tiles) is open; the taskbar clock sets it. */
    var clockOpen by mutableStateOf(false)
    var startOpen by mutableStateOf(false)
    /** Windows 11: the quick settings flyout (the connection / sound / battery button of the tray) and the search flyout (the search button of the taskbar). */
    var quickOpen by mutableStateOf(false)
    var w11SearchOpen by mutableStateOf(false)
    /** MSN (Messages) was opened: it keeps a little icon in the Windows 7 tray, as if it ran in the background. */
    var msnTray by mutableStateOf(false)
    /** The Run box (Win+R of Windows): type a command (cmd, regedit, taskmgr, control, notepad ...) and it opens. */
    var runOpen by mutableStateOf(false)
    /** Win+Tab: every window as a card (task view). */
    var taskViewOpen by mutableStateOf(false)
    /** The Windows lock screen (Win+L, Lock), the clipboard panel (Win+V), and the Shutting down / Restarting screen ("off" or "restart"; empty = none). */
    var lockOpen by mutableStateOf(false)
    var clipOpen by mutableStateOf(false)
    var powerScreen by mutableStateOf("")
    /** Alt+Tab: the card picked in the switcher while Alt is held; -1 = the switcher is closed. */
    var altTabIdx by mutableStateOf(-1)
    /** The Win+X menu (the Start button held): the tools of the system in a short list. */
    var winXOpen by mutableStateOf(false)
    /** The news page (a gesture action) is open. */
    var newsOpen by mutableStateOf(false)
    /** The hidden-icons flyout of the Windows 7 tray (the arrow pointing up) is open. */
    var trayOpen by mutableStateOf(false)
    /** The Windows Phone glance page (behind the Windows button) is open. */
    /** The boot and sign-in screens were passed in this launcher process. */
    var booted by mutableStateOf(false)
    /** What the next start of the launcher shows first: 1 = the boot (BIOS, then the start-up screen: Restart), 2 = the sign-in screen (Sign out). Read once, then 0. */
    var forceBoot = 0
    var feedOpen by mutableStateOf(false)
    /** The quick search (a swipe down on the home) is open. */
    var searchOpen by mutableStateOf(false)
    /** Bumped on every home press, so the pages scroll back to the first one. */
    var homeTick by mutableStateOf(0)
}

interface LauncherLook {
    val id: String
    val nameEn: String
    val nameTr: String
    val taskbarHeight: Dp
    val taskbarTop: Boolean
    /** True when the look draws its own search field in its apps list; otherwise the launcher puts one above it. */
    val ownSearch: Boolean get() = false
    @Composable fun Background(modifier: Modifier, content: @Composable () -> Unit)
    @Composable fun Taskbar(state: LauncherState, modifier: Modifier)
    @Composable fun StartMenu(state: LauncherState, onClose: () -> Unit, modifier: Modifier)
    @Composable fun IconCell(app: LaunchApp, iconDp: Int, modifier: Modifier)
    /** A dock down the side of the screen, for looks that have no taskbar. Looks with a taskbar keep the default, which draws nothing. */
    @Composable fun SideDock(state: LauncherState, modifier: Modifier) {}
}

object LauncherLooks {
    // add looks here
    private val defaultLook = MaterialLauncherLook("material", "Material You", "Material You", black = false)
    // the Pulse themes are drawn by the Material look code, which reads its colours from the theme of the same id
    val all: List<LauncherLook> = listOf(
        defaultLook, MaterialLauncherLook("amoled", "AMOLED", "AMOLED", black = true),
        Win11LauncherLook(), Win10LauncherLook(),
        Win7LauncherLook(), Win95LauncherLook(), XpLauncherLook(),
        // one Windows Phone look, on pure black (the grey one is gone; a saved "winphone_amoled" resolves to it)
        WinPhoneLauncherLook("winphone", "Windows Phone", "Windows Phone", black = true),
        MaterialLauncherLook("oneui", "One UI", "One UI", black = false),
        MaterialLauncherLook("coloros", "ColorOS", "ColorOS", black = false),
        TerminalLauncherLook(),
    )
    fun byId(id: String): LauncherLook = all.firstOrNull { it.id == (if (id == "winphone_amoled") "winphone" else id) } ?: defaultLook
}

/** App picture from the launcher icon, or a letter tile when the icon could not be read. Clipped to a squircle. */
@Composable
fun LaunchAppPicture(app: LaunchApp, size: Dp, tile: Color, letter: Color, modifier: Modifier = Modifier) {
    // the Windows 7 look draws the desktop's own items (This PC, Recycle Bin, Explorer, Paint, the players ...) with the pictures of the Windows 7 pack
    val w7pic = if (app.icon == null && com.umutk.pulse.T.id == "win7") LAUNCH7[app.pkg]?.let { com.umutk.pulse.features.ThemeIcons.bitmap(androidx.compose.ui.platform.LocalContext.current, "win7", it) } else null
    val shape = when (LauncherPrefs.iconShape) { "circle" -> androidx.compose.foundation.shape.CircleShape; "square" -> RoundedCornerShape(size * .06f); "rounded" -> RoundedCornerShape(size * .16f); else -> RoundedCornerShape(size * .28f) }
    // a picture the person chose for this app wins over its own icon and the icon pack's
    val lctx = androidx.compose.ui.platform.LocalContext.current
    val own = remember(app.pkg, AppIcons.rev) { AppIcons.bitmap(lctx, app.pkg) }
    val bmp = own ?: app.icon
    val n = if (LauncherPrefs.badges) LauncherBadges.counts[app.pkg] ?: 0 else 0
    Box(modifier.size(size)) {
        if (bmp != null) Image(bmp, null, Modifier.size(size).clip(shape))
        else if (w7pic != null) Image(w7pic, null, Modifier.size(size))
        else if (app.iconKey != null && tile.alpha == 0f) com.umutk.pulse.PIcon(app.iconKey, letter, size * .95f)   // no tile behind the icon (the old Windows desktops)
        else if (app.iconKey != null) Box(Modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
            com.umutk.pulse.PIcon(app.iconKey, letter, size * .7f)
        }
        else Box(Modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = letter, fontSize = (size.value * .45f).sp)
        }
        // the notification count of the app, a red dot with the number at the top right corner
        if (n > 0) {
            val d = (size.value * .34f).coerceIn(14f, 22f)
            val dot = LauncherPrefs.badgeStyle == "dot"
            Box(Modifier.align(Alignment.TopEnd).size((if (dot) d * .6f else d).dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                if (!dot) Text(if (n > 9) "9+" else n.toString(), color = Color.White, fontSize = (d * .56f).sp, lineHeight = (d * .56f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    // no extra space above or below the digits: they sit in the middle of the dot
                    style = androidx.compose.ui.text.TextStyle(platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false), lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.Both)))
            }
        }
    }
}


/** The picture of the Windows 7 pack (assets/icons/win7/<key>.webp) of each built-in item of the desktop. */
private val LAUNCH7 = mapOf(
    "pulse:pc" to "l_pc", "pulse:recycle" to "l_recycle", "pulse:files" to "l_files", "pulse:paint" to "l_paint", "pulse:cmd" to "l_cmd", "pulse:taskmgr" to "l_taskmgr",
    "pulse:settings" to "l_cp", "pulse:winsettings" to "l_settings", "pulse:store" to "l_store", "pulse:control" to "l_control", "pulse:perf" to "l_perf",
    "pulse:games" to "l_games", "pulse:mines" to "l_mines", "pulse:solitaire" to "l_solitaire", "pulse:freecell" to "l_freecell",
    "pulse:x:music" to "l_music", "pulse:x:video" to "l_video", "pulse:x:notes" to "l_notes", "pulse:x:weather" to "l_weather", "pulse:x:calculator" to "l_calc",
    "pulse:x:voice" to "l_voice", "pulse:x:whitenoise" to "l_whitenoise", "pulse:x:screenrec" to "l_screenrec", "pulse:x:notifhist" to "l_notifhist",
    "pulse:x:messages" to "l_messages", "pulse:x:toolkit" to "l_toolkit", "pulse:x:win:notepad" to "l_notepad", "pulse:x:win:snake" to "l_snake",
    "pulse:x:win:2048" to "l_snake", "pulse:x:win:tictactoe" to "l_snake",
)
