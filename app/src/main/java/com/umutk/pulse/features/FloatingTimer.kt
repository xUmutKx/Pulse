package com.umutk.pulse.features

import android.content.Context
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.umutk.pulse.PIcon
import kotlinx.coroutines.delay

private class TimerOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val reg = LifecycleRegistry(this)
    private val ctl = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = reg
    override val savedStateRegistry: SavedStateRegistry get() = ctl.savedStateRegistry
    fun start() { ctl.performRestore(Bundle()); reg.currentState = Lifecycle.State.RESUMED }
    fun stop() { reg.currentState = Lifecycle.State.DESTROYED }
}

/**
 * A small stopwatch / countdown that floats above every app: dragged by its body, started and paused with the play button, switched between
 * stopwatch and countdown with the clock button, closed with the cross. Needs "display over other apps".
 */
/** How the floating timer looks and where it sits: size, see-through, background, whether the buttons show, and the place it was left at. */
object FloatTimerPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("floattimer", Context.MODE_PRIVATE)
    fun scale(c: Context) = sp(c).getInt("scale", 80)
    fun alpha(c: Context) = sp(c).getInt("alpha", 90)
    fun controls(c: Context) = sp(c).getBoolean("controls", true)
    fun bg(c: Context) = sp(c).getInt("bg", 0)
    fun setScale(c: Context, v: Int) = sp(c).edit().putInt("scale", v).apply()
    fun setAlpha(c: Context, v: Int) = sp(c).edit().putInt("alpha", v).apply()
    fun setControls(c: Context, v: Boolean) = sp(c).edit().putBoolean("controls", v).apply()
    fun setBg(c: Context, v: Int) = sp(c).edit().putInt("bg", v).apply()
    fun pos(c: Context): Pair<Int, Int>? = sp(c).let { if (it.contains("x")) it.getInt("x", 0) to it.getInt("y", 0) else null }
    fun setPos(c: Context, x: Int, y: Int) = sp(c).edit().putInt("x", x).putInt("y", y).apply()
    /** The background colours to choose from: dark, black, the accent, light. */
    val bgColors = listOf(0xFF1C1C1C, 0xFF000000, 0xFF2563EB, 0xFFF2F2F2)
    fun bgColor(c: Context) = Color(bgColors[bg(c).coerceIn(0, bgColors.lastIndex)])
}

object FloatingTimer {
    private var view: ComposeView? = null
    private var owner: TimerOwner? = null
    val showing get() = view != null

    fun show(ctx: Context): Boolean {
        val app = ctx.applicationContext
        if (view != null) return true
        if (!com.umutk.pulse.Compat.canOverlay(app)) {
            app.startActivity(android.content.Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${app.packageName}")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            return false
        }
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val d = app.resources.displayMetrics
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // where it was left last time, else near the top left
            val saved = FloatTimerPrefs.pos(app)
            x = saved?.first ?: (d.widthPixels * 0.08f).toInt(); y = saved?.second ?: (d.heightPixels * 0.12f).toInt()
        }
        val o = TimerOwner().also { it.start() }
        val v = ComposeView(app).apply {
            setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o)
            setContent { TimerBody(app, onDrag = { dx, dy -> lp.x += dx.toInt(); lp.y += dy.toInt(); runCatching { wm.updateViewLayout(this, lp) } }, onDragEnd = { FloatTimerPrefs.setPos(app, lp.x, lp.y) }, onClose = { hide(app) }) }
        }
        view = v; owner = o
        runCatching { wm.addView(v, lp) }.onFailure { view = null; o.stop(); owner = null; return false }
        return true
    }

    fun hide(ctx: Context) {
        val v = view ?: return
        runCatching { (ctx.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        owner?.stop(); owner = null; view = null
    }
}

private fun clock(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0); val h = t / 3600; val m = t % 3600 / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun TimerBody(ctx: Context, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit, onClose: () -> Unit) {
    val sc = FloatTimerPrefs.scale(ctx) / 100f
    val bgc = FloatTimerPrefs.bgColor(ctx)
    val ink = if (bgc.red * .3f + bgc.green * .59f + bgc.blue * .11f > .6f) Color(0xFF111111) else Color.White
    val showControls = FloatTimerPrefs.controls(ctx)
    var countdown by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var elapsed by remember { mutableLongStateOf(0L) }   // stopwatch: counted up
    var left by remember { mutableLongStateOf(5 * 60_000L) }   // countdown: counted down
    LaunchedEffect(running, countdown) {
        var last = System.currentTimeMillis()
        while (running) {
            delay(100)
            val now = System.currentTimeMillis(); val dt = now - last; last = now
            if (countdown) {
                left -= dt
                if (left <= 0L) {
                    left = 0L; running = false
                    // time is up: a buzz and the default notification sound
                    runCatching { (ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator).let { v -> if (android.os.Build.VERSION.SDK_INT >= 26) v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)) else @Suppress("DEPRECATION") v.vibrate(longArrayOf(0, 400, 200, 400), -1) } }
                    runCatching { android.media.RingtoneManager.getRingtone(ctx, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)).play() }
                }
            } else elapsed += dt
        }
    }
    Column(
        Modifier.clip(RoundedCornerShape((16 * sc).dp)).background(bgc.copy(alpha = FloatTimerPrefs.alpha(ctx) / 100f)).padding(horizontal = (14 * sc).dp, vertical = (10 * sc).dp)
            .pointerInput(Unit) { detectDragGestures(onDragEnd = onDragEnd) { c, a -> c.consume(); onDrag(a.x, a.y) } },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(clock(if (countdown) left else elapsed), color = if (countdown && left == 0L) Color(0xFFFF6B6B) else ink, fontSize = (34 * sc).sp, fontWeight = FontWeight.Light)
        if (showControls) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = .14f)).clickable { running = !running; if (countdown && left == 0L) left = 5 * 60_000L }, contentAlignment = Alignment.Center) { PIcon(if (running) "pause" else "play", Color.White, 20.dp) }
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = .14f)).clickable { running = false; elapsed = 0L; left = 5 * 60_000L }, contentAlignment = Alignment.Center) { PIcon("rotate", Color.White, 20.dp) }
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(if (countdown) Color(0xFF3B82F6) else Color.White.copy(alpha = .14f)).clickable { countdown = !countdown; running = false }, contentAlignment = Alignment.Center) { PIcon("timer", Color.White, 20.dp) }
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(19.dp)).background(Color.White.copy(alpha = .14f)).clickable(onClick = onClose), contentAlignment = Alignment.Center) { PIcon("close", Color.White, 18.dp) }
        }
        // the countdown's length: +1 / +5 / +10 minutes while it is not running
        if (showControls && countdown && !running) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 5, 10).forEach { m ->
                Text("+$m", color = Color.White, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .14f)).clickable { left += m * 60_000L }.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Toolkit tool: switch the floating timer on and off, and set how it looks. */
@Composable
fun FloatingTimerTool() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(FloatingTimer.showing) }
    var scale by remember { mutableStateOf(FloatTimerPrefs.scale(ctx).toFloat()) }
    var alpha by remember { mutableStateOf(FloatTimerPrefs.alpha(ctx).toFloat()) }
    var controls by remember { mutableStateOf(FloatTimerPrefs.controls(ctx)) }
    var bg by remember { mutableStateOf(FloatTimerPrefs.bg(ctx)) }
    // a change shows at once: the window is drawn again
    fun redraw() { if (FloatingTimer.showing) { FloatingTimer.hide(ctx); FloatingTimer.show(ctx) } }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(com.umutk.pulse.tr("Floating timer", "Yüzen sayaç"), color = com.umutk.pulse.T.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        com.umutk.pulse.PSwitch(on, { want -> if (want) on = FloatingTimer.show(ctx) else { FloatingTimer.hide(ctx); on = false } })
    }
    Text(com.umutk.pulse.tr("A stopwatch and a countdown above every app. Needs \"display over other apps\".", "Her uygulamanın üstünde kronometre ve geri sayım. \"Diğer uygulamaların üstünde göster\" izni gerekir."), color = com.umutk.pulse.T.sub, fontSize = 12.sp)
    Text(com.umutk.pulse.tr("Size", "Boyut") + " ${scale.toInt()}%", color = com.umutk.pulse.T.text, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
    androidx.compose.material3.Slider(scale, { scale = it }, valueRange = 50f..140f, onValueChangeFinished = { FloatTimerPrefs.setScale(ctx, scale.toInt()); redraw() })
    Text(com.umutk.pulse.tr("See-through", "Saydamlık") + " ${alpha.toInt()}%", color = com.umutk.pulse.T.text, fontSize = 14.sp)
    androidx.compose.material3.Slider(alpha, { alpha = it }, valueRange = 20f..100f, onValueChangeFinished = { FloatTimerPrefs.setAlpha(ctx, alpha.toInt()); redraw() })
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(com.umutk.pulse.tr("Background", "Arka plan"), color = com.umutk.pulse.T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        FloatTimerPrefs.bgColors.forEachIndexed { i, c ->
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(c)).clickable { bg = i; FloatTimerPrefs.setBg(ctx, i); redraw() }
                .then(if (bg == i) Modifier.border(2.dp, com.umutk.pulse.Look.accent, RoundedCornerShape(15.dp)) else Modifier.border(1.dp, Color.Gray.copy(alpha = .5f), RoundedCornerShape(15.dp))))
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(com.umutk.pulse.tr("Show the buttons (otherwise only the time)", "Düğmeleri göster (kapalıysa yalnız süre)"), color = com.umutk.pulse.T.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        com.umutk.pulse.PSwitch(controls, { controls = it; FloatTimerPrefs.setControls(ctx, it); redraw() })
    }
}
