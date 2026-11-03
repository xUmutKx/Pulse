package com.umutk.pulse.extras.video

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.PIcon
import com.umutk.pulse.tr
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

private class MiniOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry
    fun start() { ctl.performRestore(Bundle()); reg.currentState = Lifecycle.State.RESUMED }
    fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
}

/**
 * The mini video window: a rounded player above other apps. It keeps playing after the video screen closes; drag it anywhere,
 * it snaps to the nearest side edge; throw it down to close it. A double tap on the left or right half moves 10 s back or forward.
 */
object FloatingVideo {
    private var view: ComposeView? = null
    private var owner: MiniOwner? = null
    private var held: Player? = null

    /** True when the mini window is playing [p]: the video screen must not release it. */
    fun owns(p: Player): Boolean = held === p

    /** Opens the mini window for [p]. False when it is open already or the "display over other apps" permission is missing (the settings open then). */
    fun show(ctx: Context, p: Player, size: VideoSize?): Boolean {
        val app = ctx.applicationContext
        if (view != null) return false
        if (!com.umutk.pulse.Compat.canOverlay(app)) {
            app.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = app.resources.displayMetrics
        val portrait = size != null && size.width > 0 && size.height > size.width
        val w = if (portrait) (d.widthPixels * .42f).toInt() else (d.widthPixels * .62f).toInt()
        val h = if (portrait) (w * 16f / 9f).toInt() else (w * 9f / 16f).toInt()
        val margin = (12 * d.density).toInt()
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = d.widthPixels - w - margin; y = d.heightPixels / 3 }
        val o = MiniOwner().also { it.start() }
        val v = ComposeView(app).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            // no activity behind this window: the back dispatcher the compose code may ask for is its own
            val back = object : androidx.activity.OnBackPressedDispatcherOwner {
                override val lifecycle: Lifecycle get() = o.lifecycle
                override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
            }
            setContent {
                androidx.compose.runtime.CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides back) {
                    MiniBody(
                        p = p,
                        w = with(LocalDensity.current) { w.toDp() },
                        h = with(LocalDensity.current) { h.toDp() },
                        onDrag = { dx, dy -> lp.x += dx.toInt(); lp.y += dy.toInt(); runCatching { wm.updateViewLayout(this, lp) } },
                        onEnd = { vx, vy ->
                            val screenW = d.widthPixels
                            val screenH = d.heightPixels
                            if (vy > 2200f && lp.y > screenH / 2) { hide(app) }
                            else {
                                // snap to the nearer side edge, keep inside the top and bottom
                                lp.x = if (lp.x + w / 2 < screenW / 2) margin else screenW - w - margin
                                lp.y = min(max(lp.y, margin), screenH - h - margin)
                                runCatching { wm.updateViewLayout(this, lp) }
                            }
                        },
                        onClose = { hide(app) },
                    )
                }
            }
        }
        view = v; owner = o; held = p
        return runCatching { wm.addView(v, lp) }.isSuccess.also { ok -> if (!ok) { view = null; owner = null; held = null; o.stop() } }
    }

    /** Closes the mini window and releases its player (the video stops). */
    fun hide(ctx: Context) {
        val app = ctx.applicationContext
        val v = view ?: return
        runCatching { (app.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        owner?.stop(); owner = null; view = null
        held?.release(); held = null
        runCatching { app.stopService(Intent(app, com.umutk.pulse.extras.video.VideoSessionService::class.java)) }
    }
}

@Composable
private fun MiniBody(p: Player, w: Dp, h: Dp, onDrag: (Float, Float) -> Unit, onEnd: (Float, Float) -> Unit, onClose: () -> Unit) {
    val density = LocalDensity.current
    val wPx = with(density) { w.toPx() }
    var playing by remember { mutableStateOf(p.isPlaying) }
    var shown by remember { mutableStateOf(true) }
    var speedIdx by remember { mutableIntStateOf(0) }
    DisposableEffect(p) {
        val l = object : Player.Listener { override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying } }
        p.addListener(l)
        onDispose { p.removeListener(l) }
    }
    // the controls go away on their own while the video plays; a tap brings them back
    LaunchedEffect(shown, playing) { if (shown && playing) { delay(2500); shown = false } }

    Box(
        Modifier.size(w, h).clip(RoundedCornerShape(18.dp)).background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { shown = !shown },
                    onDoubleTap = { off ->
                        val back = off.x < wPx / 2f
                        val target = if (back) max(0L, p.currentPosition - 10_000L) else p.currentPosition + 10_000L
                        p.seekTo(target); shown = true
                    },
                )
            }
            .pointerInput(Unit) {
                val vt = VelocityTracker()
                detectDragGestures(
                    onDragStart = { vt.resetTracking() },
                    onDrag = { change, drag ->
                        change.consume()
                        vt.addPosition(change.uptimeMillis, change.position)
                        onDrag(drag.x, drag.y)
                    },
                    onDragEnd = { val v = vt.calculateVelocity(); onEnd(v.x, v.y) },
                )
            },
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    player = p
                    subtitleView?.applySubPrefs(VideoPrefs(c))
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (shown) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x99000000)).clickable { onClose() },
                    contentAlignment = Alignment.Center) { PIcon("close", Color.White, 18.dp) }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x99000000)).clickable {
                    speedIdx = (speedIdx + 1) % 4; p.setPlaybackSpeed(listOf(1f, 1.5f, 2f, .5f)[speedIdx])
                }, contentAlignment = Alignment.Center) {
                    Text(speedLabel(p.playbackParameters.speed), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(Modifier.align(Alignment.Center).size(48.dp).clip(RoundedCornerShape(24.dp)).background(Color(0x99000000)).clickable {
                if (p.isPlaying) p.pause() else p.play()
                shown = true
            }, contentAlignment = Alignment.Center) {
                PIcon(if (playing) "pause" else "play", Color.White, 26.dp)
            }
        }
    }
}

private fun speedLabel(s: Float): String = (if (s % 1f == 0f) "${s.toInt()}" else "$s") + "x"
