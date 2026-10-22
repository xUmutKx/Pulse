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

/** x, y, width, height of a widget on the desktop, in dp. */
private fun posOf(id: Int, i: Int, minW: Int, minH: Int): FloatArray =
    LauncherPrefs.widgetPos[id]?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 4 }?.toFloatArray()
        ?: floatArrayOf(12f + (i % 3) * 12f, 96f + i * 40f, minW.coerceAtLeast(110).toFloat(), minH.coerceAtLeast(110).toFloat())

/**
 * The widgets of the Windows desktops, placed where the user wants them: a grip at the top left moves a widget, the cross removes it and the grip at the
 * bottom right resizes it. The positions are kept per widget in the launcher settings.
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

@Composable
private fun DesktopWidget(w: PlacedWidget, index: Int, info: android.appwidget.AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val host = LauncherWidgetHost.get(ctx)
    val d = LocalDensity.current.density
    val p = posOf(w.id, index, info.minWidth, info.minHeight)
    fun put(x: Float, y: Float, wd: Float, hd: Float) { LauncherPrefs.widgetPos = LauncherPrefs.widgetPos + (w.id to "$x,$y,$wd,$hd") }
    Box(Modifier.offset(p[0].dp, p[1].dp).size(p[2].dp, p[3].dp)) {
        AndroidView(
            factory = { c -> host.createView(c, w.id, info) },
            update = { it.setAppWidget(w.id, info); it.updateAppWidgetSize(null, p[2].toInt(), p[3].toInt(), p[2].toInt(), p[3].toInt()) },
            modifier = Modifier.fillMaxSize(),
        )
        fun dot(m: Modifier) = m.size(24.dp).clip(CircleShape).background(Color(0x99000000))
        // move, remove and resize are off while the layout is locked
        if (!LauncherPrefs.lockLayout) Box(dot(Modifier.align(Alignment.TopStart).pointerInput(w.id) {
            detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = posOf(w.id, index, info.minWidth, info.minHeight); put(q[0] + drag.x / d, (q[1] + drag.y / d).coerceAtLeast(0f), q[2], q[3]) }
        }), contentAlignment = Alignment.Center) { PIcon("gridView", Color.White, 14.dp) }
        if (!LauncherPrefs.lockLayout) Box(dot(Modifier.align(Alignment.TopEnd).clickable { removeWidget(ctx, w.id) }), contentAlignment = Alignment.Center) { PIcon("close", Color.White, 14.dp) }
        if (!LauncherPrefs.lockLayout) Box(dot(Modifier.align(Alignment.BottomEnd).pointerInput(w.id) {
            detectDragGestures(onDragEnd = { LauncherPrefs.save(ctx) }) { c, drag -> c.consume(); val q = posOf(w.id, index, info.minWidth, info.minHeight); put(q[0], q[1], (q[2] + drag.x / d).coerceAtLeast(80f), (q[3] + drag.y / d).coerceAtLeast(60f)) }
        }), contentAlignment = Alignment.Center) { PIcon("scale", Color.White, 14.dp) }
    }
}
