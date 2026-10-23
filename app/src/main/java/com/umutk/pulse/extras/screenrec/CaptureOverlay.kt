package com.umutk.pulse.extras.screenrec

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * The floating control that sits over the screen while a recording runs: pause / resume, stop, a pen to draw on the screen, and a button that
 * grabs a still picture of the screen. The bar drags by its edge. Needs the "draw over other apps" permission; without it nothing is shown.
 */
class CaptureOverlay(
    private val c: Context,
    private val onPause: () -> Unit, private val onStop: () -> Unit, private val onGrab: () -> Unit,
) {
    private val wm = c.getSystemService(WindowManager::class.java)
    private var bar: View? = null
    private var penView: PenView? = null
    private var pauseBtn: TextView? = null
    private val d = c.resources.displayMetrics.density

    fun show(): Boolean {
        if (bar != null) return true
        if (!com.umutk.pulse.Compat.canOverlay(c)) return false
        val row = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { setColor(0xE6202020.toInt()); cornerRadius = 28 * d }
            setPadding((6 * d).toInt(), (4 * d).toInt(), (6 * d).toInt(), (4 * d).toInt())
        }
        fun btn(txt: String, tint: Int, click: (TextView) -> Unit) = TextView(c).apply {
            text = txt; setTextColor(tint); textSize = 18f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams((44 * d).toInt(), (44 * d).toInt())
            setOnClickListener { click(this) }
        }
        pauseBtn = btn("II", Color.WHITE) { onPause() }
        row.addView(pauseBtn)
        row.addView(btn("■", 0xFFFF5A5A.toInt()) { onStop() })
        row.addView(btn("✎", Color.WHITE) { b -> togglePen(b) })
        row.addView(btn("◎", Color.WHITE) { onGrab() })
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (12 * d).toInt(); y = (120 * d).toInt() }
        // dragging anywhere on the bar's padding moves it; the buttons keep their taps
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
        row.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y }
                MotionEvent.ACTION_MOVE -> { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); runCatching { wm.updateViewLayout(row, lp) } }
            }
            true
        }
        val ok = runCatching { wm.addView(row, lp) }.isSuccess
        if (ok) bar = row
        return ok
    }

    fun setPaused(p: Boolean) { pauseBtn?.text = if (p) "▶" else "II" }

    private fun togglePen(b: TextView) {
        val on = penView
        if (on != null) { runCatching { wm.removeView(on) }; penView = null; b.setTextColor(Color.WHITE); return }
        val v = PenView(c)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT,
        )
        if (runCatching { wm.addView(v, lp) }.isSuccess) {
            penView = v; b.setTextColor(0xFFFFD54F.toInt())
            // the bar must stay above the drawing layer
            bar?.let { old -> runCatching { val p = old.layoutParams; wm.removeView(old); wm.addView(old, p) } }
        }
    }

    fun hide() {
        penView?.let { runCatching { wm.removeView(it) } }; penView = null
        bar?.let { runCatching { wm.removeView(it) } }; bar = null
    }

    /** A transparent layer that keeps the finger's strokes on screen until the pen is switched off. */
    private class PenView(c: Context) : View(c) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt(); style = Paint.Style.STROKE; strokeWidth = 6f * c.resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
        private val done = ArrayList<Path>()
        private var cur: Path? = null
        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.action) {
                MotionEvent.ACTION_DOWN -> cur = Path().apply { moveTo(e.x, e.y) }
                MotionEvent.ACTION_MOVE -> cur?.lineTo(e.x, e.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { cur?.let { done.add(it) }; cur = null }
            }
            invalidate(); return true
        }
        override fun onDraw(cv: Canvas) { done.forEach { cv.drawPath(it, paint) }; cur?.let { cv.drawPath(it, paint) } }
    }
}
