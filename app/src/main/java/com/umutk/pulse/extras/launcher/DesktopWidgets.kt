package com.umutk.pulse.extras.launcher

import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.umutk.pulse.PIcon
import com.umutk.pulse.PButton
import com.umutk.pulse.WinAlert
import com.umutk.pulse.T
import com.umutk.pulse.tr
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.withTimeoutOrNull

/** x, y, width, height of a widget on the desktop, in dp. */
private fun posOf(id: Int, i: Int, minW: Int, minH: Int): FloatArray =
    LauncherPrefs.widgetPos[id]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.toFloatArray()
        ?: floatArrayOf(12f + (i % 3) * 12f, 96f + i * 40f, minW.coerceAtLeast(110).toFloat(), minH.coerceAtLeast(110).toFloat())

/**
 * The widgets of the Windows desktops, placed where the user wants them: a long press puts a widget in its edit mode (a grip moves it, the cross removes it, the
 * corner resizes it, the gear opens its options). The positions are kept per widget in the launcher settings.
 */
@Composable
fun DesktopWidgetLayer() {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    val mgr = AppWidgetManager.getInstance(ctx)
    val placed = LauncherPrefs.widgets
    DisposableEffect(host) { host.startListening(); onDispose { host.stopListening() } }
    if (placed.isEmpty()) return
    placedWidgets().forEachIndexed { i, w ->
        val info = mgr.getAppWidgetInfo(w.id) ?: return@forEachIndexed
        key(w.id) { DesktopWidget(w, i, info) }
    }
}

/** Which widget is in edit mode (a placed widget's id, -1 the clock); null when none. A long press on a widget sets it, the check mark clears it. */
object WidgetEdit { var id by mutableStateOf<Int?>(null) }

/** A long press that is only watched, never consumed: the widget under it keeps getting its touches. */
internal fun Modifier.longPressEdit(key: Any, enabled: Boolean = true, onLong: () -> Unit): Modifier = pointerInput(key, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val timeout = viewConfiguration.longPressTimeoutMillis
        val slop = viewConfiguration.touchSlop
        val held = withTimeoutOrNull<Unit>(timeout) {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull
                if (!c.pressed || (c.position - down.position).getDistance() > slop) return@withTimeoutOrNull
            }
        }
        if (held == null) onLong()
    }
}

/** The round grip of the edit mode. */
@Composable
internal fun EditDot(icon: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Box(modifier.size(26.dp).clip(CircleShape).background(Color(0xCC000000)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier), contentAlignment = Alignment.Center) {
        PIcon(icon, Color.White, 15.dp)
    }
}

/** The options of one widget: its size in steps, back to the first size, and taking it off the desktop. */
@Composable
private fun WidgetOptions(w: Float, h: Float, onSize: (Float, Float) -> Unit, onReset: () -> Unit, onRemove: () -> Unit, onClose: () -> Unit) {
    WinAlert(onDismissRequest = onClose,
        title = { Text(tr("Widget options", "Widget seçenekleri")) },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                listOf(Triple(tr("Width", "Genişlik"), w, true), Triple(tr("Height", "Yükseklik"), h, false)).forEach { (name, v, isW) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$name  ${v.toInt()}", modifier = Modifier.weight(1f))
                        PButton("−") { if (isW) onSize((w - 20f).coerceAtLeast(80f), h) else onSize(w, (h - 20f).coerceAtLeast(60f)) }
                        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                        PButton("+") { if (isW) onSize((w + 20f).coerceAtMost(1200f), h) else onSize(w, (h + 20f).coerceAtMost(1600f)) }
                    }
                }
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    PButton(tr("Reset size", "Boyutu sıfırla")) { onReset() }
                    PButton(tr("Remove", "Kaldır")) { onRemove() }
                }
            }
        },
        confirmButton = { PButton(tr("Close", "Kapat"), onClick = onClose) })
}

@Composable
private fun DesktopWidget(w: PlacedWidget, index: Int, info: android.appwidget.AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    val d = LocalDensity.current.density
    val p = posOf(w.id, index, info.minWidth, info.minHeight)
    fun put(x: Float, y: Float, wd: Float, hd: Float) { LauncherPrefs.widgetPos = LauncherPrefs.widgetPos + (w.id to "$x,$y,$wd,$hd") }
    val editing = WidgetEdit.id == w.id && !LauncherPrefs.lockLayout
    var options by remember { mutableStateOf(false) }
    Box(Modifier.offset(p[0].dp, p[1].dp).size(p[2].dp, p[3].dp).longPressEdit(w.id, !LauncherPrefs.lockLayout) { WidgetEdit.id = w.id }) {
        AndroidView(
            factory = { c -> host.createView(c, w.id, info) },
            update = { it.setAppWidget(w.id, info); it.updateAppWidgetSize(null, p[2].toInt(), p[3].toInt(), p[2].toInt(), p[3].toInt()) },
            modifier = Modifier.fillMaxSize(),
        )
        // the controls exist only while the widget is in edit mode
        if (editing) {
            Box(Modifier.matchParentSize().border(2.dp, Color(0xCCFFFFFF)))
            EditDot("gridView", Modifier.align(Alignment.TopStart).pointerInput(w.id) {
                detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = posOf(w.id, index, info.minWidth, info.minHeight); put(q[0] + drag.x / d, (q[1] + drag.y / d).coerceAtLeast(0f), q[2], q[3]) }
            })
            EditDot("close", Modifier.align(Alignment.TopEnd)) { WidgetEdit.id = null; removeWidget(ctx, w.id) }
            EditDot("check", Modifier.align(Alignment.TopCenter)) { WidgetEdit.id = null }
            EditDot("settings", Modifier.align(Alignment.BottomStart)) { options = true }
            EditDot("scale", Modifier.align(Alignment.BottomEnd).pointerInput(w.id) {
                detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = posOf(w.id, index, info.minWidth, info.minHeight); put(q[0], q[1], (q[2] + drag.x / d).coerceAtLeast(80f), (q[3] + drag.y / d).coerceAtLeast(60f)) }
            })
        }
    }
    if (options) WidgetOptions(p[2], p[3], { nw, nh -> put(p[0], p[1], nw, nh); LauncherPrefs.save(ctx) },
        { LauncherPrefs.widgetPos = LauncherPrefs.widgetPos - w.id; LauncherPrefs.save(ctx) },
        { options = false; WidgetEdit.id = null; removeWidget(ctx, w.id) }, { options = false })
}
