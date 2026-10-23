package com.umutk.pulse.extras.localsend

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.features.CoverAccessibilityService
import com.umutk.pulse.features.StandByOwner
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/**
 * A small round bubble over every app while LocalSend sends or receives: a ring shows the progress, it can be dragged anywhere, and a tap opens a card
 * with the file, the progress bar and pause / cancel. It goes away a moment after the transfer ends. Drawn over the apps through the accessibility
 * layer or the "display over other apps" permission (no prompt here: without either the bubble is simply not shown).
 */
object LocalSendBubble {
    private var view: ComposeView? = null
    private var owner: StandByOwner? = null
    private var host: Context? = null
    val showing get() = view != null

    /** Shows the bubble; false when nothing can draw over the apps. */
    fun show(ctx: Context): Boolean {
        if (view != null) return true
        val acc = CoverAccessibilityService.instance
        val app = ctx.applicationContext
        if (acc == null && !com.umutk.pulse.Compat.canOverlay(app)) return false
        val base: Context = acc ?: app
        val wm = base.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 360 }
        val o = StandByOwner().also { it.start() }
        val v = ComposeView(base)
        v.setViewTreeLifecycleOwner(o); v.setViewTreeSavedStateRegistryOwner(o)
        v.setContent {
            BubbleUi(onMove = { dx, dy -> lp.x += dx.toInt(); lp.y += dy.toInt(); runCatching { wm.updateViewLayout(v, lp) } }, onHide = { hide() })
        }
        view = v; owner = o; host = base
        runCatching { wm.addView(v, lp) }.onFailure { view = null; o.stop(); owner = null; host = null; return false }
        return true
    }

    fun hide() {
        val v = view ?: return
        runCatching { ((host ?: v.context).getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        owner?.stop(); owner = null; view = null; host = null
    }
}

@Composable
private fun BubbleUi(onMove: (Float, Float) -> Unit, onHide: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    val busy = LocalSendEngine.busy
    val p = LocalSendEngine.progress
    // a few seconds after the transfer ended the bubble goes
    LaunchedEffect(busy) { if (busy.isEmpty()) { delay(3000); if (LocalSendEngine.busy.isEmpty()) onHide() } }
    val ink = Color(0xFF0A84FF); val panel = Color(0xF2202124)
    Box(Modifier.pointerInput(Unit) { detectDragGestures { c, d -> c.consume(); onMove(d.x, d.y) } }) {
        if (!open) Box(Modifier.size(58.dp).clip(CircleShape).background(panel).clickable { open = true }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(50.dp)) {
                val w = 5.dp.toPx()
                drawArc(Color.White.copy(alpha = .18f), 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                drawArc(ink, -90f, 360f * p, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text(if (busy.startsWith("←")) "↓" else "↑", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        } else Column(Modifier.width(230.dp).clip(RoundedCornerShape(16.dp)).background(panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(busy.ifEmpty { tr("Done", "Bitti") }, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth(), color = ink, trackColor = Color.White.copy(alpha = .18f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${(p * 100).toInt()}%", color = Color.White.copy(alpha = .8f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (busy.isNotEmpty()) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable { LocalSendEngine.paused = !LocalSendEngine.paused }, contentAlignment = Alignment.Center) {
                        Text(if (LocalSendEngine.paused) "▶" else "❚❚", color = Color.White, fontSize = 13.sp)
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFB3261E)).clickable { LocalSendEngine.cancel() }, contentAlignment = Alignment.Center) { Text("✕", color = Color.White, fontSize = 15.sp) }
                }
                Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f)).clickable { open = false }, contentAlignment = Alignment.Center) { Text("▾", color = Color.White, fontSize = 15.sp) }
            }
        }
    }
}
