package com.umutk.pulse.widgets

import android.app.AlarmManager
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
import com.umutk.pulse.extras.weather.Place
import com.umutk.pulse.extras.weather.WeatherApi
import com.umutk.pulse.extras.weather.WeatherFmt
import com.umutk.pulse.extras.weather.WeatherStore
import com.umutk.pulse.tr
import java.text.SimpleDateFormat
import java.util.Locale

/** What a weather widget can show: the current weather with the clock, the seven days, the next seven hours. */
enum class WeatherPart(val bit: Int) { NOW(1), DAILY(2), HOURLY(4) }

/** One cell of a forecast row: the day or hour, the sky, the high (or the temperature) and the low. */
private class ForecastCell(val label: String, val code: Int, val hi: String, val lo: String?, val day: Boolean = true)

/**
 * Drawing for every mix of parts: the current weather on top (the same as the "Weather and clock" widget), then the rows of the seven days and the seven hours.
 * Only the saved forecast of the weather app is drawn here, so the widgets need no network to redraw.
 */
object WeatherParts {
    fun draw(ctx: Context, f: Forecast?, w: Int, h: Int, parts: Set<WeatherPart>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rows = listOfNotNull(WeatherPart.DAILY.takeIf { it in parts }, WeatherPart.HOURLY.takeIf { it in parts })
        val now = WeatherPart.NOW in parts
        val top = if (!now) 0 else if (rows.isEmpty()) h else (h * .52f).toInt()
        if (now) {
            // the same layout as the "Weather and clock" widget, in the top part (or the whole widget when there are no rows)
            val place = Place(WeatherStore.savedPlace(ctx)?.name ?: tr("My location", "Konumum"), 0.0, 0.0, true)
            c.drawBitmap(WeatherClearWidget().draw(ctx, f, place, w, top), 0f, 0f, null)
        } else if (f == null) {
            c.drawText(tr("No data yet", "Henüz veri yok"), w * .04f, h * .3f, textPaint(h * .14f, true))
        }
        rows.forEachIndexed { i, part ->
            val y0 = top + (h - top) * i / rows.size
            val rh = (h - top) / rows.size
            if (f != null) drawRow(ctx, c, cellsFor(ctx, f, part), w, y0.toFloat(), rh.toFloat())
        }
        return bmp
    }

    private fun cellsFor(ctx: Context, f: Forecast, part: WeatherPart): List<ForecastCell> {
        val imp = WeatherFmt.imperial(ctx)
        return if (part == WeatherPart.HOURLY) {
            val fmt = SimpleDateFormat("HH", Locale.getDefault())
            val parse = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            f.hours.take(7).map { hr ->
                ForecastCell(runCatching { parse.parse(hr.time)?.let { fmt.format(it) } }.getOrNull() ?: hr.time.takeLast(5), hr.code, WeatherFmt.temp(hr.temp, imp), null, WeatherApi.isDayAt(f, hr.time))
            }
        } else {
            val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            f.days.take(7).map { d ->
                ForecastCell(runCatching { parse.parse(d.date)?.let { dayFmt.format(it) } }.getOrNull() ?: d.date.takeLast(5), d.code, WeatherFmt.temp(d.max, imp), WeatherFmt.temp(d.min, imp))
            }
        }
    }

    /** One row across the widget, from [y0] down [rh] pixels: the label, the sky, the high and (for days) the low. */
    private fun drawRow(ctx: Context, c: Canvas, cells: List<ForecastCell>, w: Int, y0: Float, rh: Float) {
        if (cells.isEmpty() || rh <= 0f) return
        val colW = w * .92f / cells.size
        val left0 = w * .04f
        val name = textPaint(rh * .26f, true)
        val temp = textPaint(rh * .24f, false)
        val low = textPaint(rh * .24f, false, 190)
        cells.forEachIndexed { i, cell ->
            val cx = left0 + colW * (i + .5f)
            c.drawText(cell.label, cx - name.measureText(cell.label) / 2, y0 + rh * .30f, name)
            ContextCompat.getDrawable(ctx, WeatherApi.drawable(cell.code, cell.day))?.let { dr ->
                val s = minOf(colW * .7f, rh * .36f).toInt()
                val top = (y0 + rh * .36f).toInt()
                dr.setBounds((cx - s / 2).toInt(), top, (cx + s / 2).toInt(), top + s); dr.draw(c)
            }
            val hiY = if (cell.lo != null) y0 + rh * .80f else y0 + rh * .96f
            c.drawText(cell.hi, cx - temp.measureText(cell.hi) / 2, hiY, temp)
            cell.lo?.let { lo -> c.drawText(lo, cx - low.measureText(lo) / 2, y0 + rh * .98f, low) }
        }
    }

    private fun textPaint(size: Float, bold: Boolean, alpha: Int = 255) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE; this.alpha = alpha; textSize = size
        typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        setShadowLayer(size * .08f, 0f, size * .025f, 0x99000000.toInt())
    }
}

/**
 * A weather widget made of [parts], on the bare wallpaper. Its own class per mix, so each one is a separate widget in the picker.
 * Draws the saved forecast of the weather app at once; a fetch is made when that copy is over an hour old. A widget that shows the clock redraws every minute.
 */
abstract class WeatherPartsWidget(private val parts: Set<WeatherPart>, private val heightDp: Int) : PulseWidget() {
    private val mask get() = parts.sumOf { it.bit }

    override fun content(ctx: Context, s: WidgetStyle): RemoteViews {
        val id = textId
        val f = WeatherStore.cached(ctx)
        val dens = ctx.resources.displayMetrics.density
        val sz = WidgetSize.dp(ctx, id) ?: (250 to heightDp)
        val w = (sz.first * dens).toInt().coerceIn(200, 1400); val h = (sz.second * dens).toInt().coerceIn(100, 1400)
        return RemoteViews(ctx.packageName, R.layout.widget_usage).apply {
            setImageViewBitmap(R.id.tile, WeatherParts.draw(ctx, f, w, h, parts))
            setViewVisibility(R.id.tile, View.VISIBLE)
            setContentDescription(R.id.tile, tr("Weather", "Hava durumu") + " " + (f?.let { com.umutk.pulse.extras.weather.WeatherFmt.temp(it.temp, WeatherFmt.imperial(ctx)) } ?: ""))
            setViewVisibility(R.id.icon, View.GONE); setViewVisibility(R.id.title, View.GONE); setViewVisibility(R.id.value, View.GONE)
            setInt(R.id.root, "setBackgroundResource", 0)
            val open = Intent().setComponent(ComponentName(ctx, "com.umutk.pulse.ExtraToolActivity")).putExtra("route", "weather").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 9800 + mask * 16 + (id and 0xF), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == ACTION_MINUTE) {
            // the clock moves with the minute: redrawn from the saved forecast, no network
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, javaClass))
            if (ids.isEmpty()) ctx.getSystemService(AlarmManager::class.java)?.cancel(minutePending(ctx))
            else ids.forEach { mgr.updateAppWidget(it, contentFor(ctx, it, WidgetStyles.of(ctx, it))) }
            return
        }
        super.onReceive(ctx, intent)
    }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        super.onUpdate(ctx, mgr, ids)
        if (WeatherPart.NOW in parts) scheduleMinute(ctx)
        com.umutk.pulse.extras.weather.WeatherAuto.ensure(ctx)
        val place = WeatherStore.savedPlace(ctx) ?: return
        val old = WeatherStore.cached(ctx)
        if (old != null && System.currentTimeMillis() - old.at < MAX_AGE_MS) return
        val pending = goAsync()
        Thread {
            try {
                val raw = com.umutk.pulse.extras.weather.WeatherApi.forecast(place.lat, place.lon)
                WeatherStore.saveCache(ctx, place.name, raw, old?.aqi ?: -1, com.umutk.pulse.extras.weather.WeatherApi.extras(place.lat, place.lon, WeatherStore.source(ctx) == "mgm", WeatherStore.combine(ctx)))
                WeatherWidgets.refreshAll(ctx)
            } catch (e: Exception) {
                // keep the saved copy on screen; the next update tries again
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun minutePending(ctx: Context) = PendingIntent.getBroadcast(ctx, 9600 + mask,
        Intent(ctx, javaClass).setAction(ACTION_MINUTE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** A repeating alarm every minute redraws the clock. */
    private fun scheduleMinute(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val next = (System.currentTimeMillis() / 60_000L + 1) * 60_000L
        am.setRepeating(AlarmManager.RTC, next, 60_000L, minutePending(ctx))
    }

    private companion object {
        const val MAX_AGE_MS = 60 * 60_000L
        const val ACTION_MINUTE = "com.umutk.pulse.action.WEATHER_PARTS_MINUTE"
    }
}

/** Seven days in a row. */
class WeatherDailyWidget : WeatherPartsWidget(setOf(WeatherPart.DAILY), 110)

/** The next seven hours in a row. */
class WeatherHourlyWidget : WeatherPartsWidget(setOf(WeatherPart.HOURLY), 110)

/** The current weather and clock, with the seven days under it. */
class WeatherNowDailyWidget : WeatherPartsWidget(setOf(WeatherPart.NOW, WeatherPart.DAILY), 180)

/** The current weather and clock, with the next seven hours under it. */
class WeatherNowHourlyWidget : WeatherPartsWidget(setOf(WeatherPart.NOW, WeatherPart.HOURLY), 180)

/** The seven days and the next seven hours, one row each. */
class WeatherDailyHourlyWidget : WeatherPartsWidget(setOf(WeatherPart.DAILY, WeatherPart.HOURLY), 180)

/** Everything: the current weather and clock, the seven days and the next seven hours. */
class WeatherAllWidget : WeatherPartsWidget(setOf(WeatherPart.NOW, WeatherPart.DAILY, WeatherPart.HOURLY), 250)
