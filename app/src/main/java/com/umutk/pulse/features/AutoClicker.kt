package com.umutk.pulse.features

import androidx.compose.foundation.background
import androidx.compose.ui.draw.shadow
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.umutk.pulse.PButton
import com.umutk.pulse.PCard
import com.umutk.pulse.PIcon
import com.umutk.pulse.T
import com.umutk.pulse.tr
import kotlinx.coroutines.delay

/** The spots the auto clicker taps (screen pixels), how often, and whether it runs. Kept in the app's own preferences. */
object AutoClick {
    const val MAX = 10
    class Pt(var x: Int, var y: Int)
    val points = ArrayList<Pt>()
    var intervalMs = 500L
    var running = false
    private var loaded = false

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val sp = c.getSharedPreferences("autoclick", Context.MODE_PRIVATE)
        intervalMs = sp.getLong("ms", 500L)
        sp.getString("pts", "").orEmpty().split(';').filter { it.contains(',') }.forEach {
            val a = it.split(',')
            val x = a[0].toIntOrNull(); val y = a[1].toIntOrNull()
            if (x != null && y != null && points.size < MAX) points.add(Pt(x, y))
        }
    }

    fun save(c: Context) {
        c.getSharedPreferences("autoclick", Context.MODE_PRIVATE).edit()
            .putLong("ms", intervalMs).putString("pts", points.joinToString(";") { "${it.x},${it.y}" }).apply()
    }

    fun label(ms: Long): String = if (ms < 1000) "${ms} ms" else "%.1f s".format(ms / 1000f)
}

/**
 * The auto clicker: a floating bar (points − / + , start / stop, speed, close) and a target mark for each point, which is dragged to where the tap should land.
 * The taps are real touches, sent by the accessibility service's gesture call; the marks let them through while it runs.
 */
class AutoClickService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private var wm: WindowManager? = null
    private var bar: LinearLayout? = null
    private val markers = ArrayList<Marker>()
    private var countView: TextView? = null
    private var playView: Glyph? = null
    private var msView: TextView? = null
    private var idx = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onServiceConnected() {
        instance = this
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        AutoClick.load(this)
        refreshTile(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean { hide(); instance = null; refreshTile(this); return super.onUnbind(intent) }
    override fun onDestroy() { hide(); instance = null; super.onDestroy() }

    private val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

    val shown get() = bar != null

    /** The quick tile: first press shows the bar, then it starts and stops. */
    fun tileToggle() { if (bar == null) show() else if (AutoClick.running) stop() else start() }

    fun show() {
        if (bar != null) return
        if (AutoClick.points.isEmpty()) newPoint()
        AutoClick.points.forEach { addMarker(it) }
        buildBar()
        refreshTile(this)
    }

    fun hide() {
        stop()
        val w = wm
        markers.forEach { runCatching { w?.removeView(it) } }
        markers.clear()
        bar?.let { b -> runCatching { w?.removeView(b) } }
        bar = null; countView = null; playView = null; msView = null
        refreshTile(this)
    }

    private fun newPoint() {
        val m = resources.displayMetrics
        val n = AutoClick.points.size
        AutoClick.points.add(AutoClick.Pt(m.widthPixels / 2 + (n % 3 - 1) * dp(70), m.heightPixels / 2 + (n / 3) * dp(70)))
    }

    private fun addMarker(p: AutoClick.Pt) {
        val mk = Marker(p, markers.size + 1)
        markers.add(mk)
        runCatching { wm?.addView(mk, mk.lp) }
    }

    private fun setCount(n: Int) {
        while (AutoClick.points.size < n) { newPoint(); addMarker(AutoClick.points.last()) }
        while (AutoClick.points.size > n) {
            AutoClick.points.removeAt(AutoClick.points.size - 1)
            markers.removeLastOrNull()?.let { runCatching { wm?.removeView(it) } }
        }
        AutoClick.save(this)
        countView?.text = "${AutoClick.points.size}"
    }

    private fun start() {
        if (AutoClick.points.isEmpty()) return
        AutoClick.running = true; idx = 0
        markers.forEach { it.lp.flags = it.lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; it.alpha = .45f; runCatching { wm?.updateViewLayout(it, it.lp) } }
        playView?.let { it.kind = 3; it.invalidate() }
        h.post(stepRun)
        refreshTile(this)
    }

    private fun stop() {
        val was = AutoClick.running
        AutoClick.running = false
        h.removeCallbacks(stepRun)
        if (was) markers.forEach { it.lp.flags = it.lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv(); it.alpha = 1f; runCatching { wm?.updateViewLayout(it, it.lp) } }
        playView?.let { it.kind = 2; it.invalidate() }
        refreshTile(this)
    }

    private val stepRun = object : Runnable {
        override fun run() {
            if (!AutoClick.running) return
            val pts = AutoClick.points
            if (pts.isEmpty()) { stop(); return }
            val p = pts[idx % pts.size]; idx++
            val path = Path().apply { moveTo(p.x.toFloat(), p.y.toFloat()) }
            val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0L, 30L)).build()
            runCatching { dispatchGesture(g, null, null) }
            h.postDelayed(this, AutoClick.intervalMs)
        }
    }

    private fun buildBar() {
        val b = object : LinearLayout(this) {
            private var dx = 0f
            private var dy = 0f
            private var downX = 0f
            private var downY = 0f
            private var dragging = false
            // a drag that starts on a button moves the bar too (the buttons take the touch otherwise, and only the thin edge moved it)
            override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
                val lp = layoutParams as? WindowManager.LayoutParams ?: return false
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; dragging = false; dx = e.rawX - lp.x; dy = e.rawY - lp.y }
                    MotionEvent.ACTION_MOVE -> if (!dragging && kotlin.math.hypot(e.rawX - downX, e.rawY - downY) > dp(8)) dragging = true
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragging = false
                }
                return dragging
            }
            override fun onTouchEvent(e: MotionEvent): Boolean {
                val lp = layoutParams as? WindowManager.LayoutParams ?: return false
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { dx = e.rawX - lp.x; dy = e.rawY - lp.y }
                    MotionEvent.ACTION_MOVE -> { lp.x = (e.rawX - dx).toInt(); lp.y = (e.rawY - dy).toInt(); runCatching { wm?.updateViewLayout(this, lp) } }
                }
                return true
            }
        }
        b.orientation = LinearLayout.HORIZONTAL
        b.gravity = Gravity.CENTER_VERTICAL
        b.setPadding(dp(8), dp(4), dp(8), dp(4))
        b.background = GradientDrawable().apply { setColor(0xEE202124.toInt()); cornerRadius = dp(26).toFloat() }
        fun glyph(kind: Int, onClick: () -> Unit): Glyph = Glyph(this, kind).also { g ->
            g.setOnClickListener { onClick() }
            b.addView(g, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        fun text(t: String): TextView = TextView(this).apply {
            this.text = t; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER; minWidth = dp(34)
            b.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
        }
        glyph(1) { if (!AutoClick.running && AutoClick.points.size > 1) setCount(AutoClick.points.size - 1) }
        countView = text("${AutoClick.points.size}")
        glyph(0) { if (!AutoClick.running && AutoClick.points.size < AutoClick.MAX) setCount(AutoClick.points.size + 1) }
        playView = glyph(2) { if (AutoClick.running) stop() else start() }
        msView = text(AutoClick.label(AutoClick.intervalMs)).apply {
            setPadding(dp(6), 0, dp(6), 0)
            setOnClickListener {
                val steps = longArrayOf(100, 250, 500, 1000, 2000, 5000)
                val i = steps.indexOf(AutoClick.intervalMs)
                AutoClick.intervalMs = steps[(i + 1).mod(steps.size)]
                AutoClick.save(this@AutoClickService)
                this.text = AutoClick.label(AutoClick.intervalMs)
            }
        }
        glyph(4) { hide() }
        val lp = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = dp(24); y = dp(110) }
        bar = b
        runCatching { wm?.addView(b, lp) }
    }

    /** A target mark: a small half-transparent white dot with a black outline; dragged, it sets where the tap lands. */
    private inner class Marker(val pt: AutoClick.Pt, val number: Int) : View(this@AutoClickService) {
        val size = dp(36)
        val lp = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = pt.x - size / 2; y = pt.y - size / 2 }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0x88FFFFFF.toInt() }
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xCC000000.toInt(); strokeWidth = dp(1.5f).toFloat() }
        private var dx = 0f
        private var dy = 0f

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f; val r = dp(7).toFloat()
            c.drawCircle(cx, cy, r, fill)
            c.drawCircle(cx, cy, r, line)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dx = e.rawX - lp.x; dy = e.rawY - lp.y }
                MotionEvent.ACTION_MOVE -> { lp.x = (e.rawX - dx).toInt(); lp.y = (e.rawY - dy).toInt(); runCatching { wm?.updateViewLayout(this, lp) } }
                MotionEvent.ACTION_UP -> { pt.x = lp.x + size / 2; pt.y = lp.y + size / 2; AutoClick.save(this@AutoClickService) }
            }
            return true
        }
    }

    private class Glyph(c: Context, var kind: Int) : View(c) {
        private val d = c.resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.5f * d; strokeCap = Paint.Cap.ROUND }
        private val f = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        override fun onDraw(cv: Canvas) {
            val w = width.toFloat(); val h = height.toFloat(); val m = w * .3f
            when (kind) {
                0 -> { cv.drawLine(m, h / 2, w - m, h / 2, p); cv.drawLine(w / 2, m, w / 2, h - m, p) }
                1 -> cv.drawLine(m, h / 2, w - m, h / 2, p)
                2 -> { val t = Path(); t.moveTo(w * .38f, h * .28f); t.lineTo(w * .72f, h / 2); t.lineTo(w * .38f, h * .72f); t.close(); cv.drawPath(t, f) }
                3 -> cv.drawRect(w * .34f, h * .34f, w * .66f, h * .66f, f)
                else -> { cv.drawLine(m, m, w - m, h - m, p); cv.drawLine(w - m, m, m, h - m, p) }
            }
        }
    }

    companion object {
        @Volatile var instance: AutoClickService? = null
        fun refreshTile(c: Context) {
            if (android.os.Build.VERSION.SDK_INT >= 24) runCatching { TileService.requestListeningState(c, ComponentName(c, AutoClickTile::class.java)) }
        }
        fun openSettings(c: Context) {
            runCatching { c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
}

/** Quick Settings tile of the auto clicker: it opens the accessibility settings until the service is on, then shows the bar, then starts and stops. */
@androidx.annotation.RequiresApi(24)
class AutoClickTile : TileService() {
    override fun onStartListening() { paint() }
    private fun paint() {
        qsTile?.apply {
            state = if (AutoClickService.instance != null && AutoClick.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = tr("Auto clicker", "Otomatik tıklayıcı")
            updateTile()
        }
    }
    override fun onClick() {
        var s = AutoClickService.instance
        // listed but "Not working": put it back at once (when Pulse can write the setting), so the tap works the first time
        if (s == null && Thread { runCatching { A11yRepair.run(this) } }.also { it.start() }.let { it.join(2500); true }) { Thread.sleep(600); s = AutoClickService.instance }
        if (s == null) {
            val i = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (android.os.Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE))
            else @Suppress("DEPRECATION") startActivityAndCollapse(i)
        } else { s.tileToggle(); collapsePanel() }
        paint()
    }
}

@Composable
fun AutoClickerTool() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(600); tick++ } }
    val svc = remember(tick) { AutoClickService.instance }
    // a small white note, not the board: paper colour, dark ink, about half the page wide
    Column(
        Modifier.fillMaxWidth(.72f).shadow(6.dp, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .background(androidx.compose.ui.graphics.Color(0xFFFFFEF7), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PIcon("touch", com.umutk.pulse.Look.accent, 28.dp)
            Text(
                if (svc == null) tr("Accessibility service is off", "Erişilebilirlik hizmeti kapalı")
                else if (AutoClick.running) tr("Running", "Çalışıyor")
                else if (svc.shown) tr("Ready", "Hazır") else tr("On", "Açık"),
                color = androidx.compose.ui.graphics.Color(0xFF1F1F1F), fontSize = 16.sp
            )
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (svc == null) PButton(tr("Accessibility settings", "Erişilebilirlik ayarları"), Modifier.fillMaxWidth()) { AutoClickService.openSettings(ctx) }
            else if (!svc.shown) PButton(tr("Show the bar", "Çubuğu göster"), Modifier.fillMaxWidth()) { svc.show(); tick++ }
            else PButton(tr("Hide the bar", "Çubuğu gizle"), Modifier.fillMaxWidth()) { svc.hide(); tick++ }
        }
    }
}
