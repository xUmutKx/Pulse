package com.umutk.pulse.extras.island

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/**
 * Notification light: a light for phones without one, drawn around the camera hole (the island's own place) while a notification waits.
 * Style (ring, dot, glow), motion (pulse, swirl, still) and colour (of the app or one picked) are in [IslandPrefs].
 */
internal class LedView(c: Context) : View(c) {
    private val d = c.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var phase = 0f
    var color = Color.WHITE
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    fun run(on: Boolean) { if (on) { if (!anim.isStarted) anim.start() } else anim.cancel() }

    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(cv: Canvas) {
        val pad = PAD * d
        val pw = IslandPrefs.w * d; val ph = IslandPrefs.h * d
        box.set(pad, pad, pad + pw, pad + ph)
        val r = ph / 2f
        val t = IslandPrefs.ledWidth * d
        val k = when (IslandPrefs.ledAnim) {
            "pulse" -> (0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin(phase * 2f * Math.PI.toFloat()))).coerceIn(0f, 1f)
            else -> 1f
        }
        paint.shader = null; paint.maskFilter = null
        paint.color = Color.argb((255 * k).toInt(), Color.red(color), Color.green(color), Color.blue(color))
        when (IslandPrefs.ledStyle) {
            "dot" -> {
                paint.style = Paint.Style.FILL
                cv.drawCircle(box.centerX(), box.bottom + t * 1.6f, t * 1.6f, paint)
            }
            "glow" -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = t * 2.2f
                paint.maskFilter = BlurMaskFilter(t * 2.5f, BlurMaskFilter.Blur.NORMAL)
                cv.drawRoundRect(box, r, r, paint)
            }
            else -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = t
                if (IslandPrefs.ledAnim == "swirl") {
                    val g = SweepGradient(box.centerX(), box.centerY(), intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f))
                    val m = android.graphics.Matrix(); m.postRotate(phase * 360f, box.centerX(), box.centerY()); g.setLocalMatrix(m)
                    paint.shader = g
                }
                cv.drawRoundRect(box, r, r, paint)
            }
        }
    }

    companion object { const val PAD = 24f }
}

/** Keeps the light's window and the notifications that are lighting it. */
internal class NotifLed(private val c: Context, private val wm: WindowManager) {
    private var view: LedView? = null
    private val waiting = LinkedHashMap<String, Int>()

    private fun params(): WindowManager.LayoutParams {
        val d = c.resources.displayMetrics.density
        val pad = LedView.PAD * d
        return WindowManager.LayoutParams((IslandPrefs.w * d + 2 * pad).toInt(), (IslandPrefs.h * d + 2 * pad + IslandPrefs.ledWidth * d * 4).toInt(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (IslandPrefs.x * d).toInt(); y = (IslandPrefs.top * d - pad).toInt()
            title = "Pulse light"
        }
    }

    /** A notification is waiting: light up in [col] (the colour of the app unless one is picked). */
    fun add(key: String, col: Int) {
        if (!IslandPrefs.led) return
        waiting[key] = if (IslandPrefs.ledAuto) col else IslandPrefs.ledColor
        show()
    }

    fun remove(key: String) { if (waiting.remove(key) != null && waiting.isEmpty()) hide() }

    fun clear() { waiting.clear(); hide() }

    /** A short test of the light with the chosen look. */
    fun test(h: android.os.Handler) { waiting["test"] = if (IslandPrefs.ledAuto) 0xFF4FC3F7.toInt() else IslandPrefs.ledColor; show(); h.postDelayed({ remove("test") }, 4000) }

    fun show() {
        val col = waiting.values.lastOrNull() ?: return
        val v = view ?: LedView(c).also {
            if (runCatching { wm.addView(it, params()) }.isFailure) return
            view = it
        }
        v.color = col; v.run(true); v.invalidate()
    }

    /** Position or size changed in the settings. */
    fun relayout() { view?.let { v -> runCatching { wm.updateViewLayout(v, params()) }; v.invalidate() } }

    fun hide() { view?.let { v -> v.run(false); runCatching { wm.removeView(v) } }; view = null }
}
