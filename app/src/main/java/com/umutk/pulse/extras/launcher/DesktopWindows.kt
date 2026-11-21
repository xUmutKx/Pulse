package com.umutk.pulse.extras.launcher

import androidx.compose.ui.layout.layout
import com.umutk.pulse.Look
import com.umutk.pulse.tr
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.LocalOpenTool
import com.umutk.pulse.PIcon
import com.umutk.pulse.SubScreen
import com.umutk.pulse.bevel

/** One open window of the desktop: where it is (in dp), whether it fills the screen, and which Pulse page it shows. */
class DWin(val id: Long, val title: String, val start: String, x0: Float, y0: Float, z0: Int) {
    var route by mutableStateOf(start)
    var x by mutableFloatStateOf(x0)
    var y by mutableFloatStateOf(y0)
    var max by mutableStateOf(false)
    /** Where the window stood before it was snapped to a half of the screen (x, y, width, height in dp), so Win+Down or a drag puts it back. */
    var restore: FloatArray? = null
    /** A tool that draws its own top bars over the glass (Windows 7 File Explorer) asks for a see-through body; the rest of the window is drawn by the tool. */
    var glass by mutableStateOf(false)
    var z by mutableIntStateOf(z0)
    /** Sent to the taskbar strip: hidden until its chip is tapped. */
    var min by mutableStateOf(false)
    /** Size in dp; 0 = the default share of the screen. Changed by the grip at the bottom right. */
    var wDp by mutableFloatStateOf(0f)
    var hDp by mutableFloatStateOf(0f)
    /** The progress of what the window is doing, shown on its taskbar button: 0..1, -1 while it is not known, null when nothing is going on. */
    var progress by mutableStateOf<Float?>(null)
    var pstate by mutableStateOf(com.umutk.pulse.ProgressState.Normal)
}

/** The window the page is in (null outside the desktop's windows). */
val LocalDesktopWin = androidx.compose.runtime.compositionLocalOf<DWin?> { null }

/** Tells the window's taskbar button how far what is going on in the window has come: [frac] null = not known. Does nothing outside the desktop's windows. */
@Composable
fun TaskbarProgress(frac: Float?, state: com.umutk.pulse.ProgressState = com.umutk.pulse.ProgressState.Normal) {
    val w = LocalDesktopWin.current ?: return
    androidx.compose.runtime.DisposableEffect(w, frac, state) { w.progress = frac ?: -1f; w.pstate = state; onDispose { w.progress = null } }
}

/** The windows of the Windows desktops: several at once, dragged by their title bars, the launcher behind them. */
object DesktopWindows {
    val list = mutableStateListOf<DWin>()
    private var seq = 0L
    private var top = 0

    fun open(route: String, title: String) {
        if (route == "messages") LauncherSession.msnTray = true
        list.firstOrNull { it.start == route }?.let { it.min = false; front(it); return }
        val n = list.size % 6
        list += DWin(++seq, title, route, 14f + n * 22f, 20f + n * 26f, ++top)
    }
    /**
     * A small window at the bottom right, over the tray, like the messenger popup of the MSN years: [route] opens as usual, then it is made [wDp] x [hDp] and put by the
     * tray (above the taskbar). Held on to by its title bar it can still be moved.
     */
    fun openCompact(ctx: android.content.Context, route: String, title: String, wDp: Float, hDp: Float) {
        open(route, title)
        val w = list.firstOrNull { it.start == route } ?: return
        val dm = ctx.resources.displayMetrics
        val sw = dm.widthPixels / dm.density; val sh = dm.heightPixels / dm.density
        w.max = false; w.min = false
        w.wDp = wDp.coerceAtMost(sw - 16f); w.hDp = hDp.coerceAtMost(sh - 140f)
        w.x = (sw - w.wDp - 8f).coerceAtLeast(0f); w.y = (sh - w.hDp - 100f).coerceAtLeast(8f)
        front(w)
    }
    /** Snaps [w] to the left (-1) or right (1) half of an area [areaW] dp wide; the height is the whole area (the frame keeps it inside). */
    fun snap(w: DWin, side: Int, areaW: Float) {
        if (w.restore == null && !w.max) w.restore = floatArrayOf(w.x, w.y, w.wDp, w.hDp)
        w.max = false; w.min = false
        w.wDp = areaW / 2f; w.hDp = 10000f; w.y = 0f; w.x = if (side < 0) 0f else areaW / 2f
        front(w)
    }
    /** Puts a snapped window back where it was; true when it was snapped. */
    fun unsnap(w: DWin): Boolean {
        val r = w.restore ?: return false
        w.x = r[0]; w.y = r[1]; w.wDp = r[2]; w.hDp = r[3]; w.restore = null; w.max = false
        return true
    }
    /** One more press of Tab while Alt is held: opens the switcher on the next window, then goes on round. */
    fun altTabStep() {
        val n = list.size
        if (n < 2) return
        LauncherSession.altTabIdx = if (LauncherSession.altTabIdx < 0) 1 else (LauncherSession.altTabIdx + 1) % n
    }
    /** Alt let go: the window picked in the switcher comes to the front. */
    fun altTabCommit() {
        val i = LauncherSession.altTabIdx
        LauncherSession.altTabIdx = -1
        val w = list.toList().sortedByDescending { it.z }.getOrNull(i) ?: return
        w.min = false; front(w)
    }
    fun front(w: DWin) { if (w.z != top) w.z = ++top }
    fun minimize(w: DWin) { w.min = true }
    fun close(w: DWin) { list.remove(w) }
}

/** Draws every open window over the desktop, the newest on top. [bottom] keeps them above the taskbar. */
@Composable
fun DesktopWindowLayer(lookId: String, bottom: Dp) {
    val skin = remember(lookId) { frameSkin(lookId) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = bottom)) {
        DesktopWindows.list.filter { !it.min }.sortedBy { it.z }.forEach { w -> key(w.id) { DesktopWindowFrame(w, skin, lookId, maxWidth, maxHeight) } }
        // the minimised windows wait on the taskbar (TaskbarWindows); with the taskbar hidden they wait as small chips along the bottom
        TaskView()
        val mins = DesktopWindows.list.filter { it.min }
        if (!LauncherPrefs.dockShown && mins.isNotEmpty()) Row(Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            mins.forEach { w ->
                Row(Modifier.clip(RoundedCornerShape(skin.corner.coerceAtMost(8.dp))).background(skin.frame).clickable { w.min = false; DesktopWindows.front(w) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.title, color = skin.titleInk.takeIf { skin.kind != "7" && skin.kind != "xp" } ?: Color.White, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

/** Mica on Windows 11 and Aero glass on Windows 7: the frame and title let the desktop show through, the body stays opaque. */
private fun glass(skin: WinSkin, c: androidx.compose.ui.graphics.Color) = when (skin.kind) {
    "11" -> c.copy(alpha = .4f)
    "7" -> c.copy(alpha = .38f)
    else -> c
}

@Composable
private fun DesktopWindowFrame(w: DWin, skin: WinSkin, lookId: String, areaW: Dp, areaH: Dp) {
    val d = LocalDensity.current.density
    // the music player is a tall box, as Windows Media Player is: its width is taken from the screen and its height follows that width (it is not a stretched list)
    val fw = if (w.max) areaW else if (w.wDp > 0f) w.wDp.dp.coerceIn(160.dp, areaW) else if (w.start == "music") (areaW * .9f).coerceAtMost(360.dp) else if (w.start.startsWith("win:fun:")) (areaW * .82f).coerceAtMost(300.dp) else if (areaW > areaH) (areaW * .55f).coerceAtMost(660.dp) else (areaW - 36.dp).coerceAtMost(areaW * .88f)
    // a turned screen is wide and short: the window takes more of it, so a list of files shows more than a few rows
    val fh = if (w.max) areaH else if (w.hDp > 0f) w.hDp.dp.coerceIn(120.dp, areaH) else if (w.start == "music") (fw * 1.28f).coerceAtMost(areaH * .92f) else if (w.start.startsWith("win:fun:")) (areaH * .5f).coerceAtMost(320.dp) else areaH * (if (areaW > areaH) .8f else .56f)
    val shape = RoundedCornerShape(if (w.max) 0.dp else skin.corner)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val land = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val blurred = skin.kind == "11" || skin.kind == "7"
    // the real blur: the desktop picture, blurred, sits behind the see-through frame at the spot where the window is (Mica / Aero)
    val wall = remember(lookId, land) { if (blurred) wallpaperRes(lookId, land)?.let { r -> android.graphics.BitmapFactory.decodeResource(ctx.resources, r, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() } else null }
    Box(
        Modifier.offset(if (w.max) 0.dp else w.x.dp, if (w.max) 0.dp else w.y.dp).size(fw, fh)
            // a touch anywhere on the window brings it to the front, without taking the touch from what is inside
            .pointerInput(w.id, areaW, areaH) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial); DesktopWindows.front(w) } }
            .clip(shape).clipToBounds(),
    ) {
    if (wall != null) androidx.compose.foundation.Image(wall, null,
        // unbounded from the top left: a bare requiredSize centres a picture bigger than the window, which left the lower and the outer parts of the window without the blur
        Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(areaW, areaH).offset(if (w.max) 0.dp else (-w.x).dp, if (w.max) 0.dp else (-w.y).dp).blur(if (skin.kind == "11") 28.dp else 16.dp),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop)
    Column(
        Modifier.fillMaxSize().background(glass(skin, skin.frame)).then(if (skin.kind == "95") Modifier.bevel(true) else Modifier).padding(skin.frameWidth),
    ) {
        Row(
            Modifier.fillMaxWidth().height(if (skin.kind == "95") 20.dp else if (skin.kind == "7" || skin.kind == "xp") 30.dp else 36.dp).then(Modifier.background(skin.title, alpha = when (skin.kind) { "11" -> 0f; "7" -> .34f; else -> 1f })).padding(start = 8.dp)
                // a double tap or a long press on the title bar switches between the window and the full screen, as on Windows
                .pointerInput(w.id) { detectTapGestures(onPress = { DesktopWindows.front(w) }, onDoubleTap = { w.max = !w.max }, onLongPress = { w.max = !w.max }) }
                .pointerInput(w.id, areaW, areaH) {
                    detectDragGestures(onDragEnd = {
                        // let go at an edge of the screen: the window snaps to that half (the top edge makes it full screen), as in Windows
                        if (!w.max && LauncherPrefs.dragSnap) {
                            if (w.x < -fw.value * .3f) DesktopWindows.snap(w, -1, areaW.value)
                            else if (w.x + fw.value > areaW.value + fw.value * .3f) DesktopWindows.snap(w, 1, areaW.value)
                            else if (w.y <= 0f) { w.restore = floatArrayOf(w.x, 20f, w.wDp, w.hDp); w.max = true }
                        }
                    }) { c, drag ->
                        c.consume()
                        if (!w.max) { w.x = (w.x + drag.x / d).coerceIn(-areaW.value + 80f, areaW.value - 40f); w.y = (w.y + drag.y / d).coerceIn(0f, areaH.value - 30f) }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (w.route != w.start) Box(Modifier.size(28.dp).clickable { w.route = w.start }, contentAlignment = Alignment.Center) { PIcon("back", skin.titleInk, 16.dp) }
            Text(w.title, color = skin.titleInk, fontSize = 13.sp, fontWeight = if (skin.kind == "xp" || skin.kind == "95") FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Caption(skin, 0) { DesktopWindows.minimize(w) }; Caption(skin, 1) { w.max = !w.max }; Caption(skin, 2) { DesktopWindows.close(w) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(when {
            // a tool that tints itself (Windows 7 Explorer, Windows 11 Settings) gets a bare body; every other tool's body is see-through too, over the blurred desktop
            // (as the Translucent Windows mod of Windhawk does on a real PC: File Explorer, Task Manager, Control Panel all show the picture behind them)
            (skin.kind == "7" || skin.kind == "11") && w.glass -> Color.Transparent
            skin.kind == "11" -> skin.body.copy(alpha = .58f)
            skin.kind == "7" -> skin.body.copy(alpha = .7f)
            else -> skin.body
        }).clipToBounds()) {
            // Windows 11: the black of an app's page becomes dark glass (the lighter things stay solid), so every app in a window shows the blurred desktop behind it, as the launcher does
            val glassy = skin.kind == "11" && LauncherPrefs.glassApps && android.os.Build.VERSION.SDK_INT >= 31 && !w.glass
            Box(Modifier.fillMaxSize().then(if (glassy) Modifier.graphicsLayer { renderEffect = darkGlassEffect(); compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen } else Modifier)) {
                // a window narrower than a phone lays its page out at the width of one and shows it scaled down: more of the page is in view, nothing is cut or squeezed
                val fitRef = 400.dp
                val fit = if (LauncherPrefs.fitWindows && !w.max && fw < fitRef) (fw / fitRef).coerceAtLeast(.5f) else 1f
                Box(Modifier.fillMaxSize().then(if (fit < 1f) Modifier.layout { m, c ->
                    val p = m.measure(androidx.compose.ui.unit.Constraints.fixed((c.maxWidth / fit).toInt(), (c.maxHeight / fit).toInt()))
                    layout(c.maxWidth, c.maxHeight) { p.placeWithLayer(0, 0) { scaleX = fit; scaleY = fit; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) } }
                } else Modifier)) {
                    CompositionLocalProvider(LocalOpenTool provides { id -> w.route = if (id.isNotEmpty()) id else w.start }, LocalDesktopWin provides w) {
                        SubScreen(w.route) { if (w.route != w.start) w.route = w.start else DesktopWindows.close(w) }
                    }
                }
            }
            // the grip at the bottom right resizes the window
            if (!w.max) Box(Modifier.align(Alignment.BottomEnd).size(28.dp).pointerInput(w.id, areaW, areaH) {
                detectDragGestures { c, drag ->
                    c.consume()
                    if (w.wDp <= 0f) { w.wDp = fw.value; w.hDp = fh.value }
                    w.wDp = (w.wDp + drag.x / d).coerceIn(160f, areaW.value); w.hDp = (w.hDp + drag.y / d).coerceIn(120f, areaH.value)
                }
            }, contentAlignment = Alignment.BottomEnd) {
                androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
                    val c = Color(0x99808080)
                    for (k in 1..3) drawLine(c, androidx.compose.ui.geometry.Offset(size.width, size.height * k / 4f + size.height / 4f * 0), androidx.compose.ui.geometry.Offset(size.width * (4 - k) / 4f, size.height), 2f)
                }
            }
        }
    }
    }
}


private var darkGlass: androidx.compose.ui.graphics.RenderEffect? = null

/** A colour filter that sets each pixel's opacity from its brightness: black stays 35 percent opaque (dark glass), anything lighter than a dark grey is solid. Android 12 and later. */
@androidx.annotation.RequiresApi(31)
private fun darkGlassEffect(): androidx.compose.ui.graphics.RenderEffect {
    darkGlass?.let { return it }
    val m = android.graphics.ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        1.794f, 3.522f, .684f, 0f, 89f,
    ))
    return android.graphics.RenderEffect.createColorFilterEffect(android.graphics.ColorMatrixColorFilter(m)).asComposeRenderEffect().also { darkGlass = it }
}


@OptIn(ExperimentalLayoutApi::class)
/** Win+Tab: every open window as a card over the desktop; a tap brings that window to the front, the cross closes it, a tap outside leaves. */
@Composable
private fun TaskView() {
    val alt = LauncherSession.altTabIdx
    if (!LauncherSession.taskViewOpen && alt < 0) return
    val wins = DesktopWindows.list.toList().sortedByDescending { it.z }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { LauncherSession.taskViewOpen = false; LauncherSession.altTabIdx = -1 },
        contentAlignment = Alignment.Center) {
        if (wins.isEmpty()) Text(tr("No open windows", "Açık pencere yok"), color = Color.White, fontSize = 16.sp)
        else androidx.compose.foundation.layout.FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            wins.forEachIndexed { wi, w ->
                Column(Modifier.width(150.dp).height(100.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2B2B2B)).border(if (wi == alt) 3.dp else 1.dp, if (wi == alt) Look.accent else Color.White.copy(alpha = .25f), RoundedCornerShape(8.dp))
                    .clickable { w.min = false; DesktopWindows.front(w); LauncherSession.taskViewOpen = false }.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(w.title, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Box(Modifier.size(22.dp).clickable { DesktopWindows.close(w) }, contentAlignment = Alignment.Center) { PIcon("close", Color.White, 14.dp) }
                    }
                    Text(if (w.min) tr("Minimised", "Küçültüldü") else tr("Open", "Açık"), color = Color.White.copy(alpha = .6f), fontSize = 11.sp)
                }
            }
        }
    }
}
