package com.umutk.pulse.extras.launcher

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

data class LaunchApp(val label: String, val pkg: String, val icon: ImageBitmap?, val installed: Long = 0L, val system: Boolean = false)

/** [columns] is the drawer's column count; [longPress] opens the app's action sheet. */
class LauncherState(val apps: List<LaunchApp>, val vertical: Boolean, val columns: Int, val iconDp: Int, val dock: List<LaunchApp>,
    val open: (LaunchApp) -> Unit, val longPress: (LaunchApp) -> Unit)

/** A tap opens the app, a long press asks for its action sheet. Used by every look's icons, dock and drawer. */
fun Modifier.appTap(state: LauncherState, app: LaunchApp, onTap: () -> Unit): Modifier =
    pointerInput(app.pkg) { detectTapGestures(onTap = { onTap() }, onLongPress = { state.longPress(app) }) }

/** Dock = the chosen packages that are installed (max 5), or the first 3 installed apps when none is chosen. */
fun dockApps(apps: List<LaunchApp>, pkgs: List<String>): List<LaunchApp> =
    pkgs.take(5).mapNotNull { p -> apps.firstOrNull { it.pkg == p } }.ifEmpty { apps.take(3) }

/** Side inset of the screen (rounded corners or a side cutout) in dp. Bars and menus keep their content clear of it; set by LauncherActivity. */
val LocalLauncherEdge = compositionLocalOf { 0.dp }

/** Start menu data: the pinned apps (only while nothing is typed) and every app whose label matches the search. */
fun startLists(state: LauncherState, query: String): Pair<List<LaunchApp>, List<LaunchApp>> =
    (if (query.isBlank()) state.dock else emptyList()) to state.apps.filter { it.label.contains(query.trim(), ignoreCase = true) }

/** The start menu is opened from a look's taskbar and closed by the activity (the taskbar has no callback in the look API). */
object LauncherSession {
    var startOpen by mutableStateOf(false)
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
}

object LauncherLooks {
    // add looks here
    private val defaultLook = MaterialLauncherLook("material", "Material You", "Material You", black = false)
    // the Pulse themes are drawn by the Material look code, which reads its colours from the theme of the same id
    val all: List<LauncherLook> = listOf(
        defaultLook, MaterialLauncherLook("amoled", "AMOLED", "AMOLED", black = true),
        Win11LauncherLook(), Win10LauncherLook(), Win7LauncherLook(), Win95LauncherLook(), XpLauncherLook(),
        MaterialLauncherLook("oneui", "One UI", "One UI", black = false),
        MaterialLauncherLook("coloros", "ColorOS", "ColorOS", black = false),
        MaterialLauncherLook("aurora", "Aurora", "Aurora", black = false),
        MaterialLauncherLook("sakura", "Sakura", "Sakura", black = false),
        MaterialLauncherLook("ocean", "Ocean", "Okyanus", black = false),
        MaterialLauncherLook("ember", "Ember", "Kor", black = false),
        MaterialLauncherLook("frost", "Frost", "Buz", black = false),
    )
    fun byId(id: String): LauncherLook = all.firstOrNull { it.id == id } ?: defaultLook
}

/** App picture from the launcher icon, or a letter tile when the icon could not be read. Clipped to a squircle. */
@Composable
fun LaunchAppPicture(app: LaunchApp, size: Dp, tile: Color, letter: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * .28f)
    val bmp = app.icon
    if (bmp != null) Image(bmp, null, modifier.size(size).clip(shape))
    else Box(modifier.size(size).clip(shape).background(tile), contentAlignment = Alignment.Center) {
        Text(app.label.take(1).uppercase(), color = letter, fontSize = (size.value * .45f).sp)
    }
}
