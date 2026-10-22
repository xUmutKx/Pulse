package com.umutk.pulse.extras.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
    var z by mutableIntStateOf(z0)
}

/** The windows of the Windows desktops: several at once, dragged by their title bars, the launcher behind them. */
object DesktopWindows {
    val list = mutableStateListOf<DWin>()
    private var seq = 0L
    private var top = 0

    fun open(route: String, title: String) {
        list.firstOrNull { it.start == route }?.let { front(it); return }
        val n = list.size % 6
        list += DWin(++seq, title, route, 14f + n * 22f, 20f + n * 26f, ++top)
    }
    fun front(w: DWin) { if (w.z != top) w.z = ++top }
    fun close(w: DWin) { list.remove(w) }
}

/** Draws every open window over the desktop, the newest on top. [bottom] keeps them above the taskbar. */
@Composable
fun DesktopWindowLayer(lookId: String, bottom: Dp) {
    val skin = remember(lookId) { frameSkin(lookId) }
    BoxWithConstraints(Modifier.fillMaxSize().padding(bottom = bottom)) {
        DesktopWindows.list.sortedBy { it.z }.forEach { w -> key(w.id) { DesktopWindowFrame(w, skin, maxWidth, maxHeight) } }
    }
}

@Composable
private fun DesktopWindowFrame(w: DWin, skin: WinSkin, areaW: Dp, areaH: Dp) {
    val d = LocalDensity.current.density
    val fw = if (w.max) areaW else (areaW - 20.dp).coerceAtMost(areaW * .94f)
    val fh = if (w.max) areaH else areaH * .64f
    val shape = RoundedCornerShape(if (w.max) 0.dp else skin.corner)
    Column(
        Modifier.offset(if (w.max) 0.dp else w.x.dp, if (w.max) 0.dp else w.y.dp).size(fw, fh)
            // a touch anywhere on the window brings it to the front, without taking the touch from what is inside
            .pointerInput(w.id) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false); DesktopWindows.front(w) } }
            .clip(shape).background(skin.frame).then(if (skin.kind == "95") Modifier.bevel(true) else Modifier).padding(skin.frameWidth),
    ) {
        Row(
            Modifier.fillMaxWidth().height(if (skin.kind == "95") 20.dp else if (skin.kind == "7" || skin.kind == "xp") 30.dp else 36.dp).background(skin.title).padding(start = 8.dp)
                .pointerInput(w.id) {
                    detectDragGestures { c, drag ->
                        c.consume()
                        if (!w.max) { w.x = (w.x + drag.x / d).coerceIn(-areaW.value + 80f, areaW.value - 40f); w.y = (w.y + drag.y / d).coerceIn(0f, areaH.value - 30f) }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (w.route != w.start) Box(Modifier.size(28.dp).clickable { w.route = w.start }, contentAlignment = Alignment.Center) { PIcon("back", skin.titleInk, 16.dp) }
            Text(w.title, color = skin.titleInk, fontSize = 13.sp, fontWeight = if (skin.kind == "xp" || skin.kind == "95") FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Caption(skin, 0) { DesktopWindows.close(w) }; Caption(skin, 1) { w.max = !w.max }; Caption(skin, 2) { DesktopWindows.close(w) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(skin.body).clipToBounds()) {
            CompositionLocalProvider(LocalOpenTool provides { id -> w.route = if (id.isNotEmpty()) id else w.start }) {
                SubScreen(w.route) { if (w.route != w.start) w.route = w.start else DesktopWindows.close(w) }
            }
        }
    }
}
