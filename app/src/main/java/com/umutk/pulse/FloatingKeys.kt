package com.umutk.pulse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * A floating key button over every app. A tap tries to bring up the keyboard of the field in front (needs the App Lock accessibility service, which can tell
 * where the text field is); holding it opens a small floating key panel (Esc, Tab, Ctrl, Alt, Shift, Win, arrows, Home / End, F1-F12) that you slide sideways
 * to change page. The keys are sent with the `input` command, so they need root or Shizuku; without either the panel says so.
 */
class FloatingKeysService : Service() {
    private lateinit var wm: WindowManager
    private var handle: View? = null
    private var panel: View? = null
    private val mods = linkedSetOf<Int>()                       // latched Ctrl / Alt / Shift / Win
    private val modViews = HashMap<Int, TextView>()
    private var page = 0
    private var pageHost: FrameLayout? = null
    private var dots: TextView? = null
    private val d get() = resources.displayMetrics.density

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(QuietChannel("floatkeys", "Floating keys", NotificationManager.IMPORTANCE_LOW))
        val n = androidx.core.app.NotificationCompat.Builder(this, "floatkeys").setSmallIcon(R.drawable.ic_qs_touch).setContentTitle("Floating keys on").setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(8, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(8, n)
        running = true
        wm = getSystemService(WindowManager::class.java)
        if (!Compat.canOverlay(this)) { stopSelf(); return }
        addHandle()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY
    override fun onBind(i: Intent?) = null
    override fun onDestroy() {
        running = false
        handle?.let { runCatching { wm.removeView(it) } }; panel?.let { runCatching { wm.removeView(it) } }
        super.onDestroy()
    }

    private fun lp(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun round(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r * d }

    /** The round button: drag moves it, a tap brings the keyboard up, a long press opens or closes the panel. */
    private fun addHandle() {
        val size = (46 * d).toInt()
        // the Material keyboard icon, white on a dark round button
        val v = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_float_keyboard); setColorFilter(Color.WHITE); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            val pad = (11 * d).toInt(); setPadding(pad, pad, pad, pad); background = round(0xCC202020.toInt(), 23f)
        }
        val p = lp(size, size, 0, (160 * d).toInt())
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false; var longDone = false
        val longRun = Runnable { longDone = true; togglePanel() }
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y; moved = false; longDone = false; view.postDelayed(longRun, 450) }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.hypot((e.rawX - downX).toDouble(), (e.rawY - downY).toDouble()) > 10 * d) { moved = true; view.removeCallbacks(longRun) }
                    if (moved) { p.x = startX + (e.rawX - downX).toInt(); p.y = startY + (e.rawY - downY).toInt(); runCatching { wm.updateViewLayout(view, p) } }
                }
                MotionEvent.ACTION_UP -> { view.removeCallbacks(longRun); if (!moved && !longDone) openKeyboard() }
                MotionEvent.ACTION_CANCEL -> view.removeCallbacks(longRun)
            }
            true
        }
        handle = v
        wm.addView(v, p)
    }

    /** Asks the App Lock accessibility service for the focused text field and clicks it, which brings its keyboard up. */
    private fun openKeyboard() {
        handle?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        val svc = com.umutk.pulse.applock.LockService.instance
        if (svc == null) { android.widget.Toast.makeText(this, tr("Turn on the App Lock accessibility service first", "Önce Uygulama Kilidi erişilebilirlik hizmetini açın"), android.widget.Toast.LENGTH_LONG).show(); return }
        val root = runCatching { svc.rootInActiveWindow }.getOrNull()
        // the field that has the cursor; failing that, the first editable field on the screen
        var node = runCatching { root?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (node == null && root != null) {
            val todo = ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>().apply { add(root) }
            var seen = 0
            while (todo.isNotEmpty() && seen++ < 400) {
                val n = todo.removeFirst()
                if (n.isEditable && n.isVisibleToUser) { node = n; break }
                for (i in 0 until n.childCount) n.getChild(i)?.let { todo.add(it) }
            }
        }
        val n = node
        // focus first, then a click: the click is what makes most apps raise their keyboard
        val ok = n != null && (n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_FOCUS) or n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))
        // no field to focus (nothing to type into): the keys panel still works on its own, so open that instead of just failing silently
        if (!ok) togglePanel()
    }

    private fun togglePanel() {
        panel?.let { runCatching { wm.removeView(it) }; panel = null; return }
        if (Shell.root != true && Shell.shizuku != 2) android.widget.Toast.makeText(this, tr("The keys need root or Shizuku", "Tuşlar için root veya Shizuku gerekir"), android.widget.Toast.LENGTH_LONG).show()
        val w = minOf(resources.displayMetrics.widthPixels - (24 * d).toInt(), (340 * d).toInt())
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = round(0xEE181818.toInt(), 14f); setPadding((8 * d).toInt(), (6 * d).toInt(), (8 * d).toInt(), (8 * d).toInt()) }
        // the bar: dots for the page, a close mark; it moves the panel
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dotsV = TextView(this).apply { setTextColor(0xFFBBBBBB.toInt()); textSize = 13f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, (28 * d).toInt(), 1f) }
        val close = TextView(this).apply { text = "✕"; setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams((36 * d).toInt(), (28 * d).toInt()); setOnClickListener { togglePanel() } }
        bar.addView(dotsV); bar.addView(close); root.addView(bar)
        dots = dotsV
        val host = FrameLayout(this); pageHost = host; root.addView(host, ViewGroup.LayoutParams(-1, -2))
        showPage(0)
        val p = lp(w, -2, ((resources.displayMetrics.widthPixels - w) / 2), (resources.displayMetrics.heightPixels * .35f).toInt())
        // the panel slides sideways to change the page (a drag that starts on a key counts too: the touch is watched from above) and moves by its bar
        var downX = 0f; var downY = 0f; var sx = 0; var sy = 0
        bar.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; sx = p.x; sy = p.y }
                // the view actually added to the window manager is the wrap below (kept in the panel field), not this root: updateViewLayout(root, ...)
                // threw every time (root is not attached to the window manager directly) and runCatching swallowed it — the panel never moved
                MotionEvent.ACTION_MOVE -> { p.x = sx + (e.rawX - downX).toInt(); p.y = sy + (e.rawY - downY).toInt(); panel?.let { v -> runCatching { wm.updateViewLayout(v, p) } } }
            }
            false
        }
        val swipe = object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onFling(a: MotionEvent?, b: MotionEvent, vx: Float, vy: Float): Boolean {
                if (Math.abs(vx) > Math.abs(vy) * 1.5f && Math.abs(vx) > 400) { showPage((page + (if (vx < 0) 1 else PAGES - 1)) % PAGES); return true }
                return false
            }
        }
        val gd = android.view.GestureDetector(this, swipe)
        // the wrapper sees every touch first, so a sideways fling that starts on a key still changes the page
        val wrap = object : FrameLayout(this) {
            override fun dispatchTouchEvent(e: MotionEvent): Boolean { gd.onTouchEvent(e); return super.dispatchTouchEvent(e) }
        }
        wrap.addView(root); panel = wrap
        wm.addView(wrap, p)
    }

    private fun showPage(n: Int) {
        page = n
        val host = pageHost ?: return
        host.removeAllViews()
        modViews.clear()
        val rows: List<List<Pair<String, Int>>> = when (n) {
            0 -> listOf(
                listOf("Esc" to 111, "Tab" to 61, "Ctrl" to 113, "Alt" to 57, "Shift" to 59, "Win" to 117),
                listOf("←" to 21, "↑" to 19, "↓" to 20, "→" to 22, "⌫" to 67, "Del" to 112),
            )
            1 -> listOf((1..6).map { "F$it" to 130 + it }, (7..12).map { "F$it" to 130 + it })
            2 -> listOf(
                listOf("Home" to 122, "End" to 123, "PgUp" to 92, "PgDn" to 93, "Ins" to 124, "Del" to 112),
                listOf("Enter" to 66, "Space" to 62, "Tab" to 61, "Esc" to 111, "⌫" to 67),
            )
            // a full letter keyboard, not only the side keys: QWERTY rows sending the same input-command key codes as everything
            // else here, so a latched Shift (mods, above) still combines into a capital on Android 12+ like it does with the others
            else -> listOf(
                listOf("Q" to 45, "W" to 51, "E" to 33, "R" to 46, "T" to 48, "Y" to 53, "U" to 49, "I" to 37, "O" to 43, "P" to 44),
                listOf("A" to 29, "S" to 47, "D" to 32, "F" to 34, "G" to 35, "H" to 36, "J" to 38, "K" to 39, "L" to 40),
                listOf("Z" to 54, "X" to 52, "C" to 31, "V" to 50, "B" to 30, "N" to 42, "M" to 41, "⌫" to 67),
                listOf("Shift" to 59, "Space" to 62, "Enter" to 66),
            )
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { r ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEach { (label, code) ->
                val isMod = code in setOf(113, 57, 59, 117)
                val k = TextView(this).apply {
                    text = label; setTextColor(Color.WHITE); textSize = 14f; gravity = Gravity.CENTER; background = round(if (isMod && code in mods) 0xFF3D8BFF.toInt() else 0xFF303030.toInt(), 8f)
                    layoutParams = LinearLayout.LayoutParams(0, (42 * d).toInt(), 1f).apply { setMargins((2 * d).toInt(), (2 * d).toInt(), (2 * d).toInt(), (2 * d).toInt()) }
                    setOnClickListener {
                        if (isMod) { if (!mods.remove(code)) mods.add(code); background = round(if (code in mods) 0xFF3D8BFF.toInt() else 0xFF303030.toInt(), 8f) }
                        else send(code)
                    }
                }
                if (isMod) modViews[code] = k
                row.addView(k)
            }
            col.addView(row)
        }
        host.addView(col)
        dots?.text = (0 until PAGES).joinToString("  ") { if (it == n) "●" else "○" }
    }

    /** Sends the key (with the latched modifiers) through the `input` command; Android 12 and later takes combinations. */
    private fun send(code: Int) {
        val m = mods.toList(); mods.clear(); modViews.forEach { (_, v) -> v.background = round(0xFF303030.toInt(), 8f) }
        Thread {
            val cmd = if (m.isEmpty()) "input keyevent $code" else if (Build.VERSION.SDK_INT >= 31) "input keycombination ${m.joinToString(" ")} $code" else "input keyevent $code"
            val out = Shell.run(cmd)
            if (out.contains("Exception", true) || out.contains("denied", true) || out.contains("not found", true))
                android.os.Handler(mainLooper).post { android.widget.Toast.makeText(this, tr("Could not send the key (needs root or Shizuku)", "Tuş gönderilemedi (root veya Shizuku gerekir)"), android.widget.Toast.LENGTH_SHORT).show() }
        }.start()
    }

    companion object {
        private const val PAGES = 4
        @Volatile var running = false
        fun toggle(c: Context): Boolean {
            if (!Compat.canOverlay(c)) return false
            if (running) c.stopService(Intent(c, FloatingKeysService::class.java))
            else ContextCompat.startForegroundService(c, Intent(c, FloatingKeysService::class.java))
            return true
        }
    }
}
