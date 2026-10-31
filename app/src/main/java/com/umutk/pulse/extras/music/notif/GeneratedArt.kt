package com.umutk.pulse.extras.music.notif

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

/**
 * A picture for the things that have no cover (an internet radio station, the white noise): a colour field with a drawn symbol,
 * instead of the grey placeholder of the media notification.
 */
object GeneratedArt {
    enum class Kind { RADIO, NOISE }

    private const val S = 512f

    fun make(kind: Kind): Bitmap {
        val bmp = Bitmap.createBitmap(S.toInt(), S.toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val (top, bottom) = when (kind) {
            Kind.RADIO -> Color.rgb(60, 36, 84) to Color.rgb(214, 96, 84)
            Kind.NOISE -> Color.rgb(20, 52, 74) to Color.rgb(74, 150, 176)
        }
        val bg = Paint().apply { shader = LinearGradient(0f, 0f, 0f, S, top, bottom, Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, S, S, bg)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
        when (kind) {
            Kind.RADIO -> {
                // a set with its antenna, and waves going out of it
                val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; alpha = 230 }
                c.drawRoundRect(S * .2f, S * .42f, S * .8f, S * .8f, S * .06f, S * .06f, body)
                ink.strokeWidth = S * .025f
                ink.color = Color.rgb(60, 36, 84)
                c.drawCircle(S * .36f, S * .62f, S * .09f, ink)
                c.drawLine(S * .52f, S * .56f, S * .7f, S * .56f, ink)
                c.drawLine(S * .52f, S * .66f, S * .7f, S * .66f, ink)
                ink.color = Color.WHITE
                ink.strokeWidth = S * .03f
                c.drawLine(S * .42f, S * .42f, S * .6f, S * .2f, ink)
                for (i in 1..3) {
                    val r = S * (.1f + i * .07f)
                    ink.alpha = 255 - i * 50
                    c.drawArc(S * .5f - r, S * .42f - r, S * .5f + r, S * .42f + r, 220f, 100f, false, ink)
                }
            }
            Kind.NOISE -> {
                // the hiss of the mix: a few waves of different length
                ink.strokeWidth = S * .03f
                for (i in 0 until 5) {
                    val y = S * (.3f + i * .1f)
                    val p = Path().apply {
                        moveTo(S * .15f, y)
                        var x = S * .15f
                        var up = true
                        while (x < S * .85f) {
                            val nx = x + S * (.05f + (i % 3) * .02f)
                            quadTo((x + nx) / 2f, if (up) y - S * .05f else y + S * .05f, nx, y)
                            x = nx; up = !up
                        }
                    }
                    ink.alpha = 255 - i * 35
                    c.drawPath(p, ink)
                }
            }
        }
        return bmp
    }
}
