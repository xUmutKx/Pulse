package com.umutk.pulse.extras.screenrec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.TextView
import kotlin.math.hypot

/**
 * The floating control that sits over the screen while a recording runs: pause / resume, stop, a pen to draw on the screen, and a button that
 * grabs a still picture of the screen. Needs the "draw over other apps" permission; without it nothing is shown.
 *
 * The bar moves wherever it is dragged (from any part of it, the buttons too: a tap stays a tap) and, let go while moving, it glides on a little and slows
 * down, never leaving the screen. A tap on the pen draws on the screen; holding the pen opens its box: the pen, the marker, the highlighter and the eraser
 * (each shown as the stroke it makes), the colours, three sizes, undo and clear.
 */
class CaptureOverlay(
    private val c: Context,
    private val onPause: () -> Unit, private val onStop: () -> Unit, private val onGrab: () -> Unit,
) {
    // the accessibility layer (Cover) is the topmost there is: above the bars, the keyboard and other overlays; without it the plain overlay window
    private val acc = com.umutk.pulse.features.CoverAccessibilityService.instance
    private val wm = (acc ?: c).getSystemService(WindowManager::class.java)
    private val type = if (acc != null) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val d = c.resources.displayMetrics.density
    private val prefs = c.getSharedPreferences("screenrec_pen", Context.MODE_PRIVATE)

    private var bar: DragRow? = null
    private var barLp: WindowManager.LayoutParams? = null
    private var penView: PenView? = null
    private var palette: View? = null
    private var pauseBtn: TextView? = null
    private var penBtn: TextView? = null

    // the pen: its tool (0 pen, 1 marker, 2 highlighter, 3 eraser), colour and size (0 small, 1 middle, 2 big)
    private var tool = prefs.getInt("tool", 0)
    private var color = prefs.getInt("color", 0xFFFF3B30.toInt())
    private var size = prefs.getInt("size", 1)
    private val colors = intArrayOf(0xFFFF3B30.toInt(), 0xFFFF9500.toInt(), 0xFFFFCC00.toInt(), 0xFF34C759.toInt(), 0xFF32ADE6.toInt(), 0xFF007AFF.toInt(), 0xFFAF52DE.toInt(), Color.WHITE, Color.BLACK)

    private fun savePen() { prefs.edit().putInt("tool", tool).putInt("color", color).putInt("size", size).apply() }

    /** The paint of a new stroke with the chosen tool, colour and size. */
    private fun strokePaint(t: Int = tool, col: Int = color, s: Int = size): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        val k = when (s) { 0 -> .6f; 2 -> 1.7f; else -> 1f }
        when (t) {
            1 -> { this.color = col; strokeWidth = 11f * d * k }
            2 -> { this.color = (col and 0x00FFFFFF) or 0x66000000; strokeWidth = 24f * d * k; strokeCap = Paint.Cap.SQUARE }
            3 -> { this.color = Color.TRANSPARENT; strokeWidth = 26f * d * k; xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            else -> { this.color = col; strokeWidth = 5f * d * k }
        }
    }

    fun show(): Boolean {
        if (bar != null) return true
        if (acc == null && !com.umutk.pulse.Compat.canOverlay(c)) return false
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = prefs.getInt("x", (12 * d).toInt()); y = prefs.getInt("y", (120 * d).toInt()) }
        barLp = lp
        val row = DragRow(lp).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(0xE6202020.toInt()); cornerRadius = 28 * d }
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }
        fun btn(txt: String, tint: Int, click: (TextView) -> Unit) = TextView(c).apply {
            text = txt; setTextColor(tint); textSize = 18f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt())
            setOnClickListener { click(this) }
        }
        // a grip at the start: what can be held to move the bar (the rest moves it too)
        row.addView(TextView(c).apply { text = "⋮"; setTextColor(0x99FFFFFF.toInt()); textSize = 18f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams((18 * d).toInt(), (44 * d).toInt()) })
        pauseBtn = btn("II", Color.WHITE) { onPause() }
        row.addView(pauseBtn)
        row.addView(btn("■", 0xFFFF5A5A.toInt()) { onStop() })
        penBtn = btn("✎", Color.WHITE) { togglePen() }.also { b ->
            b.setOnLongClickListener { openPalette(); true }
        }
        row.addView(penBtn)
        row.addView(btn("◎", Color.WHITE) { onGrab() })
        val ok = runCatching { wm.addView(row, lp) }.isSuccess
        if (ok) bar = row
        return ok
    }

    fun setPaused(p: Boolean) { pauseBtn?.text = if (p) "▶" else "II" }

    private fun penOn(): Boolean {
        if (penView != null) return true
        val v = PenView(c)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT,
        )
        if (!runCatching { wm.addView(v, lp) }.isSuccess) return false
        penView = v
        // the bar (and the pen's box) must stay above the drawing layer
        raise(bar); raise(palette)
        tintPen()
        return true
    }

    private fun raise(v: View?) { v ?: return; runCatching { val p = v.layoutParams; wm.removeView(v); wm.addView(v, p) } }

    private fun tintPen() { penBtn?.setTextColor(if (penView == null) Color.WHITE else if (tool == 3) 0xFFB0B0B0.toInt() else color) }

    private fun togglePen() {
        val on = penView
        if (on != null) { runCatching { wm.removeView(on) }; penView = null; closePalette(); tintPen(); return }
        penOn()
    }

    // ------------------------------------------------------------------------------------------------ the pen's box

    private fun closePalette() { palette?.let { runCatching { wm.removeView(it) } }; palette = null }

    private fun openPalette() {
        if (palette != null) { closePalette(); return }
        penOn()
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xF2202226.toInt()); cornerRadius = 22 * d }
            setPadding((12 * d).toInt(), (10 * d).toInt(), (12 * d).toInt(), (10 * d).toInt())
        }
        fun refresh() { closePalette(); openPalette() }
        fun cell(sel: Boolean, w: Int, h: Int, content: View, click: () -> Unit) = LinearLayout(c).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(if (sel) 0x33FFFFFF else 0); cornerRadius = 14 * d; if (sel) setStroke((2 * d).toInt(), 0xCCFFFFFF.toInt()) }
            layoutParams = LinearLayout.LayoutParams((w * d).toInt(), (h * d).toInt()).apply { marginEnd = (6 * d).toInt() }
            addView(content)
            setOnClickListener { click() }
        }
        // the tools, each drawn as the stroke it makes
        val tools = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in 0..3) tools.addView(cell(tool == t, 56, 44, StrokeSample(t)) { tool = t; savePen(); tintPen(); refresh() })
        box.addView(tools)
        // the colours
        val cols = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, (10 * d).toInt(), 0, 0) }
        colors.forEach { col ->
            val dot = View(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col); setStroke((1 * d).toInt(), 0x55FFFFFF) }
                layoutParams = LinearLayout.LayoutParams((24 * d).toInt(), (24 * d).toInt())
            }
            cols.addView(cell(color == col && tool != 3, 30, 34, dot) { color = col; if (tool == 3) tool = 0; savePen(); tintPen(); refresh() }.apply {
                layoutParams = LinearLayout.LayoutParams((30 * d).toInt(), (34 * d).toInt()).apply { marginEnd = (2 * d).toInt() }
            })
        }
        box.addView(cols)
        // the sizes, undo and clear
        val more = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, (10 * d).toInt(), 0, 0) }
        for (s in 0..2) {
            val r = (6 + s * 5)
            val dot = View(c).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams((r * d).toInt(), (r * d).toInt())
            }
            more.addView(cell(size == s, 40, 36, dot) { size = s; savePen(); refresh() })
        }
        fun glyph(t: String) = TextView(c).apply { text = t; setTextColor(Color.WHITE); textSize = 18f; gravity = Gravity.CENTER }
        more.addView(cell(false, 44, 36, glyph("↶")) { penView?.undo() })
        more.addView(cell(false, 44, 36, glyph("✕")) { penView?.clear() })
        box.addView(more)
        val b = barLp
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // under the bar, or over it when the bar is low on the screen
            val sh = c.resources.displayMetrics.heightPixels
            val bh = bar?.height ?: (52 * d).toInt()
            x = (b?.x ?: 0).coerceIn(0, (c.resources.displayMetrics.widthPixels - (300 * d).toInt()).coerceAtLeast(0))
            y = if ((b?.y ?: 0) + bh + (200 * d) < sh) (b?.y ?: 0) + bh + (8 * d).toInt() else ((b?.y ?: 0) - (190 * d).toInt()).coerceAtLeast(0)
        }
        if (runCatching { wm.addView(box, lp) }.isSuccess) palette = box
    }

    fun hide() {
        closePalette()
        penView?.let { runCatching { wm.removeView(it) } }; penView = null
        bar?.let { it.stopFling(); runCatching { wm.removeView(it) } }; bar = null
    }

    /** A tool of the pen's box, drawn as a short wave of the stroke it makes (the eraser as a dashed one). */
    private inner class StrokeSample(private val t: Int) : View(c) {
        init { layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (30 * d).toInt()) }
        override fun onDraw(cv: Canvas) {
            val p = if (t == 3) Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = 0xFFB0B0B0.toInt(); strokeWidth = 2f * d; pathEffect = android.graphics.DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f)
            } else strokePaint(t, color, 1).apply { strokeWidth = (strokeWidth * .6f).coerceAtMost(14f * d) }
            val w = width.toFloat(); val h = height.toFloat()
            val path = Path().apply { moveTo(w * .1f, h * .6f); cubicTo(w * .35f, h * .1f, w * .6f, h * .95f, w * .9f, h * .4f) }
            if (t == 3) cv.drawRoundRect(w * .2f, h * .15f, w * .8f, h * .85f, 6 * d, 6 * d, p) else cv.drawPath(path, p)
        }
    }

    /**
     * The bar: it moves wherever it is dragged from (a button keeps its tap: the drag starts once the finger has gone a little way), and let go while moving it
     * glides on and slows down, staying on the screen. Its place is kept for the next recording.
     */
    private inner class DragRow(private val lp: WindowManager.LayoutParams) : LinearLayout(c) {
        private val slop = ViewConfiguration.get(c).scaledTouchSlop
        private var sx = 0f; private var sy = 0f; private var ox = 0; private var oy = 0
        private var dragging = false
        private var vt: VelocityTracker? = null
        private val scroller = OverScroller(c).apply { setFriction(.035f) }
        private val glide = object : Runnable {
            override fun run() {
                if (!scroller.computeScrollOffset()) { savePlace(); return }
                place(scroller.currX, scroller.currY)
                postOnAnimation(this)
            }
        }

        fun stopFling() { scroller.forceFinished(true); removeCallbacks(glide) }

        private fun maxX() = (c.resources.displayMetrics.widthPixels - width).coerceAtLeast(0)
        private fun maxY() = (c.resources.displayMetrics.heightPixels - height).coerceAtLeast(0)
        private fun place(x: Int, y: Int) { lp.x = x.coerceIn(0, maxX()); lp.y = y.coerceIn(0, maxY()); runCatching { wm.updateViewLayout(this, lp) } }
        private fun savePlace() { prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply() }

        // the speed is measured on the screen's own coordinates (the window moves with the finger, so the coordinates inside it hardly change)
        private fun track(e: MotionEvent) {
            val ev = MotionEvent.obtain(e); ev.setLocation(e.rawX, e.rawY); vt?.addMovement(ev); ev.recycle()
        }

        private fun start(e: MotionEvent) {
            stopFling(); sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; dragging = false
            vt?.recycle(); vt = VelocityTracker.obtain(); track(e)
        }

        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> start(e)
                MotionEvent.ACTION_MOVE -> { track(e); if (!dragging && hypot(e.rawX - sx, e.rawY - sy) > slop) { dragging = true; closePalette() } }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (!dragging) { vt?.recycle(); vt = null }
            }
            return dragging
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> start(e)
                MotionEvent.ACTION_MOVE -> {
                    track(e)
                    if (!dragging && hypot(e.rawX - sx, e.rawY - sy) > slop) { dragging = true; closePalette() }
                    if (dragging) place(ox + (e.rawX - sx).toInt(), oy + (e.rawY - sy).toInt())
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    track(e)
                    if (dragging) {
                        val v = vt; v?.computeCurrentVelocity(1000)
                        val vx = v?.xVelocity ?: 0f; val vy = v?.yVelocity ?: 0f
                        if (hypot(vx, vy) > 250f) {
                            scroller.fling(lp.x, lp.y, vx.toInt(), vy.toInt(), 0, maxX(), 0, maxY())
                            postOnAnimation(glide)
                        } else savePlace()
                    }
                    dragging = false; vt?.recycle(); vt = null
                }
            }
            return true
        }
    }

    /** A transparent layer that keeps the strokes on screen until the pen is switched off; each stroke keeps the tool, colour and size it was drawn with. */
    private inner class PenView(c: Context) : View(c) {
        private val done = ArrayList<Pair<Path, Paint>>()
        private var cur: Pair<Path, Paint>? = null
        // a layer of its own, so the eraser clears the strokes under it (and not the screen)
        init { setLayerType(LAYER_TYPE_HARDWARE, null) }
        fun undo() { if (done.isNotEmpty()) { done.removeAt(done.size - 1); invalidate() } }
        fun clear() { done.clear(); invalidate() }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { closePalette(); cur = Path().apply { moveTo(e.x, e.y) } to strokePaint() }
                MotionEvent.ACTION_MOVE -> {
                    // every point the finger passed since the last event is kept (a fast stroke stays round, not a few straight bits)
                    val p = cur?.first
                    if (p != null) for (i in 0 until e.historySize) p.lineTo(e.getHistoricalX(i), e.getHistoricalY(i))
                    p?.lineTo(e.x, e.y)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cur?.let { done.add(it) }; cur = null }
            }
            invalidate(); return true
        }
        override fun onDraw(cv: Canvas) { done.forEach { (p, pt) -> cv.drawPath(p, pt) }; cur?.let { (p, pt) -> cv.drawPath(p, pt) } }
    }
}
