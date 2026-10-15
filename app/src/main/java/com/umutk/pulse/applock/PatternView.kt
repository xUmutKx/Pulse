package com.umutk.pulse.applock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Android-style 3x3 pattern pad: drag through at least four dots, the pattern goes to [onDrawn] as "0-1-4-7" and the pad clears.
 * A plain View, so it works both in the lock overlay and inside Compose (AndroidView).
 */
class PatternView(c: Context, private val dotColor: Int, private val lineColor: Int, private val onDrawn: (String) -> Unit) : View(c) {
    private val density = c.resources.displayMetrics.density
    private val cx = FloatArray(9)
    private val cy = FloatArray(9)
    private val chain = ArrayList<Int>()
    private var fx = 0f
    private var fy = 0f
    private var active = false
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor; strokeWidth = 6 * density; strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private var cell = 0f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val side = (220 * density).toInt()
        setMeasuredDimension(side, side)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val side = min(w, h).toFloat()
        cell = side / 3f
        for (i in 0 until 9) {
            cx[i] = (i % 3 + 0.5f) * cell
            cy[i] = (i / 3 + 0.5f) * cell
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = cell * 0.09f
        for (i in 0 until 9) canvas.drawCircle(cx[i], cy[i], r, dot)
        for (k in 1 until chain.size) {
            val a = chain[k - 1]; val b = chain[k]
            canvas.drawLine(cx[a], cy[a], cx[b], cy[b], line)
        }
        if (active && chain.isNotEmpty()) {
            val last = chain.last()
            canvas.drawLine(cx[last], cy[last], fx, fy, line)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        fx = e.x; fy = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                chain.clear(); active = true
                hit(e.x, e.y)
            }
            MotionEvent.ACTION_MOVE -> hit(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
                if (chain.size >= 4 && e.actionMasked == MotionEvent.ACTION_UP) onDrawn(chain.joinToString("-"))
                chain.clear()
            }
        }
        invalidate()
        return true
    }

    /** Adds the dot under the finger, once per drag. */
    private fun hit(x: Float, y: Float) {
        for (i in 0 until 9) {
            if (i !in chain && hypot(x - cx[i], y - cy[i]) < cell * 0.3f) chain.add(i)
        }
    }
}
