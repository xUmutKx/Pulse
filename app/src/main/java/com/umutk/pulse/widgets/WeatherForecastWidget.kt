package com.umutk.pulse.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.umutk.pulse.R
import com.umutk.pulse.extras.weather.Forecast
import com.umutk.pulse.extras.weather.WeatherApi
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The forecast on the bare wallpaper: today's temperature and sky on top, then seven days (or the next seven hours, the weather app's setting) in a row:
 * the day or hour, the sky, the high and the low. Draws the saved forecast of the weather app at once; a fetch is made when that copy is over an hour old.
 */
class WeatherForecastWidget : PulseWidget() {
    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val id = textId
        val f = WeatherStore.cached(ctx)
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, id) ?: (250 to 110)
        val w = (sz.first * dens).toInt().coerceIn(200, 1400); val h = (sz.second * dens).toInt().coerceIn(100, 1400)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, draw(ctx, f, w, h))
            setViewVisibility(R.id.tile, View.VISIBLE)
            setContentDescription(R.id.tile, tr("Forecast", "Tahmin") + " " + (f?.let { WeatherFmt.temp(it.temp, WeatherFmt.imperial(ctx)) } ?: ""))
            setViewVisibility(R.id.icon, View.GONE); setViewVisibility(R.id.title, View.GONE); setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            val open = Intent().setComponent(ComponentName(ctx, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 9800 + (id and 0xFF), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(ctx, mgr, ids)
        com.umutk.pulse.extras.weather.WeatherAuto.ensure(ctx)
        val place = WeatherStore.savedPlace(ctx) ?: return
        val old = WeatherStore.cached(ctx)
        if (old != null && System.currentTimeMillis() - old.at < MAX_AGE_MS) return
        val pending = goAsync()
        Thread {
            try {
                val raw = WeatherApi.forecast(place.lat, place.lon)
                WeatherStore.saveCache(ctx, place.name, raw, old?.aqi ?: -1, WeatherApi.extras(place.lat, place.lon, WeatherStore.source(ctx) == "mgm", WeatherStore.combine(ctx)))
                WeatherWidgets.refreshAll(ctx)
            } catch (e: Exception) {
                // keep the saved copy on screen; the next update tries again
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun draw(ctx: Context, f: Forecast?, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val pad = w * .04f
        fun paint(sizeF: Float, bold: Boolean = false, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; this.alpha = alpha; textSize = h * sizeF
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setShadowLayer(h * .02f, 0f, h * .006f, 0x99000000.toInt())
        }
        if (f == null) {
            c.drawText(tr("No data yet", "Henüz veri yok"), pad, h * .3f, paint(.12f, true))
            return bmp
        }
        val imp = WeatherFmt.imperial(ctx)
        // the top line: today's temperature on the left, the sky and the day's range on the right
        c.drawText(WeatherFmt.temp(f.temp, imp), pad, h * .30f, paint(.26f, true))
        val sky = WeatherApi.condition(f.code)
        val today = f.days.firstOrNull()
        val range = today?.let { "↑${WeatherFmt.temp(it.max, imp)} / ↓${WeatherFmt.temp(it.min, imp)}" }
        val skyP = paint(.085f, false, 220).apply { textAlign = Paint.Align.RIGHT }
        c.drawText(sky, w - pad, h * .20f, skyP)
        if (range != null) c.drawText(range, w - pad, h * .30f, paint(.075f, false, 190).apply { textAlign = Paint.Align.RIGHT })

        // the row: seven days, or the next seven hours when the weather app's hourly switch is on
        data class Cell(val label: String, val code: Int, val hi: String, val lo: String?)
        val hourly = WeatherStore.widgetHours(ctx) && f.hours.isNotEmpty()
        val cells: List<Cell> = if (hourly) {
            val fmt = SimpleDateFormat("HH", Locale.getDefault())
            val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            f.hours.take(7).map { hr -> Cell(runCatching { parse.parse(hr.time)?.let { fmt.format(it) } }.getOrNull() ?: hr.time.takeLast(5), hr.code, WeatherFmt.temp(hr.temp, imp), null) }
        } else {
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            f.days.take(7).map { d -> Cell(runCatching { parse.parse(d.date)?.let { dayFmt.format(it) } }.getOrNull() ?: d.date.takeLast(5), d.code, WeatherFmt.temp(d.max, imp), WeatherFmt.temp(d.min, imp)) }
        }
        if (cells.isEmpty()) return bmp
        val colW = w * .92f / cells.size
        val y0 = h * .40f
        val name = paint(.11f, true)
        val temp = paint(.10f)
        val low = paint(.10f, false, 190)
        cells.forEachIndexed { i, cell ->
            val cx = pad + colW * (i + .5f)
            c.drawText(cell.label, cx - name.measureText(cell.label) / 2, y0 + h * .14f, name)
            ContextCompat.getDrawable(ctx, WeatherApi.drawable(cell.code))?.let { dr ->
                val s = minOf(colW * .7f, h * .22f).toInt()
                val top = (y0 + h * .18f).toInt()
                dr.setBounds((cx - s / 2).toInt(), top, (cx + s / 2).toInt(), top + s); dr.draw(c)
            }
            c.drawText(cell.hi, cx - temp.measureText(cell.hi) / 2, y0 + h * .48f + h * .10f, temp)
            cell.lo?.let { lo -> c.drawText(lo, cx - low.measureText(lo) / 2, y0 + h * .48f + h * .20f, low) }
        }
        return bmp
    }

    private companion object {
        const val MAX_AGE_MS = 60 * 60_000L
    }
}
