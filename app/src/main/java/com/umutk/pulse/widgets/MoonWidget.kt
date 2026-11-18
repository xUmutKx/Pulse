package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import com.umutk.pulse.R
import com.umutk.pulse.extras.weather.MoonArt
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.tr

/** The moon of tonight on its own: the real face lit by the true phase, with the phase's name and its share of light under it when there is room. A tap opens the weather. */
class MoonWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val id = textId
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, id) ?: (110 to 110)
        val w = (sz.first * dens).toInt().coerceIn(80, 1000)
        val h = (sz.second * dens).toInt().coerceIn(80, 1000)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, draw(w, h))
            setViewVisibility(R.id.tile, View.VISIBLE)
            setContentDescription(R.id.tile, WeatherFmt.moon())
            setViewVisibility(R.id.icon, View.GONE); setViewVisibility(R.id.title, View.GONE); setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            val open = Intent().setComponent(ComponentName(ctx, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 9170 + (id and 0xFF), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    companion object {
        fun draw(w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val age = WeatherFmt.moonAge()
            // a tall enough widget gets the name and the light under the moon
            val text = h > w * 1.15f || minOf(w, h) > 260
            val room = if (text) h * .72f else h.toFloat()
            val r = minOf(w.toFloat(), room) * .44f
            val cx = w / 2f; val cy = room / 2f
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6101522.toInt() }
            c.drawCircle(cx, cy, r * 1.12f, bg)
            MoonArt.draw(c, cx, cy, r, age)
            if (text) {
                val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; textSize = h * .07f; typeface = Typeface.create("sans-serif", Typeface.BOLD); setShadowLayer(h * .02f, 0f, 0f, 0x99000000.toInt()) }
                c.drawText(WeatherFmt.moon(), cx, h * .84f, tp)
                tp.typeface = Typeface.DEFAULT; tp.textSize = h * .06f
                c.drawText(tr("%d%% lit", "%%%d aydınlık").format((MoonArt.lit(age) * 100).toInt()), cx, h * .93f, tp)
            }
            return bmp
        }
    }
}
