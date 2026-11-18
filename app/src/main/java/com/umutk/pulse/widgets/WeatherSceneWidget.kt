package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.umutk.pulse.R
import com.umutk.pulse.extras.weather.Forecast
import com.umutk.pulse.extras.weather.WeatherApi
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.extras.weather.WeatherLand
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.extras.weather.skyOf
import com.umutk.pulse.tr

/**
 * Weather on a drawn picture: the scenery of the weather page (mountains, sea, city, forest, street or highland) under the sky of this hour and this weather, with the
 * place, the temperature, the condition and the day's high and low on it. It keeps the saved forecast of the weather page up to date like the other weather widgets.
 */
class WeatherSceneWidget : WeatherWidgetBase(wide = false) {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val id = textId
        val f = WeatherStore.cached(ctx)
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, id) ?: (150 to 150)
        val w = (sz.first * dens).toInt().coerceIn(160, 1400)
        val h = (sz.second * dens).toInt().coerceIn(160, 1400)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, draw(ctx, f, w, h))
            setViewVisibility(R.id.tile, View.VISIBLE)
            setContentDescription(R.id.tile, tr("Weather", "Hava durumu") + " " + (f?.let { WeatherFmt.temp(it.temp, WeatherFmt.imperial(ctx)) } ?: ""))
            setViewVisibility(R.id.icon, View.GONE); setViewVisibility(R.id.title, View.GONE); setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            val open = Intent().setComponent(ComponentName(ctx, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 9150 + (id and 0xFF), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    companion object {
        fun draw(ctx: Context, f: Forecast?, w: Int, h: Int): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val r = minOf(w, h) * .16f
            c.clipPath(Path().apply { addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, Path.Direction.CW) })
            val land = WeatherLand.pick(WeatherStore.land(ctx), f?.place.orEmpty(), f?.elev ?: Double.NaN, skyOf(f?.code ?: 0))
            WeatherLand.draw(c, w.toFloat(), h.toFloat(), land, skyOf(f?.code ?: 0), WeatherLand.clockOf(f), variant = WeatherLand.variantOf(f?.place.orEmpty(), land))
            // a soft shade top and bottom keeps the white text readable on every sky
            val sh = Paint()
            sh.shader = LinearGradient(0f, 0f, 0f, h * .5f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP); c.drawRect(0f, 0f, w.toFloat(), h * .5f, sh)
            sh.shader = LinearGradient(0f, h * .55f, 0f, h.toFloat(), 0x00000000, 0x77000000, Shader.TileMode.CLAMP); c.drawRect(0f, h * .55f, w.toFloat(), h.toFloat(), sh)
            val imp = WeatherFmt.imperial(ctx)
            fun paint(size: Float, bold: Boolean = false, light: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = h * size
                typeface = Typeface.create(if (light) "sans-serif-light" else "sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
                setShadowLayer(h * .02f, 0f, h * .006f, 0x88000000.toInt())
            }
            val padX = w * .07f
            val name = (f?.place?.ifBlank { null } ?: tr("Weather", "Hava durumu")).take(18)
            val np = paint(.085f, true)
            c.drawText(name, padX, h * .15f, np)
            val tp = paint(.27f, false, true)
            c.drawText(f?.let { WeatherFmt.temp(it.temp, imp) } ?: "--", padX, h * .43f, tp)
            val cp = paint(.075f, true)
            c.drawText(f?.let { WeatherApi.condition(it.code) } ?: tr("No data yet", "Henüz veri yok"), padX, h * .85f, cp)
            val today = f?.days?.firstOrNull()
            if (today != null) c.drawText("H:${WeatherFmt.temp(today.max, imp)}  L:${WeatherFmt.temp(today.min, imp)}", padX, h * .93f, paint(.07f))
            val icon = f?.let { WeatherApi.drawable(it.code, it.isDay) } ?: 0
            if (icon != 0) ContextCompat.getDrawable(ctx, icon)?.let { d ->
                val s = (h * .24f).toInt(); val right = (w - padX).toInt(); val top = (h * .06f).toInt()
                d.setBounds(right - s, top, right, top + s); d.draw(c)
            }
            return bmp
        }
    }
}
