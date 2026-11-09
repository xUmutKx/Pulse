package com.umutk.pulse.extras.ai.cc

import com.umutk.pulse.R
import com.umutk.pulse.tr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** AMOLED-friendly analog clock; redraws once a minute (no seconds hand, to save battery). */
class ClockView(ctx: Context, private val numbers: Boolean, private val face: Typeface, private val accent: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val tick = object : BroadcastReceiver() { override fun onReceive(c: Context?, i: Intent?) = invalidate() }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(tick, IntentFilter().apply { addAction(Intent.ACTION_TIME_TICK); addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) })
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(tick) }
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = min(cx, cy) - 4f
        val now = Calendar.getInstance()
        val h = now.get(Calendar.HOUR) + now.get(Calendar.MINUTE) / 60f
        val m = now.get(Calendar.MINUTE).toFloat()
        p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.012f; p.color = 0xFF3A3A3A.toInt()
        c.drawCircle(cx, cy, r, p)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val major = i % 3 == 0
            val r0 = r * (if (major) 0.84f else 0.90f)
            p.strokeWidth = r * (if (major) 0.035f else 0.02f); p.color = if (major) 0xFFE0E0E0.toInt() else 0xFF777777.toInt()
            if (!numbers || !major) c.drawLine(cx + (r0 * cos(a)).toFloat(), cy + (r0 * sin(a)).toFloat(), cx + (r * 0.96f * cos(a)).toFloat(), cy + (r * 0.96f * sin(a)).toFloat(), p)
        }
        if (numbers) {
            p.style = Paint.Style.FILL; p.typeface = face; p.textSize = r * 0.26f; p.textAlign = Paint.Align.CENTER; p.color = 0xFFE0E0E0.toInt()
            for (n in intArrayOf(12, 3, 6, 9)) {
                val a = Math.toRadians(n * 30.0 - 90)
                c.drawText(n.toString(), cx + (r * 0.78f * cos(a)).toFloat(), cy + (r * 0.78f * sin(a)).toFloat() + p.textSize * 0.35f, p)
            }
        }
        fun hand(deg: Float, len: Float, w: Float, col: Int) {
            val a = Math.toRadians(deg - 90.0)
            p.style = Paint.Style.STROKE; p.strokeWidth = w; p.color = col
            c.drawLine(cx - (r * 0.1f * cos(a)).toFloat(), cy - (r * 0.1f * sin(a)).toFloat(), cx + (r * len * cos(a)).toFloat(), cy + (r * len * sin(a)).toFloat(), p)
        }
        hand(h * 30f, 0.5f, r * 0.06f, 0xFFF0F0F0.toInt())
        hand(m * 6f, 0.75f, r * 0.04f, accent)
        p.style = Paint.Style.FILL; p.color = accent
        c.drawCircle(cx, cy, r * 0.045f, p)
    }
}
