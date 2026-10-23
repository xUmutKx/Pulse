package com.umutk.pulse.extras.launcher

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
}

/** The group of an app by its store category; a system app without one goes to System, anything else to Other. */
fun startGroup(app: LaunchApp): StartGroup = when (app.category) {
    ApplicationInfo.CATEGORY_GAME -> StartGroup.GAMES
    ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> StartGroup.MEDIA
    ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS -> StartGroup.TALK
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> StartGroup.PRODUCTIVITY
    ApplicationInfo.CATEGORY_MAPS -> StartGroup.MAPS
    else -> if (app.system) StartGroup.SYSTEM else StartGroup.OTHER
}

/** The apps split into their groups, in group order; empty groups are left out. */
fun groupedApps(all: List<LaunchApp>): List<Pair<StartGroup, List<LaunchApp>>> =
    StartGroup.values().map { g -> g to all.filter { startGroup(it) == g } }.filter { it.second.isNotEmpty() }

/** [columns] is the drawer's column count; [longPress] opens the app's action sheet. */
class LauncherState(val apps: List<LaunchApp>, val vertical: Boolean, val columns: Int, val iconDp: Int, val dock: List<LaunchApp>,
    val open: (LaunchApp) -> Unit, val longPress: (LaunchApp) -> Unit)

/** A tap opens the app, a long press asks for its action sheet. Used by every look's icons, dock and drawer. */
fun Modifier.appTap(state: LauncherState, app: LaunchApp, onTap: () -> Unit): Modifier = composed {
    // where the finger is in the window, so the long-press menu opens right there like a context menu
    var origin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val view = androidx.compose.ui.platform.LocalView.current
    onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(app.pkg) { detectTapGestures(onTap = { onTap() }, onLongPress = { if (LauncherPrefs.haptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); LauncherSession.pressAt = origin + it; state.longPress(app) }) }
}

/** Dock = the chosen packages that are installed (max 5); none until the user pins some. */
fun dockApps(apps: List<LaunchApp>, pkgs: List<String>): List<LaunchApp> =
    pkgs.take(5).mapNotNull { p -> apps.firstOrNull { it.pkg == p } }

/** Side inset of the screen (rounded corners or a side cutout) in dp. Bars and menus keep their content clear of it; set by LauncherActivity. */
val LocalLauncherEdge = compositionLocalOf { 0.dp }

/** Start menu data: the pinned apps (only while nothing is typed) and every app whose label matches the search. */
fun startLists(state: LauncherState, query: String): Pair<List<LaunchApp>, List<LaunchApp>> =
    (if (query.isBlank()) state.dock else emptyList()) to state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

/** The start menu is opened from a look's taskbar and closed by the activity (the taskbar has no callback in the look API). */
object LauncherSession {
    /** Window position of the last long press on an app (px). */
    var pressAt = androidx.compose.ui.geometry.Offset.Zero
    /** The clock flyout (calendar and, in Windows 10 / 11, the quick tiles) is open; the taskbar clock sets it. */
    var clockOpen by mutableStateOf(false)
    var startOpen by mutableStateOf(false)
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
    )
    fun byId(id: String): LauncherLook = all.firstOrNull { it.id == (if (id == "winphone_amoled") "winphone" else id) } ?: defaultLook
}

/** App picture from the launcher icon, or a letter tile when the icon could not be read. Clipped to a squircle. */
@Composable
fun LaunchAppPicture(app: LaunchApp, size: Dp, tile: Color, letter: Color, modifier: Modifier = Modifier) {
    val shape = when (LauncherPrefs.iconShape) { "circle" -> androidx.compose.foundation.shape.CircleShape; "square" -> RoundedCornerShape(size * .06f); "rounded" -> RoundedCornerShape(size * .16f); else -> RoundedCornerShape(size * .28f) }
    val bmp = app.icon
    val n = if (LauncherPrefs.badges) LauncherBadges.counts[app.pkg] ?: 0 else 0
    Box(modifier.size(size)) {
        if (bmp != null) Image(bmp, null, Modifier.size(size).clip(shape))
        else if (app.iconKey != null) Box(Modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
            com.umutk.pulse.PIcon(app.iconKey, letter, size * .7f)
        }
        else Box(Modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
            Text(app.label.take(1).uppercase(), color = letter, fontSize = (size.value * .45f).sp)
        }
        // the notification count of the app, a red dot with the number at the top right corner
        if (n > 0) {
            val d = (size.value * .34f).coerceIn(14f, 22f)
            Box(Modifier.align(Alignment.TopEnd).size(d.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(0xFFE53935)), contentAlignment = Alignment.Center) {
                Text(if (n > 9) "9+" else n.toString(), color = Color.White, fontSize = (d * .56f).sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
            }
        }
    }
}
